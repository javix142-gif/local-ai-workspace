# M00 standard Compose screenshot recovery

**Code SHA:** `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`
**Parent:** `93409371bac7e6f80d968f7ef084271d474857ac`
**Branch:** `feat/0.5.0-skills-agents`
**Scope:** test-only capture source change plus evidence. No production UI, Room, workflow, product, runtime or backlog changes.

## Change and visual result

The header test previously synchronized Compose and passed its visibility/bounds assertions, then captured the display surface through `UiAutomation.takeScreenshot()`. The retained standard image from run 38182644492 was almost entirely white despite the rendered Compose semantics. This revision captures `compose.onRoot(useUnmergedTree = true).captureToImage()` after `waitForIdle()` and converts that image to Android Bitmap for the existing bounded logcat PNG transport. The three independent `assertIsDisplayed()` checks, bounds/insets, font-scale checks, and model-before-body assertion remain unchanged.

I opened both unmodified PNGs from the new Actions artifact. **Both show the three header lines.** The standard screenshot now contains the header; the large-font screenshot does too. This demonstrates that the synchronized Compose-root capture yields representative images; it does not establish the platform-level reason the older UiAutomation capture was blank.

| Capture | Resolution / bytes | SHA-256 | Visual inspection |
|---|---:|---|---|
| [Standard PNG](screenshots/chat-header-standard.png) | 1080×1920 / 39,013 | `9ecef80b3f3f7bf2fa5f7e6981374bfd741efc33863c8ed6e7afca4675a0c4f2` | Three lines visible; conversation title ellipsized |
| [Large-font PNG](screenshots/chat-header-large-font.png) | 1080×1920 / 46,707 | `47b64cb847d1ba456a733c26b8ecd333b1e959636dd59e87ad0933598214477c` | Three lines visible at Compose font scale 1.6 |

The test log records root bounds `(0,0)-(1080,1920)`, device screen 1080×1920, 420 dpi, status inset 63 px, standard app bar bottom/model bottom/body top 294/263/294 px, and large-font values 407/360/407 px. The model line ends before the body in both cases. Full sanitized Compose semantics/logcat output is in [the original logcat payload](artifact/extracted/logs/logcat-filtered-sanitized.txt.gz); the full unmodified artifact is in `artifact/actions-artifact.zip`; the capture report is [screenshot-pull.json](artifact/extracted/screenshot-pull.json).

## Host checks

- Collector tests: `python3 scripts/ci/test_collect_android_emulator_evidence.py` — **7/7 PASS**, exit 0; [log](host/collector-tests.log).
- AndroidTest source compile: `./gradlew :app:compileDebugAndroidTestKotlin -Parm64Only=true --no-daemon --no-parallel --max-workers=1 --console=plain` with `JAVA_HOME=/workspace/.toolchain/jdk-17` and `ANDROID_HOME=ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk` — **BUILD SUCCESSFUL**, 54 tasks (2 executed, 52 up-to-date), exit 0; [log](host/compileDebugAndroidTestKotlin-final.log.gz).
- The first two local invocations stopped at environment setup: first SDK location was unset; second used a Java 21 runtime without `javac`. Re-running with the installed Android SDK and Temurin JDK 17 resolved both environment issues. Details are in [host-results.json](host/host-results.json).
- `git diff --check` was run before the code commit and returned 0. No `assembleDebug`, `assembleDebugAndroidTest`, APK build, full JVM suite, or local instrumentation was run.

## One authorized emulator run

[Run 38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844), job `114608571077`, was the single push-triggered run for the exact code SHA above. API 35 / Android 15 / x86_64 emulator `emulator-5554` and ADB passed. `:app:connectedDebugAndroidTest` ran and exited 1. The JUnit XML reports **16 total: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed**. Both `ChatHeaderLayoutTest` cases passed all three independent visibility assertions and the model-before-body bounds assertion.

The sole JUnit failure remains `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`: authentic `WorkspaceDatabase/1.json` is unavailable. The schema and migration test are unchanged; this remains `ENVIRONMENT_UNAVAILABLE`. Two LiteRT tests and four Gemma-native tests remain skipped by their actual-model guards; no models were downloaded or simulated. The XML is [here](artifact/extracted/instrumented-xml/TEST-emulator-5554%20-%2015-_app-.xml.gz); the original Gradle output is [here](artifact/extracted/logs/gradle-connected.log.gz).

The collector summary also records `applicationApkInstalled=false` in its post-test package inventory and `connectedTaskSucceeded=false` because Gradle returned 1. The Gradle log says `Starting 16 tests on emulator-5554 - 15`, and the XML has 16 cases, so instrumentation did execute; the post-task package snapshot is not treated as evidence that installation failed before execution. The workflow remains **EMULATOR: FAIL**, not PASS.

Actions artifact [11720321131](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844/artifacts/11720321131): 108,492 bytes, SHA-256 `0ef4c876e0e275200000085434f6c8a01d648db7afab4eecada7b6e8dbdf709c`. Its 32 payload-file checksums and ZIP integrity were verified; see [verification.log](artifact/verification.log), [artifact API metadata](artifact-api.json), and the retained original ZIP [actions-artifact.zip](artifact/actions-artifact.zip).

## QA gate and remaining status

The push also started QA gate run [38186082135](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186082135), job `114608572934`, for the same SHA. It observed `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; JDK setup, APK build, and artifact upload were skipped. GitHub reports zero QA artifacts. No APK was generated or distributed by this task. See [qa-gate.json](qa-gate.json).

- **HOST:** collector 7/7 and AndroidTest Kotlin compile PASS; full JVM suite NOT_RUN.
- **EMULATOR:** FAIL (one Room migration failure; screenshots visually validated; six model-dependent cases SKIPPED).
- **PHYSICAL_DEVICE:** NOT_RUN. Motorola behavior is not inferred. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged. `main` and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.
