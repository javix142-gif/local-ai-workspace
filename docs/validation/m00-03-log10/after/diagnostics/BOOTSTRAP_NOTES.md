# Clean-worktree build setup attempts

These are environment diagnostics, not failed product tests.

1. Initial complete gate command was the final gate command without `-I docs/validation/skills050/force-test-execution.gradle`; it stopped in `:llama-runtime:buildCMakeRelease[arm64-v8a]` because the clean worktree did not contain ignored `third_party/llama.cpp/src/models/models.h` and its model source directory. Log: `bootstrap-missing-ignored-sources.log.gz`.
2. The local ignored `third_party/llama.cpp/src/models/` sources were copied into the isolated `/tmp` worktree from the existing workspace. A second full-gate attempt then reached native linking but used the CMake source glob generated before those files existed, yielding unresolved model vtables. Log: `bootstrap-stale-cmake-glob.log.gz`.
3. The SHA-256 of `third_party/llama.cpp/src/CMakeLists.txt` was captured, its timestamp was touched without changing contents, and the hash was rechecked. `release-configure.log.gz` records the successful native graph regeneration / standalone `:app:assembleRelease` preparation. The subsequent complete gate passed.

The ignored model sources are an existing local build prerequisite absent from Git; they are not part of this correction and were not tracked or committed. The final gate used the same code SHA as the tests and generated selector report.
