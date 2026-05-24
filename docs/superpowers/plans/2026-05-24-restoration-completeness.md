# Restoration Completeness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve jar2mp from basic Maven reconstruction toward practical runnable restoration by preserving build metadata, framework signals, resources, compile feedback, startup guidance, and stronger bytecode/source parity reports.

**Architecture:** Keep the current pipeline shape: `JarAnalyzer` extracts archive facts, `PomGenerator` writes the Maven model, `ProjectBuilder` writes the project tree, and verification/report components run after generation. Add focused model/report classes instead of expanding `ProjectBuilder` into a catch-all.

**Tech Stack:** Java 8, JUnit 5, Maven, CFR, standard JDK `JarFile`, XML DOM parsing, process execution for optional Maven verification.

---

## Status Sync - 2026-05-24

**Overall status:** complete for the planned implementation and acceptance scope.

The checklist below was synchronized against the current branch (`codex/decompile-parity-checks`), commit history, source files, tests, and a fresh acceptance run. The historical "red test" steps cannot be reproduced from the final tree, but they are marked complete because the corresponding tests, implementation commits, and passing verification now exist.

Evidence:

- `mvn test`: PASS, 67 tests, 0 failures, 0 errors.
- `mvn -q -DskipTests package`: PASS.
- CLI synthetic smoke: PASS with `--verbose --trace-runtime --verify-build`; generated `pom.xml`, restored source, `decompile-parity-report.md`, `resource-inventory.md`, `restoration-report.md`, `verification-report.md`, `RUNBOOK.md`, `runtime-trace-report.md`, `restoration-score.md`, and `gap-summary.md`.
- Spring Boot executable JAR acceptance: PASS on a generated Spring Boot 2.7.18 sample. The restored project analyzed only the 4 application classes under `BOOT-INF/classes`, skipped root `org/springframework/boot/loader/**`, generated a parent-managed `spring-boot-starter` dependency without `<version>unknown</version>`, compiled successfully, completed runtime tracing with exit code 0, and scored `100/100` (`source=100`, `resource=100`, `runtime=100`, `verification=100`).
- Additional scoring hardening: runtime score now derives expected trace categories from static bytecode evidence, and source score no longer penalizes inner classes that are represented by the restored outer-class source.
- Relevant completion commits: `d2bebf9`, `4f1f28f`, `beea56c`, `c2ceeb3`, `d98d4e4`, `9d95081`, `7ef1d84`, `8c1c121`, `17823ea`, `211f924`.

Planned incomplete items:

- None in this plan.

Known boundaries that remain by design:

- Source-level 100% semantic restoration is not guaranteed for reflection-heavy, generated, obfuscated, or environment-dependent behavior.
- Runtime evidence is opt-in and only covers behavior exercised by the smoke run.
- Runtime score no longer requires unrelated trace families such as file/socket unless bytecode evidence shows those APIs are expected; it still cannot prove unexercised branches are equivalent.
- GUI report-path surfacing is implemented in code, but this plan's acceptance criteria are primarily covered by automated tests and CLI smoke evidence.

---

## File Structure

Create or modify these files during execution:

- Modify: `src/main/java/com/z0fsec/jar2mp/model/PomInfo.java`
  - Add build metadata fields parsed from embedded POMs.
- Create: `src/main/java/com/z0fsec/jar2mp/model/BuildPluginInfo.java`
  - Represents Maven build plugin coordinates, configuration XML, and executions as raw XML snippets.
- Create: `src/main/java/com/z0fsec/jar2mp/model/RepositoryInfo.java`
  - Represents Maven repository/pluginRepository entries.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/MavenMetadataExtractor.java`
  - Parse embedded parent, properties, dependencyManagement, repositories, plugins, and profiles.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java`
  - Preserve parsed build metadata while keeping generated fallback defaults.
