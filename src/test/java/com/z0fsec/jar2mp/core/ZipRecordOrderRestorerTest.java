package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipRecordOrderRestorerTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresOriginalEntryOrderWithoutRecompressingRecords() throws Exception {
        TestEntry first = entry("first.txt", "one");
        TestEntry second = entry("second.txt", "two");
        Path original = writeStoredZip("original.jar", first, second);
        Path rebuilt = writeStoredZip("rebuilt.jar", second, first);

        Path restored = new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile())
                .toPath();

        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
    }

    @Test
    void rejectsArchivesWithDifferentEntrySets() throws Exception {
        Path original = writeStoredZip("original.jar", entry("first.txt", "one"));
        Path rebuilt = writeStoredZip("rebuilt.jar", entry("second.txt", "two"));

        assertThrows(IOException.class, () -> new ZipRecordOrderRestorer()
                .restore(original.toFile(), rebuilt.toFile(), tempDir.resolve("out").toFile()));
    }

    private Path writeStoredZip(String fileName, TestEntry... entries) throws Exception {
        Path zip = tempDir.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (TestEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setTime(0L);
                zipEntry.setSize(entry.content.length);
                zipEntry.setCompressedSize(entry.content.length);
                zipEntry.setCrc(crc32(entry.content));
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
        return new TestEntry(name, content.getBytes(StandardCharsets.UTF_8));
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
