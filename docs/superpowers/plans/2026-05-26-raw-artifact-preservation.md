# Raw Artifact Preservation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a raw artifact preservation path that can emit a byte-identical copy of the original JAR/WAR beside the restored Maven project and report that fidelity separately from source-rebuilt artifacts.

**Architecture:** Keep source restoration and Maven verification unchanged. Add a focused `RawArtifactPackager` that writes the preserved artifact under `target/raw-artifact/`, then reuse `ArtifactFidelityComparator` and `ArtifactFidelityReportWriter` to prove exactness. Real-world regression keeps existing source-package artifact fidelity columns and adds raw-artifact columns so the two restoration modes are not conflated.

**Tech Stack:** Java 8-compatible core code, existing CLI option parser, existing artifact fidelity comparator/report writer, Bash regression scripts.

**Progress sync (2026-06-11):** Implementation and current verification evidence are present in the active branch: `RawArtifactPackager`, CLI `--emit-raw-artifact`, raw artifact report columns, and the GitHub real-world raw artifact evidence are wired. Current reports show raw artifact preservation exactness separately from source-package artifact fidelity.

---

### Task 1: Raw Artifact Packager

**Files:**
- Create: `src/main/java/com/z0fsec/jar2mp/core/RawArtifactPackager.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/RawArtifactPackagerTest.java`

- [x] Write a failing test that creates a JAR, calls `RawArtifactPackager.preserve(original, outputDir)`, and asserts `ArtifactFidelityComparator.compare(original, preserved).isExactMatch()`.
- [x] Run `mvn -q -Dtest=RawArtifactPackagerTest test` and confirm it fails because the class is missing.
- [x] Implement the packager as a byte-for-byte file copy into `target/raw-artifact/<original-name>`.
- [x] Re-run the focused test and then `mvn -q test`.
- [x] Commit with `feat: add raw artifact packager`.

### Task 2: CLI Wiring

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ProjectConfig.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliOptions.java`
- Modify: `src/main/java/com/z0fsec/jar2mp/cli/CliRunner.java`
- Test: `src/test/java/com/z0fsec/jar2mp/cli/CliRunnerTest.java`

- [x] Add a failing CLI test for `--emit-raw-artifact` that verifies `target/raw-artifact/<original-name>` exists and its summary CSV starts with `true,`.
- [x] Run `mvn -q -Dtest=CliRunnerTest#emitRawArtifactWritesExactPreservedCopy test` and confirm it fails.
- [x] Add `ProjectConfig.emitRawArtifact`, parse `--emit-raw-artifact`, and call the packager after project generation.
- [x] Write raw artifact fidelity reports into `target/raw-artifact/` so they do not overwrite source-package fidelity reports.
- [x] Re-run focused tests and `mvn -q test`.
- [x] Commit with `feat: wire raw artifact preservation`.

### Task 3: Real-World Evidence Columns

**Files:**
- Modify: `scripts/regression/run-github-realworld-regression.sh`
- Modify: `docs/github-realworld-regression.md`

- [x] Add `--emit-raw-artifact` to real-world jar2mp invocation.
- [x] Add CSV/Markdown columns for `raw_artifact_exact`, `raw_artifact_diff_sha`, `raw_artifact_missing`, `raw_artifact_extra`, and `raw_artifact_diff_classes`.
- [x] Keep existing source-package artifact columns unchanged.
- [x] Run `bash -n scripts/regression/run-github-realworld-regression.sh`.
- [x] Run `./scripts/regression/run-github-realworld-regression.sh` and confirm `8/8 PASS`, `raw_artifact_exact=true` for all rows, and source-package artifact exact remains separately reported.
- [x] Commit with `chore: report raw artifact fidelity evidence`.

### Task 4: Final Verification

- [x] Run `git diff --check`.
- [x] Run `mvn -q test`.
- [x] Run `./scripts/regression/run-sample-regression.sh`.
- [x] Run `./scripts/regression/run-github-realworld-regression.sh`.
- [x] Summarize compile gate, runtime status, source-package artifact fidelity, and raw-artifact fidelity separately.
