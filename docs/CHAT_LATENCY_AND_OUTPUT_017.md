# Chat latency and incomplete output — 0.1.7 / 2026-10-04

Follow-up: captures25091–25095 now show completed turns and metrics behind Details, but the communes question receives unrelated categories. New native CPU controls reproduce semantic failure without app policy/history; discarded SDK/precision/model/profile experiments and the separate phone limitations are recorded in [the relevance audit](LITERT_RELEVANCE_AUDIT_017.md). Earlier measurements below are historical, not timing readings from those new images.

## Reported physical handset

The user identifies the phone as **Moto G86 Power, 8GB RAM**. This is reported total RAM, not a measurement of available RAM or native peak use. Chipset/region, Android version, thermal state and candidate device timings are not yet verified. No accelerator or performance PASS is inferred from the handset name.

The delivered0.1.7 normal LiteRT load already requests `min(4, availableProcessors)` CPU threads; no new APK or model-specific thread change follows solely from the reported RAM. For a controlled memory comparison, use configured context2048 with output256, retaining the same CPU/sampler/repetition settings, then compare against4096 using Details. Smaller context may reduce KV allocation; it is not a measured decode-speed improvement. Keep output room for requested lists and inspect real limit notices. Existing Performance output1024 can permit substantially longer CPU replies; it is not a benchmark-proven faster setting.

## Physical evidence from 0.1.6

The new user screenshots 24994/24995 confirm real engineReused=true and sessionReused=true in unchanged CPU chat with Qwen3.5-2B_int8.litertlm. **PHYSICAL SESSION REUSE = PASS** for the supplied capture, separately from the still-unexecuted candidate. The earlier reuse fix is working.

The captured final reply has prepareMs96, prefillMs1330, prompt21, output85, ttftMs3116, requestTtftMs3338, prefillTps15.79, decodeTps3.55, totalMs25290, chunks84, nativeDone=true, outputLimitReached=false. It starts after3.338s and generates for25.290s. Warm preparation is96ms; sustained CPU decode and the long85-token answer dominate this delay. No transport/network bottleneck is measured. The first cold-load time is not visible in these latest captures; the earlier physical40.369s measurement remains historical, not a new benchmark.

The preceding answer ends at a bare4. Its full terminal metric line is clipped, so the exact finish reason cannot be proven from that capture. The configured128-token ceiling could truncate it, and the app had no output-cap notice. Calling native onDone is not proof that an answer is semantically complete. The later model-generated fourth point is newly generated text, not evidence of a hidden original fourth point.

## Changes and preserved scope

- Machine metrics are removed from the answer card, including old Room messages. Details opens human-readable measured timings/counts/reuse; raw technical metrics require another explicit expansion. Legacy comma-decimal measurements such as decodeTps=3,55 parse correctly. New storage encoding uses stable decimal formatting and preserves unsupported/null values.
- A verified outputLimitReached=true now displays an incomplete-answer notice. Continue on the latest eligible completed answer triggers a real, visible user instruction and normal runtime generation. The app does not synthesize a missing list item, modify old text or automatically spend more CPU/output. The model can still generate incorrect content or ignore the original language. A missing/unsupported finish metric does not become a cap claim by inference from punctuation or a trailing4.
- When the immediately previous completed native turn reached its cap, a trusted app status note tells the next prompt that text after the cutoff was never generated. Partial text stays intact in history. Any continuation is newly generated. The note changes the system context, so safe fresh conversation prefill may occur while the verified engine stays warm.
- The default policy asks for1–3 complete sentences and expansion only on request. Offline CPU execution, evidence-as-data rules, project instructions, memories and citation IDs remain. This targets reply length; it does not promise a faster tokenizer/decode engine or perfect instruction following.
- The exact prior0.1.6 CPU default tuple (128/.3/.95/40/1.1/seed0) is corrected once to output256; the original512/disabled-penalty tuple first follows its existing upgrade, then the new one. Other custom tuples and subsequent deliberate128 settings are preserved. An exact tuple cannot distinguish a manually chosen same value. Balanced LiteRT uses256/1.1. More output room avoids premature caps but can lengthen a detailed answer; it is not itself a speed optimization.
- Opening a chat or selecting an eligible LiteRT model starts real offline text-only CPU preparation. The composer stays usable while actual native phases/integrity progress are shown. Send awaits any ongoing preparation, then calls the usual runtime load path which reuses the verified engine. No synthetic warmup prompt or model text is generated. If Send is immediate, it still waits for the remaining actual load; cold initialization is not eliminated.
- Cancel preparation reaches the actual runtime. Replacement waits for prior cancellation/teardown before loading another model and suppresses stale state. Stop while Send is waiting on preparation cancels both jobs and cannot start a second load afterward. Unload on chat exit and native timeout/reset behavior remain. A failed preparation displays its real diagnostic and Send can retry; successful actual load clears that preparation error.
- Diagnostic smoke remains available behind Diagnostics, explicitly labelled as a smoke-only1024-context/32-output action. Normal chat no longer appears to have those smoke settings. Preparing data/model lookup or progress-monitor failures produce visible errors rather than an unhandled coroutine failure.

