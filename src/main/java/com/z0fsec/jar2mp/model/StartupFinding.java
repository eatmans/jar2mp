package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class StartupFinding {
    private String applicationType;
    private String mainClass;
    private final List<String> commands = new ArrayList<>();
    private final List<String> evidence = new ArrayList<>();
    private final List<String> knownGaps = new ArrayList<>();

    public StartupFinding() {
    }

    public StartupFinding(String applicationType, String mainClass) {
        this.applicationType = applicationType;
        this.mainClass = mainClass;
    }

    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    public List<String> getCommands() { return commands; }
    public List<String> getEvidence() { return evidence; }
    public List<String> getKnownGaps() { return knownGaps; }
}
