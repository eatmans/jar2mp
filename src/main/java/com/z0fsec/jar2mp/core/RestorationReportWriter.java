package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.model.StartupFinding;
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

    public void writeRunbook(File outputDir, JarAnalysisResult analysis) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Runbook\n\n");

        report.append("## Detected application type\n\n");
        if (analysis.getStartupFindings().isEmpty()) {
            report.append("Unknown\n\n");
        } else {
            report.append(analysis.getStartupFindings().get(0).getApplicationType()).append("\n\n");
        }

        report.append("## Startup candidates\n\n");
        for (StartupFinding finding : analysis.getStartupFindings()) {
            if (finding.getMainClass() != null) {
                report.append("- Main class: ").append(finding.getMainClass()).append("\n");
            }
            for (String command : finding.getCommands()) {
                report.append("- `").append(command).append("`\n");
            }
        }
        if (analysis.getStartupFindings().isEmpty()) {
            report.append("- No startup candidates detected.\n");
        }

        report.append("\n## Verification commands\n\n");
        report.append("- `mvn -q -DskipTests compile`\n");
        report.append("- `mvn -q -DskipTests package`\n");

        report.append("\n## Known gaps\n\n");
        boolean hasGaps = false;
        for (StartupFinding finding : analysis.getStartupFindings()) {
            for (String gap : finding.getKnownGaps()) {
                report.append("- ").append(gap).append("\n");
                hasGaps = true;
            }
        }
        if (!hasGaps) {
            report.append("- None detected from archive metadata.\n");
        }

        report.append("\n## Evidence\n\n");
        for (StartupFinding finding : analysis.getStartupFindings()) {
            for (String evidence : finding.getEvidence()) {
                report.append("- ").append(evidence).append("\n");
            }
        }

        IoUtils.writeStringToFile(new File(outputDir, "RUNBOOK.md"), report.toString());
    }

    public void writeDecompileFailures(File outputDir, JarAnalysisResult analysis) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Decompile failures\n\n");
        if (analysis.getDecompileFindings().isEmpty()) {
            report.append("No decompilation failures detected.\n");
        } else {
            for (DecompileFinding finding : analysis.getDecompileFindings()) {
                report.append("- Failed to decompile `").append(finding.getClassPath()).append("`");
                report.append("; raw class retained at `").append(finding.getRetainedClassPath()).append("`");
                if (finding.getMessage() != null && !finding.getMessage().trim().isEmpty()) {
                    report.append("; ").append(finding.getMessage().trim());
                }
                report.append("\n");
            }
        }
        IoUtils.writeStringToFile(new File(outputDir, "decompile-failures.md"), report.toString());
    }

    private String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
