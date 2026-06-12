package com.z0fsec.jar2mp.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NestedClassSourceMerger {

    private static final String NAMED_INNER_DECLARATION_PATTERN =
            "(?:class|interface|enum|@interface)\\s+%s\\b";

    interface InnerSourceProvider {
        String sourceFor(String classPath) throws IOException;
    }

    static class MergeResult {
        private final String source;
        private final List<String> mergedClassPaths;
        private final List<String> unresolvedClassPaths;

        MergeResult(String source, List<String> mergedClassPaths, List<String> unresolvedClassPaths) {
            this.source = source;
            this.mergedClassPaths = mergedClassPaths;
            this.unresolvedClassPaths = unresolvedClassPaths;
        }

        String getSource() {
            return source;
        }

        List<String> getMergedClassPaths() {
            return mergedClassPaths;
        }

        List<String> getUnresolvedClassPaths() {
            return unresolvedClassPaths;
        }
    }

    MergeResult mergeMissingNamedInnerSources(String outerSource,
                                              String outerClassPath,
                                              Collection<String> classFiles,
                                              InnerSourceProvider sourceProvider) throws IOException {
        List<String> missingClassPaths = missingNamedInnerClassPaths(outerSource, outerClassPath, classFiles);
        List<String> directNamedInnerClassPaths = directNamedInnerClassPaths(outerClassPath, classFiles);
        if (missingClassPaths.isEmpty() && directNamedInnerClassPaths.isEmpty()) {
            return new MergeResult(outerSource, new ArrayList<>(), new ArrayList<>());
        }

        String mergedSource = outerSource;
        List<String> merged = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        String outerSimpleName = simpleClassName(outerClassPath);
        for (String innerClassPath : missingClassPaths) {
            String innerSimpleName = innerSimpleName(innerClassPath);
            String innerSource = sourceProvider == null ? null : sourceProvider.sourceFor(innerClassPath);
            String nestedSource = normalizeInnerSource(innerSource, outerSimpleName, innerSimpleName);
            if (nestedSource == null) {
                unresolved.add(innerClassPath);
                continue;
            }
            if (hasSyntheticOuterConstructorParameter(innerSource, outerSimpleName, innerSimpleName)
                    || hasNoArgConstructor(nestedSource, innerSimpleName)) {
                mergedSource = removeSyntheticOuterConstructorInvocation(mergedSource, innerSimpleName);
            }
            mergedSource = mergeImports(mergedSource, extractImports(innerSource), outerSimpleName);
            mergedSource = insertNestedSource(mergedSource, nestedSource);
            merged.add(innerClassPath);
        }

        for (String innerClassPath : directNamedInnerClassPaths) {
            if (merged.contains(innerClassPath) || sourceDeclaresInnerType(mergedSource, innerSimpleName(innerClassPath))) {
                continue;
            }
            String innerSimpleName = innerSimpleName(innerClassPath);
            String innerSource = sourceProvider == null ? null : sourceProvider.sourceFor(innerClassPath);
            String nestedSource = normalizeInnerSource(innerSource, outerSimpleName, innerSimpleName);
            if (nestedSource == null) {
                continue;
            }
            if (hasSyntheticOuterConstructorParameter(innerSource, outerSimpleName, innerSimpleName)
                    || hasNoArgConstructor(nestedSource, innerSimpleName)) {
                mergedSource = removeSyntheticOuterConstructorInvocation(mergedSource, innerSimpleName);
            }
            mergedSource = mergeImports(mergedSource, extractImports(innerSource), outerSimpleName);
            mergedSource = insertNestedSource(mergedSource, nestedSource);
            merged.add(innerClassPath);
        }

        unresolved.addAll(missingNamedInnerClassPaths(mergedSource, outerClassPath, classFiles));
        unresolved = uniqueUnresolved(unresolved, merged);
        return new MergeResult(mergedSource, merged, unresolved);
    }

    boolean hasMissingNamedInnerReferences(String source, String classPath, Collection<String> classFiles) {
        return !missingNamedInnerClassPaths(source, classPath, classFiles).isEmpty();
    }

    private List<String> missingNamedInnerClassPaths(String source, String classPath, Collection<String> classFiles) {
        List<String> missing = new ArrayList<>();
        if (source == null || classPath == null || classFiles == null || DecompilerBridge.isInnerClass(classPath)) {
            return missing;
        }
        if (!classPath.endsWith(".class")) {
            return missing;
        }

        Set<String> classFileSet = new HashSet<>(classFiles);
        String outerClassName = classPath.substring(0, classPath.length() - ".class".length()).replace('/', '.');
        Pattern selfInnerImport = Pattern.compile("(?m)^\\s*import\\s+"
                + Pattern.quote(outerClassName)
                + "\\.([A-Za-z_$][\\w$]*)\\s*;");
        Matcher matcher = selfInnerImport.matcher(source);
        while (matcher.find()) {
            String innerSimpleName = matcher.group(1);
            String innerClassPath = classPath.substring(0, classPath.length() - ".class".length())
                    + "$"
                    + innerSimpleName
                    + ".class";
            if (classFileSet.contains(innerClassPath) && !sourceDeclaresInnerType(source, innerSimpleName)) {
                addUnique(missing, innerClassPath);
            }
        }

        String directInnerPrefix = classPath.substring(0, classPath.length() - ".class".length()) + "$";
        for (String candidateClassPath : classFiles) {
            if (!isDirectNamedInnerClass(candidateClassPath, directInnerPrefix)) {
                continue;
            }
            String innerSimpleName = candidateClassPath.substring(
                    directInnerPrefix.length(),
                    candidateClassPath.length() - ".class".length());
            if (!sourceDeclaresInnerType(source, innerSimpleName)
                    && sourceReferencesIdentifier(source, innerSimpleName)) {
                addUnique(missing, candidateClassPath);
            }
        }
        return missing;
    }

    private List<String> directNamedInnerClassPaths(String outerClassPath, Collection<String> classFiles) {
        List<String> classPaths = new ArrayList<>();
        if (outerClassPath == null || classFiles == null || !outerClassPath.endsWith(".class")) {
            return classPaths;
        }
        String directInnerPrefix = outerClassPath.substring(0, outerClassPath.length() - ".class".length()) + "$";
        for (String candidateClassPath : classFiles) {
            if (isDirectNamedInnerClass(candidateClassPath, directInnerPrefix)) {
                classPaths.add(candidateClassPath);
            }
        }
        return classPaths;
    }

    private boolean isDirectNamedInnerClass(String candidateClassPath, String directInnerPrefix) {
        if (candidateClassPath == null
                || !candidateClassPath.startsWith(directInnerPrefix)
                || !candidateClassPath.endsWith(".class")) {
            return false;
        }
        String innerSimpleName = candidateClassPath.substring(
                directInnerPrefix.length(),
                candidateClassPath.length() - ".class".length());
        return !innerSimpleName.isEmpty()
                && innerSimpleName.indexOf('$') < 0
                && !Character.isDigit(innerSimpleName.charAt(0));
    }

    private boolean sourceReferencesIdentifier(String source, String identifier) {
        Pattern reference = Pattern.compile("(?<![A-Za-z0-9_$])"
                + Pattern.quote(identifier)
                + "(?![A-Za-z0-9_$])");
        return reference.matcher(source).find();
    }

    private boolean sourceDeclaresInnerType(String source, String innerSimpleName) {
        Pattern declaration = Pattern.compile(String.format(
                NAMED_INNER_DECLARATION_PATTERN, Pattern.quote(innerSimpleName)));
        return declaration.matcher(source).find();
    }

    private String normalizeInnerSource(String innerSource, String outerSimpleName, String innerSimpleName) {
        if (innerSource == null || outerSimpleName == null || innerSimpleName == null) {
            return null;
        }
        Pattern declaration = Pattern.compile("(?m)^\\s*(?:"
                + "(?:public|protected|private|abstract|static|final|strictfp)\\s+)*"
                + "(?:class|interface|enum|@interface)\\s+"
                + Pattern.quote(outerSimpleName)
                + "\\."
                + Pattern.quote(innerSimpleName)
                + "\\b");
        Matcher matcher = declaration.matcher(innerSource);
        if (!matcher.find()) {
            return null;
        }

        String nestedSource = innerSource.substring(matcher.start()).trim();
        nestedSource = replaceQualifiedInnerName(nestedSource, outerSimpleName, innerSimpleName);
        nestedSource = removeSyntheticOuterConstructorParameter(nestedSource, outerSimpleName, innerSimpleName);
        if (!sourceDeclaresInnerType(nestedSource, innerSimpleName)) {
            return null;
        }
        return nestedSource;
    }

    private String replaceQualifiedInnerName(String source, String outerSimpleName, String innerSimpleName) {
        Pattern qualified = Pattern.compile("(?<![A-Za-z0-9_$])"
                + Pattern.quote(outerSimpleName + "." + innerSimpleName)
                + "(?![A-Za-z0-9_$])");
        return qualified.matcher(source).replaceAll(Matcher.quoteReplacement(innerSimpleName));
    }

    private String removeSyntheticOuterConstructorParameter(String source,
                                                           String outerSimpleName,
                                                           String innerSimpleName) {
        Pattern onlyParameter = Pattern.compile("(?m)(\\b"
                + Pattern.quote(innerSimpleName)
                + "\\s*\\()\\s*"
                + Pattern.quote(outerSimpleName)
                + "\\s+this\\$0\\s*(\\))");
        String normalized = onlyParameter.matcher(source).replaceAll("$1$2");
        Pattern firstParameter = Pattern.compile("(?m)(\\b"
                + Pattern.quote(innerSimpleName)
                + "\\s*\\()\\s*"
                + Pattern.quote(outerSimpleName)
                + "\\s+this\\$0\\s*,\\s*");
        return firstParameter.matcher(normalized).replaceAll("$1");
    }

    private boolean hasSyntheticOuterConstructorParameter(String source,
                                                         String outerSimpleName,
                                                         String innerSimpleName) {
        if (source == null || outerSimpleName == null || innerSimpleName == null) {
            return false;
        }
        Pattern parameter = Pattern.compile("\\b"
                + Pattern.quote(outerSimpleName + "." + innerSimpleName)
                + "\\s*\\(\\s*"
                + Pattern.quote(outerSimpleName)
                + "\\s+this\\$0\\b");
        return parameter.matcher(source).find();
    }

    private boolean hasNoArgConstructor(String source, String innerSimpleName) {
        if (source == null || innerSimpleName == null) {
            return false;
        }
        Pattern constructor = Pattern.compile("\\b"
                + Pattern.quote(innerSimpleName)
                + "\\s*\\(\\s*\\)\\s*\\{");
        return constructor.matcher(source).find();
    }

    private String removeSyntheticOuterConstructorInvocation(String source, String innerSimpleName) {
        Pattern invocation = Pattern.compile("\\bnew\\s+"
                + Pattern.quote(innerSimpleName)
                + "\\s*\\(\\s*this\\s*\\)");
        return invocation.matcher(source).replaceAll(Matcher.quoteReplacement("new " + innerSimpleName + "()"));
    }

    private List<String> extractImports(String source) {
        List<String> imports = new ArrayList<>();
        if (source == null) {
            return imports;
        }
        Matcher matcher = Pattern.compile("(?m)^\\s*import\\s+[^;]+;\\s*$").matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group().trim());
        }
        return imports;
    }

    private String mergeImports(String outerSource, List<String> imports, String outerSimpleName) {
        if (imports.isEmpty()) {
            return outerSource;
        }
        Set<String> existing = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*import\\s+[^;]+;\\s*$").matcher(outerSource);
        int lastImportEnd = -1;
        while (matcher.find()) {
            existing.add(matcher.group().trim());
            lastImportEnd = matcher.end();
        }

        StringBuilder additions = new StringBuilder();
        for (String importLine : imports) {
            if (shouldSkipImport(importLine, outerSimpleName) || existing.contains(importLine)) {
                continue;
            }
            additions.append(importLine).append('\n');
            existing.add(importLine);
        }
        if (additions.length() == 0) {
            return outerSource;
        }

        if (lastImportEnd >= 0) {
            return outerSource.substring(0, lastImportEnd)
                    + "\n"
                    + additions
                    + outerSource.substring(lastImportEnd);
        }

        Matcher packageMatcher = Pattern.compile("(?m)^\\s*package\\s+[^;]+;\\s*$").matcher(outerSource);
        if (packageMatcher.find()) {
            return outerSource.substring(0, packageMatcher.end())
                    + "\n\n"
                    + additions
                    + outerSource.substring(packageMatcher.end());
        }
        return additions + outerSource;
    }

    private boolean shouldSkipImport(String importLine, String outerSimpleName) {
        if (importLine == null || outerSimpleName == null) {
            return true;
        }
        return importLine.endsWith("." + outerSimpleName + ";")
                || importLine.contains("." + outerSimpleName + ".");
    }

    private String insertNestedSource(String outerSource, String nestedSource) {
        int insertAt = outerSource.lastIndexOf('}');
        if (insertAt < 0) {
            return outerSource;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(outerSource, 0, insertAt);
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        builder.append('\n').append(indent(nestedSource)).append('\n');
        builder.append(outerSource.substring(insertAt));
        return builder.toString();
    }

    private String indent(String source) {
        String[] lines = source.split("\\r?\\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            if (lines[i].isEmpty()) {
                continue;
            }
            builder.append("    ").append(lines[i]);
        }
        return builder.toString();
    }

    private List<String> uniqueUnresolved(List<String> unresolved, List<String> merged) {
        List<String> unique = new ArrayList<>();
        Set<String> mergedSet = new HashSet<>(merged);
        for (String classPath : unresolved) {
            if (!mergedSet.contains(classPath)) {
                addUnique(unique, classPath);
            }
        }
        return unique;
    }

    private void addUnique(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private String simpleClassName(String classPath) {
        if (classPath == null) {
            return null;
        }
        int slash = classPath.lastIndexOf('/');
        int dot = classPath.endsWith(".class") ? classPath.length() - ".class".length() : classPath.length();
        return classPath.substring(slash + 1, dot);
    }

    private String innerSimpleName(String classPath) {
        if (classPath == null) {
            return null;
        }
        int dollar = classPath.lastIndexOf('$');
        int dot = classPath.endsWith(".class") ? classPath.length() - ".class".length() : classPath.length();
        if (dollar < 0 || dollar + 1 >= dot) {
            return null;
        }
        return classPath.substring(dollar + 1, dot);
    }
}
