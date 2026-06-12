package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void postProcessPreservesRawArtifactAndByteExactReference() throws Exception {
        Path jar = tempDir.resolve("sample-1.0.jar");
        createJar(jar);
        Path outputDir = tempDir.resolve("project");
        Files.createDirectories(outputDir);

        ProjectConfig config = new ProjectConfig();
        config.setEmitRawArtifact(true);
        config.setByteExactPackage(true);
        List<String> messages = new ArrayList<>();

        BuildPostProcessor.PostBuildResult result = new BuildPostProcessor()
                .postProcess(jar.toFile(), new JarAnalysisResult(), outputDir.toFile(), config, messages::add);

        assertTrue(Files.exists(outputDir.resolve("target/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(outputDir.resolve(".jar2mp/byte-exact/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(outputDir.resolve("target/raw-artifact/artifact-fidelity-report.md")));
        assertTrue(result.getRawArtifactFidelity().isExactMatch());
        assertTrue(messages.stream().anyMatch(message -> message.contains("原始归档保真副本")));
    }

    private void createJar(Path jar) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("config.properties");
            out.putNextEntry(entry);
            out.write("mode=test\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
