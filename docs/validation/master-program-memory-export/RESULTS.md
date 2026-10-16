# Portable memory bundle export and round-trip

Code commit: `b6d68b1c443696cf243003ce806f1e425a89af9d`  
Branch: `codex/master-program-20261016`  
Classification: `IMPLEMENTED_FOCUSED_HOST_VALIDATED_PENDING_INDEPENDENT_AUDIT`.

This preparatory increment adds a ZIP producer for the existing `AWS_MEMORY_IMPORT_V1` reader and an explicit Android SAF destination action. It exports only non-expired active records that the existing `ScopeAccess` lookup permits. It includes the canonical `AWS_MEMORY_IMPORT/` root, manifest inventory hashes, required Markdown/JSON/JSONL entries, and origin metadata. Embeddings are omitted and are not recomputed. Records marked pending are never included; the writer independently rejects non-active records and content matching the app's heuristic sensitive-data patterns. A flagged record aborts the export with a reason rather than being silently omitted.

The import side retains the original title and origin metadata for review and later re-export, while imported memories still become local `USER`-scope `PENDING` records. Original scope is provenance only and cannot grant access. Instructions and metadata remain data; no imported capability is executed. The ZIP remains bounded by the existing reader contract: 8 MiB archive, 2 MiB per entry, 16 MiB expanded total, at most 12 files and 250 records. No Room schema, dependency, model, runtime, routing or network behavior changed.

## HOST validation

The final run used the exact source commit above, Gradle 8.10.2, Temurin 17.0.20.1 as the Gradle runtime, Temurin 21.0.12.1 for the app JVM test worker, SDK API 35, NDK 27.0.12077973 and CMake 3.31.6. Existing Gradle caches were reused offline; no cache or toolchain was copied, installed or cleaned.

Command and environment are preserved in [`host/command.txt`](host/command.txt) and [`host/environment-preflight.txt`](host/environment-preflight.txt). The full raw log is [`host/logs/focused-tests-and-lint.log`](host/logs/focused-tests-and-lint.log); exit code was 0. Start/end timestamps were 2026-10-16 17:49:16Z and 17:55:06Z; Gradle reported 5m49s. The task summary reports 123 actionable tasks, 18 executed and 105 up-to-date.

The focused JVM XMLs show **13/13 PASS**, 0 failures, 0 errors and 0 skipped: `MemoryBundleV1Test` 7/7 and `MemoryBundleImportTest` 6/6. Coverage includes canonical ZIP-root round-trip, preserved trust/title/origin fields, pending/sensitive rejection, scope isolation, pending-import behavior and re-export after explicit approval. `lintDebug` completed successfully. `lintAnalyzeDebug` executed, while `lintReportDebug` was up-to-date; therefore this report does not claim a current-run warning count.

The lint task graph configured and built native Release libraries for arm64-v8a and x86_64. It did not assemble or retain an APK. The full JVM suite, LiteRT compatibility suite, AndroidTest compilation, emulator and physical device were not run.

## Limits and delivery

Sensitive detection remains heuristic, not comprehensive. SAF lets the user choose the destination; that provider can be local or cloud-backed. The app does not independently transmit the archive. If the selected provider fails during write, it may leave a partial file; the app does not delete a user-selected destination automatically.

The first focused run passed 13/13 on code `0f31e918efd63601818a01ebcb8529fba5befd9b`. A follow-up changed only the user-facing messages for export validation errors, and the final 13/13 plus lint run was repeated on `b6d68b1c443696cf243003ce806f1e425a89af9d`. The original logs/XML for the first attempt remain under [`attempts/attempt-01-code-0f31/`](attempts/attempt-01-code-0f31/); final validation references only the current `host/` directory.

No APK was generated. The previous update-compatible Debug private signing key is not mounted here (preflight: [`master-program-memory-import/host/signing-key-preflight.txt`](../master-program-memory-import/host/signing-key-preflight.txt)); a replacement signer could make the same-package app impossible to update without uninstalling and risking user data. No key was generated or exported. Motorola validation remains `NOT_RUN`.

This is preparation only, not completion of an original backlog ID. The authentic source backlog remains byte-identical with 97 unique IDs, 120 dependency edges and source state `TODO` for all 97; no task is promoted or marked complete. `main` and `v0.4.2` remain protected. File hashes for this report, raw logs, command/environment records, task outcomes, JUnit XML and summary are in [`SHA256SUMS`](SHA256SUMS).
