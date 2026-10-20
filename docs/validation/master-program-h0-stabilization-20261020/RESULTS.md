# H0 host stabilization — 2026-10-20

Source code SHA: 49f5913937fe7c049bf0ad71d2d75f7e5321b557
Working branch: codex/aws-master-resume-20261019
Feature target before publication: feat/0.5.0-skills-agents at e6f16ac30914fe32f7140241e81e5976660c03d2. That ref is an ancestor of the working HEAD; no branch switch, reset, or cleanup was performed.

## Changes

- Updated a Room version assertion from 8 to the actual current version 10.
- Corrected the DOCX fixture assertion to check the two pages and page-break content emitted by the production parser.
- Replaced the fabricated v1 migration-table fixture with a test from the authentic exported schema v8 through migrations 8→9→10. It does not validate historical v1 migrations; the authentic v1 schema remains unavailable, and the instrumentation test was compiled only.
- On confirmed document removal, immediately dismisses stale details while the repository completes asynchronous cleanup. The host test verifies Room deletion, the durable retry outbox, the user's retry action when needed, and source-file preservation.
- Kept project-dialog visibility/input assertions in the existing Android device test. The host screenshot test covers layout only because the platform AlertDialog/OutlinedTextField interaction did not settle in Robolectric; the device-test assertion remains.

No inference runtime, model, retrieval, permission, schema, dependency, or source-plan change was made. Room remains version 10.

## Final host results

| Check | Result | Evidence |
| --- | --- | --- |
| app:testDebugUnitTest + litert-compat:testDebugUnitTest | PASS, app 916/916 and compatibility 2/2; 125 + 1 XML; zero failures/errors/skips | XML archive and count summary under xml/; full log under logs/full-jvm.log.gz |
| app:compileDebugAndroidTestKotlin | PASS, Kotlin test sources compiled | logs/androidtest-compile.log.gz |
| app:lintDebug -Parm64Only=true | PASS, 0 errors, 77 warnings, 1 informational issue | logs/lint-arm64.log.gz |
| app:assembleDebug -Parm64Only=true | PASS, CMake/Ninja ARM64 native build and Debug APK packaging completed | logs/assemble-debug-arm64.log.gz |
| Instrumented Android tests | NOT_RUN | AndroidTest was compiled, not executed |
| Emulator / Motorola | NOT_RUN | No device validation in this increment |

The forced JVM invocation used Gradle 8.10.2, Temurin JDK 17 for Gradle, JDK 21 for app JVM test workers, SDK API 35, NDK 27.0.12077973, CMake 3.31.6 and the existing Gradle user home. Full commands and toolchain details are included.

Initial attempts remain separately recorded and are not counted as PASS: JVM runs exposed five, then three failures; the final forced rerun passed. AndroidTest compilation first missed uncached artifacts offline, then timed out with the executor Java network path; the final run used the installed system CA truststore with TLS verification enabled. An all-ABI lint attempt timed out while configuring extra CMake targets; ARM64-scoped lint passed. Assembly attempts failed offline, without Java proxy configuration, and with Temurin's truststore; the final assembly succeeded using the installed system truststore. No certificate was imported and TLS verification was not disabled. Failed logs are retained under logs/.

## Local QA APK

The local Debug APK passed ZIP CRC, zipalign, apksigner v2, package/version, and native ABI checks:

- Package: com.localai.workspace.debug
- Version: 0.5.0-debug / versionCode 27
- ABI: arm64-v8a only
- APK: 121,092,571 bytes; SHA-256 70955926aa58be1a0739975aae509d2da260acd0370565bd7ed3329b30e40b0e
- Current generated Debug certificate SHA-256: 4d95e3efd34874f6564aee7a5f347e2aa406f63a41b40d44763ddaac0603c955
- Previously retained update certificate: f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b

The compatible private key was not present in the executor; the only standard Debug keystore found was generated during this build and has a different certificate. The APK is not update-compatible with the prior personal delivery. It was not uploaded, installed, or presented as an update. Do not uninstall or clear app data to use this APK. No model weights are present; the only .bin ZIP entry, DebugProbesKt.bin, is compiled Java class data, not a model payload. See apk-validation.txt.

## Plan and remaining gates

The canonical backlog and roadmap remain byte-identical: 97 unique task IDs, 120 dependencies, all source statuses TODO. No original task is closed by these fixes. The authentic v1 migration fixture remains a historical blocker. The prior emulator result remains a Room-v1 failure with six actual-model tests skipped; that suite was not rerun here. Native inference, E2B performance, TTFT, tokens/s, memory, energy, temperature, and Motorola behavior were not measured. PHYSICAL_DEVICE=NOT_RUN and M01-06 remains PENDING_REQUIRES_DEVICE.

The user-provided visual guide SHA-256 is 9cbc5b998887481ffee9c42eaf5b900776d5e2152b9a75e9b0891f485e34966e; it is design guidance, not a brand-image reference set. No direct ChatGPT/Claude image comparison was made.

Source task IDs/dependencies are preserved. H0 is a host stabilization checkpoint, not a roadmap completion.
