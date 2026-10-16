# M02-03 — DOCX structure and traceable locations (preparatory validation)

**Result:** `PREPARATORY_HOST_FOCUSED_PASS_DEPENDENCY_BLOCKED`. This is a tested subset for the original M02-03 definition of done. The source backlog entry remains `TODO`; no original task or dependency was changed.

- Feature base observed before this increment: `c5d8d6cdf29177764667bc6dba636f02a528ff84`.
- Production parser commit: `4c91d79fe8f9ffe052a33f710f5818afad5df446`.
- Final tested code/test SHA: `2646c745b6c3393fbdbdad1d49a1b1490a7cd3e8`.
- Fixture: synthetic `structure-v1.docx`, 1,376 bytes, SHA-256 `140218250c3e4d60dabbe5e530b1e534b8a4441ec3320db79d59e9ac1e22f8c3`.
- The original source backlog still has 97 unique task IDs and 120 dependency edges, all source statuses `TODO`. M02-03 depends on M02-01; M02-01 depends on M00-10, whose Moto G86/original-workbook check remains device-required. The master authorization permits this preparatory work but does not close that dependency.

## What changed

`OfficeText.docx` now extracts bounded text from the DOCX body while preserving run concatenation and paragraph, line-break, tab and table-row separators. It emits stable paragraph ordinals for built-in `Heading*`, `Title` and `Subtitle` styles; emits one-based physical table row/cell markers and an explicit header-row marker when Word marks a row with `w:tblHeader`; and retains empty table cells. Both `w:br w:type="page"` and enabled `w:pageBreakBefore` split the parsed document into page-numbered segments. `w:pageBreakBefore` with a false value does not split. `TextChunker` carries those page numbers and character offsets into chunks.

The parser retains the existing XML/ZIP bounds, secure XML checks, cancellation check and 1,000,000-character output/input limits. Plain unstyled paragraphs remain unmarked and DOCX documents without explicit breaks keep `pageNumber=null` for compatibility. No database/schema, format, dependency, inference, retrieval or permission changes were made.

This is body-text extraction, not a Word layout renderer. It does not resolve natural pagination, headers/footers, footnotes/endnotes/comments, custom heading styles through `styles.xml`, list numbering, nested tables, merged-cell grid spans, images or floating content. Table `cell=N` is the physical `<w:tc>` ordinal in a row, not a computed visual grid column. No claim is made about preserving visual formatting or rendered page numbers absent explicit break markup.

## Host evidence

Environment: Gradle wrapper 8.10.2; Temurin JDK 17.0.20.1 runs Gradle; Temurin JDK 21.0.12.1 is discovered for the app JVM test worker; Android SDK API 35; NDK 27.0.12077973; existing `/workspace/.gradle-master-program-20261016` cache; `--offline`; no toolchain installation or cache cleanup. Focused commands use the existing test init script `docs/validation/master-program-document-delete/force-test-execution.init.gradle` to rerun filtered tests.

1. **Red reproduction**, source SHA `49e4eb15a9ebd1e783a4b1724f9c1b0852d4c0e4`: filtered `DocxStructureFidelityTest` ran 1 test and failed as expected. The assertion was specifically `expected:<2> but was:<1>` for the fixture's explicit page break. The old production parser returned one unnumbered page and flattened the XML. JUnit reports 1 failure, 0 errors, 0 skips.
2. **Compatibility probe with unavailable API 29 runtime**, source SHA `4c91d79fe8f9ffe052a33f710f5818afad5df446`: one invocation filtered `DocxStructureFidelityTest` and existing `OfficeRetrievalTest`. The DOCX class passed 2/2. Both `OfficeRetrievalTest` cases failed during Robolectric setup before their bodies because cached `android-all-instrumented:10-robolectric-5803371-i7` was absent and its fetch failed with `Connection refused` under `--offline`. Overall Gradle exit 1; this is an infrastructure/runtime failure, not a product failure. A focused API 35 regression now checks the same legacy plain-paragraph and tab-separated-cell behavior without that unavailable API runtime.
3. **Final exact-SHA focused run**, source SHA `2646c745b6c3393fbdbdad1d49a1b1490a7cd3e8`: `DocxStructureFidelityTest` passed 3/3, failures 0, errors 0, skipped 0. The tests cover `DocumentParser` integration, heading style, all fixture table cells and their location markers, an explicitly marked header row, an empty cell, explicit `w:br` pagination, `pageBreakBefore` true/false, chunk page/character offsets, and compatibility of plain paragraph text plus table tabs. JUnit class time 12.452s; Gradle reported `BUILD SUCCESSFUL in 1m 43s`, 63 actionable tasks (3 executed, 60 up-to-date); wall time from UTC start/end markers was 123 seconds; command exit 0.

The final command is recorded verbatim in `attempts/attempt-03-location-final/command.txt`; Gradle log content SHA-256 is `3b2eb654ea273443ff595aeb5c84aca37af86f32a6b38ca2e00fffff8b10e8c2`, gzip archive SHA-256 is `68217f00ccb8896be138376a5e2150af703406c74a9f0055d12112c9413142b2`, and final JUnit XML SHA-256 is `18088fc84ca5e1ae713e1515c27588c2cb28da1a7bb469bc740089ea403f1663`. Original JUnit XML, environment and outcomes are beside it. The red and API 29 blocked attempts are preserved separately, not combined into the final pass count.

Only the filtered DOCX class was green on the final SHA. The full JVM suite, LiteRT compatibility suite, lint, AndroidTest compilation/execution, emulator, Motorola and APK were **NOT_RUN** in this increment. No full HOST gate or global Android PASS is claimed.

## Integrity and backup

`SHA256SUMS` covers all files in this evidence directory except itself. Red/blocked/final Gradle logs are gzip-compressed losslessly because repository ignore rules exclude raw `*.log`; raw files remain in the local workspace. Backup of the pre-continuation working state (including the then-current parser diff and red evidence) is at `/workspace/backup/aws-m02-03-pre-edit-20261016-1958/`; its manifest verifies the Git bundle, working-tree patch and red-evidence archive. The broader master-program return bundle remains `/workspace/backup/aws-pre-integral-20261016-458864a/repository.bundle`.
