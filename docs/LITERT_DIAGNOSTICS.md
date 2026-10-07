# LiteRT-LM stabilization history and diagnostics

Current candidate: **0.1.6**, after user-provided 0.1.5 interleaved repetition and a recovered error. See [current CPU stabilization and precise phone retest](LITERT_CPU_STABILITY_016.md) for native-role compatibility, exact-history session reuse, real decode controls, tests and delivery. The records below preserve earlier investigations. Smoke now retains a private disk weight cache while forcing a fresh engine; LiteRT onDone is not reported as a proven EOS token. The Kotlin metadata-check bypass below is historical and removed by the pinned source client.

## Evidence and scope

Current phone gate: **0.1.4 MULTI_TURN_QUALITY = FAIL**. A good first hola response is followed by repeated text for como estas. Cold load was 72.9s; generation-only TTFT excludes that cost. **0.1.5 DEVICE_VALIDATION = NOT_EXECUTED**. See [current structured-history, native loop-stop and latency audit](LITERT_REPETITION_FIX.md) for exact fixes, 60-test evidence, native cancellation/engine-retention conditions and phone retest. This supersedes the earlier full-acceptance assessment; the successful basic output below remains valid historical evidence.

**LITERT_BASIC_DEVICE_INFERENCE = PASS** in the latest user-supplied physical screenshots after 0.1.3 delivery: Qwen3.5-2B_int8.litertlm + hola produced an actual reply, 10 native tokens, 9 chunks and onDone. Initial load 49,516ms, generation 6,638ms; full acceptance PASS_WITH_WARNINGS. See [physical evidence, timing boundaries and 0.1.4 retest](LITERT_PHONE_VALIDATION.md). **0.1.4 DEVICE_VALIDATION = NOT_EXECUTED**: no device/emulator/model is attached here. The physical result was supplied by the user, not executed by Codex.

Historical failures: original 0.1.0 never produced tokens; 0.1.1 Unknown LiteRT operation was located in deferred IPC dispatch and fixed in 0.1.2; 0.1.2 Hi timed out during LOADING_MODEL after **1,345,071ms (22m25s)** instead of 240,000ms. The exact original native loading substep remains unknown. No Hugging Face, multimodal, web or other feature work is part of this iteration.

## 0.1.4 unchanged-engine reuse

The service previously recreated Engine on every turn despite retaining the worker after successful normal chat. It now retains the initialized CPU engine when the verified private file and engine configuration match, closing/recreating Conversation for each full rebuilt app prompt. New sampler settings apply to the fresh session; native history is not duplicated. Smoke, changed model/context/backend configuration, explicit unload, errors or reset require cold initialization.

Measured engineReused and prepareMs are exposed through nullable domain/IPC metrics. Warm loadMs is absent, not the previous 49.5s timing or a made-up zero. SESSION_CLOSE_START and ENGINE_REUSE identify the real branch. Host resource-lifecycle and IPC tests pass; physical speedup remains NOT_EXECUTED. Initial cold load is not eliminated. SDK stays 0.17.1, CPU only, with the existing deadlines/cancellation/wake leases.

## 0.1.3 delayed watchdog and load diagnosis

Verified control defect: load was called from viewModelScope/Main; its polling watchdog checked expiry only when that consumer coroutine ran. Replies, binding and worker reset also depended on Main. An unscheduled/busy consumer can therefore delay expiry and termination. System.nanoTime did not provide Android's explicit sleep-inclusive clock. The screenshot establishes a very late timeout, but it does **not** prove whether Main was starved, the app/process was suspended, or another phone condition delayed it. Native initialization, file hashing and capabilities all shared LOADING_MODEL, so blaming prefill, architecture, INT8 or a chat template from this screenshot would be unsupported.

0.1.3 changes:

