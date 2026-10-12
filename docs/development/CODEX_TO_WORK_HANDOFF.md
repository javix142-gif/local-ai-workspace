# Latest contract: UX-02d audit correction — independent table boundary pipes (2026-10-12)

**Status:** the targeted parser correction and proportional HOST checks passed at code SHA `f54c1430cab4ea59a302b58dd2aa27deea579c49`; independent audit is pending. Stop after this correction. Do not start UX-02e, UX-03, or functional roadmap work.

## Audit finding and correction

The audited base was `ba5b1f084a3e4f29c9bb3c8c6f8b029a6d40e3d4`. `BasicMarkdown.splitTableRow` stripped an outer pipe only when both initial and final pipes were present, although the UX-02d report described each as optional. The new regression was committed first and failed on that base behavior. The parser now treats the initial and final outer pipes independently while preserving consistent row width, full literal fallback for incompatible rows, escaped-pipe handling, single-backtick spans, fences, and malformed-table behavior.

- Red test commit: `af321e397106c49a3e316feabdc58999cdddb763`; 1 test executed, 1 expected failure.
- Parser/test commits: `0c3973e5c7c6d5da7d8bb689bff1dd39eeaf4604`, `26e0fd7f901953068d8836239dc0c845c2b7b3ea`, and final verified code `f54c1430cab4ea59a302b58dd2aa27deea579c49`.
- Production/test scope: only `BasicMarkdown.kt` and `BasicMarkdownTest.kt` changed in this correction.
- Detailed red/green commands, environment, XML, raw logs, task outcomes and checksums: [pipe-boundary evidence](../validation/ux-02d-pipe-boundaries/RESULTS.md). Updated UX-02d report: [Markdown tables](../validation/ux-02d-markdown-tables/RESULTS.md).

## HOST results at the final code SHA

- Focused parser/Compose tests: **27/27 PASS**, 2 XML files, zero failure/error/skip.
- Proportional JVM gate XML: app **754/754**, compat **2/2**, total **756/756** across 90 XML files; zero failure/error/skip. Compat was UP-TO-DATE in the combined gate and was also separately forced to rerun on the same SHA: **2/2 PASS**.
- `:app:lintDebug`: exit 0, **0 errors / 82 warnings**.
- `:app:compileDebugAndroidTestKotlin`: executed and passed as a compile only; no instrumented test ran.
- Combined gate: exit 0, 137 actionable tasks (14 executed, 123 up-to-date). Gradle ran offline with the existing cache; this is not a clean-cache claim.
- Initial SDK setup attempt stopped before tests because `ANDROID_HOME` was missing; it is separately recorded and not counted as a test run.

## Validation boundaries and tracking

- **HOST:** PASS for this parser correction and the proportional host gate.
- **EMULATOR:** NOT_RUN for this correction. The prior global UX run remains **FAIL**, 16 cases total: 9 PASS, 1 historical Room v1 fixture FAIL, 6 real-model-dependent SKIPPED. This contract did not repeat it.
- **PHYSICAL_DEVICE:** NOT_RUN. No APK was generated.
- UX-02c remains `INDEPENDENTLY_AUDITED_ACCEPTED` only for structured citation presentation and its HOST evidence. UX-02d is pending independent audit.
- The original 97-task plan is not present in the tracked repository and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02e and UX-03 remain pending; functional backlog remains paused.
- `main` and `v0.4.2` are outside this work and were not changed.

**Next:** stop for independent audit of the UX-02d pipe-boundary correction.

---

# UX-02c delivery record (historical; independently accepted)

**Audit status:** `INDEPENDENTLY_AUDITED_ACCEPTED` for structured `CitationEvidenceEntity` presentation and its HOST evidence only. No UX-02c emulator suite, QA workflow, APK, or physical-device run was started. The later UX-02d delivery is recorded at the top of this handoff.

## UX-02b audit acceptance

UX-02b is independently accepted for the already-supported Markdown renderer and its HOST checks only. Its code SHA is `3fc4bf6d496702f98f370e6089e98141486b284d`; focused checks were 17/17 PASS and the prior full app JVM suite was 744/744 according to the accepted records. The automatic global emulator run `38215175075` remains FAIL (16 total, 9 PASS, 1 unavailable authentic Room v1 fixture failure, 6 model-dependent SKIPPED, 10 executed); QA run `38215175184` failed closed with no APK. PHYSICAL_DEVICE remains NOT_RUN. Links, free-text citation syntax, and tables remain literal in ChatRichText. The post-run `applicationApkInstalled=false` observation and 22/16 progress discrepancy remain undiagnosed.

## UX-02c implementation

- Base: `eb3a6aeee0966cdfe01fb2698a6601ca7af9cdc8`.
- Code commit: `0846419ea64259244a9b1c7bce9b40711cc29217`.
- The existing flow remains `CitationEvidenceEntity` → assistant message → the same `onCitation(evidenceId)` callback → MainActivity lookup by that ID. No parsing of answer text or new evidence/retrieval path was added.
- `CitationEvidenceReferences` replaces ID-only horizontal chips with stacked reference cards showing the stored ID, source label when present, and pageStart when present. They retain the same evidenceId callback, expose button semantics and an accessible description, and have a 48dp minimum target.
- `CitationEvidenceDialog` keeps the source, pageStart/existing fallback and full stored excerpt in a vertically scrollable body. It retains the local-index metadata note and explicitly says the excerpt does not represent a full-document review.
- Files: `MainActivity.kt`, new `ui/CitationEvidencePresentation.kt`, and `ChatContentUiTest.kt`.

## HOST evidence

- Focused `ChatContentUiTest`: 10/10 PASS, including exact callback ID, stored source/page/excerpt, accessible button/heading semantics, touch target, and compact-width long details at fontScale 1.0/1.6/2.0.
- Combined JVM suite: app 746/746 and LiteRT compatibility 2/2 PASS (748 total across 90 XML files).
- `lintDebug`: PASS, 0 errors, 82 warnings.
- `compileDebugAndroidTestKotlin`: PASS, compile only. Instrumented tests NOT_RUN.
- `assembleDebug` was NOT_RUN; no APK requested. `git diff --check` PASS.
- Commands, environment, XML, logs and checksums: [UX-02c evidence](../validation/ux-02c-citation-references/RESULTS.md).

## Validation and remaining risks

- **HOST:** PASS for the focused UX-02c and proportional gate above.
- **EMULATOR:** no new run. The audited UX-02b global result remains FAIL on Room v1; six tests requiring real models remain SKIPPED. The QA gate remains closed with no artifact. The package post-run check and progress-count discrepancy are still unexplained.
- **PHYSICAL_DEVICE:** NOT_RUN.
- The original user-supplied 97-task plan is not present in the tracked repository; it was not reconstructed or edited. Functional IDs/order/dependencies/progress were preserved. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02d is pending; functional backlog remains paused.

**At UX-02c delivery:** stop for independent audit. The subsequent acceptance and UX-02d status are recorded above.

---

## UX-02b delivery record (full historical report; now accepted within the scope above)

# Previous contract: UX-02b conversational content readability (2026-10-12)

**Audit status:** independently accepted for the existing Markdown renderer and HOST evidence scope. The global emulator result remains FAIL on the authentic Room v1 fixture, with six model-dependent SKIPPED and 10 executed; QA failed closed with no APK; PHYSICAL_DEVICE remains NOT_RUN.

## Baseline and prior UX-02a acceptance

- Base/current feature reference: `224756694d174af2ed00db4b8c1b5c9fb3f9cb5d` on `feat/0.5.0-skills-agents`; product code for UX-02b is `3fc4bf6d496702f98f370e6089e98141486b284d`.
- The automatic workflow event used checkout commit `3ed99d8b882668aa31c69db622b689f01dd6212f`, whose application code is the same UX-02b code commit above. A documentation-only commit may be made after this record to preserve the automatic run result.
- UX-02a source `d0832e225f51f624c981f6b68da6193b72a78ef2` was independently accepted for its UI scope. The acceptance does not turn its global emulator result into PASS.
- UX-02a remains HOST PASS; its global EMULATOR run remains FAIL on the unresolved authentic Room v1 fixture, with six real-model cases SKIPPED and 10 cases executed. PHYSICAL_DEVICE remains NOT_RUN.

## UX-02b result

The assistant message route uses `ChatRichText` and the existing lightweight `BasicMarkdown` grammar. UX-02b retains recognized heading levels 1–6 for typography and accessibility semantics, establishes clearer vertical rhythm between sections/paragraphs/list items, and preserves message text, selection, exact code-copy behavior, horizontal code scrolling, attribution and source provenance. Links, citations and tables are not interpreted by the current renderer and remain literal text. No parser behavior was added to invent content.

Product/tests changed only in `BasicMarkdown.kt`, `ChatContent.kt`, `BasicMarkdownTest.kt` and `ChatContentUiTest.kt`. No MainActivity, navigation, storage, model, inference, Room, permission, Agent/Tool or dependency changes.

