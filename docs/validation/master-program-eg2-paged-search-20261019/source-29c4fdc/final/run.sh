#!/usr/bin/env bash
set +e
EVIDENCE_DIR="docs/validation/master-program-eg2-paged-search-20261019/source-29c4fdc/final"
date -u +%Y-%m-%dT%H:%M:%SZ > "$EVIDENCE_DIR/started-at.txt"
START_NS=$(date +%s%N)
timeout --signal=INT --kill-after=10s 600s env JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk GRADLE_USER_HOME=/workspace/.gradle ./gradlew --offline --no-daemon --no-parallel --max-workers=1 --console=plain --info -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.semantic.v2.SemanticContextPostfixTest --tests com.localai.workspace.semantic.v2.SemanticStorageTest --rerun-tasks > "$EVIDENCE_DIR/gradle.log" 2>&1
RESULT=$?
END_NS=$(date +%s%N)
date -u +%Y-%m-%dT%H:%M:%SZ > "$EVIDENCE_DIR/finished-at.txt"
printf '%s\n' "$RESULT" > "$EVIDENCE_DIR/exit-code.txt"
printf '%s\n' "$(( (END_NS - START_NS) / 1000000000 ))" > "$EVIDENCE_DIR/duration-seconds.txt"
exit 0
