# Fresh-checkout ARM64 Release attempt — `6d1b25`

## Result

**BLOCKED before Gradle started.** One execution of the requested command was made from a fresh detached worktree at remote feature SHA `6d1b256b23c4e76dad72413ced9b10419425c134`. The Gradle Wrapper attempted to download its pinned Gradle 8.10.2 distribution from `services.gradle.org`, then failed with `java.net.ConnectException: Connection refused` (exit code 1). Gradle never started and no task graph was created. Per the contract, no retry, alternate URL, existing cache, or connectivity workaround was attempted.

This does **not** establish that the Android application build fails; it also does not establish a successful build. Reproducibility from this empty Gradle home remains unverified due to the wrapper download failure. The earlier clean-checkout Release PASS at `83affa67584292137a17dd544a4f6e207cf7c96f` remains historical evidence for that exact SHA and its recorded cache policy; this attempt does not supersede it.

## Checkout and vendored source preflight

- Fetched remote branch at start: `6d1b256b23c4e76dad72413ced9b10419425c134`.
- Worktree: detached at the exact fetched SHA; zero tracked, untracked, or ignored paths before the build.
- Absent before build: `.gradle/`, `app/build/`, `llama-runtime/build/`, `.cxx/`, `local.properties`.
- `third_party/llama.cpp`: 1,865 tracked files and 1,865 disk files; zero missing, extra, or blob-mismatched files.
- `src/models/models.h` is tracked and matched blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`; sample C++ source `afmoe.cpp` matched its Git blob.
- All 16 checked model/binary extensions were ignored; C++ sources and `models.h` remained tracked.
- Diff from product-code source `260a3f7c6267cc5a90a9cf516356951aec0627a1` to tested checkout `6d1b256…` contained documentation paths only.

Detailed preflight is in `preflight.json`; the captured empty status output is `git-status-porcelain-ignored.txt`.

## Command and environment

```sh
./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info
```

Toolchain present: Linux x86_64, Temurin JDK 17.0.20.1+1, Gradle Wrapper 8.10.2, AGP 8.7.3, Android platform 35 revision 2, build-tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, Ninja 1.12.1. The new `GRADLE_USER_HOME` was empty before invocation. Afterward it held only two zero-byte wrapper `.part` and `.lck` files; no distribution or dependencies were available.

The managed environment reported current connectivity and an enforced unrestricted HTTP policy; the actual Java request still returned `Connection refused`. This records the observed error without inferring its lower-level cause. No credentials were configured, and no proxy override or alternate network route was used.

- Invocation: `2026-10-09T21:50:17Z`–`2026-10-09T21:50:23Z`.
- Exit code: `1`.
- Gradle tasks executed: `0`; no task outcomes exist.
- CMake configure/build: **NOT REACHED**.
- Ninja ARM64 compile/link: **NOT REACHED**.
- APK: **NOT GENERATED**.

The complete unmodified wrapper output is `assembleRelease.log`; `task-outcomes.txt` records the non-execution explicitly. `toolchain.txt`, `run-metadata.txt`, `gradle-user-home-after.txt`, `network-environment.json`, and remote-ref files record the environment and checks.

## Validation boundaries

No JVM tests, AndroidTest compilation, instrumentation, emulator, Android CI, or Motorola run was made. `HOST` covers only clean-checkout/vendor preflight and the observed wrapper block; `EMULATOR` and `PHYSICAL_DEVICE` remain **NOT_RUN**. Android emulator CI separately remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. The original 97-task plan and its IDs/dependencies were not changed; M01-06 remains pending with `requires_device=true`.

`SHA256SUMS` covers every evidence file in this directory except itself. No APK is retained or distributed.


## Independent orchestration audit: final clean-home ARM64 Release attempt (2026-10-09 22:56 UTC)

**Finding: `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`.** This is a documentary audit of the published run at tested checkout `6d1b256b23c4e76dad72413ced9b10419425c134`; the auditor did not execute a local build or tests.

- GitHub comparison confirms the evidence delivery `7b1997589328852ff81dddc2ce0355b1342e1913` is one fast-forward commit after the tested checkout; the changed paths are documentation/evidence only. The application-source baseline remains `260a3f7c6267cc5a90a9cf516356951aec0627a1`.
- The published preflight records a fresh detached worktree at the exact tested SHA, empty tracked/untracked/ignored status, no Gradle/app/native build residue, and a newly empty `GRADLE_USER_HOME`. Its vendor inventory is 1,865/1,865 llama.cpp files with zero missing/extra/blob-mismatched files; `models.h` is Git-tracked and blob-matched. All 16/16 model/binary ignore probes are recorded as ignored. These are artifact-backed records; this auditor did not independently recreate the worktree.
- I fetched every file named by `SHA256SUMS` from the published commit and independently recomputed SHA-256 over its UTF-8 contents: **17/17 matched**. The raw `assembleRelease.log` hash is `3caee44390cf5c8ccec0423a07baf95079d50a7112b328b126f003501b76fe6c`; its text shows the wrapper's Gradle 8.10.2 download and `java.net.ConnectException: Connection refused`.
- The command exited 1 before Gradle startup: 0 tasks, no task outcomes, CMake/Ninja not reached, and no APK. The managed-environment record says connected/unrestricted policy, while the socket was refused; the lower-level network cause is **NOT DETERMINED**. This is neither an application compile failure nor a PASS. The two authorized empty-home attempts are exhausted; no retry or cache/source copy was made.
- Remote refs in the run evidence and GitHub comparison agree that `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. JVM, AndroidTest, emulator, Android CI and Motorola execution remain NOT_RUN for this attempt; Android CI separately remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`.

The evidence manifest was refreshed to include this audit. The prior clean Release PASS at `83affa67584292137a17dd544a4f6e207cf7c96f` remains scoped to that exact SHA and recorded cache policy. Fresh empty-home Release reproducibility remains unresolved pending a verifiable infrastructure path; do not describe this blocked run as a source build failure.
