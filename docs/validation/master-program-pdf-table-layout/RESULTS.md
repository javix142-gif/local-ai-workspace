# M03-04 PDF table layout — preparatory host evidence

This increment fixes a production gap in the local PDF renderer: rectangular table rows were previously flattened into pipe-separated prose. The renderer now emits bounded cell grids, wraps cell text, and splits tall rows into page-sized segments without dropping cell text. The first row is rendered as a modest shaded/bold table header. No new dependency or persistence change was made.

The original backlog remains unchanged. M03-04 is still `TODO`, requires a device, and depends on M03-01 (`TODO`). This is preparatory implementation evidence, not formal task completion or physical-reader validation.

## Regression and implementation

- Test-first commit: `c134a97f9fbea0512a2e86e88dea433fa4fa2f54`.
- Red reproduction: `generatedPdfTablesUseGridAndPaginateLongCellsWithoutLosingText` failed at the assertion that the extracted table still contained `|`, proving the previous renderer flattened the cells. XML: 1 test, 1 expected failure, 0 errors/skips; Gradle exit 1.
- Production commit: `9c1417e57bb3ffeec5211ca2e67a4b23b955096e`.
- Test hardening commit: `effb4ccd8089e04b98ffa4f6af6d6fdacb4bf475`.
- Tested code/test SHA: `effb4ccd8089e04b98ffa4f6af6d6fdacb4bf475`.

The test checks both the existing word-boundary/page/revision regression and the new table regression. The table fixture has eight columns and a long cell spanning multiple pages; it verifies the cell grid operators exist, pipe delimiters are absent, all 300 `dato` tokens and end markers survive, and later rows remain present.

## HOST result

Final focused command used Gradle 8.10.2, Temurin JDK 17 as Gradle runtime, JDK 21 exposed for toolchain discovery, SDK API 35, NDK 27.0.12077973, and the existing offline Gradle cache. Gradle reported 46 seconds; captured start/end timestamps span 47 seconds, with exit 0. JUnit: 2/2 passed, 0 failures, 0 errors, 0 skipped; 63 actionable Gradle tasks, 3 executed and 60 up-to-date. Exact commands, timestamps, environment, full logs and XML are in this directory.

The generated synthetic PDF is 2,864 bytes, SHA-256 `72207f10c97adc6a4df9d7b35870115694a6c9804917f9d0c0dcba9161e3a345`. Independent PyMuPDF 1.26.6 and pypdf 6.10.0 both opened it: three US Letter pages, 300 cell-text occurrences preserved, no pipe delimiter, 48 vector grid drawings, and no text or drawings outside page bounds. Page 1 was rendered and visually inspected; the compact multi-column table and grid are visible. The output and preview PNG are retained as synthetic evidence.

## Limits

- This does not provide embedded/custom fonts, semantic PDF tagging, repeated table headers on continuation pages, arbitrary imported-PDF editing, SAF export validation, or a mobile-viewer/device check.
- Full app/LiteRT suites, lint, AndroidTest compile/instrumentation, emulator, Motorola and APK were not run for this increment.
- Historical global EMULATOR state remains FAIL for the unavailable authentic Room v1 fixture and six model-dependent skips. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.
- The canonical source backlog and roadmap remain unchanged: 97 unique IDs, 120 dependency edges, and 97 source statuses `TODO`.
