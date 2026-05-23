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

    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    public ProjectBuilder(ProjectConfig config) {
        this.config = config;
        this.decompiler = new DecompilerBridge(config);
        this.parityReporter = new DecompileParityReporter();
        this.restorationReportWriter = new RestorationReportWriter();
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
                        if (decompileResult.isSuccess()) {
                            String javaSource = decompileResult.getSource();
                            IoUtils.ensureDirectory(outputFile.getParentFile());
                            IoUtils.writeStringToFile(outputFile, javaSource);
                        } else {
                            File retainedClassFile = resolveOutputFile(targetOriginalClasses, classPath);
                            if (retainedClassFile != null) {
                                IoUtils.ensureDirectory(retainedClassFile.getParentFile());
                                Files.write(retainedClassFile.toPath(), bytes);
                                decompileFindings.add(new DecompileFinding(
                                        classPath,
                                        relativize(outputDir, retainedClassFile),
                                        decompileResult.getFailureMessage()));
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
                    copyJarEntry(jf, resourcePath, srcMainResources,
                            stripClasspathResourcePrefix(resourcePath), copiedResourceOutputs, true);
                }

                for (String resourcePath : analysis.getResourceFiles()) {
                    processed++;
                    if (isClasspathResource(resourcePath) || isNestedLibrary(resourcePath)) {
                        continue;
                    }
                    File targetDir = analysis.isWar() ? srcMainWebapp : srcMainResources;
                    copyJarEntry(jf, resourcePath, targetDir, resourcePath, copiedResourceOutputs, false);
                }

                File metaInfTargetDir = analysis.isWar() ? srcMainWebapp : srcMainResources;
                for (String metaPath : analysis.getMetaInfFiles()) {
                    if (shouldCopyMetaInfResource(metaPath)) {
                        copyJarEntry(jf, metaPath, metaInfTargetDir, metaPath, copiedResourceOutputs, false);
                    }
                }
            }

            if (callback != null) callback.onProgress("Generating decompile parity report...", 95);
            restorationReportWriter.writeRestorationReport(outputDir, analysis);
            parityReporter.writeReport(jf, analysis, outputDir);
            restorationReportWriter.writeResourceInventory(outputDir, analysis);
            restorationReportWriter.writeRunbook(outputDir, analysis);
            restorationReportWriter.writeDecompileFailures(outputDir, analysis);
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

        int lastSlash = upperPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? upperPath.substring(lastSlash + 1) : upperPath;
        return !(fileName.endsWith(".SF") ||
                fileName.endsWith(".RSA") ||
                fileName.endsWith(".DSA") ||
                fileName.endsWith(".EC"));
    }

    private void copyJarEntry(JarFile jarFile, String entryPath, File baseDir, String outputRelativePath,
                              Set<Path> copiedOutputs, boolean overwriteExisting) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryPath);
        if (entry == null || entry.isDirectory()) {
            return;
        }

        Path outputPath = resolveOutputPath(baseDir, outputRelativePath);
        if (outputPath == null) {
            return;
        }

        if (!overwriteExisting && (copiedOutputs.contains(outputPath) || Files.exists(outputPath))) {
            return;
        }

        IoUtils.ensureDirectory(outputPath.getParent().toFile());
        try (InputStream is = jarFile.getInputStream(entry)) {
            Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            return;
        }
        copiedOutputs.add(outputPath);
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
