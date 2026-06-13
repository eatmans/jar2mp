package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.SourceRebuildFidelityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRebuildFidelityReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesMarkdownAndCsvSummary() throws Exception {
        SourceRebuildFidelityResult result = new SourceRebuildFidelityResult();
        result.setOriginalAppClasses(2);
        result.setRecompiledClasses(2);
        result.setCommonClasses(2);
        result.setSameClassBytes(1);
        result.setDifferentClassBytes(1);
        result.setCompileFallbackClasses(1);
        result.getSampleDifferentClasses().add("demo/App.class");

        new SourceRebuildFidelityReportWriter().write(tempDir.toFile(), result);

        String markdown = Files.readString(tempDir.resolve("source-rebuild-fidelity-report.md"));
        assertTrue(markdown.contains("# Source rebuild class bytecode fidelity"));
        assertTrue(markdown.contains("- Source-recompiled class bytes same: false"));
        assertTrue(markdown.contains("| app classes | 2 | 2 | 2 | 1 | 1 | 0 | 0 |"));
        assertTrue(markdown.contains("demo/App.class"));

        String csv = Files.readString(tempDir.resolve("source-rebuild-fidelity-summary.csv"));
        assertTrue(csv.contains("source_recompiled_class_bytes_same"));
        assertTrue(csv.contains("false,2,2,2,1,1,0,0,1"));
    }
}
