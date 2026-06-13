package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GapSummaryWriter {

    public void write(File outputDir, RestorationScore score) throws IOException {
        RestorationScore effectiveScore = score == null ? new RestorationScore() : score;
        StringBuilder report = new StringBuilder();
        List<RestorationScore.GapItem> restorationGaps = sortedGaps(effectiveScore, true);
        List<RestorationScore.GapItem> observations = sortedGaps(effectiveScore, false);

        report.append("# Gap summary\n\n");
        report.append("- Overall score: ").append(effectiveScore.getOverall()).append("/100\n");
        report.append("- Gap count: ").append(restorationGaps.size()).append("\n");
        report.append("- Observation count: ").append(observations.size()).append("\n");
        appendSourceRebuildFidelityEvidence(report, outputDir);
        appendPackageFidelityEvidence(report, outputDir);
        report.append("\n");
        report.append("## Remaining restoration gaps\n\n");
        report.append("| Category | Impact | Detail |\n");
        report.append("| --- | --- | --- |\n");

        if (restorationGaps.isEmpty()) {
            report.append("| (none) | 0 | No restoration gaps detected. |\n");
        } else {
            for (RestorationScore.GapItem gap : restorationGaps) {
                report.append("| ")
                        .append(safe(gap.getCategory()))
                        .append(" | ")
                        .append(gap.getImpact())
                        .append(" | ")
                        .append(safe(gap.getDetail()))
                        .append(" |\n");
            }
        }

        report.append("\n## Non-penalizing observations\n\n");
        report.append("| Observation | Impact | Detail |\n");
        report.append("| --- | --- | --- |\n");
        if (observations.isEmpty()) {
            report.append("| (none) | 0 | No non-penalizing observations recorded. |\n");
        } else {
            for (RestorationScore.GapItem gap : observations) {
                report.append("| ")
                        .append(safe(gap.getCategory()))
                        .append(" | ")
                        .append(gap.getImpact())
                        .append(" | ")
                        .append(safe(gap.getDetail()))
                        .append(" |\n");
            }
        }

        IoUtils.writeStringToFile(new File(outputDir, "gap-summary.md"), report.toString());
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
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
