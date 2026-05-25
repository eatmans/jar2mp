# Restoration Fidelity Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make restoration reports and runtime tracing more faithful by preserving nested libs, preventing trace hooks from changing behavior, and replacing note-string scoring with structured copy state.

**Architecture:** Keep the generated Maven project standard while preserving non-Maven evidence under `target/original-libs`. Keep trace hooks as thin wrappers around exact JDK calls. Move resource copy outcomes into `ResourceFinding` fields and let reports/scoring consume those fields.

**Tech Stack:** Java 8 source compatibility, JUnit 5 tests, Maven, ByteBuddy trace agent.

---

### Task 1: Make Trace Hooks Observational

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceHooks.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceEventSink.java`
- Test: `src/test/java/com/z0fsec/jar2mp/traceagent/TraceHooksBehaviorTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/traceagent/TraceEventSinkBehaviorTest.java`

- [ ] Write failing tests:
  - `forNameStringKeepsSlashNameFailure`
  - `forNameWithLoaderKeepsSlashNameFailure`
  - `forNameStringDoesNotUseContextClassLoaderFallback`
  - `recordDoesNotThrowWhenTracePathIsDirectory`
- [ ] Run the focused tests and confirm they fail on current behavior.
- [ ] Remove slash-to-dot fallback and TCCL fallback from `TraceHooks`.
- [ ] Make `TraceEventSink.write` best-effort by swallowing write failures and disabling the sink for that write path.
- [ ] Run focused trace tests.
- [ ] Run `mvn -q test`.
- [ ] Commit with `fix: keep trace hooks observational`.

### Task 2: Preserve Nested Libraries Outside Maven Sources

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ResourceFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ResourceClassifier.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ResourceClassifierTest.java`

- [ ] Write failing tests proving `BOOT-INF/lib/dependency.jar` and `WEB-INF/lib/embedded.jar` are archived under `target/original-libs/...`.
- [ ] Write failing report assertions for `resource-inventory.md` and `RUNBOOK.md`.
- [ ] Add structured copy status fields to `ResourceFinding`.
- [ ] Copy nested libraries to `target/original-libs/<original path>` during project build.
- [ ] Update report notes to say nested libraries are archived but not added to Maven classpath.
- [ ] Run focused resource/project builder tests.
- [ ] Run `mvn -q test`.
- [ ] Commit with `feat: preserve nested library archives`.

### Task 3: Score Resource Fidelity From Structured Copy State

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationScorer.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/core/RestorationScorerTest.java`

- [ ] Write failing tests for copied, failed, and archived nested library resource findings.
- [ ] Change scoring to use `copyStatus` and `actualTargetPath` before falling back to legacy target/note logic.
- [ ] Add a lower-impact `nested_library` runtime/classpath gap when a nested library is archived.
- [ ] Run focused scoring tests.
- [ ] Run `mvn -q test`.
- [ ] Commit with `fix: score structured resource copy state`.

### Task 4: Final Verification

**Files:**
- No code edits unless verification exposes a bug.

- [ ] Run `git diff --check`.
- [ ] Run `mvn -q test`.
- [ ] Run `./scripts/regression/run-sample-regression.sh`.
- [ ] Read `target/regression-samples/report/regression-summary.md`.
- [ ] Commit any verification-driven fixes separately.
- [ ] Push the branch if the work remains clean and the user wants remote sync.
