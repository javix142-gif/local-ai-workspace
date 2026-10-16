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

At the time of the initial green run, Semantic V2 returned verified relative path and line range, while lexical fallback still lacked source-code line coordinates; the follow-up below closes that measured gap. Symbol-aware parsing/ranking is not implemented. Physical/Motorola, emulator, full-suite, memory/latency and real-codebase validation were not run. M02-08 remains source TODO and preparatory/partial.

`attempts/attempt-01-red` through `attempt-03-red` were local harness iterations and are not cited as valid red evidence; the committed red run is attempt 04.

## Lexical fallback provenance follow-up

A second red regression at test-only commit `9cdee355943ff62656ad8973b13acb7b39766772` showed that legacy lexical retrieval of a code archive still labeled evidence with the archive filename (`archive-lexical-doc.zip`) instead of `src/Answer.kt`. The one-test run failed as expected. Its complete log is preserved losslessly as gzip in `attempts/attempt-06-lexical-red/`; the JUnit XML was overwritten by the next Gradle invocation before it could be copied, so no XML is claimed for that attempt.

The first line-provenance test draft then failed because it expected the function's starting line 3 while the production chunker correctly returns the complete indexed chunk, lines 1–4. This was a test-expectation mismatch, not a product defect. The assertion was corrected to the actual stored chunk interval, retaining the exact path and range checks. That focused run passed 1/1 against tree `8a67620f2a00e5df4a0c6717944c6c3e450beead` (62 seconds, exit 0). The exact committed-source run on `a6037e8d0260386a4f42448b05f81c251208ea7e`, with the same tree SHA, then ran the three archive ContextFoundation regressions and all three `CodeSegmentsTest` cases: **6/6 passed**, zero failures/errors/skips, `BUILD SUCCESSFUL in 32s`, exit 0; one of 63 actionable Gradle tasks executed and 62 were up-to-date. JUnit XML and full log are preserved under `attempts/attempt-09-committed-source-final/`.

The fallback now retains relative source path and line interval through final ContextBundle/citation provenance for the bounded recognized-code cases, and it resolves line metadata only against the currently authorized source/archive when its parsed chunks match the stored segment sequence. No Room schema, migration, dependency, runtime, model or inference behavior changed. This closes the measured lexical-path/line gap for the focused synthetic fixture; it does not add symbol-aware parsing or full Git-ignore compatibility.

Environment for the exact-source run: Gradle 8.10.2 offline; Temurin JDK 17.0.20.1 runtime and JDK 21.0.12.1 test toolchain; existing Android SDK `/workspace/.toolchain/android-sdk`, NDK 27.0.12077973. The command, timings, task outcomes, XML, raw log digest and deterministic gzip log are listed in the attempt folder. Full JVM/LiteRT, lint, AndroidTest compilation, emulator, Motorola and APK were not run by this focused validation.

The original M02-08 ID remains TODO and dependency-blocked by the formal M02-05 chain. The 97 source IDs, order, 120 dependencies and TODO statuses are unchanged. No inference-performance or physical-device result is claimed.


## Automatic Android workflows on the previously published SHA

Push commit `661c7e2a83b9f629f79dd79175da19337a6d7a3d` had already started emulator run [38965340181](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38965340181) before the lexical-fallback follow-up was published. Its AVD booted and the connected instrumentation task ran, then failed: the collector reported 16 cases (9 passed, 1 failed, 6 skipped; 10 executed, 0 errors), with the historical Room v1 fixture as the failure and six model-dependent cases skipped. This is an earlier source SHA, not validation of `a6037e8`. The matching QA run [38965340152](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38965340152) failed closed before building or uploading an APK. GitHub reports evidence artifact `12074682795`, 151,132 bytes, SHA-256 `8a8c4b67bc0eb2f2c873e9025ec62c2e8b0c18f52f6e56618c35a7dc2cd9ff5b`; the CLI download returned HTTP 403 from its signed blob URL, so this executor did not rehash the artifact payload.
