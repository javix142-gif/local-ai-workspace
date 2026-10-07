> Historical status document: this file describes the 0.2.1-era checkpoint and is retained for release history. The current baseline is 0.4.2; see the root [STATUS.md](../STATUS.md) and [architecture overview](ARCHITECTURE_OVERVIEW.md).

# Part2 delivery — 0.2.0

[Technical report and validation](PART2_IMPLEMENTATION.md). Source implementation is connected; Moto physical acceptance of Gemma tools/Thinking/audio, WebView/WASM and performance remains required. Room migration is additive (5→8); no Part3 integration.

# Project status — 0.1.12 / 2026-10-05

**FILES / NAVIGATION**: chat generation and file reading survive returning to Workspace; actual active tasks can be reopened/stopped. Application ChatSessions owns controllers, complete native requests/preparation share a gate, and cancellation/deletion settle only owned work. Room checkpoints partial output. Files exposes selection, extraction failure, text preview and real Summarize. Readable imports are next-turn context; paperclip photos use actual vision preparation. Up to3×1000-character excerpts subject to context; omitted sources remain selected. No OCR, no full-file analysis claim and no foreground process-death guarantee.

**FIRST MESSAGE**: plain greetings skip unrelated RAG/memory/evidence policy, retrieval runs off Main and rejects unrelated zero-score candidates. A compatible already initialized vision engine serves subsequent text without reinitialization. Existing startup preparation/single-engine/verification receipts remain. Cold loading and CPU decode can still take time; no measured Moto G86 speedup or factual-quality repair is asserted.

**VALIDATION PASS**:167app tests actually executed with0failures/errors/skips, including21new (12real controller/Room/SAF pipeline,7retrieval,2engine ownership boundary).2compatibility tests UP-TO-DATE. Full debug tests/build/lint PASS2m15s after correcting an initial lint indentation error. Lint0errors/73existing warnings. Release/signature/native comparison PASS1m16s; all8native libraries match0.1.11. Room3 unchanged, no new migration/data deletion. Android device/UI/native/file-response/cancel/performance validation **NOT_EXECUTED**, no device attached. [Implementation and phone checks](CHAT_FILES_AND_BACKGROUND_012.md).

