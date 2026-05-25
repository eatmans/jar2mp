package com.z0fsec.jar2mp.db;

import com.z0fsec.jar2mp.model.MavenCoordinates;

import java.io.*;
import java.util.*;

public class PackagePrefixDatabase {

    private final TreeMap<String, MavenCoordinates> mappings = new TreeMap<>();
    private boolean loaded = false;

    public void load(InputStream is) {
        try {
            Properties props = new Properties();
            props.load(is);

            for (String prefix : props.stringPropertyNames()) {
                String value = props.getProperty(prefix);
                String[] parts = value.split(":");
                if (parts.length >= 2) {
                    MavenCoordinates coord = new MavenCoordinates(
                            parts[0],
                            parts[1],
                            parts.length >= 3 ? parts[2] : "unknown"
                    );
                    mappings.put(prefix.trim(), coord);
                }
            }
            loaded = true;
        } catch (IOException e) {
            System.err.println("Failed to load package mappings: " + e.getMessage());
        }
    }

    public void loadCustom(File file) {
        if (file == null || !file.exists()) return;
        try (InputStream is = new FileInputStream(file)) {
            load(is);
        } catch (IOException e) {
            System.err.println("Failed to load custom mappings: " + e.getMessage());
        }
    }

    /**
     * Lookup the longest matching prefix for a given package name.
     */
    public MavenCoordinates lookup(String packageName) {
        Map.Entry<String, MavenCoordinates> entry = lookupEntry(packageName);
        return entry == null ? null : entry.getValue();
    }

    public String lookupPrefix(String packageName) {
        Map.Entry<String, MavenCoordinates> entry = lookupEntry(packageName);
        return entry == null ? null : entry.getKey();
    }

    private Map.Entry<String, MavenCoordinates> lookupEntry(String packageName) {
        if (!loaded) return null;

        // Try exact match first
        MavenCoordinates exact = mappings.get(packageName);
        if (exact != null) {
            return new AbstractMap.SimpleImmutableEntry<>(packageName, exact);
        }

        // Try progressively shorter prefixes
        String prefix = packageName;
        while (prefix.contains(".")) {
            prefix = prefix.substring(0, prefix.lastIndexOf('.'));
            MavenCoordinates match = mappings.get(prefix);
            if (match != null) {
                return new AbstractMap.SimpleImmutableEntry<>(prefix, match);
            }
        }

        return null;
    }

    public int size() {
        return mappings.size();
    }

    public boolean isLoaded() {
        return loaded;
    }
}
