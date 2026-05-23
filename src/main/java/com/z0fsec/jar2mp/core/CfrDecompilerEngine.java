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
            driver.analyse(Collections.singletonList(tempClassFile.getAbsolutePath()));

            String source = result.toString();
            if (DecompilerEngine.isStubSource(source)) {
                String failureMessage = "CFR returned empty or stub-only output.";
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
        }
    }
}
