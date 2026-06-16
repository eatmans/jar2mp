package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationResult;
import com.z0fsec.jar2mp.model.VerificationError;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectVerifier implements BuildVerifier {

    private static final int MAX_CAPTURE_BYTES = 20 * 1024;
    private static final long TIMEOUT_SECONDS = 120;
    private static final int MAX_COMPILE_FALLBACK_ROUNDS = 100;
    private final VerificationErrorParser errorParser = new VerificationErrorParser();

    public VerificationResult verify(File projectDir, String goal) {
        String effectiveGoal = goal == null || goal.trim().isEmpty() ? "compile" : goal.trim();
        List<String> command = new ArrayList<>();
        command.add(findMavenExecutable(projectDir, System.getenv()));
        command.add("-q");
        addVerificationSkipFlags(command);
        for (String part : effectiveGoal.split("\\s+")) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }

        List<String> compileFallbacks = new ArrayList<>();
        VerificationResult result = runMavenVerification(projectDir, command);
        for (int round = 0; shouldApplyCompileFallback(result) && round < MAX_COMPILE_FALLBACK_ROUNDS; round++) {
            List<String> applied = applyCompileFallbacks(projectDir, result);
            if (applied.isEmpty()) {
                break;
            }
            for (String classPath : applied) {
                if (!compileFallbacks.contains(classPath)) {
                    compileFallbacks.add(classPath);
                }
            }
            result = runMavenVerification(projectDir, command);
        }
        result.getCompileFallbackClassPaths().addAll(compileFallbacks);
        return result;
    }

    private VerificationResult runMavenVerification(File projectDir, List<String> command) {
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
            result.getErrors().addAll(errorParser.parse(projectDir, result.getStdout(), result.getStderr()));

            if (result.getFailureType() == null) {
                result.setFailureType(classify(result));
            }
            result.setSummary(summarize(result));
        } catch (IOException e) {
            result.setFailureType(isMavenNotFound(e) ? "MAVEN_NOT_FOUND" : "UNKNOWN");
            result.setStderr(e.getMessage());
            result.setSummary(e.getMessage());
            result.getErrors().addAll(errorParser.parse(projectDir, result.getStdout(), result.getStderr()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            result.setFailureType("TIMEOUT");
            result.setTimedOut(true);
            result.setSummary("Verification interrupted.");
            result.getErrors().addAll(errorParser.parse(projectDir, result.getStdout(), result.getStderr()));
        }

        return result;
    }

    private boolean shouldApplyCompileFallback(VerificationResult result) {
        return result != null
                && result.getExitCode() != 0
                && "COMPILATION_ERROR".equals(result.getFailureType())
                && !result.getErrors().isEmpty();
    }

    private List<String> applyCompileFallbacks(File projectDir, VerificationResult result) {
        List<String> applied = new ArrayList<>();
        if (projectDir == null || result == null) {
            return applied;
        }

        Path projectPath = projectDir.toPath().toAbsolutePath().normalize();
        Path rawRoot = projectPath.resolve("target/raw-classes").normalize();
        Path resourcesRoot = projectPath.resolve("src/main/resources").normalize();
        if (!Files.isDirectory(rawRoot)) {
            return applied;
        }

        Set<String> sourceClassPaths = new LinkedHashSet<>();
        for (VerificationError error : result.getErrors()) {
            String classPath = classPathFromSourcePath(error.getSourcePath());
            if (classPath != null) {
                sourceClassPaths.add(classPath);
            }
        }

        for (String classPath : sourceClassPaths) {
            Path rawClass = resolveUnder(rawRoot, classPath);
            if (rawClass == null || !Files.isRegularFile(rawClass)) {
                continue;
            }
            try {
                copyRawClassFamily(rawRoot, resourcesRoot, classPath);
                Path sourceFile = resolveUnder(projectPath, "src/main/java/"
                        + classPath.substring(0, classPath.length() - ".class".length())
                        + ".java");
                if (sourceFile != null) {
                    Files.deleteIfExists(sourceFile);
                }
                applied.add(classPath);
            } catch (IOException ignored) {
                // Leave the original verification failure intact if the fallback cannot be applied.
            }
        }
        return applied;
    }

    private String classPathFromSourcePath(String sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        String normalized = sourcePath.replace('\\', '/');
        String marker = "src/main/java/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0 || !normalized.endsWith(".java")) {
            return null;
        }
        String relativeJavaPath = normalized.substring(markerIndex + marker.length());
        return relativeJavaPath.substring(0, relativeJavaPath.length() - ".java".length()) + ".class";
    }

    private void copyRawClassFamily(Path rawRoot, Path resourcesRoot, String classPath) throws IOException {
        Path rawClass = resolveUnder(rawRoot, classPath);
        if (rawClass == null || !Files.isRegularFile(rawClass)) {
            return;
        }
        copyRawClass(rawRoot, resourcesRoot, classPath);

        Path parent = rawClass.getParent();
        String fileName = rawClass.getFileName().toString();
        if (parent == null || !fileName.endsWith(".class")) {
            return;
        }
        String innerClassGlob = fileName.substring(0, fileName.length() - ".class".length()) + "$*.class";
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(parent, innerClassGlob)) {
            for (Path sibling : stream) {
                if (Files.isRegularFile(sibling)) {
                    copyRawClass(rawRoot, resourcesRoot, rawRoot.relativize(sibling).toString().replace('\\', '/'));
                }
            }
        }
    }

    private void copyRawClass(Path rawRoot, Path resourcesRoot, String classPath) throws IOException {
        Path source = resolveUnder(rawRoot, classPath);
        Path target = resolveUnder(resourcesRoot, classPath);
        if (source == null || target == null || !Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveUnder(Path baseDir, String relativePath) {
        if (baseDir == null || relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }
        Path base = baseDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath.replace('\\', '/')).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }

    private void addVerificationSkipFlags(List<String> command) {
        command.add("-DskipTests");
        command.add("-Dmaven.test.skip=true");
        command.add("-Dcheckstyle.skip=true");
        command.add("-Dspring-javaformat.skip=true");
        command.add("-Dimpsort.skip=true");
        command.add("-Dformatter.skip=true");
        command.add("-Dspotless.check.skip=true");
        command.add("-Dspotless.apply.skip=true");
        command.add("-Dlicense.skip=true");
        command.add("-Drat.skip=true");
        command.add("-Denforcer.skip=true");
        command.add("-Djacoco.skip=true");
        command.add("-Dgit.commit.id.skip=true");
        command.add("-Dmaven.javadoc.skip=true");
    }

    static String findMavenExecutable(File projectDir, Map<String, String> environment) {
        Map<String, String> env = environment == null ? java.util.Collections.emptyMap() : environment;
        String wrapper = findProjectMavenWrapper(projectDir);
        if (wrapper != null) {
            return wrapper;
        }
        String mavenHome = executableFromHome(env.get("MAVEN_HOME"));
        if (mavenHome != null) {
            return mavenHome;
        }
        String m2Home = executableFromHome(env.get("M2_HOME"));
        if (m2Home != null) {
            return m2Home;
        }
        String pathMaven = executableFromPath(env.get("PATH"));
        if (pathMaven != null) {
            return pathMaven;
        }
        String wrapperCacheMaven = executableFromMavenWrapperCache();
        return wrapperCacheMaven == null ? "mvn" : wrapperCacheMaven;
    }

    private static String findProjectMavenWrapper(File projectDir) {
        if (projectDir == null) {
            return null;
        }
        File mvnw = new File(projectDir, isWindows() ? "mvnw.cmd" : "mvnw");
        if (isExecutableFile(mvnw)) {
            return mvnw.getAbsolutePath();
        }
        File alternate = new File(projectDir, isWindows() ? "mvnw" : "mvnw.cmd");
        if (isExecutableFile(alternate)) {
            return alternate.getAbsolutePath();
        }
        return null;
    }

    private static String executableFromHome(String home) {
        if (home == null || home.trim().isEmpty()) {
            return null;
        }
        File executable = new File(new File(home.trim(), "bin"), mavenExecutableName());
        return isExecutableFile(executable) ? executable.getAbsolutePath() : null;
    }

    private static String executableFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String[] parts = path.split(Pattern.quote(File.pathSeparator));
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            File executable = new File(part.trim(), mavenExecutableName());
            if (isExecutableFile(executable)) {
                return executable.getAbsolutePath();
            }
        }
        return null;
    }

    private static String executableFromMavenWrapperCache() {
        Path wrapperDists = new File(System.getProperty("user.home", ""), ".m2/wrapper/dists").toPath();
        if (!Files.isDirectory(wrapperDists)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(wrapperDists)) {
            return stream
                    .filter(path -> path.getFileName() != null
                            && path.getFileName().toString().equals(mavenExecutableName()))
                    .filter(path -> path.getParent() != null
                            && "bin".equals(path.getParent().getFileName().toString()))
                    .filter(path -> isExecutableFile(path.toFile()))
                    .max(Comparator.comparing(ProjectVerifier::mavenExecutableSortKey))
                    .map(path -> path.toAbsolutePath().toString())
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String mavenExecutableSortKey(Path path) {
        String value = path.toString();
        Matcher matcher = Pattern.compile("apache-maven-(\\d+)\\.(\\d+)\\.(\\d+)").matcher(value);
        if (matcher.find()) {
            return String.format("%05d.%05d.%05d:%s",
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    value);
        }
        return "00000.00000.00000:" + value;
    }

    private static boolean isExecutableFile(File file) {
        return file != null && file.isFile() && (isWindows() || file.canExecute());
    }

    private static String mavenExecutableName() {
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public void writeReport(File projectDir, VerificationResult result) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Verification report\n\n");
        report.append("- Command: ").append(nullToEmpty(result.getCommand())).append("\n");
        report.append("- Exit code: ").append(result.getExitCode()).append("\n");
        report.append("- Failure type: ").append(nullToEmpty(result.getFailureType())).append("\n");
        report.append("- Summary: ").append(nullToEmpty(result.getSummary()).replace("\r", " ").replace("\n", " ")).append("\n");
        report.append("- Error count: ").append(result.getErrors().size()).append("\n");
        report.append("- Compile fallback classes: ").append(result.getCompileFallbackClassPaths().size()).append("\n");
        if (!result.getCompileFallbackClassPaths().isEmpty()) {
            report.append("- Compile fallback class paths: ")
                    .append(abbreviateList(result.getCompileFallbackClassPaths(), 20)).append("\n");
        }
        Map<String, Integer> categories = countCategories(result);
        if (!categories.isEmpty()) {
            report.append("- Error categories: ");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : categories.entrySet()) {
                if (!first) {
                    report.append(", ");
                }
                report.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            report.append("\n");
        }
        IoUtils.writeStringToFile(new File(projectDir, "verification-report.md"), report.toString());
        writeErrorsReport(projectDir, result);
    }

    public void writeErrorsReport(File projectDir, VerificationResult result) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Verification errors\n\n");
        report.append("- Total errors: ").append(result.getErrors().size()).append("\n");

        Map<String, Integer> categories = countCategories(result);
        if (!categories.isEmpty()) {
            report.append("- Categories:\n");
            for (Map.Entry<String, Integer> entry : categories.entrySet()) {
                report.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        report.append("\n");

        if (result.getErrors().isEmpty()) {
            report.append("No structured verification errors were parsed.\n");
        } else {
            report.append("| Category | Source | Line | Column | Message |\n");
            report.append("| --- | --- | ---: | ---: | --- |\n");
            for (VerificationError error : result.getErrors()) {
                report.append("| ")
                        .append(escapeMarkdown(nullToEmpty(error.getCategory()))).append(" | ")
                        .append(escapeMarkdown(nullToEmpty(error.getSourcePath()))).append(" | ")
                        .append(error.getLine()).append(" | ")
                        .append(error.getColumn()).append(" | ")
                        .append(escapeMarkdown(nullToEmpty(error.getMessage()))).append(" |\n");
            }
        }
        IoUtils.writeStringToFile(new File(projectDir, "verification-errors.md"), report.toString());
    }

    private Map<String, Integer> countCategories(VerificationResult result) {
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (VerificationError error : result.getErrors()) {
            String category = nullToEmpty(error.getCategory());
            if (category.isEmpty()) {
                category = "UNKNOWN";
            }
            Integer count = categories.get(category);
            categories.put(category, count == null ? 1 : count + 1);
        }
        return categories;
    }

    private String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
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

    private String abbreviateList(List<String> values, int limit) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(values.size(), limit);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('`').append(values.get(i)).append('`');
        }
        if (values.size() > limit) {
            builder.append(", ...");
        }
        return builder.toString();
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
