# Preparatory M03-06 — reviewable code patch drafts

This increment adds an exact, review-first patch path for **generated, app-private CODE artifacts**. It is preparatory evidence for original task M03-06; it does not satisfy its workspace-path or physical-device definition of done and does not change the source task status from `TODO`.

## What changed

`CodePatchPlanner` applies bounded UTF-8 text edits only when the current body SHA-256 matches the requested base and each `before` snippet occurs exactly once. It rejects stale, absent, ambiguous, empty, no-op, oversized, or too numerous edits. The planner does not parse or execute code.

`artifacts.patchPreview` is registered as a `LOCAL_WRITE` tool. A successful tool call stores only a project-scoped preview under the app-private artifact directory and returns `PATCH_PREVIEW_STAGED_NOT_APPLIED`. The Artifacts dialog shows exact before/after bodies and hashes. The user can explicitly apply a preview as a new immutable artifact version or discard it. Older revisions remain available; external export remains a separate SAF action. No workspace path, external file URI, shell, execution, Room schema, model, inference, routing policy, or dependency was added.

The implementation is limited to artifacts created in this app. It does not patch a repository checkout, imported source file, arbitrary SAF document, or another project. The in-app preview is before/after content, not a unified diff with syntax-aware hunks. Original M03-06 requires patching in a chosen workspace, failure recovery and device validation; those remain outstanding.

## Source plan and backup

The canonical backlog and roadmap were read-only and retain their original SHA-256 values:

- `docs/development/plan-source/2026-10-08/Local_AI_Workspace_BACKLOG.json`: `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`, 46,100 bytes.
- `docs/development/plan-source/2026-10-08/Local_AI_Workspace_ROADMAP.md`: `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`, 47,285 bytes.

Recount: 97 tasks, 97 unique IDs, 120 dependency edges, DAG intact, and all 97 source statuses remain `TODO`. M03-06 still depends on M03-01 and M02-08 and remains `requires_device=true`; none of those original IDs was marked complete. M01-06 remains `PENDING_REQUIRES_DEVICE`.

Before continuing from the pre-increment local state, a backup was written outside the repository at `/workspace/backups/aws-pre-resume-20261016-215330/`. It contains the original HEAD/status, binary tracked diff, copies of all then-modified/untracked working files, and a SHA-256 manifest. Archive SHA-256: `164f76a3cf5f6359bc36f982730063717b04d83b0f1e61809e431834e25b4809`; manifest SHA-256: `3d7232d8da931958bbe91e62ff0b35571eba25b66b550111c214621d55472504`. This is an executor-local recovery copy, not a remote backup.

## Host validation

| Attempt | Source | Result | Evidence |
|---|---|---|---|
| 01 | dirty worktree based on `2cebd17` | `FAIL_TEST_COMPILATION`, exit 1; no tests started | Test referenced a nonexistent `waitUntil` import. Corrected to the Compose rule member. Raw log retained. |
| 02 | dirty worktree based on `2cebd17` | `INFRASTRUCTURE_FAILURE`, exit 1; 18 reported, 3 pass and 15 failed during Robolectric startup | Kotlin production/test compilation completed. Robolectric could not fetch `org.robolectric:android-all-instrumented:10-robolectric-5803371-i7` (connection refused). The subsequent Gradle test invocation replaced the generated XML directory before these XML were copied; per-test XML is explicitly `NOT_PRESERVED`, not reconstructed. Raw log and console summary are retained. |
| 03 | code commit `7c2ab7a603b67b8f8058959a16164a4c1e7781bd` | `PASS`, 3/3 planner tests, 0 fail/error/skip, 84 seconds wall time, exit 0 | Gradle 8.10.2, JDK 17 runtime/JDK 21 test worker, offline; `BUILD SUCCESSFUL`, 63 tasks / 7 executed. XML preserved. |
| 04 | production code `7c2ab7a603b67b8f8058959a16164a4c1e7781bd` | `PASS`, `:app:lintDebug`, exit 0, 276 seconds wall time | 112 tasks / 15 executed; lint XML and text report preserved. 77 warnings, 1 information, 0 errors; no reported location in changed files. |
| 05 | test-only follow-up `3f6387b304d615cca6d5f403d6ac938d41c964a7` | `PASS`, 3/3 planner tests, 0 fail/error/skip, 39 seconds wall time, exit 0 | Added a known SHA-256 vector and maximum edit/snippet bound assertions. Gradle 8.10.2, JDK 17/JDK 21, offline; XML preserved. |

