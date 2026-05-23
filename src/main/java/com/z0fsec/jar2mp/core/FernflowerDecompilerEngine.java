package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FernflowerDecompilerEngine implements DecompilerEngine {

    private final ProjectConfig config;

    public FernflowerDecompilerEngine(ProjectConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "fernflower";
    }

    @Override
    public Result decompile(byte[] classBytes, String className) {
        File tempClassFile = null;
        Path outputDir = null;
        try {
            tempClassFile = File.createTempFile("jar2mp_", ".class");
            Files.write(tempClassFile.toPath(), classBytes);
            outputDir = Files.createTempDirectory("jar2mp_fernflower_");

            Map<String, Object> options = new HashMap<>(IFernflowerPreferences.DEFAULTS);
            options.put(IFernflowerPreferences.UNIT_TEST_MODE, "1");
            if (config != null && config.isIncludeSynthetic()) {
                options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "0");
                options.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
            }

            CapturingResultSaver saver = new CapturingResultSaver();
            Fernflower fernflower = new Fernflower(new SingleFileBytecodeProvider(), saver, options, new NoOpLogger());
            fernflower.getStructContext().addSpace(tempClassFile, true);
            fernflower.decompileContext();

            String source = saver.getSource();
            if (DecompilerEngine.isStubSource(source)) {
                String failureMessage = "Fernflower returned empty or stub-only output.";
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        DecompilerEngine.scoreSource(source));
            }
            return Result.success(getName(), source, DecompilerEngine.scoreSource(source));
        } catch (Exception e) {
            String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Result.failure(getName(),
                    DecompilerEngine.failureComment(className, failureMessage),
                    failureMessage,
                    0);
        } finally {
            if (tempClassFile != null) {
                tempClassFile.delete();
            }
            if (outputDir != null) {
                deleteRecursive(outputDir.toFile());
            }
        }
    }

    private static class SingleFileBytecodeProvider implements IBytecodeProvider {
        @Override
        public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
            if (internalPath != null) {
                return null;
            }
            return Files.readAllBytes(new File(externalPath).toPath());
        }
    }

    private static class CapturingResultSaver implements IResultSaver {
        private String source;

        @Override
        public void saveFolder(String path) {
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
        }

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        }

        @Override
        public void createArchive(String path, String archiveName, java.util.jar.Manifest manifest) {
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entryName) {
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
            if (content != null && !content.trim().isEmpty()) {
                source = content;
            }
        }

        @Override
        public void closeArchive(String path, String archiveName) {
        }

        private String getSource() {
            return source == null ? "" : source;
        }
    }

    private static class NoOpLogger extends IFernflowerLogger {
        @Override
        public boolean accepts(Severity severity) {
            return false;
        }

        @Override
        public void writeMessage(String message, Severity severity) {
        }

        @Override
        public void writeMessage(String message, Throwable throwable) {
        }
    }

    private static void deleteRecursive(File file) {
        if (!file.exists()) {
            return;
        }
        try {
            Files.walk(file.toPath())
                    .sorted(Collections.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
