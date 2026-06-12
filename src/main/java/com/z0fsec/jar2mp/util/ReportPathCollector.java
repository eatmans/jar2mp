package com.z0fsec.jar2mp.util;

import com.z0fsec.jar2mp.model.ProjectConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ReportPathCollector {

    private ReportPathCollector() {
    }

    public static List<File> collectProjectReports(File outputDir,
                                                   ProjectConfig config,
                                                   boolean includeVerification) {
        List<File> reports = new ArrayList<>();
        add(reports, outputDir, "restoration-report.md");
        add(reports, outputDir, "resource-inventory.md");
        add(reports, outputDir, "decompile-parity-report.md");
        add(reports, outputDir, "restoration-score.md");
        add(reports, outputDir, "gap-summary.md");
        add(reports, outputDir, "RUNBOOK.md");
        add(reports, outputDir, "decompile-failures.md");

        addIfExpectedOrPresent(reports, outputDir, "runtime-trace-report.md",
                config != null && (config.isTraceRuntime() || config.isSmokeOnly()));
        boolean verificationExpected = includeVerification && config != null && config.isVerifyBuild();
        addIfExpectedOrPresent(reports, outputDir, "verification-report.md", verificationExpected);
        addIfExpectedOrPresent(reports, outputDir, "verification-errors.md", verificationExpected);

        addFidelityPairIfPresent(reports, outputDir, "target/raw-artifact");
        addFidelityPairIfPresent(reports, outputDir, "target/byte-exact-package-check");
        addFidelityPairIfPresent(reports, outputDir, "target/package-record-restore-check");
        return reports;
    }

    private static void add(List<File> reports, File outputDir, String relativePath) {
        reports.add(new File(outputDir, relativePath));
    }

    private static void addIfExpectedOrPresent(List<File> reports,
                                               File outputDir,
                                               String relativePath,
                                               boolean expected) {
        File report = new File(outputDir, relativePath);
        if (expected || report.isFile()) {
            reports.add(report);
        }
    }

    private static void addFidelityPairIfPresent(List<File> reports, File outputDir, String relativeDir) {
        File report = new File(outputDir, relativeDir + "/artifact-fidelity-report.md");
        File summary = new File(outputDir, relativeDir + "/artifact-fidelity-summary.csv");
        if (report.isFile() || summary.isFile()) {
            reports.add(report);
            reports.add(summary);
        }
    }
}
