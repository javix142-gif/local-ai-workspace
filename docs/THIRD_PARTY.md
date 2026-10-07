# Third-party inventory

| Component | Version / pin | License | Use |
| --- | --- | --- | --- |
| llama.cpp / ggml | commit `99b95488cac0f00ce3f05af113a8c1e287753f87` | MIT | GGUF parsing/loading and native token streaming |
| llama.cpp Android sample binding | same source pin | MIT (upstream repository license) | JNI/Flow adapter starting point; product adapter isolates it |
| AndroidX / Compose / Room | versions in `gradle/libs.versions.toml` | Apache-2.0 | Android UI, lifecycle and persistence |
| LiteRT-LM Android | `0.17.1` | Apache-2.0 (upstream distribution; verify before release) | Official LiteRT-LM container inspection, CPU engine, streaming conversation and modality metadata |
| Gson | `2.14.0` | Apache-2.0 | Hugging Face API response parsing and LiteRT-LM dependency isolation |
| pdfbox-android | `2.0.27.0` | Apache-2.0 upstream distribution | Local text extraction from ordinary PDFs |
| Kotlin coroutines | `1.9.0` | Apache-2.0 | Cancellation-aware background work |

The full llama.cpp `LICENSE` is preserved at `third_party/llama.cpp/LICENSE`. Dependency licenses and versions must be rechecked before a public release. No MuPDF/AGPL dependency is used.
