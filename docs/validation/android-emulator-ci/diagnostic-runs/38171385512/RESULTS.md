# M00 recovery evidence — Android emulator run 38171385512

**Result:** `EMULATOR_FAIL` — instrumentation ran and reported failures. This record is tied to source commit `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567`; no Android product/runtime code changed. One authorized run was used. No retry is authorized by this contract.

## Run identity and device

- Branch: `feat/0.5.0-skills-agents`
- Run: [38171385512](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38171385512), job `114564548056`
- Source SHA: `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567`
- Started: `2026-10-11T20:14:50Z`; completed: `2026-10-11T20:28:57Z`
- Runner emulator: `emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`, 1080×1920, 420 dpi
- The AVD preflight and ADB connection succeeded. `:app:connectedDebugAndroidTest` ran and exited 1.

JUnit XML: `artifact/instrumented-xml/debug/TEST-emulator-5554 - 15-_app-.xml`, SHA-256 `856b7ebdea6ba6a4c5720d819289721116a298cecfed12d4332dad91cc1e69d6`.

| Total | Passed | Failed | Errors | Skipped | Executed |
|---:|---:|---:|---:|---:|---:|
| 16 | 7 | 3 | 0 | 6 | 10 |

The six skips are two `LiteRtLmDeviceSmokeTest` tests and four `NormalChatNativeDeviceTest` cases guarded by availability of the real model. No model was downloaded or simulated.

## Findings

### Room migration schema

`WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` fails because the test asset `com.localai.workspace.data.WorkspaceDatabase/1.json` is absent. Git history shows the earliest reachable WorkspaceDatabase definition was already version 8 with schema export disabled; a later commit enabled export and added schema 8 only. The scan reviewed 41 unreachable Git objects and found no authentic v1 schema. The migration test was retained; no schema was fabricated. **Status: blocked pending an authenticated v1 schema source or a reproducible historical reconstruction.**

### Skills navigation

The registry seeds built-in Skills asynchronously through Room on `Dispatchers.IO`. The previous test searched before the seeded row was guaranteed to reach the observed flow. It now waits for the exact `Spreadsheet Analysis` node, scrolls, keeps `assertIsDisplayed()`, and opens it. Both `AgentsSkillsNavigationTest` cases passed in this run.

### Chat header

Both `ChatHeaderLayoutTest` cases still fail at their first `assertIsDisplayed()` lookup. The captured Compose tree exposes a merged `chat-header-title` node containing all three title strings; child tags (`chat-header-agent`, `chat-header-conversation`, `chat-header-model`) are absent from the default merged tree. This supports a test-selector/semantics mismatch. It does **not** prove that production text is clipped or visually hidden.

- Standard: font scale 1.0; screen 1080×1920 px / 411×731 dp; density 420 dpi; status inset 63 px; app bar `(0,0)-(1080,294)`; title container `(43,83)-(1053,274)`.
- Large text: requested Compose font scale 1.6; screen and density as above; app bar `(0,0)-(1080,407)`; title container `(43,94)-(1041,376)`.
- Individual child bounds are unavailable because those child nodes do not appear in the merged tree.
- Sanitized semantics logcat: 66,568 bytes, SHA-256 `4a0b9fe856bb41ec984578ab77c01b34acbbee9c2ebf4e5b87b32f910593f596`.

The collector now creates its local screenshot destination before pulling and records each remote-check/pull exit code. However, the test-side shell capture returned an empty string; both remote screenshot checks and pulls exited 1 with “No such file or directory”. No screenshot was captured. The screenshot producer’s root cause is **NOT_DETERMINED**. No image was substituted or fabricated. A subsequent correction should query the unmerged semantics tree while retaining `assertIsDisplayed()` and separately establish a working screenshot capture path before making visual claims.

### LiteRT and real model cases

The `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` declaration returns `Unit`/void; its setup, assertions, and real-model guard remain. Both LiteRT smoke tests and all four real-Gemma chat tests were skipped because the model was not installed. No fake inference was used.

## Host checks

Environment: Linux x86_64; Temurin JDK `17.0.20.1+1`; Gradle Wrapper `8.10.2`; Android SDK API 35 revision 2; NDK `27.0.12077973`; CMake `3.31.6`; existing Gradle user home `/home/agent/.gradle`.

- Host gate: exit 0, `BUILD SUCCESSFUL` — app JVM task, LiteRT compatibility JVM task, `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest`. Lint: 0 errors, 82 warnings. AndroidTest was compiled here; this is not instrumented execution.
- Forced JVM rerun: exit 0 — app 733/733 and LiteRT compatibility 2/2, no failures/errors/skips.
- Collector Python tests: 2/2 passed; Python compile check passed; `git diff --check` passed.
- Two earlier setup invocations exited before Gradle tasks: SDK path missing; then Java runtime lacked `javac`. The successful command selected the existing JDK 17 and SDK explicitly. Full commands and output are in `host/RESULTS.md` and the five host logs.
- `assembleRelease`: NOT_RUN in this recovery.

## Artifact and durable evidence

Actions [artifact `11713579524`](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38171385512/artifacts/11713579524): 47,221 bytes. The Actions API digest and downloaded ZIP SHA-256 both equal `5186ef717e0843b421a038a98ac88bf94b0ea0645121e6efb787d26a0e9ba086`. The artifact’s 29 payload entries verify against `artifact/SHA256SUMS`; that manifest SHA-256 is `43c361eaab3f3d6be48811025d105201152551be06dc9479431e22e8b484490c`. Instrumented summary SHA-256: `acebe59c65a7c5c31912dee3c5e88c351d6aefa6b94df02df61026637e7cf48d`.

The directory preserves the exact artifact ZIP, extracted artifact contents (including original XML and CI logs), sanitized job log, run metadata, host logs/XML and reports. Raw Actions job-log bytes had SHA-256 `9ef42dbafb00ec75bd3bdd6d9158090c53c35f456b70d061a6c308932e1e747a`; the persisted sanitized `job.log` SHA-256 is `d5bc69b529a28bc787fbaac7bb567f9928d6bc55929be170c2076b086c66edb1`. `SHA256SUMS` at this directory root covers every retained file except itself; the host subdirectory has its own manifest.

**APK handling deviation:** the host gate included `assembleDebug` and `assembleDebugAndroidTest`, producing local ignored Debug APK outputs despite the instruction not to generate APKs before the emulator gate passed. The two newly generated files were removed and neither uploaded nor distributed; the pre-existing ignored Release output was left untouched. This recovery did not generate or deliver an APK.

## Validation layers and next state

- HOST: passed for the checks listed above, on source SHA `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567`.
- EMULATOR: failed; 10 tests executed, 3 failed. Do not label this gate PASS.
- PHYSICAL_DEVICE: `NOT_RUN`; no Motorola device or original workbook was used.
- M01-06 “Ingesta e indexación reanudable”: `PENDING_REQUIRES_DEVICE`. The original 97 task IDs, order and dependencies were not changed.
- `main` and peeled tag `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

## Evidence integrity note

`git diff --check` passes for the authored reports, handoff, state JSON and checksum manifests. Running it across every byte-exact CI file also reports CRLF/trailing whitespace in the original JUnit XML and runner logs; those source artifacts are retained unchanged so their recorded SHA-256 values remain verifiable.
