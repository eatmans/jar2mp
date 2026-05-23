package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.util.IoUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DecompileParityReporter {

    public void writeReport(JarFile jarFile, JarAnalysisResult analysis, File outputDir) throws IOException {
        StringBuilder report = new StringBuilder();
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

            BytecodeFingerprint fingerprint;
            try {
                fingerprint = BytecodeFingerprint.fromClassFile(readAllBytes(jarFile, entry));
            } catch (Exception e) {
                report.append("## ").append(classNameFromPath(classPath)).append("\n\n")
                        .append("- Unable to parse class file: ").append(e.getMessage()).append("\n\n");
                continue;
            }

            File sourceFile = new File(outputDir, "src/main/java/" + classPath.replace(".class", ".java"));
            String source = sourceFile.isFile() ? IoUtils.readFileToString(sourceFile) : "";

            appendClassReport(report, fingerprint, source, sourceFile.toPath());
        }

        IoUtils.writeStringToFile(new File(outputDir, "decompile-parity-report.md"), report.toString());
    }

    private void appendClassReport(StringBuilder report, BytecodeFingerprint fingerprint,
                                   String source, Path sourcePath) {
        report.append("## ").append(fingerprint.getClassName()).append("\n\n");
        report.append("- Source: ");
        if (source.isEmpty()) {
            report.append("missing or not generated");
        } else {
            report.append(sourcePath);
        }
        report.append("\n");
        report.append("- Methods: ").append(fingerprint.getMethodsByKey().size()).append("\n\n");

        for (BytecodeFingerprint.MethodFingerprint method : fingerprint.getMethodsByKey().values()) {
            report.append("### ").append(method.getKey()).append("\n\n");
            if (!method.hasCode()) {
                report.append("- No Code attribute; abstract/native/synthetic-only method.\n\n");
                continue;
            }

            report.append("- Control flow: ")
                    .append(method.getBranchOpcodeCount())
                    .append(" branch opcode(s), ")
                    .append(method.getInstructions().size())
                    .append(" bytecode instruction(s).\n");

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
            appendSet(report, "String constants", method.getStringConstants());
            appendReflectionFindings(report, method);
            report.append("\n");
        }
    }

    private void appendReflectionFindings(StringBuilder report, BytecodeFingerprint.MethodFingerprint method) {
        List<String> reflectiveCalls = new ArrayList<>();
        for (String call : method.getMethodCalls()) {
            if (call.startsWith("java/lang/Class.forName") ||
                    call.startsWith("java/lang/reflect/") ||
                    call.contains(".getMethod(") ||
                    call.contains(".getDeclaredMethod(") ||
                    call.contains(".getField(") ||
                    call.contains(".getDeclaredField(") ||
                    call.contains(".invoke(")) {
                reflectiveCalls.add(call);
            }
        }

        if (!reflectiveCalls.isEmpty()) {
            report.append("- Reflection: detected call(s): ").append(join(reflectiveCalls)).append(".\n");
        }
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
}
