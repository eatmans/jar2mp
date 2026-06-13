package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.RestorationScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GapSummaryWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void embedsPackageFidelityEvidenceWhenSummaryExists() throws Exception {
        Files.createDirectories(tempDir.resolve("target/package-record-restore-check"));
        Files.writeString(tempDir.resolve("target/package-record-restore-check/artifact-fidelity-summary.csv"),
                "exact_match,content_entries_match,archive_bytes_same,rebuilt_archive_sha256\n"
                        + "true,true,true,abcdef0123456789\n");
        RestorationScore score = new RestorationScore();
        score.setOverall(80);
        score.addGap("runtime_trace",
                "Runtime trace data has not been captured; this is an observation gap.", 20);

        new GapSummaryWriter().write(tempDir.toFile(), score);

        String report = Files.readString(tempDir.resolve("gap-summary.md"));
        assertTrue(report.contains("- Overall score: 80/100"));
        assertTrue(report.contains("## Byte-level package fidelity"));
        assertTrue(report.contains("| package-record restore | true | true | true | `abcdef0123456789` |"));
        assertTrue(report.contains("| runtime_trace | 20 | Runtime trace data has not been captured"));
    }

    @Test
    void embedsSourceRebuildFidelityEvidenceWhenSummaryExists() throws Exception {
        Files.writeString(tempDir.resolve("source-rebuild-fidelity-summary.csv"),
                "source_recompiled_class_bytes_same,original_app_classes,recompiled_classes,common_classes,"
                        + "same_class_bytes,different_class_bytes,missing_recompiled_classes,"
                        + "extra_recompiled_classes,compile_fallback_classes\n"
                        + "false,10,9,9,8,1,1,0,1\n");
        RestorationScore score = new RestorationScore();
        score.setOverall(80);

        new GapSummaryWriter().write(tempDir.toFile(), score);

        String report = Files.readString(tempDir.resolve("gap-summary.md"));
        assertTrue(report.contains("## Source rebuild class bytecode fidelity"));
        assertTrue(report.contains("| false | 10 | 9 | 9 | 8 | 1 | 1 | 0 | 1 |"));
    }
}
