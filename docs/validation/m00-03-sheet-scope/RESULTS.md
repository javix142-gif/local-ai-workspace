# M00-03 sheet-scoped spreadsheet evidence correction

Status: implemented and host-tested. Android execution: **NOT RUN**.

## Baseline and root cause

- Repository: `https://github.com/javix142-gif/local-ai-workspace.git`
- Branch at start: `feat/0.5.0-skills-agents`
- Starting HEAD and remote branch tip: `9c1f32c4277a187c65af715890d1061a2244e771`
- Production correction commit tested by the final gate: `ca3fcd7ca492ea35d2255b2665c27fa26c2466cf`
- `main` and `v0.4.2` were not changed.

Before editing production, a new assertion was run against the starting HEAD. The exact reproduction selected `Hoja1!A1=999` as a dependency of `Hoja1!A3`, whose formula is `'Hoja2'!A1`; it reported no missing reference. Root cause: the selector scanned formula coordinates without retaining their worksheet qualifier, then looked up the address in a workbook-wide address map. The explicit destination was therefore replaced with a homonymous local cell.

The red JUnit XML, losslessly compressed raw Gradle logs, and command are preserved in [`before/`](before/). `gzip -dc` reproduces the captured logs byte-for-byte; the uncompressed SHA-256 is recorded in the JSON/report or can be computed from that stream. The after harness output is [`SELECTOR_REPRODUCCIONES.json`](after/SELECTOR_REPRODUCCIONES.json), explicitly labeled host-only. All its invariants pass, including the qualified-sheet no-homonym case and the earlier range, absolute-reference, and multibyte-anchor controls.

## Resolution policy and supported grammar

Cell identity is `(NFC-normalized, case-insensitive sheet key, normalized A1 address)`. Sheet comparisons use Unicode NFC followed by `uppercase(Locale.ROOT)`. The workbook's original sheet spelling is retained for display and provenance. `$` affects neither identity nor range expansion.

Supported references:

- Local cells and rectangular ranges, including relative, absolute, and mixed forms such as `A2`, `$A$2`, `$A2`, `A$2`, and `A1:B3`. Formula-local references resolve only on the sheet containing that formula.
- One explicitly qualified sheet, e.g. `Hoja2!A1`, `'Hoja 2'!$A$1`, and escaped-apostrophe names such as `'O''Brien'!A$2:B$3`. A qualifier applies to its range.
- 3D ranges between explicit worksheet endpoints. They expand inclusively through represented workbook order metadata (`sheetOrder`) only when the ordered sheets are contiguous and unambiguous. XLSX ingestion now carries worksheet order, including metadata for empty sheets. Conflicting, absent, or gapped order is reported as incomplete; the selector does not assume that the endpoints alone describe the full range.
- An unqualified query reference with one represented cell candidate includes it. If the same address is represented on multiple sheets, the selector includes no candidate values and records `AMBIGUOUS_UNQUALIFIED_REFERENCE` with the qualified candidates.

Missing references remain composite `sheet!address` entries. An unavailable explicit sheet is never replaced by a same-address cell from another sheet or document. Missing/ambiguous/unresolved references, cell-count limits, byte-budget omissions, and 3D ordering failures are carried through Context Builder provenance, inspector data, and the existing material user notice. Routine textual excerpting does not create a spreadsheet incompleteness notice.

The selector processes only the already-retrieved and already-authorized project/document passages. Context Foundation groups structured passages only within the same document and only among those existing retrieval results; it performs no global lookup to fill a formula dependency. Maximum selection remains 64 unique composite cell identities and 480 UTF-8 bytes. Formulas and cached values are preserved and displayed; formulas are not evaluated and dependencies are not recursively followed.

The scanner masks double-quoted formula literals and avoids interpreting `LOG10` as a cell. External workbook references, `INDIRECT`/`OFFSET` dynamic references, defined names, and structured table references are not resolved locally; when detected they produce unresolved/incomplete diagnostics. This is a bounded A1-reference scanner, not an Excel formula parser.

Primary syntax references:

