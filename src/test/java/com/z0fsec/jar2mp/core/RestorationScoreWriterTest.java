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
        assertTrue(report.contains("Byte-level package equality is reported separately"));
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
}
