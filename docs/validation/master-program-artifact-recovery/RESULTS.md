# Artifact API recovery and SAF export — focused host evidence

**Status:** focused HOST tests pass; preparatory evidence only. Original task **M03-01 remains TODO**, because its source prerequisite M02-05 remains TODO. No original task, dependency, or catalog status is promoted.

Code was changed only in tests. The focused changes add a process-reopen recovery regression for an immutable code-artifact revision and a Compose/SAF regression for exporting the user-selected exact bytes and verifying their readback hash. The final tested code SHA is `dfff07ec9965d774af90b146156b45072063e4bf`. Code commits are `ddb4f798c6d4ee3407140fa0028742db246ca678` (recovery), `908edd76e483efc55857d5a89f465c975ccadfbe` (SAF test), `6a99135d61f2c7c9a8060ed8f15c97643a6fcedc` (scroll export action into view), and `dfff07ec9965d774af90b146156b45072063e4bf` (fix the test's completion wait). No production source, database schema, dependency, or runtime changed.

## Final focused run

The exact command, complete Gradle log, environment, exit code, two JUnit XML files, and hashes are under [attempt 07](attempt-07-combined-focused/). Command:

```sh
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain \
  -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin \
  :app:testDebugUnitTest \
  --tests com.localai.workspace.artifacts.ArtifactPatchRecoveryTest \
  --tests com.localai.workspace.ui.ArtifactSafExportUiTest
```

On code SHA `dfff07e`, Gradle 8.10.2 ran with Temurin JDK 17 and JDK 21 test workers, existing Android API 35/NDK 27.0.12077973, and the existing offline Gradle cache. It completed in 40 seconds (exit 0; one task executed, 62 up-to-date). XML records 2 tests, 2 passes, 0 failures, 0 errors, 0 skips; the two classes each report 1/1. Their XML SHA-256 values are recorded in the attempt-local manifest.

`ArtifactPatchRecoveryTest.applyingAfterCommittedRevisionAndProcessRestartCleansPendingPreviewIdempotently` checks that a reopened store recognizes the committed immutable revision, removes the stale pending preview idempotently, preserves version 1 bytes, returns version 2 bytes, and does not leak the preview into another project. `ArtifactSafExportUiTest.explicitCreateDocumentChoiceExportsExactVerifiedBytesAndReadsThemBack` drives the actual `CreateDocument` launcher through a test registry and content resolver; it verifies the suggested filename, byte-for-byte output, the successful readback status, and SHA-256 equality.

## Attempt history

| Evidence | Result | Interpretation |
|---|---|---|
| [attempt 01](attempt-01-exact-source/) at `ddb4f79` | PASS 1/1; Gradle exit 0 | Recovery branch exercised. The original run log is retained; its XML was not copied into that attempt directory. The final combined run supplies preserved XML for the same regression. |
| [attempt 02](attempt-02-saf-exact-source/) | FAIL in test setup | The Export button was below the scroll viewport. No production failure was established. |
| [attempt 03](attempt-03-saf-visible-action/) | FAIL in completion wait | The button was made visible; the polling assertion did not observe completion. |
| [attempt 04](attempt-04-saf-callback-diagnostic/) | Shell exit 127, before Gradle | Environment logging referenced unset `ANDROID_HOME`; no test ran. |
| [attempt 05](attempt-05-saf-callback-diagnostic/) | Test assertion failed on a modified test worktree based at `6a99135` | The uncommitted diagnostic-only test patch is preserved and hashed. It reported `requestedName=answer.kt`, `readbackBytes=51`, and UI status “Exported and read back: answer.kt · v1”. The export completed; the test's Compose assertion inside the polling predicate was faulty. This was not a product failure. |
| [attempt 06](attempt-06-saf-export-fixed-test/) at `dfff07e` | PASS 1/1; Gradle exit 0 | Polling now waits for the exact output byte count, then asserts the final status and digest. |
| [attempt 07](attempt-07-combined-focused/) at `dfff07e` | PASS 2/2; Gradle exit 0 | Final source-bound run for both regressions; preserved XML and raw log. |

The final run is targeted, not the full JVM suite or the complete HOST gate. `lintDebug`, `assembleDebug`, AndroidTest compilation, emulator, physical Motorola, and APK generation were **NOT_RUN** in this increment. The previously recorded global EMULATOR result remains FAIL for the historical Room v1 fixture and six model-dependent skips; PHYSICAL_DEVICE remains NOT_RUN. M01-06 remains PENDING_REQUIRES_DEVICE. No claim is made for physical SAF behavior, actual Office viewers, or data durability under device power loss.

The original backlog/roadmap files are unchanged: 97 IDs, 120 dependency edges, all source statuses TODO. M03-01 depends on M02-05 and stays TODO; M03-02 also remains TODO and requires physical validation. The next preparatory action is a source-grounded inspection of the existing XLSX adapter/tests for a bounded host-verifiable M03-02 gap, without claiming its device DoD.
