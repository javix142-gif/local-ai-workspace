# Focalized Android instrumentation follow-up — 2026-10-11

**Disposition: `EMULATOR_EXECUTED_WITH_FAILURES`; overall FAIL. Stop after this one run.**

## Source and run identity

- Repository/branch: `javix142-gif/local-ai-workspace`, `feat/0.5.0-skills-agents`
- Tested code SHA: `ee6909d86c9a3477734addc5e933cfc2dfe40fd7`
- Code commits: `8fdc40a65673a04d6d4d68be7b19066b7d629ad2` and `ee6909d86c9a3477734addc5e933cfc2dfe40fd7`
- Actions run: [38165315028](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38165315028), push event, attempt 1
- Job: `114546554444`, [API 35 x86_64 emulator tests](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38165315028/job/114546554444)
- Started `2026-10-11T18:47:56Z`; completed `2026-10-11T18:58:15Z`; job conclusion `failure`
- Existing workflow: `.github/workflows/android-emulator-instrumented.yml`; no workflow/AVD behavior was changed.

## Host validation

The tested source SHA is `ee6909d86c9a3477734addc5e933cfc2dfe40fd7`. Host environment: Temurin JDK `17.0.20.1+1`, Gradle Wrapper `8.10.2`, Android SDK platform 35 revision 2, NDK `27.0.12077973`, CMake `3.31.6`, Linux x86_64. `GRADLE_USER_HOME` was left unset, using the environment's existing `/home/agent/.gradle`; no fresh-empty-home Release procedure was attempted.

Commands and results:

1. `./gradlew -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 :app:compileDebugAndroidTestKotlin --no-daemon --no-parallel --max-workers=1 --console=plain` — exit 0, `BUILD SUCCESSFUL`, 54 actionable tasks (2 executed, 52 up-to-date), 47 seconds.
2. `./gradlew -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 :app:testDebugUnitTest :litert-compat:testDebugUnitTest --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain` — exit 0, `BUILD SUCCESSFUL`, 68 tasks executed, 5m06s. JUnit XML: app **733 tests**, LiteRT compatibility **2 tests**, combined **735 passed / 0 failed / 0 errors / 0 skipped**.
3. `./gradlew -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --no-parallel --max-workers=1 --console=plain` — exit 0, `BUILD SUCCESSFUL`, 164 actionable tasks (28 executed, 6 from cache, 130 up-to-date), 1m35s. `lintDebug` reported 0 errors, 82 warnings, 1 informational issue. AndroidTest was compiled only by this host command; this is not instrumentation.

An earlier build attempt before the inset correction failed compiling `ChatHeaderLayoutTest.kt` because `WindowInsets.statusBars.getTop(density)` was called inside `SideEffect`. The correction computes the inset during composition and writes the value from `SideEffect`; the focused AndroidTest Kotlin compile and final gates above then passed. The failed and successful logs are retained separately. No production app/runtime code changed. No APK is included in this evidence bundle or distributed; `assembleDebug` may leave its normal ignored local build output.

## Emulator execution

The workflow setup, KVM check, AVD creation/preflight, emulator boot and ADB checks all passed. The artifact records:

- AVD `localai-api35-x86_64`; canonical home `/home/runner/.android/avd`; AVD manager and emulator both listed it, and `.ini` plus `config.ini` existed.
- ADB serial `emulator-5554`, connected state `device`; model `sdk_gphone64_x86_64`, Android 15 / API 35, ABI `x86_64`.
- Screen `1080x1920`, density `420`.
- Gradle command: `./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain`.
- The real connected task started **16 tests** and exited 1 after **8m38s**. XML is present and consistent: **16 total; 6 passed, 4 failed, 0 errors, 6 skipped; 10 executed**.

Failures:

1. `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`: `MigrationTestHelper` cannot find `com.localai.workspace.data.WorkspaceDatabase/1.json`. The migration test is unchanged. The reachable repository history and workspace search did not provide an authentic v1 Room export; the test's partial `CREATE TABLE` fixture is not a replacement for the missing complete Room schema. No schema was fabricated and this remains blocked pending an authentic historical schema source.
2. `AgentsSkillsNavigationTest.builtInSkillsCanBeInspectedWithoutRunningTools`: `performScrollTo()` cannot find the text `Spreadsheet Analysis`. This additional UI navigation failure is outside the four named corrections and was not modified.
3. `ChatHeaderLayoutTest.largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible`: `assertIsDisplayed()` fails on the first agent-title assertion at test line 89.
4. `ChatHeaderLayoutTest.standardFontScaleKeepsAgentTitleAndModelInsideHeader`: `assertIsDisplayed()` fails on the first agent-title assertion at test line 79.

