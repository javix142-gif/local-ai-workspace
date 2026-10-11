# Android emulator CI setup and execution record


## Latest M00 recovery run (2026-10-11) — EMULATOR FAIL

**One-run recovery attempt consumed; stop for independent audit.** The previous run is retained below as historical evidence.

- Code commit: `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567` (`fix(androidTest): preserve navigation and header failure evidence`)
- Actions run: [38171385512](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38171385512), push event; job `114564548056`
- API 35 x86_64 emulator boot and ADB passed: `emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`, 1080×1920, density 420 dpi.
- `:app:connectedDebugAndroidTest` ran for 12m29s and exited 1. XML: **16 total; 7 passed, 3 failed, 0 errors, 6 skipped; 10 executed**. This is **EMULATOR: FAIL**, not PASS.
- Failures: authentic `WorkspaceDatabase/1.json` is still absent; both `ChatHeaderLayoutTest` visibility assertions still fail. `AgentsSkillsNavigationTest` no longer fails: both navigation tests passed after waiting for asynchronous built-in Skill seeding.
- The two LiteRT device smoke tests, including `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()`, were discovered and skipped by their real-model assumption. Four `NormalChatNativeDeviceTest` cases were also skipped because no Gemma model was installed. No model was downloaded or simulated.

### Evidence-based findings

**Room:** reachable history for `WorkspaceDatabase.kt`, its migration test, and the WorkspaceDatabase schema directory contains only the v0.4.2 baseline (database already at version 8, `exportSchema=false`) and commit `a21400d` (enables export and adds schema 8). The baseline tree contains no WorkspaceDatabase schema JSON; current tracked schemas contain only `8.json`. A scan of 41 unreachable Git blobs found no schema export or unreachable commit; the two JSON blobs mentioning WorkspaceDatabase are old execution-state JSON, not Room schemas. The migration test remains unchanged. Do not create a v1 schema without an authenticated source.

**Skills navigation:** source defines the built-in stable entry `skill.spreadsheet-analysis` / `Spreadsheet Analysis`. `SkillRegistry.initialize()` seeds Room on `Dispatchers.IO`; the screen observes a Room flow. The prior test searched immediately after navigation, before the asynchronous seed was guaranteed visible. The test now waits up to 10 seconds for that exact node, scrolls to it, retains `assertIsDisplayed()`, and opens it. The new run confirms the fix: `builtInSkillsCanBeInspectedWithoutRunningTools` and `generalAgentIsProtectedAndValidationIsDeveloperOnly` both passed.

**Chat header:** the diagnostics now capture `printToLog` at DEBUG (`ChatHeaderLayout:V`) as well as INFO bounds. Logcat exit was 0; the sanitized file is 66,568 bytes, SHA-256 `4a0b9fe856bb41ec984578ab77c01b34acbbee9c2ebf4e5b87b32f910593f596`.

The header test reports API 35, 1080×1920, 411×731 dp, density 420 dpi, status-bar inset 63 px. Standard mode: app bar bounds `(0,0)-(1080,294)` px, title container `(43,83)-(1053,274)` px. Large-font mode: app bar `(0,0)-(1080,407)` px, title container `(43,94)-(1041,376)` px; requested Compose font scale 1.6. For both tests, the Compose tree shows a single `chat-header-title` node with `MergeDescendants=true` and combined text containing the agent, conversation and model lines. The child tags `chat-header-agent`, `chat-header-conversation`, and `chat-header-model` are absent from the merged tree, so the default merged-tree `onNodeWithTag(...).assertIsDisplayed()` lookup fails; child bounds are consequently `UNAVAILABLE`. This identifies a **test semantics selector mismatch**, not evidence that production layout clipped the lines. No production UI/layout was changed. A future focused test correction should query the unmerged semantics tree and keep `assertIsDisplayed()`; screenshot evidence is still needed before claiming visual clipping or visibility.

The collector now creates its local screenshot destination and logs remote-check/pull exit codes. The new artifact proves the separate capture producer did not leave files on the emulator: both remote `ls` checks and `adb pull` calls exited 1 with “No such file or directory”; local files are absent. The test’s `executeShellCommand` result logged an empty string. Therefore the former local-directory collector defect is fixed, but the screenshot producer’s failure remains **NOT DETERMINED**. No image was fabricated or substituted.

### Host validation and evidence

