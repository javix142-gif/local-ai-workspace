# Repository preparation audit — 0.4.2

- Workspace inventory: 17,137 files, 5,500,926,406 bytes (5.12 GiB), excluding nested `.git` administrative metadata.
- Files above 10 MB: 99; above 50 MB: 29; above 100 MB: 1.
- No `.litertlm` or `.gguf` model weight file was found in this checkout. The locally imported models live outside the workspace.
- The Pyodide WASM runtime at `app/src/main/assets/python/pyodide.asm.wasm` is required by the app and is deliberately included; generated build copies are excluded.
- `git ls-remote` returned no refs, `gh repo view` showed an empty `main`/no default branch; account `javix142-gif` was authenticated.

## Large files excluded from Git

| Path | Type | Size (bytes) | Exclusion reason |
| --- | --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `.apk` | 118,396,835 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `dist/apk/local-ai-workspace-0.4.2-memory-relevance-hotfix-arm64.apk` | `.apk` | 99,638,941 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.4.1-normal-chat-hotfix-arm64.apk` | `.apk` | 99,622,557 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `.apk` | 99,610,418 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `dist/apk/local-ai-workspace-0.4.0-context-memory-arm64.apk` | `.apk` | 99,557,021 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.3.1-semantic-stability-arm64.apk` | `.apk` | 99,180,131 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.3.0-semantic-v2-arm64.apk` | `.apk` | 99,016,291 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.2.4-qa-arm64.apk` | `.apk` | 98,536,548 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.2.3-thinking-arm64.apk` | `.apk` | 98,520,164 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.2.2-capabilities-embedding-arm64.apk` | `.apk` | 98,487,396 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.2.1-device-validation-arm64.apk` | `.apk` | 98,425,956 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.2.0-part2-arm64.apk` | `.apk` | 98,065,334 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.15-vision-hotfix151-arm64.apk` | `.apk` | 92,010,425 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.14-ui-stage15-arm64.apk` | `.apk` | 91,977,657 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.13-performance-stage1-arm64.apk` | `.apk` | 91,879,353 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.12-chat-files-arm64.apk` | `.apk` | 91,666,361 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.11-app-preload-arm64.apk` | `.apk` | 91,584,441 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.10-workspace-arm64.apk` | `.apk` | 91,535,289 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.9-warm-model-arm64.apk` | `.apk` | 91,420,601 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `dist/apk/local-ai-workspace-0.1.8-chat-delete-arm64.apk` | `.apk` | 91,387,833 | Packaged release/debug APK or retained distribution artifact; binaries stay outside Git. |
| `llama-runtime/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/library_jni/release/copyReleaseJniLibsProjectOnly/jni/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/library_jni/debug/copyDebugJniLibsProjectOnly/jni/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/cxx/Release/6zg541b1/obj/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/bin/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libllama-common.so` | `.so` | 82,179,992 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64/bin/libllama-common.so` | `.so` | 78,239,904 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/local_aar_for_lint/debug/out.aar` | `.aar` | 47,933,943 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/local_aar_for_lint/release/out.aar` | `.aar` | 47,926,202 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/debug/mergeExtDexDebug/classes.dex` | `.dex` | 44,734,712 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/library_jni/release/copyReleaseJniLibsProjectOnly/jni/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/library_jni/debug/copyDebugJniLibsProjectOnly/jni/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/cxx/Release/6zg541b1/obj/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/bin/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libllama.so` | `.so` | 40,685,184 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64/bin/libllama.so` | `.so` | 40,504,072 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/incremental/release-mergeJavaRes/zip-cache/kjOQy5nYyKpSNC7HK7aOf8lNSkg=` | `no extension` | 37,425,981 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/incremental/debug-mergeJavaRes/zip-cache/kjOQy5nYyKpSNC7HK7aOf8lNSkg=` | `no extension` | 37,425,981 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/external_libs_dex/release/mergeExtDexRelease/classes.dex` | `.dex` | 32,454,664 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/release/mergeDexRelease/classes.dex` | `.dex` | 32,454,412 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `.gradle/8.10.2/executionHistory/executionHistory.bin` | `.bin` | 30,697,404 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `docs/validation/storage-cleanup-2026-10-05/build-evidence/app/outputs/native-debug-symbols/release/native-debug-symbols.zip` | `.zip` | 29,701,172 | Raw QA snapshot, native binary or generated build evidence; only compact summaries are versioned. |
| `litert-compat/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_jni_libs/release/mergeReleaseJniLibFolders/out/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_jni_libs/debug/mergeDebugJniLibFolders/out/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_jni/release/copyReleaseJniLibsProjectOnly/jni/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_jni/debug/copyDebugJniLibsProjectOnly/jni/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/generated/native-backend/jni/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,008 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_and_local_jars_jni/release/copyReleaseJniLibsProjectAndLocalJars/jni/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_and_local_jars_jni/debug/copyDebugJniLibsProjectAndLocalJars/jni/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/x86_64/liblitertlm_jni.so` | `.so` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/native_symbol_tables/release/extractReleaseNativeSymbolTables/out/x86_64/liblitertlm_jni.so.sym` | `.sym` | 25,968,000 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` | `.zip` | 24,994,400 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_jni_libs/release/mergeReleaseJniLibFolders/out/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/merged_jni_libs/debug/mergeDebugJniLibFolders/out/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_jni/release/copyReleaseJniLibsProjectOnly/jni/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_jni/debug/copyDebugJniLibsProjectOnly/jni/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/generated/native-backend/jni/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,960 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_and_local_jars_jni/release/copyReleaseJniLibsProjectAndLocalJars/jni/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/intermediates/library_and_local_jars_jni/debug/copyDebugJniLibsProjectAndLocalJars/jni/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a/liblitertlm_jni.so` | `.so` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/native_symbol_tables/release/extractReleaseNativeSymbolTables/out/arm64-v8a/liblitertlm_jni.so.sym` | `.sym` | 21,802,952 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/build-llama/vendor/cpp-httplib/libcpp-httplib.a` | `.a` | 21,581,978 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/build-llama/vendor/cpp-httplib/CMakeFiles/cpp-httplib.dir/httplib.cpp.o` | `.o` | 21,411,672 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64/build-llama/vendor/cpp-httplib/libcpp-httplib.a` | `.a` | 20,789,320 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64/build-llama/vendor/cpp-httplib/CMakeFiles/cpp-httplib.dir/httplib.cpp.o` | `.o` | 20,620,016 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/outputs/aar/litert-compat-debug.aar` | `.aar` | 20,186,244 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `litert-compat/build/outputs/aar/litert-compat-release.aar` | `.aar` | 20,178,606 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/build-llama/common/CMakeFiles/llama-common.dir/arg.cpp.o` | `.o` | 13,929,200 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64/build-llama/common/CMakeFiles/llama-common.dir/arg.cpp.o` | `.o` | 12,158,944 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/debug/mergeExtDexDebug/classes2.dex` | `.dex` | 11,870,996 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/local_aar_for_lint/debug/out.aar` | `.aar` | 11,711,191 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `llama-runtime/build/intermediates/local_aar_for_lint/release/out.aar` | `.aar` | 11,706,272 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/release/mergeDexRelease/classes2.dex` | `.dex` | 10,986,864 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar` | `.jar` | 10,852,206 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar` | `.jar` | 10,852,206 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/debug/mergeExtDexDebug/classes3.dex` | `.dex` | 10,413,788 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/dex/release/mergeDexRelease/classes3.dex` | `.dex` | 10,347,552 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/assets/release/mergeReleaseAssets/python/pyodide.asm.wasm` | `.wasm` | 10,105,545 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |
| `app/build/intermediates/assets/debug/mergeDebugAssets/python/pyodide.asm.wasm` | `.wasm` | 10,105,545 | Generated Gradle/Android/NDK build output or cache; reproducible from source. |

