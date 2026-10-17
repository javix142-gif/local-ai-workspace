# Preparatory M00-06 — XLSX import and EG1 index timing correlation

## Scope and source task

The authoritative backlog entry is M00-06, “Instrumentar latencias XLSX y chat”: `TODO`, `depends_on=[]`, `requires_device=false`. Its DoD requires a real timeline spanning import/parse/index, memory retrieval, context, generation gate, model/TTFT/decode and end-to-end timings, while keeping private content out of diagnostics. The original backlog and roadmap were not edited.

This increment closes one measured gap in the production path: import diagnostics showed the post-import callback duration, and EG1 showed background indexing duration, but the inspector could not identify which index run belonged to the import. The change carries a random opaque UUID through the existing import callback into the actual scheduled `SemanticRetrievalService.indexProject` run and exposes the correlation and its state in the existing Performance screen.

No database/schema, inference, retrieval ranking/scope, model runtime, permissions, dependencies, files or task statuses changed. The full M00-06 DoD is **not complete**: this increment validates import-to-EG1 correlation with a fixed synthetic embedding driver; it does not benchmark Gemma or join the remaining memory/context/generation stages into one measured on-device request.

## Implementation

Code commit: `9dc50102f47d551d9d3d0298c65106e802d1021c`
Tested Git tree: `daa6c615534b445ef37196fabdde6242cb99f1fa`
Base code: `b48338c557a94351157de18378a5221d00a4ba43`
Published branch: `feat/0.5.0-skills-agents`.

Changed source/test files:

- `app/src/main/java/com/localai/workspace/data/DocumentIngestion.kt`
- `app/src/main/java/com/localai/workspace/semantic/SemanticRetrievalService.kt`
- `app/src/main/java/com/localai/workspace/AppGraph.kt`
- `app/src/main/java/com/localai/workspace/ui/PerformanceScreen.kt`
- `app/src/test/java/com/localai/workspace/data/DocumentPipelineTimingTest.kt`
- `app/src/test/java/com/localai/workspace/semantic/v2/SemanticContextPostfixTest.kt`

A correlation ID is exposed only when the callback confirms that an EG1 run was scheduled. The ID is a random UUID and contains no project/document/file identity. EG1 emits it through RUNNING, COMPLETE, CANCELLED and FAILED diagnostic states. The existing UI distinguishes the matching run from a later run that replaced the latest diagnostics. The callback duration is explicitly not presented as the background index duration.

The integration regression imports the versioned synthetic XLSX through `DocumentIngestionService`, schedules and joins the real `SemanticRetrievalService.indexProject` path using a deterministic test-only embedding provider, verifies the import timing stages and matching run IDs, and checks that diagnostic JSON excludes a synthetic private filename, local paths, content hash, project ID and cell text. It does not use the private original workbook or claim neural performance.

## HOST validation

Environment: Gradle Wrapper 8.10.2, Temurin JDK 17 Gradle runtime, Temurin JDK 21 test worker/toolchain discovery, existing Android SDK API 35 and NDK `27.0.12077973`; Gradle user home `/workspace/.gradle-master-program-20261016`; offline mode. No SDK/toolchain/dependencies were installed. The exact commands, raw logs, task outcomes, XML, timestamps and local hashes are retained under the numbered attempt directories.

Final focused run, on the exact tested tree above:

- `:app:testDebugUnitTest --tests com.localai.workspace.data.DocumentPipelineTimingTest --tests 'com.localai.workspace.semantic.v2.SemanticContextPostfixTest.editDeleteReimportKeepsProjectGenerationsScopedAndNeverServesStaleHits'`
- Exit 0, BUILD SUCCESSFUL in 50 seconds.
- JUnit: 2/2 passed, 0 failures/errors/skips. The new import/index test took 7.492 s; the existing delete/re-import callback regression took 0.923 s.
- XML and log: `attempt-05-final-focused/`.

Proportional build checks on the same production source tree:

- `:app:lintDebug :app:compileDebugAndroidTestKotlin`
- Exit 0, BUILD SUCCESSFUL in 4m50s.
- Lint: 0 errors, 77 warnings and 1 informational finding; none in the changed files.
- AndroidTest Kotlin compilation passed; no instrumentation ran.
- Gradle ran dependent CMake release configure/build tasks for arm64-v8a and x86_64. No APK packaging task ran and no APK is claimed.
- Evidence: `attempt-03-lint-androidtest-compile/`.

