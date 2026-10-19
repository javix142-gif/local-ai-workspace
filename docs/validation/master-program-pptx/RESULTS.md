# Preparatory M03-05 — bounded PNG images in generated PPTX

**Code SHA:** `6231732ad8557465abfc8155925fdcbc90d1eb87`
**Status:** partial HOST evidence; the interrupted Gradle invocation has no observed exit code. No formal backlog task is closed.

## Change

The PPTX descriptor can now carry one user-supplied PNG and required alternative text on a slide. The implementation validates canonical Base64, PNG structure and chunk CRCs, decoded dimensions/pixel count and byte bounds before embedding the actual PNG bytes. The renderer adds the PNG content type and media part, slide relationship and a picture shape with alternative text and locked aspect ratio. It fits the image in a bounded area beside the body and preserves the generated layout, master and theme. No image is generated, downloaded or read from a path/URL.

At this initial increment the artifact preview was text/structure only and not a slide canvas; the 2026-10-19 follow-up below adds an approximate slide schematic. The original artifact and prior immutable revisions are not overwritten.

## Host evidence

- **Attempt 05** ran only `PptxImageArtifactTest` with Gradle 8.10.2, Temurin 17 runtime, Temurin 21 worker, API 35/NDK 27.0.12077973 and the existing offline Gradle cache. The exec session was aborted by the user while Gradle continued in the background. Its JUnit XML records 2 tests, 2 passed, 0 failures/errors/skips. The shell/Gradle exit code and a final `BUILD SUCCESSFUL` marker were not observed. The invocation began with Git HEAD `58a63b2`; its dirty production/test tree was later committed as `6231732`, but no separate source-tree hash was captured at invocation. Treat the XML as evidence that the two test methods ran and passed, not as a complete Gradle-command PASS.
- **Attempt 06** on committed code `6231732` exited 0 and printed `BUILD SUCCESSFUL`, but all 63 tasks were `UP-TO-DATE`, including `testDebugUnitTest`; it executed zero tests. The copied XML in that directory is labeled as coming from attempt 05 and is not counted for attempt 06.
- The generated fixture `fixture-v1.pptx` is 5,464 bytes, SHA-256 `794ffc02dd8acfa650bbf660e2fd44b0580bcb6ae384c2463d838cacc04ca4c5`. The independent `python-pptx`/ZIP reader record reports valid ZIP CRC, one slide, Blank layout, one master, theme part, one picture, alt text `Mapa & datos`, and the embedded 2×1 PNG. That reader ran in attempt 04; the package hash matches the generated final fixture, and valid-image renderer output did not change after it. This is package-structure evidence, not Android viewer validation.

### Attempt ledger

| Attempt | Outcome | Counted test result |
|---|---|---|
| 01 red | Test harness compile failure (`NodeList.length`); no behavioral reproduction | 0 tests |
| 02 red | Expected feature gap: new PPTX image fields rejected as `ARTIFACT_FIELD_UNSUPPORTED` | 1 failure |
| 03 | Test harness compile failure (missing JUnit `fail` import); accompanying XML is stale from attempt 02 | 0 tests |
| 04 | Two image tests failed on test/assertion issues; ten API 29 roundtrip cases failed before test bodies during Robolectric artifact bootstrap | 12 failures, not a production conclusion |
| 05 | User aborted exec session; later JUnit XML records both final focused methods passing, but Gradle exit and final log were not observed | 2/2 XML PASS; command completion unverified |
| 06 | Gradle exited 0, all tasks including test were `UP-TO-DATE` | 0 tests; not a test PASS |

The exact commands and attempt artifacts are kept in their respective directories. XML copies in attempts 03 and 06 are explicitly marked as stale/copied and are not counted for those runs.

Earlier failed attempts are retained under `attempt-01` through `attempt-04`. In particular, an API 29 `ArtifactRoundtripTest` run failed before its test bodies because Robolectric could not fetch `android-all-instrumented:10-robolectric-5803371-i7`; do not attribute those bootstrap failures to PPTX production behavior.

No local full JVM suite, LiteRT compatibility suite, lint, AndroidTest-only compile, Motorola test or APK was run/generated for this increment. The push did trigger one GitHub-hosted emulator run; its outcome is recorded below. The compatible update-signing key is not available in this executor; no replacement key was created.

