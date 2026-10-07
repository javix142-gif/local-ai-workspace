# LiteRT two-turn repetition and latency — 0.1.5 / 2026-10-03

Historical delivery record. Subsequent user screenshots 24959/24960/24958 show **0.1.5 MULTI_TURN_QUALITY = FAIL**, despite recovery to a visible repetition error. The SDK model-history role below is superseded by the verified metadata/native-role correction and CPU reference testing in [0.1.6 stabilization](LITERT_CPU_STABILITY_016.md). Earlier eos=true meant onDone, not an observed EOS token. Keep the original build evidence separate from this later physical failure.

## Physical evidence and confirmed source defects

The user supplied 24955.jpg and 24956.jpg after installing 0.1.4. Normal CPU chat with Qwen3.5-2B_int8.litertlm produced a good first response to hola, then repeated the same substantial passage while answering como estas. The second reply remained Generating in the screenshots. **0.1.4 MULTI_TURN_QUALITY = FAIL.** Real basic inference remains proven; this is a generation-quality/recovery problem, not evidence of zero native tokens.

First-turn metrics in that capture: engineReused=false, prepareMs=77934, loadMs=72902, sessionMs=8, prefillMs=3532, prompt=83, output=10, ttftMs=4999, prefillTps=23.50, decodeTps=3.19, totalMs=6684, chunks=9, eos=true. The approximately 84.6s service preparation + generation is dominated by cold CPU initialization. TTFT=4999 excludes the 77.9s preparation. The capture does not show the second turn's final engineReused/timings, so warm latency cannot be claimed. Internal native initialization cost, phone hardware/RAM/thermal state and cache behavior need device evidence.

Confirmed code defects:

1. ChatViewModel inserted the new USER row, then fetched recent history, so that user text appeared both in history and USER MESSAGE.
2. Both USER and ASSISTANT history were concatenated into one text and sent as Message.user. LiteRT never received actual prior model turns. The 0.1.4 engine-reuse change kept this faulty prompt construction.
3. History excluded only GENERATING records, allowing FAILED diagnostics, cancelled partial answers and completed old loops into subsequent prompts.
4. Context allocation used declared/default context and a fixed output reserve rather than the configured runtime context/output limit.
5. Any received text extended decode-idle deadlines, allowing repeated text to continue toward the generous total/output cap. Normal cancellation also unloaded the engine, imposing another cold load on retry.

The exact 0.17.1 Conversation API explicitly documents callback message chunks. JniMessageCallbackImpl forwards those chunks. No evidence supports changing them into prefix-deduplicated/cumulative text. The app continues appending real deltas. These prompt/control defects are independently reproduced by host tests; without the phone/model, they cannot be established as the sole cause of native model repetition.

## Correction

ConversationPrompt and ChatMessage are runtime-neutral optional input. The app reads previous history before inserting the current turn, keeps completed USER/ASSISTANT pairs and excludes historical repeated outputs from model context while preserving all Room records. The existing context allocator decides what history/evidence fits; a dropped history item is not silently restored. Missing current user after budget allocation is an explicit recoverable error.

LiteRtConversationPayload validates bounded role/content lists. It creates actual official Message.user / Message.model entries in ConversationConfig.initialMessages. Only the current user text plus budgeted data context is passed to sendMessageAsync. Trusted app/project policy goes into systemInstruction; memories/evidence retain their data labels and evidence IDs in user context. The model's embedded template is used, with no handwritten Qwen template or double-wrapped rendered prompt. GGUF continues using the existing bounded text fallback; its adapter/native binding was not replaced.

Ordinary structured chat explicitly disables thinking through the real ThinkingConfig, enable_thinking template context and channel configuration, giving a final text answer by default. Capability metadata remains independent. An explicit domain enableThinking=true still uses actual SDK configuration and requires declared model support; no new UI toggle or unsupported capability is advertised. Legacy direct SDK input defaults remain available; diagnostic smoke stays minimal.

