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

        report.append("# Gap summary\n\n");
        report.append("- Overall score: ").append(effectiveScore.getOverall()).append("/100\n");
        report.append("- Gap count: ").append(effectiveScore.getGaps().size()).append("\n\n");
        report.append("| Category | Impact | Detail |\n");
        report.append("| --- | --- | --- |\n");

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
            report.append("| (none) | 0 | No major gaps detected. |\n");
        } else {
            for (RestorationScore.GapItem gap : gaps) {
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

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
