package com.z0fsec.jar2mp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassFileUtilsTest {

    @Test
    void mapsClassFileMajorVersionsToJavaVersions() {
        assertEquals(8, ClassFileUtils.majorVersionToJava(52));
        assertEquals(11, ClassFileUtils.majorVersionToJava(55));
        assertEquals(17, ClassFileUtils.majorVersionToJava(61));
        assertEquals(21, ClassFileUtils.majorVersionToJava(65));
        assertEquals(22, ClassFileUtils.majorVersionToJava(66));
    }
}