Room remains schema2, with no destructive migration or model/message deletion. Two private one-time preference markers preserve the previous upgrade and new output correction. Runtimes remain GGUF/llama.cpp and official0.17.1 LiteRT native CPU with the same pinned additive assistant-role compatibility client. No GPU/NPU, model/template conversion, download, multimodal, web or Python app feature was added. Existing GGUF, Model Library, RAG/citations, memory, tools and import/download paths remain.

## Source files

Created: data/GenerationMetricsPresentation.kt, data/LiteRtChatDefaultsUpgrade.kt, domain/inference/RuntimePreparation.kt; tests GenerationMetricsPresentationTest.kt, LiteRtChatDefaultsUpgradeTest.kt, RuntimePreparationTest.kt; this audit and native reference evidence.

Modified: MainActivity.kt, ui/ViewModels.kt, AppGraph.kt, inference/LiteRtConversationPayload.kt, data/ChatHistoryBuilder.kt, app/build.gradle.kts (versionCode8/name0.1.7); ConversationPromptTest.kt and LiteRtLmDeviceSmokeTest.kt; README/STATUS/architecture/changelog.

## Automated validation

Executed ./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest: BUILD SUCCESSFUL. **99 JVM tests** (97app+2compatibility client), zero failures/errors/skips; all earlier84 retained. New checks cover actual screenshot comma-decimal metrics, finish-reason honesty, persisted original/prior/custom output upgrades, real controller readiness, Send wait, repeated selection, cancellation/cleanup ordering, stale progress, error/retry, monitor failure and trusted incomplete-output context. The runtime peers in these control tests do not execute inference.

Debug and instrumented test APK compilation PASS. The updated real-device test is prepared to verify preparation without output, engine reuse on the first actual prompt, then session reuse/streaming/history restoration. Physical connected tests are **NOT_EXECUTED**; adb has no attached device. Lint:0errors/73warnings, the same72app and1unchanged upstream loader warning. Release/signing and delivery are recorded in [the artifact manifest](validation/apk-017.json).

## Actual native reference validation and unresolved quality

The public Qwen3.5-2B INT8 bundle was executed with the official LiteRT-LM native C API 0.17.1 on Linux x86_64 CPU, four threads, context4096, repeat1.1/ngram8/window256 and the candidate's actual source policy. Model SHA-256: `8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1`. This is real inference, but does not execute the Android APK, Kotlin/JNI/IPC or phone hardware. Synthetic prompts and [full results](validation/cpu-017.json) are included, alongside [the reproduction script](validation/cpu-017.py).

| Reference turn | Prior policy: output / total | Candidate policy: output / total |
| --- | --- | --- |
| hola | 11 tokens / 4.388s | 11 tokens / 3.792s |
| como estas | 50 tokens / 9.294s | 14 tokens / 3.195s |
| por que se demoran los mensajes? | 107 tokens / 17.303s | 115 tokens / 18.624s |

Candidate session prefill used only14/20 new tokens on the next two turns. Cold native engine load26.607s and unload1.490s returned. A deliberately requested real16-token limit stopped at16 decoded tokens: **native output-cap detection PASS**. Continue performed actual native generation and returned93 tokens. Literal EOS is unavailable; native completion is not reported as proof of EOS or semantic completeness.

**INSTRUCTION / CONTINUATION QUALITY = FAIL in this reference test.** The delay answer remained long and invented network reasons for offline inference. The follow-up about an unfinished fourth point invented content and reached256 tokens; Continue changed to English and echoed policy/status text. The standalone reference script does not execute the app's repetition detector, so its termination timings do not validate that detector. The native API, streaming and unload succeeded; that does not make these answers correct. The publisher has [documented CPU INT8 degradation with composite prompts](https://huggingface.co/litert-community/Qwen3.5-2B/blob/4327b7425533c26da2aec1d7b915e9742eead2cb/README.md), but this is not proof of a sole cause or an available app-code repair.

Three additional shorter policies were actually executed with the same native model/settings ([results](validation/policy-probe-017.json), [script](validation/policy-probe-017.py)). The two English variants reached256 tokens for the delay question; the Spanish variant used79 but still invented connection reasons and ignored the two-sentence instruction. None reliably solved quality, so those variants were discarded. The brief candidate policy is a best-effort length instruction, not an enforced word limit or a verified general speedup.

