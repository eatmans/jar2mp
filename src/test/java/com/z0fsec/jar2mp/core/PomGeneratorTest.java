package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.BuildPluginInfo;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.MavenDependency;
import com.z0fsec.jar2mp.model.PomInfo;
import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.io.File;

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
    void usesManifestImplementationTitleAsProjectName() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("spring-petclinic");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(17);
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setImplementationTitle("petclinic");
        analysis.setManifestInfo(manifestInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<name>petclinic</name>"));
    }

    @Test
    void disablesAnnotationProcessingForRestoredCompilePom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<proc>none</proc>"));
    }

    @Test
    void disablesGeneratedMavenDescriptorForJarPackages() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<artifactId>maven-jar-plugin</artifactId>"));
        assertTrue(pomXml.contains("<addMavenDescriptor>false</addMavenDescriptor>"));
    }

    @Test
    void disablesGeneratedMavenDescriptorForWarPackages() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<artifactId>maven-war-plugin</artifactId>"));
        assertTrue(pomXml.contains("<addMavenDescriptor>false</addMavenDescriptor>"));
    }

    @Test
    void regularWarPackagesOriginalNestedLibrariesFromCleanSafeSourceDirectory() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getResourceFiles().add("WEB-INF/lib/original-lib-1.0.0.jar");
        analysis.getDetectedDependencies().add(new MavenDependency(
                "com.example", "original-lib", "1.0.0", MavenDependency.Confidence.HIGH));

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<scope>provided</scope>"));
        assertTrue(pomXml.contains(
                "<directory>${project.basedir}/src/main/original-libs/WEB-INF/lib</directory>"));
        assertTrue(pomXml.contains("<targetPath>WEB-INF/lib</targetPath>"));
    }

    @Test
    void usesOriginalJarManifestWhenPresent() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains(
                "<manifestFile>${project.basedir}/src/main/resources/META-INF/MANIFEST.MF</manifestFile>"));
        assertTrue(pomXml.contains("<addDefaultEntries>false</addDefaultEntries>"));
    }

    @Test
    void usesOriginalWarManifestWhenPresent() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains(
                "<manifestFile>${project.basedir}/src/main/webapp/META-INF/MANIFEST.MF</manifestFile>"));
        assertTrue(pomXml.contains("<addDefaultEntries>false</addDefaultEntries>"));
    }

    @Test
    void doesNotOverrideSpringBootExecutableManifest() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(17);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("org.springframework.boot.loader.launch.JarLauncher");
        manifestInfo.addEntry("Spring-Boot-Classes", "BOOT-INF/classes/");
        analysis.setManifestInfo(manifestInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<manifestFile>"));
        assertTrue(pomXml.contains("<artifactId>maven-jar-plugin</artifactId>"));
    }

    @Test
    void preservesSpringBootExecutableManifestCreatedByEntry() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("org.springframework.boot.loader.JarLauncher");
        manifestInfo.setCreatedBy("Apache Maven 3.6.0");
        manifestInfo.addEntry("Spring-Boot-Classes", "BOOT-INF/classes/");
        analysis.setManifestInfo(manifestInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<manifestFile>"));
        assertTrue(pomXml.contains("<manifestEntries>"));
        assertTrue(pomXml.contains("<Created-By>Apache Maven 3.6.0</Created-By>"));
    }

    @Test
    void doesNotOverrideSpringBootExecutableModernJarPluginCreatedByEntry() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(21);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");
        ManifestInfo manifestInfo = new ManifestInfo();
        manifestInfo.setMainClass("org.springframework.boot.loader.launch.JarLauncher");
        manifestInfo.setCreatedBy("Maven JAR Plugin 3.4.2");
        manifestInfo.addEntry("Spring-Boot-Classes", "BOOT-INF/classes/");
        analysis.setManifestInfo(manifestInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertFalse(pomXml.contains("<manifestEntries>"));
        assertFalse(pomXml.contains("<Created-By>"));
    }

    @Test
    void replacesExistingManifestFileWithoutRegexExpansion() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(false);
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getMetaInfFiles().add("META-INF/MANIFEST.MF");
        PomInfo pomInfo = new PomInfo();
        BuildPluginInfo jarPlugin = new BuildPluginInfo();
        jarPlugin.setGroupId("org.apache.maven.plugins");
        jarPlugin.setArtifactId("maven-jar-plugin");
        jarPlugin.setConfigurationXml(
                "<configuration><archive><manifestFile>old/MANIFEST.MF</manifestFile></archive></configuration>");
        pomInfo.getBuildPlugins().add(jarPlugin);
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains(
                "<manifestFile>${project.basedir}/src/main/resources/META-INF/MANIFEST.MF</manifestFile>"));
        assertFalse(pomXml.contains("old/MANIFEST.MF"));
    }

    @Test
    void preservesOriginalGeneratedMetadataResourcesWhenPresent() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(17);
        analysis.getMetaInfFiles().add("META-INF/build-info.properties");
        analysis.getMetaInfFiles().add("META-INF/sbom/application.cdx.json");
        PomInfo pomInfo = new PomInfo();
        BuildPluginInfo bootPlugin = new BuildPluginInfo();
        bootPlugin.setGroupId("org.springframework.boot");
        bootPlugin.setArtifactId("spring-boot-maven-plugin");
        bootPlugin.getExecutionsXml().add(
                "<execution><goals><goal>build-info</goal><goal>repackage</goal></goals></execution>");
        pomInfo.getBuildPlugins().add(bootPlugin);
        BuildPluginInfo cyclonedxPlugin = new BuildPluginInfo();
        cyclonedxPlugin.setGroupId("org.cyclonedx");
        cyclonedxPlugin.setArtifactId("cyclonedx-maven-plugin");
        pomInfo.getBuildPlugins().add(cyclonedxPlugin);
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<spring-boot.build-info.skip>true</spring-boot.build-info.skip>"));
        assertTrue(pomXml.contains("<cyclonedx.skip>true</cyclonedx.skip>"));
        assertFalse(pomXml.contains("<goal>build-info</goal>"));
        assertTrue(pomXml.contains("<goal>repackage</goal>"));
    }

    @Test
    void packagesOriginalClassBytesFromCleanSafeSourceDirectory() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getClassFiles().add("com/example/App.class");

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<id>restore-original-class-bytes</id>"));
        assertTrue(pomXml.contains("<phase>process-classes</phase>"));
        assertTrue(pomXml.contains("${project.basedir}/src/main/original-classes"));
        assertTrue(pomXml.contains("${project.build.outputDirectory}"));
        assertTrue(pomXml.contains("preservelastmodified=\"true\""));
    }

    @Test
    void restoresOriginalResourceMetadataFromCleanSafeSourceDirectory() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.getResourceFiles().add("META-INF/maven/com.example/demo/pom.xml");

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<id>restore-original-resource-metadata</id>"));
        assertTrue(pomXml.contains("${project.basedir}/src/main/resources"));
        assertTrue(pomXml.contains("${project.build.outputDirectory}"));
        assertTrue(pomXml.contains("preservelastmodified=\"true\""));
    }

    @Test
    void byteExactPackageAddsSkipPropertiesForStandaloneMavenPackage() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        ProjectConfig config = new ProjectConfig();
        config.setByteExactPackage(true);

        String pomXml = new PomGenerator().generate(analysis, config);

        assertTrue(pomXml.contains("<skipTests>true</skipTests>"));
        assertTrue(pomXml.contains("<maven.test.skip>true</maven.test.skip>"));
        assertTrue(pomXml.contains("<checkstyle.skip>true</checkstyle.skip>"));
        assertTrue(pomXml.contains("<enforcer.skip>true</enforcer.skip>"));
        assertTrue(pomXml.contains("<maven.javadoc.skip>true</maven.javadoc.skip>"));
    }

    @Test
    void byteExactPackageUsesOriginalArtifactBaseNameAsFinalName() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.setSourceFile(new File("demo-1.0.0-all.jar"));
        ProjectConfig config = new ProjectConfig();
        config.setByteExactPackage(true);

        String pomXml = new PomGenerator().generate(analysis, config);

        assertTrue(pomXml.contains("<finalName>demo-1.0.0-all</finalName>"));
    }

    @Test
    void byteExactPackageSkipsPackageTransformingPluginsBeforeHelperRestoresArtifact() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.example");
        analysis.setDetectedArtifactId("demo");
        analysis.setDetectedVersion("1.0.0");
        analysis.setJavaVersion(8);
        analysis.setSourceFile(new File("demo-1.0.0-all.jar"));
        PomInfo pomInfo = new PomInfo();
        BuildPluginInfo shadePlugin = new BuildPluginInfo();
        shadePlugin.setGroupId("org.apache.maven.plugins");
        shadePlugin.setArtifactId("maven-shade-plugin");
        shadePlugin.setVersion("3.2.4");
        shadePlugin.getExecutionsXml().add("<execution><phase>package</phase><goals><goal>shade</goal></goals></execution>");
        pomInfo.getBuildPlugins().add(shadePlugin);
        analysis.setEmbeddedPomInfo(pomInfo);
        ProjectConfig config = new ProjectConfig();
        config.setByteExactPackage(true);

        String pomXml = new PomGenerator().generate(analysis, config);

        assertFalse(pomXml.contains("<artifactId>maven-shade-plugin</artifactId>"));
        assertFalse(pomXml.contains("<goal>shade</goal>"));
        assertFalse(pomXml.contains("restore-byte-exact-artifact"));
        assertTrue(pomXml.contains("restore-byte-exact-package-records"));
        assertTrue(pomXml.contains(".jar2mp/byte-exact/raw-artifact/demo-1.0.0-all.jar"));
        assertTrue(pomXml.contains("<finalName>demo-1.0.0-all</finalName>"));
    }

    @Test
    void sanitizesManifestDerivedMavenCoordinates() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("Remko Popma");
        analysis.setDetectedArtifactId("Picocli Code Generation");
        analysis.setDetectedVersion("4.7.7");
        analysis.setJavaVersion(8);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<groupId>remko.popma</groupId>"));
        assertTrue(pomXml.contains("<artifactId>picocli-code-generation</artifactId>"));
        assertFalse(pomXml.contains("<artifactId>Picocli Code Generation</artifactId>"));
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
        assertTrue(pomXml.contains("<configuration combine.self=\"override\">"));
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

    @Test
    void removesErrorProneCompilerPluginArgumentsFromRestoredPom() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setDetectedGroupId("com.google.googlejavaformat");
        analysis.setDetectedArtifactId("google-java-format");
        analysis.setDetectedVersion("1.35.0");
        analysis.setJavaVersion(17);

        PomInfo pomInfo = new PomInfo();
        pomInfo.setParentGroupId("com.google.googlejavaformat");
        pomInfo.setParentArtifactId("google-java-format-parent");
        pomInfo.setParentVersion("1.35.0");
        BuildPluginInfo compilerPlugin = new BuildPluginInfo();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        compilerPlugin.setVersion("3.9.0");
        compilerPlugin.setConfigurationXml("<configuration><source>17</source><target>17</target><compilerArgs><arg>-Xplugin:ErrorProne</arg><arg>-Xlint:all</arg></compilerArgs></configuration>");
        pomInfo.getBuildPlugins().add(compilerPlugin);
        analysis.setEmbeddedPomInfo(pomInfo);

        String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

        assertTrue(pomXml.contains("<configuration combine.self=\"override\">"));
        assertFalse(pomXml.contains("-Xplugin:ErrorProne"));
        assertTrue(pomXml.contains("<arg>-Xlint:all</arg>"));
        assertTrue(pomXml.contains("<proc>none</proc>"));
    }
}
