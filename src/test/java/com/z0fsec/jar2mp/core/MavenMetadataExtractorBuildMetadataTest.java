package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.BuildPluginInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
import com.z0fsec.jar2mp.model.RepositoryInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenMetadataExtractorBuildMetadataTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsBuildMetadataFromEmbeddedPomXml() throws Exception {
        Path jar = tempDir.resolve("demo.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/maven/com.example/demo/pom.xml"));
            out.write(embeddedPom().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        PomInfo info;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            info = new MavenMetadataExtractor().extract(jarFile);
        }

        assertEquals("com.example", info.getGroupId());
        assertEquals("demo", info.getArtifactId());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("org.springframework.boot", info.getParentGroupId());
        assertEquals("spring-boot-starter-parent", info.getParentArtifactId());
        assertEquals("2.7.18", info.getParentVersion());
        assertEquals("../parent/pom.xml", info.getParentRelativePath());
        assertEquals("17", info.getProperties().get("java.version"));
        assertEquals("true", info.getProperties().get("skipTests"));

        MavenDependency managed = info.getDependencyManagement().get(0);
        assertEquals("com.acme", managed.getGroupId());
        assertEquals("platform-bom", managed.getArtifactId());
        assertEquals("1.2.3", managed.getVersion());
        assertEquals("import", managed.getScope());
        assertEquals("pom", managed.getType());

        RepositoryInfo repository = info.getRepositories().get(0);
        assertEquals("internal", repository.getId());
        assertEquals("https://repo.example.local/maven", repository.getUrl());
        assertTrue(repository.getReleasesXml().contains("<enabled>true</enabled>"));

        RepositoryInfo pluginRepository = info.getPluginRepositories().get(0);
        assertEquals("plugin-internal", pluginRepository.getId());
        assertEquals("https://repo.example.local/plugins", pluginRepository.getUrl());

        BuildPluginInfo plugin = info.getBuildPlugins().get(0);
        assertEquals("org.springframework.boot", plugin.getGroupId());
        assertEquals("spring-boot-maven-plugin", plugin.getArtifactId());
        assertEquals("2.7.18", plugin.getVersion());
        assertTrue(plugin.getConfigurationXml().contains("<mainClass>com.example.DemoApplication</mainClass>"));
        assertEquals(1, plugin.getExecutionsXml().size());
        assertTrue(plugin.getExecutionsXml().get(0).contains("<goal>repackage</goal>"));

        assertEquals(1, info.getProfilesXml().size());
        assertTrue(info.getProfilesXml().get(0).contains("<id>prod</id>"));
    }

    private String embeddedPom() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <parent>\n" +
                "    <groupId>org.springframework.boot</groupId>\n" +
                "    <artifactId>spring-boot-starter-parent</artifactId>\n" +
                "    <version>2.7.18</version>\n" +
                "    <relativePath>../parent/pom.xml</relativePath>\n" +
                "  </parent>\n" +
                "  <groupId>com.example</groupId>\n" +
                "  <artifactId>demo</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <java.version>17</java.version>\n" +
                "    <skipTests>true</skipTests>\n" +
                "  </properties>\n" +
                "  <dependencyManagement>\n" +
                "    <dependencies>\n" +
                "      <dependency>\n" +
                "        <groupId>com.acme</groupId>\n" +
                "        <artifactId>platform-bom</artifactId>\n" +
                "        <version>1.2.3</version>\n" +
                "        <type>pom</type>\n" +
                "        <scope>import</scope>\n" +
                "      </dependency>\n" +
                "    </dependencies>\n" +
                "  </dependencyManagement>\n" +
                "  <repositories>\n" +
                "    <repository>\n" +
                "      <id>internal</id>\n" +
                "      <url>https://repo.example.local/maven</url>\n" +
                "      <releases><enabled>true</enabled></releases>\n" +
                "    </repository>\n" +
                "  </repositories>\n" +
                "  <pluginRepositories>\n" +
                "    <pluginRepository>\n" +
                "      <id>plugin-internal</id>\n" +
                "      <url>https://repo.example.local/plugins</url>\n" +
                "    </pluginRepository>\n" +
                "  </pluginRepositories>\n" +
                "  <build>\n" +
                "    <plugins>\n" +
                "      <plugin>\n" +
                "        <groupId>org.springframework.boot</groupId>\n" +
                "        <artifactId>spring-boot-maven-plugin</artifactId>\n" +
                "        <version>2.7.18</version>\n" +
                "        <configuration>\n" +
                "          <mainClass>com.example.DemoApplication</mainClass>\n" +
                "        </configuration>\n" +
                "        <executions>\n" +
                "          <execution>\n" +
                "            <goals><goal>repackage</goal></goals>\n" +
                "          </execution>\n" +
                "        </executions>\n" +
                "      </plugin>\n" +
                "    </plugins>\n" +
                "  </build>\n" +
                "  <profiles>\n" +
                "    <profile>\n" +
                "      <id>prod</id>\n" +
                "      <properties><env>prod</env></properties>\n" +
                "    </profile>\n" +
                "  </profiles>\n" +
                "</project>\n";
    }
}
