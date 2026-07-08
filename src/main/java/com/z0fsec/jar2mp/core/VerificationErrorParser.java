package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationError;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationErrorParser {

    public static final String MISSING_SYMBOL = "MISSING_SYMBOL";
    public static final String MISSING_PACKAGE = "MISSING_PACKAGE";
    public static final String INCOMPATIBLE_TYPES = "INCOMPATIBLE_TYPES";
    public static final String GENERIC_INFERENCE = "GENERIC_INFERENCE";
    public static final String SYNTAX = "SYNTAX";
    public static final String SOURCE_CONFLICT = "SOURCE_CONFLICT";
    public static final String WARNING_AS_ERROR = "WARNING_AS_ERROR";
    public static final String COMPILATION = "COMPILATION";

    private static final Pattern JAVAC_LOCATION = Pattern.compile(
            "^\\[ERROR\\]\\s+(.+?\\.java):\\[(\\d+),(\\d+)\\]\\s*(.*)$");
    private static final Pattern JAVAC_FILE_MESSAGE = Pattern.compile(
            "^\\[ERROR\\]\\s+(.+?\\.java):\\s*(.*)$");
    private static final Pattern ERROR_PREFIX = Pattern.compile("^\\[ERROR\\]\\s*");

    public List<VerificationError> parse(File projectDir, String stdout, String stderr) {
        List<VerificationError> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String combined = nullToEmpty(stdout) + "\n" + nullToEmpty(stderr);
        String[] lines = combined.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            VerificationError error = parseErrorLine(projectDir, lines, i);
            if (error == null) {
                continue;
            }
            String key = error.getSourcePath() + ":" + error.getLine() + ":" + error.getColumn()
                    + ":" + error.getMessage();
            if (seen.add(key)) {
                errors.add(error);
            }
        }
        return errors;
    }

    private VerificationError parseErrorLine(File projectDir, String[] lines, int index) {
        String line = lines[index];
        Matcher locationMatcher = JAVAC_LOCATION.matcher(line);
        if (locationMatcher.find()) {
            return buildError(projectDir,
                    locationMatcher.group(1),
                    parseInt(locationMatcher.group(2)),
                    parseInt(locationMatcher.group(3)),
                    mergeContinuation(locationMatcher.group(4), lines, index + 1));
        }

        Matcher fileMatcher = JAVAC_FILE_MESSAGE.matcher(line);
        if (fileMatcher.find()) {
            return buildError(projectDir,
                    fileMatcher.group(1),
                    0,
                    0,
                    mergeContinuation(fileMatcher.group(2), lines, index + 1));
        }
        return null;
    }

    private VerificationError buildError(File projectDir, String sourcePath, int line, int column, String message) {
        VerificationError error = new VerificationError();
        String normalizedPath = normalizePath(projectDir, sourcePath);
        error.setSourcePath(normalizedPath);
        error.setClassName(classNameFromPath(normalizedPath));
        error.setLine(line);
        error.setColumn(column);
        error.setMessage(cleanMessage(message));
        error.setCategory(classify(error.getMessage()));
        return error;
    }

    private String mergeContinuation(String firstMessage, String[] lines, int start) {
        StringBuilder builder = new StringBuilder(nullToEmpty(firstMessage).trim());
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (JAVAC_LOCATION.matcher(line).find() || JAVAC_FILE_MESSAGE.matcher(line).find()) {
                break;
            }
            if (!line.startsWith("[ERROR]")) {
                break;
            }
            String continuation = ERROR_PREFIX.matcher(line).replaceFirst("").trim();
            if (continuation.isEmpty() || continuation.startsWith("->") || continuation.startsWith("To see ")) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(continuation);
        }
        return builder.toString();
    }

    private String classify(String message) {
        String value = nullToEmpty(message);
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.contains("找不到符号") || lower.contains("cannot find symbol")) {
            return MISSING_SYMBOL;
        }
        if ((value.contains("程序包") && value.contains("不存在"))
                || (lower.contains("package ") && lower.contains(" does not exist"))) {
            return MISSING_PACKAGE;
        }
        if (value.contains("不兼容的类型") || lower.contains("incompatible types")) {
            return INCOMPATIBLE_TYPES;
        }
        if (value.contains("推论变量")
                || (value.contains("无法将") && value.contains("应用到给定类型"))
                || lower.contains("inference variable")
                || lower.contains("cannot be applied to given types")) {
            return GENERIC_INFERENCE;
        }
        if (value.contains("需要')'或','")
                || value.contains("有 'catch', 但是没有 'try'")
                || lower.contains("illegal start")
                || lower.contains("not a statement")
                || lower.contains("';' expected")
                || lower.contains("class, interface, or enum expected")) {
            return SYNTAX;
        }
        if (value.contains("错误的源文件")
                || lower.contains("bad source file")
                || lower.contains("duplicate class")) {
            return SOURCE_CONFLICT;
        }
        if (value.contains("发现警告")
                || lower.contains("warnings found")
                || lower.contains("warning found")) {
            return WARNING_AS_ERROR;
        }
        return COMPILATION;
    }

    private String normalizePath(File projectDir, String sourcePath) {
        if (sourcePath == null) {
            return "";
        }
        String normalized = sourcePath.replace('\\', '/');
        if (projectDir != null) {
            String base = projectDir.getAbsolutePath().replace('\\', '/');
            if (normalized.startsWith(base + "/")) {
                normalized = normalized.substring(base.length() + 1);
            }
        }
        return normalized;
    }

    private String classNameFromPath(String sourcePath) {
        if (sourcePath == null) {
            return "";
        }
        String value = sourcePath.replace('\\', '/');
        String marker = "src/main/java/";
        int markerIndex = value.indexOf(marker);
        if (markerIndex >= 0) {
            value = value.substring(markerIndex + marker.length());
        }
        if (value.endsWith(".java")) {
            value = value.substring(0, value.length() - ".java".length());
        }
        return value.replace('/', '.');
    }

    private String cleanMessage(String message) {
        return nullToEmpty(message).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