The successful host gate used Temurin JDK 17.0.20.1+1, Gradle 8.10.2, SDK API 35, NDK 27.0.12077973, CMake 3.31.6, and the existing `/home/agent/.gradle` cache. With SDK/JDK explicitly selected, the gate passed: `:app:testDebugUnitTest`, `:litert-compat:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and `:app:assembleDebugAndroidTest`; lint had 0 errors and 82 warnings. JVM tasks were then forced to run: app **733/733** and compatibility **2/2**, zero failures/errors/skips. Collector unit tests passed **2/2**, Python compilation passed, AndroidTest Kotlin compilation passed. Full commands/logs, original JVM XML, environment setup attempts, and hashes are in [host evidence](diagnostic-runs/38171385512/host/RESULTS.md).

**APK handling deviation:** those host gate tasks included `assembleDebug` and `assembleDebugAndroidTest`, which created local ignored Debug APK outputs despite the instruction not to generate an APK before the emulator gate passed. Both outputs were removed; neither was uploaded or distributed. The pre-existing ignored Release output was untouched. No APK is attached.

Actions artifact `11713579524` is **47,221 bytes**, API digest and downloaded SHA-256 both `5186ef717e0843b421a038a98ac88bf94b0ea0645121e6efb787d26a0e9ba086`; its 29 payload entries verify against internal manifest `SHA256SUMS` (manifest SHA-256 `43c361eaab3f3d6be48811025d105201152551be06dc9479431e22e8b484490c`). The durable run directory contains the exact artifact ZIP, extracted original XML/logs/summary, `job.log` (sanitized), metadata and root hashes. Raw job-log SHA-256 `9ef42dbafb00ec75bd3bdd6d9158090c53c35f456b70d061a6c308932e1e747a`; persisted sanitized log SHA-256 `d5bc69b529a28bc787fbaac7bb567f9928d6bc55929be170c2076b086c66edb1`. The artifact summary’s post-task package scan reports both package IDs absent; the connected Gradle task and XML prove instrumentation actually ran, so no claim is made about packages remaining installed afterward.

**HOST:** pass for recorded JVM/build/collector checks. **EMULATOR:** executed with failures. **PHYSICAL_DEVICE:** `NOT_RUN`; no Motorola or workbook used. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97-task plan, IDs, order and dependencies are unchanged. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

---

## Previous focalized run (2026-10-11) — EMULATOR FAIL; superseded by 38171385512

The latest authorized run is [38165315028](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38165315028), source SHA `ee6909d86c9a3477734addc5e933cfc2dfe40fd7`, job `114546554444`. API 35 x86_64 AVD/ADB passed and `:app:connectedDebugAndroidTest` actually ran **16 tests: 6 passed, 4 failed, 0 errors, 6 skipped; 10 executed**. It is not a PASS. The six skips are two real-model LiteRT tests plus four real-Gemma chat tests; no model was downloaded. Failures: authentic Room schema `WorkspaceDatabase/1.json` missing; one additional AgentsSkills navigation lookup; and the standard/large ChatHeader first visibility assertion. The JUnit return-type defect was corrected; its test now reaches the real-model guard and skips.

The header root cause remains undetermined because the collector failed to pull screenshots: its local destination folder was absent; filtered logcat retained no layout bounds/semantics. The Room test/schema was left unchanged because no authentic v1 schema was found; none was fabricated. No further emulator run is authorized by this contract. `PHYSICAL_DEVICE` remains `NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.

Full details, run artifact, raw job log, JUnit XML, host logs/XML and per-file hashes: [run 38165315028 evidence](diagnostic-runs/38165315028/RESULTS.md). Original artifact `11711863899` is 40,988 bytes, SHA-256 `ea98cfd8652e092994bab9e9550c64b9b27c790e2abd95c93c8504e47f56a71c`. Code commits: `8fdc40a65673a04d6d4d68be7b19066b7d629ad2` and `ee6909d86c9a3477734addc5e933cfc2dfe40fd7`. The durable evidence manifest covers 99 files; SHA-256 `3124e2f16d037d87ea39b4b7a8929c213b02cad6b4b3bd37da9c58914bb8d19d`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

---

**Previous run result (2026-10-11; run 38156881656): `EMULATOR_EXECUTED_WITH_FAILURES`.** That earlier run recorded 7 passed, 4 failed, 4 skipped (15 total; 11 executed); it is superseded by the current focalized run above. Motorola physical validation remains `NOT_RUN`.

## Previous run after AVD path correction — run 38156881656

- **Run:** [38156881656](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656), push-triggered, source/workflow SHA `3c5589e7592cb52a88bb2132b1b30fc30d737e03`.
- **Job:** `114521577365`; run time `2026-10-11T16:46:54Z`–`2026-10-11T17:01:35Z`; job conclusion `failure`.
- **AVD path diagnosis/fix:** the prior run showed `avdmanager` storing/listing the AVD in `/home/runner/.config/.android/avd`, while the emulator searched `/home/runner/.android/avd`. This run pins `ANDROID_AVD_HOME=/home/runner/.android/avd` before creation and retains it through emulator launch. Preflight recorded create/pipeline/list exit codes 0; both `avdmanager list avd` and `emulator -list-avds` listed `localai-api35-x86_64`; the expected `.ini` and `.avd/config.ini` existed; preflight passed. The emulator booted and ADB connected.
- **Emulator:** serial `emulator-5554`; model `sdk_gphone64_x86_64`; Android 15/API 35; ABI `x86_64`; `ro.kernel.qemu=1`.
- **Instrumented task:** `:app:connectedDebugAndroidTest`; command: `./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8' --stacktrace --console=plain`; exit code **1**. One JUnit XML records **15 total, 7 passed, 4 failed, 0 errors, 4 skipped; 11 executed**.

Four failures, as recorded in the XML:

