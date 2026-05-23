package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class RestorationReportWriter {

    public void writeResourceInventory(File outputDir, JarAnalysisResult analysis) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Resource inventory\n\n");
        report.append("| Category | Original path | Target path | Notes |\n");
        report.append("| --- | --- | --- | --- |\n");

        List<ResourceFinding> findings = analysis.getResourceFindings();
        if (findings.isEmpty()) {
            report.append("| OTHER | (none) | (none) | No non-class resources detected. |\n");
        } else {
            for (ResourceFinding finding : findings) {
                report.append("| ")
                        .append(escapeCell(finding.getCategory().name())).append(" | ")
                        .append(escapeCell(finding.getOriginalPath())).append(" | ")
                        .append(escapeCell(finding.getTargetPath())).append(" | ")
                        .append(escapeCell(finding.getNote())).append(" |\n");
            }
        }

        IoUtils.writeStringToFile(new File(outputDir, "resource-inventory.md"), report.toString());
    }

    private String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
