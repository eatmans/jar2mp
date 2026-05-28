#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${ROOT_DIR}/target/release-assets-samples"
ASSET_DIR="${WORK_DIR}/assets"
RESTORE_DIR="${WORK_DIR}/restored"
REPORT_DIR="${WORK_DIR}/report"
JAR2MP_JAR="${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar"

JAVA_TRACE_TIMEOUT="${JAVA_TRACE_TIMEOUT:-20}"
RELEASE_ASSET_DOWNLOAD_TIMEOUT="${RELEASE_ASSET_DOWNLOAD_TIMEOUT:-120}"
INCLUDE_MEDIUM_RELEASE_ASSETS="${INCLUDE_MEDIUM_RELEASE_ASSETS:-0}"
INCLUDE_HEAVY_RELEASE_ASSETS="${INCLUDE_HEAVY_RELEASE_ASSETS:-0}"
STRICT_RELEASE_ASSETS="${STRICT_RELEASE_ASSETS:-0}"

sample_names=()
sample_repos=()
sample_release_urls=()
sample_asset_names=()
sample_asset_urls=()
sample_asset_sizes=()
sample_types=()
sample_thresholds=()
sample_trace_modes=()
sample_trace_args=()
sample_notes=()

log() {
  printf '[release-assets] %s\n' "$*"
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
  local release_url="$3"
  local asset_name="$4"
  local asset_url="$5"
  local asset_size_mb="$6"
  local artifact_type="$7"
  local threshold="$8"
  local trace_mode="$9"
  local trace_args="${10}"
  local note="${11}"

  sample_names+=("${name}")
  sample_repos+=("${repo}")
  sample_release_urls+=("${release_url}")
  sample_asset_names+=("${asset_name}")
  sample_asset_urls+=("${asset_url}")
  sample_asset_sizes+=("${asset_size_mb}")
  sample_types+=("${artifact_type}")
  sample_thresholds+=("${threshold}")
  sample_trace_modes+=("${trace_mode}")
  sample_trace_args+=("${trace_args}")
  sample_notes+=("${note}")
}

