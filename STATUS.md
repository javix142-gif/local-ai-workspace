# Current source status — Local AI Workspace 0.5.0

- Development branch: feat/0.5.0-skills-agents. versionName 0.5.0, versionCode 27.
- Frozen baseline: v0.4.2 → de615b772cf37a99e7e08a647c59607b271de606. No main merge or new release tag.
- IMPLEMENTED: retrieval integrity prelude, four built-in/custom/imported Skills, logical Agents, additive sidecar, deterministic router, budgeted Context Builder integration, management and safe metadata diagnostics.
- TESTED_HOST (0.5.0 baseline): 661 app tests + 2 compatibility tests; see the historical evidence below.
- PRE-LOOP STABILIZATION: M00-1..M00-4 code/tests implemented on `feat/0.5.0-skills-agents`. Final host gate: 686 app tests + 2 compatibility tests pass; debug, release, lint and AndroidTest APK build pass. The original physical XLSX is unavailable and the emulator cannot run here; Moto checks remain pending. See [CODEX_TO_WORK_HANDOFF](docs/development/CODEX_TO_WORK_HANDOFF.md) and [EXECUTION_STATE](docs/development/EXECUTION_STATE.json).
- AGENTS_SKILLS_V1 (12 checks) and Android navigation tests: NOT_TESTED_DEVICE. No physical performance or native-success claim from Linux tests.
- Existing historical / Semantic V2 validation catalogs remain intact. Retrieval and Context Builder production changes require targeted Motorola validation.
- Deferred: semantic/LLM routing, Agent loops/handoffs, MCP, connectors, automation, plugins, executable imports, second LLM.

See [architecture](docs/SKILLS_AGENTS_ARCHITECTURE.md). Models/APKs/caches remain outside Git.

## Master optimization program — current H1 checkpoint (2026-10-20)

Current product code SHA `01161491acc2a7c836222a84b7bd6f778292f8f3`; the feature HEAD after documentation-only commits is `16287d649eabf063fb4482d302060f378b2ff35a`. H1 is `HOST/CI_VERIFIED`, with device/manual checks still pending: connected emulator run 39511660788 passed its selected suite (16 PASS, six model-backed SKIPPED, no failures); H1-AT-12 used real `adb install -r` and preserved synthetic data. The gated QA ARM64 run 39511660726 produced a verified Debug APK for the same code SHA. The full host suite (941/941 + 2/2) remains historical at `aed4d8f`; the H1-focused tests were rerun at `16287d6` (25/25). Physical/Motorola checks and model performance remain NOT_RUN. The user authorized beginning H2 sequentially while those blockers remain tracked. See [current development status](docs/development/STATUS.md) and [H1 evidence](docs/validation/master-program-h1-20261020/RESULTS.md).

### Historical H0 host checkpoint

H0 host stabilization at code 49f5913937fe7c049bf0ad71d2d75f7e5321b557: 916 app JVM + 2 LiteRT compatibility tests passed; ARM64 lint, AndroidTest Kotlin compilation and Debug ARM64 assembly passed. Instrumented/device tests were not run. A locally built QA APK is not update-compatible because the previous Debug private key is unavailable, so it was not uploaded or installed. Original catalog remains unchanged (97 IDs, 120 dependencies, all TODO); no source task was closed. See [H0 evidence](docs/validation/master-program-h0-stabilization-20261020/RESULTS.md).
H0 source/evidence publication read back from feat/0.5.0-skills-agents at d7baa4d5e56caf0ee3d826aab39994150bcaac43; main and v0.4.2 remain at de615b772cf37a99e7e08a647c59607b271de606.
