package com.z0fsec.jar2mp.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

class VineflowerDecompiler {

    private static final int MAX_CAPTURE_BYTES = 20 * 1024;
    private static final long TIMEOUT_SECONDS = 120;
    private static final String VINEFLOWER_VERSION = "1.10.1";
    private static final String VINEFLOWER_JAR_PROPERTY = "jar2mp.vineflowerJar";

    boolean decompile(Path inputDir, Path outputDir) {
        if (inputDir == null || outputDir == null || !Files.isDirectory(inputDir)) {
            return false;
        }
        Path jar = resolveVineflowerJar();
        if (jar == null) {
            System.err.println("Vineflower recovery skipped: set -D" + VINEFLOWER_JAR_PROPERTY
                    + " or install org.vineflower:vineflower:" + VINEFLOWER_VERSION + " in ~/.m2.");
            return false;
        }

        List<String> command = new ArrayList<>();
        command.add(findJavaExecutable(System.getenv()));
        command.add("-jar");
        command.add(jar.toString());
        command.add(inputDir.toString());
        command.add(outputDir.toString());
        return runProcess(inputDir.toFile(), command) == 0;
    }

    private Path resolveVineflowerJar() {
        String configured = System.getProperty(VINEFLOWER_JAR_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            Path jar = new File(configured.trim()).toPath().toAbsolutePath().normalize();
            return Files.isRegularFile(jar) ? jar : null;
        }

        String userHome = System.getProperty("user.home", "");
        if (userHome.trim().isEmpty()) {
            return null;
        }
        Path jar = new File(userHome, ".m2/repository/org/vineflower/vineflower/"
                + VINEFLOWER_VERSION + "/vineflower-" + VINEFLOWER_VERSION + ".jar")
                .toPath()
                .toAbsolutePath()
                .normalize();
        return Files.isRegularFile(jar) ? jar : null;
    }

    private int runProcess(File workingDir, List<String> command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDir);
            process = builder.start();

            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            stdout.join(1000);
            stderr.join(1000);
            if (finished && process.exitValue() != 0) {
                String message = stdout.getContent() + stderr.getContent();
                if (!message.trim().isEmpty()) {
                    System.err.println("Vineflower recovery failed: " + trimForLog(message));
                }
            }
            return finished ? process.exitValue() : -1;
        } catch (IOException e) {
            System.err.println("Vineflower recovery failed: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return -1;
        }
    }

    private static String trimForLog(String value) {
        String trimmed = value.replace('\r', ' ').replace('\n', ' ').trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private static String findJavaExecutable(Map<String, String> environment) {
        Map<String, String> env = environment == null ? java.util.Collections.emptyMap() : environment;
        String javaHome = env.get("JAVA_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            File executable = new File(new File(javaHome.trim(), "bin"), javaExecutableName());
            if (isExecutableFile(executable)) {
                return executable.getAbsolutePath();
            }
        }
        String path = env.get("PATH");
        if (path != null && !path.trim().isEmpty()) {
            String[] parts = path.split(Pattern.quote(File.pathSeparator));
            for (String part : parts) {
                if (part == null || part.trim().isEmpty()) {
                    continue;
                }
                File executable = new File(part.trim(), javaExecutableName());
                if (isExecutableFile(executable)) {
                    return executable.getAbsolutePath();
                }
            }
        }
        return javaExecutableName();
    }

    private static boolean isExecutableFile(File file) {
        return file != null && file.isFile() && (isWindows() || file.canExecute());
    }

    private static String javaExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static class StreamCollector extends Thread {
        private final InputStream inputStream;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        StreamCollector(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int read;
            try {
                while ((read = inputStream.read(buffer)) != -1) {
                    int remaining = MAX_CAPTURE_BYTES - output.size();
                    if (remaining > 0) {
                        output.write(buffer, 0, Math.min(read, remaining));
                    }
                }
            } catch (IOException ignored) {
                return;
            }
        }

        String getContent() {
            try {
                return output.toString("UTF-8");
            } catch (IOException e) {
                return output.toString();
            }
        }
    }
}
