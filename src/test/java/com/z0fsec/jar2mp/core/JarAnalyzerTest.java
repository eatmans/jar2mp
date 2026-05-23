package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.MavenCoordinates;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private Path createJar(String fileName, String classEntry, byte[] classBytes) throws Exception {
        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(classEntry));
            out.write(classBytes);
            out.closeEntry();
        }
        return jar;
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

    private static class FailingPackagePrefixDatabase extends PackagePrefixDatabase {
        @Override
        public MavenCoordinates lookup(String packageName) {
            throw new AssertionError("Dependency detection should not look up package mappings");
        }
    }
}
