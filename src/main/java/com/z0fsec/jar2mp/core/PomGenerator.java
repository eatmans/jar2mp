package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class PomGenerator {

    public String generate(JarAnalysisResult analysis, ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");

        PomInfo embeddedPom = analysis.getEmbeddedPomInfo();
        // GAV
        String groupId = normalizeGroupId(config.getGroupId() != null ? config.getGroupId() : analysis.getDetectedGroupId());
        String artifactId = normalizeArtifactId(config.getArtifactId() != null ? config.getArtifactId() : analysis.getDetectedArtifactId());
        String version = config.getVersion() != null ? config.getVersion() : analysis.getDetectedVersion();

        boolean parentIncluded = shouldIncludeParent(embeddedPom, groupId, version);
        if (parentIncluded) {
            appendParent(sb, embeddedPom);
        }

        sb.append("    <groupId>").append(escapeXml(groupId)).append("</groupId>\n");
        sb.append("    <artifactId>").append(escapeXml(artifactId)).append("</artifactId>\n");
        sb.append("    <version>").append(escapeXml(version)).append("</version>\n");
        String projectName = projectName(analysis);
        if (projectName != null) {
            sb.append("    <name>").append(escapeXml(projectName)).append("</name>\n");
        }

        // Packaging
        String packaging = config.getPackaging();
        if (packaging == null) {
            packaging = analysis.isWar() ? "war" : "jar";
        }
        if (!"jar".equals(packaging)) {
            sb.append("    <packaging>").append(escapeXml(packaging)).append("</packaging>\n");
        }

        sb.append("\n");

        boolean originalManifestPresent = hasMetaInfFile(analysis, "META-INF/MANIFEST.MF");
        boolean originalBuildInfoPresent = hasMetaInfFile(analysis, "META-INF/build-info.properties");
        boolean originalSbomPresent = hasMetaInfPathPrefix(analysis, "META-INF/sbom/");
        String originalManifestPath = originalManifestPath(packaging,
                originalManifestPresent && !isSpringBootExecutable(analysis));
        String originalCreatedBy = originalCreatedBy(analysis,
                originalManifestPresent && isSpringBootExecutable(analysis));
        boolean useOriginalWarLibraries = "war".equals(packaging)
                && hasResourcePathPrefix(analysis, "WEB-INF/lib/")
                && !isSpringBootExecutable(analysis);
        String byteExactArtifactFileName = null;
        if (config != null && config.isByteExactPackage() && analysis.getSourceFile() != null) {
            byteExactArtifactFileName = analysis.getSourceFile().getName();
        }
        boolean byteExactPackage = byteExactArtifactFileName != null;
        Map<String, String> originalWarLibraryPaths = originalWarLibraryPaths(analysis, useOriginalWarLibraries);

        // Properties
        int javaVersion = config.getJavaVersion() > 0 ? config.getJavaVersion() : analysis.getJavaVersion();
        Map<String, String> properties = new LinkedHashMap<>();
        if (embeddedPom != null) {
            properties.putAll(embeddedPom.getProperties());
        }
        properties.put("maven.compiler.source", String.valueOf(javaVersion));
        properties.put("maven.compiler.target", String.valueOf(javaVersion));
        if (!properties.containsKey("project.build.sourceEncoding")) {
            properties.put("project.build.sourceEncoding", "UTF-8");
        }
        if (config != null && config.isByteExactPackage()) {
            addByteExactPackageSkipProperties(properties);
        }
        if (originalBuildInfoPresent) {
            properties.put("spring-boot.build-info.skip", "true");
        }
        if (originalSbomPresent) {
            properties.put("cyclonedx.skip", "true");
        }

        sb.append("    <properties>\n");
        for (Map.Entry<String, String> property : properties.entrySet()) {
            sb.append("        <").append(property.getKey()).append(">")
                    .append(escapeXml(property.getValue()))
                    .append("</").append(property.getKey()).append(">\n");
        }
        sb.append("    </properties>\n\n");

        if (embeddedPom != null && !embeddedPom.getDependencyManagement().isEmpty()) {
            sb.append("    <dependencyManagement>\n");
            sb.append("        <dependencies>\n");
            for (MavenDependency dep : embeddedPom.getDependencyManagement()) {
                appendDependency(sb, dep, "            ", false, null);
            }
            sb.append("        </dependencies>\n");
            sb.append("    </dependencyManagement>\n\n");
        }

        // Dependencies
        List<MavenDependency> deps = analysis.getDetectedDependencies();
        List<MavenDependency> included = new java.util.ArrayList<>();
        boolean dependencyManagementIncluded = embeddedPom != null && !embeddedPom.getDependencyManagement().isEmpty();
        for (MavenDependency dep : deps) {
            if (dep.isIncluded()
                    && !isBundledByteExactDependency(dep, analysis, byteExactPackage)
                    && canRenderDependency(dep, parentIncluded || dependencyManagementIncluded,
                    groupId, version, properties)) {
                included.add(dep);
            }
        }

        if (!included.isEmpty()) {
            sb.append("    <dependencies>\n");
            for (MavenDependency dep : included) {
                appendDependency(sb, dep, "        ", useOriginalWarLibraries, originalWarLibraryPaths);
            }
            sb.append("    </dependencies>\n\n");
        }

        if (embeddedPom != null) {
            appendRepositories(sb, "repositories", "repository", embeddedPom.getRepositories());
            appendRepositories(sb, "pluginRepositories", "pluginRepository", embeddedPom.getPluginRepositories());
        }

        boolean useOriginalClassOverlay = analysis != null && !analysis.getClassFiles().isEmpty();
        boolean useOriginalResourceOverlay = analysis != null && !analysis.getResourceFiles().isEmpty();

        // Build section
        sb.append("    <build>\n");
        if (byteExactArtifactFileName != null) {
            sb.append("        <finalName>")
                    .append(escapeXml(artifactBaseName(byteExactArtifactFileName)))
                    .append("</finalName>\n");
        }
        sb.append("        <plugins>\n");
        boolean hasCompilerPlugin = false;
        boolean hasArchiveDescriptorPlugin = false;
        if (embeddedPom != null) {
            for (BuildPluginInfo plugin : embeddedPom.getBuildPlugins()) {
                if (shouldAppendBuildPlugin(plugin, byteExactPackage, properties)) {
                    if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
                        hasCompilerPlugin = true;
                    }
                    if (isArchiveDescriptorPlugin(plugin.getArtifactId(), packaging)) {
                        hasArchiveDescriptorPlugin = true;
                    }
                    appendBuildPlugin(sb, plugin, packaging, originalManifestPath, originalCreatedBy,
                            originalBuildInfoPresent, useOriginalWarLibraries);
                }
            }
        }
        if (!hasCompilerPlugin) {
            appendFallbackCompilerPlugin(sb, javaVersion);
        }
        if (!hasArchiveDescriptorPlugin) {
            appendMavenDescriptorPlugin(sb, packaging, originalManifestPath, originalCreatedBy,
                    useOriginalWarLibraries);
        }
        if (useOriginalClassOverlay || useOriginalResourceOverlay || byteExactArtifactFileName != null) {
            appendAntrunPlugin(sb, useOriginalClassOverlay, useOriginalResourceOverlay,
                    byteExactArtifactFileName);
        }
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");

        if (embeddedPom != null && !embeddedPom.getProfilesXml().isEmpty()) {
            sb.append("\n");
            sb.append("    <profiles>\n");
            for (String profileXml : embeddedPom.getProfilesXml()) {
                appendRawXml(sb, profileXml, "        ");
            }
            sb.append("    </profiles>\n");
        }

        sb.append("</project>\n");
        return sb.toString();
    }

    private void appendParent(StringBuilder sb, PomInfo pomInfo) {
        sb.append("    <parent>\n");
        appendElement(sb, "groupId", pomInfo.getParentGroupId(), "        ");
        appendElement(sb, "artifactId", pomInfo.getParentArtifactId(), "        ");
        appendElement(sb, "version", pomInfo.getParentVersion(), "        ");
        if (pomInfo.getParentRelativePath() != null) {
            appendElement(sb, "relativePath", pomInfo.getParentRelativePath(), "        ");
        }
        sb.append("    </parent>\n\n");
    }

    private boolean shouldIncludeParent(PomInfo pomInfo, String projectGroupId, String projectVersion) {
        if (pomInfo == null || pomInfo.getParentArtifactId() == null) {
            return false;
        }
        String version = pomInfo.getParentVersion();
        if (!hasKnownValue(version)) {
            return false;
        }
        String trimmed = version.trim();
        return !trimmed.contains("${")
                && !trimmed.toUpperCase().contains("SNAPSHOT")
                && !isReactorLocalParent(pomInfo, projectGroupId, projectVersion);
    }

    private boolean isReactorLocalParent(PomInfo pomInfo, String projectGroupId, String projectVersion) {
        if (!sameValue(pomInfo.getParentGroupId(), projectGroupId)
                || !sameValue(pomInfo.getParentVersion(), projectVersion)) {
            return false;
        }
        String relativePath = pomInfo.getParentRelativePath();
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        String normalized = relativePath.trim().replace('\\', '/');
        return normalized.equals("pom.xml")
                || normalized.endsWith("/pom.xml")
                || normalized.equals("../pom.xml")
                || normalized.startsWith("../");
    }

    private boolean sameValue(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    private void addByteExactPackageSkipProperties(Map<String, String> properties) {
        properties.put("skipTests", "true");
        properties.put("maven.test.skip", "true");
        properties.put("checkstyle.skip", "true");
        properties.put("spring-javaformat.skip", "true");
        properties.put("impsort.skip", "true");
        properties.put("formatter.skip", "true");
        properties.put("spotless.check.skip", "true");
        properties.put("spotless.apply.skip", "true");
        properties.put("license.skip", "true");
        properties.put("rat.skip", "true");
        properties.put("enforcer.skip", "true");
        properties.put("jacoco.skip", "true");
        properties.put("git.commit.id.skip", "true");
        properties.put("maven.javadoc.skip", "true");
        properties.put("maven.source.skip", "true");
    }

    private boolean canRenderDependency(MavenDependency dep, boolean managedVersionAvailable,
                                        String projectGroupId, String projectVersion,
                                        Map<String, String> properties) {
        if (dep == null) {
            return false;
        }
        String version = dep.getVersion();
        if (hasKnownValue(version)) {
            if (!hasResolvablePropertyReferences(version, properties)) {
                return false;
            }
            if (isProjectVersionReference(version) && isSameGroupSnapshot(dep, projectGroupId, projectVersion)) {
                return false;
            }
            return true;
        }
        return managedVersionAvailable;
    }

    private boolean isProjectVersionReference(String version) {
        return version != null && version.contains("${project.version}");
    }

    private boolean isSameGroupSnapshot(MavenDependency dep, String projectGroupId, String projectVersion) {
        return dep.getGroupId() != null
                && dep.getGroupId().equals(projectGroupId)
                && projectVersion != null
                && projectVersion.toUpperCase().contains("SNAPSHOT");
    }

    private void appendDependency(StringBuilder sb, MavenDependency dep, String indent,
                                  boolean useOriginalWarLibraries,
                                  Map<String, String> originalWarLibraryPaths) {
        sb.append(indent).append("<dependency>\n");
        appendElement(sb, "groupId", dep.getGroupId(), indent + "    ");
        appendElement(sb, "artifactId", dep.getArtifactId(), indent + "    ");
        if (hasKnownValue(dep.getVersion())) {
            appendElement(sb, "version", dep.getVersion(), indent + "    ");
        }
        if (dep.getType() != null && !"jar".equals(dep.getType())) {
            appendElement(sb, "type", dep.getType(), indent + "    ");
        }
        String systemPath = originalWarLibrarySystemPath(dep, originalWarLibraryPaths);
        String scope = systemPath == null ? dependencyScope(dep, useOriginalWarLibraries) : "system";
        if (scope != null && !"compile".equals(scope)) {
            appendElement(sb, "scope", scope, indent + "    ");
        }
        if (systemPath != null) {
            appendElement(sb, "systemPath", systemPath, indent + "    ");
        }
        sb.append(indent).append("</dependency>\n");
    }

    private String dependencyScope(MavenDependency dep, boolean useOriginalWarLibraries) {
        String scope = dep.getScope();
        if (!useOriginalWarLibraries) {
            return scope;
        }
        if (scope == null || "compile".equals(scope) || "runtime".equals(scope)) {
            return "provided";
        }
        return scope;
    }

    private Map<String, String> originalWarLibraryPaths(JarAnalysisResult analysis,
                                                        boolean useOriginalWarLibraries) {
        Map<String, String> libraries = new LinkedHashMap<>();
        if (!useOriginalWarLibraries || analysis == null) {
            return libraries;
        }
        for (String resource : analysis.getResourceFiles()) {
            if (resource != null && resource.startsWith("WEB-INF/lib/") && resource.endsWith(".jar")) {
                int slash = resource.lastIndexOf('/');
                String fileName = slash >= 0 ? resource.substring(slash + 1) : resource;
                libraries.putIfAbsent(fileName, "${project.basedir}/src/main/original-libs/" + resource);
            }
        }
        return libraries;
    }

    private String originalWarLibrarySystemPath(MavenDependency dep, Map<String, String> originalWarLibraryPaths) {
        if (dep == null || originalWarLibraryPaths == null || originalWarLibraryPaths.isEmpty()
                || !hasKnownValue(dep.getArtifactId()) || !hasKnownValue(dep.getVersion())
                || dep.getVersion().contains("${")) {
            return null;
        }
        return originalWarLibraryPaths.get(dep.getArtifactId() + "-" + dep.getVersion() + ".jar");
    }

    private boolean isBundledByteExactDependency(MavenDependency dep, JarAnalysisResult analysis,
                                                 boolean byteExactPackage) {
        if (!byteExactPackage || dep == null || analysis == null || !hasKnownValue(dep.getGroupId())) {
            return false;
        }
        String prefix = dep.getGroupId().replace('.', '/') + "/";
        return hasClassPrefix(analysis.getClassFiles(), prefix)
                || hasClassPrefix(analysis.getSkippedDependencyClassFiles(), prefix);
    }

    private boolean hasClassPrefix(List<String> classFiles, String prefix) {
        if (classFiles == null || prefix == null || prefix.isEmpty()) {
            return false;
        }
        for (String classFile : classFiles) {
            if (classFile != null && classFile.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void appendRepositories(StringBuilder sb, String containerName, String entryName,
                                    List<RepositoryInfo> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return;
        }
        sb.append("    <").append(containerName).append(">\n");
        for (RepositoryInfo repository : repositories) {
            sb.append("        <").append(entryName).append(">\n");
            appendElement(sb, "id", repository.getId(), "            ");
            appendElement(sb, "url", repository.getUrl(), "            ");
            if (repository.getReleasesXml() != null) {
                appendRawXml(sb, repository.getReleasesXml(), "            ");
            }
            if (repository.getSnapshotsXml() != null) {
                appendRawXml(sb, repository.getSnapshotsXml(), "            ");
            }
            sb.append("        </").append(entryName).append(">\n");
        }
        sb.append("    </").append(containerName).append(">\n\n");
    }

    private void appendBuildPlugin(StringBuilder sb, BuildPluginInfo plugin, String packaging,
                                   String originalManifestPath, String originalCreatedBy,
                                   boolean originalBuildInfoPresent, boolean useOriginalWarLibraries) {
        sb.append("            <plugin>\n");
        if (plugin.getGroupId() != null) {
            appendElement(sb, "groupId", plugin.getGroupId(), "                ");
        }
        appendElement(sb, "artifactId", plugin.getArtifactId(), "                ");
        if (plugin.getVersion() != null) {
            appendElement(sb, "version", plugin.getVersion(), "                ");
        }
        boolean compilerPlugin = "maven-compiler-plugin".equals(plugin.getArtifactId());
        boolean archiveDescriptorPlugin = isArchiveDescriptorPlugin(plugin.getArtifactId(), packaging);
        if (plugin.getConfigurationXml() != null) {
            String configurationXml = plugin.getConfigurationXml();
            if (compilerPlugin) {
                configurationXml = sanitizeCompilerConfiguration(configurationXml);
            }
            if (archiveDescriptorPlugin) {
                configurationXml = disableMavenDescriptor(configurationXml);
                configurationXml = configureOriginalManifest(configurationXml, originalManifestPath, true);
                configurationXml = configureOriginalCreatedBy(configurationXml, originalCreatedBy);
                if (useOriginalWarLibraries) {
                    configurationXml = configureOriginalWarLibraries(configurationXml);
                }
            }
            appendRawXml(sb, configurationXml, "                ");
        } else if (compilerPlugin) {
            sb.append("                <configuration combine.self=\"override\">\n");
            sb.append("                    <proc>none</proc>\n");
            sb.append("                </configuration>\n");
        } else if (archiveDescriptorPlugin) {
            appendMavenDescriptorConfiguration(sb, packaging, originalManifestPath, originalCreatedBy,
                    useOriginalWarLibraries, "                ");
        }
        if (!plugin.getExecutionsXml().isEmpty()) {
            sb.append("                <executions>\n");
            for (String executionXml : plugin.getExecutionsXml()) {
                String normalizedExecutionXml = executionXml;
                if (compilerPlugin) {
                    normalizedExecutionXml = sanitizeCompilerConfiguration(normalizedExecutionXml);
                }
                if (originalBuildInfoPresent && isSpringBootPlugin(plugin.getArtifactId())) {
                    normalizedExecutionXml = removeBuildInfoGoal(normalizedExecutionXml);
                }
                if (hasAnyGoal(normalizedExecutionXml)) {
                    appendRawXml(sb, normalizedExecutionXml, "                    ");
                }
            }
            sb.append("                </executions>\n");
        }
        sb.append("            </plugin>\n");
    }

    private String sanitizeCompilerConfiguration(String xml) {
        if (xml == null) {
            return null;
        }
        String sanitized = xml.replaceAll("(?s)\\s*<arg>\\s*-Werror\\s*</arg>", "")
                .replaceAll("(?s)\\s*<compilerArgument>\\s*-Werror\\s*</compilerArgument>", "")
                .replaceAll("(?is)\\s*<arg>\\s*[^<]*error[-_ ]?prone[^<]*</arg>", "")
                .replaceAll("(?is)\\s*<compilerArgument>\\s*[^<]*error[-_ ]?prone[^<]*</compilerArgument>", "")
                .replaceAll("(?s)<failOnWarning>\\s*true\\s*</failOnWarning>",
                        "<failOnWarning>false</failOnWarning>");
        sanitized = overrideCompilerConfigurationInheritance(sanitized);
        if (sanitized.matches("(?s).*<proc>.*?</proc>.*")) {
            return sanitized.replaceAll("(?s)<proc>.*?</proc>", "<proc>none</proc>");
        }
        if (sanitized.contains("</configuration>")) {
            return sanitized.replace("</configuration>", "  <proc>none</proc>\n</configuration>");
        }
        return sanitized;
    }

    private String overrideCompilerConfigurationInheritance(String xml) {
        return xml.replaceAll("(?s)<configuration(?![^>]*\\bcombine\\.self\\s*=)([^>]*)>",
                "<configuration combine.self=\"override\"$1>");
    }

    private boolean shouldAppendBuildPlugin(BuildPluginInfo plugin, boolean byteExactPackage,
                                            Map<String, String> properties) {
        if (plugin == null || plugin.getArtifactId() == null) {
            return false;
        }
        if (hasKnownValue(plugin.getVersion())
                && !hasResolvablePropertyReferences(plugin.getVersion(), properties)) {
            return false;
        }
        if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
            return true;
        }
        if (isQualityGatePlugin(plugin.getArtifactId())) {
            return false;
        }
        if (byteExactPackage && isPackageTransformingPlugin(plugin.getArtifactId())) {
            return false;
        }
        for (String executionXml : plugin.getExecutionsXml()) {
            if (runsBeforeCompile(executionXml)) {
                return false;
            }
        }
        return true;
    }

    private boolean isQualityGatePlugin(String artifactId) {
        return "xml-maven-plugin".equals(artifactId)
                || "maven-checkstyle-plugin".equals(artifactId)
                || "maven-pmd-plugin".equals(artifactId)
                || "spotbugs-maven-plugin".equals(artifactId)
                || "spotless-maven-plugin".equals(artifactId)
                || "rewrite-maven-plugin".equals(artifactId)
                || "forbiddenapis".equals(artifactId)
                || "nondex-maven-plugin".equals(artifactId)
                || "json-schema-validator".equals(artifactId)
                || "antlr4-maven-plugin".equals(artifactId)
                || "replacer".equals(artifactId)
                || "moditect-maven-plugin".equals(artifactId);
    }

    private boolean isPackageTransformingPlugin(String artifactId) {
        return "maven-shade-plugin".equals(artifactId)
                || "spring-boot-maven-plugin".equals(artifactId)
                || "maven-assembly-plugin".equals(artifactId)
                || "maven-source-plugin".equals(artifactId)
                || "maven-javadoc-plugin".equals(artifactId);
    }

    private boolean runsBeforeCompile(String executionXml) {
        if (executionXml == null) {
            return false;
        }
        String normalized = executionXml.replaceAll("\\s+", "");
        return normalized.contains("<phase>validate</phase>")
                || normalized.contains("<phase>initialize</phase>")
                || normalized.contains("<phase>generate-sources</phase>")
                || normalized.contains("<phase>process-sources</phase>")
                || normalized.contains("<phase>generate-resources</phase>")
                || normalized.contains("<phase>process-resources</phase>");
    }

    private boolean hasResolvablePropertyReferences(String value, Map<String, String> properties) {
        if (value == null) {
            return true;
        }
        int start = value.indexOf("${");
        while (start >= 0) {
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                return false;
            }
            String propertyName = value.substring(start + 2, end);
            if (!isAlwaysResolvableProperty(propertyName)) {
                String resolved = properties.get(propertyName);
                if (!hasKnownValue(resolved) || resolved.contains("${")) {
                    return false;
                }
            }
            start = value.indexOf("${", end + 1);
        }
        return true;
    }

    private boolean isAlwaysResolvableProperty(String propertyName) {
        return "project.version".equals(propertyName)
                || "project.groupId".equals(propertyName)
                || "project.artifactId".equals(propertyName);
    }

    private void appendFallbackCompilerPlugin(StringBuilder sb, int javaVersion) {
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>maven-compiler-plugin</artifactId>\n");
        sb.append("                <version>3.11.0</version>\n");
        sb.append("                <configuration combine.self=\"override\">\n");
        sb.append("                    <source>").append(javaVersion).append("</source>\n");
        sb.append("                    <target>").append(javaVersion).append("</target>\n");
        sb.append("                    <encoding>UTF-8</encoding>\n");
        sb.append("                    <proc>none</proc>\n");
        sb.append("                </configuration>\n");
        sb.append("            </plugin>\n");
    }

    private void appendMavenDescriptorPlugin(StringBuilder sb, String packaging, String originalManifestPath,
                                             String originalCreatedBy, boolean useOriginalWarLibraries) {
        boolean war = "war".equals(packaging);
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>").append(war ? "maven-war-plugin" : "maven-jar-plugin")
                .append("</artifactId>\n");
        appendMavenDescriptorConfiguration(sb, packaging, originalManifestPath, originalCreatedBy,
                useOriginalWarLibraries, "                ");
        sb.append("            </plugin>\n");
    }

    private void appendMavenDescriptorConfiguration(StringBuilder sb, String packaging,
                                                    String originalManifestPath, String originalCreatedBy,
                                                    boolean useOriginalWarLibraries,
                                                    String indent) {
        sb.append(indent).append("<configuration>\n");
        if ("war".equals(packaging)) {
            sb.append(indent).append("    <failOnMissingWebXml>false</failOnMissingWebXml>\n");
        }
        if (useOriginalWarLibraries) {
            sb.append(indent).append("    <webResources>\n");
            appendOriginalWarLibraryWebResource(sb, indent + "        ");
            sb.append(indent).append("    </webResources>\n");
        }
        sb.append(indent).append("    <archive>\n");
        if (originalCreatedBy != null) {
            sb.append(indent).append("        <manifestEntries>\n");
            appendElement(sb, "Created-By", originalCreatedBy, indent + "            ");
            sb.append(indent).append("        </manifestEntries>\n");
        }
        if (originalManifestPath != null) {
            sb.append(indent).append("        <manifest>\n");
            sb.append(indent).append("            <addDefaultEntries>false</addDefaultEntries>\n");
            sb.append(indent).append("        </manifest>\n");
            sb.append(indent).append("        <manifestFile>")
                    .append(escapeXml(originalManifestPath))
                    .append("</manifestFile>\n");
        }
        sb.append(indent).append("        <addMavenDescriptor>false</addMavenDescriptor>\n");
        sb.append(indent).append("    </archive>\n");
        sb.append(indent).append("</configuration>\n");
    }

    private boolean isArchiveDescriptorPlugin(String artifactId, String packaging) {
        if ("war".equals(packaging)) {
            return "maven-war-plugin".equals(artifactId);
        }
        return "maven-jar-plugin".equals(artifactId);
    }

    private String disableMavenDescriptor(String configurationXml) {
        if (configurationXml == null) {
            return null;
        }
        if (configurationXml.matches("(?s).*<addMavenDescriptor>.*?</addMavenDescriptor>.*")) {
            return configurationXml.replaceAll("(?s)<addMavenDescriptor>.*?</addMavenDescriptor>",
                    "<addMavenDescriptor>false</addMavenDescriptor>");
        }
        if (configurationXml.contains("</archive>")) {
            return configurationXml.replace("</archive>",
                    "  <addMavenDescriptor>false</addMavenDescriptor>\n</archive>");
        }
        if (configurationXml.contains("</configuration>")) {
            return configurationXml.replace("</configuration>",
                    "  <archive>\n"
                            + "    <addMavenDescriptor>false</addMavenDescriptor>\n"
                            + "  </archive>\n"
                            + "</configuration>");
        }
        return configurationXml;
    }

    private String configureOriginalManifest(String configurationXml, String originalManifestPath,
                                             boolean disableDefaultEntries) {
        if (configurationXml == null || originalManifestPath == null) {
            return configurationXml;
        }
        if (disableDefaultEntries) {
            configurationXml = disableDefaultManifestEntries(configurationXml);
        }
        String manifestElement = "<manifestFile>" + escapeXml(originalManifestPath) + "</manifestFile>";
        if (configurationXml.matches("(?s).*<manifestFile>.*?</manifestFile>.*")) {
            return configurationXml.replaceAll("(?s)<manifestFile>.*?</manifestFile>",
                    Matcher.quoteReplacement(manifestElement));
        }
        if (configurationXml.contains("</archive>")) {
            return configurationXml.replace("</archive>", "  " + manifestElement + "\n</archive>");
        }
        if (configurationXml.contains("</configuration>")) {
            return configurationXml.replace("</configuration>",
                    "  <archive>\n"
                            + "    " + manifestElement + "\n"
                            + "  </archive>\n"
                            + "</configuration>");
        }
        return configurationXml;
    }

    private String configureOriginalCreatedBy(String configurationXml, String originalCreatedBy) {
        if (configurationXml == null || originalCreatedBy == null) {
            return configurationXml;
        }
        String createdByElement = "<Created-By>" + escapeXml(originalCreatedBy) + "</Created-By>";
        if (configurationXml.matches("(?s).*<Created-By>.*?</Created-By>.*")) {
            return configurationXml.replaceAll("(?s)<Created-By>.*?</Created-By>",
                    Matcher.quoteReplacement(createdByElement));
        }
        if (configurationXml.contains("</manifestEntries>")) {
            return configurationXml.replace("</manifestEntries>",
                    "  " + createdByElement + "\n</manifestEntries>");
        }
        if (configurationXml.contains("</archive>")) {
            return configurationXml.replace("</archive>",
                    "  <manifestEntries>\n"
                            + "    " + createdByElement + "\n"
                            + "  </manifestEntries>\n"
                            + "</archive>");
        }
        if (configurationXml.contains("</configuration>")) {
            return configurationXml.replace("</configuration>",
                    "  <archive>\n"
                            + "    <manifestEntries>\n"
                            + "      " + createdByElement + "\n"
                            + "    </manifestEntries>\n"
                            + "  </archive>\n"
                            + "</configuration>");
        }
        return configurationXml;
    }

    private String configureOriginalWarLibraries(String configurationXml) {
        if (configurationXml == null
                || configurationXml.contains("${project.basedir}/src/main/original-libs/WEB-INF/lib")) {
            return configurationXml;
        }
        String webResource = originalWarLibraryWebResource("  ");
        if (configurationXml.contains("</webResources>")) {
            return configurationXml.replace("</webResources>", webResource + "</webResources>");
        }
        if (configurationXml.contains("</configuration>")) {
            return configurationXml.replace("</configuration>",
                    "  <webResources>\n"
                            + webResource
                            + "  </webResources>\n"
                            + "</configuration>");
        }
        return configurationXml;
    }

    private void appendOriginalWarLibraryWebResource(StringBuilder sb, String indent) {
        sb.append(originalWarLibraryWebResource(indent));
    }

    private String originalWarLibraryWebResource(String indent) {
        return indent + "<resource>\n"
                + indent + "    <directory>${project.basedir}/src/main/original-libs/WEB-INF/lib</directory>\n"
                + indent + "    <targetPath>WEB-INF/lib</targetPath>\n"
                + indent + "    <includes>\n"
                + indent + "        <include>*.jar</include>\n"
                + indent + "    </includes>\n"
                + indent + "</resource>\n";
    }

    private String disableDefaultManifestEntries(String configurationXml) {
        String defaultEntriesElement = "<addDefaultEntries>false</addDefaultEntries>";
        if (configurationXml.matches("(?s).*<addDefaultEntries>.*?</addDefaultEntries>.*")) {
            return configurationXml.replaceAll("(?s)<addDefaultEntries>.*?</addDefaultEntries>",
                    defaultEntriesElement);
        }
        if (configurationXml.contains("</manifest>")) {
            return configurationXml.replace("</manifest>", "  " + defaultEntriesElement + "\n</manifest>");
        }
        if (configurationXml.contains("</archive>")) {
            return configurationXml.replace("</archive>",
                    "  <manifest>\n"
                            + "    " + defaultEntriesElement + "\n"
                            + "  </manifest>\n"
                            + "</archive>");
        }
        if (configurationXml.contains("</configuration>")) {
            return configurationXml.replace("</configuration>",
                    "  <archive>\n"
                            + "    <manifest>\n"
                            + "      " + defaultEntriesElement + "\n"
                            + "    </manifest>\n"
                            + "  </archive>\n"
                            + "</configuration>");
        }
        return configurationXml;
    }

    private String removeBuildInfoGoal(String executionXml) {
        if (executionXml == null) {
            return null;
        }
        return executionXml.replaceAll("(?s)\\s*<goal>\\s*build-info\\s*</goal>", "");
    }

    private boolean hasAnyGoal(String executionXml) {
        return executionXml != null && executionXml.matches("(?s).*<goal>\\s*[^<]+\\s*</goal>.*");
    }

    private boolean isSpringBootPlugin(String artifactId) {
        return "spring-boot-maven-plugin".equals(artifactId);
    }

    private boolean isSpringBootExecutable(JarAnalysisResult analysis) {
        if (analysis == null || analysis.getManifestInfo() == null) {
            return false;
        }
        ManifestInfo manifestInfo = analysis.getManifestInfo();
        String mainClass = manifestInfo.getMainClass();
        return (mainClass != null && mainClass.startsWith("org.springframework.boot.loader."))
                || manifestInfo.getAllEntries().containsKey("Spring-Boot-Classes")
                || manifestInfo.getAllEntries().containsKey("Spring-Boot-Lib");
    }

    private boolean hasMetaInfFile(JarAnalysisResult analysis, String path) {
        return analysis != null
                && path != null
                && analysis.getMetaInfFiles().stream().anyMatch(path::equals);
    }

    private boolean hasMetaInfPathPrefix(JarAnalysisResult analysis, String prefix) {
        return analysis != null
                && prefix != null
                && analysis.getMetaInfFiles().stream().anyMatch(path -> path.startsWith(prefix));
    }

    private boolean hasResourcePathPrefix(JarAnalysisResult analysis, String prefix) {
        return analysis != null
                && prefix != null
                && analysis.getResourceFiles().stream().anyMatch(path -> path.startsWith(prefix));
    }

    private String projectName(JarAnalysisResult analysis) {
        if (analysis == null || analysis.getManifestInfo() == null) {
            return null;
        }
        String implementationTitle = analysis.getManifestInfo().getImplementationTitle();
        if (!hasKnownValue(implementationTitle)) {
            return null;
        }
        return implementationTitle.trim();
    }

    private String originalManifestPath(String packaging, boolean originalManifestPresent) {
        if (!originalManifestPresent) {
            return null;
        }
        String resourceRoot = "war".equals(packaging) ? "webapp" : "resources";
        return "${project.basedir}/src/main/" + resourceRoot + "/META-INF/MANIFEST.MF";
    }

    private String originalCreatedBy(JarAnalysisResult analysis, boolean originalManifestPresent) {
        if (!originalManifestPresent || analysis == null || analysis.getManifestInfo() == null) {
            return null;
        }
        String createdBy = analysis.getManifestInfo().getCreatedBy();
        if (!hasKnownValue(createdBy)) {
            return null;
        }
        String trimmed = createdBy.trim();
        return trimmed.startsWith("Apache Maven ") ? trimmed : null;
    }

    private void appendAntrunPlugin(StringBuilder sb, boolean useOriginalClassOverlay,
                                    boolean useOriginalResourceOverlay, String rawArtifactFileName) {
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>maven-antrun-plugin</artifactId>\n");
        sb.append("                <version>3.1.0</version>\n");
        sb.append("                <executions>\n");
        if (useOriginalClassOverlay) {
            appendOriginalClassOverlayExecution(sb);
        }
        if (useOriginalResourceOverlay) {
            appendOriginalResourceOverlayExecution(sb);
        }
        if (rawArtifactFileName != null) {
            appendByteExactPackageRestoreExecution(sb, rawArtifactFileName);
        }
        sb.append("                </executions>\n");
        sb.append("            </plugin>\n");
    }

    private void appendOriginalClassOverlayExecution(StringBuilder sb) {
        sb.append("                    <execution>\n");
        sb.append("                        <id>restore-original-class-bytes</id>\n");
        sb.append("                        <phase>process-classes</phase>\n");
        sb.append("                        <goals>\n");
        sb.append("                            <goal>run</goal>\n");
        sb.append("                        </goals>\n");
        sb.append("                        <configuration>\n");
        sb.append("                            <target>\n");
        sb.append("                                <copy todir=\"${project.build.outputDirectory}\" overwrite=\"true\" preservelastmodified=\"true\">\n");
        sb.append("                                    <fileset dir=\"${project.basedir}/src/main/original-classes\" />\n");
        sb.append("                                </copy>\n");
        sb.append("                            </target>\n");
        sb.append("                        </configuration>\n");
        sb.append("                    </execution>\n");
    }

    private void appendOriginalResourceOverlayExecution(StringBuilder sb) {
        sb.append("                    <execution>\n");
        sb.append("                        <id>restore-original-resource-metadata</id>\n");
        sb.append("                        <phase>process-classes</phase>\n");
        sb.append("                        <goals>\n");
        sb.append("                            <goal>run</goal>\n");
        sb.append("                        </goals>\n");
        sb.append("                        <configuration>\n");
        sb.append("                            <target>\n");
        sb.append("                                <copy todir=\"${project.build.outputDirectory}\" overwrite=\"true\" preservelastmodified=\"true\">\n");
        sb.append("                                    <fileset dir=\"${project.basedir}/src/main/resources\" />\n");
        sb.append("                                </copy>\n");
        sb.append("                            </target>\n");
        sb.append("                        </configuration>\n");
        sb.append("                    </execution>\n");
    }

    private void appendByteExactPackageRestoreExecution(StringBuilder sb, String rawArtifactFileName) {
        sb.append("                    <execution>\n");
        sb.append("                        <id>restore-byte-exact-package-records</id>\n");
        sb.append("                        <phase>package</phase>\n");
        sb.append("                        <goals>\n");
        sb.append("                            <goal>run</goal>\n");
        sb.append("                        </goals>\n");
        sb.append("                        <configuration>\n");
        sb.append("                            <target>\n");
        sb.append("                                <mkdir dir=\"${project.build.directory}/byte-exact-package-helper-classes\" />\n");
        sb.append("                                <javac srcdir=\"${project.basedir}/.jar2mp/byte-exact\" destdir=\"${project.build.directory}/byte-exact-package-helper-classes\" includeantruntime=\"false\" source=\"8\" target=\"8\" />\n");
        sb.append("                                <mkdir dir=\"${project.build.directory}/byte-exact-package-restored\" />\n");
        sb.append("                                <java classname=\"ByteExactPackageRestorer\" fork=\"true\" failonerror=\"true\">\n");
        sb.append("                                    <classpath>\n");
        sb.append("                                        <pathelement location=\"${project.build.directory}/byte-exact-package-helper-classes\" />\n");
        sb.append("                                    </classpath>\n");
        sb.append("                                    <arg value=\"${project.basedir}/.jar2mp/byte-exact/raw-artifact/")
                .append(escapeXml(rawArtifactFileName))
                .append("\" />\n");
        sb.append("                                    <arg value=\"${project.build.directory}/${project.build.finalName}.${project.packaging}\" />\n");
        sb.append("                                    <arg value=\"${project.build.directory}/byte-exact-package-restored/${project.build.finalName}.${project.packaging}\" />\n");
        sb.append("                                </java>\n");
        sb.append("                            </target>\n");
        sb.append("                        </configuration>\n");
        sb.append("                    </execution>\n");
    }

    private String artifactBaseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionIndex);
    }

    private boolean hasKnownValue(String value) {
        return value != null
                && !value.trim().isEmpty()
                && !"unknown".equalsIgnoreCase(value.trim());
    }

    private String normalizeGroupId(String value) {
        if (!hasKnownValue(value)) {
            return "com.unknown";
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[\\s_\\-]+", ".")
                .replaceAll("[^a-z0-9.]+", "")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        return normalized.isEmpty() ? "com.unknown" : normalized;
    }

    private String normalizeArtifactId(String value) {
        if (!hasKnownValue(value)) {
            return "artifact";
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9_.-]+", "-")
                .replaceAll("[-.]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "artifact" : normalized;
    }

    private void appendElement(StringBuilder sb, String tag, String value, String indent) {
        if (value == null) {
            return;
        }
        sb.append(indent)
                .append("<").append(tag).append(">")
                .append(escapeXml(value))
                .append("</").append(tag).append(">\n");
    }

    private void appendRawXml(StringBuilder sb, String xml, String indent) {
        if (xml == null || xml.trim().isEmpty()) {
            return;
        }
        String[] lines = xml.trim().split("\\r?\\n");
        for (String line : lines) {
            sb.append(indent).append(line).append("\n");
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
