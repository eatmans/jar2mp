package com.z0fsec.jar2mp.model;

public class ProjectConfig {
    private String outputDir;
    private String groupId;
    private String artifactId;
    private String version;
    private int javaVersion;
    private boolean decompile = true;
    private boolean detectDependencies = true;
    private boolean copyResources = true;
    private boolean createTestDirs = true;
    private boolean includeSynthetic = false;
    private boolean aggressiveScan = false;
    private boolean forceOverwrite = false;
    private String customMappingFile;
    private String packaging;

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public int getJavaVersion() { return javaVersion; }
    public void setJavaVersion(int javaVersion) { this.javaVersion = javaVersion; }
    public boolean isDecompile() { return decompile; }
    public void setDecompile(boolean decompile) { this.decompile = decompile; }
    public boolean isDetectDependencies() { return detectDependencies; }
    public void setDetectDependencies(boolean detectDependencies) { this.detectDependencies = detectDependencies; }
    public boolean isCopyResources() { return copyResources; }
    public void setCopyResources(boolean copyResources) { this.copyResources = copyResources; }
    public boolean isCreateTestDirs() { return createTestDirs; }
    public void setCreateTestDirs(boolean createTestDirs) { this.createTestDirs = createTestDirs; }
    public boolean isIncludeSynthetic() { return includeSynthetic; }
    public void setIncludeSynthetic(boolean includeSynthetic) { this.includeSynthetic = includeSynthetic; }
    public boolean isAggressiveScan() { return aggressiveScan; }
    public void setAggressiveScan(boolean aggressiveScan) { this.aggressiveScan = aggressiveScan; }
    public boolean isForceOverwrite() { return forceOverwrite; }
    public void setForceOverwrite(boolean forceOverwrite) { this.forceOverwrite = forceOverwrite; }
    public String getCustomMappingFile() { return customMappingFile; }
    public void setCustomMappingFile(String customMappingFile) { this.customMappingFile = customMappingFile; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
}
