# Preparatory M03-05 — approximate PPTX slide-layout preview

**Status:** focused HOST PASS for this preview increment; original M03-05 remains `TODO`, not formally complete.

## Source and change

The production/test code is `375e45035f63576b17c1111ce738720373100b99`. The red test was introduced and fixed in `aa777a0e0a84037fc4d8617a500d6266f9ee665b`, `48b62ccf1f705201ac43ea5f3086407827c4cace`, and `e50f28b1dd0a392deaa777ba22e488bb3424fce4`; implementation and refinements are `92f0823dd325bd3cd9db120845de8069bc431a7f`, `8648bd484866b2e218cf4637cf28fcfdd713c740`, and `375e45035f63576b17c1111ce738720373100b99`.

The artifact dialog now shows every PPTX slide as a 16:9 schematic with the saved title and body. An image placement region is labeled with its existing alternative text. The validated source PNG is displayed separately below at a readable size with slide identity, accessible content description, alt text and source dimensions. A visible notice says **“Approximate layout · not a rendered PowerPoint page”**. Title/body areas can scroll when their content exceeds the schematic region, while the exact selectable textual preview remains available below. No content is generated, inferred, or changed, and previewing remains read-only.

The focused test uses a synthetic two-slide deck (one supplied PNG and one text-only slide). It checks both slide/title/body nodes, the approximation notice, the image placement label, actual image visibility and accessibility, alt text, source dimensions, PNG SHA-256, and unchanged artifact bytes/history.

## Validation

Final command, environment, full Gradle log, timestamps, exit code, JUnit XML, task outcomes and run JSON are under [`attempt-06-green/`](attempt-06-green/). Gradle 8.10.2 ran with Temurin 17.0.20.1 and discovered Temurin 21.0.12.1 for test workers, existing SDK API 35/NDK 27.0.12077973, and existing Gradle cache in offline mode.

- `:app:testDebugUnitTest --tests com.localai.workspace.ui.PptxImagePreviewUiTest`: **PASS**, exit 0; 1 test, 1 passed, 0 failures/errors/skips; test method 10.562 seconds. Gradle reported 1m08s; UTC wall interval was 69 seconds; 7 tasks executed and 56 were up-to-date.
- The red regression at `e50f28b` compiled and executed 1 test, then failed because the slide preview tag did not exist. This is the reproduced product gap.
- Harness/compiler attempts and the first layout's image-visibility failure remain preserved and classified separately in `attempt-01-red/` through `attempt-05-image-visibility-failed/`; none is counted as a passing test.
- No full JVM suite, LiteRT compatibility suite, lint, AndroidTest compilation, connected instrumentation, emulator, physical device, or APK was run/generated for this increment.

## Limits and task status

This is an approximate Compose schematic, not a PowerPoint renderer, exact line-wrap simulation, screenshot, or viewer-fidelity check. The layout visually indicates the supplied image's region and its alt text; the actual PNG is previewed separately. No image generation, charts, animations, imported-file editing, storage or PPTX package behavior changed.

The authentic original source task **M03-05 — Generación/edición básica PPTX** remains `TODO`, requires a device, and depends on M03-01 (`TODO`). This focused preview evidence does not satisfy the full reader/roundtrip/device DoD. The original 97 task IDs, 120 dependencies and all source `TODO` statuses remain unchanged. `EMULATOR` and `PHYSICAL_DEVICE` were not run for this increment; the previously recorded Room-v1 failure and six model-dependent skips remain historical and separate.
