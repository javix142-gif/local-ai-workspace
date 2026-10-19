# Sheet-qualified spreadsheet cell citations — preparatory M02-05

**Status:** focused HOST checks pass; AndroidTest compilation and lint are blocked by environment; preparatory only.
**Tested code:** `6abd9eee7689f08b3cadbceb97cbfdcb5358064c`
**Base:** fetched feature/code branch head `d25f05997c0487bbcbad964f68acb34a175e2313`
**Implementation branch:** `codex/master-program-20261019`, descending from the fetched head.

## Workspace recovery and refs

The recovery prompt named `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc` as the last known feature head. At the time of this continuation, an explicit fetch showed the feature and implementation remote-tracking refs at `d25f05997c0487bbcbad964f68acb34a175e2313`; that newer SHA is the actual base of the preserved local work. The immutable recovery refs `recovery/backup-b8e75b4` and `origin/recovery/backup-b8e75b4` both resolve to the exact `b8e75b4` SHA. No reset or rebase was used.

The reported local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` is not present in the local object database (`git cat-file` could not resolve it) or available refs/reflog. Earlier bounded workspace and GitHub checks also found no patch or object. No missing code was reconstructed. The existing isolated implementation branch was retained rather than creating a competing branch. `origin/main` and peeled `v0.4.2` both remain `de615b772cf37a99e7e08a647c59607b271de606`.

The archived source plan was not modified. Its backlog SHA-256 remains `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`; roadmap SHA-256 remains `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`. It still has 97 unique task IDs, 120 dependency edges and 97 `TODO` source statuses.

## Change

The production path selected exact sheet-qualified cell references in `ContextFoundation`, but the `Evidence` object used for assistant citations did not carry them. Room persistence and the citation detail/source preview therefore lost the worksheet and cell identity. The change carries the resolved cell-reference strings into `Evidence`, serializes them with an existing-Gson codec bounded to 64 unique references, 256 characters per reference and 8 KiB of UTF-8 JSON, persists them on citation rows, and displays them in the citation card, detail and indexed-source location. Corrupt locator metadata is reported as unavailable while the saved excerpt remains visible.

Room schema version is now 10 with additive migration 9→10 (`cellReferencesJson TEXT NOT NULL DEFAULT '[]'`). Historical schema 8/9 files and existing citation rows remain intact. Room/KSP generated the checked-in schema 10. No formula evaluation, retrieval scope expansion, new dependency, model/runtime change, or task-catalog edit was made. Exact document lookup remains confined to the project/selected-document scope already authorized by retrieval.

## Test-driven evidence

The regression test commit `1924c285e7b867eff0106668fb49d9a86ca87a7a` adds assertions for EG2 and lexical fallback. On that pre-fix code, the focused run failed compilation at the two assertions because `Evidence.cellReferences` did not exist. The implementation is in `2b50c86745d657a58ce5ced6967072b3f42b7bc4`; the codec was changed to use the already-declared Gson dependency in `6abd9eee7689f08b3cadbceb97cbfdcb5358064c` after plain JVM tests showed Android's `org.json` stub throws `Method put ... not mocked`.

At exact source SHA `6abd9eee7689f08b3cadbceb97cbfdcb5358064c`, the focused citation/Context/Room/UI run passed **12/12**, 0 failures, errors or skips, Gradle exit 0, 65 seconds. It verifies EG2 cell locators reach final context/evidence, lexical fallback retains a missing qualified reference without choosing a homonymous cell, citation persistence/migrations, bounded/corrupt JSON handling, and citation UI source/page/excerpt/cell presentation. The initial 2/12 failed invocation is retained and not counted as a passing result.

The requested M03-07 revalidation was also run at the same exact source SHA. `ArtifactRevisionPreviewUiTest` passed **2/2**, 0 failures/errors/skips, exit 0, in 34 seconds. It covers exact before/after content, bounded diff fallback, cancel without modifying the saved revision, and saved-v1 export association. This is HOST-only; it does not close M03-07.

Commands, lossless gzip copies of raw Gradle logs and JVM thread dumps, captured JUnit XML and attempt metadata are stored alongside this report. `log-payload-hashes.json` records original and compressed byte counts and SHA-256 values. The local JDK/SDK setup was Gradle 8.10.2 with Temurin JDK 17 as Gradle runtime and JDK 21 for JVM test workers; Android SDK API 35, NDK 27.0.12077973, and existing `/workspace/.gradle` cache. The SHA256SUMS manifest lists 51 payload files and has SHA-256 `9bc9a5a2c906fb171687f935ed047fc20a8dfb8370de6e7d8776b144ac4c6f13`; it excludes RESULTS.md, RESULTS.json and itself.

## Validation limits

| Check | Result | Evidence/interpretation |
|---|---|---|
| Focused citation Context/Room/UI tests | **PASS 12/12** | Exact source SHA above; five JUnit XML files; full Gradle log. |
| M03-07 preview UI class, current source | **PASS 2/2** | Exact source SHA above; fresh XML and Gradle log. M03-07 remains `TODO`, `requires_device=true`, depends on M03-01 `TODO`. |
| `:app:compileDebugAndroidTestKotlin`, offline | **BLOCKED** | Missing already-declared AndroidX test/Room-testing dependencies in the existing cache; Kotlin compile was not reached. |
| `:app:compileDebugAndroidTestKotlin`, online | **INFRASTRUCTURE_FAILURE** | Exit 1 after 8m43s at `:app:processDebugAndroidTestManifest`; Google Maven and Maven Central returned `Network is unreachable`. Thread dumps show a socket connect wait; no instrumented tests or JUnit XML were produced. No retry or version change. |
| `:app:lintDebug`, correct cached Gradle home/offline | **BLOCKED** | Exit 1 before app lint: uncached `com.android.tools.lint:lint-gradle:31.7.3` at `:llama-runtime:extractDebugAnnotations`. Lint itself did not run. |
| First lint invocation | **NOT_STARTED** | Wrapper attempted to download Gradle 8.10.2 because `GRADLE_USER_HOME` was omitted; no Gradle task ran. A second invocation used the existing cache and produced the result above. |
| Full app/LiteRT JVM suites, assemble, emulator, physical device, APK | **NOT_RUN** | No full suite or APK is claimed. No new emulator or Motorola run. The historical emulator Room-v1 failure/model skips remain separate. |

The online dependency failure is environmental and does not demonstrate an AndroidTest source defect. Lint is unverified, not a pass. No debug keystore was found in the standard executor locations checked, so no signing key was generated and no APK was built. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.

## Original task status and next work

M02-05 remains source `TODO`: this closes only a preparatory exact-cell citation propagation gap. Its original dependencies M02-01 and M01-05 remain formally open; M02-01 depends on device-gated M00-10. M03-07 remains source `TODO` because its device requirement and M03-01 dependency are unmet. No source ID, dependency, ordering, status or progress was changed.

The next preparatory increment should move toward the foundational M03-01 Artifact API/persistence scope, using its authentic DoD to identify a concrete remaining host-testable invariant. Keep M03-01 formally TODO while M02-05 is open; do not redo the completed M03-07 preview work or overstate any milestone.
