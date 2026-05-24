# GitHub Real-World Regression Set

`scripts/regression/run-github-realworld-regression.sh` downloads real Java projects from GitHub, builds their published artifact shape, runs jar2mp, and writes a verify-only restoration summary. Outputs live under `target/realworld-samples/` and are not committed.

Run:

```bash
./scripts/regression/run-github-realworld-regression.sh
```

Optional environment:

```bash
MVN=/path/to/mvn JAVA8_HOME=/path/to/jdk8 ./scripts/regression/run-github-realworld-regression.sh
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

Each pass-gate sample is marked `PASS` only when:

- jar2mp exits successfully.
- `restoration-score.md` exists and the overall score meets the sample threshold.
- Source and resource buckets are both `100`.
- `verification-report.md` reports `BUILD SUCCESS` and `Failure type: NONE`.
- `decompile-failures.md` reports zero failed decompilations.

Runtime score is not part of this gate. These real samples are web applications or libraries, so the script runs `--verify-build` without `--trace-runtime`; an overall score of `80/100` is expected when source, resource, and verification buckets are all complete.

## Known Non-Gate Findings

These projects were useful during real-world probing, but they should not fail the automated gate yet:

| Candidate | Repo/ref | Result | Why it is tracked separately |
| --- | --- | --- | --- |
| `shiro-core` | `apache/shiro@eee14b9fa14695fd7a3bd295e81436932bf41c55` | source/resource `100`, no decompile failures, verification fails | The restored module inherits Apache Shiro's multi-module parent build and `directory-maven-plugin` expects the original reactor directory for `org.apache.shiro:shiro-root`. |
| `shiro-quickstart` | `apache/shiro@eee14b9fa14695fd7a3bd295e81436932bf41c55` | source/resource `100`, no decompile failures, verification fails | The restored standalone sample references the non-published reactor parent `org.apache.shiro.samples:shiro-samples:2.0.6`. |
| `commons-lang3` | `apache/commons-lang@1aa5352287f581b628c48c6f61e38866d4a2f64a` | source/resource `100`, no decompile failures, verification fails | Large library decompilation still emits invalid Java around several complex language constructs; this is a good future decompiler-arbitration target. |

## Outputs

The script writes:

- `target/realworld-samples/report/github-realworld-summary.md`
- `target/realworld-samples/report/github-realworld-summary.csv`
- `target/realworld-samples/report/*.build.log`
- `target/realworld-samples/report/*.cli.log`
- downloaded repositories under `target/realworld-samples/repos/`
- restored jar2mp projects under `target/realworld-samples/restored/`

Do not commit generated artifacts or reports. Commit the script and this documentation only.
