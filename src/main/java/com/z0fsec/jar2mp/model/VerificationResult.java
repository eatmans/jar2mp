package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class VerificationResult {
    private String command;
    private int exitCode;
    private String stdout;
    private String stderr;
    private String summary;
    private String failureType;
    private boolean timedOut;
    private final List<VerificationError> errors = new ArrayList<>();

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }
    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
    public List<VerificationError> getErrors() { return errors; }
}
