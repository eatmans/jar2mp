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
        if (embeddedPom != null && embeddedPom.getParentArtifactId() != null) {
            appendParent(sb, embeddedPom);
        }

        // GAV
        String groupId = config.getGroupId() != null ? config.getGroupId() : analysis.getDetectedGroupId();
        String artifactId = config.getArtifactId() != null ? config.getArtifactId() : analysis.getDetectedArtifactId();
        String version = config.getVersion() != null ? config.getVersion() : analysis.getDetectedVersion();

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
        for (MavenDependency dep : deps) {
            if (dep.isIncluded()) {
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
                appendBuildPlugin(sb, plugin);
            }
        }
        if (!hasCompilerPlugin) {
            appendFallbackCompilerPlugin(sb, javaVersion);
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
        if (plugin.getConfigurationXml() != null) {
            appendRawXml(sb, plugin.getConfigurationXml(), "                ");
        }
        if (!plugin.getExecutionsXml().isEmpty()) {
            sb.append("                <executions>\n");
            for (String executionXml : plugin.getExecutionsXml()) {
                appendRawXml(sb, executionXml, "                    ");
            }
            sb.append("                </executions>\n");
        }
        sb.append("            </plugin>\n");
    }

    private void appendFallbackCompilerPlugin(StringBuilder sb, int javaVersion) {
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        sb.append("                <artifactId>maven-compiler-plugin</artifactId>\n");
        sb.append("                <version>3.11.0</version>\n");
        sb.append("                <configuration>\n");
        sb.append("                    <source>").append(javaVersion).append("</source>\n");
        sb.append("                    <target>").append(javaVersion).append("</target>\n");
        sb.append("                    <encoding>UTF-8</encoding>\n");
        sb.append("                </configuration>\n");
        sb.append("            </plugin>\n");
    }

    private boolean hasKnownValue(String value) {
        return value != null
                && !value.trim().isEmpty()
                && !"unknown".equalsIgnoreCase(value.trim());
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