- Focused Compose/Robolectric tests: 17/17 PASS.
- Full app JVM suite: 744/744 PASS from 89 XML files.
- `lintDebug`: PASS, 0 errors and 82 warnings.
- `assembleDebug -Parm64Only=true`: PASS; CMake configure/build for `arm64-v8a` executed.
- `compileDebugAndroidTestKotlin`: PASS, compile-only.
- Full host gate: exit 0, 151 tasks (15 executed, 136 up-to-date), 6m38s.
- Environment, commands, full gate log, focused/full XML and checksums: [UX-02b evidence](../validation/ux-02b-conversation-content/RESULTS.md).

The new layout test uses a compact 320dp Robolectric host fixture at fontScale 1.0, 1.6 and 2.0. It verifies that the complete paragraph text remains present and wraps, heading levels expose heading semantics and size hierarchy, list entries remain reachable/visible while scrolling, and fenced code remains horizontally scrollable. Code-copy exactness remains covered by the existing test. No screenshots were generated in UX-02b; no visual screenshot review or physical-device validation is claimed.

## Validation layers and plan

- `HOST`: PASS for the listed UX-02b checks.
- `EMULATOR`: automatic push run [38215175075](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38215175075), job `114698137054`, completed FAIL on event commit `3ed99d8b882668aa31c69db622b689f01dd6212f`. AVD API 35 x86_64/ADB connected; `:app:connectedDebugAndroidTest` ran and exited 1. JUnit: 16 total, 9 PASS, 1 FAIL, 0 errors, 6 model-dependent SKIPPED, 10 executed. The one JUnit failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` due to the still-unavailable authentic `WorkspaceDatabase/1.json`; Room/tests were not changed. Both ChatHeader tests passed. Collector acceptance also reports `applicationApkInstalled=false`; its timing/cause is undetermined, so no install-success claim is made. Gradle progress reached `22/16` but final XML and collector summary agree on 16 cases; discrepancy not investigated. Actions artifact `11731948710` was 155,255 bytes with SHA-256 `1142a6e6e6b1c63730835c231873a2c931cc0aaf499eab6344edfa418bcba794`. Respecting `.gitignore` (which excludes validation ZIPs), the original archive is not committed; exact summary, JUnit XML, Gradle log and original artifact manifest are persisted in [UX-02b run evidence](../validation/ux-02b-conversation-content/emulator-run-38215175075/README.md). Its header PNGs are not visual validation of Markdown content.
- QA gate [38215175184](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38215175184), job `114698137501`, failed closed with `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; APK build and upload steps were skipped and the Actions artifact list is empty. No APK was generated or downloaded for this UX-02b delivery.
- `PHYSICAL_DEVICE`: NOT_RUN.
- The original user-provided 97-task plan is not in the tracked tree; it was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`, and the functional backlog remains paused.
- `main` and peeled `v0.4.2` remain unchanged at `de615b772cf37a99e7e08a647c59607b271de606`.

**Next at UX-02b delivery:** await independent audit. That audit has since accepted UX-02b for its stated Markdown/HOST scope; the latest work is UX-02c as documented at the top of this file.

---


---

# Previous contract: UX-02a chat header and composer (2026-10-12)

**Status at UX-02a delivery:** HOST PASS; exact-SHA emulator instrumentation executed and was FAIL overall on the unresolved historical Room v1 fixture; both ChatHeader emulator tests passed; personal QA APK was delivered. The user/orchestrator subsequently accepted UX-02a for its UI scope only; global EMULATOR remains FAIL and PHYSICAL_DEVICE remains NOT_RUN.

## Baseline and accepted UX-01

- Before changes, the current checkout was clean on `feat/0.5.0-skills-agents`; local/fetched/remote HEAD was `cd7ea02bba7dbaa0d557259ac73f6ad3427b9ad6`. `main` and peeled `v0.4.2` were both `de615b772cf37a99e7e08a647c59607b271de606`.
- UX-01 source `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8` and evidence HEAD `cd7ea02bba7dbaa0d557259ac73f6ad3427b9ad6` were independently accepted. Acceptance details/limits are retained in `EXECUTION_STATE.json`; in particular, the Drive ZIP hash for UX-01 was not recalculated by the auditor.
- The original plan of 97 task IDs is not tracked; it was not reconstructed/edited. M01-06 remains `PENDING_REQUIRES_DEVICE`.

## UX-02a changes

The real route is `MainActivity` chat `Scaffold` → header and bottom attachments/composer. The previous M3 `TopAppBar` imposed an expanded-height maximum/clipping boundary and `ChatHeaderTitle` limited conversation/model text to one line. The chat now uses `ChatTopBar`, a content-sized M3-styled row with standard minimum height, M3 window insets and colors; all header text may wrap. The model picker callback, back action, overflow actions and Scaffold remain.

Persistent Think/Tools chips are now behind one `Options` action. The selected mode stays visible in a compact summary. Existing dialogs, profile values and callbacks remain; Tools is summarized as `Off`, `Auto` or `Custom`. Tools Off continues to mean tools disabled only and is not an Agent selection. Attachments, selected files, input, send/stop callbacks, keyboard handling and routing remain unchanged. No runtime, models, Room, data, dependencies, workflows or navigation changed.

## Validation

- Commit of product/tests: `d0832e225f51f624c981f6b68da6193b72a78ef2`.
- Focused Compose/Robolectric host tests on that SHA: 9/9 PASS, 0 fail/error/skip.
- Full app JVM suite: 741/741 PASS from 89 XML files. `lintDebug`: PASS, 0 errors and 82 warnings. `assembleDebug -Parm64Only=true`: PASS. `compileDebugAndroidTestKotlin`: PASS as compile-only; it was UP-TO-DATE in the final gate and compiled in the prior successful gate after the AndroidTest changes. Gate details, commands, toolchain, XML archive, logs, and checksums are in [UX-02a results](../validation/ux-02a-chat-header-composer/RESULTS.md).
- Four Robolectric component-fixture captures at 360dp/1.0 and 411dp/1.6 in light/dark were reopened and visually reviewed. All show the three header lines and composer. They are not full-app, keyboard-visible, emulator, or Motorola captures. Host assertions cover fontScale 2.0.
- The Android `ChatHeaderLayoutTest` retains three `assertIsDisplayed` checks, long title/model text, wrapping and bounds assertions, inset diagnostics and model-before-body order. The exact-source API 35 x86_64 instrumented run later executed it: both standard and large-font tests PASS. The complete XML is 16 total: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed. The single failure is unchanged `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` because authentic `WorkspaceDatabase/1.json` is unavailable. Two LiteRT and four Gemma model-dependent tests remain skipped. No Room test/schema was changed.
- The push-triggered emulator run [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867), job `114683767158`, used source SHA `d0832e225f51f624c981f6b68da6193b72a78ef2`; AVD/ADB were API 35, x86_64, `emulator-5554`. `:app:connectedDebugAndroidTest` ran and exited 1 due to the Room fixture failure. Both original 1080×1920 screenshots were captured, opened and visually reviewed; they show the agent, long conversation title and model at normal and large font settings. The exact Actions archive and extracted XML/PNGs are retained in [emulator evidence](../validation/ux-02a-chat-header-composer/emulator/RESULTS.md); artifact ID `11729571175`, 155,402 bytes, SHA-256 `dd75931c08f5a6ededdb73482b0c728faefcc1719f92d4d8c7ee96da1baa9911`; all 33 archive manifest entries verified.
- The paired same-SHA QA gate [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883), job `114683767175`, failed closed at the wait-for-instrumentation-success step. APK build and artifact upload were skipped and this run produced no artifact. The separately assembled and Drive-uploaded personal QA APK above is not a product of this workflow run.

## Personal QA APK

The Debug ARM64 APK was built from product commit `d0832e225f51f624c981f6b68da6193b72a78ef2`: package `com.localai.workspace.debug`, `0.5.0-debug`/27, `arm64-v8a` only, 121,801,305 bytes, SHA-256 `c58b90d30228dbf58ce230039e44093cafbe71d6ae644d619618ed17117b6aa0`. ZIP CRC, `zipalign`, `apksigner` v2 and package/version checks passed; Debug certificate SHA-256 is `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. No model weight files are bundled.

