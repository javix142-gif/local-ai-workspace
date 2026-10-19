# M03-07 exact-source preview revalidation

**Classification:** focused HOST pass, preparatory only. The original task remains `TODO`.

## Recovery finding

The prior M03-07 implementation and tests were already present in reachable Git history; no missing source was reconstructed and no M03-07 product/test code changed in this revalidation. At code SHA `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`, the existing `ArtifactRevisionPreviewUiTest` was rerun to establish the result against the exact current source.

The test covers: the saved “before” body and proposed “after” body, visible line-level removed/added entries, the saved-v1 export control, unchanged revision count, exact original bytes/hash after preview and cancel, and the bounded oversized-diff fallback retaining full before/after bodies.

## Exact-source HOST result

- Code/source SHA: `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`
- Command: [`command.txt`](command.txt)
- Environment: [`environment.txt`](environment.txt)
- Start/finish UTC: `2026-10-19T19:26:02Z` – `2026-10-19T19:29:18Z` (196 s wall)
- Gradle wrapper/runtime: 8.10.2 / Temurin JDK 17; test executor: Temurin JDK 21
- Existing cache, offline: `/workspace/.gradle`; Android SDK: `/workspace/.toolchain/android-sdk`
- Gradle exit: `0`; `BUILD SUCCESSFUL in 3m 15s`; 63 actionable tasks, all 63 executed
- JUnit XML: 2 tests, 2 passed, 0 failures, 0 errors, 0 skipped
- Test methods: `oversizedLineDiffFallsBackWithoutTruncatingBeforeOrAfterPreview`; `reviewShowsExactBeforeAndAfterAndClosingKeepsSavedRevisionUnchanged`
- XML: [`TEST-com.localai.workspace.ui.ArtifactRevisionPreviewUiTest.xml`](TEST-com.localai.workspace.ui.ArtifactRevisionPreviewUiTest.xml)
- Full log: [`gradle.log`](gradle.log)

## Task boundary

The original M03-07 DoD includes preview, comparison, export visibility, and cancel without modifying the saved original. The focused HOST test covers those presentation/cancel assertions; existing SAF export behavior is not retested here. M03-07 still requires physical-device validation and depends on M03-01, whose source status is `TODO`. Therefore this result is not formal M03-07 completion. No full JVM/LiteRT gate, lint, assembly, AndroidTest, emulator, Motorola, APK, or performance benchmark was run.

The source backlog and roadmap remain unchanged: 97 unique IDs, 120 dependency edges, all 97 source statuses `TODO`.
