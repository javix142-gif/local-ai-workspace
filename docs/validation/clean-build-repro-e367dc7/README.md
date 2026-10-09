# Clean-checkout llama.cpp build reproducibility

This evidence bundle records the missing-source failure on the 0.5.0 feature
branch, the pinned-source repair, and host validation from a clean checkout.
It does not claim emulator or physical-device validation.

**Follow-up:** the initial `.gitignore` exception was too broad and could
unignore model binaries inside the vendored registry directory. Commit
`b6c460763b19de41e689c493859bc1ee649faf2c` narrows it to `*.cpp` plus
`models.h`. The clean-checkout ignore matrix and release build for that SHA
are recorded in [`ignore-fix/RESULTS.md`](ignore-fix/RESULTS.md).

## Source and branch identities

- Remote branch at preflight: `feat/0.5.0-skills-agents`
- Remote/local feature head before the repair: `f4695b5644f1230b5f711ec8300937027fb39941`
- Code repair commit tested here: `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`
- Test/build checkout: detached Git worktree at the code repair commit; clean
  tracked and ignored status before each clean validation pass.
- `main` and peeled tag `v0.4.2` both remained at
  `de615b772cf37a99e7e08a647c59607b271de606` at preflight and before publish.

## Reproduction and root cause

At 2026-10-08 22:21:28 UTC, a fresh clone at the original remote feature SHA
ran `:app:assembleRelease` with `--rerun-tasks --no-build-cache --offline`.
It failed in `third_party/llama.cpp/src/llama-model.cpp:23` because
`models/models.h` was absent. The complete raw output is
[`logs/base-red-assembleRelease.log`](logs/base-red-assembleRelease.log).

The root cause was the root `.gitignore` rule `**/models/**`. It matched the
vendored C++ model registry directory, so those files existed only as ignored
workspace residue and were absent from Git. The llama.cpp CMake graph globs
`src/models/*.cpp`, while `src/llama-model.cpp` includes `src/models/models.h`.
Thus a new clone lacked both the registry header and model implementations.

The required upstream source is established by existing
`THIRD_PARTY_NOTICES`: llama.cpp/ggml commit
`99b95488cac0f00ce3f05af113a8c1e287753f87` under MIT, with the upstream
license already present at `third_party/llama.cpp/LICENSE`. The 158 tracked
model registry files (1,879,682 bytes) were byte-compared against that exact
official upstream revision; all 158 SHA-256 values matched. The per-file
manifest is [`provenance/upstream-source-manifest.txt`](provenance/upstream-source-manifest.txt).
No model weights were added.

## Minimal repair

Code commit `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`:

1. Adds narrow `.gitignore` exceptions for
   `third_party/llama.cpp/src/models/**`, allowing this vendored source code
   to be committed while model binaries remain ignored.
2. Tracks the 158 canonical files from the pinned official source revision.
3. Adds an early CMake configure-time guard for a missing `models.h` or empty
   model implementation set, with a direct diagnostic.

The guard was exercised with a missing-header fixture and a missing-source
fixture. Both failed during configuration with the expected diagnostic; raw
outputs are under [`cmake-guard/`](cmake-guard/). The production CMake/release
build succeeded with the complete tracked source set.

## Host validation

Environment:

- Linux host; Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21.
- Gradle launcher JDK: Temurin 17.0.20.1; app unit-test worker JDK: 21.0.12.1.
- Android SDK platform/build tools 35; NDK 27.0.12077973; CMake 3.31.6;
  Ninja 1.12.1; ABI `arm64-v8a` only.
- Gradle dependencies were resolved offline from the configured Gradle cache;
  Gradle task/build caches were disabled. No source or build output was copied
  from another checkout. Java temporary files were redirected to
  `/workspace/law-e367dc7-tmp` to avoid the constrained `/tmp` filesystem.

The checkout was cleaned with `git clean -ffdx` before each final validation
command. The first command ran:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/workspace/law-e367dc7-tmp \
./gradlew --no-daemon --no-parallel --max-workers=1 --no-build-cache \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -I docs/validation/skills050/force-test-execution.gradle \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  -Parm64Only=true --offline --console=plain
```

Result: exit 0; 729 tests across 83 original XML reports, 0 failures,
0 errors, 0 skipped (app: 727/82 XML; litert-compat: 2/1 XML). The untouched
XML files are under [`tests/xml/`](tests/xml/), and the raw Gradle output is
[`logs/host-tests-clean.log`](logs/host-tests-clean.log).

After another `git clean -ffdx`, the build/lint command ran:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/workspace/law-e367dc7-tmp \
./gradlew --no-daemon --no-parallel --max-workers=1 --no-build-cache \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true --offline --console=plain
```

Result: exit 0; all four requested Gradle tasks completed. Raw output:
[`logs/host-build-clean.log`](logs/host-build-clean.log). Lint completed with
0 errors, 40 warnings and 1 informational finding; the report is
[`tests/lint-results-debug.xml`](tests/lint-results-debug.xml). Warnings
include existing dependency updates, `ApplySharedPref`, ExifInterface,
ChromeOS ABI support, backup rules, and existing TrustAllX509TrustManager
findings. CMake also reports upstream deprecation/float16-conversion warnings
and that the vendored subtree is not itself a Git checkout; these did not
fail configuration or compilation.

`assembleDebugAndroidTest` means the test APK compiled. Instrumented tests,
emulator tests and Moto G86 physical validation were **NOT RUN**.
`assembleRelease` produced a local unsigned verification artifact in the
temporary worktree; it is intentionally not included or offered as a release
APK.

## Non-final attempts retained for transparency

The files under [`attempts/`](attempts/) are not acceptance results:

- one full invocation hit the limited `/tmp` filesystem during packaging;
- one invocation lost its Gradle daemon before finishing;
- one pristine invocation was deliberately interrupted at the start of CMake
  to avoid another memory-pressure failure after tests had completed.

No cause was assigned to the daemon disappearance: the available OOM counters
had no before-run snapshot. The final clean split validation above completed;
these attempts are retained only as troubleshooting history.

## Diff check and checksums

The authored `.gitignore` and CMake changes pass `git diff --check`. The exact
upstream model sources include 14 blank lines at EOF that upstream itself
contains; plain `git diff --check` flags those lines. Preserving upstream
byte-for-byte was intentional. The original captured Gradle logs also retain
trailing spaces from Gradle's terminal output; they have not been normalized.
The full diff passes with only those two artifact whitespace rules disabled:

```sh
git -c core.whitespace=-blank-at-eof,-trailing-space diff --check \
  f4695b5644f1230b5f711ec8300937027fb39941..e367dc75d0f952ef6a9b4e96d51e1263b70d3309
```

[`SHA256SUMS`](SHA256SUMS) covers every other file in this evidence bundle;
the manifest does not include its own hash.
