package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class RuntimeLaunchPlan {

    public enum LaunchType {
        EXECUTABLE_JAR,
        EXECUTABLE_WAR,
        THIN_JAR,
        STANDARD_WAR,
        LIBRARY,
        UNKNOWN
    }

    public enum SupportStatus {
        SUPPORTED,
        UNSUPPORTED
    }

    private LaunchType launchType = LaunchType.UNKNOWN;
    private SupportStatus supportStatus = SupportStatus.UNSUPPORTED;
    private String mainClass;
    private String launchSource;
    private String reason;
    private final List<String> notes = new ArrayList<>();

    public RuntimeLaunchPlan() {
    }

    public RuntimeLaunchPlan(LaunchType launchType, SupportStatus supportStatus,
                             String mainClass, String launchSource, String reason) {
        this.launchType = launchType == null ? LaunchType.UNKNOWN : launchType;
        this.supportStatus = supportStatus == null ? SupportStatus.UNSUPPORTED : supportStatus;
        this.mainClass = mainClass;
        this.launchSource = launchSource;
        this.reason = reason;
    }

    public LaunchType getLaunchType() { return launchType; }
    public void setLaunchType(LaunchType launchType) {
        this.launchType = launchType == null ? LaunchType.UNKNOWN : launchType;
    }
    public SupportStatus getSupportStatus() { return supportStatus; }
    public void setSupportStatus(SupportStatus supportStatus) {
        this.supportStatus = supportStatus == null ? SupportStatus.UNSUPPORTED : supportStatus;
    }
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    public String getLaunchSource() { return launchSource; }
    public void setLaunchSource(String launchSource) { this.launchSource = launchSource; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getNotes() { return notes; }

    public boolean isSupported() {
        return supportStatus == SupportStatus.SUPPORTED;
    }
}
