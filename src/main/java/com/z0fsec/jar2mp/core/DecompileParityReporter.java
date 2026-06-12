package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.DecompileFinding;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DecompileParityReporter {

    public void writeReport(JarFile jarFile, JarAnalysisResult analysis, File outputDir) throws IOException {
        StringBuilder report = new StringBuilder();
        StringBuilder classDetails = new StringBuilder();
        RiskSummary riskSummary = new RiskSummary();
        report.append("# Decompile parity report\n\n");
        report.append("This report compares recoverable bytecode facts from the original archive ")
                .append("with the generated source tree. It does not prove semantic equivalence; ")
                .append("it highlights control-flow, call, reflection, string, and local-variable evidence ")
                .append("that can be checked after decompilation.\n\n");

        for (String classPath : analysis.getClassFiles()) {
            String rawEntryPath = analysis.getClassPathMapping().get(classPath);
            if (rawEntryPath == null) {
                rawEntryPath = classPath;
            }
            JarEntry entry = jarFile.getJarEntry(rawEntryPath);
            if (entry == null) {
                continue;
            }
            riskSummary.classCount++;

            BytecodeFingerprint fingerprint;
            try {
                fingerprint = BytecodeFingerprint.fromClassFile(readAllBytes(jarFile, entry));
            } catch (Exception e) {
                riskSummary.parseFailures++;
                classDetails.append("## ").append(classNameFromPath(classPath)).append("\n\n")
                        .append("- Risk level: HIGH\n")
                        .append("- Unable to parse class file: ").append(e.getMessage()).append("\n\n");
                continue;
            }

            File sourceFile = resolveSourceFile(outputDir, classPath);
            String source = sourceFile.isFile() ? IoUtils.readFileToString(sourceFile) : "";
            DecompileFinding finding = findFinding(analysis, classPath);

            appendClassReport(classDetails, fingerprint, source, sourceFile.toPath(), finding, riskSummary);
        }

        appendRiskSummary(report, riskSummary);
        report.append(classDetails);
        IoUtils.writeStringToFile(new File(outputDir, "decompile-parity-report.md"), report.toString());
    }

    private void appendClassReport(StringBuilder report, BytecodeFingerprint fingerprint,
                                   String source, Path sourcePath, DecompileFinding finding,
                                   RiskSummary riskSummary) {
        report.append("## ").append(fingerprint.getClassName()).append("\n\n");
        report.append("- Selected engine: ").append(selectedEngine(finding)).append("\n");
        if (finding != null && finding.getEngineSummary() != null && !finding.getEngineSummary().trim().isEmpty()) {
            report.append("- Engine scores: ").append(finding.getEngineSummary().trim()).append("\n");
        }
        if (finding != null && finding.getFallbackReason() != null && !finding.getFallbackReason().trim().isEmpty()) {
            report.append("- Fallback reason: ").append(finding.getFallbackReason().trim()).append("\n");
        }
        report.append("- Source coverage: ").append(source.isEmpty() ? "missing" : "present").append("\n");
        report.append("- Source: ");
        if (source.isEmpty()) {
            report.append("missing or not generated");
        } else {
            report.append(sourcePath);
        }
        report.append("\n");
        report.append("- Methods: ").append(fingerprint.getMethodsByKey().size()).append("\n\n");
        appendList(report, "Fields", fingerprint.getFields());
        appendSet(report, "Annotations", fingerprint.getAnnotations());
        appendSet(report, "Generic signatures", fingerprint.getGenericSignatures());
        appendSet(report, "Bootstrap methods", fingerprint.getBootstrapMethods());
        report.append("\n");

        for (BytecodeFingerprint.MethodFingerprint method : fingerprint.getMethodsByKey().values()) {
            String riskLevel = riskLevel(method, source);
            riskSummary.record(method, source, riskLevel);
            report.append("### ").append(method.getKey()).append("\n\n");
            report.append("- Risk level: ").append(riskLevel).append("\n");
            if (!method.hasCode()) {
                report.append("- No Code attribute; abstract/native/synthetic-only method.\n\n");
                continue;
            }

            report.append("- Control flow: ")
                    .append(method.getBranchOpcodeCount())
                    .append(" branch opcode(s), ")
                    .append(method.getInstructions().size())
                    .append(" bytecode instruction(s).\n");
            report.append("- Exception handlers: ")
                    .append(method.getExceptionHandlerCount())
                    .append("\n");

            if (method.getLocalVariableNames().isEmpty()) {
                report.append("- Variable names: unavailable; original class has no LocalVariableTable debug metadata.\n");
            } else if (allNamesPresent(source, method.getLocalVariableNames())) {
                report.append("- Variable names: present in source (")
                        .append(join(method.getLocalVariableNames()))
                        .append(").\n");
            } else {
                report.append("- Variable names: missing from source. Original names: ")
                        .append(join(method.getLocalVariableNames()))
                        .append(".\n");
            }

            appendSet(report, "Method calls", method.getMethodCalls());
            appendSet(report, "Fields", method.getFieldReferences());
            appendSet(report, "String constants", method.getStringConstants());
            appendSet(report, "Invokedynamic", method.getInvokedynamicCalls());
            appendSet(report, "Annotations", method.getAnnotations());
            appendSet(report, "Generic signatures", method.getGenericSignatures());
            appendSet(report, "Thrown exceptions", method.getThrownExceptions());
            appendReflectionFindings(report, method);
            report.append("\n");
        }
    }

    private void appendRiskSummary(StringBuilder report, RiskSummary summary) {
        report.append("## Risk summary\n\n");
        report.append("- Classes scanned: ").append(summary.classCount).append("\n");
        report.append("- Methods scanned: ").append(summary.methodCount).append("\n");
        report.append("- Class parse failures: ").append(summary.parseFailures).append("\n");
        report.append("- Methods with missing source: ").append(summary.missingSourceMethods).append("\n");
        report.append("- Methods with reflection calls: ").append(summary.reflectionMethods).append("\n");
        report.append("- Methods with invokedynamic: ").append(summary.invokedynamicMethods).append("\n");
        report.append("- Methods without LocalVariableTable names: ")
                .append(summary.missingDebugNameMethods).append("\n\n");
        report.append("| Risk | Methods |\n");
        report.append("| --- | ---: |\n");
        report.append("| HIGH | ").append(summary.highMethods).append(" |\n");
        report.append("| MEDIUM | ").append(summary.mediumMethods).append(" |\n");
        report.append("| LOW | ").append(summary.lowMethods).append(" |\n\n");
    }

    private File resolveSourceFile(File outputDir, String classPath) {
        File exactSource = new File(outputDir, "src/main/java/" + classPath.replace(".class", ".java"));
        if (exactSource.isFile()) {
            return exactSource;
        }

        int slash = classPath.lastIndexOf('/');
        int dollar = classPath.indexOf('$', slash + 1);
        if (dollar < 0) {
            return exactSource;
        }

        String outerClassPath = classPath.substring(0, dollar) + ".class";
        File outerSource = new File(outputDir, "src/main/java/" + outerClassPath.replace(".class", ".java"));
        return outerSource.isFile() ? outerSource : exactSource;
    }

    private String riskLevel(BytecodeFingerprint.MethodFingerprint method, String source) {
        if (source.isEmpty()) {
            return "HIGH (source missing)";
        }
        if (!method.hasCode()) {
            return "LOW (no bytecode body; signature-only method)";
        }
        if (hasReflection(method)) {
            return "HIGH (reflection call detected)";
        }
        if (!method.getInvokedynamicCalls().isEmpty() || method.getLocalVariableNames().isEmpty()) {
            return "MEDIUM (dynamic bytecode or missing debug names)";
        }
        return "LOW (source and bytecode facts align for basic checks)";
    }

    private void appendReflectionFindings(StringBuilder report, BytecodeFingerprint.MethodFingerprint method) {
        List<String> reflectiveCalls = new ArrayList<>();
        for (String call : method.getMethodCalls()) {
            if (isReflectiveCall(call)) {
                reflectiveCalls.add(call);
            }
        }

        if (!reflectiveCalls.isEmpty()) {
            report.append("- Reflection: detected call(s): ").append(join(reflectiveCalls)).append(".\n");
        }
    }

    private boolean hasReflection(BytecodeFingerprint.MethodFingerprint method) {
        for (String call : method.getMethodCalls()) {
            if (isReflectiveCall(call)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReflectiveCall(String call) {
        return call.startsWith("java/lang/Class.forName") ||
                call.startsWith("java/lang/reflect/") ||
                call.contains(".getMethod(") ||
                call.contains(".getDeclaredMethod(") ||
                call.contains(".getField(") ||
                call.contains(".getDeclaredField(") ||
                call.contains(".invoke(");
    }

    private void appendList(StringBuilder report, String label, List<String> values) {
        report.append("- ").append(label).append(": ");
        if (values.isEmpty()) {
            report.append("none");
        } else {
            report.append(join(values));
        }
        report.append("\n");
    }

    private void appendSet(StringBuilder report, String label, Set<String> values) {
        report.append("- ").append(label).append(": ");
        if (values.isEmpty()) {
            report.append("none");
        } else {
            report.append(join(values));
        }
        report.append("\n");
    }

    private boolean allNamesPresent(String source, Set<String> names) {
        if (source.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (!source.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private String join(Collection<String> values) {
        return String.join(", ", values);
    }

    private String classNameFromPath(String classPath) {
        return classPath.replace(".class", "").replace('/', '.');
    }

    private DecompileFinding findFinding(JarAnalysisResult analysis, String classPath) {
        for (DecompileFinding finding : analysis.getDecompileFindings()) {
            if (classPath.equals(finding.getClassPath())) {
                return finding;
            }
        }
        return null;
    }

    private String selectedEngine(DecompileFinding finding) {
        if (finding == null || finding.getSelectedEngine() == null || finding.getSelectedEngine().trim().isEmpty()) {
            return "unknown";
        }
        return finding.getSelectedEngine().trim();
    }

    private byte[] readAllBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return bos.toByteArray();
        }
    }

    private static class RiskSummary {
        private int classCount;
        private int methodCount;
        private int parseFailures;
        private int highMethods;
        private int mediumMethods;
        private int lowMethods;
        private int missingSourceMethods;
        private int reflectionMethods;
        private int invokedynamicMethods;
        private int missingDebugNameMethods;

        private void record(BytecodeFingerprint.MethodFingerprint method, String source, String riskLevel) {
            methodCount++;
            if (riskLevel.startsWith("HIGH")) {
                highMethods++;
            } else if (riskLevel.startsWith("MEDIUM")) {
                mediumMethods++;
            } else {
                lowMethods++;
            }
            if (source == null || source.isEmpty()) {
                missingSourceMethods++;
            }
            if (!method.getInvokedynamicCalls().isEmpty()) {
                invokedynamicMethods++;
            }
            if (method.hasCode() && method.getLocalVariableNames().isEmpty()) {
                missingDebugNameMethods++;
            }
            for (String call : method.getMethodCalls()) {
                if (call.startsWith("java/lang/Class.forName")
                        || call.startsWith("java/lang/reflect/")
                        || call.contains(".getMethod(")
                        || call.contains(".getDeclaredMethod(")
                        || call.contains(".getField(")
                        || call.contains(".getDeclaredField(")
                        || call.contains(".invoke(")) {
                    reflectionMethods++;
                    break;
                }
            }
        }
    }
}
