# M00-03 LOG10 selector correction evidence

This folder records one focused correction on `feat/0.5.0-skills-agents`. It is evidence for an independent audit, not an Android/device validation claim.

## Code identity and change

- Base/remote feature HEAD observed before editing: `020917cdb6e469983f77093130b58967ae56df56`.
- Code commit tested: `39aea8f55aac051edd3a43bc1b09c05d319d2783` (`fix(context): distinguish LOG10 cells from function calls`).
- `M00_SOURCE_COMMIT` in both the selector harness report and full gate: `39aea8f55aac051edd3a43bc1b09c05d319d2783`.
- Production change is confined to the evidence selector: `LOG10` is excluded as a function name only when an unqualified token is followed by `(` after whitespace. Function arguments continue through normal scanning. Bare and sheet-qualified `LOG10` remain valid A1 references; missing qualified references retain composite sheet/address diagnostics.
- No formula evaluation, new reference grammar, dependency, database, runtime, routing or app architecture change was made.

## Red reproduction against the base

`before/COMMAND.txt` runs the new assertion against production code at base SHA `020917cdb6e469983f77093130b58967ae56df56`. The source tree had only the uncommitted regression test added for this red run. It failed for the intended reason: formula cell `Hoja1!A3` with formula `LOG10` omitted existing `Hoja1!LOG10` from selected references. Result: 1 test, 1 failure, 0 errors, 0 skipped. The failure XML is `before/TEST-ContextEvidenceExcerptTest-red.xml`; the losslessly compressed Gradle log is `before/red.log.gz`.

## Green selector harness and focused tests

The focused run used the exact source SHA above. `after/SELECTOR_REPRODUCCIONES.json` identifies that SHA and reports 12/12 invariant cases passing, including existing relative/absolute formula, range and multibyte-anchor controls plus:

- `LOG10(A1)` keeps `LOG10` as a function and retains argument `A1`;
- direct query and formula dependency select a real `LOG10` cell;
- `Hoja2!LOG10` resolves only on Hoja2 despite a Hoja1 homonym;
- a missing `Hoja2!LOG10` reports that composite reference as missing with `SHEET_NOT_FOUND` and `MISSING_REFERENCED_CELLS`;
- qualified-sheet no-homonym fallback remains covered.

The focused Gradle run passed **106 tests**, 0 failures, 0 errors, 0 skipped across the five recorded JUnit XML suites: AuditHarness 1, ContextEvidenceExcerpt 32, ContextFoundation 38, StructuredDocuments 10, SemanticContextPostfix 25. This retains the immediate range/sheet/Unicode and F1/F2 context regressions. The EG2 context test and lexical fallback test assert final context/provenance behavior, including composite missing-reference diagnostics and notice propagation.

Command and environment: `after/FOCUSED_COMMAND.txt`, `after/ENVIRONMENT.txt`. Raw JUnit XML is under `after/focused-xml/`; the captured log is losslessly compressed as `after/focused.log.gz`.

## Full host gate on the tested code SHA

The full gate ran from the clean worktree at `39aea8f55aac051edd3a43bc1b09c05d319d2783`, with `M00_SOURCE_COMMIT` set to the same value. The final AuditHarness JSON written by that gate has the same `sourceCommit`, 12 cases and no failed invariants.

| Layer | Result |
|---|---:|
| App JVM | 727 tests; 0 failures, 0 errors, 0 skipped |
| LiteRT compatibility JVM | 2 tests; 0 failures, 0 errors, 0 skipped |
| `:app:assembleDebug` | PASS |
| `:app:assembleRelease` | PASS |
| `:app:lintDebug` | PASS; 0 errors, 40 warnings, 1 informational issue |
| `:app:assembleDebugAndroidTest` | PASS (test APK compilation only) |
| Instrumented Android tests | NOT_RUN |
| Motorola/physical validation | NOT_RUN |

Full command and environment: `after/FULL_GATE_COMMAND.txt`, `after/FULL_GATE_ENVIRONMENT.txt`. Start/end UTC timestamps are recorded alongside them. The full gate log is `after/full-host-gate.log.gz`; raw app/compat JUnit XML is under `after/full-host-xml/`; lint report text/XML are included. `after/SHA256SUMS` lists hashes for the evidence files.

Gradle assemble tasks necessarily produced temporary APK build outputs inside the isolated `/tmp` worktree. No APK was copied into the repository, delivered, uploaded, installed or device-tested. AndroidTest was compiled, not executed. No Android CI or emulator run occurred.

## Build-environment notes

Two preliminary full-gate attempts in the clean worktree failed before the final gate for local build-environment reasons, not Kotlin tests or this selector change:

1. The repository `.gitignore` excludes `third_party/llama.cpp/src/models/**`, so the clean Git worktree lacked CMake's required `models/models.h`/model sources.
2. After copying the ignored local source directory into the temporary worktree, CMake's previously generated glob remained stale and the native link reported unresolved model vtables. Touching the tracked `CMakeLists.txt` timestamp forced CMake to regenerate; its SHA-256 was verified unchanged. The subsequent standalone Release preparation and the final complete gate succeeded.

Losslessly compressed logs for both failed setup attempts and the successful Release preparation are in `after/diagnostics/`. The temporary ignored-source inventory is recorded there; it was not added to Git. No source content was changed for this workaround.

## Scope and validation limits

`git diff --check` is recorded in the handoff and final evidence commit. Host tests validate selector, ContextFoundation, EG2 and lexical fallback behavior using synthetic data. They do not demonstrate Gemma output or Motorola behavior. Android instrumentation and all physical checks remain NOT_RUN for this correction.
