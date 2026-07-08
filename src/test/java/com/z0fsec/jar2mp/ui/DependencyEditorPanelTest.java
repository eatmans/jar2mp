package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.MavenDependency;
import org.junit.jupiter.api.Test;

import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyEditorPanelTest {

    @Test
    void syncToResultPersistsNewManualRowsFromTheTable() throws Exception {
        DependencyEditorPanel panel = new DependencyEditorPanel(message -> { });
        JarAnalysisResult result = new JarAnalysisResult();
        panel.updateDependencies(result);

        DefaultTableModel model = getDependencyModel(panel);
        model.addRow(new Object[]{true, "com.acme", "manual-lib", "1.2.3", "runtime", "Manual"});

        panel.syncToResult();

        assertEquals(1, result.getDetectedDependencies().size());
        MavenDependency dep = result.getDetectedDependencies().get(0);
        assertEquals("com.acme", dep.getGroupId());
        assertEquals("manual-lib", dep.getArtifactId());
        assertEquals("1.2.3", dep.getVersion());
        assertEquals("runtime", dep.getScope());
        assertTrue(dep.isIncluded());
        assertEquals(MavenDependency.Confidence.MANUAL, dep.getConfidence());
    }

    private DefaultTableModel getDependencyModel(DependencyEditorPanel panel) throws Exception {
        Field field = DependencyEditorPanel.class.getDeclaredField("depModel");
        field.setAccessible(true);
        return (DefaultTableModel) field.get(panel);
    }
}
