# M03-03 preparation — built-in DOCX style templates

Date: 2026-10-19. This is a host-tested preparatory slice; it does not close the original backlog task.

## Finding and change

The source backlog defines M03-03 as “DOCX con estilos tablas y plantillas abre en lectores comunes; conserva original/versiones”. Before this change, `ArtifactSpec.parse` rejected a top-level DOCX `template` field as `ARTIFACT_FIELD_UNSUPPORTED`. The red reproduction used test-only commit `90cf6cb0e81c7d378cdc774799c981f15636247c`, with the production `ArtifactSpec.kt` and `ArtifactRenderer.kt` files restored to their base blobs from `fad9dae641263157c72f2cbcf308658c553e617c`. Its class XML records 3 tests, 1 expected failure, 0 errors and 0 skips; the new template test failed at the unsupported field.

Production commit `1cdcddb471eb795b526e16b4cd43997176958ba1` adds a bounded DOCX-only selector:

- `standard` is the default and preserves the previous `word/styles.xml` bytes; explicit `standard` is also byte-identical.
- `report` defines Calibri styles with a #3E746D title/heading color.
- `meeting_minutes` defines Arial styles with a #1D2522 title/heading color.
- Title and Heading 1 have explicit paragraph/run properties; the document content, block order, tables and immutable ArtifactStore revisions remain intact.
- Unknown DOCX template identifiers fail with `DOCX_TEMPLATE_UNSUPPORTED`. The field remains invalid for other formats.
- The Artifacts draft UI exposes the three choices and updates the structured descriptor. The tool description advertises the same names.

No dependency, database, schema, migration, model, inference, retrieval, permissions or workflow changed.

## Focused host result

Tested HEAD: `56090d0e4d5c686a9346a5359dfd18d86e7c5d78` (tree `5e11605f1a9c3cdb1f06d72174a254d8717d3ba0`). Production source commit: `1cdcddb471eb795b526e16b4cd43997176958ba1`.

Command, with the existing offline cache and toolchain:

```text
JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.artifacts.DocxArtifactStructureTest --tests com.localai.workspace.ui.ArtifactsDocxTemplateUiTest
```

Gradle 8.10.2, Temurin 17.0.20.1 Gradle runtime, Temurin 21.0.12.1 test toolchain, Android API 35, NDK 27.0.12077973, existing `/workspace/.gradle` cache, offline mode. Started 2026-10-19 15:33:34 UTC; finished 15:34:23 UTC; 49 seconds wall; exit 0. Gradle reported `BUILD SUCCESSFUL`, 63 actionable tasks (3 executed, 60 up-to-date).

JUnit totals: **4/4 passed**, 0 failures, 0 errors, 0 skips. `DocxArtifactStructureTest` passed 3/3, covering both presets’ style definitions and application to title/heading paragraphs, preserved paragraph/table order/content, valid ZIP CRC, unknown-template rejection, DOCX-only schema, legacy style-part byte compatibility, and immutable revision/template preservation. `ArtifactsDocxTemplateUiTest` passed 1/1, selecting Report and Meeting minutes in the dialog and checking the accessible selected value and descriptor.

An independent `python-docx 1.2.0` reader run on Python 3.12.14 reopened both generated files. Both ZIP CRC checks passed; both retained three paragraphs followed by a table, resolved `Title`/`Heading 1` styles, and retained exact title/body/table text. Report opened with Calibri 20/16 pt in #3E746D; meeting minutes opened with Arial 18/14 pt in #1D2522. The generated files are synthetic fixtures, not user documents. The reader script and JSON output are included here.

The `red-baseline/` XML/log are the expected pre-change failure. `ui-iteration-failure.*` records an intermediate test-assertion issue; the semantics dump showed the selected descriptor, and the test was corrected to inspect `EditableText`. It was not a production failure; the exact final-SHA run is green.

## Gates and limitations

Only the two focused JVM/Robolectric classes and the independent Python reader ran for this increment. The full app JVM suite, LiteRT compatibility suite, lint and AndroidTest compilation were not run here. The previous proportional lint attempt for the ordered-DOCX increment was blocked offline by uncached `com.android.tools.lint:lint-gradle:31.7.3`; its AndroidTest Kotlin compile was blocked before Kotlin compilation by uncached AndroidX/Room test dependencies. Those historical blockers are not claimed as a current-SHA pass.

EMULATOR and PHYSICAL_DEVICE were not run; no APK was generated. The persisted global emulator state remains FAIL for the historical Room v1 fixture with six model-dependent tests skipped; this increment did not rerun it. No Microsoft Word, LibreOffice or Motorola visual-open test was performed. These host checks establish valid package structure and Python reader compatibility only; they do not establish universal Office rendering.

M03-03 remains `TODO`, `requires_device=true`, with original dependencies M03-01 and M02-03 still TODO. No original task ID, dependency, order or source state changed. M01-06 remains `PENDING_REQUIRES_DEVICE`. The archived source catalog remains 97 unique IDs, 120 dependency edges, all 97 statuses TODO. Backlog and roadmap hashes are unchanged.

Evidence files and their SHA-256 values are listed in `SHA256SUMS`.
