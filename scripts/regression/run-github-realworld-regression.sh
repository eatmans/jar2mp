#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${ROOT_DIR}/target/realworld-samples"
REPOS_DIR="${WORK_DIR}/repos"
RESTORE_DIR="${WORK_DIR}/restored"
REPORT_DIR="${WORK_DIR}/report"
ARCHIVE_DIR="${WORK_DIR}/archives"
JAR2MP_JAR="${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar"

MVN="${MVN:-mvn}"
JAVA8_HOME="${JAVA8_HOME:-${HOME}/.sdkman/candidates/java/8.0.492.fx-librca}"
JAVA_TRACE_TIMEOUT="${JAVA_TRACE_TIMEOUT:-20}"
REALWORLD_TRACE_ARGS="${REALWORLD_TRACE_ARGS:---server.port=0}"
RESTORED_PACKAGE_FLAGS=(
  -q
  -DskipTests
  -Dmaven.test.skip=true
  -Dcheckstyle.skip=true
  -Dspring-javaformat.skip=true
  -Dimpsort.skip=true
  -Dformatter.skip=true
  -Dspotless.check.skip=true
  -Dspotless.apply.skip=true
  -Dlicense.skip=true
  -Drat.skip=true
  -Denforcer.skip=true
  -Djacoco.skip=true
  -Dgit.commit.id.skip=true
  -Dmaven.javadoc.skip=true
  package
)

sample_names=()
sample_repos=()
sample_urls=()
sample_refs=()
sample_repo_dirs=()
sample_subdirs=()
sample_build_commands=()
sample_artifacts=()
sample_types=()
sample_thresholds=()
sample_java_homes=()
sample_notes=()

log() {
  printf '[realworld] %s\n' "$*"
}

csv_field() {
  local value="$1"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
}

write_file() {
  local path="$1"
  mkdir -p "$(dirname "${path}")"
  cat > "${path}"
}

register_sample() {
  local name="$1"
  local repo="$2"
  local url="$3"
  local ref="$4"
  local repo_dir="$5"
  local subdir="$6"
  local build_command="$7"
  local artifact="$8"
  local artifact_type="$9"
  local threshold="${10}"
  local java_home="${11}"
  local note="${12}"

  sample_names+=("${name}")
  sample_repos+=("${repo}")
  sample_urls+=("${url}")
  sample_refs+=("${ref}")
  sample_repo_dirs+=("${repo_dir}")
  sample_subdirs+=("${subdir}")
  sample_build_commands+=("${build_command}")
  sample_artifacts+=("${artifact}")
  sample_types+=("${artifact_type}")
  sample_thresholds+=("${threshold}")
  sample_java_homes+=("${java_home}")
  sample_notes+=("${note}")
}

download_archive() {
  local repo="$1"
  local ref="$2"
  local dest="$3"
  local archive="${ARCHIVE_DIR}/${repo//\//-}-${ref}.zip"
  local extract_dir="${ARCHIVE_DIR}/extract-${repo//\//-}-${ref}"

  rm -rf "${dest}" "${extract_dir}"
  mkdir -p "${ARCHIVE_DIR}" "${extract_dir}" "$(dirname "${dest}")"

  if [[ ! -f "${archive}" ]]; then
    log "Downloading ${repo}@${ref}"
    curl -fsSL --retry 3 --retry-delay 2 \
      -o "${archive}" \
      "https://codeload.github.com/${repo}/zip/${ref}"
  else
    log "Using cached archive ${repo}@${ref}"
  fi

  unzip -q "${archive}" -d "${extract_dir}"
  local extracted
  extracted="$(find "${extract_dir}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1)"
  if [[ -z "${extracted}" ]]; then
    log "Archive ${archive} did not contain a top-level directory"
    exit 1
  fi
  mv "${extracted}" "${dest}"
  rm -rf "${extract_dir}"
}

