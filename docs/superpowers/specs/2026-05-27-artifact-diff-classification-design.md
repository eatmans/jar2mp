# Artifact Diff Classification Design

## Goal

Move jar2mp's source-rebuild fidelity work from coarse diff counts to actionable reasons. The project already restores source/resources, verifies Maven builds, preserves an exact raw artifact copy, and passes the current local and GitHub gates. The next useful optimization is to explain why the Maven package rebuilt from restored source is not byte-identical to the original artifact.

## Current Evidence

The GitHub real-world gate currently passes all compile checks, and `raw_artifact_*` columns are exact for every sample. The remaining non-exact signal is the source rebuild artifact:

- Spring Boot executable samples rebuild and trace successfully, but `artifact_exact=false`.
- Standard WAR, thin JAR, and library samples can compile, but direct runtime launch is unsupported by design.
- The summary exposes counts for SHA-different entries, missing entries, extra entries, and class-byte differences, but it does not classify why each difference exists.

This makes the next fix selection too manual. We need the report to say whether differences are class bytecode, manifest, Maven metadata, nested libraries, service metadata, Boot indexes, or generic resource/package layout.

## Design

### 1. Structured Difference Buckets

`ArtifactFidelityComparator` will classify every missing, extra, or SHA-different entry into a stable bucket:

- `MANIFEST`
- `CLASS_BYTECODE`
- `NESTED_LIBRARY`
- `MAVEN_METADATA`
- `SERVICE_METADATA`
- `BOOT_INDEX`
- `SIGNATURE_METADATA`
- `RESOURCE_ENTRY`

Each bucket records counts for missing, extra, and different entries plus a small sample list. This is an evidence layer only; it does not change compile pass/fail.

### 2. Report Writer Shows Fix Order

`artifact-fidelity-report.md` will include a "Difference buckets" table. The table will sort buckets by total count descending, then by bucket name for stable output. Each row will show missing, extra, different, total, and example paths.

`artifact-fidelity-summary.csv` will keep the existing columns and append per-bucket totals. Existing script parsers can keep reading their current positions, while new code can inspect the appended columns.

### 3. First Optimization Uses The Buckets

After bucket reporting exists, low-risk fixes should target non-class differences first:

- manifest drift
- Maven metadata drift
- Boot index drift
- service metadata duplication
- missing or extra resource entries

Class-byte differences are reported but not fixed in this pass. Those need a separate class-diff explainer for compiler/debug/synthetic/lambda differences.

## Acceptance Criteria

- Unit tests prove manifest, class, nested library, Maven metadata, service metadata, Boot index, signature metadata, and generic resource entries are assigned to the expected buckets.
- Unit tests prove missing, extra, and different counts are tracked independently per bucket.
- `artifact-fidelity-report.md` includes a bucket table with examples.
- `artifact-fidelity-summary.csv` keeps existing columns and appends bucket columns.
- `mvn -q test` passes.
- `./scripts/regression/run-github-realworld-regression.sh` still passes compile gates and produces the new bucket table in artifact fidelity logs.

## Out Of Scope

- Byte-identical recompilation from decompiled Java.
- HTTP endpoint crawling for runtime verification.
- Changing compile pass/fail gates based on artifact fidelity.
- Changing raw artifact preservation; it already proves exact preservation.