- Create: `src/main/java/com/z0fsec/jar2mp/core/FrameworkDetector.java`
  - Detect Spring Boot, Servlet WAR, MyBatis, Shiro, Log4j/Logback, JPA, and native-library signals.
- Create: `src/main/java/com/z0fsec/jar2mp/model/FrameworkFinding.java`
  - Stores detected framework name, confidence, evidence, and recommended restoration action.
- Modify: `src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java`
  - Add framework findings and resource inventory.
- Create: `src/main/java/com/z0fsec/jar2mp/core/ResourceClassifier.java`
  - Classify resources by purpose: config, mapper XML, templates, frontend assets, native libs, certificates, service metadata.
- Create: `src/main/java/com/z0fsec/jar2mp/model/ResourceFinding.java`
  - Stores resource path, category, target location, and risk notes.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/JarAnalyzer.java`
  - Invoke framework and resource classifiers after entry categorization.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
  - Write new restoration reports after project generation.
- Create: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
  - Writes `restoration-report.md` and `resource-inventory.md`.
- Create: `src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java`
  - Optionally runs Maven compile/package checks against generated projects.
- Create: `src/main/java/com/z0fsec/jar2mp/model/VerificationResult.java`
  - Stores verification command, exit code, stdout/stderr excerpts, and failure classification.
- Create: `src/main/java/com/z0fsec/jar2mp/core/StartupDetector.java`
  - Detect startup candidates and generate `RUNBOOK.md`.
- Create: `src/main/java/com/z0fsec/jar2mp/model/StartupFinding.java`
  - Stores main class, app type, command candidates, and evidence.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/BytecodeFingerprint.java`
  - Add exception table, field, annotation, and invokedynamic/bootstrap facts.
- Modify: `src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java`
  - Add risk levels and broader parity sections.
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
  - Add verification/startup report options and update help text.
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
  - Wire new options into analyzer/build/verification flow.
- Modify: `src/main/java/com/z0fsec/jar2mp/ui/MainPanel.java`
  - Surface generated report paths in GUI logs.
- Modify: `README.md`
  - Document restoration completeness reports and limits.

Test files:

- Create: `src/test/java/com/z0fsec/jar2mp/core/MavenMetadataExtractorBuildMetadataTest.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/PomGeneratorBuildMetadataTest.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/FrameworkDetectorTest.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/ResourceClassifierTest.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/ProjectVerifierTest.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/StartupDetectorTest.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/core/DecompileParityReporterTest.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`

---

## Milestone 1: Preserve Embedded Maven Build Metadata

### Task 1: Parse parent, properties, dependencyManagement, repositories, plugins, and profiles

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/model/PomInfo.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/BuildPluginInfo.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/RepositoryInfo.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/MavenMetadataExtractor.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/MavenMetadataExtractorBuildMetadataTest.java`

- [x] **Step 1: Write failing test for embedded POM build metadata**

Create `MavenMetadataExtractorBuildMetadataTest` with a JAR containing:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <java.version>17</java.version>
    <skipTests>true</skipTests>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.acme</groupId>
        <artifactId>platform-bom</artifactId>
        <version>1.2.3</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <repositories>
    <repository>
      <id>internal</id>
      <url>https://repo.example.local/maven</url>
    </repository>
  </repositories>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>2.7.18</version>
      </plugin>
    </plugins>
  </build>
  <profiles>
    <profile>
      <id>prod</id>
      <properties>
        <env>prod</env>
      </properties>
    </profile>
  </profiles>
</project>
```

Assert:

```java
assertEquals("org.springframework.boot", info.getParentGroupId());
assertEquals("spring-boot-starter-parent", info.getParentArtifactId());
assertEquals("2.7.18", info.getParentVersion());
assertEquals("17", info.getProperties().get("java.version"));
assertEquals(1, info.getDependencyManagement().size());
assertEquals(1, info.getRepositories().size());
assertEquals(1, info.getBuildPlugins().size());
assertEquals(1, info.getProfilesXml().size());
```

