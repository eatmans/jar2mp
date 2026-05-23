package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.DecompileFinding;
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
        DecompileFinding finding = new DecompileFinding("demo/ReflectiveFlow.class", null, null);
        finding.setSelectedEngine("fernflower");
        finding.setFallbackReason("cfr emitted stub-only output");
        analysis.getDecompileFindings().add(finding);

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Decompile parity report"));
        assertTrue(report.contains("demo/ReflectiveFlow"));
        assertTrue(report.contains("run(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(report.contains("Selected engine: fernflower"));
        assertTrue(report.contains("Fallback reason: cfr emitted stub-only output"));
        assertTrue(report.contains("Variable names: present in source"));
        assertTrue(report.contains("java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;"));
        assertTrue(report.contains("Control flow"));
    }

    @Test
    void writesReportWithAdvancedBytecodeParityFacts() throws Exception {
        CompiledClass advancedClass = compileAdvancedParityClass();
        Path jarPath = tempDir.resolve("advanced.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/AdvancedParity.class"));
            jar.write(Files.readAllBytes(advancedClass.classFile));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("demo/AuditMarker.class"));
            jar.write(Files.readAllBytes(advancedClass.annotationClassFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("advanced-out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/AdvancedParity.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, advancedParitySource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/AdvancedParity.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Exception handlers"));
        assertTrue(report.contains("Invokedynamic"));
        assertTrue(report.contains("Fields"));
        assertTrue(report.contains("Annotations"));
        assertTrue(report.contains("Generic signatures"));
        assertTrue(report.contains("Thrown exceptions"));
        assertTrue(report.contains("Risk level: MEDIUM"));
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

    private CompiledClass compileAdvancedParityClass() throws Exception {
        Path sourceDir = tempDir.resolve("advanced-src/demo");
        Files.createDirectories(sourceDir);
        Path annotationSource = sourceDir.resolve("AuditMarker.java");
        Files.write(annotationSource, ("package demo;\n" +
                "import java.lang.annotation.Retention;\n" +
                "import java.lang.annotation.RetentionPolicy;\n" +
                "@Retention(RetentionPolicy.RUNTIME)\n" +
                "public @interface AuditMarker {}\n").getBytes(StandardCharsets.UTF_8));
        Path sourceFile = sourceDir.resolve("AdvancedParity.java");
        Files.write(sourceFile, advancedParitySource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("advanced-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-g",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                annotationSource.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        return new CompiledClass(classesDir.resolve("demo/AdvancedParity.class"),
                classesDir.resolve("demo/AuditMarker.class"));
    }

    private String advancedParitySource() {
        return "package demo;\n" +
                "\n" +
                "import java.io.IOException;\n" +
                "import java.util.concurrent.Callable;\n" +
                "\n" +
                "@AuditMarker\n" +
                "public class AdvancedParity<T extends Number> {\n" +
                "    private String state;\n" +
                "\n" +
                "    @AuditMarker\n" +
                "    public String run(T input) throws IOException {\n" +
                "        Callable<String> callable = () -> String.valueOf(input);\n" +
                "        try {\n" +
                "            this.state = callable.call();\n" +
                "            return this.state;\n" +
                "        } catch (Exception ex) {\n" +
                "            throw new IOException(ex);\n" +
                "        } finally {\n" +
                "            this.state = String.valueOf(this.state);\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    private static class CompiledClass {
        private final Path classFile;
        private final Path annotationClassFile;

        private CompiledClass(Path classFile, Path annotationClassFile) {
            this.classFile = classFile;
            this.annotationClassFile = annotationClassFile;
        }
    }
}
