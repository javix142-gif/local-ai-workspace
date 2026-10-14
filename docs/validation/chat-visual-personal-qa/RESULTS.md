# Chat visual refresh and personal QA ARM64 APK

Status after readback: **focused HOST test PASS; PERSONAL QA Debug ARM64 artifact PASS for its stated checks**. It is not a Release or Android-device validation.

## Source and scope

- Branch: `feat/0.5.0-skills-agents`
- Base: `56af9b8b16c5b4327df24d5b23979842812c3f83`
- Product/UI code commit: `412d61ae1ff9e9da710d6cf6619a935fdb352125`
- Product/test files: `MainActivity.kt`, `ChatContent.kt`, `ContextMemoryUi.kt`, `ChatVisualSurfacesUiTest.kt`.
- The transcript and composer have clearer surfaces and spacing. The Inspector separates the percentage and estimated-token line and groups included/omitted reasons. Existing message, copy, citations, attachment, send/stop, and diagnostic callbacks remain. No routing, model/inference, retrieval, permissions, persistence/Room, schema, dependency, or navigation behavior was changed.
- The existing fail-closed QA and Android instrumentation workflows were not modified. The separate PERSONAL QA workflow at `.github/workflows/qa-personal-debug-arm64.yml` was corrected in `0844159` and its exact-SHA Actions artifact is recorded below; it does not invoke instrumentation.

## Focused HOST validation

Exact command:

```text
JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk timeout --signal=TERM --kill-after=10s 300s ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.ui.ChatVisualSurfacesUiTest
```

- Tested source: `412d61ae1ff9e9da710d6cf6619a935fdb352125`
- Gradle 8.10.2; Temurin JDK 17.0.20.1+1 runtime; JDK 21 available for toolchain discovery; preinstalled Android SDK. Offline execution; no installs or cache cleanup.
- Started `2026-10-14T00:34:36Z`; finished `2026-10-14T00:35:47Z`; Gradle exit **0**.
- XML: `tests=1`, `failures=0`, `errors=0`, `skipped=0`; the single test passed. XML timestamp: `2026-10-14T00:35:34`.
- Raw output: `focused-test-budget-reflow.log`; JUnit: `host-focused-budget-reflow-junit.xml`; environment: `host-environment-budget-reflow.txt`.
- The prior Stop assertion failure was fixed by bringing Stop into view before the click; the callback assertion remains. The current exact-code run and its session status both report exit 0, superseding the earlier run whose outer session status was inconsistent with its log/XML.

This was one focused test only. Full JVM suite, lint, local APK build, emulator, and physical-device validation were **NOT_RUN** in this contract.

## Host captures

The same passing test wrote and the executor opened two valid synthetic Compose/Robolectric PNG captures, each 411x900 at fontScale 1.6. The Inspector capture shows the percentage on its own row and the estimated-token text beneath it without overlap. These are host component captures, not full-device or Motorola screenshots.

- `screenshots/chat-context-inspector-final-budget-reflow-411dp-fontScale-1.6.png`
- `screenshots/chat-composer-generating-final-411dp-fontScale-1.6.png`

The earlier before-reflow captures remain named as historical examples; they are not represented as the final layout. `SHA256SUMS` covers the preserved logs, XML, metadata, and both historical/final PNGs.

## PERSONAL QA workflow and artifact

The separate workflow `.github/workflows/qa-personal-debug-arm64.yml` is push-filtered to its workflow/UI paths and supports `workflow_dispatch`. It uses pinned official actions, `contents: read`, and 30-day artifact retention; it does not depend on or bypass the fail-closed instrumented QA workflow.

The first run, [38491402535](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38491402535), remains **FAIL and not approved**. Its build, signing, package/version, and ABI checks passed, but the suffix scanner classified the sole match `DebugProbesKt.bin` as a model before ZIP/zipalign verification. Downloaded artifact inspection proved it is a 1,728-byte Java class file for `kotlin.coroutines.jvm.internal.DebugProbesKt`, not weights. The run log also showed the ZIP step's `ANDROID_HOME` was empty because the workflow overrode it from an empty expression. See [the exact artifact audit](failed-run-38491402535/ARTIFACT_AUDIT.txt).