prepare_samples() {
  register_sample \
    "gs-spring-boot" \
    "spring-guides/gs-spring-boot" \
    "https://github.com/spring-guides/gs-spring-boot" \
    "2ffad4f418c3052b534184228a45d062f566096f" \
    "gs-spring-boot" \
    "complete" \
    "./mvnw -q -DskipTests package" \
    "complete/target/spring-boot-complete-0.0.1-SNAPSHOT.jar" \
    "Spring Boot executable JAR" \
    "80" \
    "" \
    "Small Spring Boot app; exercises BOOT-INF/classes and nested libraries."

  register_sample \
    "spring-petclinic" \
    "spring-projects/spring-petclinic" \
    "https://github.com/spring-projects/spring-petclinic" \
    "a6efbed773f61a271c071461326940786998722e" \
    "spring-petclinic" \
    "." \
    "./mvnw -q -DskipTests package" \
    "target/spring-petclinic-4.0.0-SNAPSHOT.jar" \
    "Spring Boot executable JAR" \
    "80" \
    "" \
    "Larger Spring Boot app with controllers, templates, static assets, i18n, DB scripts, and SBOM metadata."

  register_sample \
    "jpetstore-6" \
    "mybatis/jpetstore-6" \
    "https://github.com/mybatis/jpetstore-6" \
    "0632ee486774fb4c09fb267a9e264975862cd778" \
    "jpetstore-6" \
    "." \
    "./mvnw -q -DskipTests -Dimpsort.skip=true package" \
    "target/jpetstore.war" \
    "WAR / MyBatis" \
    "80" \
    "" \
    "Traditional WAR; exercises WEB-INF/classes, JSPs, MyBatis mapper XML, and servlet resources."

  register_sample \
    "gs-securing-web" \
    "spring-guides/gs-securing-web" \
    "https://github.com/spring-guides/gs-securing-web" \
    "6c986e19b4b329dd4a3d9d3d932a6e0e5bf74ad5" \
    "gs-securing-web" \
    "complete" \
    "./mvnw -q -DskipTests package" \
    "complete/target/securing-web-complete-0.0.1-SNAPSHOT.jar" \
    "thin Maven JAR / Spring Security" \
    "80" \
    "" \
    "Spring Security sample; thin JAR without a Spring Boot repackage step."

  register_sample \
    "spring-boot-shiro" \
    "pbw123/springboot_learn" \
    "https://github.com/pbw123/springboot_learn" \
    "3790fd026dd333226cf6a3ec52531b2b8007d541" \
    "springboot_learn" \
    "spring-boot-shiro" \
    "./mvnw -q -DskipTests package" \
    "spring-boot-shiro/target/spring-boot-shiro-0.0.1-SNAPSHOT.jar" \
    "Spring Boot executable JAR / Shiro" \
    "80" \
    "${JAVA8_HOME}" \
    "Shiro sample uses older Lombok/Spring Boot; build and verification must run on Java 8."

  register_sample \
    "commons-fileupload" \
    "apache/commons-fileupload" \
    "https://github.com/apache/commons-fileupload" \
    "f3e030f09ac8b01b684466c793dec86eafe1e4c9" \
    "commons-fileupload" \
    "." \
    "mvn -q -DskipTests package" \
    "target/commons-fileupload-1.6.0.jar" \
    "Servlet upload library JAR" \
    "80" \
    "" \
    "Servlet upload library; complements the WAR sample with a dependency-style servlet API artifact."

  register_sample \
    "gs-uploading-files" \
    "spring-guides/gs-uploading-files" \
    "https://github.com/spring-guides/gs-uploading-files" \
    "02df6b5a928ed8d91b8aedb37e28f1d6ce9fd32a" \
    "gs-uploading-files" \
    "complete" \
    "mvn -q -DskipTests package" \
    "complete/target/uploading-files-complete-0.0.1-SNAPSHOT.jar" \
    "Spring Boot executable JAR / upload" \
    "80" \
    "" \
    "Small Spring Boot upload app with templates and file storage service paths."

  register_sample \
    "spring-boot-thymeleaf-war" \
    "kolorobot/spring-boot-thymeleaf" \
    "https://github.com/kolorobot/spring-boot-thymeleaf" \
    "00cb739087a7d933fbf3bca716fd06b4b362a996" \
    "spring-boot-thymeleaf-war" \
    "." \
    "./mvnw -q -DskipTests package" \
    "target/spring-boot-thymeleaf-2.0.0.war" \
    "Spring Boot executable WAR / Thymeleaf" \
    "80" \
    "" \
    "Executable WAR with WEB-INF/classes, Boot loader, templates, static assets, and profile config."
}

