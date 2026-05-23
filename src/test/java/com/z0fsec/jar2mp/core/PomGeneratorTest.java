package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomGeneratorTest {

    @Test
    void usesWarPackagingWhenConfigDoesNotOverrideDetectedWar() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<packaging>war</packaging>"));
    }

    @Test
    void omitsJarPackagingWhenDetectedArtifactIsJar() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<packaging>"));
    }

    @Test
    void usesDetectedJavaVersionWhenConfigDoesNotOverrideIt() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(17);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<maven.compiler.source>17</maven.compiler.source>"));
        assertTrue(pomXml.contains("<maven.compiler.target>17</maven.compiler.target>"));
    }
}
