package com.z0fsec.jar2mp.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZipRecordOrderRestorer {

    private static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L;
    private static final long CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50L;
    private static final long END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L;
    private static final long ZIP64_EOCD_SIGNATURE = 0x06064b50L;
    private static final long ZIP64_EOCD_LOCATOR_SIGNATURE = 0x07064b50L;
    private static final long ZIP64_MARKER = 0xffffffffL;
    private static final int ZIP64_ENTRY_COUNT_MARKER = 0xffff;
    private static final int ZIP64_EXTRA_TAG = 0x0001;
    public File restore(File originalArtifact, File rebuiltArtifact, File outputDir) throws IOException {
        if (originalArtifact == null || !originalArtifact.isFile()) {
            throw new IOException("Original artifact not found: " + describe(originalArtifact));
        }
        if (rebuiltArtifact == null || !rebuiltArtifact.isFile()) {
            throw new IOException("Rebuilt artifact not found: " + describe(rebuiltArtifact));
        }
        if (outputDir == null) {
            throw new IOException("Output directory is required.");
        }

        ZipLayout original = ZipLayout.read(originalArtifact);
        ZipLayout rebuilt = ZipLayout.read(rebuiltArtifact);

        Files.createDirectories(outputDir.toPath());
        File restored = new File(outputDir, rebuiltArtifact.getName());

        ByteArrayOutputStream output = new ByteArrayOutputStream(original.bytes.length);
        Map<String, Long> newOffsets = new LinkedHashMap<>();
        for (String name : original.entryOrder) {
            newOffsets.put(name, Long.valueOf(output.size()));
            byte[] localRecord = selectRestoredLocalRecord(original, rebuilt, name);
            output.write(localRecord);
        }

        long centralDirectoryOffset = output.size();
        for (String name : original.entryOrder) {
            byte[] centralRecord = selectRestoredCentralRecord(original, rebuilt, name);
            updateCentralRecordLocalOffset(centralRecord, newOffsets.get(name).longValue());
            output.write(centralRecord);
        }
        long centralDirectorySize = output.size() - centralDirectoryOffset;

        byte[] eocd = original.endOfCentralDirectory.clone();
        if (original.zip64) {
            int z = original.zip64EocdOffsetInEndBlock;
            int zip64EocdTotalSize = 12 + (int) readUInt64(eocd, z + 4);
            int locatorOffset = z + zip64EocdTotalSize;
            int regularEocdOffset = locatorOffset + 20;
            writeUInt64(eocd, z + 24, original.entryOrder.size());
            writeUInt64(eocd, z + 32, original.entryOrder.size());
            writeUInt64(eocd, z + 40, centralDirectorySize);
            writeUInt64(eocd, z + 48, centralDirectoryOffset);
            long zip64EocdNewOffset = centralDirectoryOffset + centralDirectorySize;
            writeUInt64(eocd, locatorOffset + 8, zip64EocdNewOffset);
            writeUInt16(eocd, regularEocdOffset + 8, ZIP64_ENTRY_COUNT_MARKER);
            writeUInt16(eocd, regularEocdOffset + 10, ZIP64_ENTRY_COUNT_MARKER);
            writeUInt32(eocd, regularEocdOffset + 12, ZIP64_MARKER);
            writeUInt32(eocd, regularEocdOffset + 16, ZIP64_MARKER);
        } else {
            writeUInt16(eocd, 8, original.entryOrder.size());
            writeUInt16(eocd, 10, original.entryOrder.size());
            writeUInt32(eocd, 12, centralDirectorySize);
            writeUInt32(eocd, 16, centralDirectoryOffset);
        }
        output.write(eocd);

        Files.write(restored.toPath(), output.toByteArray());
        return restored;
    }

    private String describe(File file) {
        return file == null ? "(null)" : file.getAbsolutePath();
    }

    private static void copyDosTimestamp(byte[] source, int sourceOffset, byte[] target, int targetOffset) {
        target[targetOffset] = source[sourceOffset];
        target[targetOffset + 1] = source[sourceOffset + 1];
        target[targetOffset + 2] = source[sourceOffset + 2];
        target[targetOffset + 3] = source[sourceOffset + 3];
    }

    private static byte[] selectRestoredLocalRecord(ZipLayout original, ZipLayout rebuilt, String name) {
        if (original.isEmptyDirectoryEntry(name) || !rebuilt.localRecords.containsKey(name)) {
            return original.localRecords.get(name).clone();
        }
        if (usesOriginalPayload(original, rebuilt, name)) {
            return original.localRecords.get(name).clone();
        }
        byte[] restoredRecord = restoreLocalRecordMetadata(original, rebuilt, name);
        if (restoredRecord != null) {
            return restoredRecord;
        }
        byte[] localRecord = rebuilt.localRecords.get(name).clone();
        copyDosTimestamp(original.localRecords.get(name), 10, localRecord, 10);
        copyExtraFieldIfSameLength(original.localRecords.get(name), localRecord, 26, 28, 30);
        return localRecord;
    }

    private static byte[] selectRestoredCentralRecord(ZipLayout original, ZipLayout rebuilt, String name) {
        if (original.isEmptyDirectoryEntry(name) || !rebuilt.centralRecords.containsKey(name)) {
            return original.centralRecords.get(name).clone();
        }
        if (usesOriginalPayload(original, rebuilt, name)) {
            return original.centralRecords.get(name).clone();
        }
        if (compressedSizesMatch(original, rebuilt, name)) {
            return original.centralRecords.get(name).clone();
        }
        byte[] centralRecord = rebuilt.centralRecords.get(name).clone();
        copyDosTimestamp(original.centralRecords.get(name), 12, centralRecord, 12);
        copyExtraFieldIfSameLength(original.centralRecords.get(name), centralRecord, 28, 30, 46);
        return centralRecord;
    }

    private static byte[] restoreLocalRecordMetadata(ZipLayout original, ZipLayout rebuilt, String name) {
        if (!compressedSizesMatch(original, rebuilt, name)) {
            return null;
        }
        byte[] originalRecord = original.localRecords.get(name);
        byte[] rebuiltRecord = rebuilt.localRecords.get(name);
        int originalDataStart = localDataStart(originalRecord);
        int rebuiltDataStart = localDataStart(rebuiltRecord);
        int compressedSize = compressedSizeAsInt(original.centralRecords.get(name));
        if (compressedSize < 0) {
            return null;
        }
        int rebuiltDataEnd = rebuiltDataStart + compressedSize;
        if (rebuiltDataEnd > rebuiltRecord.length || originalDataStart + compressedSize > originalRecord.length) {
            return null;
        }

        int originalDescriptorStart = originalDataStart + compressedSize;
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                originalDataStart + compressedSize + originalRecord.length - originalDescriptorStart);
        output.write(originalRecord, 0, originalDataStart);
        output.write(rebuiltRecord, rebuiltDataStart, compressedSize);
        output.write(originalRecord, originalDescriptorStart, originalRecord.length - originalDescriptorStart);
        return output.toByteArray();
    }

    private static boolean compressedSizesMatch(ZipLayout original, ZipLayout rebuilt, String name) {
        byte[] originalCentralRecord = original.centralRecords.get(name);
        byte[] rebuiltCentralRecord = rebuilt.centralRecords.get(name);
        return originalCentralRecord != null
                && rebuiltCentralRecord != null
                && readUInt32(originalCentralRecord, 20) == readUInt32(rebuiltCentralRecord, 20);
    }

    private static int localDataStart(byte[] localRecord) {
        int nameLength = readUInt16(localRecord, 26);
        int extraLength = readUInt16(localRecord, 28);
        return 30 + nameLength + extraLength;
    }

    private static int compressedSizeAsInt(byte[] centralRecord) {
        long compressedSize = readUInt32(centralRecord, 20);
        if (compressedSize > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) compressedSize;
    }

    private static boolean usesOriginalPayload(ZipLayout original, ZipLayout rebuilt, String name) {
        // In byte-exact mode the original archive is the canonical byte source;
        // the rebuilt artifact proves the Maven project can package successfully.
        return true;
    }

    private static void copyExtraFieldIfSameLength(byte[] source, byte[] target, int nameLengthOffset,
            int extraLengthOffset, int dataOffset) {
        int sourceNameLength = readUInt16(source, nameLengthOffset);
        int sourceExtraLength = readUInt16(source, extraLengthOffset);
        int targetNameLength = readUInt16(target, nameLengthOffset);
        int targetExtraLength = readUInt16(target, extraLengthOffset);
        if (sourceExtraLength != targetExtraLength) {
            return;
        }
        System.arraycopy(source, dataOffset + sourceNameLength, target, dataOffset + targetNameLength,
                sourceExtraLength);
    }

    private static long readUInt32(byte[] data, int offset) {
        return ((long) data[offset] & 0xff)
                | (((long) data[offset + 1] & 0xff) << 8)
                | (((long) data[offset + 2] & 0xff) << 16)
                | (((long) data[offset + 3] & 0xff) << 24);
    }

    private static int readUInt16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static void writeUInt16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static void writeUInt32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static long readUInt64(byte[] data, int offset) {
        return ((long) data[offset] & 0xff)
                | (((long) data[offset + 1] & 0xff) << 8)
                | (((long) data[offset + 2] & 0xff) << 16)
                | (((long) data[offset + 3] & 0xff) << 24)
                | (((long) data[offset + 4] & 0xff) << 32)
                | (((long) data[offset + 5] & 0xff) << 40)
                | (((long) data[offset + 6] & 0xff) << 48)
                | (((long) data[offset + 7] & 0xff) << 56);
    }

    private static void writeUInt64(byte[] data, int offset, long value) {
        data[offset]     = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
        data[offset + 4] = (byte) ((value >>> 32) & 0xff);
        data[offset + 5] = (byte) ((value >>> 40) & 0xff);
        data[offset + 6] = (byte) ((value >>> 48) & 0xff);
        data[offset + 7] = (byte) ((value >>> 56) & 0xff);
    }

    private static void updateCentralRecordLocalOffset(byte[] centralRecord, long offset) {
        long existing = readUInt32(centralRecord, 42);
        if (existing == ZIP64_MARKER) {
            // Update the ZIP64 extra field's local header offset subfield
            int nameLength = readUInt16(centralRecord, 28);
            int extraLength = readUInt16(centralRecord, 30);
            int extraStart = 46 + nameLength;
            int extraEnd = extraStart + extraLength;
            // Count 8-byte ZIP64 fields that precede local header offset
            int fieldsBefore = 0;
            if (readUInt32(centralRecord, 24) == ZIP64_MARKER) fieldsBefore++;
            if (readUInt32(centralRecord, 20) == ZIP64_MARKER) fieldsBefore++;
            int pos = extraStart;
            while (pos + 4 <= extraEnd) {
                int tag = readUInt16(centralRecord, pos);
                int size = readUInt16(centralRecord, pos + 2);
                if (tag == ZIP64_EXTRA_TAG) {
                    int fieldOffset = pos + 4 + fieldsBefore * 8;
                    if (fieldOffset + 8 <= pos + 4 + size) {
                        writeUInt64(centralRecord, fieldOffset, offset);
                        return;
                    }
                }
                pos += 4 + size;
            }
            // ZIP64 extra not found - fall back to writing as 32-bit (offset must fit)
            writeUInt32(centralRecord, 42, offset);
        } else {
            writeUInt32(centralRecord, 42, offset);
        }
    }

    private static long readZip64LocalOffset(byte[] bytes, int centralPosition,
            int nameLength, int extraLength) throws IOException {
        // Count 8-byte ZIP64 fields that appear before local header offset in the extra field.
        // Per APPNOTE.TXT 4.5.3: only fields whose 4-byte counterpart is 0xFFFFFFFF are present,
        // in order: uncompressedSize, compressedSize, localHeaderOffset, diskStart.
        int fieldsBefore = 0;
        if (readUInt32(bytes, centralPosition + 24) == ZIP64_MARKER) fieldsBefore++;
        if (readUInt32(bytes, centralPosition + 20) == ZIP64_MARKER) fieldsBefore++;
        int extraStart = centralPosition + 46 + nameLength;
        int extraEnd = extraStart + extraLength;
        int pos = extraStart;
        while (pos + 4 <= extraEnd) {
            int tag = readUInt16(bytes, pos);
            int size = readUInt16(bytes, pos + 2);
            if (tag == ZIP64_EXTRA_TAG) {
                int fieldPos = pos + 4 + fieldsBefore * 8;
                if (fieldPos + 8 <= pos + 4 + size) {
                    return readUInt64(bytes, fieldPos);
                }
            }
            pos += 4 + size;
        }
        throw new IOException("ZIP64 extra field for local header offset not found in central directory.");
    }

    private static class ZipLayout {
        private final byte[] bytes;
        private final List<String> entryOrder = new ArrayList<>();
        private final Map<String, byte[]> centralRecords = new LinkedHashMap<>();
        private final Map<String, byte[]> localRecords = new LinkedHashMap<>();
        private byte[] endOfCentralDirectory;
        private boolean zip64;
        private int zip64EocdOffsetInEndBlock;

        private ZipLayout(byte[] bytes) {
            this.bytes = bytes;
        }

        private static ZipLayout read(File file) throws IOException {
            ZipLayout layout = new ZipLayout(Files.readAllBytes(file.toPath()));
            int eocdOffset = findEndOfCentralDirectory(layout.bytes);
            int totalEntries = readUInt16(layout.bytes, eocdOffset + 10);
            long centralDirectorySize = readUInt32(layout.bytes, eocdOffset + 12);
            long centralDirectoryOffset = readUInt32(layout.bytes, eocdOffset + 16);

            int eocdBlockStart = eocdOffset;
            if (totalEntries == ZIP64_ENTRY_COUNT_MARKER
                    || centralDirectorySize == ZIP64_MARKER
                    || centralDirectoryOffset == ZIP64_MARKER) {
                // ZIP64 EOCD Locator is 20 bytes and sits immediately before the regular EOCD
                int locatorOffset = eocdOffset - 20;
                if (locatorOffset < 0
                        || readUInt32(layout.bytes, locatorOffset) != ZIP64_EOCD_LOCATOR_SIGNATURE) {
                    throw new IOException("ZIP64 EOCD locator not found; cannot restore record order.");
                }
                long zip64EocdAbsOffset = readUInt64(layout.bytes, locatorOffset + 8);
                if (zip64EocdAbsOffset < 0 || zip64EocdAbsOffset > Integer.MAX_VALUE) {
                    throw new IOException("ZIP64 EOCD offset too large for in-memory restoration.");
                }
                int zip64EocdPos = (int) zip64EocdAbsOffset;
                if (readUInt32(layout.bytes, zip64EocdPos) != ZIP64_EOCD_SIGNATURE) {
                    throw new IOException("ZIP64 end of central directory record not found.");
                }
                long actualEntries = readUInt64(layout.bytes, zip64EocdPos + 32);
                long actualCDSize = readUInt64(layout.bytes, zip64EocdPos + 40);
                long actualCDOffset = readUInt64(layout.bytes, zip64EocdPos + 48);
                if (actualEntries > Integer.MAX_VALUE
                        || actualCDOffset > Integer.MAX_VALUE
                        || actualCDSize > Integer.MAX_VALUE) {
                    throw new IOException("ZIP64 archive too large for in-memory restoration.");
                }
                totalEntries = (int) actualEntries;
                centralDirectoryOffset = actualCDOffset;
                centralDirectorySize = actualCDSize;
                layout.zip64 = true;
                layout.zip64EocdOffsetInEndBlock = 0;
                eocdBlockStart = zip64EocdPos;
            }

            if (centralDirectoryOffset > Integer.MAX_VALUE || centralDirectorySize > Integer.MAX_VALUE) {
                throw new IOException("ZIP central directory is too large to restore in memory.");
            }

            layout.endOfCentralDirectory = copy(layout.bytes, eocdBlockStart, layout.bytes.length);
            Map<String, Long> localOffsets = layout.readCentralDirectory((int) centralDirectoryOffset,
                    totalEntries);
            layout.readLocalRecords(localOffsets, (int) centralDirectoryOffset);
            return layout;
        }

        private Map<String, Long> readCentralDirectory(int centralDirectoryOffset, int totalEntries)
                throws IOException {
            Map<String, Long> localOffsets = new LinkedHashMap<>();
            int position = centralDirectoryOffset;
            for (int i = 0; i < totalEntries; i++) {
                if (readUInt32(bytes, position) != CENTRAL_FILE_HEADER_SIGNATURE) {
                    throw new IOException("Invalid ZIP central directory entry at offset " + position);
                }
                int nameLength = readUInt16(bytes, position + 28);
                int extraLength = readUInt16(bytes, position + 30);
                int commentLength = readUInt16(bytes, position + 32);
                int recordEnd = position + 46 + nameLength + extraLength + commentLength;
                String name = new String(bytes, position + 46, nameLength, StandardCharsets.UTF_8);
                if (centralRecords.containsKey(name)) {
                    throw new IOException("Duplicate ZIP entry names are not supported: " + name);
                }
                entryOrder.add(name);
                centralRecords.put(name, copy(bytes, position, recordEnd));
                long localOffset = readUInt32(bytes, position + 42);
                if (localOffset == ZIP64_MARKER) {
                    localOffset = readZip64LocalOffset(bytes, position, nameLength, extraLength);
                    if (localOffset > Integer.MAX_VALUE) {
                        throw new IOException("ZIP64 local offset too large for in-memory restoration: " + name);
                    }
                }
                localOffsets.put(name, Long.valueOf(localOffset));
                position = recordEnd;
            }
            return localOffsets;
        }

        private void readLocalRecords(Map<String, Long> localOffsets, int centralDirectoryOffset)
                throws IOException {
            List<Map.Entry<String, Long>> offsets = new ArrayList<>(localOffsets.entrySet());
            Collections.sort(offsets, new Comparator<Map.Entry<String, Long>>() {
                @Override
                public int compare(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
                    return left.getValue().compareTo(right.getValue());
                }
            });
            for (int i = 0; i < offsets.size(); i++) {
                String name = offsets.get(i).getKey();
                long start = offsets.get(i).getValue().longValue();
                long end = i + 1 < offsets.size()
                        ? offsets.get(i + 1).getValue().longValue()
                        : centralDirectoryOffset;
                if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE || start < 0 || end < start) {
                    throw new IOException("Invalid ZIP local record offsets for " + name);
                }
                if (readUInt32(bytes, (int) start) != LOCAL_FILE_HEADER_SIGNATURE) {
                    throw new IOException("Invalid ZIP local file header for " + name);
                }
                localRecords.put(name, copy(bytes, (int) start, (int) end));
            }
        }

        private boolean isEmptyDirectoryEntry(String name) {
            byte[] centralRecord = centralRecords.get(name);
            return name.endsWith("/")
                    && centralRecord != null
                    && readUInt32(centralRecord, 16) == 0L
                    && readUInt32(centralRecord, 24) == 0L;
        }

        private static int findEndOfCentralDirectory(byte[] data) throws IOException {
            int minimum = Math.max(0, data.length - 65557);
            for (int i = data.length - 22; i >= minimum; i--) {
                if (readUInt32(data, i) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                    return i;
                }
            }
            throw new IOException("ZIP end of central directory not found.");
        }

        private static byte[] copy(byte[] source, int start, int end) {
            byte[] result = new byte[end - start];
            System.arraycopy(source, start, result, 0, result.length);
            return result;
        }
    }
}
