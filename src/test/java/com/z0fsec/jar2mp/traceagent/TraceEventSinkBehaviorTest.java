package com.z0fsec.jar2mp.traceagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TraceEventSinkBehaviorTest {

    @TempDir
    Path tempDir;

    @Test
    void recordDoesNotThrowWhenTracePathIsDirectory() {
        TraceEventSink.install(TraceEventSink.open(tempDir.toString()));
        try {
            assertDoesNotThrow(() -> TraceEventSink.record(
                    "resource",
                    "demo.App",
                    "getResource",
                    "application.yml"));
        } finally {
            TraceEventSink.install(TraceEventSink.disabled());
        }
    }

    @Test
    void resourceHookStillExecutesWhenTraceWriteFails() {
        TraceEventSink.install(TraceEventSink.open(tempDir.toString()));
        try {
            URL resource = assertDoesNotThrow(() ->
                    TraceHooks.getResource(TraceEventSinkBehaviorTest.class, "TraceEventSinkBehaviorTest.class"));
            assertNotNull(resource);
        } finally {
            TraceEventSink.install(TraceEventSink.disabled());
        }
    }
}
