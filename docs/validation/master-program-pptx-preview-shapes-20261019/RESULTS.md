# PPTX editable-shape approximate preview follow-up — 2026-10-19

## Outcome

The existing Compose artifact dialog now shows a separate 16:9 placement canvas for native PPTX figures. It draws rectangles, ellipses, rounded rectangles and triangles with the descriptor's per-thousand slide coordinates, fill and outline colors. Shape descriptions and optional visible labels remain available to accessibility services. The surrounding UI explicitly says the canvas is approximate and is not a rendered PowerPoint slide. Preview remains read-only.

The change is preparatory work for original backlog task M03-05. M03-05 remains source `TODO`, `requires_device=true`, depends on M03-01 (`TODO`), and is not formally complete. The canonical backlog and roadmap were not modified. Their preserved hashes still identify 97 unique IDs, 120 dependency edges and 97 `TODO` source statuses.

## Code and validation

- Production preview implementation and shared label contrast helper: `67b66e0c287889a231d6497207f5883f611d29b9`.
- Final tested source/test SHA: `45e4a79b32020b7cca5aa781e08c79d42f3b9f6d`.
- Regression test source commit: `79301a0cac6c7d99134d500d7d40abe3c75047c8`.
- Test: `PptxImagePreviewUiTest.savedPptxShowsAccessibleSlideLayoutAndImageAssetWithoutChangingRevision`.

The final test verifies accessibility descriptions for all four shape kinds, visible labels where present, the rectangle's bounds relative to the normalized 16:9 canvas, existing slide and supplied-image preview content, and unchanged stored artifact bytes/revision history.

Three invocations are kept distinct:

1. **Expected red** — the text-only preview lacked `pptx-figure-canvas-1`, and one assertion failed. The invocation preflight recorded product base `7330112865450a0b1b6db7997cb10d43506b3aac`; the exact test worktree SHA was not captured. The test source from that working-tree run was subsequently committed as `79301a0cac6c7d99134d500d7d40abe3c75047c8`, so this is not reported as a run at that commit.
2. **Test compile error** — at `67b66e0c287889a231d6497207f5883f611d29b9`, `compileDebugUnitTestKotlin` failed because the test imported `fetchSemanticsNode` as a top-level function. No test body ran and no fresh XML was produced. The test was corrected to call the public `SemanticsNodeInteraction` member; product behavior was unchanged in this fix.
3. **Focused green** — at exact source/test SHA `45e4a79b32020b7cca5aa781e08c79d42f3b9f6d`, Gradle 8.10.2 exited 0 in 1m31s. The JUnit XML reports **1 test, 1 passed, 0 failures, 0 errors, 0 skipped** (method time 9.088s). Gradle reports 63 actionable tasks, 3 executed, 60 up to date. The log also shows the existing configuration-time `nativeAar` performance warning; it did not fail the build.

The final run used Temurin 17.0.20.1 as the Gradle runtime, Temurin 21.0.12.1 as the test worker, Android SDK API 35 and NDK 27.0.12077973 already present in `/workspace/.toolchain`; `/workspace/.gradle` was reused offline and not cleaned. Commands, timestamps, complete logs, XML, task outcomes and SHA-256 values are in the `attempt-*` directories below.

## Limits

This is a schematic Compose preview, not Office rendering or WYSIWYG validation. The focused test checks accessible nodes and geometric bounds; no screenshot/pixel comparison was run. No full JVM/LiteRT gate, lint, assemble, AndroidTest compilation, emulator, physical Motorola, APK or model/performance test was run for this follow-up. `M01-06=PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`.
