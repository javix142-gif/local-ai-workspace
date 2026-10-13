# Invocation preparation note

The first command-line check placed `--tests` before the task selector. The Gradle wrapper printed command-line usage and exited 1 after 2.130 seconds. The captured result shows zero Gradle task output, no worker, no JUnit listener events, no XML, and no dependency resolution. It was not a test execution.

The corrected `command.txt` places the filter after `:app:testDebugUnitTest`. Exactly one actual offline target test invocation then ran; see `run-result.json`, `test-events.tsv`, the JUnit XML, and `RESULTS.md`. The original CLI output was retained unchanged in `cli-syntax-check.log`; only its result classification was updated to identify it as a syntax check.
