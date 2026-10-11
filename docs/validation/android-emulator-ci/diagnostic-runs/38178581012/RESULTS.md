# M00 ChatHeader capture recovery — run 38178581012

## Status

**EMULATOR: FAIL; one authorized run consumed.** The API 35 x86_64 emulator booted and ADB connected. `:app:connectedDebugAndroidTest` executed and produced a coherent JUnit XML, but one test failed because the authentic Room `WorkspaceDatabase/1.json` fixture is unavailable. Both ChatHeader test methods passed their Compose visibility assertions. Screenshot transfer failed, so screenshot/visual validation remains **NOT_RUN**. No second run is authorized by this contract.

- Repository: `javix142-gif/local-ai-workspace`
- Branch: `feat/0.5.0-skills-agents`
- Tested code commit: `ff4c9f2f8a5c2c09a240f2e9346856767432489c`
- Actions run: [38178581012](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178581012)
- Job: `114586118884`
- Run: `2026-10-11T21:59:16Z`–`2026-10-11T22:13:37Z`
- Workflow conclusion: `failure`
- AVD boot and ADB: pass; serial `emulator-5554`, model `sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`, 1080×1920, density 420 dpi.
- Connected command: `./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8' --stacktrace --console=plain`
- Connected task exit: 1; Gradle reported `BUILD FAILED in 12m 27s`, 114 actionable tasks (103 executed, 11 from cache).
- JUnit: **16 total; 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed**.
- Failed test: `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`, because `com.localai.workspace.data.WorkspaceDatabase/1.json` is missing. The migration test and schema were not changed or fabricated. This remains `ENVIRONMENT_UNAVAILABLE` for an authentic historical fixture.
- Passed: Semantic V2 navigation (1), Agents/Skills navigation (2), ChatHeader (2), Context/Memory (2), Device Validation navigation (1), Project UI (1).
- Skipped: two `LiteRtLmDeviceSmokeTest` methods and four `NormalChatNativeDeviceTest` methods guarded by real-model availability. No model was downloaded or simulated.
- `PHYSICAL_DEVICE=NOT_RUN`; no Motorola or workbook was used.

## ChatHeader semantics and screenshot findings

The only source correction in this run’s code commit was test/collector/evidence code; production UI was not changed. The header nodes are queried in the unmerged Compose semantics tree; independent `assertIsDisplayed()` assertions for agent, conversation, and model remain. The test retains bounds, screen/font-scale/inset diagnostics and the model-line-before-body assertion.

Both tests passed in the emulator XML:

- Standard: requested/real resource font scale 1.0; Compose density 420 dpi; status bar inset 63 px; screen 1080×1920 px / 411×731 dp. App bar `(0,0)-(1080,294)` px; title `(43,83)-(1053,274)`; agent `(43,94)-(1053,168)`; conversation `(43,168)-(1053,221)`; model `(43,221)-(1053,263)`; body starts at y=294 px. The model-bottom assertion passed.
- Large font: requested Compose font scale 1.6; resource configuration font scale 1.0; density 420 dpi; status bar inset 63 px; screen 1080×1920 px / 411×731 dp. App bar `(0,0)-(1080,407)` px; title `(43,94)-(1041,376)`; agent `(43,110)-(1041,206)`; conversation `(43,206)-(1041,292)`; model `(43,292)-(1041,360)`; body starts at y=407 px. The model-bottom assertion passed.
- Compose tree logging at DEBUG showed `chat-header-title` with `MergeDescendants=true` and all three tagged child nodes/texts under it when `useUnmergedTree=true`.

The instrumentation called `UiAutomation.takeScreenshot()` during each rendered header test. Logcat records returned bitmaps of 1080×1920 and app-cache file sizes 45,927 bytes (large-font) and 65,313 bytes (standard). Those cache files were not transferred to the host and were deleted by the test cleanup. For both transfer attempts, the test logged `transfer=` with no status/output; the collector later recorded remote `ls` exit 1, `adb pull` exit 1, and `No such file or directory` for the expected `/sdcard/Download/local-ai-chat-header-{standard,large-font}.png` paths. The artifact contains no PNG files. The evidence narrows the failure to the cache-to-external/host transfer stage, but does not identify which shell operation failed: the producer did not record stderr or a usable exit marker. **No visual clipping/appearance claim is made.**

The collector’s local destination creation and PNG-header validation work are present in the code commit; its unit tests pass. The current Android evidence shows that those collector changes cannot compensate for the still-failing producer transfer. Do not treat the in-cache byte count as a captured or inspected image.

The workflow summary also reports both application/test package names absent in its post-task package query. The connected task and XML prove instrumentation actually ran; this post-run observation is not evidence that installation failed during execution, and persistent installed state is not claimed.

## Host checks for code SHA ff4c9f2

