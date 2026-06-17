package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CfrDecompilerEngine implements DecompilerEngine {

    private final ProjectConfig config;

    public CfrDecompilerEngine(ProjectConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "cfr";
    }

    @Override
    public Result decompile(byte[] classBytes, String className) {
        File tempClassFile = null;
        try {
            tempClassFile = File.createTempFile("jar2mp_", ".class");
            Files.write(tempClassFile.toPath(), classBytes);
            return decompileFromFile(tempClassFile, className);
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
        }
    }

    @Override
    public Result decompile(byte[] classBytes, Map<String, byte[]> innerClassBytes, String className) {
        if (innerClassBytes == null || innerClassBytes.isEmpty()) {
            return decompile(classBytes, className);
        }

        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("jar2mp_cfr_").toFile();
            File outerClassFile = writeClassFile(tempDir, className, classBytes);
            for (Map.Entry<String, byte[]> entry : innerClassBytes.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                writeClassFile(tempDir, entry.getKey(), entry.getValue());
            }
            return decompileFromFile(outerClassFile, className);
        } catch (Exception e) {
            String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Result.failure(getName(),
                    DecompilerEngine.failureComment(className, failureMessage),
                    failureMessage,
                    0);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private Result decompileFromFile(File classFile, String className) {
        StringBuilder result = new StringBuilder();
        Map<String, String> options = new HashMap<>();
        options.put("decodeenumswitch", "true");
        options.put("decodestringswitch", "true");
        options.put("sugarenums", "true");
        options.put("decodelambdas", "true");
        options.put("removeboilerplate", "true");
        if (config != null && config.isIncludeSynthetic()) {
            options.put("showversion", "false");
        }

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                return Collections.singletonList(SinkClass.STRING);
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                return new Sink<T>() {
                    @Override
                    public void write(T sinkable) {
                        if (sinkType == SinkType.JAVA && sinkable != null) {
                            result.append(sinkable.toString());
                        }
                    }
                };
            }
        };

        CfrDriver driver = new CfrDriver.Builder()
                .withOutputSink(sinkFactory)
                .withOptions(options)
                .build();
        driver.analyse(Collections.singletonList(classFile.getAbsolutePath()));

        String source = result.toString();
        if (DecompilerEngine.isStubSource(source)) {
            String failureMessage = "CFR returned empty or stub-only output.";
            return Result.failure(getName(),
                    DecompilerEngine.failureComment(className, failureMessage),
                    failureMessage,
                    DecompilerEngine.scoreSource(source));
        }
        return Result.success(getName(), source, DecompilerEngine.scoreSource(source));
    }

    private File writeClassFile(File tempDir, String binaryName, byte[] classBytes) throws java.io.IOException {
        File classFile = new File(tempDir, simpleClassFileName(binaryName));
        Files.write(classFile.toPath(), classBytes);
        return classFile;
    }

    private String simpleClassFileName(String binaryName) {
        String normalized = binaryName == null ? "Unknown" : binaryName.replace('/', '.').replace('\\', '.');
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        }
        int lastDot = normalized.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
        if (simpleName.isEmpty()) {
            simpleName = "Unknown";
        }
        return simpleName + ".class";
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
