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

    public void writeRestorationReport(File outputDir, JarAnalysisResult analysis) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Restoration report\n\n");
        report.append("- Classes: ").append(analysis.getClassFiles().size()).append("\n");
        report.append("- Resources: ").append(analysis.getResourceFiles().size()).append("\n");
        report.append("- META-INF entries: ").append(analysis.getMetaInfFiles().size()).append("\n");
        report.append("- Framework findings: ").append(analysis.getFrameworkFindings().size()).append("\n");
        report.append("- Startup candidates: ").append(analysis.getStartupFindings().size()).append("\n");
        report.append("- Resource findings: ").append(analysis.getResourceFindings().size()).append("\n");
        report.append("- Decompile failures: ").append(countDecompileFailures(analysis)).append("\n\n");
        report.append("Generated reports:\n");
        report.append("- `restoration-report.md`\n");
        report.append("- `resource-inventory.md`\n");
        report.append("- `decompile-parity-report.md`\n");
        report.append("- `RUNBOOK.md`\n");
        report.append("- `decompile-failures.md`\n");
        IoUtils.writeStringToFile(new File(outputDir, "restoration-report.md"), report.toString());
    }

    public void writeDecompileFailures(File outputDir, JarAnalysisResult analysis) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Decompile failures\n\n");
        if (countDecompileFailures(analysis) == 0) {
            report.append("No decompilation failures detected.\n");
        } else {
            for (DecompileFinding finding : analysis.getDecompileFindings()) {
                if (!finding.hasRetainedClassPath()) {
                    continue;
                }
                report.append("- Failed to decompile `").append(finding.getClassPath()).append("`");
                if (finding.getSelectedEngine() != null && !finding.getSelectedEngine().trim().isEmpty()) {
                    report.append("; Selected engine: ").append(finding.getSelectedEngine().trim());
                }
                if (finding.getFallbackReason() != null && !finding.getFallbackReason().trim().isEmpty()) {
                    report.append("; Fallback reason: ").append(finding.getFallbackReason().trim());
                }
                report.append("; raw class retained at `").append(finding.getRetainedClassPath()).append("`");
                if (finding.getMessage() != null && !finding.getMessage().trim().isEmpty()) {
                    report.append("; ").append(finding.getMessage().trim());
                }
                report.append("\n");
            }
        }
        IoUtils.writeStringToFile(new File(outputDir, "decompile-failures.md"), report.toString());
    }

    private int countDecompileFailures(JarAnalysisResult analysis) {
        int count = 0;
        for (DecompileFinding finding : analysis.getDecompileFindings()) {
            if (finding.hasRetainedClassPath()) {
                count++;
            }
        }
        return count;
    }

    private String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
