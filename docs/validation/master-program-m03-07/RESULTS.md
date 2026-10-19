# M03-07 recovery checkpoint — preview comparison and cancel safety

Date: 2026-10-19 UTC. This is host-only preparatory evidence on the new independent recovery branch; it is not a formal M03-07 completion.

## Recovery and source identity

The previous workspace was not available. Searches of the available workspace/mount/temp locations found no recoverable checkout or patch for local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd`; GitHub lookup for that object returned HTTP 422 (“No commit found”). No lost source was reconstructed or described as recovered. The exact surviving feature head `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc` was cloned. A persistent remote ref `recovery/backup-b8e75b4` was created and read back at that exact SHA. Implementation is isolated on `codex/master-program-20261019`, descending from that backup. The feature branch, `main`, and `v0.4.2` were not modified.

Product code in the surviving branch already presented a saved revision and a proposed preview, exposed export for the saved revision, and kept the proposal separate from persistence. This increment adds a focused Compose regression test only. It selects a synthetic private CODE draft, edits its structured spec, previews the proposal, verifies the saved “before” body and proposed “after” body are both visible, checks that export remains attached to saved v1, and verifies that closing does not create a revision or change the original bytes/SHA-256. No production file changed. The test is not a real external SAF export test; that behavior has separate existing coverage and was not rerun here.

## Host attempts

Attempts 01–05 used Gradle Wrapper 8.10.2, Temurin JDK 17 as Gradle runtime, Temurin JDK 21 available for worker toolchain discovery, Android SDK API 35, NDK 27.0.12077973 and the existing workspace Gradle user home. Exact commands, environments, raw logs, task summaries, XML and pre-run XML inventories are retained under `recovery-attempt-01` through `recovery-attempt-05`.

| Attempt | Tested source | Result | Interpretation |
|---|---|---|---|
| 03 | `4d7014abe903f4a80e090777c9cfc3b7e78c6119` | Exit 1; 5m38s; test task not reached; no XML | The new test did not compile because its Compose `onAllNodesWithText` import was missing. |
| 04 | `8feb93f3d5ad62c792bdb35b0124f72f2b5bd467` | Exit 1; 1m52s; 1 test failed | The test tried to scroll to the AlertDialog “Close” action, which is outside the scrollable dialog body. This was a test interaction error, not a product failure. Its original XML was inspected but not copied before Gradle replaced it on the next invocation; this evidence gap is stated explicitly. |
| 05 | `0412f605ed090c2c5ced147d297d9a9fde0781db` | **PASS**, exit 0; 41s; 1/1 | XML reports 1 test, 0 failures, 0 errors, 0 skipped; testcase duration 8.618s. Gradle reported 63 actionable tasks (3 executed, 60 up-to-date) and `BUILD SUCCESSFUL`. |
| 06 | `ac783b8cd65a0e8b7474c18333389ef8a36e2454` | **PASS**, exit 0; 223s wall; 1/1 | Revalidated after the later XLSX `ArtifactStore` changes. Fresh XML: 1 test, 0 failures, 0 errors, 0 skipped; testcase duration 15.011s. Gradle reported 63 tasks executed and `BUILD SUCCESSFUL` in 3m41s. |

Attempt 05's command/log/XML remain preserved as above. Attempt 06 ran the same focused class with `--rerun-tasks` against exact source commit `ac783b8cd65a0e8b7474c18333389ef8a36e2454`; its command, environment, raw log, exit marker, timestamps, task outcomes, and new XML are under `recovery-attempt-06/`. The JDK 17 runtime and JDK 21 test-launcher configuration are recorded; the worker process was not separately sampled. All 63 actionable tasks executed. This current-source rerun confirms the prior UI test still checks the proposed “after” versus saved “before,” saved-v1 export association, and unchanged saved revision/bytes/hash after closing. It is one focused HOST test, not a full gate.

Recovery boundary: the exact local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` is absent from the local object database and GitHub rejected direct fetch (`not our ref`). No patch or second checkout was found. The M03-07 UI/test work and its earlier evidence were already present in the reachable implementation branch at `4d7014a`–`0412f60`; this follow-up did not reconstruct the missing commit. The pre-existing remote backup ref `recovery/backup-b8e75b4` still points to the exact feature baseline `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`, and `codex/master-program-20261019` is its descendant.

## Task status and limits

Canonical source entry `M03-07` remains `TODO`: `requires_device=true` and dependency `M03-01` remains `TODO`. This run verifies one host UI path, not the complete cross-format preview/diff/SAF workflow or on-device usability. The existing UI provides a before/after preview; this test does not claim a line-by-line unified diff. `M03-01` depends on `M02-05`; no dependency status was changed. The original catalog remains 97 unique IDs and 120 dependency edges, all source statuses `TODO`; its source files were not edited (backlog SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`; roadmap SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`).

Not run in this checkpoint: full app JVM suite, LiteRT compatibility suite, lint, full builds, instrumented/emulator tests, Motorola/device tests, APK creation/delivery, model benchmarks or performance measurements. Physical-device validation remains pending; no model or private user document was used. The fixture contains only synthetic app-private data.

The original Gradle logs are retained losslessly as `.log.gz`; `log-payload-hashes.json` records the original and compressed byte counts and SHA-256. `SHA256SUMS` hashes the stored evidence payload; report files are excluded to avoid a self-referential digest.


Payload manifest: **47** stored evidence entries, SHA-256 `24601978d7970018d42529e66ce9d85453d517ba3396b3d29f6bee6e30dcc0e5`. It covers command/environment records, logs, XML, inventories, prior remote readbacks, and recovery attempt 06; it excludes this report, its JSON companion and the root manifest itself. The attempt-06 child manifest contains 12 entries and has SHA-256 `57ec696f78ff698fba3be0239a6508d9ce9cd118c4ee3148dd77135d070c740e`.
