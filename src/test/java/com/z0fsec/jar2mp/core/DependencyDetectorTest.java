package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
    void embeddedPomDependenciesSuppressLowConfidenceTransitiveClassScanAdditions() throws Exception {
        Path jarPath = tempDir.resolve("boot-shiro.jar");
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
            addEntry(jar, "BOOT-INF/classes/com/example/ShiroConfig.class",
                    classFileWithUtf8Constant(52,
                            "org/springframework/web/servlet/handler/SimpleMappingExceptionResolver"));
        }

        PackagePrefixDatabase packageDb = new PackagePrefixDatabase();
        packageDb.load(new java.io.ByteArrayInputStream(
                "org.springframework.web.servlet=org.springframework:spring-webmvc:6.1.3\n"
                        .getBytes(StandardCharsets.UTF_8)));

        PomInfo pomInfo = new PomInfo();
        pomInfo.setParentGroupId("org.springframework.boot");
        pomInfo.setParentArtifactId("spring-boot-starter-parent");
        pomInfo.setParentVersion("2.1.4.RELEASE");
        pomInfo.getDependencies().add(new MavenDependency("org.springframework.boot",
                "spring-boot-starter-web", "unknown", MavenDependency.Confidence.HIGH));

        DependencyDetector detector = new DependencyDetector(packageDb);
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<MavenDependency> dependencies = detector.detect(jarFile, null, pomInfo);

            assertEquals(1, dependencies.size());
            assertEquals("spring-boot-starter-web", dependencies.get(0).getArtifactId());
            assertFalse(dependencies.stream().anyMatch(dep -> "spring-webmvc".equals(dep.getArtifactId())));
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

    @Test
    void fillsUnknownEmbeddedDependencyVersionsFromPackageDatabase() throws Exception {
        Path jarPath = tempDir.resolve("fat.jar");
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
            addEntry(jar, "com/example/App.class",
                    classFileWithUtf8Constant(52, "org/slf4j/Logger"));
        }

        PackagePrefixDatabase packageDb = new PackagePrefixDatabase();
        packageDb.load(new java.io.ByteArrayInputStream(
                "org.slf4j=org.slf4j:slf4j-api:2.0.11\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        PomInfo pomInfo = new PomInfo();
        pomInfo.getDependencies().add(new MavenDependency("org.slf4j",
                "slf4j-api", "unknown", MavenDependency.Confidence.HIGH));

        DependencyDetector detector = new DependencyDetector(packageDb);
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<MavenDependency> dependencies = detector.detect(jarFile, null, pomInfo);

            assertEquals(1, dependencies.size());
            assertEquals("2.0.11", dependencies.get(0).getVersion());
        }
    }

    @Test
    void scopedScanIgnoresSkippedDependencyClassConstants() throws Exception {
        Path jarPath = tempDir.resolve("scoped-fat.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addEntry(jar, "com/example/App.class",
                    classFileWithUtf8Constant(52, "com/example/App"));
            addEntry(jar, "com/google/gson/Gson.class",
                    classFileWithUtf8Constant(52, "org/slf4j/Logger"));
        }

        PackagePrefixDatabase packageDb = new PackagePrefixDatabase();
        packageDb.load(new java.io.ByteArrayInputStream(
                ("com.google.gson=com.google.code.gson:gson:2.10.1\n"
                        + "org.slf4j=org.slf4j:slf4j-api:2.0.11\n")
                        .getBytes(StandardCharsets.UTF_8)));

        PomInfo gsonPom = new PomInfo();
        gsonPom.setGroupId("com.google.code.gson");
        gsonPom.setArtifactId("gson");
        gsonPom.setVersion("2.10.1");

        DependencyDetector detector = new DependencyDetector(packageDb);
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<MavenDependency> dependencies = detector.detect(jarFile, null, null,
                    Collections.singletonList(gsonPom),
                    Collections.singletonList("com/example/App.class"),
                    Collections.emptyMap());

            assertTrue(dependencies.stream()
                    .anyMatch(dep -> "com.google.code.gson".equals(dep.getGroupId())
                            && "gson".equals(dep.getArtifactId())));
            assertFalse(dependencies.stream()
                    .anyMatch(dep -> "org.slf4j".equals(dep.getGroupId())
                            && "slf4j-api".equals(dep.getArtifactId())));
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