The exact command and environment are in each attempt directory. Attempt 03 records the installed NDK path as `/workspace/.toolchain/android-sdk/ndk/27.0.12077973`; its focused Kotlin test did not execute native NDK tasks. Attempt 05 JUnit XML SHA-256 is `4688424bcf6a14c861bf323e49d136279f168737ca12204458cfe4dbfed7ec5f`; attempt 03 JUnit XML SHA-256 is `cbf7427518a1812511596cc3f2edc7da4bbd42ee881162ccf1b3dfa3cbb67111`. Attempts 03 and 05 both compile the current Kotlin production and unit-test sources. The Room-backed ArtifactStore roundtrip, tool invocation, capability gate and Compose review/apply/discard tests compile, but **did not execute** in attempt 02 because the required Robolectric runtime was unavailable. No full app JVM suite, LiteRT compatibility suite, `assembleDebug`, AndroidTest compile, emulator test or phone test was run for code SHA `7c2ab7a`/test SHA `3f6387b`.

## Push-triggered workflows on the preceding source SHA

The push of `2cebd17fa13fb52a93d8ebfcf5976c1954a57390` started three existing workflows; these do not validate the later code patch:

- Emulator run [38970584904](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38970584904): failed during pinned SDK/NDK/CMake setup. The runner received an invalid NDK ZIP (`ZipFile unknown archive`); AVD and connected tests were skipped, with zero instrumentation XML and zero tests. Evidence artifact ID `12076851077`, 22,261 bytes, digest `sha256:201986d808ff72b4848eebe0ab3352c4eee064d74a724538f031696447eb64e7`.
- Official gated QA run [38970584801](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38970584801): failed closed because matching instrumentation did not succeed; no APK artifact.
- Personal QA run [38970584816](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38970584816): succeeded for the **older** source SHA `2cebd17`, not this code. Artifact ID `12076981872`, 48,056,080 bytes, digest `sha256:f7d6695d7137c54c8824f8b4fff14e22f2ff0e492688bd6c7b440e07b2a17cc3`, expires 2026-11-15. Its workflow report identifies `com.localai.workspace.debug`, version `0.5.0-debug`/27, arm64-v8a, APK SHA-256 `27928bf9c8c16c932d9a5f46921ecaae48596845ae3719d833298151384b9e4a`, certificate SHA-256 `706cead75d02faf718e67560008c8b9a4b054c80be081d356786add157c5823c`.

Artifact URL: https://github.com/javix142-gif/local-ai-workspace/actions/runs/38970584816/artifacts/12076981872. Download attempts returned HTTP 403 from the artifact blob endpoint; APK bytes were not locally rehashed and were not uploaded to Drive. This older artifact is not a build of the code in this report.

The recorded previous personal APK signer is `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`; the older workflow artifact reports `706cead75d02faf718e67560008c8b9a4b054c80be081d356786add157c5823c`, so the recorded certificates differ. The artifact is from an older source SHA and its APK bytes were not locally rehashed. No matching private key was found at the standard Android debug-keystore paths checked. Update compatibility is **not established**; do not install it as an update or uninstall/clear data to test it.

## Device and remaining gates

The SDK's `adb devices -l` listed no device. The Moto G86, Android 16, actual local model, original private workbook, native TTFT/tokens-per-second, peak memory, battery/thermal behavior, and SAF workspace edit/rollback are `NOT_RUN`/`PENDING_DEVICE_VALIDATION`. No model weights were downloaded and no inference/performance gain is claimed. The personal signing key needed for an update-compatible package is unavailable in this executor.

