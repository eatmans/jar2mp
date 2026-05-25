package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ManifestInfo;
import com.z0fsec.jar2mp.model.RuntimeLaunchPlan;
import com.z0fsec.jar2mp.model.StartupFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeLaunchPlannerTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesSpringBootExecutableJar() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        ManifestInfo manifest = new ManifestInfo();
        manifest.setMainClass("org.springframework.boot.loader.launch.JarLauncher");
        manifest.addEntry("Start-Class", "com.example.Application");
        analysis.setManifestInfo(manifest);

        RuntimeLaunchPlan plan = new RuntimeLaunchPlanner().plan(tempDir.resolve("app.jar").toFile(), analysis);

        assertEquals(RuntimeLaunchPlan.LaunchType.EXECUTABLE_JAR, plan.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.SUPPORTED, plan.getSupportStatus());
        assertEquals("com.example.Application", plan.getMainClass());
        assertEquals("manifest Start-Class", plan.getLaunchSource());
    }

    @Test
    void classifiesSpringBootExecutableWar() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        ManifestInfo manifest = new ManifestInfo();
        manifest.setMainClass("org.springframework.boot.loader.launch.WarLauncher");
        manifest.addEntry("Start-Class", "com.example.WarApplication");
        analysis.setManifestInfo(manifest);

        RuntimeLaunchPlan plan = new RuntimeLaunchPlanner().plan(tempDir.resolve("app.war").toFile(), analysis);

        assertEquals(RuntimeLaunchPlan.LaunchType.EXECUTABLE_WAR, plan.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.SUPPORTED, plan.getSupportStatus());
        assertEquals("com.example.WarApplication", plan.getMainClass());
    }

    @Test
    void classifiesThinJarWithoutMainClass() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getStartupFindings().add(new StartupFinding("Spring Boot", "com.example.ThinApplication"));
        analysis.getClassFiles().add("com/example/ThinApplication.class");

        RuntimeLaunchPlan plan = new RuntimeLaunchPlanner().plan(tempDir.resolve("thin.jar").toFile(), analysis);

        assertEquals(RuntimeLaunchPlan.LaunchType.THIN_JAR, plan.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.UNSUPPORTED, plan.getSupportStatus());
        assertTrue(plan.getReason().contains("no runnable manifest"));
    }

    @Test
    void classifiesStandardWarWithoutLauncher() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setWar(true);
        analysis.getClassFiles().add("com/example/Servlet.class");

        RuntimeLaunchPlan plan = new RuntimeLaunchPlanner().plan(tempDir.resolve("app.war").toFile(), analysis);

        assertEquals(RuntimeLaunchPlan.LaunchType.STANDARD_WAR, plan.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.UNSUPPORTED, plan.getSupportStatus());
        assertTrue(plan.getReason().contains("servlet container"));
    }

    @Test
    void classifiesLibraryJarWithoutApplicationEntrypoint() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("org/example/library/Utility.class");

        RuntimeLaunchPlan plan = new RuntimeLaunchPlanner().plan(tempDir.resolve("library.jar").toFile(), analysis);

        assertEquals(RuntimeLaunchPlan.LaunchType.LIBRARY, plan.getLaunchType());
        assertEquals(RuntimeLaunchPlan.SupportStatus.UNSUPPORTED, plan.getSupportStatus());
        assertTrue(plan.getReason().contains("No application entrypoint"));
    }
}
