# Preparatory M03-04 — embedded Unicode fonts for generated PDFs

**Status:** focused HOST validation passed on source/test SHA `f4f69f5980401bc6ae87bab65119134f8b57fc92`. This is preparatory evidence only. Original M03-04 remains `TODO`, requires a physical device, and depends on M03-01 (`TODO`). No source task or dependency was changed.

## Reproduced defect and implementation

At test-only SHA `9ac966bfdd35dc98c2dd55023bf1580d58b31e3c`, the new regression reached the real artifact renderer and failed with `U+0395 ('Epsilon') is not available in the font Helvetica, encoding: WinAnsiEncoding`. The exception occurred while measuring the paragraph, before the PDF was saved. The same restricted fonts were used for table-cell measurement and drawing.

The renderer now loads and subsets embedded Noto Sans Regular and Bold TrueType fonts via the existing PDFBox Android API. Paragraphs and tables use those same fonts for width calculation and output, retaining regular/bold hierarchy and searchable Unicode text. No runtime dependency was added. Font binaries are from the official `notofonts/noto-fonts` repository, pinned at `ffebf8c1ee449e544955a7e813c54f9b73848eac`, and licensed under SIL OFL 1.1. Paths, byte sizes, hashes, license and measured coverage are recorded in `app/src/main/assets/fonts/README.md`.

Coverage check confirms the tested Spanish, Polish, Greek, Vietnamese and Euro glyphs exist. These two fonts do not contain CJK or emoji glyphs. The renderer still fails explicitly when a requested code point is absent; it does not transliterate, replace or omit it. Additional script-specific font assets need a separate package-size and license review.

## Regression and validation attempts

1. Initial wrapper invocation at source `9ac966b` did not start Gradle: `GRADLE_USER_HOME` was unset, so the wrapper looked under the process home and attempted to download Gradle 8.10.2; `services.gradle.org` returned `Connection refused`. Exit 1, 5.329 s, no task and no XML. The raw output is retained in `red-9ac966b/`.
2. The same focused method at `9ac966b`, using the already-present `/workspace/.gradle` and offline mode, ran and failed as expected: **1 test, 1 failure**, specifically the Helvetica/WinAnsi U+0395 exception. Gradle exit 1, 185.059 s. Fresh XML and full log are in `red-9ac966b-existing-gradle-home/`.
3. First implementation SHA `f8a171d51aed28ee929115d4fa7df41976c001e0` failed compilation because a table helper retained one `PDType1Font` type after the type change. Test was not reached and no fresh JUnit XML was produced. The compiler error is retained in `green-f8a171d/gradle.log`.
4. At code SHA `f4f69f5980401bc6ae87bab65119134f8b57fc92`, the focused class passed: **3/3, 0 failures, 0 errors, 0 skipped**. The first invocation's Gradle log, internal exit marker and XML show `BUILD SUCCESSFUL`, exit 0, and 63/63 actionable tasks executed; the outer execution session returned 1. This mismatch is preserved in `green-f4f69f5/`.
5. To resolve that tool-status mismatch, the same class was run directly once at the identical SHA. `:app:testDebugUnitTest --tests com.localai.workspace.artifacts.PdfArtifactLayoutTest` returned **exit 0**, `BUILD SUCCESSFUL in 3m 15s`, 63/63 actionable tasks executed, and a fresh XML with **3/3 pass**. Start/end: `2026-10-19T20:59:48Z`–`2026-10-19T21:03:04Z`; duration 195.576 s. The exact command, environment, direct tool exit, full log and XML are in `direct-f4f69f5/`.

The exact tested class contains:

- `generatedPdfPreservesExtendedUnicodeText` — title, paragraph and table text including Spanish, Polish, Greek and Vietnamese; confirms embedded fonts and extracted Unicode.
- `generatedPdfKeepsWordsTogetherAndPreservesPageLayoutAndVersions` — existing wrap/page/version assertions, updated to measure with the renderer's Noto Sans font.
- `generatedPdfTablesUseGridAndPaginateLongCellsWithoutLosingText` — existing table layout, pagination and repeated-header assertions.

## Independent PDF checks

The synthetic one-page PDF from the direct passing run is `direct-f4f69f5/unicode-v1.pdf` (SHA-256 `06605453e3cdd93e5f4f12603659ac0a9b4e43fa8d8731e2bd1b5417ce3dbf20`). Poppler `pdftotext` 25.03.0 and `pypdf` 6.10.0 both recovered every fixture string. `pdffonts` reports `NotoSans-Bold` and `NotoSans-Regular` as embedded subsets with Unicode maps. Poppler rendered a 1020×1320 PNG, which was opened and visually inspected; Spanish accents, Polish, Greek and Vietnamese are visible in the paragraph and table. Commands, exit codes, extracted text, preview and hashes are in the same directory.

## Scope and limits

- Focused HOST: **PASS** (3/3) on exact code/test SHA `f4f69f5…`.
- Independent PDF readers and render: **PASS** for the synthetic supported-glyph fixture.
- Full app/LiteRT suites, lint, complete APK assembly: **NOT_RUN**.
- Emulator and physical device: **NOT_RUN**. No Motorola behavior or Office/PDF-viewer device compatibility is claimed.
- APK/Drive delivery: **NOT_BUILT / NOT_UPLOADED**. The prior personal QA certificate digest is recorded in state, but no matching local debug keystore was available in the searched executor locations. A newly generated debug key would not be demonstrably update-compatible with the user's installed package; no replacement-key APK was presented as an update.
- The generated PDF in this evidence is a synthetic test artifact, not user content.

The archived backlog and roadmap remain byte-identical: 97 unique task IDs, 120 dependency edges, and all 97 source statuses `TODO`. M03-04 remains open; M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. `main`, `v0.4.2`, the original feature branch and the recovery backup were not modified.

## Evidence manifest

The payload manifest `SHA256SUMS` contains 35 entries and its SHA-256 is recorded in `RESULTS.json`; it excludes `RESULTS.md`, `RESULTS.json`, and itself. The generated PDF/PNG, raw logs, XML, reader outputs, commands, environment and font/license files are included in the payload inventory.