- [x] **Step 2: Run red test**

Run:

```bash
mvn test -Dtest=MavenMetadataExtractorBuildMetadataTest
```

Expected: compilation failure or assertion failure because the new metadata fields do not exist.

- [x] **Step 3: Add metadata model fields**

Add fields to `PomInfo`:

```java
private String parentGroupId;
private String parentArtifactId;
private String parentVersion;
private String parentRelativePath;
private final Map<String, String> properties = new LinkedHashMap<>();
private final List<MavenDependency> dependencyManagement = new ArrayList<>();
private final List<RepositoryInfo> repositories = new ArrayList<>();
private final List<RepositoryInfo> pluginRepositories = new ArrayList<>();
private final List<BuildPluginInfo> buildPlugins = new ArrayList<>();
private final List<String> profilesXml = new ArrayList<>();
```

Add standard getters/setters for scalar fields and getters for collections.

Create `BuildPluginInfo` with:

```java
private String groupId;
private String artifactId;
private String version;
private String configurationXml;
private final List<String> executionsXml = new ArrayList<>();
```

Create `RepositoryInfo` with:

```java
private String id;
private String url;
private String releasesXml;
private String snapshotsXml;
```

- [x] **Step 4: Implement parsing in `MavenMetadataExtractor`**

Add helpers:

```java
private void parseParent(Element root, PomInfo info)
private void parseProperties(Element root, PomInfo info)
private void parseDependencyManagement(Element root, PomInfo info)
private void parseRepositories(Element root, String tagName, List<RepositoryInfo> output)
private void parseBuildPlugins(Element root, PomInfo info)
private void parseProfiles(Element root, PomInfo info)
private String nodeToXml(Node node)
```

Use DOM traversal only under direct child sections to avoid collecting nested profile/plugin data twice.

- [x] **Step 5: Run test**

Run:

```bash
mvn test -Dtest=MavenMetadataExtractorBuildMetadataTest
```

Expected: pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/model/PomInfo.java \
        src/main/java/com/z0fsec/jar2mp/model/BuildPluginInfo.java \
        src/main/java/com/z0fsec/jar2mp/model/RepositoryInfo.java \
        src/main/java/com/z0fsec/jar2mp/core/MavenMetadataExtractor.java \
        src/test/java/com/z0fsec/jar2mp/core/MavenMetadataExtractorBuildMetadataTest.java
git commit -m "feat: extract embedded Maven build metadata"
```

### Task 2: Generate richer `pom.xml` from parsed build metadata

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/PomGeneratorBuildMetadataTest.java`

- [x] **Step 1: Write failing generator test**

Test should construct `JarAnalysisResult` with a populated `PomInfo` and assert generated `pom.xml` contains:

```xml
<parent>
<dependencyManagement>
<repositories>
<pluginRepositories>
<plugin>
<profiles>
```

Also assert generated fallback compiler plugin is not duplicated when embedded POM already contains `maven-compiler-plugin`.

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=PomGeneratorBuildMetadataTest
```

Expected: fail because `PomGenerator` ignores new metadata.

- [x] **Step 3: Implement metadata emission**

In `PomGenerator.generate`:

1. Emit `<parent>` before GAV if present.
2. Merge embedded properties with generated compiler/source encoding properties, with explicit CLI Java version taking priority.
3. Emit dependencyManagement before dependencies.
4. Emit repositories/pluginRepositories before build.
5. Emit embedded build plugins inside `<build><plugins>`.
6. Add fallback `maven-compiler-plugin` only if no embedded compiler plugin exists.
7. Emit raw profile XML snippets after build.

- [x] **Step 4: Run targeted tests**

```bash
mvn test -Dtest=PomGeneratorBuildMetadataTest,PomGeneratorTest
```

Expected: pass.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java \
        src/test/java/com/z0fsec/jar2mp/core/PomGeneratorBuildMetadataTest.java
git commit -m "feat: preserve Maven build metadata in generated pom"
```

