# UX-02a automatic Android emulator run

## Identity and outcome

- Label: `EMULATOR`; this is not a physical-device result.
- Source commit: `d0832e225f51f624c981f6b68da6193b72a78ef2`.
- Workflow run: [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867), job `114683767158`, push event, attempt 1.
- Workflow conclusion: **FAIL**. API 35 x86_64 AVD creation/boot and ADB connection succeeded, and the actual task `:app:connectedDebugAndroidTest` ran. It exited 1 after one existing Room migration fixture failure.
- Timing: run started `2026-10-12T06:02:34Z`; completed `2026-10-12T06:16:28Z`. The Gradle log reports `BUILD FAILED in 11m 37s`; command exit code was 1.
- Device: `emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`, screen 1080×1920, density 420.

## Instrumentation

The retained JUnit XML reports **16 total: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed**. The failed case is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`: Room cannot find the authentic `com.localai.workspace.data.WorkspaceDatabase/1.json`. No schema or migration test was changed for UX-02a.

Both real emulator ChatHeader cases passed:

- `standardFontScaleKeepsAgentTitleAndModelInsideHeader`
- `largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible`

The six skips are model-dependent: two LiteRT native-session cases and four Gemma native-chat cases. No models were downloaded or simulated. The XML can be reviewed at [`xml/TEST-emulator-5554-15-app.xml`](xml/TEST-emulator-5554-15-app.xml).

The run artifact reports a post-test package-list check of `com.localai.workspace.debug` and `.test` as absent. That post-task snapshot does not establish that installation failed during execution: the connected instrumentation task produced the JUnit report and ran the Compose/UI cases. The collector also classified the task as unsuccessful because the Room failure occurred. Overall EMULATOR remains FAIL.

## Captures

The two original artifact PNGs were validated as 1080×1920 RGBA PNGs and opened for visual review. Both show the agent, long conversation title and selected model text; the large-font title/model wrap onto additional lines without losing their text. This verifies the tested header fixture in this emulator run, not Motorola behavior.

| Capture | SHA-256 |
|---|---|
| [Standard font](screenshots/chat-header-standard.png) | `b9ad8b86a24df2e96d4377912e0de011316660396331a0c81ab093633759a1b1` |
| [Large font](screenshots/chat-header-large-font.png) | `9879101d7927d9d940af58a97b619350a03a3b019e704b34d762e39ab5d4a36a` |

## Paired fail-closed QA workflow

- Run [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883), job `114683767175`, checked the same source SHA and concluded **FAIL** at “Wait for matching Android instrumentation run to succeed”.
- JDK setup, the APK build/validation step and artifact upload were skipped. GitHub reports no artifact for this run. This is the expected gate behavior; it did not produce the personal APK described in the parent report.
- The separate local Debug ARM64 personal QA APK had already been built, validated and uploaded to Drive as documented in the parent [`RESULTS.md`](../RESULTS.md). It remains `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`, not a release and not physical validation.

## Evidence files and hashes

The exact Actions artifact ZIP is preserved at [`run-38210637867-artifact.zip`](run-38210637867-artifact.zip). GitHub artifact ID `11729571175`, name `android-emulator-evidence-38210637867-1`, size 155,402 bytes, API digest `sha256:dd75931c08f5a6ededdb73482b0c728faefcc1719f92d4d8c7ee96da1baa9911`. The downloaded bytes were independently hashed locally and match that digest. All 33 payload entries in the archive's internal `SHA256SUMS` verified successfully.

| Payload | SHA-256 |
|---|---|
| Actions artifact ZIP | `dd75931c08f5a6ededdb73482b0c728faefcc1719f92d4d8c7ee96da1baa9911` |
| `summary.json` inside artifact | `80753d724f9f7b0ee14752f272573ae8d6d929f77bc267be95506488e7d22353` |
| Instrumented JUnit XML | `d1a64440c8e83358861357f216e79b6781804e43ca4f9eaf895a033200e9328f` |
| `logs/gradle-connected.log` | `28d9fd7018e09408dbf56d48ba34e75416268d76cca702cf6d0a5beca293e240` |
| Sanitized filtered logcat | `483012b20da38c85d61c99e0dcc1ba10a26b5bde94e92397bcb4ee220a47d2c3` |
| Standard emulator PNG | `b9ad8b86a24df2e96d4377912e0de011316660396331a0c81ab093633759a1b1` |
| Large-font emulator PNG | `9879101d7927d9d940af58a97b619350a03a3b019e704b34d762e39ab5d4a36a` |

No APK, model weights or personal data are included in the run artifact.

## Layered status

- `HOST`: PASS for the focused 9/9 UI tests, 741/741 app JVM tests, lint and listed debug builds; see the parent results report.
- `EMULATOR`: **FAIL overall**; both ChatHeader cases pass within the suite, while one Room fixture failure and six model-dependent skips remain.
- `PHYSICAL_DEVICE`: `NOT_RUN`; Moto G86 Power validation is still pending.
- `M01-06`: `PENDING_REQUIRES_DEVICE`; plan IDs/order/dependencies were not changed.
