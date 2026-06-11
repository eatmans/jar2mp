# Real-World Fidelity Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Productize real-world runtime launch classification and artifact fidelity comparison so jar2mp can measure the remaining gap to faithful restoration.

**Architecture:** Add reusable core services for artifact comparison and runtime launch planning, then wire them into CLI reports and real-world regression scripts. Keep compile verification, runtime probing, and artifact fidelity as separate evidence streams so a green build is not mistaken for byte-identical restoration.

**Tech Stack:** Java 8-compatible production code, JUnit 5 tests, Maven, Bash regression scripts, ZIP/JAR APIs.

**Progress sync (2026-06-11):** The artifact comparator, runtime launch classification, trace timeout classification, real-world regression columns, and current verification commands have landed. This plan being checked off means the evidence pipeline exists and passes the current compile/regression gates; it does not mean source-rebuilt artifacts are byte-identical.

---

### Task 1: Artifact Fidelity Comparator

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/model/ArtifactFidelityResult.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparator.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparatorTest.java`

- [x] **Step 1: Write failing comparator tests**

Add tests that create temporary ZIP/JAR files with:

```java
@Test
void reportsIdenticalArchiveAsExactMatch() throws Exception {
    Path original = tempDir.resolve("original.jar");
    Path rebuilt = tempDir.resolve("rebuilt.jar");
    writeJar(original, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
            entry("com/example/App.class", classBytes(52, "App")),
            entry("BOOT-INF/lib/lib-1.0.jar", "lib"));
    writeJar(rebuilt, entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
            entry("com/example/App.class", classBytes(52, "App")),
            entry("BOOT-INF/lib/lib-1.0.jar", "lib"));

    ArtifactFidelityResult result = new ArtifactFidelityComparator().compare(original.toFile(), rebuilt.toFile());

    assertTrue(result.isExactMatch());
    assertEquals(3, result.getSameSha256());
    assertEquals(0, result.getDifferentSha256());
    assertEquals(0, result.getMissingEntries());
    assertEquals(0, result.getExtraEntries());
    assertTrue(result.isManifestSame());
}
```

Also add tests named:

```java
reportsMissingAndExtraEntries()
reportsClassByteDifferencesSeparately()
reportsNestedLibraryVersionDrift()
reportsManifestDifference()
```

- [x] **Step 2: Run tests to verify RED**

Run:

```bash
mvn -q -Dtest=ArtifactFidelityComparatorTest test
```

Expected: compilation fails because `ArtifactFidelityComparator` and `ArtifactFidelityResult` do not exist.

- [x] **Step 3: Implement result model**

Create `ArtifactFidelityResult` with integer fields for:

```java
originalEntryTotal, rebuiltEntryTotal,
originalClassEntries, rebuiltClassEntries,
originalResourceEntries, rebuiltResourceEntries,
originalNestedLibs, rebuiltNestedLibs,
commonEntries, sameSha256, differentSha256,
missingEntries, extraEntries,
commonClassEntries, sameClassBytes, differentClassBytes,
commonNestedLibs, sameNestedLibs, differentNestedLibs,
missingNestedLibs, extraNestedLibs
```

Add booleans:

```java
manifestOriginalPresent, manifestRebuiltPresent, manifestSame
```

Add list fields:

```java
sampleMissingEntries, sampleExtraEntries, sampleDifferentEntries
```

Add `isExactMatch()` returning true only when `differentSha256`, `missingEntries`, and `extraEntries` are all zero.

- [x] **Step 4: Implement comparator**

Use `java.util.zip.ZipFile` or `java.util.jar.JarFile` to read non-directory entries, compute SHA-256 for each entry, classify entries using:

```java
entry.endsWith(".class")
entry.endsWith(".jar") && (entry.startsWith("BOOT-INF/lib/") || entry.startsWith("WEB-INF/lib/") || entry.startsWith("lib/") || entry.contains("/lib/"))
```

Treat all non-class, non-nested-lib entries as resources. Compare entry names exactly.

- [x] **Step 5: Implement Markdown/CSV report writer**

`ArtifactFidelityReportWriter.write(File outputDir, ArtifactFidelityResult result)` writes:

```text
artifact-fidelity-report.md
artifact-fidelity-summary.csv
```

The Markdown must include exact-match status, entry counts, class diff counts, nested lib diff counts, manifest equality, and sample missing/extra/different entries.

- [x] **Step 6: Run focused and full tests**

Run:

```bash
mvn -q -Dtest=ArtifactFidelityComparatorTest test
mvn -q test
```

Expected: all tests pass.

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/model/ArtifactFidelityResult.java \
        src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparator.java \
        src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityReportWriter.java \
        src/test/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparatorTest.java
git commit -m "feat: add artifact fidelity comparator"
```