The next concrete prerequisite for exercising the store/UI tests is availability of the pinned Robolectric `android-all-instrumented` artifact (or network access to its configured repository). The M03-06 workspace integration and its formal DoD additionally require a real device and a user-selected workspace. Keep all original source task statuses and dependencies unchanged.


The `SHA256SUMS` manifest contains 47 payload entries (excluding the manifest itself) covering raw attempt logs, commands, environments, task outcomes, JUnit XML, lint report, and these reports. The missing attempt-02 JUnit XML is not represented as a payload. All 47 entries were verified with `sha256sum -c` before publication.

The repository whitespace check was run on the staged changes. Gradle's raw `.log` files contain trailing whitespace and indented stack traces, so an unrestricted `git diff --cached --check` reports those lines. The path-scoped check excluding only the verbatim raw-log payloads exits 0; the logs were not normalized or altered.


## GitHub Actions and APK readback — 2026-10-16

Both runs used the exact feature source `9cfb0d8112342ee097ac64aa573e5d35b446f834`.

- Emulator run [38974433001](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38974433001), job 117220332118: API 35 x86_64 booted and ADB connected. `:app:connectedDebugAndroidTest` ran and returned 1. The XML contains 16 cases: 9 passed, 1 failed, 6 skipped, 0 errors (10 executed). The sole failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` because authentic Room `WorkspaceDatabase/1.json` is unavailable. Six real-model inference cases were skipped. The two ChatHeader assertions pass; the standard synthetic capture is present and valid, but the large-font capture is missing (`CAPTURE_END_MISSING` and `SHA256_MISMATCH`). The collector also records `applicationApkInstalled=false` after the test; this post-run check does not establish pre-test installation failure. This is **EMULATOR FAIL**, not product PASS. Physical Motorola remains NOT_RUN.
- The sanitized 82,124-byte emulator evidence ZIP has SHA-256 `2633ee3a938e65ab86575c6520a206393215030b0ca3a6a3163278ebc58e30bf`; the GitHub API digest matches. It expires 2026-10-23. The original bundle, XML and summary are kept beside this report.
- Personal QA run [38975236297](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38975236297), job 117222974355: PASS as a Debug ARM64 **personal QA build only**. The 120,051,674-byte APK SHA-256 is `fc18e2d802814cf0e37b1a8edbc81d73740a9a8cd849684e5153e5bf1953730f`; package `com.localai.workspace.debug`, version `0.5.0-debug`/27, arm64-v8a only, nine native libraries, v2 signature, ZIP CRC and zipalign passed. Certificate SHA-256 is `635bc83888b30d6cdc1fcd62b2984e94fbae04db5619c2baf061b53a2656977b`. The artifact ZIP (48,074,199 bytes, SHA-256 `2275b4382b63903f8c1afb7b2b1c26181fba81bc36f546c949ad866ef23fb491`) expires 2026-11-15. This workflow does not run Android tests.
- The Drive connector rejected the 120 MB direct APK with HTTP 413 (limit 104,857,600 bytes). A ZIP containing only the unchanged APK was uploaded instead, with Drive readback byte-for-byte matching the local ZIP: [download from Drive](https://drive.google.com/file/d/1kiiLJWiW-dnCnArZVw4TVj6GIcXdPWri/view?usp=drivesdk). Drive size 46,508,187 bytes, SHA-256 `da911324c5d1ef81a4b81667b63d113bed236f06a5fba5672b0c5d21992642ca`; its only member is the APK above.
- The current signer `635bc8…` differs from the last recorded personal APK signer `f9085e…`. Android will reject this as an update to that installation. Do not uninstall/clear app data to install it. A matching signing key is unavailable in this executor.

The raw action evidence and full summaries are retained in `actions/`. The GitHub Actions APK artifact is downloadable for a limited retention period; the Drive copy is an APK-only ZIP to stay below the connector transfer limit. No official release was created.
