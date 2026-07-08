package com.z0fsec.jar2mp.core;

import com.google.gson.stream.JsonWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class RuntimeTraceWriter {

    public void write(Path traceFile, RuntimeTraceResult result) throws IOException {
        if (traceFile == null) {
            return;
        }
        Path parent = traceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<RuntimeTraceEvent> events = result == null ? null : result.getEvents();
        try (BufferedWriter writer = Files.newBufferedWriter(traceFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            if (events == null) {
                return;
            }
            for (RuntimeTraceEvent event : events) {
                if (event == null) {
                    continue;
                }
                writer.write(toJsonLine(event));
                writer.newLine();
            }
        }
    }

    public void append(Path traceFile, RuntimeTraceEvent event) throws IOException {
        if (traceFile == null || event == null) {
            return;
        }
        Path parent = traceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(traceFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writer.write(toJsonLine(event));
            writer.newLine();
        }
    }

    public String toJsonLine(RuntimeTraceEvent event) throws IOException {
        StringWriter out = new StringWriter();
        writeEvent(out, event);
        return out.toString();
    }

    private void writeEvent(Writer out, RuntimeTraceEvent event) throws IOException {
        JsonWriter writer = new JsonWriter(out);
        writer.setSerializeNulls(false);
        writer.beginObject();
        writer.name("kind").value(safe(event.getKind()));
        writer.name("owner").value(safe(event.getOwner()));
        writer.name("target").value(safe(event.getTarget()));
        writer.name("value").value(safe(event.getValue()));
        writer.name("thread").value(safe(event.getThread()));
        writer.name("stack");
        writer.beginArray();
        if (event.getStack() != null) {
            for (String frame : event.getStack()) {
                writer.value(safe(frame));
            }
        }
        writer.endArray();
        writer.endObject();
        writer.flush();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
