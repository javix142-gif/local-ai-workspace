# Changelog

## 0.4.2 — Memory Relevance Hotfix

- Separate structured-memory relevance from ranking bonuses so pinning, importance and confidence cannot make unrelated memories eligible.
- Skip Structured Memory lookup/embedding for pure small talk; preserve greetings that also contain a substantive query.
- Add safe context diagnostics for relevance components and dropped-memory reasons. Context Builder V1 OFF and database schemas remain unchanged.
- 513 JVM tests passed; debug/release, lint and instrumented APK packaging passed. Targeted Motorola 0.4.2 memory checks remain pending. See [report](docs/MEMORY042_REPORT.md).

## 0.2.0 — 2026-10-06

- Part2: official LiteRT OpenApiTool calling (calculator, project files), Thinking Off/Auto/On with hidden analysis, bounded XLSX/CSV/ZIP parsing.
- Auxiliary EmbeddingGemma GGUF CPU embeddings (768 dimensions), persisted hybrid FTS/cosine/RRF retrieval and explicitly approved relevant memory. Encoder imported separately.
- Opt-in offline CPython/WASM in a private WebView worker with virtual filesystem, execution/output/WASM heap limits; no host filesystem, networking, numpy or pandas.
- Local audio-file preprocessing to bounded PCM WAV and official LiteRT Content.AudioFile + text, CPU audio backend, persisted per-turn reference and no text-only fallback after failed pending media preparation.
- Additive Room5→8 migrations, compact functional composer chips and execution cards, diagnostics retained. No Part3 integrations.
- [Full implementation, evidence and Moto checklist](docs/PART2_IMPLEMENTATION.md). Physical Gemma/WebView/audio/performance acceptance remains PHYSICAL_DEVICE_TEST_REQUIRED.

## 0.1.14 — 2026-10-06

