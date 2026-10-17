# Preparatory M03-05 — bounded PNG images in generated PPTX

**Code SHA:** `6231732ad8557465abfc8155925fdcbc90d1eb87`
**Status:** partial HOST evidence; the interrupted Gradle invocation has no observed exit code. No formal backlog task is closed.

## Change

The PPTX descriptor can now carry one user-supplied PNG and required alternative text on a slide. The implementation validates canonical Base64, PNG structure and chunk CRCs, decoded dimensions/pixel count and byte bounds before embedding the actual PNG bytes. The renderer adds the PNG content type and media part, slide relationship and a picture shape with alternative text and locked aspect ratio. It fits the image in a bounded area beside the body and preserves the generated layout, master and theme. No image is generated, downloaded or read from a path/URL.

The artifact preview remains text/structure only and is explicitly not a slide canvas. The original artifact and prior immutable revisions are not overwritten.

## Host evidence

- **Attempt 05** ran only `PptxImageArtifactTest` with Gradle 8.10.2, Temurin 17 runtime, Temurin 21 worker, API 35/NDK 27.0.12077973 and the existing offline Gradle cache. The exec session was aborted by the user while Gradle continued in the background. Its JUnit XML records 2 tests, 2 passed, 0 failures/errors/skips. The shell/Gradle exit code and a final `BUILD SUCCESSFUL` marker were not observed. The invocation began with Git HEAD `58a63b2`; its dirty production/test tree was later committed as `6231732`, but no separate source-tree hash was captured at invocation. Treat the XML as evidence that the two test methods ran and passed, not as a complete Gradle-command PASS.
- **Attempt 06** on committed code `6231732` exited 0 and printed `BUILD SUCCESSFUL`, but all 63 tasks were `UP-TO-DATE`, including `testDebugUnitTest`; it executed zero tests. The copied XML in that directory is labeled as coming from attempt 05 and is not counted for attempt 06.
- The generated fixture `fixture-v1.pptx` is 5,464 bytes, SHA-256 `794ffc02dd8acfa650bbf660e2fd44b0580bcb6ae384c2463d838cacc04ca4c5`. The independent `python-pptx`/ZIP reader record reports valid ZIP CRC, one slide, Blank layout, one master, theme part, one picture, alt text `Mapa & datos`, and the embedded 2×1 PNG. That reader ran in attempt 04; the package hash matches the generated final fixture, and valid-image renderer output did not change after it. This is package-structure evidence, not Android viewer validation.

### Attempt ledger

| Attempt | Outcome | Counted test result |
|---|---|---|
| 01 red | Test harness compile failure (`NodeList.length`); no behavioral reproduction | 0 tests |
| 02 red | Expected feature gap: new PPTX image fields rejected as `ARTIFACT_FIELD_UNSUPPORTED` | 1 failure |
| 03 | Test harness compile failure (missing JUnit `fail` import); accompanying XML is stale from attempt 02 | 0 tests |
| 04 | Two image tests failed on test/assertion issues; ten API 29 roundtrip cases failed before test bodies during Robolectric artifact bootstrap | 12 failures, not a production conclusion |
| 05 | User aborted exec session; later JUnit XML records both final focused methods passing, but Gradle exit and final log were not observed | 2/2 XML PASS; command completion unverified |
| 06 | Gradle exited 0, all tasks including test were `UP-TO-DATE` | 0 tests; not a test PASS |

The exact commands and attempt artifacts are kept in their respective directories. XML copies in attempts 03 and 06 are explicitly marked as stale/copied and are not counted for those runs.

Earlier failed attempts are retained under `attempt-01` through `attempt-04`. In particular, an API 29 `ArtifactRoundtripTest` run failed before its test bodies because Robolectric could not fetch `android-all-instrumented:10-robolectric-5803371-i7`; do not attribute those bootstrap failures to PPTX production behavior.

No full JVM suite, LiteRT compatibility suite, lint, AndroidTest compile, emulator, Motorola test, or APK was run/generated for this increment. The compatible update-signing key is not available in this executor; no replacement key was created.

## Scope and source plan

The original source task is **M03-05 — Generación/edición básica PPTX**, still `TODO`, `requires_device=true`, and dependent on M03-01, which also remains `TODO`. The DoD additionally requires reader compatibility, preview and roundtrip. This increment supplies and validates a bounded image component of generated PPTX structure only; it does not fulfill the complete DoD. The preserved backlog remains 97 unique IDs and 120 dependency edges, all source statuses `TODO`.

The work changes no model/runtime, retrieval, Room schema, permissions, dependency, workflow, or original plan file. Global EMULATOR remains the separately recorded Room-v1 fixture failure plus six model-dependent skips; PHYSICAL_DEVICE remains `NOT_RUN`. Native latency, throughput, memory and thermal performance remain unmeasured on the Moto G86.

## Reproduction and integrity

Attempt commands, losslessly compressed Gradle logs, timestamps, JUnit XML, independent-reader output, and attempt outcomes are stored beside this report. `SHA256SUMS` covers the files under this evidence directory except itself; its own SHA-256 is recorded outside the manifest in `EXECUTION_STATE.json` and the handoff.
