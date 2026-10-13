# Full HOST gate diagnostics — 2026-10-13

The only full gate invocation for this contract used source `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. The TestListener recorded 36 `TEST_START` and 35 `TEST_END` events; every emitted `TEST_END` had `SUCCESS`, but no JUnit XML was finalized and this is not a suite PASS count. The single active event without an end was `com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit`, started at `2026-10-13T12:30:28.579499978Z`. It remained without a new test event for 420 seconds.

At the 7-minute snapshot the supervisor's process-group filter included the wrapper but missed the Gradle daemon/test worker, which had a separate process group. A direct, scoped process capture at `12:38:22Z` observed wrapper PID 133734 (Temurin 17.0.20.1), Gradle daemon PID 133767 (Temurin 17.0.20.1), and test worker PID 133962 (Temurin 21.0.12.1). `jcmd 133962 Thread.print -l` was attempted with a 20-second limit and returned 124 with zero output bytes. A follow-up `SIGQUIT` command returned 0, but no new bytes appeared in the Gradle log, so no thread dump was recovered. A subsequent `/proc` query found worker PID 133962 absent; the timing is documented, but no causal relationship to the diagnostic signal or the test is inferred.

The supplemental descendant observer also failed before capture because its broad `ps -eo` call exceeded its 8-second timeout. The supervisor's second snapshot at `12:38:47Z` found no new JUnit event; it had the same process-group limitation. The process monitor then sent SIGINT and SIGTERM to the wrapper's group; neither ended the invocation during the recorded waits, so SIGKILL ended the wrapper at `12:39:14Z` (controller return `-9`). The Gradle daemon was subsequently observed as a defunct process; wrapper and test worker PIDs were absent.

No `BUILD SUCCESSFUL` or `BUILD FAILED` marker was emitted. No new JUnit XML was produced; the pre-run `AuditHarness.xml` remained byte-identical and is excluded from this attempt. `:app:testDebugUnitTest` is incomplete; LiteRT tests, lint and AndroidTest Kotlin compilation were not reached. No APK was generated.

**Conclusion:** this run identifies the active test method, but it does not establish why it stopped producing test events or why the worker later disappeared. There is no assertion failure or causal stack. Classify the gate as `INCOMPLETE_STALLED_TEST_NO_RESULT_CAUSE_NOT_DETERMINED`; do not label it a product failure.

## Checksums and exact command evidence

- Command file SHA-256: `2d57d65ad9265b710e914ffc824c033d5950502e1e4ffef3f6d572effcd6d786`.
- Preflight SHA-256: `d5a785d3b58f77f1a65beff1365441f898d808b20d831d3bb047c2619e9b9a01`.
- Listener script SHA-256: `3d1db8e4b78ee6f9050b7eafb7117554761927b29f6f578535ffddce9b56630e`.
- Test events SHA-256: `df59f026784b031fbbf6b20132827960e5b358862298c76226922ce36f8d5cf8`.
- Complete raw Gradle log: 544,495 bytes, SHA-256 `0f07cb7474d54def26295608ce1749bfd14cf4a0375c36689552db6cdd1090b4`; lossless gzip SHA-256 `cbd26c5f8f566359d4478c05c04b47ab4e12788ecc0b7bda5ac87569479640af`. Decompression reproduced the raw SHA exactly.
- Exact command and environment assignments are in [`command.txt`](command.txt); listener and supervisor sources are preserved next to the output.

- Supervisor log: raw SHA-256 `c5f4c6d16500934503c3c950b7d986e182f428aef19155a764aa914866b03278`, lossless gzip SHA-256 `d93c1dc6eab763f074b6f6d6adc56ba6b5bbfcfd244c946a90544f8e189393ce`.

- Preflight review text was whitespace-normalized only for `git diff --check`; the original bytes are retained losslessly as `preflight.txt.gz`. The raw and normalized hashes are in `preflight-hashes.txt`.
