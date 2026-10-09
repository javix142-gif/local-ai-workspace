# Clean-checkout Release reproducibility — `83affa6`

## Result

**PASS — host Release build from the exact fetched remote commit.** This was a single detached Git worktree created from `feat/0.5.0-skills-agents` at `83affa67584292137a17dd544a4f6e207cf7c96f`. The product-code source commit remains `260a3f7c6267cc5a90a9cf516356951aec0627a1`; comparison from that commit to the tested checkout contains documentation/evidence paths only.

## Clean checkout and source integrity

Before Gradle ran, the worktree had zero tracked changes and zero untracked or ignored paths. `.gradle/`, `app/build/`, `llama-runtime/build/`, and `local.properties` were absent. The primary checkout's 197 ignored paths were counted and left untouched; none were used as source or build output for this run.

Git and disk were compared across all 1,865 tracked files under `third_party/llama.cpp`: 1,865 present, zero missing, zero untracked, zero blob mismatches. This includes 497 C++ files across the vendor tree and the model registry's 157 `.cpp` files plus tracked `models.h` (blob SHA-1 `387a4adcb239db69d72349ee57e2fbfe2db04e5a`). The 12 `git check-ignore --no-index` probes for `.bin`, `.gguf`, `.litertlm`, `.onnx`, `.tflite`, `.safetensors`, `.pt`, `.pth`, `.ckpt`, `.ggml`, `.so`, and `.a` all remained ignored, while `models.h` and `.cpp` sources were trackable. Full measurements are in `preflight.txt`.

## Release command and toolchain

```sh
./gradlew :app:assembleRelease -Parm64Only=true --offline \
  --rerun-tasks --no-build-cache --no-daemon --no-parallel \
  --max-workers=1 --console=plain --info
```

- Start: `2026-10-09T05:32:04Z`; finish: `2026-10-09T06:00:37Z`.
- Exit code: `0`; Gradle: `BUILD SUCCESSFUL in 28m 32s`; `128 actionable tasks: 128 executed`.
- Linux x86_64, Eclipse Temurin JDK `17.0.20.1+1`, Gradle `8.10.2`, Android SDK platform `35` (revision 2), build tools `35.0.0`, NDK `27.0.12077973`, CMake `3.31.6`, ABI `arm64-v8a`.
- `:llama-runtime:configureCMakeRelease[arm64-v8a]` ran with `--rerun-tasks`; the log shows CMake invoked in the new worktree, generating `.cxx/Release/.../CMakeCache.txt` and Ninja files.
- `:llama-runtime:buildCMakeRelease[arm64-v8a]` ran, invoked Ninja for the ARM64 `ggml`, `llama`, `llama-common`, `local-embedding`, and `ai-chat` targets, returned process result `0`, and reported `build complete`. Neither CMake task was `UP-TO-DATE`.

The shared Gradle User Home `/home/agent/.gradle` supplied the wrapper and offline dependency artifacts. It is outside both checkouts. The clean worktree began with no project caches or outputs, `--no-build-cache` disabled Gradle's build cache, and `--rerun-tasks` forced applicable tasks. No source, generated output, or cache directory was copied from the primary checkout.

Gradle necessarily created its normal Release build outputs during `assembleRelease`. They were not copied to this evidence bundle or distributed; the isolated build worktree was removed after preserving the log and is not an APK handoff. This task ran no JVM tests, AndroidTest compilation, instrumentation, emulator, or Motorola test. Android CI remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; M01-05 remains `VALIDADA_HOST`; M01-06 remains pending with `requires_device=true`. The original user-provided 97-task plan and its IDs/dependencies were not modified.

## Evidence files

- Raw Gradle log: 980,521 bytes; SHA-256 `e49c71329acd899edd0b8ebfde10db990404315fa4ee948d9c5c8c01298ef079`.
- `preflight.txt` — detached SHA, cleanliness, tracked-source hash comparison, ignore matrix, and toolchain.
- `run-metadata.txt` — exact command, SHA, UTC start/end, and exit code.
- `gradle-task-outcomes.txt` — task outcome lines copied from the raw Gradle output.
- `assembleRelease.log` — complete raw `--info` output, including CMake configure, Ninja compile/link, and Gradle result.
- `summary.json` — machine-readable outcome.
- `SHA256SUMS` — hashes of all evidence files except the manifest itself.

`git diff --cached --check` passed for all changed documentation and evidence files when the raw log was excluded. A full check reports 15 trailing-whitespace lines emitted by Gradle in the captured log; the log is preserved byte-for-byte rather than normalized.
