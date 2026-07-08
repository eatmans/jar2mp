package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.FrameworkFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.StartupFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
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

class StartupDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsSpringBootStartClass() {
        JarAnalysisResult result = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.addEntry("Start-Class", "com.example.App");
        result.setManifestInfo(manifestInfo);

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertStartup(findings, "Spring Boot", "com.example.App", "mvn spring-boot:run",
                "Manifest Start-Class");
    }

    @Test
    void detectsPlainJarMainClass() {
        JarAnalysisResult result = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("com.example.Main");
        result.setManifestInfo(manifestInfo);

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertStartup(findings, "Plain JAR", "com.example.Main",
                "mvn exec:java -Dexec.mainClass=com.example.Main", "Manifest Main-Class");
    }

    @Test
    void detectsWarDescriptor() {
        JarAnalysisResult result = new JarAnalysisResult();
        result.setWar(true);
        result.getResourceFiles().add("WEB-INF/web.xml");
        result.getFrameworkFindings().add(new FrameworkFinding("Servlet WAR", 95));

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertStartup(findings, "Servlet WAR", null, "mvn package", "WEB-INF/web.xml");
        assertTrue(findings.get(0).getKnownGaps().contains("External servlet container is required."));
    }

    @Test
    void detectsMainMethodFromBytecodeWhenManifestIsMissing() throws Exception {
        Path jar = tempDir.resolve("main.jar");
        Path classFile = compileMainClass();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("demo/Main.class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }

        JarAnalysisResult result = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);
        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertStartup(findings, "Plain JAR", "demo.Main",
                "mvn exec:java -Dexec.mainClass=demo.Main", "main(String[]) bytecode");
    }

    @Test
    void springBootFrameworkFindingStillUsesBytecodeMainWhenStartClassIsMissing() throws Exception {
        Path jar = tempDir.resolve("boot-main.jar");
        Path classFile = compileMainClass();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("demo/Main.class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }

        JarAnalysisResult result = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);
        result.getFrameworkFindings().add(new FrameworkFinding("Spring Boot", 85));

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertStartup(findings, "Spring Boot", "demo.Main",
                "mvn spring-boot:run -Dspring-boot.run.main-class=demo.Main",
                "main(String[]) bytecode");
    }

    @Test
    void reportsNoStartupEvidence() {
        JarAnalysisResult result = new JarAnalysisResult();

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertEquals(1, findings.size());
        assertEquals("Unknown", findings.get(0).getApplicationType());
        assertTrue(findings.get(0).getKnownGaps().contains("No startup entrypoint evidence found."));
    }

    @Test
    void skipsUnparseableClassFilesDuringOptionalStartupDetection() throws Exception {
        Path jar = tempDir.resolve("truncated.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("demo/Broken.class"));
            out.write(new byte[]{
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    0, 0, 0, 52, 0, 1
            });
            out.closeEntry();
        }

        JarAnalysisResult result = new JarAnalysisResult();
        result.setSourceFile(jar.toFile());
        result.getClassFiles().add("demo/Broken.class");

        List<StartupFinding> findings = new StartupDetector().detect(result);

        assertEquals(1, findings.size());
        assertEquals("Unknown", findings.get(0).getApplicationType());
    }

    @Test
    void projectBuilderWritesRunbook() throws Exception {
        Path jar = tempDir.resolve("boot.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Start-Class", "com.example.App");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("BOOT-INF/classes/com/example/App.class"));
            out.write(minimalClassBytes(52));
            out.closeEntry();
        }

        JarAnalysisResult analysis = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        String runbook = new String(Files.readAllBytes(outputDir.resolve("RUNBOOK.md")), StandardCharsets.UTF_8);
        assertTrue(runbook.contains("# Runbook"));
        assertTrue(runbook.contains("## Detected application type"));
        assertTrue(runbook.contains("Spring Boot"));
        assertTrue(runbook.contains("mvn spring-boot:run"));
    }

    private void assertStartup(List<StartupFinding> findings, String applicationType, String mainClass,
                               String command, String evidence) {
        assertFalse(findings.isEmpty());
        StartupFinding finding = findings.get(0);
        assertEquals(applicationType, finding.getApplicationType());
        assertEquals(mainClass, finding.getMainClass());
        assertTrue(finding.getCommands().contains(command), "command " + command);
        assertTrue(finding.getEvidence().contains(evidence), "evidence " + evidence);
    }

    private Path compileMainClass() throws Exception {
        Path sourceDir = tempDir.resolve("compile-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("Main.java");
        Files.write(sourceFile, ("package demo;\n" +
                "public class Main {\n" +
                "  public static void main(String[] args) { System.out.println(\"ok\"); }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));
        Path classesDir = tempDir.resolve("compile-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null, null, null,
                "-g",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        return classesDir.resolve("demo/Main.class");
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
