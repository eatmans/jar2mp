package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.model.VerificationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RestorationScorer {

    private static final String SOURCE = "source";
    private static final String RESOURCE = "resource";
    private static final String RUNTIME = "runtime";
    private static final String VERIFICATION = "verification";
    private static final int SOURCE_WEIGHT = 40;
    private static final int RESOURCE_WEIGHT = 20;
    private static final int RUNTIME_WEIGHT = 20;
    private static final int VERIFICATION_WEIGHT = 20;
    private static final String[] TRACE_KINDS = {"reflection", "resource", "file", "socket"};
    private static final String SKIPPED = "(skipped)";

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
        int runtimeScore = scoreRuntime(effectiveRuntime, score);
        int verificationScore = scoreVerification(effectiveVerification, score);

        score.putBucket(SOURCE, sourceScore);
        score.putBucket(RESOURCE, resourceScore);
        score.putBucket(RUNTIME, runtimeScore);
        score.putBucket(VERIFICATION, verificationScore);

        int overall = weightedAverage(sourceScore, resourceScore, runtimeScore, verificationScore);
        score.setOverall(overall);
        return score;
    }

    private int scoreSource(JarAnalysisResult analysis, RestorationScore score) {
        int totalClasses = safeSize(analysis.getClassFiles());
        if (totalClasses == 0) {
            return 100;
        }

        int restored = 0;
        for (DecompileFinding finding : safeFindings(analysis.getDecompileFindings())) {
            if (finding == null) {
                continue;
            }
            if (!finding.hasRetainedClassPath()) {
                restored++;
            } else {
                score.addGap("decompile", safeValue(finding.getClassPath()), bucketImpact(SOURCE_WEIGHT, totalClasses, 1));
            }
        }

        return percent(restored, totalClasses);
    }

    private int scoreResources(JarAnalysisResult analysis, RestorationScore score) {
        Collection<ResourceFinding> findings = safeFindings(analysis.getResourceFindings());
        int total = findings.size();
        if (total == 0) {
            return 100;
        }

        int restored = 0;
        for (ResourceFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            if (SKIPPED.equalsIgnoreCase(safeValue(finding.getTargetPath()))) {
                score.addGap(normalizeCategory(finding.getCategory()), safeValue(finding.getOriginalPath()),
                        bucketImpact(RESOURCE_WEIGHT, total, 1));
            } else {
                restored++;
            }
        }

        return percent(restored, total);
    }

    private int scoreRuntime(RuntimeTraceResult runtimeTraceResult, RestorationScore score) {
        if (runtimeTraceResult == null || runtimeTraceResult.getEvents().isEmpty()) {
            score.addGap("reflection", "No runtime trace data captured.", RUNTIME_WEIGHT);
            return 0;
        }

        Set<String> kinds = new LinkedHashSet<>();
        for (RuntimeTraceEvent event : runtimeTraceResult.getEvents()) {
            if (event == null || event.getKind() == null) {
                continue;
            }
            kinds.add(event.getKind().toLowerCase(Locale.ROOT));
        }

        int present = 0;
        for (String kind : TRACE_KINDS) {
            if (kinds.contains(kind)) {
                present++;
            } else {
                score.addGap(kind, "No runtime evidence recorded for " + kind + ".", RUNTIME_WEIGHT / 2);
            }
        }
        return percent(present, TRACE_KINDS.length);
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
        return 0;
    }

    private int weightedAverage(int sourceScore, int resourceScore, int runtimeScore, int verificationScore) {
        double total = sourceScore * (SOURCE_WEIGHT / 100.0)
                + resourceScore * (RESOURCE_WEIGHT / 100.0)
                + runtimeScore * (RUNTIME_WEIGHT / 100.0)
                + verificationScore * (VERIFICATION_WEIGHT / 100.0);
        return (int) Math.round(total);
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
