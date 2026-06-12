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
    private final List<String> fields;
    private final Set<String> annotations;
    private final Set<String> genericSignatures;
    private final Set<String> bootstrapMethods;
    private final Map<String, MethodFingerprint> methodsByKey;
    private final int accessFlags;

    private BytecodeFingerprint(String className, List<String> fields, Set<String> annotations,
                                Set<String> genericSignatures, Set<String> bootstrapMethods,
                                Map<String, MethodFingerprint> methodsByKey, int accessFlags) {
        this.className = className;
        this.fields = fields;
        this.annotations = annotations;
        this.genericSignatures = genericSignatures;
        this.bootstrapMethods = bootstrapMethods;
        this.methodsByKey = methodsByKey;
        this.accessFlags = accessFlags;
    }

    public static BytecodeFingerprint fromClassFile(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        return reader.read();
    }

    public String getClassName() {
        return className;
    }

    public List<String> getFields() {
        return fields;
    }

    public Set<String> getAnnotations() {
        return annotations;
    }

    public Set<String> getGenericSignatures() {
        return genericSignatures;
    }

    public Set<String> getBootstrapMethods() {
        return bootstrapMethods;
    }

    public Map<String, MethodFingerprint> getMethodsByKey() {
        return methodsByKey;
    }

    public boolean isEnumClass() {
        return (accessFlags & 0x4000) != 0;
    }

    public static class MethodFingerprint {
        private final String name;
        private final String descriptor;
        private final List<String> instructions;
        private final Set<String> methodCalls;
        private final Set<String> stringConstants;
        private final Set<String> localVariableNames;
        private final Set<String> fieldReferences;
        private final Set<String> annotations;
        private final Set<String> thrownExceptions;
        private final Set<String> invokedynamicCalls;
        private final Set<String> genericSignatures;
        private final Set<Integer> localStoreSlots;
        private final Set<Integer> compilerTempLocalStoreSlots;
        private final int exceptionHandlerCount;
        private final int branchOpcodeCount;
        private final int lineNumberCount;
        private final boolean hasCode;
        private final int accessFlags;

        private MethodFingerprint(String name, String descriptor, List<String> instructions,
                                  Set<String> methodCalls, Set<String> stringConstants,
                                  Set<String> localVariableNames, Set<String> fieldReferences,
                                  Set<String> annotations, Set<String> thrownExceptions,
                                  Set<String> invokedynamicCalls, Set<String> genericSignatures,
                                  Set<Integer> localStoreSlots, Set<Integer> compilerTempLocalStoreSlots,
                                  int exceptionHandlerCount, int branchOpcodeCount,
                                  int lineNumberCount, boolean hasCode, int accessFlags) {
            this.name = name;
            this.descriptor = descriptor;
            this.instructions = instructions;
            this.methodCalls = methodCalls;
            this.stringConstants = stringConstants;
            this.localVariableNames = localVariableNames;
            this.fieldReferences = fieldReferences;
            this.annotations = annotations;
            this.thrownExceptions = thrownExceptions;
            this.invokedynamicCalls = invokedynamicCalls;
            this.genericSignatures = genericSignatures;
            this.localStoreSlots = localStoreSlots;
            this.compilerTempLocalStoreSlots = compilerTempLocalStoreSlots;
            this.exceptionHandlerCount = exceptionHandlerCount;
            this.branchOpcodeCount = branchOpcodeCount;
            this.lineNumberCount = lineNumberCount;
            this.hasCode = hasCode;
            this.accessFlags = accessFlags;
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

        public Set<String> getFieldReferences() {
            return fieldReferences;
        }

        public Set<String> getAnnotations() {
            return annotations;
        }

        public Set<String> getThrownExceptions() {
            return thrownExceptions;
        }

        public Set<String> getInvokedynamicCalls() {
            return invokedynamicCalls;
        }

        public Set<String> getGenericSignatures() {
            return genericSignatures;
        }

        public int getExceptionHandlerCount() {
            return exceptionHandlerCount;
        }

        public int getBranchOpcodeCount() {
            return branchOpcodeCount;
        }

        public int getLineNumberCount() {
            return lineNumberCount;
        }

        public boolean requiresLocalVariableNames() {
            if (!hasCode) {
                return false;
            }
            if (parameterSlotCount() > 0) {
                return true;
            }
            int firstUserLocalSlot = isStatic() ? 0 : 1;
            for (Integer slot : localStoreSlots) {
                if (slot != null && slot >= firstUserLocalSlot && !compilerTempLocalStoreSlots.contains(slot)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasOnlyCompilerTempLocalStores() {
            if (!hasCode || parameterSlotCount() > 0) {
                return false;
            }
            int firstUserLocalSlot = isStatic() ? 0 : 1;
            boolean foundCompilerTemp = false;
            for (Integer slot : localStoreSlots) {
                if (slot != null && slot >= firstUserLocalSlot) {
                    if (!compilerTempLocalStoreSlots.contains(slot)) {
                        return false;
                    }
                    foundCompilerTemp = true;
                }
            }
            return foundCompilerTemp;
        }

        public boolean hasOnlyStringConcatInvokedynamic() {
            if (invokedynamicCalls.isEmpty()) {
                return false;
            }
            for (String call : invokedynamicCalls) {
                if (!(call.startsWith("invokedynamic.makeConcat")
                        && call.contains(")Ljava/lang/String;")
                        && call.contains("[bootstrap=java/lang/invoke/StringConcatFactory.makeConcat"))) {
                    return false;
                }
            }
            return true;
        }

        public boolean hasCode() {
            return hasCode;
        }

        public boolean isBridge() {
            return (accessFlags & 0x0040) != 0;
        }

        public boolean isSynthetic() {
            return (accessFlags & 0x1000) != 0;
        }

        private boolean isStatic() {
            return (accessFlags & 0x0008) != 0;
        }

        private int parameterSlotCount() {
            int count = 0;
            int cursor = descriptor.indexOf('(') + 1;
            while (cursor > 0 && cursor < descriptor.length() && descriptor.charAt(cursor) != ')') {
                char type = descriptor.charAt(cursor);
                if (type == '[') {
                    while (cursor < descriptor.length() && descriptor.charAt(cursor) == '[') {
                        cursor++;
                    }
                    if (cursor < descriptor.length() && descriptor.charAt(cursor) == 'L') {
                        cursor = descriptor.indexOf(';', cursor) + 1;
                    } else {
                        cursor++;
                    }
                    count++;
                } else if (type == 'L') {
                    cursor = descriptor.indexOf(';', cursor) + 1;
                    count++;
                } else {
                    cursor++;
                    count += type == 'J' || type == 'D' ? 2 : 1;
                }
            }
            return count;
        }
    }

    private static class ClassReader {
        private final byte[] data;
        private CpInfo[] constantPool;
        private List<BootstrapMethodInfo> bootstrapMethodsByIndex = Collections.emptyList();
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
            int classBodyOffset = offset;
            readBootstrapMethodsBeforeMembers(classBodyOffset);
            int accessFlags = readU2();
            int thisClassIndex = readU2();
            offset += 2; // super_class

            skipInterfaces();
            List<String> fields = readFields();
            Map<String, MethodFingerprint> methods = readMethods();
            ClassAttributes attributes = readClassAttributes();
            return new BytecodeFingerprint(resolveClassName(thisClassIndex), fields, attributes.annotations,
                    attributes.genericSignatures, attributes.bootstrapMethods, methods, accessFlags);
        }

        private void readBootstrapMethodsBeforeMembers(int classBodyOffset) {
            int savedOffset = offset;
            offset = classBodyOffset;
            offset += 6; // access_flags + this_class + super_class
            skipInterfaces();
            skipMembers(); // fields
            skipMembers(); // methods
            bootstrapMethodsByIndex = readBootstrapMethodsFromClassAttributes();
            offset = savedOffset;
        }

        private List<BootstrapMethodInfo> readBootstrapMethodsFromClassAttributes() {
            List<BootstrapMethodInfo> methods = new ArrayList<>();
            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                String attributeName = utf8(readU2());
                int attributeLength = readU4();
                int attributeEnd = offset + attributeLength;
                if ("BootstrapMethods".equals(attributeName)) {
                    int bootstrapCount = readU2();
                    for (int j = 0; j < bootstrapCount; j++) {
                        String reference = resolveMethodHandle(readU2());
                        int argumentCount = readU2();
                        List<String> arguments = new ArrayList<>();
                        for (int k = 0; k < argumentCount; k++) {
                            arguments.add(resolveBootstrapArgument(readU2()));
                        }
                        methods.add(new BootstrapMethodInfo(reference, arguments));
                    }
                }
                offset = attributeEnd;
            }
            return methods;
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

        private List<String> readFields() {
            List<String> fields = new ArrayList<>();
            int fieldsCount = readU2();
            for (int i = 0; i < fieldsCount; i++) {
                offset += 2; // access_flags
                String name = utf8(readU2());
                String descriptor = utf8(readU2());
                FieldAttributes attributes = readFieldAttributes();
                fields.add(name + descriptor);
                fields.addAll(attributes.genericSignatures);
                fields.addAll(attributes.annotations);
            }
            return fields;
        }

        private FieldAttributes readFieldAttributes() {
            FieldAttributes attributes = new FieldAttributes();
            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                String attributeName = utf8(readU2());
                int attributeLength = readU4();
                int attributeEnd = offset + attributeLength;
                if ("Signature".equals(attributeName)) {
                    attributes.genericSignatures.add(utf8(readU2()));
                } else if ("RuntimeVisibleAnnotations".equals(attributeName)
                        || "RuntimeInvisibleAnnotations".equals(attributeName)) {
                    readAnnotations(attributes.annotations);
                }
                offset = attributeEnd;
            }
            return attributes;
        }

        private Map<String, MethodFingerprint> readMethods() {
            Map<String, MethodFingerprint> methods = new LinkedHashMap<>();
            int methodsCount = readU2();
            for (int i = 0; i < methodsCount; i++) {
                int accessFlags = readU2();
                String name = utf8(readU2());
                String descriptor = utf8(readU2());
                MethodFingerprint method = readMethodAttributes(name, descriptor, accessFlags);
                methods.put(method.getKey(), method);
            }
            return methods;
        }

        private MethodFingerprint readMethodAttributes(String name, String descriptor, int accessFlags) {
            List<String> instructions = Collections.emptyList();
            Set<String> methodCalls = new LinkedHashSet<>();
            Set<String> stringConstants = new LinkedHashSet<>();
            Set<String> localVariableNames = new LinkedHashSet<>();
            Set<String> fieldReferences = new LinkedHashSet<>();
            Set<String> annotations = new LinkedHashSet<>();
            Set<String> thrownExceptions = new LinkedHashSet<>();
            Set<String> invokedynamicCalls = new LinkedHashSet<>();
            Set<String> genericSignatures = new LinkedHashSet<>();
            Set<Integer> localStoreSlots = new LinkedHashSet<>();
            Set<Integer> compilerTempLocalStoreSlots = new LinkedHashSet<>();
            int exceptionHandlerCount = 0;
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
                    fieldReferences.addAll(code.fieldReferences);
                    invokedynamicCalls.addAll(code.invokedynamicCalls);
                    localStoreSlots.addAll(code.localStoreSlots);
                    compilerTempLocalStoreSlots.addAll(code.compilerTempLocalStoreSlots);
                    exceptionHandlerCount = code.exceptionHandlerCount;
                    branchOpcodeCount = code.branchOpcodeCount;
                    lineNumberCount = code.lineNumberCount;
                } else if ("Exceptions".equals(attributeName)) {
                    int exceptionCount = readU2();
                    for (int j = 0; j < exceptionCount; j++) {
                        thrownExceptions.add(resolveClassName(readU2()));
                    }
                } else if ("Signature".equals(attributeName)) {
                    genericSignatures.add(utf8(readU2()));
                } else if ("RuntimeVisibleAnnotations".equals(attributeName)
                        || "RuntimeInvisibleAnnotations".equals(attributeName)) {
                    readAnnotations(annotations);
                }

                offset = attributeEnd;
            }

            return new MethodFingerprint(name, descriptor, instructions, methodCalls, stringConstants,
                    localVariableNames, fieldReferences, annotations, thrownExceptions, invokedynamicCalls,
                    genericSignatures, localStoreSlots, compilerTempLocalStoreSlots, exceptionHandlerCount,
                    branchOpcodeCount, lineNumberCount, hasCode, accessFlags);
        }

        private CodeFingerprint readCodeAttribute() {
            offset += 2; // max_stack
            offset += 2; // max_locals
            int codeLength = readU4();
            byte[] code = Arrays.copyOfRange(data, offset, offset + codeLength);
            offset += codeLength;

            int exceptionTableLength = readU2();
            Set<Integer> exceptionHandlerStarts = new HashSet<>();
            for (int i = 0; i < exceptionTableLength; i++) {
                offset += 4; // start_pc + end_pc
                exceptionHandlerStarts.add(readU2());
                offset += 2; // catch_type
            }

            CodeFingerprint fingerprint = scanCode(code, exceptionHandlerStarts);
            fingerprint.exceptionHandlerCount = exceptionTableLength;

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

        private CodeFingerprint scanCode(byte[] code, Set<Integer> exceptionHandlerStarts) {
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
                    case 0x36:
                    case 0x37:
                    case 0x38:
                    case 0x39:
                    case 0x3a:
                        recordLocalStore(fingerprint, code, position, code[position + 1] & 0xFF, exceptionHandlerStarts);
                        position += 2;
                        break;
                    case 0x3b:
                    case 0x3c:
                    case 0x3d:
                    case 0x3e:
                        recordLocalStore(fingerprint, code, position, opcode - 0x3b, exceptionHandlerStarts);
                        position += 1;
                        break;
                    case 0x3f:
                    case 0x40:
                    case 0x41:
                    case 0x42:
                        recordLocalStore(fingerprint, code, position, opcode - 0x3f, exceptionHandlerStarts);
                        position += 1;
                        break;
                    case 0x43:
                    case 0x44:
                    case 0x45:
                    case 0x46:
                        recordLocalStore(fingerprint, code, position, opcode - 0x43, exceptionHandlerStarts);
                        position += 1;
                        break;
                    case 0x47:
                    case 0x48:
                    case 0x49:
                    case 0x4a:
                        recordLocalStore(fingerprint, code, position, opcode - 0x47, exceptionHandlerStarts);
                        position += 1;
                        break;
                    case 0x4b:
                    case 0x4c:
                    case 0x4d:
                    case 0x4e:
                        recordLocalStore(fingerprint, code, position, opcode - 0x4b, exceptionHandlerStarts);
                        position += 1;
                        break;
                    case 0x13:
                    case 0x14:
                        addConstantUsage(fingerprint, BytecodeFingerprint.readU2(code, position + 1));
                        position += 3;
                        break;
                    case 0x84:
                        recordLocalStore(fingerprint, code, position, code[position + 1] & 0xFF, exceptionHandlerStarts);
                        position += 3;
                        break;
                    case 0xb6:
                    case 0xb7:
                    case 0xb8:
                        fingerprint.methodCalls.add(resolveMethodReference(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 3;
                        break;
                    case 0xb2:
                    case 0xb3:
                    case 0xb4:
                    case 0xb5:
                        fingerprint.fieldReferences.add(resolveFieldReference(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 3;
                        break;
                    case 0xb9:
                        fingerprint.methodCalls.add(resolveMethodReference(BytecodeFingerprint.readU2(code, position + 1)));
                        position += 5;
                        break;
                    case 0xba:
                        String invokedynamicCall = resolveInvokeDynamic(BytecodeFingerprint.readU2(code, position + 1));
                        fingerprint.methodCalls.add(invokedynamicCall);
                        fingerprint.invokedynamicCalls.add(invokedynamicCall);
                        position += 5;
                        break;
                    case 0xaa:
                        position = skipTableSwitch(code, position);
                        break;
                    case 0xab:
                        position = skipLookupSwitch(code, position);
                        break;
                    case 0xc4:
                        recordWideLocalStore(fingerprint, code, position);
                        position += wideInstructionLength(code, position);
                        break;
                    default:
                        position += instructionLength(opcode);
                        break;
                }
            }
            return fingerprint;
        }

        private void recordLocalStore(CodeFingerprint fingerprint, byte[] code, int position, int slot,
                                      Set<Integer> exceptionHandlerStarts) {
            fingerprint.localStoreSlots.add(slot);
            if (isMonitorTempStore(code, position) || isSyntheticMonitorExceptionStore(code, position, slot,
                    exceptionHandlerStarts)) {
                fingerprint.compilerTempLocalStoreSlots.add(slot);
            }
        }

        private boolean isMonitorTempStore(byte[] code, int position) {
            int opcode = code[position] & 0xFF;
            if (!isReferenceStoreOpcode(opcode)) {
                return false;
            }
            int next = position + instructionLength(opcode);
            return next < code.length && (code[next] & 0xFF) == 0xc2;
        }

        private boolean isSyntheticMonitorExceptionStore(byte[] code, int position, int slot,
                                                         Set<Integer> exceptionHandlerStarts) {
            int opcode = code[position] & 0xFF;
            if (!exceptionHandlerStarts.contains(position) || !isReferenceStoreOpcode(opcode)) {
                return false;
            }
            int cursor = position + instructionLength(opcode);
            int monitorLoadLength = referenceLoadInstructionLength(code, cursor);
            if (monitorLoadLength == 0) {
                return false;
            }
            cursor += monitorLoadLength;
            if (cursor >= code.length || (code[cursor] & 0xFF) != 0xc3) {
                return false;
            }
            cursor += 1;
            int exceptionLoadLength = referenceLoadInstructionLength(code, cursor, slot);
            if (exceptionLoadLength == 0) {
                return false;
            }
            cursor += exceptionLoadLength;
            return cursor < code.length && (code[cursor] & 0xFF) == 0xbf;
        }

        private boolean isReferenceStoreOpcode(int opcode) {
            return opcode == 0x3a || (opcode >= 0x4b && opcode <= 0x4e);
        }

        private int referenceLoadInstructionLength(byte[] code, int position) {
            if (position >= code.length) {
                return 0;
            }
            int opcode = code[position] & 0xFF;
            if (opcode == 0x19) {
                return position + 1 < code.length ? 2 : 0;
            }
            if (opcode >= 0x2a && opcode <= 0x2d) {
                return 1;
            }
            return 0;
        }

        private int referenceLoadInstructionLength(byte[] code, int position, int expectedSlot) {
            if (position >= code.length) {
                return 0;
            }
            int opcode = code[position] & 0xFF;
            if (opcode == 0x19) {
                return position + 1 < code.length && (code[position + 1] & 0xFF) == expectedSlot ? 2 : 0;
            }
            if (opcode >= 0x2a && opcode <= 0x2d) {
                return opcode - 0x2a == expectedSlot ? 1 : 0;
            }
            return 0;
        }

        private void recordWideLocalStore(CodeFingerprint fingerprint, byte[] code, int position) {
            int modifiedOpcode = code[position + 1] & 0xFF;
            if ((modifiedOpcode >= 0x36 && modifiedOpcode <= 0x3a) || modifiedOpcode == 0x84) {
                fingerprint.localStoreSlots.add(BytecodeFingerprint.readU2(code, position + 2));
            }
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

        private void skipMembers() {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                skipMember();
            }
        }

        private void skipAttributes() {
            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                offset += 2;
                int length = readU4();
                offset += length;
            }
        }

        private ClassAttributes readClassAttributes() {
            ClassAttributes attributes = new ClassAttributes();
            int attributesCount = readU2();
            for (int i = 0; i < attributesCount; i++) {
                String attributeName = utf8(readU2());
                int attributeLength = readU4();
                int attributeEnd = offset + attributeLength;
                if ("Signature".equals(attributeName)) {
                    attributes.genericSignatures.add(utf8(readU2()));
                } else if ("RuntimeVisibleAnnotations".equals(attributeName)
                        || "RuntimeInvisibleAnnotations".equals(attributeName)) {
                    readAnnotations(attributes.annotations);
                } else if ("BootstrapMethods".equals(attributeName)) {
                    int bootstrapCount = readU2();
                    for (int j = 0; j < bootstrapCount; j++) {
                        attributes.bootstrapMethods.add(resolveMethodHandle(readU2()));
                        int argumentCount = readU2();
                        offset += argumentCount * 2;
                    }
                }
                offset = attributeEnd;
            }
            return attributes;
        }

        private void readAnnotations(Set<String> output) {
            int annotationCount = readU2();
            for (int i = 0; i < annotationCount; i++) {
                readAnnotation(output);
            }
        }

        private void readAnnotation(Set<String> output) {
            output.add(utf8(readU2()));
            int pairCount = readU2();
            for (int i = 0; i < pairCount; i++) {
                offset += 2; // element_name_index
                skipElementValue();
            }
        }

        private void skipElementValue() {
            int tag = readU1();
            switch (tag) {
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'Z':
                case 's':
                    offset += 2;
                    break;
                case 'e':
                    offset += 4;
                    break;
                case 'c':
                    offset += 2;
                    break;
                case '@':
                    readAnnotation(new LinkedHashSet<String>());
                    break;
                case '[':
                    int count = readU2();
                    for (int i = 0; i < count; i++) {
                        skipElementValue();
                    }
                    break;
                default:
                    break;
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

        private String resolveFieldReference(int index) {
            CpInfo fieldRef = cp(index);
            if (fieldRef == null || fieldRef.tag != 9) {
                return "#" + index;
            }
            String owner = resolveClassName(fieldRef.index1);
            CpInfo nameAndType = cp(fieldRef.index2);
            if (nameAndType == null) {
                return owner + ".#" + fieldRef.index2;
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
            String call = "invokedynamic." + utf8(nameAndType.index1) + utf8(nameAndType.index2);
            if (invokeDynamic.index1 >= 0 && invokeDynamic.index1 < bootstrapMethodsByIndex.size()) {
                return call + " " + bootstrapMethodsByIndex.get(invokeDynamic.index1).format();
            }
            return call;
        }

        private String resolveClassName(int index) {
            CpInfo classInfo = cp(index);
            if (classInfo == null || classInfo.tag != 7) {
                return "#" + index;
            }
            return utf8(classInfo.index1);
        }

        private String resolveMethodHandle(int index) {
            CpInfo methodHandle = cp(index);
            if (methodHandle == null || methodHandle.tag != 15) {
                return "#" + index;
            }
            return resolveMethodReference(methodHandle.index1);
        }

        private String resolveBootstrapArgument(int index) {
            CpInfo argument = cp(index);
            if (argument == null) {
                return "#" + index;
            }
            switch (argument.tag) {
                case 7:
                    return "class:" + resolveClassName(index);
                case 8:
                    return "string:" + printableString(utf8(argument.index1));
                case 15:
                    return "handle:" + resolveMethodHandle(index);
                case 16:
                    return "methodType:" + utf8(argument.index1);
                default:
                    return "#" + index;
            }
        }

        private String printableString(String value) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (Character.isISOControl(ch)) {
                    result.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                } else {
                    result.append(ch);
                }
            }
            return result.toString();
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
        private final Set<String> fieldReferences = new LinkedHashSet<>();
        private final Set<String> invokedynamicCalls = new LinkedHashSet<>();
        private final Set<Integer> localStoreSlots = new LinkedHashSet<>();
        private final Set<Integer> compilerTempLocalStoreSlots = new LinkedHashSet<>();
        private int exceptionHandlerCount;
        private int branchOpcodeCount;
        private int lineNumberCount;
    }

    private static class FieldAttributes {
        private final Set<String> annotations = new LinkedHashSet<>();
        private final Set<String> genericSignatures = new LinkedHashSet<>();
    }

    private static class ClassAttributes {
        private final Set<String> annotations = new LinkedHashSet<>();
        private final Set<String> genericSignatures = new LinkedHashSet<>();
        private final Set<String> bootstrapMethods = new LinkedHashSet<>();
    }

    private static class BootstrapMethodInfo {
        private final String reference;
        private final List<String> arguments;

        private BootstrapMethodInfo(String reference, List<String> arguments) {
            this.reference = reference;
            this.arguments = arguments;
        }

        private String format() {
            if (arguments.isEmpty()) {
                return "[bootstrap=" + reference + "]";
            }
            return "[bootstrap=" + reference + "; args=" + String.join(", ", arguments) + "]";
        }
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
