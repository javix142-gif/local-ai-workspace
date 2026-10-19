# M03-02 preparatory follow-up — formula edit inside a native table

This check adds a test-only interaction guard for the already implemented `artifacts.xlsxSetCells` formula edit against an app-generated native XLSX table. It does not change production code or complete source task M03-02.

## Attempts

| Attempt | Tested source | Outcome |
|---|---|---|
| 01 | `1f80085ea8a2bc564e28311141fd60e9601e8a6e` | 1 test, 1 failure, 0 errors/skips, exit 1, 204 s. All tool/revision/table relationship checks before line 178 passed. The test expected `null`, but `StructuredDocuments.xlsx` returns `""` when a formula has no `<v>` element. Classification: **TEST_ASSERTION_ASSUMPTION_MISMATCH**, not a product defect. XML and full compressed log are preserved in [`attempt-01-red`](attempt-01-red/). |
| 02 | `36fa81fa0cfc4b6f5f9136d86f98e54951110b4a` | 1 test, 1 pass, 0 failures/errors/skips, Gradle exit 0, 46 s wall; test body 5.076 s. Test now checks raw OOXML for the formula and absence of a stale `<v>` cache, then asserts the reader's actual empty-string representation. See [`attempt-02-green`](attempt-02-green/). |

The test also confirms a new immutable version is produced, the prior revision's hash/bytes remain unchanged, and the edited workbook retains its native `table1.xml`, relationship, worksheet `tableParts`, table name, range and headers.

## Independent workbook read

The emitted synthetic XLSX passed ZIP CRC and `openpyxl 3.1.5` readback: sheet `Ventas`, table `TablaVentas` at `A1:B2`, formula `=SUM(A2:A2)` in B2, and no cached `<v>` value. XLSX SHA-256 is `c474bf6dbfd8031d1fc048c1e5c3f4100dcc02c0afa6de0a32379d09ef8f6672` (3,148 bytes). The first independent-reader script attempt had an over-escaped regex; that harness error and its corrected script/output are retained. The same file passed the corrected readback.

## Scope and limits

Product code SHA is `c991a4d6fd0240045a31515e5f30fec0309c37ac`; only the test file changed in commits `bcec858` and `36fa81f`. Formula recalculation is not implemented; the test checks that stale cached data is removed and leaves a compatible spreadsheet reader to recalculate. Only the focused method and independent host reader ran; no full JVM/LiteRT gate, lint, AndroidTest, emulator, Motorola, APK or Excel/LibreOffice application test ran.

Original M03-02 remains `TODO`, `requires_device=true`, depending on M03-01 and M02-02, both source `TODO`. The canonical 97-task backlog and its 120 dependencies were not modified.

The child `SHA256SUMS` covers attempt commands, logs, XML, generated XLSX and reader artifacts, excluding this report and `RESULTS.json` to avoid a checksum cycle.
