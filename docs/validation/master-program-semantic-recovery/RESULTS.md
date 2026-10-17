# Preparatory M01-06: restart recovery for Semantic V2 index jobs

## Scope and source status

Code base: `77f86c0756193184d269b98701753beac55c54f7` on the authorized development feature. Tested source tree: `2313d77ab258beb6dd72b940a28da60772656adc`. Product commit: `b48338c557a94351157de18378a5221d00a4ba43`.

The authentic source entry is M01-06, “Ingesta e indexación reanudable”; the source backlog still says `TODO`, `requires_device=true`, `depends_on=[M00-02]`, and its DoD is “Jobs cancelables con checkpoint y recuperación post-reinicio; no bloqueo de UI ni duplicación.” This is preparatory HOST evidence only. M00-02 is not formally closed, no original task is marked DONE, and the M01-06 device requirement remains open.

## Observed gap and implementation

`SemanticLayer.recover()` existed but had no startup caller. Its DAO update also marked an interrupted `INDEXING` job with the same generic error as a user cancellation. The focused red test reproduced this: expected `PROCESS_RESTART_RECOVERABLE`, observed `INDEX_JOB_CANCELLED`.

The change now distinguishes interrupted jobs with the existing job error metadata and timestamp, serializes idempotent recovery against index/model operations, runs recovery from the app's existing IO startup coroutine, and ensures recovery precedes import/index entry points. A re-index creates a new generation while the old interrupted job remains inspectable; exact content-addressed stored vectors can be reused, so the tested fixture does not recompute the embedding. The existing Semantic V2 status line explains that the user can re-index to reuse matching vectors. No database schema, migration, model, runtime, dependency, retrieval scope, or persisted document content changed.

This does not autonomously resume a background job after process death and does not implement the complete source DoD. It demonstrates recoverable classification plus deterministic reuse on an explicit re-index path.

## Host attempts

Environment: Linux; Gradle Wrapper 8.10.2; JDK 17 Gradle runtime (`/workspace/.toolchain/jdk-17-temurin`); JDK 21 test worker/toolchain discovery (`/workspace/.toolchain/jdk-21-temurin`); existing Android SDK API 35, NDK `27.0.12077973`, CMake `3.31.6`; existing `GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016`; offline; one Gradle worker. No tools, dependencies, models, or SDK packages were installed.

1. `attempt-01-red-import-correction`: test compilation failed because the new harness imported Semantic V2 types from the wrong package. Product test body NOT_RUN.
2. `attempt-02-red`: JUnit test initialization failed because a Kotlin `@After close()` inferred Boolean instead of `Unit`. Product test body NOT_RUN. Fixed the harness signature.
3. `attempt-03-red-product`: the intended regression ran and failed: recovery stored generic user-cancel metadata. One test, one assertion failure, zero errors/skips; Gradle exit 1. This is the product reproduction.
4. `attempt-04-three-class-run-api29-network-blocked`: 11 cases across three classes: the restart test and two label tests passed; the API 29 `SemanticStorageTest` had seven Robolectric bootstrap failures fetching uncached `android-all-instrumented:10-robolectric-5803371-i7` after repository connection failures; one case passed. Gradle exit 1. This attempt is infrastructure-blocked overall and is not counted as a green gate.
5. `attempt-05-focal-api35`: exact filtered classes `SemanticIndexRestartRecoveryTest` and `SemanticIndexJobLabelTest`; 3/3 passed, no failures/errors/skips; Gradle exit 0. XML files are preserved in this folder.
6. `attempt-06-lint-androidtest-compile`: `:app:lintDebug :app:compileDebugAndroidTestKotlin`; Gradle exit 0, BUILD SUCCESSFUL in 4m59s, 121 actionable tasks (17 executed, 104 up-to-date). Lint report: 0 errors, 77 warnings, 1 information; no issues point to the changed files. AndroidTest Kotlin compiled; it did not execute instrumentation.

The exact commands, raw logs, JUnit XML, timestamps, task outcomes, source tree IDs and environment are stored under the numbered attempt directories. The failed attempts are retained and not relabeled as passes.

## Existing Android automation on the base SHA

