# M02 document removal and stale-index lifecycle — focused host evidence

- **Source code SHA:** `843bf24620447d749455ec9df1a05278d3cec996`.
- **Working branch:** `codex/master-program-20261016`; based on the existing work branch at `62df7fc64bfc854998178e946e731a27f449b28e`.
- **Scope:** preparatory partial evidence for original **M02-06**. The source backlog remains `TODO`; this report does not close M02-06 or its dependency M02-05.
- **Final focused JVM result:** **7/7 PASS**, 0 failures/errors/skips, across six exact-SHA Gradle invocations. The recorded wall durations sum to 472 seconds.

## Implemented behavior

The product now offers an explicit document removal flow scoped to the owning project. It removes the document's Room records, extracted segments, lexical rows and matching EG1/embedding associations, retains citation snapshots, and schedules deletion of the app's imported private copy through the existing durable cleanup outbox. The user's original external file is not modified. The Semantic V2 callback removes only that document's sources and generation links; shared vector payloads remain while another source references them. Affected active generations are marked stale. Changed-document ingestion awaits the stale mark, Semantic V2 refuses a stale generation for the turn, and the existing lexical fallback remains constrained to current documents. Reindex tests cover publishing a new READY generation and preserving rollback while a previous READY generation remains valid.

No Room schema/version, Semantic V2 database version, dependency, model/runtime, or source backlog file changed. The app does not edit the user's source document in place.

## Exact-SHA test evidence

All six final invocations record `sourceSha=843bf24620447d749455ec9df1a05278d3cec996` and exit code 0; each XML is new for its invocation.

| Focus | Test(s) | Result | Duration | Evidence |
|---|---|---:|---:|---|
| attempt-09-ui-cleanup-wait | `removingImportedFileRequiresConfirmationAndPreservesItsOriginal` | 1/1 PASS | 56 s | [`run-metadata.txt`](attempts/attempt-09-ui-cleanup-wait/run-metadata.txt), [`gradle.log`](attempts/attempt-09-ui-cleanup-wait/gradle.log), XML: `attempts/attempt-09-ui-cleanup-wait/junit/TEST-com.localai.workspace.ui.DocumentRemovalUiTest.xml` |
| attempt-10-appgraph-document-callback | `appGraphDocumentDeletionPurgesOnlyItsGenerationSourcesAndRetainsSharedVectorPayload` | 1/1 PASS | 33 s | [`run-metadata.txt`](attempts/attempt-10-appgraph-document-callback/run-metadata.txt), [`gradle.log`](attempts/attempt-10-appgraph-document-callback/gradle.log), XML: `attempts/attempt-10-appgraph-document-callback/junit/TEST-com.localai.workspace.semantic.v2.SemanticContextPostfixTest.xml` |
| attempt-11-document-lifecycle | `deleteIsWorkspaceScopedRetainsCitationAndHonorsSharedPrivatePathReferences` | 1/1 PASS | 32 s | [`run-metadata.txt`](attempts/attempt-11-document-lifecycle/run-metadata.txt), [`gradle.log`](attempts/attempt-11-document-lifecycle/gradle.log), XML: `attempts/attempt-11-document-lifecycle/junit/TEST-com.localai.workspace.data.DocumentDeletionLifecycleTest.xml` |
| attempt-13-reindex-generation-tests | `reindexPublishesReadyGenerationAndPreservesReadyRollback; changedDocumentCanBeReindexedIntoNewReadyGeneration` | 2/2 PASS | 267 s | [`run-metadata.txt`](attempts/attempt-13-reindex-generation-tests/run-metadata.txt), [`gradle.log`](attempts/attempt-13-reindex-generation-tests/gradle.log), XML: `attempts/attempt-13-reindex-generation-tests/junit/TEST-com.localai.workspace.semantic.v2.SemanticContextPostfixTest.xml` |
| attempt-14-real-ingestion-stale-mark | `realDocumentIngestionAwaitsStaleGenerationMark` | 1/1 PASS | 41 s | [`run-metadata.txt`](attempts/attempt-14-real-ingestion-stale-mark/run-metadata.txt), [`gradle.log`](attempts/attempt-14-real-ingestion-stale-mark/gradle.log), XML: `attempts/attempt-14-real-ingestion-stale-mark/junit/TEST-com.localai.workspace.semantic.v2.SemanticContextPostfixTest.xml` |
| attempt-15-deletion-stale-fallback | `documentRemovalWithoutCallbackFailsClosedThenLexicalFallbackUsesOnlyCurrentDocuments` | 1/1 PASS | 43 s | [`run-metadata.txt`](attempts/attempt-15-deletion-stale-fallback/run-metadata.txt), [`gradle.log`](attempts/attempt-15-deletion-stale-fallback/gradle.log), XML: `attempts/attempt-15-deletion-stale-fallback/junit/TEST-com.localai.workspace.semantic.v2.SemanticContextPostfixTest.xml` |

The exact command and per-run start/end times, exit codes and paths are preserved with each attempt. All commands used Gradle 8.10.2, JDK 17 as the Gradle runtime, JDK 21 as the app test launcher/toolchain, the existing API 35 SDK/NDK 27.0.12077973/CMake 3.31.6 installation and `--offline`. `attempt-13` used `--rerun-tasks`; attempts 14–15 used the recorded temporary init script to force the selected test task to execute. The environment preflight file was captured after the tests, so it is not represented as a contemporaneous process snapshot.

## Earlier attempts retained

The `attempts/` directory retains raw logs, commands, XML and metadata, including failures and non-executions. Initial compile failure (attempt 01) was fixed before final code. The SDK 29 Robolectric attempt (02) failed during environment setup when required instrumented Android artifacts could not be obtained. Attempt 03 had an ambiguous Compose node selector; attempt 04 passed before commit and is excluded from exact-SHA totals. Attempt 05 and attempt 12 were Gradle `UP-TO-DATE` with zero executed tests and are **NOT_RUN**. Attempt 06 stalled during test-only cleanup that invoked unrelated whole-workspace deletion; its process snapshots and thread dumps are retained and no XML is attributed to it. Attempts 07–08 observed the imported private copy before its asynchronous outbox cleanup completed; recorded outbox/reference diagnostics led to a bounded wait in the test only. These attempts are not counted as PASS.

## Original plan and limits

The archived source backlog and roadmap were read without editing. Their SHA-256 values and structural check are in [`plan-source-check.json`](plan-source-check.json): 97/97 unique IDs, 120 dependency edges and all 97 source statuses `TODO`. M02-06 depends on M02-05; M02-05 remains formally unclosed, so this implementation is partial preparatory evidence only. No ID, order, dependency or source status changed.

The full app JVM suite, LiteRT compatibility suite, lint, AndroidTest compilation, assembleDebug/Release, emulator, physical Motorola, APK and performance measurements were **NOT_RUN** for this increment. M01-06 remains `PENDING_REQUIRES_DEVICE`.

Payload SHA-256 values are listed in [`SHA256SUMS`](SHA256SUMS) (78 entries, SHA-256 `9bcbb6f44e31f3b8ea18b2d4b0a3bb6eb4fc52298fe23b93104961ad696b5a4c`); the manifest intentionally excludes this report, `RESULTS.json`, and itself.
