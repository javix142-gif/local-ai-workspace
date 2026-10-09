# `.gitignore` scope follow-up

## Revisions

- Parent/evidence baseline: `8837517ee502b700c85a2b2c484beddcbcc879be`
- Code commit tested: `b6c460763b19de41e689c493859bc1ee649faf2c`
- Checkout: detached worktree at the code SHA; `git clean -ffdx` ran before
  validation, with zero tracked or ignored status entries at build start.
- Scope: `.gitignore` only. No app/runtime/build configuration, data, test
  source, model source or dependencies changed.

## Change

The prior exception `!third_party/llama.cpp/src/models/**` was broader than
the source set needed for llama.cpp and overrode later model-binary ignore
rules. It is now limited to:

- `!third_party/llama.cpp/src/models/*.cpp`
- `!third_party/llama.cpp/src/models/models.h`

The directory itself remains unignored so Git can reach these files. All
other entries below that directory continue to match the model-directory
ignore rule or the vendored llama.cpp ignore file.

## Deterministic ignore matrix

At 2026-10-08 23:50:12 UTC, `ignore-matrix.sh` ran against the clean checkout
at `b6c460763b19de41e689c493859bc1ee649faf2c` using `git check-ignore
--no-index`. All 17 assertions passed:

- `models.h` and an actual `.cpp` registry source are trackable;
- `.bin`, `.gguf`, `.litertlm`, `.onnx`, `.tflite`, `.safetensors`, `.pt`,
  `.pth`, `.ckpt`, `.ggml`, `.so` and `.a` probes are ignored;
- the tracked model registry still contains exactly 158 files: 157 `.cpp`
  and one `.h`;
- the matrix leaves the checkout unmodified.

Raw output and the portable script are in this directory. Their SHA-256
values are listed in the enclosing `SHA256SUMS`.

## Clean release build

The same clean checkout ran `:app:assembleRelease` from 2026-10-08 23:50:16
UTC to 2026-10-09 00:07:30 UTC, exit code 0 (`BUILD SUCCESSFUL`, 128 Gradle
tasks executed). The command was:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/workspace/law-b6c4607-tmp \
./gradlew :app:assembleRelease -Parm64Only=true --offline \
  --rerun-tasks --no-build-cache --no-daemon --no-parallel \
  --max-workers=1 --console=plain
```

The build used Gradle 8.10.2, Temurin JDK 17.0.20.1, Android SDK platform 35,
NDK 27.0.12077973, CMake 3.31.6 and ABI `arm64-v8a`. It compiled llama.cpp
from the tracked sources in this checkout. Existing Gradle/Kotlin and CMake /
KleidiAI warnings did not fail the build.

This correction changes only ignore behavior. The full JVM suite was last run
on parent code commit `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`: app 727/727
and LiteRT compatibility 2/2, all without failures/errors/skips. Those XML
reports remain in the enclosing evidence bundle and are not relabeled as a
new test run on `b6c4607`. The current commit has a clean `assembleRelease`
result and clean ignore matrix. AndroidTest execution, emulator/device tests,
and Motorola validation are **NOT RUN**. The local unsigned release build
output was not copied or distributed.

## Diff check

The `.gitignore` code diff passes `git diff --check`. The portable script and
this report contain no intentional whitespace exceptions. Captured Gradle
logs remain byte-for-byte and are covered by the enclosing checksum manifest.
