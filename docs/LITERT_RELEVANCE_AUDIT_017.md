# LiteRT answer relevance audit — 2026-10-04

## Latest physical evidence

User captures 25091–25095 show Qwen3.5-2B_int8.litertlm answering several consecutive turns, returning to Model ready, and putting metrics behind Details. This UI is consistent with the delivered 0.1.7; the installed version, file hash, context and terminal timings are not visible in these captures. The reported handset is Moto G86 Power with 8GB total RAM. Available RAM, chipset, Android version and temperature are unmeasured.

The answer to “que comunas de santiago de chile conoces?” lists geography/culture/economy/science instead of communes. The medical-laws answer includes vague, unreliable claims. These are semantic failures despite real text and completion. No missing question is inferred from those answers. Automatic native tool calling remains unavailable; the generic answer about external tools does not establish a dispatch failure.

Physical basic output, consecutive completed turns and metrics presentation are evidenced. Physical timing improvement, literal EOS, Stop acknowledgement, retry after native cancellation and unload are not established by these images. They remain NOT_EXECUTED by this environment. No attached adb device is available.

## Pipeline audit

The existing path is ChatViewModel → ChatHistoryBuilder/ContextBudgetManager → ConversationPromptBuilder → InferenceRuntime → LiteRtLmInferenceRuntime → IPC service → structured USER/ASSISTANT turns → the bundled native chat template → callbacks/Flow → UI. The current question is preserved. The adapter sends the structured current user message once; bounded completed history is separate. The additive assistant-role compatibility fix remains required for the Qwen ChatML template.

Native renderMessageIntoString diagnostic calls were tested before sending. Each rendered current question occurred once, and native token counts were unchanged before/after rendering (0→0, 85→85, 156→156, 434→434). This does not prove all possible conversations safe, but these runs do not support duplicate wrapping or pre-render consuming KV as the cause.

The decisive reproduction is fresh native LiteRT inference without app policy, history, pre-render or additional repetition controls: the original communes question still yields repetitive generic definitions without names. Therefore the app history/presentation is not required to reproduce this failure. The failing boundary is generated content from the published model on the tested LiteRT CPU route. The exact internal cause cannot yet be separated between model knowledge/instruction quality, conversion/quantization and CPU kernels. An unconverted floating-point reference and matching phone model hash would be needed to attribute it more narrowly.

## Reproducible local controls

All current reference executions use Linux x86_64 CPU, four threads, context 2048, seed 0, no thinking, no images/tools/RAG/memories/network inference. They execute the real public native API. They do not execute Android IPC, UI, app fallback repetition cancellation or an Android accelerator. Phone context 2048 is a suggested comparison, not a measured configuration. Host timings below are not Moto timings.

The published Qwen3.5-2B_int8.litertlm was downloaded streaming and verified against its authoritative LFS size and SHA-256:

- Repository: litert-community/Qwen3.5-2B.
- Revision: 4327b7425533c26da2aec1d7b915e9742eead2cb.
- Bytes: 2,116,592,816.
- SHA-256: 8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1.

The phone model hash is unknown, so byte equality with that file is not asserted. Model weights are never patched, renamed or embedded in Room. Reference weights stay outside the Android project.

| Reference | Actual observation | Decision |
| --- | --- | --- |
| SDK 0.17.1, published Qwen3.5 INT8, no app policy/history | Generic/repetitive non-answer to communes; capped at 256 tokens | Semantic FAIL |
| SDK 0.17.1, current app policy, isolated question | Invented “Conozco y Colón” communes | Semantic FAIL |
| SDK 0.17.1, four turns and restored assistant history | Unrelated categories/legal content and invented names | Semantic FAIL |
| Different sampling and a generic direct-answer policy | Incorrect names; one policy/control run answers 7×6 as 50 | Discarded; no production policy/sampler change |
| SDK 0.16.1, no app policy/history/pre-render/extra controls | 7×6=42, but commune answers remain false/repetitive | Discarded; no downgrade |
| SDK 0.17.1, actual C API FLOAT32 activation request, otherwise minimal | 7×6=42, but commune answers remain false/repetitive | Discarded; no Android precision change |
| Qwen3-1.7B Q4 LiteRT CPU, SDK 0.17.1 | Faster host load (7,970ms) but false communes and arithmetic 32; real native cancel terminal 170ms and retry returned | Discarded; not recommended as a semantic repair |

