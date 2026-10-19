# Batched semantic memory-vector lookup — focused HOST evidence

**Code SHA:** `c0545e8c40a7f8a394314e30d583260197eba1df`
**Branch:** `codex/aws-master-resume-20261019`
**Evidence scope:** one test-first regression and one exact-SHA focused HOST run.

## Finding and change

`MemoryManager.select()` previously fetched a stored semantic vector with one DAO call for each candidate memory. The red test with 25 eligible memories observed 25 `SELECT` statements against `context_vectors`. The implementation now loads vectors in bounded batches of 400 IDs per `spaceKey`; this yields one vector query for 25 candidates and two for 401. Vectors whose content hash no longer matches the memory are treated as missing and remain subject to the existing maximum of eight inline reindex attempts. Candidate ordering, relevance scoring, result limit, scopes and query embedding policy are preserved. No model/runtime, schema migration, dependency, retrieval scope or persistent data format changed.

The query-count test is an operation-count result; it establishes fewer Room roundtrips for this code path. It does not establish a phone latency, TTFT, throughput, memory, energy or answer-quality improvement.

## Test-first evidence

The red reproduction is in `red/`. At production baseline `2d1deafe491638543d36d6351e98307ef7bdd423`, while the new regression was still uncommitted, it failed on the measured assertion: expected one vector SELECT, observed 25. The red run's exact source tree hash was not captured, so it is attributed to the baseline production commit plus the uncommitted test, not to a synthetic commit SHA.

An earlier initial test attempt used an incorrect fixture assertion (`MemoryManager.select()` intentionally returns at most six results). It is not counted as a product red. Its raw working-tree log is not part of this evidence bundle; the accepted red evidence is `red/gradle.log.gz`.

## Exact-SHA focused HOST run

Command, environment and raw output are preserved in `exact/`. Gradle 8.10.2, JDK 17 runtime, JDK 21 test worker, SDK API 35 and NDK 27.0.12077973 were already installed; `/workspace/.gradle` was reused and the invocation was offline.

- Started: `2026-10-19T23:06:21Z`
- Finished: `2026-10-19T23:07:05Z`
- Gradle duration: 43 seconds; exit code 0
- `MemoryRelevanceTest`: 19/19 passed
- `MemoryBundleImportTest`: 6/6 passed
- Total: **25/25 passed**, 0 failures, 0 errors, 0 skipped
- Gradle tasks: 63 actionable; 3 executed and 60 up-to-date

The test counts come from the two preserved JUnit XML files; `junit-summary.json` records the parse result.

## Limits and original-task status

This is a focused HOST test only. The full app/LiteRT suite, lint, assembly, AndroidTest, emulator, Motorola and APK were not run for this increment. The original source plan remains byte-identical: 97 unique task IDs, 120 dependency edges, all 97 statuses `TODO`. M00-06's full import-to-model timeline and device metrics remain incomplete; this optimization does not complete an original task. `PHYSICAL_DEVICE=NOT_RUN`, and EMULATOR is `NOT_RUN` for this increment. It does not resolve the separate historical Room v1 failure or six model-dependent skips.

The Gradle logs are losslessly gzip-compressed to keep captured trailing whitespace intact while allowing diff --check; their decompressed bytes match the original captured logs.
