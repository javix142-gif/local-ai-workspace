# M02-05 exact retrieval and citations — focused preparatory evidence

**Classification:** implemented code gap with focused HOST checks; **partial preparatory evidence only**. The original M02-05 source status remains `TODO`. This is not a full M02-05 acceptance, a global host gate, an emulator result, or a physical-device result.

## Code and scope

- Base source commit: `7539123f84bd6982b23653ab169d7e8b6de01e86`.
- Tested/final source commit: `7b60a53f8a993227dc9d0666b548ef5447e93578`.
- Tested source tree: `100f8904a5b0f0703731d79420d4a9beb8e6d807`.
- Work branch at execution: `codex/master-program-20261016`; publication target remains only `feat/0.5.0-skills-agents`.
- The verified gap was that Semantic V2 code hits could reach Context but did not become turn-scoped citation evidence when they lacked a legacy `document_segments` row. Consequently the citation validator could not approve them and persisted citation metadata had no semantic segment or line interval.
- The change creates scoped evidence for an EG2-only hit only when its document is present in the active project and satisfies selected-document constraints; records the canonical Semantic V2 segment ID and code line interval; accepts the app-owned `SV2-` citation form only when that ID exists in evidence supplied for the current turn; carries source/line provenance through prompt and citation persistence; and displays the line interval in the existing citation UI. Legacy citation rows survive Room migration 8→9. Existing EG2 and lexical spreadsheet-range behavior is covered by the same regression class.
- No retrieval ranking, formula evaluation, storage of source documents, inference runtime, permissions, Agents/Tools semantics, dependency, model, or original backlog file was changed. No document outside the current project/selected-document scope is searched to complete a citation.

Production and regression-test diff is the code commit above. Room schema 9 was exported from the project Room processor and adds nullable legacy `segmentId`, `semanticSegmentId`, and `lineStart`/`lineEnd`; schema 8 is retained unchanged. The focused migration test constructs from the checked-in v8 schema and verifies preservation of an existing citation row and storage of a V2 code citation.

## Validation record

All Gradle runs used the existing offline dependency cache and SDK; no installation, cache cleanup, emulator, APK, or device operation occurred. The local toolchain was Gradle Wrapper 8.10.2, Temurin JDK 17 for Gradle, Temurin JDK 21 for app JVM test workers, Android SDK/API 35, and NDK `27.0.12077973`. Commands, raw logs, XML, and per-attempt metadata are retained below `attempts/`.

| Attempt | Result | Evidence |
|---|---|---|
| Baseline red, test-only regression on `7539123` | Expected FAIL, exit 1: path/line prompt assertions succeeded, then the selected EG2 code passage was absent from `ContextBundle.sourceEvidence`. The Gradle log is retained; the XML was not preserved before the next execution and is not claimed. | `attempts/attempt-01-code-citation-red/` |
| First implementation focus | FAIL, exit 1; 7 selected tests completed, 3 failed. One failure exposed lower/upper-case `SV2` ID normalization and was fixed. The API-29 migration/UI cases also failed during Robolectric setup because `android-all-instrumented:10-robolectric-5803371-i7` could not be fetched offline (`Connection refused`); these are environment-blocked, not product results. The metadata's `gradleVersion` field is malformed (`jdk17=...`); the raw log identifies Wrapper 8.10.2 and JDK 17. | `attempts/attempt-02-focused-green/` |
| Focused final group | 5/6 PASS, 1 FAIL, exit 1. Semantic retrieval/range/fallback 3/3, citation validator 1/1, and Room 8→9 migration 1/1 passed. The Compose citation test failed because the test selector `citation-lines` matched both the reference card and detail dialog; this was a duplicate test tag, not missing UI content. | `attempts/attempt-03-focused-final/` |
| Compose selector correction | 1/1 PASS, exit 0, `BUILD SUCCESSFUL`; the dialog now has a unique `citation-detail-lines` test tag. No production behavior changed after attempt 03. | `attempts/attempt-04-ui-tag-fix/` |

The final focused evidence therefore includes successful executions for six distinct checks across the recorded runs: three EG2/fallback cases, one turn-evidence citation validation case, one v8→v9 data-preservation case, and one Compose citation/path/line case. This is deliberately reported per invocation; the earlier failed invocations are retained and not folded into a fictitious single green suite.

### Gates not run

- Full `:app:testDebugUnitTest`: **NOT_RUN** as a full suite.
- `:litert-compat:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, Release, and `:app:compileDebugAndroidTestKotlin`: **NOT_RUN**.
- AndroidTest execution, EMULATOR, and PHYSICAL_DEVICE/Motorola: **NOT_RUN**.
- APK generation: **NOT_RUN**.

The existing full-gate Robolectric stall for UX-02e remains `NOT_DETERMINED`; this increment did not retry that gate. The M00 historical emulator failure remains a separate Room-v1 fixture failure with model-dependent cases skipped; no emulator run was made here.

## Original-plan reconciliation

The authentic archived backlog/roadmap were read and hash-checked. The backlog remains 97/97 unique IDs, 120 dependency edges, and 97 source statuses `TODO`; neither original was edited. M02-05 depends on M02-01 and M01-05. M02-01 itself depends on M00-10, which is not closed and requires device validation. M01-06 remains `PENDING_REQUIRES_DEVICE`; M02-07 remains device-required. The authorized master-program preparation can supply code/test evidence despite these prerequisites, but this increment does not mark any source task complete or claim that dependencies are satisfied. See `plan-source-check.json`.

The implementation is useful preparatory work, but the corpus and end-to-end exact retrieval coverage required by M02-01/M02-05 remain incomplete. Do not advance M02-06/M02-07 as formally eligible from this evidence alone.

## Integrity

`SHA256SUMS` covers the evidence payload files in this directory except itself. Its digest is recorded in `EXECUTION_STATE.json` after the bundle is finalized. XML counts were parsed from the original JUnit files and agree with their per-run summaries. No measurement of model latency, tokens/s, RAM, energy, or Motorola behavior is implied.
