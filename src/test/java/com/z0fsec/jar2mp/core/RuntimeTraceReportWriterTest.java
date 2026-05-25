package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTraceReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesCountsAndExamplesForCommonTraceKinds() throws Exception {
        RuntimeSmokeRunner.SmokeRunResult result = new RuntimeSmokeRunner.SmokeRunResult();
        result.setCommand("java -jar demo.jar");
        result.setExitCode(0);
        result.setMainClass("demo.App");
        result.setLaunchSource("manifest Main-Class");
        result.setLaunchType("EXECUTABLE_JAR");
        result.setLaunchSupport("SUPPORTED");
        result.setLaunchReason("Manifest Main-Class can be launched with java -jar.");
        result.setTraceResult(new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("reflection", "demo.App", "Class.forName", "java.lang.String", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("resource", "demo.App", "getResourceAsStream", "META-INF/services/demo.Service", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("file", "demo.App", "newInputStream", "/tmp/input.txt", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("socket", "demo.App", "connect", "http://localhost:8080", "main",
                        Arrays.asList("demo.App.main"))
        )));
        result.getNotes().add("Manifest launch metadata takes precedence over startup evidence.");

        new RuntimeTraceReportWriter().write(tempDir.toFile(), result);

        String report = Files.readString(tempDir.resolve("runtime-trace-report.md"));
        assertTrue(report.contains("# Runtime trace report"));
        assertTrue(report.contains("Total events: 4"));
        assertTrue(report.contains("Reflection"));
        assertTrue(report.contains("Resource"));
        assertTrue(report.contains("File"));
        assertTrue(report.contains("Socket"));
        assertTrue(report.contains("Class.forName"));
        assertTrue(report.contains("META-INF/services/demo.Service"));
        assertTrue(report.contains("java -jar demo.jar"));
        assertTrue(report.contains("demo.App"));
        assertTrue(report.contains("Launch type: `EXECUTABLE_JAR`"));
        assertTrue(report.contains("Launch support: `SUPPORTED`"));
        assertTrue(report.contains("Manifest Main-Class can be launched with java -jar."));
    }
}
