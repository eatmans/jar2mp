package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void postProcessPreservesRawArtifactAndByteExactReference() throws Exception {
        Path jar = tempDir.resolve("sample-1.0.jar");
        createJar(jar);
        Path outputDir = tempDir.resolve("project");
        Files.createDirectories(outputDir);

        ProjectConfig config = new ProjectConfig();
        config.setEmitRawArtifact(true);
        config.setByteExactPackage(true);
        List<String> messages = new ArrayList<>();

        BuildPostProcessor.PostBuildResult result = new BuildPostProcessor()
                .postProcess(jar.toFile(), new JarAnalysisResult(), outputDir.toFile(), config, messages::add);

        assertTrue(Files.exists(outputDir.resolve("target/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(outputDir.resolve(".jar2mp/byte-exact/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(outputDir.resolve("target/raw-artifact/artifact-fidelity-report.md")));
        assertTrue(result.getRawArtifactFidelity().isExactMatch());
        assertTrue(messages.stream().anyMatch(message -> message.contains("原始归档保真副本")));
    }

    @Test
    void rewritesParityReportAfterCompileFallbackRemovesBrokenSource() throws Exception {
        Path rawClasses = tempDir.resolve("raw-classes");
        compileRawClass(rawClasses, "demo.App",
                "package demo;\npublic class App { public String run() { return \"ok\"; } }\n");
        Path jar = tempDir.resolve("sample.jar");
        createJarFromClass(jar, rawClasses.resolve("demo/App.class"), "demo/App.class");
        Path outputDir = tempDir.resolve("project-with-fallback");
        Files.createDirectories(outputDir.resolve("src/main/java/demo"));
        Files.createDirectories(outputDir.resolve("target/raw-classes/demo"));
        Files.write(outputDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(outputDir.resolve("src/main/java/demo/App.java"),
                "package demo;\npublic class App { public void broken() { missing } }\n"
                        .getBytes(StandardCharsets.UTF_8));
        Files.copy(rawClasses.resolve("demo/App.class"), outputDir.resolve("target/raw-classes/demo/App.class"));
        Files.write(outputDir.resolve("decompile-parity-report.md"),
                "- Source coverage: present\n".getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);

        new BuildPostProcessor().postProcess(jar.toFile(), analysis, outputDir.toFile(), config, message -> { });

        String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
        assertTrue(report.contains("- Source coverage: missing"));
        assertTrue(report.contains("- Source: missing or not generated"));
    }

    @Test
    void postProcessLogsWarningWhenTraceEventsAreCollectedBeforeTimeout() throws Exception {
        Path jar = createSlowTraceJar();
        Path outputDir = tempDir.resolve("project-with-trace-timeout");
        Files.createDirectories(outputDir);

        ProjectConfig config = new ProjectConfig();
        config.setTraceRuntime(true);
        config.setTraceTimeoutSeconds(1L);
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("demo.SlowTraceMain");
        analysis.setManifestInfo(manifestInfo);
        List<String> messages = new ArrayList<>();

        new BuildPostProcessor().postProcess(jar.toFile(), analysis, outputDir.toFile(), config, messages::add);

        assertTrue(messages.stream().anyMatch(message -> message.contains("运行时追踪: WARN (")));
        assertFalse(messages.stream().anyMatch(message -> message.contains("运行时追踪: FAILED")));
    }

    private void createJar(Path jar) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("config.properties");
            out.putNextEntry(entry);
            out.write("mode=test\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private void createJarFromClass(Path jar, Path classFile, String entryName) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry(entryName);
            out.putNextEntry(entry);
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }
    }

    private Path createSlowTraceJar() throws Exception {
        Path sourceDir = tempDir.resolve("slow-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("SlowTraceMain.java");
        Files.write(sourceFile, ("package demo;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Paths;\n"
                + "public class SlowTraceMain {\n"
                + "  public static void main(String[] args) throws Exception {\n"
                + "    String traceFile = System.getProperty(\"jar2mp.traceFile\");\n"
                + "    String event = \"{\\\"kind\\\":\\\"reflection\\\",\\\"owner\\\":\\\"demo.SlowTraceMain\\\",\\\"target\\\":\\\"main\\\",\\\"value\\\":\\\"startup\\\",\\\"thread\\\":\\\"main\\\",\\\"stack\\\":[\\\"demo.SlowTraceMain.main\\\"]}\\n\";\n"
                + "    Files.write(Paths.get(traceFile), event.getBytes(StandardCharsets.UTF_8));\n"
                + "    Thread.sleep(30000L);\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("slow-classes");
        Files.createDirectories(classesDir);
        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        if (compileResult != 0) {
            throw new IllegalStateException("javac failed with exit code " + compileResult);
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.SlowTraceMain");

        Path jar = tempDir.resolve("slow-trace.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/SlowTraceMain.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/SlowTraceMain.class")));
            out.closeEntry();
        }
        return jar;
    }

    private void compileRawClass(Path rawClassesDir, String className, String source) throws Exception {
        String packagePath = "";
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            packagePath = className.substring(0, lastDot).replace('.', '/');
            simpleName = className.substring(lastDot + 1);
        }

        Path sourceDir = tempDir.resolve("raw-src-" + System.nanoTime()).resolve(packagePath);
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(simpleName + ".java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(rawClassesDir);

        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d", rawClassesDir.toString(),
                sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }
    }

    private String pomXml() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>verified-project</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <maven.compiler.source>8</maven.compiler.source>\n" +
                "    <maven.compiler.target>8</maven.compiler.target>\n" +
                "  </properties>\n" +
                "</project>\n";
    }
}
