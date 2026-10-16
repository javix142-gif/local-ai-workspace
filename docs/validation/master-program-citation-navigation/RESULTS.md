# Preparatory M02-07 citation navigation

This increment adds a local preview action for the exact indexed segment already associated with an assistant message’s persisted `CitationEvidenceEntity`. It is **preparatory evidence only**: source task M02-07 remains `TODO` because the original task requires a device and its prerequisite M02-05 remains open.

The chat now observes persisted citation rows for the active conversation and groups them by assistant message. The existing tap callback still carries the same `evidenceId`; the UI maps it to that message’s stored citation row. The detail view retains source label, page/line/character coordinates, saved excerpt, and the note that citation metadata comes from the local index. “Open indexed source segment” resolves only the exact legacy segment ID or Semantic V2 segment ID. It checks active conversation, project/document identity, Semantic V2 scope, `DOCUMENT` source type, authorized state, and the exact local path. When the exact segment is unavailable, it displays the stored excerpt with an explicit fallback notice. It never searches another document or project and never implies that the whole document was reviewed.

No entity, Room schema, migration, retrieval, inference, permission, agent/tool, or dependency behavior changed. DAO additions are read-only queries. The original backlog and roadmap remain byte-identical: backlog SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` (46,100 bytes), roadmap SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5` (47,285 bytes). The authenticated backlog still has 97 unique task IDs, 120 dependency edges, and all 97 source statuses `TODO`.

## Validation

The valid targeted run is attempt 06, source commit `ab0b14f128fdc12907391ddab0ca6f36f9f3465f`, on 2026-10-16 21:30:47–21:31:49 UTC. It used Gradle Wrapper 8.10.2 offline, Temurin JDK 17 as Gradle runtime, the existing JDK 21 test worker, Android SDK API 35, and NDK 27.0.12077973. The Gradle test task compiled main and test Kotlin code and passed **9/9** tests: `CitationSourcePreviewTest` 5/5, focused `ChatContentUiTest` 2/2, `WorkspaceCitationEvidenceMigrationTest` 1/1, and `SemanticStorageTest` 1/1. There were no failures, errors, or skips. The raw command, full log, JUnit XML, task outcome, environment, timestamps and checksums are under `attempts/attempt-06-focused-host/`.

Earlier attempts are retained without conflating their results:

- `attempt-01-red` stopped before the intended test body because API 29 Robolectric could not resolve an uncached Android runtime in the offline environment.
- `attempt-02-red-api35` is the valid red reproduction: 1 test failed because the indexed-source action was absent.
- `attempt-03-focused-host` stopped at Kotlin visibility compilation; no tests ran.
- `attempt-04-focused-host` stopped at test compilation because call sites lacked the new `onDismiss` parameter and the fixture lacked `updatedAt`; no tests ran.
- `attempt-05-focused-host` compiled and ran 7 tests, with 1 Compose assertion failure. Its first command also had a malformed filter, so the migration and semantic DAO cases were not run there. Attempt 06 corrected the assertion and used exact filters; all 9 tests passed.

No full JVM/LiteRT gate, lint, AndroidTest compilation, emulator, physical device, or APK build was run for this increment. The previous automatic emulator run `38967472318` tested SHA `a42135088ad87d7a3e483570f3b915bdd34dd966`, not this source. It booted API 35/ADB and failed the historical Room v1 fixture (`WorkspaceDatabase/1.json` missing): 16 reported cases, 9 pass, 1 fail, 6 skipped for missing real models, 10 executed. Its evidence artifact is ID `12075588913`, 152,260 bytes, digest `sha256:94e9ee6060bab2bd2aa8e13af48d03f54515232821830b4cf5f55c415a04aa68`; GitHub artifact download returned HTTP 403 in this executor. The associated QA run `38967472333` failed closed at the instrumentation-success gate and produced no APK. These runs do not validate the current source SHA. After publication, the workflow-triggered run for the final commit must be read separately.

`EMULATOR` for this increment: not yet run against this source SHA. `PHYSICAL_DEVICE`: `NOT_RUN`. M01-06 remains `PENDING_REQUIRES_DEVICE`. M02-07 and M02-05 remain source `TODO`; no original ID, dependency, or status was edited.
