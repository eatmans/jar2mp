package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.core.RuntimeSmokeRunner;
import com.z0fsec.jar2mp.core.RuntimeTraceEvent;
import com.z0fsec.jar2mp.core.RuntimeTraceResult;
import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.RestorationScore;
import com.z0fsec.jar2mp.model.VerificationError;
import com.z0fsec.jar2mp.model.VerificationResult;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.table.TableModel;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisPanelTest {

    @Test
    void updateAnalysisShowsBuildRuntimeAndRestorationGates() throws Exception {
        AnalysisPanel panel = new AnalysisPanel(message -> { });
        JarAnalysisResult result = new JarAnalysisResult();
        result.setSourceFile(new File("sample.jar"));
        result.setDetectedGroupId("demo");
        result.setDetectedArtifactId("sample");
        result.setDetectedVersion("1.0");

        VerificationResult verification = new VerificationResult();
        verification.setFailureType("NONE");
        verification.setExitCode(0);
        verification.getErrors().add(new VerificationError());
        verification.getCompileFallbackClassPaths().add("demo/Broken.class");
        result.setVerificationResult(verification);
        result.getDecompileFindings().add(new DecompileFinding("demo/Broken.class",
                "src/main/original-classes/demo/Broken.class", "syntax recovery failed"));
        result.getDecompileFindings().add(new DecompileFinding("demo/Plain.class",
                null, "decompiler failed"));

        RuntimeTraceResult traceResult = new RuntimeTraceResult(Arrays.asList(
                new RuntimeTraceEvent("reflection", "demo.App", "main", "startup", "main",
                        Arrays.asList("demo.App.main")),
                new RuntimeTraceEvent("resource", "demo.App", "getResource", "application.yml", "main",
                        Arrays.asList("demo.App.main"))
        ));
        RuntimeSmokeRunner.SmokeRunResult smokeResult = new RuntimeSmokeRunner.SmokeRunResult();
        smokeResult.setRunStatus("STARTUP_FAILED_EXIT");
        smokeResult.setFailureMessage("Runtime startup failure was detected before non-zero exit.");
        smokeResult.setStdout("APPLICATION FAILED TO START\n"
                + "Caused by: org.redisson.client.RedisConnectionException: "
                + "Unable to connect to Redis server: localhost/127.0.0.1:6379\n");
        smokeResult.setTraceResult(traceResult);
        result.setRuntimeSmokeResult(smokeResult);
        result.setRuntimeTraceResult(traceResult);

        RestorationScore score = new RestorationScore();
        score.setOverall(80);
        score.putBucket("source", 100);
        score.putBucket("resource", 100);
        score.putBucket("runtime", 0);
        score.putBucket("verification", 100);
        score.addGap("runtime_status", "Runtime startup failure was detected before non-zero exit.", 20);
        result.setRestorationScore(score);

        panel.updateAnalysis(result);

        TableModel model = summaryTable(panel).getModel();
        assertEquals("80/100 (source=100, resource=100, runtime=0, verification=100)",
                valueFor(model, "恢复评分"));
        assertEquals("BUILD SUCCESS (NONE, exit 0)", valueFor(model, "构建验证"));
        assertEquals("1", valueFor(model, "构建错误数"));
        assertEquals("1", valueFor(model, "编译回退类数"));
        assertEquals("2", valueFor(model, "反编译失败数"));
        assertEquals("1", valueFor(model, "保留原始 class 数"));
        assertEquals("STARTUP_FAILED_EXIT (events=2)", valueFor(model, "运行状态"));
        assertEquals("Runtime startup failure was detected before non-zero exit.",
                valueFor(model, "运行失败信息"));
        assertEquals("org.redisson.client.RedisConnectionException: "
                        + "Unable to connect to Redis server: localhost/127.0.0.1:6379",
                valueFor(model, "运行失败原因"));
        assertEquals("runtime_status=20", valueFor(model, "剩余缺口"));
    }

    private JTable summaryTable(AnalysisPanel panel) throws Exception {
        Field field = AnalysisPanel.class.getDeclaredField("summaryTable");
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private String valueFor(TableModel model, String key) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (key.equals(model.getValueAt(row, 0))) {
                return String.valueOf(model.getValueAt(row, 1));
            }
        }
        return null;
    }
}
