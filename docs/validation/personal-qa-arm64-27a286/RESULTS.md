# Local AI Workspace 0.5.0 — PERSONAL QA Debug ARM64

**Artifact type:** `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`
**Source commit:** `27a286daa4dd83bfb1b2d36d185459e481d40d1f` on `feat/0.5.0-skills-agents`
**Build time:** 2026-10-12 01:35:40–01:36:12 UTC
**Disposition:** APK packaging and cryptographic/package checks passed. This is a Debug QA build, not Release, not a global test pass, and not device validation.

## Download

[Download the personal QA bundle from Google Drive](https://drive.google.com/file/d/1M18I1Ls1z6GfAhYMa9dyXqEedfXxIoSI/view?usp=drivesdk) — Drive file ID `1M18I1Ls1z6GfAhYMa9dyXqEedfXxIoSI`, 50,032,510 bytes. Extract the ZIP to obtain the installable APK and verify its files using the included `SHA256SUMS`.

- Outer ZIP SHA-256: `4e3e36a231458997658e22c5ed2155fcc57a94d63abc6f90781c143069021309`; sidecar: [bundle SHA-256](https://drive.google.com/file/d/1E9DI3Kyf293yZ8rJgV1vWJeHxYgrWDNO/view?usp=drivesdk).
- Standalone report: [RESULTS.json on Drive](https://drive.google.com/file/d/1l61586udhEwkvnjoN5wM1TLZBZPe7wCR/view?usp=drivesdk).
- The Drive connector rejected the 119,297,951-byte APK as a standalone upload (413; 100 MiB transfer limit). The ZIP is under that limit and contains the unchanged APK, report, install note, checksums, verification output and compressed Gradle log. No partial APK upload was created.

## Build and APK checks

Command:

```text
./gradlew :app:assembleDebug -Parm64Only=true --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain
```

Environment: Temurin/OpenJDK 17.0.20.1+1; Gradle Wrapper 8.10.2; Android API 35; Build Tools 35.0.0; NDK 27.0.12077973; CMake 3.31.6. The Git tree was clean before the build. This was an incremental build using existing local Gradle/project intermediates, not a fresh checkout; no build cache was requested. Gradle reported 81 actionable tasks, 7 executed, 74 up-to-date; `configureCMakeRelease[arm64-v8a]`, `buildCMakeRelease[arm64-v8a]`, `packageDebug`, and `assembleDebug` executed. Exit code 0, `BUILD SUCCESSFUL`.

The APK is `com.localai.workspace.debug`, `versionName=0.5.0-debug`, `versionCode=27`, Debug build type. It is 119,297,951 bytes, SHA-256 `3aa74283c68ece12169477f837227b90a329e74e53022c70d855d4ab7871e72b`. `zipalign -c 4`, ZIP CRC inspection and `apksigner verify --verbose --print-certs` passed. Signature scheme v2 is valid; signer DN is Android Debug; certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. The APK contains 9 native libraries under `arm64-v8a`, no other native ABI, and no model-weight files.

The raw build command output is retained as [compressed Gradle log](gradle-build.log.gz); verification command output is [compressed verification log](verification.log.gz). [RESULTS.json](RESULTS.json), [bundle checksum](BUNDLE_SHA256SUMS), and this directory's `SHA256SUMS` provide machine-readable digests. The APK itself is intentionally not stored in Git.

## Instrumentation status carried with this artifact

Latest connected AndroidTest evidence is run [38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844), tested code SHA `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`, not the later documentation-only build commit. The product/build files in `app/`, `litert-compat/`, `llama-runtime/`, vendored native source and Gradle configuration have no changes between those SHAs. The test run was **EMULATOR FAIL**: 16 total, 9 pass, 1 fail (missing authentic WorkspaceDatabase v1 schema), 6 skipped for absent real models; 10 executed. These counts are historical evidence, not a same-commit rerun and not a gate pass. Room's bounded historical source review is [here](../room-v1-source-audit/RESULTS.md); its status remains `ENVIRONMENT_UNAVAILABLE`. No new emulator or Motorola run was made.

- `EMULATOR=FAIL` (run 38186081844 evidence above).
- `PHYSICAL_DEVICE=NOT_RUN`; no APK installation was attempted.
- Release build: NOT_RUN.
- Six model-required tests remain skipped, not passed.
- Existing GitHub QA push workflow and its same-SHA instrumentation gate were not modified or bypassed. This one-time Drive bundle is explicitly personal QA.

`main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. The original 97 task IDs/order/dependencies are unchanged; M01-06 stays `PENDING_REQUIRES_DEVICE`.
