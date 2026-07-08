package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.IoUtils;
import org.benf.cfr.reader.api.CfrDriver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class CfrJarDecompiler {

    public Map<String, String> decompile(File jarFile) {
        if (jarFile == null || !jarFile.isFile()) {
            return Collections.emptyMap();
        }

        File outputDir = null;
        try {
            outputDir = Files.createTempDirectory("jar2mp_cfr_context_").toFile();
            Map<String, String> options = new HashMap<>();
            options.put("outputdir", outputDir.getAbsolutePath());
            options.put("silent", "true");
            options.put("showversion", "false");
            options.put("caseinsensitivefs", "true");
            options.put("decodeenumswitch", "true");
            options.put("decodestringswitch", "true");
            options.put("sugarenums", "true");
            options.put("decodelambdas", "true");
            options.put("removeboilerplate", "true");

            new CfrDriver.Builder()
                    .withOptions(options)
                    .build()
                    .analyse(Collections.singletonList(jarFile.getAbsolutePath()));

            return readJavaSources(outputDir.toPath());
        } catch (Exception ignored) {
            return Collections.emptyMap();
        } finally {
            if (outputDir != null) {
                IoUtils.deleteRecursive(outputDir);
            }
        }
    }

    private Map<String, String> readJavaSources(Path outputDir) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> readSource(outputDir, path, sources));
        }
        return sources;
    }

    private void readSource(Path outputDir, Path sourcePath, Map<String, String> sources) {
        try {
            String relativePath = outputDir.relativize(sourcePath).toString().replace(File.separatorChar, '/');
            sources.put(relativePath, new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }
}
