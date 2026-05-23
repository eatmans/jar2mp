package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ProjectBuilder {

    private final ProjectConfig config;
    private final DecompilerBridge decompiler;

    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    public ProjectBuilder(ProjectConfig config) {
        this.config = config;
        this.decompiler = new DecompilerBridge(config);
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
        File srcTestJava = new File(outputDir, "src/test/java");
        File srcTestResources = new File(outputDir, "src/test/resources");

        IoUtils.ensureDirectory(srcMainJava);
        IoUtils.ensureDirectory(srcMainResources);
        IoUtils.ensureDirectory(srcTestJava);
        IoUtils.ensureDirectory(srcTestResources);

        // For WAR: create webapp directory
        if (analysis.isWar()) {
            IoUtils.ensureDirectory(new File(outputDir, "src/main/webapp"));
        }

        // Write pom.xml
        if (callback != null) callback.onProgress("Generating pom.xml...", 5);
        IoUtils.writeStringToFile(new File(outputDir, "pom.xml"), pomXml);

        // Process entries from JAR
        try (JarFile jf = new JarFile(jarFile)) {
            int total = analysis.getClassFiles().size() + analysis.getResourceFiles().size();
            int processed = 0;

            Set<String> decompiledOuterClasses = new HashSet<>();

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

                try (InputStream is = jf.getInputStream(entry)) {
                    if (!shouldDecompile()) {
                        File outputFile = new File(srcMainJava, classPath);
                        IoUtils.ensureDirectory(outputFile.getParentFile());
                        Files.copy(is, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        byte[] bytes = readAllBytes(is);
                        String className = classPath.replace('/', '.').replace(".class", "");

                        if (callback != null && processed % 20 == 0) {
                            callback.onProgress("Decompiling: " + className, percent);
                        }

                        String javaSource = decompiler.decompile(bytes, className);

                        // Convert path: com/example/Foo.class -> com/example/Foo.java
                        String javaPath = classPath.replace(".class", ".java");
                        File outputFile = new File(srcMainJava, javaPath);
                        IoUtils.ensureDirectory(outputFile.getParentFile());
                        IoUtils.writeStringToFile(outputFile, javaSource);
                    }

                } catch (Exception e) {
                    if (callback != null) {
                        callback.onProgress("Warning: Failed to process " + classPath + ": " + e.getMessage(), percent);
                    }
                }
            }

            // Phase 2: Copy resource files
            if (callback != null) callback.onProgress("Copying resources...", 90);
            if (config != null && !config.isCopyResources()) {
                if (callback != null) callback.onProgress("Skipping resources.", 100);
                return;
            }

            processed = 0;
            for (String resourcePath : analysis.getResourceFiles()) {
                processed++;

                // Skip WEB-INF/classes (already handled as class files)
                // Skip BOOT-INF/lib (dependency JARs, not needed)
                if (resourcePath.startsWith("WEB-INF/classes/") ||
                        resourcePath.startsWith("BOOT-INF/lib/")) {
                    continue;
                }

                JarEntry entry = jf.getJarEntry(resourcePath);
                if (entry == null || entry.isDirectory()) continue;

                // Strip BOOT-INF/classes/ and WEB-INF/classes/ prefixes for resources
                String outputResourcePath = resourcePath;
                if (resourcePath.startsWith("BOOT-INF/classes/")) {
                    outputResourcePath = resourcePath.substring("BOOT-INF/classes/".length());
                } else if (resourcePath.startsWith("WEB-INF/classes/")) {
                    outputResourcePath = resourcePath.substring("WEB-INF/classes/".length());
                }

                File outputFile = new File(srcMainResources, outputResourcePath);
                IoUtils.ensureDirectory(outputFile.getParentFile());
                try (InputStream is = jf.getInputStream(entry)) {
                    Files.copy(is, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }

            // Copy META-INF/services if present
            for (String metaPath : analysis.getMetaInfFiles()) {
                if (metaPath.startsWith("META-INF/services/") && !metaPath.endsWith("/")) {
                    JarEntry entry = jf.getJarEntry(metaPath);
                    if (entry == null) continue;
                    File outputFile = new File(srcMainResources, metaPath);
                    IoUtils.ensureDirectory(outputFile.getParentFile());
                    try (InputStream is = jf.getInputStream(entry)) {
                        Files.copy(is, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                }
            }
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
}