Download: [PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION](https://drive.google.com/file/d/1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8/view?usp=drivesdk). Drive ID `1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8`; ZIP is 46,262,838 bytes; local ZIP SHA-256 `b5b1225300aa576a5f54c991a5f119844fb41eabdfdb5c7ec7b48880f65d5bc1`. Metadata readback confirmed ID, name, MIME type, byte size, URL and parent folder; the Drive ZIP was not downloaded again to recalculate its remote hash. This is a debug QA package, not Release or production.

## Layered status and next action

- `HOST`: PASS as listed above.
- `EMULATOR`: **FAIL overall** for run [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867); connected instrumentation executed 16 cases with 9 PASS, 1 Room v1 fixture FAIL, 0 errors and 6 model-dependent SKIPPED. Both ChatHeader tests and their emulator screenshots pass within this failed overall suite. Exact artifact, XML, logs and screenshots are in [the run evidence](../validation/ux-02a-chat-header-composer/emulator/RESULTS.md).
- Paired QA gate [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883) failed closed; it did not build/upload an APK. The separate PERSONAL_QA APK and Drive link above remain the delivered binary.
- `PHYSICAL_DEVICE`: `NOT_RUN`; no Motorola claim.
- `M01-06`: `PENDING_REQUIRES_DEVICE`; original 97-task plan/IDs/order/dependencies remain unchanged.
- UX-02a delivery record only; UX-02b is documented above and remains pending independent audit.


---

# Previous contract: UX-00 audit and design foundation (2026-10-12)

**Status:** `INDEPENDENTLY_AUDITED_ACCEPTED`. The original UX-00 delivery itself was documentation-only; it made no application code, workflow, tests, Gradle configuration, database, model, or APK changes.

- Inspected feature HEAD: `ea8b25ea77319a7650dae8f870c788979530feca`. UI source SHA: `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`; the intervening feature commits up to the inspected HEAD contain documentation/evidence, not UI-source changes.
- Audit and implementable UX-01 proposal: [ux-00-audit-and-foundation.md](../design/ux-00-audit-and-foundation.md). It inventories the nine current navigation destinations, three top-level entries, project tabs and dialogs; records the current Theme/UI structure; and proposes tokens, navigation flows, wireframes and phased implementation gates.
- Reference assets: no ChatGPT/Claude screenshots were attached, so no comparison to those products is claimed. The historical app captures and Compose header-test captures are explicitly identified in the audit as product/test evidence.
- The repository has no tracked ROADMAP/BACKLOG/LOOP_RULES or original 97-task file. The original user-provided task plan was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; other roadmap work remains pending.
- Android state remains separate from the design audit: run `38186081844` is **EMULATOR FAIL** (16 total: 9 pass, 1 Room v1 fixture failure, 6 model-dependent skips; 10 executed). The two header captures were visually reviewed. `PHYSICAL_DEVICE=NOT_RUN`. This audit did not rerun tests/builds.
- `main` and peeled `v0.4.2` remain outside scope and were not modified.
- UX-01 proposal from this audit was implemented in code commit `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8`; see the latest contract above and its evidence report.
- The orchestrator automation `supervisar-correcci-n-nica-m00` is already coordinated by the user to complete/audit this contract and then wait silently for visual UX input. No automation was modified from this coding environment.

---

# Latest contract: personal QA ARM64 delivery and Room v1 source review (2026-10-12)

**Build source:** `27a286daa4dd83bfb1b2d36d185459e481d40d1f` on `feat/0.5.0-skills-agents`; product/build files are unchanged from the instrumented code SHA `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`. Room history was checked only for an attributable WorkspaceDatabase v1 declaration: no such commit exists; the reachable database history begins at v8. `ENVIRONMENT_UNAVAILABLE` remains; no schema or test was changed and the Room suite was not rerun.

Local `:app:assembleDebug -Parm64Only=true` completed at the exact source HEAD with exit 0 (Gradle 8.10.2, Temurin JDK 17.0.20.1+1, API 35; 81 tasks, 7 executed/74 up-to-date; CMake/Ninja native tasks executed). The 119,297,951-byte Debug ARM64 APK has SHA-256 `3aa74283c68ece12169477f837227b90a329e74e53022c70d855d4ab7871e72b`, package `com.localai.workspace.debug`, version `0.5.0-debug`/27, Android Debug certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`, and only `arm64-v8a` native libraries. Zip alignment, archive CRC and `apksigner` v2 checks passed. This build used existing local Gradle/project intermediates and is **PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION**, not Release.

The installable APK is inside the [Google Drive QA bundle](https://drive.google.com/file/d/1M18I1Ls1z6GfAhYMa9dyXqEedfXxIoSI/view?usp=drivesdk). Extract the ZIP and verify its included `SHA256SUMS` before installing. Bundle size: 50,032,510 bytes; SHA-256 `4e3e36a231458997658e22c5ed2155fcc57a94d63abc6f90781c143069021309`. [Machine-readable report](https://drive.google.com/file/d/1l61586udhEwkvnjoN5wM1TLZBZPe7wCR/view?usp=drivesdk) and [bundle checksum](https://drive.google.com/file/d/1E9DI3Kyf293yZ8rJgV1vWJeHxYgrWDNO/view?usp=drivesdk). The direct APK exceeded the Drive connector's 100 MiB limit, so the ZIP preserves the exact APK bytes. No GitHub workflow was changed or gate bypassed.

Instrumented evidence remains separate: run `38186081844` on source code `5e93ba6` was **EMULATOR FAIL** (16 total: 9 pass, 1 Room failure, 6 model skips; 10 executed). The Room failure is `ENVIRONMENT_UNAVAILABLE`; six real-model tests remain skipped. This artifact is not a same-commit instrumentation pass. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged. `main` and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

**Pause:** priority `MEJORAS VISUALES Y EXPERIENCIA DE USUARIO`; state `VISUAL_UX_WAITING_USER_INPUT`. Do not start new roadmap functions or visual changes until the user provides direction/screenshots. The orchestrator automation `supervisar-correcci-n-nica-m00` is coordinated to complete and audit this contract, then remain silently paused for visual UX input; no automation was modified from this coding environment. This contract stops after delivery and evidence publication.

Detailed evidence: [personal QA build](../validation/personal-qa-arm64-27a286/RESULTS.md) and [Room v1 historical source review](../validation/room-v1-source-audit/RESULTS.md).

---

# Independent orchestration audit (2026-10-12 00:23 UTC)

Audit recorded after code/evidence commits `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1` and `25b2da35cd503e800c7bdce9584145f0f2e1218b`; the branch was at `25b2da35cd503e800c7bdce9584145f0f2e1218b` when checked. GitHub confirms the code commit changes only `ChatHeaderLayoutTest.kt`, and the evidence commit changes documentation/evidence only.

The independent audit verified run [38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844) on the exact code SHA: API 35 x86_64 emulator and ADB connected; `:app:connectedDebugAndroidTest` ran 16 tests. The retained gzip XML was decompressed and parsed: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed. The failure is the missing authentic `WorkspaceDatabase/1.json`; the six skipped tests require real models. The compressed and decompressed XML hashes match the persisted manifest/state.

I fetched and opened both committed PNG captures. Both visibly show all three header lines at 1080×1920. Their byte-level SHA-256 values match the report and checksum manifest: standard `9ecef80b3f3f7bf2fa5f7e6981374bfd741efc33863c8ed6e7afca4675a0c4f2`, large-font `47b64cb847d1ba456a733c26b8ecd333b1e959636dd59e87ad0933598214477c`. The host collector log confirms 7/7 tests; the decompressed AndroidTest compile log confirms BUILD SUCCESSFUL with 54 actionable tasks (2 executed, 52 up-to-date). Neither a full JVM suite nor local APK assembly ran.

Same-SHA QA gate [38186082135](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186082135) failed closed; APK build and upload were skipped and GitHub reports zero artifacts. GitHub artifact metadata for emulator evidence reports 108,492 bytes and digest `0ef4c876e0e275200000085434f6c8a01d648db7afab4eecada7b6e8dbdf709c), matching the persisted record. The auditor independently rechecked hashes for the XML, both PNGs, and host logs; the entire ZIP and all 32 payload hashes remain executor-verified rather than recomputed in this audit.

**Disposition:** screenshot recovery is accepted; the instrumented suite remains **EMULATOR FAIL**, solely on the Room schema fixture, while six real-model tests are skipped. No schema was invented and no test was weakened. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. No additional run or APK work was started; the one-run contract is closed for audit.

The execution state was reconciled in documentation commit `d389b78a947f2cf92bb8fa412f40f7fb9ad38eb8`. It replaces stale screenshot-pending wording with the current audited result; the original 97-task plan and the next task’s device gate remain unchanged.

---

# Latest task: M00 standard Compose screenshot recovery (2026-10-12)

Code commit: `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1` on `feat/0.5.0-skills-agents`, parent `93409371bac7e6f80d968f7ef084271d474857ac`. Only `ChatHeaderLayoutTest` capture source changed: the synchronized Compose root now uses `captureToImage()` after `waitForIdle`; the three `assertIsDisplayed()` checks, insets/bounds/font-scale checks, and model-before-body check remain. No production UI or Room schema/test changed.

Host: collector tests **7/7 PASS**; `:app:compileDebugAndroidTestKotlin` **BUILD SUCCESSFUL** (54 tasks, 2 executed/52 up-to-date) with Temurin JDK 17.0.20 and the installed Android SDK. No local APK assemble or full JVM suite.

One authorized run [38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844) on the exact code SHA started API 35 x86_64 AVD/ADB and ran `:app:connectedDebugAndroidTest`: XML **16 total, 9 pass, 1 fail, 0 errors, 6 skipped, 10 executed**. The only JUnit failure is the authentic Room v1 schema asset being unavailable (`ENVIRONMENT_UNAVAILABLE`); six model-required tests remain skipped. Both new 1080×1920 original screenshots were opened and visually show all three header lines: standard SHA-256 `9ecef80b3f3f7bf2fa5f7e6981374bfd741efc33863c8ed6e7afca4675a0c4f2`; large-font SHA-256 `47b64cb847d1ba456a733c26b8ecd333b1e959636dd59e87ad0933598214477c`. Thus screenshot evidence is representative; **the emulator suite remains FAIL**.

