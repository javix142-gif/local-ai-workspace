# Files, navigation and first-message context — 0.1.12

## Findings grounded in the existing source

1. Navigation popped the chat's ViewModelStore. `ChatViewModel.onCleared()` cancelled its generation and closed its runtime leases, despite the engine pool being application-owned. A retained engine was not a retained generation task.
2. Imported files were indexed but displayed as inert chips. Selection, extraction failure, extracted text and an explicit summary action were unavailable. An empty/scanned file could become READY without a readable segment. A photo selected via the paperclip was sent to a text parser rather than the working image preparation path.
3. RRF could promote unrelated hash-vector candidates, including zero similarity. Stopwords and a mandatory system policy added unnecessary context to short greetings. Retrieval could embed up to 1,000 segments on the caller dispatcher. This is a concrete avoidable source of prompt work, not a measurement of the phone's complete delay.
4. A vision-capable initialized LiteRT engine was discarded when requesting a following text-only turn, because its vision flag needed exact equality. Text-only engines still cannot serve vision.

## Ownership and execution

`AppGraph → ChatSessions → per-chat ViewModelStore → ChatViewModel → ChatRuntimePool.Lease → selected InferenceRuntime`.

Navigation attaches/detaches a screen; it does not clear a busy controller. Generation and imports continue while the user returns to Workspace. Workspace observes actual busy tasks and supports Open chat and explicit Stop generation. Reopening obtains the same controller, preserving its current stream. Partial assistant text is checkpointed to Room at most once per second; completion, failure and cancellation persist their actual statuses.

An application `inferenceGate` protects the complete load-plus-generation transaction and the application preloader. A second chat waits rather than interrupting the first. Pending preparation is awaited **before** acquiring this gate, avoiding a cyclic wait. Cancelling a queued request cannot cancel another lease or the application's preparation. Changing the app's prepared model waits for the current generation; it does not steal its conversation owner. Deletion cancels and joins owned generation/import tasks before transactional record/file deletion.

Idle detached controllers release conversation/KV leases and expire after ten minutes. This controller retention interval does **not** unload healthy model weights on a timer. The existing single-engine retention, private-file verification receipts and idle memory-pressure handling remain. Each new native chat owner gets clean conversation state. Models, settings, memory, citations and the actual llama.cpp/LiteRT adapters remain; schema3 needs no migration in this release.

This is navigation continuity inside the running application, not an Android foreground inference service. It does not guarantee continued computation after app/worker process death, force-stop or device memory reclamation. Interrupted persisted requests retain checkpoints and use the existing interrupted-generation recovery on reopening.

## Actual file flow

Paperclip SAF selection → private streaming copy/hash → installed local parser → nonempty text chunks → Room/FTS READY → selected file IDs → bounded evidence → context budget → `GenerationRequest.conversation` / text fallback → actual chosen runtime.

- New readable imports are selected for the next user message. Sending waits for already-running imports. Existing files can be selected through **Files**.
- **Files** exposes extraction/indexing status, specific failure, selected checkboxes, **Preview text** and **Summarize**. Summarize submits a real model request; importing alone does not silently start inference.
- Up to **3 passages of 1,000 characters each** are supplied per turn, subject to the active context budget. Selection prioritizes a passage per selected source before additional passages. Generic file questions use opening excerpts; genuine matching terms can use relevant indexed passages.
- Files omitted by the passage/context limit remain selected, with a notice. Only sources actually supplied can validate citations. This does not claim entire large files were read by the model or implement a whole-document map/reduce summary.
- Installed parsers remain text/Markdown/CSV/log, readable PDF, DOCX and HTML. HTML scripts/styles/markup are stripped before context. Empty or unreadable files and unsupported formats become FAILED with the actual reason. Scanned PDF has **no OCR**; it cannot be treated as readable text or rendered automatically into model images.
- Paperclip photos use the same bounds-first, validated private image preparation as the image button; capability checks remain mandatory. Vision initializes only when an actual image request needs it. No extra accelerator is enabled.
- Source ownership is checked; only READY files enter retrieval. Filename attributes in evidence are escaped. Text remains untrusted evidence rather than executable instructions. No files, prompts or chat contents are sent online.

## First-message latency and limits

A greeting without selected files skips document retrieval and memory retrieval and carries no default evidence policy. Relevant non-greeting retrieval runs on Default, filters meaningful lexical overlap and positive vector scores, and never promotes an unrelated zero-score candidate. The existing deterministic hash embedding baseline is retained; it is not a new neural semantic search model.

An initialized vision engine can serve a subsequent text request with all other identity/hash/config checks preserved, and can return to vision without another initialization. A text engine still requires initialization for its first vision request. Application startup preparation remains text-only CPU; switching actual models/configuration or process death can still require cold loading.

The patch reduces avoidable application work and prompt context. It does not establish faster CPU decode, GPU support, first-token timing on Moto G86 Power, or improved model factual quality. Gemma/Qwen may still spend seconds processing a prompt and decoding, and the prior semantic relevance failure is not claimed fixed.