RepetitionLoopDetector observes actual deltas across callback boundaries. Three consecutive identical blocks of 48–512 characters with at least six words trigger LITERT_REPETITION_DETECTED. The adapter requests Conversation.cancelProcess on the separate cancellation executor and waits for native onDone/onError. Further chunks are not forwarded after the stop request. The independent client watchdog gives this stop a five-second limit; an unresponsive native worker is killed/reset. The result is **REPETITION_LOOP Error, never Completed**. Partial text is retained with a visible interruption reason and expandable diagnostics; the failed pair is excluded from the next model history. There is no UI-only fake completion.

After a clean onDone stop and successful old-conversation teardown, the engine may remain initialized for quick retry. No native error may have occurred. The cancelled conversation/KV state is always discarded. A native error, absent acknowledgement, cleanup error, process reset, configuration change or explicit unload retains the cold-load path. Service terminal IPC reports engineRetained; the client/ViewModel honors it only for these acknowledged stops. Diagnostic smoke still unloads. Leaving the chat retains the existing explicit unload behavior.

Native ttftMs keeps its generation-dispatch definition. New **requestTtftMs** measures from the UI's send request to its first received visible output, including Room/context/retrieval, model integrity, load and session preparation. It is nullable when no output arrives; no estimated speedup or invented native metric is substituted.

## Validation

| Check | Status | Evidence |
| --- | --- | --- |
| Prior 0.1.4 basic phone inference | PASS | Actual first reply, 10 native decode tokens |
| Prior 0.1.4 two-turn quality | FAIL | User's repeated second reply in 24955/24956 |
| 0.1.5 host tests | PASS | 60 tests, zero failures/errors/skips; all prior 45 retained |
| 0.1.5 assembleDebug | PASS | Actual Gradle execution |
| 0.1.5 lintDebug | PASS_WITH_WARNINGS | Zero errors, same 72 existing warnings |
| 0.1.5 instrumented test APK | PASS | Compilation/package only |
| 0.1.5 arm64 release / signature | PASS | Actual Gradle release build, same certificate, v3 signature |
| 0.1.5 Drive delivery | PASS | Upload and metadata readback; filename, MIME, byte size, parent and private sharing |
| 0.1.5 phone quality/performance/cancel/retry | NOT_EXECUTED | adb devices empty; no native model/device in workspace |

Executed: ./gradlew testDebugUnitTest assembleDebug lintDebug :app:assembleDebugAndroidTest. Fifteen new regressions cover exact screenshot-loop chunking, non-loop text, completed-pair/current-user selection, old failed/cancelled/looped history, role-like user text and evidence authority, budget exclusion, actual SDK initial roles/thinking flags, malformed payloads, deep list snapshots, automatic stop without native acknowledgement, retained-engine acknowledgement/manual Stop, and session-only teardown.

IPC/control fixtures do not run inference. The actual device test now exercises **hola → como estas** with real prior USER/MODEL messages, requires real tokens and completion below the greeting's cap, asserts no repeated blocks, checks warm engine reuse, unload/cold retry and the separate cancel/retry smoke. It compiles but was not executed; missing model is skipped, not PASS.

## Delivered artifact

