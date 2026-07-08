package com.z0fsec.jar2mp.util;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportPathCollectorTest {

    @TempDir
    Path outputDir;

    @Test
    void collectProjectReportsIncludesSharedCliAndGuiReportSet() throws Exception {
        ProjectConfig config = new ProjectConfig();
        config.setTraceRuntime(true);
        config.setVerifyBuild(true);
        createReport("target/raw-artifact/artifact-fidelity-report.md");
        createReport("target/byte-exact-package-check/artifact-fidelity-report.md");
        createReport("target/package-record-restore-check/artifact-fidelity-report.md");
        createReport("source-rebuild-fidelity-report.md");
        createReport("source-rebuild-fidelity-summary.csv");

        List<String> reports = relativePaths(ReportPathCollector.collectProjectReports(
                outputDir.toFile(), config, true));

        assertTrue(reports.contains("restoration-report.md"));
        assertTrue(reports.contains("resource-inventory.md"));
        assertTrue(reports.contains("decompile-parity-report.md"));
        assertTrue(reports.contains("restoration-score.md"));
        assertTrue(reports.contains("gap-summary.md"));
        assertTrue(reports.contains("RUNBOOK.md"));
        assertTrue(reports.contains("decompile-failures.md"));
        assertTrue(reports.contains("runtime-trace-report.md"));
        assertTrue(reports.contains("verification-report.md"));
        assertTrue(reports.contains("verification-errors.md"));
        assertTrue(reports.contains("source-rebuild-fidelity-report.md"));
        assertTrue(reports.contains("source-rebuild-fidelity-summary.csv"));
        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertTrue(reports.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertTrue(reports.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void collectProjectReportsIncludesExpectedFidelityReportsFromConfig() {
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setEmitRawArtifact(true);
        config.setByteExactPackage(true);

        List<String> reports = relativePaths(ReportPathCollector.collectProjectReports(
                outputDir.toFile(), config, true));

        assertTrue(reports.contains("verification-report.md"));
        assertTrue(reports.contains("verification-errors.md"));
        assertTrue(reports.contains("source-rebuild-fidelity-report.md"));
        assertTrue(reports.contains("source-rebuild-fidelity-summary.csv"));
        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertTrue(reports.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void collectProjectReportsIncludesPackageRecordReportsForStandalonePackageRecordMode() {
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setRestorePackageRecords(true);

        List<String> reports = relativePaths(ReportPathCollector.collectProjectReports(
                outputDir.toFile(), config, true));

        assertTrue(reports.contains("verification-report.md"));
        assertTrue(reports.contains("verification-errors.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertTrue(reports.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void collectProjectReportsDoesNotExpectPackageVerificationReportsWithoutVerification() {
        ProjectConfig config = new ProjectConfig();
        config.setByteExactPackage(true);
        config.setRestorePackageRecords(true);

        List<String> reports = relativePaths(ReportPathCollector.collectProjectReports(
                outputDir.toFile(), config, true));

        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(reports.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertFalse(reports.contains("verification-report.md"));
        assertFalse(reports.contains("verification-errors.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void collectProjectReportsDoesNotExpectPackageReportsForNonPackageVerificationGoals() {
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("compile");
        config.setByteExactPackage(true);
        config.setRestorePackageRecords(true);

        List<String> reports = relativePaths(ReportPathCollector.collectProjectReports(
                outputDir.toFile(), config, true));

        assertTrue(reports.contains("verification-report.md"));
        assertTrue(reports.contains("verification-errors.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertFalse(reports.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    private void createReport(String relativePath) throws Exception {
        Path report = outputDir.resolve(relativePath);
        Files.createDirectories(report.getParent());
        Files.write(report, new byte[0]);
    }

    private List<String> relativePaths(List<File> reports) {
        List<String> paths = new ArrayList<>();
        for (File report : reports) {
            paths.add(outputDir.relativize(report.toPath()).toString().replace(File.separatorChar, '/'));
        }
        return paths;
    }
}
