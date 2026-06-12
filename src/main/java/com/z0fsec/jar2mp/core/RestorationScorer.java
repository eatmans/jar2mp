package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.model.VerificationError;
import com.z0fsec.jar2mp.model.VerificationResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class RestorationScorer {

    private static final String SOURCE = "source";
    private static final String RESOURCE = "resource";
    private static final String RUNTIME = "runtime";
    private static final String VERIFICATION = "verification";
    private static final int SOURCE_WEIGHT = 40;
    private static final int RESOURCE_WEIGHT = 20;
    private static final int RUNTIME_WEIGHT = 20;
    private static final int VERIFICATION_WEIGHT = 20;
    private static final String SKIPPED = "(skipped)";
    private static final String SPRING_BOOT_FILESYSTEM_PROVIDER_SERVICE =
            "META-INF/services/java.nio.file.spi.FileSystemProvider";

    public RestorationScore score(JarAnalysisResult analysis, RuntimeTraceResult runtimeTraceResult,
                                  VerificationResult verificationResult) {
        JarAnalysisResult effectiveAnalysis = analysis == null ? new JarAnalysisResult() : analysis;
        RuntimeTraceResult effectiveRuntime = runtimeTraceResult != null ? runtimeTraceResult
                : effectiveAnalysis.getRuntimeTraceResult();
        VerificationResult effectiveVerification = verificationResult != null ? verificationResult
                : effectiveAnalysis.getVerificationResult();

        RestorationScore score = new RestorationScore();

        int sourceScore = scoreSource(effectiveAnalysis, score);
        int resourceScore = scoreResources(effectiveAnalysis, score);
        int runtimeScore = scoreRuntime(effectiveAnalysis, effectiveRuntime, score);
        int verificationScore = scoreVerification(effectiveVerification, score);

        score.putBucket(SOURCE, sourceScore);
        score.putBucket(RESOURCE, resourceScore);
        score.putBucket(RUNTIME, runtimeScore);
        score.putBucket(VERIFICATION, verificationScore);

        int overall = capPerfectScoreWhenGapsExist(weightedAverage(sourceScore, resourceScore,
                runtimeScore, verificationScore), score);
        score.setOverall(overall);
        return score;
    }

    private int scoreSource(JarAnalysisResult analysis, RestorationScore score) {
        int totalClasses = scoredSourceClassCount(analysis.getClassFiles());
        if (totalClasses == 0) {
            return 100;
        }

        int restored = 0;
        Map<String, DecompileFinding> findingsByClass = decompileFindingsByClass(analysis.getDecompileFindings());
        for (String classPath : safeFindings(analysis.getClassFiles())) {
            if (!isScoredSourceClass(classPath)) {
                continue;
            }
            DecompileFinding finding = findingsByClass.get(classPath);
            if (finding != null && !finding.hasRetainedClassPath()) {
                restored++;
            } else {
                score.addGap("decompile", safeValue(classPath), bucketImpact(SOURCE_WEIGHT, totalClasses, 1));
            }
        }

        return percent(restored, totalClasses);
    }

    private Map<String, DecompileFinding> decompileFindingsByClass(Collection<DecompileFinding> findings) {
        Map<String, DecompileFinding> byClass = new LinkedHashMap<>();
        for (DecompileFinding finding : safeFindings(findings)) {
            if (finding == null || finding.getClassPath() == null) {
                continue;
            }
            byClass.put(finding.getClassPath(), finding);
        }
        return byClass;
    }

    private int scoreResources(JarAnalysisResult analysis, RestorationScore score) {
        Collection<ResourceFinding> findings = safeFindings(analysis.getResourceFindings());
        List<ResourceFinding> scoredFindings = new ArrayList<>();
        for (ResourceFinding finding : findings) {
            if (!isIgnorableResourceFinding(finding)) {
                scoredFindings.add(finding);
            }
        }

        int total = scoredFindings.size();
        if (total == 0) {
            return 100;
        }

        int restored = 0;
        for (ResourceFinding finding : scoredFindings) {
            if (finding == null) {
                continue;
            }
            if (!isResourceRestored(finding)) {
                score.addGap(normalizeCategory(finding.getCategory()), safeValue(finding.getOriginalPath()),
                        bucketImpact(RESOURCE_WEIGHT, total, 1));
            } else {
                restored++;
            }
        }

        return percent(restored, total);
    }

    private boolean isResourceRestored(ResourceFinding finding) {
        if (finding.getCopyStatus() != null && finding.getCopyStatus() != ResourceFinding.CopyStatus.PENDING) {
            return finding.getCopyStatus() == ResourceFinding.CopyStatus.COPIED
                    || finding.getCopyStatus() == ResourceFinding.CopyStatus.ARCHIVED;
        }
        if (SKIPPED.equalsIgnoreCase(safeValue(finding.getTargetPath()))) {
            return false;
        }
        String note = safeValue(finding.getNote()).toLowerCase(Locale.ROOT);
        return !note.contains("resource not copied");
    }

    private boolean isIgnorableResourceFinding(ResourceFinding finding) {
        if (finding == null) {
            return false;
        }
        ResourceFinding.Category category = finding.getCategory();
        return category == ResourceFinding.Category.NESTED_LIBRARY
                || category == ResourceFinding.Category.META_INF_RUNTIME
                || isSkippedSpringBootLoaderService(finding);
    }

    private boolean isSkippedSpringBootLoaderService(ResourceFinding finding) {
        return finding.getCategory() == ResourceFinding.Category.SPI
                && SPRING_BOOT_FILESYSTEM_PROVIDER_SERVICE.equals(safeValue(finding.getOriginalPath()))
                && SKIPPED.equalsIgnoreCase(safeValue(finding.getTargetPath()));
    }

    private int scoreRuntime(JarAnalysisResult analysis, RuntimeTraceResult runtimeTraceResult,
                             RestorationScore score) {
        if (runtimeTraceResult == null || runtimeTraceResult.getEvents().isEmpty()) {
            score.addGap("runtime_trace",
                    "Runtime trace data has not been captured; this is an observation gap, not a byte-level package fidelity failure.",
                    RUNTIME_WEIGHT);
            return 0;
        }

        Set<String> kinds = runtimeKinds(runtimeTraceResult);
        Set<String> expectedKinds = expectedRuntimeKinds(analysis);
        if (expectedKinds.isEmpty()) {
            expectedKinds.addAll(kinds);
        }
        if (expectedKinds.isEmpty()) {
            return 100;
        }

        int present = 0;
        for (String kind : expectedKinds) {
            if (kinds.contains(kind)) {
                present++;
            } else {
                score.addGap(kind, "No runtime evidence recorded for statically detected " + kind + " usage.",
                        bucketImpact(RUNTIME_WEIGHT, expectedKinds.size(), 1));
            }
        }
        int evidenceScore = percent(present, expectedKinds.size());
        return applySmokeRunStatus(analysis, evidenceScore, score);
    }

    private int applySmokeRunStatus(JarAnalysisResult analysis, int evidenceScore, RestorationScore score) {
        RuntimeSmokeRunner.SmokeRunResult smokeResult = analysis == null ? null : analysis.getRuntimeSmokeResult();
        if (smokeResult == null) {
            return evidenceScore;
        }

        String status = safeValue(smokeResult.getRunStatus()).toUpperCase(Locale.ROOT);
        if (status.isEmpty() || "EXIT_ZERO".equals(status)) {
            return evidenceScore;
        }
        if ("TRACE_COLLECTED_TIMEOUT".equals(status)) {
            score.addGap("runtime_status",
                    "Runtime trace timed out after collecting events; clean exit and health are not verified.",
                    bucketImpact(RUNTIME_WEIGHT, 5, 1));
            return Math.min(evidenceScore, 80);
        }

        score.addGap("runtime_status",
                "Runtime smoke run did not complete successfully: " + safeValue(smokeResult.getRunStatus()) + ".",
                RUNTIME_WEIGHT);
        return 0;
    }

    private Set<String> runtimeKinds(RuntimeTraceResult runtimeTraceResult) {
        Set<String> kinds = new LinkedHashSet<>();
        for (RuntimeTraceEvent event : runtimeTraceResult.getEvents()) {
            if (event == null || event.getKind() == null) {
                continue;
            }
            kinds.add(event.getKind().toLowerCase(Locale.ROOT));
        }
        return kinds;
    }

    private Set<String> expectedRuntimeKinds(JarAnalysisResult analysis) {
        Set<String> expectedKinds = new LinkedHashSet<>();
        if (analysis == null || analysis.getSourceFile() == null || !analysis.getSourceFile().isFile()) {
            return expectedKinds;
        }

        try (JarFile jarFile = new JarFile(analysis.getSourceFile())) {
            for (String classPath : safeFindings(analysis.getClassFiles())) {
                String rawEntryPath = analysis.getClassPathMapping().get(classPath);
                if (rawEntryPath == null) {
                    rawEntryPath = classPath;
                }
                JarEntry entry = jarFile.getJarEntry(rawEntryPath);
                if (entry == null) {
                    continue;
                }
                try {
                    BytecodeFingerprint fingerprint = BytecodeFingerprint.fromClassFile(readAllBytes(jarFile, entry));
                    collectExpectedRuntimeKinds(fingerprint, expectedKinds);
                } catch (Exception ignored) {
                    // Scoring should remain best-effort when a class cannot be fingerprinted.
                }
            }
        } catch (IOException ignored) {
            return expectedKinds;
        }
        return expectedKinds;
    }

    private void collectExpectedRuntimeKinds(BytecodeFingerprint fingerprint, Set<String> expectedKinds) {
        for (BytecodeFingerprint.MethodFingerprint method : fingerprint.getMethodsByKey().values()) {
            for (String call : method.getMethodCalls()) {
                addExpectedRuntimeKind(call, expectedKinds);
            }
        }
    }

    private void addExpectedRuntimeKind(String call, Set<String> expectedKinds) {
        if (call == null) {
            return;
        }
        if (isReflectionCall(call)) {
            expectedKinds.add("reflection");
        }
        if (isResourceCall(call)) {
            expectedKinds.add("resource");
        }
        if (isFileCall(call)) {
            expectedKinds.add("file");
        }
        if (isSocketCall(call)) {
            expectedKinds.add("socket");
        }
    }

    private boolean isReflectionCall(String call) {
        return call.startsWith("java/lang/Class.forName")
                || call.startsWith("java/lang/Class.getMethod")
                || call.startsWith("java/lang/Class.getDeclaredMethod")
                || call.startsWith("java/lang/Class.getField")
                || call.startsWith("java/lang/Class.getDeclaredField")
                || call.startsWith("java/lang/reflect/Method.invoke");
    }

    private boolean isResourceCall(String call) {
        return call.startsWith("java/lang/Class.getResource")
                || call.startsWith("java/lang/ClassLoader.getResource");
    }

    private boolean isFileCall(String call) {
        return call.startsWith("java/io/FileInputStream.<init>")
                || call.startsWith("java/io/FileOutputStream.<init>")
                || call.startsWith("java/nio/file/Files.newInputStream")
                || call.startsWith("java/nio/file/Files.newOutputStream")
                || call.startsWith("java/nio/file/Files.newBufferedReader")
                || call.startsWith("java/nio/file/Files.readAllLines")
                || call.startsWith("java/nio/file/Files.lines");
    }

    private boolean isSocketCall(String call) {
        return call.startsWith("java/net/Socket.<init>")
                || call.startsWith("java/net/URLConnection.connect")
                || call.startsWith("java/net/URLConnection.getInputStream")
                || call.startsWith("java/net/URLConnection.getOutputStream")
                || call.startsWith("java/net/HttpURLConnection.connect")
                || call.startsWith("java/net/HttpURLConnection.getInputStream")
                || call.startsWith("java/net/HttpURLConnection.getOutputStream");
    }

    private byte[] readAllBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private int scoreVerification(VerificationResult verificationResult, RestorationScore score) {
        if (verificationResult == null) {
            score.addGap(VERIFICATION, "Verification has not been run yet.", VERIFICATION_WEIGHT);
            return 40;
        }

        if (verificationResult.isTimedOut()) {
            score.addGap(VERIFICATION, "Verification timed out.", VERIFICATION_WEIGHT);
            return 0;
        }

        if (verificationResult.getExitCode() == 0
                && (verificationResult.getFailureType() == null
                || "NONE".equalsIgnoreCase(verificationResult.getFailureType()))) {
            return 100;
        }

        score.addGap(VERIFICATION, safeValue(verificationResult.getFailureType()), VERIFICATION_WEIGHT);
        String errorSummary = summarizeVerificationErrors(verificationResult);
        if (!errorSummary.isEmpty()) {
            score.addGap("verification_errors", errorSummary, VERIFICATION_WEIGHT);
        }
        return 0;
    }

    private String summarizeVerificationErrors(VerificationResult verificationResult) {
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (VerificationError error : verificationResult.getErrors()) {
            String category = error.getCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "UNKNOWN";
            }
            Integer count = categories.get(category);
            categories.put(category, count == null ? 1 : count + 1);
        }
        if (categories.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : categories.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    private int weightedAverage(int sourceScore, int resourceScore, int runtimeScore, int verificationScore) {
        double total = sourceScore * (SOURCE_WEIGHT / 100.0)
                + resourceScore * (RESOURCE_WEIGHT / 100.0)
                + runtimeScore * (RUNTIME_WEIGHT / 100.0)
                + verificationScore * (VERIFICATION_WEIGHT / 100.0);
        return (int) Math.round(total);
    }

    private int capPerfectScoreWhenGapsExist(int overall, RestorationScore score) {
        if (overall >= 100 && score != null && !score.getGaps().isEmpty()) {
            return 99;
        }
        return overall;
    }

    private int percent(int restored, int total) {
        if (total <= 0) {
            return 100;
        }
        return (int) Math.round((restored * 100.0) / total);
    }

    private int bucketImpact(int bucketWeight, int totalItems, int missingItems) {
        if (totalItems <= 0 || missingItems <= 0) {
            return bucketWeight;
        }
        int impact = (int) Math.round(bucketWeight * (missingItems / (double) totalItems));
        return Math.max(1, impact);
    }

    private int scoredSourceClassCount(Collection<String> classFiles) {
        int total = 0;
        for (String classFile : safeFindings(classFiles)) {
            if (isScoredSourceClass(classFile)) {
                total++;
            }
        }
        return total;
    }

    private boolean isScoredSourceClass(String classPath) {
        String value = safeValue(classPath);
        if (value.endsWith("module-info.class")) {
            return false;
        }
        return true;
    }

    private String normalizeCategory(ResourceFinding.Category category) {
        if (category == null) {
            return RESOURCE;
        }
        return category.name().toLowerCase(Locale.ROOT);
    }

    private <T> List<T> safeFindings(Collection<T> findings) {
        if (findings == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(findings);
    }

    private int safeSize(Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
