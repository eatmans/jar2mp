package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdCoreDecompilerEngineTest {

    @Test
    void decompilesASimpleClassThroughJdCore() throws Exception {
        byte[] classBytes = TestClassCompiler.compile("demo.Sample",
                "package demo; public class Sample { public String value() { return \"ok\"; } }");

        DecompilerEngine.Result result = new JdCoreDecompilerEngine().decompile(classBytes, "demo.Sample");

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertTrue(result.getSource().contains("class Sample"));
        assertTrue(result.getSource().contains("value()"));
    }
}
