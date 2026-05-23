package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationResult;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProjectVerifier {

    private static final int MAX_CAPTURE_BYTES = 20 * 1024;
    private static final long TIMEOUT_SECONDS = 120;

    public VerificationResult verify(File projectDir, String goal) {
        String effectiveGoal = goal == null || goal.trim().isEmpty() ? "compile" : goal.trim();
        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("-q");
        command.add("-DskipTests");
        for (String part : effectiveGoal.split("\\s+")) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }

        VerificationResult result = new VerificationResult();
        result.setCommand(joinCommand(command));
        result.setExitCode(-1);

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(projectDir);
            process = builder.start();

            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setTimedOut(true);
                result.setExitCode(-1);
                result.setFailureType("TIMEOUT");
            } else {
                result.setExitCode(process.exitValue());
            }

            stdout.join(1000);
            stderr.join(1000);
            result.setStdout(stdout.getContent());
            result.setStderr(stderr.getContent());

            if (result.getFailureType() == null) {
                result.setFailureType(classify(result));
            }
            result.setSummary(summarize(result));
        } catch (IOException e) {
            result.setFailureType(isMavenNotFound(e) ? "MAVEN_NOT_FOUND" : "UNKNOWN");
            result.setStderr(e.getMessage());
            result.setSummary(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            result.setFailureType("TIMEOUT");
            result.setTimedOut(true);
            result.setSummary("Verification interrupted.");
        }

        return result;
    }

    public void writeReport(File projectDir, VerificationResult result) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Verification report\n\n");
        report.append("- Command: ").append(nullToEmpty(result.getCommand())).append("\n");
        report.append("- Exit code: ").append(result.getExitCode()).append("\n");
        report.append("- Failure type: ").append(nullToEmpty(result.getFailureType())).append("\n");
        report.append("- Summary: ").append(nullToEmpty(result.getSummary()).replace("\r", " ").replace("\n", " ")).append("\n");
        IoUtils.writeStringToFile(new File(projectDir, "verification-report.md"), report.toString());
    }

    private String classify(VerificationResult result) {
        if (result.getExitCode() == 0) {
            return "NONE";
        }

        String combined = (nullToEmpty(result.getStdout()) + "\n" + nullToEmpty(result.getStderr()))
                .toLowerCase(Locale.ROOT);
        if (combined.contains("dependencyresolutionexception")
                || combined.contains("could not resolve dependencies")
                || combined.contains("failed to collect dependencies")
                || combined.contains("could not find artifact")) {
            return "DEPENDENCY_RESOLUTION";
        }
        if (combined.contains("compilation failure")
                || combined.contains("compilation error")
                || combined.contains("maven-compiler-plugin")
                || combined.contains("cannot find symbol")) {
            return "COMPILATION_ERROR";
        }
        if (combined.contains("test failures")
                || combined.contains("there are test failures")
                || combined.contains("maven-surefire-plugin")) {
            return "TEST_FAILURE";
        }
        return "UNKNOWN";
    }

    private String summarize(VerificationResult result) {
        String combined = nullToEmpty(result.getStdout()) + "\n" + nullToEmpty(result.getStderr());
        if (result.isTimedOut()) {
            return "Verification timed out after " + TIMEOUT_SECONDS + " seconds.";
        }
        if (result.getExitCode() == 0) {
            if (combined.contains("BUILD SUCCESS")) {
                return extractLineContaining(combined, "BUILD SUCCESS");
            }
            return "BUILD SUCCESS";
        }
        if (combined.contains("Compilation failure")) {
            return extractLineContaining(combined, "Compilation failure");
        }
        if (combined.contains("Could not resolve dependencies")) {
            return extractLineContaining(combined, "Could not resolve dependencies");
        }
        if (combined.contains("BUILD FAILURE")) {
            return extractLineContaining(combined, "BUILD FAILURE");
        }
        String trimmed = combined.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String extractLineContaining(String value, String token) {
        String[] lines = value.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains(token)) {
                return line.trim();
            }
        }
        return token;
    }

    private boolean isMavenNotFound(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains("mvn");
    }

    private String joinCommand(List<String> command) {
        StringBuilder builder = new StringBuilder();
        for (String part : command) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
