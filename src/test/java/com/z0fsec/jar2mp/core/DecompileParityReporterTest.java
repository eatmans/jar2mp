package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompileParityReporterTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsBytecodeFactsNeededToCheckDecompileParity() throws Exception {
        Path classFile = compileReflectiveFlowClass();

        BytecodeFingerprint fingerprint = BytecodeFingerprint.fromClassFile(Files.readAllBytes(classFile));
        BytecodeFingerprint.MethodFingerprint method =
                fingerprint.getMethodsByKey().get("run(Ljava/lang/String;)Ljava/lang/String;");

        assertEquals("demo/ReflectiveFlow", fingerprint.getClassName());
        assertNotNull(method);
        assertTrue(method.getLocalVariableNames().contains("input"));
        assertTrue(method.getLocalVariableNames().contains("message"));
        assertTrue(method.getMethodCalls().contains("java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;"));
        assertTrue(method.getStringConstants().contains("java.lang.String"));
        assertTrue(method.getBranchOpcodeCount() > 0);
    }

    @Test
    void writesReportThatFlagsSourceCoverageAndReflectionRisks() throws Exception {
        Path classFile = compileReflectiveFlowClass();
        Path jarPath = tempDir.resolve("sample.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/ReflectiveFlow.class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/ReflectiveFlow.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, reflectiveFlowSource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/ReflectiveFlow.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Decompile parity report"));
        assertTrue(report.contains("demo/ReflectiveFlow"));
        assertTrue(report.contains("run(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(report.contains("Variable names: present in source"));
        assertTrue(report.contains("java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;"));
        assertTrue(report.contains("Control flow"));
    }

    private Path compileReflectiveFlowClass() throws Exception {
        Path sourceDir = tempDir.resolve("compile-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("ReflectiveFlow.java");
        Files.write(sourceFile, reflectiveFlowSource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("compile-classes");
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
        assertEquals(0, result);
        return classesDir.resolve("demo/ReflectiveFlow.class");
    }

    private String reflectiveFlowSource() {
        return "package demo;\n" +
                "\n" +
                "public class ReflectiveFlow {\n" +
                "    public String run(String input) throws Exception {\n" +
                "        String message = input == null ? \"missing\" : input.trim();\n" +
                "        if (message.length() > 2) {\n" +
                "            Class<?> type = Class.forName(\"java.lang.String\");\n" +
                "            return type.getName() + \":\" + message;\n" +
                "        }\n" +
                "        return message;\n" +
                "    }\n" +
                "}\n";
    }
}
