# M02-02 XLSX style metadata — valid OPC follow-up

**Classification:** preparatory HOST validation only. Source task `M02-02` remains `TODO` because its dependency `M02-01` remains `TODO`; no original task is closed.

## Outcome

The previous style-metadata test assembled only the XML parts consumed by the app parser. It was not a complete Open Packaging Conventions (OPC) workbook, so the earlier OpenPyXL attempt correctly stopped at the missing `[Content_Types].xml` and was recorded as `NOT_VALIDATED`.

Test commit `156b3e43f8728d7755b8f46f3bc474b009368097` replaces that incomplete test input with the committed synthetic resource `app/src/test/resources/fixtures/xlsx-style-metadata-valid.xlsx`. The test checks required OPC parts and ZIP CRC, then retains its assertions for raw numeric values, cell types, shared/inline strings, formula text and cached value, built-in style ID, and custom number-format metadata. No production code changed in this follow-up.

The exact focused HOST test passed **1/1** (zero failures, errors, skips), Gradle exit `0`, and Gradle reported `BUILD SUCCESSFUL`; 63 actionable tasks executed with `--rerun-tasks`. Start/end UTC timestamps were `2026-10-19T23:52:43Z` / `2026-10-19T23:55:38Z` (175 seconds by timestamps; Gradle reported 2m54s). The XML is `TEST-com.localai.workspace.data.StructuredDocumentsTest.xlsxPreservesRawStyleNumberFormatsAndCellTypesWithoutConvertingCachedValues.xml`.

The test copied the exact resource bytes to `app/build/reports/xlsx-style-metadata/fixture.xlsx` (5,365 bytes). OpenPyXL 3.1.5 independently opened that copy in formula and `data_only` modes, validated ZIP CRC and required OPC parts, read the built-in/custom number formats, bool/shared/inline cells, formula `=A1+1`, and its stored cached value `45293`. The application parser continues to expose the raw date serial `45292` and raw formula/cache fields; this check does not calculate formulas or claim Excel/LibreOffice GUI rendering.

Fixture SHA-256: `e5eabcba0c6d70044b4e97dac354a4d9b887baa1de23b4b3015d532fc5f2f643`.

## Reproduction

- Tested source/test commit: `156b3e43f8728d7755b8f46f3bc474b009368097`
- Exact command: [`command.txt`](command.txt)
- Environment: [`environment.txt`](environment.txt)
- Timing and exit: [`timing.txt`](timing.txt), [`exit-code.txt`](exit-code.txt)
- Full Gradle output: [`gradle.log`](gradle.log)
- JUnit: [`TEST-com.localai.workspace.data.StructuredDocumentsTest.xlsxPreservesRawStyleNumberFormatsAndCellTypesWithoutConvertingCachedValues.xml`](TEST-com.localai.workspace.data.StructuredDocumentsTest.xlsxPreservesRawStyleNumberFormatsAndCellTypesWithoutConvertingCachedValues.xml)
- Independent reader command, source and output: [`independent-reader-command.txt`](independent-reader-command.txt), [`independent-reader.py`](independent-reader.py), [`independent-reader-result.json`](independent-reader-result.json)
- Payload hashes: [`SHA256SUMS`](SHA256SUMS)

Gradle 8.10.2 ran on Temurin JDK 17.0.20.1 with the app test worker on Temurin JDK 21.0.12.1. Android SDK API 35 and the pre-existing `/workspace/.gradle` cache were used offline. No tools, SDK components, dependencies or caches were installed, downloaded or cleared. Only the selected JVM/Robolectric method was run; this is not Android instrumentation.

## Limits and source plan

Full app/LiteRT suites, lint, assembly, AndroidTest compilation/execution, emulator, Motorola, APK, formula recalculation and office-application rendering were `NOT_RUN` here. The earlier incomplete-fixture read remains preserved as historical `NOT_VALIDATED`; this follow-up supplies the independent-reader validation it lacked.

The authentic backlog/roadmap were not edited. Their hashes remain `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`; 97 IDs, 120 dependencies, and all 97 source `TODO` statuses remain unchanged. M03-07 is also not formally complete: its existing preview/diff/cancel path has focused HOST evidence, but the source task still requires device validation and depends on M03-01 (`TODO`).
