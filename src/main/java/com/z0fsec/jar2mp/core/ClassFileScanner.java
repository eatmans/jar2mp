package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.ClassFileUtils;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassFileScanner {

    /**
     * Scan a JAR file's class files and extract all package-like strings from constant pools.
     * Returns a set of unique package names (dot-separated) found in the bytecode.
     */
    public Set<String> scanPackages(JarFile jarFile) {
        Set<String> packages = new LinkedHashSet<>();
        boolean executableSpringBootJar = hasEntryPrefix(jarFile, "BOOT-INF/classes/");
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class") && !entry.getName().endsWith("module-info.class")) {
                if (executableSpringBootJar && isSpringBootLoaderClass(entry.getName())) {
                    continue;
                }
                try {
                    byte[] bytes = readEntryBytes(jarFile, entry);
                    Set<String> refs = extractClassReferences(bytes);
                    packages.addAll(refs);
                } catch (Exception ignored) {
                    // Skip unreadable class files
                }
            }
        }

        return packages;
    }

    public Set<String> scanPackages(JarFile jarFile, List<String> classFiles, Map<String, String> classPathMapping) {
        if (classFiles == null) {
            return scanPackages(jarFile);
        }

        Set<String> packages = new LinkedHashSet<>();
        for (String classFile : classFiles) {
            if (classFile == null || !classFile.endsWith(".class") || classFile.endsWith("module-info.class")) {
                continue;
            }

            String rawEntryPath = classPathMapping == null ? null : classPathMapping.get(classFile);
            if (rawEntryPath == null) {
                rawEntryPath = classFile;
            }

            JarEntry entry = jarFile.getJarEntry(rawEntryPath);
            if (entry == null) {
                continue;
            }

            try {
                packages.addAll(extractClassReferences(readEntryBytes(jarFile, entry)));
            } catch (Exception ignored) {
                // Skip unreadable class files
            }
        }
        return packages;
    }

    /**
     * Extract package-like references from a .class file's constant pool.
     */
    public Set<String> extractClassReferences(byte[] classBytes) {
        Set<String> refs = new HashSet<>();
        if (classBytes == null || classBytes.length < 10) return refs;

        try {
            int offset = 8; // skip magic(4) + minor(2) + major(2)
            int cpCount = ClassFileUtils.readU2(classBytes, offset) - 1;
            offset += 2;

            for (int i = 1; i <= cpCount; i++) {
                if (offset >= classBytes.length) break;
                int tag = ClassFileUtils.readU1(classBytes, offset);
                offset += 1;

                switch (tag) {
                    case 1: { // CONSTANT_Utf8
                        if (offset + 1 >= classBytes.length) break;
                        int len = ClassFileUtils.readU2(classBytes, offset);
                        offset += 2;
                        if (offset + len <= classBytes.length) {
                            String str = new String(classBytes, offset, len, "UTF-8");
                            maybeAddPackage(refs, str);
                        }
                        offset += len;
                        break;
                    }
                    case 7:  offset += 2; break;  // Class
                    case 8:  offset += 2; break;  // String
                    case 9:  offset += 4; break;  // Fieldref
                    case 10: offset += 4; break;  // Methodref
                    case 11: offset += 4; break;  // InterfaceMethodref
                    case 12: offset += 4; break;  // NameAndType
                    case 3:  offset += 4; break;  // Integer
                    case 4:  offset += 4; break;  // Float
                    case 5:  offset += 8; i++; break;  // Long (takes 2 slots)
                    case 6:  offset += 8; i++; break;  // Double (takes 2 slots)
                    case 15: offset += 3; break;  // MethodHandle
                    case 16: offset += 2; break;  // MethodType
                    case 17: offset += 4; break;  // Dynamic
                    case 18: offset += 4; break;  // InvokeDynamic
                    case 19: offset += 2; break;  // Module
                    case 20: offset += 2; break;  // Package
                    default:
                        // Unknown tag, stop parsing this class
                        return refs;
                }
            }
        } catch (Exception ignored) {
        }

        return refs;
    }

    private boolean hasEntryPrefix(JarFile jarFile, String prefix) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            if (entries.nextElement().getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpringBootLoaderClass(String entryName) {
        return entryName != null && entryName.startsWith("org/springframework/boot/loader/");
    }

    /**
     * Extract the max class file major version from a class file.
     */
    public int getMajorVersion(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) return 52; // default Java 8
        return ClassFileUtils.readU2(classBytes, 6);
    }

    private void maybeAddPackage(Set<String> refs, String str) {
        // Convert internal form (com/example/Foo) to dot form
        String dot = str.replace('/', '.');

        // Filter: looks like a Java package (at least 2 segments, starts with known prefix)
        // Exclude java.* (JDK), sun.* (internal), [ (array descriptors)
        if (dot.startsWith("[") || dot.startsWith("java.") || dot.startsWith("javax.")
                || dot.startsWith("sun.") || dot.startsWith("com.sun.")
                || dot.startsWith("jdk.") || dot.startsWith("org.w3c.")
                || dot.startsWith("org.xml.") || dot.startsWith("org.ietf.")) {
            return;
        }

        // Check if it looks like a package reference (has dots and alphanumeric)
        int lastDot = dot.lastIndexOf('.');
        if (lastDot < 1) return;

        String potentialPkg = dot.substring(0, lastDot);

        // Validate: each segment should be a valid Java identifier
        String[] segments = potentialPkg.split("\\.");
        if (segments.length < 2) return;

        for (String seg : segments) {
            if (seg.isEmpty() || !Character.isJavaIdentifierStart(seg.charAt(0))) return;
            for (int j = 1; j < seg.length(); j++) {
                if (!Character.isJavaIdentifierPart(seg.charAt(j))) return;
            }
        }

        refs.add(potentialPkg);
    }

    private byte[] readEntryBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }
    }
}
