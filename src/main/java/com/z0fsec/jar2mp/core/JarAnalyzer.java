package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.ClassFileUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarAnalyzer {

    private final ManifestParser manifestParser = new ManifestParser();
    private final MavenMetadataExtractor metadataExtractor = new MavenMetadataExtractor();
    private final FrameworkDetector frameworkDetector = new FrameworkDetector();
    private final ResourceClassifier resourceClassifier = new ResourceClassifier();
    private final StartupDetector startupDetector = new StartupDetector();
    private final DependencyDetector dependencyDetector;
    private final PackagePrefixDatabase packageDb;
    private final ProjectConfig config;

    public JarAnalyzer(PackagePrefixDatabase packageDb) {
        this(packageDb, null);
    }

    public JarAnalyzer(PackagePrefixDatabase packageDb, ProjectConfig config) {
        this.packageDb = packageDb;
        this.dependencyDetector = new DependencyDetector(packageDb);
        this.config = config;
    }

    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    public JarAnalysisResult analyze(File jarFile, ProgressCallback callback) throws IOException {
        JarAnalysisResult result = new JarAnalysisResult();
        result.setSourceFile(jarFile);

        String fileName = jarFile.getName().toLowerCase();
        result.setWar(fileName.endsWith(".war"));

        try (JarFile jf = new JarFile(jarFile)) {
            int totalEntries = 0;
            long totalSize = 0;
            boolean hasBootApplicationClasses = false;
            boolean hasWebApplicationClasses = false;
            java.util.List<String> rawClassEntries = new java.util.ArrayList<>();
            Enumeration<JarEntry> entries = jf.entries();

            // Phase 1: Categorize entries
            if (callback != null) callback.onProgress("Scanning JAR entries...", 10);

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                totalEntries++;
                totalSize += entry.getSize();

                String name = entry.getName();

                if (name.endsWith(".class")) {
                    rawClassEntries.add(name);
                    if (name.startsWith("BOOT-INF/classes/")) {
                        hasBootApplicationClasses = true;
                    } else if (name.startsWith("WEB-INF/classes/")) {
                        hasWebApplicationClasses = true;
                    }
                } else if (name.startsWith("META-INF/")) {
                    result.getMetaInfFiles().add(name);
                } else if (!entry.isDirectory()) {
                    result.getResourceFiles().add(name);
                }
            }

            result.setTotalEntries(totalEntries);
            result.setTotalSize(totalSize);

            // Phase 2: Parse MANIFEST.MF
            if (callback != null) callback.onProgress("Parsing MANIFEST.MF...", 30);

            Manifest manifest = jf.getManifest();
            ManifestInfo manifestInfo = manifestParser.parse(manifest);
            result.setManifestInfo(manifestInfo);

            // Phase 3: Extract embedded Maven metadata
            if (callback != null) callback.onProgress("Extracting Maven metadata...", 50);

            List<PomInfo> embeddedPomInfos = metadataExtractor.extractAll(jf);
            result.getEmbeddedPomInfos().addAll(embeddedPomInfos);
            PomInfo pomInfo = metadataExtractor.selectPrimary(embeddedPomInfos, jf);
            result.setEmbeddedPomInfo(pomInfo);

            determineCoordinates(result, jarFile);
            populateClassFiles(result, rawClassEntries, hasBootApplicationClasses,
                    hasWebApplicationClasses, manifestInfo, pomInfo, embeddedPomInfos);

            // Phase 4: Detect dependencies
            if (shouldDetectDependencies()) {
                if (callback != null) callback.onProgress("Detecting dependencies...", 70);
                List<MavenDependency> deps = dependencyDetector.detect(jf, manifestInfo, pomInfo,
                        embeddedPomInfos, result.getClassFiles(), result.getClassPathMapping());
                result.getDetectedDependencies().addAll(deps);
            } else if (callback != null) {
                callback.onProgress("Skipping dependency detection...", 70);
            }

            result.getFrameworkFindings().addAll(frameworkDetector.detect(result));
            result.getResourceFindings().addAll(resourceClassifier.classify(result));
            result.getStartupFindings().addAll(startupDetector.detect(result));

            // Phase 5: Determine project coordinates
            if (callback != null) callback.onProgress("Determining project coordinates...", 85);

            // Phase 6: Detect Java version
            if (callback != null) callback.onProgress("Detecting Java version...", 95);

            result.setJavaVersion(dependencyDetector.detectJavaVersion(jf));

            if (callback != null) callback.onProgress("Analysis complete.", 100);
        }

        return result;
    }

    private void populateClassFiles(JarAnalysisResult result,
                                    List<String> rawClassEntries,
                                    boolean hasBootApplicationClasses,
                                    boolean hasWebApplicationClasses,
                                    ManifestInfo manifestInfo,
                                    PomInfo primaryPom,
                                    List<PomInfo> embeddedPomInfos) {
        Set<String> applicationPrefixes = applicationPrefixes(result, manifestInfo, primaryPom);
        Set<String> dependencyPrefixes = dependencyPrefixes(primaryPom, embeddedPomInfos);
        boolean filterEmbeddedDependencies = !hasBootApplicationClasses
                && !hasWebApplicationClasses
                && !applicationPrefixes.isEmpty()
                && !dependencyPrefixes.isEmpty();
        for (String rawClassEntry : rawClassEntries) {
            if (hasBootApplicationClasses && isSpringBootLoaderClass(rawClassEntry)) {
                continue;
            }
            String strippedName = stripClassPathPrefix(rawClassEntry);
            String normalizedName = normalizeVersionedClassPath(strippedName);
            if (filterEmbeddedDependencies) {
                addDatabaseDependencyPrefix(dependencyPrefixes, normalizedName, applicationPrefixes);
                result.getEmbeddedDependencyPrefixes().addAll(dependencyPrefixes);
            }
            if (filterEmbeddedDependencies
                    && startsWithAny(normalizedName, dependencyPrefixes)
                    && !startsWithAny(normalizedName, applicationPrefixes)) {
                result.getSkippedDependencyClassFiles().add(strippedName);
                result.getSkippedDependencyClassReasons().put(strippedName,
                        "Embedded dependency class inferred from Maven metadata.");
                continue;
            }
            result.getClassFiles().add(strippedName);
            if (!strippedName.equals(rawClassEntry)) {
                result.getClassPathMapping().put(strippedName, rawClassEntry);
            }
        }
    }

    private Set<String> applicationPrefixes(JarAnalysisResult result, ManifestInfo manifestInfo, PomInfo primaryPom) {
        Set<String> prefixes = new LinkedHashSet<>();
        addGroupPrefix(prefixes, primaryPom == null ? null : primaryPom.getGroupId());
        addGroupPrefix(prefixes, result.getDetectedGroupId());
        if (manifestInfo != null) {
            addClassPackagePrefix(prefixes, manifestInfo.getMainClass());
            addClassPackagePrefix(prefixes, manifestInfo.getAllEntries().get("Start-Class"));
        }
        return prefixes;
    }

    private Set<String> dependencyPrefixes(PomInfo primaryPom, List<PomInfo> embeddedPomInfos) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (embeddedPomInfos == null || embeddedPomInfos.size() < 2) {
            return prefixes;
        }
        for (PomInfo info : embeddedPomInfos) {
            if (info == null || !info.hasCoordinates() || sameCoordinates(info, primaryPom)) {
                continue;
            }
            addGroupPrefix(prefixes, info.getGroupId());
        }
        return prefixes;
    }

    private void addGroupPrefix(Set<String> prefixes, String groupId) {
        if (!isKnownCoordinateValue(groupId) || "com.unknown".equals(groupId)) {
            return;
        }
        prefixes.add(groupId.replace('.', '/') + "/");
    }

    private void addClassPackagePrefix(Set<String> prefixes, String className) {
        if (className == null || !className.contains(".")) {
            return;
        }
        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        if (isKnownCoordinateValue(packageName)) {
            prefixes.add(packageName.replace('.', '/') + "/");
        }
    }

    private boolean sameCoordinates(PomInfo left, PomInfo right) {
        if (left == null || right == null) {
            return false;
        }
        return equals(left.getGroupId(), right.getGroupId())
                && equals(left.getArtifactId(), right.getArtifactId());
    }

    private boolean startsWithAny(String value, Set<String> prefixes) {
        if (value == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void addDatabaseDependencyPrefix(Set<String> dependencyPrefixes,
                                             String normalizedClassPath,
                                             Set<String> applicationPrefixes) {
        if (packageDb == null || normalizedClassPath == null || !normalizedClassPath.endsWith(".class")) {
            return;
        }
        String packageName = packageName(normalizedClassPath);
        if (packageName == null) {
            return;
        }
        MavenCoordinates coordinates = packageDb.lookup(packageName);
        if (coordinates == null || !hasCoordinatePrefix(dependencyPrefixes, coordinates.getGroupId())) {
            return;
        }
        String matchedPrefix = packageDb.lookupPrefix(packageName);
        if (!isKnownCoordinateValue(matchedPrefix)) {
            return;
        }
        String classPrefix = matchedPrefix.replace('.', '/') + "/";
        if (!startsWithAny(classPrefix, applicationPrefixes)) {
            dependencyPrefixes.add(classPrefix);
        }
    }

    private String packageName(String classPath) {
        int slash = classPath.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        return classPath.substring(0, slash).replace('/', '.');
    }

    private boolean hasCoordinatePrefix(Set<String> prefixes, String groupId) {
        if (!isKnownCoordinateValue(groupId)) {
            return false;
        }
        return prefixes.contains(groupId.replace('.', '/') + "/");
    }

    private String normalizeVersionedClassPath(String classPath) {
        String prefix = "META-INF/versions/";
        if (classPath == null || !classPath.startsWith(prefix)) {
            return classPath;
        }
        int start = prefix.length();
        int slash = classPath.indexOf('/', start);
        if (slash < 0) {
            return classPath;
        }
        return classPath.substring(slash + 1);
    }

    private boolean isKnownCoordinateValue(String value) {
        return value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private void determineCoordinates(JarAnalysisResult result, File jarFile) {
        PomInfo pomInfo = result.getEmbeddedPomInfo();
        ManifestInfo manifest = result.getManifestInfo();

        // Priority 1: Embedded pom.properties
        if (pomInfo != null && pomInfo.hasCoordinates()) {
            result.setDetectedGroupId(pomInfo.getGroupId());
            result.setDetectedArtifactId(pomInfo.getArtifactId());
            result.setDetectedVersion(pomInfo.getVersion());
            return;
        }

        // Priority 2: MANIFEST.MF Implementation-*
        String groupId = null;
        String artifactId = null;
        String version = null;

        if (manifest != null) {
            if (manifest.getImplementationVendorId() != null) {
                groupId = manifest.getImplementationVendorId();
            } else if (manifest.getImplementationVendor() != null) {
                groupId = manifest.getImplementationVendor().toLowerCase().replace(' ', '.');
            }
            artifactId = manifest.getImplementationTitle();
            version = manifest.getImplementationVersion();

            // OSGi fallback
            if (artifactId == null && manifest.getBundleSymbolicName() != null) {
                String bsn = manifest.getBundleSymbolicName();
                // Remove attributes like ;singleton:=true
                int semi = bsn.indexOf(';');
                if (semi > 0) bsn = bsn.substring(0, semi);
                artifactId = bsn.trim();
                if (groupId == null) {
                    int lastDot = artifactId.lastIndexOf('.');
                    if (lastDot > 0) {
                        groupId = artifactId.substring(0, lastDot);
                    }
                }
            }
            if (version == null) {
                version = manifest.getBundleVersion();
            }
        }

        // Priority 3: Filename heuristic
        if (artifactId == null || version == null) {
            String name = jarFile.getName();
            if (name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".war")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            int lastHyphen = -1;
            for (int i = name.length() - 1; i >= 0; i--) {
                if (name.charAt(i) == '-') {
                    String after = name.substring(i + 1);
                    boolean versionLike = !after.isEmpty() && Character.isDigit(after.charAt(0));
                    if (versionLike) {
                        lastHyphen = i;
                        break;
                    }
                }
            }
            if (lastHyphen > 0) {
                if (artifactId == null) artifactId = name.substring(0, lastHyphen);
                if (version == null) version = name.substring(lastHyphen + 1);
            } else {
                if (artifactId == null) artifactId = name;
                if (version == null) version = "1.0-SNAPSHOT";
            }
        }

        if (groupId == null) groupId = "com.unknown";

        result.setDetectedGroupId(groupId);
        result.setDetectedArtifactId(artifactId);
        result.setDetectedVersion(version);
    }

    /**
     * Strip Spring Boot / WAR class path prefixes so that decompiled
     * source files land under src/main/java directly instead of
     * src/main/java/BOOT-INF/classes/ or src/main/java/WEB-INF/classes/.
     */
    private static String stripClassPathPrefix(String entryName) {
        if (entryName.startsWith("BOOT-INF/classes/")) {
            return entryName.substring("BOOT-INF/classes/".length());
        }
        if (entryName.startsWith("WEB-INF/classes/")) {
            return entryName.substring("WEB-INF/classes/".length());
        }
        return entryName;
    }

    private static boolean isSpringBootLoaderClass(String entryName) {
        return entryName != null && entryName.startsWith("org/springframework/boot/loader/");
    }

    private boolean shouldDetectDependencies() {
        return config == null || config.isDetectDependencies();
    }
}
