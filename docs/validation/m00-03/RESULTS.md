# M00-03 context evidence selector correction

Evidence is synthetic and host-only. It contains no user documents or prompts.

## Source and execution

- Authorized starting commit: `7da2cc71e57aed1d213ea03b01ba8b41be849939`.
- Corrected code commit tested by the full host gate: `594a511cc088a53b97ccb5f21b720e20e8e4639a`.
- Branch: `feat/0.5.0-skills-agents`.
- Baseline and corrected harness JSON are kept separately under `before/` and `after/`.
- Gradle: 8.10.2; Android compile SDK: 35; Gradle JVM: Temurin 17.0.20.1; project test launcher: Java 21.0.12.1; ARM64-only build; offline, sequential, one worker.
- Full gate: 2026-10-08 17:32:19–17:42:55 UTC.
- Focused final suite: 70 tests, 0 failures, 0 errors, 0 skipped.
- Full gate: app JVM 700/700; LiteRT compatibility JVM 2/2; all failures/errors/skips 0.
- `assembleDebug`, `assembleRelease`, `lintDebug`, and `assembleDebugAndroidTest`: successful. Lint: 0 errors, 40 warnings, 1 informational issue.
- Android instrumented tests: **NOT RUN**. ADB listed zero devices; the SDK has no emulator executable/system image and `/dev/kvm` is absent. Building the AndroidTest APK is not a device test.

## Baseline reproduction

The pre-fix focused run completed 9 tests: 3 failed, 0 errors, 0 skipped. `range_middle_cell`, `absolute_formula_dependencies`, and `multibyte_anchor` failed. The relative-formula and ASCII-anchor controls passed. The complete JSON preserves all six outcomes and excerpts.

## Corrected behavior

- Small rectangular A1 ranges select represented interior cells. Formula references accept relative, absolute, and mixed `$` markers. Formula ranges select only cells already present in the bounded source; the selector does not evaluate formulas or expand large ranges.
- Cell references remain limited to 64 and excerpts to 480 UTF-8 bytes. Missing references and cell/byte-limit omissions carry explicit reason codes and missing addresses in context provenance/Inspector metadata. An empty result remains empty; it does not substitute neighboring cells.
- Text excerpts budget windows by UTF-8 bytes and retain the selected query anchor when it fits. Character offsets point to the exact source slice, without splitting surrogate pairs.
- Material cell omissions raise a user notice; ordinary text excerpting/truncation does not.
- Context integration tests exercise EG2 retrieval and lexical fallback through `ContextFoundation`, checking the final prompt and source provenance. Existing project/document isolation, F1/F2, generation-gate ordering, and Semantic V2 rollback tests remain in the full suite.

## Scope and remaining validation

No model, retrieval semantics, index, database, generation behavior, or validation IDs were changed. No device/Motorola behavior is claimed. The corrected cell selection and notice should still be verified with an XLSX on the Moto G86; this host evidence does not establish model answer quality.

See `evidence-manifest.json` for SHA-256 digests of the report files and logs.
