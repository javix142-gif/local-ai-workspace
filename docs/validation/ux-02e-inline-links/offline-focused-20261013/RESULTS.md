# UX-02e offline focused reproduction — 2026-10-13

## Outcome

**Focused target PASS (1/1), independently audited for this target only; the full HOST gate remains incomplete and UX-02e is not accepted overall.** Tested source SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, in a detached worktree at `/workspace/ux02e-offline-a182`. Feature branch HEAD at contract start was `f6e1b642da5f77010203157b52eadd9d5bad0bc1`. `main` and peeled `v0.4.2` were `de615b772cf37a99e7e08a647c59607b271de606`.

The target was `ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit`. Gradle ran in offline mode with the existing `/home/agent/.gradle` cache. Preflight also found the API 35 Android artifact in Maven local, but the tracked repository does not declare `mavenLocal()` and this report does not attribute that artifact as Gradle's source. The successful offline execution proves that all dependencies required by this target resolved from Gradle's available local state. No online fallback, component installation, cache cleanup, or data cleanup was performed. The isolated test did not reproduce the previous full-suite stall. That earlier stall's cause remains **NOT_DETERMINED**; no product failure is established.


## Independent audit scope (2026-10-13)

The orchestrator independently accepted this **focused target result only** as `INDEPENDENTLY_AUDITED_ACCEPTED_FOCUSED_RESULT_ONLY`. At audited feature HEAD `d160d26372c6576d75fe39a6e8ae2b65ff1d51bf`, the 32-path comparison from `f6e1b642da5f77010203157b52eadd9d5bad0bc1` contained documentation paths only; tested code remained `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. The JUnit result remains one test passed, with no failures, errors, or skips. The full HOST gate was not completed: LiteRT compatibility tests, lint, and AndroidTest Kotlin compilation were not run. Therefore UX-02e remains **NOT ACCEPTED overall**. The earlier full-suite stall cause remains `NOT_DETERMINED`; no `PRODUCT_FAILURE` is established.

For the independent hash audit, the auditor recalculated five text payloads under this bundle—`RESULTS.md`, `command.txt`, `jdk-preflight.txt`, `test-events.tsv`, and the JUnit XML—and matched them to the 26-entry bundle manifest. The auditor also checked the bundle-manifest SHA-256/count and the outer-manifest SHA-256/count (142 entries). The `.gz` log payloads and all other outer-manifest payloads were **not** independently rehashed by the auditor. The documentation closure regenerated both manifests; their post-update hashes and local manifest-check results are recorded in the root RESULTS/state/handoff.

## Execution record

- Source SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`.
- Worktree: detached, clean including ignored files before execution. Preflight: [`preflight.txt`](preflight.txt). After execution, Git reported only generated ignored `.gradle/` and module `build/` directories; no tracked or untracked source changes. These outputs were left in place; nothing was cleaned. See [`post-run-worktree-and-processes.txt`](post-run-worktree-and-processes.txt).
- Gradle: wrapper 8.10.2, Temurin JDK 17.0.20.1 runtime, Temurin JDK 21.0.12.1 test-worker toolchain, both exposed via `org.gradle.java.installations.paths`. Android SDK/NDK were existing installations; no Android component installation was requested. Details: [`jdk-preflight.txt`](jdk-preflight.txt), [`environment.txt`](environment.txt), and [`source-and-dependency-inspection.txt`](source-and-dependency-inspection.txt).
- Existing Gradle modules cache: 753 MiB. Robolectric 4.14.1 artifacts were present; the exact API 35 `android-all-instrumented-15-robolectric-12650502-i7.jar` was present in Maven local (199,527,464 bytes; SHA-256 `33c2d7cc4186d4605269d5d411fa1818972c5b30a9f70bfbbd3d430b076af52f`). The successful `--offline` run demonstrates that the artifacts required by this target's execution path resolved from the existing local stores.
- Exact Gradle argv: [`command.txt`](command.txt); process environment: [`environment.txt`](environment.txt). A temporary init-script listener records actual JUnit `TEST_START`/`TEST_END` events: [`test-events.init.gradle`](test-events.init.gradle).
- Start/end: `2026-10-13T16:37:19.330Z`–`2026-10-13T16:38:34.298Z`; duration 74.968 s; Gradle wrapper PID 138242; exit code 0; no timeout.
- Test start/end events: `2026-10-13T16:38:22.149Z` / `2026-10-13T16:38:33.088Z`; test worker launched from `/workspace/.toolchain/jdk-21-temurin/bin/java` and completed. Since the test completed normally, no timeout process snapshot or thread dump was needed.
- JUnit XML: [`xml/TEST-com.localai.workspace.context.ContextFoundationDeduplicationTest.xml`](xml/TEST-com.localai.workspace.context.ContextFoundationDeduplicationTest.xml), **1 test, 1 passed, 0 failures, 0 errors, 0 skipped**; XML duration 10.987 s. Listener events and XML agree.
- Gradle task: `:app:testDebugUnitTest` **PASS** for the single filtered test; Gradle reported `BUILD SUCCESSFUL in 1m 14s`. No other requested suite, lint, or AndroidTest task was run.
- Raw Gradle log SHA-256: `a9906c4b5c00655cd40db25a87a5a8ec6859258e6005a0e7c42d8cdde6ca9362`; size 383,464 bytes. Lossless gzip SHA-256: `d199a195aa1356179f9b42d8936f624d8c5f306080283dee0708762d34d96b71`; compressed size 30043 bytes. Event log SHA-256: `9531bd9f71247b9097adedc948b5801296aff9fa5b775551527397cfacc52e0e`; XML SHA-256: `89c65755735543de3c63e6e1ac37f43ebe4c4e33fa0ab4b962980cffd0777fdc`. All evidence-file hashes are in [`SHA256SUMS`](SHA256SUMS); the parent manifest covers this bundle and is regenerated at publication.

## Invocation preparation note

An initial command-order check put `--tests` before the task. Gradle printed CLI usage and exited 1 after 2.130 s; it started zero Gradle tasks, created no test worker/events/XML, and performed no dependency resolution. Its exact output and metadata are retained as `cli-syntax-check-*`. The command was corrected by placing `--tests` after `:app:testDebugUnitTest`; the single actual target execution above then ran once and passed. The syntax check is not counted as a test or build result. See [`invocation-preparation-note.md`](invocation-preparation-note.md).

## Classification and boundary

| Item | Result |
| --- | --- |
| Offline dependency availability for this focused target | Available from existing local stores; verified by successful offline execution |
| Target test | **PASS, 1/1** |
| Earlier full-suite Robolectric stall | Not reproduced by this isolated run; cause remains **NOT_DETERMINED** |
| Product failure | **NOT_ESTABLISHED** |
| Full HOST gate | **INCOMPLETE**; this one test does not replace the full gate |
| UX-02e acceptance | **NOT ACCEPTED overall**; the independent audit accepts only this focused target, while the full HOST gate remains incomplete |
| EMULATOR / PHYSICAL_DEVICE / APK | **NOT_RUN / NOT_RUN / NOT_GENERATED** |
| Other tests, lint, AndroidTest | **NOT_RUN** in this contract |

No Android, emulator, device, or APK operation took place. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97-task catalog was not available, reconstructed, or changed. This contract ends after this one offline target reproduction; no additional test run is made here.
