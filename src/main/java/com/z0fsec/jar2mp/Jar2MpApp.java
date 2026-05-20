package com.z0fsec.jar2mp;

import com.formdev.flatlaf.FlatLaf;
import com.z0fsec.jar2mp.cli.CliRunner;
import com.z0fsec.jar2mp.ui.MainPanel;
import com.z0fsec.jar2mp.util.ConfigUtil;
import com.z0fsec.jar2mp.util.Jar2MpConstants;
import com.z0fsec.jar2mp.util.MenuUtil;

import javax.swing.*;
import java.awt.*;

public class Jar2MpApp {

    private JFrame frame;
    private MainPanel mainPanel;

    public Jar2MpApp() {
        initializeLookAndFeel();
        createAndShowGUI();
    }

    public static void main(String[] args) {
        // CLI mode if arguments are provided
        if (args.length > 0) {
            int exitCode = new CliRunner().run(args);
            System.exit(exitCode);
            return;
        }

        // GUI mode
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", Jar2MpConstants.NAME);

        SwingUtilities.invokeLater(() -> {
            try {
                new Jar2MpApp();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "jar2mp 启动失败:\n" + e.getMessage(),
                        "启动错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void initializeLookAndFeel() {
        try {
            // 从配置文件中读取保存的主题，如果没有则使用默认主题
            String savedTheme = ConfigUtil.get("theme", ConfigUtil.DEFAULT_THEME);
            FlatLaf laf = (FlatLaf) Class.forName(savedTheme).getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);

            Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
            UIManager.put("Label.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("TextArea.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("TabbedPane.font", font);
            UIManager.put("CheckBox.font", font);
            UIManager.put("ComboBox.font", font);
            UIManager.put("Table.font", font);
            UIManager.put("List.font", font);
            UIManager.put("Menu.font", font);
            UIManager.put("MenuItem.font", font);

            UIManager.put("Component.arrowType", "chevron");
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 5);

        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void createAndShowGUI() {
        frame = new JFrame(Jar2MpConstants.NAME + " v" + Jar2MpConstants.VERSION
                + " - " + Jar2MpConstants.DESCRIPTION + " - By " +Jar2MpConstants.AUTHOR + " -  https://github.com/eatmans/jar2mp") ;

        frame.setJMenuBar(MenuUtil.createMenuBar(frame));

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(frame,
                        "确定要退出 jar2mp 吗？", "确认退出",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    frame.dispose();
                    System.exit(0);
                }
            }
        });

        frame.setSize(1100, 800);
        frame.setMinimumSize(new Dimension(900, 650));
        frame.setLocationRelativeTo(null);

        mainPanel = new MainPanel(System.out::println);
        frame.getContentPane().add(mainPanel, BorderLayout.CENTER);

        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> mainPanel.onPanelReady());
    }
}
