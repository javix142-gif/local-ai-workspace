# Application model preparation — 0.1.11

The supplied 25243–25247 captures show repeated cold preparation when choosing between Qwen and Gemma and entering chats. The previous code prepared only on chat entry, cancelled that screen's preparation on clearing its ViewModel, selected the newest library entry when a new owner had no preference, and expired idle weights after ten minutes. The native engine already reused an exact unchanged engine; it owned only one engine, so changing models still invalidated it. These are verified code paths, not a measured attribution of every captured device wait.

## Current behavior and ownership

- Application startup constructs one `ApplicationModelPreparation` and begins real CPU preparation of the last selected usable LiteRT model. A missing selection falls back to a usable library entry; partial/corrupt/importing records are not preload targets. No prompt is generated, no network call is involved, and no second model is preloaded.
- Workspace now has an **Active model** selector, real loading/integrity/session progress, ready state, cancel, retry and expandable actual error details. Status is shared with the matching chat. The selected ID is persisted in private ordinary preferences, independently of project-specific model preferences. New chats inherit the last app selection; existing project preferences remain honored.
- Preparation uses an application-owned pool preloader with no screen lease. Opening/closing a chat, cancelling its waiter or deleting its conversation cannot cancel this shared job. Explicit Cancel loading and selecting another model still cancel native preparation and await teardown. Backend deadlines/errors remain active.
- A real chat generation obtains a revocable lease through the existing serialized pool. It waits for preparation, then loads its actual bounded prompt/config and reuses the backend's exact verified engine when possible. Handover discards prior conversation/KV state. No previous project's prompt/history is shared with a new chat.
- The application's idle engine has no arbitrary ten-minute timer. Reported memory pressure still releases an idle engine and invalidates the ready badge. Android can reclaim the native worker/app; retry then needs a load. GGUF retains its existing first-send preparation and safe unload path; this version does not claim cross-chat GGUF weight retention.

## Integrity and changing models

The LiteRT worker now keeps at most eight successful SHA verification receipts. A receipt includes canonical path, actual size, millisecond mtime, inode/device, change-time seconds, expected size/hash and actual format version. Every load still verifies readable header/format/size; changes to identity/attributes/hash invalidate reuse. If stat is unavailable or the expected hash is absent/malformed, reuse is disabled. A changed file during verification, corruption, hash failure or cancelled verification cannot produce a receipt. Receipts exist only for that worker lifetime, and are not written to Room or trusted after restarting the worker.

This is a cache for app-private files with app-controlled atomic imports/replacements, not proof against arbitrary privileged in-place tampering that preserves every recorded attribute. It never reads a whole model into RAM. The log `LITERT_INTEGRITY_REUSE` means an earlier successful SHA verification was reused for the unchanged private file, not that a new hash scan occurred. Existing `LITERT_VERIFY_OK`/engine/session/error/watchdog logs remain available under `LocalAI/LiteRT`.

One native engine remains resident. Switching Qwen ↔ Gemma still initializes the other model, but an unchanged model's hash can reuse its receipt while the worker survives. Returning to Workspace/new chat with the same model does not start another native preparation when the shared configuration is already ready/running. Context/thread/vision changes may require a different engine; first startup, actual worker failure/reset, memory eviction and model file changes can require verification/loading again.

The phone has 8GB total RAM; the captures show 4.4GiB of model files. These file sizes are not measured runtime RAM requirements, and available Android memory is unverified. Two simultaneously initialized engines would require additional weights, KV caches and runtime buffers. This version deliberately retains one model and does not claim both models can remain resident or that CPU decoding is faster. The previous answer-relevance failure is also unresolved.

## Data and retained features

Room remains schema3: no new DB migration, no destructive migration and no model/chat/project deletion. Workspace separation and confirmed deletion, private-file cleanup outbox, model library, existing GGUF, RAG/citations, memory, tools, imports/downloads and image flow are retained. LiteRT0.17.1 CPU/native libraries and embedded model templates remain unchanged. A lightweight model-ID preference is added; it contains no prompts, HF token or private conversation content.

## Executed validation

- Corrected debug batch: `./gradlew testDebugUnitTest assembleDebug lintDebug --max-workers=2`, PASS in48s. All146app tests actually executed, zero failures/errors/skips; 2unchanged compatibility tests UP-TO-DATE. Lint PASS_WITH_WARNINGS: 0errors,73pre-existing warnings (72app+1compatibility).
- 17new regressions: five real Room/preferences startup/selection/settings tests, five coroutine/pool ownership/cancellation/retention tests and seven integrity receipt invalidation/failure/bounding tests. Controlled backends in these tests are not native generation or phone benchmarks.
- First compile found an inferred-type recursion in AppGraph callbacks; explicit property types corrected it. Initial test fixtures gave both models the same unique hash, replacing Qwen with Gemma, and one test awaited queued cleanup before releasing its simulated load. Corrected fixtures/async test ordering; failed logs/XML are retained, followed by the full successful suite.
- Physical Android UI/startup/latency/native runtime validation: **NOT_EXECUTED**, no attached adb device. No new device speedup or Gemma inference PASS is claimed.
- Release `assembleRelease -Parm64Only=true --max-workers=2`: PASS in1m15s,23executed tasks/115up to date. Same certificate/package/version/minSdk/ABI/feature DEX markers and all8native hashes vs0.1.10 PASS.
- [Signed0.1.11 APK in Drive](https://drive.google.com/file/d/1xOJPIKlK9cA4csUT-yphfqR9a9ijf1u_/view?usp=drivesdk),91,584,441bytes, SHA-256 `95ff736a2ba37bf4b2de0c35d9be3eecfdf4d1f23cff5518ccbacf64ff0494fb`. Drive metadata readback PASS; provider checksum unavailable. [Artifact manifest](validation/apk-011.json).

## Phone verification

Install as an update without uninstalling or clearing data. Keep existing models/projects/chats.

1. In Workspace select Qwen. Confirm progress appears there, and wait for Model ready. Close/open several chats with Qwen: no full integrity scan or engine initialization should recur. Native session creation for an independent chat is expected.
2. Begin loading from Workspace and immediately enter/leave a chat. The Workspace load must continue. Cancel loading explicitly, then Retry loading; errors must show a recoverable diagnostic.
3. Restart the app: Qwen should be selected and load once from Workspace. Restarting the app/worker may require a cold load; this does not constitute a warm-navigation result.
4. Choose Gemma. Expect one engine initialization for Gemma. Return to Qwen: initialization is expected because only one engine is retained; if the worker and file are unchanged the `LITERT_INTEGRITY_REUSE` event should replace a full hash scan.
5. Compare actual request-to-first-token/load timings in Diagnostics for cold and warm cases. Check native `LITERT_ENGINE_REUSE`, `LITERT_MODEL_OPEN_START`, `LITERT_INTEGRITY_REUSE`, `LITERT_WORKER_RESET` and errors. Do not infer CPU decode improvement from faster preparation.

For logs: `adb logcat -v threadtime 'LocalAI/LiteRT:V' 'LocalAI/Runtime:V' '*:S'`. Do not share unrelated logcat output containing private app content. Model weights/engine handles cannot survive Android process destruction permanently.