prepare_samples() {
  register_sample \
    "google-java-format" \
    "google/google-java-format" \
    "https://github.com/google/google-java-format/releases/tag/v1.35.0" \
    "google-java-format-1.35.0-all-deps.jar" \
    "https://github.com/google/google-java-format/releases/download/v1.35.0/google-java-format-1.35.0-all-deps.jar" \
    "3" \
    "Fat CLI formatter JAR" \
    "60" \
    "trace" \
    "--version" \
    "Small fat CLI jar with relocated dependencies and modern classfile levels."

  register_sample \
    "h2-database" \
    "h2database/h2database" \
    "https://github.com/h2database/h2database/releases/tag/version-2.4.240" \
    "h2-2.4.240.jar" \
    "https://github.com/h2database/h2database/releases/download/version-2.4.240/h2-2.4.240.jar" \
    "2" \
    "JDBC database library / CLI JAR" \
    "60" \
    "trace" \
    "-help" \
    "Small library with optional tool entrypoints and service metadata."

  register_sample \
    "jd-gui" \
    "java-decompiler/jd-gui" \
    "https://github.com/java-decompiler/jd-gui/releases/tag/v1.6.6" \
    "jd-gui-1.6.6-min.jar" \
    "https://github.com/java-decompiler/jd-gui/releases/download/v1.6.6/jd-gui-1.6.6-min.jar" \
    "1" \
    "Swing GUI executable JAR" \
    "60" \
    "none" \
    "" \
    "Small GUI application; trace is skipped to avoid opening the desktop UI."

  register_sample \
    "liquibase-core" \
    "liquibase/liquibase" \
    "https://github.com/liquibase/liquibase/releases/tag/v5.0.3" \
    "liquibase-core-5.0.3.jar" \
    "https://github.com/liquibase/liquibase/releases/download/v5.0.3/liquibase-core-5.0.3.jar" \
    "2" \
    "Database migration library JAR" \
    "60" \
    "none" \
    "" \
    "Library artifact with service metadata and XML/resources; no standalone smoke launch expected."

  register_sample \
    "plantuml-mit-light" \
    "plantuml/plantuml" \
    "https://github.com/plantuml/plantuml/releases/tag/v1.2026.5" \
    "plantuml-mit-light-1.2026.5.jar" \
    "https://github.com/plantuml/plantuml/releases/download/v1.2026.5/plantuml-mit-light-1.2026.5.jar" \
    "7" \
    "Fat CLI diagram JAR" \
    "60" \
    "trace" \
    "-version" \
    "Medium-small CLI jar with many resources and generated parser classes."

  if [[ "${INCLUDE_MEDIUM_RELEASE_ASSETS}" == "1" ]]; then
    register_sample \
      "checkstyle" \
      "checkstyle/checkstyle" \
      "https://github.com/checkstyle/checkstyle/releases/tag/checkstyle-13.4.2" \
      "checkstyle-13.4.2-all.jar" \
      "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-13.4.2/checkstyle-13.4.2-all.jar" \
      "16" \
      "Fat CLI static-analysis JAR" \
      "60" \
      "trace" \
      "--version" \
      "Larger CLI jar with shaded dependencies and extensive bundled configuration resources."

    register_sample \
      "karate" \
      "karatelabs/karate" \
      "https://github.com/karatelabs/karate/releases/tag/v2.0.9" \
      "karate-2.0.9.jar" \
      "https://github.com/karatelabs/karate/releases/download/v2.0.9/karate-2.0.9.jar" \
      "11" \
      "Fat test-runtime CLI JAR" \
      "60" \
      "trace" \
      "--version" \
      "CLI/test runtime jar with embedded engines and mixed resources."

    register_sample \
      "openapi-generator-cli" \
      "OpenAPITools/openapi-generator" \
      "https://github.com/OpenAPITools/openapi-generator/releases/tag/v7.22.0" \
      "openapi-generator-cli-7.22.0.jar" \
      "https://github.com/OpenAPITools/openapi-generator/releases/download/v7.22.0/openapi-generator-cli-7.22.0.jar" \
      "29" \
      "Large fat code-generator CLI JAR" \
      "60" \
      "trace" \
      "version" \
      "Large CLI jar with templates, service metadata, and many bundled dependencies."
  fi

  if [[ "${INCLUDE_HEAVY_RELEASE_ASSETS}" == "1" ]]; then
    register_sample \
      "dependency-track-bundled" \
      "DependencyTrack/dependency-track" \
      "https://github.com/DependencyTrack/dependency-track/releases/tag/4.14.2" \
      "dependency-track-bundled.jar" \
      "https://github.com/DependencyTrack/dependency-track/releases/download/4.14.2/dependency-track-bundled.jar" \
      "77" \
      "Heavy Spring Boot application JAR" \
      "60" \
      "trace" \
      "--server.port=0" \
      "Heavy application release artifact; excluded by default because restore and compile are slow."

    register_sample \
      "jenkins-war" \
      "jenkinsci/jenkins" \
      "https://github.com/jenkinsci/jenkins/releases/tag/jenkins-2.566" \
      "jenkins.war" \
      "https://github.com/jenkinsci/jenkins/releases/download/jenkins-2.566/jenkins.war" \
      "96" \
      "Heavy executable WAR" \
      "60" \
      "none" \
      "" \
      "Large executable WAR; excluded by default to keep the release-assets pass practical."
  fi
}

download_asset() {
  local url="$1"
  local asset_name="$2"
  local dest="${ASSET_DIR}/${asset_name}"
  local tmp="${dest}.part"
  mkdir -p "${ASSET_DIR}"
  if [[ ! -s "${dest}" ]]; then
    rm -f "${dest}" "${tmp}"
    log "Downloading ${asset_name}" >&2
    curl -fsSL --retry 0 --max-time "${RELEASE_ASSET_DOWNLOAD_TIMEOUT}" -o "${tmp}" "${url}"
    if ! jar tf "${tmp}" >/dev/null 2>&1; then
      rm -f "${tmp}"
      log "Downloaded asset is not a readable archive: ${asset_name}" >&2
      return 1
    fi
    mv "${tmp}" "${dest}"
  else
    log "Using cached asset ${asset_name}" >&2
  fi
  printf '%s' "${dest}"
}

