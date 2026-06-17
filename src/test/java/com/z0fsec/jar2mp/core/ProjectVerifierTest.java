package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void verifiesGeneratedProjectWithMavenCompile() throws Exception {
        Path projectDir = createProject("public class App { public String run() { return \"ok\"; } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertEquals(0, result.getExitCode());
        assertTrue(result.getCommand().contains("mvn"));
        assertTrue(result.getCommand().contains("compile"));
        assertTrue(result.getCommand().contains("-Dspring-javaformat.skip=true"));
        assertTrue(result.getCommand().contains("-Denforcer.skip=true"));
        assertTrue(result.getCommand().contains("-Drat.skip=true"));
        assertTrue(result.getSummary().contains("BUILD SUCCESS"));
        assertEquals("NONE", result.getFailureType());
    }

    @Test
    void classifiesCompilationFailure() throws Exception {
        Path projectDir = createProject("public class App { public void broken() { missing } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertTrue(result.getExitCode() != 0);
        assertEquals("COMPILATION_ERROR", result.getFailureType());
        assertTrue(result.getSummary().contains("Compilation failure"));
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrors().get(0).getSourcePath().endsWith("App.java"));

        new ProjectVerifier().writeReport(projectDir.toFile(), result);
        String errorReport = new String(Files.readAllBytes(projectDir.resolve("verification-errors.md")),
                StandardCharsets.UTF_8);
        assertTrue(errorReport.contains("# Verification errors"));
        assertTrue(errorReport.contains("| Category | Source | Line | Column | Message |"));
        assertTrue(errorReport.contains("src/main/java/demo/App.java"));
    }

    @Test
    void retriesCompileWithRawClassFallbackForBrokenGeneratedSource() throws Exception {
        Path projectDir = createProject("public class App { public void broken() { missing } }");
        compileRawClass(projectDir.resolve("target/raw-classes"),
                "demo.App",
                "package demo;\npublic class App { public String run() { return \"ok\"; } }\n");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertEquals(0, result.getExitCode());
        assertEquals("NONE", result.getFailureType());
        assertTrue(result.getCompileFallbackClassPaths().contains("demo/App.class"));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/demo/App.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/resources/demo/App.class")));
    }

    @Test
    void recoversCascadeVictimSourceAfterRootClassFallsBackToRawClass() throws Exception {
        Path projectDir = createProjectWithMaxCompilerErrors(10);
        Path sourceDir = projectDir.resolve("src/main/java/demo");
        Files.write(sourceDir.resolve("A.java"),
                ("package demo;\n"
                        + "public class A {\n"
                        + "    private MissingType missing;\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(sourceDir.resolve("B.java"),
                ("package demo;\n"
                        + "public class B {\n"
                        + "    public String call() { return new A().run(); }\n"
                        + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        compileRawClass(projectDir.resolve("target/raw-classes"),
                "demo.A",
                "package demo;\npublic class A { public String run() { return \"ok\"; } }\n");
        compileRawClass(projectDir.resolve("target/raw-classes"),
                "demo.B",
                "package demo;\npublic class B { public String call() { return new A().run(); } }\n",
                projectDir.resolve("target/raw-classes"));

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertEquals(0, result.getExitCode());
        assertEquals("NONE", result.getFailureType());
        assertTrue(result.getCompileFallbackClassPaths().contains("demo/A.class"));
        assertFalse(result.getCompileFallbackClassPaths().contains("demo/B.class"));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/demo/A.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/java/demo/B.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/resources/demo/A.class")));
        assertFalse(Files.exists(projectDir.resolve("src/main/resources/demo/B.class")));
    }

    @Test
    void injectsProvidedLombokDependencyForRecoveredFallbackSources() throws Exception {
        Path mavenRepository = tempDir.resolve(".m2/repository");
        createLombokArtifact(mavenRepository, "1.18.46");
        Path projectDir = createProjectWithRepository(mavenRepository,
                "import lombok.Generated;\n"
                        + "@Generated\n"
                        + "public class App { public String run() { return \"ok\"; } }");
        compileRawClass(projectDir.resolve("target/raw-classes"),
                "demo.App",
                "package demo;\npublic class App { public String run() { return \"ok\"; } }\n");

        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempDir.toString());
            VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

            assertEquals(0, result.getExitCode());
            assertTrue(Files.exists(projectDir.resolve("src/main/java/demo/App.java")));
            assertFalse(result.getCompileFallbackClassPaths().contains("demo/App.class"));
            assertFalse(Files.exists(projectDir.resolve("target/fallback-recovery-libs/lombok-1.18.46.jar")));

            String pomXml = new String(Files.readAllBytes(projectDir.resolve("pom.xml")),
                    StandardCharsets.UTF_8);
            assertTrue(pomXml.contains("<groupId>org.projectlombok</groupId>"));
            assertTrue(pomXml.contains("<artifactId>lombok</artifactId>"));
            assertTrue(pomXml.contains("<version>1.18.46</version>"));
            assertTrue(pomXml.contains("<scope>provided</scope>"));
            assertFalse(pomXml.contains("<scope>system</scope>"));
            assertFalse(pomXml.contains("<systemPath>"));
            assertFalse(pomXml.contains("target/fallback-recovery-libs"));
        } finally {
            if (previousHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    @Test
    void continuesRawClassFallbackPastTwentyCompilerRounds() throws Exception {
        Path projectDir = createProjectWithMaxCompilerErrors(1);
        for (int i = 0; i < 21; i++) {
            String className = String.format("demo.Broken%02d", i);
            Path sourceFile = projectDir.resolve("src/main/java/demo/Broken" + String.format("%02d", i) + ".java");
            Files.write(sourceFile,
                    ("package demo;\npublic class Broken" + String.format("%02d", i)
                            + " { public void broken() { missingSymbol; } }\n")
                            .getBytes(StandardCharsets.UTF_8));
            compileRawClass(projectDir.resolve("target/raw-classes"),
                    className,
                    "package demo;\npublic class Broken" + String.format("%02d", i)
                            + " { public String run() { return \"ok\"; } }\n");
        }

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");

        assertEquals(0, result.getExitCode());
        assertEquals("NONE", result.getFailureType());
        assertEquals(21, result.getCompileFallbackClassPaths().size());
        assertFalse(Files.exists(projectDir.resolve("src/main/java/demo/Broken20.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/resources/demo/Broken20.class")));
    }

    @Test
    void writesVerificationReport() throws Exception {
        Path projectDir = createProject("public class App { public String run() { return \"ok\"; } }");

        VerificationResult result = new ProjectVerifier().verify(projectDir.toFile(), "compile");
        new ProjectVerifier().writeReport(projectDir.toFile(), result);

        String report = new String(Files.readAllBytes(projectDir.resolve("verification-report.md")),
                StandardCharsets.UTF_8);
        assertTrue(report.contains("# Verification report"));
        assertTrue(report.contains("- Command:"));
        assertTrue(report.contains("- Exit code: 0"));
        assertTrue(report.contains("- Failure type: NONE"));
        assertTrue(report.contains("- Summary:"));
        assertTrue(report.contains("- Error count: 0"));

        String errorReport = new String(Files.readAllBytes(projectDir.resolve("verification-errors.md")),
                StandardCharsets.UTF_8);
        assertTrue(errorReport.contains("# Verification errors"));
        assertTrue(errorReport.contains("No structured verification errors were parsed."));
    }

    @Test
    void resolvesProjectMavenWrapperBeforeEnvironment() throws Exception {
        Path projectDir = createProject("public class App { }");
        Path mvnw = projectDir.resolve("mvnw");
        Files.write(mvnw, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        mvnw.toFile().setExecutable(true);

        Path mavenHome = tempDir.resolve("maven-home");
        Path homeMvn = mavenHome.resolve("bin/mvn");
        Files.createDirectories(homeMvn.getParent());
        Files.write(homeMvn, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        homeMvn.toFile().setExecutable(true);

        String resolved = ProjectVerifier.findMavenExecutable(projectDir.toFile(),
                Map.of("MAVEN_HOME", mavenHome.toString()));

        assertEquals(mvnw.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolvesMavenHomeWhenPathDoesNotContainMaven() throws Exception {
        Path projectDir = createProject("public class App { }");
        Path mavenHome = tempDir.resolve("maven-home");
        Path homeMvn = mavenHome.resolve("bin/mvn");
        Files.createDirectories(homeMvn.getParent());
        Files.write(homeMvn, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        homeMvn.toFile().setExecutable(true);

        String resolved = ProjectVerifier.findMavenExecutable(projectDir.toFile(),
                Map.of("MAVEN_HOME", mavenHome.toString(), "PATH", tempDir.toString()));

        assertEquals(homeMvn.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolvesPathMavenWhenHomeVariablesAreMissing() throws Exception {
        Path projectDir = createProject("public class App { }");
        Path binDir = tempDir.resolve("path-bin");
        Path pathMvn = binDir.resolve("mvn");
        Files.createDirectories(binDir);
        Files.write(pathMvn, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        pathMvn.toFile().setExecutable(true);

        String resolved = ProjectVerifier.findMavenExecutable(projectDir.toFile(),
                Map.of("PATH", binDir.toString()));

        assertEquals(pathMvn.toAbsolutePath().toString(), resolved);
    }

    @Test
    void resolvesMavenWrapperCacheWhenEnvironmentHasNoMaven() throws Exception {
        Path projectDir = createProject("public class App { }");
        Path cacheMvn = tempDir.resolve(".m2/wrapper/dists/apache-maven-3.9.12/hash/bin/mvn");
        Files.createDirectories(cacheMvn.getParent());
        Files.write(cacheMvn, "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        cacheMvn.toFile().setExecutable(true);

        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempDir.toString());
            String resolved = ProjectVerifier.findMavenExecutable(projectDir.toFile(), Map.of());

            assertEquals(cacheMvn.toAbsolutePath().toString(), resolved);
        } finally {
            if (previousHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    private Path createProject(String javaSource) throws Exception {
        Path projectDir = tempDir.resolve("project-" + System.nanoTime());
        Path sourceDir = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.write(projectDir.resolve("pom.xml"), pomXml().getBytes(StandardCharsets.UTF_8));
        Files.write(sourceDir.resolve("App.java"),
                ("package demo;\n" + javaSource + "\n").getBytes(StandardCharsets.UTF_8));
        return projectDir;
    }

    private Path createProjectWithRepository(Path repository, String javaSource) throws Exception {
        Path projectDir = tempDir.resolve("project-" + System.nanoTime());
        Path sourceDir = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Files.write(projectDir.resolve("pom.xml"),
                pomXmlWithRepository(repository).getBytes(StandardCharsets.UTF_8));
        Files.write(sourceDir.resolve("App.java"),
                ("package demo;\n" + javaSource + "\n").getBytes(StandardCharsets.UTF_8));
        return projectDir;
    }

    private Path createProjectWithMaxCompilerErrors(int maxErrors) throws Exception {
        Path projectDir = tempDir.resolve("project-" + System.nanoTime());
        Files.createDirectories(projectDir.resolve("src/main/java/demo"));
        Files.write(projectDir.resolve("pom.xml"),
                pomXmlWithMaxCompilerErrors(maxErrors).getBytes(StandardCharsets.UTF_8));
        return projectDir;
    }

    private void compileRawClass(Path rawClassesDir, String className, String source) throws Exception {
        compileRawClass(rawClassesDir, className, source, null);
    }

    private void compileRawClass(Path rawClassesDir, String className, String source, Path classPath) throws Exception {
        String packagePath = "";
        String simpleName = className;
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            packagePath = className.substring(0, lastDot).replace('.', '/');
            simpleName = className.substring(lastDot + 1);
        }

        Path sourceDir = tempDir.resolve("raw-src-" + System.nanoTime()).resolve(packagePath);
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve(simpleName + ".java");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(rawClassesDir);

        java.util.List<String> args = new java.util.ArrayList<>();
        if (classPath != null) {
            args.add("-cp");
            args.add(classPath.toString());
        }
        args.add("-d");
        args.add(rawClassesDir.toString());
        args.add(sourceFile.toString());

        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(new String[0]));
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }
    }

    private void createLombokArtifact(Path repository, String version) throws Exception {
        Path artifactDir = repository.resolve("org/projectlombok/lombok").resolve(version);
        Files.createDirectories(artifactDir);
        Path classesDir = tempDir.resolve("lombok-classes-" + System.nanoTime());
        Path sourceDir = tempDir.resolve("lombok-src-" + System.nanoTime()).resolve("lombok");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("Generated.java");
        Files.write(sourceFile,
                ("package lombok;\n"
                        + "import java.lang.annotation.ElementType;\n"
                        + "import java.lang.annotation.Retention;\n"
                        + "import java.lang.annotation.RetentionPolicy;\n"
                        + "import java.lang.annotation.Target;\n"
                        + "@Retention(RetentionPolicy.SOURCE)\n"
                        + "@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})\n"
                        + "public @interface Generated {}\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(classesDir);

        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classesDir.toString(), sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }

        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(artifactDir.resolve("lombok-" + version + ".jar")))) {
            jar.putNextEntry(new JarEntry("lombok/Generated.class"));
            Files.copy(classesDir.resolve("lombok/Generated.class"), jar);
            jar.closeEntry();
        }
        Files.write(artifactDir.resolve("lombok-" + version + ".pom"),
                ("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n"
                        + "  <groupId>org.projectlombok</groupId>\n"
                        + "  <artifactId>lombok</artifactId>\n"
                        + "  <version>" + version + "</version>\n"
                        + "</project>\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String pomXml() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>verified-project</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <maven.compiler.source>8</maven.compiler.source>\n" +
                "    <maven.compiler.target>8</maven.compiler.target>\n" +
                "  </properties>\n" +
                "</project>\n";
    }

    private String pomXmlWithRepository(Path repository) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>verified-project</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <maven.compiler.source>8</maven.compiler.source>\n" +
                "    <maven.compiler.target>8</maven.compiler.target>\n" +
                "  </properties>\n" +
                "  <repositories>\n" +
                "    <repository>\n" +
                "      <id>test-repository</id>\n" +
                "      <url>" + repository.toUri() + "</url>\n" +
                "    </repository>\n" +
                "  </repositories>\n" +
                "  <dependencies>\n" +
                "  </dependencies>\n" +
                "</project>\n";
    }

    private String pomXmlWithMaxCompilerErrors(int maxErrors) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
                "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>verified-project</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <properties>\n" +
                "    <maven.compiler.source>8</maven.compiler.source>\n" +
                "    <maven.compiler.target>8</maven.compiler.target>\n" +
                "  </properties>\n" +
                "  <build>\n" +
                "    <plugins>\n" +
                "      <plugin>\n" +
                "        <groupId>org.apache.maven.plugins</groupId>\n" +
                "        <artifactId>maven-compiler-plugin</artifactId>\n" +
                "        <version>3.11.0</version>\n" +
                "        <configuration>\n" +
                "          <compilerArgs>\n" +
                "            <arg>-Xmaxerrs</arg>\n" +
                "            <arg>" + maxErrors + "</arg>\n" +
                "          </compilerArgs>\n" +
                "        </configuration>\n" +
                "      </plugin>\n" +
                "    </plugins>\n" +
                "  </build>\n" +
                "</project>\n";
    }
}
