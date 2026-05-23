package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.ResourceFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceClassifierTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesResourcesAndMapsRestorationTargets() {
        JarAnalysisResult jarResult = new JarAnalysisResult();
        jarResult.getResourceFiles().add("application.yml");
        jarResult.getResourceFiles().add("bootstrap.properties");
        jarResult.getResourceFiles().add("mapper/UserMapper.xml");
        jarResult.getResourceFiles().add("templates/index.html");
        jarResult.getResourceFiles().add("static/app.js");
        jarResult.getResourceFiles().add("WEB-INF/web.xml");
        jarResult.getMetaInfFiles().add("META-INF/services/com.example.Plugin");
        jarResult.getResourceFiles().add("lib/native/libdemo.so");
        jarResult.getResourceFiles().add("certs/server.jks");

        List<ResourceFinding> findings = new ResourceClassifier().classify(jarResult);

        assertResource(findings, "application.yml", ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml");
        assertResource(findings, "bootstrap.properties", ResourceFinding.Category.CONFIG,
                "src/main/resources/bootstrap.properties");
        assertResource(findings, "mapper/UserMapper.xml", ResourceFinding.Category.MYBATIS_MAPPER,
                "src/main/resources/mapper/UserMapper.xml");
        assertResource(findings, "templates/index.html", ResourceFinding.Category.TEMPLATE,
                "src/main/resources/templates/index.html");
        assertResource(findings, "static/app.js", ResourceFinding.Category.FRONTEND_ASSET,
                "src/main/resources/static/app.js");
        assertResource(findings, "WEB-INF/web.xml", ResourceFinding.Category.SERVLET_DESCRIPTOR,
                "src/main/resources/WEB-INF/web.xml");
        assertResource(findings, "META-INF/services/com.example.Plugin", ResourceFinding.Category.SPI,
                "src/main/resources/META-INF/services/com.example.Plugin");
        assertResource(findings, "lib/native/libdemo.so", ResourceFinding.Category.NATIVE_LIBRARY,
                "src/main/resources/lib/native/libdemo.so");
        assertResource(findings, "certs/server.jks", ResourceFinding.Category.CERTIFICATE,
                "src/main/resources/certs/server.jks");
    }

    @Test
    void warResourcesUseWebappTargetsAndClasspathResourcesAreStripped() {
        JarAnalysisResult warResult = new JarAnalysisResult();
        warResult.setWar(true);
        warResult.getResourceFiles().add("WEB-INF/classes/application.yml");
        warResult.getResourceFiles().add("index.html");
        warResult.getResourceFiles().add("WEB-INF/web.xml");
        warResult.getMetaInfFiles().add("META-INF/services/com.example.Plugin");

        List<ResourceFinding> findings = new ResourceClassifier().classify(warResult);

        assertResource(findings, "WEB-INF/classes/application.yml", ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml");
        assertResource(findings, "index.html", ResourceFinding.Category.TEMPLATE,
                "src/main/webapp/index.html");
        assertResource(findings, "WEB-INF/web.xml", ResourceFinding.Category.SERVLET_DESCRIPTOR,
                "src/main/webapp/WEB-INF/web.xml");
        assertResource(findings, "META-INF/services/com.example.Plugin", ResourceFinding.Category.SPI,
                "src/main/webapp/META-INF/services/com.example.Plugin");
    }

    @Test
    void jarAnalyzerPopulatesResourceInventory() throws Exception {
        Path jar = tempDir.resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "application.yml", "app: demo\n");
            addEntry(out, "META-INF/services/com.example.Plugin", "com.example.PluginImpl\n");
        }

        JarAnalysisResult result = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);

        assertResource(result.getResourceFindings(), "application.yml", ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml");
        assertResource(result.getResourceFindings(), "META-INF/services/com.example.Plugin",
                ResourceFinding.Category.SPI, "src/main/resources/META-INF/services/com.example.Plugin");
    }

    @Test
    void projectBuilderWritesResourceInventoryReport() throws Exception {
        Path jar = tempDir.resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addEntry(out, "com/example/App.class", minimalClassBytes(52));
            addEntry(out, "application.yml", "app: demo\n");
            addEntry(out, "static/app.js", "console.log('demo');");
        }

        JarAnalysisResult analysis = new JarAnalyzer(new PackagePrefixDatabase()).analyze(jar.toFile(), null);
        Path outputDir = tempDir.resolve("out");

        new ProjectBuilder(new ProjectConfig()).build(jar.toFile(), analysis, "<project/>", outputDir.toFile(), null);

        String report = Files.readString(outputDir.resolve("resource-inventory.md"));
        assertTrue(report.contains("# Resource inventory"));
        assertTrue(report.contains("| Category | Original path | Target path | Notes |"));
        assertTrue(report.contains("| CONFIG | application.yml | src/main/resources/application.yml |"));
        assertTrue(report.contains("| FRONTEND_ASSET | static/app.js | src/main/resources/static/app.js |"));
    }

    private void assertResource(List<ResourceFinding> findings, String originalPath,
                                ResourceFinding.Category category, String targetPath) {
        ResourceFinding finding = findings.stream()
                .filter(item -> originalPath.equals(item.getOriginalPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing resource finding: " + originalPath));
        assertEquals(category, finding.getCategory(), "category for " + originalPath);
        assertEquals(targetPath, finding.getTargetPath(), "target path for " + originalPath);
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
