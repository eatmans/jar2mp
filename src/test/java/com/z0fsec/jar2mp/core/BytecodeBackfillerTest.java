package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BytecodeBackfillerTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesOriginalClassBytesToTargetClasses() throws Exception {
        byte[] originalBytes = new byte[]{0x01, 0x02, 0x03};
        byte[] rebuiltBytes = new byte[]{0x04, 0x05, 0x06};
        String classPath = "com/example/App.class";

        File jar = createJar("original.jar", classPath, originalBytes);
        Path classesDir = tempDir.resolve("project/target/classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.write(classesDir.resolve("com/example/App.class"), rebuiltBytes);

        int count = new BytecodeBackfiller().backfill(
                jar, tempDir.resolve("project").toFile(), Arrays.asList(classPath));

        assertEquals(1, count);
        assertArrayEquals(originalBytes, Files.readAllBytes(classesDir.resolve("com/example/App.class")));
    }

    @Test
    void handlesSpringBootFatJarBootInfPrefix() throws Exception {
        byte[] originalBytes = new byte[]{0x10, 0x20, 0x30};
        byte[] rebuiltBytes = new byte[]{0x40, 0x50, 0x60};
        String classPath = "com/example/Service.class";

        File jar = createJarWithPrefix("fat.jar", "BOOT-INF/classes/", classPath, originalBytes);
        Path classesDir = tempDir.resolve("project/target/classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        Files.write(classesDir.resolve("com/example/Service.class"), rebuiltBytes);

        int count = new BytecodeBackfiller().backfill(
                jar, tempDir.resolve("project").toFile(), Arrays.asList(classPath));

        assertEquals(1, count);
        assertArrayEquals(originalBytes, Files.readAllBytes(classesDir.resolve("com/example/Service.class")));
    }

    @Test
    void skipsClassNotInDifferentList() throws Exception {
        byte[] originalBytes = new byte[]{0x01, 0x02};
        String classPath = "com/example/Util.class";

        File jar = createJar("original.jar", classPath, originalBytes);
        Path classesDir = tempDir.resolve("project/target/classes");
        Files.createDirectories(classesDir.resolve("com/example"));
        byte[] rebuiltBytes = new byte[]{0x09, 0x08};
        Files.write(classesDir.resolve("com/example/Util.class"), rebuiltBytes);

        int count = new BytecodeBackfiller().backfill(
                jar, tempDir.resolve("project").toFile(), Collections.emptyList());

        assertEquals(0, count);
        assertArrayEquals(rebuiltBytes, Files.readAllBytes(classesDir.resolve("com/example/Util.class")));
    }

    @Test
    void returnsZeroWhenTargetClassesDirAbsent() throws Exception {
        File jar = createJar("original.jar", "com/example/X.class", new byte[]{0x01});

        int count = new BytecodeBackfiller().backfill(
                jar, tempDir.resolve("no-project").toFile(), Arrays.asList("com/example/X.class"));

        assertEquals(0, count);
    }

    @Test
    void returnsZeroWhenOriginalArtifactNull() throws Exception {
        int count = new BytecodeBackfiller().backfill(
                null, tempDir.toFile(), Arrays.asList("com/example/X.class"));
        assertEquals(0, count);
    }

    private File createJar(String name, String classPath, byte[] bytes) throws IOException {
        return createJarWithPrefix(name, "", classPath, bytes);
    }

    private File createJarWithPrefix(String name, String prefix, String classPath, byte[] bytes)
            throws IOException {
        File jar = tempDir.resolve(name).toFile();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar.toPath()))) {
            String entryName = prefix.isEmpty() ? classPath : prefix + classPath;
            ZipEntry entry = new ZipEntry(entryName);
            out.putNextEntry(entry);
            out.write(bytes);
            out.closeEntry();
        }
        return jar;
    }
}
