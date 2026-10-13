# UX-02e — focused Robolectric reproduction (2026-10-13)

## Latest attempt

One isolated Gradle invocation was made for the exact test that had remained active in the prior incomplete full gate. Source code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`; feature HEAD at start: `0368248d4ecc03e5dc119baa85ef14384f46f9ad`. The detached worktree was clean including ignored files before execution.

Target: `ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit`. The invocation ran from `2026-10-13T15:28:54.003Z` to `15:32:34.280Z` (**220.276 s**, wrapper exit **130**). Gradle logged repeated `java.net.SocketException: Network is unreachable` retries to `dl.google.com:443` (18) and `repo.maven.apache.org:443` (36) while configuring/resolving. After 120 seconds with no JUnit event, the process snapshot showed only this invocation's JDK 17 wrapper (PID 136571) and child single-use daemon (PID 136605); no test worker existed. Those two invocation processes were sent SIGINT. No Gradle task/build summary was emitted.

| Layer | Result |
| --- | --- |
| Gradle invocation | **INCOMPLETE / TIMEOUT_BEFORE_TEST_START**; observed network unavailability during configuration/dependency resolution |
| Target test | **NOT_RUN**; 0 TEST_START / 0 TEST_END |
| Test worker / thread dump | **NOT_CREATED**; jcmd, jstack and SIGQUIT were not applicable because no worker PID existed |
| New JUnit XML | **0 files**; 0 tests, failures, errors or skips |
| Other HOST tasks | **NOT_REQUESTED** |
| EMULATOR / PHYSICAL_DEVICE / APK | **NOT_RUN / NOT_RUN / NOT_GENERATED** |

This run does **not** reproduce or explain the earlier Robolectric worker stall: it never reached the target test. The earlier full-gate active method and its cause remain `NOT_DETERMINED`; no product failure is established. UX-02e remains `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED` and **not accepted**. Earlier parser standalone 20/20 and focused diagnostics 16/16 remain supplemental history only; the prior full HOST gate remains incomplete.

Evidence and exact command: [`focused-robolectric-20261013/`](focused-robolectric-20261013/), especially `run-analysis.json`, `command.txt`, `environment.json`, raw/compressed Gradle logs, test event file, process snapshots, and XML inventory. Raw log SHA-256: `88c2ad6485a6408600aef89d3550106b8df2985dc4d060adf3aeff1a7d1879ef`; gzip SHA-256: `5ca852c0ef3bf1ddeb68e16af5fe278c6b13ff671789363272d7455d8683ec2b`. Parent evidence manifest is regenerated with this report.

## Original-task eligibility review

The current checkout contains no authentic 97-task catalog (`ROADMAP.md`, `BACKLOG.json`, `LOOP_RULES.md` or matching task-plan file); the persistent state records only `M01-06` as the next original task. It remains `PENDING_REQUIRES_DEVICE`, so it is not eligible for this host-only contract. A historical selection note records dependencies for `M01-01` and `M02-01`, but those notes were not revalidated against the unavailable original catalog and no new task is selected. The 97 IDs/order/dependencies were not edited or reconstructed.

---

# UX-02e HOST gate — independent audit result (2026-10-13)

## Audited status

`AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`. Tested product code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. The audited remote feature HEAD was `ab349096d3a74fcd10eb83819ddb3d8bd8484a96` (16 ahead / 0 behind from the code SHA); the final documentation commit is docs-only and no product or workflow files changed after the tested SHA. `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.

The independent auditor cross-checked `full-host-gate-20261013/run-analysis.json`, `command.txt`, `task-outcomes.txt`, `junit-xml-inventory.txt` and `test-events.tsv`: exactly one HOST invocation, **17m04.871s**, controller result `-9` after the no-event cutoff, 36 `TEST_START`, 35 `TEST_END` (all successful), zero new XML, and no final Gradle result. The active method without an end event was `com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit`.

| Gate task | Audited result |
| --- | --- |
| `:app:testDebugUnitTest` | `INCOMPLETE_STALLED`; active method identified, no new XML/result |
| `:litert-compat:testDebugUnitTest` | `NOT_REACHED` |
| `:app:lintDebug` | `NOT_REACHED` |
| `:app:compileDebugAndroidTestKotlin` | `NOT_REACHED` |

