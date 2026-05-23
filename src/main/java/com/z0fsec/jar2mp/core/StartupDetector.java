package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.FrameworkFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.StartupFinding;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class StartupDetector {

    public List<StartupFinding> detect(JarAnalysisResult result) {
        List<StartupFinding> findings = new ArrayList<>();

        StartupFinding startClass = detectStartClass(result);
        if (startClass != null) {
            findings.add(startClass);
            return findings;
        }

        StartupFinding mainClass = detectMainClass(result);
        if (mainClass != null) {
            findings.add(mainClass);
            return findings;
        }

        StartupFinding springBoot = detectSpringBootFramework(result);
        if (springBoot != null) {
            findings.add(springBoot);
            return findings;
        }

        StartupFinding war = detectWar(result);
        if (war != null) {
            findings.add(war);
            return findings;
        }

        StartupFinding bytecodeMain = detectMainMethod(result);
        if (bytecodeMain != null) {
            findings.add(bytecodeMain);
            return findings;
        }

        StartupFinding unknown = new StartupFinding("Unknown", null);
        unknown.getKnownGaps().add("No startup entrypoint evidence found.");
        findings.add(unknown);
        return findings;
    }

    private StartupFinding detectStartClass(JarAnalysisResult result) {
        ManifestInfo manifest = result.getManifestInfo();
        if (manifest == null) {
            return null;
        }
        String startClass = manifest.getAllEntries().get("Start-Class");
        if (startClass == null || startClass.trim().isEmpty()) {
            return null;
        }

        StartupFinding finding = new StartupFinding("Spring Boot", startClass.trim());
        finding.getCommands().add("mvn spring-boot:run");
        finding.getCommands().add("mvn package");
        finding.getEvidence().add("Manifest Start-Class");
        finding.getKnownGaps().add("Requires Spring Boot Maven plugin or equivalent dependency metadata.");
        return finding;
    }

    private StartupFinding detectMainClass(JarAnalysisResult result) {
        ManifestInfo manifest = result.getManifestInfo();
        if (manifest == null || manifest.getMainClass() == null) {
            return null;
        }

        StartupFinding finding = new StartupFinding("Plain JAR", manifest.getMainClass());
        finding.getCommands().add("mvn exec:java -Dexec.mainClass=" + manifest.getMainClass());
        finding.getCommands().add("mvn package");
        finding.getEvidence().add("Manifest Main-Class");
        return finding;
    }

    private StartupFinding detectSpringBootFramework(JarAnalysisResult result) {
        for (FrameworkFinding frameworkFinding : result.getFrameworkFindings()) {
            if ("Spring Boot".equals(frameworkFinding.getName())) {
                StartupFinding finding = new StartupFinding("Spring Boot", null);
                finding.getCommands().add("mvn spring-boot:run");
                finding.getCommands().add("mvn package");
                finding.getEvidence().add("Spring Boot framework finding");
                finding.getKnownGaps().add("No Start-Class was found; inspect generated sources for the application class.");
                return finding;
            }
        }
        return null;
    }

    private StartupFinding detectWar(JarAnalysisResult result) {
        boolean hasWarFinding = result.isWar();
        for (FrameworkFinding frameworkFinding : result.getFrameworkFindings()) {
            if ("Servlet WAR".equals(frameworkFinding.getName())) {
                hasWarFinding = true;
                break;
            }
        }
        boolean hasWebXml = result.getResourceFiles().contains("WEB-INF/web.xml")
                || result.getMetaInfFiles().contains("WEB-INF/web.xml");
        if (!hasWarFinding && !hasWebXml) {
            return null;
        }

        StartupFinding finding = new StartupFinding("Servlet WAR", null);
        finding.getCommands().add("mvn package");
        finding.getEvidence().add(hasWebXml ? "WEB-INF/web.xml" : "Servlet WAR framework finding");
        finding.getKnownGaps().add("External servlet container is required.");
        return finding;
    }

    private StartupFinding detectMainMethod(JarAnalysisResult result) {
        File sourceFile = result.getSourceFile();
        if (sourceFile == null || !sourceFile.isFile()) {
            return null;
        }

        try (JarFile jarFile = new JarFile(sourceFile)) {
            for (String classPath : result.getClassFiles()) {
                String rawEntryPath = result.getClassPathMapping().get(classPath);
                if (rawEntryPath == null) {
                    rawEntryPath = classPath;
                }
                JarEntry entry = jarFile.getJarEntry(rawEntryPath);
                if (entry == null) {
                    continue;
                }
                BytecodeFingerprint fingerprint;
                try {
                    fingerprint = BytecodeFingerprint.fromClassFile(readAllBytes(jarFile, entry));
                } catch (RuntimeException e) {
                    continue;
                }
                if (fingerprint.getMethodsByKey().containsKey("main([Ljava/lang/String;)V")) {
                    String mainClass = fingerprint.getClassName().replace('/', '.');
                    StartupFinding finding = new StartupFinding("Plain JAR", mainClass);
                    finding.getCommands().add("mvn exec:java -Dexec.mainClass=" + mainClass);
                    finding.getCommands().add("mvn package");
                    finding.getEvidence().add("main(String[]) bytecode");
                    return finding;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private byte[] readAllBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
