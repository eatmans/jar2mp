package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.RuntimeLaunchPlan;
import com.z0fsec.jar2mp.model.StartupFinding;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuntimeSmokeRunner {

    private static final long DEFAULT_TIMEOUT_SECONDS = 120L;
    private static final long STARTUP_PROBE_POLL_MILLIS = 200L;
    private static final int STARTUP_PROBE_CONNECT_TIMEOUT_MILLIS = 500;
    private static final int STARTUP_PROBE_READ_TIMEOUT_MILLIS = 500;
    private static final String TRACE_COLLECTED_HEALTHY_TIMEOUT = "TRACE_COLLECTED_HEALTHY_TIMEOUT";
    private static final String TRACE_COLLECTED_TIMEOUT = "TRACE_COLLECTED_TIMEOUT";
    private static final String STARTUP_FAILED_TIMEOUT = "STARTUP_FAILED_TIMEOUT";
    private static final String HTTP_RESPONDED = "HTTP_RESPONDED";
    private static final Pattern[] LOCAL_HTTP_PORT_PATTERNS = new Pattern[] {
            Pattern.compile("Tomcat started on port\\(s\\):\\s*(\\d+)"),
            Pattern.compile("Tomcat started on port\\s+(\\d+)"),
            Pattern.compile("Netty started on port\\s+(\\d+)"),
            Pattern.compile("Undertow started on port\\s+(\\d+)")
    };
    private static final Pattern[] STARTUP_FAILURE_PATTERNS = new Pattern[] {
            Pattern.compile("APPLICATION FAILED TO START", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Application run failed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ApplicationContextException", Pattern.CASE_INSENSITIVE),
            Pattern.compile("BeanCreationException", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UnsatisfiedDependencyException", Pattern.CASE_INSENSITIVE)
    };
    private final RuntimeTraceCollector collector;

    public RuntimeSmokeRunner() {
        this(new RuntimeTraceCollector());
    }

    RuntimeSmokeRunner(RuntimeTraceCollector collector) {
        this.collector = collector == null ? new RuntimeTraceCollector() : collector;
    }

    public SmokeCommand buildCommand(File originalJar, JarAnalysisResult analysis, File agentJar,
                                     Path traceFile, List<String> appArgs) {
        EntryPoint entryPoint = resolveEntryPoint(analysis);

        List<String> command = new ArrayList<>();
        command.add(resolveJavaExecutable());
        command.add("-Djar2mp.traceFile=" + absolute(traceFile));
        command.add("-javaagent:" + absolute(agentJar) + "=traceFile=" + absolute(traceFile));

        if (entryPoint.isJarLaunch()) {
            command.add("-jar");
            command.add(absolute(originalJar));
        } else {
            command.add("-cp");
            command.add(absolute(originalJar));
            command.add(entryPoint.getMainClass());
        }

        if (appArgs != null) {
            command.addAll(appArgs);
        }

        return new SmokeCommand(command, entryPoint.getMainClass(), entryPoint.getLaunchSource(),
                entryPoint.getNotes());
    }

    public SmokeRunResult runSmoke(File originalJar, JarAnalysisResult analysis, File agentJar,
                                   Path traceFile, List<String> appArgs) {
        return runSmoke(originalJar, analysis, agentJar, traceFile, appArgs, DEFAULT_TIMEOUT_SECONDS);
    }

    public SmokeRunResult runSmoke(File originalJar, JarAnalysisResult analysis, File agentJar,
                                   Path traceFile, List<String> appArgs, long timeoutSeconds) {
        SmokeRunResult result = new SmokeRunResult();
        long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        try {
            RuntimeLaunchPlan launchPlan = new RuntimeLaunchPlanner().plan(originalJar, analysis);
            applyLaunchPlan(result, launchPlan);
            result.setTraceFile(traceFile);
            if (!launchPlan.isSupported()) {
                result.setRunStatus("UNSUPPORTED_LAUNCH");
                result.setFailureMessage("Unsupported runtime launch: " + launchPlan.getReason());
                result.setTraceResult(collector.read(traceFile));
                return result;
            }

            SmokeCommand command = buildCommand(originalJar, analysis, agentJar, traceFile, appArgs);
            result.setCommand(joinCommand(command.getCommand()));
            result.setMainClass(command.getMainClass());
            result.setLaunchSource(command.getLaunchSource());
            result.getNotes().addAll(command.getNotes());

            if (originalJar == null || !originalJar.isFile()) {
                result.setRunStatus("MISSING_ORIGINAL_ARTIFACT");
                result.setFailureMessage("Original jar not found: " + describe(originalJar));
                result.setTraceResult(collector.read(traceFile));
                return result;
            }
            if (agentJar == null || !agentJar.isFile()) {
                result.setRunStatus("MISSING_TRACE_AGENT");
                result.setFailureMessage("Runtime trace agent jar not found: " + describe(agentJar));
                result.setTraceResult(collector.read(traceFile));
                return result;
            }
            if (command.getMainClass() == null || command.getMainClass().trim().isEmpty()) {
                result.setRunStatus("NO_ENTRYPOINT");
                result.setFailureMessage("No runnable entrypoint could be resolved from manifest or startup evidence.");
                result.setTraceResult(collector.read(traceFile));
                return result;
            }

            if (traceFile != null) {
                Path parent = traceFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.deleteIfExists(traceFile);
            }

            ProcessBuilder builder = new ProcessBuilder(command.getCommand());
            File workingDirectory = originalJar.getAbsoluteFile().getParentFile();
            if (workingDirectory != null) {
                builder.directory(workingDirectory);
            }

            Process process = null;
            StreamCollector stdout = null;
            StreamCollector stderr = null;
            boolean timedOut = false;
            try {
                process = builder.start();
                stdout = new StreamCollector(process.getInputStream());
                stderr = new StreamCollector(process.getErrorStream());
                stdout.start();
                stderr.start();

                boolean finished = waitForProcessOrTimeout(process, stdout, stderr, result, effectiveTimeout);
                if (!finished) {
                    timedOut = true;
                    process.destroyForcibly();
                    result.setFailureMessage("Smoke run timed out after " + effectiveTimeout + " seconds.");
                } else {
                    result.setExitCode(process.exitValue());
                    if (result.getExitCode() != 0) {
                        result.setRunStatus("EXIT_NON_ZERO");
                        result.setFailureMessage("Smoke run exited with code " + result.getExitCode() + ".");
                    } else {
                        result.setRunStatus("EXIT_ZERO");
                    }
                }

                if (stdout != null) {
                    stdout.join(1000L);
                }
                if (stderr != null) {
                    stderr.join(1000L);
                }
                result.setStdout(stdout == null ? "" : stdout.getContent());
                result.setStderr(stderr == null ? "" : stderr.getContent());
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }

            result.setTraceResult(collector.read(traceFile));
            if (timedOut) {
                int eventCount = result.getTraceResult().getEvents().size();
                if (eventCount > 0 && hasStartupFailureOutput(result)) {
                    result.setRunStatus(STARTUP_FAILED_TIMEOUT);
                    result.setFailureMessage("Runtime startup failure was detected before timeout.");
                } else if (eventCount > 0 && result.hasSuccessfulStartupProbe()) {
                    result.setRunStatus(TRACE_COLLECTED_HEALTHY_TIMEOUT);
                    result.setFailureMessage(null);
                    result.getNotes().add("Runtime process exceeded trace timeout after local HTTP startup was verified.");
                } else {
                    result.setRunStatus(eventCount > 0 ? TRACE_COLLECTED_TIMEOUT : "TIMEOUT_NO_EVENTS");
                }
            }
        } catch (RuntimeException e) {
            result.setRunStatus("ERROR");
            result.setFailureMessage(e.getMessage() == null ? e.toString() : e.getMessage());
            safeReadTrace(result, traceFile);
        } catch (IOException e) {
            result.setRunStatus("ERROR");
            result.setFailureMessage(e.getMessage() == null ? e.toString() : e.getMessage());
            safeReadTrace(result, traceFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setRunStatus("ERROR");
            result.setFailureMessage("Smoke run was interrupted.");
            safeReadTrace(result, traceFile);
        }

        return result;
    }

    private boolean hasStartupFailureOutput(SmokeRunResult result) {
        String output = safeValue(result == null ? null : result.getStdout()) + "\n"
                + safeValue(result == null ? null : result.getStderr());
        for (Pattern pattern : STARTUP_FAILURE_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean waitForProcessOrTimeout(Process process, StreamCollector stdout, StreamCollector stderr,
                                            SmokeRunResult result, long timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (process.waitFor(STARTUP_PROBE_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                pollStartupProbe(stdout, stderr, result);
                return true;
            }
            pollStartupProbe(stdout, stderr, result);
        }
        pollStartupProbe(stdout, stderr, result);
        return process.waitFor(0L, TimeUnit.MILLISECONDS);
    }

    private void pollStartupProbe(StreamCollector stdout, StreamCollector stderr, SmokeRunResult result) {
        if (result == null || result.hasSuccessfulStartupProbe()) {
            return;
        }
        Integer port = extractLocalHttpPort(stdout, stderr);
        if (port == null) {
            return;
        }
        String probeUrl = "http://127.0.0.1:" + port + "/";
        result.setStartupProbeUrl(probeUrl);
        StartupProbeResult probeResult = probeHttp(probeUrl);
        result.setStartupProbeStatus(probeResult.getStatus());
        result.setStartupProbeStatusCode(probeResult.getStatusCode());
    }

    private Integer extractLocalHttpPort(StreamCollector stdout, StreamCollector stderr) {
        String output = (stdout == null ? "" : stdout.getContent()) + "\n"
                + (stderr == null ? "" : stderr.getContent());
        for (Pattern pattern : LOCAL_HTTP_PORT_PATTERNS) {
            Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                try {
                    return Integer.valueOf(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private StartupProbeResult probeHttp(String probeUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(probeUrl).openConnection();
            connection.setConnectTimeout(STARTUP_PROBE_CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(STARTUP_PROBE_READ_TIMEOUT_MILLIS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            int statusCode = connection.getResponseCode();
            return new StartupProbeResult(statusCode > 0 ? HTTP_RESPONDED : "HTTP_NO_RESPONSE", statusCode);
        } catch (IOException e) {
            return new StartupProbeResult("HTTP_NO_RESPONSE", -1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applyLaunchPlan(SmokeRunResult result, RuntimeLaunchPlan launchPlan) {
        if (result == null || launchPlan == null) {
            return;
        }
        result.setLaunchType(launchPlan.getLaunchType().name());
        result.setLaunchSupport(launchPlan.getSupportStatus().name());
        result.setLaunchReason(launchPlan.getReason());
        result.setMainClass(launchPlan.getMainClass());
        result.setLaunchSource(launchPlan.getLaunchSource());
        result.getNotes().addAll(launchPlan.getNotes());
    }

    public RuntimeTraceResult collectTrace(Path traceFile) throws IOException {
        return collector.read(traceFile);
    }

    private void safeReadTrace(SmokeRunResult result, Path traceFile) {
        try {
            result.setTraceResult(collector.read(traceFile));
        } catch (IOException ignored) {
            result.setTraceResult(new RuntimeTraceResult());
        }
    }

    private EntryPoint resolveEntryPoint(JarAnalysisResult analysis) {
        ManifestInfo manifestInfo = analysis == null ? null : analysis.getManifestInfo();
        String startClass = null;
        String mainClass = null;
        if (manifestInfo != null) {
            startClass = trimToNull(manifestInfo.getAllEntries().get("Start-Class"));
            mainClass = trimToNull(manifestInfo.getMainClass());
        }

        String startupMainClass = firstStartupMainClass(analysis);
        List<String> notes = new ArrayList<>();
        if (startClass != null) {
            notes.add("Manifest launch metadata takes precedence over startup evidence.");
            if (startupMainClass != null && !startClass.equals(startupMainClass)) {
                notes.add("Startup evidence main class '" + startupMainClass + "' differs from manifest choice '" + startClass + "'.");
            }
            return new EntryPoint(startClass, "manifest Start-Class", true, notes);
        }
        if (mainClass != null) {
            notes.add("Manifest launch metadata takes precedence over startup evidence.");
            if (startupMainClass != null && !mainClass.equals(startupMainClass)) {
                notes.add("Startup evidence main class '" + startupMainClass + "' differs from manifest choice '" + mainClass + "'.");
            }
            return new EntryPoint(mainClass, "manifest Main-Class", true, notes);
        }
        if (startupMainClass != null) {
            notes.add("Manifest launch metadata was not runnable; using startup evidence fallback.");
            return new EntryPoint(startupMainClass, "startup evidence", false, notes);
        }

        notes.add("No runnable entrypoint could be resolved from manifest or startup evidence.");
        return new EntryPoint(null, null, false, notes);
    }

    private String firstStartupMainClass(JarAnalysisResult analysis) {
        if (analysis == null) {
            return null;
        }
        Collection<StartupFinding> findings = analysis.getStartupFindings();
        if (findings == null) {
            return null;
        }
        for (StartupFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            String mainClass = trimToNull(finding.getMainClass());
            if (mainClass != null) {
                return mainClass;
            }
        }
        return null;
    }

    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.trim().isEmpty()) {
            return "java";
        }
        File binJava = new File(new File(javaHome, "bin"), "java");
        return binJava.getAbsolutePath();
    }

    private String absolute(File file) {
        return file == null ? "" : file.getAbsoluteFile().getPath();
    }

    private String absolute(Path path) {
        return path == null ? "" : path.toAbsolutePath().toString();
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

    private String describe(File file) {
        return file == null ? "(null)" : file.getAbsolutePath();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private static final class EntryPoint {
        private final String mainClass;
        private final String launchSource;
        private final boolean jarLaunch;
        private final List<String> notes;

        private EntryPoint(String mainClass, String launchSource, boolean jarLaunch, List<String> notes) {
            this.mainClass = mainClass;
            this.launchSource = launchSource;
            this.jarLaunch = jarLaunch;
            this.notes = notes == null ? new ArrayList<String>() : new ArrayList<>(notes);
        }

        private String getMainClass() {
            return mainClass;
        }

        private String getLaunchSource() {
            return launchSource;
        }

        private boolean isJarLaunch() {
            return jarLaunch;
        }

        private List<String> getNotes() {
            return notes;
        }
    }

    public static class SmokeCommand {
        private final List<String> command;
        private final String mainClass;
        private final String launchSource;
        private final List<String> notes;

        private SmokeCommand(List<String> command, String mainClass, String launchSource, List<String> notes) {
            this.command = command == null ? new ArrayList<String>() : new ArrayList<>(command);
            this.mainClass = mainClass;
            this.launchSource = launchSource;
            this.notes = notes == null ? new ArrayList<String>() : new ArrayList<>(notes);
        }

        public List<String> getCommand() {
            return command;
        }

        public String getMainClass() {
            return mainClass;
        }

        public String getLaunchSource() {
            return launchSource;
        }

        public List<String> getNotes() {
            return notes;
        }
    }

    public static class SmokeRunResult {
        private String command;
        private int exitCode = -1;
        private String stdout = "";
        private String stderr = "";
        private String failureMessage;
        private String mainClass;
        private String launchSource;
        private String launchType;
        private String launchSupport;
        private String launchReason;
        private String runStatus = "NOT_RUN";
        private String startupProbeStatus = "NOT_RUN";
        private String startupProbeUrl;
        private int startupProbeStatusCode = -1;
        private Path traceFile;
        private RuntimeTraceResult traceResult = new RuntimeTraceResult();
        private final List<String> notes = new ArrayList<>();

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public void setStdout(String stdout) {
            this.stdout = stdout == null ? "" : stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public void setStderr(String stderr) {
            this.stderr = stderr == null ? "" : stderr;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public String getMainClass() {
            return mainClass;
        }

        public void setMainClass(String mainClass) {
            this.mainClass = mainClass;
        }

        public String getLaunchSource() {
            return launchSource;
        }

        public void setLaunchSource(String launchSource) {
            this.launchSource = launchSource;
        }

        public String getLaunchType() {
            return launchType;
        }

        public void setLaunchType(String launchType) {
            this.launchType = launchType;
        }

        public String getLaunchSupport() {
            return launchSupport;
        }

        public void setLaunchSupport(String launchSupport) {
            this.launchSupport = launchSupport;
        }

        public String getLaunchReason() {
            return launchReason;
        }

        public void setLaunchReason(String launchReason) {
            this.launchReason = launchReason;
        }

        public String getRunStatus() {
            return runStatus;
        }

        public void setRunStatus(String runStatus) {
            this.runStatus = runStatus;
        }

        public String getStartupProbeStatus() {
            return startupProbeStatus;
        }

        public void setStartupProbeStatus(String startupProbeStatus) {
            this.startupProbeStatus = startupProbeStatus == null ? "NOT_RUN" : startupProbeStatus;
        }

        public String getStartupProbeUrl() {
            return startupProbeUrl;
        }

        public void setStartupProbeUrl(String startupProbeUrl) {
            this.startupProbeUrl = startupProbeUrl;
        }

        public int getStartupProbeStatusCode() {
            return startupProbeStatusCode;
        }

        public void setStartupProbeStatusCode(int startupProbeStatusCode) {
            this.startupProbeStatusCode = startupProbeStatusCode;
        }

        public boolean hasSuccessfulStartupProbe() {
            return HTTP_RESPONDED.equalsIgnoreCase(startupProbeStatus) && startupProbeStatusCode > 0;
        }

        public Path getTraceFile() {
            return traceFile;
        }

        public void setTraceFile(Path traceFile) {
            this.traceFile = traceFile;
        }

        public RuntimeTraceResult getTraceResult() {
            return traceResult;
        }

        public void setTraceResult(RuntimeTraceResult traceResult) {
            this.traceResult = traceResult == null ? new RuntimeTraceResult() : traceResult;
        }

        public List<String> getNotes() {
            return notes;
        }

        public boolean isSuccessful() {
            return failureMessage == null && exitCode == 0;
        }
    }

    private static final class StreamCollector extends Thread {
        private static final int MAX_CAPTURE_BYTES = 20 * 1024;
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
                    synchronized (output) {
                        appendTail(buffer, read);
                    }
                }
            } catch (IOException ignored) {
                // Ignore stream capture failures; the caller only needs a best-effort snippet.
            }
        }

        private void appendTail(byte[] buffer, int read) {
            if (read >= MAX_CAPTURE_BYTES) {
                output.reset();
                output.write(buffer, read - MAX_CAPTURE_BYTES, MAX_CAPTURE_BYTES);
                return;
            }
            int overflow = output.size() + read - MAX_CAPTURE_BYTES;
            if (overflow > 0) {
                byte[] existing = output.toByteArray();
                output.reset();
                output.write(existing, overflow, existing.length - overflow);
            }
            output.write(buffer, 0, read);
        }

        private String getContent() {
            synchronized (output) {
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    private static final class StartupProbeResult {
        private final String status;
        private final int statusCode;

        private StartupProbeResult(String status, int statusCode) {
            this.status = status;
            this.statusCode = statusCode;
        }

        private String getStatus() {
            return status;
        }

        private int getStatusCode() {
            return statusCode;
        }
    }
}
