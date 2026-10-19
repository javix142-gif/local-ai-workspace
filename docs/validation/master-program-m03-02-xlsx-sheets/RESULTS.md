# M03-02 — add worksheet to an app-generated XLSX draft

**Classification:** preparatory HOST evidence only; original M03-02 remains source `TODO`.

The source DoD asks to create, edit, save, reopen sheets/cells/formulas without silent loss and to declare the formula engine. This focused increment closes a concrete capability gap: an existing generated workbook can now receive one empty worksheet as a new immutable version through the local tool registry. It preserves all prior sheet descriptors, values, formulas and cached values. Sheet names use the existing Excel validation rules, the 16-sheet bound is enforced, and the optimistic current-version check prevents stale edits from overwriting a newer revision. The app still does not calculate formulas; export remains a separate user-selected SAF action. Imported workbooks are not edited.

## Changes

Code SHA `0ea514c498af7bbc2b4e0f1326591b3b2173718d` adds `artifacts.xlsxAddSheet` to the existing ArtifactTool path, deterministic routing, schema/capability reporting and local write diagnostics. The operation accepts only `id`, `expectedVersion` and `name`; it adds an empty sheet to an existing app-generated XLSX draft and creates a verified immutable revision. No dependency, Room schema, model runtime, import/retrieval path or export behavior changed.

## Regression evidence

The test was first added at red source SHA `332aa99cec18a54d9c3ea6c17fdab5c807998e62`. Its targeted HOST JUnit produced the expected 1-test/1-failure red result at the routing assertion: the query “Agrega una hoja Resumen al libro Excel” selected no tool. The implementation was then committed at `0ea514c498af7bbc2b4e0f1326591b3b2173718d`; the same test passed 1/1 with zero failures, errors or skips. The green command took 80 seconds wall / 79 seconds Gradle-reported time; 7 of 63 actionable tasks executed and 56 were up to date. The captured Gradle exit file is 0 and the log says `BUILD SUCCESSFUL`; the outer execution tool returned 1, whose cause remains `NOT_DETERMINED`. The discrepancy is preserved rather than hidden.

An opt-in synthetic XLSX emitted by that test was opened independently with openpyxl 3.1.5. ZIP CRC passed; the file has `Hoja1`, `Hoja2`, `Resumen`; the existing values and `=$A$2-$A$1` formula/cache `4` survived; `Resumen` is empty; and `fullCalcOnLoad=true`. This validates the generated package and cached-value preservation, not formula evaluation or an Android office viewer.

Exact commands, raw logs preserved losslessly as gzip, XML, timestamps, task summaries, generated workbook and reader output are in `red-01/` and `green-01/`. The root `SHA256SUMS` covers the evidence payload; attempt-local manifests cover their own files.

## Source-task and validation limits

M03-02 remains `TODO`, `requires_device=true`, and depends on M03-01 and M02-02; both dependencies remain source `TODO`. M03-01 still depends on M02-05. No original task status was promoted. This slice does not establish imported-workbook editing, adding/removing nonempty sheets, full roundtrip on Android/Office, or the full M03-02 DoD.

- Targeted JUnit: 1/1 PASS by fresh XML; command exit 0; outer execution status discrepancy unresolved.
- Independent XLSX reader: PASS, synthetic workbook only.
- Full app/LiteRT JVM suites, lint, AndroidTest compile: NOT_RUN.
- EMULATOR: NOT_RUN for this increment; the separately recorded global gate remains blocked by the authentic Room v1 fixture and model-dependent skips.
- PHYSICAL_DEVICE: NOT_RUN; no APK generated.
- Original backlog/roadmap unchanged: 97 unique IDs, 120 dependency edges, all source statuses TODO. M01-06 remains `PENDING_REQUIRES_DEVICE`.
