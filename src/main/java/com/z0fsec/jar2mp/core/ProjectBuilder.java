package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

public class ProjectBuilder {

    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String BOOT_LIB_PREFIX = "BOOT-INF/lib/";
    private static final String WEB_CLASSES_PREFIX = "WEB-INF/classes/";
    private static final String WEB_LIB_PREFIX = "WEB-INF/lib/";
    private static final String NAMED_INNER_DECLARATION_PATTERN =
            "(?:class|interface|enum|@interface)\\s+%s\\b";
    private static final Pattern ANONYMOUS_INNER_DECLARATION = Pattern.compile(
            "new\\s+[^;{}()]+(?:\\([^;{}]*\\))?\\s*\\{");

    private final ProjectConfig config;
    private final DecompilerBridge decompiler;
    private final DecompileParityReporter parityReporter;
    private final RestorationReportWriter restorationReportWriter;
    private final RestorationScorer restorationScorer;
    private final RestorationScoreWriter restorationScoreWriter;
    private final SourcePostProcessor sourcePostProcessor;
    private final GapSummaryWriter gapSummaryWriter;
    private final CfrJarDecompiler cfrJarDecompiler;

    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    public ProjectBuilder(ProjectConfig config) {
        this.config = config;
        this.decompiler = new DecompilerBridge(config);
        this.parityReporter = new DecompileParityReporter();
        this.restorationReportWriter = new RestorationReportWriter();
        this.restorationScorer = new RestorationScorer();
        this.restorationScoreWriter = new RestorationScoreWriter();
        this.sourcePostProcessor = new SourcePostProcessor();
        this.gapSummaryWriter = new GapSummaryWriter();
        this.cfrJarDecompiler = new CfrJarDecompiler();
    }

