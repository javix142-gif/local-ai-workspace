# Benchmark cohort metrics presentation — focused HOST result

- **Code under test:** `f21939773735ff17f338d571cadc793484f0bd01`
- **Code commit:** `f21939773735ff17f338d571cadc793484f0bd01` (`feat(performance): expose benchmark preparation and memory cohorts`)
- **Branch:** `codex/master-program-recovery-20261019`
- **Result:** focused HOST PASS, `BenchmarkSummaryTest` 5/5; this is not a full host gate, device benchmark, or formal source-task completion.

## Change

The existing exact-cohort summary now exposes p50/p95 distributions for model load, model preparation, prefill throughput, observed app PSS, observed worker PSS, and available RAM after the run. It reuses metrics already captured in benchmark records and the existing same-device/model/task/config/backend cohort grouping. Failed and quality-rejected runs remain excluded from speed summaries; missing measurements remain explicitly unavailable. The Performance screen renders those rows and states that PSS is the maximum of before/after endpoint samples, not generation peak. The percentile policy and small-sample caveat remain visible.

No inference engine, benchmark execution, metric collection, database, model, or dependency behavior changed. Test data is synthetic and validates aggregation/formatting only; it is not a measurement of Gemma or the Motorola.

## Command and environment

```text
JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk GRADLE_USER_HOME=/workspace/.gradle ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --rerun-tasks -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests com.localai.workspace.performance.BenchmarkSummaryTest
```

Gradle 8.10.2 ran on Temurin JDK 17.0.20.1; the Gradle test worker used Temurin JDK 21.0.12.1. Android SDK API 35 and NDK 27.0.12077973 were already present; the NDK was not a tested feature. Gradle used the existing `/workspace/.gradle` cache in offline mode. The invocation forced task execution with `--rerun-tasks`.

- Started: `2026-10-19T19:01:44Z`
- Finished: `2026-10-19T19:04:44Z`
- Gradle-reported duration: `2m59s`
- Gradle exit: `0`; `63 actionable tasks: 63 executed`
- Fresh JUnit XML: 5 tests, 0 failures, 0 errors, 0 skipped; XML test time `0.091s`.

The exact command, environment, timings, exit code, task outcomes, full Gradle log and copied JUnit XML are in this directory. `SHA256SUMS` covers the evidence files and this report.

## Scope limits and original-task status

No full app JVM suite, LiteRT compatibility suite, lint, assemble, AndroidTest compilation, emulator, physical device, APK, or real model benchmark ran for this increment. The focused test validates cohort statistics and display strings only.

The authentic source catalog remains unchanged at 97 unique IDs / 120 dependency edges with all source statuses TODO. M00-06 remains TODO because its end-to-end import/index/retrieval/context/gate/TTFT/decode timeline is incomplete. M01-01 remains TODO, requires a device, and depends on M00-10; no Moto-specific benchmark protocol/result is claimed. EMULATOR and PHYSICAL_DEVICE are NOT_RUN for this increment.
