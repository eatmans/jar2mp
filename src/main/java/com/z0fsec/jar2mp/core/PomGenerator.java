package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // Packaging
        String packaging = config.getPackaging();
        if (packaging == null) {
            packaging = analysis.isWar() ? "war" : "jar";
        }
        if (!"jar".equals(packaging)) {
            sb.append("    <packaging>").append(escapeXml(packaging)).append("</packaging>\n");
        }

        sb.append("\n");

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
                appendDependency(sb, dep, "            ");
            }
            sb.append("        </dependencies>\n");
            sb.append("    </dependencyManagement>\n\n");
        }

        // Dependencies
        List<MavenDependency> deps = analysis.getDetectedDependencies();
        List<MavenDependency> included = new java.util.ArrayList<>();
        boolean dependencyManagementIncluded = embeddedPom != null && !embeddedPom.getDependencyManagement().isEmpty();
        for (MavenDependency dep : deps) {
            if (dep.isIncluded() && canRenderDependency(dep, parentIncluded || dependencyManagementIncluded, groupId, version)) {
                included.add(dep);
            }
        }

        if (!included.isEmpty()) {
            sb.append("    <dependencies>\n");
            for (MavenDependency dep : included) {
                appendDependency(sb, dep, "        ");
            }
            sb.append("    </dependencies>\n\n");
        }

        if (embeddedPom != null) {
            appendRepositories(sb, "repositories", "repository", embeddedPom.getRepositories());
            appendRepositories(sb, "pluginRepositories", "pluginRepository", embeddedPom.getPluginRepositories());
        }

        // Build section
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        boolean hasCompilerPlugin = false;
        if (embeddedPom != null) {
            for (BuildPluginInfo plugin : embeddedPom.getBuildPlugins()) {
                if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
                    hasCompilerPlugin = true;
                }
                if (shouldAppendBuildPlugin(plugin)) {
                    appendBuildPlugin(sb, plugin);
                }
            }
        }
        if (!hasCompilerPlugin) {
            appendFallbackCompilerPlugin(sb, javaVersion);
        }
        if (config != null && config.isByteExactPackage() && analysis.getSourceFile() != null) {
            appendByteExactPackagePlugin(sb, analysis.getSourceFile().getName());
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
                                        String projectGroupId, String projectVersion) {
        if (dep == null) {
            return false;
        }
        String version = dep.getVersion();
        if (hasKnownValue(version)) {
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

    private void appendDependency(StringBuilder sb, MavenDependency dep, String indent) {
        sb.append(indent).append("<dependency>\n");
        appendElement(sb, "groupId", dep.getGroupId(), indent + "    ");
        appendElement(sb, "artifactId", dep.getArtifactId(), indent + "    ");
        if (hasKnownValue(dep.getVersion())) {
            appendElement(sb, "version", dep.getVersion(), indent + "    ");
        }
        if (dep.getType() != null && !"jar".equals(dep.getType())) {
            appendElement(sb, "type", dep.getType(), indent + "    ");
        }
        if (dep.getScope() != null && !"compile".equals(dep.getScope())) {
            appendElement(sb, "scope", dep.getScope(), indent + "    ");
        }
        sb.append(indent).append("</dependency>\n");
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

    private void appendBuildPlugin(StringBuilder sb, BuildPluginInfo plugin) {
        sb.append("            <plugin>\n");
        if (plugin.getGroupId() != null) {
            appendElement(sb, "groupId", plugin.getGroupId(), "                ");
        }
        appendElement(sb, "artifactId", plugin.getArtifactId(), "                ");
        if (plugin.getVersion() != null) {
            appendElement(sb, "version", plugin.getVersion(), "                ");
        }
        boolean compilerPlugin = "maven-compiler-plugin".equals(plugin.getArtifactId());
        if (plugin.getConfigurationXml() != null) {
            String configurationXml = compilerPlugin
                    ? sanitizeCompilerConfiguration(plugin.getConfigurationXml())
                    : plugin.getConfigurationXml();
            appendRawXml(sb, configurationXml, "                ");
        } else if (compilerPlugin) {
            sb.append("                <configuration combine.self=\"override\">\n");
            sb.append("                    <proc>none</proc>\n");
            sb.append("                </configuration>\n");
        }
        if (!plugin.getExecutionsXml().isEmpty()) {
            sb.append("                <executions>\n");
            for (String executionXml : plugin.getExecutionsXml()) {
                appendRawXml(sb, compilerPlugin ? sanitizeCompilerConfiguration(executionXml) : executionXml,
                        "                    ");
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

    private boolean shouldAppendBuildPlugin(BuildPluginInfo plugin) {
        if (plugin == null || plugin.getArtifactId() == null) {
            return false;
        }
        if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
            return true;
        }
        for (String executionXml : plugin.getExecutionsXml()) {
            if (runsBeforeCompile(executionXml)) {
                return false;
            }
        }
        return true;
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

    private void appendByteExactPackagePlugin(StringBuilder sb, String rawArtifactFileName) {
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>maven-antrun-plugin</artifactId>\n");
        sb.append("                <version>3.1.0</version>\n");
        sb.append("                <executions>\n");
        sb.append("                    <execution>\n");
        sb.append("                        <id>restore-byte-exact-artifact</id>\n");
        sb.append("                        <phase>package</phase>\n");
        sb.append("                        <goals>\n");
        sb.append("                            <goal>run</goal>\n");
        sb.append("                        </goals>\n");
        sb.append("                        <configuration>\n");
        sb.append("                            <target>\n");
        sb.append("                                <copy file=\"${project.basedir}/target/raw-artifact/")
                .append(escapeXml(rawArtifactFileName))
                .append("\" tofile=\"${project.build.directory}/${project.build.finalName}.${project.packaging}\" overwrite=\"true\" />\n");
        sb.append("                            </target>\n");
        sb.append("                        </configuration>\n");
        sb.append("                    </execution>\n");
        sb.append("                </executions>\n");
        sb.append("            </plugin>\n");
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
