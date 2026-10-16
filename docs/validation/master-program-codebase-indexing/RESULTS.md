# M02-08 codebase archive indexing — focused preparation evidence

This is preparatory evidence for original task M02-08, not a formal task completion. The source catalog remains unchanged (97 TODO task IDs, 120 dependency edges); M02-08 depends on M02-05, which is not formally complete.

## Finding and implementation

Before the fix, a ZIP imported as `source-archive.zip` was indexed as ordinary document text: semantic hits used the archive display name rather than the source path, and `.gitignore`-excluded source files remained visible. The red regression on test-only commit `dd4bd0c59098692557c8b10176dcf418f3cf8f25` reproduced both defects (2 tests, 2 failures).

The focused code commit `1863a68e407fd40d10fcc4fb13d21afeef94d60b` adds bounded ZIP source extraction, root/nested ignore handling for the documented subset, line-preserving code chunks, and Semantic V2 indexing with relative source path and line range. The integration test reaches the final ContextBundle and checks the source path/range, ignored content and cross-project isolation. No Room schema/migration, dependencies, models, or inference runtime changed.

The ignore reader intentionally supports comments, negation, root anchoring, directory rules, `*`, `?`, and `**`; it is not full Git ignore-spec compatibility. Unsupported syntax is rejected instead of silently exposing potentially ignored files. Existing ZIP entry/count/expanded-size and parser limits remain in force.

## Red reproduction

- Source/test commit: `dd4bd0c59098692557c8b10176dcf418f3cf8f25`
- Command/environment/log/XML: `attempts/attempt-04-red/`
- Result: 2 tests, 2 failures, 0 errors, 0 skipped, exit 1. The semantic result returned `source-archive.zip` instead of `src/Answer.kt`; parser output contained ignored-directory content.

## Green focused host run

- Code commit: `1863a68e407fd40d10fcc4fb13d21afeef94d60b`
- Tree SHA tested: `731ea3efba9fa4f84575fe2a730774c2491e614c`, identical to the code commit tree. The run started before commit creation with staged, uncommitted sources; no source changed between run and commit.
- Command: `attempts/attempt-05-final/command.txt`
- Environment: Gradle 8.10.2, Temurin 17 runtime, Temurin 21 test worker, existing SDK `/workspace/.toolchain/android-sdk`, NDK 27.0.12077973, Gradle offline. No tools/dependencies were installed.
- Time: 2026-10-16 20:43:21–20:47:27 UTC (246 seconds); exit 0; Gradle reported `BUILD SUCCESSFUL in 4m 5s`; 63 actionable tasks executed.
- JUnit: 5 tests, 0 failures, 0 errors, 0 skipped across the two XML files under `attempts/attempt-05-final/junit/`.

The focused run compiled the changed production and test sources and executed only the two new archive regressions and the existing three `CodeSegmentsTest` cases. It was not the full JVM suite, lint, AndroidTest, emulator, or device validation.

## Limits

Semantic V2 returns verified relative path and line range for this indexed archive fixture. Legacy lexical fallback does not yet carry source-code line coordinates; this increment does not claim line-exact fallback. Symbol-aware parsing/ranking is not implemented. Physical/Motorola, emulator, full-suite, memory/latency and real-codebase validation were not run. M02-08 remains source TODO and preparatory/partial.

`attempts/attempt-01-red` through `attempt-03-red` were local harness iterations and are not cited as valid red evidence; the committed red run is attempt 04.
