# UX-02c — structured evidence references in chat

**Status:** HOST PASS for the UX-02c presentation scope; independent audit pending. No emulator, QA workflow, APK, or Motorola run was requested or performed.

## Scope and source flow

- Branch: `feat/0.5.0-skills-agents`.
- Base: `eb3a6aeee0966cdfe01fb2698a6601ca7af9cdc8`.
- Code commit tested: `0846419ea64259244a9b1c7bce9b40711cc29217`.
- The presentation starts from `CitationEvidenceEntity` already attached to the assistant message. `ChatViewModel` validates an evidence ID against the current turn and persists the selected record's existing excerpt, source label, and page metadata. `MainActivity` still selects the detail record by the same `evidenceId` and passes that same ID through the existing `onCitation(String)` callback.
- No source discovery, free-text citation parsing, retrieval changes, or evidence creation was added. No data/model, Room, persistence, navigation, inference, permission, Agent/Tool, or dependency change was made.

## Presentation changes

- Replaced the horizontally scrolling ID-only chips with vertically stacked, full-width evidence reference cards. Each card shows the existing ID, source label when non-empty, and `pageStart` when present; long labels wrap naturally.
- Each reference exposes button semantics, an accessible description containing the existing ID/source/page values, and a minimum 48dp touch target. The evidence-reference group and detail section labels expose heading semantics.
- The detail dialog keeps the same stored `sourceLabel`, `pageStart` (or the pre-existing “Local source” fallback when absent), and complete excerpt. The excerpt area scrolls vertically within a maximum height; neither the source nor excerpt is shortened with ellipsis.
- The dialog states that the display is an excerpt, not a full-document review, and preserves the notice that metadata comes from the local index rather than model-generated citation text.

## HOST checks

Toolchain: Linux x86_64, Gradle Wrapper 8.10.2, Temurin JDK 17.0.20.1 for Gradle, project Java 21 test toolchain, Android SDK/API 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, Ninja 1.12.1. Exact commands and environment are in [`host/commands.txt`](host/commands.txt) and [`host/environment.txt`](host/environment.txt).

| Check | Result | Evidence |
|---|---:|---|
| Focused `ChatContentUiTest` | 10/10 PASS, 0 failures/errors/skips; exit 0 | [`host/focused.log.gz`](host/focused.log.gz), [`host/focused-junit/`](host/focused-junit/) |
| `:app:testDebugUnitTest` | 746/746 PASS | [`host/full-junit.tar.gz`](host/full-junit.tar.gz), [`host/full-junit-summary.json`](host/full-junit-summary.json) |
| `:litert-compat:testDebugUnitTest` | 2/2 PASS | Same combined JUnit archive |
| Combined app + compatibility JVM tests | 748/748 PASS, 90 XML files | [`host/full-host.log.gz`](host/full-host.log.gz), [`host/full-junit.tar.gz`](host/full-junit.tar.gz) |
| `:app:lintDebug` | PASS, 0 errors, 82 warnings | [`host/lint-results-debug.txt`](host/lint-results-debug.txt) |
| `:app:compileDebugAndroidTestKotlin` | PASS, compile only; no instrumented tests run | [`host/full-host.log.gz`](host/full-host.log.gz) |
| `git diff --check` | PASS | [`host/exit-codes.txt`](host/exit-codes.txt) |

The focused Compose cases check that clicking the presented reference sends the exact stored ID to the existing callback and resolves the corresponding entity; source, page and excerpt remain exact; the reference is announced as a button and meets the 48dp target; and the local-index provenance and excerpt-only notices remain present. A compact `w320dp-h640dp` Robolectric configuration checks a long source label and long excerpt at font scales 1.0, 1.6 and 2.0, with the entire excerpt preserved in semantics and the details vertically scrollable. These are host Compose checks, not screenshots or physical visual validation.

`assembleDebug` was intentionally not run because this increment does not request an APK. AndroidTest Kotlin compilation caused the existing Gradle graph to compile native dependencies; it did not assemble an app/test APK or execute instrumentation.

The first Gradle invocation in this environment stopped during configuration because `ANDROID_HOME` was unset. Subsequent successful commands explicitly pointed to the installed Android SDK. An early test-fixture attempt also failed because its local selection variable did not trigger Compose recomposition; the test was corrected to use Compose state, and the focused and full gates above passed. Those setup/development attempts are not represented as product failures.

## Validation state kept separate

- **HOST:** PASS for UX-02c focused and proportional gates listed above.
- **EMULATOR:** No new run was started. The previously audited global run 38215175075 remains **FAIL**: 16 JUnit cases, 9 PASS, 1 FAIL for the unavailable authentic Room v1 schema, 6 model-dependent SKIPPED, 10 executed. The paired QA run 38215175184 failed closed and produced no APK artifact. The collector's post-task `applicationApkInstalled=false` and Gradle progress `22/16` discrepancy remain undiagnosed; no additional cause is inferred.
- **PHYSICAL_DEVICE:** `NOT_RUN`.
- UX-02b is recorded as independently accepted only for its existing Markdown renderer and host evidence. Links, free-text citations, and tables remain literal in that renderer. UX-02c presents only structured `CitationEvidenceEntity` records already supplied to the message.
- The original user-supplied 97-task plan is not tracked here and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02d is pending. Functional backlog and roadmap state were not changed.

The complete file checksums for this evidence folder are in [`SHA256SUMS`](SHA256SUMS).
