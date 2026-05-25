package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void verifiesGeneratedProjectWithMavenCompile() throws Exception {
        Path projectDir = createProject("public class App { public String run() { return \"ok\"; } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertEquals(0, result.getExitCode());
        assertTrue(result.getCommand().contains("mvn"));
        assertTrue(result.getCommand().contains("compile"));
        assertTrue(result.getCommand().contains("-Dspring-javaformat.skip=true"));
        assertTrue(result.getCommand().contains("-Denforcer.skip=true"));
        assertTrue(result.getCommand().contains("-Drat.skip=true"));
        assertTrue(result.getSummary().contains("BUILD SUCCESS"));
        assertEquals("NONE", result.getFailureType());
    }

    @Test
    void classifiesCompilationFailure() throws Exception {
        Path projectDir = createProject("public class App { public void broken() { missing } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertTrue(result.getExitCode() != 0);
        assertEquals("COMPILATION_ERROR", result.getFailureType());
        assertTrue(result.getSummary().contains("Compilation failure"));
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrors().get(0).getSourcePath().endsWith("App.java"));

        new ProjectVerifier().writeReport(projectDir.toFile(), result);
        String errorReport = new String(Files.readAllBytes(projectDir.resolve("verification-errors.md")),
                StandardCharsets.UTF_8);
        assertTrue(errorReport.contains("# Verification errors"));
        assertTrue(errorReport.contains("| Category | Source | Line | Column | Message |"));
        assertTrue(errorReport.contains("src/main/java/demo/App.java"));
    }

    @Test
    void writesVerificationReport() throws Exception {
        Path projectDir = createProject("public class App { public String run() { return \"ok\"; } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");
        new ProjectVerifier().writeReport(projectDir.toFile(), result);

        String report = new String(Files.readAllBytes(projectDir.resolve("verification-report.md")),
                StandardCharsets.UTF_8);
        assertTrue(report.contains("# Verification report"));
        assertTrue(report.contains("- Command:"));
        assertTrue(report.contains("- Exit code: 0"));
        assertTrue(report.contains("- Failure type: NONE"));
        assertTrue(report.contains("- Summary:"));
        assertTrue(report.contains("- Error count: 0"));

        String errorReport = new String(Files.readAllBytes(projectDir.resolve("verification-errors.md")),
                StandardCharsets.UTF_8);
        assertTrue(errorReport.contains("# Verification errors"));
        assertTrue(errorReport.contains("No structured verification errors were parsed."));
    }

    private Path createProject(String javaSource) throws Exception {
        Path projectDir = tempDir.resolve("project-" + System.nanoTime());
        Path sourceDir = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.write(projectDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(sourceDir.resolve("App.java"),
                ("package demo;\n" + javaSource + "\n").getBytes(StandardCharsets.UTF_8));
        return projectDir;
    }

    private String pomXml() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>verified-project</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <maven.compiler.source>8</maven.compiler.source>\n" +
                "    <maven.compiler.target>8</maven.compiler.target>\n" +
                "  </properties>\n" +
                "</project>\n";
    }
}
