package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.RuntimeLaunchPlan;
import com.z0fsec.jar2mp.model.StartupFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

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

    @Test
    void runSmokeReportsUnsupportedStandardWarBeforeLaunching() throws Exception {
        RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.getClassFiles().add("com/example/Servlet.class");
        File originalWar = Files.createFile(tempDir.resolve("sample.war")).toFile();
        File agentJar = Files.createFile(tempDir.resolve("trace-agent.jar")).toFile();

        RuntimeSmokeRunner.SmokeRunResult result = runner.runSmoke(
                originalWar,
                analysis,
                agentJar,
                tempDir.resolve("trace.jsonl"),
                Arrays.asList("--profile=test"),
                1L);

        assertEquals(RuntimeLaunchPlan.LaunchType.STANDARD_WAR.name(), result.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.UNSUPPORTED.name(), result.getLaunchSupport());
        assertEquals("UNSUPPORTED_LAUNCH", result.getRunStatus());
        assertTrue(result.getFailureMessage().contains("Unsupported runtime launch"));
        assertTrue(result.getFailureMessage().contains("servlet container"));
    }

    @Test
    void runSmokeClassifiesTimeoutWithEventsAsTraceCollectedTimeout() throws Exception {
        RuntimeSmokeRunner runner = new RuntimeSmokeRunner();
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("demo.SlowTraceMain");
        analysis.setManifestInfo(manifestInfo);

        RuntimeSmokeRunner.SmokeRunResult result = runner.runSmoke(
                createSlowTraceJar().toFile(),
                analysis,
                traceAgentJar(),
                tempDir.resolve("timeout-trace.jsonl"),
                Arrays.asList("--profile=test"),
                1L);

        assertEquals("TRACE_COLLECTED_TIMEOUT", result.getRunStatus());
        assertEquals(-1, result.getExitCode());
        assertFalse(result.getTraceResult().getEvents().isEmpty());
    }

    private File traceAgentJar() {
        File agentJar = new File("target/jar2mp-1.0-trace-agent.jar");
        assertTrue(agentJar.isFile(), "Expected trace agent jar at " + agentJar.getAbsolutePath());
        return agentJar;
    }

    private Path createSlowTraceJar() throws Exception {
        Path sourceDir = tempDir.resolve("slow-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("SlowTraceMain.java");
        Files.write(sourceFile, ("package demo;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Paths;\n"
                + "public class SlowTraceMain {\n"
                + "  public static void main(String[] args) throws Exception {\n"
                + "    String traceFile = System.getProperty(\"jar2mp.traceFile\");\n"
                + "    String event = \"{\\\"kind\\\":\\\"reflection\\\",\\\"owner\\\":\\\"demo.SlowTraceMain\\\",\\\"target\\\":\\\"main\\\",\\\"value\\\":\\\"startup\\\",\\\"thread\\\":\\\"main\\\",\\\"stack\\\":[\\\"demo.SlowTraceMain.main\\\"]}\\n\";\n"
                + "    Files.write(Paths.get(traceFile), event.getBytes(StandardCharsets.UTF_8));\n"
                + "    Thread.sleep(30000L);\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("slow-classes");
        Files.createDirectories(classesDir);
        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, compileResult);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.SlowTraceMain");

        Path jar = tempDir.resolve("slow-trace.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/SlowTraceMain.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/SlowTraceMain.class")));
            out.closeEntry();
        }
        return jar;
    }
}
