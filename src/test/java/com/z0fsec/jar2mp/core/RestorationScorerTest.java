package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;
import com.z0fsec.jar2mp.model.RestorationScore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestorationScorerTest {

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
        analysis.getResourceFindings().add(new ResourceFinding("BOOT-INF/lib/lib.jar",
                ResourceFinding.Category.NESTED_LIBRARY,
                "(skipped)",
                "nested library"));

        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("resource", "demo.App", "getResourceAsStream", "application.yml", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("file", "demo.App", "newInputStream", "/tmp/input.txt", "main",
                        Arrays.asList("demo.App.main"))
        ));

        RestorationScore score = new RestorationScorer().score(analysis, traceResult, null);

        assertEquals(73, score.getOverall());
        assertEquals(100, score.getBreakdown().get("source").intValue());
        assertEquals(75, score.getBreakdown().get("resource").intValue());
        assertEquals(50, score.getBreakdown().get("runtime").intValue());
        assertEquals(40, score.getBreakdown().get("verification").intValue());
        assertTrue(score.getGaps().stream().anyMatch(g -> "reflection".equals(g.getCategory())));
    }
}
