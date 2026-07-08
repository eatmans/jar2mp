# Real-World Fidelity Gate Design

## Goal

Turn the ad-hoc real-world runtime and artifact fidelity probes into first-class jar2mp verification outputs, then use those outputs to drive fidelity fixes without overstating restoration quality.

## Current Evidence

The current GitHub real-world compile gate passes `8/8`, but two stricter probes show remaining gaps:

- Runtime trace succeeds only for executable Spring Boot JAR/WAR samples. Five samples collect events and score `100/100`, but the applications are stopped by timeout because they are long-running web servers.
- Three samples are not valid direct `java -jar` smoke targets: `commons-fileupload` is a library JAR, `gs-securing-web` has no runnable manifest, and `jpetstore-6` is a standard WAR that needs a servlet container.
- Rebuilt artifacts are not byte-identical to originals. Across the 8 samples there are 124 SHA-different common entries, 5 missing entries, 10 extra entries, and 109 class byte differences.
- Some differences are expected for source recompilation, but entry layout, manifest, nested library version drift, and copied service metadata are actionable.

## Design

### 1. Artifact Fidelity Is A Separate Verification Layer

Add an artifact comparator that compares an original archive to a rebuilt archive after Maven packaging. It should report:

- total entries in each archive
- class, resource, and nested library entry counts
- common entries with identical SHA-256
- common entries with different SHA-256
- missing and extra entries
- class byte identical/different counts
- nested library identical/different/missing/extra counts
- manifest presence and manifest byte equality
- first few missing/extra/different examples for triage

The comparator must be reusable from tests and CLI/regression scripts. It should not replace compile verification; compile answers "can the generated project build", while artifact fidelity answers "does the rebuilt archive match the original".

### 2. Runtime Launch Planning Is Explicit

Add a runtime launch planner that classifies each artifact before smoke execution:

- `EXECUTABLE_JAR`: manifest or Spring Boot launcher can run with `java -jar`
- `EXECUTABLE_WAR`: executable Boot WAR can run with `java -jar`
- `THIN_JAR`: has application classes but no runnable manifest or complete classpath
- `STANDARD_WAR`: WAR without executable launcher; needs servlet container
- `LIBRARY`: no application entrypoint
- `UNKNOWN`: insufficient evidence

The planner output must be written into runtime reports. Unsupported direct-smoke targets should produce a clear `UNSUPPORTED_LAUNCH` result instead of being mixed with runtime failures.

### 3. Web Runtime Probes Should Distinguish Startup From Natural Exit

For long-running Boot web applications, timeout after events have been collected is not the same as runtime failure. The runtime report should distinguish:

- process naturally exited with code `0`
- process exited non-zero
- startup evidence was collected, then the process was stopped after timeout
- artifact is unsupported for direct smoke

In this pass, a full HTTP crawler is out of scope. The minimum accepted improvement is to pass `--server.port=0` when configured, record collected events, and classify timeout-with-events as `TRACE_COLLECTED_TIMEOUT` rather than a generic failure.

### 4. Source Package Fidelity Fixes Stay Targeted

This pass should fix only the entry and dependency drift that can be addressed without changing the decompilation architecture:

- Do not copy root `META-INF/services/*` into `src/main/resources` for Spring Boot executable archives when that creates an extra `BOOT-INF/classes/META-INF/services/*` entry.
- Preserve embedded POM dependency exclusions so restored package output does not reintroduce excluded transitive libraries.
- Prefer original nested library filenames as fidelity evidence and report version drift when Maven resolves a different nested library version.

Byte-identical class output from decompiled source is not a realistic acceptance criterion for this pass. That requires a separate raw-bytecode preservation package mode.

### 5. Raw Artifact Preservation Is A Follow-Up Mode

The route to byte-identical artifacts is a separate mode that reassembles from original class/resource/lib bytes while still generating source for review. This design prepares the evidence and gates for that mode, but does not implement it in this pass.

## Acceptance Criteria

- Unit tests cover artifact comparison for identical, missing, extra, manifest-different, class-different, and nested-lib-drift cases.
- Unit tests cover runtime launch classification for executable JAR, executable WAR, thin JAR, standard WAR, and library JAR.
- Runtime reports include launch classification and classify timeout-with-events separately from hard failures.
- GitHub real-world regression outputs include artifact fidelity summary files under `target/realworld-samples/report/`.
- The real-world regression script keeps compile PASS/FAIL separate from runtime probe and artifact fidelity status.
- `mvn -q test` passes.
- The real-world regression script runs and reports any remaining artifact differences explicitly.

## Out Of Scope

- Full servlet container orchestration for standard WAR runtime probing.
- HTTP endpoint discovery and request crawling.
- Byte-identical recompilation from decompiled Java.
- Raw-bytecode fidelity packaging mode.
- Automatically installing archived nested libraries into the local Maven repository.
