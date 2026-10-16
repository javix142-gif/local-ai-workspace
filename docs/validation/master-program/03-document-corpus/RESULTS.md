# M02-01 preparatory synthetic document corpus

**Source task status:** `TODO` (dependency-blocked; no backlog state changed). **Preparatory DoD evidence:** focused HOST parser coverage passed on code commit `481e36633ae84224b63e225d84010ccfa16f8392`. This is not a formal M02-01 closure: original M02-01 depends on M00-10, which remains `TODO` and requires device validation.

## What was added and exercised

The existing synthetic, versioned corpus now includes an explicit XLSX range expectation for `Hoja1!A1:A3`, preserving the three cell identities, values `1/5/4`, formula `$A$2-$A$1`, and the fact that cached formula output is not recalculated. The quoted semicolon CSV fixture has SHA-256 metadata and an exact coordinate expectation (`CSV!A2`). The corpus manifest was regenerated and all 140 listed payload files were verified. The immutable source backlog and roadmap were not changed.

A focused Robolectric class calls the production `DocumentParser` and, for exact cell/range selection, the production `ContextEvidenceExcerptSelector`. The final run covers PDF page 2, a DOCX table cell, XLSX exact cell/range/formula/cache and v2 value, quoted CSV cell/coordinate, Kotlin source line, and PPTX slide 2. The query metadata declares the actual app-parser coverage formats: PDF, DOCX, XLSX, CSV, code, and PPTX. All fixture files are marked synthetic by the corpus metadata; none is the private Motorola workbook.

## HOST result

Final exact-code run: `481e36633ae84224b63e225d84010ccfa16f8392`; Gradle Wrapper 8.10.2; Temurin JDK 17 Gradle runtime; Temurin JDK 21 test worker; Android SDK API 35 / NDK 27.0.12077973; offline Gradle user home `/workspace/.gradle-master-program-20261016`.

Command:

```text
timeout 300s env JAVA_HOME=/workspace/.toolchain/jdk-17-temurin PATH=/workspace/.toolchain/jdk-17-temurin/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016 ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --init-script docs/validation/master-program-document-delete/force-test-execution.init.gradle -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest --tests 'com.localai.workspace.data.DocumentCorpusFixtureTest'
```

Result: exit 0 in 58 seconds; Gradle reported `BUILD SUCCESSFUL` and `3 executed, 60 up-to-date` actionable tasks. The JUnit XML reports **7 tests, 0 failures, 0 errors, 0 skipped**; all seven cases have successful test events. XML test time was 9.582 seconds. The complete Gradle log records the test executor using `/workspace/.toolchain/jdk-21-temurin/bin/java`.

An earlier exploratory run passed 6/6, but its working tree was dirty while HEAD pointed to `76f033e`; it is retained as historical evidence only. The final 7/7 exact-SHA run above is the validation result for this increment. The initial environment metadata probe without the designated Gradle user home failed to resolve the wrapper; this did not affect the subsequent offline test invocation. The corrected `--version` probe and its output are preserved separately.

## Not run / limits

Full app JVM suite, LiteRT compatibility suite, lint, AndroidTest compilation or execution, assembleDebug/Release, emulator, physical Motorola, APK generation, and performance/model benchmarks: `NOT_RUN`. This work validates representative synthetic corpus fixtures only. It does not reproduce or replace the private XLSX workbook, close M00-10, formally complete M02-01, or complete the broader exact-retrieval/citation DoD of M02-05.

M02-05 remains partial preparatory evidence; no original task ID was marked complete. M01-06 remains `PENDING_REQUIRES_DEVICE`. The source plan remains 97 unique task IDs, 120 dependency edges, and 97/97 original statuses `TODO`; its files are unchanged.