`jcmd` returned 124 with zero bytes; SIGQUIT produced no recovered thread dump. Causality remains **NOT_DETERMINED**; **PRODUCT_FAILURE_NOT_ESTABLISHED**. UX-02e is **NOT ACCEPTED**. The earlier focused runs (ChatContentUiTest 15/15; AuditHarness selector reproduction 1/1) remain diagnosis-only and do not satisfy the full gate.

## Evidence integrity and limits

The executor recorded successful `sha256sum` checks of 93/93 parent-manifest entries and 40/40 full-gate submanifest entries before publication. The independent auditor matched the manifest counts/digests and rehashed 24/25 accessible submanifest text files; the remaining `source-worktree-status-before.txt` is zero bytes with the standard empty-file SHA-256. The connector rejected 15 `.gz` payloads, so the independent auditor did not download or rehash those binary payloads. This limit does not alter or replace the executor’s historical checksum report.

HOST is incomplete. UX-02e EMULATOR and `PHYSICAL_DEVICE` are `NOT_RUN`; the separate historical global EMULATOR result remains Room v1 FAIL with six model-dependent tests SKIPPED. No APK was generated. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged. No further HOST attempt or UX-02f/UX-03 is authorized until new evidence or explicit instruction.

Evidence was published by fast-forward on `feat/0.5.0-skills-agents`; see `EXECUTION_STATE.json` and `CODEX_TO_WORK_HANDOFF.md` for publication SHAs and the parent manifest digest.

---

## UX-02e implementation and earlier validation record (historical; latest gate is above)

**Code SHA:** `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`
**Branch:** `feat/0.5.0-skills-agents`
**Remote feature HEAD at this validation:** `51434af64d64c40157ddf61548e9ebaae3ec8217`
**Status at the earlier diagnosis point:** UX-02e remained **not accepted**. The original full HOST gate was incomplete (operator-interrupted, exit 130); two bounded filters later passed 16/16 without reproducing it. The latest single full-gate result is recorded at the top of this report and remains incomplete, with its active method identified but cause undetermined.

## Bounded Robolectric stall diagnosis (2026-10-13)

The full-gate log's last named output marker was `AuditHarness > writeSelectorReproductions STANDARD_OUT`, followed by a generic JDK 21 worker stack waiting through Robolectric `Sandbox.runOnMainThread`. That output marker did not identify the active test, and the stack did not contain a class/method identity. Two distinct filtered executions were then made once each against exact source SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, each with a 450-second hard limit and the README JDK configuration:

| Target | Start–end UTC | Duration | Exit | JUnit | Outcome |
| --- | --- | ---: | ---: | --- | --- |
| `com.localai.workspace.ui.ChatContentUiTest` | 11:40:20–11:43:07 | 167.004 s | 0 | 15 tests, 0 fail/error/skip | PASS; stall not reproduced in this class |
| `com.localai.workspace.context.AuditHarness#writeSelectorReproductions` | 11:43:57–11:46:30 | 153.034 s | 0 | 1 test, 0 fail/error/skip | PASS; candidate method not stalled in isolation |

Both Gradle logs say `BUILD SUCCESSFUL`; each reports 63 actionable tasks (1 executed, 62 up-to-date). The test worker was observed under Temurin JDK 21.0.12.1 and Gradle under Temurin JDK 17.0.20.1. No run reached the 3–4 minute no-progress threshold, so the timeout process-tree/jcmd snapshot path was not triggered. The first run's raw observer summary says XML=0 because its monitor expected a custom path; AGP wrote the JUnit file at its default path. The preserved XML (15 tests, timestamp within the run), Gradle log, and `--info` report corroborate the corrected summary; the discrepancy is retained in `chat-content-ui/observer.log.gz`. The second observer and XML agree at 1/1.

**Conclusion:** the earlier 3311-second stall was not reproduced by either focused execution. The old dump does not identify a test method. Do not infer a Robolectric defect, JDK/worker defect, product deadlock, or environmental root cause. The cause remains **NOT DETERMINED**; no product defect is established. The focused run evidence and SHA256 manifest are in [`diagnostic-20261013/`](diagnostic-20261013/RUNS.md). Its 19 payload files verify against the diagnostic manifest SHA-256 `32237f5a599673465744dd0301ab7b71d13ffded1d617750fd83abd70cca5aff`. At the time of that earlier audit, the parent manifest contained 52 entries. The subsequent full HOST gate and refreshed manifests are recorded above and in `EXECUTION_STATE.json`; no acceptance is claimed.

## First historical full HOST attempt — JDK 17 Gradle runtime (2026-10-13; superseded by the latest gate above)

