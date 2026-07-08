package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.FrameworkFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsFrameworkSignalsAndRestorationActions() {
        JarAnalysisResult result = new JarAnalysisResult();
        result.setWar(true);
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.addEntry("Start-Class", "com.example.DemoApplication");
        result.setManifestInfo(manifestInfo);
        result.getClassPathMapping().put("com/example/DemoApplication.class", "BOOT-INF/classes/com/example/DemoApplication.class");
        result.getClassFiles().add("org/apache/shiro/spring/web/ShiroFilterFactoryBean.class");
        result.getResourceFiles().add("BOOT-INF/classes/application.yml");
        result.getResourceFiles().add("BOOT-INF/lib/spring-boot-2.7.18.jar");
        result.getResourceFiles().add("WEB-INF/web.xml");
        result.getResourceFiles().add("mapper/UserMapper.xml");
        result.getResourceFiles().add("shiro.ini");
        result.getResourceFiles().add("log4j2.xml");
        result.getResourceFiles().add("native/libdemo.so");
        result.getDetectedDependencies().add(new MavenDependency("org.springframework.boot",
                "spring-boot-starter-web", "2.7.18", MavenDependency.Confidence.HIGH));

        List<FrameworkFinding> findings = new FrameworkDetector().detect(result);

        assertFinding(findings, "Spring Boot", 95, "BOOT-INF/classes/",
                "Generate Spring Boot run command");
        assertFinding(findings, "Servlet WAR", 95, "WEB-INF/web.xml",
                "Preserve src/main/webapp layout");
        assertFinding(findings, "MyBatis", 70, "mapper/UserMapper.xml",
                "Verify mapper XML resource paths");
        assertFinding(findings, "Shiro", 70, "shiro.ini",
                "Review Shiro filter and realm configuration");
        assertFinding(findings, "Logging", 70, "log4j2.xml",
                "Preserve logging configuration resources");
        assertFinding(findings, "Native/JNI", 70, "native/libdemo.so",
                "Verify native library loading path");
    }

    @Test
    void jarAnalyzerPopulatesFrameworkFindings() throws Exception {
        Path jar = tempDir.resolve("boot.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Start-Class", "com.example.DemoApplication");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("BOOT-INF/classes/com/example/DemoApplication.class"));
            out.write(minimalClassBytes(52));
            out.closeEntry();
            out.putNextEntry(new JarEntry("BOOT-INF/classes/application.yml"));
            out.write("spring.application.name=demo".getBytes());
            out.closeEntry();
        }

        JarAnalysisResult result = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);

        assertFalse(result.getFrameworkFindings().isEmpty());
        assertFinding(result.getFrameworkFindings(), "Spring Boot", 95, "BOOT-INF/classes/",
                "Generate Spring Boot run command");
    }

    private void assertFinding(List<FrameworkFinding> findings, String name, int minConfidence,
                               String evidence, String action) {
        FrameworkFinding finding = findings.stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing finding: " + name));
        assertTrue(finding.getConfidence() >= minConfidence, "confidence for " + name);
        assertTrue(finding.getEvidence().contains(evidence), "evidence for " + name);
        assertTrue(finding.getRecommendedActions().contains(action), "action for " + name);
    }

    private byte[] minimalClassBytes(int majorVersion) {
        byte[] bytes = new byte[31];
        bytes[0] = (byte) 0xCA;
        bytes[1] = (byte) 0xFE;
        bytes[2] = (byte) 0xBA;
        bytes[3] = (byte) 0xBE;
        writeU2(bytes, 4, 0);
        writeU2(bytes, 6, majorVersion);
        writeU2(bytes, 8, 3);
        bytes[10] = 1;
        writeU2(bytes, 11, 3);
        bytes[13] = 'A';
        bytes[14] = 'p';
        bytes[15] = 'p';
        bytes[16] = 7;
        writeU2(bytes, 17, 1);
        writeU2(bytes, 19, 0x0021);
        writeU2(bytes, 21, 2);
        writeU2(bytes, 23, 0);
        writeU2(bytes, 25, 0);
        writeU2(bytes, 27, 0);
        writeU2(bytes, 29, 0);
        return bytes;
    }

    private void writeU2(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }
}
