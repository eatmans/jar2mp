package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.IoUtils;

import java.io.File;
import java.io.IOException;

/**
 * Retains readable decompiled source for classes that must compile from raw bytecode.
 */
public class ReadableSourceRetainer {

    private static final String ROOT_DIR = "decompiled-readable";

    public void retain(File outputDir,
                       String classPath,
                       String sourceText,
                       String fallbackReason,
                       String engineName,
                       String compiledClassRelPath) throws IOException {
        if (outputDir == null
                || classPath == null
                || !classPath.endsWith(".class")
                || sourceText == null
                || sourceText.trim().isEmpty()) {
            return;
        }

        File readableRoot = new File(outputDir, ROOT_DIR);
        String sourcePath = classPath.substring(0, classPath.length() - ".class".length()) + ".java";
        File readableSource = resolveOutputFile(readableRoot, sourcePath);
        if (readableSource == null) {
            return;
        }

        IoUtils.ensureDirectory(readableSource.getParentFile());
        IoUtils.writeStringToFile(readableSource, header(fallbackReason, engineName, compiledClassRelPath)
                + sourceText);
        writeReadme(readableRoot);
    }

    private String header(String fallbackReason, String engineName, String compiledClassRelPath) {
        String reason = fallbackReason == null || fallbackReason.trim().isEmpty()
                ? "unspecified fallback reason"
                : fallbackReason.trim();
        String engine = engineName == null || engineName.trim().isEmpty()
                ? "unknown"
                : engineName.trim();
        String compiledPath = compiledClassRelPath == null || compiledClassRelPath.trim().isEmpty()
                ? "unknown"
                : compiledClassRelPath.trim().replace('\\', '/');
        return "/*\n"
                + " * Readable decompiled source (engine: " + engine + ").\n"
                + " * This class is retained as a raw .class because it could not be recompiled:\n"
                + " * " + reason + ".\n"
                + " * It is NOT part of the Maven build (this directory is outside the compile source root).\n"
                + " * Maven compiles the original bytecode from: src/main/resources/" + compiledPath
                + " (and target/compiler-fallback-classes.jar).\n"
                + " * Provided for reading/analysis only.\n"
                + " */\n";
    }

    private void writeReadme(File readableRoot) throws IOException {
        File readme = new File(readableRoot, "README");
        if (readme.exists()) {
            return;
        }
        IoUtils.writeStringToFile(readme,
                "This directory contains readable decompiled source for classes retained as raw .class files.\n"
                        + "It is outside Maven source roots and is not compiled.\n");
    }

    private File resolveOutputFile(File root, String relativePath) throws IOException {
        File base = root.getCanonicalFile();
        File output = new File(base, relativePath.replace('\\', '/')).getCanonicalFile();
        return output.toPath().startsWith(base.toPath()) ? output : null;
    }
}
