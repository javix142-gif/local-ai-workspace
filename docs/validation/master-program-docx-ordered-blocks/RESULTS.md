# M03-03 preparatory check — ordered DOCX content blocks

This increment adds ordered paragraph/table blocks to the existing generated-DOCX path. It does **not** close original task M03-03. That source task remains `TODO`, requires device validation, and depends on M03-01 and M02-03, both still `TODO`.

## Finding and code

Before the change, `ArtifactSpec` accepted paragraphs and tables as separate collections, and `ArtifactRenderer` emitted every paragraph before every table. A DOCX body containing paragraph → table → paragraph could not be represented. The red regression on test SHA `a0a71455f9bdf1305097dd0cb987e0b3b92fb0f5` failed at `ArtifactSpec.parse` with `ARTIFACT_FIELD_UNSUPPORTED` (1 test, 1 expected failure; Gradle exit 1, 49 seconds). The captured Gradle log is retained; the run's JUnit XML was overwritten by the later test run and is not available.

Production commit `d5d49ba9fba4cfaf9869c8118142bc3abe213ad3` adds a bounded, validated `blocks` form for generated DOCX descriptors and renders paragraphs/tables in submitted order. Existing `paragraphs`/`tables` descriptors continue through their prior path. The project tool description and draft editor now use the ordered form for DOCX; PDF remains on its existing descriptor. No imported document is rewritten, no formula/content semantics were added, and no dependency was added. Test commit `463819926bf61523c59cab96ccc083513e19d8f4` verifies preview and OOXML body order across two immutable revisions, including preservation of the original bytes/hash.

## HOST results

The final source SHA was `463819926bf61523c59cab96ccc083513e19d8f4` on `codex/master-program-20261019`.

| Check | Result | Evidence |
|---|---|---|
| `DocxArtifactStructureTest` | **PASS 2/2**, 0 failures/errors/skips; Gradle exit 0; 46 s wall; Gradle says `BUILD SUCCESSFUL in 45s` | [focused attempt](attempts/final-focused/) |
| Independent `python-docx` 1.2.0 read of ordered v1/v2 | **PASS**; both ZIP CRCs pass; both readers expose paragraph/table/paragraph order and v2 content; v1 hash remains unchanged in ArtifactStore | [reader output](attempts/final-focused/external-reader.json) |
| `:app:lintDebug` (in combined lint/compile command) | **BLOCKED: uncached offline dependency**, not a lint pass. CMake/Ninja Release builds for arm64-v8a and x86_64 completed; `:llama-runtime:extractDebugAnnotations` then failed because `com.android.tools.lint:lint-gradle:31.7.3` is absent from the existing offline cache. `:app:lintDebug` was not reached. | [lint attempt](attempts/proportional-host/) |
| `:app:compileDebugAndroidTestKotlin` | **BLOCKED: uncached offline Android test dependencies**. Separate invocation failed resolving the test runtime classpath during `:app:processDebugAndroidTestManifest`; Kotlin compile task was not reached. No network retry or installation was made. | [AndroidTest compile attempt](attempts/proportional-host/) |

Toolchain: Gradle Wrapper 8.10.2, Temurin JDK 17.0.20.1 for Gradle, Temurin JDK 21.0.12.1 for JVM test worker/toolchain discovery, Android SDK API 35 and NDK 27.0.12077973, existing `/workspace/.gradle` cache, offline. The commands and full compressed logs are retained. The test run used the focused class; no full JVM/LiteRT suite was run.

## Limits and task state

- HOST evidence is preparatory. M03-03 remains source `TODO`, `requires_device=true`, depends on M03-01 and M02-03 (both `TODO`); no backlog status/dependency was changed.
- This validates newly generated synthetic DOCX paragraph/table ordering and immutable version behavior. It does not establish imported DOCX editing, SAF export on Android, rendering in Word/Google Docs, or Motorola behavior.
- No emulator, physical device, APK, or performance measurement was run.
- The original backlog and roadmap remain byte-identical: 97 unique task IDs, 120 dependency edges, all source statuses `TODO`.
- `main` and `v0.4.2` remain untouched.

The manifest `SHA256SUMS` covers all retained files in this evidence directory other than the manifest itself.
