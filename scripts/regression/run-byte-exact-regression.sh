#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${ROOT_DIR}/target/byte-exact-regression"
SOURCES_DIR="${WORK_DIR}/sources"
RESTORE_DIR="${WORK_DIR}/restored"
REPORT_DIR="${WORK_DIR}/report"
JAR2MP_JAR="${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar"

MVN="${MVN:-mvn}"

log() {
  printf '[byte-exact-regression] %s\n' "$*"
}

fail() {
  printf '[byte-exact-regression] FAIL: %s\n' "$*" >&2
  exit 1
}

write_file() {
  local path="$1"
  mkdir -p "$(dirname "${path}")"
  cat > "${path}"
}

run_maven() {
  local project_dir="$1"
  (cd "${project_dir}" && "${MVN}" -q -DskipTests package)
}

jar_plugin_with_main() {
  local main_class="$1"
  cat <<XML
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>${main_class}</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
XML
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

csv_value() {
  local csv="$1"
  local column="$2"
  awk -F',' -v column="${column}" '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == column) {
          column_index = i
        }
      }
      next
    }
    NR == 2 && column_index {
      print $column_index
    }
  ' "${csv}"
}

create_plain_maven_jar() {
  local dir="${SOURCES_DIR}/plain-maven-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/plain" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>plain-maven-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.plain.PlainMain")
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/plain-message.txt" <<'TXT'
plain-resource
TXT
  write_file "${dir}/src/main/java/com/example/plain/PlainMain.java" <<'JAVA'
package com.example.plain;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlainMain {
    public static void main(String[] args) throws Exception {
        Class<?> target = Class.forName("com.example.plain.PlainTarget");
        Object value = target.getMethod("message").invoke(target.newInstance());
        try (InputStream resource = PlainMain.class.getResourceAsStream("/plain-message.txt")) {
            if (resource == null) {
                throw new IllegalStateException("missing resource");
            }
            byte[] content = readAll(resource);
            Path temp = Files.createTempFile("jar2mp-byte-exact", ".txt");
            Files.copy(new ByteArrayInputStream(content), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (InputStream file = Files.newInputStream(temp)) {
                System.out.println(value + ":" + new String(readAll(file), StandardCharsets.UTF_8).trim());
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/plain/PlainTarget.java" <<'JAVA'
package com.example.plain;

public class PlainTarget {
    public String message() {
        return "plain-target";
    }
}
JAVA
  run_maven "${dir}"
  printf '%s\n' "${dir}/target/plain-maven-jar-1.0.0.jar"
}

# Builds a JAR with lambda expressions and a string switch compiled with -g:none
# (no LineNumberTable or LocalVariableTable). Decompiling then recompiling such
# a class always produces different bytes because javac adds debug tables by
# default, so BytecodeBackfiller and ZipRecordOrderRestorer are guaranteed to
# exercise the "class bytes differ" restoration path.
create_lambda_switch_jar() {
  local dir="${SOURCES_DIR}/lambda-switch-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/lambdaswitch"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>lambda-switch-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!-- -g:none strips all debug tables; recompilation always adds them back,
                         guaranteeing different_class_bytes > 0 after the decompile step. -->
                    <compilerArgs>
                        <arg>-g:none</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
$(jar_plugin_with_main "com.example.lambdaswitch.LambdaSwitchMain")
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/java/com/example/lambdaswitch/LambdaSwitchMain.java" <<'JAVA'
package com.example.lambdaswitch;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaSwitchMain {

    public static void main(String[] args) {
        List<String> words = Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon");
        String prefix = "result-";

        // Capturing lambda + method reference: both use invokedynamic in the class file.
        List<String> tagged = words.stream()
                .filter(w -> w.length() >= 5)
                .map(String::toUpperCase)
                .map(s -> prefix + s)
                .collect(Collectors.toList());

        for (String item : tagged) {
            System.out.println(classify(item));
        }
    }

    private static String classify(String word) {
        // String switch compiles to a hashCode-based lookupswitch.
        switch (word) {
            case "result-ALPHA":   return "greek-1";
            case "result-GAMMA":   return "greek-3";
            case "result-DELTA":   return "greek-4";
            case "result-EPSILON": return "greek-5";
            default:               return "greek-other";
        }
    }
}
JAVA
  run_maven "${dir}"
  printf '%s\n' "${dir}/target/lambda-switch-jar-1.0.0.jar"
}

# Runs the fat CLI in --byte-exact-package mode against a single sample and
# asserts byte-level fidelity.
#
# Parameters:
#   $1  sample_name            - used for log messages and directory names
#   $2  sample_jar             - path to the input JAR (original)
#   $3  packaged_jar_name      - filename of the artifact Maven produces, e.g. "plain-maven-jar-1.0.0.jar"
#   $4  assert_zip_restored    - "true" to require that target/byte-exact-package-restored/ was
#                                created, proving ZipRecordOrderRestorer ran for a sample whose
#                                class bytes are known to differ after the decompile step
run_byte_exact_test() {
  local sample_name="$1"
  local sample_jar="$2"
  local packaged_jar_name="$3"
  local assert_zip_restored="${4:-false}"

  local output_base="${RESTORE_DIR}/${sample_name}"
  local cli_log="${REPORT_DIR}/${sample_name}.cli.log"
  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  log "[${sample_name}] running CLI with --byte-exact-package"
  set +e
  java -jar "${JAR2MP_JAR}" \
    --verbose \
    --byte-exact-package \
    --verify-build \
    --verify-goal package \
    -f \
    -o "${output_base}" \
    "${sample_jar}" > "${cli_log}" 2>&1
  local cli_exit=$?
  set -e
  [[ "${cli_exit}" -eq 0 ]] || {
    tail -n 40 "${cli_log}" >&2 || true
    fail "[${sample_name}] CLI exited ${cli_exit}"
  }

  local project_dir
  project_dir="$(find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1 || true)"
  [[ -n "${project_dir}" && -d "${project_dir}" ]] || \
    fail "[${sample_name}] generated project directory not found under ${output_base}"

  # For samples where decompiled class bytes are known to differ, assert that
  # ZipRecordOrderRestorer ran (its output directory is created unconditionally
  # before writing the restored artifact).
  if [[ "${assert_zip_restored}" == "true" ]]; then
    local zip_restored_dir="${project_dir}/target/byte-exact-package-restored"
    [[ -d "${zip_restored_dir}" ]] || \
      fail "[${sample_name}] target/byte-exact-package-restored/ missing - ZipRecordOrderRestorer was expected to run because original class bytes necessarily differ (compiled with -g:none)"
    log "[${sample_name}] ZipRecordOrderRestorer ran (target/byte-exact-package-restored/ exists)"
  fi

  local package_check_dir="${project_dir}/target/byte-exact-package-check"
  local summary_csv="${package_check_dir}/artifact-fidelity-summary.csv"
  local report_md="${package_check_dir}/artifact-fidelity-report.md"
  [[ -d "${package_check_dir}" ]] || \
    fail "[${sample_name}] byte-exact report directory missing: ${package_check_dir}"
  [[ -f "${summary_csv}" ]] || \
    fail "[${sample_name}] byte-exact summary CSV missing: ${summary_csv}"

  local exact_match
  local archive_bytes_same
  exact_match="$(csv_value "${summary_csv}" "exact_match")"
  archive_bytes_same="$(csv_value "${summary_csv}" "archive_bytes_same")"
  [[ "${exact_match}" == "true" ]] || \
    fail "[${sample_name}] artifact-fidelity exact_match=${exact_match:-missing}"
  [[ "${archive_bytes_same}" == "true" ]] || \
    fail "[${sample_name}] artifact-fidelity archive_bytes_same=${archive_bytes_same:-missing}"

  local packaged_artifact="${project_dir}/target/${packaged_jar_name}"
  [[ -f "${packaged_artifact}" ]] || \
    fail "[${sample_name}] packaged artifact missing: ${packaged_artifact}"

  local original_sha
  local packaged_sha
  original_sha="$(sha256_file "${sample_jar}")"
  packaged_sha="$(sha256_file "${packaged_artifact}")"
  [[ "${original_sha}" == "${packaged_sha}" ]] || \
    fail "[${sample_name}] SHA-256 mismatch: original=${original_sha} packaged=${packaged_sha}"

  log "[${sample_name}] CLI log tail (${cli_log})"
  tail -n 20 "${cli_log}"
  log "[${sample_name}] byte-exact summary (${summary_csv})"
  cat "${summary_csv}"
  if [[ -f "${report_md}" ]]; then
    log "[${sample_name}] report key lines (${report_md})"
    grep -E 'Exact match|Archive bytes same|Original archive SHA-256|Rebuilt archive SHA-256' "${report_md}" || true
  fi
  log "[${sample_name}] SHA-256 original  ${sample_jar}: ${original_sha}"
  log "[${sample_name}] SHA-256 packaged  ${packaged_artifact}: ${packaged_sha}"
  log "[${sample_name}] generated project: ${project_dir}"
  log "[${sample_name}] PASS byte-exact package"
}

main() {
  rm -rf "${WORK_DIR}"
  mkdir -p "${SOURCES_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"

  log "Building jar2mp fat CLI"
  (cd "${ROOT_DIR}" && "${MVN}" -q -DskipTests package)
  [[ -f "${JAR2MP_JAR}" ]] || fail "fat CLI jar missing: ${JAR2MP_JAR}"

  # --- Sample 1/2: plain-maven-jar ---
  # Baseline: source recompile produces class bytes identical to the original.
  # Exercises the happy path where no record-level restoration is needed.
  log "=== Sample 1/2: plain-maven-jar ==="
  local plain_jar
  plain_jar="$(create_plain_maven_jar)"
  [[ -f "${plain_jar}" ]] || fail "plain-maven-jar sample missing: ${plain_jar}"
  run_byte_exact_test "plain-maven-jar" "${plain_jar}" "plain-maven-jar-1.0.0.jar" "false"

  # --- Sample 2/2: lambda-switch-jar ---
  # Difference scenario: compiled with -g:none so decompile always yields class
  # bytes that differ from the original (recompile adds LineNumberTable /
  # LocalVariableTable). Exercises BytecodeBackfiller and ZipRecordOrderRestorer
  # and asserts that --byte-exact-package still produces a byte-identical artifact.
  log "=== Sample 2/2: lambda-switch-jar ==="
  local lambda_switch_jar
  lambda_switch_jar="$(create_lambda_switch_jar)"
  [[ -f "${lambda_switch_jar}" ]] || fail "lambda-switch-jar sample missing: ${lambda_switch_jar}"
  run_byte_exact_test "lambda-switch-jar" "${lambda_switch_jar}" "lambda-switch-jar-1.0.0.jar" "true"

  log "PASS byte-exact package regression (2/2 samples)"
}

main "$@"
