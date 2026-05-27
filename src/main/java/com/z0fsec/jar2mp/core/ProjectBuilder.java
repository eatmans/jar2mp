package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ProjectBuilder {

    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String BOOT_LIB_PREFIX = "BOOT-INF/lib/";
    private static final String WEB_CLASSES_PREFIX = "WEB-INF/classes/";
    private static final String WEB_LIB_PREFIX = "WEB-INF/lib/";

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
        File targetOriginalLibs = new File(outputDir, "target/original-libs");
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

                // Skip inner classes only during decompilation; raw class copying should preserve them.
                if (shouldDecompile() && DecompilerBridge.isInnerClass(classPath)) {
                    continue;
                }

                // Check if already decompiled (shouldn't happen for non-inner, but safety check)
                if (decompiledOuterClasses.contains(classPath)) continue;
                decompiledOuterClasses.add(classPath);

                // Resolve the raw entry name in JAR (may be under BOOT-INF/classes/ or WEB-INF/classes/)
                String rawEntryPath = analysis.getClassPathMapping().get(classPath);
                if (rawEntryPath == null) rawEntryPath = classPath;

                JarEntry entry = jf.getJarEntry(rawEntryPath);
                if (entry == null) continue;

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
                            IoUtils.ensureDirectory(outputFile.getParentFile());
                            IoUtils.writeStringToFile(outputFile, javaSource);
                            decompileFindings.add(finding);
                        } else {
                            File retainedClassFile = resolveOutputFile(targetOriginalClasses, classPath);
                            if (retainedClassFile != null) {
                                IoUtils.ensureDirectory(retainedClassFile.getParentFile());
                                Files.write(retainedClassFile.toPath(), bytes);
                                finding.setRetainedClassPath(relativize(outputDir, retainedClassFile));
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
        if ("META-INF/MANIFEST.MF".equals(upperPath) || upperPath.startsWith("META-INF/MAVEN/")) {
            return false;
        }
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
                && !source.contains("WARNING - void declaration")
                && !source.contains("Loose catch block")
                && !source.contains("** ");
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
