# M00 capture-transfer recovery — run 38182644492

**Code SHA:** `49d7ccf11a682d04ad28c400f07e5fb577e573fb`

**Branch:** `feat/0.5.0-skills-agents`
**Status:** `EMULATOR_FAIL`; stop after the one authorized run for independent audit.

## Code change covered by this run

The tested source commit `49d7ccf11a682d04ad28c400f07e5fb577e573fb` is a child of the initially observed feature-branch head `a50f0a14d6bb04c44541f815cf13ef9d1f08bd31`. Its seven changed files are `.github/workflows/android-emulator-instrumented.yml`, `.github/workflows/qa-debug-arm64-apk.yml`, `app/src/androidTest/java/com/localai/workspace/ui/ChatHeaderLayoutTest.kt`, `scripts/ci/collect_android_emulator_evidence.py`, `scripts/ci/test_collect_android_emulator_evidence.py`, `scripts/ci/test_wait_for_android_emulator_success.py`, and `scripts/ci/wait_for_android_emulator_success.py`. There were no production UI/runtime, Room schema/test, model, or dependency changes.

## Android instrumentation

GitHub Actions [run 38182644492](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644492), job `114598286399`, was triggered by push and ran from the exact code SHA above. The API 35 x86_64 AVD preflight and boot passed. ADB identified `emulator-5554`, model `sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`, display 1080×1920 at density 420 dpi.

