package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
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

    private String csv(ArtifactFidelityResult result) {
        StringBuilder csv = new StringBuilder();
        csv.append("exact_match,original_entries,rebuilt_entries,common_entries,same_sha256,different_sha256,missing_entries,extra_entries,");
        csv.append("original_classes,rebuilt_classes,common_classes,same_class_bytes,different_class_bytes,");
        csv.append("original_nested_libs,rebuilt_nested_libs,common_nested_libs,same_nested_libs,different_nested_libs,missing_nested_libs,extra_nested_libs,manifest_same\n");
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
                .append(result.isManifestSame()).append('\n');
        return csv.toString();
    }
}
