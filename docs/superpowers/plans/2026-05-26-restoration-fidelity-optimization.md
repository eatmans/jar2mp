# Restoration Fidelity Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make restoration reports and runtime tracing more faithful by preserving nested libs, preventing trace hooks from changing behavior, and replacing note-string scoring with structured copy state.

**Architecture:** Keep the generated Maven project standard while preserving non-Maven evidence under `target/original-libs`. Keep trace hooks as thin wrappers around exact JDK calls. Move resource copy outcomes into `ResourceFinding` fields and let reports/scoring consume those fields.

**Tech Stack:** Java 8 source compatibility, JUnit 5 tests, Maven, ByteBuddy trace agent.

**Progress sync (2026-06-11):** The active branch contains observational trace hooks, best-effort trace sink writes, nested library archiving under `target/original-libs`, structured resource copy state, and scoring support for archived nested libraries. Current remaining fidelity gaps are tracked by the real-world and release-asset regression outputs rather than by this stale checklist.

---

### Task 1: Make Trace Hooks Observational

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceHooks.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/traceagent/TraceEventSink.java`
- Test: `src/test/java/com/z0fsec/jar2mp/traceagent/TraceHooksBehaviorTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/traceagent/TraceEventSinkBehaviorTest.java`

- [x] Write failing tests:
  - `forNameStringKeepsSlashNameFailure`
  - `forNameWithLoaderKeepsSlashNameFailure`
  - `forNameStringDoesNotUseContextClassLoaderFallback`
  - `recordDoesNotThrowWhenTracePathIsDirectory`
- [x] Run the focused tests and confirm they fail on current behavior.
- [x] Remove slash-to-dot fallback and TCCL fallback from `TraceHooks`.
- [x] Make `TraceEventSink.write` best-effort by swallowing write failures and disabling the sink for that write path.
- [x] Run focused trace tests.
- [x] Run `mvn -q test`.
- [x] Commit with `fix: keep trace hooks observational`.

### Task 2: Preserve Nested Libraries Outside Maven Sources

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ResourceFinding.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ResourceClassifier.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ResourceClassifierTest.java`

- [x] Write failing tests proving `BOOT-INF/lib/dependency.jar` and `WEB-INF/lib/embedded.jar` are archived under `target/original-libs/...`.
- [x] Write failing report assertions for `resource-inventory.md` and `RUNBOOK.md`.
- [x] Add structured copy status fields to `ResourceFinding`.
- [x] Copy nested libraries to `target/original-libs/<original path>` during project build.
- [x] Update report notes to say nested libraries are archived but not added to Maven classpath.
- [x] Run focused resource/project builder tests.
- [x] Run `mvn -q test`.
- [x] Commit with `feat: preserve nested library archives`.

### Task 3: Score Resource Fidelity From Structured Copy State

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/RestorationScorer.java`
- Modify: `src/test/java/com/z0fsec/jar2mp/core/RestorationScorerTest.java`

- [x] Write failing tests for copied, failed, and archived nested library resource findings.
- [x] Change scoring to use `copyStatus` and `actualTargetPath` before falling back to legacy target/note logic.
- [x] Add a lower-impact `nested_library` runtime/classpath gap when a nested library is archived.
- [x] Run focused scoring tests.
- [x] Run `mvn -q test`.
- [x] Commit with `fix: score structured resource copy state`.

### Task 4: Final Verification

**Files:**
- No code edits unless verification exposes a bug.

- [x] Run `git diff --check`.
- [x] Run `mvn -q test`.
- [x] Run `./scripts/regression/run-sample-regression.sh`.
- [x] Read `target/regression-samples/report/regression-summary.md`.
- [x] Commit any verification-driven fixes separately.
- [x] Push the branch if the work remains clean and the user wants remote sync.
