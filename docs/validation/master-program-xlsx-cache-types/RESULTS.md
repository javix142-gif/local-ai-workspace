# M03-02 XLSX cached formula result-type regression check

Date: 2026-10-19. Working branch: `codex/master-program-20261019`. This is focused preparatory HOST coverage; it does not complete original task M03-02.

## Finding

The proposed serializer defect was **not reproduced**. At code/test SHA `b08d438b8bfe2b1b7b17e16a3141998a41760df3`, the existing `ArtifactRenderer.xlsx` maps a cached formula result using the declared cell type: `number` becomes OOXML `t="n"`, `boolean` becomes `t="b"`, and a cached `string` formula becomes `t="str"`. A formula with no cached value omits the type so a reader can calculate it. The three-type focused test exercises the production `ArtifactStore.create` → `ArtifactRenderer` → verified XLSX package path and asserts the emitted worksheet XML and formula/cache text. No production source was changed.

A small openpyxl 3.1.5 control confirms the reason for the type assertions: with the same cached `<v>2</v>`, `t="str"` reads as Python string `'2'`, while `t="n"` or omitted type reads as numeric `2`. This is a format-semantics control, not an independent openpyxl read of the three-type workbook from this run. A previous M03-02 formula edit report separately records an actual generated numeric formula cache opened by openpyxl; see [`formula edit results`](../master-program-m03-02-xlsx-formulas/RESULTS.md).

## Attempts

- `attempt-01-wrapper-cache-miss`: wrapper did not start Gradle. The process used the default `/home/agent/.gradle`, which lacked the pinned Gradle 8.10.2 distribution; download failed with `Connection refused`. Exit 1, six seconds, no JUnit XML/test start. This is an environment setup failure, not a test result.
- `attempt-02-focused-type-regression`: reran the one filtered method using the already-existing `/workspace/.gradle` wrapper/dependency cache in offline mode. Gradle 8.10.2, Temurin 17 runtime and Temurin 21 worker toolchain, API 35 SDK and NDK 27.0.12077973. **1/1 PASS**, 0 failures/errors/skips; JUnit method time 5.752s, Gradle 3m30s, exit 0, 63 actionable tasks executed. The exact command, environment, complete compressed log, JUnit XML, task outcomes and hashes are in this directory. The log archive decompresses byte-for-byte to the captured Gradle output.

## Limits and original task status

This one test adds a guard for formula cached-result types; it found no production defect. It is not a full app/LiteRT JVM suite, lint, AndroidTest compile/execution, Excel/LibreOffice app test, emulator, Motorola validation, APK, or performance benchmark. The workspace cache and build outputs pre-existed; this was not a clean checkout/build. Formula values are preserved/typed; this app does not evaluate or recalculate formulas.

Original M03-02 remains source `TODO`, `requires_device=true`, dependent on M03-01 and M02-02; both dependencies remain TODO. M03-01 also depends on M02-05. No original task ID, status, order or dependency was changed. The exact backlog and roadmap source files remain unchanged (97 task IDs, 120 dependency edges, all source statuses TODO).
