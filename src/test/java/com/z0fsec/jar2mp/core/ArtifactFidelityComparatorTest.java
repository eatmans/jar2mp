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

    @Test
    void recordsDifferenceBucketsIndependently() {
        ArtifactFidelityResult result = new ArtifactFidelityResult();

        result.recordMissing(ArtifactFidelityResult.DifferenceBucket.MANIFEST, "META-INF/MANIFEST.MF");
        result.recordExtra(ArtifactFidelityResult.DifferenceBucket.MANIFEST, "META-INF/MANIFEST.MF");
        result.recordDifferent(ArtifactFidelityResult.DifferenceBucket.CLASS_BYTECODE, "com/example/App.class");

        ArtifactFidelityResult.DifferenceBucketSummary manifest = result.getBucketSummary(
                ArtifactFidelityResult.DifferenceBucket.MANIFEST);
        ArtifactFidelityResult.DifferenceBucketSummary classes = result.getBucketSummary(
                ArtifactFidelityResult.DifferenceBucket.CLASS_BYTECODE);

        assertEquals(1, manifest.getMissing());
        assertEquals(1, manifest.getExtra());
        assertEquals(0, manifest.getDifferent());
        assertEquals(2, manifest.getTotal());
        assertTrue(manifest.getSamples().contains("META-INF/MANIFEST.MF"));

        assertEquals(0, classes.getMissing());
        assertEquals(0, classes.getExtra());
        assertEquals(1, classes.getDifferent());
        assertEquals(1, classes.getTotal());
        assertTrue(classes.getSamples().contains("com/example/App.class"));
    }

    @Test
    void classifiesArtifactDifferencesIntoBuckets() throws Exception {
        Path original = tempDir.resolve("original.jar");
        Path rebuilt = tempDir.resolve("rebuilt.jar");
        writeJar(original,
                entry("META-INF/MANIFEST.MF", "Created-By: original\n"),
                entry("com/example/App.class", classBytes("App-v1")),
                entry("BOOT-INF/lib/dependency.jar", "dependency-v1"),
                entry("META-INF/maven/com.example/app/pom.properties", "version=1.0"),
                entry("BOOT-INF/classes/META-INF/services/com.example.Plugin", "com.example.PluginV1"),
                entry("BOOT-INF/classpath.idx", "- old.jar\n"),
                entry("META-INF/APP.SF", "signature-v1"),
                entry("static/app.js", "console.log('old');"),
                entry("WEB-INF/lib/old-only.jar", "old-only"));
        writeJar(rebuilt,
                entry("META-INF/MANIFEST.MF", "Created-By: rebuilt\n"),
                entry("com/example/App.class", classBytes("App-v2")),
                entry("BOOT-INF/lib/dependency.jar", "dependency-v2"),
                entry("META-INF/maven/com.example/app/pom.properties", "version=2.0"),
                entry("BOOT-INF/classes/META-INF/services/com.example.Plugin", "com.example.PluginV2"),
                entry("BOOT-INF/classpath.idx", "- new.jar\n"),
                entry("META-INF/APP.SF", "signature-v2"),
                entry("static/app.js", "console.log('new');"),
                entry("WEB-INF/lib/new-only.jar", "new-only"));

        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), rebuilt.toFile());

        assertEquals(8, result.getDifferentSha256());
        assertEquals(1, result.getMissingEntries());
        assertEquals(1, result.getExtraEntries());
        assertEquals(1, result.getMissingNestedLibs());
        assertEquals(1, result.getExtraNestedLibs());

        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.MANIFEST, 0, 0, 1,
                "META-INF/MANIFEST.MF");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.CLASS_BYTECODE, 0, 0, 1,
                "com/example/App.class");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.NESTED_LIBRARY, 1, 1, 1,
                "BOOT-INF/lib/dependency.jar");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.MAVEN_METADATA, 0, 0, 1,
                "META-INF/maven/com.example/app/pom.properties");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.SERVICE_METADATA, 0, 0, 1,
                "BOOT-INF/classes/META-INF/services/com.example.Plugin");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.BOOT_INDEX, 0, 0, 1,
                "BOOT-INF/classpath.idx");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.SIGNATURE_METADATA, 0, 0, 1,
                "META-INF/APP.SF");
        assertBucket(result, ArtifactFidelityResult.DifferenceBucket.RESOURCE_ENTRY, 0, 0, 1,
                "static/app.js");
    }

    private void assertBucket(ArtifactFidelityResult result, ArtifactFidelityResult.DifferenceBucket bucket,
                              int missing, int extra, int different, String sample) {
        ArtifactFidelityResult.DifferenceBucketSummary summary = result.getBucketSummary(bucket);
        assertEquals(missing, summary.getMissing());
        assertEquals(extra, summary.getExtra());
        assertEquals(different, summary.getDifferent());
        assertEquals(missing + extra + different, summary.getTotal());
        assertTrue(summary.getSamples().contains(sample));
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