run_with_java_home() {
  local java_home="$1"
  shift
  if [[ -n "${java_home}" ]]; then
    if [[ ! -d "${java_home}" ]]; then
      log "Missing Java home: ${java_home}"
      exit 1
    fi
    JAVA_HOME="${java_home}" PATH="${java_home}/bin:${PATH}" "$@"
  else
    "$@"
  fi
}

build_sample() {
  local index="$1"
  local name="${sample_names[${index}]}"
  local repo="${sample_repos[${index}]}"
  local ref="${sample_refs[${index}]}"
  local repo_dir="${REPOS_DIR}/${sample_repo_dirs[${index}]}"
  local subdir="${sample_subdirs[${index}]}"
  local build_command="${sample_build_commands[${index}]}"
  local java_home="${sample_java_homes[${index}]}"

  download_archive "${repo}" "${ref}" "${repo_dir}"
  find "${repo_dir}" -name mvnw -type f -exec chmod +x {} +

  log "Building ${name}"
  run_with_java_home "${java_home}" bash -c "cd \"${repo_dir}/${subdir}\" && ${build_command}" \
    > "${REPORT_DIR}/${name}.build.log" 2>&1

  local artifact="${repo_dir}/${sample_artifacts[${index}]}"
  if [[ ! -f "${artifact}" ]]; then
    log "Missing built artifact for ${name}: ${artifact}"
    exit 1
  fi
}

parse_overall_score() {
  local report="$1"
  awk -F'[:/]' '/^- Overall:/ { gsub(/ /, "", $2); print $2; exit }' "${report}" 2>/dev/null || true
}

parse_bucket_score() {
  local report="$1"
  local bucket="$2"
  awk -v bucket="${bucket}" -F'|' '
    $2 ~ bucket {
      value=$3
      gsub(/ /, "", value)
      print value
      exit
    }
  ' "${report}" 2>/dev/null || true
}

parse_verification_status() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing'
    return
  fi
  awk -F': ' '/^- Summary:/ { print $2; exit }' "${report}"
}

parse_verification_failure_type() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing'
    return
  fi
  awk -F': ' '/^- Failure type:/ { print $2; exit }' "${report}"
}

parse_decompile_failures() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing'
  elif grep -q 'No decompilation failures detected' "${report}"; then
    printf '0'
  else
    grep -c '^## ' "${report}" || true
  fi
}

parse_runtime_field() {
  local report="$1"
  local label="$2"
  local fallback="$3"
  if [[ ! -f "${report}" ]]; then
    printf '%s' "${fallback}"
    return
  fi
  local value
  value="$(awk -v label="${label}" -F': ' '$0 ~ "^- " label ":" { print $2; exit }' "${report}" 2>/dev/null || true)"
  value="${value//\`/}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value:-${fallback}}"
}

package_restored_project() {
  local project_dir="$1"
  local java_home="$2"
  local log_file="$3"
  run_with_java_home "${java_home}" bash -c "cd \"${project_dir}\" && \"${MVN}\" ${RESTORED_PACKAGE_FLAGS[*]}" \
    > "${log_file}" 2>&1
}

