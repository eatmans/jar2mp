#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Load helper functions without running the full real-world regression matrix.
# The production script's last line invokes main; strip it for these fast tests.
# shellcheck source=/dev/null
source <(sed '$d' "${ROOT_DIR}/scripts/regression/run-github-realworld-regression.sh")

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL %s: expected %s, got %s\n' "${label}" "${expected}" "${actual}" >&2
    exit 1
  fi
}

assert_equals "PASS_EXACT" "$(classify_source_artifact_gate true true true)" "exact source artifact"
assert_equals "PASS_CONTENT" "$(classify_source_artifact_gate false true false)" "content-exact source artifact"
assert_equals "WARN_DIFF" "$(classify_source_artifact_gate false false false)" "content-different source artifact"