## Largest directories

| Path | Size (bytes) |
| --- | ---: |
| `dist` | 1,737,736,513 |
| `dist/apk` | 1,736,251,105 |
| `llama-runtime` | 1,640,831,376 |
| `app` | 1,330,113,724 |
| `app/build` | 1,314,673,641 |
| `app/build/intermediates` | 1,016,221,564 |
| `llama-runtime/.cxx` | 853,031,288 |
| `llama-runtime/.cxx/Release/6zg541b1` | 852,001,337 |
| `llama-runtime/.cxx/Release` | 852,001,337 |
| `llama-runtime/build` | 787,712,306 |
| `llama-runtime/build/intermediates` | 769,088,251 |
| `litert-compat` | 670,077,236 |
| `litert-compat/build` | 669,926,784 |
| `litert-compat/build/intermediates` | 578,175,458 |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a` | 453,287,254 |
| `llama-runtime/.cxx/Release/6zg541b1/x86_64` | 398,712,725 |
| `app/build/intermediates/merged_native_libs` | 371,321,872 |
| `llama-runtime/.cxx/Release/6zg541b1/arm64-v8a/build-llama` | 291,883,247 |
| `llama-runtime/build/intermediates/merged_native_libs` | 275,705,152 |
| `llama-runtime/build/intermediates/library_jni` | 275,705,152 |

## Secret audit

The credential-pattern scan of candidate source/config/docs found no token, private-key or credential assignment. Four packaging scripts contained a debug-signing password literal and are explicitly excluded as `SECRET_CANDIDATE` paths (values omitted):

- `SECRET_CANDIDATE docs/validation/chat041/audit_package.py`
- `SECRET_CANDIDATE docs/validation/context040/audit_package.py`
- `SECRET_CANDIDATE docs/validation/memory042/audit_package.py`
- `SECRET_CANDIDATE docs/validation/semantic031/audit_package.py`

A password-like test string in `ContextFoundationTest.kt` is an intentional secret-detection fixture, not a credential. Test sources and fixtures remain tracked. No `.env`, `local.properties`, keystore, JKS or model weights were found in the tracked candidate set.

## Candidate tracking simulation

Before staging, `git ls-files --others --exclude-standard` produced 3,021 candidate files totaling 55,271,855 bytes (52.71 MiB); prohibited file extensions/config paths: 0. The dry run used explicit source/docs/module paths and the project `.gitignore`.

Largest candidate files (the 10 MB WASM asset is required by the offline Python service):

| Path | Size (bytes) |
| --- | ---: |
| `app/src/main/assets/python/pyodide.asm.wasm` | 10,105,545 |
| `third_party/llama.cpp/vendor/miniaudio/miniaudio.h` | 4,108,168 |
| `app/src/main/assets/python/python_stdlib.zip` | 2,360,737 |
| `third_party/llama.cpp/ggml/src/ggml-opencl/ggml-opencl.cpp` | 1,449,955 |
| `app/src/main/assets/python/pyodide.asm.js` | 1,255,688 |
| `third_party/llama.cpp/vendor/nlohmann/json.hpp` | 953,436 |
| `third_party/llama.cpp/ggml/src/ggml-vulkan/ggml-vulkan.cpp` | 882,199 |
| `third_party/llama.cpp/ggml/src/ggml-cpu/arch/x86/repack.cpp` | 665,038 |
| `third_party/llama.cpp/vendor/cpp-httplib/httplib.cpp` | 620,866 |
| `docs/validation/storage-cleanup-2026-10-05/cleanup.json` | 466,689 |
| `third_party/llama.cpp/ggml/src/ggml-cpu/ops.cpp` | 425,708 |
| `third_party/llama.cpp/ggml/src/ggml-hexagon/ggml-hexagon.cpp` | 355,095 |
| `third_party/llama.cpp/ggml/src/ggml-sycl/ggml-sycl.cpp` | 301,235 |
| `docs/validation/chat041/baseline040-hashes.json` | 298,743 |
| `third_party/llama.cpp/ggml/src/ggml-cpu/arch/riscv/quants.c` | 294,126 |
| `third_party/llama.cpp/ggml/src/ggml-cpu/spacemit/ime2_kernels.cpp` | 291,755 |
| `third_party/llama.cpp/vendor/stb/stb_image.h` | 283,010 |
| `third_party/llama.cpp/ggml/src/ggml-cpu/arch/arm/repack.cpp` | 269,785 |
| `third_party/llama.cpp/vendor/hash/xxhash/xxhash.h` | 264,750 |
| `third_party/llama.cpp/ggml/src/ggml.c` | 261,112 |