Correction commit `0844159b1c8653c15da6a2f47c098e46d38478ba` keeps the full configured model-suffix rejection and permits only the root entry `DebugProbesKt.bin` when its class marker, internal class name, and exact SHA-256 `4d13bd92f86a0b1415062b8ed57655c0738ccfbdf0786c1d730d988b27fd4400` match the audited bytecode. Other suffix matches, changed content, and duplicate altered entries are rejected. It removes the empty `ANDROID_HOME` override, resolves `ANDROID_HOME`/`ANDROID_SDK_ROOT` in the step, logs the resolved SDK path, checks `zipalign` exists, then validates ZIP CRC and alignment.

The corrected push run [38492777065](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38492777065) used source/event SHA `0844159b1c8653c15da6a2f47c098e46d38478ba`; every workflow step completed successfully. The log resolved Android SDK `/usr/local/lib/android/sdk`, reported `ZIP_CRC=PASS`, classified the one exact coroutine class entry, reported `MODEL_PAYLOADS=NONE`, and completed `zipalign` successfully.

**Download the verified PERSONAL QA artifact:** [GitHub Actions artifact 11862891302](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38492777065/artifacts/11862891302). The artifact ZIP is 47,852,757 bytes, SHA-256 `836a7da89a15ccc1459e9d1ad8ce2d10141ae7b2a1a094624578bd8da6857e42`, expires `2026-11-13T01:02:36Z`; its five internal checksum entries were independently matched against the downloaded bytes. The artifact contains the APK, `RESULTS.json`, SHA256SUMS, and build/verification logs.

APK: `local-ai-workspace-0.5.0-debug-arm64.apk`, 119,445,466 bytes, SHA-256 `f3e87a33662784ac9ace0bd4a5779f59589b7bc891f058b0ccf755076fd35765`; package `com.localai.workspace.debug`, version `0.5.0-debug` / versionCode 27, `arm64-v8a` only (9 native libraries). `apksigner` v2 passed; Android Debug certificate SHA-256 `39e1d90e77864dfb824bfe716cffe3e9574441e7044db108762e8ed85923f455`. APK ZIP CRC and `zipalign -c -v 4` passed in the run and on the downloaded APK. The APK is Debug QA only, not Release or production. It is not committed to Git.

The focused checker also passed on the first run's downloaded APK after classification; synthetic `.gguf`, altered `.bin`, and duplicate-entry controls all failed closed. The first run's APK was independently ZIP/zipalign verified but remains unapproved because its workflow reported FAIL; only the artifact from run 38492777065 is the delivered one.

## Separate validation state

- HOST focused chat visual test: **PASS 1/1** at source `412d61ae1ff9e9da710d6cf6619a935fdb352125`.
- PERSONAL QA ARM64 build and artifact: **PASS** for run `38492777065`, exact workflow/source SHA `0844159b1c8653c15da6a2f47c098e46d38478ba`.
- Full JVM / lint: **NOT_RUN** in this visual contract.
- Instrumented tests: **NOT_RUN** by the personal workflow. Existing global EMULATOR status remains **FAIL** for the historical Room v1 fixture; six model-dependent cases remain skipped, and this contract did not rerun them.
- PHYSICAL_DEVICE: **NOT_RUN**; no Motorola validation is claimed.
- UX-02e remains `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`; M00-04 remains not accepted; M01-06 remains `PENDING_REQUIRES_DEVICE`. The original 97 task IDs, ordering, dependencies, and progress were not modified.
- Run-level readback and the artifact's byte-for-byte RESULTS/manifest copies are in [final run evidence](final-run-38492777065/RUN_READBACK.txt).
