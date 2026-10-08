# Local AI Workspace

Local AI Workspace is an Android, local-first workspace for private on-device assistance. Once models and optional runtimes are installed, chat, project files, retrieval and user-approved memory work on-device. Network use is limited to explicit model downloads and other user-initiated online flows.

**Development version: 0.5.0 · versionCode 27.** Frozen return point: annotated tag
`v0.4.2`, commit `de615b772cf37a99e7e08a647c59607b271de606`.

0.5.0 adds four procedural Skills, user-authored/imported SKILL.md, logical Agents
sharing the existing model, project preferences and deterministic routing. It fixes
production document retrieval coverage, selected-document candidate scoping and
arbitrary unmatched evidence. See [Skills / Agents architecture](docs/SKILLS_AGENTS_ARCHITECTURE.md)
and [status](STATUS.md) for verification and device limits. No semantic/LLM Skill router,
Agent loops, MCP or cloud connectors are implemented.

## Current capabilities

- Gemma 4 E2B through LiteRT-LM 0.17.1, plus GGUF models through the vendored llama.cpp runtime.
- Workspace projects, chats, local documents and citations.
- Structured, user-approved memory; Context Builder V1; Semantic Layer V2 with EmbeddingGemma 300M and selectable EmbeddingGemma 2 740M providers.
- Local hybrid retrieval, calculator and bounded file-reading tools, Thinking modes, image input for compatible LiteRT models, audio-file input, and an opt-in constrained Python runtime.
- Developer validation suites and performance/context diagnostics.

Availability depends on the selected model, runtime and app path. Experimental or device-specific performance is not implied by a model card or a host test. See [the architecture overview](docs/ARCHITECTURE_OVERVIEW.md), [current status](STATUS.md), [0.4.2 report](docs/MEMORY042_REPORT.md), and [external artifacts](docs/EXTERNAL_ARTIFACTS.md).

## Build

Requirements are pinned in the Gradle files: JDK 17 to run Gradle and JDK 21 for the app JVM test worker, Gradle wrapper 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Android SDK API 35, NDK 27.0.12077973 and CMake 3.31.6. The app targets API 35 and supports API 29+.

```sh
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

For the ARM64 device build, add `-Parm64Only=true`. Gradle downloads dependencies on first use. The project includes a Gradle wrapper; do not commit local SDK paths or generated build output.

## Repository boundaries

GitHub is the canonical source-code history. Large external artifacts are not stored in Git: Gemma/EmbeddingGemma weights, APK/AAB packages, Gradle/NDK build output, caches, raw device exports and source backup archives. Their known names, provenance, sizes and hashes are recorded in [docs/EXTERNAL_ARTIFACTS.md](docs/EXTERNAL_ARTIFACTS.md). The Pyodide WebAssembly files under `app/src/main/assets/python/` are application runtime assets, not model weights, and are required by the opt-in Python feature.

The upstream llama.cpp source used by the Android build is vendored under `third_party/llama.cpp` at the documented upstream revision. See `THIRD_PARTY_NOTICES` and the component licenses before redistribution.
