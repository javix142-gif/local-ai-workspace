# Artifact API — format-aware SAF export (preparatory)

**Status:** focused HOST PASS; preparatory evidence only. Original task M03-01 remains `TODO`, because its dependency M02-05 remains `TODO`. No source task ID, dependency, or backlog status was changed.

## Finding and change

The existing Artifact API recorded a MIME type for each `ArtifactFormat`, and export already wrote and read back the exact immutable revision bytes. The SAF chooser nevertheless always received `application/octet-stream`. A red regression in `ArtifactSafExportUiTest.explicitCreateDocumentChoiceExportsExactVerifiedBytesAndReadsThemBack` failed with the exact comparison `expected text/plain, was application/octet-stream`; the chooser request was not using the artifact's known format.

At code SHA `0551915039efdd17557271a793163290f30be8d2`, `ArtifactCreateDocumentContract` receives the validated artifact file name and `ArtifactFormat`, then builds the existing `ACTION_CREATE_DOCUMENT` flow with that format's MIME, the suggested name, `CATEGORY_OPENABLE`, and `EXTRA_LOCAL_ONLY`. The Compose export still resolves the chosen immutable version, writes its verified bytes, reads them back, and compares SHA-256. Tests check the live CODE export and all five format request types (XLSX, DOCX, PDF, PPTX, CODE). The bytes, preview, version history, project scope, Room schema, tools, permissions, and signers are unchanged.

## Exact-source tests

- Red baseline: test-only commit `fd6ea2055c432da7b2a1d464d09158f6598a7820`; focused JUnit **1 test / 1 expected failure**, no error or skip; actual Gradle exit 1 after 203 seconds. The failure is specifically the MIME mismatch above. See [`red-existing-gradle-home`](red-existing-gradle-home/).
- Final code: `0551915039efdd17557271a793163290f30be8d2`; `ArtifactSafExportUiTest` **2/2 PASS**, 0 failures/errors/skips, Gradle exit 0 in 201 seconds, 63 actionable tasks executed. The integration test verifies the suggested filename, exact exported bytes and read-back digest; the other case verifies the create intent for all five existing formats. See [`green-0551915`](green-0551915/).
- A preceding wrapper attempt at the same red test commit did not start Gradle: the wrapper tried downloading its pinned Gradle distribution and got `Connection refused`. It is preserved in [`red-fd6ea20`](red-fd6ea20/) and is not counted as a test result. The red run used the already-present `/workspace/.gradle` distribution offline; no tool or dependency was installed.

Command for the final run:

```text
timeout 300s env GRADLE_USER_HOME=/workspace/.gradle JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.ui.ArtifactSafExportUiTest
```

Gradle 8.10.2 ran on Temurin 17.0.20.1; test workers used Temurin 21.0.12.1. Existing Android SDK API 35 and NDK 27.0.12077973 were used. Full logs, commands, timestamps, exit markers, XML inventory and summaries are retained per attempt. `SHA256SUMS` covers the evidence payloads other than `RESULTS.json` and the manifest itself; `log-payload-hashes.json` also records hashes of the losslessly gzip-compressed log payloads.

## Scope limits and source plan

This did not run the full app/LiteRT JVM gate, lint, AndroidTest compilation, assembly, emulator, physical Motorola device, or APK creation. Prior attempts on this workspace found the AndroidTest dependencies and `lint-gradle:31.7.3` uncached; one online AndroidTest compile then failed because Google Maven/Maven Central were unreachable. No retry is claimed here. No compatible local Debug signing key was found in the previously checked standard locations; no replacement key was generated. Therefore this increment produced no APK.

M03-01 remains source `TODO` and preparatory: M02-05 remains `TODO`; cell-citation host evidence does not close its original dependency or physical validation. The archived backlog and roadmap remain unchanged at 97 unique IDs, 120 dependency edges, and 97 source `TODO` states. The historical emulator result remains separate: Room v1 fixture failure and six model-dependent skips; no instrumented run was made for this change. `M01-06=PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`.
