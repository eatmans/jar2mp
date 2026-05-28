package com.z0fsec.jar2mp.cli;

import com.z0fsec.jar2mp.core.*;
import com.z0fsec.jar2mp.db.PackagePrefixDatabase;
import com.z0fsec.jar2mp.model.*;
import com.z0fsec.jar2mp.util.Jar2MpConstants;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            ProjectVerifier verifier = new ProjectVerifier();
            RuntimeSmokeRunner smokeRunner = new RuntimeSmokeRunner();
            RuntimeTraceReportWriter traceReportWriter = new RuntimeTraceReportWriter();
            RawArtifactPackager rawArtifactPackager = new RawArtifactPackager();
            ArtifactFidelityComparator artifactFidelityComparator = new ArtifactFidelityComparator();
            ArtifactFidelityReportWriter artifactFidelityReportWriter = new ArtifactFidelityReportWriter();
            RestorationScorer restorationScorer = new RestorationScorer();
            RestorationScoreWriter restorationScoreWriter = new RestorationScoreWriter();
            GapSummaryWriter gapSummaryWriter = new GapSummaryWriter();

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

                    if (config.isEmitRawArtifact()) {
                        File preservedArtifact = rawArtifactPackager.preserve(jarFile, outputDir);
                        File rawArtifactDir = preservedArtifact.getParentFile();
                        ArtifactFidelityResult rawFidelity = artifactFidelityComparator.compare(jarFile, preservedArtifact);
                        artifactFidelityReportWriter.write(rawArtifactDir, rawFidelity);
                        if (!options.isQuiet()) {
                            System.out.println("  原始归档保真副本: " + preservedArtifact.getAbsolutePath()
                                    + " (exact=" + rawFidelity.isExactMatch() + ")");
                        }
                    }

                    if (config.isTraceRuntime() || config.isSmokeOnly()) {
                        RuntimeSmokeRunner.SmokeRunResult smokeResult = smokeRunner.runSmoke(
                                jarFile,
                                result,
                                resolveTraceAgentJar(),
                                resolveTraceFile(config, outputDir),
                                config.getTraceArgs(),
                                config.getTraceTimeoutSeconds());
                        traceReportWriter.write(outputDir, smokeResult);
                        result.setRuntimeSmokeResult(smokeResult);
                        result.setRuntimeTraceResult(smokeResult.getTraceResult());
                        refreshRestorationScore(outputDir, result, restorationScorer, restorationScoreWriter,
                                gapSummaryWriter);
                        if (!options.isQuiet()) {
                            printSmokeSummary(smokeResult);
                        }
                    }

                    if (!options.isQuiet()) {
                        System.out.println("  Maven 项目已生成: " + outputDir.getAbsolutePath());
                    }

                    if (config.isVerifyBuild() && !config.isSmokeOnly()) {
                        VerificationResult verification = verifier.verify(outputDir, config.getVerifyGoal());
                        result.setVerificationResult(verification);
                        verifier.writeReport(outputDir, verification);
                        refreshRestorationScore(outputDir, result, restorationScorer, restorationScoreWriter,
                                gapSummaryWriter);
                        if (!options.isQuiet()) {
                            printVerificationReportPath(outputDir);
                        }
                        if (!options.isQuiet()) {
                            System.out.println("  构建验证: " + verification.getFailureType()
                                    + " (exit " + verification.getExitCode() + ")");
                        }
                        if (isVerificationFailure(verification)) {
                            failedCount++;
                            continue;
                        }
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
            ArtifactFidelityResult result = new ArtifactFidelityComparator().compare(original, rebuilt);
            new ArtifactFidelityReportWriter().write(outputDir, result);
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
        List<String> parsed = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return parsed;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {
                addTraceArg(parsed, current);
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        addTraceArg(parsed, current);
        return parsed;
    }

    private void addTraceArg(List<String> parsed, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        parsed.add(current.toString());
        current.setLength(0);
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

    private boolean isVerificationFailure(VerificationResult verification) {
        if (verification == null) {
            return true;
        }
        return verification.getExitCode() != 0 || !"NONE".equals(verification.getFailureType());
    }

    private Path resolveTraceFile(ProjectConfig config, File outputDir) {
        String configured = config.getTraceFile();
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured.trim());
            return path.isAbsolute() ? path : outputDir.toPath().resolve(path);
        }
        return outputDir.toPath().resolve("runtime-trace.jsonl");
    }

    private File resolveTraceAgentJar() {
        String override = System.getProperty("jar2mp.traceAgentJar");
        if (override != null && !override.trim().isEmpty()) {
            return new File(override.trim());
        }

        String agentName = "jar2mp-" + Jar2MpConstants.VERSION + "-trace-agent.jar";
        File targetAgent = new File("target", agentName);
        if (targetAgent.isFile()) {
            return targetAgent;
        }

        File codeLocation = resolveCodeLocation();
        if (codeLocation != null) {
            File baseDir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
            File sibling = new File(baseDir, agentName);
            if (sibling.isFile()) {
                return sibling;
            }
        }

        File targetDir = new File("target");
        File[] candidates = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && name.contains("trace-agent"));
        if (candidates != null && candidates.length > 0) {
            Arrays.sort(candidates, Comparator.comparing(File::getName));
            return candidates[0];
        }
        return targetAgent;
    }

    private File resolveCodeLocation() {
        try {
            if (getClass().getProtectionDomain() == null
                    || getClass().getProtectionDomain().getCodeSource() == null
                    || getClass().getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return new File(uri);
        } catch (Exception e) {
            return null;
        }
    }

    private void printSmokeSummary(RuntimeSmokeRunner.SmokeRunResult smokeResult) {
        RuntimeTraceResult traceResult = smokeResult.getTraceResult();
        int eventCount = traceResult == null ? 0 : traceResult.getEvents().size();
        String status = smokeResult.isSuccessful() ? "OK" : "FAILED";
        System.out.println("  运行时追踪: " + status + " (" + eventCount + " events)");
        if (smokeResult.getFailureMessage() != null && !smokeResult.getFailureMessage().trim().isEmpty()) {
            System.out.println("    " + smokeResult.getFailureMessage());
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
    }

    private void printVerificationReportPath(File outputDir) {
        System.out.println("  " + new File(outputDir, "verification-report.md").getAbsolutePath());
        System.out.println("  " + new File(outputDir, "verification-errors.md").getAbsolutePath());
    }

    private void refreshRestorationScore(File outputDir, JarAnalysisResult result,
                                         RestorationScorer scorer,
                                         RestorationScoreWriter scoreWriter,
                                         GapSummaryWriter gapSummaryWriter) throws IOException {
        RestorationScore score = scorer.score(result, result.getRuntimeTraceResult(), result.getVerificationResult());
        result.setRestorationScore(score);
        scoreWriter.write(outputDir, score);
        gapSummaryWriter.write(outputDir, score);
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
