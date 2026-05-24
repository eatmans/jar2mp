package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JadxCliDecompilerEngineTest {

    @Test
    void reportsUnavailableCommandAsEngineFailure() throws Exception {
        byte[] classBytes = TestClassCompiler.compile("demo.Sample",
                "package demo; public class Sample { public String value() { return \"ok\"; } }");

        DecompilerEngine.Result result = new JadxCliDecompilerEngine("/path/to/missing/jadx")
                .decompile(classBytes, "demo.Sample");

        assertEquals("jadx", result.getEngineName());
        assertTrue(result.getFailureMessage().contains("No such file")
                || result.getFailureMessage().contains("Cannot run program")
                || result.getFailureMessage().contains("missing"));
    }
}
