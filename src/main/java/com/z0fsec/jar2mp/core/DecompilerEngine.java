package com.z0fsec.jar2mp.core;

public interface DecompilerEngine {

    String getName();

    Result decompile(byte[] classBytes, String className);

    static boolean isStubSource(String source) {
        if (source == null) {
            return true;
        }
        String trimmed = source.trim();
        return trimmed.isEmpty() || trimmed.startsWith("// Failed to decompile:");
    }

    static int scoreSource(String source) {
        if (isStubSource(source)) {
            return 0;
        }

        String trimmed = source.trim();
        String structuralSource = stripLeadingComments(trimmed);
        int score = 20;
        if (structuralSource.startsWith("package ")) {
            score += 20;
        }
        if (structuralSource.contains(" class ")
                || structuralSource.contains(" interface ")
                || structuralSource.contains(" enum ")) {
            score += 20;
        }
        if (structuralSource.contains("{") && structuralSource.contains("}")) {
            score += 10;
        }
        if (trimmed.contains("UnsupportedOperationException")) {
            score -= 10;
        }
        if (trimmed.contains("Couldn't be decompiled")) {
            score -= 30;
        }
        if (trimmed.contains("Unavailable Anonymous Inner Class")) {
            score -= 40;
        }
        if (trimmed.contains(".$SwitchMap$")) {
            score -= 25;
        }
        if (trimmed.contains("Unable to fully structure code")) {
            score -= 35;
        }
        if (trimmed.contains("WARNING - void declaration")) {
            score -= 35;
        }
        if (trimmed.contains("Loose catch block")) {
            score -= 30;
        }
        if (trimmed.contains("** ")) {
            score -= 40;
        }
        if (trimmed.contains("TODO")) {
            score -= 5;
        }
        return Math.max(score, 0);
    }

    static String stripLeadingComments(String source) {
        String value = source == null ? "" : source.trim();
        boolean changed;
        do {
            changed = false;
            if (value.startsWith("/*")) {
                int end = value.indexOf("*/");
                if (end >= 0) {
                    value = value.substring(end + 2).trim();
                    changed = true;
                }
            }
            if (value.startsWith("//")) {
                int end = value.indexOf('\n');
                if (end >= 0) {
                    value = value.substring(end + 1).trim();
                    changed = true;
                }
            }
        } while (changed);
        return value;
    }

    static String failureComment(String className, String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("// Failed to decompile: ").append(className).append("\n");
        if (reason != null && !reason.trim().isEmpty()) {
            builder.append("// ").append(reason.trim()).append("\n");
        }
        return builder.toString();
    }

    final class Result {
        private final String engineName;
        private final boolean success;
        private final String source;
        private final String failureMessage;
        private final int qualityScore;

        private Result(String engineName, boolean success, String source, String failureMessage, int qualityScore) {
            this.engineName = engineName;
            this.success = success;
            this.source = source;
            this.failureMessage = failureMessage;
            this.qualityScore = qualityScore;
        }

        public static Result success(String engineName, String source, int qualityScore) {
            return new Result(engineName, true, source, null, qualityScore);
        }

        public static Result failure(String engineName, String source, String failureMessage, int qualityScore) {
            return new Result(engineName, false, source, failureMessage, qualityScore);
        }

        public String getEngineName() {
            return engineName;
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

        public int getQualityScore() {
            return qualityScore;
        }
    }
}
