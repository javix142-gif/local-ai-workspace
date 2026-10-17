# Normal chat generation checkpoint timeline

Status: **HOST focused and proportional build gates passed**. This is a presentation of existing diagnostics; it is not a model benchmark or a formal completion of M00-06.

## Source and change

- Base feature SHA: `585a86ce1e4c900e4c421db4f208c84ab96b21a6`.
- Product commit: `fdd80c0971b5ae1637ad7e616bdb4aa8f5de8700`.
- Tested source tree: `8f2a1bafc41f2a32181ef48dc8259efbe7ed7ef5` (same tree as the code commit). The Gradle commands ran against these source and test bytes in the working tree; the evidence files were outside the application source set.
- `PerformanceScreen` now presents the existing normal-chat generation checkpoints as elapsed time from request acceptance and delta from the preceding recorded checkpoint. It explains that spans may nest and are not additive. If the monotonic clock moves backward, the presentation marks timings unavailable rather than inventing durations.
- No model/runtime, request flow, trace collection, persistence, schema, dependency, permission or backlog source changed.

## HOST validation

Environment: Gradle Wrapper 8.10.2, Temurin JDK 17 runtime, JDK 21 configured as the `Test` task launcher (the project’s `app/build.gradle.kts` setting), JDK 17 and 21 toolchain discovery, Android SDK `/workspace/.toolchain/android-sdk` (API 35 installed), existing `GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016`, offline mode. No component or dependency was installed.

1. Focused unit test passed: 2 tests, 0 failures, 0 errors, 0 skipped; Gradle exit 0 in 252 seconds. The XML reports 0.046 seconds of test-method time. It covers elapsed/delta calculations in event order and invalidation after a monotonic-clock reset. Full command and unmodified output: `attempt-01-generation-timeline/command.txt`, `gradle.log`; JUnit: `attempt-01-generation-timeline/xml/TEST-com.localai.workspace.performance.GenerationTimelinePresentationTest.xml`.
2. Proportional gate passed: `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`; exit 0 in 636 seconds (`BUILD SUCCESSFUL`, 145 actionable tasks, 37 executed and 108 up-to-date). Lint reports 0 errors, 77 warnings, and 1 information finding; none refers to the changed files. AndroidTest was compiled only; tests were not run. Full app JVM/LiteRT suites were not run for this increment.

Commands, environment, timestamps, exit codes, full Gradle logs, JUnit XML and lint report are preserved in the two `attempt-*` directories. A transient Kotlin daemon startup message appears in the focused log; Gradle recovered and exited successfully.

## Local APK output and update compatibility

`assembleDebug` produced the ignored local output `app/build/outputs/apk/debug/app-debug.apk` (157,965,163 bytes; SHA-256 `5dda27f74eb1e54c48076a22b6f8f1e90dedda1c01c3764d8ffb5169b0fd5938`). It verifies with APK Signature Scheme v2, package `com.localai.workspace.debug`, version `0.5.0-debug` / code 27, but contains `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Its certificate SHA-256 is `3f0dc5e9a575d54f7e18cc9ecfe130c85f49353da2ae6bab9fd579b645b35e5e`, which differs from the previously recorded personal update certificate `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Therefore this universal local build is **not delivered as an update APK**. No signing key was replaced or exported, and no user data/device was touched.

## Exact-SHA automation readback

The push triggered Android emulator run [39000019030](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39000019030), job `117300768724`, for code SHA `fdd80c0971b5ae1637ad7e616bdb4aa8f5de8700`. The pinned SDK/NDK/CMake, KVM, AVD creation/preflight and boot passed. ADB saw `emulator-5554`; `getprop` reported API 35 / Android 15 / x86_64. The command `./gradlew :app:connectedDebugAndroidTest --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain` exited 1 after 13m29s.

The JUnit XML is internally consistent at 16 cases: 9 passed, 1 failed, 6 skipped, 0 errors; 10 cases executed. The failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` because the authentic `WorkspaceDatabase/1.json` fixture is absent. The six skipped tests are the two LiteRT and four native-chat tests that require real model weights; no model was downloaded. Two separate collection discrepancies remain visible: Gradle progress starts at 16 but ends at `22/16` / “Finished 22 tests”, unlike the 16 XML cases; and the post-task package check reports `com.localai.workspace.debug` not installed. Their cause is `NOT_DETERMINED`; neither discrepancy is silently reconciled with the XML.

The existing ChatHeader assertions passed in the XML, but only the standard screenshot was recovered. It is a valid 1080×1920 PNG (47,792 bytes, SHA-256 `214f9e93a5f68d47b1e2825fcf0bc4727f91e0d60073c024746673397441084f`). The large-font capture was incomplete and is absent from the artifact, so that visual capture requirement did not pass. Overall `EMULATOR=FAIL`, not a pass for the timeline screen or for model performance.

The official QA run [39000019176](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39000019176), job `117300769295`, waited for this exact-SHA instrumentation run and then failed closed. JDK setup, APK build and upload were skipped; the run produced no artifact and no APK. The local universal Debug build described above is not an update deliverable because its signer differs from the previously recorded personal update certificate. No manual personal-QA dispatch was made.

Artifact `12089112333`, `android-emulator-evidence-39000019030-1`, is 81,861 bytes with API SHA-256 `626cbd6a5e85782dbd4e4d3f3c355ba5297da0fed219847843b62bdde50067fc` and expires `2026-10-24T04:00:59Z`. ZIP CRC passed; all 31 entries in the internal checksum manifest were verified. The manifest SHA-256 is `a0a9f5995147b59869470ebb7ebf1bedef771e3d3247f68708d7b840e18f198e`. The raw emulator job log (336,279 bytes; SHA-256 `d0e77115f7f7c5bf7167c9d17aac575ca4bc201fb06e381ed51573eb1b86dc85`) and QA gate log (17,324 bytes; SHA-256 `0ed2417cb973af220870fe1c89f393cda02383c77dcbe01e46cdd43d92bccf1e`) are preserved with the ZIP and extracted files under `automatic-ci-39000019030/`. `RUN_READBACK.json` records metadata, outcomes and the remaining count discrepancies.

`PHYSICAL_DEVICE=NOT_RUN`. No model was loaded, and TTFT, tokens/s, RAM/PSS, quality, thermal behavior and energy were not measured. M00-06 remains source `TODO`: this addition only presents existing checkpoints and does not supply the full import-to-response/device timeline in its original definition of done. The canonical source backlog and roadmap remain unchanged: 97 task IDs, 120 dependencies, all source statuses `TODO`.
