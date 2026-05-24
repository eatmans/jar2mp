# Jar2Mp Restoration Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push jar2mp from static reconstruction into a three-signal restoration pipeline that combines multi-engine decompilation, runtime trace capture, and quantified fidelity scoring.

**Architecture:** Keep `JarAnalyzer` as the static indexer and `ProjectBuilder` as the file materializer. Add two independent evidence streams: decompiler arbitration for source fidelity and runtime trace capture for dynamic behavior. Merge those signals into a single restoration score and gap summary, then surface the new reports in CLI and GUI without bloating the existing panels.

**Tech Stack:** Java 8, Maven, CFR, Fernflower (`org.jboss.windup.decompiler.fernflower:fernflower:2.5.0.Final`), Byte Buddy (`net.bytebuddy:byte-buddy:1.18.8-jdk5`, `net.bytebuddy:byte-buddy-agent:1.18.8-jdk5`), JUnit 5.

---

## Status Sync - 2026-05-24

**Overall status:** complete for the planned restoration gap closure scope.

The checklist below was synchronized against the current branch (`codex/decompile-parity-checks`), commit history, source files, tests, and a fresh acceptance run. The historical "red test" steps cannot be reproduced from the final tree, but they are marked complete because the corresponding tests, implementation commits, and passing verification now exist.

Evidence:

- `mvn test`: PASS, 67 tests, 0 failures, 0 errors.
- `mvn -q -DskipTests package`: PASS.
- CLI synthetic smoke: PASS with `--verbose --trace-runtime --verify-build`; runtime tracing captured a reflection event, Maven verification returned `BUILD SUCCESS`, and all restoration reports were generated.
- Spring Boot executable JAR acceptance: PASS on a generated Spring Boot 2.7.18 sample. Runtime tracing completed with exit code 0, the restored project compiled successfully, `decompile-failures.md` reported no failures, and the restoration score was `100/100`.
- Additional trace-agent hardening: one-argument `Class.forName` now preserves application class loading through the thread context class loader and normalizes slash-form names only as a fallback. `TraceAgentManifestTest` now loads a custom application class so this regression is covered.
- Additional scoring hardening: runtime score is now based on statically detected trace expectations, and inner classes no longer lower source fidelity when the restored outer class source covers them.
- Relevant completion commits: `bb7adcb`, `cdfaa1a`, `96c9ba8`, `2cce9bc`, `2920040`, `f946ef4`, `2fa8ffa`, `586dbfe`.

Planned incomplete items:

- None in this plan.

Known boundaries that remain by design:

- Runtime trace reports are evidence from the exercised path, not a proof of complete runtime equivalence.
- The trace agent captures the configured reflection/resource/file/socket call families, but unexercised code paths and external services remain outside smoke-run evidence.
- File/socket trace evidence is only required when bytecode evidence indicates those APIs are relevant; unexercised branches and external-service behavior remain outside smoke-run proof.
- Decompiler arbitration improves source fidelity, but generated source still needs parity/risk reports for high-risk classes.

---

