package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void embeddedPomDependenciesSuppressLowConfidenceClassScanConflicts() throws Exception {
        Path jarPath = tempDir.resolve("boot.jar");
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
            addClassEntry(jar, "org/springframework/boot/loader/JarLauncher.class", 52);
            addEntry(jar, "BOOT-INF/classes/com/example/App.class",
                    classFileWithUtf8Constant(52, "org/springframework/boot/SpringApplication"));
        }

        PomInfo pomInfo = new PomInfo();
        pomInfo.getDependencies().add(new MavenDependency("org.springframework.boot",
                "spring-boot-starter", "unknown", MavenDependency.Confidence.HIGH));

        DependencyDetector detector = new DependencyDetector(new PackagePrefixDatabase());
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<MavenDependency> dependencies = detector.detect(jarFile, null, pomInfo);

            assertEquals(1, dependencies.size());
            assertEquals("spring-boot-starter", dependencies.get(0).getArtifactId());
            assertFalse(dependencies.stream().anyMatch(dep -> "spring-boot".equals(dep.getArtifactId())));
        }
    }

    @Test
    void manifestClasspathSkipsUnresolvableUnknownHints() throws Exception {
        Path jarPath = tempDir.resolve("security.jar");
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
            addClassEntry(jar, "com/example/SecurityMain.class", 52);
        }

        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setClassPath("lib/shiro-lang-1.13.0.jar lib/spring-aop-5.3.37.jar lib/gson-2.13.2.jar");

        PomInfo pomInfo = new PomInfo();
        pomInfo.getDependencies().add(new MavenDependency("org.apache.shiro",
                "shiro-core", "1.13.0", MavenDependency.Confidence.HIGH));

        DependencyDetector detector = new DependencyDetector(new PackagePrefixDatabase());
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<MavenDependency> dependencies = detector.detect(jarFile, manifestInfo, pomInfo);

            assertTrue(dependencies.stream()
                    .anyMatch(dep -> "org.apache.shiro".equals(dep.getGroupId())
                            && "shiro-core".equals(dep.getArtifactId())));
            assertTrue(dependencies.stream()
                    .anyMatch(dep -> "com.google.code.gson".equals(dep.getGroupId())
                            && "gson".equals(dep.getArtifactId())));
            assertFalse(dependencies.stream().anyMatch(dep -> "unknown".equals(dep.getGroupId())));
        }
    }

    private static void addClassEntry(JarOutputStream jar, String name, int majorVersion) throws IOException {
        addEntry(jar, name, minimalClassFileBytes(majorVersion));
    }

    private static void addEntry(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(bytes);
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

    private static byte[] classFileWithUtf8Constant(int majorVersion, String value) throws IOException {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        writeU2(out, 0);
        writeU2(out, majorVersion);
        writeU2(out, 2);
        out.write(1);
        writeU2(out, encoded.length);
        out.write(encoded);
        return out.toByteArray();
    }

    private static void writeU2(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
