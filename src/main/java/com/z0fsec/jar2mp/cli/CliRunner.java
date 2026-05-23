package com.z0fsec.jar2mp.cli;

import com.z0fsec.jar2mp.core.*;
import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.Jar2MpConstants;

import java.io.*;
import java.util.*;

public class CliRunner {

    public int run(String[] args) {
        CliOptions options = parseArgs(args);
        if (options == null) return 1;

        if (options.isShowHelp()) {
            CliOptions.printHelp();
            return 0;
        }

        if (options.isShowVersion()) {
            System.out.println(Jar2MpConstants.NAME + " v" + Jar2MpConstants.VERSION);
            return 0;
        }

        if (options.getInputFiles().isEmpty()) {
            System.err.println("Error: 未指定输入文件。使用 --help 查看用法。");
            return 1;
        }

        // Resolve input files: expand directories to .jar/.war files
        List<File> files = resolveInputFiles(options.getInputFiles());
        if (files.isEmpty()) {
            System.err.println("Error: 未找到有效的 JAR/WAR 文件。");
            return 1;
        }

        ProjectConfig config = options.getConfig();
        if (config.getOutputDir() == null) {
            config.setOutputDir(new File(".").getAbsolutePath());
        }

        try {
            // Load package mappings database
            PackagePrefixDatabase packageDb = new PackagePrefixDatabase();
            try (InputStream is = getClass().getResourceAsStream("/db/package-mappings.properties")) {
                if (is != null) packageDb.load(is);
            }
            if (config.getCustomMappingFile() != null) {
                packageDb.loadCustom(new File(config.getCustomMappingFile()));
            }

            JarAnalyzer analyzer = new JarAnalyzer(packageDb, config);
            PomGenerator pomGen = new PomGenerator();
            ProjectBuilder builder = new ProjectBuilder(config);

            int totalFiles = files.size();
            int successCount = 0;

            if (!options.isQuiet()) {
                System.out.println("共 " + totalFiles + " 个文件待处理\n");
            }

            for (int fileIndex = 0; fileIndex < totalFiles; fileIndex++) {
                File jarFile = files.get(fileIndex);
                final int fi = fileIndex;

                if (!options.isQuiet()) {
                    System.out.println("========== [" + (fi + 1) + "/" + totalFiles + "] " + jarFile.getName() + " ==========");
                }

                try {
                    // Analyze
                    JarAnalysisResult result = analyzer.analyze(jarFile, (message, percent) -> {
                        if (!options.isQuiet() && options.isVerbose()) {
                            int overallPercent = (fi * 100 + percent) / totalFiles;
                            System.out.println("  [" + overallPercent + "%] " + message);
                        }
                    });
                    applyDependencyOptions(result, options);

                    if (!options.isQuiet()) {
                        printSummary(result);
                    }

                    // Export dependencies if requested
                    if (options.getExportDepsFile() != null) {
                        exportDependencies(result.getDetectedDependencies(),
                                options.getExportDepsFile() + "." + result.getDetectedArtifactId());
                    }

                    // Generate pom.xml
                    String pomXml = pomGen.generate(result, config);

                    // Output dir
                    File outputDir = new File(config.getOutputDir(), result.getDetectedArtifactId());
                    if (outputDir.exists() && !config.isForceOverwrite()) {
                        System.err.println("  输出目录已存在: " + outputDir.getAbsolutePath() + " (使用 --force 覆盖)");
                        continue;
                    }

                    // Build
                    builder.build(jarFile, result, pomXml, outputDir, (message, percent) -> {
                        if (!options.isQuiet() && options.isVerbose()) {
                            System.out.println("  [" + percent + "%] " + message);
                        }
                    });

                    if (!options.isQuiet()) {
                        System.out.println("  Maven 项目已生成: " + outputDir.getAbsolutePath());
                    }
                    successCount++;

                } catch (Exception e) {
                    System.err.println("  处理失败: " + jarFile.getName() + " - " + e.getMessage());
                    if (options.isVerbose()) {
                        e.printStackTrace();
                    }
                }
            }

            if (!options.isQuiet()) {
                System.out.println("\n完成: " + successCount + "/" + totalFiles + " 个文件处理成功");
            }

            return successCount > 0 ? 0 : 2;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (options.isVerbose()) {
                e.printStackTrace();
            }
            return 2;
        }
    }

