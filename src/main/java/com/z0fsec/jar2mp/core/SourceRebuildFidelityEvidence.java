package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;

final class SourceRebuildFidelityEvidence {
    private final String sourceRecompiledClassBytesSame;
    private final String originalAppClasses;
    private final String recompiledClasses;
    private final String commonClasses;
    private final String sameClassBytes;
    private final String differentClassBytes;
    private final String missingRecompiledClasses;
    private final String extraRecompiledClasses;
    private final String compileFallbackClasses;

    private SourceRebuildFidelityEvidence(String sourceRecompiledClassBytesSame,
                                          String originalAppClasses,
                                          String recompiledClasses,
                                          String commonClasses,
                                          String sameClassBytes,
                                          String differentClassBytes,
                                          String missingRecompiledClasses,
                                          String extraRecompiledClasses,
                                          String compileFallbackClasses) {
        this.sourceRecompiledClassBytesSame = sourceRecompiledClassBytesSame;
        this.originalAppClasses = originalAppClasses;
        this.recompiledClasses = recompiledClasses;
        this.commonClasses = commonClasses;
        this.sameClassBytes = sameClassBytes;
        this.differentClassBytes = differentClassBytes;
        this.missingRecompiledClasses = missingRecompiledClasses;
        this.extraRecompiledClasses = extraRecompiledClasses;
        this.compileFallbackClasses = compileFallbackClasses;
    }

    static SourceRebuildFidelityEvidence read(File outputDir) throws IOException {
        if (outputDir == null) {
            return null;
        }
        File summary = new File(outputDir, "source-rebuild-fidelity-summary.csv");
        if (!summary.isFile()) {
            return null;
        }
        String[] lines = IoUtils.readFileToString(summary).split("\\r?\\n");
        if (lines.length < 2) {
            return null;
        }
        String[] headers = splitCsvLine(lines[0]);
        String[] values = splitCsvLine(lines[1]);
        return new SourceRebuildFidelityEvidence(
                columnValue(headers, values, "source_recompiled_class_bytes_same"),
                columnValue(headers, values, "original_app_classes"),
                columnValue(headers, values, "recompiled_classes"),
                columnValue(headers, values, "common_classes"),
                columnValue(headers, values, "same_class_bytes"),
                columnValue(headers, values, "different_class_bytes"),
                columnValue(headers, values, "missing_recompiled_classes"),
                columnValue(headers, values, "extra_recompiled_classes"),
                columnValue(headers, values, "compile_fallback_classes"));
    }

    String getSourceRecompiledClassBytesSame() {
        return sourceRecompiledClassBytesSame;
    }

    String getOriginalAppClasses() {
        return originalAppClasses;
    }

    String getRecompiledClasses() {
        return recompiledClasses;
    }

    String getCommonClasses() {
        return commonClasses;
    }

    String getSameClassBytes() {
        return sameClassBytes;
    }

    String getDifferentClassBytes() {
        return differentClassBytes;
    }

    String getMissingRecompiledClasses() {
        return missingRecompiledClasses;
    }

    String getExtraRecompiledClasses() {
        return extraRecompiledClasses;
    }

    String getCompileFallbackClasses() {
        return compileFallbackClasses;
    }

    private static String[] splitCsvLine(String line) {
        return line == null ? new String[0] : line.split(",", -1);
    }

    private static String columnValue(String[] headers, String[] values, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equals(headers[i]) && i < values.length) {
                return safe(values[i]);
            }
        }
        return "(missing)";
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value.trim();
    }
}
