package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DecompilerBridge {

    private final List<DecompilerEngine> engines;

    public DecompilerBridge(ProjectConfig config) {
        this(config, defaultEngines(config));
    }

    public DecompilerBridge(ProjectConfig config, List<DecompilerEngine> engines) {
        if (engines == null || engines.isEmpty()) {
            this.engines = defaultEngines(config);
        } else {
            this.engines = Collections.unmodifiableList(new ArrayList<>(engines));
        }
    }

    private static List<DecompilerEngine> defaultEngines(ProjectConfig config) {
        List<DecompilerEngine> defaultEngines = new ArrayList<>();
        defaultEngines.add(new CfrDecompilerEngine(config));
        defaultEngines.add(new FernflowerDecompilerEngine(config));
        return Collections.unmodifiableList(defaultEngines);
    }

    public static class DecompileResult {
        private final boolean success;
        private final String source;
        private final String failureMessage;
        private final String selectedEngine;
        private final String fallbackReason;
        private final int selectedEngineQuality;

        private DecompileResult(boolean success, String source, String failureMessage,
                                String selectedEngine, String fallbackReason, int selectedEngineQuality) {
            this.success = success;
            this.source = source;
            this.failureMessage = failureMessage;
            this.selectedEngine = selectedEngine;
            this.fallbackReason = fallbackReason;
            this.selectedEngineQuality = selectedEngineQuality;
        }

        public static DecompileResult success(String source) {
            return new DecompileResult(true, source, null, null, null, 0);
        }

        public static DecompileResult success(String source, String selectedEngine,
                                              String fallbackReason, int selectedEngineQuality) {
            return new DecompileResult(true, source, null, selectedEngine, fallbackReason, selectedEngineQuality);
        }

        public static DecompileResult failure(String source, String failureMessage) {
            return new DecompileResult(false, source, failureMessage, null, null, 0);
        }

        public static DecompileResult failure(String source, String failureMessage,
                                              String selectedEngine, String fallbackReason, int selectedEngineQuality) {
            return new DecompileResult(false, source, failureMessage, selectedEngine, fallbackReason, selectedEngineQuality);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getSource() {
            return source;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public String getSelectedEngine() {
            return selectedEngine;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public int getSelectedEngineQuality() {
            return selectedEngineQuality;
        }
    }

    /**
     * Decompile a single class file given its raw bytes.
     * Returns the decompiled Java source code, or an error comment.
     */
    public String decompile(byte[] classBytes, String className) {
        return decompileDetailed(classBytes, className).getSource();
    }

    public DecompileResult decompileDetailed(byte[] classBytes, String className) {
        List<DecompilerEngine.Result> results = new ArrayList<>();
        for (DecompilerEngine engine : engines) {
            try {
                results.add(engine.decompile(classBytes, className));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(DecompilerEngine.Result.failure(
                        engine.getName(),
                        DecompilerEngine.failureComment(className, message),
                        message,
                        0));
            }
        }

        DecompilerEngine.Result selected = selectUsableResult(results);
        if (selected != null) {
            String fallbackReason = buildFallbackReason(results, selected);
            return DecompileResult.success(selected.getSource(), selected.getEngineName(), fallbackReason,
                    selected.getQualityScore());
        }

        DecompilerEngine.Result bestFailure = selectBestResult(results);
        String failureMessage = buildFailureMessage(results);
        String source = buildFailureSource(className, bestFailure, failureMessage);
        return DecompileResult.failure(source, failureMessage,
                bestFailure == null ? null : bestFailure.getEngineName(),
                failureMessage,
                bestFailure == null ? 0 : bestFailure.getQualityScore());
    }

    private DecompilerEngine.Result selectUsableResult(List<DecompilerEngine.Result> results) {
        DecompilerEngine.Result best = null;
        for (DecompilerEngine.Result result : results) {
            if (!isUsable(result)) {
                continue;
            }
            if (best == null || result.getQualityScore() > best.getQualityScore()) {
                best = result;
            }
        }
        return best;
    }

    private DecompilerEngine.Result selectBestResult(List<DecompilerEngine.Result> results) {
        DecompilerEngine.Result best = null;
        for (DecompilerEngine.Result result : results) {
            if (best == null || result.getQualityScore() > best.getQualityScore()) {
                best = result;
            }
        }
        return best;
    }

    private boolean isUsable(DecompilerEngine.Result result) {
        return result != null && result.isSuccess() && !DecompilerEngine.isStubSource(result.getSource());
    }

    private String buildFallbackReason(List<DecompilerEngine.Result> results, DecompilerEngine.Result selected) {
        if (results.isEmpty() || selected == null) {
            return null;
        }

        DecompilerEngine.Result first = results.get(0);
        if (first == null || selected.getEngineName().equals(first.getEngineName())) {
            return null;
        }

        if (!isUsable(first)) {
            return first.getEngineName() + " returned " + describeOutcome(first)
                    + "; selected " + selected.getEngineName();
        }

        return selected.getEngineName() + " scored " + selected.getQualityScore()
                + " above " + first.getEngineName() + " (" + first.getQualityScore() + ")";
    }

    private String describeOutcome(DecompilerEngine.Result result) {
        if (result == null) {
            return "no result";
        }
        if (!result.isSuccess()) {
            return "error: " + safe(result.getFailureMessage());
        }
        if (DecompilerEngine.isStubSource(result.getSource())) {
            return "stub-only output";
        }
        return "usable output";
    }

    private String buildFailureMessage(List<DecompilerEngine.Result> results) {
        if (results.isEmpty()) {
            return "No decompiler engines were configured.";
        }

        StringBuilder builder = new StringBuilder("All engines failed: ");
        for (int i = 0; i < results.size(); i++) {
            DecompilerEngine.Result result = results.get(i);
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(result.getEngineName()).append(" -> ").append(describeOutcome(result));
        }
        return builder.toString();
    }

    private String buildFailureSource(String className, DecompilerEngine.Result bestFailure, String failureMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append(DecompilerEngine.failureComment(className, failureMessage));
        if (bestFailure != null) {
            builder.append("// Selected engine: ").append(bestFailure.getEngineName()).append("\n");
            builder.append("// Fallback reason: ").append(safe(failureMessage)).append("\n");
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Check if a class entry is an inner class (contains $ in the filename).
     */
    public static boolean isInnerClass(String entryPath) {
        String fileName = entryPath;
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);
        return fileName.contains("$");
    }

    /**
     * Get the outer class path for an inner class.
     * e.g., com/example/Foo$Bar.class -> com/example/Foo.class
     */
    public static String getOuterClassPath(String innerClassPath) {
        int dollarPos = innerClassPath.indexOf('$');
        if (dollarPos < 0) return innerClassPath;
        return innerClassPath.substring(0, dollarPos) + ".class";
    }
}