- Load/unload run on IO; a dedicated HandlerThread receives replies and executor-delivered binding callbacks. Worker termination has no Main dependency.
- A separate scheduled deadline monitor acts without consumer polling. Its thread-safe watchdog uses SystemClock.elapsedRealtime. Expiry requests SIGKILL before UI, Room writes or unbinding, closes IPC with the diagnostic error, and releases the wake lease. Monitor exceptions are surfaced.
- Explicitly requested operations hold a partial wake lease with a finite timeout. LOAD's lease bound is 346s including binding, load, session, unload budgets and a 1s release margin; the load deadline remains 240s. Native terminal events, errors, cancellation and deadlines release it early. WAKE_LOCK is a normal permission, with no runtime dialog. This is not a foreground inference service or a claim of unlimited background/process-freeze survival.
- The service reports real hash bytes, a separately labelled JNI/library step, capabilities, engine construction, **Engine.initialize / nativeCreateEngine**, and session creation. A diagnostic observer samples available/total RAM, worker PSS when available, storage, uptime and the Java stack during the call. These are observations, not fabricated native progress or peak benchmarks.
- The saved error includes the last checkpoint. Tagged JSON in the existing generationMetrics column retains the technical report; Technical details on that failed message works after reopening. Existing benchmark strings retain their display; schema stays 2.

Engine.initialize in the inspected 0.17.1 source holds its engine lock while invoking LiteRtLmJni.nativeCreateEngine. No separate allocation/cache/kernel-preparation callbacks are exposed. If the next failure is ENGINE_INITIALIZE_START, its stack/config/RAM snapshot will locate the blocked API, while an internal C++ cause still needs device/native evidence. The embedded template and prompt are not invoked before this step completes.

The displayed Hi belongs to normal chat. The smoke button instead inserts the exact fixed FUNCIONA prompt, context 1024, output 32 and disabled persistent weight cache. Merely displaying that button is not evidence that the smoke ran. Reports now record CPU_SMOKE versus NORMAL_CHAT and the actual context/cache settings.

Regression evidence: a real client with an unresponsive IPC peer and Main kept paused times out, records ENGINE_INITIALIZE_START/nativeCreateEngine, requests worker kill and releases its wake lease. Ignored CANCEL has a separate deadline; retry/unload work on the same client. The peer supplies test events and never invokes JNI. These tests validate control behavior, **not Qwen inference or physical deadline timing**.

## Located 0.1.1 failure and 0.1.2 correction

The failing boundary is **service Handler → queued native worker**, before `load()` and before integrity checking, capabilities, Engine initialization, session creation or prompt processing. `LOADING_MODEL` in that diagnostic was the operation's initial stage; it did not mean the model had been opened. There was no evidence of native inference or model incompatibility in this error.

The 0.1.1 worker closure retained `android.os.Message` and evaluated `message.what` later. Once Handler returned, Looper recycled the pooled Message, clearing `what` to 0, `replyTo` and data. The worker therefore selected the unknown-command branch. The copied Bundle did not protect the uncopied command. This defect was introduced in 0.1.1; it does not establish the root cause of the original 0.1.0 hang.

0.1.2 snapshots command, operation ID, reply Messenger and a copy of the scalar Bundle on the Handler thread. Asynchronous work only uses those captured values. Invalid incoming commands return `IPC_UNKNOWN_COMMAND` before entering the native queue, rather than a misleading native error. All Android Message handoffs were audited; the client reply Handler already copies the Bundle synchronously into its channel.

`LiteRtIpcDispatchTest` uses Robolectric's Android Message implementation and the actual service dispatcher. It blocks the real executor, invokes the Handler callback, recycles the Message, then releases the executor. LOAD must reach file validation; UNLOAD must complete. The test also retries after a file error, checks snapshots across pooled-message reuse and rejects invalid commands. These are framework/dispatch tests without JNI or a real model, not runtime validation. Host tests require JDK 21 to resolve the official AAR's class descriptors; source/Android target remains Java 17.

The four new tests passed with the fix. A controlled mutation restored the original deferred `message.what` read: both LOAD and UNLOAD regressions failed deterministically. The snapshot-based dispatcher was restored immediately afterward. This establishes the shown IPC defect independently of model architecture or SDK compatibility.

