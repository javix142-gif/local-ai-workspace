# Architecture overview — 0.4.2

This note describes the checked-in implementation. Model files, Android toolchains and generated databases are user/device artifacts, not repository inputs.

## Modules

- `:app` — Jetpack Compose Android application, workspace UI, ViewModels, Room persistence, document ingestion, retrieval/context, tools, Python service, validation and runtime adapters.
- `:litert-compat` — a small pinned Kotlin compatibility surface for LiteRT-LM 0.17.1, including the native libraries extracted from the official Android AAR at build time.
- `:llama-runtime` — GGUF inference and EmbeddingGemma 300M JNI wrappers; native Android build scripts consume the vendored `third_party/llama.cpp` tree. The vendored upstream commit is `99b95488cac0f00ce3f05af113a8c1e287753f87`.

There is no separate domain-service process for every subsystem. Android declares LiteRT inference in process `:litert` and the constrained Python WebView service in process `:python`.

## Chat and inference flow

`MainActivity` owns Compose navigation; screens call ViewModels in `ui/`. `AppGraph` composes the database, workspace repository, runtime registry, preparation/lifecycle managers, retrieval, tools and diagnostics. `ChatViewModel` builds a request from the selected chat/project, current model and attachments, then routes it through the shared inference coordination and the selected `InferenceRuntime`.

The LiteRT path uses `LiteRtLmInferenceRuntime` and `LiteRtLmService` with `:litert-compat`; conversation/session reuse and generation diagnostics live in `domain/inference` and `inference`. GGUF uses `LlamaCppInferenceRuntime` backed by `:llama-runtime`. Both paths share app-level request, persistence and presentation contracts. CPU is the validated production default; GPU remains experimental. Exact runtime support is model-dependent.

## Storage and context

`WorkspaceDatabase` stores workspace entities and currently has Room schema version 8 with additive migrations defined in `data/WorkspaceDatabase.kt`. Documents are imported into app-private storage; the database stores metadata and extracted/indexed text rather than the model binary.

Semantic Layer V2 has its own `semantic_v2.db` store for sources, segments, embeddings, index jobs and active indexes. `SemanticLayer` coordinates providers, indexing and retrieval. EmbeddingGemma 300M is the legacy GGUF/JNI provider; EmbeddingGemma 2 is an optional LiteRT-LM provider. Embedding spaces include provider/model identity and dimension so vectors from distinct spaces are not compared as if interchangeable.

Structured Memory and Context Builder V1 are an additive sidecar (`memory_context.db`). `MemoryManager` stores explicit approved memories and vectors; `ContextFoundation` applies scope, retrieval and memory relevance before `ContextBuilder` budgets and assembles prompt context. The main chat can leave Context Builder V1 disabled. The 0.4.2 small-talk skip and relevance gate are documented in `docs/MEMORY042_REPORT.md`.

## Files and tools

`DocumentIngestion` and `documents/StructuredDocuments` implement local document parsing, including structured formats. `rag/LocalRetrievalService` and semantic services retrieve evidence; citation validation checks returned IDs against evidence actually provided. `domain/tools` defines the bounded tool registry and app adapters. Python runs through the isolated WebView/WASM worker in `:python`; runtime assets are under `app/src/main/assets/python/`.

Vision and audio use explicit attachment preprocessing and LiteRT content types where the selected model/runtime supports them. These are not implied for every model. No cloud fallback silently receives private content.

## Validation and tests

- `app/src/test` — JVM unit, Robolectric and integration tests for ViewModels, persistence, runtimes, retrieval, tools and policies.
- `app/src/androidTest` — Android instrumentation/navigation/device smoke tests. These need an Android device/emulator and are not the same as host tests.
- `litert-compat/src/test` — compatibility-contract tests.
- `app/src/main/java/.../validation` plus `context/ContextMemoryValidation` and `semantic/v2/SemanticValidation` — in-app historical Device Validation, Semantic V2 and Context/Memory suites. Host doubles do not certify physical-device inference.
- `scripts/test_python_sandbox.cjs` — local Python runtime/sandbox control checks.

At the 0.4.2 source baseline, the recorded host run reports 511 app JVM tests plus 2 LiteRT compatibility tests passing, all six requested build/lint gates passing, and the instrumented test APK built but not executed. The targeted 0.4.2 Motorola memory-relevance checks remain pending. See `docs/MEMORY042_REPORT.md` and `docs/validation/memory042/test-summary.json`.

## Rebuild inputs

Gradle versions and dependencies are pinned in `gradle/libs.versions.toml` and module scripts. Native llama.cpp source is vendored. LiteRT-LM, model weights, Android SDK/NDK/CMake, and the Python WebView runtime version still need to match the app manifests and documented provenance. Model binaries are excluded; see `docs/EXTERNAL_ARTIFACTS.md`.
