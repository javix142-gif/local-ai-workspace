# M00-04 — material context omission notice regression (HOST)

## Scope and source reconciliation

This focused contract tests the original backlog item **M00-04, “Ajustar aviso de contexto omitido”**. Its source status remains `TODO`; this evidence does not mark the source catalog complete. The authentic backlog and M00 roadmap section were read without modification. Their recorded hashes remain `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`.

The prior M00 crosswalk is reconciled narrowly:

| ID | Evidence status for this crosswalk | Scope and boundary |
|---|---|---|
| M00-01 | `HOST_DOD_DEMONSTRATED_FOR_SYNTHETIC_FIXTURE_ONLY` | Synthetic 291,429-byte workbook profile only. The private workbook’s contents and cause remain `NOT_DETERMINED`. |
| M00-02 | `HOST_SYNTHETIC_DOD_DEMONSTRATED` | The filtered `formattedEmptyXlsxCellsDoNotConsumeRepresentationBudget` regression passed 1/1 for a synthetic XLSX containing 58,000 styled-empty cells and three populated cells. The 291,429-byte ZIP expands to about 2.41 MB of worksheet/XML; parsing retains A1=1, A2=5, A3=4, cached/formula data and references while producing a 392-byte representation and one chunk. This verifies bounded parsing/representation for the fixture, not the private workbook or a physical import. |
| M00-03 | `VALIDADA_HOST` | Already independently accepted in persistent state. Prior evidence is referenced, not re-audited in this contract. |
| M00-04 | `FOCUSED_PARTIAL_HOST_VALIDATION_ENVIRONMENT_BLOCKED_NOT_ACCEPTED` | Chat notice/routine omission regression passed; the Inspector UI regression did not reach its test body because Robolectric font setup failed with `ENOSPC`. M00-04 is not accepted. |

The current original task remains M00-04 because its DoD is incomplete. No M00-05 or other task was started. The source backlog remains 97/97 `TODO`, with IDs, order and dependencies unchanged. M01-06 remains `PENDING_REQUIRES_DEVICE` and still depends on M00-02.

## Code and tests

Test-only code commits on `feat/0.5.0-skills-agents`:

- `a2f85e57a4363bf5752678c3368fb5ef270668df` adds the chat-pipeline regression and Inspector UI test.
- `2f5f3613fddca6e19f510d68b2529d48f5ccfa48` corrects Compose test imports. This is the tested code SHA.

No production source, retrieval, permission, routing, persistence or model behavior changed. The chat test uses synthetic relevant and routine memory cases. It verifies a material token-budget omission creates understandable chat notice text, the context diagnostic identifies `TOKEN_BUDGET`, and a below-relevance omission remains diagnostic without adding the warning. The Inspector test seeds synthetic memory and checks the displayed `id · reason` while ensuring the dropped body is not displayed.

## HOST environment and attempts

Both Gradle invocations were filtered and offline; total wall time was approximately 157 seconds, within the 300-second contract budget. The environment used existing tools and cache only:

- Linux x86_64; Gradle Wrapper 8.10.2.
- Gradle runtime: Temurin JDK 17.0.20.1 at `/workspace/.toolchain/jdk-17`.
- Test worker: Temurin JDK 21.0.12.1 at `/workspace/.toolchain/jdk-21-temurin`.
- Existing Android SDK `/workspace/.toolchain/android-sdk`, API 35; NDK 27.0.12077973 with `source.properties` present.
- Existing Gradle user cache, offline mode. No install, cleanup or cache copy.

1. **Attempt 1**, code SHA `a2f85e57a4363bf5752678c3368fb5ef270668df`, ran from `2026-10-13T22:03:25Z` to `22:04:16Z`, exit 1 (about 51 s). `:app:compileDebugUnitTestKotlin` stopped on unresolved Compose test imports `assertDoesNotExist` and `waitUntil`; test execution did not start and no JUnit XML was generated. This was a test-harness compile error, not a product failure. The imports were corrected in the next test-only commit.
2. **Attempt 2**, code SHA `2f5f3613fddca6e19f510d68b2529d48f5ccfa48`, ran from `2026-10-13T22:04:51Z` to `22:06:37Z`, exit 1 (106 s). `:app:compileDebugUnitTestKotlin` passed; filtered `:app:testDebugUnitTest` ran the two selected test cases. XML reports two testcases: one pass and one runner-setup failure, no errors or skips. `ChatSessionPipelineTest.materialContextOmissionWarnsWhileRoutineOmissionStaysDiagnosticOnly` **passed 1/1**. `ContextOmissionInspectorUiTest.materialDropReasonIsVisibleInInspectorWithoutShowingDroppedMemoryText` failed before its method body: `RobolectricTestRunner.beforeTest` could not load the native runtime because `DefaultNativeRuntimeLoader.maybeCopyFonts` raised `java.io.IOException: No space left on device`. The Inspector assertion is therefore `NOT_RUN`; this XML failure is an environment/setup block, not evidence of a product defect.

At preflight and after the second run, `df` showed 1.8 GiB free on the overlay and 345 MiB free on `/tmp`; the precise mount quota or lower-level storage reason for the observed `ENOSPC` was not determined. No cleanup or retry was performed.

Evidence files preserve both raw Gradle logs, exact commands, preflight, XML and reconciliation: [preflight](preflight.txt), [attempt 1 command](attempt-01-compile-failure/command.txt), [attempt 1 raw log](attempt-01-compile-failure/gradle.log), [attempt 1 result](attempt-01-compile-failure/run-result.txt), [attempt 2 command](attempt-02-focused/command.txt), [attempt 2 raw log](attempt-02-focused/gradle.log), [attempt 2 environment/blocker](attempt-02-focused/environment-and-blocker.txt), [chat JUnit XML](attempt-02-focused/junit-xml/TEST-com.localai.workspace.ui.ChatSessionPipelineTest.xml), [Inspector JUnit XML](attempt-02-focused/junit-xml/TEST-com.localai.workspace.ui.ContextOmissionInspectorUiTest.xml), and [task/XML reconciliation](attempt-02-focused/task-outcomes-and-xml.txt). `SHA256SUMS` contains local content hashes for the complete evidence bundle. The attempt-02 selected test result is **PARTIAL / ENVIRONMENT_BLOCKED**, not a HOST gate pass and not acceptance of M00-04.

## Results by layer

- Focused chat warning test: **PASS**, 1/1.
- Focused Inspector UI test: **ENVIRONMENT_BLOCKED_BEFORE_TEST_BODY**; XML has one runner-setup failure. Inspector assertion: **NOT_RUN**.
- Attempt-02 filtered Gradle task: **FAIL / environment setup**, exit 1; attempt 1: test compilation failed before tests, exit 1.
- Full JVM suite, LiteRT compatibility suite, lint, AndroidTest compilation: **NOT_RUN**.
- Emulator/instrumentation: **NOT_RUN by this contract**; the previously recorded global emulator result remains separate (Room v1 failure and six model-dependent skips).
- Physical device/Moto G86 Power: **NOT_RUN**. APK: **NOT_GENERATED**.
- Production defect: **NOT_DEMONSTRATED**. M00-04: **NOT ACCEPTED** pending a valid Inspector UI test and coherent DoD evidence.
