package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class SourceRebuildFidelityResult {
    private static final int SAMPLE_LIMIT = 5;

    private int originalAppClasses;
    private int recompiledClasses;
    private int commonClasses;
    private int sameClassBytes;
    private int differentClassBytes;
    private int missingRecompiledClasses;
    private int extraRecompiledClasses;
    private int compileFallbackClasses;
    private final List<String> sampleDifferentClasses = new ArrayList<>();
    private final List<String> sampleMissingRecompiledClasses = new ArrayList<>();
    private final List<String> sampleExtraRecompiledClasses = new ArrayList<>();

    public boolean isSourceRecompiledClassBytesSame() {
        return originalAppClasses == recompiledClasses
                && commonClasses == originalAppClasses
                && sameClassBytes == originalAppClasses
                && differentClassBytes == 0
                && missingRecompiledClasses == 0
                && extraRecompiledClasses == 0
                && compileFallbackClasses == 0;
    }

    public int getOriginalAppClasses() {
        return originalAppClasses;
    }

    public void setOriginalAppClasses(int originalAppClasses) {
        this.originalAppClasses = originalAppClasses;
    }

    public int getRecompiledClasses() {
        return recompiledClasses;
    }

    public void setRecompiledClasses(int recompiledClasses) {
        this.recompiledClasses = recompiledClasses;
    }

    public int getCommonClasses() {
        return commonClasses;
    }

    public void setCommonClasses(int commonClasses) {
        this.commonClasses = commonClasses;
    }

    public int getSameClassBytes() {
        return sameClassBytes;
    }

    public void setSameClassBytes(int sameClassBytes) {
        this.sameClassBytes = sameClassBytes;
    }

    public int getDifferentClassBytes() {
        return differentClassBytes;
    }

    public void setDifferentClassBytes(int differentClassBytes) {
        this.differentClassBytes = differentClassBytes;
    }

    public int getMissingRecompiledClasses() {
        return missingRecompiledClasses;
    }

    public void setMissingRecompiledClasses(int missingRecompiledClasses) {
        this.missingRecompiledClasses = missingRecompiledClasses;
    }

    public int getExtraRecompiledClasses() {
        return extraRecompiledClasses;
    }

    public void setExtraRecompiledClasses(int extraRecompiledClasses) {
        this.extraRecompiledClasses = extraRecompiledClasses;
    }

    public int getCompileFallbackClasses() {
        return compileFallbackClasses;
    }

    public void setCompileFallbackClasses(int compileFallbackClasses) {
        this.compileFallbackClasses = compileFallbackClasses;
    }

    public List<String> getSampleDifferentClasses() {
        return sampleDifferentClasses;
    }

    public List<String> getSampleMissingRecompiledClasses() {
        return sampleMissingRecompiledClasses;
    }

    public List<String> getSampleExtraRecompiledClasses() {
        return sampleExtraRecompiledClasses;
    }

    public void recordDifferentClass(String classPath) {
        addSample(sampleDifferentClasses, classPath);
    }

    public void recordMissingRecompiledClass(String classPath) {
        addSample(sampleMissingRecompiledClasses, classPath);
    }

    public void recordExtraRecompiledClass(String classPath) {
        addSample(sampleExtraRecompiledClasses, classPath);
    }

    private void addSample(List<String> samples, String classPath) {
        if (classPath != null && samples.size() < SAMPLE_LIMIT && !samples.contains(classPath)) {
            samples.add(classPath);
        }
    }
}
