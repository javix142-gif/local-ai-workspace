# M00-06 context composition diagnostics — preparatory evidence

## Scope and result

Code commit: `22014ed8a452836b7fd20de6d1f54db0a2a12f3c` on `codex/master-program-recovery-20261019`.

This increment adds estimator-specific context composition metadata to the existing normal-generation trace and renders it on the existing Performance screen. The recorded fields are the estimator identifier, estimated and available units, included/omitted item counts, included counts by context kind, and omitted counts by reason. No prompt, response, excerpt, filename, path, project/document ID, or evidence text is added. Existing traces without the optional field remain readable. The legacy budget is explicitly identified as a characters-divided-by-four estimate; Context Builder v1 retains its existing byte-based estimate. Neither value is represented as the model's native tokenizer count.

This is preparatory evidence for original task M00-06, not formal completion. The original DoD also requires a real end-to-end timeline across import/parse/index, memory/retrieval, context, gate, model, TTFT/decode and total response, with no private content. Existing import/index diagnostics and request metrics are separate traces correlated only by matching opaque IDs; this increment fills the missing per-request context-composition summary but does not create one causal import-to-inference timeline. No model/device performance measurements were made. M00-06 remains source `TODO`.

## Exact-SHA HOST validation

The authoritative execution is `attempt-07-committed-rerun`, against code SHA `22014ed8a452836b7fd20de6d1f54db0a2a12f3c`. Gradle 8.10.2 ran on OpenJDK 17.0.20.1; the app test worker launched with OpenJDK 21.0.12.1. Android SDK API 35 and NDK 27.0.12077973 were already installed. The existing `/workspace/.gradle` cache was used offline; no toolchain or dependency was installed and no cache was cleared.

The run used the exact committed source SHA, with no tracked source modifications. The checkout retained ignored local Gradle/build outputs and two unrelated untracked evidence directories; it was not a clean-checkout build. The captured `worktree-status.txt` records that state.

Command, environment, timestamps, exit marker, complete compressed Gradle log, task list and raw JUnit XML are in the attempt directory. The command used `--rerun-tasks`; Gradle reports 63 actionable tasks executed, including `:app:testDebugUnitTest`.

| JUnit suite | Tests | Pass | Fail | Error | Skip |
|---|---:|---:|---:|---:|---:|
| `ChatSessionPipelineTest` | 1 | 1 | 0 | 0 | 0 |
| `GenerationTimelinePresentationTest` | 4 | 4 | 0 | 0 | 0 |
| `NormalGenerationTraceTest` | 5 | 5 | 0 | 0 | 0 |
| **Total** | **10** | **10** | **0** | **0** | **0** |

Gradle exit code: 0. Wall time: 201 seconds (Gradle reported 3m20s). `RESULTS.json` contains the machine-readable totals and exact paths; `SHA256SUMS` covers 38 evidence payload files in this directory, excluding the two result summaries and the manifest itself. Its SHA-256 is `1e2d8d676b2d5e309a4862ca65c127d5e63e47ebeaf8c69163a1a5c584ad161b`; local `sha256sum -c` verified 38/38.

The exact-SHA attempt immediately before this run exited 0 but marked all tasks `UP-TO-DATE`, so it executed zero tests and is not counted. The final forced rerun is the source of the PASS above. Earlier test-first history is preserved under `attempts/`: the initial regression failed because the trace lacked context metadata; one intermediate test failed because it incorrectly required a nonempty context fixture (empty context is valid); the corrected focused runs passed. The first red run's JUnit XML was overwritten before archival, so its raw log is retained but no red XML is claimed.

## Not run / boundaries

- Full app JVM suite and LiteRT compatibility suite: `NOT_RUN`.
- Lint, assembly, AndroidTest compilation: `NOT_RUN`.
- Android instrumentation/emulator: `NOT_RUN` for this increment.
- Motorola/physical-device validation: `NOT_RUN`.
- APK generation: `NOT_RUN`.
- Native TTFT, decode throughput, memory, quality, energy, thermal, and end-to-end response performance: `NOT_MEASURED`.

The canonical backlog and roadmap were not modified: 97 unique IDs, 120 dependency edges, and all 97 source statuses remain `TODO`. M01-06 remains `PENDING_REQUIRES_DEVICE`. No task is formally closed by this increment.
