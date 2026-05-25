# Restoration Fidelity Optimization Design

## Goal

Improve jar2mp's restored-project fidelity without overstating what has been recovered. The first optimization pass focuses on trace observation safety, nested library preservation, and structured resource copy evidence.

## Current Boundary

jar2mp can already restore common JAR/WAR layouts, generate Maven projects, copy application resources, verify builds, and produce parity/runtime reports. Remaining fidelity risk comes from three places:

- Runtime trace hooks can affect the original program instead of only observing it.
- `BOOT-INF/lib` and `WEB-INF/lib` archives are reported but not preserved in the output.
- Resource copy success is encoded in free-form notes, which makes reports and scoring fragile.

## Design

### 1. Trace Hooks Stay Observational

`TraceHooks` must call the same Java API with the same arguments the original bytecode used. It must not normalize slash-form class names, switch to the thread context class loader, or otherwise make a failing call succeed. Trace write failures must be best-effort and must not propagate into the traced program.

The first pass will keep existing hook coverage but remove behavior-changing fallbacks:

- `Class.forName(String)` calls only `Class.forName(name)`.
- `Class.forName(String, boolean, ClassLoader)` calls only that exact overload.
- Event recording happens after a successful call where needed, and failed calls are recorded only when doing so cannot change the thrown exception.
- `TraceEventSink` disables or skips failed writes instead of throwing into application code.

### 2. Nested Libraries Are Preserved As Evidence

Nested dependency archives should not be placed into `src/main/resources`, because that would create an inaccurate Maven project. They should be copied to `target/original-libs/<original path>` so a user can inspect, install, or manually add them when Maven metadata is incomplete.

`resource-inventory.md`, `restoration-report.md`, and `RUNBOOK.md` should make clear that these archives are preserved but not automatically added to the Maven classpath.

### 3. Resource Copy Status Is Structured

`ResourceFinding` will gain explicit copy status fields:

- `copyStatus`: `PENDING`, `COPIED`, `ARCHIVED`, `SKIPPED`, `FAILED`
- `actualTargetPath`: relative path when copied or archived
- `copyFailureReason`: reason when skipped or failed

The existing `note` field remains for human-facing context, but scoring and reporting should rely on structured status first.

### 4. Scoring Becomes Less Misleading

Normal resources still require successful copy to score as restored. Nested libraries should no longer disappear from fidelity scoring. An archived nested library is not a normal resource loss, but it is still a runtime/classpath gap, so it should appear as a gap with lower impact than an unpreserved library.

## Out Of Scope For This Pass

- Generating `systemPath` dependencies automatically.
- Installing preserved nested jars into the local Maven repository.
- Rewriting decompiler arbitration around bytecode facts.
- Broad trace transformer scoping controls.

Those are useful follow-up tasks, but the first pass should close the highest-risk fidelity misstatements with small, reversible changes.

## Acceptance Criteria

- Trace hook unit tests prove slash-form class names still fail and TCCL fallback is not used.
- Trace write failure tests prove hooks do not throw because the trace file is unwritable.
- Spring Boot and WAR tests prove nested jars are copied to `target/original-libs/...` and not copied into source resources.
- Resource inventory/report/runbook include the preserved nested library path and classpath warning.
- Restoration scoring uses structured copy status instead of matching note strings.
- `mvn -q test` passes.
- The local sample regression script passes or any failure is reported with exact evidence.
