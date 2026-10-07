# LiteRT CPU stability — 0.1.6 / 2026-10-03

## Result and physical evidence

This iteration fixes real history-role and conversation-lifecycle defects and adds native preventive decode controls. It does not add HF, GPU/NPU, vision, web or other features. **0.1.6 ANDROID_DEVICE_VALIDATION = NOT_EXECUTED**: `adb devices -l` is empty. A real Qwen model was executed with Google's 0.17.1 native Linux CPU runtime; that is reference evidence, not execution of this Android APK/JNI.

The latest user-supplied 24959/24960/24958 screenshots show **0.1.5 MULTI_TURN_QUALITY = FAIL**. A good first greeting is followed by repeated interleaved paragraphs for `por que se demoran los mensajes?`, then a visible `REPETITION_LOOP` error and Ready to retry. UI recovery is proven; those screenshots alone do not establish native stop acknowledgement rather than deadline reset, nor the second turn's engine reuse or exact elapsed time. The 4:24/4:28 capture times are not a runtime benchmark.

Actual first-turn measurements: engineReused=false, prepareMs=45593, loadMs=40369, sessionMs=7, prefillMs=3615, prompt=85, output=11, ttftMs=5060, requestTtftMs=50855, prefillTps=23.51, decodeTps=3.23, totalMs=7037, chunks=10. Cold engine preparation dominates. The old eos=true field meant native onDone; it did not prove the literal EOS token. Basic physical generation remains PASS, while full quality/cancel/retry/unload acceptance requires retesting.

## Confirmed cause and precise pipeline

Room completed-pair history → bounded ConversationPrompt → IPC payload → LiteRtConversationPayload → ConversationConfig.initialMessages → nativeCreateConversation → the bundle's embedded Jinja template → prefill/decode → MessageCallback deltas → checked IPC → Flow → Room/UI.

The official Kotlin 0.17.1 `Message.model` serializes role **model**. JNI forwards the preface JSON unchanged. Qwen3.5's actual embedded template recognizes previous assistant turns only as **assistant**; generic and Qwen3 native processors also emit assistant. Rebuilt history therefore previously rendered `<|im_start|>model`, bypassing the template's assistant-specific history/thinking branch. This is a confirmed format defect, established by source audit and actual native rendering of the downloaded model. It is not a proven sole cause of all Android repetition: reference generation with the correct role and disabled penalty also repeated to the output limit.

The controlled `litert-compat` module compiles the official 16 Kotlin client files from v0.17.1, commit `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`. Only Message.kt changes: additive Role.ASSISTANT and Message.assistant factories. Role.MODEL/Message.model, JNI class names/signatures, handles, callbacks and all other source files are retained. Apache-2.0 license and per-file upstream hashes are included. The native libraries still come from the exact Google Maven 0.17.1 AAR; its precompiled classes.jar is excluded. The previous Kotlin metadata-check bypass is removed. No model or embedded template is patched, and no rendered prompt is sent a second time.

`LiteRtBundleMetadata` reads only the bounded official FlatBuffer header and LlmMetadata protobuf (each ≤1MiB, ≤256 sections). The actual processor/template selects assistant versus model; unknown restored-history roles become a recoverable HISTORY_ROLE_UNRESOLVED error. It does not infer role from Qwen filenames, load tensors/tokenizers into RAM or invent capabilities. The public 17,113-byte test fixture contains only header/metadata, no weights.

## Session reuse and latency

The previous version kept a healthy engine but rebuilt Conversation on every turn, forcing full history prefill. `LiteRtConversationReuse` now keeps a completed ordinary text conversation only when its actual emitted answer and user content exactly equal the app's budgeted completed history, with identical system policy, sampler, seed and output settings. Actual getTokenCount plus a conservative allowance for the next input/output must fit context.

The next send contains only the new user message. Failed/cancelled/blank/oversized output, dropped/changed history, evidence differences, changed policy/configuration, thinking, images, unload and native errors invalidate the conversation. Old private evidence or budget-excluded turns are never silently retained. Safe session teardown and engine retention after acknowledged stops keep the existing cancellation contract; unresponsive native work still causes worker termination/reset.

Actual branches report engineReused and sessionReused. Reused engines have no new loadMs, reused conversations have no fabricated sessionMs. prepareMs and UI requestTtftMs remain distinct from generation-only ttftMs. Cold load still occurs after leaving/unloading the chat or configuration/reset paths. This update does not promise elimination of the approximately 40s cold initialization measured on the phone.

