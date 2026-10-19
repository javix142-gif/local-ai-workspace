# M03-06 preparatory slice — multi-edit ordering

**Result:** focused HOST pass; this is preparatory and does not close source task M03-06.

- Working branch: `codex/master-program-recovery-20261019`
- Baseline source SHA: `efb5a089268be3b03238b97561b07733a44b071f`
- Code commits: `aebd6bbe03d4f958cce526dd4d2f98d2883e0841`, `f235a2385d0f885291256ba7ed7299aceeee36a4`, `498519c696ee19bc53c734a110cabee46849fe9b`
- Planner test code SHA: `f235a2385d0f885291256ba7ed7299aceeee36a4`
- Latest test-only integration SHA: `498519c696ee19bc53c734a110cabee46849fe9b`
- Tested tree SHA: `404cc8e0fc179b80e2ee7b92e13f2db2a89fd201`

## Cause and correction

`CodePatchPlanner.apply` previously searched and applied each `before` fragment against the result of earlier edits. For `alpha beta`, edits `alpha → beta` then `beta → gamma` made the second fragment ambiguous, even though both fragments were unique in the original. The regression reproduced `CODE_PATCH_MATCH_AMBIGUOUS` on the baseline. The planner now finds every unique range in the original body, rejects overlapping ranges, and applies edits from right to left. Existing stale-hash, edit-count, snippet/body byte bounds, Unicode and line-ending behavior remain covered.

## HOST evidence

| Attempt | Code state | Result |
|---|---|---|
| `01-red` | baseline `efb5a08` plus test-only working-tree change | Wrapper download failed with `Connection refused` before Gradle; no tests/XML. |
| `02-red-offline-cache` | baseline `efb5a08` plus regression test | Expected red: 4 tests, 3 pass, 1 failure, 0 errors, 0 skipped. The targeted multi-edit test failed with `CODE_PATCH_MATCH_AMBIGUOUS`. |
| `03-green` | implementation commit `aebd6bb` | Kotlin compile failure (`Unresolved reference: after`); test did not run and no fresh XML is attributed to this attempt. |
| `04-green-current` | corrected planner code `f235a2385d0f885291256ba7ed7299aceeee36a4` | **PASS:** 5 tests, 5 passed, 0 failures, 0 errors, 0 skipped; Gradle exit 0. |
| `05-store-integration` | integration test SHA `498519c696ee19bc53c734a110cabee46849fe9b` | **PASS:** ArtifactStore preview persistence/reopen/apply, 1/1, 0 failures/errors/skips; Gradle exit 0. |

The final command took 192 seconds by recorded UTC timestamps (`19:40:14Z`–`19:43:26Z`); Gradle reported `BUILD SUCCESSFUL in 3m 10s`, with 63/63 actionable tasks executed. Gradle 8.10.2 ran on Temurin JDK 17.0.20.1; the test worker used Temurin JDK 21.0.12.1; Android SDK `/workspace/.toolchain/android-sdk`, NDK `27.0.12077973`; existing `/workspace/.gradle` cache, offline. The exact command, full logs and raw XML are retained by attempt directory.

Planner XML SHA-256: `e37448d405b20806f955413cecf10ebb16ee9bbb20ba01c944215abf50a1ca9b`. ArtifactStore integration XML SHA-256: `64ffa04d8122a46dcfdfd544c0ef21a4733f755ff15a61d1f2793345fbbea3b7`. `SHA256SUMS` covers the evidence files in this directory, excluding the manifest itself.

The integration test covers the actual `ArtifactStore.stageCodePatch`/persisted preview/`applyCodePatchPreview` route. It reopened the preview with a new store object, created immutable v2 and verified original v1 remained unchanged; it passed 1/1 on exact SHA `498519c696ee19bc53c734a110cabee46849fe9b`.

## Scope and limits

Only `CodePatch.kt` and `CodePatchPlannerTest.kt` changed in product/tests. Full app/LiteRT suites, lint, AndroidTest compilation/execution, emulator, Motorola, and APK were not run. This is not a complete M03-06 acceptance: the original task remains **TODO**, requires a device, and depends on M03-01 and M02-08, also TODO. No status or dependency in the original plan was edited. The catalog remains 97 unique task IDs, 120 dependency edges, all 97 source statuses TODO.
