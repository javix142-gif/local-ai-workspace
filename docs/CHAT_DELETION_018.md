# Chat deletion — 0.1.8 / 2026-10-05

Workspace lists projects; this version adds **⋮ → Delete chat** to each project card and to the chat toolbar. Both require an explicit confirmation, with Cancel and Delete. The project card remains available so files, sources, saved memory and model preferences are retained. Opening the project again starts an empty chat; this is not project deletion.

Deletion removes every conversation belonging to the selected project, its messages (including stored response diagnostics/metrics), citation evidence and tool-call history in one Room transaction. Model benchmarks, documents/segments, embeddings, models and independently saved memory remain. Schema version stays 2; no migration or destructive recreation is needed.

Deleting from an open chat blocks new sends/model changes, cancels preparation and generation, waits for their cleanup, unloads the native model/session/KV and then deletes the rows. Navigation returns to Workspace only after success. Failures surface as a snackbar and can be retried. Concurrent repeated clicks are ignored. From Workspace the previous chat screen has been closed; its existing onCleared cleanup cancels/unloads the runtime. Late message insertions are rejected and late citations are dropped transactionally if the old conversation no longer exists. Replacement conversations get new IDs, preserving any existing legacy ID until deletion.

Seven new Room/Robolectric tests cover child-row removal, preservation of another project/files/memory/models/settings, reopening legacy chats with a fresh ID, idempotent/missing-project deletion, late generation writes, rollback on simulated storage failure, and concurrent opening without duplicate conversations.

Build/test/signature/Drive evidence is recorded under `docs/validation/chat-delete-018` and `docs/validation/apk-018.json`. Final checks executed104app tests (including7new tests),0failures/errors/skips;2unchanged compatibility tests remained up to date after the initial cache restore. Final combined debug/test/lint build passed in1m45s; ARM64 release passed in1m59s. Lint has0errors/73existing warnings. Signature/package/version checks and all8native hashes against the previously verified0.1.7 manifest pass. APK91,387,833bytes, SHA-256`e280c232be2dfc7ce90c9c5e6e2c6a213cf9b48ab76475eedea448e2801eb628`; [Drive0.1.8](https://drive.google.com/file/d/1odrfBn_RHILGYj7Gvm8H6sPqVcfk10b3/view?usp=drivesdk) metadata confirms name/size/MIME/private sharing/parent. Server checksum is unavailable.

Distribution APKs now live in `dist/apk`, outside Gradle's packaging output directory. The initial clean rebuild took21m46s after the prior storage cleanup. Release packaging automatically removed the old local0.1.6/0.1.7 APKs in `app/build/outputs/apk/release`; their original Drive files remain. Raw-file fetch succeeded, but local re-download was blocked with HTTP403, so previous local artifact restoration is BLOCKED_EXTERNAL. No source, SDK, model binary or prior validation report was removed. Native comparison uses the retained, previously verified hashes; the six rebuilt llama libraries were also compared directly with0.1.7 before its local packaging-directory copy was removed.

No device is attached, so actual phone UI/native cancellation verification remains **NOT_EXECUTED**. No inference SDK, native algorithm, prompt policy, accelerator, model file or sampler changes are included; the previously recorded answer-relevance limitations remain.

## Phone verification

1. Install the same-certificate 0.1.8 update without uninstalling or clearing data.
2. In Workspace, open a project's ⋮ menu and choose Delete chat. Cancel first: messages must remain.
3. Repeat and confirm Delete: reopening must show an empty conversation, while imported files/memory/default model remain. A second project must keep its messages.
4. Start a reply, use ⋮ → Delete chat inside the chat, and confirm. Wait for cleanup and return to Workspace. Reopen and send a new prompt; no old chat messages should reappear.
5. Close/reopen the app and confirm the deleted messages are still absent. This deletes saved chat records, not independently saved memory or model knowledge; no filesystem forensic-erasure claim is made.
