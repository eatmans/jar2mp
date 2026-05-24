package com.z0fsec.jar2mp.core;

import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.api.printer.Printer;

public class JdCoreDecompilerEngine implements DecompilerEngine {

    @Override
    public String getName() {
        return "jd-core";
    }

    @Override
    public Result decompile(byte[] classBytes, String className) {
        try {
            String internalName = className.replace('.', '/');
            CapturingPrinter printer = new CapturingPrinter();
            new ClassFileToJavaSourceDecompiler().decompile(new SingleClassLoader(internalName, classBytes),
                    printer, internalName);

            String source = printer.getSource();
            if (DecompilerEngine.isStubSource(source)) {
                String failureMessage = "JD-Core returned empty or stub-only output.";
                return Result.failure(getName(),
                        DecompilerEngine.failureComment(className, failureMessage),
                        failureMessage,
                        DecompilerEngine.scoreSource(source));
            }
            return Result.success(getName(), source, DecompilerEngine.scoreSource(source));
        } catch (Exception e) {
            String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Result.failure(getName(),
                    DecompilerEngine.failureComment(className, failureMessage),
                    failureMessage,
                    0);
        }
    }

    private static class SingleClassLoader implements Loader {
        private final String internalName;
        private final byte[] classBytes;

        SingleClassLoader(String internalName, byte[] classBytes) {
            this.internalName = internalName;
            this.classBytes = classBytes;
        }

        @Override
        public boolean canLoad(String internalName) {
            return this.internalName.equals(stripClassSuffix(internalName));
        }

        @Override
        public byte[] load(String internalName) throws LoaderException {
            if (!canLoad(internalName)) {
                throw new LoaderException("Unsupported class: " + internalName);
            }
            return classBytes;
        }

        private String stripClassSuffix(String value) {
            if (value != null && value.endsWith(".class")) {
                return value.substring(0, value.length() - 6);
            }
            return value;
        }
    }

    static class CapturingPrinter implements Printer {
        private final StringBuilder source = new StringBuilder();
        private int indent;
        private boolean lineOpen;

        String getSource() {
            return source.toString();
        }

        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {
        }

        @Override
        public void end() {
        }

        @Override
        public void printText(String text) {
            append(text);
        }

        @Override
        public void printNumericConstant(String constant) {
            append(constant);
        }

        @Override
        public void printStringConstant(String constant, String ownerInternalName) {
            append(constant);
        }

        @Override
        public void printKeyword(String keyword) {
            append(keyword);
        }

        @Override
        public void printDeclaration(int type, String internalTypeName, String name, String descriptor) {
            append(name);
        }

        @Override
        public void printReference(int type, String internalTypeName, String name, String descriptor,
                                   String ownerInternalName) {
            append(name);
        }

        @Override
        public void indent() {
            indent++;
        }

        @Override
        public void unindent() {
            if (indent > 0) {
                indent--;
            }
        }

        @Override
        public void startLine(int lineNumber) {
            if (lineOpen) {
                source.append('\n');
            }
            for (int i = 0; i < indent; i++) {
                source.append("    ");
            }
            lineOpen = true;
        }

        @Override
        public void endLine() {
            source.append('\n');
            lineOpen = false;
        }

        @Override
        public void extraLine(int count) {
            for (int i = 0; i < count; i++) {
                source.append('\n');
            }
            lineOpen = false;
        }

        @Override
        public void startMarker(int type) {
        }

        @Override
        public void endMarker(int type) {
        }

        private void append(String value) {
            if (value != null) {
                source.append(value);
                lineOpen = true;
            }
        }
    }
}
