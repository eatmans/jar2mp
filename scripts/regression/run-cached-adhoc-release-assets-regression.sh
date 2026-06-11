#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${ROOT_DIR}/target/adhoc-github-release-assets"
ASSET_DIR="${ASSET_DIR:-${WORK_DIR}/assets}"
RESTORE_DIR="${RESTORE_DIR:-${WORK_DIR}/restored-current}"
REPORT_DIR="${REPORT_DIR:-${WORK_DIR}/report-current}"
JAR2MP_JAR="${JAR2MP_JAR:-${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar}"
MVN="${MVN:-mvn}"
BUILD_JAR2MP="${BUILD_JAR2MP:-1}"
STRICT_CACHED_ADHOC_ASSETS="${STRICT_CACHED_ADHOC_ASSETS:-1}"

sample_names=()
sample_assets=()
sample_types=()
sample_notes=()

log() {
  printf '[cached-adhoc-assets] %s\n' "$*"
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
  local asset="$2"
  local artifact_type="$3"
  local note="$4"

  sample_names+=("${name}")
  sample_assets+=("${asset}")
  sample_types+=("${artifact_type}")
  sample_notes+=("${note}")
}

prepare_samples() {
  register_sample \
    "picocli" \
    "picocli-4.7.7.jar" \
    "CLI library JAR" \
    "Small library artifact that previously verified cleanly and guards baseline compile restoration."

  register_sample \
    "picocli-codegen" \
    "picocli-codegen-4.7.7.jar" \
    "Annotation processor / codegen JAR" \
    "Contains generated and annotation-processing code paths."

  register_sample \
    "undertow-core" \
    "undertow-core-2.4.1.Final.jar" \
    "Server library JAR" \
    "Exercises nested anonymous classes, security APIs, and raw class fallback compile stability."

  register_sample \
    "undertow-examples" \
    "undertow-examples-2.4.1.Final.jar" \
    "Example application JAR" \
    "Exercises Undertow example sources and retained support classes."

  register_sample \
    "jmx-exporter-standalone" \
    "jmx_prometheus_standalone-1.5.0.jar" \
    "Standalone monitoring JAR" \
    "Covers Kotlin-style metadata and JMX exporter release packaging."

  register_sample \
    "opentelemetry-javaagent" \
    "opentelemetry-javaagent.jar" \
    "Large Java agent JAR" \
    "Large shaded agent artifact that stresses shaded dependency fallback and raw artifact preservation."
}

parse_report_field() {
  local report="$1"
  local label="$2"
  local fallback="$3"
  [[ -f "${report}" ]] || { printf '%s' "${fallback}"; return; }
  local value
  value="$(awk -v label="${label}" -F': ' '$0 ~ "^- " label ":" {print $2; exit}' "${report}" 2>/dev/null || true)"
  printf '%s' "${value:-${fallback}}"
}

parse_raw_artifact_exact() {
  local csv="$1"
  [[ -f "${csv}" ]] || { printf 'missing'; return; }
  awk -F',' 'NR == 2 {print $1; exit}' "${csv}"
}

parse_artifact_summary_field() {
  local csv="$1"
  local column="$2"
  local fallback="$3"
  [[ -f "${csv}" ]] || { printf '%s' "${fallback}"; return; }
  local value
  value="$(awk -F',' -v column="${column}" 'NR == 2 { print $column; exit }' "${csv}" 2>/dev/null || true)"
  printf '%s' "${value:-${fallback}}"
}

parse_decompile_failure_count() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing'
  elif grep -q 'No decompilation failures detected' "${report}"; then
    printf '0'
  else
    grep -c '^- Failed to decompile ' "${report}" || true
  fi
}

package_restored_project() {
  local project_dir="$1"
  local log_file="$2"
  (cd "${project_dir}" && "${MVN}" -q package) > "${log_file}" 2>&1
}

