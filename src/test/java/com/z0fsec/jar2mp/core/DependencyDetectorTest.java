package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectJavaVersionReturnsMaximumVersionAcrossEligibleClasses() throws Exception {
        Path jarPath = tempDir.resolve("mixed-java-versions.jar");
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
            addClassEntry(jar, "example/Java8Class.class", 52);
            addClassEntry(jar, "example/Java17Class.class", 61);
        }

        DependencyDetector detector = new DependencyDetector(new PackagePrefixDatabase());
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertEquals(17, detector.detectJavaVersion(jarFile));
        }
    }

    private static void addClassEntry(JarOutputStream jar, String name, int majorVersion) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(minimalClassFileBytes(majorVersion));
        jar.closeEntry();
    }

    private static byte[] minimalClassFileBytes(int majorVersion) {
        return new byte[] {
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0x00, 0x00,
                (byte) ((majorVersion >> 8) & 0xFF), (byte) (majorVersion & 0xFF),
                0x00, 0x01
        };
    }
}
