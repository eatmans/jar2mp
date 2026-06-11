package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.RestorationScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresWarStaticResourcesToWebappAndClassResourcesToResources() throws Exception {
        Path war = tempDir.resolve("sample.war");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(war))) {
            addEntry(out, "WEB-INF/classes/com/example/App.class", minimalClassBytes(52));
            addEntry(out, "WEB-INF/classes/application.yml", "server:\n  port: 8080\n");
            addEntry(out, "index.html", "<html>home</html>");
            addEntry(out, "assets/app.js", "console.log('ok');");
            addEntry(out, "META-INF/resources/webjars/demo/app.js", "alert('demo');");
            addEntry(out, "WEB-INF/web.xml", "<web-app/>");
            addEntry(out, "META-INF/context.xml", "<Context/>");
            addEntry(out, "WEB-INF/lib/embedded.jar", "library-bytes");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(war.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(war.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals("server:\n  port: 8080\n",
                Files.readString(outputDir.resolve("src/main/resources/application.yml")));
        assertEquals("<html>home</html>",
                Files.readString(outputDir.resolve("src/main/webapp/index.html")));
        assertEquals("console.log('ok');",
                Files.readString(outputDir.resolve("src/main/webapp/assets/app.js")));
        assertEquals("alert('demo');",
                Files.readString(outputDir.resolve("src/main/webapp/META-INF/resources/webjars/demo/app.js")));
        assertEquals("<web-app/>",
                Files.readString(outputDir.resolve("src/main/webapp/WEB-INF/web.xml")));
        assertEquals("<Context/>",
                Files.readString(outputDir.resolve("src/main/webapp/META-INF/context.xml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/WEB-INF/lib/embedded.jar")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/META-INF/context.xml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/webapp/WEB-INF/lib/embedded.jar")));
        assertEquals("library-bytes",
                Files.readString(outputDir.resolve("target/original-libs/WEB-INF/lib/embedded.jar")));
        assertEquals("library-bytes",
                Files.readString(outputDir.resolve("src/main/original-libs/WEB-INF/lib/embedded.jar")));
        String runbook = Files.readString(outputDir.resolve("RUNBOOK.md"));
        assertTrue(runbook.contains("target/original-libs/WEB-INF/lib/embedded.jar"));
        assertTrue(runbook.contains("not added to the generated Maven classpath"));
        assertTrue(Files.readString(outputDir.resolve("decompile-parity-report.md"))
                .contains("Decompile parity report"));
        assertTrue(Files.exists(outputDir.resolve("restoration-score.md")));
        assertTrue(Files.exists(outputDir.resolve("gap-summary.md")));
    }

    @Test
    void preservesMetaInfRuntimeResourcesForJarProjects() throws Exception {
        Path jar = tempDir.resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "META-INF/resources/webjars/demo/app.js", "alert('demo');");
            addEntry(out, "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                    "com.example.AutoConfiguration\n");
            addEntry(out, "META-INF/native-image/com.example/demo/reflect-config.json", "[]");
            addEntry(out, "META-INF/services/com.example.Service", "com.example.ServiceImpl\n");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals("alert('demo');",
                Files.readString(outputDir.resolve("src/main/resources/META-INF/resources/webjars/demo/app.js")));
        assertEquals("com.example.AutoConfiguration\n",
                Files.readString(outputDir.resolve("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")));
        assertEquals("[]",
                Files.readString(outputDir.resolve("src/main/resources/META-INF/native-image/com.example/demo/reflect-config.json")));
        assertEquals("com.example.ServiceImpl\n",
                Files.readString(outputDir.resolve("src/main/resources/META-INF/services/com.example.Service")));
    }

    @Test
    void skipsSpringBootLoaderFileSystemProviderService() throws Exception {
        Path jar = tempDir.resolve("boot-loader-service.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "BOOT-INF/classes/com/example/App.class", minimalClassBytes(52));
            addEntry(out, "META-INF/services/java.nio.file.spi.FileSystemProvider",
                    "org.springframework.boot.loader.nio.file.NestedFileSystemProvider\n");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("boot-loader-service-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(outputDir.resolve("src/main/resources/META-INF/services/java.nio.file.spi.FileSystemProvider")));
    }

    @Test
    void restoresSpringBootClassResourcesAndSkipsNestedLibraries() throws Exception {
        Path jar = tempDir.resolve("boot.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "org/springframework/boot/loader/JarLauncher.class", minimalClassBytes(52));
            addEntry(out, "BOOT-INF/classes/com/example/App.class", minimalClassBytes(52));
            addEntry(out, "BOOT-INF/classes/static/app.css", "body { color: #333; }");
            addEntry(out, "BOOT-INF/classes/templates/home.html", "<p>home</p>");
            addEntry(out, "BOOT-INF/classes/application.yml", "spring:\n  application:\n    name: restored\n");
            addEntry(out, "application.yml", "wrong: root\n");
            addEntry(out, "BOOT-INF/lib/dependency.jar", "library-bytes");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        assertFalse(analysis.getClassFiles().contains("org/springframework/boot/loader/JarLauncher.class"));
        assertTrue(analysis.getClassFiles().contains("com/example/App.class"));

        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals("body { color: #333; }",
                Files.readString(outputDir.resolve("src/main/resources/static/app.css")));
        assertEquals("<p>home</p>",
                Files.readString(outputDir.resolve("src/main/resources/templates/home.html")));
        assertEquals("spring:\n  application:\n    name: restored\n",
                Files.readString(outputDir.resolve("src/main/resources/application.yml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/BOOT-INF/lib/dependency.jar")));
        assertEquals("library-bytes",
                Files.readString(outputDir.resolve("target/original-libs/BOOT-INF/lib/dependency.jar")));
        String inventory = Files.readString(outputDir.resolve("resource-inventory.md"));
        assertTrue(inventory.contains("target/original-libs/BOOT-INF/lib/dependency.jar"));
        assertTrue(inventory.contains("not added to the generated Maven classpath"));
        assertFalse(Files.exists(outputDir.resolve("src/main/java/org/springframework/boot/loader/JarLauncher.java")));
    }

    @Test
    void doesNotGenerateSkippedEmbeddedDependencyClasses() throws Exception {
        Path jar = tempDir.resolve("assembly.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "com/google/gson/Gson.class", minimalClassBytes(52));
            addEntry(out, "META-INF/maven/com.example/sample/pom.properties",
                    "groupId=com.example\nartifactId=sample\nversion=1.0.0\n");
            addEntry(out, "META-INF/maven/com.google.code.gson/gson/pom.properties",
                    "groupId=com.google.code.gson\nartifactId=gson\nversion=2.10.1\n");
        }

        com.z0fsec.jar2mp.db.PackagePrefixDatabase packageDb = new com.z0fsec.jar2mp.db.PackagePrefixDatabase();
        packageDb.load(new ByteArrayInputStream(
                "com.google.gson=com.google.code.gson:gson:2.10.1\n".getBytes(StandardCharsets.UTF_8)));
        JarAnalysisResult analysis = new JarAnalyzer(packageDb).analyze(jar.toFile(), null);
        assertTrue(analysis.getSkippedDependencyClassFiles().contains("com/google/gson/Gson.class"));

        ProjectConfig config = new ProjectConfig();
        config.setDecompile(false);
        Path outputDir = tempDir.resolve("assembly-out");
        new ProjectBuilder(config).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertTrue(Files.exists(outputDir.resolve("src/main/java/com/example/App.class")));
        assertFalse(Files.exists(outputDir.resolve("src/main/java/com/google/gson/Gson.class")));
        assertFalse(Files.exists(outputDir.resolve("target/original-classes/com/google/gson/Gson.class")));
    }

    @Test
    void preservesOriginalMavenMetadataResources() throws Exception {
        Path jar = tempDir.resolve("sample.jar");
        String pomXml = "<project><modelVersion>4.0.0</modelVersion></project>\n";
        String pomProperties = "groupId=com.example\nartifactId=sample\nversion=1.0.0\n";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "META-INF/maven/com.example/sample/pom.xml", pomXml);
            addEntry(out, "META-INF/maven/com.example/sample/pom.properties", pomProperties);
        }

        JarAnalysisResult analysis = new JarAnalyzer(
                new com.z0fsec.jar2mp.db.PackagePrefixDatabase()).analyze(jar.toFile(), null);
        ProjectConfig config = new ProjectConfig();
        config.setDecompile(false);
        Path outputDir = tempDir.resolve("maven-metadata-out");

        new ProjectBuilder(config).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals(pomXml, Files.readString(outputDir.resolve(
                "src/main/resources/META-INF/maven/com.example/sample/pom.xml")));
        assertEquals(pomProperties, Files.readString(outputDir.resolve(
                "src/main/resources/META-INF/maven/com.example/sample/pom.properties")));
    }

    @Test
    void preservesOriginalManifestForPackageFidelity() throws Exception {
        Path jar = tempDir.resolve("manifest-sample.jar");
        String manifest = "Manifest-Version: 1.0\r\nImplementation-Title: original\r\n\r\n";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "META-INF/MANIFEST.MF", manifest);
        }

        JarAnalysisResult analysis = new JarAnalyzer(
                new com.z0fsec.jar2mp.db.PackagePrefixDatabase()).analyze(jar.toFile(), null);
        ProjectConfig config = new ProjectConfig();
        config.setDecompile(false);
        Path outputDir = tempDir.resolve("manifest-out");

        new ProjectBuilder(config).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals(manifest, Files.readString(outputDir.resolve("src/main/resources/META-INF/MANIFEST.MF")));
    }

    @Test
    void writesPackageInfoSourceAsPackageDeclaration() throws Exception {
        Path jar = tempDir.resolve("package-info.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/package-info.class", minimalClassBytes(52));
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals("package com.example;\n",
                Files.readString(outputDir.resolve("src/main/java/com/example/package-info.java")));
    }

    @Test
    void usesContextDecompilerSourceThatPreservesNamedInnerClasses() throws Exception {
        Path jar = compileJar("demo.Outer",
                "package demo;\n"
                        + "public class Outer {\n"
                        + "  private Kind kind = Kind.A;\n"
                        + "  public Kind getKind() { return kind; }\n"
                        + "  public enum Kind { A, B }\n"
                        + "}\n");

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("inner-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        String source = Files.readString(outputDir.resolve("src/main/java/demo/Outer.java"));
        assertTrue(source.contains("enum Kind"));
        assertTrue(source.contains("Kind getKind()"));
        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/Outer$Kind.java")));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("No decompilation failures detected."));
        RestorationScore score = analysis.getRestorationScore();
        assertEquals(100, score.getBreakdown().get("source").intValue());
    }

    @Test
    void acceptsContextSourceWithMaskedStringLiteral() {
        ProjectBuilder builder = new ProjectBuilder(new ProjectConfig());
        String source = "package demo;\n"
                + "class Sample {\n"
                + "  String value = \"*** masked\";\n"
                + "  static class Nested {}\n"
                + "}\n";

        assertTrue(builder.isContextSourceUsableForTest(source));
    }

    @Test
    void acceptsContextSourceWithCfrVoidDeclarationWarning() {
        ProjectBuilder builder = new ProjectBuilder(new ProjectConfig());
        String source = "package demo;\n"
                + "class Sample {\n"
                + "  /*\n"
                + "   * WARNING - void declaration\n"
                + "   */\n"
                + "  static class Nested {}\n"
                + "  void run() { void var1_2; }\n"
                + "}\n";

        assertTrue(builder.isContextSourceUsableForTest(source));
    }

    @Test
    void detectsSelfInnerImportsWithoutDeclarationsAsCompilerUnsafe() {
        ProjectBuilder builder = new ProjectBuilder(new ProjectConfig());
        String source = "package demo;\n"
                + "import demo.Outer.Node;\n"
                + "class Outer {\n"
                + "  Node head;\n"
                + "}\n";

        assertTrue(builder.hasMissingSelfInnerReferencesForTest(
                source,
                "demo/Outer.class",
                Arrays.asList("demo/Outer.class", "demo/Outer$Node.class")));
    }

    @Test
    void allowsSelfInnerImportsWhenDeclarationIsPresent() {
        ProjectBuilder builder = new ProjectBuilder(new ProjectConfig());
        String source = "package demo;\n"
                + "import demo.Outer.Node;\n"
                + "class Outer {\n"
                + "  Node head;\n"
                + "  static class Node {}\n"
                + "}\n";

        assertFalse(builder.hasMissingSelfInnerReferencesForTest(
                source,
                "demo/Outer.class",
                Arrays.asList("demo/Outer.class", "demo/Outer$Node.class")));
    }

    @Test
    void treatsAnonymousInnerClassesRepresentedInOuterSourceAsRestored() throws Exception {
        Path jar = compileJar("demo.Outer",
                "package demo;\n"
                        + "public class Outer {\n"
                        + "  public Runnable create() {\n"
                        + "    return new Runnable() { public void run() {} };\n"
                        + "  }\n"
                        + "}\n");

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("anonymous-inner-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(outputDir.resolve("target/original-classes/demo/Outer$1.class")));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("No decompilation failures detected."));
        RestorationScore score = analysis.getRestorationScore();
        assertEquals(100, score.getBreakdown().get("source").intValue());
        assertTrue(score.getGaps().stream().noneMatch(g ->
                "decompile".equals(g.getCategory()) && "demo/Outer$1.class".equals(g.getDetail())));
    }

    @Test
    void copiesUncoveredInnerClassBytesToResourcesForCompilerFallback() throws Exception {
        byte[] outerBytes = minimalClassBytes(52);
        byte[] innerBytes = minimalClassBytes(52);
        Path jar = tempDir.resolve("uncovered-inner.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "demo/Outer.class", outerBytes);
            addEntry(out, "demo/Outer$Inner.class", innerBytes);
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("uncovered-inner-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        Path retainedClass = outputDir.resolve("target/original-classes/demo/Outer$Inner.class");
        Path compilerFallbackClass = outputDir.resolve("src/main/resources/demo/Outer$Inner.class");
        assertArrayEquals(outerBytes, Files.readAllBytes(outputDir.resolve("target/raw-classes/demo/Outer.class")));
        assertArrayEquals(innerBytes, Files.readAllBytes(outputDir.resolve("target/raw-classes/demo/Outer$Inner.class")));
        assertArrayEquals(innerBytes, Files.readAllBytes(retainedClass));
        assertArrayEquals(innerBytes, Files.readAllBytes(compilerFallbackClass));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("target/original-classes/demo/Outer$Inner.class"));
    }

    @Test
    void storesCaseInsensitiveClassCollisionsInCompilerFallbackJar() throws Exception {
        byte[] upperBytes = TestClassCompiler.compile("demo.C", "package demo; public class C {}\n");
        byte[] lowerBytes = TestClassCompiler.compile("demo.c", "package demo; public class c {}\n");
        Path jar = tempDir.resolve("case-collision.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "demo/C.class", upperBytes);
            addEntry(out, "demo/c.class", lowerBytes);
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("case-collision-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project><build/></project>",
                outputDir.toFile(), null);

        Path fallbackJar = outputDir.resolve("target/compiler-fallback-classes.jar");
        assertTrue(Files.exists(fallbackJar));
        try (JarFile fallback = new JarFile(fallbackJar.toFile())) {
            assertArrayEquals(upperBytes, readJarEntry(fallback, "demo/C.class"));
            assertArrayEquals(lowerBytes, readJarEntry(fallback, "demo/c.class"));
        }
        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/C.java")));
        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/c.java")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/demo/C.class")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/demo/c.class")));
        assertTrue(Files.readString(outputDir.resolve("pom.xml"))
                .contains("<systemPath>${project.basedir}/target/compiler-fallback-classes.jar</systemPath>"));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("case-insensitive file systems"));
    }

    @Test
    void fallsBackClassesWithNonJavaSourceNamesToRawBytes() throws Exception {
        byte[] classBytes = minimalClassBytes(52);
        Path jar = tempDir.resolve("non-java-name.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "demo/-KotlinName.class", classBytes);
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("non-java-name-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/-KotlinName.java")));
        assertArrayEquals(classBytes, Files.readAllBytes(outputDir.resolve("target/raw-classes/demo/-KotlinName.class")));
        assertArrayEquals(classBytes, Files.readAllBytes(outputDir.resolve("target/original-classes/demo/-KotlinName.class")));
        assertArrayEquals(classBytes, Files.readAllBytes(outputDir.resolve("src/main/resources/demo/-KotlinName.class")));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("class name is not a legal Java source identifier"));
    }

    @Test
    void fallsBackKotlinMetadataClassesToRawBytes() throws Exception {
        byte[] classBytes = appendAscii(minimalClassBytes(52), "Lkotlin/Metadata;");
        Path jar = tempDir.resolve("kotlin-metadata.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "demo/KotlinCompiled.class", classBytes);
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("kotlin-metadata-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/KotlinCompiled.java")));
        assertArrayEquals(classBytes, Files.readAllBytes(outputDir.resolve("src/main/resources/demo/KotlinCompiled.class")));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("class was compiled from Kotlin metadata"));
    }

    @Test
    void fallsBackShadedDependencyClassesToRawBytes() throws Exception {
        byte[] classBytes = minimalClassBytes(52);
        Path jar = tempDir.resolve("shaded-dependency.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "demo/shaded/vendor/Library.class", classBytes);
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("shaded-dependency-out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(outputDir.resolve("src/main/java/demo/shaded/vendor/Library.java")));
        assertArrayEquals(classBytes, Files.readAllBytes(
                outputDir.resolve("src/main/resources/demo/shaded/vendor/Library.class")));
        assertTrue(Files.readString(outputDir.resolve("decompile-failures.md"))
                .contains("class is under a shaded dependency namespace"));
    }

    @Test
    void skipsResourcePathsThatWouldEscapeOutputDirectories() throws Exception {
        Path jar = tempDir.resolve("unsafe.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "../../../../evil.txt", "escaped");
            addEntry(out, "BOOT-INF/classes/../../../../boot-evil.txt", "escaped");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
        assertFalse(Files.exists(tempDir.resolve("boot-evil.txt")));
    }

    private void addEntry(JarOutputStream out, String name, String content) throws Exception {
        addEntry(out, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addEntry(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private byte[] readJarEntry(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name);
        assertTrue(entry != null, "Missing jar entry " + name);
        try (java.io.InputStream in = jar.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private Path compileJar(String className, String source) throws Exception {
        String packagePath = "";
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            packagePath = className.substring(0, lastDot).replace('.', '/');
            simpleName = className.substring(lastDot + 1);
        }

        Path sourceDir = tempDir.resolve("compile-src").resolve(packagePath);
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(simpleName + ".java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

        Path classesDir = tempDir.resolve("compile-classes");
        Files.createDirectories(classesDir);
        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-g",
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }

        Path jar = tempDir.resolve(simpleName + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> addCompiledEntry(out, classesDir, path));
        }
        return jar;
    }

    private void addCompiledEntry(JarOutputStream out, Path classesDir, Path classFile) {
        try {
            String name = classesDir.relativize(classFile).toString().replace('\\', '/');
            addEntry(out, name, Files.readAllBytes(classFile));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] minimalClassBytes(int majorVersion) {
        byte[] bytes = new byte[31];
        bytes[0] = (byte) 0xCA;
        bytes[1] = (byte) 0xFE;
        bytes[2] = (byte) 0xBA;
        bytes[3] = (byte) 0xBE;
        writeU2(bytes, 4, 0);
        writeU2(bytes, 6, majorVersion);
        writeU2(bytes, 8, 3);
        bytes[10] = 1;
        writeU2(bytes, 11, 3);
        bytes[13] = 'A';
        bytes[14] = 'p';
        bytes[15] = 'p';
        bytes[16] = 7;
        writeU2(bytes, 17, 1);
        writeU2(bytes, 19, 0x0021);
        writeU2(bytes, 21, 2);
        writeU2(bytes, 23, 0);
        writeU2(bytes, 25, 0);
        writeU2(bytes, 27, 0);
        writeU2(bytes, 29, 0);
        return bytes;
    }

    private byte[] appendAscii(byte[] bytes, String suffix) {
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.US_ASCII);
        byte[] combined = Arrays.copyOf(bytes, bytes.length + suffixBytes.length);
        System.arraycopy(suffixBytes, 0, combined, bytes.length, suffixBytes.length);
        return combined;
    }

    private void writeU2(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }
}
