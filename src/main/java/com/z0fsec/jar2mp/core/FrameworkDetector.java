package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.*;

import java.util.*;

public class FrameworkDetector {

    public List<FrameworkFinding> detect(JarAnalysisResult result) {
        Map<String, FrameworkFinding> findings = new LinkedHashMap<>();
        detectSpringBoot(result, findings);
        detectServletWar(result, findings);
        detectMyBatis(result, findings);
        detectShiro(result, findings);
        detectLogging(result, findings);
        detectNativeLibraries(result, findings);
        return new ArrayList<>(findings.values());
    }

    private void detectSpringBoot(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        for (String path : allPaths(result)) {
            if (path.startsWith("BOOT-INF/classes/") || path.startsWith("BOOT-INF/lib/")) {
                add(findings, "Spring Boot", 95, path.startsWith("BOOT-INF/classes/")
                                ? "BOOT-INF/classes/" : "BOOT-INF/lib/",
                        "Generate Spring Boot run command");
            }
        }

        ManifestInfo manifest = result.getManifestInfo();
        if (manifest != null && manifest.getAllEntries().containsKey("Start-Class")) {
            add(findings, "Spring Boot", 95, "Start-Class: " + manifest.getAllEntries().get("Start-Class"),
                    "Generate Spring Boot run command");
        }

        for (MavenDependency dep : result.getDetectedDependencies()) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            if (key.contains("spring-boot")) {
                add(findings, "Spring Boot", 85, key, "Generate Spring Boot run command");
            }
        }
    }

    private void detectServletWar(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        if (result.isWar()) {
            add(findings, "Servlet WAR", 95, "WAR packaging", "Preserve src/main/webapp layout");
        }
        for (String path : allPaths(result)) {
            if ("WEB-INF/web.xml".equals(path) || path.startsWith("WEB-INF/classes/")) {
                add(findings, "Servlet WAR", 95, path, "Preserve src/main/webapp layout");
            }
        }
    }

    private void detectMyBatis(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        for (String path : allPaths(result)) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith("mapper.xml") || lower.endsWith("/mybatis-config.xml")
                    || "mybatis-config.xml".equals(lower)) {
                add(findings, "MyBatis", 70, path, "Verify mapper XML resource paths");
            }
        }
        for (MavenDependency dep : result.getDetectedDependencies()) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            if (key.toLowerCase(Locale.ROOT).contains("mybatis")) {
                add(findings, "MyBatis", 85, key, "Verify mapper XML resource paths");
            }
        }
    }

    private void detectShiro(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        for (String path : allPaths(result)) {
            if (path.toLowerCase(Locale.ROOT).endsWith("shiro.ini")) {
                add(findings, "Shiro", 70, path, "Review Shiro filter and realm configuration");
            }
        }
        for (String classFile : result.getClassFiles()) {
            if (classFile.contains("ShiroFilterFactoryBean")) {
                add(findings, "Shiro", 50, classFile, "Review Shiro filter and realm configuration");
            }
        }
        for (MavenDependency dep : result.getDetectedDependencies()) {
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            if (key.toLowerCase(Locale.ROOT).contains("shiro")) {
                add(findings, "Shiro", 85, key, "Review Shiro filter and realm configuration");
            }
        }
    }

    private void detectLogging(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        for (String path : allPaths(result)) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith("log4j2.xml") || lower.endsWith("logback.xml")
                    || lower.endsWith("log4j.properties")) {
                add(findings, "Logging", 70, path, "Preserve logging configuration resources");
            }
        }
        for (MavenDependency dep : result.getDetectedDependencies()) {
            String key = (dep.getGroupId() + ":" + dep.getArtifactId()).toLowerCase(Locale.ROOT);
            if (key.contains("log4j") || key.contains("logback") || key.contains("slf4j")) {
                add(findings, "Logging", 85, dep.getGroupId() + ":" + dep.getArtifactId(),
                        "Preserve logging configuration resources");
            }
        }
    }

    private void detectNativeLibraries(JarAnalysisResult result, Map<String, FrameworkFinding> findings) {
        for (String path : allPaths(result)) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".so") || lower.endsWith(".dll") || lower.endsWith(".dylib")) {
                add(findings, "Native/JNI", 70, path, "Verify native library loading path");
            }
        }
    }

    private List<String> allPaths(JarAnalysisResult result) {
        List<String> paths = new ArrayList<>();
        paths.addAll(result.getResourceFiles());
        paths.addAll(result.getMetaInfFiles());
        paths.addAll(result.getClassPathMapping().values());
        return paths;
    }

    private void add(Map<String, FrameworkFinding> findings, String name, int confidence,
                     String evidence, String action) {
        FrameworkFinding finding = findings.get(name);
        if (finding == null) {
            finding = new FrameworkFinding(name, confidence);
            findings.put(name, finding);
        }
        if (confidence > finding.getConfidence()) {
            finding.setConfidence(confidence);
        }
        if (evidence != null && !finding.getEvidence().contains(evidence)) {
            finding.getEvidence().add(evidence);
        }
        if (action != null && !finding.getRecommendedActions().contains(action)) {
            finding.getRecommendedActions().add(action);
        }
    }
}
