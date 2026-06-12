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
        finding.setEngineSummary("cfr=failed(stub-only output), jd-core=70, jadx=70, fernflower=75");
        analysis.getDecompileFindings().add(finding);

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Decompile parity report"));
        assertTrue(report.contains("## Risk summary"));
        assertTrue(report.contains("| Risk | Methods |"));
        assertTrue(report.contains("| HIGH |"));
        assertTrue(report.contains("## Risk method index"));
        assertTrue(report.contains("| Risk | Class | Method | Reason |"));
        assertTrue(report.contains("| HIGH | `demo/ReflectiveFlow` | `run(Ljava/lang/String;)Ljava/lang/String;` | reflection call detected |"));
        assertTrue(report.contains("demo/ReflectiveFlow"));
        assertTrue(report.contains("run(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(report.contains("Selected engine: fernflower"));
        assertTrue(report.contains("Engine scores: cfr=failed"));
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
        assertTrue(report.contains("Risk level: MEDIUM (invokedynamic)"));
        assertTrue(report.contains("| MEDIUM | `demo/AdvancedParity` | `run(Ljava/lang/Number;)Ljava/lang/String;` | invokedynamic |"));
    }

    @Test
    void explainsMissingDebugNameRiskSeparatelyFromInvokedynamic() throws Exception {
        Path classFile = compileUserVariablesClassWithoutDebug();
        Path jarPath = tempDir.resolve("missing-debug-names.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/MissingDebugNames.class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("missing-debug-names-out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/MissingDebugNames.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, missingDebugNamesSource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/MissingDebugNames.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Risk level: MEDIUM (missing debug names)"));
        assertTrue(report.contains("| MEDIUM | `demo/MissingDebugNames` | `add(I)I` | missing debug names |"));
        assertTrue(report.contains("Methods with invokedynamic: 0"));
        assertTrue(report.contains("Methods without LocalVariableTable names: 1"));
    }

    @Test
    void mapsInnerClassBytecodeToOuterSourceFile() throws Exception {
        Path innerClassFile = compileInnerClass();
        Path jarPath = tempDir.resolve("inner.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/Outer$Inner.class"));
            jar.write(Files.readAllBytes(innerClassFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("inner-out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/Outer.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, innerSource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/Outer$Inner.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("## demo/Outer$Inner"));
        assertTrue(report.contains("Source coverage: present"));
        assertTrue(report.contains("Source: " + sourcePath));
        assertTrue(report.contains("Methods with missing source: 0"));
    }

    @Test
    void treatsSignatureOnlyMethodsAsLowRiskWhenSourceExists() throws Exception {
        Path interfaceClassFile = compileNoBodyInterface();
        Path jarPath = tempDir.resolve("interface.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/NoBody.class"));
            jar.write(Files.readAllBytes(interfaceClassFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("interface-out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/NoBody.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, noBodyInterfaceSource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/NoBody.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Risk level: LOW (no bytecode body; signature-only method)"));
        assertTrue(report.contains("No Code attribute; abstract/native/synthetic-only method."));
        assertTrue(report.contains("Methods without LocalVariableTable names: 0"));
        assertTrue(report.contains("| HIGH | 0 |"));
    }

    @Test
    void doesNotTreatNoDebugNoUserVariableMethodsAsMissingNameRisk() throws Exception {
        Path classFile = compileNoUserVariablesClassWithoutDebug();
        Path jarPath = tempDir.resolve("no-user-vars.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("demo/NoUserVariables.class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }

        Path outputDir = tempDir.resolve("no-user-vars-out");
        Path sourcePath = outputDir.resolve("src/main/java/demo/NoUserVariables.java");
        Files.createDirectories(sourcePath.getParent());
        Files.write(sourcePath, noUserVariablesSource().getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/NoUserVariables.class");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());
        }

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("Methods without LocalVariableTable names: 0"));
        assertTrue(report.contains("| MEDIUM | 0 |"));
        assertTrue(report.contains("Variable names: not required; bytecode has no user parameters or local stores."));
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

    private Path compileInnerClass() throws Exception {
        Path sourceDir = tempDir.resolve("inner-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("Outer.java");
        Files.write(sourceFile, innerSource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("inner-classes");
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
        return classesDir.resolve("demo/Outer$Inner.class");
    }

    private Path compileNoBodyInterface() throws Exception {
        Path sourceDir = tempDir.resolve("interface-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("NoBody.java");
        Files.write(sourceFile, noBodyInterfaceSource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("interface-classes");
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
        return classesDir.resolve("demo/NoBody.class");
    }

    private Path compileNoUserVariablesClassWithoutDebug() throws Exception {
        Path sourceDir = tempDir.resolve("no-user-vars-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("NoUserVariables.java");
        Files.write(sourceFile, noUserVariablesSource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("no-user-vars-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-g:none",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        return classesDir.resolve("demo/NoUserVariables.class");
    }

    private Path compileUserVariablesClassWithoutDebug() throws Exception {
        Path sourceDir = tempDir.resolve("missing-debug-names-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("MissingDebugNames.java");
        Files.write(sourceFile, missingDebugNamesSource().getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("missing-debug-names-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-g:none",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
        return classesDir.resolve("demo/MissingDebugNames.class");
    }

    private String innerSource() {
        return "package demo;\n" +
                "\n" +
                "public class Outer {\n" +
                "    public static class Inner {\n" +
                "        public String value(String input) {\n" +
                "            String trimmed = input.trim();\n" +
                "            return trimmed;\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    private String noBodyInterfaceSource() {
        return "package demo;\n" +
                "\n" +
                "public interface NoBody {\n" +
                "    String map(String input);\n" +
                "}\n";
    }

    private String noUserVariablesSource() {
        return "package demo;\n" +
                "\n" +
                "public class NoUserVariables {\n" +
                "    static {\n" +
                "        System.setProperty(\"demo.noUserVariables\", \"true\");\n" +
                "    }\n" +
                "\n" +
                "    public NoUserVariables() {\n" +
                "    }\n" +
                "\n" +
                "    public static int value() {\n" +
                "        return 7;\n" +
                "    }\n" +
                "}\n";
    }

    private String missingDebugNamesSource() {
        return "package demo;\n" +
                "\n" +
                "public class MissingDebugNames {\n" +
                "    public int add(int input) {\n" +
                "        int doubled = input * 2;\n" +
                "        return doubled + 1;\n" +
                "    }\n" +
                "}\n";
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