[Verified 0.1.5 APK in Drive](https://drive.google.com/file/d/1fAy73PAFPED3ziwslgeJ-CFXk0P5kU5a/view?usp=drivesdk). Local path: `app/build/outputs/apk/release/local-ai-workspace-0.1.5-litert-chat-fix-arm64.apk`. Size: **89,175,737 bytes**. Package: `com.localai.workspace`, versionCode **6**, versionName **0.1.5**, minimum Android API 29, target 35, arm64 only.

Local APK SHA-256: `35d0d9ede678ab7fde6032f96a6da06d84e506d4b606db243c2afd9123b94031`. Signature verification confirms one v3 signer with the same certificate as the preceding updates: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. This is the existing test distribution certificate. Install as an update; no model reimport or database reset is required.

Executed `./gradlew assembleRelease -Parm64Only=true` successfully after the combined debug checks, then signing, `apksigner verify --verbose --print-certs`, badging and ZIP/DEX inspection. The signed artifact includes both LiteRT-LM and llama.cpp native libraries, structured conversation classes, repetition/native-stop events, independent watchdog, engine-retention/reuse flags and requestTtftMs. Drive metadata readback confirms the exact name, MIME, byte size, original parent folder and private sharing. The local SHA is not claimed as a server-side hash check. Previous APKs remain in Drive.

## Exact next phone test

1. Install 0.1.5 as an update, preserving the model and existing installation. Open the same model/chat in CPU mode. Old failed/looped responses remain visible but are excluded from model history.
2. Send hola once. Cold load may still take about the previously observed minute; this build does not claim to remove CPU initialization cost. Record prepareMs, loadMs, requestTtftMs and response.
3. Stay in that chat, keep model/settings unchanged and send como estas. Expected: one brief coherent answer and completion, engineReused=true and no new loadMs. Record requestTtftMs, ttftMs, output, totalMs and decodeTps.
4. Send another ordinary question. Verify that the current user appears once and previous failed repetitions are not followed/imitated.
5. During a longer generation tap Stop, then retry without restarting. Native stop must acknowledge or reset within five seconds; engine retention is allowed only after clean acknowledgement. If repetition recurs, it must become a visible interrupted/error state and Ready to retry, not endless Generating.
6. Leave/reopen chat to check unload; a subsequent cold load is expected. The CPU smoke action intentionally does a cold load and unloads afterward, so it is not the warm latency test.

```bash
adb install -r local-ai-workspace-0.1.5-litert-chat-fix-arm64.apk
adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' 'AndroidRuntime:E' '*:S' > litert-two-turn.log
```

Capture from before the first send until the second completes/errors. Expect SESSION_CREATE_OK historyTurns=0 then 2, thinkingEnabled=false, ENGINE_REUSE on the second unchanged request, and FIRST_TOKEN/UI_FIRST_TOKEN/completion. If a loop occurs, expect REPETITION_DETECTED, REPETITION_CANCEL_SENT and NATIVE_STOPPED/error, or a deadline reset. No prompts, full response or token text are logged.

## Files, retained scope and limits

Created: domain/model/ConversationPrompt.kt; domain/inference/RepetitionLoopDetector.kt; data/ChatHistoryBuilder.kt; inference/LiteRtConversationPayload.kt; tests ConversationPromptTest.kt and LiteRtConversationPayloadTest.kt; this document.

Modified: WorkspaceModels.kt (optional prompt/metrics), ContextBudgetManager.kt (structured history field), ViewModels.kt, LiteRtLmService.kt, LiteRtLmInferenceRuntime.kt, LiteRtEngineResources.kt, LiteRtIpc.kt, app/build.gradle.kts (versionCode 6 / 0.1.5), LiteRtIpcDispatchTest.kt, LiteRtRuntimeDeadlineTest.kt, LiteRtEngineResourcesTest.kt, LiteRtLmDeviceSmokeTest.kt and README/STATUS/architecture/changelog/audit.

Room remains schema 2 with no migration/reset. Existing models, messages, RAG, citation verification, memory and tools are retained. SDK is still 0.17.1, CPU only. No GPU/NPU, model conversion/patch or unrelated feature was added. The conservative exact-block guard can interrupt deliberately repeated long passages; varying/short loops may escape it. It reports interruption rather than claiming a normal answer. Cold initialization, full hardware/thermal profiling, native-internal diagnostics, background/process lifecycle and Kotlin/AAR metadata compatibility remain debt. No measured phone latency improvement or full LiteRT acceptance PASS is claimed until retesting.
