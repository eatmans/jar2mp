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

  local args=(--verbose --verify-build --verify-goal compile -f -o "${output_base}" "${artifact}")

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
  local failures_report="${project_dir}/decompile-failures.md"

  local overall="0"
  local source_score="0"
  local resource_score="0"
  local runtime_score="0"
  local verification_score="0"
  local verification_status="missing"
  local verification_failure_type="missing"
  local decompile_failures="missing"
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

    overall="${overall:-0}"
    source_score="${source_score:-0}"
    resource_score="${resource_score:-0}"
    runtime_score="${runtime_score:-0}"
    verification_score="${verification_score:-0}"
    verification_status="${verification_status:-missing}"
    verification_failure_type="${verification_failure_type:-missing}"
    decompile_failures="${decompile_failures:-missing}"

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
    csv_field "${threshold}"; printf ','
    csv_field "${java_home:-default}"; printf ','
    csv_field "${sample_notes[${index}]}"; printf '\n'
  } >> "${REPORT_DIR}/github-realworld-summary.csv"

  cat >> "${REPORT_DIR}/github-realworld-summary.md" <<MD
| ${name} | ${status} | ${sample_repos[${index}]} | ${sample_refs[${index}]} | ${sample_types[${index}]} | ${overall} | ${source_score} | ${resource_score} | ${runtime_score} | ${verification_score} | ${verification_status} | ${verification_failure_type} | ${decompile_failures} | ${threshold} |
MD
}

main() {
  rm -rf "${REPOS_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"
  mkdir -p "${REPOS_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}" "${ARCHIVE_DIR}"

  log "Building jar2mp"
  (cd "${ROOT_DIR}" && "${MVN}" -q -DskipTests package)

  prepare_samples

  write_file "${REPORT_DIR}/github-realworld-summary.csv" <<'CSV'
sample,status,repo,ref,artifact_type,overall,source,resource,runtime,verification,verification_status,verification_failure_type,decompile_failures,threshold,java_home,note
CSV
  write_file "${REPORT_DIR}/github-realworld-summary.md" <<'MD'
# jar2mp GitHub Real-World Regression Summary

This is a verify-only gate. Runtime score is expected to remain 0 unless a sample is explicitly run with tracing.

| Sample | Status | Repo | Ref | Artifact type | Overall | Source | Resource | Runtime | Verification | Verification status | Failure type | Decompile failures | Threshold |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: | ---: |
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