## Scope and source plan

The original source task is **M03-05 — Generación/edición básica PPTX**, still `TODO`, `requires_device=true`, and dependent on M03-01, which also remains `TODO`. The DoD additionally requires reader compatibility, preview and roundtrip. This increment supplies and validates a bounded image component of generated PPTX structure only; it does not fulfill the complete DoD. The preserved backlog remains 97 unique IDs and 120 dependency edges, all source statuses `TODO`.

The work changes no model/runtime, retrieval, Room schema, permissions, dependency, workflow, or original plan file. The latest EMULATOR result is detailed below; PHYSICAL_DEVICE remains `NOT_RUN`. Native latency, throughput, memory and thermal performance remain unmeasured on the Moto G86.

## Reproduction and integrity

Attempt commands, losslessly compressed Gradle logs, timestamps, JUnit XML, independent-reader output, and attempt outcomes are stored beside this report. `SHA256SUMS` covers the files under this evidence directory except itself; its own SHA-256 is recorded outside the manifest in `EXECUTION_STATE.json` and the handoff.

## Automatic Android CI follow-up — run 38988880055

The push of evidence commit `06bd56e84a4a8dab2a907d136245e68761d75430` triggered the existing API 35 emulator workflow for product code `6231732ad8557465abfc8155925fdcbc90d1eb87`. The AVD booted and ADB connected on API 35 / x86_64. `:app:connectedDebugAndroidTest` ran and exited 1. Its JUnit XML reports 16 cases: 9 passed, 1 failed, 0 errors, 6 skipped (10 executed). The only failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`, which cannot load authentic `WorkspaceDatabase/1.json`; no schema was invented and the test was not changed. Six model-dependent tests were skipped; this run downloaded no models. The collector's post-task package-installed check was false, with cause not determined; it does not negate the executed XML. The run is **EMULATOR FAIL**, not Android PASS.

The same-SHA official QA gate run `38988880049` failed closed with `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; APK build and upload were skipped and the API lists no artifact. No APK was generated by these runs. The original 151,505-byte emulator artifact is preserved at [`automatic-ci-38988880055/github-artifact-12084179250.zip`](automatic-ci-38988880055/github-artifact-12084179250.zip), SHA-256 `cec74c1ae48df16f49e5910b4e882d39653a3dc80a43682f5956b173d2a14013`; the internal payload checksum manifest verifies. The QA job log, run metadata, counts and checksums are in [`automatic-ci-38988880055/`](automatic-ci-38988880055/).

Run evidence does not complete M03-05. The task and M03-01 dependency remain source `TODO`, and physical-device validation remains `NOT_RUN`. Detailed outcomes are in [the automatic CI follow-up](automatic-ci-38988880055/RESULTS.md).

## Preview follow-up — image identity and supplied-asset presentation (2026-10-17)

Code `b5222c0a388777ee4570fb40290a9e760266efe1` closes a concrete gap in the PPTX draft preview. The text/structure preview now includes the validated PNG bytes' SHA-256 beside alt text, without showing Base64. The artifact dialog shows each caller-supplied PNG itself with slide identity, alt text, and source dimensions; it explicitly says that the slide layout is not rendered. Parsing and decoding are off the main thread. Decoded preview memory is bounded to 65,536 pixels per image and 1,000,000 total, and previewing leaves stored bytes and immutable history unchanged.

The red regression at parent `998769a954c21db2641694d478e148eec073d736` failed 1/1 as expected because distinct image bytes produced identical textual previews. On the final code tree, the focused tests passed 4/4 (PptxImageArtifactTest 3/3, PptxImagePreviewUiTest 1/1; exit 0). `lintDebug` passed with 77 warnings, 1 informational finding, 0 errors, none in changed files; `compileDebugAndroidTestKotlin` passed as compilation only. Full JVM/LiteRT, connected instrumentation, emulator, physical device, APK and performance measurements were not run for this follow-up. Commands, logs, XML and checksums are in [the preview follow-up evidence](preview-follow-up/RESULTS.md). Its 40-entry manifest SHA-256 is `6b97bb107b3168874b7fa95afd6e102c0d7dc3547891c033aaf73bf9b0001b53`.

