package com.z0fsec.jar2mp.traceagent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceTransformerTest {

    @Test
    void shouldTraceTypeKeepsBroadDefaultWhenIncludesAreEmpty() {
        assertTrue(TraceTransformer.shouldTraceType(
                "org.springframework.context.support.GenericApplicationContext",
                Collections.<String>emptyList()));
        assertFalse(TraceTransformer.shouldTraceType("java.lang.String", Collections.<String>emptyList()));
        assertFalse(TraceTransformer.shouldTraceType(
                "com.z0fsec.jar2mp.traceagent.TraceHooks",
                Collections.<String>emptyList()));
    }

    @Test
    void shouldTraceTypeLimitsApplicationClassesToConfiguredIncludes() {
        assertTrue(TraceTransformer.shouldTraceType(
                "com.example.Main",
                Arrays.asList("com.example.", "org.springframework.boot.loader.")));
        assertTrue(TraceTransformer.shouldTraceType(
                "org.springframework.boot.loader.launch.JarLauncher",
                Arrays.asList("com.example.", "org.springframework.boot.loader.")));
        assertFalse(TraceTransformer.shouldTraceType(
                "org.springframework.context.support.GenericApplicationContext",
                Arrays.asList("com.example.", "org.springframework.boot.loader.")));
        assertFalse(TraceTransformer.shouldTraceType(
                "java.lang.String",
                Arrays.asList("com.example.", "org.springframework.boot.loader.")));
    }
}
