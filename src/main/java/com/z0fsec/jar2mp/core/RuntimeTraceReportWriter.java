package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RuntimeTraceReportWriter {

    private static final int PROCESS_OUTPUT_SNIPPET_LIMIT = 8000;

    public void write(File outputDir, RuntimeSmokeRunner.SmokeRunResult result) throws IOException {
        StringBuilder report = new StringBuilder();
        RuntimeTraceResult traceResult = result == null || result.getTraceResult() == null
                ? new RuntimeTraceResult()
                : result.getTraceResult();
        List<RuntimeTraceEvent> events = traceResult.getEvents();

        report.append("# Runtime trace report\n\n");
        report.append("## Run summary\n\n");
        report.append("- Command: ").append(formatInline(result == null ? null : result.getCommand())).append("\n");
        report.append("- Exit code: ").append(result == null ? -1 : result.getExitCode()).append("\n");
        report.append("- Run status: ").append(formatInline(result == null ? null : result.getRunStatus())).append("\n");
        report.append("- Main class: ").append(formatInline(result == null ? null : result.getMainClass())).append("\n");
        report.append("- Launch source: ").append(formatInline(result == null ? null : result.getLaunchSource())).append("\n");
        report.append("- Launch type: ").append(formatInline(result == null ? null : result.getLaunchType())).append("\n");
        report.append("- Launch support: ").append(formatInline(result == null ? null : result.getLaunchSupport())).append("\n");
        report.append("- Launch reason: ").append(formatInline(result == null ? null : result.getLaunchReason())).append("\n");
        report.append("- Startup probe status: ")
                .append(formatInline(result == null ? null : result.getStartupProbeStatus())).append("\n");
        report.append("- Startup probe URL: ")
                .append(formatInline(result == null ? null : result.getStartupProbeUrl())).append("\n");
        report.append("- Startup probe status code: ")
                .append(result == null ? -1 : result.getStartupProbeStatusCode()).append("\n");
        report.append("- Trace file: ").append(formatInline(result == null ? null : pathValue(result.getTraceFile()))).append("\n");
        report.append("- Total events: ").append(events.size()).append("\n");
        report.append("- Reflection events: ").append(count(events, "reflection")).append("\n");
        report.append("- Resource events: ").append(count(events, "resource")).append("\n");
        report.append("- File events: ").append(count(events, "file")).append("\n");
        report.append("- Socket events: ").append(count(events, "socket")).append("\n");

        appendProcessOutput(report, result);
        appendNotes(report, result == null ? null : result.getNotes());
        appendKindSection(report, "Reflection", "reflection", events);
        appendKindSection(report, "Resource", "resource", events);
        appendKindSection(report, "File", "file", events);
        appendKindSection(report, "Socket", "socket", events);

        IoUtils.writeStringToFile(new File(outputDir, "runtime-trace-report.md"), report.toString());
    }

    private void appendProcessOutput(StringBuilder report, RuntimeSmokeRunner.SmokeRunResult result) {
        appendOutputTail(report, "stdout", result == null ? null : result.getStdout());
        appendOutputTail(report, "stderr", result == null ? null : result.getStderr());
    }

    private void appendOutputTail(StringBuilder report, String streamName, String content) {
        report.append("\n## Process ").append(streamName).append(" tail\n\n");
        if (content == null || content.trim().isEmpty()) {
            report.append("- None captured.\n");
            return;
        }
        report.append("```text\n")
                .append(sanitizeBlock(tail(content)))
                .append("\n```\n");
    }

    private String tail(String value) {
        if (value == null || value.length() <= PROCESS_OUTPUT_SNIPPET_LIMIT) {
            return value;
        }
        return "[truncated to last " + PROCESS_OUTPUT_SNIPPET_LIMIT + " chars]\n"
                + value.substring(value.length() - PROCESS_OUTPUT_SNIPPET_LIMIT);
    }

    private void appendNotes(StringBuilder report, List<String> notes) {
        report.append("\n## Notes\n\n");
        if (notes == null || notes.isEmpty()) {
            report.append("- None\n");
            return;
        }
        for (String note : notes) {
            report.append("- ").append(sanitize(note)).append("\n");
        }
    }

    private void appendKindSection(StringBuilder report, String title, String kind, List<RuntimeTraceEvent> events) {
        report.append("\n## ").append(title).append("\n\n");
        List<RuntimeTraceEvent> filtered = filter(events, kind);
        report.append("- Count: ").append(filtered.size()).append("\n");
        if (filtered.isEmpty()) {
            report.append("- None recorded.\n");
            return;
        }
        int limit = Math.min(filtered.size(), 3);
        for (int i = 0; i < limit; i++) {
            RuntimeTraceEvent event = filtered.get(i);
            report.append("- Example ").append(i + 1).append(": ")
                    .append(sanitize(event.getOwner())).append(".")
                    .append(sanitize(event.getTarget())).append(" -> ")
                    .append(formatInline(event.getValue())).append(" (thread=")
                    .append(formatInline(event.getThread())).append(")");
            if (event.getStack() != null && !event.getStack().isEmpty()) {
                report.append(" | stack: ").append(formatStack(event.getStack()));
            }
            report.append("\n");
        }
    }

    private List<RuntimeTraceEvent> filter(List<RuntimeTraceEvent> events, String kind) {
        List<RuntimeTraceEvent> filtered = new ArrayList<>();
        if (events == null) {
            return filtered;
        }
        for (RuntimeTraceEvent event : events) {
            if (event == null || event.getKind() == null) {
                continue;
            }
            if (kind.equalsIgnoreCase(event.getKind())) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private int count(List<RuntimeTraceEvent> events, String kind) {
        return filter(events, kind).size();
    }

    private String formatStack(List<String> stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(stack.size(), 2);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(" > ");
            }
            builder.append(sanitize(stack.get(i)));
        }
        return builder.toString();
    }

    private String formatInline(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "(none)";
        }
        return "`" + sanitize(value) + "`";
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    private String sanitizeBlock(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replace("```", "` ` `");
    }

    private String pathValue(java.nio.file.Path path) {
        return path == null ? null : path.toAbsolutePath().toString();
    }
}
