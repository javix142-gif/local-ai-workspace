# Focused Robolectric reproduction evidence

Source code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`

This directory records one bounded invocation of `ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit` from a clean detached worktree. The target did not start: there were no JUnit events, worker PID, or XML. Gradle logged socket errors stating `Network is unreachable` for `dl.google.com` and `repo.maven.apache.org`; after the no-event process snapshot, the invocation was stopped. This is not a test failure and does not explain the earlier Robolectric stall.

- `preflight.txt`, `source-diff-review.txt`: checkout, toolchain, target and baseline comparison.
- `command.txt`, `environment.json`, `run-start.json`, `run-result.json`, `run-analysis.json`: invocation and result metadata.
- `test-events.init.gradle`, `test-events.tsv`: temporary JUnit listener and its empty event file.
- `gradle-focused.log.gz`: lossless original Gradle output; the expanded raw-content SHA-256 is recorded in `run-analysis.json`.
- `quiet-120s-*-process-snapshot.json`, `quiet-120s-diagnostics.json`: no-progress process capture; no test worker existed.
- `termination-actions.json`, `post-stop-process-check.json`: scoped stop and process readback.
- `task-outcomes.txt`, `junit-xml-inventory.txt`, `post-run-worktree-status.txt`: task/XML/worktree summary.

The target test result is `NOT_RUN`; the earlier full-gate cause remains `NOT_DETERMINED`; `PRODUCT_FAILURE` is not established. No other tests/tasks, emulator, device or APK were run.