write_sample_row() {
  local index="$1"
  local name="$2"
  local status="$3"
  local exit_code="$4"
  local overall="$5"
  local source_score="$6"
  local resource_score="$7"
  local runtime_score="$8"
  local verification_score="$9"
  local verification_status="${10}"
  local verification_failure_type="${11}"
  local decompile_failures="${12}"
  local runtime_launch_support="${13}"
  local runtime_run_status="${14}"
  local runtime_events="${15}"
  local runtime_gate="${16}"
  local raw_artifact_exact="${17}"
  local raw_artifact_gate="${18}"
  local project_dir="${19}"

  {
    csv_field "${name}"; printf ','
    csv_field "${status}"; printf ','
    csv_field "${sample_repos[${index}]}"; printf ','
    csv_field "${sample_release_urls[${index}]}"; printf ','
    csv_field "${sample_asset_names[${index}]}"; printf ','
    csv_field "${sample_asset_sizes[${index}]}"; printf ','
    csv_field "${sample_types[${index}]}"; printf ','
    csv_field "${exit_code}"; printf ','
    csv_field "${overall}"; printf ','
    csv_field "${source_score}"; printf ','
    csv_field "${resource_score}"; printf ','
    csv_field "${runtime_score}"; printf ','
    csv_field "${verification_score}"; printf ','
    csv_field "${verification_status}"; printf ','
    csv_field "${verification_failure_type}"; printf ','
    csv_field "${decompile_failures}"; printf ','
    csv_field "${runtime_launch_support}"; printf ','
    csv_field "${runtime_run_status}"; printf ','
    csv_field "${runtime_events}"; printf ','
    csv_field "${runtime_gate}"; printf ','
    csv_field "${raw_artifact_exact}"; printf ','
    csv_field "${raw_artifact_gate}"; printf ','
    csv_field "${sample_thresholds[${index}]}"; printf ','
    csv_field "${project_dir:-missing}"; printf ','
    csv_field "${sample_notes[${index}]}"; printf '\n'
  } >> "${REPORT_DIR}/github-release-assets-summary.csv"

  cat >> "${REPORT_DIR}/github-release-assets-summary.md" <<MD
| ${name} | ${status} | ${sample_repos[${index}]} | [release](${sample_release_urls[${index}]}) | ${sample_asset_names[${index}]} | ${sample_asset_sizes[${index}]} | ${sample_types[${index}]} | ${overall} | ${source_score} | ${resource_score} | ${runtime_score} | ${verification_score} | ${verification_status} | ${verification_failure_type} | ${decompile_failures} | ${runtime_gate} | ${raw_artifact_gate} | ${sample_thresholds[${index}]} |
MD
}

parse_overall_score() {
  local report="$1"
  [[ -f "${report}" ]] || { printf '0'; return; }
  awk -F'[:/]' '/^- Overall:/ {gsub(/ /, "", $2); print $2; exit}' "${report}"
}

parse_bucket_score() {
  local report="$1"
  local bucket="$2"
  [[ -f "${report}" ]] || { printf '0'; return; }
  awk -F'|' -v bucket="${bucket}" '
    $2 ~ " " bucket " " {
      value=$3
      gsub(/ /, "", value)
      print value
      exit
    }' "${report}"
}

parse_verification_status() {
  local report="$1"
  [[ -f "${report}" ]] || { printf 'missing'; return; }
  awk -F': ' '/^- Summary:/ {print $2; exit}' "${report}"
}

parse_verification_failure_type() {
  local report="$1"
  [[ -f "${report}" ]] || { printf 'missing'; return; }
  awk -F': ' '/^- Failure type:/ {print $2; exit}' "${report}"
}

parse_runtime_field() {
  local report="$1"
  local label="$2"
  local fallback="$3"
  [[ -f "${report}" ]] || { printf '%s' "${fallback}"; return; }
  local value
  value="$(awk -v label="${label}" -F': ' '$0 ~ "^- " label ":" {print $2; exit}' "${report}" 2>/dev/null || true)"
  value="${value//\`/}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value:-${fallback}}"
}

parse_decompile_failures() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing'
  elif grep -q 'No decompilation failures detected' "${report}"; then
    printf '0'
  else
    grep -c '^- Failed to decompile ' "${report}" || true
  fi
}

