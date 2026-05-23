package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.BuildPluginInfo;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.RepositoryInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomGeneratorBuildMetadataTest {

    @Test
    void preservesEmbeddedPomBuildMetadataAndAvoidsDuplicateCompilerPlugin() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(11);

        PomInfo pomInfo = new PomInfo();
        pomInfo.setParentGroupId("org.springframework.boot");
        pomInfo.setParentArtifactId("spring-boot-starter-parent");
        pomInfo.setParentVersion("2.7.18");
        pomInfo.setParentRelativePath("../parent/pom.xml");
        pomInfo.getProperties().put("java.version", "17");
        pomInfo.getProperties().put("skipTests", "true");

        MavenDependency managed = new MavenDependency("com.acme", "platform-bom", "1.2.3",
                MavenDependency.Confidence.HIGH);
        managed.setType("pom");
        managed.setScope("import");
        pomInfo.getDependencyManagement().add(managed);

        RepositoryInfo repository = new RepositoryInfo();
        repository.setId("internal");
        repository.setUrl("https://repo.example.local/maven");
        repository.setReleasesXml("<releases><enabled>true</enabled></releases>");
        pomInfo.getRepositories().add(repository);

        RepositoryInfo pluginRepository = new RepositoryInfo();
        pluginRepository.setId("plugin-internal");
        pluginRepository.setUrl("https://repo.example.local/plugins");
        pomInfo.getPluginRepositories().add(pluginRepository);

        BuildPluginInfo compilerPlugin = new BuildPluginInfo();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        compilerPlugin.setVersion("3.10.1");
        compilerPlugin.setConfigurationXml("<configuration><release>17</release></configuration>");
        pomInfo.getBuildPlugins().add(compilerPlugin);

        BuildPluginInfo bootPlugin = new BuildPluginInfo();
        bootPlugin.setGroupId("org.springframework.boot");
        bootPlugin.setArtifactId("spring-boot-maven-plugin");
        bootPlugin.setVersion("2.7.18");
        bootPlugin.getExecutionsXml().add("<execution><goals><goal>repackage</goal></goals></execution>");
        pomInfo.getBuildPlugins().add(bootPlugin);

        pomInfo.getProfilesXml().add("<profile><id>prod</id><properties><env>prod</env></properties></profile>");
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<parent>"));
        assertTrue(pomXml.contains("<artifactId>spring-boot-starter-parent</artifactId>"));
        assertTrue(pomXml.contains("<relativePath>../parent/pom.xml</relativePath>"));
        assertTrue(pomXml.contains("<java.version>17</java.version>"));
        assertTrue(pomXml.contains("<skipTests>true</skipTests>"));
        assertTrue(pomXml.contains("<dependencyManagement>"));
        assertTrue(pomXml.contains("<artifactId>platform-bom</artifactId>"));
        assertTrue(pomXml.contains("<type>pom</type>"));
        assertTrue(pomXml.contains("<scope>import</scope>"));
        assertTrue(pomXml.contains("<repositories>"));
        assertTrue(pomXml.contains("<url>https://repo.example.local/maven</url>"));
        assertTrue(pomXml.contains("<pluginRepositories>"));
        assertTrue(pomXml.contains("<url>https://repo.example.local/plugins</url>"));
        assertTrue(pomXml.contains("<artifactId>maven-compiler-plugin</artifactId>"));
        assertTrue(pomXml.contains("<release>17</release>"));
        assertTrue(pomXml.contains("<artifactId>spring-boot-maven-plugin</artifactId>"));
        assertTrue(pomXml.contains("<goal>repackage</goal>"));
        assertTrue(pomXml.contains("<profiles>"));
        assertTrue(pomXml.contains("<id>prod</id>"));
        assertEquals(1, countOccurrences(pomXml, "<artifactId>maven-compiler-plugin</artifactId>"));
    }

    private int countOccurrences(String value, String substring) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(substring, index)) >= 0) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
