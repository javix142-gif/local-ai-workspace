# XLSX raw style and number-format metadata

**Classification:** preparatory HOST result only; source task M02-02 remains `TODO`.

## Scope and outcome

`StructuredDocuments` now preserves the XLSX cell style index and its raw number-format metadata in structured evidence. Values, cell types, formula text, and cached values remain as stored; the app does not infer dates from number formats and does not calculate formulas. The bounded parser reads custom number formats and the ordered `cellXfs` list from `xl/styles.xml`, then associates only styles used by represented cells. Existing ZIP, XML, cell, and representation limits remain in force.

The change was driven by a test-first reproduction. At test commit `d3064ffe729ae1be9ab1feb10dab340a2358d098`, the new test failed because the style index was not linked to the raw number format. At production commit `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`, the exact `StructuredDocumentsTest` class passed 11/11.

The attempted independent `openpyxl` read is **NOT_VALIDATED**: the small parser fixture is a ZIP of the XML parts consumed by this parser test, not a complete Office Open XML package; it lacks `[Content_Types].xml`, so the library stopped before reading cells. This is not a product failure and is not counted as a pass. The unit fixture does not establish interoperability with Excel or LibreOffice.

## Red reproduction

- Source/test SHA: `d3064ffe729ae1be9ab1feb10dab340a2358d098`
- Command: [`red-d3064ff/command.txt`](red-d3064ff/command.txt)
- Environment: [`red-d3064ff/environment.txt`](red-d3064ff/environment.txt)
- Started/finished: `2026-10-19T19:18:35Z` – `2026-10-19T19:19:14Z` (39 s)
- Gradle exit: `1`; 63 actionable tasks, 3 executed, 60 up-to-date
- JUnit: 11 tests, 10 passed, 1 expected failure, 0 errors, 0 skipped
- Failure: `xlsxPreservesRawStyleNumberFormatsAndCellTypesWithoutConvertingCachedValues`, assertion `The style index must be linked to its raw number format`.
- Raw log and XML: [`red-d3064ff/`](red-d3064ff/)

## Green focused HOST run

- Exact tested code SHA: `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`
- Command: [`green-a72db3d/command.txt`](green-a72db3d/command.txt)
- Environment: [`green-a72db3d/environment.txt`](green-a72db3d/environment.txt)
- Started/finished: `2026-10-19T19:20:14Z` – `2026-10-19T19:21:23Z` (69 s wall time)
- Gradle exit: `0`; Gradle reported `BUILD SUCCESSFUL in 1m 8s`; 63 actionable tasks, 7 executed, 56 up-to-date
- JUnit: 11/11 passed, 0 failures, 0 errors, 0 skipped
- XML: [`green-a72db3d/TEST-com.localai.workspace.data.StructuredDocumentsTest.xml`](green-a72db3d/TEST-com.localai.workspace.data.StructuredDocumentsTest.xml)
- Full raw Gradle log: [`green-a72db3d/gradle.log`](green-a72db3d/gradle.log)

Both executions used Gradle 8.10.2, JDK 17 as Gradle runtime, JDK 21 for the app test worker, Android SDK `/workspace/.toolchain/android-sdk`, and the existing offline Gradle cache `/workspace/.gradle`. No dependency or toolchain installation was performed. The JVM-only test did not invoke NDK/CMake.

## Independent-reader attempt and other gates

`openpyxl 3.1.5` was invoked on `green-a72db3d/minimal-parser-input.zip`. It returned `KeyError: "There is no item named '[Content_Types].xml' in the archive"`. The result is preserved in [`green-a72db3d/independent-reader-result.json`](green-a72db3d/independent-reader-result.json). It is **NOT_VALIDATED**, not a pass and not evidence of an XLSX interoperability defect.

Full app JVM suite, LiteRT compatibility suite, lint, assembly, AndroidTest compilation/execution, emulator, physical device, and APK: **NOT_RUN** for this increment.

## Plan boundary

The canonical backlog and roadmap were not modified. M02-02 remains source `TODO`, depends on M02-01 which remains `TODO`, and has broader fidelity/device validation still open. This increment does not close M02-02 or any other original task.
