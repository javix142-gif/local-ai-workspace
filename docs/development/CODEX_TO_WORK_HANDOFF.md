# Codex → Work handoff: 0.5.0 clean-checkout stabilization and Android CI setup

**Status:** The `.gitignore` correction and clean-checkout `assembleRelease` were independently accepted as `VALIDADA_HOST` (documentary audit, not an auditor-run Gradle build). The subsequent Android CI setup is published, but its final run failed before instrumentation because the named AVD was not discoverable. Do not report Android CI or device validation as PASS.
**Repository:** `https://github.com/javix142-gif/local-ai-workspace.git`
**Branch:** `feat/0.5.0-skills-agents`
**Current code SHA:** `bdae87820b73dbcc0decfde3a65c391c6fb63279` (workflow/KVM/ADB evidence changes; Actions run attempted, instrumentation NOT_RUN).
**Accepted preceding source SHA:** `b6c460763b19de41e689c493859bc1ee649faf2c` (`.gitignore` only; ignore matrix 17/17 and clean-checkout `assembleRelease` PASS, accepted by independent documentary audit).
**Accepted preceding source-build evidence commit:** `b5fa6a2062f7aed568f33bc66c2e4ea229f28a66`.
**Most recent complete JVM gate:** `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`, app 727/727 + compatibility 2/2 = 729/729 PASS; historical, not rerun on `b6c4607` or `bdae878`.
**AndroidTest compilation/instrumentation:** final workflow run `37865813921` failed before `:app:connectedDebugAndroidTest`; XML 0, executed 0. **PHYSICAL_DEVICE:** NOT_RUN. No APK was generated or distributed.
**Baseline:** `v0.4.2` → `de615b772cf37a99e7e08a647c59607b271de606`
**App:** `com.localai.workspace`, version `0.5.0` (`versionCode 27`).

## Latest Android CI follow-up (2026-10-09)

The workflow and its evidence are described in [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md). Final source commit `bdae87820b73dbcc0decfde3a65c391c6fb63279` was tested by [Actions run 37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921), artifact [11588945524](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524). GitHub-hosted `ubuntu-24.04` had `/dev/kvm`; the workflow enabled access and the KVM verification passed. `avdmanager` then failed to leave a discoverable `localai-api35-x86_64` AVD; the emulator reported `Unknown AVD name`, ADB had no connected emulator and its 600-second wait timed out. Instrumentation was skipped by the job dependency and no JUnit XML exists. `summary.json` correctly records `FAIL`, zero tests, and physical validation `NOT_RUN`.

The first run (37865314837) was rejected before a job because of an invalid runner-context location; the second run (37865443624) stopped in the KVM preflight and exposed the collector's missing SDK-path `adb` lookup. The follow-up corrected both KVM preparation and ADB lookup. The final run confirms those checks pass but reveals the remaining AVD creation/location blocker. The two available correction attempts are exhausted; no further workflow change or rerun was made in this handoff.

