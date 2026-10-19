# EG2 stored-vector scoring without per-row FloatArray — focused HOST evidence

Code SHA: aabca8226634de6da08006dc255b0b5727360cdd

## Finding and implementation

SemanticLayer.search() loads a bounded corpus of up to 10,000 indexed rows. Before this change, each row passed through SemanticVectors.decode(), allocating a decoded FloatArray, then through a second loop for cosine scoring. The new SemanticVectors.cosineEncoded() reads the existing FLOAT32_LE_V1 bytes and accumulates the dot product in one pass without a decoded per-row array. The production semantic search now calls that path.

The persisted vector format and DB are unchanged. Search still requires the active embedding-space key and exact dimension; malformed byte lengths and non-finite values remain errors. Ranking, FTS fusion, selected-document filters, project scope and result limits are unchanged. A deterministic test asserts the exact same Double score as decode-plus-cosine for a 768-dimensional fixture.

This is a source-level allocation reduction; no heap-allocation profiler, elapsed-time benchmark, native model or Motorola measurement was run. It must not be described as a measured latency or TTFT improvement.

## Validation

At the exact code SHA, the focused HOST command ran SemanticDomainTest and SemanticContextPostfixTest.

- Start: 2026-10-19T23:13:59Z
- Finish: 2026-10-19T23:15:12Z
- Gradle exit: 0; reported 1m 12s, wrapper timeout was 600s
- SemanticDomainTest: 24/24 PASS
- SemanticContextPostfixTest: 39/39 PASS
- Total: 63/63 PASS, zero failures, errors or skips
- 63 actionable Gradle tasks: 7 executed, 56 up-to-date

The command, environment, compressed raw Gradle output, raw log checksum, JUnit XML and aggregate counts are preserved here. The Gradle log is gzip-compressed losslessly; decompression matches the captured bytes.

No full JVM/LiteRT suite, lint, assembly, AndroidTest, emulator, physical device or APK was run for this increment. The separate historical emulator suite remains blocked by the authentic Room v1 fixture and model-dependent skips; it was not repeated here.
