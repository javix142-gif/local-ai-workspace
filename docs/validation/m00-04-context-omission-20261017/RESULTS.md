# M00-04 focused HOST validation — 2026-10-17

Status: **BLOCKED_BY_TEST_ENVIRONMENT; M00-04 remains NOT ACCEPTED**. Product failure is not established.

## Source and scope

The code under test is `fdd80c0971b5ae1637ad7e616bdb4aa8f5de8700` (tree `8f2a1bafc41f2a32181ef48dc8259efbe7ed7ef5`). Documentation HEAD before the run was `5c1189bc00fad9cb34593d411a37f23c779a5b07`; paths after the product commit were documentation/evidence only. One offline Gradle invocation selected exactly the two M00-04 regressions. No source code changed for this attempt.

## HOST result

- Command: [`command.txt`](attempt-01-focused-host/command.txt); bounded wrapper: [`supervisor-command.txt`](attempt-01-focused-host/supervisor-command.txt).
- Environment: JDK 17.0.20.1 Gradle runtime; JDK 21.0.12.1 test worker; Gradle 8.10.2; SDK `/workspace/.toolchain/android-sdk`; NDK 27.0.12077973; existing Gradle home `/workspace/.gradle-master-program-20261016`; offline. Preflight had 22 GiB free. No SDK/toolchain/cache installation or cleanup.
- Started `2026-10-17T04:09:08Z`, finished `2026-10-17T04:09:55Z`; 47 seconds; exit 1.
- Gradle: `BUILD FAILED in 47s`; 63 actionable tasks, 1 executed, 62 up-to-date. Only `:app:testDebugUnitTest` ran.
- JUnit XML: 2 testcases, 0 passed, 2 failed, 0 errors, 0 skipped. Both raw XML files and the complete Gradle log are preserved under [`attempt-01-focused-host/`](attempt-01-focused-host/).

### Failure classification

1. `ChatSessionPipelineTest.materialContextOmissionWarnsWhileRoutineOmissionStaysDiagnosticOnly` failed before its assertion because Robolectric tried to fetch `org.robolectric:android-all-instrumented:10-robolectric-5803371-i7`; `MavenArtifactFetcher` received `java.net.ConnectException: Connection refused`. Gradle used `--offline`, but this Robolectric runtime fetch is outside Gradle dependency resolution. Assertion result: **NOT_RUN**.
2. `ContextOmissionInspectorUiTest.materialDropReasonIsVisibleInInspectorWithoutShowingDroppedMemoryText` failed in `Room MultiInstanceInvalidationClient.serviceConnection.onServiceConnected` with a null `ComponentName` passed by Robolectric `ShadowInstrumentation.bindService` while Compose/Espresso drained the main looper. The Inspector assertions are not established as passing or failing. Classification: test-environment/Robolectric service-callback failure; product causality **NOT_DETERMINED**.

No `ENOSPC` appeared, the process did not hang, and no thread dump was needed. No second execution is included. This attempt does not validate M00-04's full DoD. The source backlog remains unchanged (`TODO`); M00-04 is not accepted. LiteRT, lint, AndroidTest, emulator, Motorola/device, and APK were **NOT_RUN**.

The attempt checksum manifest is [`SHA256SUMS`](SHA256SUMS); machine-readable details are in [`results.json`](attempt-01-focused-host/results.json). The previous M00-04 attempts remain under `docs/validation/m00-04-context-omission-20261013/` unchanged.