1. `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` — missing test schema asset `com.localai.workspace.data.WorkspaceDatabase/1.json`.
2. `LiteRtLmDeviceSmokeTest initializationError` — JUnit rejects `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` because an `@Test` method is not `void`.
3. `ChatHeaderLayoutTest.largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible` — “The component is not displayed” (assertion at line 68).
4. `ChatHeaderLayoutTest.standardFontScaleKeepsAgentTitleAndModelInsideHeader` — “The component is not displayed” (assertion at line 59).

The four skipped cases are `NormalChatNativeDeviceTest` cases guarded by `Assume` on Gemma E2B availability. The workflow downloads no model, and the XML’s skip-reason text is empty; the source guard is the basis for this interpretation. The task ran, but the collector’s post-task `pm list packages` did not find either expected package. There is no pre-task package snapshot, so do not infer persistent post-task installation state from that check; the XML is direct evidence that the instrumentation runner executed.

**Classification:** AVD creation/discovery path mismatch is confirmed resolved for this run. `EMULATOR=EXECUTED_WITH_FAILURES`, overall CI `FAIL`; this is not an emulator PASS. `PHYSICAL_DEVICE=NOT_RUN`. Do not rerun or fix the failures under this contract; stop for independent audit. No APK or model was retained/distributed.

### Run artifact and durable evidence

Artifact [11709571451](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656/artifacts/11709571451), `android-emulator-evidence-38156881656-1`: 40,894 bytes, SHA-256 `b6152538416c30e5b8d2b94521f36b0344b00ae42044ba994f154b4d8bb7ddd7`. The artifact ZIP has 28 entries including its manifest; all **27 payload entries** in that manifest verified; the durable directory [diagnostic-runs/38156881656](diagnostic-runs/38156881656/) includes the exact artifact ZIP, original internal manifest, XML, AVD environment/preflight/list outputs, run metadata, sanitized compressed job log and a SHA-256 manifest. The durable manifest verifies all listed files; its SHA-256 is `66c7b7732e7bc131912ee79927cf1a5eb6e6527d33871778e5f274b34e9dcdef`. JUnit XML readable copy SHA-256: `346a773d82c8a632c3192f00996a7eecb728a896baa1e2c28f61624bd91a7cd5`; original archived XML entry SHA-256: `3cbb54410bcaa7a18c5597f1115ca599712d403a5e8ca0422c985dd5bccd52d4`.

The complete downloaded job log was 558,314 bytes, SHA-256 `529e1e95d45c786e9f39e0fe57df9fc3f176849c5fe813d0ea8a9e0b06a7f730`. One credential-like field was redacted before persistence; the unredacted log was not saved. Sanitized decompressed log: 555,003 bytes, SHA-256 `b71a3d67ed067fcaf2ed700349c2d0c9103a3e9b6857160221d6a576f903cdd0`; compressed file SHA-256 `74b94f7e4c0f0963b026d00f7116c36f1dd1866c7fbb30a62de267029ddff13e`.

**Historical outcome of the first CI attempt:** `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. Before the path correction, the API 35 AVD was not discoverable, ADB listed no devices, instrumentation did not run, and no XML was produced. The later evidence in the latest-run section above established the path mismatch and verified the correction; the instrumentation suite then ran but failed.

Repository: `https://github.com/javix142-gif/local-ai-workspace.git`

Branch: `feat/0.5.0-skills-agents`

Workflow source code commit: `bdae87820b73dbcc0decfde3a65c391c6fb63279`

First failed run: [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921)

