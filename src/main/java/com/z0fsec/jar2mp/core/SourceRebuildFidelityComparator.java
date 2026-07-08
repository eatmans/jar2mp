package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.SourceRebuildFidelityResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SourceRebuildFidelityComparator {

    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String WAR_CLASSES_PREFIX = "WEB-INF/classes/";

    public SourceRebuildFidelityResult compare(File originalArtifact, File projectDir,
                                               List<String> compileFallbackClassPaths) throws IOException {
        Map<String, byte[]> originalClasses = readOriginalApplicationClasses(originalArtifact);
        Map<String, byte[]> recompiledClasses = readRecompiledClasses(projectDir);
        SourceRebuildFidelityResult result = new SourceRebuildFidelityResult();
        result.setOriginalAppClasses(originalClasses.size());
        result.setRecompiledClasses(recompiledClasses.size());
        result.setCompileFallbackClasses(countCompileFallbackClasses(compileFallbackClassPaths));

        List<String> originalNames = sorted(originalClasses);
        List<String> recompiledNames = sorted(recompiledClasses);

        int common = 0;
        int same = 0;
        int different = 0;
        for (String classPath : originalNames) {
            byte[] recompiledBytes = recompiledClasses.get(classPath);
            if (recompiledBytes == null) {
                result.setMissingRecompiledClasses(result.getMissingRecompiledClasses() + 1);
                result.recordMissingRecompiledClass(classPath);
                continue;
            }

            common++;
            if (java.util.Arrays.equals(originalClasses.get(classPath), recompiledBytes)) {
                same++;
            } else {
                different++;
                result.recordDifferentClass(classPath);
            }
        }

        for (String classPath : recompiledNames) {
            if (!originalClasses.containsKey(classPath)) {
                result.setExtraRecompiledClasses(result.getExtraRecompiledClasses() + 1);
                result.recordExtraRecompiledClass(classPath);
            }
        }

        result.setCommonClasses(common);
        result.setSameClassBytes(same);
        result.setDifferentClassBytes(different);
        return result;
    }

    private Map<String, byte[]> readOriginalApplicationClasses(File originalArtifact) throws IOException {
        Map<String, byte[]> allClasses = new LinkedHashMap<>();
        Map<String, byte[]> bootClasses = new LinkedHashMap<>();
        Map<String, byte[]> warClasses = new LinkedHashMap<>();
        if (originalArtifact == null || !originalArtifact.isFile()) {
            return allClasses;
        }

        try (JarFile jarFile = new JarFile(originalArtifact)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }

                byte[] bytes = readAllBytes(jarFile, entry);
                String name = entry.getName();
                if (name.startsWith(BOOT_CLASSES_PREFIX)) {
                    bootClasses.put(name.substring(BOOT_CLASSES_PREFIX.length()), bytes);
                } else if (name.startsWith(WAR_CLASSES_PREFIX)) {
                    warClasses.put(name.substring(WAR_CLASSES_PREFIX.length()), bytes);
                } else if (!isNestedArchiveClassPath(name)) {
                    allClasses.put(name, bytes);
                }
            }
        }

        if (!bootClasses.isEmpty()) {
            return bootClasses;
        }
        if (!warClasses.isEmpty()) {
            return warClasses;
        }
        return allClasses;
    }

    private Map<String, byte[]> readRecompiledClasses(File projectDir) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        if (projectDir == null) {
            return classes;
        }
        Path classesDir = projectDir.toPath().resolve("target/classes").normalize();
        if (!Files.isDirectory(classesDir)) {
            return classes;
        }

        try (java.util.stream.Stream<Path> stream = Files.walk(classesDir)) {
            for (Path classFile : (Iterable<Path>) stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null
                            && path.getFileName().toString().endsWith(".class"))::iterator) {
                String relative = classesDir.relativize(classFile).toString().replace('\\', '/');
                classes.put(relative, Files.readAllBytes(classFile));
            }
        }
        return classes;
    }

    private int countCompileFallbackClasses(List<String> compileFallbackClassPaths) {
        if (compileFallbackClassPaths == null) {
            return 0;
        }
        int count = 0;
        for (String classPath : compileFallbackClassPaths) {
            if (classPath != null && classPath.endsWith(".class")) {
                count++;
            }
        }
        return count;
    }

    private boolean isNestedArchiveClassPath(String name) {
        return name != null && name.contains("!/");
    }

    private byte[] readAllBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream input = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private List<String> sorted(Map<String, byte[]> entries) {
        List<String> names = new ArrayList<>(entries.keySet());
        Collections.sort(names);
        return names;
    }
}
