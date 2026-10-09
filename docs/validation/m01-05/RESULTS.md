# M01-05 — Context budget and relevance deduplication

## Evidence publication correction (2026-10-09)

A GitHub tree audit of the previous evidence commit `d170173aee31a9503f5b9a9f539b797e3e202645` found that eight `.log` files listed in the checksum manifest were absent from that commit. The raw logs were still present locally as ignored files: the root `.gitignore` rules `*.log` and `docs/validation/**/*.log` had excluded them. Thus the earlier `sha256sum -c` verified the working directory, not the published Git tree. The XML and exit-code records were tracked.

The eight original log files have now been added verbatim with `git add -f`; they were recovered from this checkout and were not reconstructed or rerun. `SHA256SUMS` has been regenerated from the evidence files in this directory (excluding the manifest itself), and the complete manifest is checked against the staged/indexed tree before publication. The expected inventory is 114 files. The documentation-only follow-up did not rerun or change any test/build result. Independent audit subsequently accepted M01-05 as `VALIDADA_HOST`, limited to host evidence. Instrumented, emulator, and physical-device validation remain `NOT_RUN`.

**Host result:** `PASS` for code commit `260a3f7c6267cc5a90a9cf516356951aec0627a1` on `feat/0.5.0-skills-agents`. **Independent audit:** `VALIDADA_HOST`, host only; Android, emulator, and physical device remain `NOT_RUN`.
**Base:** `4c5fce553b007c7e565f55b389a162c9b0ecb880`.
**Date:** 2026-10-09 UTC.

M01-05 was partially present: Context Builder deduplicated equal text and packed by priority, but text alone was treated as identity. The regression run proved that this could erase separate evidence from different documents/scopes. The legacy budget path did not deduplicate repeated evidence at all; repeated copies could consume the 4096-context allowance and displace a unique lower-priority passage.

## Production changes

- Context Builder now identifies duplicates using context kind, scope, source/document/segment or message provenance, location metadata, and content fingerprint. Equal text from distinct authorized origins remains separate. Its per-source diversity cap is also scoped by context scope.
- The legacy `ContextBudgetManager` deduplicates only when scope, source, evidence ID, and exact passage text all match. Missing provenance is not guessed, and equal text with distinct source IDs is retained.
- `ContextBudgetResult` distinguishes routine `deduplicated` items from unique `budgetExcluded` items while keeping `excluded` as the combined compatibility view. Chat's budget warning now considers only actual budget exclusions.
- The user query remains intact. Tests compare the same 4096-window fixture with and without duplicates and with reversed input order; no query truncation or budget overflow is accepted.

Retrieval and embedding remain before `inferenceGate`; Context Builder OFF/legacy behavior, F1/F2 and selected-document/project isolation remain covered. No database, model/runtime, dependency, sampling, Android UI, or context-window change was made.

## Independent audit acceptance (2026-10-09)

The independent audit accepted M01-05 as `VALIDADA_HOST` for host validation only. The source review covered code commit `260a3f7c6267cc5a90a9cf516356951aec0627a1` relative to base `4c5fce553b007c7e565f55b389a162c9b0ecb880`. It verified the red reproduction (6 tests, 3 expected failures), focused post-fix XML (70/70), full JVM XML (app 733/733 and LiteRT compatibility 2/2), and host build logs. Evidence is in original evidence commit `b086adf8fad3e37d718d9c41e3499384bc83c7ef` plus the raw-log publication correction `26ab7199ebcc553b775d97d5d0c45e11eaaa388a`; the current corrected manifest hash is recorded in `docs/development/EXECUTION_STATE.json` under `latestM01_05.checksumManifestSha256`. AndroidTest was compilation only. `EMULATOR` and `PHYSICAL_DEVICE` remain `NOT_RUN`; no device inference or physical behavior is claimed.

## Red reproduction against the base

The focused regression suite was first run with production at base SHA `4c5fce553b007c7e565f55b389a162c9b0ecb880`. The offline run executed 6 tests: 3 passed, 3 failed, 0 errors, 0 skipped. The failures were the intended assertions:

