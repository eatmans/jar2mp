package com.z0fsec.jar2mp.model;

public class RepositoryInfo {
    private String id;
    private String url;
    private String releasesXml;
    private String snapshotsXml;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getReleasesXml() { return releasesXml; }
    public void setReleasesXml(String releasesXml) { this.releasesXml = releasesXml; }
    public String getSnapshotsXml() { return snapshotsXml; }
    public void setSnapshotsXml(String snapshotsXml) { this.snapshotsXml = snapshotsXml; }
}