The Compose test kept `assertIsDisplayed()` and added bounds, density/inset, semantics-tree and screenshot diagnostics. Those diagnostics were **not fully retained**: `screenshot-pull.txt` shows both `adb pull` commands failed because the collector's local `ui-diagnostics/screenshots` directory did not exist. The filtered logcat contains only TestRunner lines; no bounds/semantics output was captured in the artifact. Thus both header failures are reproduced on the emulator, but their UI/layout cause is still **NOT DETERMINED**. Do not change production layout based on this run alone.

The collector's post-task package inventory says both packages were absent after the connected task. The JUnit XML and Gradle log prove that the connected instrumentation runner launched and ran tests; no claim is made that packages remained installed after test cleanup. The collector therefore marked `applicationApkInstalled=false`, `connectedTaskSucceeded=false`, `noTestFailuresOrErrors=false`, `headerUiDiagnosticsCaptured=false`, and overall `FAIL`.

### Model-dependent cases

All six model-gated cases remain **skipped**, not passed: two `LiteRtLmDeviceSmokeTest` methods and four `NormalChatNativeDeviceTest` methods. The LiteRT method's JUnit declaration is now valid and reached its real model-presence assumption; no model was downloaded or faked. `NormalChatNativeDeviceTest` retains its real Gemma E2B assumption. The XML's skip reason strings are empty; test source and the no-model workflow policy explain these skips. No native inference was performed by these tests.

## Evidence and integrity

The original Actions artifact ZIP is preserved as `actions-artifact.zip`: 40,988 bytes, SHA-256 `ea98cfd8652e092994bab9e9550c64b9b27c790e2abd95c93c8504e47f56a71c`, matching the GitHub artifact API digest for artifact `11711863899`. The ZIP's internal SHA256SUMS lists 28 payload entries; all 28 verify. The ZIP contains 29 entries including SHA256SUMS. It contains the summary, original JUnit XML, AVD/ADB/toolchain records, Gradle connected log, emulator log, sanitized logcat and screenshot-pull failures. No APK or model is included. The durable directory SHA256SUMS verifies all 99 retained evidence files; its digest is recorded in EXECUTION_STATE.json and the handoff.

- Full Actions job log `job.log`: 546,298 bytes, SHA-256 `02e94be88feee10ce453f89d7d8bc48797ff3bafc2dec5e179c913ea7fbb6de3`.
- Readable `instrumented-results.xml` (CRLF normalized to LF): SHA-256 `7035efbe8b3d2f15a27d7d93972f2bb35fd9d87f3e4f20608867267014ec3b51`. The exact original artifact entry `instrumented-xml/debug/TEST-emulator-5554 - 15-_app-.xml` remains in `actions-artifact.zip`, SHA-256 `ed93b43fd2a8faf0d7f4bdacf0787076e5785a37e592bf32eb22dfc08b58cdbf`.
- `summary.json`: SHA-256 `f83bddf9b7a28ab958d95298e64f600fe10502a44b0aefca259c584eff0e870d`.
- `actions-artifact-internal-SHA256SUMS`: SHA-256 `fab4a440ae8e04b8ae8b67f0414c0bc1e850bca59069b41ed4c85740da9cc2df`.
- Host test/build logs and all 85 host JUnit XML files (84 app + 1 compatibility) are under `host/`; their hashes are listed in this directory's `SHA256SUMS`.
- Secret-pattern scan of retained text/XML/JSON/log evidence found no candidates.

## Classification and next unblock

| Layer | Result |
|---|---|
| HOST | **PASS** for the recorded source SHA and commands above |
| EMULATOR | **EXECUTED_WITH_FAILURES / FAIL**; AVD/API/ABI/ADB and real connected test execution were demonstrated |
| PHYSICAL_DEVICE | **NOT_RUN**; no Motorola result is claimed |

The JUnit signature correction is verified at host compile and emulator test-discovery levels. Room migration remains blocked by the missing authentic v1 schema; both header tests still fail and need captured UI bounds/semantics/screenshots before a justified layout change; the screenshot collector needs to create its local destination before pulling; and the extra Skills navigation failure needs separate triage. This contract authorizes exactly one run, which has been consumed. Do not rerun CI from this report.

`main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`. The user-supplied plan remains the source of the 97 task IDs, order and dependencies; none were changed. M01-06 remains `PENDING_REQUIRES_DEVICE`. No workbook fixture was used and physical validation is **NOT_RUN**.