### Task 1: Decompiler arbitration and parity metadata

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/z0fsec/jar2mp/core/DecompilerEngine.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/CfrDecompilerEngine.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/FernflowerDecompilerEngine.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/DecompilerBridge.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/DecompileFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/DecompilerBridgeTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/DecompileParityReporterTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/DecompilerFailureReportTest.java`

- [x] **Step 1: Write the failing tests**

Add a test double that lets `DecompilerBridge` see two engines with different outcomes, then assert that the bridge chooses the better source and records the selected engine.

```java
@Test
void prefersTheNonStubSourceWhenCandidatesDiffer() {
    DecompileResult result = bridge.decompileDetailed(classBytes, "demo.Sample");

    assertTrue(result.isSuccess());
    assertEquals("fernflower", result.getSelectedEngine());
    assertFalse(result.getSource().contains("Failed to decompile"));
}
```

Add a parity-report test that asserts engine metadata and risk labels are present in the generated markdown.

```java
@Test
void parityReportIncludesSelectedEngineAndRiskLevel() throws Exception {
    new DecompileParityReporter().writeReport(jarFile, analysis, outputDir.toFile());

    String report = Files.readString(outputDir.resolve("decompile-parity-report.md"));
    assertTrue(report.contains("Selected engine"));
    assertTrue(report.contains("Risk level: HIGH"));
}
```

- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `mvn -q -Dtest=DecompilerBridgeTest,DecompileParityReporterTest,DecompilerFailureReportTest test`

Expected: fail because `DecompilerBridge` still has a single-engine path and the report has no engine arbitration metadata yet.

- [x] **Step 3: Implement the minimal arbitration layer**

Add `DecompilerEngine` with a small result object that carries `engineName`, `success`, `source`, `failureMessage`, and a simple quality score.

Implement `CfrDecompilerEngine` as the default engine and `FernflowerDecompilerEngine` as the alternate engine. Update `DecompilerBridge` so it:

1. runs both engines for a class
2. rejects empty or stub-only output
3. prefers the source with the higher quality score
4. falls back to the current failure-comment behavior when both engines fail

Update `DecompileFinding` to record the chosen engine and the reason a fallback happened. Update `DecompileParityReporter` so it prints engine choice, source coverage, and a clearer risk bucket.

- [x] **Step 4: Re-run the focused tests**

Run: `mvn -q -Dtest=DecompilerBridgeTest,DecompileParityReporterTest,DecompilerFailureReportTest test`

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/z0fsec/jar2mp/core/DecompilerEngine.java src/main/java/com/z0fsec/jar2mp/core/CfrDecompilerEngine.java src/main/java/com/z0fsec/jar2mp/core/FernflowerDecompilerEngine.java src/main/java/com/z0fsec/jar2mp/core/DecompilerBridge.java src/main/java/com/z0fsec/jar2mp/core/DecompileParityReporter.java src/main/java/com/z0fsec/jar2mp/model/DecompileFinding.java src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java src/test/java/com/z0fsec/jar2mp/core/DecompilerBridgeTest.java src/test/java/com/z0fsec/jar2mp/core/DecompileParityReporterTest.java src/test/java/com/z0fsec/jar2mp/core/DecompilerFailureReportTest.java
git commit -m "feat: add decompiler arbitration"
```

### Task 2: Trace agent artifact and runtime event model

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceAgent.java`
- Create: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceTransformer.java`
- Create: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceEventSink.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceEvent.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceResult.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceCollector.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceCollectorTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/TraceAgentManifestTest.java`

- [x] **Step 1: Write the failing tests**

Add a parser test for JSONL runtime events.

```java
@Test
void parsesJsonlTraceEvents() throws Exception {
    Path trace = tempDir.resolve("trace.jsonl");
    Files.write(trace, List.of(
        "{\"kind\":\"reflection\",\"owner\":\"demo.App\",\"target\":\"Class.forName\",\"value\":\"java.lang.String\"}",
        "{\"kind\":\"resource\",\"owner\":\"demo.App\",\"target\":\"getResourceAsStream\",\"value\":\"META-INF/services/demo.Service\"}"
    ));

    RuntimeTraceResult result = new RuntimeTraceCollector().read(trace);

    assertEquals(2, result.getEvents().size());
    assertTrue(result.hasReflectionCalls());
}
```

Add a manifest test for the agent jar.

```java
@Test
void traceAgentJarHasPremainClass() throws Exception {
    try (JarFile jar = new JarFile(agentJar.toFile())) {
        assertEquals(
            "com.z0fsec.jar2mp.traceagent.TraceAgent",
            jar.getManifest().getMainAttributes().getValue("Premain-Class")
        );
    }
}
```

- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `mvn -q -Dtest=RuntimeTraceCollectorTest,TraceAgentManifestTest test`

Expected: fail because there is no trace agent artifact or runtime event model yet.

- [x] **Step 3: Implement the trace event pipeline**

Add a JSONL event format with stable fields:

- `kind`
- `owner`
- `target`
- `value`
- `thread`
- `stack`

Implement the agent with Byte Buddy so it can intercept the small set of calls that matter most:

- reflection: `Class.forName`, `Class#getMethod`, `Class#getDeclaredMethod`, `Class#getField`, `Class#getDeclaredField`, `Method#invoke`
- resource loads: `ClassLoader#getResource*`, `Class#getResource*`
- file I/O: `FileInputStream`, `Files.read*`, `Files.newInputStream`
- socket I/O: `Socket`, `HttpURLConnection`

