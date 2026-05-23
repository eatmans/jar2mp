package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
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
        assertEquals("<web-app/>",
                Files.readString(outputDir.resolve("src/main/webapp/WEB-INF/web.xml")));
        assertEquals("<Context/>",
                Files.readString(outputDir.resolve("src/main/webapp/META-INF/context.xml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/WEB-INF/lib/embedded.jar")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/META-INF/context.xml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/webapp/WEB-INF/lib/embedded.jar")));
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
    void restoresSpringBootClassResourcesAndSkipsNestedLibraries() throws Exception {
        Path jar = tempDir.resolve("boot.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "BOOT-INF/classes/com/example/App.class", minimalClassBytes(52));
            addEntry(out, "BOOT-INF/classes/static/app.css", "body { color: #333; }");
            addEntry(out, "BOOT-INF/classes/templates/home.html", "<p>home</p>");
            addEntry(out, "BOOT-INF/classes/application.yml", "spring:\n  application:\n    name: restored\n");
            addEntry(out, "application.yml", "wrong: root\n");
            addEntry(out, "BOOT-INF/lib/dependency.jar", "library-bytes");
        }

        JarAnalyzer analyzer = new JarAnalyzer(new com.z0fsec.jar2mp.db.PackagePrefixDatabase());
        JarAnalysisResult analysis = analyzer.analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");
        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        assertEquals("body { color: #333; }",
                Files.readString(outputDir.resolve("src/main/resources/static/app.css")));
        assertEquals("<p>home</p>",
                Files.readString(outputDir.resolve("src/main/resources/templates/home.html")));
        assertEquals("spring:\n  application:\n    name: restored\n",
                Files.readString(outputDir.resolve("src/main/resources/application.yml")));
        assertFalse(Files.exists(outputDir.resolve("src/main/resources/BOOT-INF/lib/dependency.jar")));
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

    private void writeU2(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }
}
