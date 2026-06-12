package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.model.VerificationResult;
import com.z0fsec.jar2mp.util.Jar2MpConstants;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class BuildPostProcessor {

    private final ProjectVerifier verifier;
    private final RuntimeSmokeRunner smokeRunner;
    private final RuntimeTraceReportWriter traceReportWriter;
    private final RawArtifactPackager rawArtifactPackager;
    private final ArtifactFidelityComparator artifactFidelityComparator;
    private final ArtifactFidelityReportWriter artifactFidelityReportWriter;
    private final RestorationScorer restorationScorer;
    private final RestorationScoreWriter restorationScoreWriter;
    private final GapSummaryWriter gapSummaryWriter;
    private final DecompileParityReporter parityReporter;

    public BuildPostProcessor() {
        this.verifier = new ProjectVerifier();
        this.smokeRunner = new RuntimeSmokeRunner();
        this.traceReportWriter = new RuntimeTraceReportWriter();
        this.rawArtifactPackager = new RawArtifactPackager();
        this.artifactFidelityComparator = new ArtifactFidelityComparator();
        this.artifactFidelityReportWriter = new ArtifactFidelityReportWriter();
        this.restorationScorer = new RestorationScorer();
        this.restorationScoreWriter = new RestorationScoreWriter();
        this.gapSummaryWriter = new GapSummaryWriter();
        this.parityReporter = new DecompileParityReporter();
    }

    public PostBuildResult postProcess(File originalArtifact, JarAnalysisResult analysis, File outputDir,
                                       ProjectConfig config, Consumer<String> logger) throws IOException {
        PostBuildResult result = new PostBuildResult();
        if (config == null) {
            return result;
        }

        if (config.isEmitRawArtifact()) {
            File preservedArtifact = rawArtifactPackager.preserve(originalArtifact, outputDir);
            result.setPreservedRawArtifact(preservedArtifact);
            if (config.isByteExactPackage()) {
                rawArtifactPackager.preserveByteExactReference(originalArtifact, outputDir);
            }
            File rawArtifactDir = preservedArtifact.getParentFile();
            ArtifactFidelityResult rawFidelity = artifactFidelityComparator.compare(originalArtifact, preservedArtifact);
            artifactFidelityReportWriter.write(rawArtifactDir, rawFidelity);
            result.setRawArtifactFidelity(rawFidelity);
            log(logger, "原始归档保真副本: " + preservedArtifact.getAbsolutePath()
                    + " (exact=" + rawFidelity.isExactMatch() + ")");
        }

        if (config.isTraceRuntime() || config.isSmokeOnly()) {
            RuntimeSmokeRunner.SmokeRunResult smokeResult = smokeRunner.runSmoke(
                    originalArtifact,
                    analysis,
                    resolveTraceAgentJar(),
                    resolveTraceFile(config, outputDir),
                    config.getTraceArgs(),
                    config.getTraceTimeoutSeconds());
            traceReportWriter.write(outputDir, smokeResult);
            analysis.setRuntimeSmokeResult(smokeResult);
            analysis.setRuntimeTraceResult(smokeResult.getTraceResult());
            refreshRestorationScore(outputDir, analysis);
            result.setSmokeRunResult(smokeResult);
            logSmokeSummary(logger, smokeResult);
        }

        if (config.isVerifyBuild() && !config.isSmokeOnly()) {
            VerificationResult verification = verifier.verify(outputDir, config.getVerifyGoal());
            analysis.setVerificationResult(verification);
            verifier.writeReport(outputDir, verification);
            rewriteParityReport(originalArtifact, analysis, outputDir);
            refreshRestorationScore(outputDir, analysis);
            result.setVerificationResult(verification);
            log(logger, "构建验证报告: " + new File(outputDir, "verification-report.md").getAbsolutePath());
            log(logger, "构建验证: " + verification.getFailureType()
                    + " (exit " + verification.getExitCode() + ")");
            if (isVerificationFailure(verification)) {
                result.setBlockingFailure("build verification failed: "
                        + verification.getFailureType() + " (exit " + verification.getExitCode() + ")");
                return result;
            }
            if (config.isByteExactPackage() && runsPackagePhase(config.getVerifyGoal())) {
                ArtifactFidelityResult packageFidelity = verifyByteExactPackage(originalArtifact, outputDir);
                result.setPackageFidelity(packageFidelity);
                log(logger, "字节级 package 保真: exact=" + packageFidelity.isExactMatch());
                if (!packageFidelity.isExactMatch()) {
                    result.setBlockingFailure("byte-exact package fidelity failed");
                }
            }
        }

        return result;
    }

    private void logSmokeSummary(Consumer<String> logger, RuntimeSmokeRunner.SmokeRunResult smokeResult) {
        int eventCount = smokeResult.getTraceResult() == null ? 0 : smokeResult.getTraceResult().getEvents().size();
        String status = runtimeTraceSummaryStatus(smokeResult);
        log(logger, "运行时追踪: " + status + " (" + eventCount + " events)");
        if (smokeResult.getFailureMessage() != null && !smokeResult.getFailureMessage().trim().isEmpty()) {
            log(logger, "  " + smokeResult.getFailureMessage());
        }
    }

    private String runtimeTraceSummaryStatus(RuntimeSmokeRunner.SmokeRunResult smokeResult) {
        if (smokeResult == null) {
            return "FAILED";
        }
        if (smokeResult.isSuccessful()) {
            return "OK";
        }
        String runStatus = smokeResult.getRunStatus() == null ? "" : smokeResult.getRunStatus().toUpperCase(Locale.ROOT);
        if ("TRACE_COLLECTED_TIMEOUT".equals(runStatus)) {
            return "WARN";
        }
        return "FAILED";
    }

    private void refreshRestorationScore(File outputDir, JarAnalysisResult result) throws IOException {
        RestorationScore score = restorationScorer.score(result, result.getRuntimeTraceResult(),
                result.getVerificationResult());
        result.setRestorationScore(score);
        restorationScoreWriter.write(outputDir, score);
        gapSummaryWriter.write(outputDir, score);
    }

    private void rewriteParityReport(File originalArtifact, JarAnalysisResult analysis, File outputDir)
            throws IOException {
        if (originalArtifact == null || analysis == null || outputDir == null || !originalArtifact.isFile()) {
            return;
        }
        try (JarFile jarFile = new JarFile(originalArtifact)) {
            parityReporter.writeReport(jarFile, analysis, outputDir);
        }
    }

    private Path resolveTraceFile(ProjectConfig config, File outputDir) {
        String configured = config.getTraceFile();
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured.trim());
            return path.isAbsolute() ? path : outputDir.toPath().resolve(path);
        }
        return outputDir.toPath().resolve("runtime-trace.jsonl");
    }

    private File resolveTraceAgentJar() {
        String override = System.getProperty("jar2mp.traceAgentJar");
        if (override != null && !override.trim().isEmpty()) {
            return new File(override.trim());
        }

        String agentName = "jar2mp-" + Jar2MpConstants.VERSION + "-trace-agent.jar";
        File targetAgent = new File("target", agentName);
        if (targetAgent.isFile()) {
            return targetAgent;
        }

        File codeLocation = resolveCodeLocation();
        if (codeLocation != null) {
            File baseDir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
            File sibling = new File(baseDir, agentName);
            if (sibling.isFile()) {
                return sibling;
            }
        }

        File targetDir = new File("target");
        File[] candidates = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && name.contains("trace-agent"));
        if (candidates != null && candidates.length > 0) {
            Arrays.sort(candidates, Comparator.comparing(File::getName));
            return candidates[0];
        }
        return targetAgent;
    }

    private File resolveCodeLocation() {
        try {
            if (getClass().getProtectionDomain() == null
                    || getClass().getProtectionDomain().getCodeSource() == null
                    || getClass().getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return new File(uri);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isVerificationFailure(VerificationResult verification) {
        if (verification == null) {
            return true;
        }
        return verification.getExitCode() != 0 || !"NONE".equals(verification.getFailureType());
    }

    private boolean runsPackagePhase(String goal) {
        String effectiveGoal = goal == null || goal.trim().isEmpty() ? "compile" : goal.trim();
        for (String part : effectiveGoal.split("\\s+")) {
            if ("package".equals(part)
                    || "verify".equals(part)
                    || "install".equals(part)
                    || "deploy".equals(part)) {
                return true;
            }
        }
        return false;
    }

    private ArtifactFidelityResult verifyByteExactPackage(File originalArtifact, File outputDir) throws IOException {
        File packagedArtifact = findPackagedArtifact(outputDir, originalArtifact);
        if (packagedArtifact == null) {
            throw new IOException("byte-exact package artifact not found under "
                    + new File(outputDir, "target").getAbsolutePath());
        }
        ArtifactFidelityResult fidelity = artifactFidelityComparator.compare(originalArtifact, packagedArtifact);
        if (!fidelity.isExactMatch()
                && !fidelity.isArchiveBytesSame()
                && canAttemptRecordLevelRestoration(fidelity)) {
            File restoredDir = new File(new File(outputDir, "target"), "byte-exact-package-restored");
            try {
                File restoredArtifact = new ZipRecordOrderRestorer().restore(originalArtifact, packagedArtifact,
                        restoredDir);
                Files.copy(restoredArtifact.toPath(), packagedArtifact.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                fidelity = artifactFidelityComparator.compare(originalArtifact, packagedArtifact);
            } catch (IOException ignored) {
                // Keep the original comparison report when record-level restoration is not applicable.
            }
        }
        File reportDir = new File(new File(outputDir, "target"), "byte-exact-package-check");
        artifactFidelityReportWriter.write(reportDir, fidelity);
        return fidelity;
    }

    private boolean canAttemptRecordLevelRestoration(ArtifactFidelityResult fidelity) {
        if (fidelity.isContentEntriesMatch()) {
            return true;
        }
        return fidelity.getMissingEntries() == 0
                && fidelity.getExtraEntries() == 0
                && fidelity.getDifferentSha256() == 1
                && fidelity.isManifestOriginalPresent()
                && fidelity.isManifestRebuiltPresent()
                && !fidelity.isManifestSame();
    }

    private File findPackagedArtifact(File outputDir, File originalArtifact) {
        File targetDir = new File(outputDir, "target");
        if (!targetDir.isDirectory()) {
            return null;
        }
        String extension = artifactExtension(originalArtifact);
        File[] candidates = targetDir.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)
                && !isNonPrimaryArtifact(file.getName()));
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        Arrays.sort(candidates, Comparator.comparing(File::getName));
        return candidates[0];
    }

    private String artifactExtension(File artifact) {
        String name = artifact == null ? "" : artifact.getName();
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) {
            return "jar";
        }
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isNonPrimaryArtifact(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("-sources.jar")
                || lower.endsWith("-javadoc.jar")
                || lower.equals("compiler-fallback-classes.jar")
                || lower.startsWith("original-");
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    public static class PostBuildResult {
        private File preservedRawArtifact;
        private ArtifactFidelityResult rawArtifactFidelity;
        private RuntimeSmokeRunner.SmokeRunResult smokeRunResult;
        private VerificationResult verificationResult;
        private ArtifactFidelityResult packageFidelity;
        private String blockingFailure;

        public File getPreservedRawArtifact() {
            return preservedRawArtifact;
        }

        public ArtifactFidelityResult getRawArtifactFidelity() {
            return rawArtifactFidelity;
        }

        public RuntimeSmokeRunner.SmokeRunResult getSmokeRunResult() {
            return smokeRunResult;
        }

        public VerificationResult getVerificationResult() {
            return verificationResult;
        }

        public ArtifactFidelityResult getPackageFidelity() {
            return packageFidelity;
        }

        public boolean hasBlockingFailure() {
            return blockingFailure != null;
        }

        public String getBlockingFailure() {
            return blockingFailure;
        }

        private void setPreservedRawArtifact(File preservedRawArtifact) {
            this.preservedRawArtifact = preservedRawArtifact;
        }

        private void setRawArtifactFidelity(ArtifactFidelityResult rawArtifactFidelity) {
            this.rawArtifactFidelity = rawArtifactFidelity;
        }

        private void setSmokeRunResult(RuntimeSmokeRunner.SmokeRunResult smokeRunResult) {
            this.smokeRunResult = smokeRunResult;
        }

        private void setVerificationResult(VerificationResult verificationResult) {
            this.verificationResult = verificationResult;
        }

        private void setPackageFidelity(ArtifactFidelityResult packageFidelity) {
            this.packageFidelity = packageFidelity;
        }

        private void setBlockingFailure(String blockingFailure) {
            this.blockingFailure = blockingFailure;
        }
    }
}
