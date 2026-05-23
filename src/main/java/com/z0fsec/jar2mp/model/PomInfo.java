package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PomInfo {
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String parentGroupId;
    private String parentArtifactId;
    private String parentVersion;
    private String parentRelativePath;
    private final List<MavenDependency> dependencies = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final List<MavenDependency> dependencyManagement = new ArrayList<>();
    private final List<RepositoryInfo> repositories = new ArrayList<>();
    private final List<RepositoryInfo> pluginRepositories = new ArrayList<>();
    private final List<BuildPluginInfo> buildPlugins = new ArrayList<>();
    private final List<String> profilesXml = new ArrayList<>();

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
    public String getParentGroupId() { return parentGroupId; }
    public void setParentGroupId(String parentGroupId) { this.parentGroupId = parentGroupId; }
    public String getParentArtifactId() { return parentArtifactId; }
    public void setParentArtifactId(String parentArtifactId) { this.parentArtifactId = parentArtifactId; }
    public String getParentVersion() { return parentVersion; }
    public void setParentVersion(String parentVersion) { this.parentVersion = parentVersion; }
    public String getParentRelativePath() { return parentRelativePath; }
    public void setParentRelativePath(String parentRelativePath) { this.parentRelativePath = parentRelativePath; }
    public List<MavenDependency> getDependencies() { return dependencies; }
    public Map<String, String> getProperties() { return properties; }
    public List<MavenDependency> getDependencyManagement() { return dependencyManagement; }
    public List<RepositoryInfo> getRepositories() { return repositories; }
    public List<RepositoryInfo> getPluginRepositories() { return pluginRepositories; }
    public List<BuildPluginInfo> getBuildPlugins() { return buildPlugins; }
    public List<String> getProfilesXml() { return profilesXml; }

    public boolean hasCoordinates() {
        return groupId != null && !groupId.isEmpty()
                && artifactId != null && !artifactId.isEmpty()
                && version != null && !version.isEmpty();
    }
}
