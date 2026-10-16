# Portable memory bundle import — HOST evidence

Date: 2026-10-16
Status: **IMPLEMENTED; focused HOST checks PASS; independent audit pending**
Product source commit: `76f6db08f6d582025c68350c910bc627f9deda65`
Base feature HEAD: `458864aa03bdedda2388373e6573f500afda7c79`
Working branch: `codex/master-program-20261016`

## What this increment adds

The app can copy a prompt describing the `AWS_MEMORY_IMPORT_V1` package, select a ZIP through Android's Storage Access Framework, validate and preview its contents, and let the user review, edit, exclude, and explicitly select individual memories before import. Accepted records are inserted transactionally as local `USER`-scope `PENDING` memories. Exact duplicates are skipped; possible structured conflicts are shown without overwriting existing values. Original trust, scope, dates, IDs, and provenance are retained as metadata, while imported instructions remain data rather than privileged instructions.

The reader restricts paths and filenames, verifies the manifest inventory and per-file SHA-256, requires valid UTF-8/JSON/JSONL, and bounds archive, entry, expanded size, expansion ratio, record count, and text length. No database schema/version or migration changed; `MemoryDatabase.kt` adds DAO queries only. No dependency, model runtime, network export, APK, or model weight was added.

Embeddings are not computed for unapproved imports. After approval, the existing memory recovery/search paths can index them; an item can remain `INDEX_PENDING` when the embedding capability is unavailable. Secret detection reuses local patterns as a heuristic; it is not a comprehensive scanner. This increment does not create a ZIP export producer; the existing JSON export remains separate. It does not formally complete any of the original 97 backlog tasks.

## Final focused host run

Exact source SHA: `76f6db08f6d582025c68350c910bc627f9deda65`. Gradle 8.10.2 ran on JDK 17.0.20.1; the app test worker used JDK 21.0.12.1. Existing Android SDK API 35, NDK 27.0.12077973, and CMake 3.31.6 were used. Gradle ran offline from the existing program cache. No SDK/NDK component was installed during this run.

Command and environment: [`command-verified.txt`](host/command-verified.txt), [`environment.txt`](host/environment.txt). Start/end: `2026-10-16T17:07:41Z`–`2026-10-16T17:15:02Z`; wall time 441 seconds; exit 0. Gradle reported `BUILD SUCCESSFUL in 7m 19s`, 123 actionable tasks (25 executed, 98 up-to-date). Full log, losslessly compressed: [`attempt-07-final-offline-verified.log.gz`](host/logs/attempt-07-final-offline-verified.log.gz).

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` filtered to `MemoryBundleV1Test` and `MemoryBundleImportTest` | **PASS**, 9 tests; 0 failures, 0 errors, 0 skipped |
| `:app:lintDebug` | **PASS**, 0 errors, 77 warnings, 1 information diagnostic |
| Complete app JVM suite | NOT_RUN |
| `:litert-compat:testDebugUnitTest` | NOT_RUN |
| AndroidTest Kotlin compilation / instrumentation | NOT_RUN |
| Debug/Release APK packaging | NOT_RUN in this focused gate |
| Emulator / Motorola | NOT_RUN |

JUnit XML and lint reports are preserved under [`host/`](host/). The XML reports were copied from the Gradle output generated at `2026-10-16T17:09:55Z` and `17:10:18Z` during this run and total 9/9 passing tests. The lint report contains 78 diagnostics: 77 warnings and one information item, with zero errors.

The evidence bundle contains 46 manifested files. Its `SHA256SUMS` passes locally 46/46; the SHA-256 of that manifest is recorded in `SHA256SUMS.sha256`. The attempt logs with a temporary truststore setting have only the password value redacted; the final offline log is retained unchanged. Logs are gzip-compressed; decompressing them restores the sanitized text.

## Earlier attempts retained

The seven attempt logs and exact command/time/exit records are under [`host/logs/`](host/logs/) and in [`attempt-history.json`](host/attempt-history.json). The first two attempts stopped before Gradle tasks because the wrapper download was refused and then failed TLS certificate validation. The first Gradle run reached test compilation and exposed missing imports in the new test. Two following runs exposed incorrect test-fixture expectations; those fixtures were corrected without weakening assertions. A later 9/9 plus lint pass preceded a final small UI guard/preview adjustment; the final offline run above revalidated the exact committed code. Logs containing a temporary truststore option have only that password value redacted in the committed copies.

## Source and state boundaries

The original backlog and roadmap remain byte-identical and retain their historical source state: 97/97 tasks `TODO`, 97 unique IDs, 120 dependency edges, acyclic graph. Their SHA-256 values are `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`. No original task is marked complete by this preparatory increment. M00-04 remains not accepted; M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02e remains `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`.

The preserved pre-change repository bundle is `/workspace/backup/aws-pre-integral-20261016-458864a/repository.bundle`, SHA-256 `1dc13d8142c6f0beac1038aa7a8abc5ec766b88648e32aefcfba45e037e4f925`.

The previously used local Debug signing key (certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`) is not present in this executor's mounted filesystem. No replacement key was generated. Therefore no APK was built or uploaded: a different signer could recreate the package-update conflict and risk requiring uninstall/data loss. `PHYSICAL_DEVICE` remains NOT_RUN; no Motorola is attached. The local ADB preflight returned exit 0 with zero connected devices.

The next authorized work is a separate, bounded increment to add a ZIP producer/round-trip counterpart for this package, without assigning or closing an original task ID. P0 latency/model choices still require the actual Moto G86 baseline; no speed or energy claim is made here.