The connected task `:app:connectedDebugAndroidTest` ran for the emulator and exited 1. Its exact command is in `artifact/summary.json` and the sanitized action log is gzip-preserved at `logs/android-job-sanitized.txt.gz` (decompress to recover its original bytes). The XML contains **16 cases: 9 PASS, 1 FAIL, 0 errors, 6 SKIPPED; 10 executed**. The failed case is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`; Room cannot find the authentic `com.localai.workspace.data.WorkspaceDatabase/1.json`. The migration test and schemas were left unchanged; no schema was fabricated.

Skipped model-dependent cases remain skipped: the two `LiteRtLmDeviceSmokeTest` cases and four `NormalChatNativeDeviceTest` cases. No model was downloaded or simulated. Both ChatHeader tests passed their separate `assertIsDisplayed()` checks for agent, conversation, and model, and retained the assertion that the model line ends before the chat body.

The collector's post-task package scan reports the application package absent. This is a post-run snapshot only; it does not override the connected task and XML evidence that instrumentation executed. No claim is made that the app remains installed after Gradle cleanup.

## Screenshot transfer and visual inspection

The new path uses `UiAutomation.takeScreenshot()` to write a PNG in the test app's cache and emits bounded 512-character base64 chunks through the dedicated `M00HeaderPng` log tag. The workflow clears/resizes the logcat buffer before tests, and the collector reconstructs only complete records. It verifies chunk set, begin/end markers, base64, PNG chunk CRCs and compressed stream, dimensions, byte count, and SHA-256 before saving the image. The old `run-as`/`/sdcard/Download` redirection is absent. The cache file is retained for the test process rather than deleted before collection.

Both transfers succeeded and both files are structurally valid 1080×1920 PNGs:

| Capture | Bytes | SHA-256 | Inspection |
|---|---:|---|---|
| `artifact/ui-diagnostics/screenshots/chat-header-large-font.png` | 45,338 | `c89f1647dab2e19721761744ef5931c1134aac170d9ffac3c062338b9512bd3f` | Shows the three header lines and app bar. |
| `artifact/ui-diagnostics/screenshots/chat-header-standard.png` | 11,720 | `b80a24417e71289a59d86941b5d7d603f0c42a5ce408bd8fdbb703cdb6fd7fc6` | **Not representative:** visually almost entirely white, with status/navigation bars but no header text. |

Thus the transfer protocol is demonstrated, but the standard screenshot does not meet the requirement to represent the tested header. No visual pass is claimed for that capture. The cause of the first/standard screenshot's blank content is not determined from this run; no UI/layout production code was changed. The one-run allowance is consumed, so this report records the gap rather than treating the image as valid evidence.

The Compose diagnostics remain intact. Standard scale 1.0: app bar `(0,0)-(1080,294)`, agent `(43,94)-(1053,168)`, conversation `(43,168)-(1053,221)`, model `(43,221)-(1053,263)`, body starts at y=294, status inset 63 px. Large scale requested 1.6: app bar `(0,0)-(1080,407)`, agent `(43,110)-(1041,206)`, conversation `(43,206)-(1041,292)`, model `(43,292)-(1041,360)`, body starts at y=407, status inset 63 px. The actual Compose tree and pixel images are separately preserved; semantics bounds are not substituted for the failed standard screenshot.

## Room v1 source search

The one additional bounded public GitHub lookup for an authentic Room v1 export found no source. The repository exposes `feat/0.5.0-skills-agents` and `main` plus tag `v0.4.2`; a path-specific commit lookup for `app/schemas/com.localai.workspace.data.WorkspaceDatabase/1.json` returned zero commits on both branches. A recursive tree/direct content lookup found only `WorkspaceDatabase/8.json` on the feature branch and no v1 JSON on `main`; direct `1.json` content lookup returned not found. The previously documented local reachable/unreachable-object scan was not repeated. Classification remains `ENVIRONMENT_UNAVAILABLE`; no test or schema was altered.

## QA ARM64 workflow gate

The same push triggered [QA run 38182644499](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644499), job `114598286222`, on the same source SHA. Its exact-SHA gate observed Android run `38182644492` concluding `failure` and failed closed with `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`. JDK setup and the APK build step were skipped; the upload step was skipped. GitHub's artifacts endpoint reports `total_count: 0` for this QA run. No QA APK was built or uploaded by this run. The older QA artifact from run `38178580941` was not downloaded, installed, distributed, or deleted.

## Artifact and host checks

The original emulator evidence archive is `artifact/actions-artifact.zip`, 84,293 bytes. GitHub Actions API digest and the locally recalculated SHA-256 both equal `25fdb63d4bc051abbde7cc6b4a2a0dd164834472492ca1bcceb0e036de67e7dc`. All 32 non-manifest payload files match the artifact's internal `SHA256SUMS`; the ZIP CRC check passed. Original summary is at `artifact/summary.json`. The exact XML bytes are retained in the downloaded archive and in `artifact/instrumented-xml/TEST-emulator-5554-15-app.xml.gz`; gzip decompression reproduces the original XML SHA-256 `a5c49e9873c7bbf6f3bf94bb4eb73a1430ea3f9e91b00432d5517ae2168d6355`. The compressed XML SHA-256 is `77437afc9b748cdc9197c62de5e810c87c071c4e3876b1695354daae6ada4855`.

On the local host, collector tests passed **7/7**, QA-gate tests **6/6**, Python syntax compilation passed, both workflow YAML files parsed, and `git diff --check` passed. With Temurin JDK 17.0.20.1+1, Gradle 8.10.2, and Android SDK at `/workspace/.toolchain/android-sdk`, `:app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain` passed with **54/54 tasks executed** in 2m53s. No APK assemble task was run locally. Commands, outputs, environment, XML/logs (gzip-compressed byte-for-byte where raw outputs had trailing whitespace), screenshots, artifact API metadata, and hashes are preserved in this directory. The AndroidTest Kotlin compile log is `host/compileDebugAndroidTestKotlin.log.gz` (compressed SHA-256 `bdf4405586dea1ee04e5c9d4d2eaf367fbc94094966b1542fc8d094e0080b4ad`; uncompressed SHA-256 `35d12b449bbd14fa7e606be284a291f4e72505689fbd436cf1179b1a773bfdf6`).

Run-scoped evidence integrity: the `SHA256SUMS` file at this directory root covers every retained file in this directory except itself; the final published checkout was checked with `sha256sum -c`. The original Actions archive checksum and all 32 payload checksums were also verified independently. `host/ci-run-status.json` records the emulator and same-SHA QA workflow outcomes.

## Validation state

- **HOST:** targeted checks and AndroidTest Kotlin compilation PASS; this is not the full JVM gate.
- **EMULATOR:** instrumentation executed; overall FAIL due Room v1 schema absence. Screenshot transfer was successful, but standard capture is not visually representative.
- **PHYSICAL_DEVICE:** `NOT_RUN`.
- **M01-06:** `PENDING_REQUIRES_DEVICE`; original 97 task IDs, order, and dependencies remain unchanged.
- **main / v0.4.2:** unchanged at `de615b772cf37a99e7e08a647c59607b271de606`.
