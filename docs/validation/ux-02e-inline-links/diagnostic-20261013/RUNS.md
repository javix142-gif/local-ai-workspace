# UX-02e focused Robolectric diagnosis — 2026-10-13

**Code under test:** `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` (detached worktree). No product files changed.

## Outcome

Two distinct, bounded test executions completed within the 8-minute limit. `ChatContentUiTest` passed 15/15 (exit 0; 167.004 s). `AuditHarness.writeSelectorReproductions` passed 1/1 (exit 0; 153.034 s). The prior 3311-second stall did not reproduce in either isolated target. This does **not** identify the original active method or root cause, does not validate the full host gate, and does not accept UX-02e. No defect is attributed to UX-02e.

The preceding compressed gate log's last named output marker is `AuditHarness > writeSelectorReproductions STANDARD_OUT`; this is stdout attribution, not a JUnit start event. The following JDK 21 dump has a generic JUnit/Robolectric runner stack parked in `Sandbox.runOnMainThread` and contains no class/method identity. The focused rerun of that named method passed. Therefore the original stall remains undiagnosed (possibly order-dependent or environmental); no cause is inferred.

## Run 1 — Compose UI class

- Target: `com.localai.workspace.ui.ChatContentUiTest`
- Start/end: `2026-10-13T11:40:20Z` – `2026-10-13T11:43:07Z`; duration 167.004 s; process exit 0; `BUILD SUCCESSFUL` (Gradle reported 2m46s).
- PID: Gradle client 131709; Gradle daemon PID 131741 in listener; test worker PID 131884, executable `/workspace/.toolchain/jdk-21-temurin/bin/java`. (The listener PID field is the Gradle daemon, not the test worker.)
- XML: one JUnit file, 15 tests, 0 failures/errors/skips. All 15 method cases are recorded in the XML.
- The monitor's raw `RUN_END` says XML=0 because it looked in a custom output path that AGP overrode. The Gradle log states that the stale default results folder was deleted for this invocation; the actual default-path XML is timestamped inside the run and has 15 cases. The corrected monitor summary is preserved beside the original observer log.

## Run 2 — previously visible harness method

- Target: `com.localai.workspace.context.AuditHarness`, method `writeSelectorReproductions`.
- Start/end: `2026-10-13T11:43:57Z` – `2026-10-13T11:46:30Z`; duration 153.034 s; process exit 0; `BUILD SUCCESSFUL` (Gradle reported 2m30s).
- PID: Gradle client 131961; worker PID 132126, executable `/workspace/.toolchain/jdk-21-temurin/bin/java`; Gradle daemon PID 131994 as recorded in the test event listener.
- XML: one JUnit file, 1 test, 0 failures/errors/skips. Listener records `TEST_START` and `TEST_END SUCCESS` for the exact method.
- The log contains `Network is unreachable` dependency-resolution retries, but the task and test completed successfully; this is not established as the cause of the earlier stall.

## Environment and command

Both runs used Gradle Wrapper 8.10.2 with Temurin JDK 17.0.20.1 as the Gradle runtime (`/workspace/.toolchain/jdk-17`) and explicitly exposed JDK 17 plus Temurin JDK 21.0.12.1 (`/workspace/.toolchain/jdk-21-temurin`) through `org.gradle.java.installations.paths`. The Gradle `--info` launch line and worker process record confirm that test workers used JDK 21. Existing SDK path was `/workspace/.toolchain/android-sdk`; Android environment variables were set to that path. The existing Gradle user home/cache was not cleaned. The detached source worktree was at the exact code SHA; ignored build outputs from earlier work already existed and were not cleaned/copied. No toolchain or dependency installation was requested.

The common command was the exact command in each `command.txt`; the execution wrapper set `JAVA_HOME=/workspace/.toolchain/jdk-17`, prepended its `bin` to `PATH`, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` to `/workspace/.toolchain/android-sdk`, set `M00_SOURCE_COMMIT=a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, and provided the per-run event path as `UX02E_TEST_EVENTS`. The temporary init script was first run from `/tmp`; its exact bytes are archived here as `test-events.init.gradle`, SHA-256 `ee80bdbe033952bddd681ff6bba1f6e89b72a1d45ad3fbbd98709e7689bfca3d`.

## Key evidence SHA-256

- Run 1 Gradle log raw SHA-256 `b05ebf296ac87be5fc14040ed9f6ec04f05ed4ad85544eadce249304c6d4d37c`, stored losslessly as `chat-content-ui/gradle.log.gz` (compressed SHA-256 `904b22c680b2eff519d38fc003c8b884c455b4d8b63a9ff5953699de978705a4`); observer raw SHA-256 `4bdd4577fc2c405bcdec0039675cf18d8612eaea0c8372935b31e67c7736df63`, compressed at `chat-content-ui/observer.log.gz` (SHA-256 `0a58e97a9e75e49ee1c7ebf4a2fca464dc100902cf9d9ce1abe3662ead69944e`). JUnit XML: `cfb455381339744525747245d957854671ab05cf05ce2208535d76ce9ae9a4c2`; test event stream: `a08817d96bacb5b83e0280c083b9be0686717189768ed528dd174a2f13926aeb`.
- Run 2 Gradle log raw SHA-256 `168b5f7f53a71e301e3c596ca33acabdd2e0be6c31e146787a012ce715e91f72`, stored losslessly as `audit-harness/gradle.log.gz` (compressed SHA-256 `35de01f9a98e469db36b990ad348ec75cf51cf2be69e48a60f12f4fbbf8d86f9`); observer raw SHA-256 `5e90292c4fa293ab1d7d498fc45c382a1a75bc6769ea907861e2f7f544991b84`, compressed at `audit-harness/observer.log.gz` (SHA-256 `990f1e209ed4374fd9a2455880e50aec8a3bfc3d602a39cde9e3ac66de935216`). JUnit XML: `79844d737305e1f6bff6f8621be0d16f84adf75eda4f851f86b5be8eca3ce97b`; test event stream: `373c7786ffe8794881b20f43908c29c21b106b4b25099fea055d49331144e6ff`.
- Shared temporary listener script: `ee80bdbe033952bddd681ff6bba1f6e89b72a1d45ad3fbbd98709e7689bfca3d`.
- The per-file manifest `SHA256SUMS` in this directory covers every diagnostic file except itself; the parent UX-02e manifest also covers that manifest.

## Scope boundary

These are two focused JVM test runs only; they are not the requested full host gate. LiteRT compatibility tests, lint, AndroidTest Kotlin compilation, EMULATOR, PHYSICAL_DEVICE and APK generation were not run under this diagnostic contract. The older full-gate attempt remains incomplete/exit 130; its cause is not attributed to product code. UX-02e remains pending independent audit.
