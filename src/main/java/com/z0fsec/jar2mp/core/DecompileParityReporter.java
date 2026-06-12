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

    private static final String[] REFLECTIVE_CALL_PREFIXES = {
            "java/lang/Class.forName",
            "java/lang/Class.getMethod(",
            "java/lang/Class.getDeclaredMethod(",
            "java/lang/Class.getMethods(",
            "java/lang/Class.getDeclaredMethods(",
            "java/lang/Class.getField(",
            "java/lang/Class.getDeclaredField(",
            "java/lang/Class.getFields(",
            "java/lang/Class.getDeclaredFields(",
            "java/lang/Class.getConstructor(",
            "java/lang/Class.getDeclaredConstructor(",
            "java/lang/Class.getConstructors(",
            "java/lang/Class.getDeclaredConstructors(",
            "java/lang/Class.getAnnotation(",
            "java/lang/Class.getDeclaredAnnotation(",
            "java/lang/Class.getAnnotations(",
            "java/lang/Class.getDeclaredAnnotations(",
            "java/lang/reflect/"
    };

    private static final String[] REFLECTION_UTILITY_PREFIXES = {
            "cn/hutool/core/util/ReflectUtil.",
            "org/springframework/util/ReflectionUtils."
    };

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
        appendRiskMethodIndex(report, riskSummary);
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

        boolean syntheticSwitchMap = isSyntheticSwitchMapFinding(finding) || isSyntheticSwitchMapClass(fingerprint);
        for (BytecodeFingerprint.MethodFingerprint method : fingerprint.getMethodsByKey().values()) {
            String supportReason = compilerGeneratedSupportReason(fingerprint, method, syntheticSwitchMap);
            String riskLevel = riskLevel(method, source, supportReason);
            riskSummary.record(fingerprint.getClassName(), method, source, riskLevel, supportReason);
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

            if (supportReason != null && method.getLocalVariableNames().isEmpty()) {
                report.append("- Variable names: not required; ").append(supportReason).append(".\n");
            } else if (method.getLocalVariableNames().isEmpty() && method.requiresLocalVariableNames()) {
                report.append("- Variable names: unavailable; original class has no LocalVariableTable debug metadata.\n");
            } else if (method.getLocalVariableNames().isEmpty() && method.hasOnlyCompilerTempLocalStores()) {
                report.append("- Variable names: not required; local stores are compiler-generated monitor temporaries.\n");
            } else if (method.getLocalVariableNames().isEmpty()) {
                report.append("- Variable names: not required; bytecode has no user parameters or local stores.\n");
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
        report.append("Methods without LocalVariableTable names excludes bytecode bodies with no ")
                .append("user parameters and no local-variable stores, plus compiler-generated ")
                .append("synthetic switch-map support classes, bridge methods, enum support methods, ")
                .append("lambda deserialization support methods, outer-this constructors, and monitor temporaries.\n\n");
        report.append("| Risk | Methods |\n");
        report.append("| --- | ---: |\n");
        report.append("| HIGH | ").append(summary.highMethods).append(" |\n");
        report.append("| MEDIUM | ").append(summary.mediumMethods).append(" |\n");
        report.append("| LOW | ").append(summary.lowMethods).append(" |\n\n");
    }

    private void appendRiskMethodIndex(StringBuilder report, RiskSummary summary) {
        report.append("## Risk method index\n\n");
        if (summary.riskMethods.isEmpty()) {
            report.append("No HIGH or MEDIUM methods.\n\n");
            return;
        }

        report.append("| Risk | Class | Method | Reason |\n");
        report.append("| --- | --- | --- | --- |\n");
        for (RiskMethod riskMethod : summary.riskMethods) {
            report.append("| ")
                    .append(markdownCell(riskMethod.risk))
                    .append(" | `")
                    .append(markdownCode(riskMethod.className))
                    .append("` | `")
                    .append(markdownCode(riskMethod.methodKey))
                    .append("` | ")
                    .append(markdownCell(riskMethod.reason))
                    .append(" |\n");
        }
        report.append("\n");
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

    private String riskLevel(BytecodeFingerprint.MethodFingerprint method, String source, String supportReason) {
        if (source.isEmpty()) {
            return "HIGH (source missing)";
        }
        if (!method.hasCode()) {
            return "LOW (no bytecode body; signature-only method)";
        }
        if (supportReason != null) {
            return "LOW (" + supportReason + ")";
        }
        if (hasReflection(method)) {
            return "HIGH (reflection call detected)";
        }
        boolean hasInvokedynamic = !method.getInvokedynamicCalls().isEmpty();
        boolean missingDebugNames = method.requiresLocalVariableNames()
                && method.getLocalVariableNames().isEmpty();
        if (hasInvokedynamic && missingDebugNames) {
            return "MEDIUM (" + invokedynamicRiskReason(method) + " and missing debug names)";
        }
        if (hasInvokedynamic && method.hasOnlyStringConcatInvokedynamic()) {
            return "LOW (string-concat invokedynamic only)";
        }
        if (hasInvokedynamic) {
            return "MEDIUM (" + invokedynamicRiskReason(method) + ")";
        }
        if (missingDebugNames) {
            return "MEDIUM (missing debug names)";
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

    private static String invokedynamicRiskReason(BytecodeFingerprint.MethodFingerprint method) {
        boolean hasLambdaMetafactory = false;
        boolean hasOther = false;
        for (String call : method.getInvokedynamicCalls()) {
            if (call.contains("java/lang/invoke/LambdaMetafactory.")) {
                hasLambdaMetafactory = true;
            } else if (!call.contains("java/lang/invoke/StringConcatFactory.")) {
                hasOther = true;
            }
        }
        if (hasLambdaMetafactory && hasOther) {
            return "lambda metafactory and other invokedynamic";
        }
        if (hasLambdaMetafactory) {
            return "lambda metafactory invokedynamic";
        }
        return "invokedynamic";
    }

    private static boolean isReflectiveCall(String call) {
        return startsWithAny(call, REFLECTIVE_CALL_PREFIXES)
                || startsWithAny(call, REFLECTION_UTILITY_PREFIXES)
                || containsBootstrapMethodHandle(call, REFLECTIVE_CALL_PREFIXES)
                || containsBootstrapMethodHandle(call, REFLECTION_UTILITY_PREFIXES);
    }

    private static boolean startsWithAny(String call, String[] prefixes) {
        for (String prefix : prefixes) {
            if (call.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBootstrapMethodHandle(String call, String[] prefixes) {
        for (String prefix : prefixes) {
            if (call.contains("; args=handle:" + prefix) || call.contains(", handle:" + prefix)) {
                return true;
            }
        }
        return false;
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

    private String markdownCell(String value) {
        return value.replace("|", "\\|");
    }

    private String markdownCode(String value) {
        return value.replace("`", "\\`");
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

    private boolean isSyntheticSwitchMapFinding(DecompileFinding finding) {
        return "synthetic-switch-map".equals(selectedEngine(finding));
    }

    private static String compilerGeneratedSupportReason(BytecodeFingerprint fingerprint,
                                                         BytecodeFingerprint.MethodFingerprint method,
                                                         boolean syntheticSwitchMap) {
        if (syntheticSwitchMap) {
            return "compiler-generated synthetic switch-map support class";
        }
        if (isCompilerGeneratedBridgeMethod(method)) {
            return "compiler-generated bridge method";
        }
        if (isCompilerGeneratedEnumMethod(fingerprint, method)) {
            if ("<init>".equals(method.getName())) {
                return "compiler-generated enum constructor";
            }
            return "compiler-generated enum support method";
        }
        if (isLambdaDeserializationSupportMethod(method)) {
            return "compiler-generated lambda deserialization support method";
        }
        if (isOuterThisOnlyConstructor(fingerprint, method)) {
            return "compiler-generated outer-this constructor";
        }
        return null;
    }

    private static boolean isSyntheticSwitchMapClass(BytecodeFingerprint fingerprint) {
        boolean hasSwitchMapField = false;
        for (String field : fingerprint.getFields()) {
            if (field.startsWith("$SwitchMap$") && field.endsWith("[I")) {
                hasSwitchMapField = true;
                break;
            }
        }
        if (!hasSwitchMapField) {
            return false;
        }
        for (String methodKey : fingerprint.getMethodsByKey().keySet()) {
            if (!"<clinit>()V".equals(methodKey) && !"<init>()V".equals(methodKey)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompilerGeneratedBridgeMethod(BytecodeFingerprint.MethodFingerprint method) {
        return method.isBridge() && method.isSynthetic();
    }

    private static boolean isCompilerGeneratedEnumMethod(BytecodeFingerprint fingerprint,
                                                         BytecodeFingerprint.MethodFingerprint method) {
        if (!fingerprint.isEnumClass()) {
            return false;
        }
        String descriptor = method.getDescriptor();
        if ("<init>".equals(method.getName())) {
            return "(Ljava/lang/String;I)V".equals(descriptor);
        }
        String enumDescriptor = "L" + fingerprint.getClassName() + ";";
        if ("values".equals(method.getName())) {
            return ("()[" + enumDescriptor).equals(descriptor);
        }
        if ("valueOf".equals(method.getName())) {
            return ("(Ljava/lang/String;)" + enumDescriptor).equals(descriptor);
        }
        return "$values".equals(method.getName())
                && ("()[" + enumDescriptor).equals(descriptor);
    }

    private static boolean isLambdaDeserializationSupportMethod(BytecodeFingerprint.MethodFingerprint method) {
        return "$deserializeLambda$".equals(method.getName())
                && "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;".equals(method.getDescriptor());
    }

    private static boolean isOuterThisOnlyConstructor(BytecodeFingerprint fingerprint,
                                                      BytecodeFingerprint.MethodFingerprint method) {
        if (!"<init>".equals(method.getName())
                || !method.getDescriptor().startsWith("(L")
                || !method.getDescriptor().endsWith(";)V")) {
            return false;
        }
        String outerDescriptor = method.getDescriptor().substring(1, method.getDescriptor().length() - 2);
        if (!fingerprint.getFields().contains("this$0" + outerDescriptor)) {
            return false;
        }
        if (method.getBranchOpcodeCount() != 0
                || !method.getStringConstants().isEmpty()
                || !method.getInvokedynamicCalls().isEmpty()) {
            return false;
        }
        if (method.getMethodCalls().size() != 1
                || !method.getMethodCalls().contains("java/lang/Object.<init>()V")) {
            return false;
        }
        if (method.getFieldReferences().isEmpty()) {
            return false;
        }
        String outerThisField = ".this$0" + outerDescriptor;
        for (String fieldReference : method.getFieldReferences()) {
            if (!fieldReference.endsWith(outerThisField)) {
                return false;
            }
        }
        return true;
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
        private final List<RiskMethod> riskMethods = new ArrayList<>();

        private void record(String className, BytecodeFingerprint.MethodFingerprint method,
                            String source, String riskLevel, String supportReason) {
            methodCount++;
            if (riskLevel.startsWith("HIGH")) {
                highMethods++;
                riskMethods.add(RiskMethod.from(className, method.getKey(), riskLevel));
            } else if (riskLevel.startsWith("MEDIUM")) {
                mediumMethods++;
                riskMethods.add(RiskMethod.from(className, method.getKey(), riskLevel));
            } else {
                lowMethods++;
            }
            if (source == null || source.isEmpty()) {
                missingSourceMethods++;
            }
            if (!method.getInvokedynamicCalls().isEmpty()) {
                invokedynamicMethods++;
            }
            if (method.hasCode()
                    && method.requiresLocalVariableNames()
                    && method.getLocalVariableNames().isEmpty()
                    && supportReason == null) {
                missingDebugNameMethods++;
            }
            for (String call : method.getMethodCalls()) {
                if (isReflectiveCall(call)) {
                    reflectionMethods++;
                    break;
                }
            }
        }
    }

    private static class RiskMethod {
        private final String risk;
        private final String className;
        private final String methodKey;
        private final String reason;

        private RiskMethod(String risk, String className, String methodKey, String reason) {
            this.risk = risk;
            this.className = className;
            this.methodKey = methodKey;
            this.reason = reason;
        }

        private static RiskMethod from(String className, String methodKey, String riskLevel) {
            int separator = riskLevel.indexOf(' ');
            String risk = separator < 0 ? riskLevel : riskLevel.substring(0, separator);
            int start = riskLevel.indexOf('(');
            int end = riskLevel.lastIndexOf(')');
            String reason = start >= 0 && end > start ? riskLevel.substring(start + 1, end) : riskLevel;
            return new RiskMethod(risk, className, methodKey, reason);
        }
    }
}