## Confirmed defects

1. Engine initialization, session creation and response collection had no deadline. Kotlin timeout alone cannot interrupt blocking JNI.
2. Chat displayed one Generating state for all phases. Blank historical messages also displayed Generating with no running job.
3. Cancellation tried to update Room inside an already cancelled coroutine, potentially leaving GENERATING records. Persistence now uses `NonCancellable`; interrupted records are recovered on conversation open.
4. The official 0.17.1 Flow has `awaitClose {}` without native cancellation and ignores `trySend` failures. This adapter now uses explicit `MessageCallback`, checked bounded IPC and real native cancellation/process reset.
5. `getBenchmarkInfo()` was used without enabling `ExperimentalFlags.enableBenchmark` (false by default). Benchmark is now enabled before initialization; unavailable metrics cannot fail a completed response.
6. `Message.toString()` omits channels. Actual content/channel chunks are now observed explicitly; empty callbacks do not count as output or extend the first-token deadline.
7. The new Engine was not owned until after session creation. The worker retains ownership immediately and closes conversation before engine. Version 0.1.4 isolates this lifecycle in LiteRtEngineResources and reuses the verified engine with fresh conversations.
8. 0.1.1 deferred workers retained a pooled Android Message and lost the command after recycling. 0.1.2 uses a synchronous request snapshot.

These are verified code defects. **The exact original native blocking phase remains unknown without the phone's trace.** A missing MODEL_OPEN_OK means engine creation; missing SESSION_CREATE_OK means conversation creation; missing PROMPT_FORMAT_OK means native template rendering. Missing FIRST_TOKEN after NATIVE_SEND_RETURNED narrows it to combined prefill/first decode/callback. FIRST_TOKEN without UI_FIRST_TOKEN points to IPC/Flow/UI.

## Actual SDK and model

