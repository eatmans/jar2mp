package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.ClassFileUtils;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DependencyDetector {

    private final PackagePrefixDatabase packageDb;
    private final ClassFileScanner classFileScanner;
    private final MavenMetadataExtractor metadataExtractor;
    private final ManifestParser manifestParser;

    public DependencyDetector(PackagePrefixDatabase packageDb) {
        this.packageDb = packageDb;
        this.classFileScanner = new ClassFileScanner();
        this.metadataExtractor = new MavenMetadataExtractor();
        this.manifestParser = new ManifestParser();
    }

    /**
     * Detect all dependencies from a JAR file using multiple strategies.
     */
    public List<MavenDependency> detect(JarFile jarFile, ManifestInfo manifestInfo, PomInfo pomInfo) {
        return detect(jarFile, manifestInfo, pomInfo, null, null, null);
    }

    public List<MavenDependency> detect(JarFile jarFile,
                                        ManifestInfo manifestInfo,
                                        PomInfo pomInfo,
                                        List<PomInfo> embeddedPomInfos,
                                        List<String> applicationClassFiles,
                                        Map<String, String> classPathMapping) {
        Map<String, MavenDependency> deps = new LinkedHashMap<>();

        // Strategy 1: Embedded POM dependencies (highest confidence)
        if (pomInfo != null && pomInfo.getDependencies() != null) {
            for (MavenDependency dep : pomInfo.getDependencies()) {
                dep.setConfidence(MavenDependency.Confidence.HIGH);
                deps.put(dep.getKey(), dep);
            }
        }
        addEmbeddedPomDependencies(deps, pomInfo, embeddedPomInfos);

        // Strategy 2: Spring Boot/WAR bundled dependency jars
        addNestedLibraryDependencies(deps, jarFile);
        boolean hasEmbeddedDependencies = !deps.isEmpty();

        // Strategy 3: MANIFEST.MF Class-Path hints
        if (manifestInfo != null && manifestInfo.getClassPath() != null) {
            String[] cpEntries = manifestInfo.getClassPath().split("\\s+");
            for (String cpEntry : cpEntries) {
                MavenDependency dep = guessFromFilename(new File(cpEntry).getName());
                if (isResolvableManifestHint(dep)) {
                    dep.setConfidence(MavenDependency.Confidence.MEDIUM);
                    deps.putIfAbsent(dep.getKey(), dep);
                }
            }
        }

        // Strategy 4: Class file scanning against package database. When
        // embedded metadata or bundled libs exist, use scan data to fill
        // missing/property versions and add only external packages not already
        // covered by higher-confidence evidence.
        Set<String> packages = classFileScanner.scanPackages(jarFile, applicationClassFiles, classPathMapping);
        for (String pkg : packages) {
            MavenCoordinates coord = packageDb.lookup(pkg);
            if (coord == null) {
                continue;
            }
            String key = coord.getGroupId() + ":" + coord.getArtifactId();
            MavenDependency existing = deps.get(key);
            if (existing != null) {
                if (!hasConcreteVersion(existing.getVersion()) && hasConcreteVersion(coord.getVersion())) {
                    existing.setVersion(coord.getVersion());
                }
                continue;
            }
            if (hasEmbeddedDependencies && isOwnGroup(coord, pomInfo)) {
                continue;
            }
            if (hasEmbeddedDependencies && isCoveredByEmbeddedDependency(coord, deps.values())) {
                continue;
            }
            if (!hasEmbeddedDependencies || isExternalPackage(coord, pomInfo)) {
                MavenDependency dep = new MavenDependency(
                        coord.getGroupId(),
                        coord.getArtifactId(),
                        coord.getVersion(),
                        MavenDependency.Confidence.LOW
                );
                deps.put(key, dep);
            }
        }

        // Strategy 5: Filename heuristic for WAR files
        // (handled separately in WarAnalyzer)

        return new ArrayList<>(deps.values());
    }

    private void addNestedLibraryDependencies(Map<String, MavenDependency> deps, JarFile jarFile) {
        if (jarFile == null) {
            return;
        }
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!isNestedLibraryPath(name)) {
                continue;
            }
            MavenDependency dependency = null;
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                dependency = readNestedLibraryDependency(inputStream);
            } catch (IOException ignored) {
                // Filename parsing below remains the fallback for unreadable nested JARs.
            }
            if (dependency == null) {
                dependency = guessFromFilename(fileName(name));
            }
            if (dependency != null) {
                deps.putIfAbsent(dependency.getKey(), dependency);
            }
        }
    }

    private boolean isNestedLibraryPath(String name) {
        return name != null
                && (name.startsWith("BOOT-INF/lib/") || name.startsWith("WEB-INF/lib/"))
                && name.endsWith(".jar");
    }

    private MavenDependency readNestedLibraryDependency(InputStream inputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || !name.startsWith("META-INF/maven/")
                        || !name.endsWith("/pom.properties")) {
                    continue;
                }
                Properties properties = new Properties();
                properties.load(zipInputStream);
                String groupId = trimToNull(properties.getProperty("groupId"));
                String artifactId = trimToNull(properties.getProperty("artifactId"));
                String version = trimToNull(properties.getProperty("version"));
                if (groupId != null && artifactId != null && version != null) {
                    return new MavenDependency(groupId, artifactId, version, MavenDependency.Confidence.HIGH);
                }
            }
        }
        return null;
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addEmbeddedPomDependencies(Map<String, MavenDependency> deps,
                                            PomInfo primaryPom,
                                            List<PomInfo> embeddedPomInfos) {
        if (embeddedPomInfos == null || embeddedPomInfos.isEmpty()) {
            return;
        }
        for (PomInfo info : embeddedPomInfos) {
            if (info == null || !info.hasCoordinates() || sameCoordinates(info, primaryPom)) {
                continue;
            }
            MavenDependency dep = new MavenDependency(
                    info.getGroupId(),
                    info.getArtifactId(),
                    info.getVersion(),
                    MavenDependency.Confidence.HIGH
            );
            deps.putIfAbsent(dep.getKey(), dep);
        }
    }

    private boolean sameCoordinates(PomInfo left, PomInfo right) {
        if (left == null || right == null) {
            return false;
        }
        return equals(left.getGroupId(), right.getGroupId())
                && equals(left.getArtifactId(), right.getArtifactId());
    }

    private boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean isResolvableManifestHint(MavenDependency dep) {
        if (dep == null) {
            return false;
        }
        return isKnown(dep.getGroupId()) && isKnown(dep.getVersion());
    }

    private boolean isKnown(String value) {
        return value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private boolean hasConcreteVersion(String value) {
        return isKnown(value) && !value.contains("${");
    }

    private boolean isOwnGroup(MavenCoordinates coord, PomInfo pomInfo) {
        return coord != null
                && pomInfo != null
                && pomInfo.getGroupId() != null
                && coord.getGroupId() != null
                && coord.getGroupId().equals(pomInfo.getGroupId());
    }

    private boolean isExternalPackage(MavenCoordinates coord, PomInfo pomInfo) {
        return !isOwnGroup(coord, pomInfo);
    }

    private boolean isCoveredByEmbeddedDependency(MavenCoordinates coord, Collection<MavenDependency> embeddedDeps) {
        if (coord == null || embeddedDeps == null || embeddedDeps.isEmpty()) {
            return false;
        }
        for (MavenDependency embeddedDep : embeddedDeps) {
            if (embeddedDep == null) {
                continue;
            }
            if (sameGroup(coord, embeddedDep)) {
                return true;
            }
            if (isSpringBootStarter(embeddedDep) && isSpringBootManagedTransitive(coord)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameGroup(MavenCoordinates left, MavenCoordinates right) {
        return left.getGroupId() != null && left.getGroupId().equals(right.getGroupId());
    }

    private boolean isSpringBootStarter(MavenCoordinates coord) {
        return "org.springframework.boot".equals(coord.getGroupId())
                && coord.getArtifactId() != null
                && coord.getArtifactId().startsWith("spring-boot-starter");
    }

    private boolean isSpringBootManagedTransitive(MavenCoordinates coord) {
        String groupId = coord.getGroupId();
        if (groupId == null) {
            return false;
        }
        return groupId.equals("org.springframework")
                || groupId.equals("org.springframework.boot")
                || groupId.equals("org.springframework.security")
                || groupId.equals("org.thymeleaf")
                || groupId.equals("org.attoparser")
                || groupId.equals("ognl")
                || groupId.equals("org.javassist")
                || groupId.equals("org.slf4j")
                || groupId.equals("ch.qos.logback")
                || groupId.equals("org.apache.tomcat.embed")
                || groupId.equals("com.fasterxml.jackson.core")
                || groupId.equals("org.hibernate.validator");
    }

    /**
     * Guess Maven coordinates from a JAR filename.
     * Pattern: {artifactId}-{version}.jar
     */
    public MavenDependency guessFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) return null;

        String name = filename;
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }

        // Find last hyphen followed by a version-like segment
        int lastHyphen = -1;
        for (int i = name.length() - 1; i >= 0; i--) {
            if (name.charAt(i) == '-') {
                String after = name.substring(i + 1);
                if (isVersionLike(after)) {
                    lastHyphen = i;
                    break;
                }
            }
        }

        if (lastHyphen > 0) {
            String artifactId = name.substring(0, lastHyphen);
            String version = name.substring(lastHyphen + 1);
            // Guess groupId from common patterns
            String groupId = guessGroupId(artifactId);
            return new MavenDependency(groupId, artifactId, version, MavenDependency.Confidence.GUESS);
        }

        return new MavenDependency("unknown", name, "unknown", MavenDependency.Confidence.GUESS);
    }

    private boolean isVersionLike(String s) {
        if (s.isEmpty()) return false;
        // Must start with a digit
        if (!Character.isDigit(s.charAt(0))) return false;
        // Contains at least one dot or is purely numeric/dot/hyphen
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c) && c != '.' && c != '-' && c != '_' && !Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    private String guessGroupId(String artifactId) {
        // Common groupId patterns
        Map<String, String> known = new HashMap<>();
        known.put("spring-core", "org.springframework");
        known.put("spring-context", "org.springframework");
        known.put("spring-web", "org.springframework");
        known.put("spring-webmvc", "org.springframework");
        known.put("spring-boot", "org.springframework.boot");
        known.put("spring-security-core", "org.springframework.security");
        known.put("guava", "com.google.guava");
        known.put("gson", "com.google.code.gson");
        known.put("commons-lang3", "org.apache.commons");
        known.put("commons-io", "commons-io");
        known.put("httpclient", "org.apache.httpcomponents");
        known.put("httpcore", "org.apache.httpcomponents");
        known.put("jackson-core", "com.fasterxml.jackson.core");
        known.put("jackson-databind", "com.fasterxml.jackson.core");
        known.put("jackson-annotations", "com.fasterxml.jackson.core");
        known.put("log4j-core", "org.apache.logging.log4j");
        known.put("logback-classic", "ch.qos.logback");
        known.put("slf4j-api", "org.slf4j");
        known.put("fastjson", "com.alibaba");
        known.put("shiro-core", "org.apache.shiro");
        known.put("netty-all", "io.netty");
        known.put("okhttp", "com.squareup.okhttp3");
        known.put("mybatis", "org.mybatis");
        known.put("hibernate-core", "org.hibernate");
        known.put("javax.servlet-api", "javax.servlet");
        known.put("junit", "junit");
        known.put("mockito-core", "org.mockito");

        String gid = known.get(artifactId);
        if (gid != null) return gid;

        // Generic fallback: prefix with unknown
        return "unknown";
    }

    public int detectJavaVersion(JarFile jarFile) {
        int maxMajor = 52; // default Java 8
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class") && !entry.getName().contains("$")
                    && !entry.getName().endsWith("module-info.class")) {
                try (InputStream is = jarFile.getInputStream(entry)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        bos.write(buf, 0, len);
                    }
                    byte[] bytes = bos.toByteArray();
                    int major = classFileScanner.getMajorVersion(bytes);
                    if (major > maxMajor) maxMajor = major;
                } catch (IOException ignored) {
                }
            }
        }

        return ClassFileUtils.majorVersionToJava(maxMajor);
    }
}
