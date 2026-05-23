package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JarAnalysisResult {
    private java.io.File sourceFile;
    private ManifestInfo manifestInfo;
    private PomInfo embeddedPomInfo;
    private final List<String> classFiles = new ArrayList<>();
    private final List<String> resourceFiles = new ArrayList<>();
    private final List<String> metaInfFiles = new ArrayList<>();
    private final List<MavenDependency> detectedDependencies = new ArrayList<>();
    private final List<FrameworkFinding> frameworkFindings = new ArrayList<>();
    private final List<ResourceFinding> resourceFindings = new ArrayList<>();
    /** Maps stripped class path -> original entry path in JAR (for BOOT-INF/classes/, WEB-INF/classes/) */
    private final Map<String, String> classPathMapping = new LinkedHashMap<>();
    private String detectedGroupId;
    private String detectedArtifactId;
    private String detectedVersion;
    private int javaVersion = 8;
    private boolean isWar = false;
    private long totalSize;
    private int totalEntries;

    public void setSourceFile(java.io.File sourceFile) { this.sourceFile = sourceFile; }
    public java.io.File getSourceFile() { return sourceFile; }
    public ManifestInfo getManifestInfo() { return manifestInfo; }
    public void setManifestInfo(ManifestInfo manifestInfo) { this.manifestInfo = manifestInfo; }
    public PomInfo getEmbeddedPomInfo() { return embeddedPomInfo; }
    public void setEmbeddedPomInfo(PomInfo embeddedPomInfo) { this.embeddedPomInfo = embeddedPomInfo; }
    public List<String> getClassFiles() { return classFiles; }
    public List<String> getResourceFiles() { return resourceFiles; }
    public List<String> getMetaInfFiles() { return metaInfFiles; }
    public Map<String, String> getClassPathMapping() { return classPathMapping; }
    public List<MavenDependency> getDetectedDependencies() { return detectedDependencies; }
    public List<FrameworkFinding> getFrameworkFindings() { return frameworkFindings; }
    public List<ResourceFinding> getResourceFindings() { return resourceFindings; }
    public String getDetectedGroupId() { return detectedGroupId; }
    public void setDetectedGroupId(String detectedGroupId) { this.detectedGroupId = detectedGroupId; }
    public String getDetectedArtifactId() { return detectedArtifactId; }
    public void setDetectedArtifactId(String detectedArtifactId) { this.detectedArtifactId = detectedArtifactId; }
    public String getDetectedVersion() { return detectedVersion; }
    public void setDetectedVersion(String detectedVersion) { this.detectedVersion = detectedVersion; }
    public int getJavaVersion() { return javaVersion; }
    public void setJavaVersion(int javaVersion) { this.javaVersion = javaVersion; }
    public boolean isWar() { return isWar; }
    public void setWar(boolean war) { isWar = war; }
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public int getTotalEntries() { return totalEntries; }
    public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }
}
