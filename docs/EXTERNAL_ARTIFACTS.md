# External artifacts

Model weights and APKs are intentionally kept outside Git. Values below come from the recorded artifact verification reports; they describe the verified artifact revision, not a claim that the binary is present in this checkout or installed on every device.

| Artifact | File | Size | SHA-256 | Provenance |
| --- | --- | ---: | --- | --- |
| Gemma 4 E2B | `gemma-4-E2B-it.litertlm` | 2,588,147,712 bytes | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` | `litert-community/gemma-4-E2B-it-litert-lm`, revision recorded in `docs/HOTFIX022_CAPABILITIES_EMBEDDING.md` |
| EmbeddingGemma 300M Q8_0 | `embeddinggemma-300M-Q8_0.gguf` | 333,590,944 bytes | `b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63` | `ggml-org/embeddinggemma-300M-GGUF`, revision `0f741b5a6585bd53aeb15cd1372c56f2a0f65e12` |
| EmbeddingGemma 2 740M | `embeddinggemma-2-740m.litertlm` | 484,622,336 bytes | `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c` | `litert-community/embeddinggemma-2-740m-litert-lm`, revision `24d962e906c7d332c6428e71c9676855024569e2` |
| Local AI Workspace 0.4.2 ARM64 | `local-ai-workspace-0.4.2-memory-relevance-hotfix-arm64.apk` | 99,638,941 bytes | `2de32fbad0f295baebccd6aba67060140e78f1436f293a2905a90f069066af4a` | Built and signed for versionCode 26; signature details in `docs/MEMORY042_REPORT.md` |

External dependencies also include pinned Maven artifacts, Android SDK/NDK/CMake toolchains, and Gradle caches. They are fetched by the build, not copied into this repository. Large physical validation exports and source snapshots are delivered separately; raw logs and build evidence are not part of the source backup.

To obtain model weights, use the app's explicit import/download flow or the source repositories listed in the corresponding reports. Verify hashes when the workflow provides them. Do not commit private model files or credentials.
