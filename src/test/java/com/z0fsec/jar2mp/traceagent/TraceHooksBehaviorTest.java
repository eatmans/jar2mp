package com.z0fsec.jar2mp.traceagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceHooksBehaviorTest {

    @TempDir
    Path tempDir;

    @Test
    void forNameStringKeepsSlashNameFailure() {
        assertThrows(ClassNotFoundException.class, () -> TraceHooks.forName("java/lang/String"));
    }

    @Test
    void forNameWithLoaderKeepsSlashNameFailure() {
        assertThrows(ClassNotFoundException.class,
                () -> TraceHooks.forName("java/lang/String", false, getClass().getClassLoader()));
    }

    @Test
    void forNameStringDoesNotUseContextClassLoaderFallback() throws Exception {
        Path classesDir = compileIsolatedClass();
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader contextLoader = new URLClassLoader(
                new URL[]{classesDir.toUri().toURL()},
                null)) {
            Thread.currentThread().setContextClassLoader(contextLoader);

            assertThrows(ClassNotFoundException.class, () -> TraceHooks.forName("isolated.OnlyContext"));
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private Path compileIsolatedClass() throws Exception {
        Path sourceDir = tempDir.resolve("src/isolated");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("OnlyContext.java");
        Files.write(sourceFile, ("package isolated;\n"
                + "public class OnlyContext {\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        return classesDir;
    }
}
