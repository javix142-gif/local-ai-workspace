# Preparatory EG2 bounded semantic-search corpus paging

## Change and scope

The production search path in `SemanticLayer.search()` previously loaded up to 10,000 joined corpus rows, including persisted vector byte arrays, into one list and then scored/sorted the list. It now reads keyset-ordered pages of at most 256 `(segmentId, taskType, contentHash)` rows and keeps a deterministic semantic top-30 heap plus at most 30 lexical candidates. The existing 10,000-row generation bound, project/document scope, selected-document filtering, modality filtering, FTS rank fusion, cancellation checks, READY-generation checks, and query embedding behavior are retained. A pre-scan returns without embedding when no selected/in-scope row has an eligible modality.

At 768-dimensional FLOAT32 vectors, one page contains at most 256 × 768 × 4 = 786,432 vector payload bytes (0.75 MiB), excluding row metadata and SQLite/runtime buffers. The old 10,000-row ceiling could materialize 30,720,000 vector payload bytes (about 29.3 MiB) at the same dimension. This is a structural upper-bound comparison, not a measured heap profile or runtime improvement. All eligible rows up to the existing 10,000 bound are still scored; no neural model was loaded.

The page query uses a composite keyset cursor and stable ordering so multiple task/hash rows for a segment are not skipped. Focused integration coverage places a selected text segment beyond the first 256 rows, verifies an IMAGE-only miss does not embed, verifies selected-document retrieval beyond page one, and checks the page query count and bind limits. Storage coverage checks composite traversal and project/space isolation.

No Room schema/version, dependency, model, runtime, persistence format, or source backlog file changed. The stale test literal that expected Workspace DB version 8 was updated to compare the current version before and after opening the Semantic V2 sidecar; the current Workspace DB is version 10. Its existing data/vector assertions remain.

## Test-first and validation record

- Red regression at `939d14d`: `searchReadsSelectedSemanticCorpusInBoundedPages` failed as expected because the old search performed one corpus materialization/query where two bounded pages were required (1 test, 1 failure). See `source-939d14d/red/`.
- Implementation at `29c4fdc`: focused classes reported 49 tests with one failure in a pre-existing assertion pinned to obsolete Workspace DB version 8; the actual current version was 10. The Gradle log is preserved at `source-29c4fdc/final/`. Its generated XML was overwritten by the later invocation and is not included or reconstructed.
- After changing that stale assertion, both focused classes executed with `--rerun-tasks` and passed 49/49 on the exact committed test/source SHA `bb85544e37a44ed1a8a9eacfcd843ee0a79f5112`: `SemanticContextPostfixTest` 40/40 and `SemanticStorageTest` 9/9, zero failures/errors/skips. Gradle 8.10.2, offline, JDK 17 runtime/JDK 21 test worker, 185 seconds, exit 0. Exact command, environment, full compressed log, task outcomes, JUnit XML, and checksums are in `source-bb85544/verified-run/`.
- Commit `d214453df7e1c53ed60107d76e98017ce7beab6c` only removes an obsolete “version-8 workspace” KDoc claim. Its focused Gradle invocation exited 0 and compiled app Kotlin, but the test task was `UP-TO-DATE`; it executed no tests. This invocation is recorded separately and is not counted as a test pass. The behavior-changing paging and test sources are unchanged from the 49/49 run.

## Limits

This is focused HOST/Robolectric validation only. Full JVM/LiteRT suites, lint, APK assembly, AndroidTest, EMULATOR, PHYSICAL_DEVICE, model inference, phone latency, peak heap, thermal and energy measurements were not run. No original backlog task is marked complete. M00-06 and device-dependent work remain open. The original catalog remains 97 unique task IDs, 120 dependencies, all source statuses TODO.