Evidence artifact for the first failed run: [android-emulator-evidence-37865813921-1.zip](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524), 23,012 bytes, SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac` (expires 2026-10-16).

## Historical first-run inspection (2026-10-09; superseded by the latest run above)

This executor inspected the retained complete extraction of artifact `11588945524` for run `37865813921` and verified all 11 files against its internal `SHA256SUMS`. At that earlier inspection, the artifact API reported 23,012 bytes and `expired=false`; that attempt to fetch the blob and job log returned no payload. The later assignment reinspection below successfully fetched both through the GitHub connector. The GitHub Jobs API did provide step conclusions: SDK install `SUCCESS`, KVM preflight `SUCCESS`, AVD create/boot `FAILURE`, connected instrumentation `SKIPPED`, evidence collection `FAILURE`, upload `SUCCESS`.

Observed artifact details:

- `logs/android-toolchain.txt` resolves `sdkmanager` and `avdmanager` beneath `/usr/local/lib/android/sdk`; installed components include API 35 `google_apis;x86_64` system image revision 9, emulator 37.2.12 and platform-tools 37.0.1. This identifies executable locations, not the literal effective value of `ANDROID_HOME`.
- `logs/avdmanager.log` contains repository loading/fetch progress and `Auto-selecting single ABI x86_64`; it contains no explicit creation confirmation, resolved AVD home or AVD listing.
- `logs/emulator.log` reports `Unknown AVD name [localai-api35-x86_64]` and no matching `.ini` under the displayed `$HOME/.android/avd`; the printed search-order variables are not expanded.
- `adb-devices.txt` has no devices. `summary.json` records `FAIL`, `gradleExitCode: null`, XML count 0 and executed 0. `adb-after-tests.txt` says no booted emulator was available.
- The workflow creates the AVD and launches the emulator in the same step and does not explicitly set `ANDROID_AVD_HOME`, `ANDROID_USER_HOME`, `ANDROID_SDK_HOME` or `HOME`. The artifact does not record the inherited values. It also has no `avdmanager list avd`, `emulator -list-avds`, or `.ini`/`config.ini` inventory.

Therefore the directly observed failure is established: the configured emulator could not discover a usable named AVD. The reason the AVD was absent from emulator discovery remains **UNPROVEN**; the evidence cannot distinguish an absent/partial creation from creation under a non-discoverable path or another cause. No workflow or script was changed and no new Actions run was launched. The conditional run was not started because its prerequisite—demonstrated root cause—was not met. Status remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; `EMULATOR` and `PHYSICAL_DEVICE` remain `NOT_RUN`.

## Workflow

The workflow runs on feature-branch pushes that touch application/build/CI inputs and declares `workflow_dispatch`. Only the push trigger was exercised. GitHub requires a manually dispatched workflow to be present on the default branch; the workflow was intentionally not copied to `main`, so dispatch availability was not tested. It uses GitHub-hosted standard `ubuntu-24.04`, Temurin JDK 17, API 35 `google_apis;x86_64`, and the repository-pinned NDK/CMake. The runner has no model download step. Workflow permissions are `contents: read`; no repository secret, self-hosted runner, cache, paid runner/service, APK, or model artifact was configured or used. The GitHub Actions platform supplies its standard read-only token for checkout. The connected test task is `:app:connectedDebugAndroidTest` with no test filters. Motorola validation remains a separate physical-device requirement.

## Run history

| Run | Source SHA | Result | Evidence |
|---|---|---|---|
| [37865314837](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865314837) | `b61bb49c1fcf11838885f90c0cb0ab1e6087f150` | Workflow definition was rejected before a job started. The `runner.temp` expression was used at job scope. | No job or artifact. `actionlint` identified the invalid context; the expression was moved to step scope in the next commit. |
| [37865443624](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865443624) | `628748536850876eed800c83329835a3f12e5b28` | SDK setup passed; the emulator setup stopped at its `/dev/kvm` access checks. Instrumentation did not run. The collector also could not find `adb` by bare name. | [Artifact 11587932236](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865443624/artifacts/11587932236), 22,007 bytes, SHA-256 `94d70ae0ddcc5f9b1194bed327b529adea9efc439bdd710c95a540f067b93c08`. Its manifest verified with `sha256sum -c`. |
| [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921) | `bdae87820b73dbcc0decfde3a65c391c6fb63279` | KVM passed; AVD was not discoverable, ADB wait exited 124, instrumentation did not run (historical failure before the path fix). | [Artifact 11588945524](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524); its manifest verified with `sha256sum -c`. |
| [38156881656](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656) | `3c5589e7592cb52a88bb2132b1b30fc30d737e03` | AVD/path preflight and API 35 x86_64 boot/ADB passed; connected instrumentation ran 15 cases and failed (7 pass, 4 fail, 4 skipped). | [Artifact 11709571451](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656/artifacts/11709571451), ZIP SHA-256 `b6152538416c30e5b8d2b94521f36b0344b00ae42044ba994f154b4d8bb7ddd7`; full details above. |

For historical run 37865813921, the runner used API 35 x86_64 `google_apis`; KVM passed, but the AVD was not discoverable and instrumentation never began. The later run 38156881656 is the current outcome and is described at the top of this report.

Run 37865813921 summary (historical): `device.connected=false`; Gradle exit code null because the task was skipped; XML count 0; all test counts 0; status `FAIL`; `physicalDeviceValidation=NOT_RUN`. No `gradle-connected.log` or instrumented XML was fabricated because Gradle and the test runner never started; the GitHub job log records those steps as skipped. Model-dependent `Assume` tests were never reached, so their skip status is unknown, not PASS or SKIPPED.

Key final artifact file hashes (also listed in its `SHA256SUMS`):

- `logs/kvm-check.txt`: `8502e277b25a23ce374142ac005ddcecf4a556f54117ba429bc48fcb2c8e8630`
- `logs/avdmanager.log`: `0b5da32a7559d38a2203eeae77b3b5553d08688a9f2b7984e23644dba000e007`
- `logs/emulator.log`: `0514e3760109faffa47a46e56a6e2c8e09256fe6aa675510e3bb2fe884a8a497`
- `adb-devices.txt`: `36f15c2fe32964a6fbe902e914a47739d4e6304b0d3e812bf08096ff0419b7ae`
- `summary.json`: `76bd871f1474c888625b2effd273f7177300a7157ceb5ebf6f9faf3151589c14`

The previous artifact had `adb-devices.txt` SHA-256 `e4a7bf7973c8b170f9efb327e1e2a23e7b83f858973189be4bf8e15843ab1d03` and summary SHA-256 `da0a009f77cf77311ae52bc840ed2993bcc1929df0212201e3dc8b21cc9d379d`. It recorded the collector's missing-`adb` error. A local synthetic collector smoke test after the fix confirmed the SDK-path `adb` is invoked and an empty device list is reported as failure, not as a passing run.

## Historical follow-up investigation: AVD path/creation cause unproven (2026-10-09; later diagnosed and corrected as recorded above)

The complete artifact for run [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921) was downloaded and inspected, including `logs/avdmanager.log`, `logs/emulator.log`, `logs/android-toolchain.txt`, `logs/sdkmanager.log`, `logs/kvm-check.txt`, `adb-devices.txt`, `summary.json`, and `SHA256SUMS`. All 11 entries listed by the artifact manifest verified successfully. The ZIP is 23,012 bytes with SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`; artifact ID `11588945524`; source code SHA `bdae87820b73dbcc0decfde3a65c391c6fb63279`; job ID `113612155627`.

