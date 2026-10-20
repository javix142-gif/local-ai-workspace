# H1 memory acceptance and connected QA — 2026-10-20

## Code

- Branch: `feat/0.5.0-skills-agents`
- Source code SHA: `3ca1d7945752b89817ea1e972fed2cdc4872e6d4`
- Included fixes: preserve imported pin metadata without treating import as a new pin; reject explicit pins that exceed the 15% cap before persistence; report over-budget pinned context before generation; update the Android acceptance test selectors to traverse the real expanded project-memory UI; close deleted-memory details after successful removal.
- The original backlog/roadmap files were not changed (97 IDs, 120 dependency edges, historical source states unchanged).

## HOST gate

Command:

```sh
GRADLE_USER_HOME=/workspace/.gradle JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --rerun-tasks --init-script /tmp/aws-h1-test-events.init.gradle -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

- Gradle 8.10.2, Temurin 17 runtime, Temurin 21 app JVM test worker, existing SDK/NDK, existing offline cache.
- Start/end `2026-10-20T18:15:27Z` / `18:25:08Z`; 9m41s; exit 0; `BUILD SUCCESSFUL`; 158 tasks executed.
- JUnit: app 126 XML / 939 passed; LiteRT compatibility 1 XML / 2 passed; 941 passed total, no failures, errors, or skips. H1 acceptance class: 22/22.
- `lintDebug`, `assembleDebug`, and `compileDebugAndroidTestKotlin`: PASS.
- Full log remains at `/tmp/aws-h1-full-host-gate-v4.log` (SHA-256 `1d4c70f0f05faca56f59145df85a93c7b319225161a623c28b85fff11767aaaf`). Metadata: `/tmp/aws-h1-full-host-gate-v4.meta` (SHA-256 `772d528879acc7e57322c52b44af70d7645ed69acbc57b01b3eb2e231c987c53`). The test-event listener SHA-256 is `539222edf9c75f83f2b07da14bf34a0957e103a0bce46e4611dc0c71844c7d5d`.

## EMULATOR run

- Run [39474604686](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604686), job `118807197684`, source SHA matches the code above.
- Environment: GitHub-hosted Ubuntu, Android 15/API 35, AVD `localai-api35-x86_64`, x86_64, ADB `emulator-5554` connected.
- Executed `:app:connectedDebugAndroidTest`; Gradle exit 0; collector PASS. JUnit XML is internally consistent: 20 cases, 14 PASS, 6 SKIPPED, 0 FAIL, 0 ERROR. All six skips require actual model weights; no model was downloaded or bundled. Progress output showed `26/20 completed` despite the XML's 20 cases; cause not determined, and the XML/collector totals were used.
- The room migration checks present in this suite (schema 8/9/10 and v8 citation migration) passed. The unavailable authentic Room v1 fixture is not resolved by this run.
- Evidence artifact [`12289288740`](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604686/artifacts/12289288740), 148,684 bytes, archive digest `81b0f027ad80a80196961fd603b0ee8c7a7a541328a046c6c83cd2097ab0d6cc`, expires 2026-10-27. The downloaded ZIP hash matched the API digest; internal manifest 32/32. Two valid screenshots are 1080×1920: standard header SHA-256 `214f9e93a5f68d47b1e2825fcf0bc4727f91e0d60073c024746673397441084f`; large-font header SHA-256 `648e95eee679b74cd1e415fd9e704a203fc1a336e1a3b8ad0c45cf39d78a8469`.
- `PHYSICAL_DEVICE=NOT_RUN`; this emulator result is not Motorola validation or model performance evidence.

## QA ARM64 APK

- Official gate [39474604678](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604678) waited for the matching instrumentation pass and completed successfully. Artifact [`12290056446`](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604678/artifacts/12290056446), 48,879,298 bytes, SHA-256 `7de7ca2697176fe57e691d052e7d5344636e062a2771a14c030bccfed424136f`; expiry 2026-10-27.
- APK: `local-ai-workspace-0.5.0-debug-arm64.apk`, 121,190,875 bytes, SHA-256 `0ec88a2efeffb4adb95b8bd869cfe6ada9a3caeb89bf698c181e24faad9352d6`; `com.localai.workspace.debug`, versionName `0.5.0-debug`, versionCode 27; only `arm64-v8a` (9 native libraries).
- `apksigner` v2, ZIP CRC, `zipalign`, `aapt` package/version, and model-payload detector passed. The `.bin` entry is the verified Kotlin coroutine debug-probe class; no model weights are present.
- Signing certificate SHA-256 `f68ad607fee4d7145ac631d92645a4cd5d72c050a74ce9334fce1f851830ccd7` is different from the previous personal update certificate `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. This APK is not update-compatible with the existing installed app. Uninstalling to install it can delete local chats and files; nothing was installed or uninstalled.
- An APK-only ZIP is in Drive, explicitly named for the incompatibility: [download](https://drive.google.com/file/d/1DQ2MaHDvo1u1c5vphlg-gM4QTFLlfbqA/view?usp=drivesdk). Drive ID `1DQ2MaHDvo1u1c5vphlg-gM4QTFLlfbqA`, 47,297,228 bytes, SHA-256 `4fff7aad4c8e812d4b039161262b55da23d9647bc8c46dbda4b3ec70604524d5`; a raw Drive readback matched the uploaded ZIP byte-for-byte. The ZIP contains only the APK.
- Separate PERSONAL QA run `39474605036` failed before building because `QA_KEYSTORE_B64`, `QA_KEYSTORE_PASSWORD`, `QA_KEY_ALIAS`, and `QA_KEY_PASSWORD` were absent; it uploaded a failure report only. No replacement signing key was created.

## Remaining

H1 is not declared complete: remaining criteria require physical-device validation/review. H1-AT-06/20/24 and M01-06 remain device-gated; M01-06 is `PENDING_REQUIRES_DEVICE`. The Moto G86 Power, original XLSX and prior backup condition for AT-20 are still required. No inference latency, throughput, memory, power, thermal, or answer-quality metrics were measured. H2 remains unstarted. Original plan/task IDs/dependencies remain unchanged.