Write events to the file path provided by `-Djar2mp.traceFile=...`. Keep the sink append-only and line oriented so the collector can parse it without guessing.

- [x] **Step 4: Re-run the focused tests**

Run: `mvn -q -Dtest=RuntimeTraceCollectorTest,TraceAgentManifestTest test`

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/z0fsec/jar2mp/traceagent/TraceAgent.java src/main/java/com/z0fsec/jar2mp/traceagent/TraceTransformer.java src/main/java/com/z0fsec/jar2mp/traceagent/TraceEventSink.java src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceEvent.java src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceResult.java src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceCollector.java src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceWriter.java src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceCollectorTest.java src/test/java/com/z0fsec/jar2mp/core/TraceAgentManifestTest.java
git commit -m "feat: add runtime trace agent"
```

### Task 3: Runtime smoke runner and trace report

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`

- [x] **Step 1: Write the failing tests**

Add a command-builder test that proves the runner injects the agent and app args.

```java
@Test
void buildsTraceCommandFromManifestMainClassAndAppArgs() {
    List<String> command = runner.buildCommand(
        originalJar.toFile(),
        "demo.Main",
        List.of("--profile=test")
    );

    assertTrue(command.contains("-javaagent:" + agentJar));
    assertTrue(command.contains("--profile=test"));
    assertTrue(command.contains("demo.Main"));
}
```

Add a report-writer test that asserts the trace report includes reflection and resource sections.

```java
@Test
void writesRuntimeTraceReport() throws Exception {
    new RuntimeTraceReportWriter().write(outputDir.toFile(), traceResult);

    String report = Files.readString(outputDir.resolve("runtime-trace-report.md"));
    assertTrue(report.contains("# Runtime trace report"));
    assertTrue(report.contains("Reflection"));
    assertTrue(report.contains("Resource loads"));
}
```

- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `mvn -q -Dtest=RuntimeSmokeRunnerTest,RuntimeTraceReportWriterTest,CliRunnerTest test`

Expected: fail because the CLI has no trace flags and there is no smoke runner yet.

- [x] **Step 3: Implement the smoke runner**

Make `RuntimeSmokeRunner` launch the original jar with the agent jar and a trace file path. The runner should:

1. resolve the `Main-Class` or `Start-Class` from analysis
2. build a `java -javaagent:... -jar ...` command
3. pass optional app args from `ProjectConfig`
4. wait with a bounded timeout
5. collect the JSONL trace into `RuntimeTraceResult`

Wire `CliRunner` so runtime tracing runs after analysis and before build verification. Keep `ProjectVerifier` focused on Maven compile/package, and let the new runner own runtime evidence.

- [x] **Step 4: Re-run the focused tests**

Run: `mvn -q -Dtest=RuntimeSmokeRunnerTest,RuntimeTraceReportWriterTest,CliRunnerTest test`

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunner.java src/main/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriter.java src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java src/test/java/com/z0fsec/jar2mp/core/RuntimeSmokeRunnerTest.java src/test/java/com/z0fsec/jar2mp/core/RuntimeTraceReportWriterTest.java src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java
git commit -m "feat: add runtime trace runner"
```

### Task 4: Restoration scoring and gap summary

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/model/RestorationScore.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RestorationScorer.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/RestorationScoreWriter.java`
- Create: `src/main/java/com/z0fsec/jar2mp/core/GapSummaryWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RestorationScorerTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`

- [x] **Step 1: Write the failing tests**

Add a scoring test that proves the overall score is a weighted blend of static, resource, runtime, and verification signals.