Original 0.17.1 relevance run loaded in 29,279ms, reached approximately 3.32GiB host peak RSS and unloaded. The 0.16.1 valid comparison loaded in 40,245ms; requested FLOAT32/0.17.1 loaded in 41,805ms. These are individual uncontrolled Linux measurements, not speed rankings or phone benchmarks. FLOAT32 activation does not turn INT8 weights into an unquantized reference.

One initial 0.16.1 attempt used a missing host cache directory, emitted cache-save errors and was OOM-killed. That invalid attempt is excluded; the recorded comparison was rerun with an existing private cache directory. The Android code already creates its private cache directory. No Android cache defect is inferred from that invalid host setup.

The completed host reports and exact scripts are retained under validation/relevance-017. Prompts are public synthetic copies of the test questions; these reports contain no credentials, private documents or model weights. Native onDone and actual token counts establish execution, not semantic correctness or literal EOS.

## External compatibility evidence

Google Maven and PyPI metadata checked during this audit both report 0.17.1 as the latest stable LiteRT-LM. There is no evidenced newer stable SDK to adopt blindly.

The publisher's pinned Qwen3.5-2B model card reports composite INT8 CPU degradation (3/8 in that specific eight-question test), while its individual probes pass. Its text GPU FP32 model does not fit its Pixel 8a 8GB test. These are publisher results on different devices. They support investigating this route, but neither prove the exact internal cause nor authorize advertising GPU on the Moto. Source: https://huggingface.co/litert-community/Qwen3.5-2B/blob/4327b7425533c26da2aec1d7b915e9742eead2cb/README.md

## Independent GGUF control — also discarded

An independent official GGUF was downloaded and verified to avoid recommending another untested model. It is Qwen/Qwen2.5-1.5B-Instruct-GGUF, revision91cad51170dc346986eccefdc2dd33a9da36ead9, filename qwen2.5-1.5b-instruct-q4_k_m.gguf, 1,117,320,736bytes, SHA-256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e, Apache-2.0. This is a separate model, never a conversion of the LiteRT file. The app's existing architecture allowlist includes qwen2.

The native reference was built from stock llama.cpp commit99b95488cac0f00ce3f05af113a8c1e287753f87 in an isolated worktree outside the app. The tested header, model loader, main llama source and ggml.c match the corresponding vendored app files. Stock CLI/completion compilation passed. This is Linux CPU execution, not the Android JNI adapter or phone testing.

Initial CLI/direct four-thread attempts did not finish within150s in this host with a two-core CPU quota. The direct attempt streamed a partial introduction. These are not successful benchmark results and do not establish phone behavior. A one-thread direct native arithmetic control completed; the final eight-case comparison uses one host thread, disabled polling, context8192, batch/microbatch512, output128, temperature0.3/topP0.95/topK40/minP0.05, repetition1.0 and seed0. No such host thread/polling change is applied to Android. The exact scheduling/kernel reason for the earlier slowdown was not isolated.

All eight final native processes return successfully and unload. Native EOG is observed via the stock completion driver's EOG marker in seven cases; the first reaches its ceiling without that marker. TTFT and exact generated-token counts were not instrumented and are not invented. The parser preserves actual native load/prefill/decode values; these cannot be ranked against four-thread LiteRT results or used as Moto benchmarks.

Semantic result remains FAIL:

- Isolated ordinary communes question invents La Concordia/Quilpué and repeats names.
- Explicit five-name question still includes Quilpué and incorrectly claims16 communes.
- Isolated arithmetic gives42; greeting completes normally.
- The final emulated four-turn answer repeats the medical-laws disclaimer instead of answering the communes question.

This control emulates the existing GGUF flattened prompt: trusted policy/current user are allocated before optional history. The legacy adapter receives current user before that history. This order is a separate prompt-layout concern, not a proven cause of isolated LiteRT failure; the structured LiteRT path places history and current user separately. Existing GGUF binding also fixes context8192, uses up to four CPU threads/default temperature0.3 and reloads for its load-scoped system policy; selected context/sampling options are not all wired through. Its app token metrics are estimates. These limitations are retained as debt, not hidden behind a performance recommendation.

Both tested alternatives fail semantic checks, so neither is offered as a reliable repair. This independent negative control also means semantic hallucinations cannot be attributed exclusively to LiteRT kernels. Exact attribution still requires a trustworthy floating-point reference of the original Qwen3.5 model. Files/reports below retain actual answers rather than a fabricated PASS.

