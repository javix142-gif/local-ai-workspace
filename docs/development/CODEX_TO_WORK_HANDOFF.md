# Codex → Work handoff: 0.5.0 pre-loop stabilization

**Status:** 0.5.0 host candidate with the authorized M00-03 sheet-scope correction; no device validation for this correction.
**Repository:** `https://github.com/javix142-gif/local-ai-workspace.git`
**Branch:** `feat/0.5.0-skills-agents`
**Latest tested production source HEAD:** `ca3fcd7ca492ea35d2255b2665c27fa26c2466cf` (source correction; evidence and handoff are a separate documentation commit).
**Baseline:** `v0.4.2` → `de615b772cf37a99e7e08a647c59607b271de606`
**App:** `com.localai.workspace`, version `0.5.0` (`versionCode 27`).

## Latest follow-up: M00-03 sheet-scoped references (2026-10-08)

The earlier M00-03 selector follow-up below fixed range interiors, absolute/mixed references and multibyte text anchors. A separate audit then found that qualified formula references still lost their worksheet identity. At the clean feature HEAD `9c1f32c4277a187c65af715890d1061a2244e771`, the red regression selected `Hoja1!A1=999` for formula `'Hoja2'!A1` and reported no missing cell. The correction is `ca3fcd7ca492ea35d2255b2665c27fa26c2466cf`; its host validation evidence is in [M00-03 sheet-scope results](../validation/m00-03-sheet-scope/RESULTS.md).

Design decision: A1 identity is the pair `(NFC + Locale.ROOT uppercase worksheet key, normalized coordinate)`, while the original sheet name is preserved for display. Unqualified formula references resolve only on the formula cell's sheet. Qualified references resolve only on the named sheet. A direct unqualified query address with multiple represented candidates is treated as ambiguous: no candidate values are sent and a material diagnostic is raised. `$` does not change identity. Qualified single-sheet references/ranges and escaped-apostrophe sheet names are supported. 3D ranges are expanded inclusively only when source metadata supplies a contiguous, conflict-free workbook sheet order; otherwise the excerpt is explicitly incomplete and does not claim endpoint-only coverage.

Selection is bounded to 64 unique composite `(sheet,address)` identities and 480 UTF-8 bytes. Formula strings are masked; formulas are not evaluated or recursively followed. `LOG10` is not interpreted as a cell. External workbook references, dynamic `INDIRECT`/`OFFSET`, defined names and structured table references remain unresolved with diagnostics. Missing sheets/cells, ambiguity, 3D ordering failures and evidence-limit omissions propagate through Context provenance/Inspector and the existing material user notice. The indexed source stays unchanged.

Context Foundation reuses only passages already returned by authorized Semantic V2 or lexical retrieval, grouped within the same document. It does not search globally to complete a formula or cross document/project boundaries. F1/F2 and the retrieval-before-generation-gate tests remain in the full host suite.

Validation at code commit `ca3fcd7`: focused suites 99/99, app JVM 720/720, LiteRT compatibility 2/2; debug/release builds, lint and AndroidTest APK compilation succeeded. Lint reported 0 errors, 40 warnings and 1 informational item. Instrumented and Moto G86 execution are **NOT RUN**; the AndroidTest APK compilation is not an Android PASS. The required assemble tasks produced only ignored local Gradle build outputs; no APK was copied or distributed. The exact commands and raw JUnit/log evidence are in the validation folder above.

## Follow-up: M00-03 evidence selector correction (2026-10-08)

The later authorized host correction is implemented in `594a511cc088a53b97ccb5f21b720e20e8e4639a`, based on `7da2cc71e57aed1d213ea03b01ba8b41be849939`. It fixes the range-interior omission, absolute/mixed formula reference parsing, and UTF-8 query-anchor windowing. Missing cell dependencies and cell/byte-budget omissions now remain explicit in provenance/Inspector data and trigger a concise user notice; ordinary text excerpting does not.

Focused tests passed 70/70. The subsequent complete host gate passed app JVM 700/700 and LiteRT compatibility 2/2, with debug/release assemble, lintDebug and AndroidTest APK assemble successful. Lint had 0 errors, 40 warnings and 1 informational issue. The test APK was only built: instrumented tests and Motorola validation are **NOT RUN** because no ADB device, emulator binary/system image or KVM was available. The full synthetic before/after JSON, JUnit XML, gate log, summary and hashes are in [M00-03 validation evidence](../validation/m00-03/RESULTS.md).

The prior sections below describe the earlier pre-loop stabilization commit and its measurements; their 686 app-test count and artifact metadata are historical for that earlier commit. The current M00-03 gate result is 700 app tests plus 2 compatibility tests.

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

The current code version is built as an ARM64-only release APK, signed with the configured local update certificate, then checked with `apksigner`, `zipalign`, package metadata and ABI inspection. APK: `dist/apk/local-ai-workspace-0.5.0-preloop-stabilization-arm64.apk`, 100,130,461 bytes, SHA-256 `a7bd3f5c69c2644b7de9fbe86f62aa3edf78fb60961487cd96782fe66848c0d0`. Certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b` matches the previously recorded update certificate; ABI inspection found only `arm64-v8a`. It is not device validated. A companion `.sha256` file is present beside the APK. APK/model binaries remain outside Git.

The authorized feature-branch push was a fast-forward from remote `de615b772cf37a99e7e08a647c59607b271de606` to `2e01b59d7b8aa84d53e7dc326969ecd136e63527`; readback confirmed local and remote HEAD equality. [Pushed source handoff commit](https://github.com/javix142-gif/local-ai-workspace/commit/2e01b59d7b8aa84d53e7dc326969ecd136e63527). The push contained no APK/model binaries or secrets. `main` and `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`. No force push, merge or `v0.5.0` tag.

## Physical validations still required

On the Motorola Moto G86 Power 5G, test with the original workbook when available:

1. Import the ~300 KB XLSX and inspect parse, chunk, and background-index stage timings.
2. Ask the A3/formula follow-up twice: once cold and once warm; export metadata-only diagnostics.
3. Confirm the relevant cell/formula excerpt reaches Gemma, provenance contains A1/A2/A3, and ordinary redundant trimming does not show the prominent omission notice.
4. Confirm all three header lines remain visible with the keyboard open, a long chat title and standard/large font scale.
5. Run the F1/F2 Context Builder checks after importing/updating a document and after reindex.

Historical physical evidence (12/12 AGENTS_SKILLS_V1 and prior 0.5.0 validations) belongs to its recorded build; it does not validate this stabilization APK.

## Recommended first loop cycle

First install the candidate on the Motorola and collect the above cold/warm stage timings using the actual workbook. Have the independent Work orchestrator audit those results and the code diff. Only then select the next roadmap item; do not infer the 3-minute bottleneck or start an autonomous loop from host timings alone.
