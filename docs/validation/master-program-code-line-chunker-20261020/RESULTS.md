# Code line chunk budget and provenance follow-up

**Status:** focused HOST verification passed after a test-first reproduction. This is preparatory M02-08 evidence only; the original task is still `TODO`.

## Finding and correction

`CodeLineChunker` could exceed `maxCharacters` by one when a source began with an empty line and the next line exactly filled the limit. The empty row left the buffer empty, but the second source line still received a leading newline. The red JUnit output showed the extra newline in the emitted chunk.

The five-line production change omits that separator only when it would exceed the budget and moves `lineStart` to the actual line being emitted. A leading blank line remains preserved when it fits. Internal blank-line ranges and explicit oversized-line rejection are covered by the focused tests. Retrieval, ranking, project/source scope, persistence, Room, models, runtime and dependencies are unchanged.

## Source and results

- Test-only red commit: `6ce7788429a00260816d184eb39601905fad317c`.
- Production code and exact tested SHA: `3e3fb0dffcd6e4488e1f28522067bfad7dd2a270`.
- Code change: [CodeLineChunker.kt](../../../app/src/main/java/com/localai/workspace/documents/CodeLineChunker.kt).
- Regression tests: [CodeLineChunkerTest.kt](../../../app/src/test/java/com/localai/workspace/documents/CodeLineChunkerTest.kt).
- Test-first source diff and commit summary are in `source-diff.patch` and `source-commits.txt`.

| Run | JUnit | Gradle | Wall time |
|---|---:|---|---:|
| Red, test-only SHA `6ce7788` | 3 tests, 1 expected failure; 0 errors/skips | Exit 1, `BUILD FAILED in 2m 47s`, 63 tasks executed | 168 s |
| Green, code SHA `3e3fb0d` | 3/3 pass; 0 failures/errors/skips | Exit 0, `BUILD SUCCESSFUL in 44s`, 7 tasks executed / 56 up-to-date | 45 s |
| Existing `CodeSegments` wrapper, same code SHA | 3/3 pass; 0 failures/errors/skips | Exit 0, `BUILD SUCCESSFUL in 20s`, 1 task executed / 62 up-to-date | 20 s |

Each run used Gradle Wrapper 8.10.2, Temurin JDK 17 for Gradle, JDK 21 for the test worker, the existing Android SDK API 35 and NDK 27.0.12077973, and the pre-existing `/workspace/.gradle` cache in offline mode. Full commands, environment, exit codes, timestamps and JUnit XML are preserved under `attempts/`. Each complete Gradle log is stored losslessly as `gradle.log.gz`; gzip integrity and byte-for-byte equality with the retained original log were checked before publication. `junit-summary.json` records both the compressed-file SHA-256 and the SHA-256/byte count of the decompressed original log, and is parsed from the three JUnit XML files.

## Original plan and limits

The authentic plan remains unchanged: 97 unique task IDs, 120 dependency edges, and all source statuses `TODO`. M02-08 still depends on M02-05 (`TODO`); this focused correction does not complete M02-08 or satisfy its full path/symbol/indexing definition. See `plan-source-check.txt` and the M02 section excerpt in `roadmap-m02-08-excerpt.txt`.

Full app/LiteRT suites, lint, assembly, AndroidTest, EMULATOR, PHYSICAL_DEVICE and APK were **NOT_RUN** for this increment. No Gemma quality, speed, memory, energy or Motorola behavior is claimed. The ongoing emulator/Room and device/model limitations are unchanged.

Payload hashes are listed in `SHA256SUMS`; the manifest excludes itself. The evidence commit and publication readback are recorded in execution state and handoff after publication.
