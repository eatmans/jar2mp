# GitHub Real-World Regression Set

`scripts/regression/run-github-realworld-regression.sh` downloads real Java projects from GitHub, builds their published artifact shape, runs jar2mp, and writes a compile/package-gate summary with runtime, source artifact-fidelity, raw artifact, and byte-exact package evidence. Outputs live under `target/realworld-samples/` and are not committed.

jar2mp uses multi-engine decompiler arbitration for these samples. CFR and JD-Core run in-process, JADX participates when the `jadx` command is available on `PATH` or `JADX_BIN`, and Fernflower remains as an additional fallback engine. JD-GUI itself is an interactive GUI; JD-Core is the automated engine from the same Java Decompiler family.

Run:

```bash
./scripts/regression/run-github-realworld-regression.sh
```

Optional environment:

```bash
MVN=/path/to/mvn JAVA8_HOME=/path/to/jdk8 JAVA_TRACE_TIMEOUT=20 REALWORLD_TRACE_ARGS='--server.port=0' ./scripts/regression/run-github-realworld-regression.sh
```

The script uses GitHub codeload ZIP archives instead of `git clone`. This keeps the regression less sensitive to local Git HTTPS stalls and avoids cloning full repository history.

## Pass Gate

| Sample | Repo | Ref | Artifact type | Build command | Threshold | Purpose |
| --- | --- | --- | --- | --- | ---: | --- |
| `gs-spring-boot` | `spring-guides/gs-spring-boot` | `2ffad4f418c3052b534184228a45d062f566096f` | Spring Boot executable JAR | `cd complete && ./mvnw -q -DskipTests package` | 80 | Small Boot executable with `BOOT-INF/classes` and nested libraries. |
| `spring-petclinic` | `spring-projects/spring-petclinic` | `a6efbed773f61a271c071461326940786998722e` | Spring Boot executable JAR | `./mvnw -q -DskipTests package` | 80 | Larger Boot application with controllers, templates, static assets, i18n, DB scripts, and SBOM metadata. |
| `jpetstore-6` | `mybatis/jpetstore-6` | `0632ee486774fb4c09fb267a9e264975862cd778` | WAR / MyBatis | `./mvnw -q -DskipTests -Dimpsort.skip=true package` | 80 | Traditional WAR with `WEB-INF/classes`, JSPs, MyBatis mapper XML, and servlet resources. |
| `gs-securing-web` | `spring-guides/gs-securing-web` | `6c986e19b4b329dd4a3d9d3d932a6e0e5bf74ad5` | thin Maven JAR / Spring Security | `cd complete && ./mvnw -q -DskipTests package` | 80 | Spring Security and Spring MVC sample; not a Boot executable because it has no repackage step. |
| `spring-boot-shiro` | `pbw123/springboot_learn` | `3790fd026dd333226cf6a3ec52531b2b8007d541` | Spring Boot executable JAR / Shiro | `cd spring-boot-shiro && ./mvnw -q -DskipTests package` | 80 | Shiro integration sample. It uses older Spring Boot/Lombok, so build and jar2mp verification run with Java 8. |
| `commons-fileupload` | `apache/commons-fileupload` | `f3e030f09ac8b01b684466c793dec86eafe1e4c9` | Servlet upload library JAR | `mvn -q -DskipTests package` | 80 | Servlet upload library; complements the WAR sample with a dependency-style servlet API artifact. |
| `gs-uploading-files` | `spring-guides/gs-uploading-files` | `02df6b5a928ed8d91b8aedb37e28f1d6ce9fd32a` | Spring Boot executable JAR / upload | `cd complete && mvn -q -DskipTests package` | 80 | Small Spring Boot upload app with templates and file storage service paths. |
| `spring-boot-thymeleaf-war` | `kolorobot/spring-boot-thymeleaf` | `00cb739087a7d933fbf3bca716fd06b4b362a996` | Spring Boot executable WAR / Thymeleaf | `./mvnw -q -DskipTests package` | 80 | Executable WAR with `WEB-INF/classes`, Boot loader, templates, static assets, and profile config. |

Each pass-gate sample is marked `PASS` only when:

- jar2mp exits successfully.
- `restoration-score.md` exists and the overall score meets the sample threshold.
- Source and resource buckets are both `100`.
- `verification-report.md` reports `BUILD SUCCESS` and `Failure type: NONE`.
- `decompile-failures.md` reports zero failed decompilations.
- The restored project can be packaged with the regression skip flags.
- The raw preserved artifact under `target/raw-artifact/` compares as exact.
- A separate `--byte-exact-package --verify-build` restoration reports `BUILD SUCCESS`, `Failure type: NONE`, and its final packaged JAR/WAR compares as exact.
- Supported runtime launches either exit cleanly or collect startup trace events before timeout.

Runtime and artifact evidence are reported as separate gate columns. Long-running web applications that collect trace events but do not exit before the timeout are marked `PASS_WITH_WARNINGS` with runtime gate `WARN_STARTED_TIMEOUT`; rebuilt source-package byte differences are also warning-level evidence, not proof of byte-identical source recompilation. Library JARs, thin JARs without a runnable manifest, and standard WARs can report `UNSUPPORTED` launch support with runtime gate `SKIPPED_UNSUPPORTED`.

The script now records three artifact-fidelity tracks:

