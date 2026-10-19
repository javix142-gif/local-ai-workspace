# DOCX list extraction — preparatory HOST increment

This is a bounded reading-path improvement for the original M02-03 DOCX structure work. It is **not** formal M02-03 completion. The archived backlog remains untouched: M02-03 is `TODO`, depends on M02-01 (`TODO`), and M02-01 depends on device-gated M00-10. The recovery branch and implementation work remain separate from `main` and `v0.4.2`.

## Reproduced gap

At production base `5800c4bdefb78265f2c00caa39b7f2f4d08c9a18`, `OfficeText.docx` extracted DOCX text runs, paragraph/heading locations, table cells and explicit page breaks, but ignored paragraph `numPr` and `word/numbering.xml`. A test-only commit `a351f5f8ace366251833504170a72fa1f2587657` added a synthetic OOXML fixture for bullet, nested and decimal lists. The red run failed 1/1 because the extracted text contained only the five item strings as plain paragraphs (`Primer elemento`, `Segundo elemento`, `Elemento anidado`, `Primero numerado`, `Segundo numerado`); no list markers survived. Gradle exit was 1. The fresh JUnit XML and full log are in `red-baseline/`.

## Change

Production commit `dadcb46ca06eb74301f0e6a4e5f3eb9e39dcb240` resolves explicit paragraph `numId`/`ilvl` references against bounded `word/numbering.xml` data. It emits deterministic textual list markers for bullets and supported numbering formats (decimal, zero-padded decimal, alphabetic, Roman), honors level starts and `startOverride`, increments counters in document order, and keeps nested levels visible. A missing definition or unsupported format emits a `[DOCX list unresolved …]` marker while retaining the actual item text. The output stays compatible with the existing `ParsedDocument` text/provenance path; no schema or dependency changed.

The parser reads the numbering part through the existing 8 MiB bounded XML path, rejects DTD/entity declarations, checks thread interruption, limits stored abstract numbering definitions and number instances to 4096 each, and supports levels 0–8. The representation is intentionally limited: it does not resolve list numbering inherited only from paragraph styles, picture bullets, style-linked numbering, or every Word `numFmt`; those cases are not claimed as faithfully numbered. Unsupported explicit definitions remain visible as unresolved metadata rather than receiving an invented label. Bullet glyphs are canonical textual bullets by nesting level, not a reproduction of a source font's visual symbol.

## HOST evidence

The final exact test tree is `f88c756fa78f0f6d31a538ef16c470bd39c033fa`; production code is unchanged from `dadcb46`. The filtered `DocxStructureFidelityTest` run passed **5/5**, with 0 failures, 0 errors and 0 skips; Gradle exit 0, 63/63 actionable tasks executed, 188 seconds by UTC timestamps. It covers bullets, nested bullets, decimal sequence, alphabetic numbering with `startOverride`, unresolved definitions retaining item text, and the pre-existing heading/table/page-break/separator cases. The XML totals reconcile with the Gradle log.

The red run was 1/1 expected failure at test SHA `a351f5f8ace366251833504170a72fa1f2587657` over production base `5800c4b`. An intermediate green run at `dadcb46` passed 5/5 before the additional `startOverride` assertions were added; the final green run at `f88c756` supersedes it for current test coverage. Exact commands, JDK/SDK details, timestamps, lossless gzip copies of complete Gradle logs, XML inventories, exit codes and XML are retained in the attempt directories. `gradle-log-payload-hash.txt` records original and compressed sizes/hashes and byte-for-byte decompression checks.

Gradle 8.10.2 ran on Temurin JDK 17; the project configured the app test worker with Temurin JDK 21. Android SDK API 35 and NDK 27.0.12077973 were already present. All test invocations used the pre-existing `/workspace/.gradle` cache with `--offline`; no component or dependency was installed. One auxiliary wrapper-version probe omitted `GRADLE_USER_HOME` and `--offline`, attempted to fetch the pinned wrapper distribution and received `Connection refused` before Gradle started; no task ran in that probe. The correctly configured offline wrapper probe and the test runs succeeded. This probe discrepancy does not alter the recorded test exits.

## Limits

- This is synthetic HOST parser evidence; no user workbook/document was used.
- No full JVM/LiteRT suite, lint, AndroidTest compilation/execution, emulator, physical device or APK was run for this increment.
- M02-03 remains source `TODO`; M02-01 dependency remains `TODO` and ultimately device-gated. M03-07 remains `TODO` and device-required; this change does not close or validate its UI DoD.
- `M01-06=PENDING_REQUIRES_DEVICE` and `PHYSICAL_DEVICE=NOT_RUN` remain unchanged.
- The original source plan files remain byte-identical: 97 unique IDs, 120 dependency edges, all 97 source statuses `TODO`.

Payload file hashes are listed in `SHA256SUMS`: 33 entries, manifest SHA-256 `5ced51c2841bf3d246335b3075a6450b28f340bf3d0b089b52c1c8d41d62036b`. Local verification passed 33/33. The manifest excludes `RESULTS.md`, `RESULTS.json` and itself; verify from this directory with `sha256sum -c SHA256SUMS`.
