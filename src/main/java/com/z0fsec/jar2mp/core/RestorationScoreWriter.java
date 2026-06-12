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
                .append("Byte-level package equality is reported separately in ")
                .append("`target/byte-exact-package-check/` and ")
                .append("`target/package-record-restore-check/` artifact fidelity reports.\n\n");
        report.append("| Bucket | Score | Weight | Contribution |\n");
        report.append("| --- | --- | --- | --- |\n");
        appendBucket(report, "source", 40, effectiveScore.getBreakdown().get("source"));
        appendBucket(report, "resource", 20, effectiveScore.getBreakdown().get("resource"));
        appendBucket(report, "runtime", 20, effectiveScore.getBreakdown().get("runtime"));
        appendBucket(report, "verification", 20, effectiveScore.getBreakdown().get("verification"));

        appendPackageFidelityEvidence(report, outputDir);

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

    private void appendPackageFidelityEvidence(StringBuilder report, File outputDir) throws IOException {
        List<PackageFidelityEvidence> evidence = readPackageFidelityEvidence(outputDir);
        if (evidence.isEmpty()) {
            return;
        }

        report.append("\n## Byte-level package fidelity\n\n");
        report.append("| Mode | Exact match | Archive bytes same | Content entries match | Rebuilt SHA-256 |\n");
        report.append("| --- | --- | --- | --- | --- |\n");
        for (PackageFidelityEvidence item : evidence) {
            report.append("| ")
                    .append(item.mode)
                    .append(" | ")
                    .append(item.exactMatch)
                    .append(" | ")
                    .append(item.archiveBytesSame)
                    .append(" | ")
                    .append(item.contentEntriesMatch)
                    .append(" | ")
                    .append(formatHash(item.rebuiltArchiveSha256))
                    .append(" |\n");
        }
    }

    private List<PackageFidelityEvidence> readPackageFidelityEvidence(File outputDir) throws IOException {
        List<PackageFidelityEvidence> evidence = new ArrayList<>();
        addPackageFidelityEvidence(evidence, outputDir, "byte-exact package",
                "target/byte-exact-package-check/artifact-fidelity-summary.csv");
        addPackageFidelityEvidence(evidence, outputDir, "package-record restore",
                "target/package-record-restore-check/artifact-fidelity-summary.csv");
        return evidence;
    }

    private void addPackageFidelityEvidence(List<PackageFidelityEvidence> evidence, File outputDir,
                                            String mode, String relativePath) throws IOException {
        if (outputDir == null) {
            return;
        }
        File summary = new File(outputDir, relativePath);
        if (!summary.isFile()) {
            return;
        }
        String[] lines = IoUtils.readFileToString(summary).split("\\r?\\n");
        if (lines.length < 2) {
            return;
        }
        String[] headers = splitCsvLine(lines[0]);
        String[] values = splitCsvLine(lines[1]);
        evidence.add(new PackageFidelityEvidence(
                mode,
                columnValue(headers, values, "exact_match"),
                columnValue(headers, values, "archive_bytes_same"),
                columnValue(headers, values, "content_entries_match"),
                columnValue(headers, values, "rebuilt_archive_sha256")));
    }

    private String[] splitCsvLine(String line) {
        return line == null ? new String[0] : line.split(",", -1);
    }

    private String columnValue(String[] headers, String[] values, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equals(headers[i]) && i < values.length) {
                return safe(values[i]);
            }
        }
        return "(missing)";
    }

    private String formatHash(String value) {
        String hash = safe(value);
        return "(missing)".equals(hash) || "(none)".equals(hash) ? hash : "`" + hash + "`";
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value.trim();
    }

    private static class PackageFidelityEvidence {
        private final String mode;
        private final String exactMatch;
        private final String archiveBytesSame;
        private final String contentEntriesMatch;
        private final String rebuiltArchiveSha256;

        private PackageFidelityEvidence(String mode, String exactMatch, String archiveBytesSame,
                                        String contentEntriesMatch, String rebuiltArchiveSha256) {
            this.mode = mode;
            this.exactMatch = exactMatch;
            this.archiveBytesSame = archiveBytesSame;
            this.contentEntriesMatch = contentEntriesMatch;
            this.rebuiltArchiveSha256 = rebuiltArchiveSha256;
        }
    }
}
