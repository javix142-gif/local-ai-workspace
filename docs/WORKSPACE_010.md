# Workspace: direct chats and projects — 0.1.10

## User behavior

Workspace now leads with Start chat (direct, no project-creation dialog) and Start project (name, then project screen). Separate Chats and Projects lists have explicit confirmed deletion actions. Direct chats acquire a title from their first user message. A project can contain several independently addressed conversations plus shared local files and project memory. Its screen supports Start chat, Add file, Memory, chat reopening/deletion and Delete project. Imported files use the existing real SAF/private-copy/local-index pipeline.

- Delete standalone chat removes its card, messages, citations, tool history, attached private copies/index/embeddings and its local memory.
- Delete project removes its card and all of its conversations and owned resources.
- Delete one chat inside a project removes only that conversation/messages/citations/tool history; sibling chats, shared project files and memory remain.
- Installed models/settings/benchmarks, global memory and other workspaces remain. Original user source files outside the app are never deleted. Confirmation dialogs describe these different scopes.
- Chat deletion from the open chat still stops/joins native preparation/generation and closes its conversation/KV before deleting persisted history. Healthy LiteRT weights can remain warm through the0.1.9 pool. Runtime/SDK/native library code is unchanged in this iteration.

## Data and migration

Room schema3 adds projects.workspaceKind with SQL default PROJECT and pending_document_deletions(localPath primary key). The projects table remains an internal resource-ownership namespace: CHAT denotes an isolated standalone conversation, PROJECT denotes a collection with shared resources. A standalone chat is never presented as a project in the UI. Conversation selection uses explicit project/conversation IDs; cross-project IDs and deleted owners are rejected. Existing project IDs/names/chats/files/preferences/models are preserved as PROJECT by MIGRATION_2_3; version1 retains the existing1→2 migration then2→3. No destructive migration.

The old application startup automatically created Personal workspace if it did not exist. Startup now only retries pending private-file cleanup, so deleting that project cannot recreate it on relaunch. Existing Personal workspace remains until the user deletes it.

Deletion is one Room transaction: enqueue owned private document paths, remove citations/tools/messages/conversations, embeddings, segments (including Room external-content FTS triggers), documents, project-local memory and owner row. A failure rolls back both data deletion and the cleanup outbox. File deletion follows commit and is constrained to canonical paths under filesDir/documents; models, source originals, directories and escaping symlink targets are excluded. Files still referenced by another workspace are retained. Missing files count as already cleaned.

Failed cleanup remains in a durable outbox and is retried on launch or Workspace → Retry cleanup, with a visible pending count. Imports check that their owner still exists before row insertion and before writing their index; deletion during streaming copy cannot recreate data, and unregistered partial copies are cleaned/queued. Models never enter this cleanup queue.

## Validation and phone checks

Automated tests exercise real Room transactions, actual2→3 database opening/schema validation, private filesystem cleanup, FTS/embedding removal, isolation/rollback/outbox/symlink handling, import-vs-delete races and the retained runtime pool. Fake native resource tests do not constitute Android inference validation.

Install as an update, without uninstalling or clearing app data:

1. Confirm existing projects/history/models remain. Start chat must immediately open a direct chat and produce a new Chats entry after returning.
2. Start project, create two chats, reopen each and verify separate message histories. Add a text/PDF document; both project chats can access the same project source through existing local retrieval.
3. Delete one project chat: its card/messages disappear while its sibling, files and project memory remain.
4. Delete a standalone chat: its Chats card disappears. Delete a project from its card or project screen: its Projects card disappears with all owned chats/files/memory. Confirm installed models and unrelated work remain.
5. Close/reopen the app: deleted projects, including Personal workspace, must stay deleted. If a private file could not be cleaned, use the visible Retry cleanup action.
6. Repeat normal LiteRT navigation using the same model/settings to check0.1.9 engine reuse still works. Actual Android UI, native lifecycle and timing checks remain NOT_EXECUTED here because no device is attached.

Build/test/lint/signature/Drive results are recorded in [the artifact manifest](validation/apk-010.json).

## Executed checks

- Final delivery `./gradlew testDebugUnitTest assembleDebug lintDebug --max-workers=2`: PASS in1m42s,22tasks executed/137up to date. All129app tests executed,0failures/errors/skips;2unchanged compatibility tests up to date. Includes15new regressions:11workspace lifecycle/file-safety tests,1actual Room2→3 migration/schema-validation test,3document-import/delete tests.
- Lint PASS_WITH_WARNINGS:0errors/73existing warnings (72app+1compatibility).
- Initial new migration-test fixture used a generic signature unavailable in the pinned Robolectric API; corrected. First test round then found JUnit required a void return for the idempotent-deletion test and two legacy tests hard-coded schema2. Corrected declaration and made the model-default tests compare schema before/after their helper, keeping their original invariance assertions. Chat-deletion schema assertion updated to3. Final suite passes. Failed logs are retained.
- No Android device attached. Device UI/deletion/native lifecycle/performance validation NOT_EXECUTED.
- Release `assembleRelease -Parm64Only=true --max-workers=2`: PASS in44s,13tasks executed/125up to date. Signature/update certificate, package/version/minSdk/ABI, feature DEX markers and all8 native hashes against0.1.9 PASS.
- The last review corrected the import notice: importing is not proof that extraction/indexing succeeded; users now see “Document imported; check its indexing status in Files”. Debug tests/build/lint and release were rerun before replacing the initial Drive upload in place.
- Final signed artifact is91,535,289bytes; SHA-256 `63cee591e87d99502244026b0f2bcfaaeaeba9bdb35feb847cc20fe0eadf640a`. Drive raw replacement and metadata readback PASS for name/size/MIME/parent/private sharing. Provider checksum is unavailable; no remote hash comparison is claimed.
- [Download final0.1.10 APK](https://drive.google.com/file/d/1DnJoNPZNrboP5QMgNXCkZxN1BcYP1wnI/view?usp=drivesdk). Release/signature/Drive metadata are recorded in the artifact manifest.
