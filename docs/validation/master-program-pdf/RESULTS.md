# PDF layout correction — preparatory HOST evidence for M03-04

This increment fixes one reproduced defect in the local PDF renderer: a normal word could be split at the 504 pt text-width boundary. The renderer now wraps at whitespace boundaries and only divides an individual unbroken token when that token itself is wider than the available line. Splitting iterates Unicode code points, so it does not cut surrogate pairs. PDF generation remains a controlled regeneration path; it does not edit arbitrary imported PDFs.

The original M03-04 source task remains `TODO`, `requires_device=true`, and depends on M03-01, which also remains formally TODO. This is preparatory HOST evidence; no task ID or dependency was changed or completed.

## Reproduction and correction

- Red test-only source: `3532cc69e177ece728e0c2e21c72da0e79cc025f`.
- Code fix: `648515e7e69bfeea0c7101c0d8ed4ab01270b634`.
- Test: `PdfArtifactLayoutTest.generatedPdfKeepsWordsTogetherAndPreservesPageLayoutAndVersions`, using synthetic content only.

The red run passed its prior-page, page-size, title and heading assertions, then failed because PDFTextStripper returned `WORD_BOUNDARY` and `_MARKER` on separate lines. Its XML has 1 test, 1 failure, 0 errors and 0 skipped. That is the expected reproduction of the production defect, not a harness/setup failure.

The corrected exact-source run passed 1/1 with 0 failures/errors/skips. It generated revision 1 and revision 2 through the production `ArtifactStore`, verified two Letter pages and preserved the first immutable revision after editing the second. Full raw logs, command, environment, timestamps, XML and exit codes are retained under `attempt-01-red/` and `attempt-02-green/`.

## HOST checks

The green Gradle invocation used Gradle Wrapper 8.10.2, Temurin JDK 17 as Gradle runtime, Temurin JDK 21 for test/toolchain discovery, Android SDK API 35, NDK 27.0.12077973, and the already-present Gradle cache in offline mode. No SDK components, dependencies or caches were installed, copied or cleaned.

The exact command (with a ten-minute outer timeout) is in `attempt-02-green/command.txt`. It ran only `:app:testDebugUnitTest --tests com.localai.workspace.artifacts.PdfArtifactLayoutTest`; 63 Gradle tasks executed. The command exited 0 in 227 seconds; the JUnit method took 7.214 seconds.

Both generated PDFs were also opened by independent readers PyMuPDF 1.26.6 and pypdf 6.10.0. The verifier confirms:

- two pages, each 612 × 792 pt;
- title 20 pt, heading 16 pt and body 12 pt;
- all extracted text bounds remain within each page;
- the page-break content and table text remain readable;
- `WORD_BOUNDARY_MARKER` is one extracted word after wrapping;
- the revised PDF contains the revised title, while the original revision remains a separate file and its hash is unchanged by the store test.

The synthetic PDFs and machine-readable results are retained in `attempt-02-green/independent-validation.json`. They contain no user documents or personal data.

## Scope and limits

- `ArtifactRenderer.kt` is the only production file changed in this increment; no dependencies, persistence, Room schema, runtime, model or workflow changed.
- This is one focused HOST test plus independent PDF parsing. The full JVM/LiteRT suites, lint, AndroidTest compilation, emulator, Motorola device, visual rendering in a mobile PDF viewer, SAF export and APK were not run for this code SHA.
- M03-04 is not formally complete: source requires a device and depends on M03-01, still TODO. The generated files are synthetic fixtures and do not demonstrate arbitrary imported-PDF editing.
- The historical global EMULATOR state remains FAIL because the authentic Room v1 fixture is unavailable, with six model-dependent tests skipped. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.
- The canonical source backlog and roadmap remain unchanged: 97 unique task IDs, 120 dependency edges and 97 TODO statuses.

## Next preparatory increment

M03-05 is the next source entry in order. Its source status remains TODO, it requires a device, and it depends on M03-01, still TODO. Any subsequent work remains explicitly preparatory and cannot close the original milestone without its dependencies and required device validation.
