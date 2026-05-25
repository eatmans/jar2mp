package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.BuildPluginInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
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

    @Test
    void omitsSnapshotParentAndUnresolvableProjectSiblingsForStandalonePom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("io.netty");
        analysis.setDetectedArtifactId("netty-common");
        analysis.setDetectedVersion("4.2.15.Final-SNAPSHOT");
        analysis.setJavaVersion(8);

        PomInfo pomInfo = new PomInfo();
        pomInfo.setParentGroupId("io.netty");
        pomInfo.setParentArtifactId("netty-parent");
        pomInfo.setParentVersion("4.2.15.Final-SNAPSHOT");
        analysis.setEmbeddedPomInfo(pomInfo);

        analysis.getDetectedDependencies().add(new MavenDependency("org.slf4j",
                "slf4j-api", "unknown", MavenDependency.Confidence.HIGH));
        analysis.getDetectedDependencies().add(new MavenDependency("org.jctools",
                "jctools-core", "4.0.5", MavenDependency.Confidence.HIGH));
        analysis.getDetectedDependencies().add(new MavenDependency("io.netty",
                "netty-jfr-stub", "${project.version}", MavenDependency.Confidence.HIGH));

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<parent>"));
        assertFalse(pomXml.contains("<artifactId>slf4j-api</artifactId>"));
        assertFalse(pomXml.contains("<artifactId>netty-jfr-stub</artifactId>"));
        assertTrue(pomXml.contains("<artifactId>jctools-core</artifactId>"));
        assertTrue(pomXml.contains("<version>4.0.5</version>"));
    }

    @Test
    void omitsReactorLocalParentFromStandalonePom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("org.apache.shiro");
        analysis.setDetectedArtifactId("shiro-core");
        analysis.setDetectedVersion("2.0.6");
        analysis.setJavaVersion(8);

        PomInfo pomInfo = new PomInfo();
        pomInfo.setParentGroupId("org.apache.shiro");
        pomInfo.setParentArtifactId("shiro-root");
        pomInfo.setParentVersion("2.0.6");
        pomInfo.setParentRelativePath("../pom.xml");
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<parent>"));
        assertFalse(pomXml.contains("<relativePath>../pom.xml</relativePath>"));
    }

    @Test
    void omitsSourceGenerationPluginsFromStandaloneCompilePom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("io.netty");
        analysis.setDetectedArtifactId("netty-common");
        analysis.setDetectedVersion("4.2.15.Final-SNAPSHOT");
        analysis.setJavaVersion(8);

        PomInfo pomInfo = new PomInfo();
        BuildPluginInfo groovyPlugin = new BuildPluginInfo();
        groovyPlugin.setGroupId("org.codehaus.gmaven");
        groovyPlugin.setArtifactId("groovy-maven-plugin");
        groovyPlugin.getExecutionsXml().add("<execution><phase>generate-sources</phase><goals><goal>execute</goal></goals></execution>");
        pomInfo.getBuildPlugins().add(groovyPlugin);

        BuildPluginInfo compilerPlugin = new BuildPluginInfo();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        compilerPlugin.setVersion("3.11.0");
        pomInfo.getBuildPlugins().add(compilerPlugin);
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<artifactId>groovy-maven-plugin</artifactId>"));
        assertTrue(pomXml.contains("<artifactId>maven-compiler-plugin</artifactId>"));
    }

    @Test
    void removesStrictCompilerWarningFailuresFromRestoredPom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(21);

        PomInfo pomInfo = new PomInfo();
        BuildPluginInfo compilerPlugin = new BuildPluginInfo();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        compilerPlugin.setVersion("3.11.0");
        compilerPlugin.setConfigurationXml("<configuration><compilerArgs><arg>-Xlint:all</arg><arg>-Werror</arg></compilerArgs><failOnWarning>true</failOnWarning></configuration>");
        pomInfo.getBuildPlugins().add(compilerPlugin);
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("-Werror"));
        assertFalse(pomXml.contains("<failOnWarning>true</failOnWarning>"));
        assertTrue(pomXml.contains("<arg>-Xlint:all</arg>"));
        assertTrue(pomXml.contains("<failOnWarning>false</failOnWarning>"));
    }
}
