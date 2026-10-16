# Semantic V2 scope cleanup on workspace deletion

Status: **implemented and focused HOST-tested; preparatory partial only**. Tested code: `8b91675646abca8c4e3e22f3ecd86d97ee817345`.

## Finding and change

`WorkspaceRepository` removed the project and its EG1/document state, but the Semantic V2 sidecar kept its active-index row, jobs, sources, segments, embedding links, and vector payloads. `AppGraph` now connects confirmed workspace deletion to a scoped `SemanticLayer.deleteProjectScope(projectId)` operation. Project deletion and standalone CHAT deletion both await cleanup after the primary Workspace Room transaction commits. One Semantic V2 transaction removes only `PROJECT:<id>` state; deduplicated vector payloads remain while any surviving index references them. Normal chat deletion inside a still-existing project does not erase the project index.

No source files, user documents, memory records, models, Room schema, semantic database version, runtime, or dependencies changed. Code changes are in `AppGraph.kt`, `WorkspaceRepository.kt`, `SemanticDatabase.kt`, `SemanticLayer.kt`, and `SemanticContextPostfixTest.kt`. Three focused tests cover both delete routes, the real AppGraph callback, project isolation, retained shared payloads, and removal of orphan payloads.

This is only partial evidence for original **M02-06**, which remains `TODO`: the app still has no document-level remove/edit flow, and the full edit/delete/reimport definition has not been met. No task ID was completed or promoted.

## HOST validation

The final exact-SHA run used Gradle 8.10.2, Temurin JDK 17 runtime, the configured Temurin JDK 21 app test launcher, Android SDK API 35, NDK 27.0.12077973, CMake 3.31.6 and the existing offline Gradle cache. Command, timestamps, environment, task outcomes, raw log, and JUnit XML are in `final-host/`.

Result: `:app:testDebugUnitTest` with three exact method filters **PASS**, 3 tests, 0 failures, 0 errors, 0 skipped; Gradle exit 0, 3m37s reported (218 seconds by recorded timestamps), all 63 actionable tasks executed due `--rerun-tasks --no-build-cache`.

Earlier attempts remain separated and transparent: code SHA `377d73d` passed the two initial deletion cases (2/2); an intermediate fixture revision at `0d25b5f` stopped at Kotlin test compilation because an `Int` was passed where `Byte` was required (exit 1, 0 tests). Its directory labels a copied prior XML as not current; it is not counted. The final committed fixture correction was rerun at SHA `8b91675` and passed all three cases.

`lintDebug`, full app/LiteRT JVM suites, AndroidTest compilation, assemble, APK, emulator and physical-device testing were **NOT_RUN**. No performance or Motorola claim is made.

The archived source plan remains byte-for-byte unchanged: 97 unique IDs, 120 dependency edges, all 97 source statuses TODO. The original 97-task plan is preserved separately from this preparatory implementation. `main` and peeled `v0.4.2` remain unchanged.

Evidence payload manifest: 23 files; SHA-256 `e9f6da0f568fe5476ee1885b517b47bb278bcbf736eecdf40f728a01b55b5c2f`; every listed payload passed local `sha256sum -c`. The manifest excludes this report, its JSON counterpart, and itself; their state references are recorded separately.