1. Context Builder retained only `10-global` when the same passage existed in global, project, and separate document origins.
2. The real `ContextFoundation → ContextBuilder → ContextBundle.conversation()` path retained `E-A` but lost `E-B` from another selected document with identical text.
3. The legacy budget path included repeated `E-HIGH` copies and displaced `E-SECONDARY`.

The exact JUnit XML and the Gradle log are preserved under [`before/`](before/). An earlier attempt with incorrect imports is preserved separately as a harness-compilation failure; another online attempt stalled on an HTTPS connection and was interrupted before test execution. Neither is counted as the red reproduction.

## Focused post-fix tests

On code SHA `260a3f7c6267cc5a90a9cf516356951aec0627a1`, the focused run executed **70 tests: 70 passed, 0 failures, 0 errors, 0 skipped**. It covers the new ablation, real ContextFoundation provenance, Context Builder, budget management, and existing `SemanticContextPostfixTest` F1/F2 coverage.

Command:

```sh
export JAVA_HOME=/workspace/.toolchain/jdk-17
export ANDROID_HOME=/workspace/.toolchain/android-sdk
export ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --rerun-tasks --console=plain \
  :app:testDebugUnitTest \
  --tests com.localai.workspace.context.ContextDeduplicationAblationTest \
  --tests com.localai.workspace.context.ContextFoundationDeduplicationTest \
  --tests com.localai.workspace.domain.ContextBudgetManagerTest \
  --tests com.localai.workspace.context.ContextFoundationTest \
  --tests com.localai.workspace.semantic.v2.SemanticContextPostfixTest
```

JUnit XML: [`after/final/xml/`](after/final/xml/). Log: [`after/final/focused.log`](after/final/focused.log).

## Full host validation

Environment: Linux host; Gradle wrapper 8.10.2; `JAVA_HOME=/workspace/.toolchain/jdk-17` (Eclipse Temurin 17.0.20.1); Android SDK platform/compile/target API 35; NDK 27.0.12077973; CMake 3.31.6; build ABI `arm64-v8a`. Gradle was run offline because the first online attempt remained in `SYN-SENT` while resolving an HTTPS metadata request. No model was loaded and no Android device/emulator was used.

The full build/lint command completed with exit 0 and `BUILD SUCCESSFUL`:

```sh
export JAVA_HOME=/workspace/.toolchain/jdk-17
export ANDROID_HOME=/workspace/.toolchain/android-sdk
export ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk
./gradlew --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2048m \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true --offline --console=plain
```

The initial gate output marked JVM tests `UP-TO-DATE`, so a follow-up forced both suites without filters. That actual full JVM run also exited 0:

```sh
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --rerun-tasks \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2048m --console=plain \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest
```

Results from the regenerated JUnit XML: app **733/733 passed**, compatibility **2/2 passed**, zero failures/errors/skips. All 84 app XML files and the compatibility XML are preserved under [`after/full-tests/`](after/full-tests/).

Build/lint results: `assembleDebug` successful (also reported successful in the first same-SHA gate attempt before it was interrupted later); `assembleRelease` successful; `lintDebug` successful with 0 errors and 40 warnings; `assembleDebugAndroidTest` successful as a build task. The complete gate log is [`after/final/full-host.log`](after/final/full-host.log), and the forced full-suite log is [`after/final/full-tests.log`](after/final/full-tests.log). One interrupted first build-gate attempt is retained as `full-host-interrupted-01.log` and is not counted as a result.

AndroidTest was compiled only. Instrumented execution, emulator validation, and Motorola physical validation are **NOT_RUN**. The existing M00 Motorola prerequisite block and Android emulator CI blocker remain unchanged.

## Evidence integrity

The corrected [`SHA256SUMS`](SHA256SUMS) contains 114 entries and covers the red/green XML, all eight original logs, exit-code records, and summary. Every entry passed `sha256sum -c`; the manifest path set exactly matches the tracked evidence files, and each recovered log hash matches its value in the previous manifest. The post-push remote tree was checked against the published commit. `summary.json` identifies the exact code SHA for every current result. The original 97-task plan remains the attached source of task IDs/order/dependencies; this repository has no `ROADMAP.md`, `BACKLOG.json`, or `LOOP_RULES.md`, and none were created or reconstructed.
