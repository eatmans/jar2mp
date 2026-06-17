package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadableSourceRetainerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesReadableSourceOutsideMavenSourceRoot() throws Exception {
        new ReadableSourceRetainer().retain(
                tempDir.toFile(),
                "demo/App.class",
                "package demo;\npublic class App {}\n",
                "source could not be recompiled",
                "cfr",
                "demo/App.class");

        Path readableSource = tempDir.resolve("decompiled-readable/demo/App.java");
        String content = new String(Files.readAllBytes(readableSource), StandardCharsets.UTF_8);

        assertTrue(Files.exists(readableSource));
        assertTrue(content.contains("Readable decompiled source (engine: cfr)."));
        assertTrue(content.contains("source could not be recompiled."));
        assertTrue(content.contains("src/main/resources/demo/App.class"));
        assertTrue(content.contains("package demo;"));
        assertFalse(Files.exists(tempDir.resolve("src/main/java/demo/App.java")));
    }
}
