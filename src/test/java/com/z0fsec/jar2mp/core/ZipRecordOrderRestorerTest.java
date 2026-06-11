package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ZipRecordOrderRestorerTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresOriginalEntryOrderWithoutRecompressingRecords() throws Exception {
        TestEntry first = entry("first.txt", "one");
        TestEntry second = entry("second.txt", "two");
        Path original = writeStoredZip("original.jar", 0L, first, second);
        Path rebuilt = writeStoredZip("rebuilt.jar", 0L, second, first);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalEntryTimestampsWhileReorderingRecords() throws Exception {
        TestEntry first = entry("first.txt", "one");
        TestEntry second = entry("second.txt", "two");
        Path original = writeStoredZip("original.jar", 0L, first, second);
        Path rebuilt = writeStoredZip("rebuilt.jar", 2000L, second, first);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalDirectoryEntrySetWhenOnlyEmptyDirectoriesDiffer() throws Exception {
        TestEntry webInf = directory("WEB-INF/");
        TestEntry webXml = entry("WEB-INF/web.xml", "<web-app/>");
        TestEntry generatedMavenDir = directory("META-INF/maven/");
        Path original = writeStoredZip("original.war", 0L, webInf, webXml);
        Path rebuilt = writeStoredZip("rebuilt.war", 2000L, generatedMavenDir, webInf, webXml);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalEmptyDirectoryCompressionRecords() throws Exception {
        TestEntry bootInf = deflatedDirectory("BOOT-INF/");
        TestEntry appClass = entry("BOOT-INF/classes/App.class", "bytecode");
        Path original = writeStoredZip("original.jar", 0L, bootInf, appClass);
        Path rebuilt = writeStoredZip("rebuilt.jar", 2000L, directory("BOOT-INF/"), appClass);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalModuleInfoPayload() throws Exception {
        TestEntry manifest = entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n");
        TestEntry originalModule = entry("META-INF/versions/9/module-info.class",
                new byte[]{0x01, 0x02, 0x03});
        TestEntry rebuiltModule = entry("META-INF/versions/9/module-info.class",
                new byte[]{0x01, 0x02, 0x03, 0x04});
        Path original = writeStoredZip("original.jar", 0L, manifest, originalModule);
        Path rebuilt = writeStoredZip("rebuilt.jar", 2000L, manifest, rebuiltModule);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalCompressedPayloadWhenContentMatches() throws Exception {
        TestEntry resource = deflatedEntry("org/example/Resource.txt",
                "same payload ".repeat(400).getBytes(StandardCharsets.UTF_8));
        Path original = writeDeflatedZip("original.jar", 0L, Deflater.BEST_COMPRESSION, resource);
        Path rebuilt = writeDeflatedZip("rebuilt.jar", 2000L, Deflater.BEST_SPEED, resource);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalPayloadWhenRebuiltResourceContentDiffers() throws Exception {
        TestEntry originalResource = deflatedEntry("logback.xml",
                "<configuration><appender-ref ref=\"STDOUT\" /></configuration>".getBytes(StandardCharsets.UTF_8));
        TestEntry rebuiltResource = deflatedEntry("logback.xml",
                "<configuration></configuration>".getBytes(StandardCharsets.UTF_8));
        Path original = writeDeflatedZip("original.jar", 0L, Deflater.BEST_COMPRESSION, originalResource);
        Path rebuilt = writeDeflatedZip("rebuilt.jar", 2000L, Deflater.BEST_COMPRESSION, rebuiltResource);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void restoresOriginalEntriesWhenRebuiltEntrySetDiffers() throws Exception {
        Path original = writeStoredZip("original.jar", 0L,
                entry("first.txt", "one"),
                entry("original-only.class", "original"));
        Path rebuilt = writeStoredZip("rebuilt.jar", 0L,
                entry("first.txt", "one"),
                entry("rebuilt-only.class", "rebuilt"));

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    private Path writeStoredZip(String fileName, long entryTime, TestEntry... entries) throws Exception {
        Path zip = tempDir.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (TestEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                zipEntry.setMethod(entry.method);
                zipEntry.setTime(entryTime);
                if (entry.method == ZipEntry.STORED) {
                    zipEntry.setSize(entry.content.length);
                    zipEntry.setCompressedSize(entry.content.length);
                    zipEntry.setCrc(crc32(entry.content));
                }
                zipEntry.setExtra(new byte[0]);
                output.putNextEntry(zipEntry);
                output.write(entry.content);
                output.closeEntry();
            }
        }
        return zip;
    }

    private Path writeDeflatedZip(String fileName, long entryTime, int level, TestEntry... entries)
            throws Exception {
        Path zip = tempDir.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.setLevel(level);
            for (TestEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                zipEntry.setMethod(entry.method);
                zipEntry.setTime(entryTime);
                zipEntry.setExtra(new byte[0]);
                output.putNextEntry(zipEntry);
                output.write(entry.content);
                output.closeEntry();
            }
        }
        return zip;
    }

    private long crc32(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return crc.getValue();
    }

    private TestEntry entry(String name, String content) {
        return new TestEntry(name, content.getBytes(StandardCharsets.UTF_8), ZipEntry.STORED);
    }

    private TestEntry entry(String name, byte[] content) {
        return new TestEntry(name, content, ZipEntry.STORED);
    }

    private TestEntry directory(String name) {
        return new TestEntry(name, new byte[0], ZipEntry.STORED);
    }

    private TestEntry deflatedDirectory(String name) {
        return new TestEntry(name, new byte[0], ZipEntry.DEFLATED);
    }

    private TestEntry deflatedEntry(String name, byte[] content) {
        return new TestEntry(name, content, ZipEntry.DEFLATED);
    }

    private static class TestEntry {
        private final String name;
        private final byte[] content;
        private final int method;

        private TestEntry(String name, byte[] content, int method) {
            this.name = name;
            this.content = content;
            this.method = method;
        }
    }
}