**APK / DRIVE PASS**: [Download signed0.1.12 ARM64 APK](https://drive.google.com/file/d/10_AIAPomtCQnemliE0qksZLEGTM346WO/view?usp=drivesdk),91,666,361bytes,versionCode13. Local path `dist/apk/local-ai-workspace-0.1.12-chat-files-arm64.apk`, SHA-256 `3175b1c8d126befa84f979b5cb9b3382c9bc043934747f3f665bd5b0ca51e824`. Same update certificate; metadata readback verifies filename/size/MIME/original parent/private sharing. Provider checksum unavailable. Install as an update without uninstalling or clearing data. Prior APKs retained. [Manifest](validation/apk-012.json).

# Historical project status — 0.1.11 / 2026-10-05

**APP-OWNED MODEL PREPARATION**: real LiteRT CPU preparation starts at app launch for the last selected usable model; Workspace shows Active model selection, actual progress/integrity/session/error/cancel/retry. New chats inherit this selection; existing project model preferences remain honored. Closing a chat or cancelling its waiter cannot cancel the application job. Generation uses the existing isolated chat lease, serialized native access and fresh conversation/KV handover.

**RETENTION / INTEGRITY**: app idle weights no longer expire after ten minutes; memory pressure still releases idle engines and invalidates readiness. Only one engine is retained; Qwen↔Gemma/context/vision/backend changes can require initialization. A bounded worker-lifetime SHA receipt cache rechecks header/size/identity/hash/stat attributes, invalidates changes/failures and skips repeated full scans only for previously verified unchanged private files. No persistent verification bypass, no models loaded into RAM for hashing and no promise to retain both models in8GB. Android worker/app reclamation can require cold loading.

**VALIDATION PASS**:146app tests actually executed,0failures/errors/skips;2unchanged compatibility tests UP-TO-DATE. Full debug tests/build/lint PASS in48s. Lint0errors/73pre-existing warnings. Release PASS in1m15s; same-certificate signature/package/version/ABI/feature DEX checks PASS; all8native hashes match0.1.10. Room remains schema3; no migration/data deletion. LiteRT0.17.1CPU/templates/native libraries and existing GGUF/RAG/citations/memory/tools/library/import/download/deletion flows retained. CPU decoding and prior semantic-relevance failure unchanged. Android physical UI/startup/native/timing validation **NOT_EXECUTED**, no attached device.

**APK / DRIVE PASS**: [Download signed0.1.11 ARM64 APK](https://drive.google.com/file/d/1xOJPIKlK9cA4csUT-yphfqR9a9ijf1u_/view?usp=drivesdk),91,584,441bytes,versionCode12. Local path `dist/apk/local-ai-workspace-0.1.11-app-preload-arm64.apk`, SHA-256 `95ff736a2ba37bf4b2de0c35d9be3eecfdf4d1f23cff5518ccbacf64ff0494fb`. Drive readback confirms exact filename/size/MIME/original parent/private sharing; provider hash not exposed. Install as an update without uninstalling or clearing data. Earlier signed artifacts remain intact.

[Behavior and phone checks](APP_MODEL_PREPARATION_011.md). [Artifact](validation/apk-011.json).

## Historical project status — 0.1.10 / 2026-10-05

**WORKSPACE REORGANIZED**: direct Start chat plus optional Start project; separate chat/project lists; project screen with multiple independently addressed conversations, shared local files and memory. Confirmed standalone-chat deletion removes its isolated owner/card/resources; Delete project removes its card and every owned chat/document/index/embedding/project memory; Delete project chat preserves siblings/shared resources. Models/settings/benchmarks, global memory and unrelated work remain. Startup no longer recreates a deleted Personal workspace.

**ROOM MIGRATION3**: real2→3 migration adds workspaceKind (existing rows defaultPROJECT) and durable pending private-document deletion table, with no destructive migration. Preserves existing IDs/history/models/preferences/files. SQL deletion/outbox is one transaction; canonical private-filesystem cleanup follows commit and can retry on launch or from Workspace. Source originals/models/escaping symlinks are excluded. Import-vs-delete guards prevent late document/index resurrection.

**HOST VALIDATION PASS**:129app tests executed,0failures/errors/skips (114preceding+15new),2compatibility tests up to date. Final delivery testDebugUnitTest/assembleDebug/lintDebug succeed in1m42s; lint0errors/73existing warnings. Includes actual Room2→3 opening/schema validation, deletion/isolation/rollback/FTS/embeddings/file-outbox/symlink and streaming import race tests. Device UI/native/performance validation NOT_EXECUTED; no adb device. Warm LiteRT pool unchanged, no semantic-quality/CPU-decoding improvement claimed.

[Behavior and phone checks](WORKSPACE_010.md). [Artifact](validation/apk-010.json).

**APK / DRIVE PASS**: [Download signed 0.1.10 ARM64 APK](https://drive.google.com/file/d/1DnJoNPZNrboP5QMgNXCkZxN1BcYP1wnI/view?usp=drivesdk), 91,535,289 bytes, versionCode11, same update certificate. Local file `dist/apk/local-ai-workspace-0.1.10-workspace-arm64.apk`, SHA-256 `63cee591e87d99502244026b0f2bcfaaeaeba9bdb35feb847cc20fe0eadf640a`. Release assembly PASS in44s; signature/package/version/DEX markers and all8 native SHA comparisons with0.1.9 PASS. Drive replacement and metadata readback confirm exact filename/size/MIME/original parent/private sharing; provider checksum unavailable. Install as an update without uninstalling or clearing app data. Device validation remains NOT_EXECUTED.

## Historical project status — 0.1.9 / 2026-10-05

**WARM LITERT ACROSS CHATS IMPLEMENTED**: application-owned single-engine pool, per-chat revocable runtime leases, serialized native operations and real RESET_CONVERSATION IPC. Closing/deleting a chat discards conversation/KV; an unchanged healthy LiteRT engine can remain warm. The next owner always gets an independent session. Late old cancellation/cleanup cannot touch the new owner. Idle retention10minutes, idle memory-pressure eviction, normal backend/config/file invalidation; existing GGUF unload fallback retained. Room remains schema2; SDK/JNI unchanged0.17.1CPU.

**HOST VALIDATION PASS**:114 app tests executed,0failures/errors/skips (104 preceding +9pool lifecycle +1real service dispatch),2unchanged compatibility tests up to date. Final testDebugUnitTest/assembleDebug/lintDebug succeed in7m10s. Lint PASS_WITH_WARNINGS:0errors/73existing warnings. Android physical new lifecycle and timing validation **NOT_EXECUTED**; no attached adb device. No faster CPU decode or semantic quality fix is claimed.

[Implementation and phone checks](WARM_MODEL_019.md). [Release/artifact manifest](validation/apk-019.json).

**APK / DRIVE PASS**: [Download signed 0.1.9 ARM64 APK](https://drive.google.com/file/d/1uog6Eiy_sH97ThrnN6k681EmvtKfy9UE/view?usp=drivesdk), 91,420,601 bytes, versionCode10, same update certificate. Local file `dist/apk/local-ai-workspace-0.1.9-warm-model-arm64.apk`, SHA-256 `b21c059ba92c65d4de426bd1b8c78f462acf484bd186116e17f822aaea21095d`. Release assembly PASS in3m4s; signature/package/version/DEX markers and all8 native SHA comparisons with0.1.8 PASS. Drive readback confirms exact filename/size/MIME/parent/private sharing; provider checksum unavailable. The0.1.8 APK remains intact locally and in Drive. Install as an update without clearing app data.

## Historical Local AI Workspace status — 0.1.8 / 2026-10-05

Chat deletion is implemented in Workspace project-card menus and the chat toolbar, with confirmation. Only the selected project's conversations/messages/citations/tool history are removed, atomically. Documents, memories, models/settings and model benchmarks remain. Open-chat deletion cancels/joins generation and preparation, unloads native state and detaches the old runtime owner before navigating back; replacement chats get fresh IDs and late writes cannot resurrect deleted content. Room stays schema2.

104app tests actually executed, including7new Room regression tests, all PASS;2unchanged compatibility tests remained up to date (initial cache restore). Final debug/test/lint build PASS; ARM64 release and same-certificate signature/package/version checks PASS. Lint0errors/73existing warnings. All native hashes match the recorded0.1.7 values. Physical UI/cancel/retry verification NOT_EXECUTED: no attached Android device. [Full chat-deletion evidence](CHAT_DELETION_018.md).

[Signed0.1.8 APK in Drive](https://drive.google.com/file/d/1odrfBn_RHILGYj7Gvm8H6sPqVcfk10b3/view?usp=drivesdk),91,387,833bytes,versionCode9. Metadata readback PASS; provider checksum not exposed. Stable local distribution path `dist/apk/local-ai-workspace-0.1.8-chat-delete-arm64.apk`. Gradle packaging cleaned previous local output-directory APKs; their original Drive files remain, but local re-download was blocked HTTP403. Source, original reference model, SDK and validation reports remain. [Artifact manifest](validation/apk-018.json).

## Previous runtime assessment — 0.1.7

Offline by default. Runtime execution, semantic quality, compilation and physical-device validation are distinct.

Latest captures25091–25095 show multiple completed turns, Model ready and metrics behind Details, consistent with the0.1.7 UI. Installed version/hash/context/timings are not captured. **PHYSICAL BASIC OUTPUT/CONSECUTIVE COMPLETION/METRICS PRESENTATION PASS; ANSWER RELEVANCE FAIL**: the communes question receives unrelated categories. Real local controls reproduce failure without app policy/history/pre-render/extra repetition controls. OlderSDK0.16.1, FLOAT32 activation/0.17.1, sampling/policy changes and a smaller LiteRT candidate fail semantic checks and were discarded. No speculative runtime change or new APK is shipped. Exact model/conversion/kernel cause remains unproven. [Current relevance audit](LITERT_RELEVANCE_AUDIT_017.md).

Follow-up command testDebugUnitTest/assembleDebug/lintDebug succeeds in40s. All99 tests actually rerun (97app+2compat,0failures/errors/skips), while unchanged compilation/lint outputs remain reusable; lint0errors/73existing warnings. [Verification record](validation/relevance-017/validation.json). An independent official Qwen2.5-1.5B GGUF also fails names/final four-turn relevance and is discarded; no alternative model is presented as a successful repair. Existing GGUF context/settings/flattened-prompt limitations are documented in the relevance audit.

Latest supplied0.1.6 physical capture: **BASIC DEVICE OUTPUT / WARM SESSION REUSE = PASS**. engineReused=true/sessionReused=true, prepare96ms, request TTFT3.338s, output85, total generation25.290s, decode3.55tokens/s. Warm load is not the principal delay for this response. The preceding answer ends at4, but its terminal metric line is clipped; the exact finish reason is unproven. Earlier measured cold load40.369s remains historical. [Full evidence](CHAT_LATENCY_AND_OUTPUT_017.md).

**Automated validation: 99 JVM tests PASS** (97app+2compat, no failures/errors/skips, all preceding84 retained). Executed `./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest`: debug/instrumented APK compilation PASS; lint PASS_WITH_WARNINGS,0errors/73existing warnings. No attached adb device: **ANDROID0.1.7 DEVICE_VALIDATION = NOT_EXECUTED**.

0.1.7 moves raw metrics into Details, starts real CPU preparation before Send, handles cancellation/replacement/progress failure and offers a real Continue action with measured cap warnings and trusted incomplete-output context. The exact old CPU128 default is corrected once to256; custom settings and later explicit128 choices remain. Increased output room may lengthen detailed answers. Room schema2, models/chats and prior GGUF/Model Library/RAG/citations/memory/tools/import/download capabilities are retained. LiteRT remains the official0.17.1 CPU native backend and pinned additive assistant-role client; no unrelated features.

**Actual native Linux CPU reference = PASS_WITH_WARNINGS for API execution, QUALITY = FAIL**. Candidate policy uses11/14/115 output tokens for hola/como/delay; delay was longer than the prior107-token reply and invents network causes. A requested16-token limit was reached and actual Continue returned93 tokens, but missing-point content and continuation language fail. Three shorter policies were tested and discarded after unreliable answers. These are real C-API tokens, not Android APK/JNI tests; literal EOS is unavailable. [Results](validation/cpu-017.json), [policy comparison](validation/policy-probe-017.json). No0.1.7 physical cancellation, quality or speed PASS is claimed.

**Release/signature/Drive delivery PASS**: `./gradlew assembleRelease -Parm64Only=true` succeeded. [Verified0.1.7 arm64 APK in Drive](https://drive.google.com/file/d/1nxDYFA2d07YSExVlfVIFGFDwqYcC54Ej/view?usp=drivesdk), 91,355,065bytes, versionCode8, package `com.localai.workspace`, minAPI29/target35, same update certificate. Upload metadata readback confirms exact name/size/MIME/original parent and private sharing. SHA-256 `c4d539fb6e15293a320e52673d622281c6c6ada2ebcd63bfc34ac2886e28703a`. Native libraries are byte-identical to0.1.6; the official LiteRT source extraction matches Google's AAR and packaged JNI matches its standard NDK-stripped library. Previous Drive APKs remain. [Artifact manifest](validation/apk-017.json).

Historical failures remain:0.1.0 no-token hang;0.1.1 pooled Message dispatch error (fixed0.1.2);0.1.2 late MODEL_LOAD_TIMEOUT;0.1.4/0.1.5 repeated second replies. Physical0.1.6 warm reuse is established; full LiteRT semantic/device acceptance is still incomplete.

## Gate summary

| Gate | Status | Evidence / limitation |
| --- | --- | --- |
| 0 — baseline + runtime | PASS_WITH_WARNINGS | Reproducible Gradle/native build and JNI adapter; real model load/stream/cancel/metrics still needs hardware + fixtures |
| 1 — model manager/chat | PASS_WITH_WARNINGS | Runtime-neutral descriptors, Add model, Room migration, multi-runtime picker; user supplied real Qwen text output, full device regression pending |
| 1a — LiteRT-LM | FAIL / environment device NOT_EXECUTED | User captures show basic output, consecutive completion and Details presentation; relevance FAIL reproduces in isolated native CPU controls; new timings/cancel/retry/unload pending |
| 1b — Hugging Face | PASS_WITH_WARNINGS | HTTPS repository parsing, filtered tree, optional Keystore token, streaming/resume/hash/size validation and private finalization are implemented; long-running process-death/notification test pending |
| 1c — multimodal bundles | PASS_WITH_WARNINGS | Self-contained LiteRT-LM and GGUF+mmproj bundle records are supported; current llama.cpp adapter intentionally reports GGUF vision unsupported |
| 2 — projects/files | PASS_WITH_WARNINGS | Room CRUD, SAF import, copy/hash, PDF/TXT/Markdown/HTML/CSV/DOCX parser path exist; process-death/instrumented verification pending |
| 3 — RAG/citations | PASS_WITH_WARNINGS | FTS4 + local vector baseline + RRF + authoritative citation validator + tests; no real-model end-to-end citation run |
| 4 — memory | PASS_WITH_WARNINGS | Project memory screen supports user-approved add/edit/archive and scoped retrieval; process-death and poisoning instrumented tests remain |
| 5 — tools | PASS_WITH_WARNINGS | Typed bounded registry, safe calculator/file-read tools, schema/JSON checks, timeout, loop/repeat limits and confirmation boundary; autonomous model tool loop is disabled because this runtime adapter reports no reliable tool calling |
| 6–11 | BLOCKED_EXTERNAL / not started | Web/connectors/OCR/image generation need additional implementation, device checks, provider permissions or model fixtures |

Known warnings:

- The llama.cpp binding still reloads between turns for its load-scoped system prompt. LiteRT keeps healthy native KV state only for exact bounded history/configuration equality; cold initialization and measured3.55tok/s physical decode remain separate concerns.
- The small pinned Apache-2.0 Kotlin compatibility client must be maintained against future SDK changes. Only the additive assistant role differs; all other upstream source/native APIs remain intact. The old metadata-check bypass is removed.
- LiteRT-LM GPU/NPU are not advertised yet. The runtime and model/device combination must be probed and validated before those accelerators can be selected.
- Hugging Face downloads run in a lifecycle-scoped coroutine in this iteration. Partial files survive cancellation/network loss and resume, but a future release should move long downloads to a foreground WorkManager/notification worker for process-death survival.
- The default vector baseline is hashed lexical embedding, not a neural embedding model.
- The Android sample binding has a single global native engine; the app keeps one runtime and does not attempt concurrent model loads.

Next action: obtain model SHA-256 from FILES and cold/warm reply Details, then compare the original Qwen3.5 floating-point reference/template before attributing a conversion/kernel defect. Neither tested alternative is recommended. Keep basic token completion separate from correct answers. No unrelated features, SDK/precision changes or GPU availability without evidence. Full steps and logcat are in [the relevance audit](LITERT_RELEVANCE_AUDIT_017.md).
