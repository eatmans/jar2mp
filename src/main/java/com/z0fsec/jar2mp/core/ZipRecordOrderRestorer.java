package com.z0fsec.jar2mp.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ZipRecordOrderRestorer {

    private static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L;
    private static final long CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50L;
    private static final long END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L;
    private static final long ZIP64_MARKER = 0xffffffffL;
    private static final int ZIP64_ENTRY_COUNT_MARKER = 0xffff;
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

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
        validateSameEntrySet(original, rebuilt);

        Files.createDirectories(outputDir.toPath());
        File restored = new File(outputDir, rebuiltArtifact.getName());

        ByteArrayOutputStream output = new ByteArrayOutputStream(rebuilt.bytes.length);
        Map<String, Long> newOffsets = new LinkedHashMap<>();
        for (String name : original.entryOrder) {
            newOffsets.put(name, Long.valueOf(output.size()));
            byte[] localRecord = selectRestoredLocalRecord(original, rebuilt, name);
            output.write(localRecord);
        }

        long centralDirectoryOffset = output.size();
        for (String name : original.entryOrder) {
            byte[] centralRecord = selectRestoredCentralRecord(original, rebuilt, name);
            writeUInt32(centralRecord, 42, newOffsets.get(name).longValue());
            output.write(centralRecord);
        }
        long centralDirectorySize = output.size() - centralDirectoryOffset;

        byte[] eocd = rebuilt.endOfCentralDirectory.clone();
        writeUInt16(eocd, 8, original.entryOrder.size());
        writeUInt16(eocd, 10, original.entryOrder.size());
        writeUInt32(eocd, 12, centralDirectorySize);
        writeUInt32(eocd, 16, centralDirectoryOffset);
        output.write(eocd);

        Files.write(restored.toPath(), output.toByteArray());
        return restored;
    }

    private void validateSameEntrySet(ZipLayout original, ZipLayout rebuilt) throws IOException {
        Set<String> originalNames = new HashSet<>(original.entryOrder);
        Set<String> rebuiltNames = new HashSet<>(rebuilt.entryOrder);
        for (String name : originalNames) {
            if (!rebuiltNames.contains(name) && !original.isEmptyDirectoryEntry(name)) {
                throw new IOException("Cannot restore ZIP entry order because original entry is missing from rebuilt: "
                        + name);
            }
        }
        for (String name : rebuiltNames) {
            if (!originalNames.contains(name) && !rebuilt.isEmptyDirectoryEntry(name)) {
                throw new IOException("Cannot restore ZIP entry order because rebuilt has extra entry: " + name);
            }
        }
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
        if (usesOriginalPayload(name)) {
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
        if (usesOriginalPayload(name)) {
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

    private static boolean usesOriginalPayload(String name) {
        return MANIFEST_ENTRY.equals(name);
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

    private static class ZipLayout {
        private final byte[] bytes;
        private final List<String> entryOrder = new ArrayList<>();
        private final Map<String, byte[]> centralRecords = new LinkedHashMap<>();
        private final Map<String, byte[]> localRecords = new LinkedHashMap<>();
        private byte[] endOfCentralDirectory;

        private ZipLayout(byte[] bytes) {
            this.bytes = bytes;
        }

        private static ZipLayout read(File file) throws IOException {
            ZipLayout layout = new ZipLayout(Files.readAllBytes(file.toPath()));
            int eocdOffset = findEndOfCentralDirectory(layout.bytes);
            layout.endOfCentralDirectory = copy(layout.bytes, eocdOffset, layout.bytes.length);
            int totalEntries = readUInt16(layout.bytes, eocdOffset + 10);
            long centralDirectorySize = readUInt32(layout.bytes, eocdOffset + 12);
            long centralDirectoryOffset = readUInt32(layout.bytes, eocdOffset + 16);
            if (totalEntries == ZIP64_ENTRY_COUNT_MARKER
                    || centralDirectorySize == ZIP64_MARKER
                    || centralDirectoryOffset == ZIP64_MARKER) {
                throw new IOException("ZIP64 archives are not supported for record-order restoration yet.");
            }
            if (centralDirectoryOffset > Integer.MAX_VALUE || centralDirectorySize > Integer.MAX_VALUE) {
                throw new IOException("ZIP central directory is too large to restore in memory.");
            }

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
                localOffsets.put(name, Long.valueOf(readUInt32(bytes, position + 42)));
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
