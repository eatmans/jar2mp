package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.traceagent.TraceEventSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTraceCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesJsonlTraceEvents() throws Exception {
        Path trace = tempDir.resolve("trace.jsonl");
        Files.write(trace, Arrays.asList(
                "{\"kind\":\"reflection\",\"owner\":\"demo.App\",\"target\":\"Class.forName\",\"value\":\"java.lang.String\",\"thread\":\"main\",\"stack\":[\"demo.App.main\"]}",
                "{\"kind\":\"resource\",\"owner\":\"demo.App\",\"target\":\"getResourceAsStream\",\"value\":\"META-INF/services/demo.Service\",\"thread\":\"main\",\"stack\":[\"demo.App.main\"]}"
        ), StandardCharsets.UTF_8);

        RuntimeTraceResult result = new RuntimeTraceCollector().read(trace);

        assertEquals(2, result.getEvents().size());
        assertTrue(result.hasReflectionCalls());
        assertFalse(result.getEvents().isEmpty());
        assertEquals("demo.App", result.getEvents().get(0).getOwner());
    }

    @Test
    void writesStableJsonlTraceEvents() throws Exception {
        Path trace = tempDir.resolve("trace.jsonl");
        RuntimeTraceEvent event = new RuntimeTraceEvent(
                "reflection",
                "demo.App",
                "Class.forName",
                "java.lang.String",
                "main",
                Arrays.asList("demo.App.main", "java.lang.Thread.run"));

        RuntimeTraceResult result = new RuntimeTraceResult(Arrays.asList(event));
        new RuntimeTraceWriter().write(trace, result);

        List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"kind\":\"reflection\""));
        assertTrue(lines.get(0).contains("\"owner\":\"demo.App\""));
        assertTrue(lines.get(0).contains("\"target\":\"Class.forName\""));

        RuntimeTraceResult parsed = new RuntimeTraceCollector().read(trace);
        assertEquals(1, parsed.getEvents().size());
        assertEquals("java.lang.String", parsed.getEvents().get(0).getValue());
        assertEquals("main", parsed.getEvents().get(0).getThread());
    }

    @Test
    void traceEventSinkAppendsWithoutTruncating() throws Exception {
        Path trace = tempDir.resolve("trace.jsonl");
        TraceEventSink sink = TraceEventSink.open(trace.toString());

        sink.write("reflection", "demo.App", "Class.forName", "java.lang.String");
        sink.write("resource", "demo.App", "getResourceAsStream", "META-INF/services/demo.Service");

        List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());

        RuntimeTraceResult parsed = new RuntimeTraceCollector().read(trace);
        assertEquals(2, parsed.getEvents().size());
        assertEquals("reflection", parsed.getEvents().get(0).getKind());
        assertEquals("resource", parsed.getEvents().get(1).getKind());
    }
}
