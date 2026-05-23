package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.MavenDependency;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;

public class DependencyEditorPanel extends BasePanel {

    private DefaultTableModel depModel;
    private JTable depTable;
    private JarAnalysisResult currentResult;

    public DependencyEditorPanel(Consumer<String> logConsumer) {
        super(logConsumer);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(this::onAdd);
        JButton removeBtn = new JButton("移除");
        removeBtn.addActionListener(this::onRemove);
        JButton selectAllBtn = new JButton("全选");
        selectAllBtn.addActionListener(e -> setAllIncluded(true));
        JButton deselectAllBtn = new JButton("取消全选");
        deselectAllBtn.addActionListener(e -> setAllIncluded(false));
        toolbar.add(addBtn);
        toolbar.add(removeBtn);
        toolbar.add(selectAllBtn);
        toolbar.add(deselectAllBtn);
        contentPanel.add(toolbar, BorderLayout.NORTH);

        // Table
        depModel = new DefaultTableModel(
                new Object[]{"包含", "GroupId", "ArtifactId", "版本", "范围", "来源"}, 0) {
            Class<?>[] types = {Boolean.class, String.class, String.class, String.class, String.class, String.class};

            public Class<?> getColumnClass(int col) { return types[col]; }

            public boolean isCellEditable(int row, int col) { return col != 5; }
        };
        depTable = new JTable(depModel);
        depTable.setRowHeight(24);
        depTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        depTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        depTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        depTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        depTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        depTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        contentPanel.add(new JScrollPane(depTable), BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
    }

    public void updateDependencies(JarAnalysisResult result) {
        currentResult = result;
        depModel.setRowCount(0);
        if (result == null) return;

        List<MavenDependency> deps = result.getDetectedDependencies();
        for (MavenDependency dep : deps) {
            depModel.addRow(new Object[]{
                    dep.isIncluded(),
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    dep.getScope() != null ? dep.getScope() : "compile",
                    dep.getConfidence() != null ? dep.getConfidence().getLabel() : "?"
            });
        }
        appendSuccess("已加载 " + deps.size() + " 个依赖");
    }

    public void syncToResult() {
        if (currentResult == null) return;
        List<MavenDependency> deps = currentResult.getDetectedDependencies();
        for (int i = 0; i < depModel.getRowCount(); i++) {
            MavenDependency dep;
            if (i < deps.size()) {
                dep = deps.get(i);
            } else {
                dep = new MavenDependency();
                dep.setConfidence(MavenDependency.Confidence.MANUAL);
                deps.add(dep);
            }
            dep.setIncluded((Boolean) depModel.getValueAt(i, 0));
            dep.setGroupId((String) depModel.getValueAt(i, 1));
            dep.setArtifactId((String) depModel.getValueAt(i, 2));
            dep.setVersion((String) depModel.getValueAt(i, 3));
            dep.setScope((String) depModel.getValueAt(i, 4));
        }
        while (deps.size() > depModel.getRowCount()) {
            deps.remove(deps.size() - 1);
        }
    }

    private void onAdd(ActionEvent e) {
        depModel.addRow(new Object[]{true, "com.example", "artifact", "1.0", "compile", "Manual"});
    }

    private void onRemove(ActionEvent e) {
        int[] rows = depTable.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--) {
            depModel.removeRow(rows[i]);
            if (currentResult != null && rows[i] < currentResult.getDetectedDependencies().size()) {
                currentResult.getDetectedDependencies().remove(rows[i]);
            }
        }
    }

    private void setAllIncluded(boolean included) {
        for (int i = 0; i < depModel.getRowCount(); i++) {
            depModel.setValueAt(included, i, 0);
        }
    }

    public void clearData() {
        currentResult = null;
        depModel.setRowCount(0);
    }
}
