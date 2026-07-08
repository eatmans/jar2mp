package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.model.RestorationScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.text.StyledDocument;
import javax.swing.table.TableModel;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPanelTest {

    @Test
    void createBuildConfigReflectsAdvancedBuildOptions() throws Exception {
        MainPanel panel = new MainPanel(message -> { });

        checkBox(panel, "byteExactPackageCheckBox").setSelected(true);
        checkBox(panel, "restorePackageRecordsCheckBox").setSelected(true);
        checkBox(panel, "emitRawArtifactCheckBox").setSelected(true);
        checkBox(panel, "verifyBuildCheckBox").setSelected(true);
        textField(panel, "verifyGoalField").setText("package");
        checkBox(panel, "traceRuntimeCheckBox").setSelected(true);
        textField(panel, "traceArgsField").setText("--spring.profiles.active=test --server.port=0");
        spinner(panel, "traceTimeoutSpinner").setValue(45L);
        checkBox(panel, "smokeOnlyCheckBox").setSelected(true);

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertEquals("/tmp/out", config.getOutputDir());
        assertTrue(config.isByteExactPackage());
        assertTrue(config.isRestorePackageRecords());
        assertTrue(config.isEmitRawArtifact());
        assertTrue(config.isVerifyBuild());
        assertEquals("package", config.getVerifyGoal());
        assertTrue(config.isTraceRuntime());
        assertEquals(Arrays.asList("--spring.profiles.active=test", "--server.port=0"), config.getTraceArgs());
        assertEquals(45L, config.getTraceTimeoutSeconds());
        assertTrue(config.isSmokeOnly());
    }

    @Test
    void createBuildConfigKeepsAdvancedOptionsDisabledByDefault() {
        MainPanel panel = new MainPanel(message -> { });

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertEquals("/tmp/out", config.getOutputDir());
        assertFalse(config.isByteExactPackage());
        assertFalse(config.isRestorePackageRecords());
        assertFalse(config.isEmitRawArtifact());
        assertFalse(config.isVerifyBuild());
        assertEquals("compile", config.getVerifyGoal());
        assertFalse(config.isTraceRuntime());
        assertTrue(config.getTraceArgs().isEmpty());
        assertEquals(120L, config.getTraceTimeoutSeconds());
        assertFalse(config.isSmokeOnly());
    }

    @Test
    void byteExactBuildConfigEnablesRawArtifactAndPackageGoal() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        checkBox(panel, "byteExactPackageCheckBox").setSelected(true);
        checkBox(panel, "emitRawArtifactCheckBox").setSelected(false);
        textField(panel, "verifyGoalField").setText("compile");

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertTrue(config.isByteExactPackage());
        assertTrue(config.isEmitRawArtifact());
        assertEquals("package", config.getVerifyGoal());
    }

    @Test
    void restorePackageRecordsBuildConfigUsesPackageGoalWithoutRawArtifact() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        checkBox(panel, "restorePackageRecordsCheckBox").setSelected(true);
        checkBox(panel, "emitRawArtifactCheckBox").setSelected(false);
        textField(panel, "verifyGoalField").setText("compile");

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertTrue(config.isRestorePackageRecords());
        assertFalse(config.isByteExactPackage());
        assertFalse(config.isEmitRawArtifact());
        assertEquals("package", config.getVerifyGoal());
    }

    @Test
    void smokeOnlyBuildConfigEnablesRuntimeTrace() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        checkBox(panel, "traceRuntimeCheckBox").setSelected(false);
        checkBox(panel, "smokeOnlyCheckBox").setSelected(true);

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertTrue(config.isSmokeOnly());
        assertTrue(config.isTraceRuntime());
    }

    @Test
    void createBuildConfigParsesQuotedTraceArgs() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        textField(panel, "traceArgsField").setText("--name \"two words\" 'single value' escaped\\ space");

        ProjectConfig config = panel.createBuildConfig("/tmp/out");

        assertEquals(Arrays.asList("--name", "two words", "single value", "escaped space"),
                config.getTraceArgs());
    }

    @Test
    void generatePomUsesAdvancedBuildOptions() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        setCurrentResult(panel);
        textField(panel, "outputDirField").setText("/tmp/out");
        checkBox(panel, "byteExactPackageCheckBox").setSelected(true);
        checkBox(panel, "restorePackageRecordsCheckBox").setSelected(true);
        checkBox(panel, "emitRawArtifactCheckBox").setSelected(true);
        checkBox(panel, "verifyBuildCheckBox").setSelected(true);
        textField(panel, "verifyGoalField").setText("package");

        invokeDoGeneratePom(panel);

        ProjectConfig config = (ProjectConfig) field(panel, "currentConfig");
        assertTrue(config.isByteExactPackage());
        assertTrue(config.isRestorePackageRecords());
        assertTrue(config.isEmitRawArtifact());
        assertTrue(config.isVerifyBuild());
        assertEquals("package", config.getVerifyGoal());
    }

    @Test
    void appendReportPathsIncludesByteLevelFidelityReports(@TempDir Path outputDir) throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        Files.createDirectories(outputDir.resolve("target/raw-artifact"));
        Files.createDirectories(outputDir.resolve("target/byte-exact-package-check"));
        Files.createDirectories(outputDir.resolve("target/package-record-restore-check"));
        Files.writeString(outputDir.resolve("target/raw-artifact/artifact-fidelity-report.md"), "");
        Files.writeString(outputDir.resolve("target/raw-artifact/artifact-fidelity-summary.csv"), "");
        Files.writeString(outputDir.resolve("target/byte-exact-package-check/artifact-fidelity-report.md"), "");
        Files.writeString(outputDir.resolve("target/byte-exact-package-check/artifact-fidelity-summary.csv"), "");
        Files.writeString(outputDir.resolve("target/package-record-restore-check/artifact-fidelity-report.md"), "");
        Files.writeString(outputDir.resolve("target/package-record-restore-check/artifact-fidelity-summary.csv"), "");

        invokeAppendReportPaths(panel, outputDir.toFile());

        String output = logDocumentText(panel);
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertTrue(output.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertTrue(output.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void appendReportPathsIncludesExpectedByteLevelReportsFromConfig(@TempDir Path outputDir) throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setEmitRawArtifact(true);
        config.setByteExactPackage(true);
        config.setRestorePackageRecords(true);
        setField(panel, "currentConfig", config);

        invokeAppendReportPaths(panel, outputDir.toFile());

        String output = logDocumentText(panel);
        assertTrue(output.contains("verification-report.md"));
        assertTrue(output.contains("verification-errors.md"));
        assertTrue(output.contains("source-rebuild-fidelity-report.md"));
        assertTrue(output.contains("source-rebuild-fidelity-summary.csv"));
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertTrue(output.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertFalse(output.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertFalse(output.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void appendReportPathsDoesNotListPackageVerificationReportsWithoutVerification(
            @TempDir Path outputDir) throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        ProjectConfig config = new ProjectConfig();
        config.setByteExactPackage(true);
        config.setRestorePackageRecords(true);
        setField(panel, "currentConfig", config);

        invokeAppendReportPaths(panel, outputDir.toFile());

        String output = logDocumentText(panel);
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/raw-artifact/artifact-fidelity-summary.csv"));
        assertFalse(output.contains("verification-report.md"));
        assertFalse(output.contains("verification-errors.md"));
        assertFalse(output.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertFalse(output.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertFalse(output.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertFalse(output.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void appendReportPathsListsPackageRecordReportsForStandalonePackageRecordMode(
            @TempDir Path outputDir) throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        ProjectConfig config = new ProjectConfig();
        config.setVerifyBuild(true);
        config.setVerifyGoal("package");
        config.setRestorePackageRecords(true);
        setField(panel, "currentConfig", config);

        invokeAppendReportPaths(panel, outputDir.toFile());

        String output = logDocumentText(panel);
        assertTrue(output.contains("verification-report.md"));
        assertTrue(output.contains("verification-errors.md"));
        assertFalse(output.contains("target/byte-exact-package-check/artifact-fidelity-report.md"));
        assertFalse(output.contains("target/byte-exact-package-check/artifact-fidelity-summary.csv"));
        assertTrue(output.contains("target/package-record-restore-check/artifact-fidelity-report.md"));
        assertTrue(output.contains("target/package-record-restore-check/artifact-fidelity-summary.csv"));
    }

    @Test
    void refreshAnalysisIfSelectedShowsPostBuildRestorationScore() throws Exception {
        MainPanel panel = new MainPanel(message -> { });
        File jarFile = new File("sample.jar");
        fileListModel(panel).addElement(jarFile);
        fileJList(panel).setSelectedValue(jarFile, true);

        JarAnalysisResult result = new JarAnalysisResult();
        result.setSourceFile(jarFile);
        result.setDetectedGroupId("demo");
        result.setDetectedArtifactId("sample");
        result.setDetectedVersion("1.0");
        RestorationScore score = new RestorationScore();
        score.setOverall(100);
        score.putBucket("source", 100);
        score.putBucket("resource", 100);
        score.putBucket("runtime", 100);
        score.putBucket("verification", 100);
        result.setRestorationScore(score);

        invokeRefreshAnalysisIfSelected(panel, jarFile, result);

        assertEquals("100/100 (source=100, resource=100, runtime=100, verification=100)",
                valueFor(analysisSummaryModel(panel), "恢复评分"));
    }

    private JCheckBox checkBox(MainPanel panel, String name) throws Exception {
        return (JCheckBox) field(panel, name);
    }

    private JTextField textField(MainPanel panel, String name) throws Exception {
        return (JTextField) field(panel, name);
    }

    private JSpinner spinner(MainPanel panel, String name) throws Exception {
        return (JSpinner) field(panel, name);
    }

    private Object field(MainPanel panel, String name) throws Exception {
        Field field = MainPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    @SuppressWarnings("unchecked")
    private DefaultListModel<File> fileListModel(MainPanel panel) throws Exception {
        return (DefaultListModel<File>) field(panel, "fileListModel");
    }

    @SuppressWarnings("unchecked")
    private JList<File> fileJList(MainPanel panel) throws Exception {
        return (JList<File>) field(panel, "fileJList");
    }

    private void setField(MainPanel panel, String name, Object value) throws Exception {
        Field field = MainPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }

    private void invokeDoGeneratePom(MainPanel panel) throws Exception {
        Method method = MainPanel.class.getDeclaredMethod("doGeneratePom");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private void invokeAppendReportPaths(MainPanel panel, File outputDir) throws Exception {
        Method method = MainPanel.class.getDeclaredMethod("appendReportPaths", File.class);
        method.setAccessible(true);
        method.invoke(panel, outputDir);
    }

    private void invokeRefreshAnalysisIfSelected(MainPanel panel, File jarFile, JarAnalysisResult result)
            throws Exception {
        Method method = MainPanel.class.getDeclaredMethod("refreshAnalysisIfSelected",
                File.class, JarAnalysisResult.class);
        method.setAccessible(true);
        method.invoke(panel, jarFile, result);
    }

    private TableModel analysisSummaryModel(MainPanel panel) throws Exception {
        Field analysisPanelField = MainPanel.class.getDeclaredField("analysisPanel");
        analysisPanelField.setAccessible(true);
        AnalysisPanel analysisPanel = (AnalysisPanel) analysisPanelField.get(panel);
        Field summaryTableField = AnalysisPanel.class.getDeclaredField("summaryTable");
        summaryTableField.setAccessible(true);
        JTable table = (JTable) summaryTableField.get(analysisPanel);
        return table.getModel();
    }

    private String valueFor(TableModel model, String key) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (key.equals(model.getValueAt(row, 0))) {
                return String.valueOf(model.getValueAt(row, 1));
            }
        }
        return null;
    }

    private String logDocumentText(MainPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Field field = BasePanel.class.getDeclaredField("logDocument");
        field.setAccessible(true);
        StyledDocument document = (StyledDocument) field.get(panel);
        return document.getText(0, document.getLength());
    }

    private void setCurrentResult(MainPanel panel) throws Exception {
        JarAnalysisResult result = new JarAnalysisResult();
        result.setDetectedGroupId("com.example");
        result.setDetectedArtifactId("sample");
        result.setDetectedVersion("1.0");
        result.setSourceFile(new File("sample-1.0.jar"));
        setField(panel, "currentResult", result);
    }
}
