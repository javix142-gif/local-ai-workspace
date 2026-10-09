# QA ARM64 Debug APK

## Result

The QA Debug APK was built from source commit `4ef2b39b9cba6aa1def081171d42d9146bbd968c` on `feat/0.5.0-skills-agents` by GitHub Actions run [37874837502](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37874837502). The run completed successfully. This is a **Debug/QA build**, not a Release build or production distribution.

Download the [GitHub Actions artifact ZIP](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37874837502/artifacts/11592306483). It contains the APK, `RESULTS.json`, `SHA256SUMS`, and a sanitized Gradle log. The artifact is retained for seven days and expires at **2026-10-16 02:37:56 UTC** (2026-10-15 23:37:56 in Chile, UTC−03:00).

| Item | Value |
|---|---|
| Run / job | `37874837502` / `113640935577` |
| Source SHA | `4ef2b39b9cba6aa1def081171d42d9146bbd968c` |
| Build command | `./gradlew :app:clean :app:assembleDebug -Parm64Only=true --no-daemon --no-parallel --max-workers=2 --stacktrace --console=plain` |
| Runner / toolchain | GitHub-hosted `ubuntu-24.04`; Temurin 17; Gradle 8.10.2; Android platform 35; build-tools 35.0.0; NDK 27.0.12077973; CMake 3.31.6 |
| Gradle result | `BUILD SUCCESSFUL in 7m 36s` |

## APK identity and verification

| Property | Verified value |
|---|---|
| File | `local-ai-workspace-0.5.0-debug-arm64.apk` |
| Size | 119,281,567 bytes |
| SHA-256 | `287ac97d6ce4e0563d1618e59726177355673321e401085c6383fe19b9ba4650` |
| Package | `com.localai.workspace.debug` |
| Version | `0.5.0-debug` (`versionCode` 27) |
| Native ABIs | `arm64-v8a` only; 9 native libraries |
| Signature | `apksigner verify --verbose --print-certs`: PASS, APK Signature Scheme v2 |
| Certificate SHA-256 | `56d5fb674b00a7aa2c86ff4835690e5b1ef3a1fcc525b08f2a1ec0c355129ffa` |
| Signer | Standard Gradle Debug signer (`C=US, O=Android, CN=Android Debug`) |

The APK was downloaded from the Actions artifact and checked independently. `aapt dump badging` confirmed its package and version; inspection of APK native library entries found only `arm64-v8a`. Its SHA-256 matches the artifact manifest. The runner-generated debug keystore was not exported.

## Artifact integrity

Artifact: `local-ai-workspace-qa-debug-arm64-37874837502-1` (ID `11592306483`), 47,787,296 bytes.

- ZIP SHA-256: `5ef240ea08899079e85835bcd2778eeda2b5bd11abde5472f113a974bf6884a4`
- `SHA256SUMS` file SHA-256: `f472ba4d6af778983cc2eeb64023c7ba9aefdbf0c444f008e984925adfccf690`; every listed artifact member verified.
- `RESULTS.json` SHA-256: `0d5aa0fb2c6637f8195cc1a253762fdf7a59eaa0cf3339ddccc67e774db7e952` (1,848 bytes).
- `logs/qa-build-sanitized.log` SHA-256: `30f2f8b41eb34bb5ff9deb5b888547f83bf993ec85801f45a3e36704d53b140d` (173,956 bytes).

## Validation status

| Layer | Status | Scope |
|---|---|---|
| HOST | **PASS** | GitHub runner build; downloaded artifact and member hashes; APK signature, package/version, and ABI verified. No JVM test suite was run for this artifact task. |
| EMULATOR | **NOT_RUN** | The separate emulator CI remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. Run `37865813921` stopped before instrumentation: XML 0, executed 0. This APK build does not change that result. |
| PHYSICAL_DEVICE | **NOT_RUN** | Motorola Moto G86 Power installation and validation remain pending. |

No model was downloaded. No APK was added to Git. No app/runtime behavior, inference result, latency, or physical-device compatibility is claimed by this build. The APK is for QA only and is not a release-signed production package.