### Task 2: Runtime Launch Planner

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/model/RuntimeLaunchPlan.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeLaunchPlanner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeLaunchPlannerTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java`

- [x] **Step 1: Write failing planner tests**

Add tests named:

```java
classifiesSpringBootExecutableJar()
classifiesSpringBootExecutableWar()
classifiesThinJarWithoutMainClass()
classifiesStandardWarWithoutLauncher()
classifiesLibraryJarWithoutApplicationEntrypoint()
```

Each test should build a minimal `JarAnalysisResult` with manifest and class/resource facts, call `new RuntimeLaunchPlanner().plan(file, analysis)`, and assert the expected enum value and reason.

- [x] **Step 2: Run tests to verify RED**

Run:

```bash
mvn -q -Dtest=RuntimeLaunchPlannerTest test
```

Expected: compilation fails because planner classes do not exist.

- [x] **Step 3: Implement launch plan model**

`RuntimeLaunchPlan` contains:

```java
enum LaunchType { EXECUTABLE_JAR, EXECUTABLE_WAR, THIN_JAR, STANDARD_WAR, LIBRARY, UNKNOWN }
enum SupportStatus { SUPPORTED, UNSUPPORTED }
```

Fields:

```java
LaunchType launchType;
SupportStatus supportStatus;
String mainClass;
String launchSource;
String reason;
List<String> notes;
```

- [x] **Step 4: Implement planner**

Rules:

```java
Start-Class + Main-Class containing JarLauncher -> EXECUTABLE_JAR
Start-Class + Main-Class containing WarLauncher -> EXECUTABLE_WAR
analysis.isWar() without executable launcher -> STANDARD_WAR unsupported
manifest main class present -> EXECUTABLE_JAR supported
startup evidence present but no complete manifest classpath -> THIN_JAR unsupported
no entrypoint and few/no application classes -> LIBRARY unsupported
otherwise UNKNOWN unsupported
```

- [x] **Step 5: Wire planner into smoke runner**

`RuntimeSmokeRunner.runSmoke` must ask the planner first. If unsupported, return a `SmokeRunResult` with:

```java
failureMessage = "Unsupported runtime launch: " + plan.getReason()
launchType = plan.getLaunchType().name()
supportStatus = plan.getSupportStatus().name()
```

Supported plans continue to use current command construction.

- [x] **Step 6: Report launch classification**

Update `RuntimeTraceReportWriter` so `runtime-trace-report.md` includes:

```text
- Launch type: `EXECUTABLE_JAR`
- Launch support: `SUPPORTED`
- Launch reason: `...`
```

- [x] **Step 7: Run focused and full tests**

Run:

```bash
mvn -q -Dtest=RuntimeLaunchPlannerTest,RuntimeSmokeRunnerTest,RuntimeTraceReportWriterTest test
mvn -q test
```

Expected: all tests pass.

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/model/RuntimeLaunchPlan.java \
        src/main/java/com/z0fsec/jar2mp/core/RuntimeLaunchPlanner.java \
        src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java \
        src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java \
        src/test/java/com/z0fsec/jar2mp/core/RuntimeLaunchPlannerTest.java \
        src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java \
        src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java
git commit -m "feat: classify runtime launch support"
```