parse_artifact_summary_field() {
  local csv="$1"
  local column="$2"
  local fallback="$3"
  [[ -f "${csv}" ]] || { printf '%s' "${fallback}"; return; }
  local value
  value="$(awk -F',' -v column="${column}" 'NR == 2 {print $column; exit}' "${csv}" 2>/dev/null || true)"
  printf '%s' "${value:-${fallback}}"
}

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

classify_runtime_gate() {
  local trace_mode="$1"
  local launch_support="$2"
  local run_status="$3"
  local runtime_events="$4"

  if [[ "${trace_mode}" != "trace" ]]; then
    printf 'SKIPPED_BY_SAMPLE'
    return
  fi
  if [[ "${launch_support}" != "SUPPORTED" ]]; then
    printf 'SKIPPED_UNSUPPORTED'
    return
  fi
  if [[ "${run_status}" == "EXIT_ZERO" ]]; then
    printf 'PASS_EXIT_ZERO'
    return
  fi
  if [[ "${run_status}" == "TRACE_COLLECTED_TIMEOUT" ]] && is_positive_integer "${runtime_events}"; then
    printf 'WARN_STARTED_TIMEOUT'
    return
  fi
  printf 'WARN_%s' "${run_status:-missing}"
}

classify_raw_gate() {
  local exact="$1"
  if [[ "${exact}" == "true" ]]; then
    printf 'PASS_EXACT'
  else
    printf 'FAIL_%s' "${exact:-missing}"
  fi
}

run_sample() {
  local index="$1"
  local name="${sample_names[${index}]}"
  local asset_name="${sample_asset_names[${index}]}"
  local asset_url="${sample_asset_urls[${index}]}"
  local threshold="${sample_thresholds[${index}]}"
  local trace_mode="${sample_trace_modes[${index}]}"
  local trace_args="${sample_trace_args[${index}]}"
  local output_base="${RESTORE_DIR}/${name}"
  local cli_log="${REPORT_DIR}/${name}.cli.log"
  local asset_path

  if ! asset_path="$(download_asset "${asset_url}" "${asset_name}")"; then
    write_sample_row "${index}" "${name}" "DOWNLOAD_FAILED" "download-failed" \
      "0" "0" "0" "0" "0" "not-run" "not-run" "not-run" \
      "not-run" "not-run" "not-run" "not-run" "missing" "not-run" "missing"
    return
  fi
  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  local args=(--verbose --emit-raw-artifact --verify-build --verify-goal compile -f -o "${output_base}")
  if [[ "${trace_mode}" == "trace" ]]; then
    args+=(--trace-runtime --trace-timeout "${JAVA_TRACE_TIMEOUT}")
    if [[ -n "${trace_args}" ]]; then
      args+=(--trace-args "${trace_args}")
    fi
  fi

  log "Restoring ${name}"
  set +e
  java -jar "${JAR2MP_JAR}" "${args[@]}" "${asset_path}" > "${cli_log}" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1 || true)"
  local score_report="${project_dir}/restoration-score.md"
  local verification_report="${project_dir}/verification-report.md"
  local runtime_report="${project_dir}/runtime-trace-report.md"
  local failures_report="${project_dir}/decompile-failures.md"
  local raw_artifact_csv="${project_dir}/target/raw-artifact/artifact-fidelity-summary.csv"

  local overall="0"
  local source_score="0"
  local resource_score="0"
  local runtime_score="0"
  local verification_score="0"
  local verification_status="missing"
  local verification_failure_type="missing"
  local decompile_failures="missing"
  local runtime_launch_support="not-run"
  local runtime_run_status="not-run"
  local runtime_events="not-run"
  local runtime_gate="not-run"
  local raw_artifact_exact="missing"
  local raw_artifact_gate="missing"
  local status="RESTORE_FAILED"

  if [[ -n "${project_dir}" && -f "${score_report}" ]]; then
    overall="$(parse_overall_score "${score_report}")"
    source_score="$(parse_bucket_score "${score_report}" "source")"
    resource_score="$(parse_bucket_score "${score_report}" "resource")"
    runtime_score="$(parse_bucket_score "${score_report}" "runtime")"
    verification_score="$(parse_bucket_score "${score_report}" "verification")"
    verification_status="$(parse_verification_status "${verification_report}")"
    verification_failure_type="$(parse_verification_failure_type "${verification_report}")"
    decompile_failures="$(parse_decompile_failures "${failures_report}")"
    runtime_launch_support="$(parse_runtime_field "${runtime_report}" "Launch support" "not-run")"
    runtime_run_status="$(parse_runtime_field "${runtime_report}" "Run status" "not-run")"
    runtime_events="$(parse_runtime_field "${runtime_report}" "Total events" "not-run")"
    raw_artifact_exact="$(parse_artifact_summary_field "${raw_artifact_csv}" 1 "missing")"
    runtime_gate="$(classify_runtime_gate "${trace_mode}" "${runtime_launch_support}" "${runtime_run_status}" "${runtime_events}")"
    raw_artifact_gate="$(classify_raw_gate "${raw_artifact_exact}")"

    status="GAP"
    if [[ "${exit_code}" -eq 0 \
      && "${overall:-0}" -ge "${threshold}" \
      && "${source_score:-0}" -eq 100 \
      && "${resource_score:-0}" -eq 100 \
      && "${verification_status}" == "BUILD SUCCESS" \
      && "${verification_failure_type}" == "NONE" \
      && "${decompile_failures}" == "0" \
      && "${raw_artifact_gate}" == "PASS_EXACT" ]]; then
      status="PASS"
      if [[ "${runtime_gate}" == WARN_* || "${runtime_gate}" == SKIPPED_* ]]; then
        status="PASS_WITH_WARNINGS"
      fi
    fi
  fi

  write_sample_row "${index}" "${name}" "${status}" "${exit_code}" \
    "${overall}" "${source_score}" "${resource_score}" "${runtime_score}" "${verification_score}" \
    "${verification_status}" "${verification_failure_type}" "${decompile_failures}" \
    "${runtime_launch_support}" "${runtime_run_status}" "${runtime_events}" "${runtime_gate}" \
    "${raw_artifact_exact}" "${raw_artifact_gate}" "${project_dir:-missing}"
}

