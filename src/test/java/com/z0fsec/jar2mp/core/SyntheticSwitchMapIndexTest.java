package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SyntheticSwitchMapIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void readsOriginalEnumCaseOrderFromSyntheticSwitchMapClass() throws Exception {
        Path sourceDir = tempDir.resolve("src/demo");
        Files.createDirectories(sourceDir);
        Path sampleSource = sourceDir.resolve("Sample.java");
        Files.writeString(sampleSource,
                "package demo;\n"
                        + "public class Sample {\n"
                        + "    enum Kind { FIRST, SECOND, THIRD }\n"
                        + "}\n");
        Path switchMapSource = sourceDir.resolve("Sample$1.java");
        Files.writeString(switchMapSource,
                "package demo;\n"
                        + "class Sample$1 {\n"
                        + "    static final int[] $SwitchMap$demo$Sample$Kind;\n"
                        + "    static {\n"
                        + "        $SwitchMap$demo$Sample$Kind = new int[Sample.Kind.values().length];\n"
                        + "        try {\n"
                        + "            $SwitchMap$demo$Sample$Kind[Sample.Kind.THIRD.ordinal()] = 1;\n"
                        + "        } catch (NoSuchFieldError ignored) {\n"
                        + "        }\n"
                        + "        try {\n"
                        + "            $SwitchMap$demo$Sample$Kind[Sample.Kind.FIRST.ordinal()] = 2;\n"
                        + "        } catch (NoSuchFieldError ignored) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-d", classesDir.toString(),
                sampleSource.toString(), switchMapSource.toString()));

        Path jar = tempDir.resolve("sample.jar");
        writeJar(classesDir, jar);

        Map<String, Map<Integer, String>> switchMaps;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            switchMaps = SyntheticSwitchMapIndex.fromJar(jarFile);
        }

        Map<Integer, String> kindSwitchMap = switchMaps.get("$SwitchMap$demo$Sample$Kind");
        assertNotNull(kindSwitchMap);
        assertEquals("THIRD", kindSwitchMap.get(1));
        assertEquals("FIRST", kindSwitchMap.get(2));
    }

    private void writeJar(Path classesDir, Path jar) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> paths = Files.walk(classesDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String entryName = classesDir.relativize(path).toString().replace('\\', '/');
                out.putNextEntry(new JarEntry(entryName));
                Files.copy(path, out);
                out.closeEntry();
            }
        }
    }
}