- Artifact: `com.google.ai.edge.litertlm:litertlm-android:0.17.1`. Its compiled signatures and [official v0.17.1 source](https://github.com/google-ai-edge/LiteRT-LM/tree/v0.17.1/kotlin/java/com/google/ai/edge/litertlm) were inspected, including Engine, Conversation, Config, Message and Benchmark.
- [Model card](https://huggingface.co/litert-community/Qwen3.5-2B/blob/main/README.md): requires LiteRT-LM >= 0.15; declares CPU/GPU, compiled context 4096 and an embedded simplified ChatML template with thinking disabled. 0.17.1 meets its declared version requirement; that is not device validation.
- [Published manifest](https://huggingface.co/litert-community/Qwen3.5-2B/blob/main/litertlm_manifest.json), checked 2026-10-03: current text variant **2,116,592,816 bytes**, SHA-256 **8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1**. Earlier revisions may differ. These are reference values, not proof that the user's file matches.
- The worker validates magic/format major, recorded size and streamed SHA-256 before loading. Matching the import hash proves the private file is unchanged since import; provider integrity additionally requires a trusted source hash.
- Google Maven currently lists 0.17.1 as latest; the SDK was not changed speculatively. The existing newer-AAR/Kotlin-2.0 metadata workaround remains. The callback path avoids the SDK coroutine Flow, but binary compatibility still requires device execution.

## Pipeline and cleanup

Chat → registry → LiteRtLmInferenceRuntime → non-exported Messenger service in **`:litert`** → one native worker → Engine → Conversation → structured user Message → MessageCallback → checked IPC → GenerationEvent → ViewModel StateFlow → Compose.

The main process owns an independent timer and off-Main IPC independently of JNI. The service main thread accepts IPC while a separate native thread loads/generates. A control thread calls `Conversation.cancelProcess()`; an observer collects loader checkpoints and structural diagnostics. Engine initialization has no cancellation API, so cancel during load requests termination of the worker identified by the trusted service handshake. Timeout/error terminate before IO unbinding; app coroutine cancellation also resets that process. Room and chat stay in the main process. Android/native process behavior still needs physical execution.

The native `renderMessageIntoString` validates a nonempty template render; only lengths are logged. The rendered string is never sent as the prompt. The original `Message.user(Contents.of(Content.Text(prompt)))` is sent once with the bundle template. There is no manual ChatML wrapper.

| Phase | Limit | Diagnostic |
| --- | --- | --- |
| Service bind / handshake | 15 s each | SERVICE_BIND_TIMEOUT / handshake error |
| Integrity + model initialization | 240 s | MODEL_LOAD_TIMEOUT |
| Conversation creation | 60 s | SESSION_CREATE_TIMEOUT |
| Prompt preparation/render | 30 s | PROMPT_FORMAT_TIMEOUT |
| Combined prefill/first decode, no output | 180 s | FIRST_TOKEN_TIMEOUT |
| Decode without fresh output | 120 s | DECODE_TIMEOUT |
| Total generation, even with output | 900 s | GENERATION_TIMEOUT |
| Native cancellation confirmation | 5 s | CANCEL_TIMEOUT + worker reset |
| Resource unload | 15 s | UNLOAD_TIMEOUT + worker reset |

Repeated phase updates/empty callbacks cannot extend load or first-token limits. Errors include phase, code, elapsed time and expandable technical detail. Completion without actual output is NO_TOKENS. A callback chunk is not equated to a tokenizer token.

## Logs and measurements

Tag **LocalAI/LiteRT**. Structured events include timestamp (wall ms), thread, PID, app version, phase, elapsed ms, model label, CPU and operation UUID. Prompt, response and token text are never logged. Native logs are disabled because native messages may include private data. Technical UI details expose exception type/status/frames; load/session reasons are bounded with private paths/quoted values redacted. Generation raw messages are not echoed.

Events: `LITERT_IMPORT_OK`, `LITERT_CHAT_REQUEST`, `LITERT_VERIFY_START/OK`, `LITERT_CAPABILITIES_START/OK`, `LITERT_DEVICE`, `LITERT_MODEL_OPEN_START/OK`, `LITERT_JNI_INIT_START/OK`, `LITERT_SESSION_CREATE_START/OK`, `LITERT_PROMPT_FORMAT_START/OK`, `LITERT_PREFILL_START`, `LITERT_DECODE_SCHEDULED`, `LITERT_NATIVE_SEND_RETURNED`, `LITERT_FIRST_TOKEN`, `LITERT_DECODE_START`, `LITERT_TOKEN`, `LITERT_UI_FIRST_TOKEN`, `LITERT_EOS`, `LITERT_METRICS`, `LITERT_PREFILL_OK`, `LITERT_COMPLETED`, `LITERT_CANCEL_REQUESTED`, `LITERT_CANCEL`, `LITERT_CANCELLED`, `LITERT_ERROR`, timeout codes, `LITERT_UNLOAD_START`, `LITERT_UNLOAD`, `LITERT_WORKER_RESET`.

0.1.2 adds `LITERT_IPC_RECEIVED`, `LITERT_IPC_DISPATCH` and `LITERT_IPC_ERROR`, with numeric command/name and operation ID but no request contents. For a successful load dispatch, expect RECEIVED(LOAD=2) → DISPATCH(LOAD=2) → VERIFY_START, followed by the actual integrity/native stages. `appVersion` distinguishes the candidate from earlier APKs.

0.1.3 adds ENGINE_INITIALIZE_START/OK and LOAD_HEARTBEAT, with checkpoint and real observations delivered to the report. JNI_INIT_START/OK and CAPABILITIES_START/OK now update the client state as well as logcat. Heartbeats and integrity progress cannot extend the load/first-token deadline. Native raw logs remain disabled; report stack frames contain method names only, and never prompts or output.

0.1.4 adds SESSION_CLOSE_START and ENGINE_REUSE, and measured engineReused/prepareMs in the session/metrics result. Device observations remain available for cold and warm preparation. A reused verified file does not report a new full-hash progress measurement; changed file/configuration triggers the original streamed integrity/native load path.

LITERT_DEVICE records real RAM/storage/Android/ABI without extra permissions. The model card notes persistent CPU weight caches may need roughly another model-size of disk; the smoke test disables that cache.

TTFT uses a monotonic clock from native send dispatch to first nonempty output callback. Request/dispatch/first-output wall timestamps are recorded. Prefill/decode counts and rates come from native benchmark. Prefill duration is derived from native count/rate after completion. **Conversation has no prefill-complete callback**: `prefillCompletedAt` stays null. FIRST_TOKEN_TIMEOUT cannot distinguish prefill from first decode; PREFILL_OK is explicitly a post-completion benchmark observation, not a fabricated live boundary. `prefillStartedAt` records API dispatch, not an unexposed internal native timestamp.

LITERT_EOS means native **onDone** was observed. This API does not expose whether it stopped on an EOS token or the output limit; no specific finish reason is claimed.

## Exact manual phone test

1. Install the supplied **0.1.4 arm64 release APK as an update**. Its application ID/certificate match the previous Drive APK. Keep the installation and imported model. Debug builds have a separate `.debug` application ID. First perform the two normal-chat-turn [warm-engine test](LITERT_PHONE_VALIDATION.md); the smoke below intentionally reloads and does not measure reuse.
2. Select `Qwen3.5-2B_int8.litertlm` in the existing chat and tap **Run CPU smoke test**.
3. This bypasses RAG, memory, history, project/system instructions, images and tools. CPU, up to 4 threads, context 1024, output 32, no speculative decoding/thinking/channel parsing, no persistent weight cache. Exact user turn: `Responde únicamente con la palabra FUNCIONA`.
4. Observe load/session/prompt phases and at least one real output chunk. Record response and displayed loadMs, sessionMs, prefillMs, ttftMs, native output count, decodeTps, chunks, totalMs and completion callback. Blank output is failure. The smoke also unloads afterward.
5. Repeat. During another run tap **Stop**, then rerun without restarting the phone. Record cancel/retry/unload separately. Finally try `Hola` in normal chat.
6. On failure open **Technical details** on the failed message, including after reopening the chat. Record phase, **Last checkpoint**, elapsed time, mode/context/cache, RAM and workerStack. The four-minute LOAD limit is unchanged; record any observed late expiry as another physical failure. A control timeout requests worker reset and permits retry, subject to the phone test.

With USB debugging:

```bash
adb devices -l
adb install -r local-ai-workspace-0.1.4-litert-warm-engine-arm64.apk
adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' 'AndroidRuntime:E' '*:S' > litert-qwen35.log
```

Start capture before the smoke button; stop with Ctrl+C after completion/error. Do not filter by main PID: inference is another process. For suspected Android memory/process termination, optionally collect:

```bash
adb logcat -d -v threadtime 'ActivityManager:I' 'lmkd:I' '*:S' > litert-process.log
```

Share `litert-qwen35.log` and the technical error. The optional OS log may include unrelated app identifiers; inspect it before sharing.

## Instrumented smoke

`LiteRtLmDeviceSmokeTest` needs the exact model imported in the target **debug** app. It asserts native output, benchmark decode tokens >= 1, measured load/session/prefill/TTFT, completed streaming, cancellation, successful retry and unload. A second method tests normal cold/warm turns with updated sampler and unload/cold retry. Missing model means skipped, not PASS. Both methods compile; neither has run here.

```bash
./gradlew installDebug
# Import the model in the debug app, then leave chat idle.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.localai.workspace.inference.LiteRtLmDeviceSmokeTest
```

## Validation and touched files

| Check | State | Evidence |
| --- | --- | --- |
| 0.1.4 two-turn phone quality | FAIL | Second answer repeats in 24955/24956 |
| 0.1.5 unit/control/config tests | PASS | 60 tests; zero failures/errors/skips, previous 45 retained |
| 0.1.5 assembleDebug | PASS | Actual Gradle execution |
| 0.1.5 lintDebug | PASS_WITH_WARNINGS | Zero errors, 72 existing warnings |
| 0.1.5 instrumented APK build | PASS | Compilation/package only |
| 0.1.5 arm64 release + signature | PASS | Actual Gradle execution; same package/certificate, versionCode 6, v3 signature |
| 0.1.5 Drive delivery | PASS | Upload/readback confirms filename, 89,175,737 bytes, original parent and private sharing |
| 0.1.5 phone quality/performance/recovery | NOT_EXECUTED | No attached device/model |
| Original Qwen physical inference | FAIL | User's 0.1.0 phone result |
| 0.1.1 Qwen physical inference | FAIL | User's 5–8 ms error, Unknown LiteRT operation, before JNI |
| 0.1.2 Qwen physical inference | FAIL | MODEL_LOAD_TIMEOUT after 1,345,071 ms, no tokens |
| Latest user-supplied Qwen physical inference | PASS | Actual reply, output=10, chunks=9, TTFT=4816, onDone; after 0.1.3 delivery |
| 0.1.4 unit/control/lifecycle tests | PASS | 45 tests; zero failures/errors/skips; all preceding 37 retained |
| 0.1.4 assembleDebug | PASS | Actual Gradle run |
| 0.1.4 lintDebug | PASS_WITH_WARNINGS | Zero errors; same 72 warnings |
| 0.1.4 Android test APK | PASS | Compilation/package only |
| 0.1.4 arm64 release + signature | PASS | Actual Gradle build, v3, same package/certificate as 0.1.3 |
| 0.1.4 Drive delivery | PASS | Upload and metadata readback; name, 89,159,353 bytes, same parent and private sharing |
| 0.1.4 warm-engine physical speed | NOT_EXECUTED | Phone retest required |
| 0.1.3 unit/control tests | PASS | 37 tests; zero failures/errors/skips; all preceding 29 retained |
| 0.1.3 assembleDebug | PASS | Actual Gradle run |
| 0.1.3 lintDebug | PASS_WITH_WARNINGS | Zero errors; same 72 warnings |
| 0.1.3 Android test APK | PASS | Compilation/package only |
| 0.1.3 arm64 release + signature | PASS | Actual Gradle release build; v3 and same certificate verified |
| 0.1.3 Drive upload/readback | PASS | Name, 89,142,969 bytes, parent and private state verified |
| 0.1.2 candidate unit tests | PASS | 29 tests, zero failures/skips; prior 25 + 4 IPC tests |
| IPC regression sensitivity | PASS | Both LOAD/UNLOAD tests fail when the original deferred Message read is reintroduced; fixed code restored |
| 0.1.2 assembleDebug | PASS | Actual Gradle run |
| 0.1.2 lintDebug | PASS_WITH_WARNINGS | Zero errors, 72 warnings; details below |
| 0.1.2 assembleRelease arm64 + APK signature | PASS | Actual release build; v3 signature and same update certificate verified |
| 0.1.2 Drive delivery | PASS | Upload/readback verified filename, 89,110,201 bytes, parent folder and private sharing |
| 0.1.2 Android test APK | PASS | Compilation/build only, no device execution |
| Latest physical load, session, prefill, first token | PASS | User's successful reply and native metrics; Codex has no connected device |
| Latest physical streaming/onDone | PASS_WITH_WARNINGS | 9 output chunks and onDone; screenshots do not show progressive display or literal EOS reason |
| Physical cancel, retry, unload | NOT_EXECUTED | Not demonstrated by latest user captures |

0.1.3 created: `IndependentInferenceDeadline.kt`, `GenerationDiagnosticCodec.kt`, `IndependentInferenceDeadlineTest.kt`, `LiteRtRuntimeDeadlineTest.kt`, `GenerationDiagnosticPersistenceTest.kt`.

0.1.3 modified: `GenerationDiagnostics.kt`, `LiteRtLmInferenceRuntime.kt`, `LiteRtLmService.kt`, `MainActivity.kt`, `ui/ViewModels.kt`, manifest (bounded-operation WAKE_LOCK), app Gradle version, `LiteRtIpcDispatchTest.kt`, `LiteRtModelIntegrityTest.kt`, README/STATUS/architecture/changelog and this audit. SDK stays 0.17.1, CPU only; no model patch/conversion or migration. Subsequent user screenshots provide real output and callback metrics. Loader internals, real peak memory, physical cancel/retry/unload, broader background-service design and Kotlin/AAR metadata workaround remain debt. 0.1.4 implements engine reuse; unrelated work remains paused.

Created in 0.1.2: `app/src/test/java/com/localai/workspace/inference/LiteRtIpcDispatchTest.kt`.

Modified in 0.1.2: `LiteRtLmService.kt`, `LiteRtIpc.kt`, `LiteRtTrace.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, README, root/technical STATUS, CHANGELOG and this audit. No runtime upgrade, model conversion, new features, DB migration or destructive reset. The existing watchdog limits, error/timeout recovery, cancellation, CPU smoke and generation callback implementation are retained.

0.1.1 groundwork created: `GenerationDiagnostics.kt`, `LiteRtTrace.kt`, `LiteRtIpc.kt`, `LiteRtLmService.kt`, `InferenceWatchdogTest.kt`, `LiteRtModelIntegrityTest.kt`, `LiteRtLmDeviceSmokeTest.kt`, this audit.

0.1.1 groundwork modified: `LiteRtLmInferenceRuntime.kt`, `InferenceRuntime.kt`, `WorkspaceModels.kt`, `ViewModels.kt`, `MainActivity.kt`, `LocalAiApplication.kt`, `ModelImportService.kt`, `Daos.kt`, `WorkspaceRepository.kt`, Android manifest, app Gradle version, `ModelManagerDomainTest.kt`, README/STATUS/architecture/changelog. Room schema stays at version 2; no migration or destructive reset is needed. Existing GGUF and RAG/security/tool tests pass; real GGUF device regression remains unexecuted here.

Next gate: verify two normal warm/cold chat turns, then physical cancellation, retry and unload. GPU and unrelated feature work remain paused until recovery passes. See the [latest changed files and exact phone checklist](LITERT_REPETITION_FIX.md).

## Delivered artifact — 0.1.5

[Verified 0.1.5 APK in Drive](https://drive.google.com/file/d/1fAy73PAFPED3ziwslgeJ-CFXk0P5kU5a/view?usp=drivesdk). Local APK: `app/build/outputs/apk/release/local-ai-workspace-0.1.5-litert-chat-fix-arm64.apk`, **89,175,737 bytes**, package `com.localai.workspace`, versionCode **6**, versionName **0.1.5**, minimum API 29, target 35, arm64. Local SHA-256: `35d0d9ede678ab7fde6032f96a6da06d84e506d4b606db243c2afd9123b94031`.

Final combined debug/test/lint/instrumented-compilation run and `./gradlew assembleRelease -Parm64Only=true` passed. Signature verification confirms one v3 signer with unchanged certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Badging and ZIP/DEX checks confirm both native runtimes and the new structured-chat/repetition-stop/requestTTFT controls. Upload and metadata readback confirm filename, MIME, byte size, original parent and private sharing; this is not a remote SHA check. Models, Room schema 2 and prior APKs are preserved. Candidate device validation remains NOT_EXECUTED; detailed files, limitations and phone instructions are in the [two-turn audit](LITERT_REPETITION_FIX.md).

## Previous delivered artifact — 0.1.4

APK: `app/build/outputs/apk/release/local-ai-workspace-0.1.4-litert-warm-engine-arm64.apk` — **89,159,353 bytes**, package `com.localai.workspace`, versionCode **5**, versionName **0.1.4**, Android API 29 minimum, arm64. `apksigner verify --verbose --print-certs` confirms one v3 signer and the same certificate SHA-256 as 0.1.3: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. This remains the existing test distribution certificate, not a production signing key. Install as an update to preserve imported models and Room data.

Local APK SHA-256: `3ee2555240e8dbcab5febd03b7b80e98380845bab145d21e66ae00651dd7b0eb`.

[Verified APK 0.1.4 in Drive](https://drive.google.com/file/d/1xawAe0Or4rkgkXbpdHzL1lXwpELR38_e/view?usp=drivesdk). Upload and metadata readback confirm filename, MIME, byte size, original parent folder and private sharing. The local SHA is not represented as a server-side hash check. Previous APKs remain in Drive.

Badging confirms package/version/minimum Android/arm64/WAKE_LOCK. ZIP/DEX inspection confirms both LiteRT-LM and llama.cpp native libraries, LiteRtEngineResources, ENGINE_REUSE, SESSION_CLOSE_START, engineReused/prepareMs and the independent watchdog. Debug APK and instrumented test APK also built. Room schema stays 2.

Final executed commands: `./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest` (45 tests; no failures/errors/skips; build successful), then `./gradlew assembleRelease -Parm64Only=true` (build successful), signing, signature/badging/DEX checks, upload and metadata readback. No connected/native inference tests executed in this environment; `adb devices -l` is empty. User-supplied previous physical output is separately recorded as PASS; 0.1.4 warm-engine/recovery validation is NOT_EXECUTED. The [phone evidence record](LITERT_PHONE_VALIDATION.md) lists created/modified files, known limits, metrics and the exact retest.

## Previous delivered artifact — 0.1.3

APK: `app/build/outputs/apk/release/local-ai-workspace-0.1.3-litert-load-watchdog-arm64.apk` — **89,142,969 bytes**, package `com.localai.workspace`, versionCode **4**, versionName **0.1.3**, minimum Android API 29, arm64. Signed with the same local test keystore used for previous release APKs; this is a test distribution certificate, not a production signing key. `apksigner verify` confirms one signer and the v3 signature; certificate SHA-256 is `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Packaged native libraries include both `liblitertlm_jni.so` and llama.cpp. DEX inspection confirms the independent deadline class and engine-initialize checkpoint are present. Badging confirms version/package/ABI and the bounded-operation WAKE_LOCK permission.

SHA-256: `cfe06bed41ab39ec97973d33a347c69ae2f4fa6fa516c504974781c08b189ad5`.

[Verified 0.1.3 APK in Drive](https://drive.google.com/file/d/1fmDuYbOtqTtRid6bXXdYsquRbz1Uc12O/view?usp=drivesdk). Upload/readback confirmed filename, size, private sharing state and the same folder as earlier APKs. [Previous 0.1.2 APK](https://drive.google.com/file/d/1hnsrFqxyAfdxLPm4MtZvCPES2mTYlLPk/view?usp=drivesdk) was retained; its physical load timed out. Latest user screenshots after 0.1.3 delivery show real output and onDone; physical recovery remains pending.

Additional built artifacts: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. These are separate debug-app artifacts; the Drive update is the release-package APK.

Commands executed for 0.1.3: `./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest`, then `./gradlew assembleRelease -Parm64Only=true`. All passed, with 37 tests and zero failures/errors/skips. No connected tests or real model/native benchmarks were executed here. The initial new fixture compile referenced a static Robolectric wake-lock accessor incorrectly; it was corrected before the final test/build runs. JDK 21 remains the explicit host test launcher for the AAR descriptors; the Android source target stays Java 17.

Lint's 72 warnings comprise 60 dependency-version notices, 6 Android Gradle version notices, 3 TrustAllX509TrustManager findings inside the existing BouncyCastle PDF dependency, plus conditional arm64/ChromeOS packaging, data-extraction rules and missing application icon. No new trust manager was added; the inference fix does not change network/TLS behavior. The findings are retained and documented, not suppressed. Compiler warning remaining: the preexisting non-auto-mirrored Send icon.