The original M03-05 remains `TODO`, requires a device, and depends on M03-01 (`TODO`). The image is previewed as an asset, not as a rendered slide; no WYSIWYG/reader/device claim is made. The previous global emulator result remains separately classified as FAIL for the authentic Room-v1 fixture and six model-dependent skips; it predates this follow-up. The source backlog remains 97 unique IDs / 120 dependencies / 97 TODO statuses.

## Slide-layout preview follow-up — 2026-10-19

The earlier statements above that the dialog had no slide-layout preview describe the older code at those historical SHAs. Code `375e45035f63576b17c1111ce738720373100b99` now adds a clearly labeled, approximate 16:9 Compose schematic for every saved slide. It uses the stored title/body and shows a labeled image-placement area; the actual supplied PNG is rendered separately below the schematic with its existing accessibility label and dimensions. Text zones scroll instead of silently truncating. The notice is explicit: this is **not a rendered PowerPoint page**.

A test-first regression at `e50f28b1dd0a392deaa777ba22e488bb3424fce4` ran and failed 1/1 because the slide-layout node did not exist. The final exact-SHA test run at `375e45035f63576b17c1111ce738720373100b99` ran `PptxImagePreviewUiTest` 1/1 PASS (Gradle exit 0, `BUILD SUCCESSFUL`, 7 tasks executed, 56 up-to-date). It verifies a two-slide synthetic deck, title/body for text and image slides, image region/alt text, actual supplied PNG accessibility and visibility, source dimensions/hash, and no mutation of saved artifact bytes or revision history. The full command, JDK/SDK/NDK paths, start/end timestamps, log, task outcomes and JUnit XML are in [`layout-preview/RESULTS.md`](layout-preview/RESULTS.md); its 58-entry `SHA256SUMS` covers its evidence files (manifest SHA-256 `4c33a41952ad2718d3048153e9c7ccf0a4b68dc83f2095e9abd4679d382e1a69`).

The implementation is a schematic only. It does not rasterize OOXML, emulate PowerPoint typography/line wrapping, or claim Android/Office viewer fidelity. This increment ran no full JVM/LiteRT gate, lint, AndroidTest compilation, connected instrumentation, emulator, physical device or APK. The original M03-05 status and dependency remain `TODO`; no backlog task is closed. The catalog remains 97 unique task IDs, 120 dependency edges and 97 `TODO` statuses.


## Current-source PPTX image roundtrip — 2026-10-19

At checkout SHA `95199cd3f7f5703bf436de4214cb42e4721e2787` (product code SHA `9f99beba0d38787a62e83bbfa457cc39b9e9d0ab`), `PptxImageArtifactTest` was forced to execute: **3/3 PASS**, 0 failures/errors/skips, command exit 0, 29 seconds, 63/63 actionable tasks executed. JUnit confirms the image validation, image-preserving immutable revision, and preview revision marker cases.

The exact generated synthetic v1 and revised v2 packages were reopened independently with `python-pptx 1.0.2`. Both pass ZIP CRC; each contains one slide with a `Blank` layout, one master, a theme part, one image and alt text `Mapa & datos`. The embedded PNG SHA-256 is identical across revisions (`3431e53e66db1ed7740699afa02201bdf19354227ef76a560eb6df149df95a1b`). The title changes from `Informe visual` to `Informe revisado`; the previous revision bytes/hash remain unchanged. This is not validation in PowerPoint, LibreOffice, Android or on the Moto G86.

Exact command, environment, complete log, task markers, XML, fixtures, reader script/output and checksums: [`roundtrip-v2-current-sha-95199cd/`](roundtrip-v2-current-sha-95199cd/). Its child manifest contains 22 entries, SHA-256 `a3bc59ab7dfda994ee451adc2e48852f5e794b3d88ef08c8e2c362b7646bf62c`; the enclosing manifest now retains the original listed payloads and adds this bundle (178 entries total). M03-05 remains source `TODO`, requires a physical device, and depends on M03-01 (`TODO`); this is preparatory HOST evidence only.
