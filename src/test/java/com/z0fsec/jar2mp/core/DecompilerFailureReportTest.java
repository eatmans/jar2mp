package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerFailureReportTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsDecompilerFailureAndRetainsRawClassBytes() throws Exception {
        Path jar = tempDir.resolve("broken.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("demo/Broken.class"));
            out.write(new byte[]{
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                    0, 0, 0, 52, 0, 1
            });
            out.closeEntry();
        }

        JarAnalysisResult analysis = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);
        Path output = tempDir.resolve("out");

        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", output.toFile(), null);

        String report = new String(Files.readAllBytes(output.resolve("decompile-failures.md")),
                StandardCharsets.UTF_8);
        assertTrue(report.contains("Failed to decompile"));
        assertTrue(report.contains("raw class retained"));
        assertTrue(Files.exists(output.resolve("target/original-classes/demo/Broken.class")));
    }
}