Run `38992803542` belongs to source SHA `77f86c0756193184d269b98701753beac55c54f7`, before this code change. Its API 35 x86_64 AVD and ADB worked. The XML had 16 cases: 8 passed, 2 failed, 6 skipped, 10 executed; Gradle exit 1. Failures were the authentic missing Room schema v1 and `AgentsSkillsNavigationTest.generalAgentIsProtectedAndValidationIsDeveloperOnly` failing to locate “General Agent”. Six model-dependent tests were skipped. The downloaded 152,249-byte artifact's SHA-256 is `ab8e1e02dca5aa947eed8bae0c2da097cc6f38753ca1c737aa45e7aaa6842e3a`; ZIP CRC and its 32-entry internal hash manifest verified. Same-SHA official QA run `38992803632` failed closed at the instrumentation wait, skipped APK build/upload, and has no artifact. Details: [baseline run review](automatic-ci-38992803542-review.md).

The current code SHA's push-triggered emulator/QA runs are recorded separately in `EXECUTION_STATE.json` after their completion. The base run above is not counted as validation of the new code.

## Limits and preservation

- HOST focused tests: PASS 3/3 on the code tree; lint PASS; AndroidTest Kotlin compile PASS.
- Full app JVM suite and full LiteRT compatibility suite: NOT_RUN for this code tree. An attempted three-class combination had the API 29 Robolectric dependency fetch failure above.
- EMULATOR/connected instrumentation: recorded per exact SHA; no global PASS is claimed here.
- PHYSICAL_DEVICE: NOT_RUN. Motorola Moto G86 Power and the original private XLSX remain unavailable to this executor.
- Neural TTFT, tokens/s, memory, thermal, energy or quality improvement: NOT_MEASURED. The synthetic vector driver only verifies stored-vector reuse without another embedding call.
- No APK generated or delivered for this small preparatory increment. A prior personal Debug signer is not present locally; generating with an unrelated ephemeral signer could conflict with the installed package. No data-clearing/uninstall advice is implied.
- Canonical source backlog/roadmap unchanged: 97 IDs, 120 edges, all 97 source statuses TODO. Their original hashes remain in `docs/development/EXECUTION_STATE.json`.

Evidence manifest: `SHA256SUMS` (covers attempt payloads, not this report or the manifest itself).

## Exact-source automatic emulator/QA results for M01-06 code (2026-10-17)

The push-triggered runs below tested the M01-06 recovery code commit `b48338c557a94351157de18378a5221d00a4ba43`, not its later documentation-only commits and not the newer M00-06 timing correlation commit.

- **EMULATOR: FAIL after real connected instrumentation.** Run `38994652751`, job `117284408169`, API 35 / Android 15 / x86_64 (`sdk_gphone64_x86_64`), AVD and ADB connected; task `:app:connectedDebugAndroidTest`, Gradle exit 1. JUnit XML has 16 total: 9 PASS, 1 FAIL, 0 errors, 6 SKIPPED, 10 executed. The sole failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`, whose authentic historical `WorkspaceDatabase/1.json` fixture remains unavailable; no schema was fabricated and the test was not changed. The six skips are the two LiteRT and four Gemma/native tests guarded by model availability; no weights were downloaded. The post-task package inventory says packages are absent after the test task, which is recorded only as a post-task observation, not evidence that installation did not occur during testing.
- Run URL: <https://github.com/javix142-gif/local-ai-workspace/actions/runs/38994652751>.
- Evidence artifact `12086264959`, 151,578 bytes, SHA-256 `095df1a306656c546a3295f3c36d38ee1bbfbd88262ef9242153cd9398126bb0`; local ZIP CRC check passed and the internal 32-entry payload manifest was independently verified. Evidence is in `automatic-ci-38994652751/`.
- **Official QA gate: FAIL_CLOSED_NO_APK.** Run `38994652879`, job `117284408950`, URL <https://github.com/javix142-gif/local-ai-workspace/actions/runs/38994652879>. It observed the exact-SHA emulator failure, exited before APK build/upload, and produced no artifact.
- `PHYSICAL_DEVICE=NOT_RUN`; no APK generated. These runs do not complete M01-06 and do not establish Motorola behavior.

Evidence payload envelope: `SHA256SUMS` covers 99 files in this directory (excluding this top-level report and the envelope manifest itself); manifest SHA-256 `febfe0788ecfead95508e50ad85a6ad095c2a3bdb6475ab93206d31102ac2e3b`. Local `sha256sum -c` passed for every listed entry.
