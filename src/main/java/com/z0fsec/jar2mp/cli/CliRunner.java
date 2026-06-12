package com.z0fsec.jar2mp.cli;

import com.z0fsec.jar2mp.core.*;
import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.Jar2MpConstants;
import com.z0fsec.jar2mp.util.TraceArgsParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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

        if (options.getCompareArtifactFile() != null) {
            return compareArtifacts(options);
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
            BuildPostProcessor postProcessor = new BuildPostProcessor();

            int totalFiles = files.size();
            int successCount = 0;
            int failedCount = 0;

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
                        failedCount++;
                        continue;
                    }

                    // Build
                    builder.build(jarFile, result, pomXml, outputDir, (message, percent) -> {
                        if (!options.isQuiet() && options.isVerbose()) {
                            System.out.println("  [" + percent + "%] " + message);
                        }
                    });

                    BuildPostProcessor.PostBuildResult postBuildResult = postProcessor.postProcess(
                            jarFile,
                            result,
                            outputDir,
                            config,
                            message -> {
                                if (!options.isQuiet()) {
                                    System.out.println("  " + message);
                                }
                            });

                    if (!options.isQuiet()) {
                        System.out.println("  Maven 项目已生成: " + outputDir.getAbsolutePath());
                    }

                    if (postBuildResult.hasBlockingFailure()) {
                        failedCount++;
                        continue;
                    }

                    if (!options.isQuiet()) {
                        printReportPaths(outputDir, config, false);
                    }
                    successCount++;

                } catch (Exception e) {
                    failedCount++;
                    System.err.println("  处理失败: " + jarFile.getName() + " - " + e.getMessage());
                    if (options.isVerbose()) {
                        e.printStackTrace();
                    }
                }
            }

            if (!options.isQuiet()) {
                System.out.println("\n完成: " + successCount + "/" + totalFiles + " 个文件处理成功");
            }

            if (successCount == totalFiles && failedCount == 0) {
                return 0;
            }
            return successCount > 0 ? 1 : 2;

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
        boolean verifyGoalExplicit = false;
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
                    Integer javaVersion = parsePositiveInt(args[i], arg);
                    if (javaVersion == null) return null;
                    options.getConfig().setJavaVersion(javaVersion.intValue());
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
                case "--verify-build":
                    options.getConfig().setVerifyBuild(true);
                    break;
                case "--verify-goal":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setVerifyGoal(args[i]);
                    verifyGoalExplicit = true;
                    break;
                case "--trace-runtime":
                    options.getConfig().setTraceRuntime(true);
                    break;
                case "--trace-args":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.getConfig().setTraceRuntime(true);
                    options.getConfig().getTraceArgs().addAll(parseTraceArgs(args[i]));
                    break;
                case "--trace-timeout":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    Long timeout = parsePositiveLong(args[i], arg);
                    if (timeout == null) return null;
                    options.getConfig().setTraceRuntime(true);
                    options.getConfig().setTraceTimeoutSeconds(timeout.longValue());
                    break;
                case "--smoke-only":
                    options.getConfig().setTraceRuntime(true);
                    options.getConfig().setSmokeOnly(true);
                    break;
                case "--emit-raw-artifact":
                    options.getConfig().setEmitRawArtifact(true);
                    break;
                case "--byte-exact-package":
                    options.getConfig().setEmitRawArtifact(true);
                    options.getConfig().setByteExactPackage(true);
                    if (!verifyGoalExplicit) {
                        options.getConfig().setVerifyGoal("package");
                    }
                    break;
                case "--compare-artifact":
                    if (++i >= args.length) { System.err.println("Missing value for " + arg); return null; }
                    options.setCompareArtifactFile(args[i]);
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

    private int compareArtifacts(CliOptions options) {
        if (options.getInputFiles().size() != 1) {
            System.err.println("Error: --compare-artifact requires exactly one original JAR/WAR input.");
            return 1;
        }

        File original = new File(options.getInputFiles().get(0));
        File rebuilt = new File(options.getCompareArtifactFile());
        if (!original.isFile()) {
            System.err.println("Error: 原始归档不存在: " + original.getAbsolutePath());
            return 1;
        }
        if (!rebuilt.isFile()) {
            System.err.println("Error: 重建归档不存在: " + rebuilt.getAbsolutePath());
            return 1;
        }

        File outputDir = options.getConfig().getOutputDir() == null
                ? new File(".")
                : new File(options.getConfig().getOutputDir());
        try {
            ArtifactFidelityComparator comparator = new ArtifactFidelityComparator();
            ArtifactFidelityReportWriter reportWriter = new ArtifactFidelityReportWriter();
            ArtifactFidelityResult result = comparator.compare(original, rebuilt);
            reportWriter.write(outputDir, result);
            maybeWriteArchiveOrderRestoredCandidate(original, rebuilt, outputDir, result, comparator,
                    reportWriter, options.isQuiet());
            if (!options.isQuiet()) {
                System.out.println("Artifact fidelity exact match: " + result.isExactMatch());
                System.out.println("Artifact fidelity report: "
                        + new File(outputDir, "artifact-fidelity-report.md").getAbsolutePath());
                System.out.println("Artifact fidelity CSV: "
                        + new File(outputDir, "artifact-fidelity-summary.csv").getAbsolutePath());
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Error: artifact fidelity compare failed: " + e.getMessage());
            return 2;
        }
    }

    private void maybeWriteArchiveOrderRestoredCandidate(File original, File rebuilt, File outputDir,
                                                         ArtifactFidelityResult result,
                                                         ArtifactFidelityComparator comparator,
                                                         ArtifactFidelityReportWriter reportWriter,
                                                         boolean quiet) {
        if (!result.isContentEntriesMatch()
                || result.isArchiveBytesSame()
                || result.isArchiveEntryOrderSame()) {
            return;
        }
        File restoredDir = new File(outputDir, "archive-order-restored");
        try {
            File restored = new ZipRecordOrderRestorer().restore(original, rebuilt, restoredDir);
            ArtifactFidelityResult restoredResult = comparator.compare(original, restored);
            reportWriter.write(restoredDir, restoredResult);
            if (!quiet) {
                System.out.println("Archive-order restored artifact: " + restored.getAbsolutePath()
                        + " (exact=" + restoredResult.isExactMatch() + ")");
            }
        } catch (IOException e) {
            if (!quiet) {
                System.err.println("Archive-order restoration skipped: " + e.getMessage());
            }
        }
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

    private List<String> parseTraceArgs(String value) {
        return TraceArgsParser.parse(value);
    }

    private Long parsePositiveLong(String value, String optionName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                System.err.println("Invalid value for " + optionName + ": " + value);
                return null;
            }
            return Long.valueOf(parsed);
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
    }

    private Integer parsePositiveInt(String value, String optionName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                System.err.println("Invalid value for " + optionName + ": " + value);
                return null;
            }
            return Integer.valueOf(parsed);
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for " + optionName + ": " + value);
            return null;
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

    private void printReportPaths(File outputDir, ProjectConfig config, boolean includeVerification) {
        System.out.println("  Reports:");
        System.out.println("    " + new File(outputDir, "restoration-report.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "resource-inventory.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "decompile-parity-report.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "restoration-score.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "gap-summary.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "RUNBOOK.md").getAbsolutePath());
        System.out.println("    " + new File(outputDir, "decompile-failures.md").getAbsolutePath());
        File traceReport = new File(outputDir, "runtime-trace-report.md");
        if (traceReport.isFile() || config.isTraceRuntime() || config.isSmokeOnly()) {
            System.out.println("    " + traceReport.getAbsolutePath());
        }
        if (includeVerification && config.isVerifyBuild()) {
            System.out.println("    " + new File(outputDir, "verification-report.md").getAbsolutePath());
            System.out.println("    " + new File(outputDir, "verification-errors.md").getAbsolutePath());
        }
        File byteExactPackageReport = new File(outputDir, "target/byte-exact-package-check/artifact-fidelity-report.md");
        if (byteExactPackageReport.isFile()) {
            System.out.println("    " + byteExactPackageReport.getAbsolutePath());
            System.out.println("    " + new File(outputDir,
                    "target/byte-exact-package-check/artifact-fidelity-summary.csv").getAbsolutePath());
        }
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
