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
}
