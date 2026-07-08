# Review Findings Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the three code-review findings so CLI verification failures are observable, release asset regression cannot silently pass with zero effective samples, and invalid Java-version arguments fail cleanly.

**Architecture:** Keep the fixes scoped to the existing CLI and regression shell script surfaces. Add tests before implementation for CLI behavior, and add a shell-level smoke check for the release-assets summary gate. Do not change artifact fidelity semantics.

**Tech Stack:** Java 8, JUnit 5, Maven Surefire, Bash regression scripts.

---

### Task 1: CLI Verification Failure Exit Code

**Files:**
- Modify: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`

- [x] **Step 1: Write the failing test**

Add a JUnit test that creates a valid sample JAR, runs CLI with `--verify-build --verify-goal no-such-goal`, and asserts the CLI returns non-zero while still writing `verification-report.md`.

```java
@Test
void verifyBuildFailureMakesCliFail() throws Exception {
    Path jar = createJar("sample-1.0.jar", "com/example/Sample.class", minimalClassBytes(52, "com/example/Sample"));
    Path output = tempDir.resolve("out");

    int exitCode = new CliRunner().run(new String[]{
            "--no-decompile",
            "--verify-build",
            "--verify-goal", "no-such-goal",
            "-f",
            "-o", output.toString(),
            jar.toString()
    });

    assertNotEquals(0, exitCode);
    Path report = output.resolve("sample").resolve("verification-report.md");
    assertTrue(Files.exists(report));
    assertTrue(Files.readString(report).contains("Failure type:"));
}
```

- [x] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -q -Dtest=CliRunnerTest#verifyBuildFailureMakesCliFail test
```

Expected: FAIL because `CliRunner` currently counts the file as successful even when Maven verification fails.

- [x] **Step 3: Write minimal implementation**

In `CliRunner.run`, after writing verification reports, treat non-zero verification exit or non-`NONE` failure type as a per-file failure.

```java
if (verification.getExitCode() != 0 || !"NONE".equals(verification.getFailureType())) {
    failedCount++;
    continue;
}
```

- [x] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -q -Dtest=CliRunnerTest#verifyBuildFailureMakesCliFail test
```

Expected: PASS.

### Task 2: Invalid Java Version Argument Handling

**Files:**
- Modify: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`

- [x] **Step 1: Write the failing test**

Add a JUnit test that calls `new CliRunner().run(new String[]{"--java-version", "abc"})` and expects a normal CLI error return instead of an uncaught exception.

```java
@Test
void invalidJavaVersionReturnsUsageError() {
    int exitCode = new CliRunner().run(new String[]{"--java-version", "abc"});

    assertEquals(1, exitCode);
}
```

- [x] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
mvn -q -Dtest=CliRunnerTest#invalidJavaVersionReturnsUsageError test
```

Expected: ERROR from `NumberFormatException`.

- [x] **Step 3: Write minimal implementation**

Add an integer parser helper and use it for `--java-version`.

```java
private Integer parsePositiveInt(String value, String optionName) {
    try {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            System.err.println("Invalid value for " + optionName + ": " + value);
            return null;
        }
        return Integer.valueOf(parsed);
    } catch (NumberFormatException e) {
        System.err.println("Invalid value for " + optionName + ": " + value);
        return null;
    }
}
```

- [x] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
mvn -q -Dtest=CliRunnerTest#invalidJavaVersionReturnsUsageError test
```

Expected: PASS.

### Task 3: Release Asset Zero Effective Samples Gate

**Files:**
- Modify: `scripts/regression/run-github-release-assets-regression.sh`

- [x] **Step 1: Write a shell-level failing check**

Run the current script against the existing all-download-failed summary logic by setting strict mode off and checking that the script would allow `DOWNLOAD_FAILED` rows.

```bash
bash -n scripts/regression/run-github-release-assets-regression.sh
grep -q 'DOWNLOAD_FAILED' target/release-assets-samples/report/github-release-assets-summary.csv
```

Expected before implementation: syntax passes and the existing report contains download failures that are non-fatal by default.

- [x] **Step 2: Write minimal implementation**

Track effective sample count in the script and fail when no rows are `PASS` or `PASS_WITH_WARNINGS`, independent of `STRICT_RELEASE_ASSETS`.

```bash
local effective_count
effective_count="$(awk -F',' 'NR > 1 && ($2 == "\"PASS\"" || $2 == "\"PASS_WITH_WARNINGS\"") { count++ } END { print count + 0 }' "${REPORT_DIR}/github-release-assets-summary.csv")"
if [[ "${effective_count}" -eq 0 ]]; then
  log "No release asset samples completed successfully."
  exit 1
fi
```

- [x] **Step 3: Verify script syntax and gate text**

Run:

```bash
bash -n scripts/regression/run-github-release-assets-regression.sh
rg -n "No release asset samples completed successfully|effective_count" scripts/regression/run-github-release-assets-regression.sh
```

Expected: syntax passes and the gate is present.

### Task 4: Batch Verification And Commit

**Files:**
- Verify all modified code and scripts.

- [x] **Step 1: Run targeted tests**

Run:

```bash
mvn -q -Dtest=CliRunnerTest test
```

Expected: PASS.

- [x] **Step 2: Run full tests and script syntax checks**

Run:

```bash
mvn -q test
bash -n scripts/regression/run-github-release-assets-regression.sh
bash -n scripts/regression/run-github-realworld-regression.sh
bash -n scripts/regression/run-sample-regression.sh
git diff --check
```

Expected: all pass.

- [x] **Step 3: Commit the verified batch**

Run:

```bash
git add docs/superpowers/plans/2026-05-28-review-findings-fix.md src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java scripts/regression/run-github-release-assets-regression.sh
git commit -m "fix: address review findings"
```
