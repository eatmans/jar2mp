package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.util.ClassFileUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class BytecodeFingerprint {

    private static final Set<Integer> BRANCH_OPCODES = new HashSet<>(Arrays.asList(
            0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e,
            0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4,
            0xa5, 0xa6, 0xa7, 0xa8, 0xaa, 0xab,
            0xc6, 0xc7, 0xc8, 0xc9));

    private final String className;
    private final Map<String, MethodFingerprint> methodsByKey;

    private BytecodeFingerprint(String className, Map<String, MethodFingerprint> methodsByKey) {
        this.className = className;
        this.methodsByKey = methodsByKey;
    }

    public static BytecodeFingerprint fromClassFile(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        return reader.read();
    }

    public String getClassName() {
        return className;
    }

    public Map<String, MethodFingerprint> getMethodsByKey() {
        return methodsByKey;
    }

    public static class MethodFingerprint {
        private final String name;
        private final String descriptor;
        private final List<String> instructions;
        private final Set<String> methodCalls;
        private final Set<String> stringConstants;
        private final Set<String> localVariableNames;
        private final int branchOpcodeCount;
        private final int lineNumberCount;
        private final boolean hasCode;

        private MethodFingerprint(String name, String descriptor, List<String> instructions,
                                  Set<String> methodCalls, Set<String> stringConstants,
                                  Set<String> localVariableNames, int branchOpcodeCount,
                                  int lineNumberCount, boolean hasCode) {
            this.name = name;
            this.descriptor = descriptor;
            this.instructions = instructions;
            this.methodCalls = methodCalls;
            this.stringConstants = stringConstants;
            this.localVariableNames = localVariableNames;
            this.branchOpcodeCount = branchOpcodeCount;
            this.lineNumberCount = lineNumberCount;
            this.hasCode = hasCode;
        }

        public String getKey() {
            return name + descriptor;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public List<String> getInstructions() {
            return instructions;
        }

        public Set<String> getMethodCalls() {
            return methodCalls;
        }

        public Set<String> getStringConstants() {
            return stringConstants;
        }

        public Set<String> getLocalVariableNames() {
            return localVariableNames;
        }

        public int getBranchOpcodeCount() {
            return branchOpcodeCount;
        }

        public int getLineNumberCount() {
            return lineNumberCount;
        }

        public boolean hasCode() {
            return hasCode;
        }
    }

    private static class ClassReader {
        private final byte[] data;
        private CpInfo[] constantPool;
        private int offset;

        private ClassReader(byte[] data) {
            this.data = data;
        }

        private BytecodeFingerprint read() {
            if (data == null || data.length < 10 || ClassFileUtils.readU4(data, 0) != 0xCAFEBABE) {
                throw new IllegalArgumentException("Invalid class file");
            }

            offset = 8;
            readConstantPool();
            offset += 2; // access_flags
            int thisClassIndex = readU2();
            offset += 2; // super_class

            skipInterfaces();
            skipMembers();
            Map<String, MethodFingerprint> methods = readMethods();
            return new BytecodeFingerprint(resolveClassName(thisClassIndex), methods);
        }

        private void readConstantPool() {
            int constantPoolCount = readU2();
            constantPool = new CpInfo[constantPoolCount];
            for (int i = 1; i < constantPoolCount; i++) {
                int tag = readU1();
                CpInfo info = new CpInfo(tag);
                constantPool[i] = info;
                switch (tag) {
                    case 1:
                        int length = readU2();
                        info.text = readUtf8(length);
                        break;
                    case 3:
                    case 4:
                        offset += 4;
                        break;
                    case 5:
                    case 6:
                        offset += 8;
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        info.index1 = readU2();
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        info.index1 = readU2();
                        info.index2 = readU2();
                        break;
                    case 15:
                        offset += 1;
                        info.index1 = readU2();
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
                }
            }
        }

        private void skipInterfaces() {
            int count = readU2();
            offset += count * 2;
        }

        private void skipMembers() {
            int fieldsCount = readU2();
            for (int i = 0; i < fieldsCount; i++) {
                skipMember();
            }
        }

        private Map<String, MethodFingerprint> readMethods() {
            Map<String, MethodFingerprint> methods = new LinkedHashMap<>();
            int methodsCount = readU2();
            for (int i = 0; i < methodsCount; i++) {
                offset += 2; // access_flags
                String name = utf8(readU2());
                String descriptor = utf8(readU2());
                MethodFingerprint method = readMethodAttributes(name, descriptor);
                methods.put(method.getKey(), method);
            }
            return methods;
        }

        private MethodFingerprint readMethodAttributes(String name, String descriptor) {
            List<String> instructions = Collections.emptyList();
            Set<String> methodCalls = new LinkedHashSet<>();
            Set<String> stringConstants = new LinkedHashSet<>();
            Set<String> localVariableNames = new LinkedHashSet<>();
            int branchOpcodeCount = 0;
            int lineNumberCount = 0;
            boolean hasCode = false;

            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                String attributeName = utf8(readU2());
                int attributeLength = readU4();
                int attributeEnd = offset + attributeLength;

                if ("Code".equals(attributeName)) {
                    hasCode = true;
                    CodeFingerprint code = readCodeAttribute();
                    instructions = code.instructions;
                    methodCalls.addAll(code.methodCalls);
                    stringConstants.addAll(code.stringConstants);
                    localVariableNames.addAll(code.localVariableNames);
                    branchOpcodeCount = code.branchOpcodeCount;
                    lineNumberCount = code.lineNumberCount;
                }

                offset = attributeEnd;
            }

            return new MethodFingerprint(name, descriptor, instructions, methodCalls, stringConstants,
                    localVariableNames, branchOpcodeCount, lineNumberCount, hasCode);
        }

        private CodeFingerprint readCodeAttribute() {
            offset += 2; // max_stack
            offset += 2; // max_locals
            int codeLength = readU4();
            byte[] code = Arrays.copyOfRange(data, offset, offset + codeLength);
            offset += codeLength;

            CodeFingerprint fingerprint = scanCode(code);

            int exceptionTableLength = readU2();
            offset += exceptionTableLength * 8;

            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                String attributeName = utf8(readU2());
                int attributeLength = readU4();
                int attributeEnd = offset + attributeLength;

                if ("LocalVariableTable".equals(attributeName)) {
                    int localVariableCount = readU2();
                    for (int j = 0; j < localVariableCount; j++) {
                        offset += 4; // start_pc + length
                        String variableName = utf8(readU2());
                        offset += 2; // descriptor_index
                        offset += 2; // index
                        if (!"this".equals(variableName)) {
                            fingerprint.localVariableNames.add(variableName);
                        }
                    }
                } else if ("LineNumberTable".equals(attributeName)) {
                    int lineNumberCount = readU2();
                    fingerprint.lineNumberCount += lineNumberCount;
                    offset += lineNumberCount * 4;
                }

                offset = attributeEnd;
            }

            return fingerprint;
        }

        private CodeFingerprint scanCode(byte[] code) {
            CodeFingerprint fingerprint = new CodeFingerprint();
            int position = 0;
            while (position < code.length) {
                int opcode = code[position] & 0xFF;
                fingerprint.instructions.add(String.format(Locale.ROOT, "%04d:%s", position, opcodeName(opcode)));
                if (BRANCH_OPCODES.contains(opcode)) {
                    fingerprint.branchOpcodeCount++;
                }

                switch (opcode) {
                    case 0x12:
                        addConstantUsage(fingerprint, code[position + 1] & 0xFF);
                        position += 2;
                        break;
                    case 0x13:
                    case 0x14:
                        addConstantUsage(fingerprint, BytecodeFingerprint.readU2(code, position + 1));
                        position += 3;
                        break;
                    case 0xb6:
                    case 0xb7:
                    case 0xb8:
                        fingerprint.methodCalls.add(resolveMethodReference(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 3;
                        break;
                    case 0xb9:
                        fingerprint.methodCalls.add(resolveMethodReference(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 5;
                        break;
                    case 0xba:
                        fingerprint.methodCalls.add(resolveInvokeDynamic(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 5;
                        break;
                    case 0xaa:
                        position = skipTableSwitch(code, position);
                        break;
                    case 0xab:
                        position = skipLookupSwitch(code, position);
                        break;
                    case 0xc4:
                        position += wideInstructionLength(code, position);
                        break;
                    default:
                        position += instructionLength(opcode);
                        break;
                }
            }
            return fingerprint;
        }

        private void addConstantUsage(CodeFingerprint fingerprint, int constantIndex) {
            CpInfo info = cp(constantIndex);
            if (info == null) {
                return;
            }
            if (info.tag == 8) {
                fingerprint.stringConstants.add(utf8(info.index1));
            } else if (info.tag == 7) {
                fingerprint.stringConstants.add(resolveClassName(constantIndex));
            }
        }

        private void skipMember() {
            offset += 2; // access_flags
            offset += 2; // name_index
            offset += 2; // descriptor_index
            skipAttributes();
        }

        private void skipAttributes() {
            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                offset += 2;
                int length = readU4();
                offset += length;
            }
        }

        private String resolveMethodReference(int index) {
            CpInfo methodRef = cp(index);
            if (methodRef == null || (methodRef.tag != 10 && methodRef.tag != 11)) {
                return "#" + index;
            }
            String owner = resolveClassName(methodRef.index1);
            CpInfo nameAndType = cp(methodRef.index2);
            if (nameAndType == null) {
                return owner + ".#" + methodRef.index2;
            }
            return owner + "." + utf8(nameAndType.index1) + utf8(nameAndType.index2);
        }

        private String resolveInvokeDynamic(int index) {
            CpInfo invokeDynamic = cp(index);
            if (invokeDynamic == null || invokeDynamic.tag != 18) {
                return "invokedynamic#" + index;
            }
            CpInfo nameAndType = cp(invokeDynamic.index2);
            if (nameAndType == null) {
                return "invokedynamic#" + index;
            }
            return "invokedynamic." + utf8(nameAndType.index1) + utf8(nameAndType.index2);
        }

        private String resolveClassName(int index) {
            CpInfo classInfo = cp(index);
            if (classInfo == null || classInfo.tag != 7) {
                return "#" + index;
            }
            return utf8(classInfo.index1);
        }

        private String utf8(int index) {
            CpInfo info = cp(index);
            return info == null || info.text == null ? "" : info.text;
        }

        private CpInfo cp(int index) {
            if (index <= 0 || index >= constantPool.length) {
                return null;
            }
            return constantPool[index];
        }

        private int readU1() {
            return data[offset++] & 0xFF;
        }

        private int readU2() {
            int value = BytecodeFingerprint.readU2(data, offset);
            offset += 2;
            return value;
        }

        private int readU4() {
            int value = ClassFileUtils.readU4(data, offset);
            offset += 4;
            return value;
        }

        private String readUtf8(int length) {
            try {
                String value = new String(data, offset, length, "UTF-8");
                offset += length;
                return value;
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class CodeFingerprint {
        private final List<String> instructions = new ArrayList<>();
        private final Set<String> methodCalls = new LinkedHashSet<>();
        private final Set<String> stringConstants = new LinkedHashSet<>();
        private final Set<String> localVariableNames = new LinkedHashSet<>();
        private int branchOpcodeCount;
        private int lineNumberCount;
    }

    private static class CpInfo {
        private final int tag;
        private int index1;
        private int index2;
        private String text;

        private CpInfo(int tag) {
            this.tag = tag;
        }
    }

    private static int readU2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int skipTableSwitch(byte[] code, int position) {
        int cursor = position + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        cursor += 4; // default
        int low = readInt(code, cursor);
        cursor += 4;
        int high = readInt(code, cursor);
        cursor += 4;
        return cursor + ((high - low + 1) * 4);
    }

    private static int skipLookupSwitch(byte[] code, int position) {
        int cursor = position + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        cursor += 4; // default
        int npairs = readInt(code, cursor);
        cursor += 4;
        return cursor + (npairs * 8);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }

    private static int wideInstructionLength(byte[] code, int position) {
        int modifiedOpcode = code[position + 1] & 0xFF;
        return modifiedOpcode == 0x84 ? 6 : 4;
    }

    private static int instructionLength(int opcode) {
        switch (opcode) {
            case 0x10:
            case 0x12:
            case 0x15:
            case 0x16:
            case 0x17:
            case 0x18:
            case 0x19:
            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3a:
            case 0xa9:
            case 0xbc:
                return 2;
            case 0x11:
            case 0x13:
            case 0x14:
            case 0x84:
            case 0x99:
            case 0x9a:
            case 0x9b:
            case 0x9c:
            case 0x9d:
            case 0x9e:
            case 0x9f:
            case 0xa0:
            case 0xa1:
            case 0xa2:
            case 0xa3:
            case 0xa4:
            case 0xa5:
            case 0xa6:
            case 0xa7:
            case 0xa8:
            case 0xbb:
            case 0xbd:
            case 0xc0:
            case 0xc1:
            case 0xc6:
            case 0xc7:
                return 3;
            case 0xb2:
            case 0xb3:
            case 0xb4:
            case 0xb5:
            case 0xb6:
            case 0xb7:
            case 0xb8:
            case 0xc5:
                return 3;
            case 0xc8:
            case 0xc9:
                return 5;
            case 0xb9:
            case 0xba:
                return 5;
            default:
                return 1;
        }
    }

    private static String opcodeName(int opcode) {
        switch (opcode) {
            case 0x12:
                return "ldc";
            case 0x13:
                return "ldc_w";
            case 0x14:
                return "ldc2_w";
            case 0x99:
                return "ifeq";
            case 0x9a:
                return "ifne";
            case 0x9b:
                return "iflt";
            case 0x9c:
                return "ifge";
            case 0x9d:
                return "ifgt";
            case 0x9e:
                return "ifle";
            case 0x9f:
                return "if_icmpeq";
            case 0xa0:
                return "if_icmpne";
            case 0xa1:
                return "if_icmplt";
            case 0xa2:
                return "if_icmpge";
            case 0xa3:
                return "if_icmpgt";
            case 0xa4:
                return "if_icmple";
            case 0xa5:
                return "if_acmpeq";
            case 0xa6:
                return "if_acmpne";
            case 0xa7:
                return "goto";
            case 0xaa:
                return "tableswitch";
            case 0xab:
                return "lookupswitch";
            case 0xb6:
                return "invokevirtual";
            case 0xb7:
                return "invokespecial";
            case 0xb8:
                return "invokestatic";
            case 0xb9:
                return "invokeinterface";
            case 0xba:
                return "invokedynamic";
            case 0xc6:
                return "ifnull";
            case 0xc7:
                return "ifnonnull";
            default:
                return "op_" + Integer.toHexString(opcode);
        }
    }
}
