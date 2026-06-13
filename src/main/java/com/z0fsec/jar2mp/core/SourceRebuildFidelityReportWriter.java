package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.SourceRebuildFidelityResult;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SourceRebuildFidelityReportWriter {

    public void write(File outputDir, SourceRebuildFidelityResult result) throws IOException {
        SourceRebuildFidelityResult effective = result == null ? new SourceRebuildFidelityResult() : result;
        IoUtils.writeStringToFile(new File(outputDir, "source-rebuild-fidelity-report.md"), markdown(effective));
        IoUtils.writeStringToFile(new File(outputDir, "source-rebuild-fidelity-summary.csv"), csv(effective));
    }

    private String markdown(SourceRebuildFidelityResult result) {
        StringBuilder report = new StringBuilder();
        report.append("# Source rebuild class bytecode fidelity\n\n");
        report.append("This report compares application `.class` files compiled into `target/classes` ")
                .append("from generated sources with application class entries from the original archive. ")
                .append("Compile fallback classes are reported separately because raw class fallback is not ")
                .append("pure source recompilation.\n\n");
        report.append("- Source-recompiled class bytes same: ")
                .append(result.isSourceRecompiledClassBytesSame()).append("\n");
        report.append("- Compile fallback classes: ").append(result.getCompileFallbackClasses()).append("\n\n");
        report.append("| Metric | Original | Recompiled | Common | Same | Different | Missing | Extra |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        report.append("| app classes | ")
                .append(result.getOriginalAppClasses()).append(" | ")
                .append(result.getRecompiledClasses()).append(" | ")
                .append(result.getCommonClasses()).append(" | ")
                .append(result.getSameClassBytes()).append(" | ")
                .append(result.getDifferentClassBytes()).append(" | ")
                .append(result.getMissingRecompiledClasses()).append(" | ")
                .append(result.getExtraRecompiledClasses()).append(" |\n\n");
        appendSamples(report, "Different class byte examples", result.getSampleDifferentClasses());
        appendSamples(report, "Missing recompiled class examples",
                result.getSampleMissingRecompiledClasses());
        appendSamples(report, "Extra recompiled class examples", result.getSampleExtraRecompiledClasses());
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

    private String csv(SourceRebuildFidelityResult result) {
        StringBuilder csv = new StringBuilder();
        csv.append("source_recompiled_class_bytes_same,original_app_classes,recompiled_classes,");
        csv.append("common_classes,same_class_bytes,different_class_bytes,missing_recompiled_classes,");
        csv.append("extra_recompiled_classes,compile_fallback_classes\n");
        csv.append(result.isSourceRecompiledClassBytesSame()).append(',')
                .append(result.getOriginalAppClasses()).append(',')
                .append(result.getRecompiledClasses()).append(',')
                .append(result.getCommonClasses()).append(',')
                .append(result.getSameClassBytes()).append(',')
                .append(result.getDifferentClassBytes()).append(',')
                .append(result.getMissingRecompiledClasses()).append(',')
                .append(result.getExtraRecompiledClasses()).append(',')
                .append(result.getCompileFallbackClasses())
                .append('\n');
        return csv.toString();
    }
}
