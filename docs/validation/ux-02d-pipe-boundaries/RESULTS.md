# UX-02d follow-up — independent optional table edge pipes

**Status:** HOST checks passed for this parser correction; pending independent audit. No emulator, APK, or physical-device run.

## Scope and fix

Audit base: `ba5b1f084a3e4f29c9bb3c8c6f8b029a6d40e3d4` on `feat/0.5.0-skills-agents`.

The audit found that `BasicMarkdown.splitTableRow` removed the leading and trailing `|` only when both were present, although the parser comment and UX-02d report described outer pipes as optional. The [GFM tables extension](https://github.github.com/gfm/#tables-extension-) permits initial and final pipes to be used inconsistently. The focused red regression reproduced the mismatch before the production edit.

The parser now recognizes the initial and final outer pipe independently. It retains the existing fixed column count across header, separator, and body rows. Structurally incompatible candidates remain a literal paragraph in full; rows and cells are not discarded. Existing escaped-pipe, single-backtick span, fence, and malformed-table handling remains covered. The follow-up also guards a trailing escaped `\\|` from being consumed as an outer boundary pipe. It adds no table features beyond this boundary recognition.

Code/test commits, all on the existing feature branch:

- `af321e397106c49a3e316feabdc58999cdddb763` — added the mixed-boundary regression first.
- `0c3973e5c7c6d5da7d8bb689bff1dd39eeaf4604` — independent edge-pipe parsing fix.
- `26e0fd7f901953068d8836239dc0c845c2b7b3ea` — added backtick-span coverage.
- `f54c1430cab4ea59a302b58dd2aa27deea579c49` — final guard/test for escaped terminal pipes; exact code SHA used by the final checks.

Only `BasicMarkdown.kt` and `BasicMarkdownTest.kt` changed in these commits. No Compose renderer, workflow, dependency, storage, parser feature outside edge-pipe recognition, or application behavior was changed.

## Red reproduction

At test-only SHA `af321e397106c49a3e316feabdc58999cdddb763`, the focused test expected a table composed of lines with initial-only, final-only, both, and neither outer pipe. The baseline parser failed it: **1 executed, 1 failure**, Gradle exit 1. The failure is retained in [`red/`](red/): command/run record, raw Gradle log, and JUnit XML.

An earlier setup attempt at 09:36 UTC lacked `ANDROID_HOME` and stopped before meaningful test execution. It is retained under `setup-attempt/`, explicitly excluded from red/green test counts. The valid red run exported the recorded JDK and Android SDK paths.

## Final HOST verification

Final verification used code SHA `f54c1430cab4ea59a302b58dd2aa27deea579c49`, Linux x86_64, Temurin JDK `17.0.20.1+1`, Gradle Wrapper `8.10.2`, Android SDK API 35, Build Tools 34.0.0 and 35.0.0. Gradle ran offline with the environment's existing cache; this is not a clean-cache build.

Focused command (exit 0):

```text
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain :app:testDebugUnitTest --tests com.localai.workspace.ui.BasicMarkdownTest --tests com.localai.workspace.ui.ChatContentUiTest
```

Result: **27/27 PASS** across two XML files (15 `BasicMarkdownTest`, 12 `ChatContentUiTest`), zero failures/errors/skips. It includes all four combinations of boundary pipes, escaped trailing pipe, inline code spans, escaped interior pipes, fences, and malformed literal preservation. Exact timestamps and environment are in [`final/focused-run.txt`](final/focused-run.txt) and [`final/environment.txt`](final/environment.txt); raw log and XML are in `final/`.

Proportional HOST gate (exit 0):

```text
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
```

The final gate ran `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`; the Kotlin AndroidTest compile task was executed, not merely reported up-to-date. The compat JVM task was UP-TO-DATE in this combined invocation, so it was separately rerun on the same SHA:

```text
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --rerun-tasks --console=plain :litert-compat:testDebugUnitTest
```

That rerun exited 0 and passed **2/2**. The JUnit archive from the final combined gate contains 90 XML files: app **754/754** and compat **2/2**, total **756/756**, with zero failures/errors/skips. Gradle reported 137 actionable tasks, 14 executed and 123 up-to-date. `lintDebug` reported **0 errors and 82 warnings**. Logs, commands, XML, lint report and environment are retained in [`final/`](final/); SHA-256 values are listed in `SHA256SUMS`.

The first full gate run at `26e0fd7f901953068d8836239dc0c845c2b7b3ea` is retained under `intermediate-26e/` as historical intermediate evidence, not as the final result. The final run and JUnit archive are tied to `f54c143`.

`git diff --cached --check -- . ':!**/*.log'` exited 0. The unrestricted staged `git diff --cached --check` reported only the standard trailing space in the first “Daemon will be stopped at the end of the build ” line of eight preserved raw Gradle/setup logs; these original logs were not normalized or edited.

## Validation boundaries

- **HOST:** PASS for the focused parser/UI tests and proportional host gate above.
- **EMULATOR:** NOT_RUN for this correction. The previously known global run remains a separate failure: historical Room v1 fixture unavailable, with six real-model-dependent cases skipped; this follow-up did not rerun it.
- **PHYSICAL_DEVICE:** NOT_RUN.
- No APK was generated. No comparison with the Motorola or other device is claimed.
- The original 97-task plan was not present in the tracked repository and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-03 and other functional work remain pending/paused.

## Evidence files

- [`red/`](red/) — expected-failing baseline test, XML, command and log.
- [`final/`](final/) — final code-SHA environment, command records, raw Gradle logs, focused XML, full JUnit archive, and lint report.
- [`intermediate-26e/`](intermediate-26e/) — superseded intermediate attempt, kept for history.
- [`setup-attempt/`](setup-attempt/) — SDK environment setup failure before tests; not counted.
- [`SHA256SUMS`](SHA256SUMS) — checksums for all evidence files and this report/JSON, excluding the manifest itself.
