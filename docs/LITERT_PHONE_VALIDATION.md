# Physical Qwen evidence and warm-engine verification — 2026-10-03

Subsequent 0.1.4 phone evidence shows a repeating second reply. Basic output documented here remains PASS, but two-turn quality is FAIL. Use the [current 0.1.5 fix/retest](LITERT_REPETITION_FIX.md); its phone validation is NOT_EXECUTED.

## Latest phone result

The user's screenshots `24947.jpg` and `24948.jpg`, supplied after the 0.1.3 delivery, show the existing physical Android app using `Qwen3.5-2B_int8.litertlm` with runtime `litert-lm-android`. Normal chat input `hola` produced **Hola! ¿En qué puedo ayudarte hoy?**. This is real user-supplied device evidence, not a host simulation or a connected test run by Codex. The screenshots do not show a version number or phone hardware details. They show normal chat, not execution of the fixed FUNCIONA smoke action.

**LITERT_BASIC_DEVICE_INFERENCE = PASS.** The earlier no-token failures remain historical failures, not the current basic-inference status. Full acceptance is **PASS_WITH_WARNINGS**: physical cancellation, retry after cancellation, unload and warm-engine performance still need specific tests.

| Observation | Actual value | Boundary |
| --- | --- | --- |
| Engine load | 49,516 ms | Engine construction and initialize; excludes file verification |
| Conversation creation | 70 ms | Native createConversation |
| Prefill | 3,442 ms, 83 tokens, 24.11 tok/s | Duration derived from native benchmark, not a live phase callback |
| TTFT | 4,816 ms | Generation dispatch to first output, excludes engine load |
| Decode | 10 tokens, 3.17 tok/s | Native benchmark count/rate |
| Streaming callbacks | 9 nonempty chunks | Chunks are not tokenizer tokens |
| Generation total | 6,638 ms | Includes prefill/decode, excludes load |
| Completion | eos=true | Native onDone, not a specific EOS-versus-token-limit finish reason |

The reported approximately one minute matches 49.5s of engine load plus 6.6s of generation, with integrity and app preparation also outside those two numbers. Screenshot 24947 shows Integrity 100% at an elapsed 6s; that is not a separate measured hashing benchmark. No claim is made about internal CPU kernel preparation, model-wide compatibility, GPU, NPU or vision.

| Physical criterion | Status | Evidence or remaining test |
| --- | --- | --- |
| MODEL LOAD | PASS | Measured load and actual reply |
| SESSION | PASS | sessionMs=70 and actual reply |
| PREFILL | PASS | Native benchmark and successful generation; exact completion timestamp unavailable |
| FIRST TOKEN | PASS | Actual text, output=10 and TTFT=4816 |
| STREAMING | PASS_WITH_WARNINGS | 9 native output chunks; screenshots do not show progressive display of every chunk |
| Completion / EOS | PASS_WITH_WARNINGS | onDone observed; literal native finish reason unavailable |
| CANCEL | NOT_EXECUTED | Not demonstrated by these captures |
| RETRY after cancel/error | NOT_EXECUTED | Not demonstrated within the same installed build |
| UNLOAD | NOT_EXECUTED | Not demonstrated by these captures |
| 0.1.4 warm-engine device validation | NOT_EXECUTED | No device attached to this workspace |

## 0.1.4 performance correction

Confirmed source-level cost: ChatViewModel calls loadWithFallback for every turn. The previous LiteRT service closed both Conversation and Engine on every LOAD, repeated file hashing and initialized the same model again, even after a successful normal chat. Keeping the worker alone did not avoid this reload.

The service now uses LiteRtEngineResources to retain one initialized CPU Engine. Reuse requires the same canonical private model path, byte size, modification time, recorded SHA-256, expected size, context, CPU threads, vision setting, cache directory and runtime version. A recorded valid SHA must already have been checked on the cold path. File stat/config changes invalidate reuse. Native initialization failures never store a reusable key. Explicit unload, model/runtime switching, timeout/reset and generation errors release the engine; a later request loads again. Diagnostic smoke always forces cold initialization with disabled persistent cache.

Every LOAD closes the old Conversation and creates a fresh one, even when retaining the Engine. LiteRT's 0.17.1 Engine.createConversation API explicitly supports this separation. The full app prompt already contains budgeted history, evidence and memory; retaining its native conversation history would duplicate context. Updated sampler/output settings are applied to the new conversation. Prompts, RAG, memory, citation resolution and GGUF behavior are retained.

