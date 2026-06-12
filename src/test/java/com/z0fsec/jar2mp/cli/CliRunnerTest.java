package com.z0fsec.jar2mp.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.z0fsec.jar2mp.core.ArtifactFidelityComparator;
import com.z0fsec.jar2mp.model.ArtifactFidelityResult;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void noDependenciesSkipsDependencyDetectionInGeneratedPom() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App", "com/google/gson/Gson"));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-dependencies",
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String pomXml = Files.readString(output.resolve("sample").resolve("pom.xml"));
        assertFalse(pomXml.contains("<dependencies>"));
        assertFalse(pomXml.contains("gson"));
    }

    @Test
    void defaultDependencyDetectionUsesPackageMappings() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App", "com/google/gson/Gson"));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String pomXml = Files.readString(output.resolve("sample").resolve("pom.xml"));
        assertTrue(pomXml.contains("<artifactId>gson</artifactId>"));
    }

    @Test
    void importDepsAddsDependenciesToGeneratedPom() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path output = tempDir.resolve("out");
        Path depsFile = tempDir.resolve("deps.txt");
        Files.writeString(depsFile,
                "# comment\ncom.example:imported-lib:2.0.0:runtime:MANUAL\n",
                StandardCharsets.UTF_8);

        int exitCode = new CliRunner().run(new String[]{
                "--no-dependencies",
                "--no-decompile",
                "--import-deps", depsFile.toString(),
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String pomXml = Files.readString(output.resolve("sample").resolve("pom.xml"));
        assertTrue(pomXml.contains("<groupId>com.example</groupId>"));
        assertTrue(pomXml.contains("<artifactId>imported-lib</artifactId>"));
        assertTrue(pomXml.contains("<version>2.0.0</version>"));
        assertTrue(pomXml.contains("<scope>runtime</scope>"));
    }

    @Test
    void importDepsOverridesAutomaticallyDetectedDependencyWithoutDuplicatingIt() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App", "com/google/gson/Gson"));
        Path output = tempDir.resolve("out");
        Path depsFile = tempDir.resolve("deps.txt");
        Files.writeString(depsFile,
                "com.google.code.gson:gson:2.13.2:runtime:MANUAL\n",
                StandardCharsets.UTF_8);

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "--import-deps", depsFile.toString(),
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String pomXml = Files.readString(output.resolve("sample").resolve("pom.xml"));
        assertEquals(1, countOccurrences(pomXml, "<artifactId>gson</artifactId>"));
        assertTrue(pomXml.contains("<version>2.13.2</version>"));
        assertTrue(pomXml.contains("<scope>runtime</scope>"));
        assertFalse(pomXml.contains("<version>2.10.1</version>"));
    }

    @Test
    void compareArtifactModeWritesFidelityReports() throws Exception {
        Path original = createJar("original.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"),
                "config.properties", "value=original\n".getBytes(StandardCharsets.UTF_8));
        Path rebuilt = createJar("rebuilt.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"),
                "config.properties", "value=rebuilt\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("compare-out");

        int exitCode = new CliRunner().run(new String[]{
                "--compare-artifact", rebuilt.toString(),
                "-o", output.toString(),
                original.toString()
        });

        assertEquals(0, exitCode);
        String report = Files.readString(output.resolve("artifact-fidelity-report.md"));
        String csv = Files.readString(output.resolve("artifact-fidelity-summary.csv"));
        assertTrue(report.contains("# Artifact fidelity report"));
        assertTrue(report.contains("Exact match: false"));
        assertTrue(csv.contains("false,"));
    }

    @Test
    void compareArtifactModeWritesArchiveOrderRestoredCandidate() throws Exception {
        StoredEntry first = storedEntry("first.txt", "one");
        StoredEntry second = storedEntry("second.txt", "two");
        Path original = createStoredZip("original.jar", first, second);
        Path rebuilt = createStoredZip("rebuilt.jar", second, first);
        Path output = tempDir.resolve("compare-order");

        int exitCode = new CliRunner().run(new String[]{
                "--compare-artifact", rebuilt.toString(),
                "-o", output.toString(),
                original.toString()
        });

        assertEquals(0, exitCode);
        Path restored = output.resolve("archive-order-restored").resolve("rebuilt.jar");
        assertArrayEquals(Files.readAllBytes(original), Files.readAllBytes(restored));
        String csv = Files.readString(output.resolve("archive-order-restored")
                .resolve("artifact-fidelity-summary.csv"));
        assertTrue(csv.contains("\ntrue,"));
    }

    @Test
    void emitRawArtifactWritesExactPreservedCopy() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"),
                "config.properties", "mode=raw\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--emit-raw-artifact",
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path rawDir = output.resolve("sample").resolve("target/raw-artifact");
        assertTrue(Files.exists(rawDir.resolve("sample-1.0.jar")));
        String csv = Files.readString(rawDir.resolve("artifact-fidelity-summary.csv"));
        assertTrue(csv.startsWith("exact_match,"));
        assertTrue(csv.contains("\ntrue,"));
        String pomXml = Files.readString(output.resolve("sample").resolve("pom.xml"));
        assertFalse(pomXml.contains("restore-byte-exact-artifact"));
    }

    @Test
    void byteExactPackageKeepsRawArtifactAuxiliaryWithoutPomRawCopy() throws Exception {
        byte[] manifestBytes = ("Manifest-Version: 1.0\r\n"
                + "Created-By: Maven JAR Plugin 3.4.2\r\n"
                + "Build-Jdk-Spec: 21\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8);
        Path jar = createJarWithRawManifest("sample-1.0.jar", manifestBytes,
                "com/example/App.class", compiledClassBytes("com/example/App"),
                "config.properties", "mode=raw-package\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("sample");
        assertTrue(Files.exists(projectDir.resolve("target/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(projectDir.resolve(".jar2mp/byte-exact/raw-artifact/sample-1.0.jar")));
        String pomXml = Files.readString(projectDir.resolve("pom.xml"));
        assertTrue(pomXml.contains("<finalName>sample-1.0</finalName>"));
        assertFalse(pomXml.contains("restore-byte-exact-artifact"));
        assertTrue(pomXml.contains("restore-byte-exact-package-records"));
        assertTrue(pomXml.contains(".jar2mp/byte-exact/raw-artifact/sample-1.0.jar"));
        assertTrue(Files.exists(projectDir.resolve(".jar2mp/byte-exact/ByteExactPackageRestorer.java")));

        int packageExitCode = runMaven(projectDir, "clean package");
        assertEquals(0, packageExitCode);
        Path rebuilt = projectDir.resolve("target/sample-1.0.jar");
        ArtifactFidelityResult fidelity = new ArtifactFidelityComparator().compare(jar.toFile(), rebuilt.toFile());
        assertTrue(fidelity.isExactMatch());
    }

    @Test
    void restorePackageRecordsInstallsGuardedPackageRecordHelper() throws Exception {
        byte[] manifestBytes = ("Manifest-Version: 1.0\r\n"
                + "Created-By: Maven JAR Plugin 3.4.2\r\n"
                + "Build-Jdk-Spec: 21\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8);
        Path jar = createJarWithRawManifest("sample-1.0.jar", manifestBytes,
                "com/example/App.class", compiledClassBytes("com/example/App"),
                "config.properties", "mode=package-records\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--restore-package-records",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("sample");
        assertTrue(Files.exists(projectDir.resolve(".jar2mp/package-records/raw-artifact/sample-1.0.jar")));
        assertTrue(Files.exists(projectDir.resolve(".jar2mp/package-records/PackageRecordRestorer.java")));
        String pomXml = Files.readString(projectDir.resolve("pom.xml"));
        assertTrue(pomXml.contains("restore-package-records"));
        assertTrue(pomXml.contains(".jar2mp/package-records/raw-artifact/sample-1.0.jar"));
        assertFalse(pomXml.contains("<finalName>sample-1.0</finalName>"));

        Path rebuilt = projectDir.resolve("target/sample-1.0.jar");
        ArtifactFidelityResult fidelity = new ArtifactFidelityComparator().compare(jar.toFile(), rebuilt.toFile());
        assertTrue(fidelity.isExactMatch());
        Path fidelityDir = projectDir.resolve("target/package-record-restore-check");
        assertTrue(Files.exists(fidelityDir.resolve("artifact-fidelity-summary.csv")));
        String csv = Files.readString(fidelityDir.resolve("artifact-fidelity-summary.csv"));
        assertTrue(csv.contains("\ntrue,"));
    }

    @Test
    void restorePackageRecordsFailsWhenPackageContentDiffers() throws Exception {
        Path jar = createJarWithManifest("sample-1.0.jar", "com/example/App.class",
                compiledClassBytes("com/example/App"),
                "config.properties", "mode=original\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--restore-package-records",
                "--no-decompile",
                "--no-dependencies",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("sample");
        Files.writeString(projectDir.resolve("src/main/resources/config.properties"), "mode=changed\n",
                StandardCharsets.UTF_8);

        int packageExitCode = runMaven(projectDir, "clean package");

        assertNotEquals(0, packageExitCode);
    }

    @Test
    void byteExactPackageMakesWarPackageOutputByteExact() throws Exception {
        Path war = createJarWithManifest("sample-web-1.0.war", "WEB-INF/classes/com/example/WebApp.class",
                compiledClassBytes("com/example/WebApp"),
                "WEB-INF/web.xml", "<web-app/>".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "-o", output.toString(),
                war.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("sample-web");
        assertTrue(Files.exists(projectDir.resolve("target/raw-artifact/sample-web-1.0.war")));
        String pomXml = Files.readString(projectDir.resolve("pom.xml"));
        assertTrue(pomXml.contains("<packaging>war</packaging>"));
        assertFalse(pomXml.contains("restore-byte-exact-artifact"));
        Path rebuilt = projectDir.resolve("target/sample-web-1.0.war");
        assertTrue(Files.exists(rebuilt));
        ArtifactFidelityResult fidelity = new ArtifactFidelityComparator().compare(war.toFile(), rebuilt.toFile());
        assertTrue(fidelity.isExactMatch());
    }

    @Test
    void byteExactPackageVerifyBuildDefaultsToPackageGoal() throws Exception {
        Path jar = createJarWithManifest("sample-1.0.jar", "com/example/App.class",
                compiledClassBytes("com/example/App"),
                "config.properties", "mode=verify-package\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String report = Files.readString(output.resolve("sample").resolve("verification-report.md"));
        assertTrue(report.contains("package"));
    }

    @Test
    void byteExactPackageVerifyBuildWritesExactPackageReport() throws Exception {
        Path jar = createJarWithManifest("sample-1.0.jar", "com/example/App.class",
                compiledClassBytes("com/example/App"),
                "config.properties", "mode=verify-package-exact\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path fidelityDir = output.resolve("sample").resolve("target/byte-exact-package-check");
        assertTrue(Files.exists(fidelityDir.resolve("artifact-fidelity-report.md")));
        String csv = Files.readString(fidelityDir.resolve("artifact-fidelity-summary.csv"));
        assertTrue(csv.startsWith("exact_match,"));
        assertTrue(csv.contains("\ntrue,"));
        Path rebuilt = output.resolve("sample").resolve("target/sample-1.0.jar");
        ArtifactFidelityResult fidelity = new ArtifactFidelityComparator().compare(jar.toFile(), rebuilt.toFile());
        assertTrue(fidelity.isExactMatch());
    }

    @Test
    void byteExactPackageRestoresOriginalManifestByteOrder() throws Exception {
        byte[] manifestBytes = ("Manifest-Version: 1.0\r\n"
                + "Created-By: Maven JAR Plugin 3.4.2\r\n"
                + "Build-Jdk-Spec: 21\r\n"
                + "Implementation-Title: sample\r\n"
                + "Implementation-Version: 1.0\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8);
        Path jar = createJarWithRawManifest("sample-1.0.jar", manifestBytes,
                "com/example/App.class", compiledClassBytes("com/example/App"),
                "config.properties", "mode=manifest-order\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path rebuilt = output.resolve("sample").resolve("target/sample-1.0.jar");
        ArtifactFidelityResult fidelity = new ArtifactFidelityComparator().compare(jar.toFile(), rebuilt.toFile());
        assertTrue(fidelity.isExactMatch());
    }

    @Test
    void explicitVerifyGoalOverridesByteExactPackageDefault() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"),
                "config.properties", "mode=verify-validate\n".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--byte-exact-package",
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "--verify-goal", "validate",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String report = Files.readString(output.resolve("sample").resolve("verification-report.md"));
        assertTrue(report.contains("validate"));
        assertFalse(Files.exists(output.resolve("sample")
                .resolve("target/byte-exact-package-check/artifact-fidelity-summary.csv")));
    }

    @Test
    void noDecompileCopiesClassFilesInsteadOfWritingJavaSources() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("sample");
        assertTrue(Files.exists(projectDir.resolve("src/main/java/com/example/App.class")));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/com/example/App.java")));
    }

    @Test
    void noDecompileCopiesModuleInfoClass() throws Exception {
        Path jar = tempDir.resolve("sample-1.0.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("module-info.class"));
            out.write(minimalClassBytes(53, "module-info"));
            out.closeEntry();
            out.putNextEntry(new JarEntry("com/example/App.class"));
            out.write(minimalClassBytes(52, "com/example/App"));
            out.closeEntry();
        }
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output.resolve("sample").resolve("src/main/java/module-info.class")));
    }

    @Test
    void noDecompileCopiesInnerClassFiles() throws Exception {
        Path jar = tempDir.resolve("sample-1.0.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("com/example/App.class"));
            out.write(minimalClassBytes(52, "com/example/App"));
            out.closeEntry();
            out.putNextEntry(new JarEntry("com/example/App$Inner.class"));
            out.write(minimalClassBytes(52, "com/example/App$Inner"));
            out.closeEntry();
        }
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output.resolve("sample").resolve("src/main/java/com/example/App$Inner.class")));
    }

    @Test
    void noResourcesSkipsResourceExtraction() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"), "application.properties",
                "name=demo".getBytes(StandardCharsets.UTF_8));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "--no-resources",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        assertFalse(Files.exists(output.resolve("sample").resolve("src/main/resources/application.properties")));
        assertTrue(Files.exists(output.resolve("sample").resolve("decompile-parity-report.md")));
    }

    @Test
    void batchRunReturnsPartialFailureWhenAnyInputFails() throws Exception {
        Path validJar = createJar("valid-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path invalidJar = tempDir.resolve("broken.jar");
        Files.writeString(invalidJar, "not a jar", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "--no-dependencies",
                "-o", output.toString(),
                validJar.toString(),
                invalidJar.toString()
        });

        assertEquals(1, exitCode);
        assertTrue(Files.exists(output.resolve("valid").resolve("pom.xml")));
    }

    @Test
    void verifyBuildWritesVerificationReportWhenEnabled() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "--verify-goal", "validate",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        String report = Files.readString(output.resolve("sample").resolve("verification-report.md"));
        assertTrue(report.contains("# Verification report"));
        assertTrue(report.contains("mvn"));
        assertTrue(report.contains("validate"));
        assertTrue(report.contains("Exit code: 0"));
    }

    @Test
    void verifyBuildFailureMakesCliFail() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--no-decompile",
                "--no-dependencies",
                "--verify-build",
                "--verify-goal", "no-such-goal",
                "-o", output.toString(),
                jar.toString()
        });

        assertNotEquals(0, exitCode);
        Path report = output.resolve("sample").resolve("verification-report.md");
        assertTrue(Files.exists(report));
        assertTrue(Files.readString(report).contains("Failure type:"));
    }

    @Test
    void invalidJavaVersionReturnsUsageError() {
        int exitCode = new CliRunner().run(new String[]{"--java-version", "abc"});

        assertEquals(1, exitCode);
    }

    @Test
    void verboseBuildPrintsGeneratedReportPaths() throws Exception {
        Path jar = createJar("sample-1.0.jar", "com/example/App.class",
                minimalClassBytes(52, "com/example/App"));
        Path output = tempDir.resolve("out");
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CliRunner().run(new String[]{
                    "--verbose",
                    "--no-decompile",
                    "--no-dependencies",
                    "-o", output.toString(),
                    jar.toString()
            });
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String outputText = new String(capturedOut.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(outputText.contains("decompile-parity-report.md"));
        assertTrue(outputText.contains("resource-inventory.md"));
        assertTrue(outputText.contains("restoration-report.md"));
        assertTrue(outputText.contains("restoration-score.md"));
        assertTrue(outputText.contains("gap-summary.md"));
        assertTrue(outputText.contains("RUNBOOK.md"));
    }

    @Test
    void helpMentionsRuntimeTraceOptionsAndReport() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CliRunner().run(new String[]{"--help"});
            assertEquals(0, exitCode);
        } finally {
            System.setOut(originalOut);
        }

        String outputText = new String(capturedOut.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(outputText.contains("--trace-runtime"));
        assertTrue(outputText.contains("--trace-args"));
        assertTrue(outputText.contains("--trace-timeout"));
        assertTrue(outputText.contains("--smoke-only"));
        assertTrue(outputText.contains("--byte-exact-package"));
        assertTrue(outputText.contains("--restore-package-records"));
        assertTrue(outputText.contains("target/byte-exact-package-check"));
        assertTrue(outputText.contains("target/package-record-restore-check"));
        assertTrue(outputText.contains("restoration-score.md"));
        assertTrue(outputText.contains("gap-summary.md"));
        assertTrue(outputText.contains("runtime-trace-report.md"));
    }

    @Test
    void traceRuntimeWritesRuntimeTraceReport() throws Exception {
        Path jar = createRunnableJar("trace-sample-1.0.jar");
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--trace-runtime",
                "--trace-timeout", "10",
                "--trace-args", "--smoke-test",
                "--no-decompile",
                "--no-dependencies",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path reportPath = output.resolve("trace-sample").resolve("runtime-trace-report.md");
        assertTrue(Files.exists(reportPath));
        assertTrue(Files.exists(output.resolve("trace-sample").resolve("restoration-score.md")));
        assertTrue(Files.exists(output.resolve("trace-sample").resolve("gap-summary.md")));
        String report = Files.readString(reportPath);
        assertTrue(report.contains("# Runtime trace report"));
        assertTrue(report.contains("--smoke-test"));
        assertTrue(report.contains("demo.TraceMain"));
    }

    @Test
    void traceRuntimeAndVerificationWritesFinalReports() throws Exception {
        Path jar = createRunnableJar("trace-verify-sample-1.0.jar");
        Path output = tempDir.resolve("out");

        int exitCode = new CliRunner().run(new String[]{
                "--trace-runtime",
                "--verify-build",
                "--verify-goal", "compile",
                "-o", output.toString(),
                jar.toString()
        });

        assertEquals(0, exitCode);
        Path projectDir = output.resolve("trace-verify-sample");
        assertTrue(Files.exists(projectDir.resolve("runtime-trace-report.md")));
        assertTrue(Files.exists(projectDir.resolve("restoration-score.md")));
        assertTrue(Files.exists(projectDir.resolve("gap-summary.md")));
        assertTrue(Files.exists(projectDir.resolve("verification-report.md")));
    }

    private Path createJar(String fileName, String classEntry, byte[] classBytes) throws Exception {
        return createJar(fileName, classEntry, classBytes, null, null);
    }

    private byte[] compiledClassBytes(String internalName) throws Exception {
        String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
        String packageName = "";
        int packageEnd = internalName.lastIndexOf('/');
        if (packageEnd >= 0) {
            packageName = internalName.substring(0, packageEnd).replace('/', '.');
        }
        Path sourceRoot = tempDir.resolve("compiled-src-" + simpleName + "-" + System.nanoTime());
        Path packageDir = packageEnd >= 0
                ? sourceRoot.resolve(internalName.substring(0, packageEnd))
                : sourceRoot;
        Files.createDirectories(packageDir);
        Path sourceFile = packageDir.resolve(simpleName + ".java");
        String source = (packageName.isEmpty() ? "" : "package " + packageName + ";\n")
                + "public class " + simpleName + " {}\n";
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        Path classesDir = tempDir.resolve("compiled-classes-" + simpleName + "-" + System.nanoTime());
        Files.createDirectories(classesDir);
        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, compileResult);
        return Files.readAllBytes(classesDir.resolve(internalName + ".class"));
    }

    private Path createRunnableJar(String fileName) throws Exception {
        Path sourceDir = tempDir.resolve("runtime-src/demo");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TraceMain.java");
        Files.writeString(sourceFile,
                "package demo;\n" +
                        "public class TraceMain {\n" +
                        "  public static void main(String[] args) throws Exception {\n" +
                        "    Class.forName(\"java.lang.String\");\n" +
                        "  }\n" +
                        "}\n",
                StandardCharsets.UTF_8);

        Path classesDir = tempDir.resolve("runtime-classes");
        Files.createDirectories(classesDir);
        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-source", "8",
                "-target", "8",
                "-d", classesDir.toString(),
                sourceFile.toString());
        assertEquals(0, compileResult);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.TraceMain");

        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/TraceMain.class"));
            out.write(Files.readAllBytes(classesDir.resolve("demo/TraceMain.class")));
            out.closeEntry();
        }
        return jar;
    }

    private int runMaven(Path projectDir, String goal) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("-q");
        for (String part : goal.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }
        Process process = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start();
        try (java.io.InputStream in = process.getInputStream()) {
            while (in.read() != -1) {
                // Drain output so Maven cannot block on a full pipe.
            }
        }
        return process.waitFor();
    }

    private Path createJar(String fileName, String classEntry, byte[] classBytes,
                           String resourceEntry, byte[] resourceBytes) throws Exception {
        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(classEntry));
            out.write(classBytes);
            out.closeEntry();
            if (resourceEntry != null) {
                out.putNextEntry(new JarEntry(resourceEntry));
                out.write(resourceBytes);
                out.closeEntry();
            }
        }
        return jar;
    }

    private Path createJarWithManifest(String fileName, String classEntry, byte[] classBytes,
                                       String resourceEntry, byte[] resourceBytes) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry(classEntry));
            out.write(classBytes);
            out.closeEntry();
            if (resourceEntry != null) {
                out.putNextEntry(new JarEntry(resourceEntry));
                out.write(resourceBytes);
                out.closeEntry();
            }
        }
        return jar;
    }

    private Path createJarWithRawManifest(String fileName, byte[] manifestBytes,
                                          String classEntry, byte[] classBytes,
                                          String resourceEntry, byte[] resourceBytes) throws Exception {
        Path jar = tempDir.resolve(fileName);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write(manifestBytes);
            out.closeEntry();
            out.putNextEntry(new ZipEntry(classEntry));
            out.write(classBytes);
            out.closeEntry();
            if (resourceEntry != null) {
                out.putNextEntry(new ZipEntry(resourceEntry));
                out.write(resourceBytes);
                out.closeEntry();
            }
        }
        return jar;
    }

    private Path createStoredZip(String fileName, StoredEntry... entries) throws Exception {
        Path jar = tempDir.resolve(fileName);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (StoredEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setTime(0L);
                zipEntry.setSize(entry.content.length);
                zipEntry.setCompressedSize(entry.content.length);
                zipEntry.setCrc(crc32(entry.content));
                zipEntry.setExtra(new byte[0]);
                out.putNextEntry(zipEntry);
                out.write(entry.content);
                out.closeEntry();
            }
        }
        return jar;
    }

    private StoredEntry storedEntry(String name, String content) {
        return new StoredEntry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private long crc32(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return crc.getValue();
    }

    private byte[] minimalClassBytes(int majorVersion, String className) throws Exception {
        return minimalClassBytes(majorVersion, className, null);
    }

    private byte[] minimalClassBytes(int majorVersion, String className, String referencedClassName) throws Exception {
        byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
        byte[] referenceBytes = referencedClassName != null
                ? referencedClassName.getBytes(StandardCharsets.UTF_8)
                : null;
        int constantPoolCount = referenceBytes != null ? 4 : 3;
        int size = 24 + nameBytes.length + (referenceBytes != null ? 3 + referenceBytes.length : 0);
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xCA;
        bytes[1] = (byte) 0xFE;
        bytes[2] = (byte) 0xBA;
        bytes[3] = (byte) 0xBE;
        writeU2(bytes, 4, 0);
        writeU2(bytes, 6, majorVersion);
        writeU2(bytes, 8, constantPoolCount);
        bytes[10] = 1;
        writeU2(bytes, 11, nameBytes.length);
        System.arraycopy(nameBytes, 0, bytes, 13, nameBytes.length);
        int classInfoOffset = 13 + nameBytes.length;
        if (referenceBytes != null) {
            bytes[classInfoOffset] = 1;
            writeU2(bytes, classInfoOffset + 1, referenceBytes.length);
            System.arraycopy(referenceBytes, 0, bytes, classInfoOffset + 3, referenceBytes.length);
            classInfoOffset += 3 + referenceBytes.length;
        }
        bytes[classInfoOffset] = 7;
        writeU2(bytes, classInfoOffset + 1, 1);
        writeU2(bytes, classInfoOffset + 3, 0x0021);
        writeU2(bytes, classInfoOffset + 5, 2);
        writeU2(bytes, classInfoOffset + 7, 0);
        writeU2(bytes, classInfoOffset + 9, 0);
        return bytes;
    }

    private void writeU2(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private int countOccurrences(String value, String substring) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(substring, index)) >= 0) {
            count++;
            index += substring.length();
        }
        return count;
    }

    private static class StoredEntry {
        private final String name;
        private final byte[] content;

        private StoredEntry(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