## Current project validation

Executed ./gradlew testDebugUnitTest assembleDebug lintDebug with a temporary init script invalidating only Test task outputs, so unit tests rerun while unchanged compilation/lint outputs remain eligible for reuse. BUILD SUCCESSFUL in40s;159 actionable tasks,9 executed/150 up-to-date. XML reports confirm97app+2compat=99 tests,0failures/0errors/0skips. Debug assembly PASS; lint PASS_WITH_WARNINGS,0errors/73existing warnings (72app+1compat). No production app/runtime/test/schema source is changed in this follow-up. Release/signature checks from the original0.1.7 artifact remain applicable to its reverified unchanged SHA-256.

| Follow-up check | Result | Scope |
| --- | --- | --- |
| Unit tests99 / debug assembly | PASS | Executed current Gradle command; Test tasks forced to rerun |
| Lint | PASS_WITH_WARNINGS | 0errors/73existing warnings; unchanged lint artifacts reused |
| Original LiteRT native load/text/stream/unload controls | PASS_WITH_WARNINGS | Actual Linux CPU; exact semantic acceptance FAIL |
| Relevance controls / proposed alternative model repair | FAIL | Incorrect literal answers retained in native reports |
| Current APK SHA-256 / Drive metadata readback | PASS | Existing0.1.7, not a new repair |
| Android instrumented/device execution in this environment | NOT_EXECUTED | No attached device; user screenshots are separate limited evidence |
| Original floating-point model reference | NOT_EXECUTED | Required for narrower attribution; phone model hash also absent |

## Production disposition

No speculative policy, precision, SDK downgrade, new model default, GPU selection or unrelated feature is shipped as a fix. The existing 0.1.7 code/APK is preserved. Room remains schema 2, models/history remain private and unchanged, and both independent runtime import paths remain available. Full LiteRT semantic acceptance remains FAIL; physical performance/cancel/retry/unload require new measurements.

The already delivered same-certificate arm64 APK remains available in Drive: https://drive.google.com/file/d/1nxDYFA2d07YSExVlfVIFGFDwqYcC54Ej/view?usp=drivesdk

Local APK size/hash and Drive name/size/MIME/parent/private-sharing metadata were rechecked during this follow-up. It is the existing0.1.7 artifact, not a new quality fix. New unit/debug/lint results are above. Release and instrumented-APK compilation from the original unchanged artifact remain PASS; neither is a physical execution. See validation/apk-017.json and the retained Gradle logs. Host comparison compilation is a separate check.

Existing documentation updated: README.md, STATUS.md, CHANGELOG.md, docs/STATUS.md and docs/CHAT_LATENCY_AND_OUTPUT_017.md. This audit is new. Scripts, literal native results/logs, source provenance, build/Gradle logs and the current validation record are under docs/validation/relevance-017; [manifest](validation/relevance-017/manifest.json) lists files/hashes, and [verification](validation/relevance-017/validation.json) distinguishes execution from reuse. Production Kotlin/C++, runtimes, test source and Room migration are unchanged; models/history are preserved. No unverified feature is added.

## Exact phone comparison

1. Keep the existing LiteRT model and chats. From Models → model details → FILES record SHA-256; compare with the published original hash above. Also record configured context and reply Details for cold and warm turns, especially Wait until first text, Generation speed, output count and actual output-limit flag. Context2048/output256 is a memory comparison, not a speed guarantee. It is supported by LiteRT; the legacy GGUF configured context is currently not wired through.
2. In a new ordinary text chat ask: “Nombra cinco comunas de Santiago de Chile. Solo los nombres.” Then “¿Cuánto es 7 por 6? Solo el resultado.” Record literal replies, terminal state and Details; do not judge success from Model ready alone.
3. Compare the original four-turn sequence (Hola → tools → medical laws → communes) with the isolated communes question. Keep history, current question and cutoff notices visible when reporting errors. Do not treat a small model's medical/legal answer as verified source material.
4. Capture logcat with adb logcat -v threadtime -s 'LocalAI/LiteRT' 'LocalAI/Runtime' AndroidRuntime. Share only technical events/diagnostics after inspecting for sensitive data; no Hugging Face token is needed.

The next development step is a floating-point reference comparison of the original model/template and a matching phone-file identity, followed by controlled native kernel/backend testing. A new APK, unsupported GPU selection or another unverified smaller model would not provide the missing evidence. Do not recommend the discarded reference models as a solution.