find_rebuilt_artifact() {
  local project_dir="$1"
  local original_artifact="$2"
  local extension="${original_artifact##*.}"
  find "${project_dir}/target" -maxdepth 1 -type f \
    -name "*.${extension}" \
    ! -name "*-sources.jar" \
    ! -name "*-javadoc.jar" \
    ! -name "original-*.jar" \
    ! -name "original-*.war" \
    | sort | head -n 1
}

parse_artifact_summary_field() {
  local csv="$1"
  local column="$2"
  local fallback="$3"
  if [[ ! -f "${csv}" ]]; then
    printf '%s' "${fallback}"
    return
  fi
  local value
  value="$(awk -F',' -v column="${column}" 'NR == 2 { print $column; exit }' "${csv}" 2>/dev/null || true)"
  printf '%s' "${value:-${fallback}}"
}

run_sample() {
  local index="$1"
  local name="${sample_names[${index}]}"
  local repo_dir="${REPOS_DIR}/${sample_repo_dirs[${index}]}"
  local artifact="${repo_dir}/${sample_artifacts[${index}]}"
  local threshold="${sample_thresholds[${index}]}"
  local java_home="${sample_java_homes[${index}]}"
  local output_base="${RESTORE_DIR}/${name}"

  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  local args=(--verbose --emit-raw-artifact --trace-runtime --trace-timeout "${JAVA_TRACE_TIMEOUT}" --trace-args "${REALWORLD_TRACE_ARGS}" --verify-build --verify-goal compile -f -o "${output_base}" "${artifact}")

  log "Restoring ${name}"
  set +e
  run_with_java_home "${java_home}" java -jar "${JAR2MP_JAR}" "${args[@]}" \
    > "${REPORT_DIR}/${name}.cli.log" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1 || true)"
  local score_report="${project_dir}/restoration-score.md"
  local verification_report="${project_dir}/verification-report.md"
  local runtime_report="${project_dir}/runtime-trace-report.md"
  local failures_report="${project_dir}/decompile-failures.md"

  local overall="0"
  local source_score="0"
  local resource_score="0"
  local runtime_score="0"
  local verification_score="0"
  local verification_status="missing"
  local verification_failure_type="missing"
  local decompile_failures="missing"
  local runtime_launch_type="not-run"
  local runtime_launch_support="not-run"
  local runtime_run_status="not-run"
  local runtime_events="not-run"
  local artifact_exact="not-run"
  local artifact_diff_sha="not-run"
  local artifact_missing="not-run"
  local artifact_extra="not-run"
  local artifact_diff_classes="not-run"
  local raw_artifact_exact="not-run"
  local raw_artifact_diff_sha="not-run"
  local raw_artifact_missing="not-run"
  local raw_artifact_extra="not-run"
  local raw_artifact_diff_classes="not-run"
  local status="FAIL"

  if [[ -n "${project_dir}" && -f "${score_report}" ]]; then
    overall="$(parse_overall_score "${score_report}")"
    source_score="$(parse_bucket_score "${score_report}" "source")"
    resource_score="$(parse_bucket_score "${score_report}" "resource")"
    runtime_score="$(parse_bucket_score "${score_report}" "runtime")"
    verification_score="$(parse_bucket_score "${score_report}" "verification")"
    verification_status="$(parse_verification_status "${verification_report}")"
    verification_failure_type="$(parse_verification_failure_type "${verification_report}")"
    decompile_failures="$(parse_decompile_failures "${failures_report}")"
    runtime_launch_type="$(parse_runtime_field "${runtime_report}" "Launch type" "missing")"
    runtime_launch_support="$(parse_runtime_field "${runtime_report}" "Launch support" "missing")"
    runtime_run_status="$(parse_runtime_field "${runtime_report}" "Run status" "missing")"
    runtime_events="$(parse_runtime_field "${runtime_report}" "Total events" "missing")"

    overall="${overall:-0}"
    source_score="${source_score:-0}"
    resource_score="${resource_score:-0}"
    runtime_score="${runtime_score:-0}"
    verification_score="${verification_score:-0}"
    verification_status="${verification_status:-missing}"
    verification_failure_type="${verification_failure_type:-missing}"
    decompile_failures="${decompile_failures:-missing}"
    runtime_launch_type="${runtime_launch_type:-missing}"
    runtime_launch_support="${runtime_launch_support:-missing}"
    runtime_run_status="${runtime_run_status:-missing}"
    runtime_events="${runtime_events:-missing}"

    local raw_artifact_csv="${project_dir}/target/raw-artifact/artifact-fidelity-summary.csv"
    raw_artifact_exact="$(parse_artifact_summary_field "${raw_artifact_csv}" 1 "missing")"
    raw_artifact_diff_sha="$(parse_artifact_summary_field "${raw_artifact_csv}" 6 "missing")"
    raw_artifact_missing="$(parse_artifact_summary_field "${raw_artifact_csv}" 7 "missing")"
    raw_artifact_extra="$(parse_artifact_summary_field "${raw_artifact_csv}" 8 "missing")"
    raw_artifact_diff_classes="$(parse_artifact_summary_field "${raw_artifact_csv}" 13 "missing")"
    raw_artifact_exact="${raw_artifact_exact:-missing}"
    raw_artifact_diff_sha="${raw_artifact_diff_sha:-missing}"
    raw_artifact_missing="${raw_artifact_missing:-missing}"
    raw_artifact_extra="${raw_artifact_extra:-missing}"
    raw_artifact_diff_classes="${raw_artifact_diff_classes:-missing}"

    local rebuilt_artifact=""
    if package_restored_project "${project_dir}" "${java_home}" "${REPORT_DIR}/${name}.package.log"; then
      rebuilt_artifact="$(find_rebuilt_artifact "${project_dir}" "${artifact}")"
      if [[ -n "${rebuilt_artifact}" && -f "${rebuilt_artifact}" ]]; then
        if run_with_java_home "${java_home}" java -jar "${JAR2MP_JAR}" \
          --compare-artifact "${rebuilt_artifact}" -q -o "${project_dir}" "${artifact}" \
          > "${REPORT_DIR}/${name}.artifact-fidelity.log" 2>&1; then
          local artifact_csv="${project_dir}/artifact-fidelity-summary.csv"
          artifact_exact="$(parse_artifact_summary_field "${artifact_csv}" 1 "missing")"
          artifact_diff_sha="$(parse_artifact_summary_field "${artifact_csv}" 6 "missing")"
          artifact_missing="$(parse_artifact_summary_field "${artifact_csv}" 7 "missing")"
          artifact_extra="$(parse_artifact_summary_field "${artifact_csv}" 8 "missing")"
          artifact_diff_classes="$(parse_artifact_summary_field "${artifact_csv}" 13 "missing")"
        else
          artifact_exact="compare-failed"
        fi
      else
        artifact_exact="rebuilt-missing"
      fi
    else
      artifact_exact="package-failed"
    fi

    if [[ "${exit_code}" -eq 0 \
      && "${overall}" -ge "${threshold}" \
      && "${source_score}" -eq 100 \
      && "${resource_score}" -eq 100 \
      && "${verification_status}" == "BUILD SUCCESS" \
      && "${verification_failure_type}" == "NONE" \
      && "${decompile_failures}" == "0" ]]; then
      status="PASS"
    fi
  fi

  {
    csv_field "${name}"; printf ','
    csv_field "${status}"; printf ','
    csv_field "${sample_repos[${index}]}"; printf ','
    csv_field "${sample_refs[${index}]}"; printf ','
    csv_field "${sample_types[${index}]}"; printf ','
    csv_field "${overall}"; printf ','
    csv_field "${source_score}"; printf ','
    csv_field "${resource_score}"; printf ','
    csv_field "${runtime_score}"; printf ','
    csv_field "${verification_score}"; printf ','
    csv_field "${verification_status}"; printf ','
    csv_field "${verification_failure_type}"; printf ','
    csv_field "${decompile_failures}"; printf ','
    csv_field "${runtime_launch_type}"; printf ','
    csv_field "${runtime_launch_support}"; printf ','
    csv_field "${runtime_run_status}"; printf ','
    csv_field "${runtime_events}"; printf ','
    csv_field "${artifact_exact}"; printf ','
    csv_field "${artifact_diff_sha}"; printf ','
    csv_field "${artifact_missing}"; printf ','
    csv_field "${artifact_extra}"; printf ','
    csv_field "${artifact_diff_classes}"; printf ','
    csv_field "${raw_artifact_exact}"; printf ','
    csv_field "${raw_artifact_diff_sha}"; printf ','
    csv_field "${raw_artifact_missing}"; printf ','
    csv_field "${raw_artifact_extra}"; printf ','
    csv_field "${raw_artifact_diff_classes}"; printf ','
    csv_field "${threshold}"; printf ','
    csv_field "${java_home:-default}"; printf ','
    csv_field "${sample_notes[${index}]}"; printf '\n'
  } >> "${REPORT_DIR}/github-realworld-summary.csv"

  cat >> "${REPORT_DIR}/github-realworld-summary.md" <<MD
