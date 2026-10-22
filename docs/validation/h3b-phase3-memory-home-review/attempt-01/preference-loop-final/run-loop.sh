#!/usr/bin/env bash
set -u

repo_root="$(git rev-parse --show-toplevel)"
evidence_dir="$repo_root/docs/validation/h3b-phase3-memory-home-review/attempt-01/preference-loop-final"
xml_source="$repo_root/app/build/test-results/testDebugUnitTest/TEST-com.localai.workspace.h1acceptance.H1PreferenceContextTest.xml"
init_script="$repo_root/docs/validation/h3b-phase3-memory-home-review/attempt-01/preference-loop/always-run-tests.gradle"
test_name='com.localai.workspace.h1acceptance.H1PreferenceContextTest.H1_AT_01_approvedPreferencesReachTheRealGenerationRequestIncludingGreeting'
mkdir -p "$evidence_dir/xml"
printf 'iteration\tstarted_utc\tduration_seconds\texit_code\ttests\tfailures\terrors\tskipped\tresult\tlog\txml\n' > "$evidence_dir/iterations.tsv"

overall=0
for iteration in $(seq 1 20); do
  number="$(printf '%02d' "$iteration")"
  log_file="$evidence_dir/run-$number.log"
  xml_file="$evidence_dir/xml/iteration-$number.xml"
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  begin="$(date +%s)"
  previous_xml_mtime="$(stat -c %Y "$xml_source" 2>/dev/null || printf '0')"
  GRADLE_USER_HOME=/workspace/.gradle \
  JAVA_HOME=/workspace/.toolchain/jdk-17 \
  PATH=/workspace/.toolchain/jdk-17/bin:$PATH \
  ANDROID_HOME=/workspace/.toolchain/android-sdk \
  ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
    "$repo_root/gradlew" --no-daemon --no-parallel --max-workers=1 --offline --console=plain \
      -I "$init_script" \
      -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin \
      :app:testDebugUnitTest --tests "$test_name" > "$log_file" 2>&1
  rc=$?
  finish="$(date +%s)"
  current_xml_mtime="$(stat -c %Y "$xml_source" 2>/dev/null || printf '0')"
  if [[ -f "$xml_source" && "$current_xml_mtime" -gt "$previous_xml_mtime" ]]; then
    cp "$xml_source" "$xml_file"
    readout="$(python3 - "$xml_file" <<'PY'
import sys, xml.etree.ElementTree as ET
r=ET.parse(sys.argv[1]).getroot()
print(*(r.attrib.get(k, '0') for k in ('tests','failures','errors','skipped')))
PY
    )"
    read -r tests failures errors skipped <<< "$readout"
  else
    tests=0; failures=0; errors=0; skipped=0
  fi
  if [[ "$rc" -eq 0 && "$tests" -eq 1 && "$failures" -eq 0 && "$errors" -eq 0 && "$skipped" -eq 0 ]]; then
    result=PASS
  else
    result=FAIL
    overall=1
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\trun-%s.log\txml/iteration-%s.xml\n' \
    "$iteration" "$started" "$((finish-begin))" "$rc" "$tests" "$failures" "$errors" "$skipped" "$result" "$number" "$number" \
    >> "$evidence_dir/iterations.tsv"
  printf 'iteration=%s/%s result=%s exit=%s duration_seconds=%s\n' "$iteration" 20 "$result" "$rc" "$((finish-begin))"
done

exit "$overall"