find_packaged_artifact() {
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

write_sample_row() {
  local index="$1"
  local name="$2"
  local run_status="$3"
  local exit_code="$4"
  local verification_summary="$5"
  local verification_failure_type="$6"
  local verification_error_count="$7"
  local compile_fallback_classes="$8"
  local decompile_failures="$9"
  local raw_artifact_exact="${10}"
  local byte_exact_package_status="${11}"
  local byte_exact_package_exact="${12}"
  local project_dir="${13}"

  {
    csv_field "${name}"; printf ','
    csv_field "${run_status}"; printf ','
    csv_field "${sample_assets[${index}]}"; printf ','
    csv_field "${sample_types[${index}]}"; printf ','
    csv_field "${exit_code}"; printf ','
    csv_field "${verification_summary}"; printf ','
    csv_field "${verification_failure_type}"; printf ','
    csv_field "${verification_error_count}"; printf ','
    csv_field "${compile_fallback_classes}"; printf ','
    csv_field "${decompile_failures}"; printf ','
    csv_field "${raw_artifact_exact}"; printf ','
    csv_field "${byte_exact_package_status}"; printf ','
    csv_field "${byte_exact_package_exact}"; printf ','
    csv_field "${project_dir:-missing}"; printf ','
    csv_field "${sample_notes[${index}]}"; printf '\n'
  } >> "${REPORT_DIR}/adhoc-github-release-assets-summary.csv"

  cat >> "${REPORT_DIR}/adhoc-github-release-assets-summary.md" <<MD
| ${name} | ${run_status} | ${sample_assets[${index}]} | ${sample_types[${index}]} | ${exit_code} | ${verification_summary} | ${verification_failure_type} | ${verification_error_count} | ${compile_fallback_classes} | ${decompile_failures} | ${raw_artifact_exact} | ${byte_exact_package_status} | ${byte_exact_package_exact} |
MD
}

run_sample() {
  local index="$1"
  local name="${sample_names[${index}]}"
  local asset="${sample_assets[${index}]}"
  local asset_path="${ASSET_DIR}/${asset}"
  local output_base="${RESTORE_DIR}/${name}"
  local cli_log="${REPORT_DIR}/${name}.cli.log"

  if [[ ! -s "${asset_path}" ]]; then
    log "Missing cached asset ${asset_path}"
    write_sample_row "${index}" "${name}" "MISSING_ASSET" "not-run" \
      "not-run" "not-run" "not-run" "not-run" "not-run" "missing" "not-run" "missing" "missing"
    return
  fi

  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  log "Restoring ${name}"
  set +e
  java -jar "${JAR2MP_JAR}" \
    --verbose \
    --byte-exact-package \
    --verify-build \
    --verify-goal compile \
    -f \
    -o "${output_base}" \
    "${asset_path}" > "${cli_log}" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1 || true)"

  local verification_report="${project_dir}/verification-report.md"
  local failures_report="${project_dir}/decompile-failures.md"
  local raw_artifact_csv="${project_dir}/target/raw-artifact/artifact-fidelity-summary.csv"

  local verification_summary="missing"
  local verification_failure_type="missing"
  local verification_error_count="missing"
  local compile_fallback_classes="missing"
  local decompile_failures="missing"
  local raw_artifact_exact="missing"
  local byte_exact_package_status="not-run"
  local byte_exact_package_exact="not-run"
  local run_status="RESTORE_FAILED"

  if [[ -n "${project_dir}" ]]; then
    verification_summary="$(parse_report_field "${verification_report}" "Summary" "missing")"
    verification_failure_type="$(parse_report_field "${verification_report}" "Failure type" "missing")"
    verification_error_count="$(parse_report_field "${verification_report}" "Error count" "missing")"
    compile_fallback_classes="$(parse_report_field "${verification_report}" "Compile fallback classes" "missing")"
    decompile_failures="$(parse_decompile_failure_count "${failures_report}")"
    raw_artifact_exact="$(parse_raw_artifact_exact "${raw_artifact_csv}")"

    local packaged_artifact=""
    if package_restored_project "${project_dir}" "${REPORT_DIR}/${name}.package.log"; then
      byte_exact_package_status="PASS"
      packaged_artifact="$(find_packaged_artifact "${project_dir}" "${asset_path}")"
      if [[ -n "${packaged_artifact}" && -f "${packaged_artifact}" ]]; then
        local compare_dir="${project_dir}/target/byte-exact-package-check"
        if java -jar "${JAR2MP_JAR}" \
          --compare-artifact "${packaged_artifact}" \
          -q \
          -o "${compare_dir}" \
          "${asset_path}" > "${REPORT_DIR}/${name}.byte-exact-package.log" 2>&1; then
          byte_exact_package_exact="$(parse_artifact_summary_field "${compare_dir}/artifact-fidelity-summary.csv" 1 "missing")"
        else
          byte_exact_package_exact="compare-failed"
        fi
      else
        byte_exact_package_exact="package-missing"
      fi
    else
      byte_exact_package_status="FAIL"
      byte_exact_package_exact="package-failed"
    fi

    run_status="VERIFY_FAILED"
    if [[ "${exit_code}" -eq 0 \
      && "${verification_summary}" == "BUILD SUCCESS" \
      && "${verification_failure_type}" == "NONE" \
      && "${verification_error_count}" == "0" \
      && "${raw_artifact_exact}" == "true" \
      && "${byte_exact_package_status}" == "PASS" \
      && "${byte_exact_package_exact}" == "true" ]]; then
      run_status="PASS"
    fi
  fi

  write_sample_row "${index}" "${name}" "${run_status}" "${exit_code}" \
    "${verification_summary}" "${verification_failure_type}" "${verification_error_count}" \
    "${compile_fallback_classes}" "${decompile_failures}" "${raw_artifact_exact}" \
    "${byte_exact_package_status}" "${byte_exact_package_exact}" "${project_dir:-missing}"
}

