package com.z0fsec.jar2mp.ui;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.z0fsec.jar2mp.core.JarAnalyzer;
import com.z0fsec.jar2mp.core.PomGenerator;
import com.z0fsec.jar2mp.core.ProjectBuilder;
import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ProjectConfig;
import com.z0fsec.jar2mp.util.IoUtils;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MainPanel extends BasePanel {

    private DefaultListModel<File> fileListModel;
    private JList<File> fileJList;
    private JTextField outputDirField;
    private JTabbedPane tabbedPane;

    private AnalysisPanel analysisPanel;
    private DependencyEditorPanel dependencyPanel;
    private PomPreviewPanel pomPreviewPanel;

    private final Map<File, JarAnalysisResult> resultMap = new LinkedHashMap<>();
    private JarAnalysisResult currentResult;
    private PackagePrefixDatabase packageDb;
    private ProjectConfig currentConfig;

    public MainPanel(Consumer<String> logConsumer) {
        super(logConsumer);
        initUI();
        loadPackageDatabase();
    }

    private void loadPackageDatabase() {
        packageDb = new PackagePrefixDatabase();
        try (InputStream is = getClass().getResourceAsStream("/db/package-mappings.properties")) {
            if (is != null) {
                packageDb.load(is);
                appendSuccess("已加载 " + packageDb.size() + " 条包名映射");
            }
        } catch (Exception e) {
            appendWarning("加载包名映射失败: " + e.getMessage());
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 顶部: 输入区
        add(createInputPanel(), BorderLayout.NORTH);

        // 中间: 标签页
        tabbedPane = new JTabbedPane();

        analysisPanel = new AnalysisPanel(this::appendLog);
        analysisPanel.externalLogWriter = this;

        dependencyPanel = new DependencyEditorPanel(this::appendLog);
        dependencyPanel.externalLogWriter = this;

        pomPreviewPanel = new PomPreviewPanel(this::appendLog);
        pomPreviewPanel.externalLogWriter = this;

        tabbedPane.addTab("分析结果", analysisPanel);
        tabbedPane.addTab("依赖管理", dependencyPanel);
        tabbedPane.addTab("pom.xml 预览", pomPreviewPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // 底部: 日志
        add(createLogPanelWithScroll(), BorderLayout.SOUTH);
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("输入"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: 文件列表 + 右侧控件
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        panel.add(new JLabel("JAR/WAR 文件:"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.gridheight = 3;
        c.fill = GridBagConstraints.BOTH;
        fileListModel = new DefaultListModel<>();
        fileJList = new JList<>(fileListModel);

        // 启用拖拽支持
        enableDragAndDrop();

        // 启用右键菜单
        enableContextMenu();

        fileJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    File f = (File) value;
                    setText(f.getName());
                    setToolTipText(f.getAbsolutePath());
                    if (resultMap.containsKey(f)) {
                        setForeground(isSelected ? Color.WHITE : new Color(0, 128, 0));
                    }
                }
                return this;
            }
        });
        fileJList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    onFileSelected();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(fileJList);
        listScroll.setPreferredSize(new Dimension(300, 80));
        panel.add(listScroll, c);

        // 右侧: 按钮和输出目录
        c.gridx = 2;
        c.weightx = 0;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        JButton addBtn = new JButton("添加文件...");
        addBtn.addActionListener(e -> browseAndAddFiles());
        c.gridy = 0;
        panel.add(addBtn, c);

        JButton addDirBtn = new JButton("添加目录...");
        addDirBtn.addActionListener(e -> browseAndAddDirectory());
        c.gridy = 1;
        panel.add(addDirBtn, c);

        JButton removeBtn = new JButton("移除选中");
        removeBtn.addActionListener(e -> removeSelectedFiles());
        c.gridy = 2;
        panel.add(removeBtn, c);

        // Row 3: 输出目录
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("输出目录:"), c);

        c.gridx = 1;
        c.weightx = 1;
        outputDirField = new JTextField();
        outputDirField.setText(new File(".").getAbsolutePath());
        panel.add(outputDirField, c);

        c.gridx = 2;
        c.weightx = 0;
        JButton outputBrowseBtn = new JButton("选择...");
        outputBrowseBtn.addActionListener(e -> browseOutputDir());
        panel.add(outputBrowseBtn, c);

        // Row 4: 操作按钮
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0;
        JButton analyzeBtn = new JButton("分析全部");
        analyzeBtn.addActionListener(e -> doAnalyzeAll());
        panel.add(analyzeBtn, c);

        c.gridx = 1;
        c.weightx = 0;
        JButton genPomBtn = new JButton("生成 pom.xml");
        genPomBtn.addActionListener(e -> doGeneratePom());
        panel.add(genPomBtn, c);

        c.gridx = 2;
        c.weightx = 0;
        JButton buildBtn = new JButton("构建全部");
        buildBtn.addActionListener(e -> doBuildAll());
        panel.add(buildBtn, c);

        c.gridheight = 1;

        return panel;
    }

    // ========== 文件列表操作 ==========

    private void browseAndAddFiles() {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setMultiSelectionEnabled(true);
        SystemFileChooser.FileNameExtensionFilter filter = new SystemFileChooser.FileNameExtensionFilter("JAR/WAR 文件 (*.jar, *.war)", "jar", "war");
        chooser.setFileFilter(filter);
        if (!fileListModel.isEmpty()) {
            chooser.setCurrentDirectory(fileListModel.get(0).getParentFile());
        }
        if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (!containsFile(f)) {
                    fileListModel.addElement(f);
                }
            }
            appendInfo("已添加 " + chooser.getSelectedFiles().length + " 个文件");
        }
    }

    private void browseAndAddDirectory() {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            File[] jars = dir.listFiles((d, name) ->
                    name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".war"));
            if (jars != null && jars.length > 0) {
                int added = 0;
                for (File f : jars) {
                    if (!containsFile(f)) {
                        fileListModel.addElement(f);
                        added++;
                    }
                }
                appendInfo("从目录添加了 " + added + " 个文件");
            } else {
                appendWarning("目录中未找到 JAR/WAR 文件: " + dir.getAbsolutePath());
            }
        }
    }

    private void removeSelectedFiles() {
        int[] indices = fileJList.getSelectedIndices();
        if (indices.length == 0) {
            appendWarning("请选择要移除的文件");
            return;
        }

        for (int i = indices.length - 1; i >= 0; i--) {
            File f = fileListModel.get(indices[i]);
            resultMap.remove(f);
            pomPreviewPanel.clearCache(f.getName());
            fileListModel.remove(indices[i]);
        }

        currentResult = null;
        if (fileListModel.isEmpty()) {
            analysisPanel.clearData();
            dependencyPanel.clearData();
            pomPreviewPanel.clearAllCache();
            return;
        }

        fileJList.setSelectedIndex(0);
    }

    private boolean containsFile(File f) {
        for (int i = 0; i < fileListModel.size(); i++) {
            if (fileListModel.get(i).getAbsolutePath().equals(f.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private void browseOutputDir() {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ========== 拖拽支持 ==========
    private void enableDragAndDrop() {
        new DropTarget(fileJList, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    Transferable transferable = dtde.getTransferable();

                    // 处理文件拖拽
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                        int addedCount = 0;
                        for (File file : files) {
                            if (file.isDirectory()) {
                                // 如果是目录，扫描其中的 JAR/WAR 文件
                                File[] jars = file.listFiles((d, name) ->
                                        name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".war"));
                                if (jars != null) {
                                    for (File jar : jars) {
                                        if (!containsFile(jar)) {
                                            fileListModel.addElement(jar);
                                            addedCount++;
                                        }
                                    }
                                }
                            } else if (file.getName().toLowerCase().endsWith(".jar")
                                    || file.getName().toLowerCase().endsWith(".war")) {
                                // 如果是 JAR/WAR 文件，直接添加
                                if (!containsFile(file)) {
                                    fileListModel.addElement(file);
                                    addedCount++;
                                }
                            }
                        }

                        if (addedCount > 0) {
                            appendInfo("已添加 " + addedCount + " 个文件");
                        } else {
                            appendWarning("文件已存在或不是有效的 JAR/WAR 文件");
                        }
                    }

                    dtde.dropComplete(true);
                } catch (Exception e) {
                    appendError("拖拽失败: " + e.getMessage());
                    dtde.dropComplete(false);
                }
            }
        }, true);

        // 添加视觉提示
        fileJList.setToolTipText("拖拽 JAR/WAR 文件或目录到此处");
    }

    // ========== 右键菜单 ==========

    private void enableContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        // 如果有选中项，添加移除选项
        JMenuItem removeMenuItem = new JMenuItem("移除选中");
        removeMenuItem.addActionListener(e -> removeSelectedFiles());
        popupMenu.add(removeMenuItem);

        JMenuItem clearMenuItem = new JMenuItem("清空列表");
        clearMenuItem.addActionListener(e -> clearFileList());
        popupMenu.add(clearMenuItem);

        // 监听鼠标事件显示菜单
        fileJList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfTrigger(e, popupMenu);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfTrigger(e, popupMenu);
            }

            private void showPopupIfTrigger(MouseEvent e, JPopupMenu menu) {
                if (e.isPopupTrigger()) {
                    // 如果点击位置没有选中项，先取消选择
                    int index = fileJList.locationToIndex(e.getPoint());
                    if (index < 0 || !fileJList.isSelectedIndex(index)) {
                        fileJList.clearSelection();
                    }
                    menu.show(fileJList, e.getX(), e.getY());
                }
            }
        });
    }

    private void clearFileList() {
        if (fileListModel.isEmpty()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要清空所有文件吗？",
                "确认清空",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // 保存当前编辑
            if (currentResult != null) {
                dependencyPanel.syncToResult();
                pomPreviewPanel.saveCurrentCache();
            }

            // 清空所有数据
            resultMap.clear();
            pomPreviewPanel.clearAllCache();
            fileListModel.clear();
            currentResult = null;

            // 清空面板显示
            analysisPanel.clearData();
            dependencyPanel.clearData();

            appendInfo("已清空文件列表");
        }
    }

    // ========== 文件切换 ==========

    private void onFileSelected() {
        File selected = fileJList.getSelectedValue();
        if (selected == null) return;

        // 保存当前编辑
        if (currentResult != null) {
            dependencyPanel.syncToResult();
            pomPreviewPanel.saveCurrentCache();
        }

        // 加载选中文件的结果
        JarAnalysisResult result = resultMap.get(selected);
        currentResult = result;
        if (result != null) {
            analysisPanel.updateAnalysis(result);
            dependencyPanel.updateDependencies(result);
            pomPreviewPanel.switchToFile(selected.getName());
        } else {
            analysisPanel.clearData();
            dependencyPanel.clearData();
        }
    }

    // ========== 分析 ==========

    private void doAnalyzeAll() {
        if (fileListModel.size() == 0) {
            JOptionPane.showMessageDialog(this, "请先添加 JAR/WAR 文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<File> toAnalyze = new ArrayList<>();
        for (int i = 0; i < fileListModel.size(); i++) {
            File f = fileListModel.get(i);
            if (!resultMap.containsKey(f)) {
                toAnalyze.add(f);
            }
        }

        if (toAnalyze.isEmpty()) {
            appendInfo("所有文件已分析过，无需重复分析");
            return;
        }

        appendInfo("开始分析 " + toAnalyze.size() + " 个文件...");

        new Thread(() -> {
            JarAnalyzer analyzer = new JarAnalyzer(packageDb);
            int total = toAnalyze.size();
            int done = 0;

            for (File jarFile : toAnalyze) {
                try {
                    JarAnalysisResult result = analyzer.analyze(jarFile, (message, percent) -> {
                        SwingUtilities.invokeLater(() -> appendDebug(message));
                    });

                    final int d = ++done;
                    SwingUtilities.invokeLater(() -> {
                        resultMap.put(jarFile, result);
                        fileJList.repaint();
                        appendSuccess("[" + d + "/" + total + "] 分析完成: " + jarFile.getName()
                                + " -> " + result.getDetectedGroupId() + ":"
                                + result.getDetectedArtifactId() + ":" + result.getDetectedVersion()
                                + " (" + result.getDetectedDependencies().size() + " 个依赖)");

                        // 自动选中第一个新分析的结果
                        if (d == 1) {
                            fileJList.setSelectedValue(jarFile, true);
                        }
                    });

                } catch (Exception e) {
                    final int d = ++done;
                    SwingUtilities.invokeLater(() -> {
                        appendError("[" + d + "/" + total + "] 分析失败: " + jarFile.getName() + " - " + e.getMessage());
                    });
                }
            }

            SwingUtilities.invokeLater(() -> {
                appendSuccess("全部分析完成: " + resultMap.size() + "/" + fileListModel.size() + " 个文件");
            });

        }).start();
    }

    // ========== 生成 pom.xml ==========

    private void doGeneratePom() {
        if (currentResult == null) {
            JOptionPane.showMessageDialog(this, "请先分析文件，或选中一个已分析的文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        dependencyPanel.syncToResult();

        currentConfig = new ProjectConfig();
        currentConfig.setOutputDir(outputDirField.getText().trim());

        PomGenerator gen = new PomGenerator();
        String pomXml = gen.generate(currentResult, currentConfig);
        File selected = fileJList.getSelectedValue();
        String key = selected != null ? selected.getName() : currentResult.getDetectedArtifactId();
        pomPreviewPanel.setPomContent(pomXml, key);
        tabbedPane.setSelectedIndex(2);
    }

    // ========== 构建全部 ==========

    private void doBuildAll() {
        if (resultMap.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先分析文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 保存当前编辑
        dependencyPanel.syncToResult();
        pomPreviewPanel.saveCurrentCache();

        String outputDirRaw = outputDirField.getText().trim();
        final String outputDir = outputDirRaw.isEmpty() ? "." : outputDirRaw;

        currentConfig = new ProjectConfig();
        currentConfig.setOutputDir(outputDir);
        final ProjectConfig config = currentConfig;

        final List<Map.Entry<File, JarAnalysisResult>> entries = new ArrayList<>(resultMap.entrySet());

        appendInfo("开始构建 " + entries.size() + " 个 Maven 项目...");

        new Thread(() -> {
            PomGenerator gen = new PomGenerator();
            ProjectBuilder builder = new ProjectBuilder(config);
            int total = entries.size();
            int done = 0;

            for (Map.Entry<File, JarAnalysisResult> entry : entries) {
                File jarFile = entry.getKey();
                JarAnalysisResult result = entry.getValue();

                File outDir = new File(outputDir, result.getDetectedArtifactId());

                if (outDir.exists()) {
                    IoUtils.deleteRecursive(outDir);
                }

                try {
                    String pomXml = gen.generate(result, config);

                    final int d = ++done;
                    builder.build(jarFile, result, pomXml, outDir, (message, percent) -> {
                        SwingUtilities.invokeLater(() -> appendDebug(message));
                    });

                    SwingUtilities.invokeLater(() -> {
                        appendSuccess("[" + d + "/" + total + "] 已生成: " + outDir.getAbsolutePath());
                        appendReportPaths(outDir);
                    });

                } catch (Exception e) {
                    final int d = ++done;
                    SwingUtilities.invokeLater(() -> {
                        appendError("[" + d + "/" + total + "] 构建失败: " + jarFile.getName() + " - " + e.getMessage());
                    });
                }
            }

            SwingUtilities.invokeLater(() -> {
                appendSuccess("全部构建完成!");
                JOptionPane.showMessageDialog(MainPanel.this,
                        entries.size() + " 个 Maven 项目已生成到:\n" + outputDir,
                        "完成", JOptionPane.INFORMATION_MESSAGE);
            });

        }).start();
    }

    public void onPanelReady() {
        appendSuccess("jar2mp v1.0 - JAR 转 Maven 项目工具");
        appendInfo("添加一个或多个 JAR/WAR 文件，然后点击「分析全部」开始");
    }

    private void appendReportPaths(File outDir) {
        appendInfo("Reports:");
        appendInfo("  " + new File(outDir, "restoration-report.md").getAbsolutePath());
        appendInfo("  " + new File(outDir, "resource-inventory.md").getAbsolutePath());
        appendInfo("  " + new File(outDir, "decompile-parity-report.md").getAbsolutePath());
        File runtimeTraceReport = new File(outDir, "runtime-trace-report.md");
        if (runtimeTraceReport.exists()) {
            appendInfo("  " + runtimeTraceReport.getAbsolutePath());
        }
        appendInfo("  " + new File(outDir, "RUNBOOK.md").getAbsolutePath());
        appendInfo("  " + new File(outDir, "decompile-failures.md").getAbsolutePath());
    }
}
