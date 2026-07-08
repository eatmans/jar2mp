package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ArtifactFidelityResult {
    private static final int SAMPLE_LIMIT = 5;

    public enum DifferenceBucket {
        MANIFEST,
        CLASS_BYTECODE,
        NESTED_LIBRARY,
        MAVEN_METADATA,
        SERVICE_METADATA,
        BOOT_INDEX,
        SIGNATURE_METADATA,
        RESOURCE_ENTRY
    }

    private int originalEntryTotal;
    private int rebuiltEntryTotal;
    private int originalClassEntries;
    private int rebuiltClassEntries;
    private int originalResourceEntries;
    private int rebuiltResourceEntries;
    private int originalNestedLibs;
    private int rebuiltNestedLibs;
    private int commonEntries;
    private int sameSha256;
    private int differentSha256;
    private int missingEntries;
    private int extraEntries;
    private int commonClassEntries;
    private int sameClassBytes;
    private int differentClassBytes;
    private int commonNestedLibs;
    private int sameNestedLibs;
    private int differentNestedLibs;
    private int missingNestedLibs;
    private int extraNestedLibs;
    private boolean manifestOriginalPresent;
    private boolean manifestRebuiltPresent;
    private boolean manifestSame;
    private boolean archiveBytesSame;
    private boolean archiveEntryOrderSame;
    private int archiveMetadataDiffEntries;
    private int archiveTimestampDifferences;
    private int archiveCompressionMethodDifferences;
    private int archiveCompressedSizeDifferences;
    private int archiveExtraFieldDifferences;
    private int archiveCommentDifferences;
    private String originalArchiveSha256;
    private String rebuiltArchiveSha256;
    private final List<String> sampleMissingEntries = new ArrayList<>();
    private final List<String> sampleExtraEntries = new ArrayList<>();
    private final List<String> sampleDifferentEntries = new ArrayList<>();
    private final List<String> sampleArchiveMetadataDifferences = new ArrayList<>();
    private final Map<DifferenceBucket, DifferenceBucketSummary> bucketSummaries =
            new EnumMap<>(DifferenceBucket.class);

    public ArtifactFidelityResult() {
        for (DifferenceBucket bucket : DifferenceBucket.values()) {
            bucketSummaries.put(bucket, new DifferenceBucketSummary(bucket));
        }
    }

    public boolean isExactMatch() {
        return archiveBytesSame;
    }

    public boolean isContentEntriesMatch() {
        return differentSha256 == 0 && missingEntries == 0 && extraEntries == 0;
    }

    public int getOriginalEntryTotal() { return originalEntryTotal; }
    public void setOriginalEntryTotal(int originalEntryTotal) { this.originalEntryTotal = originalEntryTotal; }
    public int getRebuiltEntryTotal() { return rebuiltEntryTotal; }
    public void setRebuiltEntryTotal(int rebuiltEntryTotal) { this.rebuiltEntryTotal = rebuiltEntryTotal; }
    public int getOriginalClassEntries() { return originalClassEntries; }
    public void setOriginalClassEntries(int originalClassEntries) { this.originalClassEntries = originalClassEntries; }
    public int getRebuiltClassEntries() { return rebuiltClassEntries; }
    public void setRebuiltClassEntries(int rebuiltClassEntries) { this.rebuiltClassEntries = rebuiltClassEntries; }
    public int getOriginalResourceEntries() { return originalResourceEntries; }
    public void setOriginalResourceEntries(int originalResourceEntries) { this.originalResourceEntries = originalResourceEntries; }
    public int getRebuiltResourceEntries() { return rebuiltResourceEntries; }
    public void setRebuiltResourceEntries(int rebuiltResourceEntries) { this.rebuiltResourceEntries = rebuiltResourceEntries; }
    public int getOriginalNestedLibs() { return originalNestedLibs; }
    public void setOriginalNestedLibs(int originalNestedLibs) { this.originalNestedLibs = originalNestedLibs; }
    public int getRebuiltNestedLibs() { return rebuiltNestedLibs; }
    public void setRebuiltNestedLibs(int rebuiltNestedLibs) { this.rebuiltNestedLibs = rebuiltNestedLibs; }
    public int getCommonEntries() { return commonEntries; }
    public void setCommonEntries(int commonEntries) { this.commonEntries = commonEntries; }
    public int getSameSha256() { return sameSha256; }
    public void setSameSha256(int sameSha256) { this.sameSha256 = sameSha256; }
    public int getDifferentSha256() { return differentSha256; }
    public void setDifferentSha256(int differentSha256) { this.differentSha256 = differentSha256; }
    public int getMissingEntries() { return missingEntries; }
    public void setMissingEntries(int missingEntries) { this.missingEntries = missingEntries; }
    public int getExtraEntries() { return extraEntries; }
    public void setExtraEntries(int extraEntries) { this.extraEntries = extraEntries; }
    public int getCommonClassEntries() { return commonClassEntries; }
    public void setCommonClassEntries(int commonClassEntries) { this.commonClassEntries = commonClassEntries; }
    public int getSameClassBytes() { return sameClassBytes; }
    public void setSameClassBytes(int sameClassBytes) { this.sameClassBytes = sameClassBytes; }
    public int getDifferentClassBytes() { return differentClassBytes; }
    public void setDifferentClassBytes(int differentClassBytes) { this.differentClassBytes = differentClassBytes; }
    public int getCommonNestedLibs() { return commonNestedLibs; }
    public void setCommonNestedLibs(int commonNestedLibs) { this.commonNestedLibs = commonNestedLibs; }
    public int getSameNestedLibs() { return sameNestedLibs; }
    public void setSameNestedLibs(int sameNestedLibs) { this.sameNestedLibs = sameNestedLibs; }
    public int getDifferentNestedLibs() { return differentNestedLibs; }
    public void setDifferentNestedLibs(int differentNestedLibs) { this.differentNestedLibs = differentNestedLibs; }
    public int getMissingNestedLibs() { return missingNestedLibs; }
    public void setMissingNestedLibs(int missingNestedLibs) { this.missingNestedLibs = missingNestedLibs; }
    public int getExtraNestedLibs() { return extraNestedLibs; }
    public void setExtraNestedLibs(int extraNestedLibs) { this.extraNestedLibs = extraNestedLibs; }
    public boolean isManifestOriginalPresent() { return manifestOriginalPresent; }
    public void setManifestOriginalPresent(boolean manifestOriginalPresent) { this.manifestOriginalPresent = manifestOriginalPresent; }
    public boolean isManifestRebuiltPresent() { return manifestRebuiltPresent; }
    public void setManifestRebuiltPresent(boolean manifestRebuiltPresent) { this.manifestRebuiltPresent = manifestRebuiltPresent; }
    public boolean isManifestSame() { return manifestSame; }
    public void setManifestSame(boolean manifestSame) { this.manifestSame = manifestSame; }
    public boolean isArchiveBytesSame() { return archiveBytesSame; }
    public void setArchiveBytesSame(boolean archiveBytesSame) { this.archiveBytesSame = archiveBytesSame; }
    public boolean isArchiveEntryOrderSame() { return archiveEntryOrderSame; }
    public void setArchiveEntryOrderSame(boolean archiveEntryOrderSame) { this.archiveEntryOrderSame = archiveEntryOrderSame; }
    public int getArchiveMetadataDiffEntries() { return archiveMetadataDiffEntries; }
    public void setArchiveMetadataDiffEntries(int archiveMetadataDiffEntries) { this.archiveMetadataDiffEntries = archiveMetadataDiffEntries; }
    public int getArchiveTimestampDifferences() { return archiveTimestampDifferences; }
    public void setArchiveTimestampDifferences(int archiveTimestampDifferences) { this.archiveTimestampDifferences = archiveTimestampDifferences; }
    public int getArchiveCompressionMethodDifferences() { return archiveCompressionMethodDifferences; }
    public void setArchiveCompressionMethodDifferences(int archiveCompressionMethodDifferences) { this.archiveCompressionMethodDifferences = archiveCompressionMethodDifferences; }
    public int getArchiveCompressedSizeDifferences() { return archiveCompressedSizeDifferences; }
    public void setArchiveCompressedSizeDifferences(int archiveCompressedSizeDifferences) { this.archiveCompressedSizeDifferences = archiveCompressedSizeDifferences; }
    public int getArchiveExtraFieldDifferences() { return archiveExtraFieldDifferences; }
    public void setArchiveExtraFieldDifferences(int archiveExtraFieldDifferences) { this.archiveExtraFieldDifferences = archiveExtraFieldDifferences; }
    public int getArchiveCommentDifferences() { return archiveCommentDifferences; }
    public void setArchiveCommentDifferences(int archiveCommentDifferences) { this.archiveCommentDifferences = archiveCommentDifferences; }
    public String getOriginalArchiveSha256() { return originalArchiveSha256; }
    public void setOriginalArchiveSha256(String originalArchiveSha256) { this.originalArchiveSha256 = originalArchiveSha256; }
    public String getRebuiltArchiveSha256() { return rebuiltArchiveSha256; }
    public void setRebuiltArchiveSha256(String rebuiltArchiveSha256) { this.rebuiltArchiveSha256 = rebuiltArchiveSha256; }
    public List<String> getSampleMissingEntries() { return sampleMissingEntries; }
    public List<String> getSampleExtraEntries() { return sampleExtraEntries; }
    public List<String> getSampleDifferentEntries() { return sampleDifferentEntries; }
    public List<String> getSampleArchiveMetadataDifferences() { return sampleArchiveMetadataDifferences; }

    public void recordMissing(DifferenceBucket bucket, String path) {
        getBucketSummary(bucket).recordMissing(path);
    }

    public void recordExtra(DifferenceBucket bucket, String path) {
        getBucketSummary(bucket).recordExtra(path);
    }

    public void recordDifferent(DifferenceBucket bucket, String path) {
        getBucketSummary(bucket).recordDifferent(path);
    }

    public void recordArchiveMetadataDifference(String path) {
        addSample(sampleArchiveMetadataDifferences, path);
    }

    public DifferenceBucketSummary getBucketSummary(DifferenceBucket bucket) {
        DifferenceBucket normalized = bucket == null ? DifferenceBucket.RESOURCE_ENTRY : bucket;
        DifferenceBucketSummary summary = bucketSummaries.get(normalized);
        if (summary == null) {
            summary = new DifferenceBucketSummary(normalized);
            bucketSummaries.put(normalized, summary);
        }
        return summary;
    }

    public List<DifferenceBucketSummary> getBucketSummaries() {
        List<DifferenceBucketSummary> summaries = new ArrayList<>();
        for (DifferenceBucket bucket : DifferenceBucket.values()) {
            summaries.add(getBucketSummary(bucket));
        }
        return summaries;
    }

    private void addSample(List<String> samples, String path) {
        if (path != null && samples.size() < SAMPLE_LIMIT && !samples.contains(path)) {
            samples.add(path);
        }
    }

    public static class DifferenceBucketSummary {
        private final DifferenceBucket bucket;
        private int missing;
        private int extra;
        private int different;
        private final List<String> samples = new ArrayList<>();

        private DifferenceBucketSummary(DifferenceBucket bucket) {
            this.bucket = bucket;
        }

        private void recordMissing(String path) {
            missing++;
            addSample(path);
        }

        private void recordExtra(String path) {
            extra++;
            addSample(path);
        }

        private void recordDifferent(String path) {
            different++;
            addSample(path);
        }

        private void addSample(String path) {
            if (path != null && samples.size() < SAMPLE_LIMIT && !samples.contains(path)) {
                samples.add(path);
            }
        }

        public DifferenceBucket getBucket() { return bucket; }
        public int getMissing() { return missing; }
        public int getExtra() { return extra; }
        public int getDifferent() { return different; }
        public int getTotal() { return missing + extra + different; }
        public List<String> getSamples() { return samples; }
    }
}
