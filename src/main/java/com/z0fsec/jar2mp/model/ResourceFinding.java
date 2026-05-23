package com.z0fsec.jar2mp.model;

public class ResourceFinding {

    public enum Category {
        CONFIG,
        MYBATIS_MAPPER,
        TEMPLATE,
        FRONTEND_ASSET,
        SERVLET_DESCRIPTOR,
        SPI,
        NATIVE_LIBRARY,
        CERTIFICATE,
        NESTED_LIBRARY,
        META_INF_RUNTIME,
        OTHER
    }

    private String originalPath;
    private Category category;
    private String targetPath;
    private String note;

    public ResourceFinding() {
    }

    public ResourceFinding(String originalPath, Category category, String targetPath, String note) {
        this.originalPath = originalPath;
        this.category = category;
        this.targetPath = targetPath;
        this.note = note;
    }

    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
