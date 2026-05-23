package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FernflowerDecompilerEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void decompilesASimpleClassThroughTheRealFernflowerPath() throws Exception {
        Path classFile = compileSampleClass();
        byte[] classBytes = Files.readAllBytes(classFile);

        DecompilerEngine.Result result =
                new FernflowerDecompilerEngine(new ProjectConfig()).decompile(classBytes, "demo.Sample");

        assertTrue(result.isSuccess());
        assertFalse(DecompilerEngine.isStubSource(result.getSource()));
        assertTrue(result.getSource().contains("package demo;"));
        assertTrue(result.getSource().contains("class Sample"));
    }

    @Test
    void capturesTheRequestedClassInsteadOfLaterNoise() {
        FernflowerDecompilerEngine.CapturingResultSaver saver =
                new FernflowerDecompilerEngine.CapturingResultSaver("demo.Sample");

        saver.saveClassEntry(null, null, "demo.Other", "demo/Other.java",
                "package demo;\npublic class Other {}\n");
        saver.saveClassFile(null, "demo.Sample", "demo/Sample.java",
                "package demo;\npublic class Sample {}\n", null);
        saver.saveClassEntry(null, null, "demo.Sample$Inner", "demo/Sample$Inner.java",
                "package demo;\npublic class Sample$Inner {}\n");

        assertEquals("package demo;\npublic class Sample {}\n", saver.getSource());
    }

    private Path compileSampleClass() throws Exception {
        Path sourceDir = tempDir.resolve("src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("Sample.java");
        Files.write(sourceFile, ("package demo;\n" +
                "public class Sample {\n" +
                "  public String echo(String input) { return input.trim(); }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-g",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertTrue(result == 0);
        return classesDir.resolve("demo/Sample.class");
    }
}
