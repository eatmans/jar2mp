package com.z0fsec.jar2mp.model;

public class DecompileFinding {
    private String classPath;
    private String retainedClassPath;
    private String message;
    private String selectedEngine;
    private String fallbackReason;

    public DecompileFinding() {
    }

    public DecompileFinding(String classPath, String retainedClassPath, String message) {
        this.classPath = classPath;
        this.retainedClassPath = retainedClassPath;
        this.message = message;
    }

    public String getClassPath() { return classPath; }
    public void setClassPath(String classPath) { this.classPath = classPath; }
    public String getRetainedClassPath() { return retainedClassPath; }
    public void setRetainedClassPath(String retainedClassPath) { this.retainedClassPath = retainedClassPath; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSelectedEngine() { return selectedEngine; }
    public void setSelectedEngine(String selectedEngine) { this.selectedEngine = selectedEngine; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public boolean hasRetainedClassPath() {
        return retainedClassPath != null && !retainedClassPath.trim().isEmpty();
    }
}