| Evidence | Established | Not established |
|---|---|---|
| SDK inventory | `android-toolchain.txt` gives `sdkmanager` and `avdmanager` under `/usr/local/lib/android/sdk`; the installed API 35 `google_apis;x86_64` image is revision 9, emulator 37.2.12, platform-tools 37.0.1. The workflow derives both manager paths from `$ANDROID_HOME`. | The log does not print the literal `ANDROID_HOME` value; the SDK root is identified from the resolved executable paths. |
| Workflow environment and directories | The runner log identifies the checkout directory as `/home/runner/work/local-ai-workspace/local-ai-workspace`; the collector path establishes `RUNNER_TEMP=/home/runner/work/_temp`. The AVD step does not set `ANDROID_AVD_HOME`, `ANDROID_USER_HOME`, `ANDROID_SDK_HOME`, or `HOME`. | GitHub's step log prints explicitly declared step environment values, not all inherited variables. Their effective values during AVD creation are absent from the artifact and job log. The checkout action's temporary `HOME` override is logged only during checkout and does not establish the later AVD-step value. The three AVD search locations are logged only as unexpanded templates. |
| `avdmanager` | The executed command was `printf 'no\n' | "$AVDMANAGER" create avd --force --name localai-api35-x86_64 --package 'system-images;android-35;google_apis;x86_64' --device pixel_2`. Under `set -euo pipefail`, the script proceeded to emulator launch, so the pipeline returned success. Its captured output contains repository progress and `Auto-selecting single ABI x86_64`. | There is no `avdmanager list avd`, `.ini`/`config.ini` inventory, resolved AVD home, or explicit creation confirmation in the retained evidence. A successful command exit does not prove where a usable AVD was written. |
| Emulator and ADB | Emulator 37.2.12 reports `Unknown AVD name [localai-api35-x86_64]`, says the `.ini` is absent under the displayed `$HOME/.android/avd` location, and prints the search-order template `$ANDROID_AVD_HOME`, `$ANDROID_SDK_HOME/avd`, `$HOME/.android/avd`. ADB lists no devices; `wait-for-device` exits 124 after 600 seconds. | The emulator log shows variable-name templates, not their expanded values. It cannot distinguish an AVD created under a different search path from an absent/partial creation or another cause. |

The local executor is not a faithful reproduction environment: its SDK has command-line tools but no installed API 35 system image or emulator package. No image was installed and no local reproduction was claimed. Therefore the root cause is **NOT PROVEN**. The only demonstrated failure is that the emulator could not discover a usable AVD at launch. No workflow or application file was changed, and no additional GitHub Actions run was started. A fixed absolute `-p` path plus `.ini`/`config.ini` preflight remains a possible future diagnostic/fix, not a cause established by this evidence.

Artifact entry hashes are recorded in `SHA256SUMS` and were verified. Relevant values: `logs/android-toolchain.txt` `bf83fa52fbf2cea47c83553a2bb2ca3de312cf93093ff448c4ef932213cc6e34`; `logs/sdkmanager.log` `3cbf7c9c581353dc16a96f8c8f0813cb1b7eef448eeacdb880155048f0cecb1c`; `logs/avdmanager.log` `0b5da32a7559d38a2203eeae77b3b5553d08688a9f2b7984e23644dba000e007`; `logs/emulator.log` `0514e3760109faffa47a46e56a6e2c8e09256fe6aa675510e3bb2fe884a8a497`; `adb-devices.txt` `36f15c2fe32964a6fbe902e914a47739d4e6304b0d3e812bf08096ff0419b7ae`; `summary.json` `76bd871f1474c888625b2effd273f7177300a7157ceb5ebf6f9faf3151589c14`.

The failed AVD step then attempted to launch the configured name and wait for ADB:

```bash
nohup "$ANDROID_HOME/emulator/emulator" -avd localai-api35-x86_64 -accel on \
  -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
  -gpu swiftshader_indirect -memory 2048 -cores 2 \
  > "$RUNNER_TEMP/emulator.log" 2>&1 &
timeout 600 "$ANDROID_HOME/platform-tools/adb" wait-for-device
```

For this investigation, `sha256sum -c SHA256SUMS` passed for all 11 artifact entries; the report and `EXECUTION_STATE.json` were checked with `git diff --check` and `python3 -m json.tool`. No local Gradle/JVM task or local AVD run was performed.

