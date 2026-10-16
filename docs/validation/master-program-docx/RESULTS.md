# DOCX generator — preparatory host evidence for M03-03

This increment closes a specific test-coverage gap on the generated-DOCX path. It does not close M03-03. The original task remains `TODO`, `requires_device=true`, and depends on M03-01 and M02-03, which remain formally TODO. No imported document was edited.

## What is verified

A new API 35 Robolectric test creates a synthetic DOCX through the production `ArtifactStore`/`ArtifactRenderer`, checks required package parts, content-type declarations, and internal relationship targets, and parses the XML parts. It verifies Title and Heading1 style references and definitions, bullet `numId` resolution, an explicit page-break paragraph, a rectangular 2×2 table grid/cell structure, and extracted page/table content. It then creates a new immutable revision and confirms the older bytes/hash are unchanged.

The resulting synthetic v1 and v2 DOCX files also opened with `python-docx 1.2.0`. The independent reader found the title and heading styles, list numbering id 1, the page break before “Segunda página”, the 2×2 table and exact Spanish/Unicode cell text. The two files and reader output are retained in `attempt-04-api35-exact-source/`.

No production code or dependency changed: inspection and independent reading did not demonstrate a renderer defect. This increment adds test coverage only.

## Attempts and result

| Attempt | Source | Outcome |
|---|---|---|
| `attempt-01-independent-reader` | published base `bacb049bdb3732a6354ec48c3899cd8fb5e2a16a` | `ArtifactRoundtripTest` did not reach its body: Robolectric SDK 29 tried to fetch `android-all-instrumented:10-robolectric-5803371-i7`, then got `Connection refused`. Gradle exit 1; classify as environment/setup failure, not product failure. JUnit XML records one bootstrap failure. |
| `attempt-02-api35-structure` | `dd7abdfa6cba62014973245614b4bc5336f5e0cc` | Compilation failed in the newly authored test (wrong JUnit import and suspend calls outside `runBlocking`); `testDebugUnitTest` did not execute and this attempt has no new JUnit XML. |
| `attempt-03-api35-exact-source` | `6bdc710600d2930bffe23182b809bc32597e7f4b` | Test ran and failed one assertion because the harness expected table text on page 1 even though this descriptor serializes all tables after its paragraphs, including the page-break paragraph. This was a test expectation error; no production defect was shown. The generated v1 file was separately opened by `python-docx`. One initial reader invocation used the wrong relative path and failed to locate the file; the corrected invocation passed. |
| `attempt-04-api35-exact-source` | `c0bc7504595c40976d86bf2fe2375f8382b0a94e` | **PASS**. `DocxArtifactStructureTest`: 1/1, 0 failures/errors/skips, JUnit method time 8.507 s; Gradle exit 0, 4m09s, 63 tasks executed with `--rerun-tasks`. The independent `python-docx` check opened v1 and v2 and passed all content/style/list/page/table assertions. |

The final host command was:

```sh
ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016 JAVA_HOME=/workspace/.toolchain/jdk-17-temurin \
PATH=/workspace/.toolchain/jdk-17-temurin/bin:$PATH \
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --rerun-tasks \
  -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin \
  :app:testDebugUnitTest --tests com.localai.workspace.artifacts.DocxArtifactStructureTest
```

Toolchain: Gradle Wrapper 8.10.2; Temurin JDK 17 runtime; Temurin JDK 21 worker/toolchain; Android SDK API 35; NDK 27.0.12077973; pre-existing Gradle cache, offline. No tools, dependencies, or SDK packages were installed.

## Existing push workflows for the base SHA

The earlier push of `bacb049` triggered Android emulator run [38981700237](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700237) and official gated QA run [38981700277](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700277). The emulator booted and `:app:connectedDebugAndroidTest` executed: XML had 16 cases, 9 passed, 1 failed (the missing authentic Room v1 schema), and 6 model-dependent cases skipped. This is EMULATOR FAIL, not PASS. Its evidence artifact is [12081328312](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700237/artifacts/12081328312), 151,404 bytes, SHA-256 `ec89be926f36ae81d66a08231f58bc9b8e5bff9f8b93291b57d7bb89bb0d313f`; the downloaded archive hash matched the Actions API digest. The QA workflow failed closed at the exact-SHA instrumentation gate; APK build and artifact upload were skipped. Neither run tested the later DOCX test commits.

No APK was generated for this increment. HOST evidence above is not emulator or Motorola validation. No full JVM suite, LiteRT compatibility suite, lint, AndroidTest compile/run, device benchmark, or physical test was run for this DOCX increment.

## Original plan and next step

The source backlog and roadmap remain unchanged at SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`: 97 unique IDs and 120 dependency edges; all 97 source statuses remain TODO. M03-03 stays TODO and device-required. M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola is `NOT_RUN`; the global emulator state remains the Room-v1 failure plus six model-based skips.

Next preparatory review: M03-04, the existing local PDF generator. Its original task is TODO, requires a device, and depends on M03-01 (TODO). Inspect actual layout/text/font/page behavior and add only bounded host evidence or a demonstrated minimal fix; do not claim physical-reader or Motorola validation from host tests.