Same-SHA QA gate run [38186082135](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186082135) failed closed after the instrumentation conclusion; APK build/upload were skipped and no QA artifact was created. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 stays `PENDING_REQUIRES_DEVICE`; no workbook was used. The original 97-task IDs/order/dependencies, `main`, and `v0.4.2` are unchanged.

Detailed run evidence, raw artifact ZIP, XML/logs, screenshot manifest and checksums: [compose-capture-5e93ba6](../validation/android-emulator-ci/compose-capture-5e93ba6/RESULTS.md). Latest device-validation summary: [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md). Stop for independent audit; no further run is authorized under this contract.

---

## Latest M00 capture-transfer recovery (2026-10-11) — EMULATOR FAIL / stop for independent audit

Source code SHA: `49d7ccf11a682d04ad28c400f07e5fb577e573fb`; evidence bundle commit: `455f579cbf0f172d2cd3b6daf93e98b10ad46d8b` (documentation/evidence only). One push-triggered run [38182644492](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644492), job `114598286399`, completed from that exact SHA. API 35 x86_64 AVD/ADB passed and the real `:app:connectedDebugAndroidTest` executed 16 XML cases: **9 PASS, 1 FAIL, 0 errors, 6 SKIPPED; 10 executed**. The only failed test is the unchanged Room migration test, which cannot find the authentic `WorkspaceDatabase/1.json`. Two model-dependent LiteRT cases and four Gemma-native cases remain SKIPPED; no models were downloaded or simulated.

Both ChatHeader `assertIsDisplayed()` tests passed and the collector received two PNGs (1080×1920) through bounded logcat base64 chunks. The large-font capture visibly shows the header; the standard capture is nearly all white and does not represent the rendered header. The transfer mechanism works, but a complete visual validation is **NOT_MET** for the standard case. The cause is not determined and the one-run allowance is consumed; no production UI was changed. The retained Compose diagnostics show standard app-bar bottom/model bottom/body top at 294/263/294 px and large-font values at 407/360/407 px.

The same push started QA run [38182644499](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644499), job `114598286222`, for the exact same SHA. The new gate observed the instrumentation conclusion `failure`; JDK setup, APK build, and upload were skipped. GitHub reports zero artifacts for this QA run. The older QA artifact from run `38178580941` was not downloaded, installed, distributed, or deleted.

One bounded additional public GitHub branch/tag/history lookup found no authentic Room v1 schema: feature branch schema tree contains only version 8; `main` has no v1 JSON; path-specific commit lookup for `WorkspaceDatabase/1.json` returned no commits. The prior 41-object local unreachable scan was not repeated. The migration test and schema stay unchanged; classify as `ENVIRONMENT_UNAVAILABLE`, not a repaired migration.

Host evidence on `49d7ccf`: collector tests 7/7, QA gate tests 6/6, Python compilation and YAML parsing passed, `git diff --check` passed, and `:app:compileDebugAndroidTestKotlin` passed (54/54 tasks executed, 2m53s). No local APK assemble task ran. Full report, archive, XML, screenshots, checksums and sanitized logs are in [run 38182644492 evidence](../validation/android-emulator-ci/diagnostic-runs/38182644492/RESULTS.md); the run-directory manifest is `docs/validation/android-emulator-ci/diagnostic-runs/38182644492/SHA256SUMS`. `EMULATOR=FAIL`; `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. The source 97-task plan, IDs, order and dependencies are unchanged. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

## Previous M00 ChatHeader recovery (2026-10-11; run 38178581012) — superseded by code SHA 49d7ccf

The single authorized run is [38178581012](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178581012), tested code SHA `ff4c9f2f8a5c2c09a240f2e9346856767432489c`, job `114586118884`. The API 35 x86_64 AVD and ADB passed (`emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, 1080×1920, 420 dpi). The real `:app:connectedDebugAndroidTest` ran and exited 1. XML: **16 total, 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed**. The only test failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`, missing authentic `WorkspaceDatabase/1.json`. Preserve the test and schema; the prior full Git history/unreachable-object search found no authentic v1 source, so classify this fixture as `ENVIRONMENT_UNAVAILABLE`, with no invented schema.

Both `ChatHeaderLayoutTest` cases now PASS. The test queries the unmerged Compose semantics tree and retains independent `assertIsDisplayed()` checks for agent, conversation, and model; it also retains bounds, font-scale/inset diagnostics and the model-bottom-before-body assertion. Standard: scale 1.0, 1080×1920 px/411×731 dp, 420 dpi, inset 63 px; app bar bottom 294 px, model bottom 263 px, body top 294 px. Large-font: requested Compose scale 1.6 (resource scale 1.0), same screen/density/inset; app bar bottom 407 px, model bottom 360 px, body top 407 px. Logcat confirms `chat-header-title` merges descendants and that all three child tags/texts appear when using the unmerged tree. No production UI changed.

Screenshot capture remains unresolved. `UiAutomation.takeScreenshot()` returned a 1080×1920 bitmap for each test and the test logged app-cache sizes 45,927 bytes (large-font) and 65,313 bytes (standard). The transfer log value is empty for both; remote checks and `adb pull` each exited 1 because the expected `/sdcard/Download` PNGs do not exist. The artifact contains zero PNGs. Available evidence narrows the loss to cache-to-external/host transfer; it does not identify which shell operation failed because the producer captured neither stderr nor a trustworthy exit marker. No visual screenshot inspection/validation is claimed, and no image was fabricated. The collector now detects/records the absent paths correctly. **The one run is consumed; do not try another transfer approach without a new authorization.**

Two LiteRT tests and four native Gemma chat cases were skipped by real-model availability guards; no model was downloaded or simulated. The XML also proves actual instrumentation ran, even though the post-task package query reports both package IDs absent. Do not treat that post-task snapshot as evidence that install failed during execution.

Host checks on ff4c9f2: collector Python 4/4 PASS; Python `py_compile` PASS; `:app:compileDebugAndroidTestKotlin` PASS (54 tasks, 2 executed/52 up-to-date); `git diff --check` PASS. No local APK assembly was run; the last full JVM suite remains attributed to its earlier SHA and was not rerun here. The exact compile command/log and all hashes are under [run 38178581012 evidence](../validation/android-emulator-ci/diagnostic-runs/38178581012/RESULTS.md). The original 46,460-byte Actions ZIP is SHA-256 `18bf2ab6d03a8b5e7e6907a3869a8c864516b8b3c28781d0712ed9c65bb5df2a`; XML/logcat/screenshot report and the 556,673-byte full job log have per-file hashes in that directory. Its 21-entry durable evidence manifest verifies; after adding the QA workflow side-effect correction, `SHA256SUMS` SHA-256 is `97537205356fc5f5df09c5a12ca9a667bdafa73490169aa143f445e11808d26c`.

`HOST`: targeted checks/AndroidTest Kotlin compilation pass; not a full JVM gate on this SHA. `EMULATOR`: executed, FAIL (Room + screenshot evidence); `PHYSICAL_DEVICE`: `NOT_RUN`. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged and no workbook was used. No APK was assembled locally or downloaded/installed by this executor; the automatically uploaded QA artifact is documented below. `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.



## Automatic QA workflow side effect discovered at readback

The push of `ff4c9f2f8a5c2c09a240f2e9346856767432489c` matched `.github/workflows/qa-debug-arm64-apk.yml` because the source change was under `app/**`. This existing workflow ran automatically (not manually dispatched): [run 38178580941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178580941), job `114586118645`, source SHA `ff4c9f2f8a5c2c09a240f2e9346856767432489c`, conclusion `success`. It uploaded artifact [11716383219](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178580941/artifacts/11716383219), 47,793,824 bytes, API digest `sha256:badd53df1d801ea5164288e876f60c0c662f3edade88913bfd952eb0e591f145`; retention is configured for seven days. The workflow log reports an ARM64 `0.5.0-debug` APK (versionCode 27), 119,297,951 bytes, APK SHA-256 `59a033edc60d7e06300b35b22a3c59cf5d728a4938f4063c602039f61a58e816`, successful signature validation and certificate fingerprint `ab660bd12cb93714ec2c91e1124dca81cf6579c95c909fba5542b78429177c6c`. These APK details have not been independently rehashed because the artifact was not downloaded.

This was an unintended workflow side effect and a sequencing deviation: the artifact uploaded before the emulator run completed with FAIL. This executor did not download, install, or distribute the APK, and did not delete the artifact or launch another workflow. The QA build does not constitute emulator or physical-device validation. The emulator run remains 9 PASS / 1 FAIL / 6 SKIPPED, with screenshot inspection NOT_RUN; the Room fixture is still unavailable.

## Previous M00 recovery result (2026-10-11) — superseded by run 38178581012

The only authorized recovery run is [38171385512](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38171385512), code SHA `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567`, job `114564548056`. API35 x86_64 AVD and ADB passed; `:app:connectedDebugAndroidTest` executed 16 cases and exited 1: **7 passed, 3 failed, 0 errors, 6 skipped; 10 executed**. Failures are the unavailable authentic Room v1 schema and two ChatHeader `assertIsDisplayed()` lookups. Both `AgentsSkillsNavigationTest` cases now pass. This is **EMULATOR: FAIL**; the one-run budget is consumed.

