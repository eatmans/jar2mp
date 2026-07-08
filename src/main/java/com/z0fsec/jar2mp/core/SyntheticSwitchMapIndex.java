package com.z0fsec.jar2mp.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class SyntheticSwitchMapIndex {

    private static final String SWITCH_MAP_PREFIX = "$SwitchMap$";

    private SyntheticSwitchMapIndex() {
    }

    static Map<String, Map<Integer, String>> fromJar(JarFile jarFile) throws IOException {
        if (jarFile == null) {
            return Collections.emptyMap();
        }
        Map<String, Map<Integer, String>> switchMaps = new LinkedHashMap<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;
            }
            try (InputStream input = jarFile.getInputStream(entry)) {
                new ClassFileReader(readAllBytes(input)).addSwitchMaps(switchMaps);
            } catch (RuntimeException ignored) {
                // Malformed class files should not stop project generation.
            }
        }
        return switchMaps;
    }

    static boolean isSyntheticSwitchMapClass(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return false;
        }
        Map<String, Map<Integer, String>> switchMaps = new LinkedHashMap<>();
        try {
            new ClassFileReader(classBytes).addSwitchMaps(switchMaps);
        } catch (RuntimeException ignored) {
            return false;
        }
        return !switchMaps.isEmpty();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class ClassFileReader {
        private final byte[] data;
        private Object[] constantPool;
        private int offset;
        private String thisClassName;

        private ClassFileReader(byte[] data) {
            this.data = data;
        }

        private void addSwitchMaps(Map<String, Map<Integer, String>> switchMaps) {
            if (readU4() != 0xCAFEBABE) {
                return;
            }
            offset += 4; // minor_version + major_version
            readConstantPool();

            offset += 2; // access_flags
            thisClassName = className(readU2());
            offset += 2; // super_class
            skipInterfaces();
            skipMembers();
            readMethods(switchMaps);
        }

        private void readConstantPool() {
            int count = readU2();
            constantPool = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = readU1();
                switch (tag) {
                    case 1:
                        int length = readU2();
                        constantPool[i] = new String(data, offset, length, java.nio.charset.StandardCharsets.UTF_8);
                        offset += length;
                        break;
                    case 3:
                        constantPool[i] = Integer.valueOf(readU4());
                        break;
                    case 4:
                        offset += 4;
                        break;
                    case 5:
                    case 6:
                        offset += 8;
                        i++;
                        break;
                    case 7:
                        constantPool[i] = new ClassInfo(readU2());
                        break;
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        offset += 2;
                        break;
                    case 9:
                    case 10:
                    case 11:
                        constantPool[i] = new RefInfo(readU2(), readU2());
                        break;
                    case 12:
                        constantPool[i] = new NameAndTypeInfo(readU2(), readU2());
                        break;
                    case 15:
                        offset += 3;
                        break;
                    case 17:
                    case 18:
                        offset += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported constant pool tag " + tag);
                }
            }
        }

        private void skipInterfaces() {
            int count = readU2();
            offset += count * 2;
        }

        private void skipMembers() {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                offset += 6; // access_flags + name_index + descriptor_index
                skipAttributes();
            }
        }

        private void readMethods(Map<String, Map<Integer, String>> switchMaps) {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                offset += 2; // access_flags
                String name = utf8(readU2());
                offset += 2; // descriptor_index
                int attributes = readU2();
                for (int j = 0; j < attributes; j++) {
                    String attributeName = utf8(readU2());
                    int attributeLength = readU4();
                    int attributeStart = offset;
                    if ("<clinit>".equals(name) && "Code".equals(attributeName)) {
                        readCodeAttribute(attributeStart, switchMaps);
                    }
                    offset = attributeStart + attributeLength;
                }
            }
        }

        private void skipAttributes() {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                offset += 2; // attribute_name_index
                int length = readU4();
                offset += length;
            }
        }

        private void readCodeAttribute(int attributeStart, Map<String, Map<Integer, String>> switchMaps) {
            int codeLength = readU4(attributeStart + 4);
            int codeStart = attributeStart + 8;
            List<Instruction> instructions = readInstructions(codeStart, codeLength);
            for (int i = 4; i < instructions.size(); i++) {
                Instruction current = instructions.get(i);
                if (current.opcode != 79) { // iastore
                    continue;
                }
                Instruction caseValue = instructions.get(i - 1);
                Instruction ordinalCall = instructions.get(i - 2);
                Instruction enumConstant = instructions.get(i - 3);
                Instruction switchArray = instructions.get(i - 4);
                if (!caseValue.hasIntValue()
                        || ordinalCall.opcode != 182
                        || enumConstant.field == null
                        || switchArray.field == null
                        || !switchArray.field.name.startsWith(SWITCH_MAP_PREFIX)
                        || !"[I".equals(switchArray.field.descriptor)) {
                    continue;
                }
                addSwitchCase(switchMaps, switchArray.field.name, caseValue.intValue, enumConstant.field.name);
            }
        }

        private void addSwitchCase(Map<String, Map<Integer, String>> switchMaps,
                                   String fieldName,
                                   int caseNumber,
                                   String enumConstant) {
            String binaryOwner = stripSyntheticSwitchMapSuffix(thisClassName);
            putSwitchCase(switchMaps, binaryOwner + "#" + fieldName, caseNumber, enumConstant);
            putSwitchCase(switchMaps, binaryOwner.replace('$', '.') + "#" + fieldName, caseNumber, enumConstant);
            switchMaps.computeIfAbsent(fieldName, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(caseNumber, enumConstant);
        }

        private String stripSyntheticSwitchMapSuffix(String className) {
            if (className == null) {
                return "";
            }
            return className.replace('/', '.').replaceFirst("\\$\\d+$", "");
        }

        private void putSwitchCase(Map<String, Map<Integer, String>> switchMaps,
                                   String key,
                                   int caseNumber,
                                   String enumConstant) {
            switchMaps.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(caseNumber, enumConstant);
        }

        private List<Instruction> readInstructions(int codeStart, int codeLength) {
            int codeEnd = codeStart + codeLength;
            int pointer = codeStart;
            List<Instruction> instructions = new ArrayList<>();
            while (pointer < codeEnd) {
                int opcode = readU1(pointer);
                instructions.add(instructionAt(pointer, opcode));
                pointer += instructionLength(codeStart, pointer, opcode);
            }
            return instructions;
        }

        private Instruction instructionAt(int pointer, int opcode) {
            switch (opcode) {
                case 2:
                    return Instruction.intValue(opcode, -1);
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                    return Instruction.intValue(opcode, opcode - 3);
                case 16:
                    return Instruction.intValue(opcode, (byte) readU1(pointer + 1));
                case 17:
                    return Instruction.intValue(opcode, (short) readU2(pointer + 1));
                case 18:
                    return Instruction.intValue(opcode, integerConstant(readU1(pointer + 1)));
                case 19:
                    return Instruction.intValue(opcode, integerConstant(readU2(pointer + 1)));
                case 178:
                    return Instruction.field(opcode, fieldReference(readU2(pointer + 1)));
                default:
                    return new Instruction(opcode);
            }
        }

        private int instructionLength(int codeStart, int pointer, int opcode) {
            switch (opcode) {
                case 16:
                case 18:
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                case 54:
                case 55:
                case 56:
                case 57:
                case 58:
                case 169:
                case 188:
                    return 2;
                case 17:
                case 19:
                case 20:
                case 132:
                case 153:
                case 154:
                case 155:
                case 156:
                case 157:
                case 158:
                case 159:
                case 160:
                case 161:
                case 162:
                case 163:
                case 164:
                case 165:
                case 166:
                case 167:
                case 168:
                case 178:
                case 179:
                case 180:
                case 181:
                case 182:
                case 183:
                case 184:
                case 187:
                case 189:
                case 192:
                case 193:
                case 198:
                case 199:
                    return 3;
                case 185:
                case 186:
                case 200:
                case 201:
                    return 5;
                case 197:
                    return 4;
                case 196:
                    int wideOpcode = readU1(pointer + 1);
                    return wideOpcode == 132 ? 6 : 4;
                case 170:
                    int tableStart = alignedSwitchOperandStart(codeStart, pointer);
                    int low = readU4(tableStart + 4);
                    int high = readU4(tableStart + 8);
                    return tableStart + 12 + (high - low + 1) * 4 - pointer;
                case 171:
                    int lookupStart = alignedSwitchOperandStart(codeStart, pointer);
                    int pairs = readU4(lookupStart + 4);
                    return lookupStart + 8 + pairs * 8 - pointer;
                default:
                    return 1;
            }
        }

        private int alignedSwitchOperandStart(int codeStart, int pointer) {
            int afterOpcode = pointer + 1;
            int padding = (4 - ((afterOpcode - codeStart) % 4)) % 4;
            return afterOpcode + padding;
        }

        private FieldReference fieldReference(int index) {
            RefInfo ref = (RefInfo) constantPool[index];
            NameAndTypeInfo nameAndType = (NameAndTypeInfo) constantPool[ref.nameAndTypeIndex];
            return new FieldReference(className(ref.classIndex),
                    utf8(nameAndType.nameIndex),
                    utf8(nameAndType.descriptorIndex));
        }

        private String className(int index) {
            ClassInfo classInfo = (ClassInfo) constantPool[index];
            return utf8(classInfo.nameIndex);
        }

        private String utf8(int index) {
            return (String) constantPool[index];
        }

        private int integerConstant(int index) {
            Object value = constantPool[index];
            return value instanceof Integer ? (Integer) value : Integer.MIN_VALUE;
        }

        private int readU1() {
            return data[offset++] & 0xFF;
        }

        private int readU1(int position) {
            return data[position] & 0xFF;
        }

        private int readU2() {
            int value = readU2(offset);
            offset += 2;
            return value;
        }

        private int readU2(int position) {
            return ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
        }

        private int readU4() {
            int value = readU4(offset);
            offset += 4;
            return value;
        }

        private int readU4(int position) {
            return ((data[position] & 0xFF) << 24)
                    | ((data[position + 1] & 0xFF) << 16)
                    | ((data[position + 2] & 0xFF) << 8)
                    | (data[position + 3] & 0xFF);
        }
    }

    private static final class Instruction {
        private final int opcode;
        private final Integer intValue;
        private final FieldReference field;

        private Instruction(int opcode) {
            this(opcode, null, null);
        }

        private Instruction(int opcode, Integer intValue, FieldReference field) {
            this.opcode = opcode;
            this.intValue = intValue;
            this.field = field;
        }

        private static Instruction intValue(int opcode, int value) {
            return new Instruction(opcode, value, null);
        }

        private static Instruction field(int opcode, FieldReference field) {
            return new Instruction(opcode, null, field);
        }

        private boolean hasIntValue() {
            return intValue != null && intValue != Integer.MIN_VALUE;
        }
    }

    private static final class FieldReference {
        private final String owner;
        private final String name;
        private final String descriptor;

        private FieldReference(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final class ClassInfo {
        private final int nameIndex;

        private ClassInfo(int nameIndex) {
            this.nameIndex = nameIndex;
        }
    }

    private static final class RefInfo {
        private final int classIndex;
        private final int nameAndTypeIndex;

        private RefInfo(int classIndex, int nameAndTypeIndex) {
            this.classIndex = classIndex;
            this.nameAndTypeIndex = nameAndTypeIndex;
        }
    }

    private static final class NameAndTypeInfo {
        private final int nameIndex;
        private final int descriptorIndex;

        private NameAndTypeInfo(int nameIndex, int descriptorIndex) {
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
        }
    }
}