| ${name} | ${status} | ${sample_repos[${index}]} | ${sample_refs[${index}]} | ${sample_types[${index}]} | ${overall} | ${source_score} | ${resource_score} | ${runtime_score} | ${verification_score} | ${verification_status} | ${verification_failure_type} | ${decompile_failures} | ${runtime_launch_type} | ${runtime_launch_support} | ${runtime_run_status} | ${runtime_events} | ${artifact_exact} | ${artifact_diff_sha} | ${artifact_missing} | ${artifact_extra} | ${artifact_diff_classes} | ${raw_artifact_exact} | ${raw_artifact_diff_sha} | ${raw_artifact_missing} | ${raw_artifact_extra} | ${raw_artifact_diff_classes} | ${threshold} |
MD
}

main() {
  rm -rf "${REPOS_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"
  mkdir -p "${REPOS_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}" "${ARCHIVE_DIR}"

  log "Building jar2mp"
  (cd "${ROOT_DIR}" && "${MVN}" -q -DskipTests package)

  prepare_samples

  write_file "${REPORT_DIR}/github-realworld-summary.csv" <<'CSV'
sample,status,repo,ref,artifact_type,overall,source,resource,runtime,verification,verification_status,verification_failure_type,decompile_failures,runtime_launch_type,runtime_launch_support,runtime_run_status,runtime_events,artifact_exact,artifact_diff_sha,artifact_missing,artifact_extra,artifact_diff_classes,raw_artifact_exact,raw_artifact_diff_sha,raw_artifact_missing,raw_artifact_extra,raw_artifact_diff_classes,threshold,java_home,note
CSV
  write_file "${REPORT_DIR}/github-realworld-summary.md" <<'MD'
# jar2mp GitHub Real-World Regression Summary

This is a compile-gate summary with non-gating runtime and artifact-fidelity evidence columns.

| Sample | Status | Repo | Ref | Artifact type | Overall | Source | Resource | Runtime | Verification | Verification status | Failure type | Decompile failures | Runtime launch | Runtime support | Runtime status | Runtime events | Artifact exact | Artifact diff SHA | Artifact missing | Artifact extra | Artifact diff classes | Raw exact | Raw diff SHA | Raw missing | Raw extra | Raw diff classes | Threshold |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: | --- | --- | --- | ---: | --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |
MD

  local i
  for i in "${!sample_names[@]}"; do
    build_sample "${i}"
    run_sample "${i}"
  done

  log "Summary: ${REPORT_DIR}/github-realworld-summary.md"
  log "CSV: ${REPORT_DIR}/github-realworld-summary.csv"
  if grep -q ',"FAIL",' "${REPORT_DIR}/github-realworld-summary.csv"; then
    log "At least one real-world sample failed."
    exit 1
  fi
}

main "$@"