The exact creation and launch commands are preserved in the [workflow source](https://github.com/javix142-gif/local-ai-workspace/blob/bdae87820b73dbcc0decfde3a65c391c6fb63279/.github/workflows/android-emulator-instrumented.yml); the complete timestamped job log is available from the run page. No new run, XML, or Gradle result exists for this investigation. Instrumentation remains `NOT_RUN`, with XML=0 and executed=0; physical-device validation remains `NOT_RUN`.

## Validation layers and limits

- **HOST:** `git diff --check` passed; Python collector byte-compilation passed; synthetic `adb`-path smoke test passed (no device was intentionally present). No Gradle/JVM suite was run for this CI-only change.
- **EMULATOR:** **NOT RUN**. The workflow reached emulator setup but produced no ADB device and no instrumentation XML. This run is a failed CI attempt, not an emulator test result.
- **PHYSICAL_DEVICE:** **NOT RUN**. Moto G86 validation is still pending.
- No APK was generated, retained, or distributed by this task.

Disposition: `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. Stop for independent audit. Do not infer the cause or make a speculative workflow change from this run. The user-authorized single run was conditional on proving the cause; that condition was not met, so no run was launched. This investigation did not prepare an APK or alter application/runtime behavior.

## Latest assignment reinspection (2026-10-09)

The assigned feature head `ba3117bca90494514cd7f2c78f65d53e69625c1a` was fetched and inspected in a clean detached worktree. The workflow and collector source are unchanged from incident source `bdae87820b73dbcc0decfde3a65c391c6fb63279`: workflow SHA-256 `4ec258a5d880da00c878255e745e1ae7f35d6cdbef481d8dca1896d7c418cabd`; collector SHA-256 `e23551e9e94bf5801fc86d46a141cb727472ee90268679ebdf443c032f43e792` (file-content hashes). No application, workflow, collector, model, or test files were modified.

A fresh download of artifact `11588945524` returned HTTP 200 and 23,012 bytes. The downloaded ZIP SHA-256 matches the GitHub artifact API digest `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. It contains 12 ZIP entries: `SHA256SUMS` plus 11 listed files; `sha256sum -c SHA256SUMS` passed 11/11. The manifest itself has SHA-256 `52a7c701a7400a31c083a675c00b892e264d333f2975e9d8b053e5d31a29b753`.

The complete GitHub job log was freshly retrieved (211,335 characters; 1,541 lines). It shows:

- At `00:39:52.748Z`, the step ran `avdmanager create avd --force --name localai-api35-x86_64 --package system-images;android-35;google_apis;x86_64 --device pixel_2`, then launched `emulator -avd localai-api35-x86_64`. The pipeline proceeds under `set -euo pipefail`; this establishes that it did not stop on a nonzero pipeline status, but does not prove a usable AVD was written or where.
- `avdmanager.log` ends with `Auto-selecting single ABI x86_64`; it contains no explicit creation path, `list avd`, or file inventory.
- At `00:39:54.407Z`, the ADB daemon had started. The emulator log reports `Unknown AVD name [localai-api35-x86_64]` and a missing `.ini` under the displayed `$HOME/.android/avd` template. At `00:49:53.956Z`, `timeout 600 adb wait-for-device` exited 124. The artifact's ADB list is empty.
- Job metadata marks connected instrumentation `SKIPPED`. `summary.json` records Gradle exit `null`, XML=0 and total/passed/failed/errors/skipped/executed all 0. No test was executed; zero test-level skips is not a test result.

Environment/path findings are limited:

| Item | What this run establishes | What remains unavailable |
|---|---|---|
| SDK root / `ANDROID_HOME` | `android-toolchain.txt` records expanded `SDKMANAGER=/usr/local/lib/android/sdk/cmdline-tools/latest/bin/sdkmanager` and `AVDMANAGER=/usr/local/lib/android/sdk/cmdline-tools/latest/bin/avdmanager`; the workflow formed these from `$ANDROID_HOME`, so the SDK root is established for that install step. | The AVD step did not dump its effective environment; it did not separately print `ANDROID_HOME`. |
| `HOME` | Checkout logs a temporary HOME override used during checkout only. The emulator error says HOME is defined and prints a variable template. | Effective HOME during AVD create/launch is not captured. |
| `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, `ANDROID_SDK_HOME` | The workflow does not set these variables explicitly. | Their inherited effective values are not present in the job log/artifact. The emulator search paths are unexpanded templates. |
| AVD tools/files | Emulator 37.2.12 started; the configured invocation uses `$ANDROID_HOME/emulator/emulator`. | No `avdmanager list avd`, `emulator -list-avds`, resolved AVD-home path, or `.ini/config.ini` inventory was recorded. |

Therefore the immediate failure is confirmed, but the causal distinction is not: the retained evidence cannot tell whether creation was absent/partial, wrote somewhere not searched, or failed for another reason. KVM passed its check and is not evidence for the AVD cause. No workflow/script edit and no new Actions run were made because the required causal proof is missing. Status remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; connected instrumentation and `EMULATOR` test validation remain `NOT_RUN`; `PHYSICAL_DEVICE` remains `NOT_RUN`. The original 97-task plan is unchanged and M01-06 remains pending/device-required.

## Independent orchestration audit (2026-10-09 07:36:16 UTC)

**Disposition: `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`.** I independently checked the feature-branch compare, current branch reference, workflow source, GitHub Actions job metadata/log, artifact metadata, and persisted execution state. The branch at `a18f1fc99be211fe2346799218233548a15adf16` was one fast-forward documentation-only commit beyond `ba3117bca90494514cd7f2c78f65d53e69625c1a`; the changed files were the handoff, execution state, and this results record. No application, workflow, or test source changed.

The job for run `37865813921` (source commit `bdae87820b73dbcc0decfde3a65c391c6fb63279`) marks AVD setup `FAILURE`, connected instrumentation `SKIPPED`, and records `timeout 600 adb wait-for-device` exiting 124. The configured creation and launch commands appear in the job log, but that log does not prove a usable AVD was created or where it was stored. XML count and executed-test count are both zero; Gradle instrumentation was not run. The emulator's inability to discover the named AVD is established; the cause remains unproven because effective AVD environment values and AVD directory/listing diagnostics are absent.

GitHub's artifact metadata reports artifact `11588945524`, 23,012 bytes, SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. The download connector returned a file reference that this audit could not inspect as ZIP bytes, so I did not independently recalculate its archive or internal manifest checksums; the executor's reported manifest verification remains attributed to the executor. No local build/test or new Actions run was performed by this auditor.

The branch is `feat/0.5.0-skills-agents`; `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. No additional executor instruction was sent. The original 97-task plan remains unchanged. Emulator and physical-device validation are still `NOT_RUN`; continue only when new evidence or a bounded diagnostic opportunity can establish the AVD creation/discovery cause.


## Reinspection and bounded diagnostic preparation (2026-10-11)

The current feature branch was fetched at `fcad2eca9bfb4d0b286e1508f1eaeb67ab726a7b`; since the previous local evidence head, the newly observed commit changed only Release documentation/evidence. `main` and peeled `v0.4.2` both remain `de615b772cf37a99e7e08a647c59607b271de606`. The workflow and collector at `fcad2ec` are byte-identical to the incident-source files at `bdae87820b73dbcc0decfde3a65c391c6fb63279`.

The original run artifact was freshly downloaded from artifact ID `11588945524`. The ZIP is 23,012 bytes with SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`; all 11 files in its internal `SHA256SUMS` verified. It contains 12 ZIP entries including the manifest. The toolchain log identifies `sdkmanager` and `avdmanager` under `/usr/local/lib/android/sdk`, API 35 `google_apis;x86_64` system image revision 9, emulator 37.2.12 and platform-tools 37.0.1. KVM was readable/writable. `avdmanager.log` ends at `Auto-selecting single ABI x86_64`; `emulator.log` reports `Unknown AVD name [localai-api35-x86_64]` and no matching `.ini` under the displayed `$HOME/.android/avd`. ADB has no connected devices. The existing run summary is `FAIL`, XML=0, executed=0, Gradle exit `null`; connected instrumentation was skipped. The artifact does not establish the direct AVD-step values of HOME/ANDROID_* or where `avdmanager` wrote files, so the creation-versus-search cause remains **UNPROVEN**.

### Separate Release/ARM64 evidence classification

- Fresh empty-`GRADLE_USER_HOME` Release procedure: `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED` after the two authorized attempts. The final wrapper failed with `Connection refused` fetching Gradle 8.10.2 before Gradle started; zero Gradle tasks, CMake/Ninja not reached, no APK. The lower-level network cause remains `NOT_DETERMINED`; no retry or cache workaround was made. See [6d1b25 results](../clean-release-repro-6d1b25/RESULTS.md).
- Historical Release pass: checkout `83affa67584292137a17dd544a4f6e207cf7c96f`, product source `260a3f7c6267cc5a90a9cf516356951aec0627a1`, `:app:assembleRelease -Parm64Only=true --offline`, with shared pre-provisioned `/home/agent/.gradle`. It executed 128/128 tasks and recorded ARM64 CMake configure/Ninja success. This proves only that recorded checkout/dependency-cache policy; it does not prove an empty Gradle home. No APK was retained or distributed. See [83affa results](../clean-release-repro-83affa/RESULTS.md).
- The only existing ARM64 Actions artifact workflow is explicitly QA Debug: run `37874837502`, job `113640935577`, source `4ef2b39b9cba6aa1def081171d42d9146bbd968c`, task `:app:assembleDebug`, artifact `11592306483` (47,787,296 bytes, ZIP SHA-256 `5ef240ea08899079e85835bcd2778eeda2b5bd11abde5472f113a974bf6884a4`). Its report/log identifies package `com.localai.workspace.debug`, version `0.5.0-debug` / versionCode 27, ABI `arm64-v8a` only, and a successful Debug build. It is not a Release artifact and does not demonstrate `assembleRelease`.

### Diagnostic change under review

The workflow change records only the six requested HOME/ANDROID_* variables, resolved SDK/AVD search paths, the independent output/exit status of `avdmanager list avd` and `emulator -list-avds`, `.ini`/`config.ini` inventory, and the `avdmanager create` command's own exit status before launch. The collector copies these records into the always-uploaded artifact and summary. The existing AVD create input/name, launch arguments, and ADB wait sequence are unchanged. The workflow's existing push path includes its own workflow file, so the authorized single verification run is push-triggered; the QA Debug workflow's paths do not match these changes.

Static/host checks before that run: extracted workflow shell passes `bash -n`; collector parses with Python `ast`; a temporary stub-tool smoke check verified exit-code capture and artifact collection while correctly producing `FAIL`, zero executed tests. This synthetic check is not Android/emulator execution. `actionlint` is unavailable in this environment. The new GitHub Actions run and its diagnostic outcome are recorded in the next section after its single authorized execution.

## Historical AVD diagnostic execution (2026-10-11; run 38154790618)

The diagnostics-only change at source SHA `d0d4d7608c8e8149bdb05d003da2742260c1b8dd` was exercised once by the existing push trigger: [Actions run 38154790618](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618), job `114515427123`. The runner was GitHub-hosted `ubuntu-24.04`; SDK/API 35 setup and KVM preparation succeeded. The AVD setup step failed after `timeout 600 .../adb wait-for-device` returned 124. The connected instrumentation step was **SKIPPED**; the collector and artifact upload ran via `always()`. No second run was started.

### Cause established at the discovery boundary

The captured AVD-step environment was:

- `HOME=/home/runner`
- `ANDROID_HOME=/usr/local/lib/android/sdk`
- `ANDROID_SDK_ROOT=/usr/local/lib/android/sdk`
- `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, and `ANDROID_SDK_HOME` were unset.

`avdmanager create avd` returned **0**; the full pipeline status was `0 0 0`. `avdmanager list avd` also returned **0** and listed `localai-api35-x86_64` with its path under `/home/runner/.config/.android/avd/localai-api35-x86_64.avd`. In contrast, `emulator -list-avds` returned **0** with empty output. The emulator error names the missing AVD under `/home/runner/.android/avd`; that was the only resolved search directory recorded by the workflow, and it did not exist. Therefore the causal discovery failure is **AVD home/path mismatch**: avdmanager's listing places the AVD in `.config/.android/avd`, outside the emulator's discovered home in this run.

The evidence does **not** identify why the Android command-line tools selected the `.config` directory; no unrecorded variable such as `XDG_CONFIG_HOME` is attributed as the cause. The diagnostic inventory searched the resolved environment-derived AVD directories and `HOME/.android/avd`, but did not expand the separate `.config/.android/avd` path printed by `avdmanager list avd`; consequently this run does not provide file-level hashes for `.ini`/`config.ini` in that alternate directory. The list/path mismatch itself is directly observed, while that file-level detail and the reason for the manager's path choice remain unresolved. No AVD path/create/launch fix was applied. The result is recorded as **`AVD_DISCOVERY_PATH_MISMATCH_DEMONSTRATED; AWAITING_INDEPENDENT_AUDIT`**; stop here until that audit.

### Instrumentation outcome

The ADB list remained empty. `:app:connectedDebugAndroidTest` did not execute; Gradle exit is `null`; XML count and all test counts are zero (`executed=0`). The run and collector are **FAIL**, not a device-test pass. `EMULATOR` instrumentation is `NOT_RUN`; `PHYSICAL_DEVICE` is `NOT_RUN`. No model or APK was included. This Actions runner result says nothing about Motorola behavior.

### Evidence and integrity

The uploaded artifact is [11708477219](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618/artifacts/11708477219), `android-emulator-evidence-38154790618-1`, 26,361 bytes. GitHub API digest and downloaded ZIP SHA-256 both equal `bf6faee748d258e77401c02e79d10271213143ec4dfcdf9cfe19d2ba0a6365ab`; all 23 internal manifest entries verified. It expires `2026-10-18T16:28:18Z`.

Durable copies of the original ZIP, readable diagnostic records, the original artifact manifest, and a sanitized compressed job log are in [the run evidence directory](diagnostic-runs/38154790618/). The full job log was captured with `gh run view 38154790618 --job 114515427123 --log`; its original SHA-256 is `70c5f3194fa986db19801064fa2d1360cff80f55dc86eed5b61b946987aa1fd4` (358,861 bytes). One server-password-like field was redacted before persisting the job log; the sanitized decompressed log SHA-256 is `4ac57c7ed5a5dfd2c0f001e790b6216f1402981aa0ac3af469ef213c18f2eeda`; compressed file SHA-256 is `f563c74fd84b1ca2ca1a7d05a73ba8e2a3fd1ba73a482abbc622c22018864c2a`. The run directory `SHA256SUMS` covers the durable files; the Actions ZIP's own `SHA256SUMS` remains preserved and was independently rechecked.

### Classification carried forward

The empty-`GRADLE_USER_HOME` Release attempts remain separately classified `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED`, because the wrapper was refused while downloading Gradle 8.10.2 before Gradle startup; the lower-level network cause is `NOT_DETERMINED`. The earlier `83affa6` Release pass remains limited to its clean checkout with shared pre-provisioned dependencies. Actions run `37874837502` remains a Debug/QA ARM64 artifact only, not Release evidence. No build was run for this classification. Main and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`; the original 97 task IDs/order/dependencies are unchanged and M01-06 remains `PENDING_REQUIRES_DEVICE`.

The durable evidence-directory manifest SHA-256 is `a049ac05c74dc807c7ad62da68d1a94da5a3766903bda4e40ef125a029e310e3`; `sha256sum -c` passed for every retained run file. The exact Actions artifact ZIP is preserved at `diagnostic-runs/38154790618/actions-artifact.zip`.
