package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import com.z0fsec.jar2mp.model.SourceRebuildFidelityResult;
import com.z0fsec.jar2mp.model.VerificationResult;

import java.io.File;

public class PostBuildResult {
    private File preservedRawArtifact;
    private ArtifactFidelityResult rawArtifactFidelity;
    private RuntimeSmokeRunner.SmokeRunResult smokeRunResult;
    private VerificationResult verificationResult;
    private SourceRebuildFidelityResult sourceRebuildFidelity;
    private ArtifactFidelityResult packageFidelity;
    private String blockingFailure;
    private int backfilledClassCount;

    public File getPreservedRawArtifact() { return preservedRawArtifact; }
    public ArtifactFidelityResult getRawArtifactFidelity() { return rawArtifactFidelity; }
    public RuntimeSmokeRunner.SmokeRunResult getSmokeRunResult() { return smokeRunResult; }
    public VerificationResult getVerificationResult() { return verificationResult; }
    public SourceRebuildFidelityResult getSourceRebuildFidelity() { return sourceRebuildFidelity; }
    public ArtifactFidelityResult getPackageFidelity() { return packageFidelity; }
    public boolean hasBlockingFailure() { return blockingFailure != null; }
    public String getBlockingFailure() { return blockingFailure; }
    public int getBackfilledClassCount() { return backfilledClassCount; }

    void setPreservedRawArtifact(File preservedRawArtifact) { this.preservedRawArtifact = preservedRawArtifact; }
    void setRawArtifactFidelity(ArtifactFidelityResult rawArtifactFidelity) { this.rawArtifactFidelity = rawArtifactFidelity; }
    void setSmokeRunResult(RuntimeSmokeRunner.SmokeRunResult smokeRunResult) { this.smokeRunResult = smokeRunResult; }
    void setVerificationResult(VerificationResult verificationResult) { this.verificationResult = verificationResult; }
    void setSourceRebuildFidelity(SourceRebuildFidelityResult sourceRebuildFidelity) { this.sourceRebuildFidelity = sourceRebuildFidelity; }
    void setPackageFidelity(ArtifactFidelityResult packageFidelity) { this.packageFidelity = packageFidelity; }
    void setBlockingFailure(String blockingFailure) { this.blockingFailure = blockingFailure; }
    void setBackfilledClassCount(int backfilledClassCount) { this.backfilledClassCount = backfilledClassCount; }
}