    public void build(File jarFile, JarAnalysisResult analysis, String pomXml,
                      File outputDir, ProgressCallback callback) throws IOException {
        if (outputDir.exists() && outputDir.list().length > 0) {
            IoUtils.deleteRecursive(outputDir);
        }
        outputDir.mkdirs();

        // Create Maven directory structure
        File srcMainJava = new File(outputDir, "src/main/java");
        File srcMainResources = new File(outputDir, "src/main/resources");
        File srcMainWebapp = new File(outputDir, "src/main/webapp");
        File targetOriginalClasses = new File(outputDir, "target/original-classes");
        File targetRawClasses = new File(outputDir, "target/raw-classes");
        File targetOriginalLibs = new File(outputDir, "target/original-libs");
        File srcMainOriginalLibs = new File(outputDir, "src/main/original-libs");
        File compilerFallbackJar = new File(outputDir, "target/compiler-fallback-classes.jar");
        File srcTestJava = new File(outputDir, "src/test/java");
        File srcTestResources = new File(outputDir, "src/test/resources");

        IoUtils.ensureDirectory(srcMainJava);
        IoUtils.ensureDirectory(srcMainResources);
        IoUtils.ensureDirectory(srcTestJava);
        IoUtils.ensureDirectory(srcTestResources);

        // For WAR: create webapp directory
        if (analysis.isWar()) {
            IoUtils.ensureDirectory(srcMainWebapp);
        }

        // Write pom.xml
        if (callback != null) callback.onProgress("Generating pom.xml...", 5);
        IoUtils.writeStringToFile(new File(outputDir, "pom.xml"), pomXml);

        // Process entries from JAR
        try (JarFile jf = new JarFile(jarFile)) {
            int total = analysis.getClassFiles().size() + analysis.getResourceFiles().size();
            int processed = 0;

            Set<String> decompiledOuterClasses = new HashSet<>();
            List<DecompileFinding> decompileFindings = analysis.getDecompileFindings();
            Set<String> caseInsensitiveClassCollisions =
                    findCaseInsensitiveClassCollisions(analysis.getClassFiles());
            Map<String, byte[]> compilerFallbackJarEntries = new LinkedHashMap<>();
            Map<String, String> contextSources = shouldDecompile()
                    ? cfrJarDecompiler.decompile(jarFile)
                    : Collections.emptyMap();

            // Phase 1: Decompile class files
            if (callback != null) callback.onProgress("Decompiling class files...", 10);

            for (String classPath : analysis.getClassFiles()) {
                processed++;
                int percent = 10 + (int) (80.0 * processed / total);

                // Skip module-info.class and package-info.class
                String fileName = classPath;
                int lastSlash = fileName.lastIndexOf('/');
                if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);

                if (shouldDecompile() && "module-info.class".equals(fileName)) {
                    if (callback != null) callback.onProgress("Skipping module-info.class", percent);
                    continue;
                }

                boolean packageInfo = shouldDecompile() && "package-info.class".equals(fileName);

                // Check if already decompiled (shouldn't happen for non-inner, but safety check)
                if (decompiledOuterClasses.contains(classPath)) continue;
                decompiledOuterClasses.add(classPath);

                // Resolve the raw entry name in JAR (may be under BOOT-INF/classes/ or WEB-INF/classes/)
                String rawEntryPath = analysis.getClassPathMapping().get(classPath);
                if (rawEntryPath == null) rawEntryPath = classPath;

                JarEntry entry = jf.getJarEntry(rawEntryPath);
                if (entry == null) continue;

                if (shouldDecompile() && caseInsensitiveClassCollisions.contains(classPath)) {
                    DecompileFinding finding = rawClassFallbackFinding(
                            classPath,
                            "class path collides on case-insensitive file systems",
                            "Decompiled source was skipped because the output file system cannot safely store "
                                    + "case-only distinct class paths as separate files.");
                    try (InputStream is = jf.getInputStream(entry)) {
                        retainRawClassForFallback(readAllBytes(is), classPath, targetOriginalClasses,
                                srcMainResources, outputDir, finding, compilerFallbackJarEntries, true);
                    }
                    if (finding.hasRetainedClassPath()) {
                        decompileFindings.add(finding);
                    }
                    continue;
                }

                cacheRawClass(jf, entry, classPath, targetRawClasses);

                // Skip inner classes as standalone source files, but retain them
                // when the outer source does not visibly cover the named type.
                if (shouldDecompile() && DecompilerBridge.isInnerClass(classPath)) {
                    handleSkippedInnerClass(jf, entry, classPath, contextSources, targetOriginalClasses,
                            srcMainResources, outputDir, decompileFindings, compilerFallbackJarEntries);
                    continue;
                }

                if (shouldDecompile() && !hasLegalTopLevelJavaSourceName(classPath)) {
                    DecompileFinding finding = rawClassFallbackFinding(
                            classPath,
                            "class name is not a legal Java source identifier",
                            "Decompiled source was skipped because the JVM class name cannot be emitted as Java source.");
                    try (InputStream is = jf.getInputStream(entry)) {
                        retainRawClassForFallback(readAllBytes(is), classPath, targetOriginalClasses,
                                srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                    }
                    if (finding.hasRetainedClassPath()) {
                        decompileFindings.add(finding);
                    }
                    continue;
                }

                if (shouldDecompile() && isShadedDependencyClass(classPath)) {
                    DecompileFinding finding = rawClassFallbackFinding(
                            classPath,
                            "class is under a shaded dependency namespace",
                            "Decompiled source was skipped because relocated dependency bytecode is retained as raw "
                                    + "classes for compile stability.");
                    try (InputStream is = jf.getInputStream(entry)) {
                        retainRawClassForFallback(readAllBytes(is), classPath, targetOriginalClasses,
                                srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                    }
                    if (finding.hasRetainedClassPath()) {
                        decompileFindings.add(finding);
                    }
                    continue;
                }

                if (shouldDecompile()) {
                    byte[] rawClassBytes = readRawClassBytes(jf, entry);
                    if (isKotlinMetadataClass(rawClassBytes)) {
                        DecompileFinding finding = rawClassFallbackFinding(
                                classPath,
                                "class was compiled from Kotlin metadata",
                                "Decompiled source was skipped because Kotlin bytecode is not reliably emitted as "
                                        + "compilable Java source.");
                        retainRawClassForFallback(rawClassBytes, classPath, targetOriginalClasses,
                                srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                        if (finding.hasRetainedClassPath()) {
                            decompileFindings.add(finding);
                        }
                        continue;
                    }
                }

                String outputPath = shouldDecompile() ? classPath.replace(".class", ".java") : classPath;
                File outputFile = resolveOutputFile(srcMainJava, outputPath);
                if (outputFile == null) {
                    if (callback != null) {
                        callback.onProgress("Warning: Skipping unsafe class path " + classPath, percent);
                    }
                    continue;
                }

                if (packageInfo) {
                    IoUtils.ensureDirectory(outputFile.getParentFile());
                    IoUtils.writeStringToFile(outputFile, packageInfoSource(classPath));
                    decompileFindings.add(new DecompileFinding(classPath, null, null));
                    continue;
                }

                String contextSource = findContextSource(contextSources, classPath, rawEntryPath);
                if (contextSource != null && isContextSourceUsable(contextSource)) {
                    String className = classPath.replace('/', '.').replace(".class", "");
                    String javaSource = sourcePostProcessor.process(contextSource, className);
                    if (hasMissingSelfInnerReferences(javaSource, classPath, analysis.getClassFiles())) {
                        DecompileFinding finding = rawClassFallbackFinding(
                                classPath,
                                "decompiled outer source references inner classes that are not declared",
                                "Decompiled source was skipped because javac cannot resolve retained inner classes as "
                                        + "members of the source type.");
                        try (InputStream is = jf.getInputStream(entry)) {
                            retainRawClassForFallback(readAllBytes(is), classPath, targetOriginalClasses,
                                    srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                        }
                        if (finding.hasRetainedClassPath()) {
                            decompileFindings.add(finding);
                        }
                        continue;
                    }
                    IoUtils.ensureDirectory(outputFile.getParentFile());
                    IoUtils.writeStringToFile(outputFile, javaSource);
                    DecompileFinding finding = new DecompileFinding(classPath, null, null);
                    finding.setSelectedEngine("cfr-context");
                    finding.setEngineSummary("cfr-context=" + DecompilerEngine.scoreSource(javaSource));
                    decompileFindings.add(finding);
                    continue;
                }

                try (InputStream is = jf.getInputStream(entry)) {
                    if (!shouldDecompile()) {
                        IoUtils.ensureDirectory(outputFile.getParentFile());
                        Files.copy(is, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        byte[] bytes = readAllBytes(is);
                        String className = classPath.replace('/', '.').replace(".class", "");

                        if (callback != null && processed % 20 == 0) {
                            callback.onProgress("Decompiling: " + className, percent);
                        }

                        DecompilerBridge.DecompileResult decompileResult =
                                decompiler.decompileDetailed(bytes, className);
                        DecompileFinding finding = new DecompileFinding(
                                classPath,
                                null,
                                decompileResult.getFailureMessage());
                        finding.setSelectedEngine(decompileResult.getSelectedEngine());
                        finding.setFallbackReason(decompileResult.getFallbackReason());
                        finding.setEngineSummary(decompileResult.getEngineSummary());
                        if (decompileResult.isSuccess()) {
                            String javaSource = sourcePostProcessor.process(decompileResult.getSource(), className);
                            if (hasMissingSelfInnerReferences(javaSource, classPath, analysis.getClassFiles())) {
                                finding = rawClassFallbackFinding(
                                        classPath,
                                        "decompiled outer source references inner classes that are not declared",
                                        "Decompiled source was skipped because javac cannot resolve retained inner "
                                                + "classes as members of the source type.");
                                retainRawClassForFallback(bytes, classPath, targetOriginalClasses,
                                        srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                                if (finding.hasRetainedClassPath()) {
                                    decompileFindings.add(finding);
                                }
                                continue;
                            }
                            IoUtils.ensureDirectory(outputFile.getParentFile());
                            IoUtils.writeStringToFile(outputFile, javaSource);
                            decompileFindings.add(finding);
                        } else {
                            retainRawClassForFallback(bytes, classPath, targetOriginalClasses,
                                    srcMainResources, outputDir, finding, compilerFallbackJarEntries, false);
                            if (finding.hasRetainedClassPath()) {
                                decompileFindings.add(finding);
                            }
                        }
                    }

                } catch (Exception e) {
                    if (callback != null) {
                        callback.onProgress("Warning: Failed to process " + classPath + ": " + e.getMessage(), percent);
                    }
                }
            }

            // Phase 2: Copy resource files
            if (callback != null) callback.onProgress("Copying resources...", 90);
            boolean copyResources = config == null || config.isCopyResources();
            if (!copyResources) {
                if (callback != null) callback.onProgress("Skipping resources.", 90);
            }

            processed = 0;
            Set<Path> copiedResourceOutputs = new HashSet<>();

            if (copyResources) {
                for (String resourcePath : analysis.getResourceFiles()) {
                    processed++;
                    if (!isClasspathResource(resourcePath)) {
                        continue;
                    }
                    CopyResult copyResult = copyJarEntry(jf, resourcePath, srcMainResources,
                            stripClasspathResourcePrefix(resourcePath), copiedResourceOutputs, true);
                    recordResourceCopyResult(analysis, resourcePath, copyResult);
                }

                for (String resourcePath : analysis.getResourceFiles()) {
                    processed++;
                    if (isClasspathResource(resourcePath)) {
                        continue;
                    }
                    if (isNestedLibrary(resourcePath)) {
                        CopyResult copyResult = copyJarEntry(jf, resourcePath, targetOriginalLibs,
                                resourcePath, copiedResourceOutputs, false);
                        if (copyResult.isCopied() && analysis.isWar() && resourcePath.startsWith(WEB_LIB_PREFIX)) {
                            copyJarEntry(jf, resourcePath, srcMainOriginalLibs, resourcePath,
                                    copiedResourceOutputs, false);
                        }
                        recordResourceCopyResult(analysis, resourcePath, copyResult, true);
                        continue;
                    }
                    File targetDir = analysis.isWar() ? srcMainWebapp : srcMainResources;
                    CopyResult copyResult = copyJarEntry(jf, resourcePath, targetDir, resourcePath,
                            copiedResourceOutputs, false);
                    recordResourceCopyResult(analysis, resourcePath, copyResult);
                }

                File metaInfTargetDir = analysis.isWar() ? srcMainWebapp : srcMainResources;
                for (String metaPath : analysis.getMetaInfFiles()) {
                    if (shouldCopyMetaInfResource(metaPath)) {
                        CopyResult copyResult = copyJarEntry(jf, metaPath, metaInfTargetDir, metaPath,
                                copiedResourceOutputs, false);
                        recordResourceCopyResult(analysis, metaPath, copyResult);
                    }
                }
            }

            if (callback != null) callback.onProgress("Generating decompile parity report...", 95);
            if (!compilerFallbackJarEntries.isEmpty()) {
                writeCompilerFallbackJar(compilerFallbackJar, compilerFallbackJarEntries);
                IoUtils.writeStringToFile(new File(outputDir, "pom.xml"),
                        addCompilerFallbackDependency(pomXml));
            }
            restorationReportWriter.writeRestorationReport(outputDir, analysis);
            parityReporter.writeReport(jf, analysis, outputDir);
            restorationReportWriter.writeResourceInventory(outputDir, analysis);
            restorationReportWriter.writeRunbook(outputDir, analysis);
            restorationReportWriter.writeDecompileFailures(outputDir, analysis);

            RestorationScore restorationScore = restorationScorer.score(
                    analysis,
                    analysis.getRuntimeTraceResult(),
                    analysis.getVerificationResult());
            analysis.setRestorationScore(restorationScore);
            restorationScoreWriter.write(outputDir, restorationScore);
            gapSummaryWriter.write(outputDir, restorationScore);
        }

        if (callback != null) callback.onProgress("Maven project generated successfully!", 100);
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    private boolean shouldDecompile() {
        return config == null || config.isDecompile();
    }

    private String packageInfoSource(String classPath) {
        String suffix = "/package-info.class";
        if (classPath == null || !classPath.endsWith(suffix)) {
            return "";
        }
        String packagePath = classPath.substring(0, classPath.length() - suffix.length());
        if (packagePath.isEmpty()) {
            return "// package-info for the default package\n";
        }
        return "package " + packagePath.replace('/', '.') + ";\n";
    }

    private boolean isClasspathResource(String resourcePath) {
        return resourcePath.startsWith(BOOT_CLASSES_PREFIX) || resourcePath.startsWith(WEB_CLASSES_PREFIX);
    }

    private String stripClasspathResourcePrefix(String resourcePath) {
        if (resourcePath.startsWith(BOOT_CLASSES_PREFIX)) {
            return resourcePath.substring(BOOT_CLASSES_PREFIX.length());
        }
        if (resourcePath.startsWith(WEB_CLASSES_PREFIX)) {
            return resourcePath.substring(WEB_CLASSES_PREFIX.length());
        }
        return resourcePath;
    }

    private boolean isNestedLibrary(String resourcePath) {
        return resourcePath.startsWith(BOOT_LIB_PREFIX) || resourcePath.startsWith(WEB_LIB_PREFIX);
    }

    private boolean shouldCopyMetaInfResource(String metaPath) {
        if (metaPath == null || metaPath.endsWith("/")) {
            return false;
        }

        String upperPath = metaPath.toUpperCase(Locale.ROOT);
        if ("META-INF/SERVICES/JAVA.NIO.FILE.SPI.FILESYSTEMPROVIDER".equals(upperPath)) {
            return false;
        }

        int lastSlash = upperPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? upperPath.substring(lastSlash + 1) : upperPath;
        return !(fileName.endsWith(".SF") ||
                fileName.endsWith(".RSA") ||
                fileName.endsWith(".DSA") ||
                fileName.endsWith(".EC"));
    }

    private String findContextSource(Map<String, String> contextSources, String classPath, String rawEntryPath) {
        if (contextSources == null || contextSources.isEmpty() || classPath == null) {
            return null;
        }

        String sourcePath = classPath.replace(".class", ".java");
        String source = contextSources.get(sourcePath);
        if (source != null) {
            return source;
        }

        if (rawEntryPath == null) {
            return null;
        }
        return contextSources.get(rawEntryPath.replace(".class", ".java"));
    }

    private boolean isContextSourceUsable(String source) {
        if (source == null || DecompilerEngine.isStubSource(source)) {
            return false;
        }
        return !source.contains("Unable to fully structure code")
                && !source.contains("Loose catch block");
    }

    boolean isContextSourceUsableForTest(String source) {
        return isContextSourceUsable(source);
    }

    boolean hasMissingSelfInnerReferencesForTest(String source, String classPath, Collection<String> classFiles) {
        return hasMissingSelfInnerReferences(source, classPath, classFiles);
    }

    private void cacheRawClass(JarFile jarFile, JarEntry entry, String classPath, File targetRawClasses)
            throws IOException {
        File rawClassFile = resolveOutputFile(targetRawClasses, classPath);
        if (rawClassFile == null) {
            return;
        }
        IoUtils.ensureDirectory(rawClassFile.getParentFile());
        try (InputStream input = jarFile.getInputStream(entry)) {
            Files.copy(input, rawClassFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] readRawClassBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream input = jarFile.getInputStream(entry)) {
            return readAllBytes(input);
        }
    }

    private boolean isKotlinMetadataClass(byte[] bytes) {
        return containsBytes(bytes, "Lkotlin/Metadata;".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                || containsBytes(bytes, "kotlin/Metadata".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private boolean containsBytes(byte[] bytes, byte[] needle) {
        if (bytes == null || needle == null || needle.length == 0 || bytes.length < needle.length) {
            return false;
        }
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && bytes[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMissingSelfInnerReferences(String source, String classPath, Collection<String> classFiles) {
        if (source == null || classPath == null || classFiles == null || DecompilerBridge.isInnerClass(classPath)) {
            return false;
        }
        if (!classPath.endsWith(".class")) {
            return false;
        }

        String outerClassName = classPath.substring(0, classPath.length() - ".class".length()).replace('/', '.');
        Pattern selfInnerImport = Pattern.compile("(?m)^\\s*import\\s+"
                + Pattern.quote(outerClassName)
                + "\\.([A-Za-z_$][\\w$]*)\\s*;");
        java.util.regex.Matcher matcher = selfInnerImport.matcher(source);
        while (matcher.find()) {
            String innerSimpleName = matcher.group(1);
            String innerClassPath = classPath.substring(0, classPath.length() - ".class".length())
                    + "$"
                    + innerSimpleName
                    + ".class";
            if (classFiles.contains(innerClassPath) && !sourceDeclaresInnerType(source, innerSimpleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean sourceDeclaresInnerType(String source, String innerSimpleName) {
        Pattern declaration = Pattern.compile(String.format(
                NAMED_INNER_DECLARATION_PATTERN, Pattern.quote(innerSimpleName)));
        return declaration.matcher(source).find();
    }

    private boolean hasLegalTopLevelJavaSourceName(String classPath) {
        if (classPath == null || !classPath.endsWith(".class")) {
            return false;
        }
        String fileName = classPath;
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        String simpleName = fileName.substring(0, fileName.length() - ".class".length());
        if ("package-info".equals(simpleName) || "module-info".equals(simpleName)) {
            return true;
        }
        if (simpleName.isEmpty() || !Character.isJavaIdentifierStart(simpleName.charAt(0))) {
            return false;
        }
        for (int i = 1; i < simpleName.length(); i++) {
            if (!Character.isJavaIdentifierPart(simpleName.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isShadedDependencyClass(String classPath) {
        if (classPath == null) {
            return false;
        }
        return classPath.replace('\\', '/').contains("/shaded/");
    }

    private DecompileFinding rawClassFallbackFinding(String classPath, String fallbackReason, String message) {
        DecompileFinding finding = new DecompileFinding(classPath, null, null);
        finding.setSelectedEngine("raw-class-fallback");
        finding.setFallbackReason(fallbackReason);
        finding.setMessage(message);
        return finding;
    }

    private void handleSkippedInnerClass(JarFile jarFile,
                                         JarEntry entry,
                                         String classPath,
                                         Map<String, String> contextSources,
                                         File targetOriginalClasses,
                                         File srcMainResources,
                                         File outputDir,
                                         List<DecompileFinding> decompileFindings,
                                         Map<String, byte[]> compilerFallbackJarEntries) throws IOException {
        DecompileFinding finding = new DecompileFinding(classPath, null, null);
        if (isInnerClassCoveredByOuterSource(contextSources, classPath)) {
            finding.setSelectedEngine("outer-source");
            finding.setEngineSummary("inner class declaration is present in the outer decompiled source");
            decompileFindings.add(finding);
            return;
        }

        try (InputStream input = jarFile.getInputStream(entry)) {
            byte[] bytes = readAllBytes(input);
            finding.setSelectedEngine("skipped-inner-class");
            finding.setFallbackReason("inner class is not emitted as standalone source");
            finding.setMessage("Inner or anonymous class was not confidently represented in the outer source.");
            retainRawClassForFallback(bytes, classPath, targetOriginalClasses, srcMainResources, outputDir, finding,
                    compilerFallbackJarEntries, false);
            decompileFindings.add(finding);
        }
    }

    private void retainRawClassForFallback(byte[] bytes,
                                           String classPath,
                                           File targetOriginalClasses,
                                           File srcMainResources,
                                           File outputDir,
                                           DecompileFinding finding,
                                           Map<String, byte[]> compilerFallbackJarEntries,
                                           boolean compilerJarOnly) throws IOException {
        if (compilerJarOnly) {
            compilerFallbackJarEntries.put(classPath, bytes);
            finding.setRetainedClassPath("target/compiler-fallback-classes.jar!/" + classPath);
            finding.setMessage(appendNote(finding.getMessage(),
                    "Raw class added to `target/compiler-fallback-classes.jar` for Maven compile fallback."));
            return;
        }

        File retainedClassFile = resolveOutputFile(targetOriginalClasses, classPath);
        if (retainedClassFile != null) {
            IoUtils.ensureDirectory(retainedClassFile.getParentFile());
            Files.write(retainedClassFile.toPath(), bytes);
            finding.setRetainedClassPath(relativize(outputDir, retainedClassFile));
        }

        File compilerFallbackClassFile = resolveOutputFile(srcMainResources, classPath);
        if (compilerFallbackClassFile != null) {
            IoUtils.ensureDirectory(compilerFallbackClassFile.getParentFile());
            Files.write(compilerFallbackClassFile.toPath(), bytes);
            finding.setMessage(appendNote(finding.getMessage(),
                    "Raw class copied to `"
                            + relativize(outputDir, compilerFallbackClassFile)
                            + "` for Maven compile fallback."));
        }
    }

    private Set<String> findCaseInsensitiveClassCollisions(List<String> classPaths) {
        Map<String, String> firstByLowercase = new HashMap<>();
        Set<String> collisions = new HashSet<>();
        for (String classPath : classPaths) {
            String lowercase = classPath.toLowerCase(Locale.ROOT);
            String first = firstByLowercase.putIfAbsent(lowercase, classPath);
            if (first != null && !first.equals(classPath)) {
                collisions.add(first);
                collisions.add(classPath);
            }
        }
        return collisions;
    }

    private void writeCompilerFallbackJar(File jarFile, Map<String, byte[]> entries) throws IOException {
        IoUtils.ensureDirectory(jarFile.getParentFile());
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0L);
                out.putNextEntry(jarEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    private String addCompilerFallbackDependency(String pomXml) {
        if (pomXml.contains("<artifactId>compiler-fallback-classes</artifactId>")) {
            return pomXml;
        }
        String dependency = "        <dependency>\n"
                + "            <groupId>com.z0fsec.jar2mp</groupId>\n"
                + "            <artifactId>compiler-fallback-classes</artifactId>\n"
                + "            <version>1.0.0</version>\n"
                + "            <scope>system</scope>\n"
                + "            <systemPath>${project.basedir}/target/compiler-fallback-classes.jar</systemPath>\n"
                + "        </dependency>\n";
        int searchStart = 0;
        int dependencyManagementEnd = pomXml.indexOf("</dependencyManagement>");
        if (dependencyManagementEnd >= 0) {
            searchStart = dependencyManagementEnd + "</dependencyManagement>".length();
        }
        int dependenciesStart = pomXml.indexOf("<dependencies>", searchStart);
        if (dependenciesStart >= 0) {
            int dependenciesEnd = pomXml.indexOf("</dependencies>", dependenciesStart);
            if (dependenciesEnd >= 0) {
                return pomXml.substring(0, dependenciesEnd) + dependency + pomXml.substring(dependenciesEnd);
            }
        }
        String block = "    <dependencies>\n" + dependency + "    </dependencies>\n\n";
        int buildStart = pomXml.indexOf("<build>");
        if (buildStart >= 0) {
            return pomXml.substring(0, buildStart) + block + pomXml.substring(buildStart);
        }
        int projectEnd = pomXml.lastIndexOf("</project>");
        if (projectEnd >= 0) {
            return pomXml.substring(0, projectEnd) + block + pomXml.substring(projectEnd);
        }
        return pomXml + "\n" + block;
    }

    private boolean isInnerClassCoveredByOuterSource(Map<String, String> contextSources, String classPath) {
        String innerSimpleName = innerSimpleName(classPath);
        if (innerSimpleName == null || innerSimpleName.isEmpty()) {
            return false;
        }
        String outerClassPath = outerClassPath(classPath);
        if (outerClassPath == null) {
            return false;
        }
        String outerSource = findContextSource(contextSources, outerClassPath, null);
        if (!isContextSourceUsable(outerSource)) {
            return false;
        }
        if (Character.isDigit(innerSimpleName.charAt(0))) {
            return countAnonymousInnerDeclarations(outerSource) >= anonymousInnerIndex(innerSimpleName);
        }
        Pattern declaration = Pattern.compile(String.format(
                NAMED_INNER_DECLARATION_PATTERN, Pattern.quote(innerSimpleName)));
        return declaration.matcher(outerSource).find();
    }

    private int anonymousInnerIndex(String innerSimpleName) {
        try {
            return Integer.parseInt(innerSimpleName);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private int countAnonymousInnerDeclarations(String source) {
        int count = 0;
        java.util.regex.Matcher matcher = ANONYMOUS_INNER_DECLARATION.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String outerClassPath(String classPath) {
        if (classPath == null || !classPath.endsWith(".class")) {
            return null;
        }
        int dollar = classPath.indexOf('$');
        if (dollar < 0) {
            return null;
        }
        return classPath.substring(0, dollar) + ".class";
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

    private CopyResult copyJarEntry(JarFile jarFile, String entryPath, File baseDir, String outputRelativePath,
                                    Set<Path> copiedOutputs, boolean overwriteExisting) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            return CopyResult.skipped("JAR entry missing or directory");
        }

        Path outputPath = resolveOutputPath(baseDir, outputRelativePath);
        if (outputPath == null) {
            return CopyResult.skipped("Unsafe output path");
        }

        if (!overwriteExisting && (copiedOutputs.contains(outputPath) || Files.exists(outputPath))) {
            return CopyResult.skipped("Output path collision: " + outputRelativePath);
        }

        IoUtils.ensureDirectory(outputPath.getParent().toFile());
        try (InputStream is = jarFile.getInputStream(entry)) {
            Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return CopyResult.failed("Copy failed: " + e.getMessage());
        }
        copiedOutputs.add(outputPath);
        return CopyResult.copied(outputRelativePath);
    }

    private void recordResourceCopyResult(JarAnalysisResult analysis, String originalPath, CopyResult copyResult) {
        recordResourceCopyResult(analysis, originalPath, copyResult, false);
    }

    private void recordResourceCopyResult(JarAnalysisResult analysis, String originalPath, CopyResult copyResult,
                                          boolean archived) {
        if (analysis == null || originalPath == null || copyResult == null) {
            return;
        }
        for (ResourceFinding finding : analysis.getResourceFindings()) {
            if (!originalPath.equals(finding.getOriginalPath())) {
                continue;
            }
            if (copyResult.isCopied()) {
                finding.setCopyStatus(archived
                        ? ResourceFinding.CopyStatus.ARCHIVED
                        : ResourceFinding.CopyStatus.COPIED);
                String actualPath = archived
                        ? "target/original-libs/" + copyResult.getOutputPath()
                        : copyResult.getOutputPath();
                finding.setActualTargetPath(actualPath);
                finding.setNote(appendNote(finding.getNote(),
                        (archived ? "Archived at " : "Copied to ") + actualPath + "."));
            } else {
                finding.setCopyStatus(copyResult.isFailure()
                        ? ResourceFinding.CopyStatus.FAILED
                        : ResourceFinding.CopyStatus.SKIPPED);
                finding.setCopyFailureReason(copyResult.getReason());
                finding.setNote(appendNote(finding.getNote(), "Resource not copied: " + copyResult.getReason() + "."));
            }
        }
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.trim().isEmpty()) {
            return addition;
        }
        return existing.trim() + " " + addition;
    }

    private static class CopyResult {
        private final boolean copied;
        private final String outputPath;
        private final String reason;
        private final boolean failure;

        private CopyResult(boolean copied, String outputPath, String reason, boolean failure) {
            this.copied = copied;
            this.outputPath = outputPath;
            this.reason = reason;
            this.failure = failure;
        }

        private static CopyResult copied(String outputPath) {
            return new CopyResult(true, outputPath, null, false);
        }

        private static CopyResult skipped(String reason) {
            return new CopyResult(false, null, reason, false);
        }

        private static CopyResult failed(String reason) {
            return new CopyResult(false, null, reason, true);
        }

        private boolean isCopied() {
            return copied;
        }

        private String getOutputPath() {
            return outputPath;
        }

        private String getReason() {
            return reason;
        }

        private boolean isFailure() {
            return failure;
        }
    }

    private File resolveOutputFile(File baseDir, String relativePath) {
        Path outputPath = resolveOutputPath(baseDir, relativePath);
        return outputPath == null ? null : outputPath.toFile();
    }

    private String relativize(File outputDir, File file) {
        Path base = outputDir.toPath().toAbsolutePath().normalize();
        Path target = file.toPath().toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            return file.getPath();
        }
        return base.relativize(target).toString().replace('\\', '/');
    }

    private Path resolveOutputPath(File baseDir, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        try {
            Path basePath = baseDir.toPath().toAbsolutePath().normalize();
            Path outputPath = basePath.resolve(relativePath.replace('\\', '/')).normalize();
            if (outputPath.equals(basePath) || !outputPath.startsWith(basePath)) {
                return null;
            }
            return outputPath;
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
