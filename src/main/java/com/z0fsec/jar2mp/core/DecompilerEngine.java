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
        int score = 20;
        if (trimmed.startsWith("package ")) {
            score += 20;
        }
        if (trimmed.contains(" class ") || trimmed.contains(" interface ") || trimmed.contains(" enum ")) {
            score += 20;
        }
        if (trimmed.contains("{") && trimmed.contains("}")) {
            score += 10;
        }
        if (trimmed.contains("UnsupportedOperationException")) {
            score -= 10;
        }
        if (trimmed.contains("TODO")) {
            score -= 5;
        }
        return Math.max(score, 0);
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