Room history was inspected through all reachable commits and unreachable objects. The oldest tracked WorkspaceDatabase code is already version 8 with schema export disabled; only schema 8 was later exported. No authenticated v1 schema exists in Git history or the scanned unreachable blobs. The migration test remains intact; no schema was fabricated.

The Skills failure was a test race against Room-backed built-in seeding on `Dispatchers.IO`. The updated test waits for the exact `Spreadsheet Analysis` node, scrolls, asserts displayed, and opens it; the new XML confirms both navigation tests passed.

Header diagnostics captured the Compose tree and device metrics. The tree merges descendants on `chat-header-title`, and its combined text contains all three expected lines; child test tags/bounds are absent from the default merged tree. The two `assertIsDisplayed()` calls query those child tags in that merged tree, explaining the assertion failures as a test semantics lookup issue. This does not prove visual clipping. No production UI change was made. The screenshot collector’s local-directory bug is fixed, but both remote screenshot files were absent (remote-check and pull exit 1); the test’s shell-capture output was empty. Screenshot production remains unresolved, and no image was fabricated. Next focused step, after independent review: query header nodes in the unmerged Compose tree while retaining visibility assertions, and repair screenshot capture using a verified method before any visual claim.

The LiteRT `@Test` method is still `Unit`/void, preserves setup/assertions and its exact real-model assumption; both LiteRT device tests were skipped because no model was present. Four real Gemma cases were also skipped. No models were downloaded or faked.

Host evidence is in [run 38171385512 host results](../validation/android-emulator-ci/diagnostic-runs/38171385512/host/RESULTS.md): app JVM 733/733, LiteRT compatibility 2/2, focused collector tests 2/2; debug build, lint (0 errors, 82 warnings) and AndroidTest compilation passed. `PHYSICAL_DEVICE=NOT_RUN`; no workbook/Moto was used. M01-06 remains `PENDING_REQUIRES_DEVICE`; all 97 task IDs, order and dependencies remain unchanged. No further run, APK, main/tag change, merge or release was made.

**APK handling deviation:** the host command included Debug app and test APK assembly and produced local ignored outputs despite the instruction not to generate an APK before the emulator gate passed. Those two new outputs were removed and not uploaded/distributed; the older ignored Release output was untouched. This deviation is documented in the host evidence.

Full artifact/XML/log hashes, run metadata and root evidence manifest are in [the run evidence directory](../validation/android-emulator-ci/diagnostic-runs/38171385512/RESULTS.md). `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.

## Previous Android emulator CI run (2026-10-11; run 38165315028) — superseded by the recovery result above

The single authorized follow-up was tested from code SHA `ee6909d86c9a3477734addc5e933cfc2dfe40fd7` (commits `8fdc40a` and `ee6909d`). Host validation passed: app JVM 733/733 + LiteRT compatibility 2/2, debug assemble, lintDebug and AndroidTest compilation; no host command executed instrumentation. GitHub Actions [run 38165315028](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38165315028), job `114546554444`, used the existing API 35 x86_64 workflow. AVD preflight/boot and ADB passed (`emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, ABI x86_64). The real `:app:connectedDebugAndroidTest` task ran 16 tests and exited 1: **6 passed, 4 failed, 0 errors, 6 skipped; 10 executed**. This is **EMULATOR: FAIL**, not PASS.

Observed failures are: missing authentic `WorkspaceDatabase/1.json`; `AgentsSkillsNavigationTest` cannot `performScrollTo()` the `Spreadsheet Analysis` text; and both `ChatHeaderLayoutTest` cases fail their first `assertIsDisplayed()` on the agent-title node (large-font line 89, standard line 79). The `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` JUnit declaration is corrected and is now skipped by its real-model guard, alongside one other LiteRT smoke and four native-chat cases. No model was downloaded or simulated. The Room test/schema was not altered; reachable Git/workspace evidence did not provide an authentic v1 Room schema, so none was fabricated.

Header bounds, Compose semantics and screenshots were attempted but not retained. The artifact records that screenshot `adb pull` failed because the collector-local `ui-diagnostics/screenshots` directory was absent; filtered logcat has only TestRunner output. Therefore the header root cause remains **NOT DETERMINED** and no production UI change was made. The additional Skills navigation failure was outside the named corrections and was not changed. Collector summary reports packages absent in its post-task scan; XML/Gradle prove the test runner executed, but no persistent installed-package state is claimed.

The exact Actions ZIP, its 28-entry internal manifest, full job log, JUnit XML, summary, host logs and 85 host JUnit XML files are in [run 38165315028 evidence](../validation/android-emulator-ci/diagnostic-runs/38165315028/). Artifact `11711863899`: 40,988 bytes, SHA-256 `ea98cfd8652e092994bab9e9550c64b9b27c790e2abd95c93c8504e47f56a71c`; readable XML copy SHA-256 `7035efbe8b3d2f15a27d7d93972f2bb35fd9d87f3e4f20608867267014ec3b51` (CRLF normalized to LF; exact original artifact XML SHA-256 `ed93b43fd2a8faf0d7f4bdacf0787076e5785a37e592bf32eb22dfc08b58cdbf`); full job log SHA-256 `02e94be88feee10ce453f89d7d8bc48797ff3bafc2dec5e179c913ea7fbb6de3`. The 99-file evidence manifest verifies all retained evidence; its SHA-256 is `3124e2f16d037d87ea39b4b7a8929c213b02cad6b4b3bd37da9c58914bb8d19d`.

This contract's one CI run is consumed; do not retry it. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. The source 97-task plan, IDs, order and dependencies are unchanged. M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola validation is **PHYSICAL_DEVICE: NOT_RUN**, and no XLSX fixture was used. See [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md).

## Previous Android emulator CI run (2026-10-11; run 38156881656)

The previous verification run after the AVD path correction was [GitHub Actions run 38156881656](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656), source/workflow SHA `3c5589e7592cb52a88bb2132b1b30fc30d737e03`, job `114521577365`. The correction pins `ANDROID_AVD_HOME=/home/runner/.android/avd` before creation and uses that same environment for launch. Evidence shows `avdmanager list avd` and `emulator -list-avds` both found `localai-api35-x86_64`; the `.ini` and `.avd/config.ini` existed; preflight passed; the emulator booted and ADB connected as `emulator-5554` (`sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`). This confirms the prior AVD discovery path mismatch is resolved for this runner/run.

The real task `:app:connectedDebugAndroidTest` executed and exited 1. Its one JUnit XML reports **15 total: 7 passed, 4 failed, 0 errors, 4 skipped; 11 executed**. The four failures are: missing Room schema asset `WorkspaceDatabase/1.json`; JUnit `InvalidTestClassError` because `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` is not `void`; and the standard-font and large-font `ChatHeaderLayoutTest` assertions both report “component is not displayed.” Four model-dependent `NormalChatNativeDeviceTest` cases were skipped by source `Assume` guards because no Gemma model is installed; the XML skip-reason text is empty, so this is attributed to the source guard rather than a runner-supplied reason. The job’s post-task package inventory did not find either expected package, and no pre-task inventory was captured; the XML establishes that instrumentation ran, but persistent post-task installation state is not claimed.

The run is **EMULATOR: EXECUTED_WITH_FAILURES / overall FAIL**, not PASS. **PHYSICAL_DEVICE: NOT_RUN.** The APK is not retained or distributed. At that earlier checkpoint, no second CI run was authorized; the later single run is recorded at the top of this handoff. That earlier result required a separate reviewed task for subsequent test changes.

Artifact [11709571451](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656/artifacts/11709571451) (`android-emulator-evidence-38156881656-1`) is 40,894 bytes, SHA-256 `b6152538416c30e5b8d2b94521f36b0344b00ae42044ba994f154b4d8bb7ddd7`; all 27 payload entries in the artifact manifest verify (the ZIP has 28 entries including `SHA256SUMS`), and the durable evidence-directory manifest verifies (manifest SHA-256 `66c7b7732e7bc131912ee79927cf1a5eb6e6527d33871778e5f274b34e9dcdef`). JUnit XML readable copy SHA-256: `346a773d82c8a632c3192f00996a7eecb728a896baa1e2c28f61624bd91a7cd5`; its original artifact entry `instrumented-xml/debug/TEST-emulator-5554 - 15-_app-.xml` has SHA-256 `3cbb54410bcaa7a18c5597f1115ca599712d403a5e8ca0422c985dd5bccd52d4` (the exact original bytes are inside `actions-artifact.zip`). The exact XML, sanitized compressed job log, AVD preflight/environment/listings, original artifact ZIP, run metadata, and manifests are in [the run evidence directory](../validation/android-emulator-ci/diagnostic-runs/38156881656/). Job-log original SHA-256: `529e1e95d45c786e9f39e0fe57df9fc3f176849c5fe813d0ea8a9e0b06a7f730` (558,314 bytes); only the sanitized log is persisted, with one credential-like field redacted. The sanitized decompressed log SHA-256 is `b71a3d67ed067fcaf2ed700349c2d0c9103a3e9b6857160221d6a576f903cdd0`; compressed file SHA-256 is `74b94f7e4c0f0963b026d00f7116c36f1dd1866c7fbb30a62de267029ddff13e`.

