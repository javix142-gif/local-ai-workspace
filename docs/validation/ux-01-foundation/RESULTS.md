# UX-01 shared visual foundation

**Status:** host validation passed; personal QA Debug ARM64 bundle uploaded; awaiting independent audit. This is not a release or device validation.

## Source and scope

- Base: UX-00 accepted at `80292816115a6d632d96ee3e8e8996adb4c7bd7a`.
- Code commit: `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8` on `feat/0.5.0-skills-agents`.
- The original 97-task source was not present in the repository and was not reconstructed or edited. `M01-06` remains `PENDING_REQUIRES_DEVICE`; all existing IDs, ordering, dependencies and progress are unchanged.
- The change adds explicit fixed light/dark Material 3 schemes, typography and geometry tokens, semantic warning/success colors, a shared content frame and a minimum-touch-target modifier. The frame is used on Workspace, Models and Settings. Chat and Project retain their existing scaffolds.
- MainActivity navigation is unchanged: the same three top-level entries, `workspace` start destination, nine destinations, routes/arguments, selection and back-stack behavior remain. No runtime, model, data, persistence, permission, routing or dependency changes were made.

## Tokens and visual checks

The fixed palette matches the values in [UX-00](../../design/ux-00-audit-and-foundation.md). Light/dark selection follows the system; tests can select either palette explicitly. Dynamic color remains disabled. Warning/success foregrounds and containers are exposed through `LocalAiStatusColors`, separate from Material color roles. Typography uses the system font and the requested 22/28, 18/24, 16/24, 14/20 and 12/16 sp styles. Spacing, radii, reading width, mobile margin and 48 dp minimum target are centralized.

Deterministic WCAG 2.x checks use sRGB relative luminance. All registered text pairs passed 4.5:1; graphical outline pairs passed 3:1. The measured representative ratios retained from the color calculation are: main text/background 14.57:1 light and 14.28:1 dark; secondary text/container 5.78:1 and 8.77:1; brand/background 4.98:1 and 11.62:1; warning foreground/container 5.02:1 and 7.62:1; success foreground/container 5.21:1 and 6.88:1; outline/container 3.54:1 and 4.81:1. Light outline over white is 4.03:1 and must remain graphical only, not normal text.

Six full-app Robolectric captures show Workspace, Models and Settings in light and dark at 360 dp / font scale 1.0. They were opened and visually inspected. The 411 dp / font scale 1.6 capture is the shared AppScreenContent primitive fixture: it verifies readable wrapping and a 48 dp target; it is not a full-app large-font screenshot. The remaining full-app width/font-scale matrix is deferred to UX-05. These are host-rendered images, not Moto G86 evidence.

The compact Models capture also shows the final filter chip label partly clipped at the right edge. This contract did not redesign the model filter row; retain it as a concrete visual follow-up for UX-02.

| Capture | Size | SHA-256 |
| --- | ---: | --- |
| `screenshots/workspace-light-360dp-fontScale-1.0.png` | 29,059 B | `f067bb37660697f19e5d852a0c9bf59ad3067c48b208ca84ae38eeca07889479` |
| `screenshots/models-light-360dp-fontScale-1.0.png` | 29,256 B | `61fcd80ebcdea2e827b0222a2cf0f362e56ff366fb2d554a7393e4c95895ffc5` |
| `screenshots/settings-light-360dp-fontScale-1.0.png` | 22,843 B | `baf2213602f166b89caf8b9374ca608f9ee21dc11cc400f25b7d88eb97dac8dd` |
| `screenshots/workspace-dark-360dp-fontScale-1.0.png` | 29,425 B | `edb57fa74fbe6038bc0a312be27c6aad37fa4e853f38eb86852cb2411ae2f146` |
| `screenshots/models-dark-360dp-fontScale-1.0.png` | 29,600 B | `1c007878801f736f353a95cd00dd3f073e5ee14cf08d777b1e5d331ba42da004` |
| `screenshots/settings-dark-360dp-fontScale-1.0.png` | 23,265 B | `a5281fef1d66fe9345b4519f317b84510f50ad0bea7b2fc5434fc773b4164ef1` |
| `screenshots/shared-frame-411dp-fontScale-1.6.png` | 14,217 B | `38c1d0e9cfa72fda29741ff24824bd24117968c9defcb9b4c74b3e389f9fc02e` |

## Host validation

Environment: Gradle 8.10.2, Temurin 17.0.20.1+1, Android API 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, Ninja 1.12.1; `-Parm64Only=true` for the APK task.

