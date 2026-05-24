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
        report.append("| Bucket | Score | Weight | Contribution |\n");
        report.append("| --- | --- | --- | --- |\n");
        appendBucket(report, "source", 40, effectiveScore.getBreakdown().get("source"));
        appendBucket(report, "resource", 20, effectiveScore.getBreakdown().get("resource"));
        appendBucket(report, "runtime", 20, effectiveScore.getBreakdown().get("runtime"));
        appendBucket(report, "verification", 20, effectiveScore.getBreakdown().get("verification"));

        report.append("\n## Top gaps\n\n");
        List<RestorationScore.GapItem> gaps = new ArrayList<>(effectiveScore.getGaps());
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

        IoUtils.writeStringToFile(new File(outputDir, "restoration-score.md"), report.toString());
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

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value.trim();
    }
}
