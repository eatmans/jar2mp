package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ArtifactFidelityComparator {

    private static final int SAMPLE_LIMIT = 5;
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    public ArtifactFidelityResult compare(File original, File rebuilt) throws IOException {
        Map<String, EntryFingerprint> originalEntries = readEntries(original);
        Map<String, EntryFingerprint> rebuiltEntries = readEntries(rebuilt);

        ArtifactFidelityResult result = new ArtifactFidelityResult();
        populateTotals(result, originalEntries, true);
        populateTotals(result, rebuiltEntries, false);

        List<String> originalNames = sorted(originalEntries);
        List<String> rebuiltNames = sorted(rebuiltEntries);

        int common = 0;
        int same = 0;
        int different = 0;
        int commonClasses = 0;
        int sameClasses = 0;
        int differentClasses = 0;
        int commonNestedLibs = 0;
        int sameNestedLibs = 0;
        int differentNestedLibs = 0;

        for (String name : originalNames) {
            EntryFingerprint originalEntry = originalEntries.get(name);
            EntryFingerprint rebuiltEntry = rebuiltEntries.get(name);
            if (rebuiltEntry == null) {
                result.setMissingEntries(result.getMissingEntries() + 1);
                if (isNestedLibrary(name)) {
                    result.setMissingNestedLibs(result.getMissingNestedLibs() + 1);
                }
                result.recordMissing(classifyEntry(name), name);
                addSample(result.getSampleMissingEntries(), name);
                continue;
            }

            common++;
            boolean sameHash = originalEntry.getSha256().equals(rebuiltEntry.getSha256());
            if (sameHash) {
                same++;
            } else {
                different++;
                result.recordDifferent(classifyEntry(name), name);
                addSample(result.getSampleDifferentEntries(), name);
            }

            if (isClassEntry(name)) {
                commonClasses++;
                if (sameHash) {
                    sameClasses++;
                } else {
                    differentClasses++;
                }
            }
            if (isNestedLibrary(name)) {
                commonNestedLibs++;
                if (sameHash) {
                    sameNestedLibs++;
                } else {
                    differentNestedLibs++;
                }
            }
        }

        for (String name : rebuiltNames) {
            if (originalEntries.containsKey(name)) {
                continue;
            }
            result.setExtraEntries(result.getExtraEntries() + 1);
            if (isNestedLibrary(name)) {
                result.setExtraNestedLibs(result.getExtraNestedLibs() + 1);
            }
            result.recordExtra(classifyEntry(name), name);
            addSample(result.getSampleExtraEntries(), name);
        }

        result.setCommonEntries(common);
        result.setSameSha256(same);
        result.setDifferentSha256(different);
        result.setCommonClassEntries(commonClasses);
        result.setSameClassBytes(sameClasses);
        result.setDifferentClassBytes(differentClasses);
        result.setCommonNestedLibs(commonNestedLibs);
        result.setSameNestedLibs(sameNestedLibs);
        result.setDifferentNestedLibs(differentNestedLibs);
        result.setManifestOriginalPresent(originalEntries.containsKey(MANIFEST_PATH));
        result.setManifestRebuiltPresent(rebuiltEntries.containsKey(MANIFEST_PATH));
        result.setManifestSame(result.isManifestOriginalPresent()
                && result.isManifestRebuiltPresent()
                && originalEntries.get(MANIFEST_PATH).getSha256().equals(rebuiltEntries.get(MANIFEST_PATH).getSha256()));
        return result;
    }

    private void populateTotals(ArtifactFidelityResult result, Map<String, EntryFingerprint> entries,
                                boolean original) {
        int classes = 0;
        int nestedLibs = 0;
        int resources = 0;
        for (String name : entries.keySet()) {
            if (isClassEntry(name)) {
                classes++;
            } else if (isNestedLibrary(name)) {
                nestedLibs++;
            } else {
                resources++;
            }
        }

        if (original) {
            result.setOriginalEntryTotal(entries.size());
            result.setOriginalClassEntries(classes);
            result.setOriginalNestedLibs(nestedLibs);
            result.setOriginalResourceEntries(resources);
        } else {
            result.setRebuiltEntryTotal(entries.size());
            result.setRebuiltClassEntries(classes);
            result.setRebuiltNestedLibs(nestedLibs);
            result.setRebuiltResourceEntries(resources);
        }
    }

    private Map<String, EntryFingerprint> readEntries(File file) throws IOException {
        Map<String, EntryFingerprint> entries = new LinkedHashMap<>();
        try (JarFile jarFile = new JarFile(file)) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                entries.put(entry.getName(), new EntryFingerprint(sha256(readAllBytes(jarFile, entry))));
            }
        }
        return entries;
    }

    private byte[] readAllBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream input = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String sha256(byte[] content) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest is not available", e);
        }
    }

    private List<String> sorted(Map<String, EntryFingerprint> entries) {
        List<String> names = new ArrayList<>(entries.keySet());
        Collections.sort(names);
        return names;
    }

    private void addSample(List<String> samples, String name) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(name);
        }
    }

    private boolean isClassEntry(String name) {
        return name != null && name.endsWith(".class");
    }

    private ArtifactFidelityResult.DifferenceBucket classifyEntry(String name) {
        if (name == null) {
            return ArtifactFidelityResult.DifferenceBucket.RESOURCE_ENTRY;
        }
        if (MANIFEST_PATH.equalsIgnoreCase(name)) {
            return ArtifactFidelityResult.DifferenceBucket.MANIFEST;
        }
        if (isClassEntry(name)) {
            return ArtifactFidelityResult.DifferenceBucket.CLASS_BYTECODE;
        }
        if (isNestedLibrary(name)) {
            return ArtifactFidelityResult.DifferenceBucket.NESTED_LIBRARY;
        }
        if (name.startsWith("META-INF/maven/")) {
            return ArtifactFidelityResult.DifferenceBucket.MAVEN_METADATA;
        }
        if (name.startsWith("META-INF/services/")
                || name.contains("/META-INF/services/")) {
            return ArtifactFidelityResult.DifferenceBucket.SERVICE_METADATA;
        }
        if (isBootIndex(name)) {
            return ArtifactFidelityResult.DifferenceBucket.BOOT_INDEX;
        }
        if (isSignatureMetadata(name)) {
            return ArtifactFidelityResult.DifferenceBucket.SIGNATURE_METADATA;
        }
        return ArtifactFidelityResult.DifferenceBucket.RESOURCE_ENTRY;
    }

    private boolean isBootIndex(String name) {
        return "BOOT-INF/classpath.idx".equals(name)
                || "BOOT-INF/layers.idx".equals(name)
                || "WEB-INF/classpath.idx".equals(name)
                || "WEB-INF/layers.idx".equals(name);
    }

    private boolean isSignatureMetadata(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String upper = name.toUpperCase();
        return upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC");
    }

    private boolean isNestedLibrary(String name) {
        if (name == null || !name.endsWith(".jar")) {
            return false;
        }
        return name.startsWith("BOOT-INF/lib/")
                || name.startsWith("WEB-INF/lib/")
                || name.startsWith("lib/")
                || name.contains("/lib/");
    }

    private static class EntryFingerprint {
        private final String sha256;

        private EntryFingerprint(String sha256) {
            this.sha256 = sha256;
        }

        private String getSha256() {
            return sha256;
        }
    }
}
