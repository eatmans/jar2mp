package com.z0fsec.jar2mp.util;

public class ClassFileUtils {

    public static int majorVersionToJava(int majorVersion) {
        if (majorVersion == 48) {
            return 4;
        }
        if (majorVersion >= 49) {
            return majorVersion - 44;
        }
        return 8;
    }

    public static int readU1(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    public static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static int readU4(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
                ((data[offset + 1] & 0xFF) << 16) |
                ((data[offset + 2] & 0xFF) << 8) |
                (data[offset + 3] & 0xFF);
    }
}
