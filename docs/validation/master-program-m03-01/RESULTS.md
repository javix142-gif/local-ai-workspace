# M03-01 artifact API — focused host preparation

**Status: preparatory evidence only; original task M03-01 remains TODO.** This checkpoint does not close the source task because its authentic prerequisite M02-05 remains TODO (and M02-05 depends on M02-01 and M01-05). No original backlog entry or status was edited.

## Scope and findings

The inspected implementation already has a bounded `ArtifactSpec`, immutable app-private version directories, content/spec hashes, validation and read-back checks, atomic publication, recovery handling, preview support, and an explicit Storage Access Framework export path. This increment added no production code. The source/test change under investigation is limited to `ArtifactsPatchPreviewUiTest.kt`; it makes the Compose test wait for the asynchronous preview row and asserts the full status text emitted by the current UI. It does not prove a product defect or establish every M03-01 requirement.

The M03-01-focused slice is the four existing suites `ArtifactRoundtripTest`, `ArtifactPatchRecoveryTest`, `ArtifactRevisionPreviewUiTest`, and `ArtifactSafExportUiTest`: 13 tests passed in the first two composite runs. They cover create/reopen/edit while retaining previous bytes, hashes and project isolation, bounded/invalid inputs, recovery, a Compose preview/cancel case, and explicit SAF export/read-back in Robolectric. In those same composite runs, the separate `ArtifactsPatchPreviewUiTest` (two cases associated with code-patch UI) failed. A later exact-source isolated discard case passed. The apply case has not been rerun after its expected-status assertion was corrected.

## Commands and results

Environment: Linux; Gradle Wrapper 8.10.2; Temurin JDK 17 as Gradle runtime; Temurin JDK 21 available to Gradle test workers/toolchain discovery; Android SDK API 35, build-tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6; existing `GRADLE_USER_HOME=/workspace/.gradle`. Network proxy settings are recorded verbatim in each `command.txt`. No SDK, NDK, dependency, or model was installed by these runs.

| Attempt | Source SHA | Scope/result |
|---|---|---|
| 01 | `bb2c9829732b6c82c6d3cbdf01c65509925284ed` | Composite 15 tests: 13 pass, 2 fail in patch-preview UI; exit 1. The other four suites were 13/13. |
| 02 | `5514d085bccf14948cb6efa019df52f009f8950c` | Composite 15 tests: 13 pass, 2 fail in patch-preview UI; exit 1. Failure evidence showed asynchronous-row/status assertion issues, not a demonstrated API defect. |
| 03 | `6d60bf6a8065309444e087b41f138c0d1633279f` | Patch-preview class: 0/2 pass, exit 1. Apply reached the UI's successful immutable-v2 status; the assertion expected a partial phrase as an exact value. The discard case still timed out waiting for its row. |
| 04 | `1b82c05c65edfe173fe2d968c85dc38598f2f769` | Isolated discard case: XML 1/1 pass, Gradle log BUILD SUCCESSFUL, captured exit 0. The execution tool reported outer exit 1; discrepancy retained. |
| 05 | `1b82c05c65edfe173fe2d968c85dc38598f2f769` | No test executed: `testDebugUnitTest` was UP-TO-DATE, no new XML; captured exit 0, outer tool exit 1. Not counted as a pass. |
| 06 | `1b82c05c65edfe173fe2d968c85dc38598f2f769` | Forced rerun of discard with `--rerun-tasks`: 1/1 pass, XML `6.471s`; Gradle BUILD SUCCESSFUL, 63 tasks executed, captured exit 0. The outer exec session again reported exit 1; cause unresolved. No further test run was made. |

Attempt 06 used:

```text
JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk GRADLE_USER_HOME=/workspace/.gradle timeout --signal=INT --kill-after=20s 300s ./gradlew --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.ui.ArtifactsPatchPreviewUiTest.discardingPreviewDoesNotChangeArtifactHistory
```

The command, captured exit marker, run metadata, task outcomes, JUnit XML, and lossless compressed Gradle logs are retained under `attempt-01` through `attempt-06`. Attempt 05 is explicitly `NOT_EXECUTED_UP_TO_DATE`. Attempt 06's fresh XML timestamp and one passing testcase agree with its Gradle log and captured command exit; the tool-session exit discrepancy is not explained and is not hidden.

## Validation boundaries

- Preparatory M03-01 focused tests: 13/13 pass in attempts 01 and 02, but these are host/Robolectric tests only and do not close M03-01.
- Patch-preview UI: one isolated discard test passed on the final test SHA; the apply case after its assertion correction is unverified. The earlier class failures are retained.
- Production code changed: none in this increment. The only code path change is test-only.
- Full JVM/LiteRT gate, lint, AndroidTest compilation, emulator, Motorola, APK, and real Office-reader roundtrips: NOT_RUN.
- M03-01 and M03-07 remain source TODO. M03-07 additionally requires a device. M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola is `PHYSICAL_DEVICE=NOT_RUN`.
- The preserved backlog/roadmap source remains byte-identical: 97 unique IDs, 120 dependency edges, all source statuses TODO. Their original source files were not changed.

Evidence file digests are recorded in `SHA256SUMS`; the compressed logs preserve their original bytes and `log-hash.json` records both raw and gzip digests.
