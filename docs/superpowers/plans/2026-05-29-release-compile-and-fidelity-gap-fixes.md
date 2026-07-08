# Release Compile And Fidelity Gap Fixes Implementation Plan

**Goal:** Improve two separate gates:

1. Prebuilt binary JARs from GitHub Releases should restore to Maven projects that compile.
2. Strict source-rebuilt artifact byte equivalence must stay separate from raw artifact preservation.

**Architecture:** Prefer compilable restored projects over pretending every decompiled source file is recoverable. When Java source is not trustworthy or fails the compile gate, retain the original `.class` bytes as a Maven resource fallback and report the fallback count explicitly. Keep `artifact_*` source rebuild metrics separate from `raw_artifact_*` preservation metrics.

---

### Task 1: Fix CFR Post-Processing Syntax Regressions

**Files:**
- `src/main/java/com/z0fsec/jar2mp/core/SourcePostProcessor.java`
- `src/test/java/com/z0fsec/jar2mp/core/SourcePostProcessorTest.java`

- [x] Add failing tests for `AccessController.doPrivileged` inside control blocks and generic helper methods.
- [x] Fix enclosing method return-type detection so it skips control blocks and strips method type parameters.
- [x] Add fixes/tests for duplicate anonymous-class placeholders.
- [x] Add fixes/tests for wildcard lambda parameters such as `(? super K key, ? super V value) ->`.
- [x] Verify `mvn -q -Dtest=SourcePostProcessorTest test`.

### Task 2: Add Raw-Class Compile Fallbacks

**Files:**
- `src/main/java/com/z0fsec/jar2mp/core/ProjectBuilder.java`
- `src/main/java/com/z0fsec/jar2mp/core/ProjectVerifier.java`
- `src/main/java/com/z0fsec/jar2mp/model/VerificationResult.java`
- `src/test/java/com/z0fsec/jar2mp/core/ProjectBuilderTest.java`
- `src/test/java/com/z0fsec/jar2mp/core/ProjectVerifierTest.java`

- [x] Cache every original class under `target/raw-classes`.
- [x] Copy retained raw classes to `src/main/resources` so Maven can compile against them.
- [x] Fall back outer classes whose decompiled source imports inner classes not declared in that source.
- [x] Fall back JVM classes that cannot be emitted as legal Java top-level source names.
- [x] Fall back Kotlin-metadata classes to raw bytes instead of attempting Java source recovery.
- [x] Fall back classes under shaded dependency namespaces to raw bytes for compile stability.
- [x] Retry Maven compile after deleting errored generated sources and copying their raw class families.
- [x] Report `Compile fallback classes` in `verification-report.md`.

### Task 3: Fix Generated POM Compile Classpath

**Files:**
- `src/main/java/com/z0fsec/jar2mp/core/PomGenerator.java`
- `src/main/resources/db/package-mappings.properties`
- `src/test/java/com/z0fsec/jar2mp/core/PomGeneratorTest.java`
- `src/test/java/com/z0fsec/jar2mp/db/PackagePrefixDatabaseTest.java`

- [x] Disable annotation processing with `<proc>none</proc>` to avoid self-loading processors from restored service files.
- [x] Map `com.google.auto.value` to `auto-value-annotations`, not the processor artifact.
- [x] Map `javax.annotation` / `javax.annotation.concurrent` to `com.google.code.findbugs:jsr305:3.0.2`.

### Task 4: Verify Matrices

- [x] `mvn -q test`
- [x] `./scripts/regression/run-sample-regression.sh`
- [x] Cached GitHub release binary JAR matrix under `target/adhoc-github-release-assets/assets`
- [x] `./scripts/regression/run-github-realworld-regression.sh`
- [x] `git diff --check`

Cached GitHub release binary JAR results:

| Sample | Compile gate | Raw artifact |
| --- | --- | --- |
| `jmx_prometheus_standalone-1.5.0.jar` | PASS | exact=true |
| `opentelemetry-javaagent.jar` | PASS | exact=true |
| `picocli-4.7.7.jar` | PASS | exact=true |
| `picocli-codegen-4.7.7.jar` | PASS | exact=true |
| `undertow-core-2.4.1.Final.jar` | PASS | exact=true |
| `undertow-examples-2.4.1.Final.jar` | PASS | exact=true |

Regression results:

- Local sample matrix: `8/8 PASS` in `target/regression-samples/report/regression-summary.csv`.
- GitHub real-world matrix: `8/8 PASS_WITH_WARNINGS` in `target/realworld-samples/report/github-realworld-summary.csv`.
- GitHub real-world raw artifact preservation: `raw_artifact_exact=true` for all 8 rows.
- GitHub real-world source-rebuilt artifacts are still not byte-exact (`artifact_exact=false`), so raw preservation must not be presented as source rebuild equivalence.

### Remaining Strict Fidelity Gaps

- Source-rebuilt archive byte equivalence still differs due class bytecode, metadata, manifest/resource, nested library, and archive ordering/timestamp/compression differences.
- The new binary compile gate can pass by using raw-class fallbacks. This is correct for "binary JAR restores to a compilable Maven project", but it is not proof that every class was converted to equivalent Java source.