`engineReused=true` means initialization actually was skipped. `prepareMs` measures this turn's service preparation, including verification/initialization when needed and new session creation. A warm turn has no `loadMs`: an old cold timing or a fabricated zero is not reported. TTFT keeps its existing generation-only definition. Initial cold load still costs what the device requires; improvement on subsequent turns is not measured until the phone retest.

Logs add **LITERT_SESSION_CLOSE_START** and **LITERT_ENGINE_REUSE** under `LocalAI/LiteRT`. Session metrics include measured prepareMs/sessionMs and engineReused. Watchdogs, bounded wake leases, native cancellation/reset and private diagnostic redaction remain active.

## Exact next phone test

1. Install the supplied **0.1.4 release APK as an update**, keeping the existing installation and imported model. Package and certificate match 0.1.3.
2. Select Qwen in normal chat and send `hola`. Initial load can still take approximately the previously observed minute. Record engineReused=false, loadMs and prepareMs.
3. Stay in that chat with unchanged model/context/backend and send `Responde únicamente: funciona`. Expect **engineReused=true**, a fresh session and no loadMs. Record prepareMs, ttftMs, totalMs and visible text. Do not promise the same latency as the first short turn: history/prefill and device state can differ.
4. Test Stop during a longer response, then retry in the same app. Cancellation must reach native processing or the independent reset deadline; actual output must work on retry. A reset may require another cold load.
5. Leave the chat to exercise unload, reopen it and repeat. Changing context/model/image-backend configuration must require cold initialization. **Run CPU smoke test** is intentionally cold and unloads afterward; it is not the warm-turn performance test.
6. On failure open Technical details. Capture stage, code, last checkpoint and elapsed time. The existing 240s load and 5s cancellation deadlines remain unchanged.

```bash
adb install -r local-ai-workspace-0.1.4-litert-warm-engine-arm64.apk
adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' 'AndroidRuntime:E' '*:S' > litert-qwen-warm.log
```

Begin log capture before sending the first message. Expect ENGINE_INITIALIZE_START/OK for the first request, then SESSION_CLOSE_START → ENGINE_REUSE → SESSION_CREATE_START/OK for the next. Do not filter to the main PID; inference runs in :litert. No prompts or token text are logged.

Instrumented LiteRtLmDeviceSmokeTest now includes a real cold → warm with changed sampler → unload → cold test alongside first-token/cancel/retry smoke. Import the exact model in the target **debug** app before running connectedDebugAndroidTest; absent model means skipped, not PASS. Both methods compile but were not run here: adb reports no devices.

## Automated evidence and changed files

45 JVM tests PASS, zero failures/errors/skips: all 37 preceding tests retained, plus 7 resource-lifecycle tests and 1 IPC metric round-trip. The lifecycle tests verify one initialization across turns, fresh history/settings, config/file invalidation, always-cold smoke, missing hash, unload/lost handle and cleanup/retry after initialization/session failure. They use resource fixtures and do not execute model inference.

Final `./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest` PASS, with 72 existing lint warnings and zero errors. `./gradlew assembleRelease -Parm64Only=true` PASS. Instrumented test APK compilation is not device execution. [APK 0.1.4 in Drive](https://drive.google.com/file/d/1xawAe0Or4rkgkXbpdHzL1lXwpELR38_e/view?usp=drivesdk); upload/readback verified filename, size, private sharing and parent. See the [runtime audit](LITERT_DIAGNOSTICS.md) for local SHA/path and signing verification.

Created: LiteRtEngineResources.kt, LiteRtEngineResourcesTest.kt, this record. Modified: LiteRtLmService.kt, LiteRtIpc.kt, WorkspaceModels.kt (nullable runtime metrics), ViewModels.kt (metrics display), app/build.gradle.kts (versionCode 5 / 0.1.4), LiteRtIpcDispatchTest.kt, LiteRtLmDeviceSmokeTest.kt, README, root/technical STATUS, ARCHITECTURE, CHANGELOG and the audit. Room schema stays 2; no migration or destructive reset. SDK stays 0.17.1 and CPU only. Native loader internals, cold-load tuning, physical recovery checks, GPU/NPU and broader background lifecycle handling remain debt. Unrelated feature work remains paused.
