package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.model.VerificationError;
import com.z0fsec.jar2mp.model.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestorationScorerTest {

    @TempDir
    Path tempDir;

    @Test
    void combinesStaticRuntimeAndVerificationSignalsIntoAWeightedScore() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        analysis.getDecompileFindings().add(new DecompileFinding("demo/App.class", null, null));
        analysis.getResourceFindings().add(new ResourceFinding("application.yml",
                ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml",
                "config"));
        analysis.getResourceFindings().add(new ResourceFinding("static/app.js",
                ResourceFinding.Category.FRONTEND_ASSET,
                "src/main/resources/static/app.js",
                "asset"));
        analysis.getResourceFindings().add(new ResourceFinding("templates/home.html",
                ResourceFinding.Category.TEMPLATE,
                "src/main/resources/templates/home.html",
                "template"));
        ResourceFinding nestedLibrary = new ResourceFinding("BOOT-INF/lib/lib.jar",
                ResourceFinding.Category.NESTED_LIBRARY,
                "target/original-libs/BOOT-INF/lib/lib.jar",
                "nested library");
        nestedLibrary.setCopyStatus(ResourceFinding.CopyStatus.ARCHIVED);
        nestedLibrary.setActualTargetPath("target/original-libs/BOOT-INF/lib/lib.jar");
        analysis.getResourceFindings().add(nestedLibrary);

        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("resource", "demo.App", "getResourceAsStream", "application.yml", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("file", "demo.App", "newInputStream", "/tmp/input.txt", "main",
                        Arrays.asList("demo.App.main"))
        ));

        RestorationScore score = new RestorationScorer().score(analysis, traceResult, null);

        assertEquals(88, score.getOverall());
        assertEquals(100, score.getBreakdown().get("source").intValue());
        assertEquals(100, score.getBreakdown().get("resource").intValue());
        assertEquals(100, score.getBreakdown().get("runtime").intValue());
        assertEquals(40, score.getBreakdown().get("verification").intValue());
        assertTrue(score.getGaps().stream().noneMatch(g -> "nested_library".equals(g.getCategory())));
    }

    @Test
    void metaInfRuntimeFilesDoNotLowerResourceFidelityForGeneratedMavenProjects() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getResourceFindings().add(new ResourceFinding("BOOT-INF/classes/application.yml",
                ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml",
                "config"));
        analysis.getResourceFindings().add(new ResourceFinding("META-INF/MANIFEST.MF",
                ResourceFinding.Category.META_INF_RUNTIME,
                "(skipped)",
                "runtime metadata"));

        RestorationScore score = new RestorationScorer().score(analysis, null, null);

        assertEquals(100, score.getBreakdown().get("resource").intValue());
        assertTrue(score.getGaps().stream().noneMatch(g -> "meta_inf_runtime".equals(g.getCategory())));
    }

    @Test
    void resourceScoreUsesActualCopyFailures() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        ResourceFinding copied = new ResourceFinding("BOOT-INF/classes/static/app.js",
                ResourceFinding.Category.FRONTEND_ASSET,
                "src/main/resources/static/app.js",
                "Copied to static/app.js.");
        copied.setCopyStatus(ResourceFinding.CopyStatus.COPIED);
        copied.setActualTargetPath("static/app.js");
        analysis.getResourceFindings().add(copied);

        ResourceFinding failed = new ResourceFinding("static/app.js",
                ResourceFinding.Category.FRONTEND_ASSET,
                "src/main/resources/static/app.js",
                "legacy note says copied");
        failed.setCopyStatus(ResourceFinding.CopyStatus.SKIPPED);
        failed.setCopyFailureReason("Output path collision: static/app.js");
        analysis.getResourceFindings().add(failed);

        RestorationScore score = new RestorationScorer().score(analysis, null, null);

        assertEquals(50, score.getBreakdown().get("resource").intValue());
        assertTrue(score.getGaps().stream().anyMatch(g ->
                "frontend_asset".equals(g.getCategory())
                        && "static/app.js".equals(g.getDetail())));
    }

    @Test
    void archivedNestedLibrariesDoNotLowerTheOverallRestorationScore() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        ResourceFinding nestedLibrary = new ResourceFinding("WEB-INF/lib/private.jar",
                ResourceFinding.Category.NESTED_LIBRARY,
                "target/original-libs/WEB-INF/lib/private.jar",
                "Archived at target/original-libs/WEB-INF/lib/private.jar.");
        nestedLibrary.setCopyStatus(ResourceFinding.CopyStatus.ARCHIVED);
        nestedLibrary.setActualTargetPath("target/original-libs/WEB-INF/lib/private.jar");
        analysis.getResourceFindings().add(nestedLibrary);
        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("resource", "demo.App", "getResourceAsStream",
                        "application.yml", "main", Arrays.asList("demo.App.main"))));
        VerificationResult verification = new VerificationResult();
        verification.setExitCode(0);
        verification.setFailureType("NONE");

        RestorationScore score = new RestorationScorer().score(analysis, traceResult, verification);

        assertEquals(100, score.getOverall());
        assertEquals(100, score.getBreakdown().get("resource").intValue());
        assertTrue(score.getGaps().stream().noneMatch(g -> "nested_library".equals(g.getCategory())));
    }

    @Test
    void runtimeScoreUsesStaticBytecodeExpectationsInsteadOfAllTraceKinds() throws Exception {
        Path jar = compileJar("demo.TraceExpectations",
                "package demo;\n" +
                        "public class TraceExpectations {\n" +
                        "  public void run() throws Exception {\n" +
                        "    Class.forName(\"java.lang.String\");\n" +
                        "    TraceExpectations.class.getResourceAsStream(\"/application.yml\");\n" +
                        "  }\n" +
                        "}\n");
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setSourceFile(jar.toFile());
        analysis.getClassFiles().add("demo/TraceExpectations.class");
        analysis.getDecompileFindings().add(new DecompileFinding("demo/TraceExpectations.class", null, null));
        analysis.getResourceFindings().add(new ResourceFinding("application.yml",
                ResourceFinding.Category.CONFIG,
                "src/main/resources/application.yml",
                "config"));

        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("reflection", "demo.TraceExpectations", "Class.forName",
                        "java.lang.String", "main", Arrays.asList("demo.TraceExpectations.run")),
                new RuntimeTraceEvent("resource", "demo.TraceExpectations", "getResourceAsStream",
                        "/application.yml", "main", Arrays.asList("demo.TraceExpectations.run"))
        ));

        RestorationScore score = new RestorationScorer().score(analysis, traceResult, null);

        assertEquals(100, score.getBreakdown().get("runtime").intValue());
        assertTrue(score.getGaps().stream().noneMatch(g -> "file".equals(g.getCategory())));
        assertTrue(score.getGaps().stream().noneMatch(g -> "socket".equals(g.getCategory())));
    }

    @Test
    void runtimeScoreReportsMissingExpectedStaticKind() throws Exception {
        Path jar = compileJar("demo.TraceExpectations",
                "package demo;\n" +
                        "public class TraceExpectations {\n" +
                        "  public void run() throws Exception {\n" +
                        "    Class.forName(\"java.lang.String\");\n" +
                        "    TraceExpectations.class.getResourceAsStream(\"/application.yml\");\n" +
                        "  }\n" +
                        "}\n");
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.setSourceFile(jar.toFile());
        analysis.getClassFiles().add("demo/TraceExpectations.class");
        analysis.getDecompileFindings().add(new DecompileFinding("demo/TraceExpectations.class", null, null));

        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("reflection", "demo.TraceExpectations", "Class.forName",
                        "java.lang.String", "main", Arrays.asList("demo.TraceExpectations.run"))
        ));

        RestorationScore score = new RestorationScorer().score(analysis, traceResult, null);

        assertEquals(50, score.getBreakdown().get("runtime").intValue());
        assertTrue(score.getGaps().stream().anyMatch(g -> "resource".equals(g.getCategory())));
        assertTrue(score.getGaps().stream().noneMatch(g -> "file".equals(g.getCategory())));
        assertTrue(score.getGaps().stream().noneMatch(g -> "socket".equals(g.getCategory())));
    }

    @Test
    void innerClassesDoNotLowerSourceFidelityWhenOuterClassSourceIsRestored() {
        JarAnalysisResult analysis = new JarAnalysisResult();
        analysis.getClassFiles().add("demo/App.class");
        analysis.getClassFiles().add("demo/App$Inner.class");
        analysis.getDecompileFindings().add(new DecompileFinding("demo/App.class", null, null));

        RestorationScore score = new RestorationScorer().score(analysis, null, null);

        assertEquals(100, score.getBreakdown().get("source").intValue());
    }

    @Test
    void verificationGapIncludesParsedErrorCategories() {
        VerificationResult verification = new VerificationResult();
        verification.setExitCode(1);
        verification.setFailureType("COMPILATION_ERROR");
        verification.getErrors().add(verificationError(VerificationErrorParser.MISSING_SYMBOL));
        verification.getErrors().add(verificationError(VerificationErrorParser.MISSING_SYMBOL));
        verification.getErrors().add(verificationError(VerificationErrorParser.GENERIC_INFERENCE));

        RestorationScore score = new RestorationScorer().score(new JarAnalysisResult(), null, verification);

        assertEquals(0, score.getBreakdown().get("verification").intValue());
        assertTrue(score.getGaps().stream().anyMatch(g ->
                "verification_errors".equals(g.getCategory())
                        && g.getDetail().contains("MISSING_SYMBOL=2")
                        && g.getDetail().contains("GENERIC_INFERENCE=1")));
    }

    private VerificationError verificationError(String category) {
        VerificationError error = new VerificationError();
        error.setCategory(category);
        return error;
    }

    private Path compileJar(String className, String source) throws Exception {
        Path sourceDir = tempDir.resolve("src");
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Path sourceFile = sourceDir.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));

        int result = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, result);

        Path jar = tempDir.resolve(className.substring(className.lastIndexOf('.') + 1) + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            String classEntry = className.replace('.', '/') + ".class";
            out.putNextEntry(new JarEntry(classEntry));
            out.write(Files.readAllBytes(classesDir.resolve(classEntry)));
            out.closeEntry();
        }
        return jar;
    }
}