No Android product/runtime code changed in this task. The original 97-task plan, order, IDs, and dependencies remain unchanged. M01-06 “Ingesta e indexación reanudable” remains `PENDING_REQUIRES_DEVICE`; no workbook/device test was attempted. The separate fresh-empty-Gradle-User-Home Release attempt remains `INFRASTRUCTURE_FAILURE / RETRY_EXHAUSTED` before Gradle startup; lower-level network cause is `NOT_DETERMINED` and it was not retried. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

# Codex → Work handoff: M01-05 context budget and deduplication

## Historical Android emulator CI reinspection (2026-10-09; superseded by run 38156881656)

The assignment head `ba3117bca90494514cd7f2c78f65d53e69625c1a` was fetched and checked in a clean detached worktree. The Android emulator workflow and evidence collector are byte-identical to the files used at incident SHA `bdae87820b73dbcc0decfde3a65c391c6fb63279`; only documentation was updated. The fresh GitHub job-log response (211,335 characters / 1,541 lines) and newly downloaded artifact 11588945524 (23,012 bytes; SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`) were inspected. All 11 manifest entries verified; job steps show AVD setup failed, connected instrumentation was skipped, XML=0 and executed=0.

The log establishes that `avdmanager create avd` did not abort the shell pipeline and the emulator was launched, but the emulator then reported `Unknown AVD name`; ADB remained empty and `wait-for-device` exited 124. SDK tool paths expand under `/usr/local/lib/android/sdk` in the SDK install step. The AVD-step environment dump does not include the direct values of `HOME` or `ANDROID_HOME`; values for `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, and `ANDROID_SDK_HOME` are also absent. The log additionally lacks `avdmanager list avd`, `emulator -list-avds`, and `.ini/config.ini` inventory, were not captured. The cause of the discovery failure is therefore still **UNPROVEN**. No workflow/script edit and no CI run were made; no speculative path correction was applied.

At that historical checkpoint, state was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the prior attempt failed before instrumentation. This was superseded by the single 2026-10-11 run recorded above, where emulator instrumentation executed and failed four tests. Main and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. M01-06 remains pending and device-required; the source plan of 97 IDs/dependencies is unchanged. Details and hashes are in [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md).

## Historical verification: Release from a clean Git checkout (2026-10-09)

The remote feature branch was fetched at `83affa67584292137a17dd544a4f6e207cf7c96f`. A new detached worktree was created from that exact commit. Before the build, its tracked, untracked and ignored status counts were all zero, and no project `.gradle`, `build`, native `.cxx`, or `local.properties` output existed. The primary checkout remained untouched; its 197 pre-existing ignored paths were excluded from the verification. All 1,865 tracked llama.cpp vendor files matched their Git blobs, including `src/models/models.h`; the model-weight ignore probes passed 12/12.

`:app:assembleRelease -Parm64Only=true --offline --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info` completed with exit 0: `BUILD SUCCESSFUL in 28m 32s`, 128/128 actionable Gradle tasks executed. The log shows fresh `configureCMakeRelease[arm64-v8a]`, Ninja compilation/linking for ARM64, and `buildCMakeRelease` process result 0; neither CMake task was `UP-TO-DATE`. Toolchain: Temurin 17.0.20.1+1, Gradle 8.10.2, Android SDK 35, NDK 27.0.12077973, CMake 3.31.6. The checked-out product code is still `260a3f7c6267cc5a90a9cf516356951aec0627a1`; the intervening paths from that code commit to tested checkout `83affa6` are documentation/evidence only.

The shared offline Gradle User Home `/home/agent/.gradle` supplied wrapper/dependency artifacts; no source, build output, or project cache was copied from the primary checkout. `--no-build-cache` and `--rerun-tasks` were used. Gradle's normal Release outputs were discarded with the isolated build worktree; no APK is retained or distributed. JVM tests, AndroidTest compilation, instrumentation, emulator and Motorola checks are **NOT_RUN**. At that earlier Release checkpoint, Android emulator CI was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current state is recorded at the top of this handoff. M01-05 remains `VALIDADA_HOST`; original task M01-06 remains `PENDING`, `requires_device=true`; the user-supplied 97-task plan and IDs/dependencies were not changed.

Full raw log, clean-tree preflight, task outcomes, summary and checksums are in [clean-checkout Release reproducibility evidence](../validation/clean-release-repro-83affa/RESULTS.md). This supplements, and does not relabel, the earlier `b6c4607` clean build.

**Independent clean-checkout Release audit (2026-10-09):** Accepted as `PASS_HOST_CLEAN_CHECKOUT_RELEASE` for checkout `83affa67584292137a17dd544a4f6e207cf7c96f` (product source `260a3f7c6267cc5a90a9cf516356951aec0627a1`). The independent review verified the remote diff is documentation/evidence only, the detached worktree and llama.cpp tracked-source inventory, the ARM64 CMake configure/Ninja build and exit 0, and all six evidence-file SHA-256 values plus the manifest SHA. No tests, APK delivery, emulator, Android instrumentation or physical-device validation occurred. The next authorized work is diagnosis and proof of the AVD creation/discovery cause from run `37865813921`; do not edit the workflow or relaunch CI before the cause is documented.

**M01-05 evidence publication correction:** Audit of the previous published tree `d170173aee31a9503f5b9a9f539b797e3e202645` found eight raw `.log` files missing from Git although present locally and listed by the manifest. `.gitignore` excluded them. They have been recovered verbatim from the checkout and force-tracked; the manifest is regenerated from the evidence files and checked against the index. This is a documentation/evidence-only supplement; test counts and tested code SHA remain unchanged. The regenerated 114-entry manifest SHA-256 is `33446fd4af5bcf27aac64eeaecb89f7c62dbaeb89a56c8bf589a60fe8d062e78`. The delivery commit is the commit containing this update (see Git history); the post-push remote tree check is reported with this delivery. M01-05 is now `VALIDADA_HOST` by independent audit, limited to host evidence; Android instrumentation, emulator, and physical-device runs remain **NOT_RUN**.

**M01-05:** `VALIDADA_HOST` by independent audit, tested code commit `260a3f7c6267cc5a90a9cf516356951aec0627a1`, base `4c5fce553b007c7e565f55b389a162c9b0ecb880`. Focused tests 70/70; full JVM app 733/733 and LiteRT compatibility 2/2; host build/lint gate accepted. AndroidTest was compile-only; instrumented, emulator, and Motorola execution are **NOT_RUN**.
**Next original backlog item:** M01-06 “Ingesta e indexación reanudable” is **PENDING**, `requires_device=true`; wait for device access and preserve its original position/dependencies from the attached 97-task plan. No backlog file or task ID was reconstructed.
**Android CI gate at that earlier snapshot:** `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. This is historical and superseded by the AVD-path correction and run 38156881656 described at the top of this handoff.
**M01-05 report:** [Context budget and relevance deduplication results](../validation/m01-05/RESULTS.md). Raw logs, red/green XML, summary JSON and checksums are under `docs/validation/m01-05/`.
**M01-05 code commit:** `260a3f7c6267cc5a90a9cf516356951aec0627a1`; independently accepted as `VALIDADA_HOST` only. **Prior evidence commit:** `b086adf8fad3e37d718d9c41e3499384bc83c7ef`; its published tree omitted eight ignored logs, now restored by the current documentation-only supplement. The corrected manifest has 114 entries and SHA-256 `33446fd4af5bcf27aac64eeaecb89f7c62dbaeb89a56c8bf589a60fe8d062e78`. The supplement commit SHA is the current delivery commit reported in the final handoff.
**Task-plan source:** The original user-provided attachment containing 97 tasks remains the source of IDs, order and dependencies. The repository has no `ROADMAP.md`, `BACKLOG.json`, or `LOOP_RULES.md`; none were reconstructed or changed.

**Earlier QA/M00 status (unchanged):** The QA-only ARM64 Debug build was independently accepted as `VALIDADA_HOST_QA_BUILD`. The focused Motorola preflight on 2026-10-09 is `BLOCKED_PREREQUISITES_UNAVAILABLE`: ADB found no device and the original workbook is absent. No APK was downloaded or installed in that attempt; all physical checks are **NOT_RUN**. At that 2026-10-09 preflight checkpoint, Android emulator CI was separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the latest result is recorded above.
**Repository:** `https://github.com/javix142-gif/local-ai-workspace.git`
**Branch:** `feat/0.5.0-skills-agents`
**QA APK source SHA:** `4ef2b39b9cba6aa1def081171d42d9146bbd968c` (QA build workflow and verification script; app/runtime behavior was not changed for this artifact task).
**Android CI attempt SHA:** `bdae87820b73dbcc0decfde3a65c391c6fb63279` (separate emulator workflow attempt; instrumentation NOT_RUN).
**Accepted preceding source SHA:** `b6c460763b19de41e689c493859bc1ee649faf2c` (`.gitignore` only; ignore matrix 17/17 and clean-checkout `assembleRelease` PASS, accepted by independent documentary audit).
**Accepted preceding source-build evidence commit:** `b5fa6a2062f7aed568f33bc66c2e4ea229f28a66`.
**Most recent complete JVM gate:** `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`, app 727/727 + compatibility 2/2 = 729/729 PASS; historical, not rerun on `b6c4607`, `bdae878`, or the QA workflow source `4ef2b39`.
**AndroidTest compilation/instrumentation:** final emulator workflow run `37865813921` failed before `:app:connectedDebugAndroidTest`; XML 0, executed 0. The separate QA artifact below is a Debug APK, not evidence of instrumentation. **PHYSICAL_DEVICE:** NOT_RUN.
**Baseline:** `v0.4.2` → `de615b772cf37a99e7e08a647c59607b271de606`
**App:** `com.localai.workspace`, version `0.5.0` (`versionCode 27`).

## Historical delivery: QA ARM64 Debug APK (2026-10-09)

The [GitHub Actions artifact ZIP](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37874837502/artifacts/11592306483) contains `local-ai-workspace-0.5.0-debug-arm64.apk`, `RESULTS.json`, `SHA256SUMS`, and the sanitized build log. Run `37874837502` completed successfully from source SHA `4ef2b39b9cba6aa1def081171d42d9146bbd968c`; job `113640935577` ran `:app:clean :app:assembleDebug -Parm64Only=true` using the project-pinned Android toolchain. The artifact is retained for seven days through `2026-10-16T02:37:56Z`.

- APK: 119,281,567 bytes; SHA-256 `287ac97d6ce4e0563d1618e59726177355673321e401085c6383fe19b9ba4650`.
- Package/version: `com.localai.workspace.debug`, `0.5.0-debug`, versionCode 27.
- Signing: `apksigner` verified the standard Gradle Debug signature (v2); certificate SHA-256 `56d5fb674b00a7aa2c86ff4835690e5b1ef3a1fcc525b08f2a1ec0c355129ffa`. The ephemeral debug keystore was not exported.
- ABI: `arm64-v8a` only, 9 native libraries. The artifact ZIP is 47,787,296 bytes; SHA-256 `5ef240ea08899079e85835bcd2778eeda2b5bd11abde5472f113a974bf6884a4`.
- `RESULTS.json`, log, and member checksums match the downloaded artifact. Full hashes, exact command, toolchain, and verification details are in [QA ARM64 artifact results](../validation/qa-debug-arm64/RESULTS.md).

Status for this delivery: **HOST build/artifact verification PASS**; JVM tests not run for this task; **EMULATOR NOT_RUN**; **PHYSICAL_DEVICE NOT_RUN**. Emulator CI remains blocked as described below. No APK is tracked in Git, and no application/runtime changes were made for this QA delivery.

## Historical M00 Motorola physical validation preflight (2026-10-09)

Preflight stopped before APK download, installation, or testing because both required inputs were not available. The SDK ADB executable `/workspace/.toolchain/android-sdk/platform-tools/adb` ran `adb devices -l` successfully but returned only `List of devices attached` with no serials. Therefore no Motorola identity could be confirmed and `getprop` was not run. Searching `/workspace`, `/tmp`, and `/mnt/data` for `.xlsx`, `.xlsm`, and `.xls` found no original user workbook. The only relevant candidate was `docs/validation/part2/moto-fixtures/sales.xlsx`, a synthetic fixture, which was deliberately not used as a substitute. The exact user workbook had also been recorded as unavailable in the earlier M00 handoff.

Consequently, artifact download/hash recheck, installed-package check, install, workbook import, M00 context/provenance/notice checks, keyboard/header check, and cold/warm Motorola timing are all **NOT_RUN**. This is a prerequisite block, not a product test failure. The detailed read-only evidence is in [M00 Motorola preflight results](../validation/m00-physical-device/RESULTS.md); machine-readable state is `EXECUTION_STATE.json` → `physicalDeviceResults.latestM00FocusedPreflight`. The accepted QA artifact and its hashes remain recorded separately above.

## Historical Android CI follow-up (2026-10-09)

The workflow and its evidence are described in [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md). Final source commit `bdae87820b73dbcc0decfde3a65c391c6fb63279` was tested by [Actions run 37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921), job `113612155627`, artifact [11588945524](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524). GitHub-hosted `ubuntu-24.04` had `/dev/kvm`; the workflow enabled access and the KVM verification passed. `avdmanager` returned through its pipeline and the script launched the emulator, but the captured output contains no AVD path, list, or creation confirmation. The emulator reported `Unknown AVD name`, ADB had no connected emulator and its 600-second wait timed out. Instrumentation was skipped by the job dependency and no JUnit XML exists. `summary.json` correctly records `FAIL`, zero tests, and physical validation `NOT_RUN`.

### AVD cause investigation

The full 23,012-byte artifact was downloaded and all 11 entries in `SHA256SUMS` verified. Its SHA-256 is `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. `android-toolchain.txt` resolves both manager executables under `/usr/local/lib/android/sdk`, and confirms the API 35 `google_apis;x86_64` image revision 9, emulator 37.2.12 and platform-tools 37.0.1. The literal `ANDROID_HOME` value is not printed, although the workflow derives these paths from it.

The AVD step does not set `ANDROID_AVD_HOME`, `ANDROID_USER_HOME`, `ANDROID_SDK_HOME`, or `HOME`; the runner log does not record their effective inherited values for that step. The checkout action's temporary `HOME` override applies only during checkout and does not identify the later AVD-step value. The emulator's message prints unexpanded variable-name templates, not resolved search paths. The artifact contains no `avdmanager list avd` or filesystem inventory for `.ini`/`config.ini`. Thus the evidence proves that a usable named AVD was not discoverable, but **does not prove** whether `avdmanager` wrote it elsewhere, created an incomplete entry, or encountered another condition. The local SDK lacks the API 35 image and emulator package, so no local reproduction was attempted. No workflow edit and no further Actions run were made. A fixed `-p` path/preflight is an unverified hypothesis, not a root-cause fix.

The run summary remains: connected device false; Gradle task NOT_RUN/exit null; XML 0; total, passed, failed, errors, skipped, executed all 0; `EMULATOR` instrumentation NOT_RUN; `PHYSICAL_DEVICE` NOT_RUN. See the RESULTS report for the command, hashes and entry-level manifest.

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

## Historical next step from the prior handoff (superseded)

Resume the focused M00 Motorola validation only when the exact original XLSX is accessible and a Motorola Moto G86 Power is connected and ADB-authorized. Then use only the accepted artifact from run `37874837502`, recheck its ZIP/APK hashes before installation, and stop if `com.localai.workspace.debug` is already installed. Do not substitute the synthetic fixture or another device. Until those prerequisites are met, `PHYSICAL_DEVICE` remains **NOT_RUN**. At that historical preflight, Android emulator CI was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current run and classification are recorded above.

## Independent orchestration audit: Android AVD blocker (2026-10-09 07:36:16 UTC)

Audit record: `docs/validation/android-emulator-ci/RESULTS.md`, appended for audited feature head `a18f1fc99be211fe2346799218233548a15adf16`.

The executor's bounded AVD-cause investigation is independently confirmed as `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`: GitHub reports AVD setup failure, instrumentation skipped, and zero XML/tests; its job log records the AVD create/launch commands and a 600-second `adb wait-for-device` timeout, but does not establish whether creation was partial/absent or used an undiscovered path. No workflow fix or new CI run was made. No new executor instruction is assigned while this evidence boundary remains unchanged.

The current audit checked the one-commit fast-forward comparison from `ba3117bca90494514cd7f2c78f65d53e69625c1a` to `a18f1fc99be211fe2346799218233548a15adf16`; only documentation files changed. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. The auditor did not execute local Android tests and could not recompute artifact ZIP/internal hashes from the connector's returned file reference; see the audit limitation recorded in RESULTS.md. The original 97-task plan remains unchanged. Resume this blocked track only when new evidence or a bounded diagnostic opportunity can distinguish the AVD creation/discovery cause.


## Historical clean-home ARM64 Release reproducibility attempt (2026-10-09)

This bounded attempt used a fresh detached worktree of remote `feat/0.5.0-skills-agents` SHA `4559b502c55497f9f3c932c3e428f708fb086fd2`; the branch had not advanced from the assigned SHA. The checkout had zero tracked, untracked, or ignored paths before the command. All 1,865 tracked llama.cpp files matched disk, including `src/models/models.h` blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`; all 15 model/binary ignore probes were ignored.

The one requested invocation was `./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info`, with Temurin JDK 17.0.20.1+1, SDK 35, NDK 27.0.12077973, CMake 3.31.6 and a new empty `GRADLE_USER_HOME`. At `2026-10-09T08:31:52Z`, the wrapper attempted to download pinned Gradle 8.10.2 from `services.gradle.org`; it received `java.net.ConnectException: Connection refused` and exited 1 at `08:31:58Z`. The Gradle distribution did not start: zero Gradle tasks ran, CMake/Ninja were not reached, and no APK was generated. No retry or cache/source copy was made. This is a **network-blocked attempt**, not an application build failure or PASS; the previous `83affa6` clean Release PASS remains preserved under `previousCleanReleaseRepro` in `EXECUTION_STATE.json`.

Raw log, preflight, toolchain, failed wrapper-home inventory, task outcomes, remote refs, summary and checksums are in [the 4559b5 clean Release attempt](../validation/clean-release-repro-4559b5/RESULTS.md). The remote feature HEAD was still `4559b502…` after the command; `main` and peeled `v0.4.2` were still `de615b772cf37a99e7e08a647c59607b271de606`. No JVM, AndroidTest, emulator, Android CI or Motorola work was performed. Android/emulator/instrumented/physical validation remain **NOT_RUN** for this attempt; the separate Android CI status remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. The original 97-task plan is unchanged and M01-06 remains pending because `requires_device=true`.

## Independent audit: clean-home Release network block (2026-10-09 09:35:53 UTC)

Audit record: `docs/validation/clean-release-repro-4559b5/RESULTS.md`. For tested checkout `4559b502c55497f9f3c932c3e428f708fb086fd2`, the detached-worktree/source preflight and 13/13 evidence hashes were independently read back and verified. The raw wrapper log establishes `Connection refused` while fetching pinned Gradle 8.10.2; Gradle task graph=0, CMake/Ninja not reached, APK not generated. This is `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`, not a build result. No retry or cache/source copy was made under the active contract. The earlier clean Release PASS for `83affa6` remains separately scoped to that SHA. Feature branch at the evidence delivery was `fcae468dc2a776d6e86709547a8bc3f295d1063d`; main/tag stayed at `de615b772cf37a99e7e08a647c59607b271de606`. Android and Motorola remain `NOT_RUN`.


## Final empty-home ARM64 Release attempt (2/2; 2026-10-09)

The assigned remote SHA `6d1b256b23c4e76dad72413ced9b10419425c134` was fetched and compared with the preceding evidence head `fcae468dc2a776d6e86709547a8bc3f295d1063d`; intervening changes were documentation only. A fresh detached worktree was clean before the build. All 1,865 llama.cpp files matched Git, including `src/models/models.h` blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`, with 16/16 model/binary ignore probes passing.

The one and final authorized command was `./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info` with a newly empty `GRADLE_USER_HOME`. It ran from `2026-10-09T21:50:17Z` to `21:50:23Z`, exit 1. The wrapper tried to fetch Gradle 8.10.2 from `services.gradle.org` and got `java.net.ConnectException: Connection refused`. The tool environment reported `connected` with an enforced unrestricted network policy; this does not establish the lower-level cause of the refused socket. Gradle did not start, zero tasks ran, CMake/Ninja were not reached, and no APK was generated. No retry, alternate source, or cache was used. This is **not a PASS and not an application compilation failure**; fresh empty-home Release reproducibility remains unverified.

The complete run record and manifest are in [the final 6d1b25 attempt](../validation/clean-release-repro-6d1b25/RESULTS.md). Remote feature SHA remained `6d1b256…` through the build; `main` and peeled `v0.4.2` remained `de615b772cf37a99e7e08a647c59607b271de606`. The prior blocked attempt at `4559b502…` and its independent audit are preserved as history in `EXECUTION_STATE.json`; the earlier successful clean Release check at `83affa6…` also remains separately scoped to its SHA.

No JVM, AndroidTest, emulator, Android CI or Motorola validation was run. Those statuses remain **NOT_RUN** (Android emulator CI separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`). The original 97-task plan is unchanged; M01-06 remains `PENDING_REQUIRES_DEVICE`. This contract's two empty-home attempts are exhausted.