- `artifact_*` columns compare the original artifact with the Maven package produced from the restored source project. jar2mp overlays original class bytes during `process-classes`, preserves original entry mtimes for copied class/resource bytes, keeps original `META-INF/maven/**` metadata, package manifests where Maven can safely consume them, and retained build-info/SBOM resources while disabling generated Maven descriptors or metadata generators that would overwrite those entries. This track reports `artifact_content_entries_match` separately from `artifact_archive_bytes_same`; `source_artifact_gate=PASS_CONTENT` means every non-directory entry's bytes match and only the ZIP container bytes still differ. Per-project `artifact-fidelity-summary.csv` files also append ZIP-container diagnostics such as `archive_entry_order_same`, `archive_metadata_diff_entries`, timestamp differences, compression-method differences, compressed-size differences, and extra-field differences so container-only drift can be triaged separately from content drift. When content matches but entry order differs, `--compare-artifact` also writes an `archive-order-restored/` candidate by reordering the rebuilt ZIP records without recompressing them; the real-world summary promotes that result as `artifact_archive_order_restored_exact` alongside the other `artifact_archive_*` columns.
- `raw_artifact_*` columns compare the original artifact with `target/raw-artifact/<original-name>`, a byte-for-byte preserved copy emitted by `--emit-raw-artifact`. These columns prove jar2mp can retain an exact raw artifact alongside the source restoration without claiming that the source rebuild is byte-identical.
- `byte_exact_*` columns run a separate `--byte-exact-package --verify-build` restoration and compare its final Maven `package` output against the original artifact. This is the strict package-level byte equality gate.

For strict byte-level package restoration, run jar2mp with `--byte-exact-package`. That mode implies `--emit-raw-artifact`, uses the original artifact base name as Maven `finalName`, adds common test and quality plugin skip properties, omits package-transforming shade/assembly/repackage plugins, and wires the generated Maven `package` phase to overwrite the final JAR/WAR with the preserved raw artifact. When combined with `--verify-build`, its default verification goal is `package` unless `--verify-goal` is set explicitly, and jar2mp writes `target/byte-exact-package-check/` fidelity reports for the final package output. The realworld script runs this mode separately from the normal source-rebuild package check so `byte_exact_*` proves strict package-level restoration while `artifact_*` continues to measure how close recompilation from decompiled sources is.

After jar2mp verification, the script still packages the restored project with skip flags for tests, checkstyle, formatting, license, enforcer, jacoco, git metadata, and javadocs. It compares that rebuilt artifact separately from the raw artifact. Source-package artifact exact match is non-gating; raw artifact exactness is evidence that a 1:1 preservation path exists.

## Known Non-Gate Findings

These projects were useful during real-world probing, but they should not fail the automated gate yet:

| Candidate | Repo/ref | Result | Why it is tracked separately |
| --- | --- | --- | --- |
| `shiro-core` | `apache/shiro@eee14b9fa14695fd7a3bd295e81436932bf41c55` | source/resource `100`, no decompile failures, verification fails | The restored module inherits Apache Shiro's multi-module parent build and `directory-maven-plugin` expects the original reactor directory for `org.apache.shiro:shiro-root`. |
| `shiro-quickstart` | `apache/shiro@eee14b9fa14695fd7a3bd295e81436932bf41c55` | source/resource `100`, no decompile failures, verification fails | The restored standalone sample references the non-published reactor parent `org.apache.shiro.samples:shiro-samples:2.0.6`. |
| `commons-lang3` | `apache/commons-lang@1aa5352287f581b628c48c6f61e38866d4a2f64a` | source/resource `100`, no decompile failures, verification fails | Large library decompilation still emits invalid Java around several complex language constructs; this is a good future decompiler-arbitration target. |
| `commons-cli` | `apache/commons-cli@91369572408eff424ff5cec2d46dd9667ceba1b3` | source/resource `100`, no decompile failures, verification fails | Decompilation emits incompatible generic wildcard types in `PatternOptionBuilder` and `TypeHandler`. |
| `commons-logging` | `apache/commons-logging@698cda4096ef648f3d4ee520d86b79b44aba86fe` | source/resource `100`, no decompile failures, verification fails | Decompilation emits ambiguous `AccessController.doPrivileged` method references and several raw/generic type mismatches. |
| `json-java` | `stleary/JSON-java@f9b5587c87aaf02e4a5ed1991d48d6c05993624a` | source/resource `100`, no decompile failures, verification fails | Decompilation emits unreachable control flow in `org.json.CDL`. |

## Outputs

The script writes:

- `target/realworld-samples/report/github-realworld-summary.md`
- `target/realworld-samples/report/github-realworld-summary.csv`
- `target/realworld-samples/report/*.build.log`
- `target/realworld-samples/report/*.cli.log`
- `target/realworld-samples/report/*.package.log`
- `target/realworld-samples/report/*.artifact-fidelity.log`
- `target/realworld-samples/report/*.byte-exact.cli.log`
- `target/realworld-samples/report/*.byte-exact-package.log`
- downloaded repositories under `target/realworld-samples/repos/`
- restored jar2mp projects under `target/realworld-samples/restored/`
- byte-exact restored jar2mp projects under `target/realworld-samples/restored/<sample>-byte-exact/`
- per-project raw artifact fidelity under `target/realworld-samples/restored/<sample>/<artifactId>/target/raw-artifact/`

Do not commit generated artifacts or reports. Commit the script and this documentation only.