```java
@Test
void combinesStaticRuntimeAndVerificationSignalsIntoAWeightedScore() {
    RestorationScore score = scorer.score(analysis, traceResult, verificationResult);

    assertEquals(73, score.getOverall());
    assertTrue(score.getBreakdown().containsKey("runtime"));
    assertTrue(score.getGaps().stream().anyMatch(g -> g.getCategory().equals("reflection")));
}
```

Add a project-builder test that asserts the new score and gap files are written beside the existing reports.

```java
@Test
void writesScoreAndGapSummaryReports() throws Exception {
    new ProjectBuilder(config).build(jar.toFile(), analysis, pomXml, outputDir.toFile(), null);

    assertTrue(Files.exists(outputDir.resolve("restoration-score.md")));
    assertTrue(Files.exists(outputDir.resolve("gap-summary.md")));
}
```

- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `mvn -q -Dtest=RestorationScorerTest,ProjectBuilderTest test`

Expected: fail because there is no scoring model or gap summary yet.

- [x] **Step 3: Implement the scoring model**

Use a fixed weighted formula so the output is stable and explainable:

- 40% decompiler/source fidelity
- 20% resource fidelity
- 20% runtime trace coverage
- 20% verification success

Record the per-bucket score plus the top missing items that pulled the project below 100. Write the result into `JarAnalysisResult` so CLI, GUI, and report writers all see the same score.

- [x] **Step 4: Re-run the focused tests**

Run: `mvn -q -Dtest=RestorationScorerTest,ProjectBuilderTest test`

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/model/RestorationScore.java src/main/java/com/z0fsec/jar2mp/core/RestorationScorer.java src/main/java/com/z0fsec/jar2mp/core/RestorationScoreWriter.java src/main/java/com/z0fsec/jar2mp/core/GapSummaryWriter.java src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java src/main/java/com/z0fsec/jar2mp/model/JarAnalysisResult.java src/test/java/com/z0fsec/jar2mp/core/RestorationScorerTest.java src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java
git commit -m "feat: add restoration scoring"
```

### Task 5: CLI/GUI wiring, docs, and end-to-end smoke

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/ui/MainPanel.java`
- Modify: `README.md`
- Modify: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`

- [x] **Step 1: Write the failing tests**

Add CLI assertions for the new help text and report paths.

```java
@Test
void helpMentionsTraceAndScoreReports() {
    String help = captureHelp();

    assertTrue(help.contains("--trace-runtime"));
    assertTrue(help.contains("runtime-trace-report.md"));
    assertTrue(help.contains("restoration-score.md"));
}
```

Add an end-to-end smoke test that verifies a tiny jar produces all report files when tracing and verification are enabled.

```java
@Test
void cliProducesAllRestorationReports() throws Exception {
    int exitCode = runner.run(new String[] {
        "--trace-runtime", "--verify-build", sampleJar.toString()
    });

    assertEquals(0, exitCode);
    assertTrue(Files.exists(projectDir.resolve("decompile-parity-report.md")));
    assertTrue(Files.exists(projectDir.resolve("runtime-trace-report.md")));
    assertTrue(Files.exists(projectDir.resolve("restoration-score.md")));
}
```

- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `mvn -q -Dtest=CliRunnerTest,ProjectBuilderTest test`

Expected: fail because the new CLI flags, UI report paths, and docs are not wired yet.

- [x] **Step 3: Wire the new flags and report output**

Update CLI parsing and help text for:

- `--trace-runtime`
- `--trace-args`
- `--trace-timeout`
- `--smoke-only`

Update the GUI log/report output so the extra files are surfaced after a build. Keep the GUI controls minimal; do not add a separate advanced panel unless a later test proves it is necessary.

Update the README with:

- the new end-to-end workflow
- the new report files
- the separation between structural restoration and runtime fidelity

- [x] **Step 4: Run the full suite**

Run:

```bash
mvn -q test
mvn -q clean package
```

Expected: PASS for both commands.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java src/main/java/com/z0fsec/jar2mp/ui/MainPanel.java README.md src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java
git commit -m "feat: wire restoration reports through cli and gui"
```

## Coverage check

- Static source fidelity: Task 1
- Runtime behavior capture: Tasks 2 and 3
- Quantified residual gap: Task 4
- User-facing wiring and docs: Task 5
