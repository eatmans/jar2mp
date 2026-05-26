package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

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
    private boolean verifyBuild = false;
    private String verifyGoal = "compile";
    private String customMappingFile;
    private String packaging;
    private String traceFile;
    private boolean traceRuntime = false;
    private final List<String> traceArgs = new ArrayList<>();
    private long traceTimeoutSeconds = 120L;
    private boolean smokeOnly = false;
    private boolean emitRawArtifact = false;

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
    public boolean isVerifyBuild() { return verifyBuild; }
    public void setVerifyBuild(boolean verifyBuild) { this.verifyBuild = verifyBuild; }
    public String getVerifyGoal() { return verifyGoal; }
    public void setVerifyGoal(String verifyGoal) { this.verifyGoal = verifyGoal; }
    public String getCustomMappingFile() { return customMappingFile; }
    public void setCustomMappingFile(String customMappingFile) { this.customMappingFile = customMappingFile; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
    public String getTraceFile() { return traceFile; }
    public void setTraceFile(String traceFile) { this.traceFile = traceFile; }
    public boolean isTraceRuntime() { return traceRuntime; }
    public void setTraceRuntime(boolean traceRuntime) { this.traceRuntime = traceRuntime; }
    public List<String> getTraceArgs() { return traceArgs; }
    public void setTraceArgs(List<String> traceArgs) {
        this.traceArgs.clear();
        if (traceArgs != null) {
            this.traceArgs.addAll(traceArgs);
        }
    }
    public long getTraceTimeoutSeconds() { return traceTimeoutSeconds; }
    public void setTraceTimeoutSeconds(long traceTimeoutSeconds) {
        if (traceTimeoutSeconds > 0) {
            this.traceTimeoutSeconds = traceTimeoutSeconds;
        }
    }
    public boolean isSmokeOnly() { return smokeOnly; }
    public void setSmokeOnly(boolean smokeOnly) { this.smokeOnly = smokeOnly; }
    public boolean isEmitRawArtifact() { return emitRawArtifact; }
    public void setEmitRawArtifact(boolean emitRawArtifact) { this.emitRawArtifact = emitRawArtifact; }
}
