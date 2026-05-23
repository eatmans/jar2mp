package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class FrameworkFinding {
    private String name;
    private int confidence;
    private final List<String> evidence = new ArrayList<>();
    private final List<String> recommendedActions = new ArrayList<>();

    public FrameworkFinding() {
    }

    public FrameworkFinding(String name, int confidence) {
        this.name = name;
        this.confidence = confidence;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public List<String> getEvidence() { return evidence; }
    public List<String> getRecommendedActions() { return recommendedActions; }
}
