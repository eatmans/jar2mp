package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedClassSourceMergerTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesCfrStyleNamedInnerSourceIntoOuterSource() throws Exception {
        String outerSource = "package demo;\n\n"
                + "public class Outer {\n"
                + "    private DTO data() {\n"
                + "        return new DTO().setItems(null);\n"
                + "    }\n"
                + "}\n";
        String innerSource = "package demo;\n\n"
                + "import demo.Outer;\n"
                + "import java.util.Set;\n\n"
                + "static class Outer.DTO {\n"
                + "    private Set<String> items;\n\n"
                + "    public Outer.DTO() {\n"
                + "    }\n\n"
                + "    public Set<String> getItems() {\n"
                + "        return this.items;\n"
                + "    }\n\n"
                + "    public Outer.DTO setItems(Set<String> items) {\n"
                + "        this.items = items;\n"
                + "        return this;\n"
                + "    }\n"
                + "}\n";

        NestedClassSourceMerger.MergeResult result = new NestedClassSourceMerger()
                .mergeMissingNamedInnerSources(
                        outerSource,
                        "demo/Outer.class",
                        Arrays.asList("demo/Outer.class", "demo/Outer$DTO.class"),
                        classPath -> innerSource);

        assertTrue(result.getUnresolvedClassPaths().isEmpty());
        assertEquals(Arrays.asList("demo/Outer$DTO.class"), result.getMergedClassPaths());
        assertTrue(result.getSource().contains("import java.util.Set;"));
        assertFalse(result.getSource().contains("import demo.Outer;"));
        assertTrue(result.getSource().contains("static class DTO"));
        assertFalse(result.getSource().contains("Outer.DTO"));
        compile("demo/Outer.java", result.getSource());
    }

    @Test
    void keepsUnresolvedClassWhenInnerSourceCannotBeNormalized() throws Exception {
        String outerSource = "package demo;\n"
                + "public class Outer { private DTO data() { return null; } }\n";

        NestedClassSourceMerger.MergeResult result = new NestedClassSourceMerger()
                .mergeMissingNamedInnerSources(
                        outerSource,
                        "demo/Outer.class",
                        Arrays.asList("demo/Outer.class", "demo/Outer$DTO.class"),
                        classPath -> "package demo; public class Other { }\n");

        assertTrue(result.getMergedClassPaths().isEmpty());
        assertEquals(Arrays.asList("demo/Outer$DTO.class"), result.getUnresolvedClassPaths());
    }

    @Test
    void mergesUnreferencedNamedInnerAndRemovesSyntheticOuterConstructorParameter() throws Exception {
        String outerSource = "package demo;\n"
                + "public class Outer {\n"
                + "    Object listener() { return new Listener(this); }\n"
                + "}\n";
        String innerSource = "package demo;\n\n"
                + "public class Outer.Listener {\n"
                + "    public Outer.Listener(Outer this$0) {\n"
                + "    }\n"
                + "}\n";

        NestedClassSourceMerger.MergeResult result = new NestedClassSourceMerger()
                .mergeMissingNamedInnerSources(
                        outerSource,
                        "demo/Outer.class",
                        Arrays.asList("demo/Outer.class", "demo/Outer$Listener.class"),
                        classPath -> innerSource);

        assertTrue(result.getUnresolvedClassPaths().isEmpty());
        assertEquals(Arrays.asList("demo/Outer$Listener.class"), result.getMergedClassPaths());
        assertTrue(result.getSource().contains("public class Listener"));
        assertTrue(result.getSource().contains("public Listener()"));
        assertTrue(result.getSource().contains("new Listener()"));
        assertFalse(result.getSource().contains("new Listener(this)"));
        assertFalse(result.getSource().contains("this$0"));
        compile("demo/Outer.java", result.getSource());
    }

    private void compile(String sourcePath, String source) throws Exception {
        Path sourceFile = tempDir.resolve(sourcePath);
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);
    }
}
