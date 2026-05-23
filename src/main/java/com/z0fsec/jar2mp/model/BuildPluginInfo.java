package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class BuildPluginInfo {
    private String groupId;
    private String artifactId;
    private String version;
    private String configurationXml;
    private final List<String> executionsXml = new ArrayList<>();

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getConfigurationXml() { return configurationXml; }
    public void setConfigurationXml(String configurationXml) { this.configurationXml = configurationXml; }
    public List<String> getExecutionsXml() { return executionsXml; }
}
