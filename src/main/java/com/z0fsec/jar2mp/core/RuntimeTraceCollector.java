package com.z0fsec.jar2mp.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RuntimeTraceCollector {
    private final Gson gson = new GsonBuilder().create();

    public RuntimeTraceResult read(Path traceFile) throws IOException {
        List<RuntimeTraceEvent> events = new ArrayList<>();
        if (traceFile == null || !Files.exists(traceFile)) {
            return new RuntimeTraceResult(events);
        }

        try (BufferedReader reader = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    RuntimeTraceEvent event = gson.fromJson(trimmed, RuntimeTraceEvent.class);
                    if (event != null) {
                        normalize(event);
                        events.add(event);
                    }
                } catch (JsonSyntaxException e) {
                    throw new IOException("Invalid trace event JSON at line " + lineNumber, e);
                }
            }
        }
        return new RuntimeTraceResult(events);
    }

    private void normalize(RuntimeTraceEvent event) {
        if (event.getKind() == null) {
            event.setKind("");
        }
        if (event.getOwner() == null) {
            event.setOwner("");
        }
        if (event.getTarget() == null) {
            event.setTarget("");
        }
        if (event.getValue() == null) {
            event.setValue("");
        }
        if (event.getThread() == null) {
            event.setThread("");
        }
        if (event.getStack() == null) {
            event.setStack(new ArrayList<String>());
        }
    }
}
