# UX-02e — inline Markdown links in chat

**Code SHA:** `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`
**Branch:** `feat/0.5.0-skills-agents`
**Baseline remote HEAD at start:** `fca3e65b16c5d54ef30747c4c90c13608e520499`
**Status:** implementation committed; parser-only JVM check passed. A recovery attempt confirmed a usable pre-existing SDK/NDK but the Gradle gate stopped before tasks because the selected Java runtime lacks the compiler. UX-02e is **not accepted** and this is **not** a full HOST PASS.

## What changed

The assistant chat's existing `ChatRichText` path now recognizes inline `[label](URL)` syntax in paragraphs, headings, and list items. The parser accepts absolute `http` and `https` URIs with a host, including balanced parentheses in the destination. It rejects relative URLs, malformed destinations, empty/nested labels, whitespace/control characters in destinations, and all other schemes. Rejected Markdown remains visible as literal text; malformed outer tokens are consumed as literal spans so a nested URL cannot become a live link accidentally.

Links use the Compose `LinkAnnotation.Url` API. The displayed label uses the theme primary color and underline. The annotation retains the destination for accessibility and the platform opens it through `UriHandler` only after link activation. No WebView, network fetch, preview, or download path was added. The focused Compose test supplies a replacement `UriHandler` and asserts that neither HTTP nor HTTPS opens before an explicit click.

Inline and fenced code remain on their existing code paths and are not parsed for links. Image syntax and HTML tags remain literal; Markdown-looking text inside an HTML tag is kept literal. Table cells call the same inline renderer with link parsing disabled, leaving the existing table behavior unchanged. Free-text citation syntax remains unparsed.

The original assistant response remains the value passed to the response-copy action. The existing button was extracted as `AssistantResponseCopyAction` so its clipboard behavior can be tested; it still copies `AnnotatedString(content)` from the unmodified response. The separate menu copy path in `MainActivity` is unchanged.

Product/test files in the code commit:

- `app/src/main/java/com/localai/workspace/ui/BasicMarkdown.kt`
- `app/src/main/java/com/localai/workspace/ui/ChatContent.kt`
- `app/src/main/java/com/localai/workspace/MainActivity.kt` (only delegates the existing copy button to the shared composable)
- `app/src/test/java/com/localai/workspace/ui/BasicMarkdownTest.kt`
- `app/src/test/java/com/localai/workspace/ui/ChatContentUiTest.kt`

No dependencies, navigation, retrieval, persistence, Room, model/runtime, permission, Agent/Tool, or workflow files changed.

## Checks and outcomes

### Single recovery attempt with the pre-existing SDK

The recovery check found `/workspace/.toolchain/android-sdk` already installed with API 35, Build Tools 35.0.0, CMake 3.31.6, and the exact NDK `27.0.12077973`. Its `source.properties` reports revision 27.0.12077973, and `ndk-build`, `clang`, and `clang++` were present. No SDK/NDK installation, copy, cleanup, or version change was made. The existing Android emulator and QA workflows were inspected; neither was dispatched or triggered by this documentation-only recovery.

The gate was run against code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`; the checked-out HEAD was `720099451426e4ecc98d338f74ed52c33f31be24`, whose product/build source is identical to the code commit (docs-only commits follow it).

Exact command:

```text
ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

It exited **1** during task-graph dependency calculation, before any requested task started. Gradle selected `/usr/lib/jvm/java-21-openjdk-amd64`, an OpenJDK runtime without `JAVA_COMPILER`; `javac` was absent from PATH. Two full JDKs were found afterward under `/workspace/.toolchain`, but the single recovery attempt was not repeated with a different Java selector.

No JUnit XML was emitted. Existing ignored test XML predates this attempt (the inspected results were timestamped around 09:57 UTC; the attempt began at 11:32 UTC) and is not counted. The raw gate log is preserved byte-for-byte in [`host/recovery-gradle-gate.log.gz`](host/recovery-gradle-gate.log.gz); SDK/NDK and Java preflight details are in [`host/recovery-environment.txt`](host/recovery-environment.txt).


### Parser-only JVM check

A supplementary standalone runner compiled `BasicMarkdown.kt` and `BasicMarkdownTest.kt` with the cached Kotlin 2.0.21 compiler, JUnit 4.13.2 and Java target 17, then ran JUnit directly. It is not the app Gradle suite and does not cover Compose rendering.

- Command/runner: `python3 docs/validation/ux-02e-inline-links/host/standalone-parser-check.py`
- Code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`
- Compile exit: 0
- JUnit: **20 tests, 20 passed**, exit 0
- Raw output: [`host/parser-standalone.log.gz`](host/parser-standalone.log.gz)
- The direct JUnit runner does not emit Gradle XML; no XML is claimed for this check.

It covers HTTP and HTTPS destinations, surrounding text, rejected schemes, malformed input, nested links inside rejected/image syntax, HTML attributes, inline/fenced code, and literal table-cell mode.

### Earlier Android Gradle attempt with the incomplete temporary SDK

The initial proportional gate attempt (before the recovery check above) used the temporary SDK and existing Gradle cache:

```text
ANDROID_HOME=/workspace/android-sdk-ux02e ANDROID_SDK_ROOT=/workspace/android-sdk-ux02e ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

- Environment: Linux x86_64, OpenJDK 21.0.12.1, Gradle Wrapper 8.10.2, Android API 35 and Build Tools 35.0.0 installed under `/workspace/android-sdk-ux02e`.
- Exit: **1 during project configuration**, before any requested task ran.
- Exact cause: AGP rejected `/workspace/android-sdk-ux02e/ndk/27.0.12077973` because it has no `source.properties`. The SDK manager had previously failed to install that NDK with `No space left on device`; no NDK was fabricated or substituted.
- Therefore Gradle JVM tests, Compose/Robolectric tests, compatibility tests, lint and AndroidTest Kotlin compilation are **NOT_RUN** in this contract. No JUnit XML was generated by Gradle.
- Captured raw output: [`host/gradle-proportional-gate.log.gz`](host/gradle-proportional-gate.log.gz).
- Earlier environment setup attempts that stopped before test execution are retained under [`setup/`](setup/); they are not test results.

Compose tests were added for HTTP/HTTPS URI activation through a fake `UriHandler`, label/destination annotations, visible rejected and malformed syntax, unchanged literal table links, and exact original Markdown copying. These tests are authored but **NOT_RUN** because Gradle could not configure the Android project.

## Validation layers and limits

- **HOST:** parser-only standalone JUnit 20/20 PASS. The single recovery Android Gradle/Compose gate is BLOCKED before task execution because the selected system Java runtime lacks `JAVA_COMPILER`; do not promote this to a full HOST PASS. The earlier attempt with the temporary SDK had a separate incomplete-NDK blocker.
- **EMULATOR:** NOT_RUN for UX-02e. The prior global result remains FAIL: 16 cases, 9 PASS, the historical authentic Room v1 fixture FAIL, six real-model-dependent SKIPPED. This contract did not rerun it.
- **PHYSICAL_DEVICE:** NOT_RUN.
- **APK:** NOT_GENERATED.
- No TalkBack session or device visual validation was performed. The Compose annotation carries the visible label and URL destination for the platform accessibility link span; screen-reader behavior remains to be verified on device.
- `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.
- The original 97-task plan is not tracked in this repository and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02f and UX-03 remain pending; functional backlog remains paused.

## Evidence

`SHA256SUMS` covers this report, the JSON summary, the standalone runner, captured logs and environment records. No Gradle XML exists for the recovery attempt because task graph calculation failed before test tasks started. Existing ignored XML predates the attempt and is not counted.
