# M03-02 generated XLSX formula-edit preparation

Date: 2026-10-19. This is a focused preparatory increment in `codex/master-program-20261019`, not formal completion of original backlog task M03-02.

At preflight, `origin/codex/master-program-20261019` was `83a4a74508a94880ffb000b546efc7a362e4545d` and an ancestor of the tested code. The separate feature branch remained `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`; `main` and peeled `v0.4.2` remained `de615b772cf37a99e7e08a647c59607b271de606`. Publication is restricted to the Codex program branch.

## Change

Code SHA tested: `de7445114ad90c0a11453663971286feb2c5fdef`. The implementation extends the existing `artifacts.xlsxSetCells` action for app-private, generated XLSX revisions. Each edit supplies exactly one of a literal `value` or a bounded `formula`; formula edits create a new immutable revision and keep the original bytes/hash, formula and cached value intact. The changed formula in the new revision has its old cached `<v>` removed, and the workbook requests recalculation when opened by a compatible spreadsheet reader. This app does not evaluate or recalculate formulas. The preview explicitly says the cached result is unavailable and the formula was not recalculated. The existing bounded formula validation remains in effect, so external `WEBSERVICE` input is rejected without creating a revision. Deterministic routing handles explicit formula-edit intent, while a request to recalculate is not routed to this non-calculating tool.

Scope is generated private artifacts only. Imported source workbooks, Room/schema, runtime/model, dependencies, permissions and the original plan were not changed. No independent Excel/Office reader or Android device was used.

## Regression history

- Red attempt at `0aca92ac7f1faf680c2f3e020191fa4d569643b7`: the two new behavior tests compiled and failed for the target gaps—formula-edit intent selected no tool, and formula input was rejected by the existing literal-only tool schema. XML: 2 tests, 2 failures, 0 errors/skips.
- Earlier attempts `aebd4ed` and `c65c772` did not reach JUnit because of test-source syntax/type errors. They are preserved but are not product-failure evidence.
- First implementation run at `00429818a4b9c5b5bce13b6fb45ec65b54d4cc02`: 5 tests, 3 passed and 2 failed only on the response-note assertion requiring the exact phrase “not recalculated.” The implementation created the revision; the subsequent wording was corrected in `de744511` to state that the app does not recalculate formulas.
- Final exact-code run at `de7445114ad90c0a11453663971286feb2c5fdef`: `XlsxArtifactRevisionTest` passed 5/5, 0 failures, 0 errors, 0 skips; Gradle exit 0. JUnit test time totals 5.83s; Gradle wall time was 51s by recorded start/end timestamps. The full app suite was not run.

## Reproduction

The exact command, environment, start/end time, exit code, unfiltered Gradle log and fresh JUnit XML are in `green-attempt-02/`. Gradle 8.10.2 ran on Temurin JDK 17.0.20.1; the app test launcher is configured for Java 21 and both JDKs were exposed to toolchain discovery. The existing `/workspace/.gradle` cache and build outputs were reused in offline mode. This result establishes a focused HOST test on the exact committed code; it does not establish a clean-checkout build or a full host gate.

The test log is preserved byte-for-byte as `green-attempt-02/gradle.log.gz`. Its compressed SHA-256 is `93d25925ed06bdd5f497eb3d98a596e0e15d7729e70a3c6785f1e42b3246c931`; decompression reproduces the original 5,290-byte log with SHA-256 `521c07aa5b7d6e599a995c800e17ed1fd26572f409e3f05a454fae72ed2ba3d5`. `LOG_HASHES.json` records this round-trip and equivalent hashes for every attempt. The local ignored `.log` copies were not altered.

JUnit XML: `green-attempt-02/junit-xml/TEST-com.localai.workspace.artifacts.XlsxArtifactRevisionTest.xml` (SHA-256 `25284ade2e59239aa0c94d2931163e0a37c6776525b08539eaf2aa7bf05cb342`).

All preserved payload files and their SHA-256 values are listed in `SHA256SUMS`; the manifest digest is recorded in `EXECUTION_STATE.json` after publication.

## Original task status and limits

Original M03-02 (`Exportador XLSX editable`) remains `TODO`, requires a device, and depends on M03-01 and M02-02. Both dependencies remain source `TODO`; M03-01 also depends on M02-05. This increment does not prove the complete create/edit/save/reopen DoD on a device and does not establish compatibility with Excel, LibreOffice or another independent spreadsheet reader. It does not calculate formulas. No APK, lint, full JVM/LiteRT suite, AndroidTest compile or execution, emulator or Motorola test was run. No original task ID/status/dependency was changed.

The source backlog and roadmap remain byte-identical: backlog SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` (97 unique task IDs, 120 dependencies, all 97 source statuses `TODO`); roadmap SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`.
