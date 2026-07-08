package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationErrorParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesChineseMissingSymbolErrorWithContinuation() {
        Path projectDir = tempDir.resolve("project");
        Path source = projectDir.resolve("src/main/java/com/acme/App.java");
        String output = "[ERROR] " + source + ":[10,5] 找不到符号\n"
                + "[ERROR]   符号:   类 Missing\n"
                + "[ERROR]   位置: 类 com.acme.App\n";

        List<VerificationError> errors = new VerificationErrorParser()
                .parse(projectDir.toFile(), output, "");

        assertEquals(1, errors.size());
        VerificationError error = errors.get(0);
        assertEquals("src/main/java/com/acme/App.java", error.getSourcePath());
        assertEquals("com.acme.App", error.getClassName());
        assertEquals(10, error.getLine());
        assertEquals(5, error.getColumn());
        assertEquals(VerificationErrorParser.MISSING_SYMBOL, error.getCategory());
        assertTrue(error.getMessage().contains("符号:   类 Missing"));
    }

    @Test
    void classifiesGenericInferenceError() {
        Path projectDir = tempDir.resolve("project");
        Path source = projectDir.resolve("src/main/java/com/acme/Generic.java");
        String output = "[ERROR] " + source + ":[23,20] 无法将类 com.acme.Generic中的方法 backtracking应用到给定类型;\n"
                + "[ERROR]   需要: T[]\n"
                + "[ERROR]   找到: java.lang.Object[]\n"
                + "[ERROR]   原因: 推论变量 T 具有不兼容的上限\n";

        List<VerificationError> errors = new VerificationErrorParser()
                .parse(projectDir.toFile(), output, "");

        assertEquals(1, errors.size());
        assertEquals(VerificationErrorParser.GENERIC_INFERENCE, errors.get(0).getCategory());
        assertTrue(errors.get(0).getMessage().contains("推论变量 T"));
    }

    @Test
    void ignoresUnstructuredMavenFooter() {
        String output = "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:compile\n"
                + "[ERROR] -> [Help 1]\n";

        List<VerificationError> errors = new VerificationErrorParser()
                .parse(tempDir.toFile(), output, "");

        assertTrue(errors.isEmpty());
    }
}