main() {
  mkdir -p "${ASSET_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"
  rm -f "${REPORT_DIR}/adhoc-github-release-assets-summary.md" \
    "${REPORT_DIR}/adhoc-github-release-assets-summary.csv"

  if [[ "${BUILD_JAR2MP}" == "1" ]]; then
    log "Building jar2mp"
    (cd "${ROOT_DIR}" && "${MVN}" -q -DskipTests package)
  fi

  if [[ ! -s "${JAR2MP_JAR}" ]]; then
    log "Missing jar2mp artifact ${JAR2MP_JAR}"
    exit 1
  fi

  prepare_samples

  write_file "${REPORT_DIR}/adhoc-github-release-assets-summary.md" <<'MD'
# jar2mp Cached Ad-hoc GitHub Release Asset Regression Summary

This matrix replays the cached binary assets under `target/adhoc-github-release-assets/assets/`. It does not download artifacts. `PASS` requires jar2mp exit code `0`, Maven verification `BUILD SUCCESS`, `Failure type: NONE`, `Error count: 0`, raw artifact exact match `true`, and generated Maven `package` output exact match `true`.

| Sample | Status | Asset | Artifact type | Exit code | Verification summary | Failure type | Error count | Compile fallback classes | Decompile failures | Raw exact | Byte-exact package | Byte-exact package exact |
| --- | --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | --- | --- | --- |
MD

  write_file "${REPORT_DIR}/adhoc-github-release-assets-summary.csv" <<'CSV'
sample,status,asset,artifact_type,exit_code,verification_summary,verification_failure_type,verification_error_count,compile_fallback_classes,decompile_failures,raw_artifact_exact,byte_exact_package_status,byte_exact_package_exact,project_dir,note
CSV

  local total="${#sample_names[@]}"
  local index
  for ((index = 0; index < total; index++)); do
    run_sample "${index}"
  done

  log "Summary: ${REPORT_DIR}/adhoc-github-release-assets-summary.md"
  log "CSV: ${REPORT_DIR}/adhoc-github-release-assets-summary.csv"

  local pass_count
  pass_count="$(awk -F',' 'NR > 1 && $2 == "\"PASS\"" { count++ } END { print count + 0 }' "${REPORT_DIR}/adhoc-github-release-assets-summary.csv")"
  if [[ "${pass_count}" -eq 0 ]]; then
    log "No cached ad-hoc release asset samples passed."
    exit 1
  fi

  if [[ "${STRICT_CACHED_ADHOC_ASSETS}" == "1" ]] \
    && grep -Ev '^sample,' "${REPORT_DIR}/adhoc-github-release-assets-summary.csv" \
    | grep -vq ',"PASS",'; then
    log "At least one cached ad-hoc release asset sample failed."
    exit 1
  fi
}

main "$@"
