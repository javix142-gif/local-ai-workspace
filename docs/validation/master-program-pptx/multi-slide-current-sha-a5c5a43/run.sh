#!/usr/bin/env bash
set +e
EVIDENCE_DIR="docs/validation/master-program-pptx/multi-slide-current-sha-a5c5a43"
date -u +%Y-%m-%dT%H:%M:%SZ > "$EVIDENCE_DIR/started-utc.txt"
timeout --signal=INT --kill-after=10s 300s env JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk GRADLE_USER_HOME=/workspace/.gradle ./gradlew --offline --no-daemon --no-parallel --max-workers=1 --console=plain --info -I docs/validation/master-program-pptx/multi-slide-current-sha-a5c5a43/test-events.init.gradle -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.artifacts.PptxImageArtifactTest > "$EVIDENCE_DIR/gradle.log" 2>&1
command_exit=$?
printf '%s\n' "$command_exit" > "$EVIDENCE_DIR/exit-code.txt"
date -u +%Y-%m-%dT%H:%M:%SZ > "$EVIDENCE_DIR/finished-utc.txt"
exit "$command_exit"