- Stage1.5 UX/UI: compact Workspace privacy/active-model summaries, title search, simpler chat header with model selection and Diagnostics in menus, single + attachment menu, contextual attachment state and compact processing labels. Existing navigation IDs and app-owned chat lifecycle remain unchanged.
- Project tabs expose Chats/Files/Memory/current Settings; compact approved-memory cards with explicit removal confirmation and Global label when relevant. Friendly Gemma/Qwen display names do not rename stored models. Library shows app-available text/images, with model-only/unavailable/experimental capability distinctions in Details; no Audio ✓ claim. Add Model groups device imports and online Hugging Face.
- Basic response formatting for paragraphs/lists/bold/inline/fenced code, language and exact code copy. Finished responses use compact Copy/options actions, preserving Details/Continue and last failed/cancelled Retry. Optional composer slot stays empty until future functions are connected; no fake tools/thinking/audio/web controls.
- No runtime/inference/benchmark/warm-up/KV/GPU/speculative/RAG/memory-policy/Room-schema changes.104baseline app files unchanged by SHA; all8native libraries identical to0.1.13. Theme policy unchanged. Release is a signed update with the same package/certificate;91,977,657bytes.
- Full host validation PASS4m45s:221app tests,2compat results PASS/UP-TO-DATE, debug/release and instrumented-test APK builds, lint0errors/76warnings.19new UI/parser/presentation tests PASS. Modal text-entry flow compiled as a device test after Robolectric idle limitations; keyboard/SAF/physical gestures and Moto colors require PHYSICAL_DEVICE_TEST_REQUIRED.
- [APK](https://drive.google.com/file/d/19mUWuLhWwtddSM-rXHc2H2L5hdNi2iSp/view?usp=drivesdk), [report/screenshots/phone checks](docs/UI_STAGE15_014.md), [manifest](docs/validation/apk-014.json).

## 0.1.13 — 2026-10-06

- Add manual Gemma text performance suite with COLD/WARM/CONTINUATION, iterations, private metadata-only JSON export, profile matrix and per-case comparisons. Separate native/app/benchmark/UI timing boundaries; add main/worker PSS, system RAM and Android thermal status. No physical-device speedup is claimed.
- Add opt-in engine-scoped real warm-up in an isolated temporary conversation, cancellation/deadlines and nonfatal fallback. CPU remains default; GPU and speculative are experimental OFF by default. Bundle capability, request and successful engine configuration are distinguished; GPU/spec fallback is visible and remembered rather than repeated every turn. LiteRT-LM 0.17.1 SDK/native libraries are unchanged.
- Persist canonical effective model turns separately from visible user messages with non-destructive Room3→4 migration. Preserve complete recent history pairs within budget, isolate native conversations by chat/model/config, expose rebuild reasons and real cached token count. Preserve measured TTFT even if generation later fails. Sampling and existing data/preferences remain unchanged.
- Remove private-content logging in the local GGUF adapter/native logging paths; other GGUF output/config/cleanup debt remains outside this stage.
- Final host validation: 202app JVM tests PASS, 2compat tests previously passing/UP-TO-DATE, debug/release builds and lint PASS (0errors,73existing warnings). Signed ARM64 production-package update uses the same certificate as0.1.12;91,879,353bytes. GPU Mali, native warm-up/KV/speculative, performance/RAM/thermal and physical UI checks: PHYSICAL_DEVICE_TEST_REQUIRED.
- [APK](https://drive.google.com/file/d/1adjrjfw3K4juH-f7qgj8aXGuSsUkhEQy/view?usp=drivesdk), [delivery manifest](docs/validation/apk-013.json), [implementation and Motorola validation plan](docs/PERFORMANCE_STAGE1_013.md).

## 0.1.12 — 2026-10-05

- Application-owned ChatSessions retains per-chat controllers while generating/importing; navigation does not cancel them. Workspace shows actual busy tasks, reopen and explicit Stop generation. Persist streamed text checkpoints; settle owned tasks before deletion. Serialize preparation and complete load/generate requests, with pending preparation awaited outside the gate; queued stop cannot cancel another chat.
- Replace inert file chips with Files selection/status/failure/Preview text/Summarize. Readable SAF imports select actual indexed excerpts for the next turn; paperclip images use the existing bounded vision path. Empty/scanned/unsupported files fail clearly; HTML scripts/styles/markup are stripped. Bound evidence to3passages×1000characters; consume only actually supplied sources, retain omitted selections and validate citations against supplied evidence.
- Greetings without selected files skip unrelated RAG/memory/policy; off-Main retrieval filters stopwords/unrelated zero-score candidates. Retain a compatible initialized vision engine for subsequent text. No SDK/native/sampler change, new accelerator, measured CPU speedup or model-quality claim; actual process death may still interrupt work/cold-load weights. Room3 unchanged.
-167app tests executed/PASS (21new),2compatibility tests UP-TO-DATE; full debug tests/build/lint PASS2m15s, lint0errors/73existing warnings. Initial lint indentation failure corrected and full batch rerun; evidence retained. Release/signature/native comparison PASS1m16s; Drive upload/readback PASS,91,666,361bytes,versionCode13. Physical UI/native/file response/timing tests NOT_EXECUTED. [APK](https://drive.google.com/file/d/10_AIAPomtCQnemliE0qksZLEGTM346WO/view?usp=drivesdk), [manifest](docs/validation/apk-012.json).
- [Implementation and exact phone checks](docs/CHAT_FILES_AND_BACKGROUND_012.md).

## 0.1.11 — 2026-10-05

- Start real application-owned CPU preparation of the last selected usable LiteRT model; Workspace shows Active model selection and actual loading/integrity/session/ready/error/cancel/retry. Persist app selection; new chats inherit it while existing project preferences remain honored.
- Shared pool preloader survives chat closure/cancelled waiters; explicit loading cancellation/switch still reaches native preparation. Existing serialized generation leases preserve conversation isolation. Remove the application's ten-minute idle expiry; retain memory-pressure eviction and ready invalidation. One engine is resident; switching models may still initialize the other.
- Cache up to8actual successful worker-lifetime SHA receipts for unchanged app-private file identity/stat/expected hash/size; always check header/format/size, invalidate changes/failures, no reuse without known stat/valid hash. Report `LITERT_INTEGRITY_REUSE`; never read multi-GB models wholly into RAM.
- 146app tests executed/PASS (17new),2compatibility tests UP-TO-DATE; debug tests/build/lint PASS48s, lint0errors/73existing warnings. Release/signature/native-byte comparison PASS1m15s. Room3/SDK0.17.1native/template unchanged; existing deletion/GGUF/RAG/memory/tools/model management retained. Phone UI/native/timing checks NOT_EXECUTED; no faster CPU decode or quality repair claimed.
- [Signed0.1.11 APK in Drive](https://drive.google.com/file/d/1xOJPIKlK9cA4csUT-yphfqR9a9ijf1u_/view?usp=drivesdk),91,584,441bytes,versionCode12; metadata readback PASS. [Implementation and phone checks](docs/APP_MODEL_PREPARATION_011.md), [manifest](docs/validation/apk-011.json).

## 0.1.10 — 2026-10-05

- Reorganize Workspace with primary Start chat, optional Start project, separate lists and actual confirmed deletion for both. Projects have a screen containing independently addressed chats, shared files and memory. Direct chats auto-title from their first user message.
- Delete a standalone chat removes its isolated owner/card and resources. Delete project removes all its chats/private documents/FTS indexes/embeddings/project memory and its card. Delete one project chat keeps other chats/files/memory. Models/preferences/benchmarks/global memory remain.
- Room2→3 real migration preserves existing records, adds workspaceKind defaultPROJECT and durable private-file deletion outbox. Canonical cleanup is limited to app-private documents, with visible retry after failures/restart. Stop recreating the deleted starter project; guard imports against concurrent deletion.
-129app tests executed/PASS (15new),2unchanged compatibility tests up to date; debug build PASS, lint0errors/73existing warnings. Existing model-default invariance tests now compare current schema before/after their helper. New real Room migration validation passes. Physical device checks NOT_EXECUTED; runtime/SDK/native libraries and0.1.9warm pool unchanged. [Implementation/checks](docs/WORKSPACE_010.md), [artifact](docs/validation/apk-010.json).

- Final delivery batch PASS in1m42s; release/signature/native comparison PASS in44s. [Signed0.1.10 APK in Drive](https://drive.google.com/file/d/1DnJoNPZNrboP5QMgNXCkZxN1BcYP1wnI/view?usp=drivesdk),91,535,289bytes,versionCode11. Replaced initial upload in place after correcting the project-import notice to report indexing status accurately; final source/APK rerun and metadata readback PASS. Physical checks remain NOT_EXECUTED.

## 0.1.9 — 2026-10-05

- Keep a healthy unchanged LiteRT engine across project/chat navigation. Application-owned ChatRuntimePool serializes native operations and uses revocable per-screen leases; late old close/cancel/unload cannot affect another owner.
- Add actual RESET_CONVERSATION native-worker IPC: close the session/KV and invalidate its history tracker while preserving verified weights. New chat owners always get clean sessions, including after Delete chat. Backend/model/config changes invalidate reuse as before; GGUF uses its existing unload fallback.
- Evict idle retained engines after10minutes or reported memory pressure; active chats are not cancelled by idle eviction. Android worker/process reclamation and unfinished cold-load cancellation may still require reinitialization.
-114app tests executed/PASS (10new regressions),2compatibility tests up to date; debug assembly PASS; lint0errors/73existing warnings. Room schema2 and SDK0.17.1native libraries unchanged. Physical phone warm-navigation/speed and new inference tests NOT_EXECUTED; prior relevance failure unresolved. [Behavior/checks](docs/WARM_MODEL_019.md), [artifact](docs/validation/apk-019.json).

- Release/signature/native comparison PASS; [signed0.1.9 APK in Drive](https://drive.google.com/file/d/1uog6Eiy_sH97ThrnN6k681EmvtKfy9UE/view?usp=drivesdk),91,420,601bytes,versionCode10. Metadata readback PASS; phone validation remains NOT_EXECUTED.

## 0.1.8 — 2026-10-05

- Added Workspace project-card and chat-toolbar menus with explicit Delete chat confirmation. Removes all conversations/messages/citations/tool history in the selected project in one Room transaction; project files, saved memory, models/settings and model benchmarks remain.
- Open-chat deletion stops and joins preparation/generation, unloads native session/KV state, clears transient reply/evidence/image state and returns to Workspace after success. Duplicate clicks/new sends are blocked during deletion; failures are shown for retry.
- Preserve existing conversation IDs until deletion; replacement chats use fresh IDs, and late writes cannot recreate deleted messages/citations. Concurrent opens are serialized. Room remains schema2 with no destructive migration or runtime SDK/native changes.
- Added seven actual Room regression tests, including rollback under simulated storage failure, preservation/isolation, late writes and concurrent creation. Validation and APK delivery are recorded in [the audit](docs/CHAT_DELETION_018.md) and [artifact manifest](docs/validation/apk-018.json).
- Final104app tests executed/PASS;2unchanged compatibility tests up to date after initial cache restore. Debug/release assembly and same-certificate signing PASS; lint0errors/73existing warnings; Android device execution NOT_EXECUTED. [0.1.8 APK in Drive](https://drive.google.com/file/d/1odrfBn_RHILGYj7Gvm8H6sPqVcfk10b3/view?usp=drivesdk),91,387,833bytes,metadata readback PASS. Signed distribution uses `dist/apk` outside Gradle's cleaned output directory; previous local APKs were cleaned by packaging and remain in Drive, with local restore blocked HTTP403. No model-quality/speedup fix is claimed.

## 0.1.7 follow-up — answer relevance / 2026-10-04

- Captures25091–25095 show consecutive completed answers and metrics behind Details. The communes question receives unrelated categories; the medical-laws answer is unreliable. Installed version/hash/context/timing are not captured, and no new physical speed PASS is inferred.
- Reproduced the communes failure in real native Linux LiteRT CPU with no app policy/history/pre-render/extra repetition controls. Render diagnostics keep native token counts unchanged and contain the current question once. Failing boundary is generated content; exact model/conversion/kernel attribution remains unproven.
- Discarded SDK0.16.1, requestedFLOAT32/0.17.1, sampling/policy experiments and a smaller Qwen3 LiteRT candidate after semantic failures. No production runtime/model/profile change or new APK is represented as a repair. Stable SDK0.17.1 remains current; GPU stays unadvertised.
- Preserved the existing0.1.7 signed APK, database/models/history and all features. Rechecked local APK hash and Drive metadata. Added [the audit](docs/LITERT_RELEVANCE_AUDIT_017.md) and native scripts/results/source provenance under docs/validation/relevance-017; reference weights stay outside the project. Prior99-test/build/lint results belong to the unchanged artifact, and new environment phone tests remain NOT_EXECUTED.
- Independently verified/tested Qwen2.5-1.5B Q4_K_M GGUF using the matching pinned llama.cpp core. Eight final one-thread Linux cases complete, but commune names and the final four-turn answer are incorrect; discarded as a repair. Initial four-thread host-quota timeouts are retained as failed controls, never phone benchmarks. GGUF fixed context/partial settings wiring/flattened prompt order are documented as separate existing debt.
- Reran all99 unit tests with only Test tasks forced out of date; testDebugUnitTest/assembleDebug/lintDebug succeed in40s,0failures/errors/skips. Unchanged debug compilation/lint outputs are reused, with0lint errors/73existing warnings. No new production source or signed artifact. [Current verification](docs/validation/relevance-017/validation.json).

## 0.1.7 — 2026-10-04

- Recorded real physical warm engine/session reuse: 96ms preparation, request TTFT3.338s, 85 output tokens/25.290s generation, decode3.55tokens/s. Cold cost and sustained CPU decode are separate; no new phone speedup is claimed.
- Removed raw benchmark fields from old/new answer cards. Details shows measured human-readable values and explicitly expandable technical fields; legacy decimal-comma records remain readable.
- Show actual output-cap warnings and offer real, visible Continue generation on the latest eligible turn. Preserve partial replies and add trusted cutoff context instead of synthesizing an absent fourth point. Native answer quality still fails that follow-up in the reference test.
- Start actual CPU model/session preparation at chat entry/selection, without a synthetic prompt. Keep typing available, await preparation on Send, cancel native work before replacement, suppress stale readiness and display preparation errors. Successful real retry clears an old preparation failure. Diagnostics collapses the separate smoke action.
- Correct the exact prior128-token CPU default once to256 to give requested lists more room; preserve custom settings and later explicit128 choices. The brief policy is best effort: longer ceilings may increase latency and cannot fix INT8 instruction following.
- All99 JVM tests pass (97app+2compat, no failures/errors/skips); debug and instrumented APK compilation pass; lint0errors/73existing warnings. Real Linux CPU reference produces tokens, streams, reaches a measured16-token cap and unloads, but instruction/missing-point/continuation-language quality FAIL. Three shorter policy variants also failed and were discarded. Android candidate validation NOT_EXECUTED.
- Room remains schema2 and runtimes/native libraries remain unchanged. GGUF, Model Library, RAG/citations, memory, tools and existing imports/downloads are retained. No unrelated features. See [implementation, native results and phone test](docs/CHAT_LATENCY_AND_OUTPUT_017.md); signed artifact/delivery are recorded in [the manifest](docs/validation/apk-017.json).

- Same-certificate arm64 release/signature PASS, versionCode8; [APK uploaded to Drive](https://drive.google.com/file/d/1nxDYFA2d07YSExVlfVIFGFDwqYcC54Ej/view?usp=drivesdk),91,355,065bytes. Exact size/name/MIME/parent/private-sharing metadata readback PASS; native libraries remain byte-identical to0.1.6.

## 0.1.6 — 2026-10-03

- Recorded 0.1.5 physical multi-turn quality FAIL and measured cold load40.4s/UI TTFT50.9s. Basic phone tokens remain proven; screenshot recovery alone does not prove native cancel acknowledgement or warm speed.
- Corrected the restored-history role using actual bounded bundle metadata: Qwen/generic ChatML expects assistant, but the official Kotlin factory serialized model. Added a small pinned Apache-2.0 source compatibility client preserving all other APIs/source and the unchanged official0.17.1 native backend. No model/template patch or metadata-check bypass.
- Reuse healthy native conversation/KV state only for exact completed budgeted history/configuration and safe remaining context. Report sessionReused separately from engineReused; cancelled/changed/unsafe transcripts always discard session state.
- Apply real repetition1.1/ngram8/window256 controls for ordinary enabled chat, preserve intentional1.0 disable/custom settings, and correct the exact old output512/penalty1.0 tuple once to128/1.1. Catch interleaved repeated paragraphs earlier; retain native cancel and five-second worker-reset deadline. Smoke uses private disk cache while forcing a fresh engine.
- Keep actual onDone separate from unavailable literal EOS and expose measured output-cap detection. Retain failed partial answers/detailed diagnostics without oversized transient notices. Room remains schema2; GGUF, library, RAG/citations, memory, tools and existing download/import flows are retained.
- Actual Qwen native Linux CPU reference: three turns, restored assistant history, real streaming, native cancel138ms, retry and unload. Exact-word smoke quality FAIL (FUNCION/echo); Android candidate validation NOT_EXECUTED. Strong presence/sampler experiments were discarded. Cold CPU initialization and INT8 quality remain limitations.
- All84 JVM tests pass (82app+2compat, zero failures/errors/skips). Debug/release/instrumented-APK builds pass; lint0errors/73warnings. [Signed arm64 APK delivered to Drive](https://drive.google.com/file/d/18WkNQngnTdpeNm-xjDxIJjUTFw3a8K-c/view?usp=drivesdk),91,305,913bytes, existing package/certificate, versionCode7; metadata readback verified. See [full audit/phone retest](docs/LITERT_CPU_STABILITY_016.md).

## 0.1.5 — 2026-10-03

- Corrected the 0.1.4 phone two-turn failure: the current USER was duplicated in flattened history, and all prior roles were sent inside one USER turn. LiteRT now receives completed native USER/MODEL history and the current message once, with model-owned template rendering.
- Exclude failed/cancelled/looped responses from inference history while retaining Room messages; allocate against configured context/output and preserve budgeted evidence/memory labels and citation IDs.
- Ordinary structured chat requests final text with real thinking/channel settings. Native capability metadata remains unchanged; no GPU/NPU or template/model patch.
- Detect substantial exact repeated blocks, call native cancel off the callback thread and use a five-second stop/reset deadline. A loop is an interrupted Error, never a fake successful answer. Clean native onDone plus session teardown may retain the engine for retry; native errors/timeouts reset it.
- Added requestTtftMs from UI send to first received output, including load/preparation. Initial CPU cost remains device-dependent; no physical speedup/quality PASS claimed for this candidate.
- All 60 JVM tests pass (prior 45 + 15 regressions); debug/lint/instrumented APK builds pass, same 72 lint warnings/0 errors. Instrumented hola → como estas quality/warm-retry test compiles but awaits a device. Room remains schema 2, SDK remains 0.17.1. See [two-turn audit](docs/LITERT_REPETITION_FIX.md).
- Arm64 release and v3 signature verified with the existing update certificate, versionCode 6; [APK delivered to Drive](https://drive.google.com/file/d/1fAy73PAFPED3ziwslgeJ-CFXk0P5kU5a/view?usp=drivesdk), 89,175,737 bytes, metadata readback confirmed. Prior APKs/sharing are retained.

## 0.1.4 — 2026-10-03

- Recorded actual user-supplied Qwen phone output after 0.1.3 delivery: 10 native tokens, 9 chunks, onDone, load 49.5s and generation 6.6s. Basic inference now PASS; cancel/retry/unload remain separate unexecuted physical criteria.
- Reuse the verified LiteRT CPU Engine for identical file/configuration, while closing and recreating Conversation per full app prompt. File/config changes, smoke, errors and unload invalidate reuse. Retain CPU, SDK 0.17.1, native cancellation, independent deadlines and existing GGUF behavior.
- Report actual engineReused and prepareMs; warm turns do not repeat the previous cold load timing. No measured phone speedup is claimed for 0.1.4 yet.
- Added 7 production lifecycle-owner tests and an IPC metric regression; all 45 JVM tests pass. Debug/lint/instrumented-test APK builds pass with the same 72 lint warnings. Added an instrumented cold/warm/unload/cold test, not run without a device.
- Arm64 release build and same-certificate v3 signature verified; [APK delivered to Drive](https://drive.google.com/file/d/1xawAe0Or4rkgkXbpdHzL1lXwpELR38_e/view?usp=drivesdk) with metadata readback. VersionCode 5 preserves upgrade compatibility with the previous release.
- Room remains schema 2, models and histories preserved. See [phone validation/retest](docs/LITERT_PHONE_VALIDATION.md).

## 0.1.3 — 2026-10-03

- Moved LiteRT load/unload, Messenger replies and binding callbacks off Main. Replaced consumer polling with an independent elapsed-realtime watchdog that requests worker termination before UI/database cleanup.
- Added bounded partial wake leases for explicitly requested operations, released on native completion/error, cancellation and deadline. The normal Android WAKE_LOCK permission supports these leases.
- Added file-hash progress, separate native-library/capability/engine checkpoints, periodic loader stack/RAM snapshots and a checkpoint in the saved error message.
- Persisted tagged diagnostics in the existing generationMetrics field and made Technical details available on failed messages after reopening the chat. Existing benchmarks and Room schema 2 are preserved.
- Added regression tests for paused Main, unresponsive load/cancel, same-client retry/unload, wake release, monitor exceptions, diagnostic persistence and actual integrity progress. These do not validate native model inference.
- The 0.1.2 phone result is FAIL (MODEL_LOAD_TIMEOUT after 1,345,071 ms); the exact native loading substep remains unknown. Subsequent phone screenshots after 0.1.3 delivery show a real response with 10 native tokens and completion; see the 0.1.4 evidence record for boundaries.

## 0.1.2 — 2026-10-03

- Fixed the 0.1.1 service dispatcher reading Android Message.what on a deferred worker after Handler had recycled the message. Command, reply, ID and scalar payload are now copied at the Handler boundary.
- Added deterministic Robolectric service regression tests for recycled LOAD/UNLOAD requests, retry after file-validation failure, snapshot reuse and invalid commands; these tests do not validate native inference.
- Added IPC receive/dispatch/error traces and app version in structured logs. Unsupported commands are classified as IPC_UNKNOWN_COMMAND before any native work.
- Retained LiteRT-LM 0.17.1, CPU settings, timeouts/cancellation and Room schema 2. Physical inference remains FAIL for prior APKs; candidate 0.1.2 is NOT_EXECUTED on device.

## 0.1.1 — 2026-10-03

- Added phase diagnostics, watchdogs and a recoverable separate native process after the Qwen LiteRT-LM device hang.
- Replaced SDK Flow with checked callback/IPC streaming, enabled native benchmarks and separated chunks from actual token counts.
- Corrected cancelled/interrupted message persistence and blank historical Generating messages.
- Added CPU smoke action and instrumented first-token/cancel/retry/unload test. Subsequent physical validation failed before model loading with Unknown LiteRT operation; addressed in 0.1.2.
- Retained DB schema version 2, model records and GGUF/RAG/memory/tools paths.

## 0.1.0 — 2026-10-03

- Bootstrapped the native Android/Compose application and Gradle wrapper.
- Added the pinned llama.cpp Android runtime module and GGUF metadata inspection.
- Added Room persistence for projects, chats, messages, models, documents, segments, embeddings, memory, citations and tool audit records.
- Added local document ingestion, structural chunking, FTS4 retrieval, hashed local embeddings and reciprocal-rank fusion.
- Added deterministic citation validation and adversarial security/unit tests.
- Added project/chat/model/settings UI with local privacy status and explicit missing-capability messaging.
- Reworked Models into a runtime-neutral Model Manager with GGUF, official LiteRT-LM, Hugging Face and multimodal-bundle flows.
- Added Room 1→2 migration, private staged model storage, SHA-256 duplicate checks, filtered/resumable Hugging Face downloads and Keystore-backed optional authentication.
- Added model picker/runtime switching in Chat and real LiteRT-LM image-content input when model metadata declares vision.

## 0.1.15 — HOTFIX 1.5.1 (vision / image attachments)

- Consume image draft after durable USER acceptance; persistent bounded thumbnail and explicit retry reference.
- Preserve effective visual history over internal IPC; validate private image and initialized CPU vision backend, without text-only fallback.
- Record modality rebuild and metadata-only vision input diagnostics; preserve text sampling/backend/capacity policy.
- Additive Room 4→5 image reference, safe existing-outbox cleanup, EXIF orientation correction.
- Physical Gemma perception remains PHYSICAL_DEVICE_TEST_REQUIRED; original first-image denial is NO DETERMINADO from code alone.
- Full technical report: docs/VISION_HOTFIX151_015.md.
