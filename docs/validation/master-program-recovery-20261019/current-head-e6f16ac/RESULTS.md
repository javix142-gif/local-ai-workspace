# Recovery and M03-07 current-source checkpoint

## Recovery findings

The recovery prompt named `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`, but the live feature ref was already `e6f16ac30914fe32f7140241e81e5976660c03d2` (81 commits later). The implementation did not reset or overwrite the existing checkout. A fresh full clone was checked at the exact fetched SHA with an empty tracked/untracked status before a separate implementation branch was created.

The reported local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` remains **NOT_RECOVERED**. It is absent from the available object database/refs; prior persisted recovery evidence records that direct GitHub fetch was rejected and that no patch or backup was found in the bounded workspace search. No code was reconstructed. The current original workspace's separate test-only commit `1f80085ea8a2bc564e28311141fd60e9601e8a6e` was preserved in a verified local Git bundle and its test change was carried into the new recovery branch as `bcec858`.

The fresh clone created and read back two refs without force-push: backup `recovery/backup-e6f16ac-20261019` at `e6f16ac…`, and independent implementation branch `codex/master-program-recovery-20261019` initially at the same SHA. The pre-existing feature branch was not changed by implementation work. `main` and peeled `v0.4.2` remained at `de615b772cf37a99e7e08a647c59607b271de606` at preflight.

## M03-07 current-source revalidation

At exact remote source SHA `e6f16ac30914fe32f7140241e81e5976660c03d2`, the focused HOST class `ArtifactRevisionPreviewUiTest` passed **2/2**, with 0 failures/errors/skips. Gradle exited 0 and reported `BUILD SUCCESSFUL` in 3m22s (63 actionable tasks, 63 executed). The tests cover visible saved/proposed content, the bounded line-diff fallback, the saved-v1 export affordance, and cancel preserving the prior revision bytes/hash. The exact command, environment, fresh XML, full compressed Gradle log, timing and hashes are in [`m03-07/`](m03-07/).

This is focused HOST evidence, not formal M03-07 completion. The authentic task remains `TODO`, `requires_device=true`, and depends on M03-01, which remains `TODO`. No full JVM/LiteRT gate, lint, build, AndroidTest, emulator, Motorola, APK or performance benchmark was run in this checkpoint. The historical emulator Room-v1 failure and model-dependent skips remain separate; physical-device state is `NOT_RUN`.

## Separate XLSX test-only follow-up

The preserved local table-cell formula test first failed only because it expected `null` while `StructuredDocuments.xlsx` represents an absent formula cache as an empty string. Its preceding assertions passed, including immutable prior revision and table XML relationships. A corrected test asserts the reader's actual empty-string contract and independently checks raw OOXML: the formula exists and its stale `<v>` cache is absent. The corrected focused run at test SHA `36fa81fa0cfc4b6f5f9136d86f98e54951110b4a` passed 1/1, Gradle exit 0 in 46s. `openpyxl 3.1.5` reopened the emitted synthetic workbook, confirmed the `Ventas` sheet, `TablaVentas` range `A1:B2`, formula `=SUM(A2:A2)`, absent cache and ZIP CRC. Full details and both attempts are under [`M03-02 cell-edit follow-up`](../../master-program-m03-02-xlsx-tables/cell-edit-follow-up-20261019/RESULTS.md).

No production code changed in this follow-up. M03-02 remains `TODO`, requires a device, and depends on M03-01 and M02-02, both `TODO`. The original backlog and roadmap were not edited: 97 unique IDs, 120 dependency edges, all 97 source statuses `TODO`.
