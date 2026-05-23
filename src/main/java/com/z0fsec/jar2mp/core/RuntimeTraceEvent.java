package com.z0fsec.jar2mp.core;

import java.util.ArrayList;
import java.util.List;

public class RuntimeTraceEvent {
    private String kind;
    private String owner;
    private String target;
    private String value;
    private String thread;
    private List<String> stack = new ArrayList<>();

    public RuntimeTraceEvent() {
    }

    public RuntimeTraceEvent(String kind, String owner, String target, String value, String thread, List<String> stack) {
        this.kind = kind;
        this.owner = owner;
        this.target = target;
        this.value = value;
        this.thread = thread;
        setStack(stack);
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getThread() { return thread; }
    public void setThread(String thread) { this.thread = thread; }
    public List<String> getStack() { return stack; }
    public void setStack(List<String> stack) {
        this.stack = stack == null ? new ArrayList<String>() : new ArrayList<>(stack);
    }
}