The new default256 ceiling gives more room for explicit lists and exposes real caps; it can also increase long-answer time. Preparation overlaps cold work with typing instead of eliminating initialization. CPU token throughput has not been accelerated. The original missing fourth point cannot be recovered: a continuation is newly generated. Device/model quality, sustained decode speed and cancellation/retry after preparation still require the physical test below. The app does not create a canned fourth point or claim a full LiteRT acceptance PASS.

| Validation | Status |
| --- | --- |
| Supplied 0.1.6 physical output and warm session reuse | PASS |
| 99 JVM tests / debug build / instrumented APK compilation | PASS |
| Debug lint | PASS_WITH_WARNINGS — 0 errors, 73 warnings |
| Linux native tokens, streaming, real cap, continuation API and unload | PASS_WITH_WARNINGS — answer quality fails; literal EOS unavailable |
| Linux instruction following / missing-point answer / continuation language | FAIL |
| Candidate Android device load, quality, speed, Stop, retry and unload | NOT_EXECUTED |

No new inference cancellation benchmark was run in the 0.1.7 reference script. The previous real native cancellation/retry result remains [historical evidence](validation/final-cpu-016.json), not a new Android result. The current 99 tests cover preparation/controller cancellation ordering, not physical native cancellation.

## Signed delivery

**Release/signature/Drive delivery PASS**: `./gradlew assembleRelease -Parm64Only=true` succeeded. [Verified0.1.7 arm64 APK in Drive](https://drive.google.com/file/d/1nxDYFA2d07YSExVlfVIFGFDwqYcC54Ej/view?usp=drivesdk), 91,355,065bytes, versionCode8, package `com.localai.workspace`, minAPI29/target35, same update certificate. Upload metadata readback confirms exact name/size/MIME/original parent and private sharing. SHA-256 `c4d539fb6e15293a320e52673d622281c6c6ada2ebcd63bfc34ac2886e28703a`. Native libraries are byte-identical to0.1.6; the official LiteRT source extraction matches Google's AAR and packaged JNI matches its standard NDK-stripped library. Previous Drive APKs remain. [Artifact manifest](validation/apk-017.json).

Local artifact: `/workspace/local-ai-workspace/app/build/outputs/apk/release/local-ai-workspace-0.1.7-litert-chat-latency-arm64.apk`. The certificate SHA-256 is `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Debug and instrumented APKs also exist under their normal build output directories, but the linked Drive file is the signed arm64 release.

| Delivery check | Status |
| --- | --- |
| Arm64 release build / signature / version and package | PASS |
| Unchanged native backends and official SDK source/library integrity | PASS |
| APK upload / name, size, parent and private-sharing readback | PASS |

## Remaining debt and next step

Physical preparation/Stop/retry/model switching and output-quality validation are pending. Intrinsic CPU initialization, sustained decode and INT8 quality remain unresolved; no new GPU/NPU availability or measured CPU speedup is claimed. The pinned Kotlin history-role compatibility client needs maintenance with SDK updates. Literal EOS and unsupported runtime metrics remain unavailable. Existing inference background lifecycle and long-download foreground-worker debt were not expanded in this iteration. The next step is the exact physical comparison below, including the handset model/RAM and measured Details, before choosing a verified CPU model/backend or tuning threads. Increasing context/output or enabling an unverified accelerator is not a demonstrated repair.

## Next physical check

1. Install0.1.7 as an update, keeping the model and chats. Open a chat and observe real Loading model/integrity/session preparation before sending. Type while it prepares; compare sending immediately versus after Model ready. This overlaps work and does not remove the underlying cold cost.
2. Send hola → como estas → the delay question without changing the model/settings. Raw engineReused text must no longer appear below the answers. Record whether replies are brief and complete: the native reference did not reliably achieve that. Open Details to capture request wait, new input/output tokens, speed and reuse. CPU throughput may remain around the previous3.55tokens/s; no measured phone speedup is asserted.
3. Request four numbered points explicitly. If a real cap occurs, the answer must show its limit notice. Continue must perform real generation. Check language and content; the reference model invented a missing fourth point despite the trusted cutoff note. A failure here is answer-quality FAIL, even if the runtime produces tokens and returns idle.
4. Cancel preparation, then send a simple prompt. Switch models during preparation; verify prior teardown and a usable state. During real streaming test Stop → new prompt. Leave/reopen to test restored history; cold initialization after unload is expected.
5. Open Diagnostics only for the separate CPU smoke. Capture technical metrics only when needed.

```bash
adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' 'AndroidRuntime:E' '*:S' > litert-017-chat.log
```

Start capture before opening chat; include preparation, Send, Stop/retry and terminal state. Do not restrict to one PID because the worker may reset. App diagnostics omit prompt/full token text; review vendor/platform logs before sharing. Android0.1.7 load/quality/latency/continuation/cancel/retry validation remains NOT_EXECUTED here.
