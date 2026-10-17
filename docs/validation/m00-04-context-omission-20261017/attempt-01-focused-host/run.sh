#!/usr/bin/env bash
set -u
out=docs/validation/m00-04-context-omission-20261017/attempt-01-focused-host
start_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
start_epoch=$(date +%s)
printf '%s\n' "$start_utc" > "$out/start-utc.txt"
printf 'timeout --foreground --signal=INT --kill-after=20s 300s ' > "$out/supervisor-command.txt"
cat "$out/command.txt" >> "$out/supervisor-command.txt"
set +e
timeout --foreground --signal=INT --kill-after=20s 300s env \
  JAVA_HOME=/workspace/.toolchain/jdk-17-temurin \
  PATH=/workspace/.toolchain/jdk-17-temurin/bin:$PATH \
  ANDROID_HOME=/workspace/.toolchain/android-sdk \
  ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
  GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016 \
  ./gradlew --offline --no-daemon --no-parallel --max-workers=1 --console=plain --info \
  -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin \
  :app:testDebugUnitTest \
  --tests com.localai.workspace.ui.ChatSessionPipelineTest.materialContextOmissionWarnsWhileRoutineOmissionStaysDiagnosticOnly \
  --tests com.localai.workspace.ui.ContextOmissionInspectorUiTest.materialDropReasonIsVisibleInInspectorWithoutShowingDroppedMemoryText \
  > "$out/gradle.log" 2>&1
rc=$?
end_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
end_epoch=$(date +%s)
printf '%s\n' "$end_utc" > "$out/end-utc.txt"
printf '%s\n' "$rc" > "$out/exit-code.txt"
printf '%s\n' "$((end_epoch-start_epoch))" > "$out/duration-seconds.txt"
printf 'run_finished_utc=%s exit=%s duration_seconds=%s\n' "$end_utc" "$rc" "$((end_epoch-start_epoch))"
exit "$rc"
