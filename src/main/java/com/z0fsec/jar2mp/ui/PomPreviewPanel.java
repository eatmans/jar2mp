package com.z0fsec.jar2mp.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PomPreviewPanel extends BasePanel {

    private JTextArea pomArea;
    private String currentFileKey;
    private final Map<String, String> pomCache = new HashMap<>();

    public PomPreviewPanel(Consumer<String> logConsumer) {
        super(logConsumer);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 工具栏
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton copyBtn = new JButton("复制到剪贴板");
        copyBtn.addActionListener(e -> copyToClipboard());
        JButton saveBtn = new JButton("保存到文件");
        saveBtn.addActionListener(e -> saveToFile());
        toolbar.add(copyBtn);
        toolbar.add(saveBtn);
        contentPanel.add(toolbar, BorderLayout.NORTH);

        // 文本区
        pomArea = new JTextArea();
        pomArea.setEditable(true);
        pomArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        pomArea.setTabSize(4);

        // 启用右键菜单
        enablePomContextMenu();

        contentPanel.add(new JScrollPane(pomArea), BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * 设置 pom.xml 内容并缓存到指定 fileKey。
     */
    public void setPomContent(String pomXml, String fileKey) {
        currentFileKey = fileKey;
        pomCache.put(fileKey, pomXml);
        pomArea.setText(pomXml);
        pomArea.setCaretPosition(0);
        appendSuccess("pom.xml 预览已生成: " + fileKey);
    }

    /**
     * 保留旧接口兼容（无缓存）。
     */
    public void setPomContent(String pomXml) {
        pomArea.setText(pomXml);
        pomArea.setCaretPosition(0);
        appendSuccess("pom.xml 预览已生成");
    }

    public String getPomContent() {
        return pomArea.getText();
    }

    /**
     * 切换到指定文件的 pom.xml 缓存。
     * 先保存当前编辑到缓存，再从缓存加载目标文件。
     */
    public void switchToFile(String fileKey) {
        // 保存当前编辑
        if (currentFileKey != null) {
            pomCache.put(currentFileKey, pomArea.getText());
        }
        currentFileKey = fileKey;
        String cached = pomCache.get(fileKey);
        pomArea.setText(cached != null ? cached : "");
        pomArea.setCaretPosition(0);
    }

    /**
     * 保存当前编辑到缓存。
     */
    public void saveCurrentCache() {
        if (currentFileKey != null) {
            pomCache.put(currentFileKey, pomArea.getText());
        }
    }

    public void clearCache(String fileKey) {
        pomCache.remove(fileKey);
        if (currentFileKey != null && currentFileKey.equals(fileKey)) {
            pomArea.setText("");
            currentFileKey = null;
        }
    }

    public void clearAllCache() {
        pomCache.clear();
        pomArea.setText("");
        currentFileKey = null;
    }

    private void copyToClipboard() {
        String text = pomArea.getText();
        if (text.isEmpty()) {
            appendWarning("没有内容可复制");
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null);
        appendSuccess("pom.xml 已复制到剪贴板");
    }

    private void saveToFile() {
        String text = pomArea.getText();
        if (text.isEmpty()) {
            appendWarning("没有内容可保存");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("pom.xml"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                com.z0fsec.jar2mp.util.IoUtils.writeStringToFile(chooser.getSelectedFile(), text);
                appendSuccess("pom.xml 已保存到: " + chooser.getSelectedFile().getAbsolutePath());
            } catch (Exception e) {
                appendError("保存失败: " + e.getMessage());
            }
        }
    }

    // ========== 右键菜单 ==========

    private void enablePomContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyMenuItem = new JMenuItem("复制");
        copyMenuItem.addActionListener(e -> copySelectedText());
        popupMenu.add(copyMenuItem);

        // 监听鼠标事件显示菜单
        pomArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfTrigger(e);
            }

            private void showPopupIfTrigger(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popupMenu.show(pomArea, e.getX(), e.getY());
                }
            }
        });
    }

    private void copySelectedText() {
        String selectedText = pomArea.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            appendWarning("请先选择要复制的内容");
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(selectedText), null);
        appendSuccess("已复制选中内容到剪贴板（" + selectedText.length() + " 个字符）");
    }

    public void clearData() {
        pomArea.setText("");
    }
}