- Focused theme/UI run: 5/5 pass, 0 failures/errors/skips.
- Full `:app:testDebugUnitTest`: 738 pass, 0 failures/errors/skips across 87 JUnit XML files. The original XML files are archived at `host/full-jvm-xml.tar.gz`; the three UX-specific XML reports are also retained in `host/junit-focused/`.
- `:app:lintDebug`: exit 0, 0 errors, 82 warnings and 1 informational finding. No lint warning points to the new theme/token/component or capture-test files; the existing informational finding is in MainActivity outside the changed lines.
- `:app:assembleDebug -Parm64Only=true`: exit 0; the Debug APK was built.
- `:app:compileDebugAndroidTestKotlin`: exit 0. This compiles instrumented sources only; no instrumented test ran.
- A first attempt used the ambiguous Gradle abbreviation `:app:compileDebugAndroidTest` and stopped before executing tasks. The successful gate uses the concrete Kotlin compile task; it is not counted as a test/build pass.

Raw focused/full logs and APK verification output are losslessly gzip-compressed under `host/`; their compressed and original-byte SHA-256 values are in `RESULTS.json`. Toolchain versions, lint report, JUnit reports and commands are also retained. `SHA256SUMS` covers the committed evidence files.

## Personal QA APK

Artifact type: `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`.

- Package `com.localai.workspace.debug`, version `0.5.0-debug`, versionCode 27.
- ABI: `arm64-v8a` only; no model weights bundled.
- APK: 121,780,917 bytes; SHA-256 `cd0d7b10e4667c996a156e472e015ed6bea1bf96273ebb11764113e985ccd9a9`.
- `unzip -t`, `zipalign -c 4` and `apksigner verify` passed; APK Signature Scheme v2 is valid. Debug certificate SHA-256: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`.
- Drive bundle: [download personal QA ARM64 ZIP](https://drive.google.com/file/d/17WpxDcyBvCokhJaT6UODidxKAKb_FtlI/view?usp=drivesdk). Drive readback reports 46,116,609 bytes. ZIP SHA-256: `0c5d0277b88d9d3a0720d8e52e4f5c3a9ee783305a8922c11791ef7fd43327eb`. It contains the APK, report, SHA manifest, build log and verification notes. The upload is confirmed in folder [Local AI Workspace QA](https://drive.google.com/drive/folders/1rvoZO3ovX33qgaHBMnY8xtE-T8rdDLaB).
- This APK was built in the existing local workspace/cache; it is not evidence of a clean-checkout build and is not a release APK.

## Android/device state and limits

- **HOST:** UX-01 tests, app JVM suite, lint, Debug ARM64 assembly and AndroidTest source compilation passed as recorded above.
- **EMULATOR:** the push of event SHA `11f5ae4fe6596b63ebac8e56f8762420f6e5eb50` automatically triggered run [38206516624](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516624). It was cancelled during pinned SDK installation, while downloading the API 35 x86_64 system image. KVM, AVD, ADB connection, APK installation and `:app:connectedDebugAndroidTest` were not reached. The retained artifact reports `device.connected=false`, 0 XML and 0 tests executed; this is **NOT_RUN**, not a test result for UX-01. Artifact [11727751535](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516624/artifacts/11727751535) is 21,744 bytes with SHA-256 `a44b9ae36b73bd1f099df98ca8ac462a65be67fafb20db90d9cde13ff5d36583`; its ZIP CRC and 9-entry internal hash manifest were verified. Details and local copies are in [automatic workflow evidence](workflow-side-effects/38206516624/README.md).
- **QA gate:** the paired automatic run [38206516667](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38206516667) failed closed at `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL` because the matching emulator run was cancelled. APK build and upload steps were skipped; the run has no artifact. It did not invalidate or rebuild the separately generated personal QA bundle described above.
- The latest prior connected run remains run `38186081844` on older code SHA `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`: 16 XML cases, 9 pass, 1 Room v1 fixture failure, 6 model-dependent skips, 10 executed. It is not same-SHA UX-01 evidence.
- **PHYSICAL_DEVICE:** `NOT_RUN`; the Motorola Moto G86 and original workbook were unavailable in the recorded preflight. No device behavior or latency is claimed.
- The Room schema/test was not modified. Six model-required instrumented tests were not simulated. UX-05 owns the full device matrix and Motorola visual validation. `M01-06` remains `PENDING_REQUIRES_DEVICE`.

The full app still needs physical visual review; no UX-02 or roadmap work is started by this delivery.

After code commit `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8`, `:app:assembleDebug -Parm64Only=true` was checked again at that exact HEAD. It exited 0 (81 actionable tasks, 2 executed, 79 up-to-date); CMake configure/build tasks ran and `:app:assembleDebug` was up-to-date. The APK SHA-256 remained `cd0d7b10e4667c996a156e472e015ed6bea1bf96273ebb11764113e985ccd9a9`, byte-for-byte identical to the already uploaded bundle member. The working-tree differences at that point were documentation only; app sources were committed at the tested source SHA.
