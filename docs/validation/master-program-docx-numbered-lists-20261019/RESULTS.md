# DOCX ordered-list generation — preparatory M03-03 slice

This increment adds real decimal ordered lists to generated DOCX artifacts. It is preparatory work only: original task **M03-03 remains TODO**, requires device validation, and depends on M03-01 and M02-03, both still TODO.

## Change

`ArtifactSpec` now accepts the DOCX paragraph style `numbered` in both ordered `blocks` and legacy `paragraphs`. The DOCX renderer writes a decimal `abstractNum` and a distinct `w:num` instance for each contiguous numbered run. Consecutive items advance together; a paragraph/table boundary, title, heading, page break or bullet closes the run so a later numbered list restarts at one. Existing bullet numbering keeps its original `numId`. DOCX previews show the same numbered/bullet markers, and the artifact tool description advertises the input. PDF continues to reject `numbered` rather than silently accepting a style it does not render.

The scope is limited to flat decimal level-zero lists. Custom starts, nested ordered lists and Roman/alphabetic output formats are not implemented here.

## Test-first and validation record

The expected-red run at production base `008d7b5` failed 1/1 because `ArtifactSpec.parse` rejected `style=numbered` with `PARAGRAPH_STYLE_UNSUPPORTED`, before creating a DOCX. The equivalent test-only commit is `84bb54f`.

The first post-change attempt at `d1bd206` failed in `compileDebugUnitTestKotlin`: the test called `getElementsByTagNameNS` on a DOM `Node` instead of an `Element`. No test started and no fresh JUnit XML was produced; the unchanged older XML in the Gradle inventory is excluded. A test-only cast correction was committed as `356db30`.

On exact tree `356db30a9982dec804060ea5492d998c2de5d9f7` (production implementation `d1bd2062c0638ac3edd44d243d4783a578f2d00e`), the focused `DocxArtifactStructureTest` class passed **4/4**, with 0 failures, errors or skips. Gradle exited 0; all 63 actionable tasks executed. The fresh JUnit XML timestamp is after this run's start and its counts reconcile with the full Gradle log.

The test checks that generated blocks create valid decimal `numPr` references, that contiguous items share an instance, a later run restarts through a new numbering instance, bullets remain available, previews carry the markers, the legacy paragraph input path works, and PDF rejects the DOCX-only style. It then parses the generated DOCX through the application parser and checks the displayed labels.

The test wrote a synthetic file `green-final/ordered-list.docx`. A separate installed `python-docx 1.2.0` reader opened that exact evidence copy, verified ZIP CRC and required OPC parts, and read paragraph numbering IDs `2, 2, none, 3, 1`. This independently confirms package relationships and numbering references; it does **not** claim a Word/LibreOffice visual rendering test.

## Environment and limits

Gradle 8.10.2 ran on Temurin JDK 17.0.20.1; the app test worker used Temurin JDK 21.0.12.1. Existing Android SDK API 35, NDK 27.0.12077973 and `/workspace/.gradle` cache were used offline. No dependency or SDK component was installed. Full app/LiteRT suites, lint, debug/release assembly, AndroidTest compilation/execution, emulator, physical device and APK were **NOT_RUN**.

No user document was used. The source backlog and roadmap remain byte-identical: 97 unique task IDs, 120 dependencies and all 97 original statuses TODO. M03-03 stays TODO; M03-01 and M02-03 stay TODO. M01-06 remains PENDING_REQUIRES_DEVICE and PHYSICAL_DEVICE remains NOT_RUN.

## Evidence

The three complete Gradle logs are retained as lossless gzip copies with original and compressed SHA-256 values in each `gradle-log-payload-hash.txt`. Red and final JUnit XML, the compile-failure task/XML inventory, toolchain preflight, independent-reader script/output, synthetic DOCX and exact commands are in this directory. Payload checksums are listed in `SHA256SUMS`: 45 entries; manifest SHA-256 `50c3aa2e408058de110272bbb6bf3488cf53d3fc4189f2419d9ba2aad5f3d348`. Local verification passed 45/45. `RESULTS.md`, `RESULTS.json` and `SHA256SUMS` are excluded from that payload manifest; verify with `sha256sum -c SHA256SUMS` from this directory.
