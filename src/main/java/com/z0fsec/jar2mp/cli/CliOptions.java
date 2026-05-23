package com.z0fsec.jar2mp.cli;

import com.z0fsec.jar2mp.model.ProjectConfig;

import java.util.ArrayList;
import java.util.List;

public class CliOptions {
    private final List<String> inputFiles = new ArrayList<>();
    private ProjectConfig config = new ProjectConfig();
    private boolean quiet = false;
    private boolean verbose = false;
    private boolean showHelp = false;
    private boolean showVersion = false;
    private String exportDepsFile;
    private String importDepsFile;
    private String traceFile;

    public List<String> getInputFiles() { return inputFiles; }
    public void addInputFile(String file) { this.inputFiles.add(file); }
    public ProjectConfig getConfig() { return config; }
    public void setConfig(ProjectConfig config) { this.config = config; }
    public boolean isQuiet() { return quiet; }
    public void setQuiet(boolean quiet) { this.quiet = quiet; }
    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public boolean isShowHelp() { return showHelp; }
    public void setShowHelp(boolean showHelp) { this.showHelp = showHelp; }
    public boolean isShowVersion() { return showVersion; }
    public void setShowVersion(boolean showVersion) { this.showVersion = showVersion; }
    public String getExportDepsFile() { return exportDepsFile; }
    public void setExportDepsFile(String exportDepsFile) { this.exportDepsFile = exportDepsFile; }
    public String getImportDepsFile() { return importDepsFile; }
    public void setImportDepsFile(String importDepsFile) { this.importDepsFile = importDepsFile; }
    public String getTraceFile() { return traceFile; }
    public void setTraceFile(String traceFile) { this.traceFile = traceFile; }

    public static void printHelp() {
        System.out.println("Usage: java -jar jar2mp.jar [options] <jar-or-war-files...>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <jar-or-war-files...>           JAR/WAR 文件或目录路径（支持多个，目录自动扫描）");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o, --output <dir>              输出目录（默认当前目录）");
        System.out.println("  -g, --groupId <groupId>         覆盖检测到的 groupId");
        System.out.println("  -a, --artifactId <artifactId>   覆盖检测到的 artifactId");
        System.out.println("  -v, --version <version>         覆盖检测到的 version");
        System.out.println("  -j, --java-version <version>    目标 Java 版本（默认自动检测）");
        System.out.println("  -p, --packaging <type>          打包类型: jar / war（默认自动检测）");
        System.out.println("      --no-decompile              跳过反编译，直接复制 .class 文件");
        System.out.println("      --no-dependencies           跳过依赖检测");
        System.out.println("      --no-resources              跳过资源文件提取");
        System.out.println("      --mapping-file <file>       自定义包名映射文件");
        System.out.println("      --aggressive-scan           激进扫描模式");
        System.out.println("      --include-synthetic         包含合成/桥接方法");
        System.out.println("      --export-deps <file>        导出依赖到文件");
        System.out.println("      --import-deps <file>        从文件导入依赖");
        System.out.println("      --verify-build              生成项目后运行 Maven 构建验证");
        System.out.println("      --verify-goal <goal>        构建验证 Maven goal（默认 compile）");
        System.out.println("  -f, --force                     覆盖已存在的输出目录");
        System.out.println("  -q, --quiet                     静默模式");
        System.out.println("      --verbose                   详细输出");
        System.out.println("  -h, --help                      显示帮助");
        System.out.println("      --version                   显示版本号");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  每个项目会生成 decompile-parity-report.md 和 resource-inventory.md。");
        System.out.println("  启用 --verify-build 后额外生成 verification-report.md。");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar jar2mp.jar target/app.jar");
        System.out.println("  java -jar jar2mp.jar a.jar b.jar c.jar --verbose");
        System.out.println("  java -jar jar2mp.jar /path/to/libs/ -o /tmp/output");
        System.out.println("  java -jar jar2mp.jar -o /tmp/project -g com.example app.jar");
    }
}
