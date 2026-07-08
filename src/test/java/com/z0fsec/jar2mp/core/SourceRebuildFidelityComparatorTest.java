package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.SourceRebuildFidelityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceRebuildFidelityComparatorTest {

    @TempDir
    Path tempDir;

    @Test
    void comparesBootApplicationClassesAgainstTargetClasses() throws Exception {
        Path originalClasses = compileClass("original", "demo.App",
                "package demo;\npublic class App { public String value() { return \"ok\"; } }\n");
        Path jar = tempDir.resolve("boot-app.jar");
        createJar(jar, originalClasses, "BOOT-INF/classes/");
        Path projectDir = tempDir.resolve("project");
        copyClass(originalClasses, projectDir.resolve("target/classes"), "demo/App.class");

        SourceRebuildFidelityResult result = new SourceRebuildFidelityComparator()
                .compare(jar.toFile(), projectDir.toFile(), Collections.emptyList());

        assertTrue(result.isSourceRecompiledClassBytesSame());
        assertEquals(1, result.getOriginalAppClasses());
        assertEquals(1, result.getRecompiledClasses());
        assertEquals(1, result.getCommonClasses());
        assertEquals(1, result.getSameClassBytes());
        assertEquals(0, result.getDifferentClassBytes());
        assertEquals(0, result.getMissingRecompiledClasses());
        assertEquals(0, result.getExtraRecompiledClasses());
        assertEquals(0, result.getCompileFallbackClasses());
    }

    @Test
    void reportsDifferentMissingExtraAndCompileFallbackClasses() throws Exception {
        Path originalClasses = compileClass("original-diff", "demo.App",
                "package demo;\npublic class App { public String value() { return \"one\"; } }\n");
        compileClassInto(originalClasses, "demo.Missing",
                "package demo;\npublic class Missing { public int value() { return 1; } }\n");
        Path recompiledClasses = compileClass("recompiled-diff", "demo.App",
                "package demo;\npublic class App { public String value() { return \"two\"; } }\n");
        compileClassInto(recompiledClasses, "demo.Extra",
                "package demo;\npublic class Extra { public int value() { return 2; } }\n");
        Path jar = tempDir.resolve("boot-app-diff.jar");
        createJar(jar, originalClasses, "BOOT-INF/classes/");
        Path projectDir = tempDir.resolve("project-diff");
        copyClass(recompiledClasses, projectDir.resolve("target/classes"), "demo/App.class");
        copyClass(recompiledClasses, projectDir.resolve("target/classes"), "demo/Extra.class");

        SourceRebuildFidelityResult result = new SourceRebuildFidelityComparator()
                .compare(jar.toFile(), projectDir.toFile(), Collections.singletonList("demo/App.class"));

        assertFalse(result.isSourceRecompiledClassBytesSame());
        assertEquals(2, result.getOriginalAppClasses());
        assertEquals(2, result.getRecompiledClasses());
        assertEquals(1, result.getCommonClasses());
        assertEquals(0, result.getSameClassBytes());
        assertEquals(1, result.getDifferentClassBytes());
        assertEquals(1, result.getMissingRecompiledClasses());
        assertEquals(1, result.getExtraRecompiledClasses());
        assertEquals(1, result.getCompileFallbackClasses());
        assertTrue(result.getSampleDifferentClasses().contains("demo/App.class"));
        assertTrue(result.getSampleMissingRecompiledClasses().contains("demo/Missing.class"));
        assertTrue(result.getSampleExtraRecompiledClasses().contains("demo/Extra.class"));
    }

    private Path compileClass(String workName, String className, String source) throws Exception {
        Path classesDir = tempDir.resolve(workName + "-classes");
        compileClassInto(classesDir, className, source);
        return classesDir;
    }

    private void compileClassInto(Path classesDir, String className, String source) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String packagePath = "";
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd >= 0) {
            packagePath = className.substring(0, packageEnd).replace('.', '/');
        }
        Path sourceDir = tempDir.resolve("src-" + simpleName + "-" + System.nanoTime()).resolve(packagePath);
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(simpleName + ".java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(classesDir);
        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d", classesDir.toString(),
                sourceFile.toString());
        if (compileResult != 0) {
            throw new IllegalStateException("javac failed with exit code " + compileResult);
        }
    }

    private void copyClass(Path sourceRoot, Path targetRoot, String classPath) throws Exception {
        Files.createDirectories(targetRoot.resolve(classPath).getParent());
        Files.copy(sourceRoot.resolve(classPath), targetRoot.resolve(classPath));
    }

    private void createJar(Path jar, Path classesRoot, String prefix) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            try (java.util.stream.Stream<Path> stream = Files.walk(classesRoot)) {
                for (Path classFile : (Iterable<Path>) stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".class"))::iterator) {
                    String relative = classesRoot.relativize(classFile).toString().replace('\\', '/');
                    out.putNextEntry(new JarEntry(prefix + relative));
                    out.write(Files.readAllBytes(classFile));
                    out.closeEntry();
                }
            }
        }
    }
}