## Validation and phone checks

Executed host checks and APK hashes are recorded in [the artifact manifest](validation/apk-012.json), with Gradle logs and actual test XML under `validation/chat-files-012/`. New Room/SAF/controller tests use a controlled native boundary; they cannot demonstrate physical model inference. Existing model import, migration, runtime mapping, pool, citation, history, memory and tool tests remain in the full suite.

`DEVICE_VALIDATION = NOT_EXECUTED`: no physical Android device is connected in this environment.

Install 0.1.12 as an update without uninstalling/clearing data, then:

1. In Workspace, await the existing Active model readiness once. Open an empty Gemma chat, send **Hola**, compare Details → request TTFT/prompt count against the previous build. Repeat without attached files; record cold load separately from prefill and decode. No target speed has been asserted.
2. Attach a short `.md` containing a unique marker, e.g. `PRUEBA_ARCHIVO: el código es AZUL47`. Verify Files → READY and Preview text includes it. Ask **¿Qué código dice el archivo?** or use Summarize. The marker must appear in supplied context; verify the actual model response separately. Re-select an existing file for another question.
3. Attach an empty TXT, unsupported `.doc` or scanned PDF. Expect a visible FAILED reason, never a false successful extraction. For >3 selected files, expect a notice and omitted files still selected.
4. Attach a photo using the paperclip with a vision model. It must show Image prepared for vision, with no bogus text-document READY row. Compare with the image button. Following text turns should retain a compatible initialized vision engine; inspect Details/logcat rather than assuming reuse.
5. Start a longer real response, return to Workspace while tokens arrive, observe its active card, reopen it and verify continued output/completion. Repeat with file reading and navigate away. Try a second chat while the first is busy: it must wait. Stop generation from Workspace; verify native cancellation and persisted status, then retry.
6. Delete a busy chat/project from Workspace; it must settle and disappear without late rows, touching neither other chats nor installed models. GGUF first-send/switch behavior must remain functional.

For runtime diagnostics: `adb logcat -v threadtime -s LocalAI/LiteRT LocalAI/Runtime`. Collect the version, actual active model, Details and whether the engine/verification receipts were reused. Do not include private prompt/document text in logs.

Pending physical checks: model load, session, actual extracted-file response, image response accuracy, streaming across navigation, native cancel/retry/unload, CPU TTFT/decode comparisons, Android process pressure and visual UI verification. Neither host tests nor earlier screenshots mark the new build as physical PASS.

## Final artifact and executed results

[Signed 0.1.12 ARM64 APK in Drive](https://drive.google.com/file/d/10_AIAPomtCQnemliE0qksZLEGTM346WO/view?usp=drivesdk). Local path `dist/apk/local-ai-workspace-0.1.12-chat-files-arm64.apk`,91,666,361bytes; SHA-256 `3175b1c8d126befa84f979b5cb9b3382c9bc043934747f3f665bd5b0ca51e824`. Package/versionCode13/min29/target35 and update certificate verified. All8native libraries match0.1.11. Drive metadata readback verifies exact name/size/MIME/parent/private sharing; it does not expose a server checksum.

| Check | Result |
| --- | --- |
| testDebugUnitTest | PASS:167app tests executed;2compatibility tests UP-TO-DATE;0failures/errors/skips |
| New controller/Room/SAF, retrieval and reuse regressions | PASS:21new tests, controlled native boundary |
| assembleDebug | PASS, full final batch2m15s |
| lintDebug | PASS_WITH_WARNINGS:0errors,73existing warnings |
| assembleRelease, ARM64 | PASS,1m16s |
| Signature, package/version/ABI/DEX/native comparison | PASS |
| Drive upload and metadata readback | PASS |
| New physical inference, navigation UI, cancel/retry, file response and timing | NOT_EXECUTED |

The initial batch passed163app tests and debug assembly but failed lint's SuspiciousIndentation check. Indentation was corrected, four additional file regressions were added, and the complete final batch passed. Original logs/results are retained rather than reported as successful.

Created source/test/document files: `ui/ChatSessions.kt`, `rag/RetrievalQuery.kt`, `ui/ChatSessionPipelineTest.kt`, `rag/DocumentRetrievalTest.kt`, `inference/VisionEngineTextReuseTest.kt` and this document. Production and test package roots are `app/src/main/java/com/localai/workspace/` and `app/src/test/java/com/localai/workspace/` respectively.

Modified: `app/build.gradle.kts`, `AppGraph.kt`, `MainActivity.kt`, `ui/ViewModels.kt`, `data/ApplicationModelPreparation.kt`, `data/DocumentIngestion.kt`, `data/Daos.kt`, `rag/LocalRetrievalService.kt`, `inference/LiteRtEngineResources.kt`, `README.md`, both `STATUS.md` files and `CHANGELOG.md`. Exact source hashes and patch are in the validation directory; native SDK, Room schema, HF downloads and runtime selection remain unchanged.