- Source SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, freshly checked out detached at that exact commit in `/workspace/ux02e-a182-jdk17gradle-contract`. Feature branch HEAD at the start was `51434af64d64c40157ddf61548e9ebaae3ec8217`; the source commit was an ancestor and changes after it were documentation only.
- Read-only preflight passed for Temurin JDK 17.0.20.1 and JDK 21.0.12.1: both `java`, `javac`, `jdk.compiler`, and `ToolProvider.getSystemJavaCompiler()` were available. Gradle ran with JDK 17, and `--info` confirms the app test worker launched from JDK 21 as configured in `app/build.gradle.kts`. Toolchain discovery exposed both homes. Existing API 35 SDK, Build Tools 35.0.0, NDK 27.0.12077973 with `source.properties`, and CMake 3.31.6 passed preflight. No JDK/SDK components or dependencies were installed by the operator, and no caches/data were deleted.
- The exact command is recorded in [`command.txt`](host/jdk17-runtime-20261013/command.txt); the full read-only preflight is in [`preflight.txt`](host/jdk17-runtime-20261013/preflight.txt). The command ran once:

  ```text
  JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --console=plain --info --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
  ```

- Start/end: `2026-10-13T02:58:09Z`–`2026-10-13T03:53:20Z`; duration **3311 seconds**; wrapper shell exit **130** (`tee` exit 0). Gradle compiled the app and entered `:app:testDebugUnitTest`; the task did not emit a completion summary. After about 45 minutes without new log output or XML, a SIGQUIT dump showed the test worker waiting in `FutureTask.get` through `org.robolectric.internal.bytecode.Sandbox.runOnMainThread`. The dump does not identify the test class or method. The worker was stopped by the operator; Gradle emitted neither `BUILD SUCCESSFUL` nor `BUILD FAILED`. Cause remains undetermined. This is **INCOMPLETE_STALLED_ROBOLECTRIC_WORKER_OPERATOR_INTERRUPTED**, not an assertion failure or product diagnosis.
- `:litert-compat:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin` were **NOT_REACHED**. The app test task did not finish; its tests/passed/failed/errors/skipped counts are **NOT_AVAILABLE**. A fresh-worktree search found **0 JUnit XML**. No XML was counted from the main checkout or from non-test Android resource outputs.
- Gradle logged failed network retries to `dl.google.com` and Maven Central while resolving/configuring, but it reached test execution using available local dependencies. Network is not established as the terminal cause. No emulator, physical device, APK, or Android installation was used.
- Full log: [`gradle-gate.log.gz`](host/jdk17-runtime-20261013/gradle-gate.log.gz), raw SHA-256 `9e6ad449bff8a0d7039096b4f93cb7b6e297d224de8197aa4bec29b9faba7fc6`, gzip SHA-256 `aaf91f91b166eef790bdf88d22ec7daa785ca421be35a5d395668b4d040d6efc`. Run metadata, task progress, the zero-XML inventory, file hashes and the [post-run clean tracked/untracked status check](host/jdk17-runtime-20261013/post-run-working-tree.txt) are alongside it. The evidence manifest at that historical publication contained 32 entries; this contract refreshes it to include the focused diagnostic evidence.
- At the time of this full-gate report, no additional retry was included. The later bounded diagnosis is recorded above; it did not reproduce the stall and does not complete the full host gate.

## Historical attempt — JDK 21 runtime contrary to repository README (2026-10-13)

