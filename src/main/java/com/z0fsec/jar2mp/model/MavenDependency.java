package com.z0fsec.jar2mp.model;

public class MavenDependency extends MavenCoordinates {

    public enum Confidence {
        HIGH("Embedded POM"),
        MEDIUM("MANIFEST.MF"),
        LOW("Class Scan"),
        GUESS("Filename"),
        MANUAL("Manual");

        private final String label;
        Confidence(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private Confidence confidence;
    private String scope;
    private String type;
    private boolean included = true;

    public MavenDependency() {
        super();
    }

    public MavenDependency(String groupId, String artifactId, String version, Confidence confidence) {
        super(groupId, artifactId, version);
        this.confidence = confidence;
        this.scope = "compile";
    }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isIncluded() { return included; }
    public void setIncluded(boolean included) { this.included = included; }

    @Override
    public String toString() {
        return super.toString() + " [" + (confidence != null ? confidence.getLabel() : "?") + "]";
    }
}
