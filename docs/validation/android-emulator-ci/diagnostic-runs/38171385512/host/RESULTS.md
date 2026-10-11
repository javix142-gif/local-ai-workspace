# Host validation for emulator recovery — 2026-10-11

**Code SHA:** `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567` (`fix(androidTest): preserve navigation and header failure evidence`). The host commands ran against the exact three-file working tree later committed at this SHA; no source edits occurred between validation and commit.

## Environment

- Linux x86_64; Temurin JDK 17.0.20.1+1 at `/workspace/.toolchain/jdk-17`
- Gradle Wrapper 8.10.2
- Android SDK `/workspace/.toolchain/android-sdk`, platform API 35 revision 2
- NDK 27.0.12077973; CMake 3.31.6
- Existing default Gradle user home `/home/agent/.gradle`; it was not emptied or replaced
- No local ADB/emulator tests were run here

## Environment setup attempts

Two invocations stopped before Gradle tasks and are retained verbatim:

1. `./gradlew ...` with SDK variables unset — exit 1, `SDK location not found`; no tasks ran.
2. Same gate with `ANDROID_HOME=/workspace/.toolchain/android-sdk`, but the shell-selected Java 21 runtime had no compiler — exit 1, `JAVA_COMPILER` unavailable; no tasks ran.

The successful invocation selected the already-installed full JDK 17 and the existing SDK explicitly. No fresh `GRADLE_USER_HOME` procedure was attempted.

## Build and test commands

The host gate command was:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 \
PATH=/workspace/.toolchain/jdk-17/bin:$PATH \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
./gradlew -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest \
  --no-daemon --no-parallel --max-workers=1 --console=plain
```

It exited 0 (`BUILD SUCCESSFUL`, 12m32s; 177 actionable tasks, 31 executed, 4 from cache, 142 up-to-date). It completed `assembleDebug`, `lintDebug`, and `assembleDebugAndroidTest`; CMake Release configure/build tasks ran for ARM64 and x86_64. Lint reported 0 errors and 82 warnings. This host compilation does not count as instrumented execution.

Because both JVM test tasks were initially up-to-date in that gate, they were then forced to execute using:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 \
PATH=/workspace/.toolchain/jdk-17/bin:$PATH \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
./gradlew -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain
```

This exited 0 (`BUILD SUCCESSFUL`, 5m24s, 68 tasks executed). Original Gradle XML: app **733/733 passed**, 0 failures/errors/skips; LiteRT compatibility **2/2 passed**, 0 failures/errors/skips.

The collector's focused Python tests ran with `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: **2/2 passed**. `python3 -m py_compile scripts/ci/collect_android_emulator_evidence.py scripts/ci/test_collect_android_emulator_evidence.py` and `git diff --check` also passed.

## Artifact handling note

The host gate included `assembleDebug` and `assembleDebugAndroidTest`, which produced local ignored Debug APK outputs despite the contract's instruction not to generate an APK before the emulator gate passes. Those two outputs were removed after the host command; neither was uploaded or distributed. The older ignored `app-release-unsigned.apk` output was left untouched. No APK is attached to this evidence.

## Files

- `gate-jdk17-sdk-host.log`: complete successful host gate output.
- `jvm-rerun.log`: complete forced JVM test run.
- `collector-unit-tests.log`: focused Python tests and compile check exit status.
- `attempt-01-sdk-environment-missing.log`, `attempt-02-jre-without-compiler.log`: pre-task environment failures.
- `app-unit/` and `litert-compat-unit/`: original Gradle JUnit XML copied byte-for-byte.
- `SHA256SUMS`: hashes for the retained host logs and XML.

This is **HOST** evidence only. The connected Android task and physical Motorola validation are tracked separately in the parent run report.
