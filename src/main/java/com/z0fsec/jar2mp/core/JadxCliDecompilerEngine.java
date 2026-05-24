package com.z0fsec.jar2mp.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JadxCliDecompilerEngine implements DecompilerEngine {

    private final String command;

    public JadxCliDecompilerEngine() {
        this(resolveCommand());
    }

    JadxCliDecompilerEngine(String command) {
        this.command = command;
    }

    public static boolean isAvailable() {
        String command = resolveCommand();
        return command != null && canRun(command);
    }

    @Override
    public String getName() {
        return "jadx";
    }

    @Override
    public Result decompile(byte[] classBytes, String className) {
        if (command == null || command.trim().isEmpty()) {
            return unavailable(className, "jadx command not found.");
        }

        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("jar2mp_jadx_").toFile();
            File classFile = new File(tempDir, "input.class");
            File outputDir = new File(tempDir, "out");
            File logFile = new File(tempDir, "jadx.log");
            Files.write(classFile.toPath(), classBytes);

            List<String> commandLine = new ArrayList<>();
            commandLine.add(command);
            commandLine.add("-q");
            commandLine.add("--comments-level");
            commandLine.add("none");
            commandLine.add("--single-class");
            commandLine.add(className);
            commandLine.add("-d");
            commandLine.add(outputDir.getAbsolutePath());
            commandLine.add(classFile.getAbsolutePath());

            Process process = new ProcessBuilder(commandLine)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                    .start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                String failureMessage = "jadx timed out after 45 seconds.";
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        0);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String failureMessage = "jadx exited with code " + exitCode + ": "
                        + new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8).trim();
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        0);
            }

            File sourceFile = findSourceFile(outputDir, className);
            if (sourceFile == null) {
                String failureMessage = "jadx did not write source for " + className;
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        0);
            }

            String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
            if (DecompilerEngine.isStubSource(source)) {
                String failureMessage = "jadx returned empty or stub-only output.";
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        DecompilerEngine.scoreSource(source));
            }
            return Result.success(getName(), source, DecompilerEngine.scoreSource(source));
        } catch (Exception e) {
            String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Result.failure(getName(),
                    DecompilerEngine.failureComment(className, failureMessage),
                    failureMessage,
                    0);
        } finally {
            if (tempDir != null) {
                deleteRecursive(tempDir);
            }
        }
    }

    private Result unavailable(String className, String failureMessage) {
        return Result.failure(getName(),
                DecompilerEngine.failureComment(className, failureMessage),
                failureMessage,
                0);
    }

    private static String resolveCommand() {
        String configured = System.getProperty("jar2mp.jadx.path");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("JADX_BIN");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = "jadx";
        }
        return configured.trim();
    }

    private static boolean canRun(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version")
                    .redirectErrorStream(true)
                    .start();
            readAllBytes(process);
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static File findSourceFile(File outputDir, String className) throws IOException {
        File expected = new File(outputDir, "sources/" + className.replace('.', '/') + ".java");
        if (expected.isFile()) {
            return expected;
        }

        List<File> candidates = new ArrayList<>();
        collectJavaFiles(outputDir, candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        String suffix = className.substring(className.lastIndexOf('.') + 1) + ".java";
        for (File candidate : candidates) {
            if (candidate.getName().equals(suffix)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static void collectJavaFiles(File directory, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectJavaFiles(child, files);
            } else if (child.getName().endsWith(".java")) {
                files.add(child);
            }
        }
    }

    private static byte[] readAllBytes(Process process) throws IOException {
        return readAllBytes(process.getInputStream());
    }

    private static byte[] readAllBytes(java.io.InputStream input) throws IOException {
        try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void deleteRecursive(File file) {
        if (!file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
