# Fresh-checkout Release reproducibility attempt — `4559b5`

## Result

**BLOCKED before Gradle started.** The requested command was invoked once from a fresh detached checkout of remote branch SHA `4559b502c55497f9f3c932c3e428f708fb086fd2`. The wrapper attempted to fetch the pinned Gradle 8.10.2 distribution from `https://services.gradle.org/distributions/gradle-8.10.2-bin.zip`, then failed with `java.net.ConnectException: Connection refused` (exit code 1). The Gradle distribution was not installed in the new, empty `GRADLE_USER_HOME`, so no Gradle task graph started. No retry was made and no cache or source was copied from another checkout.

This result does not show an application build failure or a successful Release build. It leaves the fresh, empty-home reproducibility check **unverified due to network access**. It does not invalidate the separately recorded historical clean-checkout Release PASS at `83affa67584292137a17dd544a4f6e207cf7c96f`; that earlier run used the shared Gradle User Home and is preserved separately.

## Checkout and source verification

- Remote feature branch at start and after the command: `4559b502c55497f9f3c932c3e428f708fb086fd2`.
- Checkout: fresh detached worktree created from that fetched SHA.
- `git status --porcelain=v1 --ignored`: empty, 0 lines before invocation; the working tree remained clean after wrapper failure.
- Absent before build: `.gradle/`, `app/build/`, `llama-runtime/build/`, `.cxx/`, `local.properties`.
- All `third_party/llama.cpp` files matched Git: 1,865 tracked and 1,865 on disk; no missing files, extras, or blob mismatches.
- `third_party/llama.cpp/src/models/models.h` was tracked and matched blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`; `afmoe.cpp` was also tracked and matched.
- All 15 probed model/binary artifact extensions remained ignored. The `.cpp` sources and `models.h` stayed trackable.
- The source diff from application code commit `260a3f7c6267cc5a90a9cf516356951aec0627a1` to tested checkout `4559b502c55497f9f3c932c3e428f708fb086fd2` contains documentation paths only.

The full structured preflight is in `preflight.json`; the captured pre-build porcelain output is retained in `git-status-porcelain-ignored.txt` (empty file).

## Command, environment and outcome

```sh
./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info
```

The command ran with Temurin JDK 17.0.20.1+1, Android SDK platform 35 revision 2, build tools 35.0.0, NDK 27.0.12077973 and CMake 3.31.6 installed. Gradle 8.10.2 is pinned by the wrapper. `GRADLE_USER_HOME=/workspace/law-clean-release-4559b5-gradle-home` did not exist before setup and was empty immediately before invocation. After failure it contained only zero-byte `.zip.part` and `.lck` files for the failed Gradle distribution fetch.

- Invocation: `2026-10-09T08:31:52Z`–`2026-10-09T08:31:58Z`.
- Exit code: `1`.
- Gradle distribution download: failed with `Connection refused`.
- Gradle tasks executed: `0`; Gradle task outcomes: none.
- CMake configure/build: **NOT REACHED**.
- Ninja ARM64 compile/link: **NOT REACHED**.
- APK: not generated; none retained or distributed.

The raw, unmodified output is in `assembleRelease.log`. `task-outcomes.txt` distinguishes the requested task from tasks that actually ran. `toolchain.txt`, `run-metadata.txt`, `gradle-user-home-after.txt` and `remote-refs.txt` preserve the environment and ref checks.

## Evidence and limits

`SHA256SUMS` covers the evidence files in this directory except the manifest itself. The JSON summary records `BLOCKED_BEFORE_GRADLE_START_NETWORK` and zero task/CMake/Ninja execution.

No JVM suite, AndroidTest compilation, instrumentation, emulator run, Android CI run, or Motorola validation was performed. `EMULATOR`, Android, and `PHYSICAL_DEVICE` validation remain **NOT_RUN** for this attempt; Android emulator CI separately remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. This attempt generated no APK and provides no Android or device validation.

## Independent orchestration audit (2026-10-09 09:35:53 UTC)

**Disposition: `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`.** I independently reviewed the commit comparison, current branch reference, published preflight, run metadata, task outcomes, raw Gradle-wrapper log, remote refs, and SHA manifest. Commit `fcae468dc2a776d6e86709547a8bc3f295d1063d` is one fast-forward commit beyond tested checkout `4559b502c55497f9f3c932c3e428f708fb086fd2`; the diff contains documentation/evidence only. The reported clean detached preflight has 0 porcelain/ignored lines, 1,865/1,865 llama.cpp files with no missing, extra or blob-mismatched files, and 15/15 binary ignore probes. These are independently read-back records, not a build I ran locally.

The raw log shows the wrapper attempted the pinned Gradle 8.10.2 download and failed with `java.net.ConnectException: Connection refused`. The recorded exit code is 1, but the evidence also confirms Gradle never started: zero tasks ran and CMake/Ninja were not reached. No APK was produced. The correct status is a network-blocked attempt, not build PASS or application build failure. I recalculated all 13 published evidence hashes from the GitHub file contents; all match `SHA256SUMS`, whose SHA-256 matches the execution state. The raw log hash also matches its manifest entry.

The feature branch was `fcae468dc2a776d6e86709547a8bc3f295d1063d`; `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. No JVM, AndroidTest, emulator, CI, or physical-device tests were run. The historical successful clean-checkout build at `83affa67584292137a17dd544a4f6e207cf7c96f` remains valid for that SHA and is not replaced by this blocked empty-Gradle-home attempt. No retry was started because the contract required stopping on the network failure without copying caches.