main() {
  mkdir -p "${ASSET_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"
  rm -f "${REPORT_DIR}/github-release-assets-summary.md" "${REPORT_DIR}/github-release-assets-summary.csv"

  log "Building jar2mp"
  (cd "${ROOT_DIR}" && mvn -q -DskipTests package)

  prepare_samples

  write_file "${REPORT_DIR}/github-release-assets-summary.md" <<'MD'
# jar2mp GitHub Release Asset Regression Summary

This matrix downloads prebuilt JAR/WAR assets from GitHub Releases. It is exploratory by default: set `STRICT_RELEASE_ASSETS=1` to fail the script when any sample is not `PASS` or `PASS_WITH_WARNINGS`.

| Sample | Status | Repo | Release | Asset | Size MB | Artifact type | Overall | Source | Resource | Runtime | Verification | Verification status | Failure type | Decompile failures | Runtime gate | Raw gate | Threshold |
| --- | --- | --- | --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | ---: | --- | --- | ---: |
MD

  write_file "${REPORT_DIR}/github-release-assets-summary.csv" <<'CSV'
sample,status,repo,release_url,asset,size_mb,artifact_type,exit_code,overall,source,resource,runtime,verification,verification_status,verification_failure_type,decompile_failures,runtime_support,runtime_status,runtime_events,runtime_gate,raw_artifact_exact,raw_gate,threshold,project_dir,note
CSV

  local total="${#sample_names[@]}"
  local index
  for ((index = 0; index < total; index++)); do
    run_sample "${index}"
  done

  log "Summary: ${REPORT_DIR}/github-release-assets-summary.md"
  log "CSV: ${REPORT_DIR}/github-release-assets-summary.csv"

  if [[ "${STRICT_RELEASE_ASSETS}" == "1" ]] \
    && grep -Ev '^sample,' "${REPORT_DIR}/github-release-assets-summary.csv" \
    | grep -Eq ',"(GAP|RESTORE_FAILED|DOWNLOAD_FAILED)"'; then
    log "At least one release asset sample exposed a gap."
    exit 1
  fi
}

main "$@"
