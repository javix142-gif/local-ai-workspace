# M02-02 preparatory XLSX fidelity evidence

**Original task:** M02-02, “Mejorar fidelidad XLSX y formulas cacheadas.” **Source status remains `TODO`.** The original dependency M02-01 remains formally TODO because it depends on M00-10, which is device-required. This increment demonstrates the bounded M02-02 behavior on synthetic versioned fixtures; it does not close either source ID.

## Audit and change

`StructuredDocuments.xlsx()` already preserved worksheet order/names, cell address/type, formulas and the cached `<v>` value, and it never evaluates formulas. The implementation has explicit archive/cell/size bounds. Existing tests covered a formula and cached value but did not assert the actual XLSX type metadata across the versioned corpus. I added one focused assertion to the existing API 35 corpus test, with no production changes.

The test verifies the v1 workbook's `Hoja1`/`Hoja2` order; numeric `A1`, `A2`, and formula cell `A3`; inline-string `B1` and `Hoja2!B14`; serialized `valueType`; formula `$A$2-$A$1`; cached result `4`; and the v2 revision where `A2=7` while the supplied `A3` cache remains `4`. It expressly checks that serialization does not claim recalculation.

## HOST runs

Final tested code commit: `6662910a477640d9e2b11f3051feb7991e31d4a9` (test-only). Previous code commit `d0dbca3ea6cf55fc4fe7cb6e8212d265e40894c4` placed the new assertion in the existing API 29 suite; that run could not reach any test body because the offline cache lacked Robolectric's `android-all-instrumented:10-robolectric-5803371-i7`, and network connection attempts were refused. Its XML reports 11 setup failures with the same missing-runtime cause. This is an infrastructure failure, not an XLSX assertion failure. The test was moved into the already runnable API 35 corpus test class without weakening its assertions.

The final run used Gradle Wrapper 8.10.2, Temurin JDK 17 runtime, Temurin JDK 21 test worker, Android SDK API 35 / NDK 27.0.12077973, and the existing offline Gradle home. The exact command is in `attempts/attempt-02-api35-final/command.txt`. It ran only `DocumentCorpusFixtureTest`, finished in 59 seconds with exit 0, and Gradle reported `BUILD SUCCESSFUL` (3 tasks executed, 60 up-to-date). XML: **8 tests, 0 failures, 0 errors, 0 skipped**; the M02-02 fidelity assertion passed. The full log and wrapper output are preserved losslessly as gzip files; their compressed and decompressed hashes are recorded in `RESULTS.json` and `SHA256SUMS`.

## Limits

No production code changed. The full JVM suite, LiteRT compatibility, lint, AndroidTest compilation/execution, assembleDebug/Release, emulator, physical Motorola, APK generation, and performance benchmarks were `NOT_RUN`. All inputs were synthetic and versioned; no private XLSX was used. Formula results were read only from stored cache values.

The archived plan remains unchanged: 97 unique IDs, 120 dependency edges, all 97 original statuses TODO. M02-01 and M02-02 are not formally complete; M02-05 remains partial preparatory evidence; M01-06 remains `PENDING_REQUIRES_DEVICE`. The next candidate is preparatory M02-03 DOCX structure coverage under the explicit master-program authorization, with its original dependencies/status preserved.
