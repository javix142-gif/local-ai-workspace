# ArtifactStore recovery and SAF export — focused host evidence

**Status:** focused HOST coverage passes for the recorded attempts, with an unresolved outer-executor exit-status discrepancy on the latest two calls; preparatory evidence only. Original task **M03-01 remains TODO**, because its source prerequisite M02-05 remains TODO. No original task, dependency, or catalog status is promoted.

The original attempts 01–07 added the process-reopen recovery regression for an immutable code-artifact revision and a Compose/SAF regression for exact user-selected bytes and readback hash. That historical slice changed tests only; its tested source was `dfff07ec9965d774af90b146156b45072063e4bf`. A later, separate follow-up below adds narrowly scoped production recovery for crash-abandoned `.pending-UUID` revision staging directories.

## Earlier API/SAF focused run (attempt 07)

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

## Workspace continuity and abandoned ArtifactStore staging recovery — 2026-10-19

### Recovery readback

The working repository is available at `/workspace/local-ai-workspace`; it was not necessary to reconstruct it from a new clone. A fresh fetch showed the feature branch and the already-published `recovery/backup-b8e75b4` ref both at `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`. The existing implementation branch `codex/master-program-20261019` is separate and descends from that source state; its remote checkpoint before this follow-up was `1c252cce243b33e541f26755f35f51d0df806466`. No extra branch was created because the persistent backup and implementation branches already existed.

The reported local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` is **NOT_RECOVERED**. `git cat-file`, refs, reflogs, and a read-only `git fsck --unreachable` found no such commit or unreachable commit/tag. A bounded `/workspace` filename search found no matching commit-named file, `.patch`, `.diff`, or backup file. No source was reconstructed. Existing M03-07 implementation and evidence remain in the current branch, so the lost commit is not needed to restore the already-published work.

M03-07's latest source-backed preparation is still the bounded line-diff preview at `fad7acb0663485e2bd6ec86cf76e36b85a9baf57`: its focused class passed 2/2. The only lint attempt exited 1 before `:app:lintDebug`, while a pinned KleidiAI source download returned proxy HTTP/2 503. Lint is therefore NOT_RUN, not PASS. M03-07 remains source TODO, requires a device, and depends on M03-01 TODO. This follow-up did not rerun M03-07 or its lint attempt.

### M03-01 staging-recovery defect and fix

The regression was committed first at `c87228c3fd7d076f5116bed92c09efd2f468dfcc`. Its baseline run reproduced a crash-recovery gap: `ArtifactStore.list()` returned the committed revision but left the abandoned `.pending-UUID` directory on disk (1 test, 1 expected failure, exit 1; `attempt-08-staging-red/`).

Production commit `26ff528e02be5e9cdcc17ebb776232a78556dae6` adds cleanup only for exact UUID-named staging directories directly inside UUID-named artifact folders. `list()` attempts cleanup only while it can take the existing project write lock; if another writer holds the lock, it lists committed revisions and leaves staging intact. `commit()` performs the same scoped cleanup after acquiring that lock. The recursive removal uses `Files.walkFileTree` without following symlinks and validates the canonical parent paths. Published `vN` revisions and unrelated entries are not cleanup candidates. Test commit `3015ebec721bb6f66a6d19cc57947ae442d37ed4` adds a regression proving a live writer's pending folder is preserved until its lock is released.

| Attempt | Exact code SHA | Result |
|---|---|---|
| Red baseline | `c87228c3fd7d076f5116bed92c09efd2f468dfcc` | 1 test, 1 expected failure at the missing staging-cleanup assertion; Gradle exit 1. |
| Initial green | `26ff528e02be5e9cdcc17ebb776232a78556dae6` | 2/2 pass, Gradle marker exit 0, `BUILD SUCCESSFUL` in 3m31s; the outer execution session reported 1. |
| Final focused class | `3015ebec721bb6f66a6d19cc57947ae442d37ed4` | 3/3 pass, 0 failures/errors/skips; Gradle marker exit 0, `BUILD SUCCESSFUL` in 40s, 3 tasks executed/60 up-to-date. The outer execution session again reported 1. |

The outer-session discrepancy is unresolved and preserved in each attempt's `outer-executor-status.txt`; no cause is inferred. The final Gradle log, shell-captured exit marker, and fresh JUnit XML agree on success. Environment: Gradle 8.10.2, JDK 17 runtime (`/workspace/.toolchain/jdk-17`), JDK 21 toolchain (`/workspace/.toolchain/jdk-21-temurin`), Android SDK API 35, NDK 27.0.12077973, existing `/workspace/.gradle` cache in offline mode. Exact command, UTC timestamps, environment, pre/post XML inventory, task outcomes, JUnit, losslessly compressed Gradle logs (`.log.gz`, with original and archive hashes) and manifests are in `attempt-08-staging-red/`, `attempt-09-staging-green/`, and `attempt-10-staging-green-final/`. The final test body took 5.423 seconds. This was one focused test class, not the full JVM/LiteRT/lint/build gate.

No Room schema, dependency, model/runtime, permission, or plan-source file changed. Full JVM/LiteRT, lint, AndroidTest compilation, emulator, Motorola and APK were NOT_RUN for this increment. The previously recorded global EMULATOR result remains FAIL for the authentic Room v1 fixture plus six model-dependent skips; `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains PENDING_REQUIRES_DEVICE. Source M03-01 remains TODO because M02-05 is TODO; M03-07 remains TODO/device-gated. The original catalog remains 97 unique IDs, 120 edges, all 97 source statuses TODO.
