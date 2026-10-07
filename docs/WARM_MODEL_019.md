# Warm model across chats — 0.1.9

The 0.1.8 screenshots (25214/25218) show native CPU initialization at each project entry. The cause in code was ChatViewModel.onCleared unconditionally unloading the application singleton runtime and resetting its worker. Existing within-chat reuse could not survive navigation.

## Implementation

- AppGraph owns ChatRuntimePool: one retained backend, serialized native operations, and distinct revocable leases per ChatViewModel/backend. A closed lease cannot submit work. Cancellation only targets that lease's currently executing operation; stale unload/close cannot affect a replacement owner.
- Normal navigation closes the native conversation and invalidates its history/KV reuse tracker. It retains a healthy LiteRT engine and verified metadata. The next chat always receives a fresh conversation even when settings/history match. Room history, RAG evidence, memories and images remain scoped to their original project.
- RESET_CONVERSATION is an internal, non-exported Messenger operation, snapshotted before Message recycling and executed on the existing native worker. It calls the actual Conversation.close through LiteRtEngineResources and leaves Engine initialized. Existing deadlines/worker reset still handle a stuck cleanup. Logs: LITERT_SESSION_RESET_START/OK, LITERT_ENGINE_RETAINED, LITERT_ENGINE_REUSE and LITERT_SESSION_CREATE_OK. No prompt content is logged.
- Engine reuse still requires the same private canonical path, file size/modification time, stored SHA-256, context, thread count, modality/backend/version and a healthy initialized native handle. A changed model/configuration or worker death triggers real initialization. SDK/JNI remains 0.17.1, CPU only.
- Different runtime selection unloads the previous backend before load. GGUF uses the existing full unload fallback for conversation reset; this change does not claim cross-chat llama.cpp weight caching.
- Deleting the current chat cancels/joins preparation and generation, closes its native conversation/KV, then deletes its Room records. LiteRT weights can stay warm; the deleted conversation cannot remain in native session state. Room schema remains 2.
- The idle engine is evicted after 10 minutes with no chat owner, or on reported memory pressure when idle. Opening a chat cancels idle expiry. Android can reclaim the worker/process earlier, particularly under memory pressure; no guarantee of indefinite retention. Active chat/generation is not cancelled by idle eviction.
- Cancelling unfinished native initialization may still require worker reset. Smoke tests intentionally use fresh engine settings and unload afterward; they are not the normal-chat performance path.

## What this improves and what remains

It removes unnecessary repeated cold model loading between normal chats/projects while an unchanged healthy engine remains available. Initial cold loading still incurs integrity/native CPU initialization, and CPU prefill/decode speed is unchanged. No phone timing improvement, GPU support or semantic quality improvement is claimed. Prior answer relevance failure remains unresolved.

## Phone check — install as update, do not uninstall

1. Open a project with Qwen3.5-2B_int8.litertlm, wait for Model ready and send a short message. The first entry can still be slow.
2. Return to Workspace and immediately open/create another project using exactly the same model/settings. It should prepare a fresh session without repeating CPU engine initialization. Send a short message and inspect Details: engineReused=true, sessionReused=false for the new chat's first request (a later turn may reuse its own session).
3. Check conversation isolation with a unique phrase in project A, then project B: database history must be different; log session reset/new session boundaries. A model's answer alone is not a reliable test of private-state isolation.
4. Return during generation, reopen another chat, then Stop/retry; no old cancellation/cleanup should stop the new owner. Returning during an unfinished cold load may still force a reset.
5. Delete a chat, confirm and reopen: empty history, fresh session, healthy LiteRT weights may remain. Change model/context or wait over 10 idle minutes: a new load is expected.

Collect actual timing evidence and private-free runtime events with:

```bash
adb logcat -c
adb logcat -v threadtime 'LocalAI/LiteRT:I' 'LocalAI/Runtime:V' '*:S' > warm-model-019-logcat.txt
```

Host unit tests exercise real ownership/resource/IPC dispatch code with fake native handles, not real Android inference. Device validation remains NOT_EXECUTED until tested on the Moto G86 Power (8 GB total RAM).

## Executed validation

- `./gradlew testDebugUnitTest assembleDebug lintDebug --max-workers=2`: PASS, 7m10s, 24 tasks executed/135 up to date. 114 app tests executed, 0 failures/errors/skips; 2 unchanged compatibility tests up to date. New coverage: 9 ownership/lifecycle regressions and the real service RESET_CONVERSATION dispatch/recycle regression; existing engine-key/resource tests remain.
- Lint: PASS_WITH_WARNINGS, 0 errors/73 existing warnings (72 app, 1 compatibility).
- Initial attempt failed compilation of the new test fixture because required contextSize was omitted; corrected. The failed batch and a queued repeat were terminated to run one final build serially; logs retained.
- Android device/real new warm-navigation timings: NOT_EXECUTED, adb has no attached device.
- Final release/signature/native-hash/Drive evidence: [artifact manifest](validation/apk-019.json).

Release assembly PASS in3m4s, same-certificate signing and ARM64/package/version/DEX checks PASS. All8 native library hashes match0.1.8. Drive metadata readback PASS (91,420,601bytes, private sharing); server checksum not exposed. The previous0.1.8 APK remains intact in dist/apk.

[Download 0.1.9](https://drive.google.com/file/d/1uog6Eiy_sH97ThrnN6k681EmvtKfy9UE/view?usp=drivesdk).