- Exact source code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, in a newly created detached worktree `/workspace/ux02e-a182-jdk17toolchain`. Before the run, `git status --porcelain=v1 --ignored=matching --untracked-files=all` was empty; `.gradle/`, `app/build/`, `litert-compat/build/`, `llama-runtime/build/`, `.cxx/`, and `local.properties` were absent. The source SHA is an ancestor of the feature HEAD; later changes are documentation/evidence only.
- JDK 21 runtime preflight passed at `/workspace/.toolchain/jdk-21-temurin` (Temurin 21.0.12.1, `java`, `javac`, `jdk.compiler`, and `ToolProvider.getSystemJavaCompiler()` available). JDK 17 toolchain preflight passed at `/workspace/.toolchain/jdk-17` (Temurin 17.0.20.1, same compiler checks available). The existing SDK `/workspace/.toolchain/android-sdk` has API 35, Build Tools 35.0.0, NDK 27.0.12077973 with `source.properties`, and CMake 3.31.6. No toolchain or Android component was installed or downloaded, and no cache/data was deleted.
- Gradle 8.10.2 documents `org.gradle.java.installations.paths` as its custom toolchain-location property ([toolchain documentation](https://docs.gradle.org/8.10.2/userguide/toolchains.html#sec:custom_loc)). It was supplied as a command-line project property to expose JDK 17 without changing versioned files. Gradle's `--info` output confirms it probed `/workspace/.toolchain/jdk-17/bin/java`.
- Exact command, once:

  ```text
  JAVA_HOME=/workspace/.toolchain/jdk-21-temurin PATH=/workspace/.toolchain/jdk-21-temurin/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --console=plain --info --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17 :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
  ```

- Start/end: `2026-10-13T01:40:59Z`–`2026-10-13T01:46:04Z`; duration **305 s**; exit code **1**. Gradle started and executed 40 actionable tasks. It failed at `:app:kspDebugKotlin` with: `Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' (17) and 'kspDebugKotlin' (21).` This is a build/JVM-target incompatibility under the requested JDK 21 Gradle runtime, not a UX-02e assertion failure. The contract does not authorize changing unrelated app build configuration, so no code was changed and no second invocation was made.
- The repository `README.md` documents JDK 17 to run Gradle and JDK 21 for app JVM test workers. This earlier attempt selected JDK 21 as the Gradle runtime, contrary to the repository's documented setup. Its mismatch is a launcher-configuration issue, not evidence of a UX-02e assertion failure; the corrected JDK 17 attempt is recorded above.
- Gradle emitted retries reporting `Network is unreachable` for `dl.google.com` and Maven Central while resolving/configuring. It nevertheless proceeded through project configuration and the 40-task summary; the recorded terminal failure is the JVM-target mismatch. This evidence does not establish network as the terminal cause.
- Requested app/LiteRT JVM tests, Compose UI tests, `lintDebug`, and `compileDebugAndroidTestKotlin` did not complete. JUnit result inventory in the clean worktree: **0 files**; test counts are **NOT_RUN**, not PASS. Android resource/intermediate XML is not counted as test XML.
- Full Gradle log: [`jdk21-runtime-jdk17-toolchain-gate-20261013.log.gz`](host/jdk21-runtime-jdk17-toolchain-gate-20261013.log.gz), raw SHA-256 `b5694b57610c2e17e7b1953a7053a659bd7d4683a7699edfdc7861665d36ee1d`, gzip SHA-256 `114adf0edb22639d03643919b3ad7976f63c51bf4d135300ef925a538dda9662`. Exact command, preflight, runtime environment, task summary, XML inventory, and exit/timing metadata are in the adjacent `jdk21-runtime-jdk17-toolchain-*-20261013.*` files; `SHA256SUMS` records their hashes.
- No emulator, physical device, APK, installation, or artifact workflow was used. The failure does not test or disprove link behavior; UX-02e remains unaccepted pending a successful host gate and independent audit.

## Previous HOST attempt — explicit JDK 21 without JDK 17 discovery path (2026-10-12)

- Exact source code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, built from a clean detached worktree. The worktree had no ignored/untracked files before the invocation and no `.gradle/`, `app/build/`, `llama-runtime/build/`, `.cxx/`, or `local.properties`.
- Read-only preflight passed for `/workspace/.toolchain/jdk-21-temurin`: `java` and `javac` are executable, both report 21.0.12.1, `jdk.compiler` is present, and `ToolProvider.getSystemJavaCompiler()` is non-null. The existing SDK `/workspace/.toolchain/android-sdk` has API 35/Build Tools 35.0.0 and NDK `27.0.12077973` with `source.properties`. No installation, download, cleanup, or version change occurred.
- Exact command:

  ```text
  JAVA_HOME=/workspace/.toolchain/jdk-21-temurin PATH=/workspace/.toolchain/jdk-21-temurin/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --rerun-tasks :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
  ```

- Start/end: `2026-10-12T13:33:30Z`–`2026-10-12T13:34:01Z` (31 s); exit code **1**. Gradle 8.10.2 failed while calculating dependencies for `:litert-compat:bundleLibCompileToJarDebug`. It requires `languageVersion=17` for `:litert-compat:compileDebugJavaWithJavac`; the selected JDK 21 is complete, but Gradle reported no locally installed Java 17 toolchain matching this invocation, and toolchain download repositories are not configured. **Zero tasks started.** This is a Java toolchain/environment blocker; no product test failed. No second invocation is authorized or was performed.
- JUnit XML: **0 files** from this run. The clean detached checkout had no previous result XML; existing ignored XML in the main checkout is not included or counted. App JVM, LiteRT compatibility JVM, Compose, lint and AndroidTest Kotlin compilation are all **NOT_RUN** by Gradle.
- Complete Gradle output (111,204 raw bytes, SHA-256 `b1d0eda3ce23eb027c0be53c2a2115b920e5d8141fb121bf01edf5c0934a630f`; gzip SHA-256 `7caf158c0292a933e646a20f59a80b4f6449d41f879c830031a8de81ee38c93f`): [`host/jdk21-gate-attempt-20261012.log.gz`](host/jdk21-gate-attempt-20261012.log.gz). Preflight, command/timing and XML inventory are in the adjacent `jdk21-*.txt`/`.json` files.

This result was the latest attempt at that time and superseded the earlier system-JRE invocation; it is now historical. It failed before tasks because no JDK 17 installation path was exposed to Gradle. The subsequent 2026-10-13 JDK 21 invocation explicitly exposed JDK 17 but selected the wrong Gradle runtime per README; the corrected JDK 17 runtime attempt is now the latest record above.

The feature branch was at local HEAD `24439fe1ef0f0fce9103c8beb4797ff4e9daddc8` when this attempt began. A post-run fetch found remote-only commits `60511db` and `69f1dd0`; the inspected diff contained only `docs/development/EXECUTION_STATE.json` and `docs/development/CODEX_TO_WORK_HANDOFF.md`, so the branch was fast-forwarded before this evidence update. `main` and peeled `v0.4.2` still resolve to `de615b772cf37a99e7e08a647c59607b271de606`.

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

### Earlier recovery attempt with system JRE (historical; superseded by the explicit-JDK attempt)

The recovery check found `/workspace/.toolchain/android-sdk` already installed with API 35, Build Tools 35.0.0, CMake 3.31.6, and the exact NDK `27.0.12077973`. Its `source.properties` reports revision 27.0.12077973, and `ndk-build`, `clang`, and `clang++` were present. No SDK/NDK installation, copy, cleanup, or version change was made. The existing Android emulator and QA workflows were inspected; neither was dispatched or triggered by this documentation-only recovery.

This earlier gate was run against code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`; the checked-out HEAD was `720099451426e4ecc98d338f74ed52c33f31be24`, whose product/build source is identical to the code commit (docs-only commits followed it). It is retained as historical evidence and is not the latest recovery result.

Exact command:

```text
ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

It exited **1** during task-graph dependency calculation, before any requested task started. Gradle selected `/usr/lib/jvm/java-21-openjdk-amd64`, an OpenJDK runtime without `JAVA_COMPILER`; `javac` was absent from PATH. At the time of this earlier attempt no invocation with a different Java selector had yet occurred; the later and final explicit-JDK attempt is recorded above.

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

- **HOST:** the full requested Gradle gate remains **INCOMPLETE** (prior wrapper exit 130; later requested tasks were not reached). The bounded diagnostics PASS: `ChatContentUiTest` 15/15 and `AuditHarness.writeSelectorReproductions` 1/1, both with zero failures/errors/skips. The previous stall did not reproduce and its cause remains undetermined. These two filtered executions do not complete/replace the full gate; LiteRT compatibility tests, lint and AndroidTest Kotlin compilation remain NOT_RUN in this contract. UX-02e remains unaccepted and is now classified `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`; the full HOST gate did not complete.
- **EMULATOR:** NOT_RUN for UX-02e. The prior global result remains FAIL: 16 cases, 9 PASS, the historical authentic Room v1 fixture FAIL, six real-model-dependent SKIPPED. This contract did not rerun it.
- **PHYSICAL_DEVICE:** NOT_RUN.
- **APK:** NOT_GENERATED.
- No TalkBack session or device visual validation was performed. The Compose annotation carries the visible label and URL destination for the platform accessibility link span; screen-reader behavior remains to be verified on device.
- `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.
- The original 97-task plan is not tracked in this repository and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02f and UX-03 remain pending; functional backlog remains paused.

## Evidence

`SHA256SUMS` covers this report, JSON summary, prior attempts, and the diagnostic bundle. The diagnostic bundle includes both original focused Gradle logs, observer/event files, worker runtime records, JUnit XML, exact commands, the temporary listener script, and prior-run inspection. The earlier full gate emitted no JUnit XML; the two focused runs each emitted and preserved their own JUnit XML. Existing XML from outside the exact-source worktree is not counted.
