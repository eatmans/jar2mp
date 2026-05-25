package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.RuntimeLaunchPlan;
import com.z0fsec.jar2mp.model.StartupFinding;

import java.io.File;
import java.util.Collection;

public class RuntimeLaunchPlanner {

    public RuntimeLaunchPlan plan(File artifact, JarAnalysisResult analysis) {
        ManifestInfo manifest = analysis == null ? null : analysis.getManifestInfo();
        String mainClass = trimToNull(manifest == null ? null : manifest.getMainClass());
        String startClass = trimToNull(manifest == null ? null : manifest.getAllEntries().get("Start-Class"));
        String startupMain = firstStartupMainClass(analysis);
        boolean war = isWar(artifact, analysis);

        if (startClass != null && isBootWarLauncher(mainClass)) {
            return supported(RuntimeLaunchPlan.LaunchType.EXECUTABLE_WAR, startClass,
                    "manifest Start-Class", "Spring Boot executable WAR can be launched with java -jar.");
        }
        if (startClass != null && isBootJarLauncher(mainClass)) {
            return supported(RuntimeLaunchPlan.LaunchType.EXECUTABLE_JAR, startClass,
                    "manifest Start-Class", "Spring Boot executable JAR can be launched with java -jar.");
        }
        if (war) {
            return unsupported(RuntimeLaunchPlan.LaunchType.STANDARD_WAR, startupMain, "startup evidence",
                    "Standard WAR requires a servlet container and is not a direct java -jar smoke target.");
        }
        if (mainClass != null) {
            return supported(RuntimeLaunchPlan.LaunchType.EXECUTABLE_JAR, mainClass,
                    "manifest Main-Class", "Manifest Main-Class can be launched with java -jar.");
        }
        if (startupMain != null) {
            return unsupported(RuntimeLaunchPlan.LaunchType.THIN_JAR, startupMain, "startup evidence",
                    "Startup evidence found but artifact has no runnable manifest or complete runtime classpath.");
        }
        if (hasApplicationClasses(analysis)) {
            return unsupported(RuntimeLaunchPlan.LaunchType.LIBRARY, null, null,
                    "No application entrypoint was found; treating artifact as a library or non-runnable archive.");
        }
        return unsupported(RuntimeLaunchPlan.LaunchType.UNKNOWN, null, null,
                "Insufficient evidence to choose a runtime launch command.");
    }

    private RuntimeLaunchPlan supported(RuntimeLaunchPlan.LaunchType launchType, String mainClass,
                                        String launchSource, String reason) {
        return new RuntimeLaunchPlan(launchType, RuntimeLaunchPlan.SupportStatus.SUPPORTED,
                mainClass, launchSource, reason);
    }

    private RuntimeLaunchPlan unsupported(RuntimeLaunchPlan.LaunchType launchType, String mainClass,
                                          String launchSource, String reason) {
        return new RuntimeLaunchPlan(launchType, RuntimeLaunchPlan.SupportStatus.UNSUPPORTED,
                mainClass, launchSource, reason);
    }

    private boolean isWar(File artifact, JarAnalysisResult analysis) {
        if (analysis != null && analysis.isWar()) {
            return true;
        }
        return artifact != null && artifact.getName() != null
                && artifact.getName().toLowerCase().endsWith(".war");
    }

    private boolean isBootJarLauncher(String mainClass) {
        return mainClass != null && mainClass.contains("JarLauncher");
    }

    private boolean isBootWarLauncher(String mainClass) {
        return mainClass != null && mainClass.contains("WarLauncher");
    }

    private boolean hasApplicationClasses(JarAnalysisResult analysis) {
        return analysis != null && analysis.getClassFiles() != null && !analysis.getClassFiles().isEmpty();
    }

    private String firstStartupMainClass(JarAnalysisResult analysis) {
        if (analysis == null) {
            return null;
        }
        Collection<StartupFinding> findings = analysis.getStartupFindings();
        if (findings == null) {
            return null;
        }
        for (StartupFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            String mainClass = trimToNull(finding.getMainClass());
            if (mainClass != null) {
                return mainClass;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
