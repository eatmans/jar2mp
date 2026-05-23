package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DecompilerBridge {

    private final ProjectConfig config;

    public DecompilerBridge(ProjectConfig config) {
        this.config = config;
    }

    public static class DecompileResult {
        private final boolean success;
        private final String source;
        private final String failureMessage;

        private DecompileResult(boolean success, String source, String failureMessage) {
            this.success = success;
            this.source = source;
            this.failureMessage = failureMessage;
        }

        public static DecompileResult success(String source) {
            return new DecompileResult(true, source, null);
        }

        public static DecompileResult failure(String source, String failureMessage) {
            return new DecompileResult(false, source, failureMessage);
        }

        public boolean isSuccess() { return success; }
        public String getSource() { return source; }
        public String getFailureMessage() { return failureMessage; }
    }

    /**
     * Decompile a single class file given its raw bytes.
     * Returns the decompiled Java source code, or an error comment.
     */
    public String decompile(byte[] classBytes, String className) {
        return decompileDetailed(classBytes, className).getSource();
    }

    public DecompileResult decompileDetailed(byte[] classBytes, String className) {
        try {
            StringBuilder result = new StringBuilder();

            Map<String, String> options = new HashMap<>();
            options.put("decodeenumswitch", "true");
            options.put("decodestringswitch", "true");
            options.put("sugarenums", "true");
            options.put("decodelambdas", "true");
            options.put("removeboilerplate", "true");
            options.put("decodestringswitch", "true");

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

            // Use a temp file approach since CFR needs file paths
            java.io.File tempFile = null;
            try {
                tempFile = java.io.File.createTempFile("jar2mp_", ".class");
                java.nio.file.Files.write(tempFile.toPath(), classBytes);
                driver.analyse(Collections.singletonList(tempFile.getAbsolutePath()));
            } finally {
                if (tempFile != null) {
                    tempFile.delete();
                }
            }

            String source = result.toString();
            if (source.trim().isEmpty()) {
                String failure = "The class file may be obfuscated or corrupted.";
                return DecompileResult.failure(
                        "// Failed to decompile: " + className + "\n// " + failure + "\n",
                        failure);
            }
            if (source.startsWith("// Failed to decompile:")) {
                return DecompileResult.failure(source, source.trim());
            }
            return DecompileResult.success(source);

        } catch (Exception e) {
            String source = "// Failed to decompile: " + className + "\n// Error: " + e.getMessage() + "\n";
            return DecompileResult.failure(source, e.getMessage());
        }
    }

    /**
     * Check if a class entry is an inner class (contains $ in the filename).
     */
    public static boolean isInnerClass(String entryPath) {
        String fileName = entryPath;
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);
        return fileName.contains("$");
    }

    /**
     * Get the outer class path for an inner class.
     * e.g., com/example/Foo$Bar.class -> com/example/Foo.class
     */
    public static String getOuterClassPath(String innerClassPath) {
        int dollarPos = innerClassPath.indexOf('$');
        if (dollarPos < 0) return innerClassPath;
        return innerClassPath.substring(0, dollarPos) + ".class";
    }
}
