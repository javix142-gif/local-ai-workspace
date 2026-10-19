# Repeated headers for generated PDF tables — 2026-10-19

## Result

A test-first reproduction showed that a long generated table printed its header only on page 1. The red run at test SHA `b2681a631cd6e7a91bcc196e83c3524918c347a` failed the new per-page assertion with `[true, false, false]`; the existing word-boundary and revision test passed. The assertion was retained.

Production fix SHA `786f979ba50a1bd2d609712e297bec24909a0ac5` repeats the first row as a table header on continuation pages. It also reserves enough room for one body line after the header so pagination cannot create a header-only page. When the first row is too tall to meet that bound, repeating it is disabled rather than consuming the page. No data, schema, persistence, dependencies, inference or tools changed.

## Exact validation

The only Gradle test class run was `com.localai.workspace.artifacts.PdfArtifactLayoutTest`.

- Red: command in `red-b2681a6/command.txt`; source `b2681a631cd6e7a91bcc196e83c3524918c347a`; Gradle exit 1; 42 seconds; XML 2 tests, 1 failure, 0 errors, 0 skipped. The failing assertion is `Each table page should repeat its header row: [true, false, false]`.
- Green: same command in `green-786f979/command.txt`; source `786f979ba50a1bd2d609712e297bec24909a0ac5`; Gradle exit 0; Gradle reports 53 seconds (captured start/end span 54 seconds); XML 2 tests, 0 failures/errors/skips; 63 actionable tasks, 7 executed, 56 up-to-date.
- Both used Gradle wrapper 8.10.2, JDK 17 Gradle runtime, configured Temurin JDK 21 test worker, existing Android API 35 / NDK 27.0.12077973, and existing `/workspace/.gradle` cache in offline mode. No package/tool installation or cache cleanup occurred.

The green run generated `synthetic-long-table.pdf` (3,234 bytes). Independent pypdf 6.10.0 and PyMuPDF 1.26.6 reopened three Letter pages. Both readers found the header on every page; PyMuPDF found the header before continued cell text on each continuation page; all page text was in bounds; pypdf counted all 300 `dato` occurrences. The machine-readable reader results are in `green-786f979/independent-reader-result.json`.

## Scope and limits

Changed production file: `app/src/main/java/com/localai/workspace/artifacts/ArtifactRenderer.kt`. Regression test: `app/src/test/java/com/localai/workspace/artifacts/PdfArtifactLayoutTest.kt`. The red test and final renderer patch are retained in the attempt folders.

This is synthetic, focused HOST evidence for one PDF table layout path. It is not a full JVM/LiteRT gate, lint, AndroidTest, emulator, Motorola, Office/mobile-reader, imported-PDF editing or performance validation. No APK was built. The original M03-04 task remains TODO, requires a device, and depends on M03-01 TODO; no source backlog task is closed. M01-06 remains `PENDING_REQUIRES_DEVICE`, and physical validation remains `NOT_RUN`. Original task IDs, order, 120 dependencies and source TODO statuses are unchanged.

All files other than `SHA256SUMS` in this directory are listed in the manifest. Verify with `sha256sum -c SHA256SUMS` from this directory.