An initial test compile attempt failed before executing tests because the new test omitted `kotlinx.coroutines.launch`; that test-only import was added and the failure is preserved under `attempt-01`. A subsequent 1/1 intermediate pass is preserved under `attempt-02`. The final 2/2 run includes the strengthened assertion that excludes the synthetic cell text. A selected stale-generation callback regression also passed separately in `attempt-04`; its XML was superseded by the exact final XML in attempt-05 and is not claimed separately by XML.

Full application JVM suite and LiteRT compatibility suite: **NOT_RUN**. Exact-SHA connected Android instrumentation was attempted and is **FAIL_INCOMPLETE_INSTRUMENTATION** as detailed below. No APK was generated. E2B latency, quality, tokens/s, RAM, thermal and energy measurements: **NOT_MEASURED**.

## Same-SHA automation readback

For code SHA `9dc50102f47d551d9d3d0298c65106e802d1021c`, the existing Android workflow run `38997371699` finished **FAIL / INCOMPLETE_INSTRUMENTATION** on API 35 / Android 15 / x86_64. The AVD booted, ADB connected, and the workflow executed `:app:connectedDebugAndroidTest` (exit 1). Run: <https://github.com/javix142-gif/local-ai-workspace/actions/runs/38997371699>; job `117292766307`.

The XML artifact reports 7 cases: 3 passed, 2 failed, 0 errors and 2 skipped (5 executed). The Room migration fails because the authentic `WorkspaceDatabase/1.json` is unavailable. The two LiteRT cases were skipped by their real-model assumptions. The other failure is `ChatHeaderLayoutTest.largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible`; its XML failure element is empty. The filtered TestRunner log shows that method started and finished but has no failure event/stack for it. Its Compose capture and bounds show the three lines and no overlap, but those visuals do not override the recorded test failure.

This run's instrumentation output is incomplete and must not be counted as a complete emulator gate: Gradle reports `Expected 16 tests, received 6`; progress output reached 9/16; the sanitized logcat contains 10 TEST_START, 9 TEST_FINISHED, 1 failure event and 2 model assumption skips; the single XML contains only 7 cases and omits two tests logged as finished, while a final ContextMemory test has a start with no finish. The collector's internally consistent XML counts therefore do not reconcile with the connected task. `ChatHeaderLayoutTest.kt` is byte-identical to the parent source and no ChatHeader or AndroidTest file changed in `9dc5010`; the cause of this empty header failure and the incomplete run remains **NOT_DETERMINED**, and is not attributed to the import/index implementation.

Both captures are valid 1080x1920 PNGs and were visually inspected from the downloaded artifact: standard font SHA-256 `214f9e93a5f68d47b1e2825fcf0bc4727f91e0d60073c024746673397441084f` (47,792 bytes), Compose font scale 1.6 SHA-256 `648e95eee679b74cd1e415fd9e704a203fc1a336e1a3b8ad0c45cf39d78a8469` (82,777 bytes). They are emulator evidence, not Motorola validation.

Artifact `android-emulator-evidence-38997371699-1` (ID `12087494744`) is 149,528 bytes, SHA-256 `4c03ee8cc624ee2ae905e58743c9cd9c8e95630c06b2db0d99ba86885f65f4fa`, expires 2026-10-24T03:18:36Z. Local ZIP CRC passed and all 32 internal manifest entries verified. A copy and extraction are preserved under `automatic-ci-38997371699/`; `RUN_READBACK.json` reconciles the raw job, XML, progress and capture details.

The same-SHA official QA workflow `38997371673` failed closed at the exact-SHA instrumentation check (`ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`); APK build and upload were skipped and the run has zero artifacts. No APK was generated for this increment. This was not an Android PASS. `PHYSICAL_DEVICE=NOT_RUN` and neural/device metrics remain unmeasured.

The historical Room-v1 blocker is unchanged. The new header result is recorded separately as an unexplained incomplete-run test result, not as a confirmed product regression. The source backlog remains byte-identical: 97 unique IDs, 120 dependencies and all 97 statuses `TODO`; source hashes remain unchanged. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

The retained evidence payload is covered by `SHA256SUMS`: 112 files, manifest SHA-256 `9aa2e8cba179599fa8a6ba95f125b7824fce345b41c99ded3487f4523f47ed75`. The top-level `RESULTS.md` and its manifest are excluded from the payload list to avoid a checksum cycle. Local `sha256sum -c` completed successfully for every listed file, including the downloaded Actions ZIP and the extracted 32-entry artifact manifest.
