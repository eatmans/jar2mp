package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactFidelityComparatorTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsIdenticalArchiveAsExactMatch() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original,
                entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                entry("com/example/App.class", classBytes("App")),
                entry("BOOT-INF/lib/lib-1.0.jar", "lib"));
        writeJar(rebuilt,
                entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                entry("com/example/App.class", classBytes("App")),
                entry("BOOT-INF/lib/lib-1.0.jar", "lib"));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertTrue(result.isExactMatch());
        assertEquals(3, result.getSameSha256());
        assertEquals(0, result.getDifferentSha256());
        assertEquals(0, result.getMissingEntries());
        assertEquals(0, result.getExtraEntries());
        assertEquals(1, result.getSameClassBytes());
        assertEquals(1, result.getSameNestedLibs());
        assertTrue(result.isManifestSame());
    }

    @Test
    void reportsMissingAndExtraEntries() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original,
                entry("present.txt", "same"),
                entry("missing.txt", "missing"));
        writeJar(rebuilt,
                entry("present.txt", "same"),
                entry("extra.txt", "extra"));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertFalse(result.isExactMatch());
        assertEquals(1, result.getSameSha256());
        assertEquals(1, result.getMissingEntries());
        assertEquals(1, result.getExtraEntries());
        assertTrue(result.getSampleMissingEntries().contains("missing.txt"));
        assertTrue(result.getSampleExtraEntries().contains("extra.txt"));
    }

    @Test
    void reportsClassByteDifferencesSeparately() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original, entry("com/example/App.class", classBytes("App-v1")));
        writeJar(rebuilt, entry("com/example/App.class", classBytes("App-v2")));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertEquals(1, result.getCommonClassEntries());
        assertEquals(0, result.getSameClassBytes());
        assertEquals(1, result.getDifferentClassBytes());
        assertEquals(1, result.getDifferentSha256());
        assertTrue(result.getSampleDifferentEntries().contains("com/example/App.class"));
    }

    @Test
    void reportsNestedLibraryVersionDrift() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original,
                entry("WEB-INF/lib/thymeleaf-3.0.12.RELEASE.jar", "old"));
        writeJar(rebuilt,
                entry("WEB-INF/lib/thymeleaf-3.1.2.RELEASE.jar", "new"));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertEquals(1, result.getOriginalNestedLibs());
        assertEquals(1, result.getRebuiltNestedLibs());
        assertEquals(1, result.getMissingNestedLibs());
        assertEquals(1, result.getExtraNestedLibs());
        assertTrue(result.getSampleMissingEntries().contains("WEB-INF/lib/thymeleaf-3.0.12.RELEASE.jar"));
        assertTrue(result.getSampleExtraEntries().contains("WEB-INF/lib/thymeleaf-3.1.2.RELEASE.jar"));
    }

    @Test
    void reportsManifestDifference() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nCreated-By: original\n"));
        writeJar(rebuilt, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nCreated-By: rebuilt\n"));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertTrue(result.isManifestOriginalPresent());
        assertTrue(result.isManifestRebuiltPresent());
        assertFalse(result.isManifestSame());
        assertEquals(1, result.getDifferentSha256());
        assertTrue(result.getSampleDifferentEntries().contains("META-INF/MANIFEST.MF"));
    }

    @Test
    void writesMarkdownAndCsvReports() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));
        writeJar(rebuilt, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"));
        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        new ArtifactFidelityReportWriter().write(tempDir.toFile(), result);

        String markdown = new String(Files.readAllBytes(tempDir.resolve("artifact-fidelity-report.md")),
                StandardCharsets.UTF_8);
        String csv = new String(Files.readAllBytes(tempDir.resolve("artifact-fidelity-summary.csv")),
                StandardCharsets.UTF_8);
        assertTrue(markdown.contains("# Artifact fidelity report"));
        assertTrue(markdown.contains("- Exact match: true"));
        assertTrue(csv.contains("exact_match"));
        assertTrue(csv.contains("true"));
    }

    private void writeJar(Path path, TestEntry... entries) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (TestEntry entry : entries) {
                JarEntry jarEntry = new JarEntry(entry.name);
                output.putNextEntry(jarEntry);
                output.write(entry.content);
                output.closeEntry();
            }
        }
    }

    private TestEntry entry(String name, String content) {
        return entry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private TestEntry entry(String name, byte[] content) {
        return new TestEntry(name, content);
    }

    private byte[] classBytes(String marker) {
        return ("CAFEBABE-" + marker).getBytes(StandardCharsets.UTF_8);
    }

    private static class TestEntry {
        private final String name;
        private final byte[] content;

        private TestEntry(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
