# M03-05 preparatory follow-up — PPTX image preview identity and asset view

**Code SHA:** `b5222c0a388777ee4570fb40290a9e760266efe1`
**Parent/documentation base:** `998769a954c21db2641694d478e148eec073d736`
**Status:** focused HOST and proportional lint/AndroidTest compilation passed; M03-05 remains partial and source `TODO`.

## Change

The textual PPTX preview now includes the SHA-256 of each validated PNG alongside its alternative text. Two revisions with the same alt text but different image bytes are distinguishable without displaying or copying the Base64 payload.

The artifact dialog also shows the caller-supplied PNG itself as an accessible image asset preview, with slide number/title, alternative text, source dimensions, and an explicit disclaimer that slide layout is not rendered. Parsing and image decoding run on `Dispatchers.Default`. Preview bitmaps use the existing PNG validator, are downsampled to at most 65,536 pixels each and 1,000,000 pixels total, and are recycled when replaced or when the dialog leaves composition. Preview remains read-only; saved bytes and immutable revision history do not change.

No image generation, slide-canvas rendering, network/file-path access, OOXML change, dependency, database/schema, inference or workflow change was made.

## Regression and test outcomes

- Before production changes, the new textual-preview regression failed 1/1: different PNGs with identical slide text/alt text produced identical previews. The old code is at base `998769a`; the test was added to the working tree for that red run. The raw log/exit and command are under `red-baseline/`.
- Iteration logs retain two implementation/test-fixture issues: a compile-time typo before tests ran, and an initial oversized Robolectric-generated PNG fixture rejected by the existing 256 KiB descriptor limit. Neither changed production bounds. The final fixture is a compact valid synthetic PNG; its 512×512 decoded source proves the visual decoder downsamples within the per-image pixel budget.
- The final focused command is in `final-focused/command.txt`; Gradle 8.10.2 completed with exit 0 and `BUILD SUCCESSFUL`. JUnit XML contains **4 tests, 4 passed, 0 failures, 0 errors, 0 skipped**: `PptxImageArtifactTest` 3/3 and `PptxImagePreviewUiTest` 1/1. The test verifies accessible image semantics, disclaimer, alt text, source dimensions, the exact image hash in textual preview, pixel bound, and unchanged stored bytes/history.
- Proportional gate command is in `final-host-gates/command.txt`; Gradle completed with exit 0 in 5m56s: `:app:lintDebug` and `:app:compileDebugAndroidTestKotlin` both completed. Lint reported 77 warnings and 1 informational finding, with 0 errors; none of the findings points to a changed file. AndroidTest was compiled only; it was not installed or executed.
- Logs, command files, JUnit XML, lint XML, and the attempt ledger are covered by `SHA256SUMS` in this directory. The parent `docs/validation/master-program-pptx/SHA256SUMS` also covers this directory's payload and child manifest.

Both Gradle invocations started with Git HEAD at parent `998769a954c21db2641694d478e148eec073d736` and the five final source/test changes present in the working tree. Those source/test files were subsequently committed unchanged as `b5222c0a388777ee4570fb40290a9e760266efe1`; no separate Git tree object was recorded at invocation, so these results are attributed to that working-tree source change, not described as commands launched with HEAD at the code commit. Runtime was Temurin JDK 17.0.20.1, Gradle 8.10.2, Android SDK API 35 at `/workspace/.toolchain/android-sdk`, and the existing offline Gradle user home `/workspace/.gradle-master-program-20261016`. JDK 17 and JDK 21 paths were both provided to Gradle toolchain discovery. No SDK/NDK installation or cache cleanup occurred.

## Scope and limits

The canonical source task remains **M03-05 — Generación/edición básica PPTX**, `TODO`, `requires_device=true`, dependent on M03-01 (`TODO`). This follow-up improves the preview of an image asset already supplied in the descriptor; it does not render the complete slide or fulfill reader-roundtrip/device acceptance. The original 97-task catalog and 120 dependency edges are unchanged.

No full JVM suite, LiteRT compatibility suite, Android instrumentation, emulator, Motorola, APK, or performance benchmark was run for this follow-up. The previously recorded emulator run `38988880055` remains **EMULATOR FAIL** for the authentic Room-v1 schema fixture plus six model-dependent skips; that run predates this code. Physical-device validation is `NOT_RUN`. No new APK was generated.

## Reproduction

Run the focused tests with the command recorded in `final-focused/command.txt`. Run `lintDebug` and AndroidTest source compilation with the command in `final-host-gates/command.txt`. Verify all retained files with `sha256sum -c SHA256SUMS` from this directory.
