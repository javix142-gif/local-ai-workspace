# Android emulator CI setup and execution record

**Outcome: `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`.** The workflow reached a GitHub-hosted accelerated runner, but the API 35 AVD was not discoverable in the final run. `adb` listed no devices, Gradle connected instrumentation did not run, and no JUnit XML was produced. This is not an Android test pass. A latest assignment reinspection fetched the full job log and artifact anew; it adds the exact failure sequence but still does not establish why the AVD was absent.

Repository: `https://github.com/javix142-gif/local-ai-workspace.git`

Branch: `feat/0.5.0-skills-agents`

Workflow source code commit: `bdae87820b73dbcc0decfde3a65c391c6fb63279`

Final run: [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921)

Final evidence artifact: [android-emulator-evidence-37865813921-1.zip](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524), 23,012 bytes, SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac` (expires 2026-10-16).

## Earlier follow-up inspection (2026-10-09; retrieval note superseded below)

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
| [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921) | `bdae87820b73dbcc0decfde3a65c391c6fb63279` | KVM check passed after granting access on the ephemeral runner. AVD creation did not leave the named AVD configuration; emulator logged `Unknown AVD name`, `adb` showed no device, and `timeout 600 adb wait-for-device` exited 124. Instrumentation did not run. | [Artifact 11588945524](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524); its manifest verified with `sha256sum -c`. |

The final run used API 35, x86_64, `google_apis` system image. The KVM artifact records `/dev/kvm` present and changes its mode from `crw-rw---- root:kvm` to readable/writable `crw-rw-rw-`. `avdmanager.log` contains only `Auto-selecting single ABI x86_64`; the expected AVD `.ini` was absent. `emulator.log` reports that `localai-api35-x86_64` is unknown and cannot be found under the searched AVD directories. The immediate blocker is AVD creation/location; the underlying reason `avdmanager` returned without a discoverable AVD is not established by this run.

Final run summary: `device.connected=false`; Gradle exit code is null because the Gradle task was skipped; XML count 0; total/passed/failed/errors/skipped/executed all 0; status `FAIL`; `physicalDeviceValidation=NOT_RUN`. No `gradle-connected.log` or instrumented XML was fabricated because Gradle and the test runner never started; the GitHub job log records those steps as skipped. Model-dependent `Assume` tests were never reached, so their skip status is unknown, not PASS or SKIPPED.

Key final artifact file hashes (also listed in its `SHA256SUMS`):

- `logs/kvm-check.txt`: `8502e277b25a23ce374142ac005ddcecf4a556f54117ba429bc48fcb2c8e8630`
- `logs/avdmanager.log`: `0b5da32a7559d38a2203eeae77b3b5553d08688a9f2b7984e23644dba000e007`
- `logs/emulator.log`: `0514e3760109faffa47a46e56a6e2c8e09256fe6aa675510e3bb2fe884a8a497`
- `adb-devices.txt`: `36f15c2fe32964a6fbe902e914a47739d4e6304b0d3e812bf08096ff0419b7ae`
- `summary.json`: `76bd871f1474c888625b2effd273f7177300a7157ceb5ebf6f9faf3151589c14`

The previous artifact had `adb-devices.txt` SHA-256 `e4a7bf7973c8b170f9efb327e1e2a23e7b83f858973189be4bf8e15843ab1d03` and summary SHA-256 `da0a009f77cf77311ae52bc840ed2993bcc1929df0212201e3dc8b21cc9d379d`. It recorded the collector's missing-`adb` error. A local synthetic collector smoke test after the fix confirmed the SDK-path `adb` is invoked and an empty device list is reported as failure, not as a passing run.

## Follow-up investigation: AVD path/creation cause unproven (2026-10-09)

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

