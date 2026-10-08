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
