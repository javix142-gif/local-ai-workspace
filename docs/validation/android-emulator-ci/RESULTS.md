# Android emulator CI setup and execution record

**Outcome: BLOCKED before instrumentation.** The workflow is published and reached a GitHub-hosted accelerated runner, but the API 35 AVD was not created in the final run. `adb` listed no devices, Gradle connected instrumentation did not run, and no JUnit XML was produced. This is not an Android test pass.

Repository: `https://github.com/javix142-gif/local-ai-workspace.git`

Branch: `feat/0.5.0-skills-agents`

Workflow source code commit: `bdae87820b73dbcc0decfde3a65c391c6fb63279`

Final run: [37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921)

Final evidence artifact: [android-emulator-evidence-37865813921-1.zip](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524), 23,012 bytes, SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac` (expires 2026-10-16).

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

## Validation layers and limits

- **HOST:** `git diff --check` passed; Python collector byte-compilation passed; synthetic `adb`-path smoke test passed (no device was intentionally present). No Gradle/JVM suite was run for this CI-only change.
- **EMULATOR:** **NOT RUN**. The workflow reached emulator setup but produced no ADB device and no instrumentation XML. This run is a failed CI attempt, not an emulator test result.
- **PHYSICAL_DEVICE:** **NOT RUN**. Moto G86 validation is still pending.
- No APK was generated, retained, or distributed by this task.

Open CI follow-up: make AVD creation and emulator lookup use one explicit AVD directory and assert the expected `.ini` exists before launch. This report stops for independent audit. The next requested milestone after that audit is the ARM64 APK; it must not be described as emulator- or device-tested based on these runs.