## Independent orchestration audit: final empty-home ARM64 Release attempt (2026-10-09 22:56 UTC)

**Status: `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`.** I reviewed the final attempt at checkout `6d1b256b23c4e76dad72413ced9b10419425c134` from GitHub evidence only; I did not run Gradle locally. GitHub comparison confirms evidence commit `7b1997589328852ff81dddc2ce0355b1342e1913` is one fast-forward, documentation/evidence-only commit after the tested SHA. The application source baseline is `260a3f7c6267cc5a90a9cf516356951aec0627a1`.

The published preflight reports an exact-SHA clean detached worktree, empty ignored/tracked/untracked status, 1,865/1,865 vendored llama.cpp files matching Git (including `models.h`), and 16/16 model/binary ignore probes. This is artifact-backed evidence, not a local reproduction by the auditor. I independently fetched and recomputed the SHA-256 of all 17 files in the evidence manifest: 17/17 match. The raw wrapper log SHA-256 is `3caee44390cf5c8ccec0423a07baf95079d50a7112b328b126f003501b76fe6c`; it records `Connection refused` while downloading Gradle 8.10.2. Gradle did not start (0 tasks), CMake/Ninja were not reached, and no APK was generated. Although the environment reported connected/unrestricted policy, the lower-level cause of the refused socket remains NOT DETERMINED. The two authorized attempts are exhausted; this is not an application build failure or PASS.

