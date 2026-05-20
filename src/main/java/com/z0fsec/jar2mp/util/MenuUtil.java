package com.z0fsec.jar2mp.util;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;

public class MenuUtil {

    public static JMenuBar createMenuBar(JFrame frame) {
        JMenuBar menuBar = new JMenuBar();

        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(frame, "确定要退出 jar2mp 吗？", "确认退出",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // 设置菜单
        JMenu settingsMenu = new JMenu("设置");

        // 主题子菜单
        JMenu themeMenu = new JMenu("主题");
        ButtonGroup themeGroup = new ButtonGroup();

        String[][] themes = {
                {"Flat Light", "com.formdev.flatlaf.FlatLightLaf"},
                {"Flat Dark", "com.formdev.flatlaf.FlatDarkLaf"},
                {"Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf"},
                {"Flat IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf"},
                {"Flat Mac Dark", "com.formdev.flatlaf.themes.FlatMacDarkLaf"},
                {"Flat Mac Light", "com.formdev.flatlaf.themes.FlatMacLightLaf"},
        };

        JRadioButtonMenuItem[] themeItems = new JRadioButtonMenuItem[themes.length];
        for (int i = 0; i < themes.length; i++) {
            String[] theme = themes[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(theme[0]);
            item.addActionListener(ev -> switchTheme(theme[1], frame));
            themeGroup.add(item);
            themeMenu.add(item);
            themeItems[i] = item;
        }

        // 设置当前选中的主题
        setSelectedTheme(themeItems, themes);

        settingsMenu.add(themeMenu);
        menuBar.add(settingsMenu);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "<html><b>jar2mp</b> v" + Jar2MpConstants.VERSION +
                        "<br>JAR 转 Maven 项目工具" +
                        "<br><br>作者: " + Jar2MpConstants.AUTHOR +
                        "<br><br>用法: java -jar jar2mp.jar [选项] &lt;jar文件&gt;</html>",
                "关于 jar2mp", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private static void setSelectedTheme(JRadioButtonMenuItem[] themeItems, String[][] themes) {
        String currentTheme = ConfigUtil.get(ConfigUtil.THEME_KEY, ConfigUtil.DEFAULT_THEME);

        for (int i = 0; i < themes.length; i++) {
            if (themes[i][1].equals(currentTheme)) {
                themeItems[i].setSelected(true);
                break;
            }
        }
    }

    private static void switchTheme(String className, JFrame frame) {
        try {
            FlatLaf laf = (FlatLaf) Class.forName(className).getDeclaredConstructor().newInstance();
            UIManager.setLookAndFeel(laf);

            // 保存主题偏好设置到配置文件
            ConfigUtil.set(ConfigUtil.THEME_KEY, className);

            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "切换主题失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
