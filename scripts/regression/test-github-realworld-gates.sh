#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT_DIR="${ROOT_DIR}"

# Load helper functions without running the full real-world regression matrix.
helper_source="$(mktemp)"
awk '/^main "\$@"/ { exit } { print }' \
  "${TEST_ROOT_DIR}/scripts/regression/run-github-realworld-regression.sh" > "${helper_source}"
# shellcheck source=/dev/null
source "${helper_source}"
rm -f "${helper_source}"

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL %s: expected %s, got %s\n' "${label}" "${expected}" "${actual}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local file="$1"
  local needle="$2"
  local label="$3"
  if ! grep -q "${needle}" "${file}"; then
    printf 'FAIL %s: %s does not contain %s\n' "${label}" "${file}" "${needle}" >&2
    exit 1
  fi
}

assert_equals "PASS_EXACT" "$(classify_source_artifact_gate true true true)" "exact source artifact"
assert_equals "PASS_CONTENT" "$(classify_source_artifact_gate false true false)" "content-exact source artifact"
assert_equals "WARN_DIFF" "$(classify_source_artifact_gate false false false)" "content-different source artifact"
assert_equals "PASS_EXACT" "$(classify_package_record_restore_gate PASS true)" "package-record exact artifact"
assert_equals "FAIL_package-failed" "$(classify_package_record_restore_gate FAIL package-failed)" "package-record failed package"

assert_file_contains "${TEST_ROOT_DIR}/scripts/regression/run-github-realworld-regression.sh" "package_record_restore_status" "realworld package-record status column"
assert_file_contains "${TEST_ROOT_DIR}/scripts/regression/run-github-release-assets-regression.sh" "package_record_restore_status" "release package-record status column"
assert_file_contains "${TEST_ROOT_DIR}/scripts/regression/run-cached-adhoc-release-assets-regression.sh" "package_record_restore_status" "cached package-record status column"
