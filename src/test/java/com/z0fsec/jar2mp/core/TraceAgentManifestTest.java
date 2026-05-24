package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceAgentManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void traceAgentJarHasPremainClass() throws Exception {
        File agentJar = traceAgentJar();

        try (JarFile jar = new JarFile(agentJar)) {
            assertEquals(
                    "com.z0fsec.jar2mp.traceagent.TraceAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertEquals("true", jar.getManifest().getMainAttributes().getValue("Can-Retransform-Classes"));
        }
    }

    @Test
    void traceAgentCanLaunchApplicationJar() throws Exception {
        File agentJar = traceAgentJar();
        Path appJar = createRunnableJar();
        Path traceFile = tempDir.resolve("trace.jsonl");

        List<String> command = Arrays.asList(
                javaExecutable(),
                "-Djar2mp.traceFile=" + traceFile.toAbsolutePath(),
                "-javaagent:" + agentJar.getAbsolutePath() + "=traceFile=" + traceFile.toAbsolutePath(),
                "-jar",
                appJar.toString());

        Process process = new ProcessBuilder(command).start();
        StreamCollector stdout = new StreamCollector(process.getInputStream());
        StreamCollector stderr = new StreamCollector(process.getErrorStream());
        stdout.start();
        stderr.start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        stdout.join(1000);
        stderr.join(1000);

        assertTrue(finished, "Agent smoke process timed out. stdout=" + stdout.getContent()
                + " stderr=" + stderr.getContent());
        assertEquals(0, process.exitValue(), "stdout=" + stdout.getContent()
                + " stderr=" + stderr.getContent());
        RuntimeTraceResult result = new RuntimeTraceCollector().read(traceFile);
        assertTrue(result.hasReflectionCalls(), "Expected reflection trace event in " + traceFile);
    }

    private File traceAgentJar() {
        File targetDir = new File("target");
        File[] agentJars = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && name.contains("agent"));
        assertTrue(agentJars != null && agentJars.length > 0, "Expected an agent jar under target/");

        List<File> jars = Arrays.asList(agentJars);
        jars.sort(Comparator.comparing(File::getName));
        return jars.get(0);
    }

    private Path createRunnableJar() throws Exception {
        Path sourceDir = tempDir.resolve("agent-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("AgentMain.java");
        Files.write(sourceFile, ("package demo;\n" +
                "public class AgentMain {\n" +
                "  public static void main(String[] args) throws Exception {\n" +
                "    Class.forName(\"demo.AgentTarget\").getDeclaredConstructor().newInstance();\n" +
                "  }\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));
        Path targetFile = sourceDir.resolve("AgentTarget.java");
        Files.write(targetFile, ("package demo;\n" +
                "public class AgentTarget {\n" +
                "}\n").getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("agent-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString(),
                targetFile.toString());
        assertEquals(0, result);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.AgentMain");

        Path jar = tempDir.resolve("agent-app.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/AgentMain.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/AgentMain.class")));
            out.closeEntry();
            out.putNextEntry(new JarEntry("demo/AgentTarget.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/AgentTarget.class")));
            out.closeEntry();
        }
        return jar;
    }

    private String javaExecutable() {
        File java = new File(new File(System.getProperty("java.home"), "bin"), "java");
        return java.getAbsolutePath();
    }

    private static final class StreamCollector extends Thread {
        private final InputStream inputStream;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private StreamCollector(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int read;
            try {
                while ((read = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                return;
            }
        }

        private String getContent() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
