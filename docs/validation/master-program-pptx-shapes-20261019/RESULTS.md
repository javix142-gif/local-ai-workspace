# Preparatory PPTX native figures — 2026-10-19

## Result

The app-generated PPTX descriptor now accepts a bounded set of native editable figures: rectangle, ellipse, rounded rectangle, and isosceles triangle. Each figure has a required accessible description, optional visible label, normalized placement, and bounded RGB fill/outline colors. Shapes are emitted as native DrawingML `p:sp` records, not rasterized images. The textual artifact preview lists each figure's description, label, and normalized box; it explicitly remains a textual preview, not a rendered slide canvas.

The regression was reproduced before implementation: the new descriptor reached the existing PPTX field allowlist and failed with `ARTIFACT_FIELD_UNSUPPORTED` in `ArtifactSpec.parse`. The production implementation then passed the focused artifact test class, including prior image/relationship/revision tests. The synthetic output was opened independently with `python-pptx 1.0.2`; it recognized all four geometry presets as editable auto-shapes, read their visible labels and accessible descriptions, and passed ZIP CRC validation.

## Source task boundary

This is preparatory implementation for original task **M03-05**, not completion. The authentic backlog still marks M03-05 `TODO`, `requires_device=true`, dependent on M03-01 (`TODO`). M03-01 itself remains `TODO`. No original task ID, dependency, source status, or plan file changed. The verified source catalog remains 97 unique tasks and 120 dependency edges, all source statuses `TODO`.

## Code and commands

- Production implementation: `8573aa3` (`feat(artifacts): render bounded editable pptx figures`).
- Final test/evidence source: `b46827368ad289a73ea9bde342e86aaebe54efa8` (`test(artifacts): export editable pptx fixture for reader validation`).
- Red regression: test content later committed in `b14c737e8ac73aa39e88b6ad1acfa073906ae848`; at invocation, production was unchanged from `1f955a8f4abac3d04a8a4b946e938f644e0e928d` and the regression existed as a working-tree test edit. It failed 1/1 for the expected unsupported `shapes` field.
- Green Gradle command: see `attempt-03-interoperability/command.txt`.
- Reader command: see `attempt-03-interoperability/reader-command.txt`.

The final Gradle run used wrapper 8.10.2, Temurin 17 runtime, Temurin 21 test worker, installed API 35 SDK/NDK 27.0.12077973, the existing Gradle cache in offline mode, and a 300-second timeout. It completed in 43 seconds with exit 0; 63 actionable tasks, 3 executed and 60 up to date. The JUnit XML at `attempt-03-interoperability/TEST-com.localai.workspace.artifacts.PptxImageArtifactTest.xml` reports **6 tests, 6 passed, 0 failures, 0 errors, 0 skipped** (suite time 5.273 seconds). An earlier green run of the same class at production commit `8573aa3` also passed 6/6 in 64 seconds.

The independent reader opened `generated-figures-v1.pptx` (5,408 bytes), verified ZIP CRC, one 16:9 slide, and four editable figure shapes with descriptions. Its first checker invocation exited 1 because the checker expected the enum label `TRIANGLE`; `python-pptx` correctly reports the OOXML triangle preset as `ISOSCELES_TRIANGLE`. The file was unchanged. The checker was corrected and the subsequent run exited 0. Both outputs are retained in `attempt-03-interoperability/`.

## Limits

This focused HOST result does not establish rendering fidelity in Microsoft PowerPoint/LibreOffice, Android presentation viewers, Compose preview, Gemma tool selection, Motorola behavior, or the full M03-05 roundtrip DoD. No full JVM/LiteRT suite, lint, assemble, AndroidTest compilation, emulator, physical device, or APK was run for this increment. `M01-06` remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. No claim is made about device speed or inference.

All evidence payloads, including both red/green Gradle logs, JUnit XML, the reader check, and the synthetic PPTX, are hashed in `SHA256SUMS`.
