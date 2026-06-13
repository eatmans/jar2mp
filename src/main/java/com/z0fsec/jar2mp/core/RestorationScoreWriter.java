package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RestorationScoreWriter {

    public void write(File outputDir, RestorationScore score) throws IOException {
        StringBuilder report = new StringBuilder();
        RestorationScore effectiveScore = score == null ? new RestorationScore() : score;

        report.append("# Restoration score\n\n");
        report.append("- Overall: ").append(effectiveScore.getOverall()).append("/100\n\n");
        report.append("> Overall score includes source, resource, runtime observation, and build verification. ")
                .append("Byte-level package equality is summarized below when package fidelity reports exist; ")
                .append("full reports live in ")
                .append("`target/byte-exact-package-check/` and ")
                .append("`target/package-record-restore-check/` artifact fidelity reports.\n\n");
        report.append("| Bucket | Score | Weight | Contribution |\n");
        report.append("| --- | --- | --- | --- |\n");
        appendBucket(report, "source", 40, effectiveScore.getBreakdown().get("source"));
        appendBucket(report, "resource", 20, effectiveScore.getBreakdown().get("resource"));
        appendBucket(report, "runtime", 20, effectiveScore.getBreakdown().get("runtime"));
        appendBucket(report, "verification", 20, effectiveScore.getBreakdown().get("verification"));

        appendSourceRebuildFidelityEvidence(report, outputDir);
        appendPackageFidelityEvidence(report, outputDir);

        report.append("\n## Top gaps\n\n");
        List<RestorationScore.GapItem> gaps = sortedGaps(effectiveScore, true);

        if (gaps.isEmpty()) {
            report.append("- None detected.\n");
        } else {
            int limit = Math.min(10, gaps.size());
            for (int i = 0; i < limit; i++) {
                RestorationScore.GapItem gap = gaps.get(i);
                report.append("- [")
                        .append(gap.getCategory())
                        .append("] ")
                        .append(safe(gap.getDetail()))
                        .append(" (impact ")
                        .append(gap.getImpact())
                        .append(")\n");
            }
        }

        report.append("\n## Non-penalizing observations\n\n");
        List<RestorationScore.GapItem> observations = sortedGaps(effectiveScore, false);
        if (observations.isEmpty()) {
            report.append("- None recorded.\n");
        } else {
            int limit = Math.min(10, observations.size());
            for (int i = 0; i < limit; i++) {
                RestorationScore.GapItem gap = observations.get(i);
                report.append("- [")
                        .append(gap.getCategory())
                        .append("] ")
                        .append(safe(gap.getDetail()))
                        .append(" (impact ")
                        .append(gap.getImpact())
                        .append(")\n");
            }
        }

        IoUtils.writeStringToFile(new File(outputDir, "restoration-score.md"), report.toString());
    }

    private List<RestorationScore.GapItem> sortedGaps(RestorationScore score, boolean positiveImpact) {
        List<RestorationScore.GapItem> gaps = new ArrayList<>();
        for (RestorationScore.GapItem gap : score.getGaps()) {
            if (gap == null) {
                continue;
            }
            if ((gap.getImpact() > 0) == positiveImpact) {
                gaps.add(gap);
            }
        }
        Collections.sort(gaps, new Comparator<RestorationScore.GapItem>() {
            @Override
            public int compare(RestorationScore.GapItem left, RestorationScore.GapItem right) {
                int impact = Integer.compare(right.getImpact(), left.getImpact());
                if (impact != 0) {
                    return impact;
                }
                String leftCategory = left.getCategory() == null ? "" : left.getCategory();
                String rightCategory = right.getCategory() == null ? "" : right.getCategory();
                return leftCategory.compareTo(rightCategory);
            }
        });
        return gaps;
    }

    private void appendBucket(StringBuilder report, String bucket, int weight, Integer score) {
        int value = score == null ? 0 : score.intValue();
        int contribution = (int) Math.round(value * (weight / 100.0));
        report.append("| ")
                .append(bucket)
                .append(" | ")
                .append(value)
                .append(" | ")
                .append(weight)
                .append("% | ")
                .append(contribution)
                .append(" |\n");
    }

    private void appendPackageFidelityEvidence(StringBuilder report, File outputDir) throws IOException {
        List<PackageFidelityEvidence> evidence = PackageFidelityEvidence.readAll(outputDir);
        if (evidence.isEmpty()) {
            return;
        }

        report.append("\n## Byte-level package fidelity\n\n");
        report.append("| Mode | Exact match | Archive bytes same | Content entries match | Rebuilt SHA-256 |\n");
        report.append("| --- | --- | --- | --- | --- |\n");
        for (PackageFidelityEvidence item : evidence) {
            report.append("| ")
                    .append(item.getMode())
                    .append(" | ")
                    .append(item.getExactMatch())
                    .append(" | ")
                    .append(item.getArchiveBytesSame())
                    .append(" | ")
                    .append(item.getContentEntriesMatch())
                    .append(" | ")
                    .append(item.getFormattedRebuiltArchiveSha256())
                    .append(" |\n");
        }
    }

    private void appendSourceRebuildFidelityEvidence(StringBuilder report, File outputDir) throws IOException {
        SourceRebuildFidelityEvidence evidence = SourceRebuildFidelityEvidence.read(outputDir);
        if (evidence == null) {
            return;
        }

        report.append("\n## Source rebuild class bytecode fidelity\n\n");
        report.append("| Source-recompiled class bytes same | Original app classes | Recompiled classes | Common | Same class bytes | Different class bytes | Missing | Extra | Compile fallback classes |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        report.append("| ")
                .append(evidence.getSourceRecompiledClassBytesSame())
                .append(" | ")
                .append(evidence.getOriginalAppClasses())
                .append(" | ")
                .append(evidence.getRecompiledClasses())
                .append(" | ")
                .append(evidence.getCommonClasses())
                .append(" | ")
                .append(evidence.getSameClassBytes())
                .append(" | ")
                .append(evidence.getDifferentClassBytes())
                .append(" | ")
                .append(evidence.getMissingRecompiledClasses())
                .append(" | ")
                .append(evidence.getExtraRecompiledClasses())
                .append(" | ")
                .append(evidence.getCompileFallbackClasses())
                .append(" |\n");
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value.trim();
    }
}
