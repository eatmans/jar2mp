# Artifact Diff Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add actionable artifact difference buckets so source-rebuild fidelity work can target the highest-value non-class gaps first.

**Architecture:** Extend `ArtifactFidelityResult` with bucket counts and examples. Teach `ArtifactFidelityComparator` to classify missing, extra, and SHA-different entries while preserving current aggregate metrics. Update the report writer to add a Markdown bucket table and append CSV bucket columns without breaking existing columns.

**Tech Stack:** Java 8, Maven, JUnit 5, JAR/WAR archive comparison.

---

### Task 1: Model Difference Buckets

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/model/ArtifactFidelityResult.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparatorTest.java`

- [ ] Add an `ArtifactFidelityResult.DifferenceBucket` enum with:
  - `MANIFEST`
  - `CLASS_BYTECODE`
  - `NESTED_LIBRARY`
  - `MAVEN_METADATA`
  - `SERVICE_METADATA`
  - `BOOT_INDEX`
  - `SIGNATURE_METADATA`
  - `RESOURCE_ENTRY`
- [ ] Add an `ArtifactFidelityResult.DifferenceBucketSummary` class with:
  - `DifferenceBucket bucket`
  - `int missing`
  - `int extra`
  - `int different`
  - `List<String> samples`
  - `int getTotal()`
- [ ] Add `recordMissing(DifferenceBucket bucket, String path)`, `recordExtra(...)`, and `recordDifferent(...)` methods. Each method increments the matching count and stores up to 5 sample paths.
- [ ] Add `getBucketSummaries()` returning bucket summaries sorted by enum order.
- [ ] Add tests that call the record methods directly and assert independent missing/extra/different counts.
- [ ] Run `mvn -q -Dtest=ArtifactFidelityComparatorTest test`.
- [ ] Commit with `feat: model artifact difference buckets`.

### Task 2: Classify Archive Entry Differences

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparator.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparatorTest.java`

- [ ] Add a private `classifyEntry(String name)` method:
  - `META-INF/MANIFEST.MF` -> `MANIFEST`
  - `*.class` -> `CLASS_BYTECODE`
  - `BOOT-INF/lib/*.jar`, `WEB-INF/lib/*.jar`, `lib/*.jar`, or `*/lib/*.jar` -> `NESTED_LIBRARY`
  - `META-INF/maven/**` -> `MAVEN_METADATA`
  - `META-INF/services/**` or `BOOT-INF/classes/META-INF/services/**` -> `SERVICE_METADATA`
  - `BOOT-INF/classpath.idx`, `BOOT-INF/layers.idx`, `WEB-INF/classpath.idx`, `WEB-INF/layers.idx` -> `BOOT_INDEX`
  - `*.SF`, `*.RSA`, `*.DSA`, `*.EC` under `META-INF/` -> `SIGNATURE_METADATA`
  - everything else -> `RESOURCE_ENTRY`
- [ ] When an original entry is missing in rebuilt, call `recordMissing(classifyEntry(name), name)`.
- [ ] When a rebuilt entry is extra, call `recordExtra(classifyEntry(name), name)`.
- [ ] When common entry SHA differs, call `recordDifferent(classifyEntry(name), name)`.
- [ ] Add tests with a synthetic original/rebuilt pair that produces one missing, one extra, and one different entry for multiple buckets.
- [ ] Run `mvn -q -Dtest=ArtifactFidelityComparatorTest test`.
- [ ] Commit with `feat: classify artifact fidelity differences`.

### Task 3: Report Bucket Evidence

**Files:**
- Modify: `src/main/java/com/z0fsec/jar2mp/core/ArtifactFidelityReportWriter.java`
- Test: `src/test/java/com/z0fsec/jar2mp/core/ArtifactFidelityComparatorTest.java`

- [ ] Add a "Difference buckets" table to Markdown with columns:
  - `Bucket`
  - `Missing`
  - `Extra`
  - `Different`
  - `Total`
  - `Examples`
- [ ] Sort rows by total descending, then bucket name ascending.
- [ ] Append CSV columns after `manifest_same`:
  - `bucket_manifest`
  - `bucket_class_bytecode`
  - `bucket_nested_library`
  - `bucket_maven_metadata`
  - `bucket_service_metadata`
  - `bucket_boot_index`
  - `bucket_signature_metadata`
  - `bucket_resource_entry`
- [ ] Add tests that write the report and assert the table and appended CSV columns exist.
- [ ] Run `mvn -q -Dtest=ArtifactFidelityComparatorTest test`.
- [ ] Run `mvn -q test`.
- [ ] Commit with `chore: report artifact difference buckets`.

### Task 4: Regression Evidence

**Files:**
- No source edits unless verification exposes a bug.

- [ ] Run `git diff --check`.
- [ ] Run `mvn -q test`.
- [ ] Run `./scripts/regression/run-github-realworld-regression.sh`.
- [ ] Inspect `target/realworld-samples/report/*.artifact-fidelity.log` for the "Difference buckets" table.
- [ ] Inspect `target/realworld-samples/report/github-realworld-summary.md` and confirm compile gate remains PASS.
- [ ] Commit verification-driven fixes separately if needed.
