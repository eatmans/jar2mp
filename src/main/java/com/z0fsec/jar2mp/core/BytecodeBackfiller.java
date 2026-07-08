package com.z0fsec.jar2mp.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Copies original .class bytes from the source JAR into target/classes for classes where
 * recompilation produced different bytes, bypassing decompiler non-determinism.
 */
class BytecodeBackfiller {

    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String WAR_CLASSES_PREFIX = "WEB-INF/classes/";

    int backfill(File originalArtifact, File projectDir, List<String> differentClassPaths)
            throws IOException {
        if (originalArtifact == null || !originalArtifact.isFile()
                || projectDir == null
                || differentClassPaths == null || differentClassPaths.isEmpty()) {
            return 0;
        }
        Path classesDir = projectDir.toPath().resolve("target/classes");
        if (!Files.isDirectory(classesDir)) {
            return 0;
        }
        Set<String> targets = new HashSet<>(differentClassPaths);
        Map<String, byte[]> originals = readOriginalAppClasses(originalArtifact);
        int count = 0;
        for (String classPath : targets) {
            byte[] bytes = originals.get(classPath);
            if (bytes == null) {
                continue;
            }
            Path dest = classesDir.resolve(classPath.replace('/', File.separatorChar));
            Files.createDirectories(dest.getParent());
            Files.write(dest, bytes);
            count++;
        }
        return count;
    }

    private Map<String, byte[]> readOriginalAppClasses(File artifact) throws IOException {
        Map<String, byte[]> all = new LinkedHashMap<>();
        Map<String, byte[]> boot = new LinkedHashMap<>();
        Map<String, byte[]> war = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(artifact)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) {
                    continue;
                }
                byte[] bytes = readBytes(jf, entry);
                if (name.startsWith(BOOT_CLASSES_PREFIX)) {
                    boot.put(name.substring(BOOT_CLASSES_PREFIX.length()), bytes);
                } else if (name.startsWith(WAR_CLASSES_PREFIX)) {
                    war.put(name.substring(WAR_CLASSES_PREFIX.length()), bytes);
                } else if (!name.contains("!/")) {
                    all.put(name, bytes);
                }
            }
        }
        if (!boot.isEmpty()) {
            return boot;
        }
        if (!war.isEmpty()) {
            return war;
        }
        return all;
    }

    private byte[] readBytes(JarFile jf, JarEntry entry) throws IOException {
        try (InputStream in = jf.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
