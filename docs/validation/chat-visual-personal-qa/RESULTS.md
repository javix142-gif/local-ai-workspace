# Chat visual refresh and personal QA ARM64 APK

Status before publication: **focused HOST test PASS; PERSONAL QA workflow prepared and awaiting its one Actions run**. This does not claim a full host gate or Android validation.

## Source and scope

- Branch: `feat/0.5.0-skills-agents`
- Base: `56af9b8b16c5b4327df24d5b23979842812c3f83`
- Product/UI code commit: `412d61ae1ff9e9da710d6cf6619a935fdb352125`
- Product/test files: `MainActivity.kt`, `ChatContent.kt`, `ContextMemoryUi.kt`, `ChatVisualSurfacesUiTest.kt`.
- The transcript and composer have clearer surfaces and spacing. The Inspector separates the percentage and estimated-token line and groups included/omitted reasons. Existing message, copy, citations, attachment, send/stop, and diagnostic callbacks remain. No routing, model/inference, retrieval, permissions, persistence/Room, schema, dependency, or navigation behavior was changed.
- The existing fail-closed QA and Android instrumentation workflows were not modified. A separate PERSONAL QA workflow is prepared at `.github/workflows/qa-personal-debug-arm64.yml`; it does not invoke instrumentation.

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

## PERSONAL QA workflow

The new workflow is push-filtered to this workflow/UI change and also supports `workflow_dispatch`. It has `contents: read`, pinned official actions, and 30-day artifact retention. It invokes the existing ARM64 Debug QA build/verification script, then checks APK ZIP CRC, `zipalign`, Debug signature, package/version, and ARM64-only native libraries. The workflow checks APK ZIP CRC, `zipalign`, and that no model-weight suffix is present. Its report must say `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`, with instrumentation, emulator, and device `NOT_RUN`.

The workflow is **not yet run** at this record. Run/artifact IDs, download URL, APK/bundle hashes and certificate digest are pending GitHub Actions readback. No APK has been built locally or committed.

## Separate validation state

- HOST focused UI test: **PASS 1/1**.
- Full JVM / lint / local APK: **NOT_RUN**.
- EMULATOR: **NOT_RUN in this contract**; the previously recorded global Room v1 failure and model-dependent skips are unchanged.
- PHYSICAL_DEVICE: **NOT_RUN**.
- UX-02e remains `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`; M00-04 remains not accepted; M01-06 remains `PENDING_REQUIRES_DEVICE`. The original 97 task IDs, ordering, dependencies and progress were not modified.
