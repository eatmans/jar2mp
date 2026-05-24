package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.StartupFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSmokeRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void prefersManifestMainClassOverStartupEvidence() {
        RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("com.example.ManifestMain");
        analysis.setManifestInfo(manifestInfo);

        StartupFinding startupFinding = new StartupFinding("Plain JAR", "com.example.StartupMain");
        analysis.getStartupFindings().add(startupFinding);

        RuntimeSmokeRunner.SmokeCommand command = runner.buildCommand(
                tempDir.resolve("sample.jar").toFile(),
                analysis,
                new File("target/jar2mp-1.0-trace-agent.jar"),
                tempDir.resolve("trace.jsonl"),
                Arrays.asList("--profile=test"));

        assertEquals("com.example.ManifestMain", command.getMainClass());
        assertEquals("manifest Main-Class", command.getLaunchSource());
        assertTrue(command.getNotes().contains("Manifest launch metadata takes precedence over startup evidence."));
    }

    @Test
    void buildsJarLaunchCommandForExecutableArchive() {
        RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("com.example.Main");
        analysis.setManifestInfo(manifestInfo);

        File originalJar = tempDir.resolve("sample.jar").toFile();
        File agentJar = new File("target/jar2mp-1.0-trace-agent.jar");
        Path traceFile = tempDir.resolve("trace.jsonl");
        RuntimeSmokeRunner.SmokeCommand smokeCommand =
                runner.buildCommand(originalJar, analysis, agentJar, traceFile, Arrays.asList("--profile=test"));

        List<String> command = smokeCommand.getCommand();
        assertTrue(command.get(0).endsWith("java"));
        assertEquals("-Djar2mp.traceFile=" + traceFile.toAbsolutePath(), command.get(1));
        assertEquals("-javaagent:" + agentJar.getAbsolutePath() + "=traceFile=" + traceFile.toAbsolutePath(), command.get(2));
        assertEquals("-jar", command.get(3));
        assertEquals(originalJar.getAbsolutePath(), command.get(4));
        assertEquals("--profile=test", command.get(5));
        assertFalse(command.contains("com.example.Main"));
        assertEquals("com.example.Main", smokeCommand.getMainClass());
        assertEquals("manifest Main-Class", smokeCommand.getLaunchSource());
    }

    @Test
    void buildsClasspathFallbackWhenManifestMainClassIsMissing() {
        RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getStartupFindings().add(new StartupFinding("Plain JAR", "demo.Main"));

        File originalJar = tempDir.resolve("sample.jar").toFile();
        File agentJar = new File("target/jar2mp-1.0-trace-agent.jar");
        Path traceFile = tempDir.resolve("trace.jsonl");
        RuntimeSmokeRunner.SmokeCommand smokeCommand =
                runner.buildCommand(originalJar, analysis, agentJar, traceFile, Arrays.asList("--profile=test"));

        List<String> command = smokeCommand.getCommand();
        assertTrue(command.contains("-cp"));
        assertTrue(command.contains(originalJar.getAbsolutePath()));
        assertTrue(command.contains("demo.Main"));
        assertTrue(command.contains("--profile=test"));
        assertEquals("demo.Main", smokeCommand.getMainClass());
        assertEquals("startup evidence", smokeCommand.getLaunchSource());
    }
}
