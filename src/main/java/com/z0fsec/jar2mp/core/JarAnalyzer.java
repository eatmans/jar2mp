package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.ClassFileUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
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
    private final ProjectConfig config;

    public JarAnalyzer(PackagePrefixDatabase packageDb) {
        this(packageDb, null);
    }

    public JarAnalyzer(PackagePrefixDatabase packageDb, ProjectConfig config) {
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
            Enumeration<JarEntry> entries = jf.entries();

            // Phase 1: Categorize entries
            if (callback != null) callback.onProgress("Scanning JAR entries...", 10);

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                totalEntries++;
                totalSize += entry.getSize();

                String name = entry.getName();

                if (name.endsWith(".class")) {
                    String strippedName = stripClassPathPrefix(name);
                    result.getClassFiles().add(strippedName);
                    if (!strippedName.equals(name)) {
                        result.getClassPathMapping().put(strippedName, name);
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

            PomInfo pomInfo = metadataExtractor.extract(jf);
            result.setEmbeddedPomInfo(pomInfo);

            // Phase 4: Detect dependencies
            if (shouldDetectDependencies()) {
                if (callback != null) callback.onProgress("Detecting dependencies...", 70);
                List<MavenDependency> deps = dependencyDetector.detect(jf, manifestInfo, pomInfo);
                result.getDetectedDependencies().addAll(deps);
            } else if (callback != null) {
                callback.onProgress("Skipping dependency detection...", 70);
            }

            result.getFrameworkFindings().addAll(frameworkDetector.detect(result));
            result.getResourceFindings().addAll(resourceClassifier.classify(result));
            result.getStartupFindings().addAll(startupDetector.detect(result));

            // Phase 5: Determine project coordinates
            if (callback != null) callback.onProgress("Determining project coordinates...", 85);

            determineCoordinates(result, jarFile);

            // Phase 6: Detect Java version
            if (callback != null) callback.onProgress("Detecting Java version...", 95);

            result.setJavaVersion(dependencyDetector.detectJavaVersion(jf));

            if (callback != null) callback.onProgress("Analysis complete.", 100);
        }

        return result;
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

    private boolean shouldDetectDependencies() {
        return config == null || config.isDetectDependencies();
    }
}
