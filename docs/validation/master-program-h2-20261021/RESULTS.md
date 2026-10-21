# H2 delivery and M00-04 focused verification

Date: 2026-10-21. Product/test source: `a65a80dae1cf7edc3ae42afeb76620f66bfdc309` on `feat/0.5.0-skills-agents`. This report records the H2 implementation, its exact-SHA Actions runs, local HOST evidence, and a separate focused M00-04 check. It does not close source backlog tasks.

## H2 implementation

H2 adds bounded local `calendar.read` and `contacts.lookup` tools, requests Android read permission at point of use after an explicit chat action, marks returned data untrusted, and redacts personal query/result values from persisted tool history. Routing remains deterministic; email requests do not route to phone-only contact lookup. Existing Android action proposals retain explicit user confirmation. The compact artifact tool description retains DOCX ordered-list syntax and other format boundaries.

## HOST results

Environment for the recorded local runs: Linux x86_64, Gradle 8.10.2, Gradle runtime Temurin 17.0.20.1 at `/workspace/.toolchain/jdk-17`, test toolchain Temurin 21.0.12.1 at `/workspace/.toolchain/jdk-21-temurin`, Android SDK API 35 and NDK 27.0.12077973 under `/workspace/.toolchain/android-sdk`; existing Gradle cache, offline.

- The full app JVM run produced 960 tests: 959 passed and one failed in `DocxArtifactStructureTest.generatedDocxEmitsOrderedListNumberingAndKeepsBulletList` at `DocxArtifactStructureTest.kt:185`. The shortened `artifacts.create/revise` description had omitted DOCX list/style syntax. The description was restored compactly. The affected class then passed 4/4 in later focused runs. The full 960-test suite was not rerun after that correction: the final full-suite gate is **NOT ESTABLISHED**.
- An intermediate 18-test focused run failed one assertion in `AndroidPersonalDataToolsTest.toolSchemasAndDeterministicRoutingExposeOnlyRelevantPersonalReadsAndActionHandoffs` (reported line 110). A later combined run on the H2 source passed its 18 tests; a separate focused run passed 19/19 with 19 start/end events. The exact cause of the intermediate discrepancy was not established. No test was removed or weakened. The XML from the 19-test run was not retained before the subsequent M00-04 invocation replaced Gradle's result directory; the event log is retained and is not represented as JUnit XML.
- The later combined 18-test run passed and `:app:lintDebug` plus `:app:compileDebugAndroidTestKotlin` completed successfully in 4m27s (exit 0). Lint reported 0 errors, 128 warnings, and 1 informational finding. The combined log did not preserve the original shell invocation, so this report records observed Gradle tasks and outcomes, not an inferred literal command.
- LiteRT compatibility tests passed 2/2 in a separate run; this module was not modified by H2.
- Current-source M00-04 focused HOST run passed 2/2 in 34 seconds (Gradle exit 0): material omission produces a comprehensible notice and the Inspector shows the dropped reason; routine below-threshold omission remains diagnostic-only. The exact command, environment, raw log, two JUnit XML files, and checksums are in [`m00-04-focused/`](m00-04-focused/).

## EMULATOR

Run [39520261327](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520261327), source SHA `a65a80d`, passed `:app:connectedDebugAndroidTest`. API 35 / Android 15 / x86_64 AVD, 1080×1920 at 420 dpi, ADB serial `emulator-5554`. JUnit: 22 total, 16 passed, 0 failed, 0 errors, 6 skipped; the six skips are two LiteRT-native and four Gemma-native cases requiring real model weights. No weights were downloaded. H1-AT-12 also passed an `adb install -r` synthetic-data update check, preserving 20 synthetic memories, a project, a chat/message, and the context preference. This is emulator-only evidence.

The historical missing authentic Room v1 schema has not been resolved or claimed as passed here. This run exercised the current exported v8-to-current migration case; it does not establish v1-v7 migration behavior.

Full emulator artifact: GitHub artifact **12312145804**, 166,575 bytes, SHA-256 `bd5563ad852f551c9a662c50b8fb5031bc10a985e22e457a107c8568c81a9ba3`, expires 2026-10-28. The downloaded ZIP and its 50-entry internal manifest were verified locally; the preserved copy is [`emulator/github-artifact-12312145804.zip`](emulator/github-artifact-12312145804.zip).

## PERSONAL_QA ARM64 APK

Personal QA run [39520260941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520260941), job `118962107767`, passed on the exact H2 SHA. Artifact **12310364342**, `local-ai-workspace-personal-qa-debug-arm64-39520260941-1`, 48,987,457 bytes, API SHA-256 `277d90d978117326d5387e93e8c54a630cc0784208f2787465abdad9dd3775eb`, expires 2026-11-20. The downloaded artifact ZIP matched that digest and its five payload checksums passed.

APK `local-ai-workspace-0.5.0-debug-arm64.apk`: 121,635,467 bytes; SHA-256 `b2860ab3db809ab90bfc91078ab6246dd3dabd480ce5825fc31b2f17f0567d00`; `com.localai.workspace.debug`, version `0.5.0-debug`/27; only `arm64-v8a` (9 native libraries). apksigner v2, ZIP CRC, zipalign, package/version, ABI-only and no-model-payload checks passed. Debug certificate SHA-256: `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. This is PERSONAL_QA, not Release or production.

The APK is in Drive as a ZIP container because the raw APK exceeds the direct-upload size limit. The container contains exactly the APK and no other files: [download APK-only ZIP](https://drive.google.com/file/d/16LuVNvAsd_3WxUQn7wQKyzXwu9glMDLP/view?usp=drivesdk), Drive ID `16LuVNvAsd_3WxUQn7wQKyzXwu9glMDLP`, 47,264,703 bytes, SHA-256 `1432e93a432a936f07221236bd3135554c78e3c5f36c34f69e08bfd759deeb8b`. Drive readback confirmed the name, size and private folder. Extract the APK before installing.

The separate gated QA run [39520260812](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520260812) also completed PASS after the matching emulator run. Its artifact metadata was read, but that artifact was not independently downloaded; the PERSONAL_QA artifact above is the independently downloaded and verified delivery.

## Scope and next dependency

`PHYSICAL_DEVICE=NOT_RUN`; no Motorola Moto G86 was connected. No real-model inference, TTFT, tokens/s, RAM/PSS, energy, thermal, or quality measurement is claimed. The original backlog and roadmap remain unchanged: 97 IDs, 120 dependency edges, all source statuses TODO. M00-04 has focused HOST evidence but no source status change. M00-06 is the next original host-eligible task under its existing DoD; its existing traces cover distinct import/index and generation stages, and the missing end-to-end correlation must be inspected before any further implementation. Device measurements remain a genuine external dependency.

`main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.