Smoke forces a fresh engine, text-only CPU, context1024/output32, no history/tools/RAG/memory/vision/speculation. It now uses the app-private disk XNNPACK cache rather than a multi-GB in-memory weight cache. Normal chat uses that same private cache. The reference cache occupied approximately 1.8GiB in addition to the 1.97GiB model; free storage and RAM still matter, and Android may evict cache files. No cache/model is bundled in the APK.

## Decode and recovery controls

Ordinary structured final-text generation with repeatPenalty>1 uses the real native RepetitionPenaltyConfig with a 256-token window and NoRepeatNgramConfig(size8, window256). No forced presence/frequency penalty or speculative decoding. A deliberate repeatPenalty=1.0 disables these extra native controls; advanced thinking and minimal smoke do not receive the ngram constraint. Thinking and capabilities remain separate, and no GPU/NPU is advertised.

The old persisted CPU default tuple (output512, temperature0.3, topP0.95, topK40, repeat1.0, seed0) changes **once** to output128/repeat1.1 on normal LiteRT use. Context and other fields are retained. Any other custom tuple and all GGUF rows stay unchanged; a subsequent deliberate return to the old tuple is respected. An exact match cannot distinguish an explicitly chosen legacy tuple from the old default. The one-time marker is in private SharedPreferences, not a Room schema reset. Room remains version2, with the existing 1→2 migration unchanged.

The Balanced LiteRT profile also applies real output128/repeat1.1 parameters. Smaller output bounds cap long generations; they do not accelerate engine load or guarantee a faster short answer. The system policy is shorter, and empty-evidence turns do not carry unnecessary citation instructions. Evidence/memory labels, actual citation IDs, project instructions and the GGUF text binding remain in place.

The fallback RepetitionLoopDetector retains cyclic-block detection and now catches three substantial identical paragraphs even when interleaved with other paragraphs or split across callback/newline boundaries. It calls actual native cancel from the separate executor; further chunks are suppressed. A five-second independent stop deadline resets an unresponsive native worker. Errors remain Errors, partial messages stay visible with diagnostics, and failed pairs cannot poison retry history. Deliberately repeated long passages can be interrupted; varying semantic repetition may escape the exact guard.

## Diagnostics and metric honesty

Retained structured events include verify/JNI/capabilities/model open/session creation, prompt rendering, prefill scheduling, first actual output, decode chunks, native stop/error/unload and deadline-reset checkpoints. New events: LITERT_HISTORY_ROLE, LITERT_SESSION_REUSE_CHECK, LITERT_SESSION_REUSE, LITERT_CACHE_COUNT_UNAVAILABLE, LITERT_DECODE_CONTROLS and LITERT_NATIVE_DONE. Events carry timestamp/thread/elapsed/model/CPU and available counts; prompts, full generated text, documents and tokens are not logged.

Error snapshots now retain real engine/session reuse, private file hash/format, client version, sampler, preparation/load/session/first-output timing and chunk counts. Technical details remain available after reopening the chat. The transient notice contains the interruption reason; the existing message retains partial output and detailed diagnostics.

Native onDone can mean EOS or output-limit termination. Version0.1.6 reports nativeDone separately and derives outputLimitReached only from actual benchmark decode count versus the requested cap. LiteRT eosObserved and exact prefill-completed timestamp remain null/unavailable. Callback chunks are not tokenizer counts; actual outputTokens comes from native benchmark info. No metric is fabricated when unavailable.

## Actual native reference tests

Public model: `litert-community/Qwen3.5-2B`, pinned revision `4327b7425533c26da2aec1d7b915e9742eead2cb`, Qwen3.5-2B_int8.litertlm, 2,116,592,816 bytes; verified SHA-256 `8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1`. Actual container format1.6.0, generic processor, declared context4096 and embedded assistant template. Apache-2.0 public model; no gated access bypass or token. The user's imported file was not available for a matching-hash claim.

Reference environment: official litert-lm-api0.17.1, Linux x86_64 CPU, four threads, context4096, private disk cache, benchmark enabled, speculation disabled. The final run uses the app's actual normal system policy, sampler0.3/0.95/40/seed0, repetition1.1/ngram8/window256 and output128. It uses the native C API, not Android JNI/IPC/ViewModel.

