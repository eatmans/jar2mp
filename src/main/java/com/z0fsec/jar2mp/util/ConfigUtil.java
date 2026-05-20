package com.z0fsec.jar2mp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 配置文件工具类
 * 用于管理应用程序的配置文件存储和读取
 */
public class ConfigUtil {

    private static final String USER_HOME = System.getProperty("user.home");
    public static final String CONFIG_HOME = USER_HOME + File.separator + ".jar2mp";
    private static final String CONFIG_FILE = CONFIG_HOME + File.separator + "config.properties";

    public static final String THEME_KEY = "theme";
    public static final String DEFAULT_THEME = "com.formdev.flatlaf.FlatLightLaf";

    private static Properties config;

    static {
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        config = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
            } catch (IOException e) {
                System.err.println("加载配置文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存配置到文件
     */
    private static void saveConfig() {
        // 确保配置目录存在
        File configDir = new File(CONFIG_HOME);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "jar2mp 配置文件");
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static String get(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }

    /**
     * 获取配置值（整型）
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取配置值（布尔型）
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = config.getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * 设置配置值
     * @param key 配置键
     * @param value 配置值
     */
    public static void set(String key, String value) {
        config.setProperty(key, value);
        saveConfig();
    }

    /**
     * 设置配置值（整型）
     * @param key 配置键
     * @param value 配置值
     */
    public static void set(String key, int value) {
        set(key, String.valueOf(value));
    }

    /**
     * 设置配置值（布尔型）
     * @param key 配置键
     * @param value 配置值
     */
    public static void set(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    /**
     * 删除配置项
     * @param key 配置键
     */
    public static void remove(String key) {
        config.remove(key);
        saveConfig();
    }

    /**
     * 检查配置项是否存在
     * @param key 配置键
     * @return 是否存在
     */
    public static boolean containsKey(String key) {
        return config.containsKey(key);
    }

    /**
     * 获取配置项数量
     * @return 配置项数量
     */
    public static int size() {
        return config.size();
    }

    /**
     * 清空所有配置
     */
    public static void clear() {
        config.clear();
        saveConfig();
    }

    /**
     * 重新加载配置文件
     */
    public static void reload() {
        loadConfig();
    }

    /**
     * 获取配置文件路径
     * @return 配置文件完整路径
     */
    public static String getConfigFilePath() {
        return CONFIG_FILE;
    }

    /**
     * 获取配置目录路径
     * @return 配置目录路径
     */
    public static String getConfigHomePath() {
        return CONFIG_HOME;
    }
}