The final artifact contains the ADB listing, SDK inventory, KVM report, `avdmanager`/emulator logs, summary, and SHA-256 manifest. Its ZIP SHA-256 is `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. The artifact is retained by GitHub through 2026-10-16. No APK, model, secret, or personal data is included.

## Accepted preceding delivery: clean-checkout source ignore matrix and release build (2026-10-09)

The published feature base was `8837517ee502b700c85a2b2c484beddcbcc879be`. Code commit `b6c460763b19de41e689c493859bc1ee649faf2c` narrows the root `.gitignore` exceptions to the vendored llama.cpp model registry's `.cpp` sources and `models.h`; the production application code is unchanged.

The original clean-checkout failure is independently preserved at code SHA `f4695b5644f1230b5f711ec8300937027fb39941`: `:app:assembleRelease` failed because `third_party/llama.cpp/src/models/models.h` was absent from Git. Raw log: [`base-red-assembleRelease.log`](../validation/clean-build-repro-e367dc7/logs/base-red-assembleRelease.log), SHA-256 `14cc8269f6db42fb570a882cb1172c8664b5313c8b08ef6a6fa2f50fe55f0943`. The subsequent `e367dc7` repair tracked the pinned upstream sources; the current `.gitignore` follow-up was then verified against that complete tracked tree.

From a fresh detached checkout of that exact source SHA, after `git clean -ffdx`, the deterministic `git check-ignore --no-index` matrix passed 17/17 assertions: the required C++ sources/header are trackable, all 12 probed model/binary extensions remain ignored, the registry has exactly 158 tracked source files (157 `.cpp`, one `.h`), and the checkout remains unmodified. Script, raw output and checksums are in [the ignore-fix evidence](../validation/clean-build-repro-e367dc7/ignore-fix/RESULTS.md).

From the same clean checkout, `:app:assembleRelease` passed on code SHA `b6c460763b19de41e689c493859bc1ee649faf2c`: exit 0, 128/128 Gradle tasks, 2026-10-08 23:50:16 UTC–2026-10-09 00:07:30 UTC. It used Gradle 8.10.2, Temurin JDK 17.0.20.1, Android SDK 35, NDK 27.0.12077973, CMake 3.31.6 and ABI `arm64-v8a`. The exact command, environment, raw log and SHA-256 are recorded in the evidence report. The temporary build output was removed with the isolated worktree; no APK was delivered or distributed.

The full host JVM gate was last run on `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`: app 727/727 plus LiteRT compatibility 2/2, zero failures/errors/skips. Those results are preserved as **historical per-SHA evidence**, not attributed to `b6c4607`. AndroidTest compilation was not run for `b6c4607`; instrumented tests, emulator and Motorola validation are **NOT RUN**. This delivery's machine-readable status is in [`EXECUTION_STATE.json`](EXECUTION_STATE.json) → `latestDelivery`.

## Historical follow-up: M00-03 LOG10 cell/function distinction (2026-10-08)

The published feature base before this correction was `020917cdb6e469983f77093130b58967ae56df56`. A red test against that code failed for the intended reason: the selector skipped `LOG10` unconditionally, so formula `Hoja1!A3` with formula `LOG10` omitted the existing dependency `Hoja1!LOG10`. The correction is `39aea8f55aac051edd3a43bc1b09c05d319d2783`. It treats unqualified `LOG10` as a function name only when followed by `(` (allowing whitespace); it continues scanning function arguments. A bare or qualified `LOG10` cell reference remains a cell, and missing qualified references keep their sheet/address identity and existing incomplete-evidence notice.

The selector harness reports 12/12 synthetic invariants passing. Focused Context Evidence, ContextFoundation, structured document, Semantic Context postfix and harness tests passed 106/106 on source `39aea8f`. The complete host-gate results recorded for the subsequent tracked-source baseline are 727 app JVM + 2 compatibility tests on `e367dc7`; do not attribute that later run to `39aea8f`. The earlier recorded run also built debug/release/lint/AndroidTest, with lint 0 errors, 40 warnings and 1 informational issue. Instrumented tests, emulator execution and Motorola validation are **NOT RUN**. These source/log records remain historical.

The exact red/green XML, commands, JSON harness report, losslessly compressed logs and SHA-256 manifest are in [M00-03 LOG10 correction evidence](../validation/m00-03-log10/RESULTS.md). The LOG10 harness uses `M00_SOURCE_COMMIT=39aea8f55aac051edd3a43bc1b09c05d319d2783`. At that historical stage, a clean-worktree native build exposed missing ignored llama.cpp model sources. The later `e367dc7` commit tracks the pinned sources and adds the CMake guard; the current `b6c4607` clean-checkout release build passed. The clean-build evidence and raw logs are in [the reproducibility bundle](../validation/clean-build-repro-e367dc7/README.md).

## Historical follow-up: M00-03 sheet-scoped references (2026-10-08)

The earlier M00-03 selector follow-up below fixed range interiors, absolute/mixed references and multibyte text anchors. A separate audit then found that qualified formula references still lost their worksheet identity. At the clean feature HEAD `9c1f32c4277a187c65af715890d1061a2244e771`, the red regression selected `Hoja1!A1=999` for formula `'Hoja2'!A1` and reported no missing cell. The correction is `ca3fcd7ca492ea35d2255b2665c27fa26c2466cf`; its host validation evidence is in [M00-03 sheet-scope results](../validation/m00-03-sheet-scope/RESULTS.md).

Design decision: A1 identity is the pair `(NFC + Locale.ROOT uppercase worksheet key, normalized coordinate)`, while the original sheet name is preserved for display. Unqualified formula references resolve only on the formula cell's sheet. Qualified references resolve only on the named sheet. A direct unqualified query address with multiple represented candidates is treated as ambiguous: no candidate values are sent and a material diagnostic is raised. `$` does not change identity. Qualified single-sheet references/ranges and escaped-apostrophe sheet names are supported. 3D ranges are expanded inclusively only when source metadata supplies a contiguous, conflict-free workbook sheet order; otherwise the excerpt is explicitly incomplete and does not claim endpoint-only coverage.

Selection is bounded to 64 unique composite `(sheet,address)` identities and 480 UTF-8 bytes. Formula strings are masked; formulas are not evaluated or recursively followed. At that historical source commit, `LOG10` was skipped unconditionally; the current contextual behavior is documented above. External workbook references, dynamic `INDIRECT`/`OFFSET`, defined names and structured table references remain unresolved with diagnostics. Missing sheets/cells, ambiguity, 3D ordering failures and evidence-limit omissions propagate through Context provenance/Inspector and the existing material user notice. The indexed source stays unchanged.

Context Foundation reuses only passages already returned by authorized Semantic V2 or lexical retrieval, grouped within the same document. It does not search globally to complete a formula or cross document/project boundaries. F1/F2 and the retrieval-before-generation-gate tests remain in the full host suite.

Historical validation at code commit `ca3fcd7`: focused suites 99/99, app JVM 720/720, LiteRT compatibility 2/2; debug/release builds, lint and AndroidTest APK compilation succeeded. These counts are not the current LOG10 correction result; see the latest section above. Instrumented and Moto G86 execution were **NOT RUN** for that revision.

## Historical follow-up: M00-03 evidence selector correction (2026-10-08)

The later authorized host correction is implemented in `594a511cc088a53b97ccb5f21b720e20e8e4639a`, based on `7da2cc71e57aed1d213ea03b01ba8b41be849939`. It fixes the range-interior omission, absolute/mixed formula reference parsing, and UTF-8 query-anchor windowing. Missing cell dependencies and cell/byte-budget omissions now remain explicit in provenance/Inspector data and trigger a concise user notice; ordinary text excerpting does not.

Focused tests passed 70/70. The subsequent complete host gate passed app JVM 700/700 and LiteRT compatibility 2/2, with debug/release assemble, lintDebug and AndroidTest APK assemble successful. Lint had 0 errors, 40 warnings and 1 informational issue. The test APK was only built: instrumented tests and Motorola validation are **NOT RUN** because no ADB device, emulator binary/system image or KVM was available. The full synthetic before/after JSON, JUnit XML, gate log, summary and hashes are in [M00-03 validation evidence](../validation/m00-03/RESULTS.md).

The prior sections below describe earlier stabilization revisions. Their 686, 700, and 720 app-test counts, APK metadata, and push state are historical only. The LOG10 correction is also historical; current clean-checkout results are recorded at the beginning of this handoff and in `EXECUTION_STATE.json` → `latestDelivery`.

The implementation is based on the completed 0.5.0 Skills/Agents and Semantic V2 postfix. It preserves the single Gemma runtime, Skills/Agents, existing databases, Semantic V2 and the F1/F2 fixes. No merge or release tag is part of this handoff.

## Work completed in this stabilization

- **M00-1 XLSX:** representation budgeting now accounts for actual serialized cells incrementally; blank/style-only cells are omitted instead of consuming estimated JSON budget. Formula text, cached values, coordinates, XML/cell limits, interruption and parser failure behavior remain. A synthetic workbook with 40,000 formatted empty cells exercises the regression. The user’s original ~300 KB workbook was not available, so its exact ZIP/XML dimensions and exact failure trigger remain **NOT DETERMINED**.
- **M00-2 context:** full indexed segments remain intact, while Context Builder chooses a query-relevant, bounded excerpt for the current request. XLSX evidence prioritizes queried cells, formula dependencies and headers; provenance includes cell addresses and character offsets when the excerpt is contiguous. Repetitive/deduplicated/scope-filtered or old-history omissions are Inspector diagnostics; important items lost to token budget retain a user notice. Prompt metadata identifies source content as selected passages and avoids claiming whole-file coverage.
- **M00-3 chat header:** the three identity rows use an adaptive top-bar height, font-scale-aware padding, one-line ellipsis for long names and Compose test tags. A Compose UI test covers standard and 1.6 font scales; execution requires an Android device/emulator.
- **M00-4 diagnostics:** import/copy/hash, parse, chunk, segment commit, background EG1 indexing, skill/agent routing, memory retrieval, source retrieval, conversation retrieval, context construction, inference gate wait, generation/TTFT and existing load/warm-up/device metrics are separated where the production pipeline exposes them. Timings contain metadata only, not prompt, document, filename, path or content. `contextBuildMs` is inclusive of sub-stages; do not add the inclusive total to its component timings.

Focused commits on the feature branch:

- `2df24f2` — bounded XLSX representation.
- `8c48d68` — bounded relevant context evidence; F1/F2 regression tests.
- `5a27a6e` — stage timings and diagnostics.
- `b577934` — adaptive chat header and Compose test.

### Root-cause confidence

The production XLSX code had been charging its representation estimate for blank formatted cells that do not produce useful evidence. Skipping empty cells and accounting serialized output addresses that avoidable expansion while retaining hard bounds. Because the original workbook was unavailable, this is a code-level reproduction and explanation, not proof of its precise internal cell count or expanded size.

The observed ~3-minute first answer has **no established root cause** from the available physical evidence. The host synthetic fixture measures parser/serialization/chunk stages only; it does not run Android native embedding or Gemma. The new instrumentation is intended to isolate the next cold and warm physical run. No speedup is claimed.

## F1 / F2 preservation

Targeted tests continue to cover EG2 retrieval through Context Builder even when precomputed evidence is empty, project and selected-document isolation, no duplicate evidence, lexical fallback on V2 failure, and work completing before the generation gate. Stale `NEEDS_REINDEX`, `INDEXING`, `FAILED` and `CANCELLED` generations remain ineligible as current evidence; READY generation publication and rollback are retained. No Semantic V2 redesign was made.

## Validation

The first full gate run passed JVM tests, debug/release builds and lint but exposed a compile error in the new Compose test (`assertExists` import unsupported by the project’s Compose test API). The redundant assertion/import was removed; the final full-gate outcome is recorded in `EXECUTION_STATE.json` after rerun.

Host synthetic XLSX evidence is written by `StructuredDocumentsTest` to `app/build/reports/xlsx-formatting-repro.json`. The fixture is 207,035 compressed bytes, with 1,778,015 bytes of worksheet XML, 40,000 formatted empty cells, three populated cells and 293 serialized UTF-8 bytes. The parser reported 299 ms on this host; serialization and chunk creation rounded to 0 ms at the report’s millisecond resolution. The report labels itself `HOST_SYNTHETIC_NOT_USER_WORKBOOK`; embedding/indexing is explicitly marked not executed. These values are not measurements for the user’s workbook or Motorola.

Final host gate command (JDK 17, Android SDK, ARM64-only, offline, sequential Gradle):

```text
./gradlew --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2048m \
  -I docs/validation/skills050/force-test-execution.gradle \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true --offline --console=plain
```

Result: `:app:testDebugUnitTest` **686/686 PASS**; `:litert-compat:testDebugUnitTest` **2/2 PASS**; debug, release, lint and AndroidTest APK build PASS. Lint has 0 errors, 40 warnings and 1 informational issue. The first gate attempt compiled the new Compose test with an unavailable `assertExists` import; that redundant call was removed and the complete gate then passed. The Compose test APK was built, but tests were not run.

### Android execution environment

Environment inspection found no attached ADB device, no AVD, no installed Android system image and no `/dev/kvm`. Therefore Android instrumented/UI tests cannot run here. `assembleDebugAndroidTest` means only that the test APK compiles; it is not a UI test PASS. Moto G86 performance, thermal, native inference and real-workbook behavior remain physical-device validations.

## Artifacts and Git synchronization

Historical artifact from an earlier stabilization build (not the current `b6c4607` delivery): ARM64-only release APK `dist/apk/local-ai-workspace-0.5.0-preloop-stabilization-arm64.apk`, 100,130,461 bytes, SHA-256 `a7bd3f5c69c2644b7de9fbe86f62aa3edf78fb60961487cd96782fe66848c0d0`. It was not device validated. The `b6c4607` clean release output was removed with its temporary worktree and no APK was delivered. APK/model binaries remain outside Git.

Historical Git synchronization records above are not the current feature-branch publication state. For this delivery the intended code/evidence commits are `b6c460763b19de41e689c493859bc1ee649faf2c` and `b5fa6a2062f7aed568f33bc66c2e4ea229f28a66`; publication/readback status is reported after the authorized fast-forward push. `main` and peeled `v0.4.2` are expected to remain at `de615b772cf37a99e7e08a647c59607b271de606`. No force push, merge, release or `v0.5.0` tag is authorized.

## Physical validations still required

On the Motorola Moto G86 Power 5G, test with the original workbook when available:

1. Import the ~300 KB XLSX and inspect parse, chunk, and background-index stage timings.
2. Ask the A3/formula follow-up twice: once cold and once warm; export metadata-only diagnostics.
3. Confirm the relevant cell/formula excerpt reaches Gemma, provenance contains A1/A2/A3, and ordinary redundant trimming does not show the prominent omission notice.
4. Confirm all three header lines remain visible with the keyboard open, a long chat title and standard/large font scale.
5. Run the F1/F2 Context Builder checks after importing/updating a document and after reindex.

Historical physical evidence (12/12 AGENTS_SKILLS_V1 and prior 0.5.0 validations) belongs to its recorded build; it does not validate this stabilization APK.

## Historical recommendation from the prior handoff

The previous handoff recommended installing its stabilization candidate and collecting cold/warm timings. That recommendation belongs to that earlier delivery.

## Current next step

Independently audit the Android CI attempt and evidence in `../validation/android-emulator-ci/RESULTS.md`. The `.gitignore` correction has already been accepted by the independent documentary audit. The next requested milestone after that audit is the ARM64 APK; keep the AVD creation blocker explicit, and do not describe the APK as emulator-tested unless connected instrumentation actually runs. Host compilation and workflow configuration are not Android test execution.
