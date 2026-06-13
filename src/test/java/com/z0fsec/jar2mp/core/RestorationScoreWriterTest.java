package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.RestorationScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RestorationScoreWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesEvidenceBoundaryBetweenOverallScoreAndByteExactReports() throws Exception {
        RestorationScore score = new RestorationScore();
        score.setOverall(80);
        score.putBucket("source", 100);
        score.putBucket("resource", 100);
        score.putBucket("runtime", 0);
        score.putBucket("verification", 100);

        new RestorationScoreWriter().write(tempDir.toFile(), score);

        String report = Files.readString(tempDir.resolve("restoration-score.md"));
        assertTrue(report.contains("Overall score includes source, resource, runtime observation, and build verification"));
        assertTrue(report.contains("Byte-level package equality is summarized below when package fidelity reports exist"));
        assertTrue(report.contains("target/byte-exact-package-check"));
        assertTrue(report.contains("target/package-record-restore-check"));
    }

    @Test
    void embedsByteExactPackageFidelityEvidenceWhenSummaryExists() throws Exception {
        Files.createDirectories(tempDir.resolve("target/byte-exact-package-check"));
        Files.writeString(tempDir.resolve("target/byte-exact-package-check/artifact-fidelity-summary.csv"),
                "exact_match,content_entries_match,archive_bytes_same,rebuilt_archive_sha256\n"
                        + "true,true,true,0123456789abcdef\n");
        RestorationScore score = new RestorationScore();
        score.setOverall(80);
        score.putBucket("source", 100);
        score.putBucket("resource", 100);
        score.putBucket("runtime", 0);
        score.putBucket("verification", 100);

        new RestorationScoreWriter().write(tempDir.toFile(), score);

        String report = Files.readString(tempDir.resolve("restoration-score.md"));
        assertTrue(report.contains("- Overall: 80/100"));
        assertTrue(report.contains("## Byte-level package fidelity"));
        assertTrue(report.contains("| Mode | Exact match | Archive bytes same | Content entries match | Rebuilt SHA-256 |"));
        assertTrue(report.contains("| byte-exact package | true | true | true | `0123456789abcdef` |"));
    }

    @Test
    void embedsSourceRebuildFidelityEvidenceWhenSummaryExists() throws Exception {
        Files.writeString(tempDir.resolve("source-rebuild-fidelity-summary.csv"),
                "source_recompiled_class_bytes_same,original_app_classes,recompiled_classes,common_classes,"
                        + "same_class_bytes,different_class_bytes,missing_recompiled_classes,"
                        + "extra_recompiled_classes,compile_fallback_classes\n"
                        + "true,413,413,413,413,0,0,0,0\n");
        RestorationScore score = new RestorationScore();
        score.setOverall(100);

        new RestorationScoreWriter().write(tempDir.toFile(), score);

        String report = Files.readString(tempDir.resolve("restoration-score.md"));
        assertTrue(report.contains("## Source rebuild class bytecode fidelity"));
        assertTrue(report.contains("| Source-recompiled class bytes same | Original app classes | Recompiled classes | Common | Same class bytes | Different class bytes | Missing | Extra | Compile fallback classes |"));
        assertTrue(report.contains("| true | 413 | 413 | 413 | 413 | 0 | 0 | 0 | 0 |"));
    }
}