- [Excel cell references](https://support.microsoft.com/en-au/excel/create-or-change-a-cell-reference)
- [Excel 3D references](https://support.microsoft.com/en-au/excel/create-a-reference-to-the-same-cell-range-on-multiple-worksheets)
- [Excel defined names](https://support.microsoft.com/en-us/excel/names-in-formulas)

## Integration and regression coverage

The production path is exercised through Semantic V2 and lexical fallback. Tests inspect final prompt text, composite provenance, missing/ambiguous references, material-notice state, and document/project isolation. F1/F2 tests remain in the existing Semantic Context postfix suite. Retrieval remains outside the Gemma generation gate.

Relevant final-gate JUnit XML files are preserved under [`after/junit-relevant/`](after/junit-relevant/). They are the original host JUnit XML outputs from the full gate, with the complete relevant test classes.

| Run | Result |
|---|---|
| Baseline red reproduction | 1 test, 1 failure, 0 errors, 0 skipped |
| Focused suites after correction | 99 tests, 0 failures, 0 errors, 0 skipped |
| Full `app` JVM suite | 720 tests, 0 failures, 0 errors, 0 skipped |
| `litert-compat` JVM suite | 2 tests, 0 failures, 0 errors, 0 skipped |
| `assembleDebug` | PASS |
| `assembleRelease` | PASS |
| `lintDebug` | PASS; report lists 0 errors, 40 warnings, 1 informational issue |
| `assembleDebugAndroidTest` | PASS (test APK compiled only) |
| Instrumented/emulator/Motorola execution | **NOT RUN** |

The full original Gradle gate log and focused run log are losslessly compressed in [`after/`](after/). SHA-256 values for all preserved evidence files are in [`SHA256SUMS.txt`](SHA256SUMS.txt).

## Commands and host environment

Host: Linux `x86_64`, JDK Temurin `17.0.20.1+1`, Gradle wrapper `8.10.2`, Android SDK platform `android-35`, Kotlin Android plugin `2.0.21`; build variant native ABI restricted to `arm64-v8a`. JVM tests ran on the host with Robolectric. The Gradle distribution's embedded Kotlin version is `1.9.24`.

Focused command (run before the final gate):

```text
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk M00_SOURCE_COMMIT=9c1f32c4277a187c65af715890d1061a2244e771 ./gradlew --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' -Pkotlin.daemon.jvmargs=-Xmx2048m :app:testDebugUnitTest --tests 'com.localai.workspace.context.ContextEvidenceExcerptTest' --tests 'com.localai.workspace.context.AuditHarness' --tests 'com.localai.workspace.context.ContextFoundationTest' --tests 'com.localai.workspace.data.StructuredDocumentsTest' --tests 'com.localai.workspace.semantic.v2.SemanticContextPostfixTest' --offline --console=plain
```

The focused run's XML counts are 27 + 1 + 38 + 10 + 23 = 99. The selector harness JSON committed after the code commit is stamped with the tested code commit SHA above.

Final host gate (started `2026-10-08T18:47:07Z`, completed `2026-10-08T18:57:01Z`):

```text
M00_SOURCE_COMMIT=ca3fcd7ca492ea35d2255b2665c27fa26c2466cf JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' -Pkotlin.daemon.jvmargs=-Xmx2048m -I docs/validation/skills050/force-test-execution.gradle :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest -Parm64Only=true --offline --console=plain
```

Result: `BUILD SUCCESSFUL` in 9m54s. The `lint-results-debug.txt` report is retained; the Gradle console also records the completed lint task. `adb` is unavailable and `/dev/kvm` is absent, so no Android test or device result is claimed. The required local assemble tasks created ignored Gradle build outputs; no APK was copied, attached, or distributed.

## Scope preserved

No database/schema, model, inference, retrieval ranking, formula evaluation, Skills/Agents, runtime, or app version changes were made. F1/F2, selected-document constraints, project isolation, cancellation behavior, and retrieval-before-generation ordering remain covered by the host suites. No model or user workbook was loaded. No APK is part of this evidence bundle.
