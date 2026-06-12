package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerBridgeTest {

    @Test
    void prefersTheNonStubSourceWhenCandidatesDiffer() {
        DecompilerBridge bridge = new DecompilerBridge(new ProjectConfig(), engines(
                new FixedEngine("cfr", DecompilerEngine.Result.success(
                        "cfr",
                        "// Failed to decompile: demo.Sample\n// stub output\n",
                        5)),
                new FixedEngine("fernflower", DecompilerEngine.Result.success(
                        "fernflower",
                        "package demo;\n\npublic class Sample {}\n",
                        90))
        ));

        DecompilerBridge.DecompileResult result = bridge.decompileDetailed(new byte[]{1, 2, 3}, "demo.Sample");

        assertTrue(result.isSuccess());
        assertEquals("fernflower", result.getSelectedEngine());
        assertNotNull(result.getFallbackReason());
        assertTrue(result.getFallbackReason().toLowerCase().contains("stub"));
        assertFalse(result.getSource().contains("Failed to decompile"));
    }

    @Test
    void reportsFallbackReasonWhenEveryEngineFails() {
        DecompilerBridge bridge = new DecompilerBridge(new ProjectConfig(), engines(
                new FixedEngine("cfr", DecompilerEngine.Result.failure(
                        "cfr",
                        "// Failed to decompile: demo.Sample\n// Error: broken\n",
                        "broken",
                        0)),
                new FixedEngine("fernflower", DecompilerEngine.Result.failure(
                        "fernflower",
                        "// Failed to decompile: demo.Sample\n// Error: broken\n",
                        "broken",
                        0))
        ));

        DecompilerBridge.DecompileResult result = bridge.decompileDetailed(new byte[]{1, 2, 3}, "demo.Sample");

        assertFalse(result.isSuccess());
        assertEquals("cfr", result.getSelectedEngine());
        assertNotNull(result.getFallbackReason());
        assertTrue(result.getFallbackReason().toLowerCase().contains("all engines failed"));
        assertTrue(result.getSource().contains("Failed to decompile"));
    }

    @Test
    void scoresCfrHeaderCommentAsStructuralSource() {
        String source = "/*\n * Decompiled with CFR.\n */\n"
                + "package demo;\n\npublic class Sample {}\n";

        assertEquals(70, DecompilerEngine.scoreSource(source));
    }

    @Test
    void penalizesFernflowerUndecompiledMethodMarkers() {
        String source = "package demo;\n\npublic class Sample {\n"
                + "  String value() {\n"
                + "    // $FF: Couldn't be decompiled\n"
                + "  }\n"
                + "}\n";

        assertTrue(DecompilerEngine.scoreSource(source) < 70);
    }

    @Test
    void penalizesEveryFernflowerUndecompiledMethodMarker() {
        String source = "package demo;\n\npublic class Sample {\n"
                + "  void first() { // $FF: Couldn't be decompiled\n"
                + "  }\n"
                + "  void second() { // $FF: Couldn't be decompiled\n"
                + "  }\n"
                + "}\n";

        assertTrue(DecompilerEngine.scoreSource(source) < 30);
    }

    @Test
    void prefersLowerScoredStructuralSourceOverRepeatedUndecompiledMarkers() {
        String cfrSource = "package demo;\n\npublic class Sample {\n"
                + "  void first() { helper(); }\n"
                + "  void second() { helper(); }\n"
                + "  void helper() {}\n"
                + "}\n";
        String fernflowerSource = "package demo;\n\npublic class Sample {\n"
                + "  void first() { // $FF: Couldn't be decompiled\n"
                + "  }\n"
                + "  void second() { // $FF: Couldn't be decompiled\n"
                + "  }\n"
                + "}\n";
        DecompilerBridge bridge = new DecompilerBridge(new ProjectConfig(), engines(
                new FixedEngine("cfr", DecompilerEngine.Result.success("cfr", cfrSource, 30)),
                new FixedEngine("fernflower", DecompilerEngine.Result.success("fernflower", fernflowerSource, 70))
        ));

        DecompilerBridge.DecompileResult result = bridge.decompileDetailed(new byte[]{1, 2, 3}, "demo.Sample");

        assertEquals("cfr", result.getSelectedEngine());
    }

    @Test
    void penalizesKnownUncompilablePlaceholders() {
        String source = "package demo;\n\npublic class Sample {\n"
                + "  private static final Runnable R = new /* Unavailable Anonymous Inner Class!! */;\n"
                + "  int value(Mode mode) { switch (1.$SwitchMap$demo$Mode[mode.ordinal()]) { default: return 0; } }\n"
                + "}\n";

        assertTrue(DecompilerEngine.scoreSource(source) < 70);
    }

    @Test
    void penalizesUnstructuredCfrOutputMarkers() {
        String source = "package demo;\n\npublic class Sample {\n"
                + "  /*\n"
                + "   * Unable to fully structure code\n"
                + "   * WARNING - void declaration\n"
                + "   * Loose catch block\n"
                + "   */\n"
                + "  void run() { if (true) ** break; }\n"
                + "}\n";

        assertTrue(DecompilerEngine.scoreSource(source) < 40);
    }

    private List<DecompilerEngine> engines(DecompilerEngine... engines) {
        return Arrays.asList(engines);
    }

    private static class FixedEngine implements DecompilerEngine {
        private final String name;
        private final DecompilerEngine.Result result;

        private FixedEngine(String name, DecompilerEngine.Result result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public DecompilerEngine.Result decompile(byte[] classBytes, String className) {
            return result;
        }
    }
}
