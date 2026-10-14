# Chat and Workspace visual refinement — personal QA

Status: `PERSONAL_QA_DELIVERED_HOST_PARTIAL_NOT_GLOBAL_PASS`.

Source: `6bf0cb651905c3d5fb616081c152f8ac9aec2d3d`; base:
`55471af8202a888778f2537f79b43b53be4ace17`. Folder date is the task-start
date; original UTC timestamps in the command/result files are authoritative.

## Changed presentation

- All 15 Material3 typography roles explicitly use SansSerif, with lighter
  headings. System font scaling and established type sizes remain supported.
- Nested Scaffold padding is consumed once. Conversation title and wrapping
  agent/model metadata size to their content; no fixed maximum header height.
- Composer is a compact rounded text surface with inline Options and 48dp
  attach/send/stop targets. Existing callbacks and Tools/Agent meanings remain.
- Assistant messages and the real Workspace screen have quieter surfaces,
  smaller headings and compact creation actions/cards.

No changes to workflow, dependencies, data, navigation destinations, Room,
inference, permissions, retrieval, routing or the original functional plan.
The five new user-reference screenshots were not available in this executor;
the supplied observations were used, without claiming direct visual comparison.

## Executed checks

Runtime: Gradle 8.10.2 on the existing JDK17 installation; test workers JDK21.
SDK, NDK 27.0.12077973 and CMake 3.31.6 were already provisioned. Commands,
paths, source SHAs, timestamps, duration and exit codes are preserved in the
JSON command/result files and original logs; no toolchain installation or
cache cleaning was used. Builds used existing offline dependencies and
incremental/cache outputs, not a clean dependency-home reproducibility test.

| Check | Source / result |
|---|---|
| Final Debug ARM64 build, unit-test Kotlin compile, lint, AndroidTest Kotlin compile | `6bf0cb6`, exit 0, 141.961s; 145 tasks: 5 executed, 2 from cache, 138 up-to-date. AndroidTest compilation was up-to-date, not instrumented execution. |
| Last Native-graphics focused run | `89ef8fe`, 11 cases: 9 PASS, 2 FAIL, no errors/skips, 191.235s. Final production and ordinary test source trees are identical to this SHA: [source equivalence](source-equivalence.txt). |
| Failed Native cases | Both `WorkspaceRefinementUiTest` project-dialog interactions: `AppNotIdleException`. Assertions are retained. Cause `NOT_DETERMINED`; no product-failure attribution claimed. |
| Root-inset hypothesis probe | One case still failed after temporarily removing only root inset consumption; patch/source metadata preserved and production restored. |
| One-off Legacy diagnostic | Two failures: idle exception and subsequent JVM heap OOM in Robolectric. Diagnostic source archived, original Native regressions preserved; not an acceptance result. |
| Lint | 0 errors, 41 warnings, 1 informational issue. |
| Full JVM app/compat suites | `NOT_RUN`. |
| Emulator / Motorola | `NOT_RUN`: no local emulator/system image/KVM; no instrumented workflow dispatched. |

Earlier failures and their original logs/XML remain under `first-attempt`,
`first-focal`, `second-focal`, `third-focal`, `root-inset-probe` and
`legacy-dialog-probe`. First compile found callback compatibility; first
Native run found 40dp controls; second found a test-clock Stop-state issue.
Those concrete issues were corrected without removing assertions. The two
project-dialog idle failures remain unresolved and visible.

[JUnit counts](junit-summary.json), [final build](build-result.json),
[last Native XML](third-focal/junit/TEST-com.localai.workspace.ui.ChatRefinementUiTest.xml),
[failed dialog XML](third-focal/junit/TEST-com.localai.workspace.ui.WorkspaceRefinementUiTest.xml),
[lint XML](lint-results-debug.xml).

## Visual evidence

All six Native-host PNGs were opened and inspected: four chat component
fixtures at 360/393/411dp and scales 1.0/1.3/1.6/2.0, plus actual empty
Workspace light/dark captures. The chat fixtures contain synthetic text and
status and a simulated 120dp bottom inset; this is not a real keyboard or
phone test. The host window is 411dp; narrower fixtures retain unused side
space in the uncropped image. Workspace images precede the failed dialog
interaction and do not prove that interaction.

- [Chat light 360dp/1.0](captures/chat-360dp-scale-1.0-light.png)
- [Chat dark 393dp/1.3](captures/chat-393dp-scale-1.3-dark.png)
- [Chat dark 411dp/1.6](captures/chat-411dp-scale-1.6-dark.png)
- [Chat dark 360dp/2.0](captures/chat-360dp-scale-2.0-dark.png)
- [Workspace light](captures/workspace-light-360dp.png)
- [Workspace dark](captures/workspace-dark-411dp.png)

## Standalone APK in Drive

`PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`; source `6bf0cb6`.

- File: `local-ai-workspace-0.5.0-chat-home-6bf0cb6-arm64.apk`
- Package: `com.localai.workspace.debug`, `0.5.0-debug` / versionCode 27.
- ABI: only `arm64-v8a`; no model weights included.
- Bytes: **65,570,665**.
- APK SHA-256: `de4b77692993ebceff09695a530e6c29dab3f72988bac96d14431e90f219c827`.
- Debug certificate SHA-256: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`.
- Signature v2/v3, ZIP CRC and zipalign 4/16KiB: PASS. Delivery packaging
  compresses DEX only; all 284 uncompressed payloads match the original build.
- Same retained local signer/package as UX01/UX02a and the previous personal
  update. Equal versionCode replacement needs no increment; actual phone
  installation and the phone's installed signer remain unverified.

[Download the APK](https://drive.google.com/file/d/1pFtgGGF_jNNGkRyeAM9z-ZClmvonHovq/view?usp=drivesdk).
Drive metadata readback confirms ID, MIME, folder and bytes; the Drive payload
was not independently downloaded and rehashed. APK/key/weights are not in Git.
[Packaging checks](apk-result.json), [signature/alignment log](apk-validation.log),
[Drive readback](drive-readback.json). `SHA256SUMS` covers the actual evidence
files in this directory, including failed logs and screenshots.

Source/tests/authored documentation `diff --check` passed with original `*.log`
payloads excluded. The unfiltered check reports whitespace in raw Gradle/JVM
output; those original bytes are intentionally preserved. See [check](diff-check.json).

## Remaining checks / pause

Await independent audit and user installation feedback. Check the actual phone
header/keyboard and Start project dialog; do not uninstall or clear user data.
No next implementation started. Historical Room-v1 failure and six real-model
skips remain separate; UX02e remains not accepted. M01-06 remains device-pending;
the 97-task source/progress is unchanged. No global HOST, emulator or Motorola
PASS is claimed.
