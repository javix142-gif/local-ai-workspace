# Master-program recovery checkpoint — 2026-10-19

## Scope and state

Work is isolated on `codex/master-program-20261019`, a descendant of the authorized feature history. Code/test tip for this checkpoint: `98665c064f08389c46f4e4fe13545f1b27088751`. The incremental changes here are tests only; no product/runtime, database, dependency, workflow, model, permission, or inference change was made. The evidence folder preserves the M00-04 and M00-05 attempts, M03-07 preview tests, M00-06 instrumentation tests, and a focused M02-08 code-archive audit.

The canonical backlog and roadmap were not edited. Their hashes remain backlog `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and roadmap `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`. The backlog still has 97 unique IDs, 120 dependency edges, and 97 `TODO` source statuses. This report records preparatory evidence; it does not promote source-task status.

## M00-04 — material context-omission notice

At `617082eb9f9ce3eaa28c313b2e8719aa934fdbaa`, the focused run was red: two tests ran, one passed and the Inspector test failed on a 25-second wait for a combined accessibility-text selector. The UI exposes the title and detail as separate Compose text nodes; this was an assertion/query mismatch, not a demonstrated production defect. The original raw Gradle command was not captured and is not reconstructed.

At `bbcd5517f567910170a6a6d7c9f98b6ebbb82098`, the corrected focused run passed 2/2. One test confirms a relevant synthetic memory omission gives a comprehensible notice while a routine below-threshold omission does not. The Inspector test checks the dropped item ID and `TOKEN_BUDGET` reason and confirms the synthetic private memory text is not exposed. Evidence is in [`m00-04`](m00-04/).

## M00-05 — chat header and compact composer

At `78b5309f5ea7388a2ab780c8902156b7b196512f`, `ChatRefinementUiTest.compactComposerAndNestedInsetsKeepConversationAndActionsVisible` passed 1/1. It exercises 360/393/411 dp internal widths at font scales 1.0/1.3/1.6/2.0, checks the full conversation title, agent/model labels, first-message bounds relative to header/composer, composer controls and callbacks, and focus plus Robolectric's shadow IME-visible state.

Four Compose captures are retained and hash-checked. They are host/Robolectric output, not screenshots from a physical keyboard or Motorola. The reserved bottom inset is a layout constraint; `ShadowInputMethodManager` is not a device IME. The test therefore supports host layout behavior only. Evidence and captures are in [`m00-05`](m00-05/).

## M00-06 — XLSX and normal-chat timing coverage

The original DoD remains source-backed and unchanged: a real timeline covering import, parse, index, memory retrieval, context, gate, model, TTFT, decode and end-to-end, with metadata free of private content. The existing production implementation records document import/index timings and normal-generation checkpoints/metrics. The focused tests below improve host evidence without changing production.

- Test-only attempt at `b0556656652f774df53f8722e08912c49591b7` exited 1 during Kotlin compilation because the new test referred to nonexistent `GenerationMetricsPresentation` properties. No test started. The attempted log and command are retained under [`compile-failure-sha-b055665`](m00-06/compile-failure-sha-b055665/); this was a test-authoring error, not a product failure.
- At `efab48751ae0da97b7d29782b8fc425f39c894c2`, `DocumentPipelineTimingTest.xlsxImportAndBackgroundIndexShareOnlyOpaqueTimingRunId` passed 1/1. Its synthetic XLSX exercises source copy/hash, metadata commit, parse, chunking, segment commit, callback, total and background EG1 indexing, with an opaque correlated run ID and privacy assertions. `ChatSessionPipelineTest.normalChatTraceOrdersContextBeforeGateAndVisibleContentWithoutPersistingText` also passed 1/1 at this SHA before the final assertions were strengthened.
- At `98665c064f08389c46f4e4fe13545f1b27088751`, the final normal-chat test passed 1/1. It asserts model preparation/readiness, memory completion, Context V1, context/retrieval/history/gate timing fields, ordering before generation, first visible content, request completion/settlement and privacy-safe diagnostics. Native TTFT and decode rate remain absent for the fake backend and are explicitly asserted absent rather than fabricated.

These are separate focused invocations, not a single end-to-end import-to-neural-generation trace. They do not measure real model load, native TTFT/decode, quality, RAM, energy, thermal behavior, or Motorola latency. M00-06 remains source `TODO` and is not formally complete. Evidence is under [`m00-06`](m00-06/).

## M02-08 — code archive path/line retrieval

Inspection found the implementation already present: bounded ZIP code-file extraction, nested `.gitignore` filtering, code line chunking, semantic indexing with relative path/language/line metadata, project-scoped retrieval, and lexical fallback. At `efab48751ae0da97b7d29782b8fc425f39c894c2`, four focused methods in `SemanticContextPostfixTest` passed 4/4:

- EG2 code hit preserves exact path, line and citable evidence.
- Code archive search preserves relative path/line and excludes a foreign project's homonymous path/content.
- Lexical fallback preserves relative path, line and evidence.
- Archive parsing excludes ignored build/vendor/generated and `.git` entries.

No redundant product change was made. This is preparatory HOST evidence only: M02-08 remains source `TODO`, and its M02-05 dependency remains source `TODO`; M00-10/device gates are not waived. Evidence is under [`m02-08`](m02-08/).

## M03-07 — artifact preview/comparison

At `bbcd5517f567910170a6a6d7c9f98b6ebbb82098`, the focused preview/revision and patch-preview UI tests passed 4/4. This is historical host evidence only. M03-07 remains source `TODO`, requires a device and depends on M03-01, still source `TODO`. Evidence is under [`m03-07`](m03-07/).

## Validation boundaries

| Layer | Result in this checkpoint |
|---|---|
| HOST focused M00-04 | PASS 2/2 at `bbcd551` |
| HOST focused M00-05 | PASS 1/1 at `78b5309`; Shadow IME only |
| HOST focused M00-06 | PASS 1/1 document/index at `efab487`; final PASS 1/1 chat trace at `98665c0` |
| HOST focused M02-08 | PASS 4/4 at `efab487`; no product change |
| HOST focused M03-07 | PASS 4/4 at `bbcd551` |
| Full app/LiteRT JVM suites | NOT_RUN for this checkpoint |
| Lint / assemble / AndroidTest compilation | NOT_RUN for this checkpoint |
| EMULATOR | NOT_RUN for this checkpoint; prior global Room-v1/model-skip status remains historical and unchanged |
| PHYSICAL_DEVICE / Moto G86 Power | NOT_RUN |
| APK | NOT_GENERATED |

M00-06 remains the next task to reconcile against the full timeline DoD; the focused tests do not prove native model metrics or one complete cross-stage view. After that, M00-07, M00-08, M00-09 and M00-10 remain behind their original device gates. M01-06 remains `PENDING_REQUIRES_DEVICE`. No device result is inferred from host output.

## Reproduction

All successful runs used Gradle Wrapper 8.10.2, JDK 17 runtime (`/workspace/.toolchain/jdk-17`), JDK 21 worker (`/workspace/.toolchain/jdk-21-temurin`), Android SDK API 35 and NDK 27.0.12077973, with the existing `/workspace/.gradle` cache and `--offline`. Exact commands, complete gzip-compressed logs, test-event extracts, task outcomes, JUnit XML, timestamps and exit codes are retained beside each run. `SHA256SUMS` covers evidence payloads; `RESULTS.md`, `RESULTS.json` and `SHA256SUMS` are excluded to avoid a checksum cycle. The independent count/hashes are generated from the working tree before commit.