### Task 3: Runtime Timeout Status

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java`

- [x] **Step 1: Write failing tests**

Add a test proving timeout with at least one event is classified as trace-collected timeout:

```java
assertEquals("TRACE_COLLECTED_TIMEOUT", result.getRunStatus());
assertEquals(-1, result.getExitCode());
assertFalse(result.getTraceResult().getEvents().isEmpty());
```

Add a report writer test asserting the Markdown contains:

```text
- Run status: `TRACE_COLLECTED_TIMEOUT`
```

- [x] **Step 2: Run tests to verify RED**

Run:

```bash
mvn -q -Dtest=RuntimeSmokeRunnerTest,RuntimeTraceReportWriterTest test
```

Expected: tests fail because `runStatus` is not present.

- [x] **Step 3: Implement run status**

Add `runStatus` to `SmokeRunResult` with values:

```text
NOT_RUN
EXIT_ZERO
EXIT_NON_ZERO
TIMEOUT_NO_EVENTS
TRACE_COLLECTED_TIMEOUT
UNSUPPORTED_LAUNCH
ERROR
```

When a timeout occurs, read the trace file before final classification. If event count is greater than zero, use `TRACE_COLLECTED_TIMEOUT`; otherwise use `TIMEOUT_NO_EVENTS`.

- [x] **Step 4: Update report output**

Add run status to the run summary section. Keep existing exit code and failure message for compatibility.

- [x] **Step 5: Run tests**

Run:

```bash
mvn -q -Dtest=RuntimeSmokeRunnerTest,RuntimeTraceReportWriterTest test
mvn -q test
```

Expected: all tests pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java \
        src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java \
        src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java \
        src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java
git commit -m "fix: classify runtime trace timeouts"
```

### Task 4: Real-World Regression Outputs

**Files:**
- Modify: `scripts/regression/run-github-realworld-regression.sh`
- Modify: `docs/github-realworld-regression.md`
- Test: script execution

- [x] **Step 1: Add artifact fidelity summary generation**

After each restored project is generated, package the restored project with the same skip flags used by `ProjectVerifier`:

```bash
mvn -q -DskipTests -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dspring-javaformat.skip=true -Dimpsort.skip=true -Dformatter.skip=true -Dspotless.check.skip=true -Dspotless.apply.skip=true -Dlicense.skip=true -Drat.skip=true -Denforcer.skip=true -Djacoco.skip=true -Dgit.commit.id.skip=true -Dmaven.javadoc.skip=true package
```

Then compare original and rebuilt artifact with the new comparator CLI entrypoint or a Java helper method exposed through the jar2mp CLI.

- [x] **Step 2: Add runtime probe classification columns**

The summary CSV should keep compile gate columns and add:

```csv
runtime_launch_type,runtime_launch_support,runtime_run_status,runtime_events,artifact_exact,artifact_diff_sha,artifact_missing,artifact_extra,artifact_diff_classes
```

- [x] **Step 3: Keep compile gate separate**

The existing `status` column continues to mean compile gate PASS/FAIL only. Runtime and artifact fidelity are reported as evidence columns and should not fail the compile gate yet.

- [x] **Step 4: Update docs**

Document that:

- compile gate is still the regression pass/fail gate
- runtime support can be unsupported for library, thin JAR, and standard WAR samples
- artifact fidelity is expected to show remaining diffs until raw-bytecode package mode exists

- [x] **Step 5: Run regression**

Run:

```bash
./scripts/regression/run-github-realworld-regression.sh
```

Expected: 8 compile gate PASS rows, plus runtime/artifact columns populated.

- [x] **Step 6: Commit**

```bash
git add scripts/regression/run-github-realworld-regression.sh docs/github-realworld-regression.md
git commit -m "chore: report real-world fidelity evidence"
```

### Task 5: Final Verification

**Files:**
- No code edits unless verification exposes a bug.

- [x] **Step 1: Run diff check**

```bash
git diff --check
```

Expected: no whitespace errors.

- [x] **Step 2: Run unit tests**

```bash
mvn -q test
```

Expected: all tests pass.

- [x] **Step 3: Run local sample regression**

```bash
./scripts/regression/run-sample-regression.sh
```

Expected: all local samples PASS, or failures are documented with exact report paths.

- [x] **Step 4: Run GitHub real-world regression**

```bash
./scripts/regression/run-github-realworld-regression.sh
```

Expected: compile gate remains `8/8 PASS`; runtime and artifact fidelity evidence columns are populated.

- [x] **Step 5: Summarize remaining gap**

Use the generated summaries to report:

- compile pass count
- executable runtime probe count
- unsupported runtime launch count
- artifact exact-match count
- total artifact diff counts

Do not claim 100% restoration unless artifact exact-match is `8/8` and runtime requirements are explicitly satisfied.