| Final reference stage | Actual result | Status |
| --- | --- | --- |
| Engine open | 28,889ms wall time | PASS |
| First hola | session39ms; TTFT3654ms; 71 prefill/11 decode; 4971ms; 10 chunks | PASS |
| Same-session como estas | 14 new prefill/50 decode; TTFT1465ms; 8692ms | PASS_WITH_WARNINGS: coherent but longer than needed |
| Same-session delay question | 20 new prefill/107 decode; TTFT1394ms; 16497ms | PASS_WITH_WARNINGS: no observed substantial loop, imperfect explanation |
| Restored assistant history | 2 assistant markers, no model marker; 97 prefill/19 decode; TTFT2015ms | PASS |
| Minimal smoke actual tokens | 21 prefill/3 decode; TTFT1586ms; text FUNCION | PASS for actual tokens; FAIL for exact FUNCIONA instruction |
| Cancel | after real chunks; native stream settled in138ms | PASS on Linux only |
| Retry after cancelled session teardown | real 10 decode tokens, TTFT1259ms; echoed instruction | PASS for runtime recovery; FAIL for exact instruction quality |
| Unload | native close returned in369ms | PASS on Linux only |
| Literal EOS token | not exposed by API | NOT_EXECUTED |
| Android candidate load/first token/stream/cancel/retry/unload | no connected device | NOT_EXECUTED |

The reference reports contain only these synthetic/public test prompts and outputs: [final run](validation/final-cpu-016.json), [role baseline](validation/probe-016.json), [conversation/presence comparison](validation/profile-016.json), [moderate controls/sampler comparison](validation/guard-profile-016.json). The [reference harness](validation/run-final-cpu-016.py) is a desktop diagnostic, not a Python feature or runtime dependency in the app. referenceExecution=PASS in its JSON means native API/token/cancel/retry/close checks passed, not semantic answer quality or Android acceptance.

Correct role alone still repeated on the default CPU profile. Window16 allowed a short otro/otra loop; window8 was retained instead. Strong presence1.5 and sampler0.7/0.8/20 experiments worsened answers/echoed prompts; **they are not shipped**. The final profile keeps existing0.3/0.95/40 sampling and no presence/frequency penalty. An initial reference harness failed after neglecting to create its cache directory and was OOM-killed; the directory was created and every reported final test rerun successfully. That harness setup mistake is not attributed to the Android app.

The model publisher also documents CPU INT8 degradation on a composite multi-question probe, even when individual questions pass. It corroborates a quality limitation, not proof of the sole cause of this phone's loop. Exact answer correctness, diverse conversation quality and physical speed still need verification. [Pinned model card](https://huggingface.co/litert-community/Qwen3.5-2B/blob/4327b7425533c26da2aec1d7b915e9742eead2cb/README.md).

## Automated validation and delivery

