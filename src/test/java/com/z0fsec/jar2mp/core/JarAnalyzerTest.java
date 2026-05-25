package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.MavenCoordinates;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledDependencyDetectionSkipsPackageLookupButKeepsCoreAnalysis() throws Exception {
        ProjectConfig config = new ProjectConfig();
        config.setDetectDependencies(false);
        Path jar = createJar("sample-1.2.3.jar", "com/example/App.class",
                minimalClassBytes(61, "com/example/App", "com/google/gson/Gson"));

        JarAnalysisResult result = new JarAnalyzer(new FailingPackagePrefixDatabase(), config)
                .analyze(jar.toFile(), null);

        assertTrue(result.getDetectedDependencies().isEmpty());
        assertEquals("sample", result.getDetectedArtifactId());
        assertEquals("1.2.3", result.getDetectedVersion());
        assertEquals(17, result.getJavaVersion());
    }

    @Test
    void assemblyJarSkipsEmbeddedDependencyClassesButKeepsDependencyCoordinates() throws Exception {
        ProjectConfig config = new ProjectConfig();
        config.setDetectDependencies(true);
        Path jar = createJar("sample-1.0.0.jar",
                entry("com/example/App.class",
                        minimalClassBytes(52, "com/example/App", "com/google/gson/Gson")),
                entry("com/google/gson/Gson.class",
                        minimalClassBytes(52, "com/google/gson/Gson", "com/google/gson/internal/Helper")),
                entry("META-INF/maven/com.example/sample/pom.properties",
                        "groupId=com.example\nartifactId=sample\nversion=1.0.0\n".getBytes(StandardCharsets.UTF_8)),
                entry("META-INF/maven/com.google.code.gson/gson/pom.properties",
                        "groupId=com.google.code.gson\nartifactId=gson\nversion=2.10.1\n".getBytes(StandardCharsets.UTF_8)));

        JarAnalysisResult result = new JarAnalyzer(packageDb("com.google.gson=com.google.code.gson:gson:2.10.1\n"), config)
                .analyze(jar.toFile(), null);

        assertTrue(result.getClassFiles().contains("com/example/App.class"));
        assertFalse(result.getClassFiles().contains("com/google/gson/Gson.class"));
        assertTrue(result.getSkippedDependencyClassFiles().contains("com/google/gson/Gson.class"));
        assertTrue(result.isEmbeddedDependencyPath("com/google/gson/Gson.class"));
        assertEquals(2, result.getEmbeddedPomInfos().size());
        assertTrue(result.getDetectedDependencies().stream()
                .anyMatch(dep -> "com.google.code.gson".equals(dep.getGroupId())
                        && "gson".equals(dep.getArtifactId())
                        && "2.10.1".equals(dep.getVersion())
                        && dep.getConfidence() == MavenDependency.Confidence.HIGH));
    }

    private Path createJar(String fileName, String classEntry, byte[] classBytes) throws Exception {
        return createJar(fileName, entry(classEntry, classBytes));
    }

    private Path createJar(String fileName, TestEntry... entries) throws Exception {
        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (TestEntry entry : entries) {
                out.putNextEntry(new JarEntry(entry.name));
                out.write(entry.bytes);
                out.closeEntry();
            }
        }
        return jar;
    }

    private TestEntry entry(String name, byte[] bytes) {
        return new TestEntry(name, bytes);
    }

    private PackagePrefixDatabase packageDb(String mappings) {
        PackagePrefixDatabase database = new PackagePrefixDatabase();
        database.load(new java.io.ByteArrayInputStream(mappings.getBytes(StandardCharsets.UTF_8)));
        return database;
    }

    private byte[] minimalClassBytes(int majorVersion, String className, String referencedClassName) {
        byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
        byte[] referenceBytes = referencedClassName.getBytes(StandardCharsets.UTF_8);
        int size = 24 + nameBytes.length + 3 + referenceBytes.length;
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xCA;
        bytes[1] = (byte) 0xFE;
        bytes[2] = (byte) 0xBA;
        bytes[3] = (byte) 0xBE;
        writeU2(bytes, 4, 0);
        writeU2(bytes, 6, majorVersion);
        writeU2(bytes, 8, 4);
        bytes[10] = 1;
        writeU2(bytes, 11, nameBytes.length);
        System.arraycopy(nameBytes, 0, bytes, 13, nameBytes.length);
        int classInfoOffset = 13 + nameBytes.length;
        bytes[classInfoOffset] = 1;
        writeU2(bytes, classInfoOffset + 1, referenceBytes.length);
        System.arraycopy(referenceBytes, 0, bytes, classInfoOffset + 3, referenceBytes.length);
        classInfoOffset += 3 + referenceBytes.length;
        bytes[classInfoOffset] = 7;
        writeU2(bytes, classInfoOffset + 1, 1);
        writeU2(bytes, classInfoOffset + 3, 0x0021);
        writeU2(bytes, classInfoOffset + 5, 2);
        writeU2(bytes, classInfoOffset + 7, 0);
        writeU2(bytes, classInfoOffset + 9, 0);
        return bytes;
    }

    private void writeU2(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static class TestEntry {
        private final String name;
        private final byte[] bytes;

        private TestEntry(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private static class FailingPackagePrefixDatabase extends PackagePrefixDatabase {
        @Override
        public MavenCoordinates lookup(String packageName) {
            throw new AssertionError("Dependency detection should not look up package mappings");
        }
    }
}