---

## Milestone 2: Framework and Resource Intelligence

### Task 3: Detect frameworks and restoration actions

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/FrameworkDetector.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/FrameworkFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/JarAnalyzer.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/FrameworkDetectorTest.java`

- [x] **Step 1: Write failing tests**

Cover these cases:

- Spring Boot: `BOOT-INF/classes/`, `BOOT-INF/lib/`, manifest `Start-Class`, or dependency `spring-boot`.
- Servlet WAR: file extension `.war`, `WEB-INF/web.xml`, or `WEB-INF/classes`.
- MyBatis: `**/*Mapper.xml`, `mybatis-config.xml`.
- Shiro: `shiro.ini`, `ShiroFilterFactoryBean` class reference.
- Logging: `log4j2.xml`, `logback.xml`, `log4j.properties`.
- Native/JNI: `*.so`, `*.dll`, `*.dylib`.

Assert each finding has:

```java
assertEquals("Spring Boot", finding.getName());
assertTrue(finding.getConfidence() >= 80);
assertTrue(finding.getEvidence().contains("BOOT-INF/classes/"));
assertTrue(finding.getRecommendedActions().contains("Generate Spring Boot run command"));
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=FrameworkDetectorTest
```

Expected: fail because detector does not exist.

- [x] **Step 3: Implement detector**

`FrameworkDetector.detect(JarAnalysisResult result)` should inspect:

- `result.isWar()`
- `result.getResourceFiles()`
- `result.getMetaInfFiles()`
- `result.getManifestInfo()`
- `result.getDetectedDependencies()`
- `result.getClassFiles()`

Use deterministic confidence values:

- 95: structural evidence such as `BOOT-INF/classes`, `WEB-INF/web.xml`
- 85: dependency evidence
- 70: resource/config evidence
- 50: weak class-name evidence

- [x] **Step 4: Wire into `JarAnalyzer`**

After dependency detection and before coordinate finalization:

```java
result.getFrameworkFindings().addAll(frameworkDetector.detect(result));
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=FrameworkDetectorTest,JarAnalyzerTest
```

Expected: pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/FrameworkDetector.java \
        src/main/java/com/z0fsec/jar2mp/model/FrameworkFinding.java \
        src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java \
        src/main/java/com/z0fsec/jar2mp/core/JarAnalyzer.java \
        src/test/java/com/z0fsec/jar2mp/core/FrameworkDetectorTest.java
git commit -m "feat: detect restoration framework signals"
```

### Task 4: Build a resource inventory and write `resource-inventory.md`

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/ResourceClassifier.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/ResourceFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/ResourceClassifierTest.java`

- [x] **Step 1: Write failing classifier test**

Use resource paths:

```text
application.yml
bootstrap.properties
mapper/UserMapper.xml
templates/index.html
static/app.js
WEB-INF/web.xml
META-INF/services/com.example.Plugin
lib/native/libdemo.so
certs/server.jks
```

Expected categories:

```text
CONFIG
MYBATIS_MAPPER
TEMPLATE
FRONTEND_ASSET
SERVLET_DESCRIPTOR
SPI
NATIVE_LIBRARY
CERTIFICATE
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=ResourceClassifierTest
```

Expected: fail because classifier does not exist.

- [x] **Step 3: Implement classifier**

`ResourceClassifier.classify(JarAnalysisResult result)` should map each known resource to:

```java
new ResourceFinding(originalPath, category, targetPath, note)
```

Use the same output target rules as `ProjectBuilder`:

- WAR root resources -> `src/main/webapp`
- `WEB-INF/classes/**` -> `src/main/resources`
- `BOOT-INF/classes/**` -> `src/main/resources`
- JAR root resources -> `src/main/resources`

- [x] **Step 4: Write report from `ProjectBuilder`**

`RestorationReportWriter.writeResourceInventory(File outputDir, JarAnalysisResult analysis)` writes:

```markdown
# Resource inventory

| Category | Original path | Target path | Notes |
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=ResourceClassifierTest,ProjectBuilderTest
```

Expected: pass and generated test project contains `resource-inventory.md`.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/ResourceClassifier.java \
        src/main/java/com/z0fsec/jar2mp/model/ResourceFinding.java \
        src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java \
        src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java \
        src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java \
        src/test/java/com/z0fsec/jar2mp/core/ResourceClassifierTest.java
git commit -m "feat: report classified restored resources"
```

---

## Milestone 3: Compile and Startup Verification

### Task 5: Add optional Maven build verification

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/VerificationResult.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/ProjectVerifierTest.java`

- [x] **Step 1: Write failing verifier tests**

Use a temporary Maven project with a minimal `pom.xml` and `src/main/java/demo/App.java`.

Assert:

```java
VerificationResult result = new ProjectVerifier().verify(projectDir, "compile");
assertEquals(0, result.getExitCode());
assertTrue(result.getCommand().contains("mvn"));
assertTrue(result.getSummary().contains("BUILD SUCCESS"));
```

Also test a broken source file and assert:

```java
assertTrue(result.getExitCode() != 0);
assertEquals("COMPILATION_ERROR", result.getFailureType());
assertTrue(result.getSummary().contains("Compilation failure"));
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=ProjectVerifierTest
```

Expected: fail because verifier does not exist.

- [x] **Step 3: Implement verifier**

Implement:

```java
public VerificationResult verify(File projectDir, String goal)
```

Rules:

- Default goal: `compile`
- Command: `mvn -q -DskipTests compile`
- Timeout: 120 seconds
- Capture stdout/stderr into bounded strings, max 20 KB each.
- Classify failure:
  - `DEPENDENCY_RESOLUTION`
  - `COMPILATION_ERROR`
  - `TEST_FAILURE`
  - `TIMEOUT`
  - `MAVEN_NOT_FOUND`
  - `UNKNOWN`

- [x] **Step 4: Add CLI option**

Add:

```text
--verify-build                Run Maven compile verification after project generation
--verify-goal <goal>          Maven goal used by verification, default compile
```

Do not run verification by default because it may require network access.

- [x] **Step 5: Write `verification-report.md`**

When verification is enabled, write:

```markdown
# Verification report

- Command:
- Exit code:
- Failure type:
- Summary:
```

- [x] **Step 6: Run tests**

```bash
mvn test -Dtest=ProjectVerifierTest,CliRunnerTest
```

Expected: pass.

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java \
        src/main/java/com/z0fsec/jar2mp/model/VerificationResult.java \
        src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java \
        src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java \
        src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java \
        src/test/java/com/z0fsec/jar2mp/core/ProjectVerifierTest.java
git commit -m "feat: add optional generated project verification"
```

### Task 6: Generate startup runbook

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/StartupDetector.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/StartupFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/StartupDetectorTest.java`

- [x] **Step 1: Write failing startup detector tests**

Cover:

- Spring Boot JAR with manifest `Start-Class: com.example.App`.
- Plain JAR with `Main-Class: com.example.Main`.
- WAR with `WEB-INF/web.xml`.
- No startup evidence.

Expected commands:

```text
mvn spring-boot:run
mvn exec:java -Dexec.mainClass=com.example.Main
mvn package
```

WAR runbook should say external servlet container is needed unless embedded server dependencies are detected.

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=StartupDetectorTest
```

Expected: fail because detector does not exist.

- [x] **Step 3: Implement startup detection**

Evidence priority:

1. Manifest `Start-Class`
2. Manifest `Main-Class`
3. Spring Boot framework finding
4. WAR framework finding
5. Bytecode method named `main` with descriptor `([Ljava/lang/String;)V`

- [x] **Step 4: Generate `RUNBOOK.md`**

Write:

```markdown
# Runbook

## Detected application type
## Startup candidates
## Verification commands
## Known gaps
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=StartupDetectorTest,ProjectBuilderTest
```

Expected: pass and generated test output includes `RUNBOOK.md`.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/StartupDetector.java \
        src/main/java/com/z0fsec/jar2mp/model/StartupFinding.java \
        src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java \
        src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java \
        src/test/java/com/z0fsec/jar2mp/core/StartupDetectorTest.java
git commit -m "feat: generate startup runbook"
```

---

## Milestone 4: Decompile Parity and Source Quality

### Task 7: Extend bytecode fingerprint coverage

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/BytecodeFingerprint.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/core/DecompileParityReporterTest.java`

- [x] **Step 1: Write failing tests**

Add compiled fixture class with:

- try/catch/finally
- lambda expression
- annotation
- field access
- generic signature
- thrown exception

Assert report includes:

```text
Exception handlers
Invokedynamic
Fields
Annotations
Generic signatures
Thrown exceptions
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=DecompileParityReporterTest
```

Expected: fail because current report lacks these sections.

- [x] **Step 3: Parse class-level and method-level attributes**

Add support for:

- `Signature`
- `RuntimeVisibleAnnotations`
- `RuntimeInvisibleAnnotations`
- `Exceptions`
- Code exception table
- field table
- bootstrap method references enough to report invokedynamic presence

- [x] **Step 4: Add risk levels**

Report each method as:

```text
LOW: no reflection, no invokedynamic, local variable table present
MEDIUM: lambdas/invokedynamic or missing local variable table
HIGH: reflection, native methods, parse errors, missing source
```

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=DecompileParityReporterTest
```

Expected: pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/BytecodeFingerprint.java \
        src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java \
        src/test/java/com/z0fsec/jar2mp/core/DecompileParityReporterTest.java
git commit -m "feat: expand decompile parity fingerprinting"
```

### Task 8: Add decompiler failure inventory and class-copy fallback report

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/DecompilerBridge.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Create: `src/main/java/com/z0fsec/jar2mp/model/DecompileFinding.java`
- Create: `src/test/java/com/z0fsec/jar2mp/core/DecompilerFailureReportTest.java`

- [x] **Step 1: Write failing test**

Use a malformed `.class` entry and assert:

```java
assertTrue(report.contains("Failed to decompile"));
assertTrue(report.contains("raw class retained"));
assertTrue(Files.exists(output.resolve("target/original-classes/...")));
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=DecompilerFailureReportTest
```

Expected: fail because there is no structured decompile finding.

- [x] **Step 3: Implement structured decompile result**

Change `DecompilerBridge` to expose:

```java
public DecompileResult decompileDetailed(byte[] classBytes, String className)
```

Keep existing `decompile` method for compatibility.

- [x] **Step 4: Retain raw original class bytes**

When source generation fails, copy original class bytes to:

```text
target/original-classes/<classPath>
```

Do not put these under `src/main/java`.

- [x] **Step 5: Run tests**

```bash
mvn test -Dtest=DecompilerFailureReportTest,ProjectBuilderTest
```

Expected: pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/DecompilerBridge.java \
        src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java \
        src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java \
        src/main/java/com/z0fsec/jar2mp/model/DecompileFinding.java \
        src/test/java/com/z0fsec/jar2mp/core/DecompilerFailureReportTest.java
git commit -m "feat: report decompile failures with raw class fallback"
```

---

## Milestone 5: CLI, GUI, Docs, and End-to-End Acceptance

### Task 9: Add CLI and GUI report surfacing

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/ui/MainPanel.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`

- [x] **Step 1: Write CLI test**

Assert CLI output in verbose mode mentions:

```text
decompile-parity-report.md
resource-inventory.md
restoration-report.md
RUNBOOK.md
```

- [x] **Step 2: Run red test**

```bash
mvn test -Dtest=CliRunnerTest
```

Expected: fail because CLI does not print all report paths.

- [x] **Step 3: Implement output surfacing**

After project build:

```java
System.out.println("  Reports:");
System.out.println("    " + new File(outputDir, "restoration-report.md").getAbsolutePath());
System.out.println("    " + new File(outputDir, "resource-inventory.md").getAbsolutePath());
System.out.println("    " + new File(outputDir, "decompile-parity-report.md").getAbsolutePath());
System.out.println("    " + new File(outputDir, "RUNBOOK.md").getAbsolutePath());
```

GUI should append equivalent log lines after each project build.

- [x] **Step 4: Run tests**

```bash
mvn test -Dtest=CliRunnerTest
```

Expected: pass.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java \
        src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java \
        src/main/java/com/z0fsec/jar2mp/ui/MainPanel.java \
        src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java
git commit -m "feat: surface restoration reports in cli and gui"
```

### Task 10: Update README and run full acceptance suite

**Files:**
- Modify: `README.md`

- [x] **Step 1: Update README sections**

Add:

- "Restoration completeness reports"
- "What can and cannot be restored"
- "Recommended verification workflow"
- "Interpreting decompile parity risk"
- Updated project output tree including:

```text
decompile-parity-report.md
resource-inventory.md
restoration-report.md
verification-report.md
RUNBOOK.md
target/original-classes/
```

- [x] **Step 2: Run full tests**

```bash
mvn test
```

Expected:

```text
BUILD SUCCESS
Failures: 0
Errors: 0
```

- [x] **Step 3: Run package build**

```bash
mvn clean package
```

Expected:

```text
BUILD SUCCESS
target/jar2mp-1.0-jar-with-dependencies.jar
```

- [x] **Step 4: Smoke test CLI on synthetic JAR**

Create a temporary sample JAR using existing test fixtures or a small compiled class. Run:

```bash
java -jar target/jar2mp-1.0-jar-with-dependencies.jar --verbose --verify-build -o /tmp/jar2mp-smoke /path/to/sample.jar
```

Expected generated files:

```text
pom.xml
src/main/java
src/main/resources
decompile-parity-report.md
resource-inventory.md
restoration-report.md
verification-report.md
RUNBOOK.md
```

- [x] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document restoration completeness workflow"
```

---

## Acceptance Criteria

The plan is complete when:

- `mvn test` passes.
- `mvn clean package` passes.
- Generated projects include:
  - `pom.xml`
  - restored sources/resources
  - `decompile-parity-report.md`
  - `resource-inventory.md`
  - `restoration-report.md`
  - `RUNBOOK.md`
  - optional `verification-report.md` when `--verify-build` is used
- WAR, Spring Boot JAR, and plain JAR fixtures each have targeted tests.
- Build metadata from embedded POMs is preserved instead of being discarded.
- Framework and resource reports explain what was detected and what still needs manual action.
- Verification failures are classified rather than hidden in raw Maven output.
- The README clearly states that source-level 100% restoration is not guaranteed, and explains what evidence the reports provide.

## Recommended Execution Order

1. Milestone 1: Build metadata preservation.
2. Milestone 2: Framework and resource intelligence.
3. Milestone 3: Compile and startup verification.
4. Milestone 4: Decompile parity/source quality.
5. Milestone 5: CLI, GUI, docs, and acceptance.

This order improves practical runnable restoration first, then improves visibility and source-level confidence.

## Self-Review

- Spec coverage: all missing focus areas from the assessment are covered: richer POM/build metadata, framework/config/resource classification, compile verification, startup guidance, decompile parity, and docs.
- Placeholder scan: no implementation task says "TBD" or "implement later"; each task has files, tests, commands, and expected results.
- Type consistency: model names are stable across tasks: `FrameworkFinding`, `ResourceFinding`, `VerificationResult`, `StartupFinding`, `BuildPluginInfo`, and `RepositoryInfo`.