- `python3 scripts/ci/test_collect_android_emulator_evidence.py`: exit 0; 4 tests passed.
- `python3 -m py_compile scripts/ci/collect_android_emulator_evidence.py scripts/ci/test_collect_android_emulator_evidence.py`: exit 0.
- `:app:compileDebugAndroidTestKotlin`: exit 0; `BUILD SUCCESSFUL in 1m 4s`, 54 actionable tasks, 2 executed and 52 up-to-date. Toolchain: JDK 17 at `/workspace/.toolchain/jdk-17`, Gradle Wrapper 8.10.2, Android SDK/API 35 at `/workspace/.toolchain/android-sdk`; NDK 27.0.12077973 and CMake 3.31.6 were configured. The exact command is in `host/commands.txt`.
- `git diff --check ff4c9f2^ ff4c9f2`: exit 0.
- Full JVM suites were **not** rerun on ff4c9f2; the last recorded full JVM result remains historical at its own SHA. No local `assembleDebug` or `assembleDebugAndroidTest` task was run. No distributable APK was retained or uploaded. The emulator workflow used transient app/test APKs as required to install and execute instrumentation; its evidence ZIP contains no APK/model.

## Evidence and hashes

Original GitHub Actions artifact: [artifact 11717233240](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178581012/artifacts/11717233240), 46,460 bytes, SHA-256 `18bf2ab6d03a8b5e7e6907a3869a8c864516b8b3c28781d0712ed9c65bb5df2a`. It is stored byte-for-byte as `artifact/actions-artifact.zip`; the complete original payload and its internal manifest remain in the ZIP. A review-friendly subset and a normalized XML copy are beside it; the toolchain review extract trims terminal progress-bar padding, while its exact bytes remain in the ZIP. All original entries were verified against the archive manifest before creating the review copies. Extracted internal manifest copy `artifact/payload-review/internal-SHA256SUMS.txt` SHA-256: `dea994ec6448d23c19bf2d977adc21fdf63f28ddfcf7f7f2f0253c01c91f1c29`; the original archive retains the same manifest.

- JUnit XML: `instrumented-results.normalized.xml` (review copy; the unmodified CRLF XML remains inside the Actions ZIP), raw ZIP XML SHA-256 `5d3d9d8bd1bd0f77162db5dc1e615237f0a7d735897b5753a85392722b5e8356`; normalized LF review copy SHA-256 `b96671933481c0a1e3759e489328151ad21c09abca47be2f319d095ca1d3e2be`. Parsed counts are independently recorded in `xml-validation.json` (SHA-256 `12172a77f8286b3463036f3922ff7b2a1609feb85ec50ee714296f660716a655`) and match the XML suite attributes.
- `summary.json`: SHA-256 `c7ddea82a830bdb57c7be099b15bd4fcd0c5468dedec2ecab1b669b527507c56`.
- Sanitized logcat: 53,993 bytes, SHA-256 `9e706ad2f6ccfbc247e28c29408da9bc7babdcbe7a6316df3812a0aee88c3207`.
- Screenshot pull report: SHA-256 `6eacb88f35c3ebe396dd46b8581fa7119e775eaad8c6c506ff300f1505c50a1c`.
- Full GitHub job log fetched after completion: original 556,673 bytes, SHA-256 `1ae9fa59a4fb2355c1a073697bf78ef7962bee7a36f734118915ef0a7412c422`, stored losslessly as `job.log.gz` (gzip SHA-256 `bb17f7c62ee60d045ce1043873318008c603c801b2f3566cfa0b8df3c7a9a5d0`); GitHub’s secret masking was preserved. Scan found no unmasked common token pattern.
- Host AndroidTest Kotlin compile log: original SHA-256 `2012201430a2542b966cd536607305ab6dff857e5b31953210a00a1f144aa707`, stored losslessly as `host/androidTest-kotlin-compile.log.gz` (gzip SHA-256 `e9ff29997368329b83392e0f994e0af34c5ec959404a58051d3aa8b95ebc926b`).
- Host focused Python tests: SHA-256 `0b84f102981dc6654759bcfcdc982d1aee0d4f58b7f2f29eda3974e0a697b113`.

`SHA256SUMS` in this directory covers each retained evidence file other than the manifest itself. The exact Actions ZIP, review extracts, normalized XML and host/job logs are preserved here.

## Scope and disposition

No production app/runtime, Room migration, schema, workflow, AVD configuration, models, or test expectations were changed by this follow-up. The two ChatHeader tests now pass on the emulator, but screenshot transfer is unresolved and visual verification remains incomplete. The single authorized run is consumed; stop for independent audit rather than attempt another transfer strategy in this contract. The authentic Room v1 schema remains unavailable and its migration test still fails.

The original user-supplied 97-task plan, IDs, order, and dependencies remain unchanged. M01-06 remains `PENDING_REQUIRES_DEVICE`; no workbook fixture was used. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.
