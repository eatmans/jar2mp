package com.z0fsec.jar2mp.core;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestClassCompiler {

    private TestClassCompiler() {
    }

    static byte[] compile(String className, String source) throws Exception {
        Path tempDir = Files.createTempDirectory("jar2mp_compile_");
        String packagePath = "";
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            packagePath = className.substring(0, lastDot).replace('.', '/');
            simpleName = className.substring(lastDot + 1);
        }

        Path sourceDir = tempDir.resolve("src").resolve(packagePath);
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(simpleName + ".java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

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
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }
        return Files.readAllBytes(classesDir.resolve(packagePath).resolve(simpleName + ".class"));
    }
}
