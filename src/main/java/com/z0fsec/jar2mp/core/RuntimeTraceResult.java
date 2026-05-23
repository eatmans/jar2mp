package com.z0fsec.jar2mp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RuntimeTraceResult {
    private final List<RuntimeTraceEvent> events = new ArrayList<>();

    public RuntimeTraceResult() {
    }

    public RuntimeTraceResult(Collection<RuntimeTraceEvent> events) {
        setEvents(events);
    }

    public List<RuntimeTraceEvent> getEvents() {
        return events;
    }

    public void setEvents(Collection<RuntimeTraceEvent> events) {
        this.events.clear();
        if (events == null) {
            return;
        }
        this.events.addAll(events);
    }

    public boolean hasReflectionCalls() {
        for (RuntimeTraceEvent event : events) {
            if (event != null && "reflection".equalsIgnoreCase(event.getKind())) {
                return true;
            }
        }
        return false;
    }
}