GitHub refs still show `main` and peeled `v0.4.2` at `de615b772cf37a99e7e08a647c59607b271de606`. JVM, AndroidTest, emulator and Motorola execution remain NOT_RUN; At that historical attempt, Android CI was separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current run is recorded at the top. The original 97-task plan is unchanged. Updated results SHA-256: `c5f7bc2f5f77d3731179b9188c4567e4150d0294ef39827e97926df308d4615f`; refreshed 17-entry manifest SHA-256: `b92cb4d60e103f65326965d32c7951d70f55a8ad02f9fedc6c1ea74a4345c8c4`.

No further clean-home build retry is assigned under this exhausted contract. Continue only with other original tasks whose prerequisites are satisfied; do not claim clean-release reproducibility or Android validation from this attempt.

## Historical bounded follow-up: Release evidence classification and AVD diagnostics (2026-10-11)

At fetched feature HEAD `fcad2eca9bfb4d0b286e1508f1eaeb67ab726a7b`, the fresh empty-Gradle-home Release path remains `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED`: both authorized wrapper attempts stopped before Gradle startup with `Connection refused` fetching Gradle 8.10.2; lower network cause is `NOT_DETERMINED`. The separate `83affa67584292137a17dd544a4f6e207cf7c96f` result is a historical ARM64 `assembleRelease` PASS only with pre-provisioned shared Gradle dependencies (`--offline`), 128/128 tasks, and CMake/Ninja success. It is not an empty-home result and no APK was retained. The current ARM64 Actions artifact is run `37874837502`, explicitly `:app:assembleDebug` and QA/debug-signed; it is not Release evidence. Details are in `EXECUTION_STATE.json` → `latestGradleEvidenceClassification` and the AVD [results report](../validation/android-emulator-ci/RESULTS.md).

At this pre-run checkpoint, the original AVD artifact established only that the named AVD was undiscoverable, ADB was empty, instrumentation was skipped, and XML/executed counts were zero. A diagnostics-only workflow/collector change was then prepared without changing AVD creation/name/path/launch. The single authorized diagnostic run 38154790618 later demonstrated the AVD home/path mismatch; the subsequent path correction and instrumented run 38156881656 are retained in history, and the current run 38165315028 is recorded at the top of this handoff. These historical steps do not change the separate Motorola state; M01-06 remains `PENDING_REQUIRES_DEVICE`, and the original 97 task IDs/dependencies are unchanged.

## Historical Android AVD diagnostic run before the path correction (2026-10-11)

The diagnostics-only workflow source `d0d4d7608c8e8149bdb05d003da2742260c1b8dd` was run once by push: [run 38154790618](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618), job `114515427123`, artifact [11708477219](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618/artifacts/11708477219). SDK/API 35 and KVM setup passed. `avdmanager create` exit=0 and pipeline=`0 0 0`; `avdmanager list avd` exit=0 lists the named AVD under `/home/runner/.config/.android/avd/...`; `emulator -list-avds` exit=0 is empty. `HOME=/home/runner`; both SDK variables resolve to `/usr/local/lib/android/sdk`; `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, `ANDROID_SDK_HOME` are unset. The recorded emulator candidate `/home/runner/.android/avd` does not exist, and emulator logged that the expected `.ini` was missing there. The path divergence demonstrates the AVD discovery cause; why the manager selected `.config` remains unknown. The inventory did not inspect the alternate `.config` path, so `.ini/config.ini` there are not independently hashed. Do not change or rerun the workflow before independent audit.

Run result is **FAIL before instrumentation**, not Android PASS: ADB wait timed out with exit 124; connected tests were skipped; Gradle task NOT_RUN, XML=0, executed=0; physical validation NOT_RUN. Full artifact ZIP and the sanitized job log are preserved and checksummed under `docs/validation/android-emulator-ci/diagnostic-runs/38154790618/`. See `EXECUTION_STATE.json` → `latestAndroidCi.diagnosticFollowUp` for run/source/artifact hashes and layer-separated statuses.

Release classification stays separate: empty Gradle-home attempts are `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED`, lower-level cause `NOT_DETERMINED`; the older `83affa6` Release success used a pre-provisioned shared Gradle cache; Actions artifact `37874837502` is Debug ARM64 only. No build was run here. Main/tag are unchanged, M01-06 remains `PENDING_REQUIRES_DEVICE`, and the original 97-task plan is unchanged. This executor stops after this run for independent audit.
