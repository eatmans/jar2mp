package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PackageFidelityEvidence {
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

    static List<PackageFidelityEvidence> readAll(File outputDir) throws IOException {
        List<PackageFidelityEvidence> evidence = new ArrayList<>();
        add(evidence, outputDir, "byte-exact package",
                "target/byte-exact-package-check/artifact-fidelity-summary.csv");
        add(evidence, outputDir, "package-record restore",
                "target/package-record-restore-check/artifact-fidelity-summary.csv");
        return evidence;
    }

    String getMode() {
        return mode;
    }

    String getExactMatch() {
        return exactMatch;
    }

    String getArchiveBytesSame() {
        return archiveBytesSame;
    }

    String getContentEntriesMatch() {
        return contentEntriesMatch;
    }

    String getFormattedRebuiltArchiveSha256() {
        String hash = safe(rebuiltArchiveSha256);
        return "(missing)".equals(hash) || "(none)".equals(hash) ? hash : "`" + hash + "`";
    }

    private static void add(List<PackageFidelityEvidence> evidence, File outputDir,
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

    private static String[] splitCsvLine(String line) {
        return line == null ? new String[0] : line.split(",", -1);
    }

    private static String columnValue(String[] headers, String[] values, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equals(headers[i]) && i < values.length) {
                return safe(values[i]);
            }
        }
        return "(missing)";
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value.trim();
    }
}
