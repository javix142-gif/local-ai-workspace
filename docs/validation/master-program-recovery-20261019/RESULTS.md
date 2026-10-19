# Master-program recovery checkpoint — 2026-10-19

## Scope and state

Work is isolated on `codex/master-program-20261019`, a descendant of the authorized feature history. The earlier recovery checkpoint's test-only tip was `98665c064f08389c46f4e4fe13545f1b27088751`; the latest code/test tip is `9f99beba0d38787a62e83bbfa457cc39b9e9d0ab`. The latest M00-06 increment adds a bounded, opaque-ID correlation between a normal-chat trace and its runtime metrics, plus a separate import/index timeline view; see the exact-SHA addendum below. No database, dependency, workflow, model, permission, retrieval-semantic, or inference-runtime change was made. The evidence folder preserves the M00-04 and M00-05 attempts, M03-07 preview tests, M00-06 instrumentation tests, and a focused M02-08 code-archive audit.

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

### M00-06 — strict chat-metric correlation and import/index timeline (2026-10-19)

Code SHA `9f99beba0d38787a62e83bbfa457cc39b9e9d0ab` adds an opaque normal-generation trace ID to runtime metrics and serializes it with the existing metrics presentation. The Performance screen joins those values only when the metric ID exactly matches the displayed chat trace; otherwise it explicitly reports that correlated metrics are unavailable. It shows only actual non-null runtime values, including native TTFT/prefill/decode when the real backend supplies them, and never derives or fills missing values. A separate import timeline orders copy/hash, metadata commit, parse, chunk, segment commit, callback and import total. The background EG1 indexing duration is included only when its run ID matches the import's opaque index ID; stale or unidentified runs are explicitly left unavailable. The two operations remain separate timelines because document ingestion/indexing and a later chat request are distinct operations.

The exact-SHA focused HOST run passed **10/10** (0 failures/errors/skips), Gradle exit 0, 120 seconds by recorded timestamps. It ran `GenerationMetricsPresentationTest` 4/4, `DocumentPipelineTimelinePresentationTest` 2/2, `GenerationTimelinePresentationTest` 3/3, and the exact normal-chat pipeline method 1/1. The test event listener recorded 10 starts and 10 successful ends; all four fresh JUnit XML files reconcile to 10/10. Command, environment, full Gradle log, XML, task markers and hashes are in [`M00-06 correlated timeline evidence`](m00-06-correlated-timeline-9f99beb/). The bundle manifest has 17 payload entries and SHA-256 `3c0e8ad114e22f30b9b47bce4844206e2931fc6eb0d3636364f634c6b8291a4f`.

This closes a host-tested presentation/correlation gap, not the original M00-06 DoD. There is still no single causal document-import-to-chat trace, no real model run proving native TTFT/prefill/decode values in this environment, and no target-device cold/warm, memory, quality, energy or thermal data. The source task remains `TODO`; no canonical task status was changed. Full JVM/LiteRT gates, lint, AndroidTest compilation, emulator, Motorola and APK were not run for this increment.

## M02-08 — code archive path/line retrieval

Inspection found the implementation already present: bounded ZIP code-file extraction, nested `.gitignore` filtering, code line chunking, semantic indexing with relative path/language/line metadata, project-scoped retrieval, and lexical fallback. At `efab48751ae0da97b7d29782b8fc425f39c894c2`, four focused methods in `SemanticContextPostfixTest` passed 4/4:

- EG2 code hit preserves exact path, line and citable evidence.
- Code archive search preserves relative path/line and excludes a foreign project's homonymous path/content.
- Lexical fallback preserves relative path, line and evidence.
- Archive parsing excludes ignored build/vendor/generated and `.git` entries.

No redundant product change was made. This is preparatory HOST evidence only: M02-08 remains source `TODO`, and its M02-05 dependency remains source `TODO`; M00-10/device gates are not waived. Evidence is under [`m02-08`](m02-08/).

## M03-07 — artifact preview/comparison

At `bbcd5517f567910170a6a6d7c9f98b6ebbb82098`, the focused preview/revision and patch-preview UI tests passed 4/4. This is historical host evidence only. M03-07 remains source `TODO`, requires a device and depends on M03-01, still source `TODO`. Evidence is under [`m03-07`](m03-07/).

The same preview class was re-run against the current source HEAD `bceb60ade0e6e3697d4f9a7d4d731869d9c8978a` to check that later artifact UI additions had not regressed this path. `ArtifactRevisionPreviewUiTest` passed **2/2**, 0 failures/errors/skips, exit 0, with Gradle `BUILD SUCCESSFUL` in 212 seconds. It covers visible saved/proposed content and bounded line changes, export remaining attached to saved v1, cancel preserving bytes/hash, and the oversized-diff fallback preserving complete before/after bodies. Exact command, JDK/SDK, full log, task markers, XML and hashes are in [`current HEAD revalidation`](m03-07-current-sha-bceb/).

This remains focused HOST evidence only. It does not establish full cross-format preview/SAF usability or physical-device behavior. M03-07 stays source `TODO`, `requires_device=true`, and depends on M03-01 `TODO`.

## Validation boundaries

| Layer | Result in this checkpoint |
|---|---|
| HOST focused M00-04 | PASS 2/2 at `bbcd551` |
| HOST focused M00-05 | PASS 1/1 at `78b5309`; Shadow IME only |
| HOST focused M00-06 | PASS 1/1 document/index at `efab487`; PASS 1/1 chat trace at `98665c0`; latest exact-SHA correlation/timeline increment PASS 10/10 at `9f99beb` |
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

The M03-07 current-HEAD bundle has its own child manifest. The new M00-06 correlated-timeline bundle has 17 payload entries and child manifest SHA-256 `3c0e8ad114e22f30b9b47bce4844206e2931fc6eb0d3636364f634c6b8291a4f`. The enclosing recovery manifest has 113 payload entries and SHA-256 `af7fe8f41abae15e4664a4b83f516bed4f25d68bbb33edd7809136baa71d92c0`; it covers the preserved payload list plus the new bundle. Both levels verify locally with `sha256sum -c`.
