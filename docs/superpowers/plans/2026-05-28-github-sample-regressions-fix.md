# GitHub Sample Regressions Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the highest-impact failures exposed by the ad-hoc GitHub release asset matrix: invalid Maven artifact IDs from manifest metadata and lost nested class declarations when whole-JAR CFR output is rejected too aggressively.

**Architecture:** Keep changes scoped to coordinate normalization, context-source acceptance, and source post-processing. Do not attempt a full shaded/Kotlin recovery redesign in this batch; use the newly downloaded GitHub samples as integration evidence and leave remaining decompiler limitations explicit.

**Tech Stack:** Java 8, JUnit 5, Maven Surefire, CFR decompiler integration.

---

### Task 1: Sanitize Generated Maven Coordinates

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/PomGeneratorTest.java`

- [x] **Step 1: Write the failing test**

Add a test that feeds manifest-derived coordinates containing spaces into `PomGenerator` and expects Maven-safe IDs.

```java
@Test
void sanitizesManifestDerivedMavenCoordinates() {
    JarAnalysisResult analysis = new JarAnalysisResult();
    analysis.setDetectedGroupId("Remko Popma");
    analysis.setDetectedArtifactId("Picocli Code Generation");
    analysis.setDetectedVersion("4.7.7");

    String pomXml = new PomGenerator().generate(analysis, new ProjectConfig());

    assertTrue(pomXml.contains("<groupId>remko.popma</groupId>"));
    assertTrue(pomXml.contains("<artifactId>picocli-code-generation</artifactId>"));
    assertFalse(pomXml.contains("<artifactId>Picocli Code Generation</artifactId>"));
}
```

- [x] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -q -Dtest=PomGeneratorTest#sanitizesManifestDerivedMavenCoordinates test
```

Expected: FAIL because the current POM writes the raw manifest title.

- [x] **Step 3: Implement minimal normalization**

Normalize `groupId` and `artifactId` before rendering:

```java
String groupId = normalizeGroupId(config.getGroupId() != null ? config.getGroupId() : analysis.getDetectedGroupId());
String artifactId = normalizeArtifactId(config.getArtifactId() != null ? config.getArtifactId() : analysis.getDetectedArtifactId());
```

Rules:
- groupId: trim, lower-case, replace whitespace with `.`, remove invalid Maven-id characters from each segment, fall back to `com.unknown`.
- artifactId: trim, lower-case, replace invalid runs with `-`, strip leading/trailing punctuation, fall back to `artifact`.

- [x] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -q -Dtest=PomGeneratorTest#sanitizesManifestDerivedMavenCoordinates test
```

Expected: PASS.

### Task 2: Keep Usable Whole-JAR CFR Context Sources

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/SourcePostProcessor.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/SourcePostProcessorTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`

- [x] **Step 1: Write a post-processor failing test**

Add a test proving CFR `void varN;` temporary locals are converted to compilable Java.

```java
@Test
void replacesCfrVoidTemporaryLocalDeclarations() {
    String source = "class Sample {\\n" +
            "  void run() {\\n" +
            "    void var15_20;\\n" +
            "    System.out.println(1);\\n" +
            "  }\\n" +
            "}\\n";

    String processed = new SourcePostProcessor().process(source, "Sample");

    assertFalse(processed.contains("void var15_20;"));
    assertTrue(processed.contains("Object var15_20 = null;"));
}
```

- [x] **Step 2: Write a context-source acceptance failing test**

Add a package-visible seam if needed, then test that a source containing `"*** masked"` is not rejected solely because of literal asterisks, and that a source with nested declarations remains usable after post-processing.

```java
@Test
void acceptsContextSourceWithMaskedStringLiteral() {
    ProjectBuilder builder = new ProjectBuilder(new ProjectConfig());
    String source = "package demo;\\nclass Sample { String value = \\"*** masked\\"; }\\n";

    assertTrue(builder.isContextSourceUsableForTest(source));
}
```

- [x] **Step 3: Run targeted tests to verify they fail**

Run:

```bash
mvn -q -Dtest=SourcePostProcessorTest#replacesCfrVoidTemporaryLocalDeclarations,ProjectBuilderTest#acceptsContextSourceWithMaskedStringLiteral test
```

Expected: FAIL because `void var...` is untouched and context source is rejected by broad `** ` matching.

- [x] **Step 4: Implement minimal context-source fix**

In `ProjectBuilder.isContextSourceUsable`, remove the broad `source.contains("** ")` rejection and keep specific CFR structural warning checks. In `SourcePostProcessor`, replace local `void varName;` declarations with `Object varName = null;`.

Also cover the follow-on CFR context regressions exposed by the first integration rerun:
- strip only the leading decompiler header and preserve `package` / `import` declarations,
- shorten invalid qualified inner-class instance creation like `ansi.new Ansi.Text(...)` to `ansi.new Text(...)`,
- infer numeric generic placeholders like `ArrayList<1>` from the enhanced-for element type when possible.

- [x] **Step 5: Run targeted tests to verify they pass**

Run:

```bash
mvn -q -Dtest=SourcePostProcessorTest#replacesCfrVoidTemporaryLocalDeclarations,ProjectBuilderTest#acceptsContextSourceWithMaskedStringLiteral test
```

Expected: PASS.

### Task 3: Verify Against Unit Tests And GitHub Samples

**Files:**
- Verify modified code.

- [x] **Step 1: Run focused test classes**

Run:

```bash
mvn -q -Dtest=PomGeneratorTest,SourcePostProcessorTest,ProjectBuilderTest test
```

Expected: PASS.

- [x] **Step 2: Run full unit tests**

Run:

```bash
mvn -q test
git diff --check
```

Expected: PASS and no whitespace errors.

- [x] **Step 3: Re-run the impacted GitHub samples**

Run current jar2mp package, then re-run at least:

```bash
mvn -q -DskipTests package
java -jar target/jar2mp-1.0-jar-with-dependencies.jar --verbose --emit-raw-artifact --verify-build --verify-goal compile -f -o target/adhoc-github-release-assets/restored-after-fix/picocli-codegen target/adhoc-github-release-assets/assets/picocli-codegen-4.7.7.jar
java -jar target/jar2mp-1.0-jar-with-dependencies.jar --verbose --emit-raw-artifact --verify-build --verify-goal compile -f -o target/adhoc-github-release-assets/restored-after-fix/picocli target/adhoc-github-release-assets/assets/picocli-4.7.7.jar
```

Expected:
- `picocli-codegen` no longer fails with Maven model parse error from invalid artifactId.
- `picocli` no longer fails from wholesale missing nested declarations; remaining compiler failures, if any, must be recorded from `verification-errors.md`.

- [x] **Step 4: Commit the verified batch**

Run:

```bash
git add docs/superpowers/plans/2026-05-28-github-sample-regressions-fix.md src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java src/main/java/com/z0fsec/jar2mp/core/SourcePostProcessor.java src/test/java/com/z0fsec/jar2mp/core/PomGeneratorTest.java src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java src/test/java/com/z0fsec/jar2mp/core/SourcePostProcessorTest.java
git commit -m "fix: improve github sample restoration"
```
