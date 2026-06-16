package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.core.PostBuildResult;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        PostBuildResult result = new BuildPostProcessor()
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
    void postProcessStoresSourceRebuildFidelityOnAnalysisResult() throws Exception {
        Path rawClasses = tempDir.resolve("source-fidelity-classes");
        compileRawClass(rawClasses, "demo.App", "package demo;\npublic class App {}\n");
        Path jar = tempDir.resolve("source-fidelity.jar");
        createJarFromClass(jar, rawClasses.resolve("demo/App.class"), "demo/App.class");
        Path outputDir = tempDir.resolve("project-with-source-fidelity");
        Files.createDirectories(outputDir.resolve("src/main/java/demo"));
        Files.write(outputDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(outputDir.resolve("src/main/java/demo/App.java"),
                "package demo;\npublic class App {}\n".getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        List<String> messages = new ArrayList<>();

        new BuildPostProcessor().postProcess(jar.toFile(), analysis, outputDir.toFile(), config, messages::add);

        assertNotNull(analysis.getSourceRebuildFidelity());
        assertTrue(analysis.getSourceRebuildFidelity().isSourceRecompiledClassBytesSame(),
                "source rebuild fidelity should be exact: "
                        + "original=" + analysis.getSourceRebuildFidelity().getOriginalAppClasses()
                        + ", recompiled=" + analysis.getSourceRebuildFidelity().getRecompiledClasses()
                        + ", common=" + analysis.getSourceRebuildFidelity().getCommonClasses()
                        + ", same=" + analysis.getSourceRebuildFidelity().getSameClassBytes()
                        + ", different=" + analysis.getSourceRebuildFidelity().getDifferentClassBytes()
                        + ", missing=" + analysis.getSourceRebuildFidelity().getMissingRecompiledClasses()
                        + ", extra=" + analysis.getSourceRebuildFidelity().getExtraRecompiledClasses()
                        + ", fallback=" + analysis.getSourceRebuildFidelity().getCompileFallbackClasses()
                        + ", differentSamples="
                        + analysis.getSourceRebuildFidelity().getSampleDifferentClasses());
        assertTrue(messages.stream().anyMatch(message -> message.contains(
                "源码重编译 class 字节保真: exact=true, same=1/1, different=0, missing=0, extra=0, fallback=0")));
    }

    @Test
    void postProcessStoresPackageFidelityOnAnalysisResult() throws Exception {
        Path rawClasses = tempDir.resolve("package-fidelity-classes");
        compileRawClass(rawClasses, "demo.App", "package demo;\npublic class App {}\n");
        Path jar = tempDir.resolve("package-fidelity.jar");
        createJarFromClass(jar, rawClasses.resolve("demo/App.class"), "demo/App.class");
        Path outputDir = tempDir.resolve("project-with-package-fidelity");
        Files.createDirectories(outputDir.resolve("src/main/java/demo"));
        Files.write(outputDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(outputDir.resolve("src/main/java/demo/App.java"),
                "package demo;\npublic class App {}\n".getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setByteExactPackage(true);

        new BuildPostProcessor().postProcess(jar.toFile(), analysis, outputDir.toFile(), config, message -> { });

        assertNotNull(analysis.getPackageFidelity());
    }

    @Test
    void byteExactPackageRestoresOriginalArtifactWhenRecompiledClassBytesDiffer() throws Exception {
        Path rawClasses = tempDir.resolve("byte-exact-original-classes");
        compileRawClass(rawClasses, "demo.App",
                "package demo;\npublic class App { public String value() { return \"original\"; } }\n");
        Path jar = tempDir.resolve("byte-exact.jar");
        createJarFromClass(jar, rawClasses.resolve("demo/App.class"), "demo/App.class");
        Path outputDir = tempDir.resolve("project-byte-exact-different-classes");
        Files.createDirectories(outputDir.resolve("src/main/java/demo"));
        Files.write(outputDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(outputDir.resolve("src/main/java/demo/App.java"),
                "package demo;\npublic class App { public String value() { return \"rebuilt\"; } }\n"
                        .getBytes(StandardCharsets.UTF_8));

        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setByteExactPackage(true);

        PostBuildResult result = new BuildPostProcessor()
                .postProcess(jar.toFile(), analysis, outputDir.toFile(), config, message -> { });
        Path packagedArtifact = outputDir.resolve("target/verified-project-1.0.0.jar");

        assertNotNull(result.getVerificationResult());
        assertEquals(0, result.getVerificationResult().getExitCode());
        assertEquals(1, result.getBackfilledClassCount(),
                "test fixture must exercise the realistic differing-class-byte path");
        assertNotNull(result.getPackageFidelity());
        assertTrue(result.getPackageFidelity().isExactMatch(),
                "byte-exact mode must only report success after archive bytes match");
        assertFalse(result.hasBlockingFailure(), result.getBlockingFailure());
        assertEquals(sha256(jar), sha256(packagedArtifact),
                "restored package artifact must be byte-for-byte identical to the original");
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

    @Test
    void postProcessLogsEnvironmentWhenStartupFailureIsExternalDependency() throws Exception {
        Path jar = createRedisFailureTraceJar();
        Path outputDir = tempDir.resolve("project-with-redis-failure");
        Files.createDirectories(outputDir);

        ProjectConfig config = new ProjectConfig();
        config.setTraceRuntime(true);
        config.setTraceTimeoutSeconds(10L);
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("demo.RedisFailureMain");
        analysis.setManifestInfo(manifestInfo);
        List<String> messages = new ArrayList<>();

        new BuildPostProcessor().postProcess(jar.toFile(), analysis, outputDir.toFile(), config, messages::add);

        assertTrue(messages.stream().anyMatch(message -> message.contains("运行时追踪: ENVIRONMENT (")));
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

    private Path createRedisFailureTraceJar() throws Exception {
        Path sourceDir = tempDir.resolve("redis-failure-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("RedisFailureMain.java");
        Files.write(sourceFile, ("package demo;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.nio.file.Files;\n"
                + "import java.nio.file.Paths;\n"
                + "public class RedisFailureMain {\n"
                + "  public static void main(String[] args) throws Exception {\n"
                + "    String traceFile = System.getProperty(\"jar2mp.traceFile\");\n"
                + "    String event = \"{\\\"kind\\\":\\\"reflection\\\",\\\"owner\\\":\\\"demo.RedisFailureMain\\\",\\\"target\\\":\\\"main\\\",\\\"value\\\":\\\"startup\\\",\\\"thread\\\":\\\"main\\\",\\\"stack\\\":[\\\"demo.RedisFailureMain.main\\\"]}\\n\";\n"
                + "    Files.write(Paths.get(traceFile), event.getBytes(StandardCharsets.UTF_8));\n"
                + "    System.out.println(\"APPLICATION FAILED TO START\");\n"
                + "    System.out.println(\"Caused by: org.redisson.client.RedisConnectionException: Unable to connect to Redis server: localhost/127.0.0.1:6379\");\n"
                + "    System.exit(1);\n"
                + "  }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("redis-failure-classes");
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
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.RedisFailureMain");

        Path jar = tempDir.resolve("redis-failure-trace.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/RedisFailureMain.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/RedisFailureMain.class")));
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
                "-g",
                "-source", "8",
                "-target", "8",
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

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }
}