Executed `./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL. **84 JVM tests** (82 app +2 compatibility-client), zero failures/errors/skips; all earlier60 remain. New regressions cover the real public metadata, malicious/truncated bounds, processor/template roles, exact native role serialization, session identity/invalidation, resource closure, interleaved screenshot paragraphs, persisted default upgrade/custom-setting preservation, actual native control configuration and distinction between onDone/cap/unsupported EOS.

Lint: zero errors, **73 warnings total** (72 app warnings retained; one upstream NativeLibraryLoader `UnsafeDynamicallyLoadedCode` warning). The loader retains official support for an explicitly configured native-library location; no model/repository content supplies that path. Its source is unchanged. Instrumented test APK compilation is PASS; connected instrumentation is NOT_EXECUTED, with no missing-fixture fake inference PASS. The instrumented tests now require actual tokens, exact healthy-session reuse, three normal turns, restored assistant history, real cancel/retry and unload.

`./gradlew assembleRelease -Parm64Only=true`: BUILD SUCCESSFUL. Signed and verified with the same existing test-distribution certificate (one v3 signer), SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Package com.localai.workspace, versionCode7/name0.1.6, minAPI29, target35, arm64-v8a only. The native source extraction equals Google's pinned AAR byte-for-byte; the packaged JNI library equals the original after standard NDK strip (an eight-byte non-code difference). DEX/ZIP inspection confirms both actual runtimes, assistant role, session-reuse/controls/watchdog code and no model/diagnostic report payload in the APK.

**[Verified APK0.1.6 in Drive](https://drive.google.com/file/d/18WkNQngnTdpeNm-xjDxIJjUTFw3a8K-c/view?usp=drivesdk)**. Absolute local path: `/workspace/local-ai-workspace/app/build/outputs/apk/release/local-ai-workspace-0.1.6-litert-cpu-stability-arm64.apk`. Size **91,305,913 bytes**. Local SHA-256 `e12a7dc2e38c956d305b5340f78aca4e6e501f366a78617987d6e25f3d54208a`. Upload and metadata readback confirm exact name/MIME/size/original parent and private sharing; no server-side SHA check is claimed. Prior Drive APKs/sharing are retained. Machine-readable [artifact/check manifest](validation/apk-016.json).

| Delivery check | Status |
| --- | --- |
| JVM84 / debug / arm64 release / signature | PASS |
| Lint0 errors /73 warnings | PASS_WITH_WARNINGS |
| Instrumented test APK compilation | PASS |
| Drive upload and metadata readback | PASS |
| Qwen native Linux execution/recovery | PASS_WITH_WARNINGS |
| Exact smoke answer instruction | FAIL |
| Candidate Android inference/quality/performance/cancel/retry/unload | NOT_EXECUTED |

## Exact next phone test

1. Install0.1.6 as an update using the existing package/signature; preserve the model and app data. Use CPU and ordinary chat, without image/RAG/memory extras for isolation. Normal use corrects the exact old default tuple once; other customized output/penalty settings remain and can be inspected in model settings.
2. In one chat send `hola`, then `como estas`, then `por que se demoran los mensajes?`, keeping settings/model unchanged. Expected: real streamed tokens, recoverable terminal states and no repeating paragraphs. Record first cold loadMs/prepareMs/requestTtftMs. The first load can still take the previously observed tens of seconds.
3. On the next completed reply inspect engineReused=true and sessionReused=true, no new loadMs/sessionMs, actual prompt/output counts and requestTtftMs. If sessionReused=false, capture logs: exact-history/context safety may legitimately require fresh prefill. Do not assume a warm engine from a screenshot with no terminal metrics.
4. Tap Stop during a longer reply, then send a new simple message. It must acknowledge or reset within five seconds, return to a usable state and produce actual new tokens. Capture native-stop acknowledgement versus reset; visual Ready alone does not prove native cancellation.
5. Leave/reopen the chat, then ask another simple question. That exercises restored assistant history after unload; a new cold load is expected. Old failed repetitions stay visible in history but must not be followed in new inference.
6. Run the separate CPU smoke for minimum actual-token validation. Record exact text separately from runtime success. The reference model sometimes returned FUNCION or echoed the instruction; do not turn nonzero tokens into a semantic-quality PASS.

```bash
adb install -r local-ai-workspace-0.1.6-litert-cpu-stability-arm64.apk
adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' 'AndroidRuntime:E' '*:S' > litert-016-three-turns.log
```

Start logcat before the first send and leave it running through Stop/retry. Do not restrict capture to one PID: the service process can reset. Look for HISTORY_ROLE historyRole=assistant; SESSION_REUSE_CHECK/SESSION_REUSE; DECODE_CONTROLS repeat1.1/ngram8/window256; FIRST_TOKEN/UI_FIRST_TOKEN; NATIVE_DONE, NATIVE_STOPPED or explicit ERROR/deadline reset. App diagnostics do not log prompt/token text; review platform/vendor logs before sharing them.

## Files, preservation and remaining debt

Created: litert-compat/build.gradle.kts, LICENSE, UPSTREAM.json, README, 16 pinned SDK Kotlin sources and NativeHistoryRoleTest; LiteRtBundleMetadata.kt, LiteRtConversationReuse.kt, LiteRtDecodeControls.kt, data/LiteRtDefaultsUpgrade.kt; metadata fixture/provenance; five app test files; this document and desktop reference reports/harness.

Modified: settings.gradle.kts; app/build.gradle.kts; AppGraph.kt; MainActivity.kt; ui/ViewModels.kt; domain/model/WorkspaceModels.kt; domain/inference/RepetitionLoopDetector.kt; inference/LiteRtLmService.kt, LiteRtLmInferenceRuntime.kt, LiteRtEngineResources.kt, LiteRtConversationPayload.kt, LiteRtIpc.kt; existing payload/resources/IPC tests; LiteRtLmDeviceSmokeTest; README/STATUS/architecture/changelog/earlier diagnostic record.

Room schema/migration, model files, GGUF binding/native engine, Model Library, download/import services, RAG/citation validation, memory and tools are retained. No destructive migration or new permissions. Both actual runtimes and formats remain GGUF/llama.cpp and LiteRT-LM/0.17.1 CPU.

Remaining debt: Android physical quality/cancel/retry/unload and thermal/memory/storage validation; intrinsic cold CPU initialization; model INT8 semantic quality; unsupported native EOS/prefill-boundary/peak-memory metrics; compatibility fork maintenance against future official SDK releases; conservative context/repetition heuristics; existing broader inference background lifecycle and long-download foreground-worker limitations. No full LiteRT device PASS or measured phone speedup is asserted. The next step is the three-turn physical retest above, before unrelated feature development.
