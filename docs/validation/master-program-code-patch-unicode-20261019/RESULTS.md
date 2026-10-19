# M03-06 preparatory slice — UTF-16 patch integrity

**Result:** focused HOST pass; no formal source task completion.

- Branch: `codex/master-program-recovery-20261019`
- Baseline code: `da47ed7f1fa1d395a01d1132ad25202802b660f7`
- Test-only red commit: `7b737dc0a0a584a69a617e18166d580374ad26f9`
- Fix commit / exact tested code: `9778e4f5dd4ea5358011189e3be366351747c86f`
- Tested tree: `7878d780001cc8079980ac84ba0a5ec1bc47bdbc`

## Reproduced issue and fix

The new red test passed a valid body containing `😀` but gave the planner only its high-surrogate half as the match. The planner previously accepted that edit and produced malformed UTF-16 instead of refusing it. The same API also accepted malformed replacement or source text. `CodePatchPlanner` now validates the UTF-16 structure of the source and each `before`/`after` string, rejecting malformed input with `CODE_PATCH_INVALID_UTF16`. Valid supplementary characters remain supported. No parsing, rewriting or normalization of valid source content was added.

## HOST runs

| Run | Result |
|---|---|
| Baseline red, test-only change on `da47ed7` | 1 test, 1 failure (expected), exit 1: the planner did not reject the split surrogate. |
| Planner class on `9778e4f` | **PASS 6/6**, 0 failures/errors/skips, exit 0, 63/63 tasks executed in 3m03s. |
| ArtifactStore integration on `9778e4f` | **PASS 1/1**, 0 failures/errors/skips, exit 0, 63/63 tasks executed in 3m05s. Persisted preview reopen/apply still produces v2 and preserves v1. |

All runs used Gradle 8.10.2, JDK 17 runtime, the project-configured JDK 21 test launcher, existing Android SDK and offline Gradle cache. Exact command, environment, timestamps, exit code, complete logs and XML are retained per run. XML SHA-256: planner `ef0c38d6b822eb26982ee787491065ddeea35d2872510b4e07281bbe2383b839`; ArtifactStore `0c553262bfb3ef7f9d1a874baf73c62d087218cbad3a85df3d207b3b381c1d32`. `SHA256SUMS` covers all payload files in this directory except itself.

## Scope and limits

Only `CodePatch.kt` and `CodePatchPlannerTest.kt` changed for this fix; the exact ArtifactStore regression is reused unchanged from `498519c`. Full JVM/LiteRT, lint, AndroidTest, emulator, Motorola, APK, code workspace edits and syntax validation were not run. M03-06 remains source `TODO`, requires a device, and depends on M03-01 and M02-08, also TODO. The original source catalog is unchanged: 97 unique IDs, 120 dependency edges, all 97 statuses TODO.
