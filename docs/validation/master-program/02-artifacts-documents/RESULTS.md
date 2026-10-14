# Master program — artifacts/documents/benchmark preparation

Code `c400dae169c6b511ba90266f9e570912470982a7`, feature branch, initial base `b44470b`. Status: **HOST_TESTED_PREPARATORY_SUBSETS; MASTER PROGRAM INCOMPLETE**.

Production scope/format limitations/ID crosswalk: [master program](../../../development/MASTER_OPTIMIZATION_PROGRAM.md). Original 97 IDs and 120 dependencies are unchanged. Synthetic corpus is not the private Motorola workbook.

## Real executed gates

| Scope | Code SHA | Result |
|---|---|---|
| Real adapter + artifact roundtrip | c400dae | 8/8 PASS |
| Final package + exact cells/Office import | 4bed0b6 | 9/9 PASS |
| EG2 F1/F2 + configuration-change regression | 2a08977 | 27/27 PASS; semantic production source unchanged afterward |
| Real ChatViewModel controller/registries/quantiles | 357366d | 37/37 PASS |
| App selected classes + full LiteRT compatibility JVM | 6495856 | 36/36 PASS, includes 2 compatibility cases; not full app suite |
| Independent reader roundtrips | c400dae | 10/10 original/edited files, with metadata SHA and fresh-output checks |
| Delivery Debug ARM64/lint/AndroidTest Kotlin compile | c400dae | exit0, 402.447s; cached/offline inputs, not clean-room proof |
| Emulator / physical device | — | NOT_RUN; ADB reports zero devices |

Counts across runs overlap; **do not sum them into a unique/full-suite total**. [All run outcomes](run-results.json), per-run command/result JSON, JUnit XML and original compressed logs preserve failed attempts. JDK17 runtime/JDK21 workers, existing SDK35/NDK27.0.12077973/CMake3.31.6; offline, retained caches, no toolchain download/cleanup. 300s test limit, per-test listener and process capture; pure build receives 900s after the first 300s build timed out during lint.

The first cache fixture had an invalid hash (2 failures), corrected separately. The configuration-change test reproduced a real 1/1 failure before the revision snapshot/check fix. Native adapter initially failed compilation (0 tests) then a wrong exception expectation: pinned SDK returns an error string; exact scope-error/no-side-effect assertions now pass. Initial independent DOCX/PPTX checks found missing table grid/layout links; corrected packages now validate both content and relationships. The initial probe stdout is in session history, not an invented saved red binary.

## Files and interfaces

Actual new XLSX/DOCX/PDF/PPTX/code bytes are created, hashed, reopened, revised and restored through app-owned immutable versions. Code is never executed by this API, formulas are not recalculated, original imports are unchanged. Real native SDK ToolManager/NativeChatTools wiring and private Room ownership checks are exercised without an LLM. Model-directed tool selection remains DEVICE_PENDING.

Chat menu **Artifacts** provides structured descriptor editing, text/structure before/after preview, version inspection/restoration, explicit SAF export and hash readback. Choose **Tools → Auto** to enable artifact tools on an existing stored profile; Tools Off does not disable the Agent. SAF/open-in-reader/rotation/power-loss/ENOSPC behaviors need physical checks; AndroidTest compilation is not execution.

Synthetic files: [corpus](synthetic-corpus/expected-queries.json). [Independent validators](independent-reader-results.json), [reproducible validation script](validate_artifact_readers.py). Libraries were already installed; no new Android dependencies. Python reader checks do not replace Android/Office GUI rendering. Initial reader scope covered text only; final scope additionally verifies blank layout/master/theme relationships.

## P0 / model decision

Eight query vectors use at most 24KiB raw vector bytes, with hashed identity, live source/index/rank checks and no private text telemetry. Controlled repeat native-compute count 2 → 1 is not a measured neural/phone speed gain. Benchmark cohorts now report p50/p95 without mixing device/model/config or filling missing data with zero. No 25–30% claim.

E2B remains preferred. Official release metadata for LiteRT-LM v0.18.0/Gallery1.0.20 is archived; runtime stays pinned0.17.1 pending actual comparisons. No E4B default, weight download, new resident LLM or fine-tuning. ADB exit0/deviceRows0 is an authentic blocker for the target P0 baseline and model decisions. User-provided E4B 12GiB guidance is a reference constraint, not a measured device result.

Historical Room v1 absence, six real-model omissions and UX02e incomplete full gate remain separate; no formal milestone or original task is promoted. The first preparatory implementation is delivered while native performance/quality and advanced imported editing/OCR/resume/code-project/image features remain incomplete.

Final lint severities: {"Warning": 41, "Information": 1}. New Android UI/SAF interaction and viewer/device inference are NOT_RUN, not certified by compile.

## Personal Debug ARM64 delivery

Source `c400dae169c6b511ba90266f9e570912470982a7`, `com.localai.workspace.debug`, 0.5.0-debug/versionCode27, arm64-v8a only. 65,660,777 bytes. SHA-256 `8203e58cdbd4dbbca064d373ea58b4f14a5b6b4fc0b7e5ea7e48c128f46c4ff8`. Signature v2/v3, zipalign4/16KiB, CRC and identical284 payload checks PASS. Debug cert SHA `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b` matches previous personal/UX01/UX02a signer; phone's installed signer/installation not verified. Same versionCode replacement is permitted; do not uninstall or clear data.

[Standalone APK in Drive](https://drive.google.com/file/d/1K5tcG-igT6gcGq77mhDLLmJ9FMi7odV8/view?usp=drivesdk). Readback confirms ID/name/MIME/folder/bytes; Drive payload not independently downloaded/rehash and no public-sharing claim. APK/key/weights are not in Git. Older intermediate4bed APK was verified locally but not delivered; this link is finalc400.

Program is incomplete, with P0 native baseline/benchmark and target-specific model decisions blocked by zero connected devices. Independent code/human audit is pending; independent library validation is HOST evidence only. Original task/UX/Room/device statuses remain unchanged. No release/merge/tag.
