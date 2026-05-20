package com.z0fsec.jar2mp.ui;

import com.z0fsec.jar2mp.util.TimeUtils;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BasePanel extends JPanel {
    protected Consumer<String> logConsumer;
    protected JTextPane logArea;
    protected StyledDocument logDocument;
    protected StyleContext logStyleContext;
    protected Map<String, Style> logStyles;

    // External log writer: when set, all log output is delegated to this panel
    // instead of writing to the local (non-existent) logDocument.
    protected BasePanel externalLogWriter;

    private static final Color COLOR_INFO = new Color(0, 100, 0);
    private static final Color COLOR_SUCCESS = new Color(0, 128, 0);
    private static final Color COLOR_WARNING = new Color(255, 140, 0);
    private static final Color COLOR_ERROR = new Color(220, 20, 60);
    private static final Color COLOR_DEBUG = new Color(70, 130, 180);
    private static final Color COLOR_DEFAULT = new Color(0, 0, 0);

    private static final String STYLE_INFO = "info";
    private static final String STYLE_SUCCESS = "success";
    private static final String STYLE_WARNING = "warning";
    private static final String STYLE_ERROR = "error";
    private static final String STYLE_DEBUG = "debug";
    private static final String STYLE_DEFAULT = "default";

    public BasePanel(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
        setLayout(new BorderLayout());
        initializeLogStyles();
    }

    private void initializeLogStyles() {
        logStyleContext = new StyleContext();
        logStyles = new HashMap<>();
        createLogStyle(STYLE_DEFAULT, COLOR_DEFAULT, false);
        createLogStyle(STYLE_INFO, COLOR_INFO, false);
        createLogStyle(STYLE_SUCCESS, COLOR_SUCCESS, false);
        createLogStyle(STYLE_WARNING, COLOR_WARNING, false);
        createLogStyle(STYLE_ERROR, COLOR_ERROR, false);
        createLogStyle(STYLE_DEBUG, COLOR_DEBUG, true);
    }

    private void createLogStyle(String styleName, Color color, boolean italic) {
        Style style = logStyleContext.addStyle(styleName, null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setFontFamily(style, "Microsoft YaHei");
        if (italic) {
            StyleConstants.setItalic(style, true);
        }
        logStyles.put(styleName, style);
    }

    protected JPanel createLogPanelWithScroll() {
        JPanel panel = new JPanel(new BorderLayout());
        logArea = new JTextPane();
        logDocument = logArea.getStyledDocument();
        logArea.setEditable(false);
        setOptimalFont(logArea);

        // 启用右键菜单
        enableLogContextMenu();

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(600, 150));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        panel.setBorder(BorderFactory.createTitledBorder("操作日志"));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void enableLogContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem clearMenuItem = new JMenuItem("清空日志");
        clearMenuItem.addActionListener(e -> {
            if (externalLogWriter != null) {
                externalLogWriter.clearLog();
            } else {
                clearLog();
            }
        });
        popupMenu.add(clearMenuItem);

        // 监听鼠标事件显示菜单
        logArea.addMouseListener(new MouseAdapter() {
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
                    popupMenu.show(logArea, e.getX(), e.getY());
                }
            }
        });
    }

    private void setOptimalFont(JTextPane textPane) {
        String[] preferredFonts = {
                "Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC",
                "Source Han Sans SC", "SimHei", "SimSun", "DejaVu Sans",
        };
        Font font = null;
        for (String fontName : preferredFonts) {
            font = new Font(fontName, Font.PLAIN, 12);
            if (font.getFamily().equals(fontName)) break;
        }
        if (font != null) {
            textPane.setFont(font);
        } else {
            textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        }
    }

    public void appendLog(String message) { appendLog(message, STYLE_DEFAULT); }
    public void appendInfo(String message) { appendLog("[i] " + message, STYLE_INFO); }
    public void appendSuccess(String message) { appendLog("[+] " + message, STYLE_SUCCESS); }
    public void appendWarning(String message) { appendLog("[!] " + message, STYLE_WARNING); }
    public void appendError(String message) { appendLog("[-] " + message, STYLE_ERROR); }
    public void appendDebug(String message) { appendLog("[*] " + message, STYLE_DEBUG); }

    public void appendLog(String message, String styleName) {
        // If an external log writer is set, delegate to it
        if (externalLogWriter != null) {
            externalLogWriter.appendLog(message, styleName);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = TimeUtils.timestampToDate(System.currentTimeMillis() / 1000);
                if (logDocument != null) {
                    Style tsStyle = logStyles.get(STYLE_DEFAULT);
                    logDocument.insertString(logDocument.getLength(), "[" + timestamp + "] ", tsStyle);
                    Style msgStyle = logStyles.getOrDefault(styleName, logStyles.get(STYLE_DEFAULT));
                    logDocument.insertString(logDocument.getLength(), message + "\n", msgStyle);
                    logArea.setCaretPosition(logDocument.getLength());
                }
                autoCleanLog();
            } catch (BadLocationException e) {
                System.err.println("Log append failed: " + e.getMessage());
            }
        });
    }

    private void autoCleanLog() {
        try {
            int maxLines = 500;
            int lineCount = logDocument.getDefaultRootElement().getElementCount();
            if (lineCount > maxLines) {
                int removeCount = lineCount - maxLines;
                Element root = logDocument.getDefaultRootElement();
                int endOffset = root.getElement(removeCount - 1).getEndOffset();
                logDocument.remove(0, endOffset);
            }
        } catch (Exception ignored) {
        }
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> {
            try {
                logDocument.remove(0, logDocument.getLength());
            } catch (BadLocationException e) {
                System.err.println("Clear log failed: " + e.getMessage());
            }
        });
    }

    public String getLogContent() {
        try {
            return logDocument.getText(0, logDocument.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }
}
