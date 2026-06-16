package com.z0fsec.jar2mp.model;

import com.z0fsec.jar2mp.core.BytecodeFingerprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JarAnalysisResult {
    private java.io.File sourceFile;
    private ManifestInfo manifestInfo;
    private PomInfo embeddedPomInfo;
    private final List<PomInfo> embeddedPomInfos = new ArrayList<>();
    private final List<String> classFiles = new ArrayList<>();
    private final List<String> skippedDependencyClassFiles = new ArrayList<>();
    private final Map<String, String> skippedDependencyClassReasons = new LinkedHashMap<>();
    private final Set<String> embeddedDependencyPrefixes = new LinkedHashSet<>();
    private final List<String> resourceFiles = new ArrayList<>();
    private final List<String> metaInfFiles = new ArrayList<>();
    private final List<MavenDependency> detectedDependencies = new ArrayList<>();
    private final List<FrameworkFinding> frameworkFindings = new ArrayList<>();
    private final List<ResourceFinding> resourceFindings = new ArrayList<>();
    private final List<StartupFinding> startupFindings = new ArrayList<>();
    private final List<DecompileFinding> decompileFindings = new ArrayList<>();
    private final List<String> metadataWarnings = new ArrayList<>();
    private final Map<String, BytecodeFingerprint> classBytecodeFingerprints = new LinkedHashMap<>();
    private com.z0fsec.jar2mp.core.RuntimeTraceResult runtimeTraceResult;
    private com.z0fsec.jar2mp.core.RuntimeSmokeRunner.SmokeRunResult runtimeSmokeResult;
    private VerificationResult verificationResult;
    private SourceRebuildFidelityResult sourceRebuildFidelity;
    private ArtifactFidelityResult packageFidelity;
    private RestorationScore restorationScore;
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
    public List<PomInfo> getEmbeddedPomInfos() { return embeddedPomInfos; }
    public List<String> getClassFiles() { return classFiles; }
    public List<String> getSkippedDependencyClassFiles() { return skippedDependencyClassFiles; }
    public Map<String, String> getSkippedDependencyClassReasons() { return skippedDependencyClassReasons; }
    public Set<String> getEmbeddedDependencyPrefixes() { return embeddedDependencyPrefixes; }
    public List<String> getResourceFiles() { return resourceFiles; }
    public List<String> getMetaInfFiles() { return metaInfFiles; }
    public Map<String, String> getClassPathMapping() { return classPathMapping; }
    public List<MavenDependency> getDetectedDependencies() { return detectedDependencies; }
    public List<FrameworkFinding> getFrameworkFindings() { return frameworkFindings; }
    public List<ResourceFinding> getResourceFindings() { return resourceFindings; }
    public List<StartupFinding> getStartupFindings() { return startupFindings; }
    public List<DecompileFinding> getDecompileFindings() { return decompileFindings; }
    public List<String> getMetadataWarnings() { return metadataWarnings; }
    public Map<String, BytecodeFingerprint> getClassBytecodeFingerprints() { return classBytecodeFingerprints; }
    public com.z0fsec.jar2mp.core.RuntimeTraceResult getRuntimeTraceResult() { return runtimeTraceResult; }
    public void setRuntimeTraceResult(com.z0fsec.jar2mp.core.RuntimeTraceResult runtimeTraceResult) {
        this.runtimeTraceResult = runtimeTraceResult;
    }
    public com.z0fsec.jar2mp.core.RuntimeSmokeRunner.SmokeRunResult getRuntimeSmokeResult() { return runtimeSmokeResult; }
    public void setRuntimeSmokeResult(com.z0fsec.jar2mp.core.RuntimeSmokeRunner.SmokeRunResult runtimeSmokeResult) {
        this.runtimeSmokeResult = runtimeSmokeResult;
    }
    public VerificationResult getVerificationResult() { return verificationResult; }
    public void setVerificationResult(VerificationResult verificationResult) {
        this.verificationResult = verificationResult;
    }
    public SourceRebuildFidelityResult getSourceRebuildFidelity() { return sourceRebuildFidelity; }
    public void setSourceRebuildFidelity(SourceRebuildFidelityResult sourceRebuildFidelity) {
        this.sourceRebuildFidelity = sourceRebuildFidelity;
    }
    public ArtifactFidelityResult getPackageFidelity() { return packageFidelity; }
    public void setPackageFidelity(ArtifactFidelityResult packageFidelity) {
        this.packageFidelity = packageFidelity;
    }
    public RestorationScore getRestorationScore() { return restorationScore; }
    public void setRestorationScore(RestorationScore restorationScore) {
        this.restorationScore = restorationScore;
    }
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

    public boolean isEmbeddedDependencyPath(String path) {
        if (path == null || embeddedDependencyPrefixes.isEmpty()) {
            return false;
        }
        String normalized = normalizeVersionedPath(stripClassContainerPrefix(path.replace('\\', '/')));
        for (String prefix : embeddedDependencyPrefixes) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String stripClassContainerPrefix(String path) {
        if (path.startsWith("BOOT-INF/classes/")) {
            return path.substring("BOOT-INF/classes/".length());
        }
        if (path.startsWith("WEB-INF/classes/")) {
            return path.substring("WEB-INF/classes/".length());
        }
        return path;
    }

    private String normalizeVersionedPath(String path) {
        String prefix = "META-INF/versions/";
        if (!path.startsWith(prefix)) {
            return path;
        }
        int start = prefix.length();
        int slash = path.indexOf('/', start);
        if (slash < 0) {
            return path;
        }
        return path.substring(slash + 1);
    }
}