    /**
     * Resolve input paths: expand directories to their .jar/.war contents.
     */
    private List<File> resolveInputFiles(List<String> paths) {
        List<File> result = new ArrayList<>();
        for (String path : paths) {
            File file = new File(path);
            if (!file.exists()) {
                System.err.println("文件不存在: " + file.getAbsolutePath());
                continue;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles((dir, name) ->
                        name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".war"));
                if (children != null) {
                    for (File child : children) {
                        result.add(child);
                    }
                }
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jar") || name.endsWith(".war")) {
                    result.add(file);
                } else {
                    System.err.println("跳过非 JAR/WAR 文件: " + file.getName());
                }
            }
        }
        return result;
    }

    private CliOptions parseArgs(String[] args) {
        CliOptions options = new CliOptions();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    options.setShowHelp(true);
                    return options;
                case "--version":
                    options.setShowVersion(true);
                    return options;
                case "-o":
                case "--output":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setOutputDir(args[i]);
                    break;
                case "-g":
                case "--groupId":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setGroupId(args[i]);
                    break;
                case "-a":
                case "--artifactId":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setArtifactId(args[i]);
                    break;
                case "-v":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setVersion(args[i]);
                    break;
                case "--set-version":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setVersion(args[i]);
                    break;
                case "-j":
                case "--java-version":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setJavaVersion(Integer.parseInt(args[i]));
                    break;
                case "-p":
                case "--packaging":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setPackaging(args[i]);
                    break;
                case "--no-decompile":
                    options.getConfig().setDecompile(false);
                    break;
                case "--no-dependencies":
                    options.getConfig().setDetectDependencies(false);
                    break;
                case "--no-resources":
                    options.getConfig().setCopyResources(false);
                    break;
                case "--mapping-file":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setCustomMappingFile(args[i]);
                    break;
                case "--aggressive-scan":
                    options.getConfig().setAggressiveScan(true);
                    break;
                case "--include-synthetic":
                    options.getConfig().setIncludeSynthetic(true);
                    break;
                case "--export-deps":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.setExportDepsFile(args[i]);
                    break;
                case "--import-deps":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.setImportDepsFile(args[i]);
                    break;
                case "-f":
                case "--force":
                    options.getConfig().setForceOverwrite(true);
                    break;
                case "-q":
                case "--quiet":
                    options.setQuiet(true);
                    break;
                case "--verbose":
                    options.setVerbose(true);
                    break;
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        return null;
                    }
                    options.addInputFile(arg);
                    break;
            }
            i++;
        }
        return options;
    }

    private void printSummary(JarAnalysisResult result) {
        System.out.println("  类型: " + (result.isWar() ? "WAR" : "JAR")
                + " | 条目: " + result.getTotalEntries()
                + " | 类文件: " + result.getClassFiles().size()
                + " | Java " + result.getJavaVersion());
        System.out.println("  坐标: " + result.getDetectedGroupId() + ":"
                + result.getDetectedArtifactId() + ":" + result.getDetectedVersion());

        List<MavenDependency> deps = result.getDetectedDependencies();
        if (!deps.isEmpty()) {
            System.out.println("  依赖 (" + deps.size() + "):");
            for (MavenDependency dep : deps) {
                System.out.println("    " + dep);
            }
        }
    }

    private void exportDependencies(List<MavenDependency> deps, String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("# jar2mp dependency export");
            pw.println("# Format: groupId:artifactId:version:scope:confidence");
            for (MavenDependency dep : deps) {
                pw.println(dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion()
                        + ":" + (dep.getScope() != null ? dep.getScope() : "compile")
                        + ":" + (dep.getConfidence() != null ? dep.getConfidence().name() : "UNKNOWN"));
            }
        } catch (IOException e) {
            System.err.println("导出依赖失败: " + e.getMessage());
        }
    }

    private void applyDependencyOptions(JarAnalysisResult result, CliOptions options) throws IOException {
        if (options.getImportDepsFile() != null) {
            mergeImportedDependencies(result.getDetectedDependencies(), importDependencies(options.getImportDepsFile()));
        }
    }

    private void mergeImportedDependencies(List<MavenDependency> detectedDeps, List<MavenDependency> importedDeps) {
        Map<String, Integer> dependencyIndexes = new LinkedHashMap<>();
        for (int i = 0; i < detectedDeps.size(); i++) {
            MavenDependency dep = detectedDeps.get(i);
            dependencyIndexes.put(dependencyKey(dep), i);
        }

        for (MavenDependency importedDep : importedDeps) {
            String key = dependencyKey(importedDep);
            Integer existingIndex = dependencyIndexes.get(key);
            if (existingIndex != null) {
                detectedDeps.set(existingIndex, importedDep);
            } else {
                dependencyIndexes.put(key, detectedDeps.size());
                detectedDeps.add(importedDep);
            }
        }
    }

    private String dependencyKey(MavenDependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId();
    }

    private List<MavenDependency> importDependencies(String filePath) throws IOException {
        List<MavenDependency> deps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(":");
                if (parts.length < 3) {
                    continue;
                }
                MavenDependency dep = new MavenDependency(
                        parts[0],
                        parts[1],
                        parts[2],
                        parseConfidence(parts.length >= 5 ? parts[4] : null)
                );
                if (parts.length >= 4 && !parts[3].isEmpty()) {
                    dep.setScope(parts[3]);
                }
                deps.add(dep);
            }
        }
        return deps;
    }

    private MavenDependency.Confidence parseConfidence(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MavenDependency.Confidence.MANUAL;
        }
        try {
            return MavenDependency.Confidence.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return MavenDependency.Confidence.MANUAL;
        }
    }
}
