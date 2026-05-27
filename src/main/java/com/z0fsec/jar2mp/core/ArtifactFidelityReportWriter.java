package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ArtifactFidelityReportWriter {

    public void write(File outputDir, ArtifactFidelityResult result) throws IOException {
        ArtifactFidelityResult effective = result == null ? new ArtifactFidelityResult() : result;
        IoUtils.writeStringToFile(new File(outputDir, "artifact-fidelity-report.md"), markdown(effective));
        IoUtils.writeStringToFile(new File(outputDir, "artifact-fidelity-summary.csv"), csv(effective));
    }

    private String markdown(ArtifactFidelityResult result) {
        StringBuilder report = new StringBuilder();
        report.append("# Artifact fidelity report\n\n");
        report.append("- Exact match: ").append(result.isExactMatch()).append("\n");
        report.append("- Manifest same: ").append(result.isManifestSame()).append("\n\n");
        report.append("| Metric | Original | Rebuilt | Common | Same | Different | Missing | Extra |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        report.append("| entries | ").append(result.getOriginalEntryTotal()).append(" | ")
                .append(result.getRebuiltEntryTotal()).append(" | ")
                .append(result.getCommonEntries()).append(" | ")
                .append(result.getSameSha256()).append(" | ")
                .append(result.getDifferentSha256()).append(" | ")
                .append(result.getMissingEntries()).append(" | ")
                .append(result.getExtraEntries()).append(" |\n");
        report.append("| classes | ").append(result.getOriginalClassEntries()).append(" | ")
                .append(result.getRebuiltClassEntries()).append(" | ")
                .append(result.getCommonClassEntries()).append(" | ")
                .append(result.getSameClassBytes()).append(" | ")
                .append(result.getDifferentClassBytes()).append(" |  |  |\n");
        report.append("| nested libs | ").append(result.getOriginalNestedLibs()).append(" | ")
                .append(result.getRebuiltNestedLibs()).append(" | ")
                .append(result.getCommonNestedLibs()).append(" | ")
                .append(result.getSameNestedLibs()).append(" | ")
                .append(result.getDifferentNestedLibs()).append(" | ")
                .append(result.getMissingNestedLibs()).append(" | ")
                .append(result.getExtraNestedLibs()).append(" |\n\n");
        appendSamples(report, "Missing Entries", result.getSampleMissingEntries());
        appendSamples(report, "Extra Entries", result.getSampleExtraEntries());
        appendSamples(report, "Different Entries", result.getSampleDifferentEntries());
        appendDifferenceBuckets(report, result);
        return report.toString();
    }

    private void appendSamples(StringBuilder report, String title, List<String> samples) {
        report.append("## ").append(title).append("\n\n");
        if (samples == null || samples.isEmpty()) {
            report.append("- None\n\n");
            return;
        }
        for (String sample : samples) {
            report.append("- `").append(sample).append("`\n");
        }
        report.append("\n");
    }

    private void appendDifferenceBuckets(StringBuilder report, ArtifactFidelityResult result) {
        report.append("## Difference buckets\n\n");
        report.append("| Bucket | Missing | Extra | Different | Total | Examples |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | --- |\n");
        for (ArtifactFidelityResult.DifferenceBucketSummary summary : sortedBuckets(result)) {
            report.append("| ").append(summary.getBucket()).append(" | ")
                    .append(summary.getMissing()).append(" | ")
                    .append(summary.getExtra()).append(" | ")
                    .append(summary.getDifferent()).append(" | ")
                    .append(summary.getTotal()).append(" | ")
                    .append(formatExamples(summary.getSamples())).append(" |\n");
        }
        report.append("\n");
    }

    private List<ArtifactFidelityResult.DifferenceBucketSummary> sortedBuckets(ArtifactFidelityResult result) {
        List<ArtifactFidelityResult.DifferenceBucketSummary> summaries =
                new ArrayList<>(result.getBucketSummaries());
        Collections.sort(summaries, new Comparator<ArtifactFidelityResult.DifferenceBucketSummary>() {
            @Override
            public int compare(ArtifactFidelityResult.DifferenceBucketSummary left,
                               ArtifactFidelityResult.DifferenceBucketSummary right) {
                int total = Integer.compare(right.getTotal(), left.getTotal());
                if (total != 0) {
                    return total;
                }
                return left.getBucket().name().compareTo(right.getBucket().name());
            }
        });
        return summaries;
    }

    private String formatExamples(List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return "";
        }
        StringBuilder examples = new StringBuilder();
        for (String sample : samples) {
            if (examples.length() > 0) {
                examples.append("<br>");
            }
            examples.append('`').append(sample).append('`');
        }
        return examples.toString();
    }

    private String csv(ArtifactFidelityResult result) {
        StringBuilder csv = new StringBuilder();
        csv.append("exact_match,original_entries,rebuilt_entries,common_entries,same_sha256,different_sha256,missing_entries,extra_entries,");
        csv.append("original_classes,rebuilt_classes,common_classes,same_class_bytes,different_class_bytes,");
        csv.append("original_nested_libs,rebuilt_nested_libs,common_nested_libs,same_nested_libs,different_nested_libs,missing_nested_libs,extra_nested_libs,manifest_same,");
        csv.append("bucket_manifest,bucket_class_bytecode,bucket_nested_library,bucket_maven_metadata,bucket_service_metadata,bucket_boot_index,bucket_signature_metadata,bucket_resource_entry\n");
        csv.append(result.isExactMatch()).append(',')
                .append(result.getOriginalEntryTotal()).append(',')
                .append(result.getRebuiltEntryTotal()).append(',')
                .append(result.getCommonEntries()).append(',')
                .append(result.getSameSha256()).append(',')
                .append(result.getDifferentSha256()).append(',')
                .append(result.getMissingEntries()).append(',')
                .append(result.getExtraEntries()).append(',')
                .append(result.getOriginalClassEntries()).append(',')
                .append(result.getRebuiltClassEntries()).append(',')
                .append(result.getCommonClassEntries()).append(',')
                .append(result.getSameClassBytes()).append(',')
                .append(result.getDifferentClassBytes()).append(',')
                .append(result.getOriginalNestedLibs()).append(',')
                .append(result.getRebuiltNestedLibs()).append(',')
                .append(result.getCommonNestedLibs()).append(',')
                .append(result.getSameNestedLibs()).append(',')
                .append(result.getDifferentNestedLibs()).append(',')
                .append(result.getMissingNestedLibs()).append(',')
                .append(result.getExtraNestedLibs()).append(',')
                .append(result.isManifestSame());
        for (ArtifactFidelityResult.DifferenceBucket bucket : ArtifactFidelityResult.DifferenceBucket.values()) {
            csv.append(',').append(result.getBucketSummary(bucket).getTotal());
        }
        csv.append('\n');
        return csv.toString();
    }
}
