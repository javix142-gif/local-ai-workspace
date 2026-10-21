# Current checkpoint — H2 HOST complete; emulator evidence gate failed; H3a device pending / H3b awaiting references (2026-10-21)

## Latest handoff — H2 technical close and H3a/H3b

Feature branch code commits: 6e6acdaaedb20b7aff95fd2154d2b20eb8495cc6 (SET_ALARM manifest permission plus packaged-manifest instrumentation test) and fa6071f0322e0a60da5c2acf023e95847ab16619 (Moto smoke script and sanitized ADB errors). The full HOST gate ran on the working tree at Git HEAD a779880351ae1ed25ab32e8f6ef906abd7252fe8 before the code commit; it included the then-uncommitted permission/test changes later committed unchanged as 6e6acda. Do not describe it as a clean checkout of 6e6acda. App JVM: 961/961 in 132 XML. LiteRT compatibility: 2/2 in a separate forced task run after 6e6acda; that task was UP-TO-DATE in the combined gate. Lint, assembleDebug, AndroidTest Kotlin compilation and assembleDebugAndroidTest completed in HOST. The separate connected Android instrumentation and its non-PASS collector result are recorded immediately below. Two repeated focused cases (personal-data routing and the previously inconsistent DOCX list case) passed 1/1 each, twice; earlier cause remains NOT_DETERMINED. Exact logs/XML and commands are under docs/validation/h2-close-h3a-20261021/.

The manifest requests com.android.alarm.permission.SET_ALARM. Existing code/tests retain confirmation and EXTRA_SKIP_UI=false for ACTION_SET_ALARM and ACTION_SET_TIMER. `AlarmPermissionManifestTest.packagedApplicationRequestsSetAlarmPermission` passed in connected API 35 instrumentation on `fa6071f`. The complete emulator workflow is FAIL only at the evidence collector: its mandatory large-font screenshot is incomplete/missing. The connected task itself exited 0 with XML 23 total, 17 pass, 0 fail/error, 6 skipped for absent real model files. Do not report overall emulator PASS. Gradle console progress (28/23 and Finished 29) disagrees with the XML total 23; cause undetermined.

H3a script: scripts/device/smoke_moto.ps1, documented in docs/development/H3A_MOTO_G86_SMOKE.md. Static source review only; this Linux executor has no PowerShell and no device is connected (the SDK adb enumerated zero devices). H3a=DEVICE_PENDING; M01-06=PENDING_REQUIRES_DEVICE; PHYSICAL_DEVICE=NOT_RUN. The script does not install/update the app, change permissions or data, send messages, create alarms/events, or download models. An optional conversation screenshot requires an explicit interactive prompt and stays private.

The personal QA workflow [39601677489](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39601677489) passed independently of instrumentation. Its APK is in Drive as an APK-only ZIP: [download](https://drive.google.com/file/d/10K4tD-Gbo0bKGZ4jtgC4Dmfh79Z17oJI/view?usp=drivesdk), ZIP SHA-256 `3f098eca94460cd9bbc04678a21977da04fc88c43a9e95576e7d84970f181fc1`; inner APK SHA-256 `2ecbb8dcbb4e5101f29adb9a76582a412f4c87a58496997bdb8f4a643f8cf366`. It is Debug ARM64 only, package `com.localai.workspace.debug`, version `0.5.0-debug`/27, signed with the prior Debug QA certificate. Do not treat it as an official release or physical test.

H3b: awaiting the nine user-provided visual references; no UI changes before they arrive. M00–M03 stay frozen for this transition. The authoritative task catalog remains 97 unique IDs/120 edges, source statuses TODO, unchanged and hashed in the preserved source files. main and peeled v0.4.2 remain de615b772cf37a99e7e08a647c59607b271de606.

The following H1/H2 sections preserve earlier checkpoints and remain historical unless superseded above.

# Historical checkpoint — H1 HOST/CI_VERIFIED; physical checks pending (2026-10-20)

## Current update — H1-AT-04 pending-pin cap fix (`a5090d601426622b123cc2a970c13a5e8d9b3ff8`)

The 15% pinned-memory cap now applies before persistence when a pending memory is pinned. HOST focal: intended red 1/1; corrected test 1/1; H1 unit/UI suites 25/25; `lintDebug` and `compileDebugAndroidTestKotlin` exit 0. No full JVM suite was run.

EMULATOR run [39525954404](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954404) succeeded on API 35/x86_64. Connected XML/collector: 22 total, 16 PASS, 6 model-dependent SKIPPED, no failures/errors. H1-AT-12 in the same run installed source `9cfb0d8`, seeded synthetic data, then used `adb install -r` for `a5090d6`; package/certificate matched and 20 memories, project, chat/message and next-context preference survived. Synthetic fixture only; physical device NOT_RUN. One runner progress line said 27/22 although XML/collector agree at 22; reason NOT_DETERMINED.

Official QA [39525954210](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954210) and Personal QA [39525954318](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954318) succeeded for the exact code SHA. Debug ARM64 APK SHA-256 `063a5cf081c452e74c95bc250a71fd1678930637b5f2bdd40728c7899dbcc3b8`, 121,635,467 bytes, package `com.localai.workspace.debug`, version `0.5.0-debug`/27, arm64-v8a only, v2 signature verified; certificate SHA-256 `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. Personal artifact [12313972768](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954318/artifacts/12313972768); official artifact [12314321055](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954210/artifacts/12314321055). Both are QA artifacts, not Release. Emulator artifact [12313033931](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39525954404/artifacts/12313033931), SHA-256 `6ba3b3b0f69e05d1ec69c8058c53b8a8a4973d574ea670a539b3e1d1c39e5db7`; ZIP and internal 50-file manifest verified locally.

H2 implementation remains source SHA `a65a80d` and is unchanged. H1 physical AT-06/20/24 and Motorola tests remain pending; no native performance metrics are claimed. Room v1-v7 authentic schemas remain unavailable; the six model-dependent Android cases remain skipped. Backlog source remains 97 IDs/120 edges/TODO; `EXECUTION_STATE.json` and source catalog were not edited. `main`/peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.

---

## H2 implementation and validation checkpoint — 2026-10-21

Source SHA: `a65a80dae1cf7edc3ae42afeb76620f66bfdc309`, branch `feat/0.5.0-skills-agents`. The implementation adds bounded local `calendar.read`/`contacts.lookup`, point-of-use permissions, untrusted local results, redacted tool history, deterministic routing, Android action handoffs with explicit confirmation, and compact artifact guidance preserving DOCX list semantics.

HOST: focused logs report 19/19 PASS; LiteRT compatibility 2/2 PASS; lint and AndroidTest Kotlin compilation PASS (0 lint errors, 128 warnings, 1 info). Full app test was 959/960 once, with one DOCX list-description regression. That description was restored and the affected class passed 4/4 in later focused runs; the full suite was not repeated. An 18-test focused attempt also failed once at the personal-data schema/routing test; subsequent 18- and 19-test runs passed, and the cause remains NOT_DETERMINED. See [H2 evidence](../validation/master-program-h2-20261021/RESULTS.md).

M00-04 focused verification on this exact code passed 2/2, including the material-vs-routine notice behavior and Inspector drop reason. Source catalog status remains TODO and unchanged.

EMULATOR [run 39520261327](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520261327) passed its connected task on API 35 / Android 15 / x86_64: XML 22 total, 16 passed, 0 failed/errors, 6 skipped for real model weights. H1-AT-12 used synthetic update data and passed. The historical authentic Room v1-v7 schema is still unavailable and is not validated by the v8 migration case.

PERSONAL_QA [run 39520260941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520260941) passed and produced the Debug ARM64 APK. The APK-only Drive container is [here](https://drive.google.com/file/d/16LuVNvAsd_3WxUQn7wQKyzXwu9glMDLP/view?usp=drivesdk); SHA-256 `1432e93a432a936f07221236bd3135554c78e3c5f36c34f69e08bfd759deeb8b`. It contains exactly the APK. Full metadata and limits are in H2 RESULTS. `PHYSICAL_DEVICE=NOT_RUN`; no native model/device performance metrics are claimed.

The canonical plan files remain unchanged at 97 unique IDs/120 dependencies, all source statuses TODO. M00-04 behavior is HOST-verified but not written back as a source task status. M00-06 is the next original host-eligible task and already has partial instrumentation; audit its remaining end-to-end correlation gap before implementation. Preserve all H1, Room, model, and Moto limitations.

---

# Previous checkpoint — H1 HOST/CI_VERIFIED; physical checks pending (2026-10-20)

- Branch `feat/0.5.0-skills-agents`; feature HEAD `16287d649eabf063fb4482d302060f378b2ff35a`; product/test SHA `01161491acc2a7c836222a84b7bd6f778292f8f3`. The intervening commits are docs/evidence only.
- H1 corrective code is already present after audited SHA `3ca1d794`: commit `1f44f88` enforces pre-persistence pin budgeting, explicit no-pin approval, and H1-AT-12 update verification; later commits localize H1 UI/diagnostics.
- Current focused HOST verification: 25/25 PASS (`H1PreferenceContextTest` 24/24, `H1MemoryApprovalUiTest` 1/1), zero failures/errors/skips, Gradle exit 0 in 210s. It verifies the 15% cap, explicit selection and confirmation, preserved `originalPinned` provenance, 1,000-record roundtrip and usable chat. Full log/XML/environment/checksums: [`verification-16287d6`](../validation/master-program-h1-20261020/corrective/verification-16287d6/).
- Historical full HOST gate at `aed4d8f`: app 941/941, LiteRT compatibility 2/2, lint and AndroidTest Kotlin compile passed; Debug assembly was UP-TO-DATE. Do not attribute that full gate to the current source SHA.
- Connected run [39511660788](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660788) tested source `0116149`: 16 PASS, 0 FAIL/ERROR, 6 model-dependent SKIPPED. H1-AT-12 executed `adb install -r`, preserved 20 synthetic memories, project/chat/message and next-context preference, and opened the updated app.
- Official QA run [39511660726](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660726) passed on the same code SHA. Its Debug ARM64 signer is the development certificate `b0d1c408…ab120b`; artifact metadata and Drive delivery are in [H1 results](../validation/master-program-h1-20261020/RESULTS.md).
- H1 status is `HOST/CI_VERIFIED`, not physical-device completion. AT-06/20/24 remain device-pending; AT-20 requires an external backup before Moto installation. The original XLSX and Moto G86 are unavailable here. Six model tests remain skipped. Authentic Room v1–7 fixtures remain unavailable; no such migration claim is made.
- The ZIP is a memory portability package, not a full-app backup: no chats/messages, project entities, documents/files, models, settings, DB or vectors. See README for scope and import behavior.
- The public Debug/QA signer is `keystore/aws-qa-dev.jks`, alias `awsqa`, password `awsqadev`; never use it for Release. Before any official publication, replace it with protected signing: **“Reemplazar el keystore de desarrollo por una clave segura antes de publicar”**. The `.parallel` transition variant remains available.
- H2 is next and may proceed sequentially under the user's authorization. Preserve all 97 source task IDs/120 dependencies and historical TODO statuses. `EXECUTION_STATE.json` and the original task catalog remain untouched by this closure. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

---

# Historical handoff snapshot — AWS H0 → H1 at code 3ca1d79 (superseded by the current checkpoint above)

Rama única `feat/0.5.0-skills-agents`. Código H1 validado: `3ca1d7945752b89817ea1e972fed2cdc4872e6d4`, con commits de código `06d6d8f`, `e14a06a` y `3ca1d79` después del primer slice `3efc959`. El HEAD remoto previo a este cierre documental coincidía con `3ca1d79`; `main` y el commit pelado de `v0.4.2` siguen en `de615b772cf37a99e7e08a647c59607b271de606`. Los siete bundles preexistentes bajo `docs/validation/master-program-memory-vector-batch-20261019/` se preservan sin añadirlos.

Los documentos de referencia AWS están preservados byte por byte. `H1_PRUEBAS_ACEPTACION.md` v1.1 es el contrato activo; `archive/H1_PRUEBAS_ACEPTACION_v1.0.md` queda intacto. El catálogo original mantiene sus 97 IDs, 120 dependencias y TODO históricos; no se modificó `EXECUTION_STATE.json` bajo el alcance de continuidad actual.

## Cambios verificados de H1

- La aprobación de un lote importado conserva `pinned` y el resto de metadata de los registros; esa operación no convierte la importación en una acción explícita de pin. `MemoryManager.pin()` aplica el tope v1.1 del 15 % y devuelve rechazo antes de persistir si lo excedería.
- Si una consulta selecciona pins cuyo bloque supera ese 15 %, Context Builder no llama al motor y ChatViewModel muestra un motivo claro. Ninguna memoria marcada como fijada se elimina del contexto sin explicación.
- El test de AT-17 preserva los 1,000 registros/metadata y el caso de importación duplicada, a la vez que comprueba el rechazo de un prompt con pin sobredimensionado. AT-04 prueba tres pins de unos 60 caracteres que caben y un pin explícito rechazado. No se suavizaron las aserciones.
- El Android test de proyecto navega el UI real: abre el acordeón “Structured memory”, usa “Add”/“Content”, abre “Details” y sigue el botón “Edit”; la aserción de que el contenido eliminado desaparece se conserva. La pantalla limpia el detalle tras una eliminación exitosa.

## HOST — árbol fuente publicado como `3ca1d79`

Gate ejecutado 2026-10-20 18:15:27–18:25:08 UTC, 9m41s, Gradle exit 0, `BUILD SUCCESSFUL`, 158/158 tareas. Comando literal:

```sh
GRADLE_USER_HOME=/workspace/.gradle JAVA_HOME=/workspace/.toolchain/jdk-17 PATH=/workspace/.toolchain/jdk-17/bin:$PATH ANDROID_HOME=/workspace/.toolchain/android-sdk ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk ./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --info --rerun-tasks --init-script /tmp/aws-h1-test-events.init.gradle -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

JUnit: app 126 XML/939 PASS; LiteRT compatibilidad 1 XML/2 PASS; total 941 PASS, 0 fail/error/skip. La clase de aceptación H1 fue 22/22. El listener registró 941 `TEST_START` y 941 `TEST_END SUCCESS`. `lintDebug`, `assembleDebug` y `compileDebugAndroidTestKotlin` finalizaron. Gradle 8.10.2; JDK 17 runtime; JDK 21 worker app tests; SDK/NDK existentes; offline. Log íntegro local `/tmp/aws-h1-full-host-gate-v4.log`, SHA-256 `1d4c70f0f05faca56f59145df85a93c7b319225161a623c28b85fff11767aaaf`; metadata `/tmp/aws-h1-full-host-gate-v4.meta`, SHA-256 `772d528879acc7e57322c52b44af70d7645ed69acbc57b01b3eb2e231c987c53`; init listener SHA-256 `539222edf9c75f83f2b07da14bf34a0957e103a0bce46e4611dc0c71844c7d5d`.

## EMULATOR — run `39474604686`

- Fuente `3ca1d7945752b89817ea1e972fed2cdc4872e6d4`, job `118807197684`, API 35 / Android 15 x86_64, AVD `localai-api35-x86_64`, ADB `emulator-5554`. `:app:connectedDebugAndroidTest` exit 0; collector PASS. XML único: 20 casos, 14 pass, 6 skip, 0 fail/error. Los seis skips son dos `LiteRtLmDeviceSmokeTest` y cuatro `NormalChatNativeDeviceTest` que requieren el modelo real no instalado. XML/summary informan 20; Gradle también imprimió `26/20 completed`, discrepancia de progreso que permanece sin explicación.
- Los tests del proyecto de memoria y migración v8/v9/v10, navegación, encabezado, Context Inspector y Skill UI pasaron. Room v1 auténtico sigue sin fuente/fixture histórica; este run no la resuelve.
- Artifact [12289288740](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604686/artifacts/12289288740): 148,684 bytes, SHA-256 `81b0f027ad80a80196961fd603b0ee8c7a7a541328a046c6c83cd2097ab0d6cc`, expira 2026-10-27T18:40:13Z. ZIP descargado y hash recalculado localmente; manifest interno 32/32. PNG estándar y font-scale grande: 1080×1920, válidos; hashes `214f9e93a5f68d47b1e2825fcf0bc4727f91e0d60073c024746673397441084f` y `648e95eee679b74cd1e415fd9e704a203fc1a336e1a3b8ad0c45cf39d78a8469`.

## APK Debug ARM64 — QA y compatibilidad

El gate oficial [39474604678](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604678) esperó el PASS exacto de instrumentación y produjo el artifact `12290056446` (48,879,298 bytes; SHA-256 `7de7ca2697176fe57e691d052e7d5344636e062a2771a14c030bccfed424136f`; retención 7 días). El artifact se descargó y su manifest 3/3 se verificó. APK `local-ai-workspace-0.5.0-debug-arm64.apk`: 121,190,875 bytes, SHA-256 `0ec88a2efeffb4adb95b8bd869cfe6ada9a3caeb89bf698c181e24faad9352d6`; package `com.localai.workspace.debug`, version `0.5.0-debug`/27, nueve bibliotecas solo `arm64-v8a`. `apksigner` v2, ZIP CRC, `zipalign`, `aapt` package/version y `MODEL_PAYLOADS=NONE` pasaron en la revisión del artifact.

La clave QA persistente no está disponible. Firma del APK actual `f68ad607fee4d7145ac631d92645a4cd5d72c050a74ce9334fce1f851830ccd7`; firma de la APK personal anterior `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. No es actualizable como reemplazo de la app existente. El ZIP de Drive incluye solo el APK y está etiquetado para evitar instalación accidental: [descargar (NO COMPATIBLE COMO ACTUALIZACIÓN)](https://drive.google.com/file/d/1DQ2MaHDvo1u1c5vphlg-gM4QTFLlfbqA/view?usp=drivesdk), ID `1DQ2MaHDvo1u1c5vphlg-gM4QTFLlfbqA`, 47,297,228 bytes, SHA-256 `4fff7aad4c8e812d4b039161262b55da23d9647bc8c46dbda4b3ec70604524d5`. Drive readback confirmó el mismo tamaño y bytes; el archivo ZIP contiene solamente el APK. No se instaló ni se desinstaló nada.

El PERSONAL QA run `39474605036` no produjo APK porque faltaban `QA_KEYSTORE_B64`, `QA_KEYSTORE_PASSWORD`, `QA_KEY_ALIAS`, `QA_KEY_PASSWORD`; su artifact `12289590346` contiene únicamente el informe del fallo. El estándar QA y este intento personal son resultados separados.

## Pendientes

- H1 permanece abierto hasta la revisión completa de AT-01…24 y los criterios de dispositivo; AT-06/20/24 requieren el Motorola. AT-20 exige confirmar backup externo antes de cualquier instalación.
- `PHYSICAL_DEVICE=NOT_RUN`; `M01-06=PENDING_REQUIRES_DEVICE`. Para la validación M00 se necesita el Moto G86 Power y el XLSX original. Nunca se sustituye por las fixtures sintéticas.
- Room v1 sigue bloqueado por falta de esquema histórico auténtico. No se inventó, copió ni cambió fixture/test.
- No se midieron inferencia real, TTFT, tokens/s, RAM/PSS, energía, temperatura ni calidad en el teléfono. Los seis tests de modelo en emulator siguen `SKIPPED`.
- H2 no iniciado. La fuente de plan sigue intacta: 97 tareas/IDs, 120 dependencias; todos los estados fuente históricos permanecen TODO. `EXECUTION_STATE.json` no se modificó bajo el contrato vigente.


## Historical checkpoints (preserved)
---

# Current master-program checkpoint — H0 host stabilization (2026-10-20)

Code SHA 49f5913937fe7c049bf0ad71d2d75f7e5321b557 contains the H0 test/runtime fixes: schema version expectation 10, two-page DOCX assertions, immediate dismissal of stale removed-file details with a retry-outbox UI regression, and an authentic exported-schema v8→v10 migration test. Historical Room v1 remains unverified because its authentic schema is unavailable; the Android migration test compiled but was not instrumented.

The forced HOST JVM gate passed 916/916 app plus 2/2 LiteRT compatibility tests. ARM64-scoped lint passed with 0 errors, 77 warnings and 1 informational item; AndroidTest Kotlin compilation and Debug ARM64 assembly passed. No emulator, Motorola or inference/model performance test ran. Full commands, XML, compressed logs, APK validation and checksums are in [H0 stabilization evidence](../validation/master-program-h0-stabilization-20261020/RESULTS.md).

A local 0.5.0-debug/27 ARM64 APK passed archive, alignment, signature and package checks, but its certificate 4d95e3... differs from the prior update certificate f9085e.... The compatible private key is unavailable. The APK was not uploaded or installed; do not uninstall or clear app data to use it.

The canonical source plan remains 97 unique IDs and 120 dependencies, all 97 statuses TODO; source files were not modified. M01-06 remains PENDING_REQUIRES_DEVICE and PHYSICAL_DEVICE=NOT_RUN. The user-provided GUIA_VISUAL_AWS.md is design guidance (SHA-256 9cbc5b998887481ffee9c42eaf5b900776d5e2152b9a75e9b0891f485e34966e), not brand-image reference material.

Continue the authorized program with an inspected, test-first M02-01 synthetic corpus preparation if current fixtures leave a concrete gap. M02-01 stays TODO because its M00-10 dependency remains device-gated. No original task is closed by H0.

---

# Current master-program checkpoint — direct EG2 scoring from stored vectors (2026-10-19)

Code commit aabca8226634de6da08006dc255b0b5727360cdd replaces the SemanticLayer corpus path's decode-to-FloatArray plus second scoring pass with a single pass over persisted FLOAT32_LE_V1 bytes. This avoids one decoded vector array per corpus row (the existing search bound is 10,000 rows). Space identity, dimensions, malformed vectors, non-finite values, project/selected-document scope, FTS fusion and result limits remain checked. No schema, dependency, runtime or model changed.

The exact-SHA focused HOST run passed 63/63: SemanticDomainTest 24/24 and SemanticContextPostfixTest 39/39, zero failures/errors/skips, Gradle exit 0 in 1m12s. The numeric unit check matches the existing decoded score exactly on a 768-element synthetic vector; the context integration tests use the real SemanticLayer path with deterministic test embeddings. This demonstrates source-level allocation removal, not measured latency, heap usage, native model speed or Motorola behavior. Logs, exact command, XML, environment and manifest are in [direct EG2 vector scoring evidence](../validation/master-program-semantic-vector-direct-cosine-20261019/source-aabca82/RESULTS.md).

Published readback: direct-scoring code aabca8226634de6da08006dc255b0b5727360cdd and its evidence are on codex/aws-master-resume-20261019; remote HEAD bf708b7e2136bd9f5ea8a129ca09fd5e790c5664 was read back. main and peeled v0.4.2 remain de615b772cf37a99e7e08a647c59607b271de606; feature remains e6f16ac30914fe32f7140241e81e5976660c03d2.\n\nThis is preparatory work only. M00-06 remains source TODO because the end-to-end import/parse/index/retrieval/context/gate/native-TTFT/decode timeline and physical device measurements are incomplete. The preserved backlog remains 97 IDs, 120 dependency edges and all source statuses TODO. No full suite, lint, assemble, AndroidTest, emulator, Moto G86 or APK was run for this increment.

---

# Current master-program checkpoint — batched semantic memory vectors (2026-10-19)

Code commit `c0545e8c40a7f8a394314e30d583260197eba1df` fixes an observed N+1 Room read in `MemoryManager.select()`: the test-first red fixture measured 25 `context_vectors` SELECTs for 25 candidates; the implementation now batches IDs in groups of 400 (one query for 25, two for 401). Stale content hashes are rejected before semantic scoring, and existing scope, ranking, result-limit and maximum-eight inline indexing behavior is retained.

At this exact code SHA, the focused HOST command covering `MemoryRelevanceTest` and `MemoryBundleImportTest` passed 25/25 (0 failures/errors/skips), Gradle exit 0 in 43s. It used Gradle 8.10.2 offline, Temurin 17 as Gradle runtime, Temurin 21 as test worker, existing SDK API 35/NDK 27.0.12077973 and existing `/workspace/.gradle` cache. Logs, command, environment, XML, parsed count summary and checksums are in [`M00-06 memory-vector batching evidence`](../validation/master-program-memory-vector-batch-20261019/source-c0545e8/RESULTS.md). The measured improvement is SQL operation count only; no phone latency, TTFT, tokens/s, memory, energy or answer-quality result is claimed.

This preparatory optimization does **not** complete original M00-06: the full import/parse/index/memory/retrieval/context/model timeline and real-model/device metrics remain absent. The original backlog/roadmap are byte-identical: 97 IDs, 120 dependency edges and all source statuses `TODO`. The separate emulator suite remains historically blocked by the unavailable authentic Room v1 fixture with six model-dependent tests skipped; that was not run or changed here. `EMULATOR=NOT_RUN` for this increment and `PHYSICAL_DEVICE=NOT_RUN`; no APK was built.

Published readback: source commit c0545e8c40a7f8a394314e30d583260197eba1df and evidence commit 801fd1a2e01804b2457588e8bc81904733ee46fb are on codex/aws-master-resume-20261019; the remote ref readback matched 801fd1a. Main and peeled v0.4.2 stayed at de615b772cf37a99e7e08a647c59607b271de606; the original feature ref stayed at e6f16ac30914fe32f7140241e81e5976660c03d2.

The red test was captured before implementation while the regression test was uncommitted, against production baseline `2d1deafe491638543d36d6351e98307ef7bdd423`; its working-tree tree SHA was not captured. An earlier fixture assertion incorrectly expected 25 selected rows despite the existing result cap of six and is not counted as a product failure.

Continue the authorized master program sequentially with another concrete, source-backed implementation; do not close source tasks from partial evidence, repeat unrelated audit-only work, or claim Motorola/model performance without the actual device and model.

---

# Current checkpoint — approximate preview of editable PPTX figures (2026-10-19)

The current continuation is on `codex/aws-master-resume-20261019`. Before this increment, the tracked/untracked work state was preserved outside the repository at `/workspace/backup/aws-pre-integral-20261019-223922/`; its restore ref is `refs/backup/aws-pre-integral-20261019-223922`, and the patch/archive hashes are recorded in that backup's `restore-record.txt` and `SHA256SUMS`. The local red-test commit `79301a0` remains in history; implementation commit `67b66e0c287889a231d6497207f5883f611d29b9` adds the separate approximate placement canvas, and `45e4a79b32020b7cca5aa781e08c79d42f3b9f6d` corrects the Compose test API usage. No reset or checkout discard was used.

The final focused test at exact code SHA `45e4a79b32020b7cca5aa781e08c79d42f3b9f6d` passed `PptxImagePreviewUiTest.savedPptxShowsAccessibleSlideLayoutAndImageAssetWithoutChangingRevision` 1/1 (0 failures/errors/skips), Gradle exit 0 in 1m31s. It checks descriptions/labels for all four shape kinds, normalized rectangle bounds, continued slide/image preview, and that preview does not change artifact bytes or revision history. One earlier run failed in test compilation because `fetchSemanticsNode` was imported incorrectly; the public interaction member corrected that test-only error. The original red run found no figure canvas, but its exact working-tree SHA was not captured; do not claim a commit-at-run for that attempt. Full commands, timestamps, JUnit XML, raw logs and checksums are in [`PPTX figure preview follow-up`](../validation/master-program-pptx-preview-shapes-20261019/RESULTS.md).

This is an approximate Compose schematic, not a rendered PowerPoint page. Office/LibreOffice rendering, full host gates, lint, APK, emulator and Motorola were not run. Original M03-05 remains source `TODO`, `requires_device=true`, dependent on M03-01 `TODO`; no task or dependency is closed. The original catalog remains 97 unique IDs/120 dependency edges/all `TODO`; M01-06 remains `PENDING_REQUIRES_DEVICE`, `PHYSICAL_DEVICE=NOT_RUN`. Continue with a distinct source-backed preparatory M01/M02/M03 gap; do not repeat this preview increment.

# Current checkpoint — M03-03 DOCX ordered-list generation preparation (2026-10-19)

Production code `d1bd2062c0638ac3edd44d243d4783a578f2d00e` adds real decimal numbering to generated DOCX paragraphs. A `numbered` style is accepted in DOCX block and legacy descriptors only, rendered with OOXML numbering instances, shown in the preview and documented to the artifact tool. Consecutive items continue, and a non-list paragraph/table starts a new decimal run. Existing bullet output remains intact; PDF rejects the DOCX-only style.

The exact tested tree `356db30a9982dec804060ea5492d998c2de5d9f7` passed the focused `DocxArtifactStructureTest` class 4/4, zero failures/errors/skips; Gradle exit 0 with 63/63 actionable tasks executed. A separate `python-docx 1.2.0` read of the generated synthetic DOCX confirmed ZIP CRC, required package parts, paragraph order and numbering IDs. The expected-red run had failed because the descriptor rejected `numbered`. One intervening attempt failed while compiling a DOM test assertion; it ran no tests and generated no fresh XML, and the assertion type was corrected without changing production.

Evidence, timestamps, commands, preflight, lossless compressed Gradle logs, JUnit XML, test output and checksums are in [`M03-03 ordered DOCX list preparation`](../validation/master-program-docx-numbered-lists-20261019/RESULTS.md). This is preparatory only: original M03-03 remains `TODO`, `requires_device=true`, and depends on M03-01 and M02-03, both `TODO`. Full app/LiteRT suites, lint, assembly, AndroidTest, emulator, physical-device and APK were `NOT_RUN`.

The original backlog remains byte-identical with 97 unique IDs and 120 dependencies; all 97 source statuses remain TODO. M01-06 stays `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. Continue sequentially with bounded, source-backed work and preserve task dependencies.

# Current recovery checkpoint — DOCX list extraction preparation (2026-10-19)

The exact focused HOST run at test/code tree `f88c756fa78f0f6d31a538ef16c470bd39c033fa` passed `DocxStructureFidelityTest` 5/5 (0 failures/errors/skips), Gradle 8.10.2 exit 0, 63/63 actionable tasks executed. A test-first red run at `a351f5f` reproduced that the production parser returned list items as plain paragraphs without list markers. Production code at `dadcb46` resolves common explicit DOCX `numPr`/`numbering.xml` lists, including bullet/nesting, decimal and tested alphabetic start overrides; unknown explicit numbering remains visibly unresolved and item text is retained. See [`M02-03 DOCX list evidence`](../validation/master-program-docx-list-markers-20261019/RESULTS.md) and the recorded commands, logs, XML, preflight and checksums.

This is preparatory only: source M02-03 remains `TODO`, depends on M02-01 (`TODO`), and does not close the full heading/table/page/location fidelity DoD. M02-01 is itself device-gated. The original plan stays at 97 IDs/120 dependencies/all source statuses TODO. No full suite, LiteRT, lint, AndroidTest, emulator, physical device or APK ran. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 stays `PENDING_REQUIRES_DEVICE`. `M03-07` also remains TODO/device-gated with M03-01 still TODO. The lost local commit `2f8b0d4` remains unrecovered; no code was reconstructed from it.

The recovery checkpoint remains on `codex/master-program-recovery-20261019`; `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. Continue sequentially with one source-backed preparatory implementation; do not promote a task in the source catalog from this partial evidence.

---

# Current master-program recovery checkpoint — M00-06 context composition (2026-10-19)

The user-supplied recovery prompt referenced feature HEAD `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`; the actual fetched feature was `e6f16ac30914fe32f7140241e81e5976660c03d2`. The exact fetched commit is preserved by `recovery/backup-e6f16ac-20261019`; implementation continues on the independent `codex/master-program-recovery-20261019` branch. The reported lost local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was not recovered and no source was reconstructed. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

At exact current source `e6f16ac`, `ArtifactRevisionPreviewUiTest` passed 2/2 on HOST. M03-07 remains source TODO, device-required, and dependent on M03-01 TODO. The native-table formula-edit follow-up is test-only: the initial test assertion expected `null`, while the current reader returns an empty string for a formula without `<v>`; the corrected test passed 1/1 at `36fa81fa0cfc4b6f5f9136d86f98e54951110b4a`, and `openpyxl 3.1.5` verified a synthetic XLSX table, formula, missing stale cache and ZIP CRC. M03-02 remains TODO, device-required, and depends on M03-01/M02-02 TODO. Evidence is in [`current recovery/M03-07 results`](../validation/master-program-recovery-20261019/current-head-e6f16ac/RESULTS.md) and [`M03-02 formula/table follow-up`](../validation/master-program-m03-02-xlsx-tables/cell-edit-follow-up-20261019/RESULTS.md).

## M00-06 context composition follow-up

Code commit `22014ed8a452836b7fd20de6d1f54db0a2a12f3c` records estimator-specific context composition on the normal-generation trace: estimated/available units, included/omitted counts, counts by kind/reason and estimator identifier. The trace contains no prompt, response, excerpt, filenames, paths, or project/document/evidence identifiers. The Performance screen labels these values as estimator-specific rather than native model token counts; old persisted traces without this optional field still load.

Test-first evidence: attempt 01 reproduced the missing field and failed 1/1; its JUnit XML was overwritten before archival, so only the raw log remains. Attempt 02 was an invalid test assumption: an empty context was incorrectly treated as failure; zero context is valid. The corrected exact-commit command used Gradle 8.10.2, JDK 17 runtime/JDK 21 worker, API 35, existing offline cache and `--rerun-tasks`. Gradle exited 0 in 201 seconds; all 63 actionable tasks executed and the three fresh XML suites total 10/10 PASS (ChatSessionPipeline 1, GenerationTimelinePresentation 4, NormalGenerationTrace 5). Exact logs, XML and the 38-entry checksum manifest are in [`M00-06 context composition results`](../validation/master-program-context-composition-22014ed/RESULTS.md), manifest SHA-256 `1e2d8d676b2d5e309a4862ca65c127d5e63e47ebeaf8c69163a1a5c584ad161b` (38/38 locally verified). A preceding exact-SHA invocation was `UP-TO-DATE` and ran zero tests; it is explicitly not counted.

This is only a preparatory M00-06 slice. Import/index and a later chat remain separately measured operations, joined only where existing opaque IDs match. The original cross-stage end-to-end timeline and actual model/device TTFT, decode, memory, quality, energy and thermal measurements remain unproven. M00-06 stays source TODO. Full app/LiteRT tests, lint, assembly, AndroidTest compilation, emulator, Motorola and APK were not run in this increment.

The original plan files remain byte-identical: 97 unique IDs, 120 dependencies, all source statuses TODO. M01-06 remains PENDING_REQUIRES_DEVICE and PHYSICAL_DEVICE remains NOT_RUN. Continue autonomously with one source-backed preparatory increment allowed by the master prompt; retain exact dependency constraints and do not promote an original task from partial evidence.

---

# Current master-program checkpoint — M03-02 native XLSX tables (2026-10-19)

A verified pre-documentation snapshot was created at `/workspace/backups/aws-pre-integral-20261019T181355Z/`. `repository.bundle` verifies with `git bundle verify`; its SHA-256 is `f0002619ff0297980df8b7b7467b132f76c51119fe4a5c241082c3da95ae20d4`. It captures the already-implemented, tracked-clean code SHA `c991a4d6fd0240045a31515e5f30fec0309c37ac` before this evidence update; it is not a pre-code baseline. The older backup path cited in prior notes was not mounted in this executor. Generated/ignored caches were not copied or touched.

The M03-02 native-table gap was reproduced test-first: at `0ffedf9f60560cda7db7503967034d2ab7d771e9`, `xlsxDescriptorAcceptsBoundedStructuredTableSpec` failed 1/1 because the artifact descriptor rejected the `tables` field. Code at `c991a4d6fd0240045a31515e5f30fec0309c37ac` adds bounded native OOXML tables to app-generated XLSX files, including the table part, content-type entry, worksheet relationship and tableParts reference; add/resize creates immutable app-private revisions. The exact focused HOST command passed 16/16 (0 failures/errors/skips) across `XlsxArtifactRevisionTest` (10), `Part2ThinkingTest` (3), and `ChatToolSchemasTest` (3), Gradle exit 0 in 67 seconds. `openpyxl 3.1.5` independently reopened both synthetic workbooks and validated ZIP CRC, table name/range/headers, and the resized fixture's formula `=1+2` plus cached value `3`. Details, raw compressed logs, fresh XML, commands, reader script/output, two synthetic workbooks and a 19-entry checksum manifest are in [`M03-02 native XLSX table results`](../validation/master-program-m03-02-xlsx-tables/RESULTS.md); manifest SHA-256 `8da8235cc32d3163fb349a8751d8c2bfd2632ba6021f697fc183f87527331e38` (19/19 files verified).

This is a bounded preparatory subset, not M03-02 completion. The source task remains `TODO`, `requires_device=true`, and depends on M03-01 and M02-02, both `TODO`. This code edits only app-generated private artifact revisions; it does not edit imported XLSX files or calculate formulas. No full JVM/LiteRT suite, lint, assemble, AndroidTest compilation, emulator, Motorola or APK ran for this increment. Excel/LibreOffice GUI behavior and SAF interaction are unverified. The canonical backlog/roadmap remain byte-identical: 97 unique IDs, 120 dependency edges, and all 97 source statuses `TODO`; M01-06 remains `PENDING_REQUIRES_DEVICE`, and PHYSICAL_DEVICE remains `NOT_RUN`.

# Previous checkpoint — M03-01 format-aware SAF export (2026-10-19)

At code SHA `0551915039efdd17557271a793163290f30be8d2`, a focused test-first check found that the existing SAF export chooser always advertised `application/octet-stream` even though each `ArtifactFormat` declares its own MIME. The red `ArtifactSafExportUiTest` failed 1/1 on the exact assertion (`text/plain` expected, generic MIME received). A format-aware `ArtifactCreateDocumentContract` now sends the selected artifact's MIME and suggested filename to `ACTION_CREATE_DOCUMENT`; it keeps the same explicit user choice and byte/hash readback path. The exact-source focused class passed 2/2, 0 failures/errors/skips, Gradle exit 0 in 201 seconds (63 actionable tasks executed). The second test covers all five current artifact formats; the integration test confirms the CODE export still writes exact bytes and reads back the immutable SHA-256. Evidence, raw/log payload hashes, commands, timestamps and XML are in [`M03-01 SAF MIME results`](../validation/master-program-artifact-export-mime/RESULTS.md); 25/25 committed payloads verify against the child `SHA256SUMS` (manifest SHA-256 `2639758d52129b2b424b03e61d5e47d46f32bdd8093377eb8a5494ff8d4538b5`).

An initial wrapper attempt did not start Gradle because the default Gradle user home lacked the pinned distribution and network access returned `Connection refused`; it is not a test result. The red and green runs used the existing `/workspace/.gradle` cache offline, with JDK 17 Gradle runtime/JDK 21 test worker, SDK API 35 and NDK 27.0.12077973. This increment did not run full JVM/LiteRT, lint, AndroidTest compilation, assembly, emulator, Moto, or APK. No compatible prior Debug key was found in the standard checked locations; no replacement key was generated.

This is preparatory only: source M03-01 remains `TODO` because M02-05 is still `TODO`; the source backlog remains byte-identical at 97 IDs/120 edges/all 97 source statuses `TODO`. No task/dependency was promoted, no Room or inference behavior changed, and `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. The next action is another bounded inspection of M03-01's original Artifact API/persistence DoD for a concrete host-testable invariant. Do not represent this MIME fix as full export, device, or M03-01 closure.

---

# Previous checkpoint — recovery continuation, cell citations, and M03-07 revalidation (2026-10-19)

The recovery prompt named feature head `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`; an explicit fetch showed that the feature and codex remote branches had already advanced to `d25f05997c0487bbcbad964f68acb34a175e2313`. The preserved refs `recovery/backup-b8e75b4` and `origin/recovery/backup-b8e75b4` still resolve to exact `b8e75b4`. The existing isolated implementation branch `codex/master-program-20261019` was reused. Lost local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was unavailable in the current object database; no source was reconstructed. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

At code SHA `6abd9eee7689f08b3cadbceb97cbfdcb5358064c`, a preparatory M02-05 gap is closed: resolved sheet-qualified cell references now flow from EG2 and lexical ContextFoundation into `Evidence`, an additive Room 9→10 citation metadata column, and the existing citation card/detail/source preview. The codec reuses Gson and is bounded (64 references, 256 chars each, 8 KiB JSON); corrupt locator data remains explicitly unavailable while the excerpt stays visible. Focused tests pass **12/12** (0 failures/errors/skips, exit 0, 65s); the TDD baseline at `1924c28` failed compilation on the absent locator property. The first implementation run’s 2/12 Android `org.json` stub failures are retained; the codec was corrected to use existing Gson and the final run passed. This is preparatory partial work: source M02-05 remains TODO because M02-01 and M01-05 are still formally TODO; M02-01 depends on device-gated M00-10.

At the same exact code SHA, `ArtifactRevisionPreviewUiTest` passed **2/2** (exit 0, 34s) for the surviving M03-07 UI. M03-07 remains TODO, requires a device, and depends on M03-01 TODO. The source task and dependency catalog were not edited.

Validation limits on this increment: AndroidTest compilation first stopped in offline mode because declared AndroidX/Room test dependencies were uncached; the single online attempt ended after 8m43 at `:app:processDebugAndroidTestManifest` with Google Maven/Maven Central `Network is unreachable`, before Kotlin compilation, with zero test XML. Correctly configured offline lint stopped before `:app:lintDebug` because `com.android.tools.lint:lint-gradle:31.7.3` was not cached. The first lint wrapper call also failed before Gradle because its user home lacked the wrapper distribution. No full JVM/LiteRT gate, assemble, emulator, physical device or APK was run. No debug keystore was present in the standard executor locations checked, and no replacement was created. The historical emulator result remains separate; `PHYSICAL_DEVICE=NOT_RUN`, and M01-06 remains `PENDING_REQUIRES_DEVICE`. Full commands, raw logs, thread dumps, XML, counts and hashes are under [`cell-citation results`](../validation/master-program-cell-citations/RESULTS.md).

The archived backlog/roadmap remain byte-identical: 97 unique IDs, 120 dependency edges, all 97 source statuses TODO. Continue with one preparatory audit of the authentic M03-01 Artifact API/persistence DoD and its remaining host-testable invariants; M03-01 remains formally open while M02-05 is TODO. Do not redo M03-07 or claim a source task complete.

---

# Master program — current recovery checkpoint (2026-10-19)

Current isolated work branch: `codex/master-program-20261019`; latest tested code SHA `9f99beba0d38787a62e83bbfa457cc39b9e9d0ab`. The latest M00-06 increment changes performance presentation/metadata and focused tests only; no database, dependency, workflow, model, permission, retrieval-semantic, or inference-runtime behavior changed. The original backlog and roadmap files remain byte-identical: 97 unique task IDs, 120 dependency edges, all 97 source statuses `TODO`.

- **M00-04:** focused synthetic HOST checks pass 2/2 at `bbcd551`. The earlier 1/2 attempt at `617082e` failed because the test queried title and detail as one Compose node; the correction is test-only. No product failure was shown.
- **M00-05:** host Compose test passes 1/1 at `78b5309`, across compact widths and font scales, with Robolectric shadow-IME visibility and four captures. A shadow IME/inset is not physical keyboard or Moto evidence.
- **M00-06:** synthetic XLSX import/parse/chunk/commit and EG1 indexing coverage passes 1/1 at `efab487`; the normal-chat checkpoint test passed 1/1 at `98665c0`. At the latest code SHA `9f99beb`, a focused 10/10 HOST run verifies exact opaque-ID correlation of chat trace/runtime metrics and import/index timeline stages, including rejection of stale index IDs. Runtime metrics are shown only when actually present; fake-backend native TTFT/decode remain absent. Import/index and later chat remain separate operations, so this does not fabricate one causal document-to-neural timeline. M00-06 remains source `TODO`; native/device performance is not claimed. Full command, XML and log: `docs/validation/master-program-recovery-20261019/m00-06-correlated-timeline-9f99beb/`; its child SHA256SUMS has 17 entries and digest `220e348700e00c1027873788b157eb21b9a7133c8de2351c271c716a63c821ed`.
- **M02-08:** the existing code-archive implementation passes four focused HOST tests at `efab487` for EG2 path/line citation, lexical fallback, `.gitignore` exclusions, and project isolation. No redundant production change was made. M02-08 stays source `TODO`; M02-05 dependency remains `TODO`.
- **M03-07:** existing preview/revision tests pass 4/4 at `bbcd551`, host only. It remains source `TODO`, requires device, and depends on M03-01 `TODO`.
- **M03-07 current-source recheck:** `ArtifactRevisionPreviewUiTest` passed 2/2 on source `bceb60ade0e6e3697d4f9a7d4d731869d9c8978a` (Gradle exit 0, 212 s). This is focused HOST evidence only; source task remains `TODO`, `requires_device=true`, and depends on M03-01 `TODO`. Full log/XML/commands and checksums are in `docs/validation/master-program-recovery-20261019/m03-07-current-sha-bceb/`.

No full JVM/LiteRT suite, lint, assembly, AndroidTest compilation, emulator, Motorola, or APK was run for code SHA `9f99beb`. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. M00-06 is still source `TODO`: host presentation/correlation coverage improved, but the original complete timeline and actual native/device measurements are not demonstrated. M00-07 requires the Moto G86 Power. Continue only authorized, source-grounded preparation and preserve all dependency states. Do not alter the plan source or mark any original ID complete from these focused checks.

Exact commands, environment, run times, complete gzip logs, listener events, task outcomes, JUnit XML, captures and checksums: [master-program recovery results](../validation/master-program-recovery-20261019/RESULTS.md).

---

# Current checkpoint — M03-03 ordered DOCX content blocks (2026-10-19)

The code is on `codex/master-program-20261019`; tested source SHA `463819926bf61523c59cab96ccc083513e19d8f4`. The red regression at `a0a71455f9bdf1305097dd0cb987e0b3b92fb0f5` demonstrated that a generated document could not represent paragraph → table → paragraph: `ArtifactSpec.parse` rejected the ordered `blocks` field with `ARTIFACT_FIELD_UNSUPPORTED`. Production commit `d5d49ba9fba4cfaf9869c8118142bc3abe213ad3` adds a bounded ordered-block descriptor and renders paragraphs/tables in sequence; the old `paragraphs`/`tables` descriptor remains supported. The editor and project-tool description now use the ordered DOCX form. Test commit `4638199` checks ordered preview/OOXML content and two immutable revisions. The red log is retained, but its JUnit XML was overwritten by the final green run and is not available.

At exact source SHA `4638199`, `DocxArtifactStructureTest` passed **2/2**, 0 failures/errors/skips, Gradle exit 0, 46s wall; XML and full log are in [`M03-03 ordered DOCX evidence`](../validation/master-program-docx-ordered-blocks/RESULTS.md). `python-docx 1.2.0` independently opened both synthetic v1/v2 DOCX files; ZIP CRC passes and direct body order is `p, tbl, p, sectPr`; the revised v2 changes its final paragraph while v1 bytes/hash remain unchanged.

The proportional Gradle command `:app:lintDebug :app:compileDebugAndroidTestKotlin` exited 1 after 12m13s in offline mode because `:llama-runtime:extractDebugAnnotations` could not resolve uncached `com.android.tools.lint:lint-gradle:31.7.3`; CMake/Ninja release builds for arm64-v8a and x86_64 finished, but `:app:lintDebug` was not reached. A separate `:app:compileDebugAndroidTestKotlin` attempt exited 1 in 23s while resolving AndroidX/Roombased Android test dependencies at `:app:processDebugAndroidTestManifest`; Kotlin compilation was not reached. No network retry or package install was made. Both complete logs/commands are in the evidence directory. This is an offline-cache blocker, not a product pass or demonstrated product failure.

M03-03 remains source `TODO`, `requires_device=true`, with M03-01 and M02-03 dependencies still `TODO`. The original 97 task IDs, 120 edges/order and all source statuses are unchanged. This tests synthetic generated DOCX only; imported DOCX preservation, Android SAF/Office viewer behavior, emulator and Motorola remain unverified. No full JVM/LiteRT suite or APK was run. M03-04 PDF already has separate preparatory evidence; the cross-format test above confirms its layout path on the current code. The next uncovered M03-03 DoD aspect is a bounded, deterministic built-in DOCX template selector. Do not alter source task states.

A shared-renderer regression on the same product code, recorded by documentation commit `1f5ff6c552c5c19c322fbc4c5f324a37d366f672`, reran `PdfArtifactLayoutTest` and passed 1/1 (32s; PDF production code unchanged). XML/log are in the same evidence package. This confirms the DOCX change did not regress the existing PDF word-wrap/page/revision behavior.

Evidence manifest: `docs/validation/master-program-docx-ordered-blocks/SHA256SUMS` (22 entries, SHA-256 `6d5eae812f10de5d8bce3bc45302480024b56c1121b4799f34aea790534d9a98`).

---

## Previous checkpoint — M03-02 cached formula result-type coverage (2026-10-19)

Current source/test SHA: `b08d438b8bfe2b1b7b17e16a3141998a41760df3`; this branch adds a focused regression test only. A proposed serialization defect was checked and **not reproduced**: production `ArtifactRenderer.xlsx` already emits numeric formula caches as OOXML `n`, boolean caches as `b`, and string caches as `str`. The new filtered test passed 1/1, zero failures/errors/skips, captured Gradle exit 0, 3m30s; source/log/XML/task outcome and hashes are in [`M03-02 cache-type results`](../validation/master-program-xlsx-cache-types/RESULTS.md). A first wrapper attempt did not start Gradle because the default `/home/agent/.gradle` lacked Gradle 8.10.2 and the network refused the distribution request; the successful run used the existing `/workspace/.gradle` cache offline. The independent openpyxl control demonstrates why the type distinction matters, but this run did not independently reopen its emitted workbook with openpyxl.

No app production file changed. No full JVM/LiteRT gate, lint, AndroidTest, emulator, Motorola, APK, or Office application test was run. M03-02 remains source `TODO`, requires device, and depends on M03-01 and M02-02; both dependencies remain TODO. The original plan is unchanged: 97 IDs, 120 edges, all source statuses TODO. `main` and `v0.4.2` were not targeted. Next: inspect M03-03 against its authentic source DoD and surviving DOCX code, then continue only with a concrete bounded gap.

---

# Current continuation checkpoint — workspace recovery and ArtifactStore staging (2026-10-19)

The current checkout exists at `/workspace/local-ai-workspace` on the persistent implementation branch `codex/master-program-20261019`; it was fetched and inspected without a destructive reset. The exact feature baseline `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc` is preserved by the already-published and read-back `recovery/backup-b8e75b4` ref. The implementation branch's remote checkpoint before this follow-up was `1c252cce243b33e541f26755f35f51d0df806466`. No additional branch was needed.

The reported local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was not recovered: it is absent from local refs/reflogs/object lookup and no unreachable commit/tag or matching patch/diff/backup filename was found in the bounded `/workspace` search. No lost source was reconstructed. Existing M03-07 code/evidence already survives in this branch: the latest bounded line-diff UI class passed 2/2 on `fad7acb`; its lint invocation failed before `:app:lintDebug` because the pinned KleidiAI source download returned proxy HTTP/2 503. Do not report lint PASS or M03-07 completion. It remains source TODO, device-required, and depends on M03-01 TODO. See [`M03-07 recovery/results`](../validation/master-program-m03-07/RESULTS.md).

The current M03-01 focused follow-up is source-preparatory only. Baseline test commit `c87228c3fd7d076f5116bed92c09efd2f468dfcc` reproduced stale crash-leftover `.pending-UUID` storage (1 expected failure). Production commit `26ff528e02be5e9cdcc17ebb776232a78556dae6` cleans only matching app-owned staging directories while holding the project write lock; test commit `3015ebec721bb6f66a6d19cc57947ae442d37ed4` also proves `list()` preserves a live writer's staging until lock release. The final focused class at exact SHA `3015ebec721bb6f66a6d19cc57947ae442d37ed4` has fresh XML **3/3 PASS**, zero failures/errors/skips; captured Gradle exit marker is 0 and the log says `BUILD SUCCESSFUL` in 40s (3 tasks executed/60 up-to-date). The outer `write_stdin` session reported exit 1; that discrepancy is explicitly unresolved and is not hidden. Logs, commands, timestamps, XML, environment, task outcomes and hashes are in [`M03-01 staging recovery evidence`](../validation/master-program-artifact-recovery/RESULTS.md).

JDK 17 ran Gradle 8.10.2; JDK 21 was available for test-worker toolchain discovery; existing API 35 SDK/NDK 27.0.12077973 and `/workspace/.gradle` cache were used offline. No full JVM/LiteRT gate, lint, APK, AndroidTest, emulator or device test was run for this increment. The catalog remains the preserved original 97 unique IDs/120 dependencies, all source statuses TODO; M03-01 remains TODO due M02-05, M03-07 remains TODO/device-gated, M01-06 remains PENDING_REQUIRES_DEVICE, and Motorola is NOT_RUN. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

Next: continue one bounded, source-backed preparatory artifact increment on this same branch. Do not recreate lost source, infer task closure, or rerun M03-07 lint without a new cause.

---

## Prior checkpoint — M03-05 approximate PPTX slide schematic (2026-10-19)

The exact code tested is `375e45035f63576b17c1111ce738720373100b99`. It adds a read-only 16:9 Compose schematic from existing slide title/body plus a labeled image region; the validated caller-supplied PNG remains separately visible with accessible alt text and source dimensions. The UI explicitly says this is approximate and not a rendered PowerPoint page; text areas scroll and the exact text preview remains selectable.

The original M03-05 DoD gap was reproduced at `e50f28b1dd0a392deaa777ba22e488bb3424fce4` (focused test 1/1 failure: slide preview node absent). The final exact-SHA `PptxImagePreviewUiTest` at `375e450` passed 1/1, Gradle exit 0 in 69 seconds (7 tasks executed, 56 up-to-date). The path, command, environment, full log, XML and attempt classifications are documented in `../validation/master-program-pptx/layout-preview/RESULTS.md`; its 58-entry evidence manifest is `../validation/master-program-pptx/layout-preview/SHA256SUMS` (SHA-256 `4c33a41952ad2718d3048153e9c7ccf0a4b68dc83f2095e9abd4679d382e1a69`). The enclosing 155-entry PPTX evidence manifest is `../validation/master-program-pptx/SHA256SUMS` (155 entries; SHA-256 `72208e63556f2708a922880e014ecd523518fc52f0f29da59d8d01fb1a9cd97e`).

No full app/LiteRT suite, lint, AndroidTest compilation/execution, emulator, Motorola, APK or performance benchmark was run. The original M03-05 remains `TODO`, requires device validation and depends on M03-01 (`TODO`); this is preparatory HOST evidence only. The 97 source IDs, 120 dependencies and all 97 source TODO statuses are unchanged. The older Room-v1 emulator failure/six model-dependent skips remain historical and separate.

Code commits: `aa777a0`, `48b62cc`, `e50f28b`, `92f0823`, `8648bd4`, `375e450`. The first two harness syntax failures, one expected red, one compile failure, and one image-visibility failure are preserved and not counted as PASS.

---

# Current checkpoint — M03-07 bounded line-diff preparation (2026-10-19)

The active implementation branch is `codex/master-program-20261019`; the exact source/test SHA is `fad7acb0663485e2bd6ec86cf76e36b85a9baf57`. The existing feature baseline is `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`, with recovery backup `recovery/backup-b8e75b4` at the same SHA. This increment adds a bounded added/removed/unchanged line view to the generic revision preview and an explicit oversized-diff fallback that preserves the complete before/after text. Production commit: `bddf6827c873e84708fc686ceed6655807187c12`; final test commit: `fad7acb0663485e2bd6ec86cf76e36b85a9baf57`. Regression test-only commit: `54b37764fecf48681266c061ec2184b5a97b590c`.

The final focused HOST test passed **2/2**, exit 0, 76 s wall; Gradle reported `BUILD SUCCESSFUL`, 7 tasks executed/56 up-to-date. The red baseline was 1/1 expected failure before implementation. A proportional lint invocation on the same source exited 1 after 21 s while `:llama-runtime:configureCMakeRelease[arm64-v8a]` tried to fetch the pinned KleidiAI 1.24.0 source archive and the proxy returned HTTP/2 503; `:app:lintDebug` was not reached. This is an infrastructure block, not a lint PASS or product failure. Commands, complete logs, XML, task outcomes, timestamps, environment, child manifests and hashes are in [M03-07 results](../validation/master-program-m03-07/RESULTS.md).

M03-07 remains source `TODO`, requires a device and depends on M03-01 (`TODO`); this is preparatory HOST evidence, not formal task completion. Full JVM/LiteRT gates, successful lint/build, AndroidTest compilation/execution, emulator, Motorola and APK are not established by this increment. The source catalog remains 97 unique task IDs, 120 dependency edges, all 97 statuses `TODO`; source files are unchanged. M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. `main` and peeled `v0.4.2` remain untouched.

The original M02-01 synthetic corpus checkpoint already exists; it remains source TODO because M00-10 is open. Do not duplicate it or infer completion. Continue with the next bounded preparatory gap only after checking the authentic source graph and existing code/test coverage.

---

# Previous master-program checkpoint — M03-02 XLSX worksheet addition (2026-10-19)

Code commit `0ea514c498af7bbc2b4e0f1326591b3b2173718d` adds the bounded `artifacts.xlsxAddSheet` operation to the existing app-private generated-XLSX path. It creates one empty worksheet in a new immutable revision, preserves existing sheets/cells/formulas/cached values, enforces the existing sheet-name rules and 16-sheet limit, uses the current-version check, and routes the request through the existing LOCAL_WRITE tool capability. The app still does not evaluate formulas; export remains an explicit SAF action; imported workbooks are unchanged.

The regression was red at source `332aa99cec18a54d9c3ea6c17fdab5c807998e62`: 1/1 expected failure because deterministic routing selected no tool for “Agrega una hoja Resumen al libro Excel.” At code `0ea514c`, the same focused test passed 1/1 (0 failure/error/skip), captured Gradle exit 0 and `BUILD SUCCESSFUL`; 7/63 actionable tasks executed in 80 seconds wall. The outer execution tool returned 1, cause `NOT_DETERMINED`. openpyxl 3.1.5 independently opened the generated synthetic workbook, verified ZIP CRC, `Hoja1`/`Hoja2`/`Resumen`, preserved values, formula/cache 4, and empty new sheet. Commands, red/green XML, compressed logs, generated workbook, reader result and checksums: [`M03-02 worksheet-add evidence`](../validation/master-program-m03-02-xlsx-sheets/RESULTS.md).

This remains preparatory only: original M03-02 is `TODO`, `requires_device=true`, and depends on M03-01/M02-02, which remain TODO; M03-01 depends on M02-05. Full app/LiteRT tests, lint, AndroidTest compilation, emulator, Motorola and APK were not run. M01-06 remains `PENDING_REQUIRES_DEVICE`; physical device `NOT_RUN`; the 97 task IDs, 120 dependencies and source TODO statuses are unchanged. `main` and peeled `v0.4.2` remain unchanged.

---

# Previous master-program checkpoint — M03-01 patch-preview Apply follow-up (2026-10-19)

On source SHA `f8a409369d3da0deed58eca71768f6063d2e7757`, the previously unrerun `ArtifactsPatchPreviewUiTest.previewShowsExactBeforeAndAfterAndOnlyAppliesAfterUserAction` case passed: fresh JUnit XML 1/1, no failure/error/skip, testcase 7.801 s. Gradle 8.10.2/JDK 17, with the JDK 21 toolchain exposed, ran `:app:testDebugUnitTest --tests ...` offline with `--rerun-tasks`; 63 tasks executed, Gradle reported `BUILD SUCCESSFUL` in 3m34s, and the captured command exit was 0. The execution tool's outer session returned 1; its cause remains `NOT_DETERMINED`. The complete compressed log, command, exit marker, XML, metadata and hashes are in [`M03-01 apply-only evidence`](../validation/master-program-m03-01/attempt-08-apply-only/).

Together with the earlier isolated discard case (1/1 on `1b82c05`), both patch-preview actions now have focused HOST JUnit evidence. This does not close M03-01: its source status stays `TODO`, depends on M02-05, and the broader source DoD and device/provider behavior remain open. No production code changed in this follow-up. No full JVM/LiteRT gate, lint, AndroidTest compilation, emulator, Motorola or APK ran. The authentic plan is unchanged at 97 IDs, 120 dependencies and all source statuses TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`.

---

# Previous master-program checkpoint — M03-02 formula-edit preparation (2026-10-19)

Code SHA `de7445114ad90c0a11453663971286feb2c5fdef` extends the app-private generated-XLSX `artifacts.xlsxSetCells` operation to accept a bounded formula edit. It creates an immutable revision, preserves the original file/hash and cached result, removes the now-stale cached result from the edited formula cell, and requests recalculation when opened by a compatible spreadsheet reader. The app does not evaluate formulas; explicit formula-edit requests route to the tool, while recalculation-only requests do not. Unsupported external formula input is rejected without a new revision. Imported workbooks are not changed.

The exact-SHA focused HOST run passed `XlsxArtifactRevisionTest` 5/5 (0 failures/errors/skips), Gradle exit 0, 51 seconds wall time. Gradle 8.10.2/JDK 17 runtime, JDK 21 app test launcher, SDK API 35 and existing offline cache are recorded with the command, raw log, fresh XML and hashes in [`M03-02 formula-edit results`](../validation/master-program-m03-02-xlsx-formulas/RESULTS.md). This reused existing Gradle/build outputs; it was not a clean-checkout build or the full JVM/LiteRT/lint/AndroidTest gate. No APK, emulator, or device was run.

Test-only commit `188047a188a9b03a493ebf1a6d26a42c7a81553a` adds opt-in export of two synthetic XLSX versions from the focused test. The class again passed 5/5, and openpyxl 3.1.5 independently read both packages, confirming the old formula/cache on v1 and the edited formula/no stale cache/recalculation flag on v2. The Gradle subprocess exit file is 0 and log/XML pass; the outer unified exec session reported 1, an unresolved wrapper-status discrepancy retained in the evidence. No Excel/LibreOffice GUI or device was tested.

Original M03-02 remains `TODO`, `requires_device=true`, and depends on M03-01 and M02-02; both remain source `TODO`. This is a preparatory formula-edit slice, not full XLSX editor completion and not independent Excel/LibreOffice validation. The original backlog/roadmap remain unchanged at 97 unique task IDs, 120 dependencies and all source statuses `TODO`. `main` and peeled `v0.4.2` were not modified. Next preparatory review: inspect the authentic M03-02 remainder/compatibility needs after this formula edit; keep all formal task states and device gates open.

---

# Historical focused M00-04 HOST attempt — 2026-10-17

The single authorized offline invocation used code `fdd80c0971b5ae1637ad7e616bdb4aa8f5de8700` (tree `8f2a1bafc41f2a32181ef48dc8259efbe7ed7ef5`), JDK 17 for Gradle and JDK 21 for the test worker. It ended in 47 seconds, exit 1: XML reports 2 cases, 0 PASS, 2 FAIL, 0 errors/skips. One failed before its assertion because Robolectric attempted an uncached `android-all-instrumented` fetch and the connection was refused. The Inspector case failed in Room's multi-instance service callback invoked by Robolectric `ShadowInstrumentation` with a null component name during Compose/Espresso idle draining. The relevant assertions are not established as passing; product failure is **not demonstrated**. No `ENOSPC`, hang, thread dump, emulator/device, APK, or full host gate. M00-04 remains source `TODO`, partial and not accepted. Do not rerun the same setup. Raw logs, XML and hashes: [`M00-04 results`](../validation/m00-04-context-omission-20261017/RESULTS.md), manifest SHA-256 `0cf3a6e21cf4c6d9a22563bc77d312f242580a29aa09e4ab3035514c127f278a` (18/18 local entries verified).

# Current continuation checkpoint — request-timing timeline and exact-SHA automation readback (2026-10-17)

Product code commit `fdd80c0971b5ae1637ad7e616bdb4aa8f5de8700` (tree `8f2a1bafc41f2a32181ef48dc8259efbe7ed7ef5`) adds presentation for existing `NormalGenerationTrace` checkpoints: elapsed-from-request, adjacent interval, non-additivity because spans may overlap, and unavailable timings after a monotonic-clock reset. It adds no instrumentation or inference behavior. The focused host test passed 2/2 (exit 0, 252 s); proportional `lintDebug`, `compileDebugAndroidTestKotlin`, and `assembleDebug` passed (exit 0, 636 s; 0 lint errors, 77 warnings, 1 informational finding, no changed-file findings). AndroidTest only compiled; full JVM/LiteRT suites were not run. Full commands, logs, XML and hashes are in [`master-program-observability/RESULTS.md`](../validation/master-program-observability/RESULTS.md).

Exact-SHA emulator run [39000019030](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39000019030), job `117300768724`, booted the API 35 / Android 15 x86_64 AVD, connected `emulator-5554`, then ran `:app:connectedDebugAndroidTest` and exited 1. XML: 16 cases, 9 PASS, 1 FAIL (authentic historical Room v1 schema absent), 6 model-dependent SKIPPED; 10 executed. The Gradle log reports `Starting 16 tests` but later `22/16 completed` and `Finished 22 tests`; cause is `NOT_DETERMINED`. The post-task package check says the app is not installed, also cause `NOT_DETERMINED`. The standard screenshot is valid; the large-font screenshot transfer is incomplete, so that visual capture requirement is not met. Overall `EMULATOR=FAIL`.

The same-SHA official QA run [39000019176](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39000019176) failed closed at its instrumentation prerequisite. APK build/upload were skipped and there is no new APK. The already-built local universal APK remains non-deliverable as an update because its signer differs from the prior personal update certificate. `PHYSICAL_DEVICE=NOT_RUN`; model TTFT/tokens/s/RAM/quality/thermal/energy were not measured. Original `M00-06` stays `TODO`; only a presentation subset was added. The source plan remains 97 IDs/120 dependencies/all `TODO`, unchanged. Full exact-SHA run metadata, XML/logs, artifact digest and discrepancies are in [`RUN_READBACK.json`](../validation/master-program-observability/automatic-ci-39000019030/RUN_READBACK.json).

The 60-entry evidence manifest `../validation/master-program-observability/SHA256SUMS` has SHA-256 `ed7efc22caed870147bc6f5182e80bc9fcee9583e5b1f092003100bb472cf8a0`; local verification passed 60/60. The one authorized M00-04 HOST attempt on 2026-10-17 ran both focused regressions but is blocked by environment: the chat test could not fetch Robolectric `android-all-instrumented` (connection refused), and the Inspector test failed in Room multi-instance service binding through Robolectric `ShadowInstrumentation`. XML is 2 tests / 0 PASS / 2 FAIL; product failure is not demonstrated and M00-04 is not accepted. Do not repeat under unchanged conditions. Do not treat the emulator run as model-performance validation. The earlier M00-06 readback below remains attributed to code `9dc5010` only.

## Previous checkpoint — M00-06 import/index correlation and exact-SHA automation readback (2026-10-17)

Code SHA `9dc50102f47d551d9d3d0298c65106e802d1021c` (tree `daa6c615534b445ef37196fabdde6242cb99f1fa`) adds a privacy-preserving correlation ID between a successful XLSX import callback and its asynchronous EG1 project-index run. The final focused HOST tests passed 2/2; proportional `lintDebug` and AndroidTest Kotlin compilation passed. Full app JVM and LiteRT compatibility suites were not run.

The exact-SHA emulator workflow [38997371699](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38997371699), job `117292766307`, booted API 35/Android 15 x86_64 and connected ADB, then failed during `:app:connectedDebugAndroidTest`. Its single JUnit XML is internally consistent at 7 cases (3 PASS, 2 FAIL, 2 SKIPPED; 5 executed), but it does **not** reconcile with Gradle, which expected 16 and reported 6 received; the runner log has 10 starts/9 finishes and omits two finished cases from XML. The Room failure is the unavailable authentic v1 schema. The second XML failure is an empty `ChatHeaderLayoutTest.largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible` failure element without a failure event/stack; that test file is unchanged from the parent, the screenshots show all three lines, and its cause is `NOT_DETERMINED`, not attributed to this code. Only two real-model skips were observed before the run ended; the remaining four expected model-dependent cases were not reached and are not counted as observed skips. Do not call this run an emulator PASS.

Artifact `12087494744` is 149,528 bytes, SHA-256 `4c03ee8cc624ee2ae905e58743c9cd9c8e95630c06b2db0d99ba86885f65f4fa`; ZIP CRC passed, internal manifest 32/32, expiry 2026-10-24T03:18:36Z. Standard and 1.6-scale emulator screenshots were visually inspected and their hashes are in `../validation/master-program-m00-06/automatic-ci-38997371699/RUN_READBACK.json`; these are not Motorola evidence. Exact-SHA official QA run [38997371673](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38997371673) failed closed on the instrumentation gate, skipped APK build/upload, and produced no artifact. `PHYSICAL_DEVICE=NOT_RUN`; no current-SHA APK exists.

Evidence: `../validation/master-program-m00-06/RESULTS.md`, `RUN_READBACK.json`, and the 112-entry `SHA256SUMS` payload manifest (manifest SHA-256 `9aa2e8cba179599fa8a6ba95f125b7824fce345b41c99ded3487f4523f47ed75`). A second preserved 99-entry Semantic V2 recovery evidence envelope verifies locally with manifest SHA-256 `febfe0788ecfead95508e50ad85a6ad095c2a3bdb6475ab93206d31102ac2e3b`. The M00-06 source entry remains `TODO`; this increment is preparatory only. The canonical backlog remains unchanged at 97 IDs, 120 dependencies and all source statuses TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`. No E2B performance, quality, RAM, thermal or energy measurements were obtained on the Moto G86.

# Current continuation checkpoint — M03-05 PPTX image asset preview follow-up (2026-10-17)

Code commit `b5222c0a388777ee4570fb40290a9e760266efe1` adds a bounded visual preview for caller-supplied PPTX PNG assets and a SHA-256 identity in the textual before/after preview. Alt text, source dimensions, slide number/title and a clear “slide layout is not rendered” notice are visible. Parsing/decoding run off the UI thread, preview bitmaps are downsampled and capped, and previewing does not change bytes or revision history.

Final focused host execution passed 4/4 tests with exit 0. Proportional `lintDebug` passed with 77 warnings, 1 information finding and 0 errors (none in changed files); `compileDebugAndroidTestKotlin` completed successfully but did not run instrumentation. Full JVM/LiteRT, emulator, physical device, APK and performance tests were not run for this follow-up. Commands, logs, XML and hashes are in `../validation/master-program-pptx/preview-follow-up/`; the source task remains partial.

The focused and proportional Gradle invocations started at parent HEAD `998769a954c21db2641694d478e148eec073d736` with the five final source/test changes in the working tree; those files were committed unchanged afterward as `b5222c0a388777ee4570fb40290a9e760266efe1`. No Git tree object was captured at invocation, so the evidence is attributed to the working-tree source change rather than claiming Gradle launched at the code commit. The 40-entry follow-up manifest is SHA-256 `6b97bb107b3168874b7fa95afd6e102c0d7dc3547891c033aaf73bf9b0001b53`; the enclosing 105-entry PPTX evidence manifest is SHA-256 `aea7216b242dcc2e0c8c069d80372ff17aca4fb7a1e34237a510eaad839bb181`.

At the time of that earlier checkpoint, the overall emulator status referred to the prior code. It is superseded by the exact-SHA 9dc5010 readback recorded at the top of this handoff and below; do not apply its prior run counts to the current source. M03-05 remains source `TODO`, `requires_device=true`, depends on M03-01 `TODO`, and neither task is formally complete. The original 97 task IDs, order, 120 dependency edges and all-TODO source baseline are unchanged; M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`.

## Previous checkpoint — bounded PPTX image insertion and automatic CI

Code commit `6231732ad8557465abfc8155925fdcbc90d1eb87` adds bounded embedding of a caller-supplied PNG plus alternative text into generated PPTX slides. It validates canonical Base64, PNG chunks/CRCs, byte and pixel limits, and a sampled bitmap decode; writes a real media part, slide relationship and picture shape; locks aspect ratio; and preserves layout/master/theme. The preview remains textual/structural, not a slide canvas. No image generation, URL/path access, remote fetch, dependency, runtime, Room, or original plan change was made.

The focused `PptxImageArtifactTest` JUnit XML reports 2/2 passing test methods. Its Gradle invocation was interrupted by the user; the exec returned no shell/Gradle exit code or final `BUILD SUCCESSFUL` marker. The invocation began at Git HEAD `58a63b2` with the final production/test changes dirty; those changes were subsequently committed as `6231732`, but a separate tree hash was not captured. Do not report that invocation as a complete Gradle command PASS. A later invocation against `6231732` exited 0 after 21.998 seconds, but every task was `UP-TO-DATE`, including `testDebugUnitTest`; zero tests ran in that invocation. The XML duplicated in its evidence folder is explicitly attributed to attempt 05 and is not counted again.

An independent python-pptx/ZIP reader opened the recorded 5,464-byte package (SHA-256 `794ffc02dd8acfa650bbf660e2fd44b0580bcb6ae384c2463d838cacc04ca4c5`): ZIP CRC valid, one slide, Blank layout, one master, theme present, one embedded 2×1 PNG with alternative text. The reader output is from attempt 04 and applies to the same package hash; it is package-structure evidence, not Android viewer validation. Earlier API 29 roundtrip tests could not start their bodies because Robolectric's `android-all-instrumented` artifact was unavailable offline.

No full JVM/LiteRT gate, lint, AndroidTest-only compilation or APK build was run locally for this increment. The push did trigger the existing connected emulator workflow, which ran and failed on the authentic Room-v1 fixture; the same-SHA official QA gate failed closed before building an APK. No compatible update-signing key was available locally; no replacement was made. M03-05 remains source `TODO`, `requires_device=true`, and depends on M03-01 `TODO`; this is preparatory partial evidence, not task completion. The exact original catalog remains 97 unique IDs/120 dependencies/all 97 `TODO`; M01-06 remains `PENDING_REQUIRES_DEVICE`; PHYSICAL_DEVICE remains `NOT_RUN`.

Evidence, attempts, losslessly compressed partial logs, XML, independent-reader result and the 66-payload SHA256SUMS after the workflow evidence addition (manifest SHA-256 `cb65fa8c4cf2bc84f74ff7b7b950eb0cf81f2e8ec6cd79195f95318c62ea9b99`) are in [M03-05 PPTX image preparation](../validation/master-program-pptx/RESULTS.md). Backup before this program continuation remains at `/workspace/backup/aws-pre-integral-20261017-001224/`; bundle SHA-256 `31618477129bf141091e84cb102a78761a20a5961255e5f39a64da6afa740c5a`, evidence archive SHA-256 `0538e25edfb9d54908fc8e300d2ee1eeff6a591e46b1b94b3349106f5497b242`.

## Automatic connected-test and QA-gate result for M03-05 (2026-10-17)

The code source is `6231732ad8557465abfc8155925fdcbc90d1eb87`; workflow checkout SHA was documentation-only descendant `06bd56e84a4a8dab2a907d136245e68761d75430`. Emulator run [`38988880055`](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38988880055), job `117266559214`, booted API 35 / Android 15 x86_64 and connected ADB. `:app:connectedDebugAndroidTest` executed with exit 1; XML is 16 total, 9 pass, 1 fail, 0 errors, 6 skipped, 10 executed. The sole failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`: the authentic `WorkspaceDatabase/1.json` is unavailable. The six model-dependent cases were skipped; no model was downloaded. The collector's post-task application-package check was false; cause is `NOT_DETERMINED`, and this does not override the XML proof that the task ran. Live Gradle progress printed 22/16, so the coherent JUnit counts are authoritative.

Same-SHA official QA run [`38988880049`](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38988880049), job `117266559052`, failed closed with `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; build/upload were skipped and no APK artifact exists. The exact emulator artifact (151,505 bytes, SHA-256 `cec74c1ae48df16f49e5910b4e882d39653a3dc80a43682f5956b173d2a14013`) and decoded QA gate log are preserved with their checksum at `../validation/master-program-pptx/automatic-ci-38988880055/`. See its `RESULTS.md` and `run-metadata.json`. EMULATOR is FAIL; PHYSICAL_DEVICE is NOT_RUN. M03-05 and M03-01 remain source TODO.

## Current continuation checkpoint — M03-04 PDF word-boundary preparation (2026-10-17)

Code commit `648515e7e69bfeea0c7101c0d8ed4ab01270b634` fixes a demonstrated PDF layout defect in `ArtifactRenderer`: ordinary words were split at the fixed 504 pt line-width boundary. The renderer now wraps at whitespace and only splits an individually oversized token, iterating Unicode code points. The test was first red at `3532cc69e177ece728e0c2e21c72da0e79cc025f` (1 test, 1 expected failure: `WORD_BOUNDARY` became `WORD_BOUNDARY` + `_MARKER`); it then passed at the exact fix SHA (1/1, 0 failures/errors/skips, Gradle exit 0, 3m45s, 63 tasks executed). The focused test also verifies Letter page geometry, text, explicit page break, a table, and immutable revision bytes.

Independent PyMuPDF 1.26.6 and pypdf 6.10.0 opened both generated synthetic PDF revisions. Each has two 612×792 pt pages, 20/16/12 pt title/heading/body sizes, all text bounds within the page, and the v1 boundary marker is a single extracted word. Full commands, raw Gradle logs, red/green XML, synthetic PDFs, reader output, timestamps and hashes are in [M03-04 PDF preparation evidence](../validation/master-program-pdf/RESULTS.md). The 26-entry evidence manifest SHA-256 is `97fd6042f4a0937529be03d28f4101540fc420b6d97ff745723048a3888d96aa`; all 26 payloads verify locally. `PHYSICAL_DEVICE`, emulator for this SHA, full JVM/LiteRT suites, lint, AndroidTest compilation and APK are NOT_RUN.

This remains preparatory evidence: source M03-04 is still `TODO`, `requires_device=true`, and depends on M03-01 (`TODO`). The canonical plan is unchanged at 97 IDs, 120 dependency edges and all 97 source statuses TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`. The separately recorded historical emulator gate remains FAIL for the unavailable authentic Room v1 schema plus six actual-model skips. The previous target `bacb049bdb3732a6354ec48c3899cd8fb5e2a16a` was fetched before publication and is an ancestor of this local work. Next preparatory review: M03-05 PPTX against its original DoD, while preserving its TODO/device/dependency gates.

---

## Current continuation checkpoint — M03-03 DOCX structure preparation (2026-10-16)

Code/test commits `dd7abdf`, `6bdc710`, and `c0bc750` add one focused API 35 test; no production code or dependency changed. At exact source `c0bc7504595c40976d86bf2fe2375f8382b0a94e`, `DocxArtifactStructureTest` passed 1/1 (0 failures/errors/skips), exit 0, Gradle 8.10.2, JDK 17 runtime/JDK 21 worker, SDK API 35/NDK 27.0.12077973, offline existing cache; Gradle took 4m09s and executed 63 tasks. It checks generated package relationships/content types, title/heading styles, list numbering, page-break structure, rectangular table cells, parser output, and immutable revisions. Independent `python-docx 1.2.0` opened both synthetic v1/v2 documents and verified text, styles, numbering, page break, and 2×2 Unicode table. Logs, XML, synthetic DOCX bytes, commands, attempts and checksums: [M03-03 DOCX preparation evidence](../validation/master-program-docx/RESULTS.md); manifest SHA-256 `b54ab6e6ff155b0fb7d2a9b4dc365beeeb0e108fa56a96101afa9db83fe65d04`.

Attempts are retained separately: API 29 bootstrap could not fetch `android-all-instrumented` and did not reach the test body; the first API 35 harness compile failed on a JUnit import/coroutine wrapper; the next test failed only because its expectation put tables on the wrong parsed page. Those were infrastructure/harness issues, not demonstrated renderer defects. The final exact-source test and independent reader pass. M03-03 remains source TODO, `requires_device=true`, dependent on M03-01 and M02-03 (both TODO); no source task is closed. No emulator test for the DOCX SHA, physical device, full JVM/LiteRT gate, lint, AndroidTest-only compile, or APK was run.

The preceding push at `bacb049bdb3732a6354ec48c3899cd8fb5e2a16a` also triggered emulator run [38981700237](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700237): AVD/ADB and the connected task ran; XML was 9 PASS, 1 authentic Room v1 schema FAIL, 6 real-model SKIPPED (16 total, 10 executed). This is EMULATOR FAIL. The official QA workflow [38981700277](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700277) failed closed at its exact-SHA instrumentation gate; build/upload were skipped and no APK was produced. Artifact `12081328312` (151,404 bytes, SHA-256 `ec89be926f36ae81d66a08231f58bc9b8e5bff9f8b93291b57d7bb89bb0d313f`) is available at [workflow artifact](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38981700237/artifacts/12081328312). This prior run predates the DOCX test commits.

The authentic source backlog/roadmap remain byte-identical (97 IDs, 120 dependencies, all source states TODO). M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola `NOT_RUN`. At that earlier checkpoint, M03-04 had not yet been reviewed; the completed preparatory review is recorded above. Do not represent it as device or all-reader validation.

---

## Current continuation checkpoint — M03-02 bounded XLSX editing preparation (2026-10-16)

Code SHA `045047cca7bc9d9cb11897ccd68d3a16fe74568f` adds a deterministic `artifacts.xlsxSetCells` tool for 1–64 explicit literal A1 edits on the app's private generated XLSX drafts. It requires `LOCAL_WRITE`, current-version identity, project scope, and creates an immutable revision whose bytes are verified. Formula cells are rejected by this endpoint; formulas/cached values are not recalculated. Imported originals are not edited. The routing regression was first red (missing tool selection) and then green. The final exact-source HOST run passed the complete focused class, 3/3, 0 failures/errors/skips, exit 0, 271 seconds with `--rerun-tasks`; it compiled app and tests and reports 63 tasks executed. JDK17 runtime/JDK21 worker, API35/NDK27.0.12077973, existing offline Gradle cache. Detailed logs/XML/attempts and hashes: [M03-02 XLSX cell-edit preparation](../validation/master-program-xlsx-cell-edit/RESULTS.md); manifest SHA-256 is recorded in `EXECUTION_STATE.json`.

This is preparatory only. The original backlog still says M03-02 `TODO`, `requires_device=true`, depends on M03-01 and M02-02; both are `TODO` with their own open dependencies. The source plan remains 97 unique IDs/120 edges/all 97 `TODO`, unchanged. No complete JVM/LiteRT gate, lint, APK, AndroidTest, emulator or Motorola test was run for this increment. Formula editing, recalculation, arbitrary imported-workbook fidelity, office-reader and physical validation remain unverified. M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola `NOT_RUN`; the prior global emulator result remains the Room-v1 fixture failure plus six actual-model skips.

Before further continuation, a recovery point was preserved at `/workspace/backups/aws-pre-integral-20261016-231902/`: a Git bundle for the committed state, a tar of then-uncommitted XLSX evidence, restore notes, and SHA-256 checksums. The current `045047c` test-scope commit is a later normal Git commit and remains in local history. Next preparatory review is M03-03 DOCX generation/editor; do not mark its source ID complete or change original dependencies.

---

## Current continuation checkpoint — M03-01 artifact recovery and SAF evidence (2026-10-16)

Code SHA `dfff07ec9965d774af90b146156b45072063e4bf` adds two focused tests only: interrupted immutable CODE-artifact recovery after reopening `ArtifactStore`, and the actual Compose `CreateDocument` callback/content-resolver export with byte and SHA-256 readback. The final combined exact-source HOST run passed 2/2 (0 failures/errors/skips), Gradle 8.10.2, JDK 17 runtime/JDK 21 worker, API 35/NDK 27.0.12077973, offline existing cache, exit 0 in 40 seconds. XML, complete log, commands, attempts, and checksums: [M03-01 focused evidence](../validation/master-program-artifact-recovery/RESULTS.md).

An intermediate SAF assertion failed even though its diagnostic showed the destination had 51 bytes and the UI reported “Exported and read back: answer.kt · v1”; the test had used a Compose assertion inside its polling predicate. The test now waits for exact output byte count, then separately verifies the visible success status and digest. This is a corrected test harness, not a production export fix. One shell attempt exited 127 before Gradle because `ANDROID_HOME` was unset under `set -u`; it ran no tests and is retained as such.

This is preparatory evidence only. Original M03-01 remains source `TODO` because it depends on M02-05, which remains `TODO`; the 97-task source files are unchanged. No production code, Room schema, dependency, or runtime changed. Full JVM/LiteRT gates, lint, APK, AndroidTest compilation, emulator, and Motorola were not run here. The previously recorded global emulator state remains Room-v1 FAIL plus six model-dependent skips; physical validation is `NOT_RUN`; M01-06 stays `PENDING_REQUIRES_DEVICE`. Next authorized preparatory review: inspect M03-02's current XLSX adapter against its original DoD, while retaining `requires_device=true` and source `TODO`.

---

## Current continuation checkpoint — M02-06 stale-index reimport coverage (2026-10-16)

Test-only commit `1433537fd1d46fb5d39622f63a02682f0c55a153` adds one synthetic integration regression for import → index → delete → changed-content reimport → reindex across two project scopes. On the exact code SHA, the focused API 35 HOST run forced task execution and passed 1/1 (0 failures/errors/skips, 8.944 s test body, 3m33s Gradle, exit 0; 63 tasks). It verifies the old generation remains `NEEDS_REINDEX`, stale semantic results are rejected, Context Builder uses current lexical evidence with a notice during the stale window, the replacement publishes a new `READY` generation, and another project's generation/content is unchanged. Full command, JDK/SDK, attempts, XML and compressed logs are in [M02-06 incremental reindex evidence](../validation/master-program-incremental-reindex/RESULTS.md).

An initial filtered command did not discover the test because its Kotlin `@Test` function returned `Boolean`; this was corrected to return `Unit`. A subsequent exact-SHA Gradle run was `UP-TO-DATE` and executed zero tests, so it is not counted. The final `--rerun-tasks` run is the actual exact-SHA test result. No production files, Room schema, workflow, dependency, or runtime changed; the scoped regression was sufficient for this test-only increment. Full JVM/LiteRT gates, lint, APK, AndroidTest compile/run, emulator, and Motorola were not run for this increment.

M02-06 receives preparatory HOST evidence only and remains source `TODO` because it depends on M02-05, still formally `TODO`. The original backlog and roadmap are unchanged: 97 unique IDs, 120 edges, all source statuses `TODO`; canonical hashes remain recorded in `EXECUTION_STATE.json`. EMULATOR remains at the separately documented historical Room-v1 failure plus six actual-model skips; `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. Next bounded review: M03-01's existing Artifact API interrupted-commit recovery path and whether it has direct regression coverage. No original task status is promoted.

# Master program — real artifacts delivered, native baseline required

## Latest continuation checkpoint — M02-02 XLSX fidelity preparation (2026-10-16)

Test-only code commit `6662910a477640d9e2b11f3051feb7991e31d4a9` adds an assertion to the synthetic corpus parser test. It verifies worksheet names/order, numeric and inline-string types, exact cells, serialized `valueType`, formula `$A$2-$A$1`, and cached `A3=4`; the v2 fixture changes `A2` to 7 while preserving the supplied formula cache. No formula evaluator or production change was added.

One API 29 invocation at source `d0dbca3ea6cf55fc4fe7cb6e8212d265e40894c4` ended exit 1 with 11 setup failures because Robolectric could not fetch `android-all-instrumented:10-robolectric-5803371-i7` offline; every XML failure records that same dependency/connection-refused cause before test bodies. I moved the new coverage into the existing API 35 corpus test class, which already uses the locally cached Robolectric runtime. The final exact-SHA API 35 HOST run passed **8/8**, 0 failures/errors/skips, in 59 seconds (Gradle 8.10.2, JDK 17 runtime/JDK 21 worker, offline). This is a focused test pass, not a full host gate. Logs/XML, commands, outcomes, hashes, and the initial infrastructure failure are preserved in [M02-02 XLSX fidelity evidence](../validation/master-program/04-xlsx-fidelity/RESULTS.md).

Original M02-02 remains `TODO` because it depends on M02-01, which remains formally TODO while M00-10/device validation is pending. The source plan is unchanged at 97 IDs, 120 dependencies, all 97 TODO. M02-01's synthetic parser evidence remains preparatory; M02-05 remains partial/TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`. Full JVM/compatibility/lint/AndroidTest, emulator, physical Motorola and APK were not run here. The next authorized preparatory candidate is M02-03 DOCX structure/table coverage; do not mark source IDs complete.

## Previous continuation checkpoint — M02-01 synthetic corpus preparation (2026-10-16)

Code/test commit `481e36633ae84224b63e225d84010ccfa16f8392` adds a focused Robolectric class that exercises the production `DocumentParser` and exact-cell/range selector against the checked-in synthetic corpus. It covers PDF page 2, DOCX table text, XLSX exact cell/range/formula/cache and a versioned value, quoted CSV cell `CSV!A2`, Kotlin line 2, and PPTX slide 2. XLSX expectations state that the cached value is preserved and no formula recalculation occurs. The fixtures include versioned metadata; the corpus manifest verifies 140/140 files. No private Motorola workbook was used.

The exact-SHA focused HOST invocation passed **7/7** (0 failures/errors/skips) in 58 seconds: Gradle 8.10.2, Temurin JDK 17 runtime, JDK 21 test worker, SDK API 35/NDK 27.0.12077973, offline. Command, complete Gradle log, JUnit XML, corrected wrapper-version probe, outcomes and hashes are in [M02-01 corpus preparation evidence](../validation/master-program/03-document-corpus/RESULTS.md). A preliminary version probe without the configured Gradle user home emitted a wrapper network error; the actual test command used the configured cached home and passed. The earlier 6/6 run had uncommitted test inputs and is supplemental only; it is not treated as exact-SHA validation.

This is preparatory evidence, not formal task closure. The authentic backlog still records M02-01 `TODO`, dependent on M00-10 (`TODO`, requires device); none of the 97 source tasks or 120 dependencies changed. The archived source files retain SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` (backlog) and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5` (roadmap). M02-05 remains partial/TODO, M01-06 remains `PENDING_REQUIRES_DEVICE`, and no full JVM/compatibility/lint/AndroidTest, emulator, physical-device or APK validation ran here. The next authorized preparatory candidate is M02-02 XLSX fidelity/cached-formula coverage; it also remains formally TODO.

The initial working HEAD was feature `76f033e1918b659cb84a7321a0519930d5c3ae72`; this increment is to be published only as a fast-forward on `feat/0.5.0-skills-agents`. `main` and peeled `v0.4.2` remain untouched.

## Previous continuation checkpoint — M02-05 focused citation provenance (2026-10-16)

Code commit `7b60a53f8a993227dc9d0666b548ef5447e93578` is partial preparatory implementation for the authentic M02-05 “Recuperación exacta por celda pagina y codigo” DoD. The regression showed that an EG2 code hit had a path/line passage in Context but no `ContextBundle.sourceEvidence` when it had no legacy segment row. The fix adds in-turn evidence only for a document present in the active project and selected-document scope; preserves semantic segment identity and line bounds through prompt, citation validation, Room storage, and the existing citation UI. Room schema 9 uses a data-preserving 8→9 migration; schema 8 remains unchanged. Code/range/fallback tests cover the targeted gap. No ranking, formula evaluation, inference, permissions, dependencies or data source formats changed.

Exact evidence is in [M02-05 focused retrieval/citation results](../validation/master-program-exact-retrieval/RESULTS.md), with raw commands, logs and XML under that folder. Baseline red was reproduced at `7539123f84bd6982b23653ab169d7e8b6de01e86`: a selected EG2 code passage was absent from turn evidence. The final focused group passed its semantic range/code, validator and Room 8→9 cases (5/5); the Compose assertion initially failed due to a duplicate test tag and passed 1/1 after only the dialog tag was made unique. An earlier 7-case invocation retained its 3 failures: one corrected ID case-normalization issue and two API-29 Robolectric dependency fetch failures. These results are per invocation, not a full green suite.

Gradle Wrapper 8.10.2, Temurin JDK 17 runtime/JDK 21 app test worker, existing API 35 SDK/NDK 27.0.12077973, offline cache. Full app JVM suite, LiteRT compatibility suite, lint, APK build, AndroidTest compilation/execution, emulator, and Motorola were **NOT_RUN** for this increment. UX-02e's prior full-gate stall remains `NOT_DETERMINED`; no retry was made.

The authentic source backlog and roadmap remain unchanged: 97/97 unique IDs, 120 dependencies, all 97 source statuses `TODO`; hashes are unchanged in `plan-source-check.json`. M02-05 depends on M02-01 and M01-05; M02-01 depends on M00-10, still TODO and device-required. Thus M02-05 remains partial/TODO and no source ID is complete. The authorized master prompt permits preparatory M01/M02/M03 work while M00 hardware gates remain pending, without changing the archived plan. M01-06 remains `PENDING_REQUIRES_DEVICE`; `EMULATOR` and `PHYSICAL_DEVICE` were not run here. The next preparatory slice is synthetic/versioned M02-01 corpus work only; do not claim the M00-10 dependency or M02-01/M02-05 complete.

`main` and peeled `v0.4.2` were not modified; both were `de615b772cf37a99e7e08a647c59607b271de606` at preflight. Publication is limited to a fast-forward of `feat/0.5.0-skills-agents`.

---

## Program scope and prior delivery history

First preparatory implementation source `c400dae169c6b511ba90266f9e570912470982a7`, initial base `b44470bdda2bf1b99810b637144cd12f3e750970`. User master authorization supersedes archived M00-only/push rules for preparatory M01/M02/M03. Original 97 task IDs/120 dependencies/order/TODO baseline/progress are intact; no formal milestone closed.

## Latest continuation checkpoint — document removal and stale reindex (2026-10-16)

Tested source `843bf24620447d749455ec9df1a05278d3cec996` on `codex/master-program-20261016`; code changes since the prior Semantic V2 cleanup are in `AppGraph.kt`, `MainActivity.kt`, `Daos.kt`, `WorkspaceRepository.kt`, `SemanticDatabase.kt`, `SemanticLayer.kt`, `ViewModels.kt` and the focused lifecycle/UI tests. The user can remove an imported document from its owning project after an explicit confirmation. The operation removes that document's workspace/index references, retains citation snapshots and cleans only the app's private imported copy through the existing outbox; it does not modify the user's original external file. Semantic V2 removes matching document sources and marks affected active generations stale, while preserving shared vector payloads and the existing generation history. Reimport/update awaits stale marking; stale semantic evidence is rejected and the lexical fallback uses current documents. Focused reindex tests verify a new READY generation and rollback while the prior generation remains READY. No Room schema/database version, dependency, model runtime or source plan changed.

Six exact-SHA HOST invocations passed **7/7 JVM tests** (0 failures, errors or skips; 472 seconds summed from recorded wall durations): document-removal confirmation/outbox, AppGraph document-scope cleanup/shared payload retention, lifecycle scope/citation/shared-path behavior, new READY generation/rollback, real-ingestion stale marking, and fail-closed stale retrieval/current-document lexical fallback. Commands, JDK17 Gradle/JDK21 test launcher, SDK/NDK/CMake versions, per-run logs/XML/exit codes and hashes are in [M02 document removal and stale-index evidence](../validation/master-program-document-delete/RESULTS.md); its manifest has 78 payload entries (SHA-256 `9bcbb6f44e31f3b8ea18b2d4b0a3bb6eb4fc52298fe23b93104961ad696b5a4c`) and is verified locally.

This is **partial preparatory evidence**, not formal M02-06 completion. The original M02-06 status remains `TODO`, and M02-05 is not formally closed; no original ID or dependency was changed. Original backlog/roadmap hashes remain `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5` (97 unique IDs, 120 edges, all 97 source statuses TODO). Full JVM/LiteRT gates, lint, AndroidTest compilation/build, APK, emulator and Motorola were not run in this increment. M01-06 stays `PENDING_REQUIRES_DEVICE`; physical validation is `NOT_RUN`. At that checkpoint, next was to inspect M02-05; the focused M02-05 work is now recorded above, but that source task remains TODO because its dependencies and full DoD are not closed. Main and peeled v0.4.2 remain untouched.

## Previous continuation checkpoint — Semantic V2 project-scope cleanup (2026-10-16)

### Historical delivery record — Semantic V2 scope cleanup (2026-10-16)

Code commits `377d73d82df3d6fee61a3d3e20ada05970b2bd27`, `0d25b5ffa20b048617f44544999acc728f73ba67`, and `8b91675646abca8c4e3e22f3ecd86d97ee817345`; the last SHA is the tested source. Workspace deletion previously removed project/document/EG1 state but left the corresponding Semantic V2 sidecar generations, sources, segments, links and payloads. `AppGraph` now routes confirmed project deletion and standalone CHAT deletion through a scope-keyed Semantic V2 cleanup after the Workspace Room transaction commits. Cleanup preserves payloads still referenced by another project and leaves ordinary conversation deletion within a project untouched. No source documents, model/runtime, Room schema or DB version changed.

The exact-SHA focused HOST test run passed **3/3** in 218 seconds by timestamps (Gradle 3m37s, exit 0); it tested project scope deletion/shared payload retention, standalone chat deletion and the production AppGraph callback. The final log/XML, toolchain, task outcomes, prior attempts and checksums are in [M02 scope cleanup evidence](../validation/master-program-semantic-scope-cleanup/RESULTS.md). An intermediate compile failure from a fixture `Int`/`Byte` mismatch is preserved and explicitly not counted; the final corrected test ran at the committed SHA.

This is only preparatory partial evidence for M02-06, which remains **TODO** because the app still lacks a document-level edit/remove flow and the complete original DoD is unmet. No source task ID or dependency was altered. The authenticated backlog remains 97 unique IDs/120 edges/all TODO; originals and hashes are unchanged. Full JVM/LiteRT suites, lint, AndroidTest, APK, EMULATOR and PHYSICAL_DEVICE were not run. The next step is to inspect the authentic M02-08 codebase-indexing DoD and current file/document path model before scoping any further preparatory work. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

## Previous continuation checkpoint — portable memory import (2026-10-16)

Code commit `76f6db08f6d582025c68350c910bc627f9deda65` on working branch `codex/master-program-20261016` adds the bounded, reviewed `AWS_MEMORY_IMPORT_V1` ZIP import flow. Its focused source tests passed 9/9 and `lintDebug` passed (0 errors, 77 warnings, 1 information diagnostic) on that exact SHA. Command, JUnit XML, lint report, sanitized attempt logs, environment, results and hashes are recorded in [memory import evidence](../validation/master-program-memory-import/RESULTS.md). The 46-entry evidence manifest is SHA-256 `af8f3e3dcf682dabcc99408ec4dd00c92918fed2ef9fc9610980ad3323eac8e3`; local verification passed 46/46 payloads. This is a preparatory capability only: no original task ID/status is changed, the archived 97 tasks/120 edges remain intact, and M00-04 is still partial/not accepted, M01-06 remains `PENDING_REQUIRES_DEVICE`, and UX-02e remains incomplete/not accepted.

The imported entries remain pending until the user approves them; existing memory recovery/search can index approved entries when the embedding capability is available. Sensitive-content filtering is a local heuristic. ZIP export/round-trip is not part of this increment. Full JVM/compatibility/AndroidTest suites and emulator/phone checks were not run. No APK was produced because the retained local Debug signing key (previous certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`) is not mounted in this executor; no replacement signer was generated, avoiding an update conflict. The previous `c400dae` APK record below is historical and is not built from this source. The next preparatory step is the canonical ZIP export/round-trip counterpart, not a new original task completion.

Implemented: bounded EG2 query vector reuse with live READY/scope/config checks (controlled native calls2→1, not neural speed claim), comparable p50/p95 cohorts, exact sheet/range tool reads, ordered PPTX import and DOCX body/table extraction, real generated XLSX/DOCX/PDF/PPTX/code files with immutable versions/hash/conflict protection, production SDK artifact create/read/revise/list, chat preview/editor/restore and explicit SAF export/readback/open. Private drafts never overwrite imported originals. Existing stored Tools selections unchanged; choose Tools→Auto to enable artifacts; Off never disables Agent.

Latest source focal8/8 PASS; package/range9/9, real chat/registry/statistics37/37, cache integration27/27 and selected app+compat36/36 are separately attributed by SHA (overlapping subsets, not a total suite count). Final Debug ARM64/lint/AndroidTest Kotlin gate exit0, 402.447s, warnings41/info1/errors0. AndroidTest compiled, not executed. Ten fresh synthetic original/edited files rehashed/reopened by installed independent readers incl PPTX master/layout. Initial tblGrid/slide-layout omissions were found by stronger validation and fixed; exact native SDK ownership/error contract retained.

APK `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`: com.localai.workspace.debug, 0.5.0-debug/27, arm64-v8a, 65,660,777 bytes; SHA `8203e58cdbd4dbbca064d373ea58b4f14a5b6b4fc0b7e5ea7e48c128f46c4ff8`, retained cert `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`, CRC/signature/alignment/identical-payload PASS. Drive readback confirms file metadata, not independent payload rehash or public sharing: https://drive.google.com/file/d/1K5tcG-igT6gcGq77mhDLLmJ9FMi7odV8/view?usp=drivesdk . No uninstall/data clear; actual installed signer/device upgrade not verified.

Authentic blocker for P0 decisions: adb devices -l exit0 with zero devices. E2B remains default/preferred; no E4B/CPU-GPU/MTP/quantization/runtime upgrade/training decision without same-target measurements. Official latest-release metadata read, not weights. Model exactness, native tool selection, latency/quality/RAM/energy/thermal, Android UI/SAF/viewer interoperability are PENDING_DEVICE_VALIDATION; EMULATOR/PHYSICAL_DEVICE NOT_RUN. No physical25–30% gain claimed.

The master program remains authorized and incomplete. Current delivery supports generated documents only, text/structure previews, text-only PPTX, standard-font controlled PDF regeneration and individual UTF-8 code files. Imported lossless editing, XLSX structured tables, PPTX images, arbitrary PDF modification, resumable ingestion/indexing/OCR/code-project patches and full QA remain pending. Host groundwork is not a completed M01/M02/M03 DoD. No next model/runtime optimization is justified until the actual Motorola baseline is available. Ordinary future increments retain user authorization; no new executor/automation.

[Implementation/crosswalk](MASTER_OPTIMIZATION_PROGRAM.md), [original failures, XML, logs, independent reader checks and delivery](../validation/master-program/02-artifacts-documents/RESULTS.md). Historical UX02e incomplete gate, Room-v1 fixture absence, real-model omissions, M00/M01-06/device conditions below remain unchanged; independent human/code audit pending. No global JVM, emulator or device PASS.

---

# Chat and Workspace visual refinement — personal QA delivered

Source `6bf0cb651905c3d5fb616081c152f8ac9aec2d3d`; base `55471af8202a888778f2537f79b43b53be4ace17`. Explicit SansSerif typography, lighter headings/Workspace cards, nested inset consumption, wrapping header and compact composer with 48dp controls are implemented. Callback semantics, data, Room, inference, permissions, routing and original functional progress are unchanged. The five new user reference images were unavailable; supplied observations were used.

Final offline provisioned build/lint/Kotlin compiles: exit 0, 141.961s (5 executed/2 cached/138 up-to-date); AndroidTest was compilation/up-to-date only. Last Native focused source `89ef8fe5128a86cbd573d2d563291e39cb011ec6` has the same production/normal test tree as final source: 9/11 PASS, two project-dialog idle failures retained. An inset-removal probe did not resolve them; a one-off Legacy diagnostic also failed (idle/OOM). Cause remains NOT_DETERMINED. Do not call this global HOST PASS. Six Native-host captures opened/inspected; component fixtures are synthetic and use a simulated keyboard inset, while Workspace captures are the real empty app screen before the failed dialog interaction. No emulator or Motorola execution, no full JVM suite.

Standalone personal QA Debug ARM64 APK, `com.localai.workspace.debug`, 0.5.0-debug/27, 65,570,665 bytes. SHA-256 `de4b77692993ebceff09695a530e6c29dab3f72988bac96d14431e90f219c827`; retained Debug certificate `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b` matches UX01/UX02a and the previous personal delivery. CRC, payload preservation, zipalign and signature v2/v3 PASS. Same-version replacement permitted; installed phone signer/installation not verified. Do not uninstall or clear user data.

Drive readback: https://drive.google.com/file/d/1pFtgGGF_jNNGkRyeAM9z-ZClmvonHovq/view?usp=drivesdk — MIME/folder/bytes verified, Drive payload not independently rehashed. APK/key not stored in Git. Evidence, original failed XML/logs, commands/hashes/captures: [RESULTS](../validation/chat-home-refinement-20261013/RESULTS.md).

Await independent audit and user checks of header/keyboard and Start project dialog. No new phase started. Historical Room/model-dependent instrumented result, UX02e incomplete status, M00-04 and M01-06/device conditions remain unchanged; original 97-task catalog and progress are intact. Orchestrator coordinates the pause.

---

# Environment cleanup and APK install recovery — 2026-10-14

User requested environment cleanup before fixing the package conflict reported by
the Android installer. Cleanup reclaimed 14,422,118,400 disk bytes while preserving
the current checkout/history, authentic 97-task plan/evidence, unique model files,
required toolchains/dependency cache and the existing debug signing key. Removed
worktrees were clean ancestral checkouts; no user documents in Drive or phone data
were removed. Inventory: `../validation/install-conflict-recovery-20261014/cleanup.json`.

The delivered Actions APK's certificate (`39e1d90...`) differs from the historical
local-signed UX01/UX02a certificate (`f9085e76...`). This mismatch is proven between
deliveries and can explain an Android update conflict. No device is connected;
the installed certificate/failure code and actual installation remain unverified.

Tooling `065ae36930d6f3dc7e72f039067c3065c9b057fd` prepared the same app payloads from
source `0844159b1c8653c15da6a2f47c098e46d38478ba`: 22 DEX entries compressed, all 284
payload hashes unchanged, stored native libraries retained/aligned, APK signed
with the existing local Debug key. Result: 65,566,569 bytes; SHA-256
`566915521ead68c01925f88081d2df994e29685e87fe9a02a68d69c7223f23f1`;
`com.localai.workspace.debug`, 0.5.0-debug/27, arm64-v8a, signature v2/v3 PASS.
HOST packaging tests 2/2 and integrity/alignment/payload checks PASS. No Gradle
build, Android suite, emulator or physical validation was run in this recovery.
Source and workflows are unchanged; the helper is a delivery-packaging tool.

Standalone APK uploaded and read back in Drive:
https://drive.google.com/file/d/1V2evRNicMs66afgN0Lb3MhDCXP08v_Ro/view?usp=drivesdk
The observed metadata confirms MIME, folder and byte count. Local APK SHA verified;
Drive payload not independently re-downloaded/hashed. Detailed results/logs:
`../validation/install-conflict-recovery-20261014/RESULTS.md`.

Do not uninstall or clear user data. Update compatibility is established for the
historical local signer, not an unidentified other signer. Await user installation
feedback. All original functional/UX states, Room historical fixture blocker,
M00-04 not-accepted state and M01-06 pending-device state below remain historical
and unchanged; cleanup alone does not promote any test or task to PASS.

---

# Estado M00 y contrato M00-04 — avisos de omisión de contexto (2026-10-13)

## Fuente original y crosswalk acotado

Los archivos auténticos siguen preservados, sin cambios, en [`Local_AI_Workspace_BACKLOG.json`](plan-source/2026-10-08/Local_AI_Workspace_BACKLOG.json) y [`Local_AI_Workspace_ROADMAP.md`](plan-source/2026-10-08/Local_AI_Workspace_ROADMAP.md). Los hashes registrados son `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` y `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`. El backlog original sigue con 97/97 tareas `TODO`, 97 IDs únicos, 120 dependencias y grafo acíclico; no se cambiaron archivos fuente, estados, IDs, orden ni dependencias.

| ID | Estado de evidencia | Delimitación |
|---|---|---|
| M00-01 | `HOST_DOD_DEMONSTRATED_FOR_SYNTHETIC_FIXTURE_ONLY` | Fixture representativa de 291,429 bytes. Causa/contenido del XLSX privado: `NOT_DETERMINED`. No sustituye el archivo ni la validación física. |
| M00-02 | `HOST_SYNTHETIC_DOD_DEMONSTRATED` | El test filtrado `formattedEmptyXlsxCellsDoNotConsumeRepresentationBudget` pasó 1/1 para 58,000 celdas vacías con estilo y tres celdas pobladas. El ZIP sintético mide 291,429 bytes y expande ~2.41 MB XML; parser/representación conserva A1/A2/A3, fórmula/cache y referencias en 392 bytes y un chunk. Es host/sintético, no el XLSX privado ni validación de dispositivo. |
| M00-03 | `VALIDADA_HOST` | Aceptación independiente ya registrada en el estado persistente. Este contrato no reabrió el gate ni reaudita manifiestos. Referencias: `docs/validation/m00-03/`, `m00-03-sheet-scope/RESULTS.md` y `m00-03-log10/RESULTS.md`. |
| M00-04 | `FOCUSED_PARTIAL_HOST_VALIDATION_ENVIRONMENT_BLOCKED_NOT_ACCEPTED` | El test de chat pasó 1/1. El test UI del Inspector falló antes del cuerpo por `ENOSPC` al copiar fuentes del runtime nativo de Robolectric; la aserción del Inspector quedó `NOT_RUN`. No se demostró discrepancia de producto. |

## Resultado M00-04

Código de prueba validado: `2f5f3613fddca6e19f510d68b2529d48f5ccfa48`; commits de test: `a2f85e57a4363bf5752678c3368fb5ef270668df` y `2f5f3613fddca6e19f510d68b2529d48f5ccfa48`. No cambió código productivo. El primer intento (`a2f85e5`, 51 s, exit 1) se detuvo al compilar por imports inexistentes de Compose test; no generó XML ni inició pruebas. Corregidos esos imports, el segundo intento offline y filtrado (`2f5f361`, 106 s, exit 1) compiló tests y produjo XML: el test de `ChatSessionPipelineTest` pasó, mientras que `ContextOmissionInspectorUiTest` no alcanzó el cuerpo porque `RobolectricTestRunner.beforeTest` propagó `IOException: No space left on device` desde `DefaultNativeRuntimeLoader.maybeCopyFonts`. El XML registra 2 casos, 1 PASS, 1 setup failure, 0 errores/omisiones. En el preflight/post-run quedaban 1.8 GiB en overlay y 345 MiB en `/tmp`; la cuota o causa de almacenamiento más específica no se determinó. No se limpió nada y no hubo otro intento.

Ambos intentos suman aproximadamente 157 s dentro del presupuesto de 300 s. Se usaron Gradle 8.10.2, JDK 17 para Gradle, JDK 21 para workers, SDK API 35/NDK existente y caché Gradle existente offline. Gate JVM completo, compatibilidad, lint y compilación AndroidTest: `NOT_RUN`. Emulador: `NOT_RUN` en este contrato; el resultado global histórico sigue separado (Room v1 FAIL y seis casos dependientes de modelos omitidos). Motorola/PHYSICAL_DEVICE: `NOT_RUN`; APK: `NOT_GENERATED`.

**M00-04 no está aceptado**: la parte de aviso de chat tiene regresión PASS, pero falta un resultado válido de la prueba UI que demuestra el motivo `dropped` visible en Inspector. El siguiente trabajo original continúa siendo M00-04; no avanzar a M00-05 ni seleccionar otra tarea aquí. M01-06 permanece `PENDING_REQUIRES_DEVICE` y depende de M00-02. UX-02e permanece `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`.

La evidencia reproducible de los comandos, logs crudos, XML, resumen y hashes está en [`M00-04 RESULTS`](../validation/m00-04-context-omission-20261013/RESULTS.md) y `docs/validation/m00-04-context-omission-20261013/SHA256SUMS`.

---

## Evidencia histórica HOST de M00-01

El commit de código/prueba `988e666910f093344e32e96c46646f555dfb128a` cambia únicamente `app/src/test/java/com/localai/workspace/data/StructuredDocumentsTest.kt`; la implementación del parser ya existía en `2df24f25a1156bfda054d5cafb1d02e9f85d67d1`. Para la fixture sintética: ZIP comprimido 291,429 bytes; worksheet XML 2,414,015 bytes; XML expandido total 2,414,269 bytes en 3 entradas; estimación fija histórica 23,066,872 bytes frente al límite de 8,388,608; representación UTF-8 actual 392 bytes y 1 chunk. La estimación deriva de la fórmula del código anterior y no se midió ejecutando el parser antiguo. Las etapas host midieron parseo 410 ms, representación 9 ms y chunk 3 ms; embedding/indexación no se ejecutó.

- Intento 1: timeout tras 315 s, exit 137, sin XML ni perfil; no cuenta como PASS/FAIL del método.
- Intento 2: el método filtrado exacto pasó 1/1, sin fallos/errores/omisiones, en 34 s; Gradle exit 0. Reutilizó outputs compilados por el intento inmediatamente anterior en el mismo worktree detached; no se copiaron archivos del checkout principal.
- Suite JVM completa y AndroidTest: `NOT_RUN`; emulador y PHYSICAL_DEVICE: `NOT_RUN`; APK: `NOT_GENERATED`. Comandos, entornos, logs comprimidos, XML, perfil y hashes están en [RESULTS de M00-01](../validation/m00-01-xlsx-profile-20261013/RESULTS.md). El manifiesto de 16 archivos pasa `sha256sum -c`; su SHA-256 es `e9bb01fbee2eb9a4d70e5f18e59d5dfa3a2be8d3add7a9df0385624420052cbe`; el XML focal tiene SHA-256 `91013ac484fa4a6bb8559ee36418227f62de9161ab8b1babc8455d15d00a7e96`.

UX-02e se mantiene `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`: este test de M00 no completa su gate HOST ni cambia la causa del stall, que sigue `NOT_DETERMINED`.

---

# UX-02e — current state after focused prefix diagnostic (2026-10-13)

## Independent audit closure — focused prefix only

The independent audit is recorded as `INDEPENDENTLY_AUDITED_DIAGNOSTIC_ONLY` for source code `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`, read back at feature HEAD `fdcc9bc0cce05a264207e1475416debf0da67ac4` (parent `db5dd75f7fecc5055b176fa9259c55be1ca1b93e`). It confirmed the bounded prefix evidence: 36/36 `TEST_START`/`TEST_END SUCCESS` events and four reconciled JUnit XML suites totaling 36/36 passed, with no failures, errors, or skips. This is diagnostic evidence only; UX-02e remains **AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED** because the full HOST gate is incomplete.

The auditor independently recalculated hashes for **22 of 24** payloads in the focused prefix bundle. `gradle.log` and `supervisor.log` could not be read by the audit connector, so their payload hashes were not independently recalculated. The executor's pre-publication local 24/24 checksum report remains attributed to the executor and is not upgraded to an independent 24/24 verification. The outer evidence manifest was not re-audited in this closure. No source result or manifest was changed.

The earlier full-gate stall remains **NOT_DETERMINED**; the bounded prefix did not reproduce it, and **PRODUCT_FAILURE is NOT_ESTABLISHED**. The original task catalog of 97 entries is unavailable in the tracked repository/context, so no further task ID or dependency is selected or inferred. M01-06 remains `PENDING_REQUIRES_DEVICE`; task progress and the original plan remain untouched. No tests, builds, Room checks, emulator/device runs, APK generation, or manifest recalculation were performed for this documentary audit closure.


## Result and classification

Overall UX-02e status remains **AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED**. The independent acceptance recorded below applies only to the isolated target result; the full HOST gate remains incomplete and the cause of its earlier stall remains **NOT_DETERMINED**. **PRODUCT_FAILURE is NOT_ESTABLISHED**.

One additional, bounded offline diagnostic was run against code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` on the existing detached worktree `/workspace/ux02e-offline-a182`. It replayed the exact 35 successful test events immediately preceding the target in the stalled full-gate event log, followed by the target. It finished naturally: Gradle 8.10.2 `BUILD SUCCESSFUL`, 45.049 seconds, wrapper exit 0, **36 TEST_START / 36 TEST_END**, all successful; four new JUnit XML files total **36/36 PASS, 0 failures, 0 errors, 0 skipped**. Event order exactly matched that prior prefix. The target itself passed in **0.850 s**.

This run did not reproduce the stall in the immediate prefix. The target had also passed alone in the previously audited offline focal run. This reduces evidence for a deterministic interaction among those immediate preceding tests, but does not rule out interference from earlier classes in the complete suite, external state, resource/timing conditions, or nondeterminism. No causal fix is justified. The last complete approved HOST JVM suite remains historical UX-02d code `f54c1430cab4ea59a302b58dd2aa27deea579c49`, 756/756; it was not rerun here. Measured timing varied substantially: the old `ContextEvidenceExcerptTest` suite interval was 106.922 s versus 1.783 s in this run (about 60×). Variable host/resource conditions are plausible but unproven because no load telemetry or causal trace was captured.

The last `TEST_END SUCCESS` before the target in the stalled full-gate run was `ContextEvidenceExcerptTest.log10FunctionCallKeepsArgumentWithoutSelectingSameNamedCell` at `2026-10-13T12:30:26.210366900Z`; the target started at `12:30:28.579499978Z` and had no end event. The target test and selected Gradle/Robolectric config files are unchanged between code `f54c1430cab4ea59a302b58dd2aa27deea579c49` and UX-02e code `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. The test uses Robolectric SDK 35, `runBlocking`, in-memory Workspace/Semantic Room databases, an `AppGraph` with `Dispatchers.Unconfined`, and cleanup in `finally`.

An older archived dump from a different attempt names `ApplicationModelPreparationTest.close`, not the target. It shows the Robolectric worker waiting in `Sandbox.runOnMainThread`, with the SDK 29 main thread in JDK 21 image/module/classloader work before `ApplicationModelPreparationTest.kt:54`. This is not causal evidence for the later target stall. No new thread dump was required for the current run because it completed naturally. Its test worker PID was not captured; the Gradle `--info` log identifies `/workspace/.toolchain/jdk-21-temurin/bin/java`.

The prior online attempt that logged 18 retries to `dl.google.com` and 36 to Maven Central did not start tasks/tests; it remains a separate infrastructure interruption and does not explain the in-test stall. The current diagnostic used `--offline`, made no downloads/installations, and did not clean or copy caches/outputs. The test task alone was forced out of date by a temporary init script; compile tasks retained normal up-to-date behavior.

## Evidence and boundaries

- Command, environment, raw log, event stream, XML, source comparison and checksums: [prefix diagnostic evidence](../validation/ux-02e-inline-links/prefix-diagnostic-20261013/RESULTS.md).
- Prefix bundle manifest: 24 entries, SHA-256 `82b626b27e08c4fd7ed9a1030d2e014ab728be8efae33974cbd1441090d9961e`; outer UX-02e evidence manifest: 162 entries, SHA-256 `7fe6a8b6d571ef5701ac96f48509b58d3e73e033390835ca5e40bafcf4c8a2c4`. Both manifests were verified locally after generation.
- Prior full gate: 36 test starts / 35 successful ends, target active without end, zero XML; LiteRT compatibility, lint and AndroidTest Kotlin compilation were not reached. Its result remains **INCOMPLETE / NOT ACCEPTED**.
- Current filtered task: **PASS for this 36-test prefix only**; it does not complete the overall HOST gate.
- **EMULATOR:** NOT_RUN. The separate historical global run remains FAIL for the authentic Room v1 fixture, with six model-dependent cases SKIPPED.
- **PHYSICAL_DEVICE:** NOT_RUN. **APK:** NOT_GENERATED.
- Original 97-task catalog is unavailable in this context; no task ID/dependency was selected or inferred. M01-06 remains `PENDING_REQUIRES_DEVICE`; task order, dependencies and progress remain unchanged.
- Root `nextRecommendedTask`: no functional task selected. The prefix audit is closed, but the full HOST gate remains incomplete and the stall cause is `NOT_DETERMINED`; the authentic 97-task catalog is unavailable, so no ID/dependency is inferred. M01-06 remains `PENDING_REQUIRES_DEVICE`.

This documentary audit closure is complete. No functional task is selected: wait for the authentic 97-task catalog or explicit user instruction before selecting work. Do not run another gate or start UX-02f, UX-03, or functional backlog work under this contract.

---

## Previous audit closure: independently accepted offline focused target only (2026-10-13)

## Audit outcome

`INDEPENDENTLY_AUDITED_ACCEPTED_FOCUSED_RESULT_ONLY` applies **only** to the single focused offline test result. UX-02e remains **NOT ACCEPTED overall** because the required full HOST gate is incomplete. Tested code SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. Independent audit checked remote feature HEAD `d160d26372c6576d75fe39a6e8ae2b65ff1d51bf` (direct child of `f6e1b642da5f77010203157b52eadd9d5bad0bc1`); all 32 compared paths were under `docs/`, with no product or workflow change after the tested code. `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.

The audited invocation used Gradle 8.10.2/JDK 17 for Gradle and JDK 21 for its test worker. The exact target `ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit` passed 1/1: 74.968 s, exit 0, one start/end event, no skips/failures/errors. The prior full-suite stall was not reproduced; its cause remains **NOT_DETERMINED**, and **PRODUCT_FAILURE is NOT_ESTABLISHED**. `:litert-compat:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin` were not run, so the full HOST gate is still incomplete. No new test/build was run for this documentary closure.

## Independent hash review and regenerated manifests

The auditor rehashed exactly five text payloads under `docs/validation/ux-02e-inline-links/offline-focused-20261013/`: `RESULTS.md`, `command.txt`, `jdk-preflight.txt`, `test-events.tsv`, and the target JUnit XML. All five matched the bundle manifest. At audit time, the bundle manifest had 26 entries and SHA-256 `f26e0f76dd04fd7ad06156d722ca16cf25d82eb720ed362174aae5d9b028f7d1`; the outer manifest had 142 entries and SHA-256 `99f85f1610bf90bee2b8620c6ee8614c3746026f8826578f0eda287b3ba1c054`. The auditor checked those manifest digests/counts but did **not** independently rehash the `.gz` log payloads or all 142 outer payloads; the connector prevented gzip download.

This closure updated the current audit records and regenerated both manifests. The bundle remains 26 entries, current SHA-256 `77c8b436071ca9d7ef6ffbb63cc56e8d57cea30d1a18dd9723d7cc5cbb90f896`; local checksum verification passed 26/26. The outer manifest remains 142 entries, current SHA-256 `9da2d11af8a0836eeac12931ed43348c56a08069e61dcfd2f930fd9f5278a207`; local checksum verification passed 142/142. These local post-update checks are not represented as part of the independent auditor's narrower byte rehash.

## Status boundaries

- **HOST:** focused target independently accepted 1/1; full gate **INCOMPLETE / NOT ACCEPTED**.
- **EMULATOR:** NOT_RUN for this audit. The separate historical global run remains FAIL on the authentic Room v1 fixture, with six model-dependent cases SKIPPED.
- **PHYSICAL_DEVICE:** NOT_RUN.
- **APK:** NOT_GENERATED.
- Earlier full-suite stall cause: **NOT_DETERMINED**; no product failure established.
- Original 97-task catalog: unavailable; not reconstructed or changed. No other task selected. M01-06 remains `PENDING_REQUIRES_DEVICE`; task IDs/order/dependencies/progress unchanged.
- Stop after this documentary closure. No further HOST attempt, UX-02f/UX-03, M00-M12, or other work under this contract.

---

## Prior contract: independent audit closure before the offline focused run

`AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`; UX-02e is not accepted. Audit checked remote feature HEAD `a3cfd6d6787fe7970d7ed818e4d371ccf46e49e8` (direct child of `0368248d4ecc03e5dc119baa85ef14384f46f9ad`) and confirmed `main`/peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`. Code SHA tested: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`; the code/test diff since UX-02d is limited to `MainActivity.kt`, `BasicMarkdown.kt`, `ChatContent.kt`, and two UI tests. The target dedup test and Gradle/Robolectric configuration were unchanged.

The audit independently rehashed 21/21 payloads in the focused bundle (manifest SHA at audit: `165410ca27d5388fab4f73a693e618e49eab6ec3c04bd97f36caeda190d971eb`). For the outer manifest, it checked the manifest digest and 115 entry lines, **not all 115 payload bytes**. It found and corrected a literal template expression in `focused-robolectric-20261013/task-outcomes.txt`; the observed counts are `dl.google.com=18` and `repo.maven.apache.org=36`. The corrected focused manifest SHA-256 is `49b22596939ea63d4468a73e56fdd7bac9ff83be4e67c560ef60086554f18915`. The current outer manifest SHA-256 is `2d89d35544eec35618b9c6f83b37d09d7f10e68defff1c3f872fa516078ef19e` (115 entries); local checkout verification matched 115/115. This local checksum check is distinct from the independent audit, which did not byte-rehash all 115 payloads.

The isolated invocation remains `TIMEOUT_BEFORE_TEST_START / INFRASTRUCTURE_FAILURE`: the log shows 18 and 36 network errors respectively, with 0 `TEST_START`, 0 `TEST_END`, 0 task starts and 0 JUnit XML. The target test is NOT_RUN. The earlier Robolectric stall cause remains NOT_DETERMINED; PRODUCT_FAILURE is NOT_ESTABLISHED. No test/build was run for this documentary closure.

No original task was selected. The authentic 97-task catalog was unavailable; the audit saw only `EXECUTION_STATE.json` and this handoff in `docs/development`, and `BACKLOG.json` returned 404. M01-06 remains `PENDING_REQUIRES_DEVICE`; preserve the original plan/progress and wait for its authentic source or new instruction before selecting any task.

## Result

One invocation targeted the method left active in the earlier incomplete full gate, at source SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`; feature HEAD at start was `0368248d4ecc03e5dc119baa85ef14384f46f9ad`. The method **did not start**: the JUnit listener recorded 0 starts/0 ends, no test worker was created, and no XML was produced. Gradle configuration/resolution logged network-unreachable socket errors to `dl.google.com` and Maven Central. After the 120-second no-event process snapshot, only wrapper PID 136571 and child single-use daemon PID 136605 were stopped with SIGINT. Invocation duration was 220.276 s and wrapper exit code 130; no Gradle task/build summary was emitted.

This is an environment-blocked attempt (`TIMEOUT_BEFORE_TEST_START`), not a test failure. It does not diagnose the earlier Robolectric stall: that cause remains `NOT_DETERMINED`; `PRODUCT_FAILURE` is not established. UX-02e remains not accepted. No full gate, unrelated tests, emulator/device or APK were run.

Evidence: [focused run bundle](../validation/ux-02e-inline-links/focused-robolectric-20261013/); [analysis](../validation/ux-02e-inline-links/focused-robolectric-20261013/run-analysis.json), [command](../validation/ux-02e-inline-links/focused-robolectric-20261013/command.txt), [JUnit inventory](../validation/ux-02e-inline-links/focused-robolectric-20261013/junit-xml-inventory.txt). Gradle log raw-content/gzip SHA-256: `88c2ad6485a6408600aef89d3550106b8df2985dc4d060adf3aeff1a7d1879ef` / `5ca852c0ef3bf1ddeb68e16af5fe278c6b13ff671789363272d7455d8683ec2b`; original log is preserved losslessly as gzip. The audit independently rehashed 21/21 focused-bundle payloads (manifest SHA-256 at audit: `165410ca27d5388fab4f73a693e618e49eab6ec3c04bd97f36caeda190d971eb`). For the outer manifest it checked the digest and 115 entry lines, but did not independently rehash all 115 payloads (manifest SHA-256 at audit: `e07e696e8e1940adfe2cdcf648b45f6a19c3002346a46984a41d583327188355`).

The original 97-task catalog is unavailable in the current checkout/current attachment set; no task was selected. Persistent state identifies only M01-06 as `PENDING_REQUIRES_DEVICE`. The 97-task plan and IDs/dependencies remain unchanged. Stop for independent audit/new instruction.

---

# Previous contract: UX-02e single instrumented HOST gate (2026-10-13; prior evidence)

## Result

The independently audited prior diagnosis is accepted **as diagnosis only**: 19/19 manifest entries verified; focused `ChatContentUiTest` 15/15 and `AuditHarness.writeSelectorReproductions` 1/1 passed. Those focused runs did not reproduce the earlier full-gate stall; the previous full gate remains incomplete, exit 130, zero XML, with its active test/root cause undetermined. No product defect was established.

Exactly one full HOST gate was run against code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` from detached worktree `/workspace/ux02e-a182-jdk17gradle-contract`, while feature HEAD at start was `9e21a355b56d4eb24880c174b399c276078ce043`. It used Gradle 8.10.2/JDK 17.0.20.1, the JDK 21.0.12.1 app test worker, and existing SDK `/workspace/.toolchain/android-sdk` with API 35/NDK 27.0.12077973. Start/end: `2026-10-13T12:22:09Z`–`12:39:14Z`, 1024.871 s. The listener recorded 36 starts/35 ends (all 35 end events SUCCESS); no XML was generated and Gradle emitted no final build summary.

The active JUnit event without an end was `com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit` (start `12:30:28.579499978Z`). The requested task statuses are: app JVM tests **INCOMPLETE**; LiteRT compatibility JVM tests **NOT_REACHED**; lint **NOT_REACHED**; AndroidTest Kotlin compilation **NOT_REACHED**. The wrapper was terminated after the 420-second no-event threshold and a second snapshot 60 seconds later; controller return code `-9` after SIGKILL escalation. This is not a Gradle assertion result.

The test worker was observed as PID 133962 under JDK 21. `jcmd Thread.print -l` timed out (124, zero bytes); SIGQUIT returned 0 but no dump entered the Gradle log. The process-group snapshot omitted the daemon/worker, and the supplemental `ps` observer timed out. A subsequent direct process check found the worker absent; the reason remains unknown. The active method is known, but the cause of the stall/worker disappearance is **NOT_DETERMINED** and `PRODUCT_FAILURE` is **NOT_ESTABLISHED**. No retry or fix was made.

Full raw Gradle log (lossless gzip), test events, run summary, preflight, snapshots, PID/JDK evidence and command are under [`docs/validation/ux-02e-inline-links/full-host-gate-20261013/`](../validation/ux-02e-inline-links/full-host-gate-20261013/). The HOST gate is **INCOMPLETE** and independently audited as **AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED**; UX-02e is not accepted. Parent manifest: 93/93 entries verified; SHA-256 `0359d174243e883d77095d09518d346c304b19cd5adee24b5315e132947c427c`. Full-gate submanifest: 40/40, SHA-256 `24c9cb37ec48bc953aaffde36c3cd7625a472cc55497670190b49d56cf708490`. EMULATOR was not run in this contract; the separate historical global status remains FAIL for Room v1 plus six model-dependent skips. PHYSICAL_DEVICE=NOT_RUN; APK not generated. Main and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`. The original 97-task plan is unchanged. The independent audit found no product/workflow changes after code SHA `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` (16 ahead/0 behind at audited HEAD `ab349096d3a74fcd10eb83819ddb3d8bd8484a96`). It cross-checked one 17m04.871s HOST invocation, 36 TEST_START/35 successful TEST_END events, zero new XML, and the active test without an end event: `com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit`. `jcmd` returned 124/0 bytes and SIGQUIT yielded no dump; causality remains NOT_DETERMINED and PRODUCT_FAILURE is NOT_ESTABLISHED. Auditor rehashed 24/25 accessible submanifest text payloads; 15 `.gz` payloads were unavailable through the connector and were not independently rehashed. The executor’s historical 93/93 and 40/40 checks remain recorded. No further HOST attempts or UX-02f/UX-03 until new evidence or explicit instruction. M01-06 remains PENDING_REQUIRES_DEVICE; original 97-task plan unchanged.
Publication readback: evidence commit `6fe1389b36bdece375f888adb42a7f4023155756` was read back as the remote feature HEAD, fast-forwarded from `9e21a355b56d4eb24880c174b399c276078ce043`; readback checked 2026-10-13T13:06:09Z. This note is a documentation-only closure update. `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`. The 93-entry parent and 40-entry full-gate manifests both verify.

---

# Prior contract: UX-02e bounded Robolectric stall diagnosis (2026-10-13; superseded above)

## Outcome

Tested source SHA: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. Feature branch and remote HEAD at diagnostic start: `4946d603af26c6f6b913d1bbafc957613dc620ee`; source SHA was an ancestor. The original full JDK17 Gradle gate remains incomplete: it stalled in a generic Robolectric `Sandbox.runOnMainThread` stack and was operator-interrupted after 3311 s (exit 130, zero XML). This contract performed two different filtered runs once each, both under an 8-minute limit:

- `ChatContentUiTest`: **15/15 PASS**, zero fail/error/skip; `2026-10-13T11:40:20Z`–`11:43:07Z`, 167.004 s, exit 0.
- `AuditHarness.writeSelectorReproductions`: **1/1 PASS**, zero fail/error/skip; `2026-10-13T11:43:57Z`–`11:46:30Z`, 153.034 s, exit 0.

Neither reproduced the stall. The previous full-gate log's last named output is `AuditHarness > writeSelectorReproductions STANDARD_OUT`, but this is not a test-start event; the old stack does not identify an active class/method. The focused rerun of that candidate passed. Therefore the prior active test and root cause remain **NOT DETERMINED**. No product defect is established or attributed. UX-02e remains **not accepted**; focused tests do not substitute for the full HOST gate.

## Execution and evidence

Both runs used Gradle 8.10.2, Temurin JDK 17.0.20.1 as Gradle runtime and Temurin JDK 21.0.12.1 as the test worker, with both toolchains exposed. SDK environment pointed to `/workspace/.toolchain/android-sdk`; existing Gradle caches/build outputs were not cleaned or copied. The detached worktree was pinned to the exact code SHA and tracked files remained clean. Temporary Gradle TestListener instrumentation ran from `/tmp`, recorded test events and worker/runtime identifiers, and its exact script is archived with the logs. Both jobs completed before the no-progress threshold, so no new jcmd/process-tree stall dump was due.

- Exact commands, environment, timestamps, PIDs, XML and results: [focused run report](../validation/ux-02e-inline-links/diagnostic-20261013/RUNS.md).
- The original Gradle and observer logs are losslessly gzip-compressed; raw event files, compressed worker-runtime records and JUnit XML: [diagnostic bundle](../validation/ux-02e-inline-links/diagnostic-20261013/).
- Earlier interrupted full-gate log remains preserved at [`gradle-gate.log.gz`](../validation/ux-02e-inline-links/host/jdk17-runtime-20261013/gradle-gate.log.gz); its raw SHA-256 is `9e6ad449bff8a0d7039096b4f93cb7b6e297d224de8197aa4bec29b9faba7fc6`, compressed SHA-256 `aaf91f91b166eef790bdf88d22ec7daa785ca421be35a5d395668b4d040d6efc`.
- The complete UX-02e evidence manifest contains 52 entries, all verified by `sha256sum -c`; SHA-256 `0b0a27f934de80e0aa1bf2fcb0db26c0a913aced0c0edf439a38c408a1bdc5f7`. The diagnostic sub-manifest verifies 19/19 payload entries; SHA-256 `32237f5a599673465744dd0301ab7b71d13ffded1d617750fd83abd70cca5aff`.

**HOST:** two filtered test targets PASS (16/16 combined); full requested gate remains incomplete. LiteRT compatibility tests, lint and AndroidTest Kotlin compilation were NOT_RUN in this diagnostic contract. **EMULATOR:** NOT_RUN for UX-02e; the historical global run remains FAIL on the unavailable authentic Room v1 fixture, with six model-dependent cases SKIPPED. **PHYSICAL_DEVICE:** NOT_RUN. **APK:** none generated. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97-task plan was not reconstructed or edited. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. Stop for independent audit; no full gate, further tests, UX-02f/UX-03, emulator/device or APK under this contract.

---

## UX-02e implementation and prior attempts (historical; latest diagnosis is recorded at top)

UX-02e code commit: `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7`. The parser recognizes the inline form `` `[label](URL)` `` only for absolute HTTP/HTTPS URIs with a host. It renders Compose `LinkAnnotation.Url` with theme primary color and underline; the URL opens through `UriHandler` only after explicit activation. Malformed/disallowed destinations remain literal and are not rescanned for nested links. Inline/fenced code, image syntax, HTML tags, table-cell link behavior and free-text citation syntax remain unchanged. The original response copy button was extracted into `AssistantResponseCopyAction` without changing its behavior; its test asserts the exact Markdown source is copied.

- Product/test changes: `BasicMarkdown.kt`, `ChatContent.kt`, `MainActivity.kt`, `BasicMarkdownTest.kt`, and `ChatContentUiTest.kt`; no dependencies or functional routing/storage/runtime changes.
- Parser-only direct Kotlin/JUnit check: **20/20 PASS**, compile/test exit 0 at the code SHA. This supplementary check is not the app Gradle suite and did not emit Gradle XML. Raw output and runner: [UX-02e host evidence](../validation/ux-02e-inline-links/RESULTS.md).
- Initial Gradle attempt: the temporary SDK `/workspace/android-sdk-ux02e` had an incomplete NDK after `sdkmanager` reported `No space left on device`; no files were copied or fabricated.
- The one recovery gate attempt found a pre-existing complete SDK at `/workspace/.toolchain/android-sdk`, including NDK `27.0.12077973` and `source.properties`. It exited 1 before tasks because the command selected `/usr/lib/jvm/java-21-openjdk-amd64`, an OpenJDK runtime without `JAVA_COMPILER`; no test ran and no JUnit XML was produced. Full JDK executables existed elsewhere but were not selected for a second invocation under the one-attempt scope. Raw log and preflight: [recovery evidence](../validation/ux-02e-inline-links/host/recovery-environment.txt).
- A later historical gate selected Temurin JDK 21 as Gradle runtime while exposing JDK 17 for toolchain discovery. That selection contradicted `README.md`, which requires JDK 17 to run Gradle and JDK 21 for app JVM test workers. It reached `:app:kspDebugKotlin` and reported a Java target 17/KSP target 21 mismatch after 40 task actions; classify it as launcher configuration contrary to README, not a UX-02e assertion failure. Its log is [`jdk21-runtime-jdk17-toolchain-gate-20261013.log.gz`](../validation/ux-02e-inline-links/host/jdk21-runtime-jdk17-toolchain-gate-20261013.log.gz), raw SHA-256 `b5694b57610c2e17e7b1953a7053a659bd7d4683a7699edfdc7861665d36ee1d`, gzip SHA-256 `114adf0edb22639d03643919b3ad7976f63c51bf4d135300ef925a538dda9662`.
- For the earlier attempts listed above, app/compat JVM tests, Compose UI tests, lint, and AndroidTest Kotlin compilation were **NOT_RUN**. The prior corrected JDK 17 full-gate attempt remains INCOMPLETE; the two later focused runs documented at the top pass 16/16 combined but do not make the full HOST gate pass. UX-02e remains unaccepted pending audit.
- `EMULATOR`: NOT_RUN for UX-02e; the prior global run remains FAIL on the authentic Room v1 fixture and six model-dependent tests remain SKIPPED. `PHYSICAL_DEVICE=NOT_RUN`; no APK generated. TalkBack behavior was not exercised on device.
- The original 97-task plan remains untracked, unreconstructed and unedited. M01-06 is `PENDING_REQUIRES_DEVICE`; UX-02f/UX-03 and functional backlog remain pending/paused.

## UX-02d delivery record (pre-audit; now accepted)

## Audit finding and correction

The audited base was `ba5b1f084a3e4f29c9bb3c8c6f8b029a6d40e3d4`. `BasicMarkdown.splitTableRow` stripped an outer pipe only when both initial and final pipes were present, although the UX-02d report described each as optional. The new regression was committed first and failed on that base behavior. The parser now treats the initial and final outer pipes independently while preserving consistent row width, full literal fallback for incompatible rows, escaped-pipe handling, single-backtick spans, fences, and malformed-table behavior.

- Red test commit: `af321e397106c49a3e316feabdc58999cdddb763`; 1 test executed, 1 expected failure.
- Parser/test commits: `0c3973e5c7c6d5da7d8bb689bff1dd39eeaf4604`, `26e0fd7f901953068d8836239dc0c845c2b7b3ea`, and final verified code `f54c1430cab4ea59a302b58dd2aa27deea579c49`.
- Production/test scope: only `BasicMarkdown.kt` and `BasicMarkdownTest.kt` changed in this correction.
- Evidence bundle commit: `38d095ab294627718a70234b28ecc4c08ca74402`; this handoff/state closure records that bundle.
- Fast-forward push/readback verified at feature HEAD `e749c45538b4b5b23ee87f157835e65cf428a1d5`; both remote evidence manifests were checked entry-by-entry (16 and 120 entries). This state update is the subsequent documentation closure.
- Detailed red/green commands, environment, XML, raw logs, task outcomes and checksums: [pipe-boundary evidence](../validation/ux-02d-pipe-boundaries/RESULTS.md). Updated UX-02d report: [Markdown tables](../validation/ux-02d-markdown-tables/RESULTS.md).

## HOST results at the final code SHA

- Focused parser/Compose tests: **27/27 PASS**, 2 XML files, zero failure/error/skip.
- Proportional JVM gate XML: app **754/754**, compat **2/2**, total **756/756** across 90 XML files; zero failure/error/skip. Compat was UP-TO-DATE in the combined gate and was also separately forced to rerun on the same SHA: **2/2 PASS**.
- `:app:lintDebug`: exit 0, **0 errors / 82 warnings**.
- `:app:compileDebugAndroidTestKotlin`: executed and passed as a compile only; no instrumented test ran.
- Combined gate: exit 0, 137 actionable tasks (14 executed, 123 up-to-date). Gradle ran offline with the existing cache; this is not a clean-cache claim.
- Initial SDK setup attempt stopped before tests because `ANDROID_HOME` was missing; it is separately recorded and not counted as a test run.

## Validation boundaries and tracking

- **HOST:** PASS for this parser correction and the proportional host gate.
- **EMULATOR:** NOT_RUN for this correction. The prior global UX run remains **FAIL**, 16 cases total: 9 PASS, 1 historical Room v1 fixture FAIL, 6 real-model-dependent SKIPPED. This contract did not repeat it.
- **PHYSICAL_DEVICE:** NOT_RUN. No APK was generated.
- UX-02c remains `INDEPENDENTLY_AUDITED_ACCEPTED` only for structured citation presentation/HOST. UX-02d is `INDEPENDENTLY_AUDITED_ACCEPTED` only for Markdown tables/HOST, as recorded above. UX-02e is now delivered and pending its independent audit.
- The original 97-task plan is not present in the tracked repository and was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02f/UX-03 remain pending; functional backlog remains paused.
- `main` and `v0.4.2` are outside this work and were not changed.

**Historical next step at UX-02d delivery:** complete UX-02e and stop for independent audit; now superseded by the UX-02e delivery record above.

---

# UX-02c delivery record (historical; independently accepted)

**Audit status:** `INDEPENDENTLY_AUDITED_ACCEPTED` for structured `CitationEvidenceEntity` presentation and its HOST evidence only. No UX-02c emulator suite, QA workflow, APK, or physical-device run was started. The later UX-02d delivery is recorded at the top of this handoff.

## UX-02b audit acceptance

UX-02b is independently accepted for the already-supported Markdown renderer and its HOST checks only. Its code SHA is `3fc4bf6d496702f98f370e6089e98141486b284d`; focused checks were 17/17 PASS and the prior full app JVM suite was 744/744 according to the accepted records. The automatic global emulator run `38215175075` remains FAIL (16 total, 9 PASS, 1 unavailable authentic Room v1 fixture failure, 6 model-dependent SKIPPED, 10 executed); QA run `38215175184` failed closed with no APK. PHYSICAL_DEVICE remains NOT_RUN. Links, free-text citation syntax, and tables remain literal in ChatRichText. The post-run `applicationApkInstalled=false` observation and 22/16 progress discrepancy remain undiagnosed.

## UX-02c implementation

- Base: `eb3a6aeee0966cdfe01fb2698a6601ca7af9cdc8`.
- Code commit: `0846419ea64259244a9b1c7bce9b40711cc29217`.
- The existing flow remains `CitationEvidenceEntity` → assistant message → the same `onCitation(evidenceId)` callback → MainActivity lookup by that ID. No parsing of answer text or new evidence/retrieval path was added.
- `CitationEvidenceReferences` replaces ID-only horizontal chips with stacked reference cards showing the stored ID, source label when present, and pageStart when present. They retain the same evidenceId callback, expose button semantics and an accessible description, and have a 48dp minimum target.
- `CitationEvidenceDialog` keeps the source, pageStart/existing fallback and full stored excerpt in a vertically scrollable body. It retains the local-index metadata note and explicitly says the excerpt does not represent a full-document review.
- Files: `MainActivity.kt`, new `ui/CitationEvidencePresentation.kt`, and `ChatContentUiTest.kt`.

## HOST evidence

- Focused `ChatContentUiTest`: 10/10 PASS, including exact callback ID, stored source/page/excerpt, accessible button/heading semantics, touch target, and compact-width long details at fontScale 1.0/1.6/2.0.
- Combined JVM suite: app 746/746 and LiteRT compatibility 2/2 PASS (748 total across 90 XML files).
- `lintDebug`: PASS, 0 errors, 82 warnings.
- `compileDebugAndroidTestKotlin`: PASS, compile only. Instrumented tests NOT_RUN.
- `assembleDebug` was NOT_RUN; no APK requested. `git diff --check` PASS.
- Commands, environment, XML, logs and checksums: [UX-02c evidence](../validation/ux-02c-citation-references/RESULTS.md).

## Validation and remaining risks

- **HOST:** PASS for the focused UX-02c and proportional gate above.
- **EMULATOR:** no new run. The audited UX-02b global result remains FAIL on Room v1; six tests requiring real models remain SKIPPED. The QA gate remains closed with no artifact. The package post-run check and progress-count discrepancy are still unexplained.
- **PHYSICAL_DEVICE:** NOT_RUN.
- The original user-supplied 97-task plan is not present in the tracked repository; it was not reconstructed or edited. Functional IDs/order/dependencies/progress were preserved. M01-06 remains `PENDING_REQUIRES_DEVICE`; UX-02d is pending; functional backlog remains paused.

**At UX-02c delivery:** stop for independent audit. The subsequent acceptance and UX-02d status are recorded above.

---

## UX-02b delivery record (full historical report; now accepted within the scope above)

# Previous contract: UX-02b conversational content readability (2026-10-12)

**Audit status:** independently accepted for the existing Markdown renderer and HOST evidence scope. The global emulator result remains FAIL on the authentic Room v1 fixture, with six model-dependent SKIPPED and 10 executed; QA failed closed with no APK; PHYSICAL_DEVICE remains NOT_RUN.

## Baseline and prior UX-02a acceptance

- Base/current feature reference: `224756694d174af2ed00db4b8c1b5c9fb3f9cb5d` on `feat/0.5.0-skills-agents`; product code for UX-02b is `3fc4bf6d496702f98f370e6089e98141486b284d`.
- The automatic workflow event used checkout commit `3ed99d8b882668aa31c69db622b689f01dd6212f`, whose application code is the same UX-02b code commit above. A documentation-only commit may be made after this record to preserve the automatic run result.
- UX-02a source `d0832e225f51f624c981f6b68da6193b72a78ef2` was independently accepted for its UI scope. The acceptance does not turn its global emulator result into PASS.
- UX-02a remains HOST PASS; its global EMULATOR run remains FAIL on the unresolved authentic Room v1 fixture, with six real-model cases SKIPPED and 10 cases executed. PHYSICAL_DEVICE remains NOT_RUN.

## UX-02b result

The assistant message route uses `ChatRichText` and the existing lightweight `BasicMarkdown` grammar. UX-02b retains recognized heading levels 1–6 for typography and accessibility semantics, establishes clearer vertical rhythm between sections/paragraphs/list items, and preserves message text, selection, exact code-copy behavior, horizontal code scrolling, attribution and source provenance. Links, citations and tables are not interpreted by the current renderer and remain literal text. No parser behavior was added to invent content.

Product/tests changed only in `BasicMarkdown.kt`, `ChatContent.kt`, `BasicMarkdownTest.kt` and `ChatContentUiTest.kt`. No MainActivity, navigation, storage, model, inference, Room, permission, Agent/Tool or dependency changes.

- Focused Compose/Robolectric tests: 17/17 PASS.
- Full app JVM suite: 744/744 PASS from 89 XML files.
- `lintDebug`: PASS, 0 errors and 82 warnings.
- `assembleDebug -Parm64Only=true`: PASS; CMake configure/build for `arm64-v8a` executed.
- `compileDebugAndroidTestKotlin`: PASS, compile-only.
- Full host gate: exit 0, 151 tasks (15 executed, 136 up-to-date), 6m38s.
- Environment, commands, full gate log, focused/full XML and checksums: [UX-02b evidence](../validation/ux-02b-conversation-content/RESULTS.md).

The new layout test uses a compact 320dp Robolectric host fixture at fontScale 1.0, 1.6 and 2.0. It verifies that the complete paragraph text remains present and wraps, heading levels expose heading semantics and size hierarchy, list entries remain reachable/visible while scrolling, and fenced code remains horizontally scrollable. Code-copy exactness remains covered by the existing test. No screenshots were generated in UX-02b; no visual screenshot review or physical-device validation is claimed.

## Validation layers and plan

- `HOST`: PASS for the listed UX-02b checks.
- `EMULATOR`: automatic push run [38215175075](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38215175075), job `114698137054`, completed FAIL on event commit `3ed99d8b882668aa31c69db622b689f01dd6212f`. AVD API 35 x86_64/ADB connected; `:app:connectedDebugAndroidTest` ran and exited 1. JUnit: 16 total, 9 PASS, 1 FAIL, 0 errors, 6 model-dependent SKIPPED, 10 executed. The one JUnit failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` due to the still-unavailable authentic `WorkspaceDatabase/1.json`; Room/tests were not changed. Both ChatHeader tests passed. Collector acceptance also reports `applicationApkInstalled=false`; its timing/cause is undetermined, so no install-success claim is made. Gradle progress reached `22/16` but final XML and collector summary agree on 16 cases; discrepancy not investigated. Actions artifact `11731948710` was 155,255 bytes with SHA-256 `1142a6e6e6b1c63730835c231873a2c931cc0aaf499eab6344edfa418bcba794`. Respecting `.gitignore` (which excludes validation ZIPs), the original archive is not committed; exact summary, JUnit XML, Gradle log and original artifact manifest are persisted in [UX-02b run evidence](../validation/ux-02b-conversation-content/emulator-run-38215175075/README.md). Its header PNGs are not visual validation of Markdown content.
- QA gate [38215175184](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38215175184), job `114698137501`, failed closed with `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; APK build and upload steps were skipped and the Actions artifact list is empty. No APK was generated or downloaded for this UX-02b delivery.
- `PHYSICAL_DEVICE`: NOT_RUN.
- The original user-provided 97-task plan is not in the tracked tree; it was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`, and the functional backlog remains paused.
- `main` and peeled `v0.4.2` remain unchanged at `de615b772cf37a99e7e08a647c59607b271de606`.

**Next at UX-02b delivery:** await independent audit. That audit has since accepted UX-02b for its stated Markdown/HOST scope; the latest work is UX-02c as documented at the top of this file.

---


---

# Previous contract: UX-02a chat header and composer (2026-10-12)

**Status at UX-02a delivery:** HOST PASS; exact-SHA emulator instrumentation executed and was FAIL overall on the unresolved historical Room v1 fixture; both ChatHeader emulator tests passed; personal QA APK was delivered. The user/orchestrator subsequently accepted UX-02a for its UI scope only; global EMULATOR remains FAIL and PHYSICAL_DEVICE remains NOT_RUN.

## Baseline and accepted UX-01

- Before changes, the current checkout was clean on `feat/0.5.0-skills-agents`; local/fetched/remote HEAD was `cd7ea02bba7dbaa0d557259ac73f6ad3427b9ad6`. `main` and peeled `v0.4.2` were both `de615b772cf37a99e7e08a647c59607b271de606`.
- UX-01 source `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8` and evidence HEAD `cd7ea02bba7dbaa0d557259ac73f6ad3427b9ad6` were independently accepted. Acceptance details/limits are retained in `EXECUTION_STATE.json`; in particular, the Drive ZIP hash for UX-01 was not recalculated by the auditor.
- The original plan of 97 task IDs is not tracked; it was not reconstructed/edited. M01-06 remains `PENDING_REQUIRES_DEVICE`.

## UX-02a changes

The real route is `MainActivity` chat `Scaffold` → header and bottom attachments/composer. The previous M3 `TopAppBar` imposed an expanded-height maximum/clipping boundary and `ChatHeaderTitle` limited conversation/model text to one line. The chat now uses `ChatTopBar`, a content-sized M3-styled row with standard minimum height, M3 window insets and colors; all header text may wrap. The model picker callback, back action, overflow actions and Scaffold remain.

Persistent Think/Tools chips are now behind one `Options` action. The selected mode stays visible in a compact summary. Existing dialogs, profile values and callbacks remain; Tools is summarized as `Off`, `Auto` or `Custom`. Tools Off continues to mean tools disabled only and is not an Agent selection. Attachments, selected files, input, send/stop callbacks, keyboard handling and routing remain unchanged. No runtime, models, Room, data, dependencies, workflows or navigation changed.

## Validation

- Commit of product/tests: `d0832e225f51f624c981f6b68da6193b72a78ef2`.
- Focused Compose/Robolectric host tests on that SHA: 9/9 PASS, 0 fail/error/skip.
- Full app JVM suite: 741/741 PASS from 89 XML files. `lintDebug`: PASS, 0 errors and 82 warnings. `assembleDebug -Parm64Only=true`: PASS. `compileDebugAndroidTestKotlin`: PASS as compile-only; it was UP-TO-DATE in the final gate and compiled in the prior successful gate after the AndroidTest changes. Gate details, commands, toolchain, XML archive, logs, and checksums are in [UX-02a results](../validation/ux-02a-chat-header-composer/RESULTS.md).
- Four Robolectric component-fixture captures at 360dp/1.0 and 411dp/1.6 in light/dark were reopened and visually reviewed. All show the three header lines and composer. They are not full-app, keyboard-visible, emulator, or Motorola captures. Host assertions cover fontScale 2.0.
- The Android `ChatHeaderLayoutTest` retains three `assertIsDisplayed` checks, long title/model text, wrapping and bounds assertions, inset diagnostics and model-before-body order. The exact-source API 35 x86_64 instrumented run later executed it: both standard and large-font tests PASS. The complete XML is 16 total: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed. The single failure is unchanged `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords` because authentic `WorkspaceDatabase/1.json` is unavailable. Two LiteRT and four Gemma model-dependent tests remain skipped. No Room test/schema was changed.
- The push-triggered emulator run [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867), job `114683767158`, used source SHA `d0832e225f51f624c981f6b68da6193b72a78ef2`; AVD/ADB were API 35, x86_64, `emulator-5554`. `:app:connectedDebugAndroidTest` ran and exited 1 due to the Room fixture failure. Both original 1080×1920 screenshots were captured, opened and visually reviewed; they show the agent, long conversation title and model at normal and large font settings. The exact Actions archive and extracted XML/PNGs are retained in [emulator evidence](../validation/ux-02a-chat-header-composer/emulator/RESULTS.md); artifact ID `11729571175`, 155,402 bytes, SHA-256 `dd75931c08f5a6ededdb73482b0c728faefcc1719f92d4d8c7ee96da1baa9911`; all 33 archive manifest entries verified.
- The paired same-SHA QA gate [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883), job `114683767175`, failed closed at the wait-for-instrumentation-success step. APK build and artifact upload were skipped and this run produced no artifact. The separately assembled and Drive-uploaded personal QA APK above is not a product of this workflow run.

## Personal QA APK

The Debug ARM64 APK was built from product commit `d0832e225f51f624c981f6b68da6193b72a78ef2`: package `com.localai.workspace.debug`, `0.5.0-debug`/27, `arm64-v8a` only, 121,801,305 bytes, SHA-256 `c58b90d30228dbf58ce230039e44093cafbe71d6ae644d619618ed17117b6aa0`. ZIP CRC, `zipalign`, `apksigner` v2 and package/version checks passed; Debug certificate SHA-256 is `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. No model weight files are bundled.

Download: [PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION](https://drive.google.com/file/d/1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8/view?usp=drivesdk). Drive ID `1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8`; ZIP is 46,262,838 bytes; local ZIP SHA-256 `b5b1225300aa576a5f54c991a5f119844fb41eabdfdb5c7ec7b48880f65d5bc1`. Metadata readback confirmed ID, name, MIME type, byte size, URL and parent folder; the Drive ZIP was not downloaded again to recalculate its remote hash. This is a debug QA package, not Release or production.

## Layered status and next action

- `HOST`: PASS as listed above.
- `EMULATOR`: **FAIL overall** for run [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867); connected instrumentation executed 16 cases with 9 PASS, 1 Room v1 fixture FAIL, 0 errors and 6 model-dependent SKIPPED. Both ChatHeader tests and their emulator screenshots pass within this failed overall suite. Exact artifact, XML, logs and screenshots are in [the run evidence](../validation/ux-02a-chat-header-composer/emulator/RESULTS.md).
- Paired QA gate [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883) failed closed; it did not build/upload an APK. The separate PERSONAL_QA APK and Drive link above remain the delivered binary.
- `PHYSICAL_DEVICE`: `NOT_RUN`; no Motorola claim.
- `M01-06`: `PENDING_REQUIRES_DEVICE`; original 97-task plan/IDs/order/dependencies remain unchanged.
- UX-02a delivery record only; UX-02b is documented above and remains pending independent audit.


---

# Previous contract: UX-00 audit and design foundation (2026-10-12)

**Status:** `INDEPENDENTLY_AUDITED_ACCEPTED`. The original UX-00 delivery itself was documentation-only; it made no application code, workflow, tests, Gradle configuration, database, model, or APK changes.

- Inspected feature HEAD: `ea8b25ea77319a7650dae8f870c788979530feca`. UI source SHA: `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`; the intervening feature commits up to the inspected HEAD contain documentation/evidence, not UI-source changes.
- Audit and implementable UX-01 proposal: [ux-00-audit-and-foundation.md](../design/ux-00-audit-and-foundation.md). It inventories the nine current navigation destinations, three top-level entries, project tabs and dialogs; records the current Theme/UI structure; and proposes tokens, navigation flows, wireframes and phased implementation gates.
- Reference assets: no ChatGPT/Claude screenshots were attached, so no comparison to those products is claimed. The historical app captures and Compose header-test captures are explicitly identified in the audit as product/test evidence.
- The repository has no tracked ROADMAP/BACKLOG/LOOP_RULES or original 97-task file. The original user-provided task plan was not reconstructed or edited. M01-06 remains `PENDING_REQUIRES_DEVICE`; other roadmap work remains pending.
- Android state remains separate from the design audit: run `38186081844` is **EMULATOR FAIL** (16 total: 9 pass, 1 Room v1 fixture failure, 6 model-dependent skips; 10 executed). The two header captures were visually reviewed. `PHYSICAL_DEVICE=NOT_RUN`. This audit did not rerun tests/builds.
- `main` and peeled `v0.4.2` remain outside scope and were not modified.
- UX-01 proposal from this audit was implemented in code commit `4f6ea6faf3d541d366af8ce7284a32fa58f8c1e8`; see the latest contract above and its evidence report.
- The orchestrator automation `supervisar-correcci-n-nica-m00` is already coordinated by the user to complete/audit this contract and then wait silently for visual UX input. No automation was modified from this coding environment.

---

# Latest contract: personal QA ARM64 delivery and Room v1 source review (2026-10-12)

**Build source:** `27a286daa4dd83bfb1b2d36d185459e481d40d1f` on `feat/0.5.0-skills-agents`; product/build files are unchanged from the instrumented code SHA `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1`. Room history was checked only for an attributable WorkspaceDatabase v1 declaration: no such commit exists; the reachable database history begins at v8. `ENVIRONMENT_UNAVAILABLE` remains; no schema or test was changed and the Room suite was not rerun.

Local `:app:assembleDebug -Parm64Only=true` completed at the exact source HEAD with exit 0 (Gradle 8.10.2, Temurin JDK 17.0.20.1+1, API 35; 81 tasks, 7 executed/74 up-to-date; CMake/Ninja native tasks executed). The 119,297,951-byte Debug ARM64 APK has SHA-256 `3aa74283c68ece12169477f837227b90a329e74e53022c70d855d4ab7871e72b`, package `com.localai.workspace.debug`, version `0.5.0-debug`/27, Android Debug certificate SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`, and only `arm64-v8a` native libraries. Zip alignment, archive CRC and `apksigner` v2 checks passed. This build used existing local Gradle/project intermediates and is **PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION**, not Release.

The installable APK is inside the [Google Drive QA bundle](https://drive.google.com/file/d/1M18I1Ls1z6GfAhYMa9dyXqEedfXxIoSI/view?usp=drivesdk). Extract the ZIP and verify its included `SHA256SUMS` before installing. Bundle size: 50,032,510 bytes; SHA-256 `4e3e36a231458997658e22c5ed2155fcc57a94d63abc6f90781c143069021309`. [Machine-readable report](https://drive.google.com/file/d/1l61586udhEwkvnjoN5wM1TLZBZPe7wCR/view?usp=drivesdk) and [bundle checksum](https://drive.google.com/file/d/1E9DI3Kyf293yZ8rJgV1vWJeHxYgrWDNO/view?usp=drivesdk). The direct APK exceeded the Drive connector's 100 MiB limit, so the ZIP preserves the exact APK bytes. No GitHub workflow was changed or gate bypassed.

Instrumented evidence remains separate: run `38186081844` on source code `5e93ba6` was **EMULATOR FAIL** (16 total: 9 pass, 1 Room failure, 6 model skips; 10 executed). The Room failure is `ENVIRONMENT_UNAVAILABLE`; six real-model tests remain skipped. This artifact is not a same-commit instrumentation pass. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged. `main` and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

**Pause:** priority `MEJORAS VISUALES Y EXPERIENCIA DE USUARIO`; state `VISUAL_UX_WAITING_USER_INPUT`. Do not start new roadmap functions or visual changes until the user provides direction/screenshots. The orchestrator automation `supervisar-correcci-n-nica-m00` is coordinated to complete and audit this contract, then remain silently paused for visual UX input; no automation was modified from this coding environment. This contract stops after delivery and evidence publication.

Detailed evidence: [personal QA build](../validation/personal-qa-arm64-27a286/RESULTS.md) and [Room v1 historical source review](../validation/room-v1-source-audit/RESULTS.md).

---

# Independent orchestration audit (2026-10-12 00:23 UTC)

Audit recorded after code/evidence commits `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1` and `25b2da35cd503e800c7bdce9584145f0f2e1218b`; the branch was at `25b2da35cd503e800c7bdce9584145f0f2e1218b` when checked. GitHub confirms the code commit changes only `ChatHeaderLayoutTest.kt`, and the evidence commit changes documentation/evidence only.

The independent audit verified run [38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844) on the exact code SHA: API 35 x86_64 emulator and ADB connected; `:app:connectedDebugAndroidTest` ran 16 tests. The retained gzip XML was decompressed and parsed: 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed. The failure is the missing authentic `WorkspaceDatabase/1.json`; the six skipped tests require real models. The compressed and decompressed XML hashes match the persisted manifest/state.

I fetched and opened both committed PNG captures. Both visibly show all three header lines at 1080×1920. Their byte-level SHA-256 values match the report and checksum manifest: standard `9ecef80b3f3f7bf2fa5f7e6981374bfd741efc33863c8ed6e7afca4675a0c4f2`, large-font `47b64cb847d1ba456a733c26b8ecd333b1e959636dd59e87ad0933598214477c`. The host collector log confirms 7/7 tests; the decompressed AndroidTest compile log confirms BUILD SUCCESSFUL with 54 actionable tasks (2 executed, 52 up-to-date). Neither a full JVM suite nor local APK assembly ran.

Same-SHA QA gate [38186082135](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186082135) failed closed; APK build and upload were skipped and GitHub reports zero artifacts. GitHub artifact metadata for emulator evidence reports 108,492 bytes and digest `0ef4c876e0e275200000085434f6c8a01d648db7afab4eecada7b6e8dbdf709c), matching the persisted record. The auditor independently rechecked hashes for the XML, both PNGs, and host logs; the entire ZIP and all 32 payload hashes remain executor-verified rather than recomputed in this audit.

**Disposition:** screenshot recovery is accepted; the instrumented suite remains **EMULATOR FAIL**, solely on the Room schema fixture, while six real-model tests are skipped. No schema was invented and no test was weakened. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. No additional run or APK work was started; the one-run contract is closed for audit.

The execution state was reconciled in documentation commit `d389b78a947f2cf92bb8fa412f40f7fb9ad38eb8`. It replaces stale screenshot-pending wording with the current audited result; the original 97-task plan and the next task’s device gate remain unchanged.

---

# Latest task: M00 standard Compose screenshot recovery (2026-10-12)

Code commit: `5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1` on `feat/0.5.0-skills-agents`, parent `93409371bac7e6f80d968f7ef084271d474857ac`. Only `ChatHeaderLayoutTest` capture source changed: the synchronized Compose root now uses `captureToImage()` after `waitForIdle`; the three `assertIsDisplayed()` checks, insets/bounds/font-scale checks, and model-before-body check remain. No production UI or Room schema/test changed.

Host: collector tests **7/7 PASS**; `:app:compileDebugAndroidTestKotlin` **BUILD SUCCESSFUL** (54 tasks, 2 executed/52 up-to-date) with Temurin JDK 17.0.20 and the installed Android SDK. No local APK assemble or full JVM suite.

One authorized run [38186081844](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186081844) on the exact code SHA started API 35 x86_64 AVD/ADB and ran `:app:connectedDebugAndroidTest`: XML **16 total, 9 pass, 1 fail, 0 errors, 6 skipped, 10 executed**. The only JUnit failure is the authentic Room v1 schema asset being unavailable (`ENVIRONMENT_UNAVAILABLE`); six model-required tests remain skipped. Both new 1080×1920 original screenshots were opened and visually show all three header lines: standard SHA-256 `9ecef80b3f3f7bf2fa5f7e6981374bfd741efc33863c8ed6e7afca4675a0c4f2`; large-font SHA-256 `47b64cb847d1ba456a733c26b8ecd333b1e959636dd59e87ad0933598214477c`. Thus screenshot evidence is representative; **the emulator suite remains FAIL**.

Same-SHA QA gate run [38186082135](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38186082135) failed closed after the instrumentation conclusion; APK build/upload were skipped and no QA artifact was created. `PHYSICAL_DEVICE=NOT_RUN`; M01-06 stays `PENDING_REQUIRES_DEVICE`; no workbook was used. The original 97-task IDs/order/dependencies, `main`, and `v0.4.2` are unchanged.

Detailed run evidence, raw artifact ZIP, XML/logs, screenshot manifest and checksums: [compose-capture-5e93ba6](../validation/android-emulator-ci/compose-capture-5e93ba6/RESULTS.md). Latest device-validation summary: [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md). Stop for independent audit; no further run is authorized under this contract.

---

## Latest M00 capture-transfer recovery (2026-10-11) — EMULATOR FAIL / stop for independent audit

Source code SHA: `49d7ccf11a682d04ad28c400f07e5fb577e573fb`; evidence bundle commit: `455f579cbf0f172d2cd3b6daf93e98b10ad46d8b` (documentation/evidence only). One push-triggered run [38182644492](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644492), job `114598286399`, completed from that exact SHA. API 35 x86_64 AVD/ADB passed and the real `:app:connectedDebugAndroidTest` executed 16 XML cases: **9 PASS, 1 FAIL, 0 errors, 6 SKIPPED; 10 executed**. The only failed test is the unchanged Room migration test, which cannot find the authentic `WorkspaceDatabase/1.json`. Two model-dependent LiteRT cases and four Gemma-native cases remain SKIPPED; no models were downloaded or simulated.

Both ChatHeader `assertIsDisplayed()` tests passed and the collector received two PNGs (1080×1920) through bounded logcat base64 chunks. The large-font capture visibly shows the header; the standard capture is nearly all white and does not represent the rendered header. The transfer mechanism works, but a complete visual validation is **NOT_MET** for the standard case. The cause is not determined and the one-run allowance is consumed; no production UI was changed. The retained Compose diagnostics show standard app-bar bottom/model bottom/body top at 294/263/294 px and large-font values at 407/360/407 px.

The same push started QA run [38182644499](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38182644499), job `114598286222`, for the exact same SHA. The new gate observed the instrumentation conclusion `failure`; JDK setup, APK build, and upload were skipped. GitHub reports zero artifacts for this QA run. The older QA artifact from run `38178580941` was not downloaded, installed, distributed, or deleted.

One bounded additional public GitHub branch/tag/history lookup found no authentic Room v1 schema: feature branch schema tree contains only version 8; `main` has no v1 JSON; path-specific commit lookup for `WorkspaceDatabase/1.json` returned no commits. The prior 41-object local unreachable scan was not repeated. The migration test and schema stay unchanged; classify as `ENVIRONMENT_UNAVAILABLE`, not a repaired migration.

Host evidence on `49d7ccf`: collector tests 7/7, QA gate tests 6/6, Python compilation and YAML parsing passed, `git diff --check` passed, and `:app:compileDebugAndroidTestKotlin` passed (54/54 tasks executed, 2m53s). No local APK assemble task ran. Full report, archive, XML, screenshots, checksums and sanitized logs are in [run 38182644492 evidence](../validation/android-emulator-ci/diagnostic-runs/38182644492/RESULTS.md); the run-directory manifest is `docs/validation/android-emulator-ci/diagnostic-runs/38182644492/SHA256SUMS`. `EMULATOR=FAIL`; `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. The source 97-task plan, IDs, order and dependencies are unchanged. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

## Previous M00 ChatHeader recovery (2026-10-11; run 38178581012) — superseded by code SHA 49d7ccf

The single authorized run is [38178581012](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178581012), tested code SHA `ff4c9f2f8a5c2c09a240f2e9346856767432489c`, job `114586118884`. The API 35 x86_64 AVD and ADB passed (`emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, 1080×1920, 420 dpi). The real `:app:connectedDebugAndroidTest` ran and exited 1. XML: **16 total, 9 passed, 1 failed, 0 errors, 6 skipped; 10 executed**. The only test failure is `WorkspaceDatabaseMigrationTest.v1ModelsAreExtendedWithoutLosingExistingRecords`, missing authentic `WorkspaceDatabase/1.json`. Preserve the test and schema; the prior full Git history/unreachable-object search found no authentic v1 source, so classify this fixture as `ENVIRONMENT_UNAVAILABLE`, with no invented schema.

Both `ChatHeaderLayoutTest` cases now PASS. The test queries the unmerged Compose semantics tree and retains independent `assertIsDisplayed()` checks for agent, conversation, and model; it also retains bounds, font-scale/inset diagnostics and the model-bottom-before-body assertion. Standard: scale 1.0, 1080×1920 px/411×731 dp, 420 dpi, inset 63 px; app bar bottom 294 px, model bottom 263 px, body top 294 px. Large-font: requested Compose scale 1.6 (resource scale 1.0), same screen/density/inset; app bar bottom 407 px, model bottom 360 px, body top 407 px. Logcat confirms `chat-header-title` merges descendants and that all three child tags/texts appear when using the unmerged tree. No production UI changed.

Screenshot capture remains unresolved. `UiAutomation.takeScreenshot()` returned a 1080×1920 bitmap for each test and the test logged app-cache sizes 45,927 bytes (large-font) and 65,313 bytes (standard). The transfer log value is empty for both; remote checks and `adb pull` each exited 1 because the expected `/sdcard/Download` PNGs do not exist. The artifact contains zero PNGs. Available evidence narrows the loss to cache-to-external/host transfer; it does not identify which shell operation failed because the producer captured neither stderr nor a trustworthy exit marker. No visual screenshot inspection/validation is claimed, and no image was fabricated. The collector now detects/records the absent paths correctly. **The one run is consumed; do not try another transfer approach without a new authorization.**

Two LiteRT tests and four native Gemma chat cases were skipped by real-model availability guards; no model was downloaded or simulated. The XML also proves actual instrumentation ran, even though the post-task package query reports both package IDs absent. Do not treat that post-task snapshot as evidence that install failed during execution.

Host checks on ff4c9f2: collector Python 4/4 PASS; Python `py_compile` PASS; `:app:compileDebugAndroidTestKotlin` PASS (54 tasks, 2 executed/52 up-to-date); `git diff --check` PASS. No local APK assembly was run; the last full JVM suite remains attributed to its earlier SHA and was not rerun here. The exact compile command/log and all hashes are under [run 38178581012 evidence](../validation/android-emulator-ci/diagnostic-runs/38178581012/RESULTS.md). The original 46,460-byte Actions ZIP is SHA-256 `18bf2ab6d03a8b5e7e6907a3869a8c864516b8b3c28781d0712ed9c65bb5df2a`; XML/logcat/screenshot report and the 556,673-byte full job log have per-file hashes in that directory. Its 21-entry durable evidence manifest verifies; after adding the QA workflow side-effect correction, `SHA256SUMS` SHA-256 is `97537205356fc5f5df09c5a12ca9a667bdafa73490169aa143f445e11808d26c`.

`HOST`: targeted checks/AndroidTest Kotlin compilation pass; not a full JVM gate on this SHA. `EMULATOR`: executed, FAIL (Room + screenshot evidence); `PHYSICAL_DEVICE`: `NOT_RUN`. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97 task IDs/order/dependencies are unchanged and no workbook was used. No APK was assembled locally or downloaded/installed by this executor; the automatically uploaded QA artifact is documented below. `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.



## Automatic QA workflow side effect discovered at readback

The push of `ff4c9f2f8a5c2c09a240f2e9346856767432489c` matched `.github/workflows/qa-debug-arm64-apk.yml` because the source change was under `app/**`. This existing workflow ran automatically (not manually dispatched): [run 38178580941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178580941), job `114586118645`, source SHA `ff4c9f2f8a5c2c09a240f2e9346856767432489c`, conclusion `success`. It uploaded artifact [11716383219](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38178580941/artifacts/11716383219), 47,793,824 bytes, API digest `sha256:badd53df1d801ea5164288e876f60c0c662f3edade88913bfd952eb0e591f145`; retention is configured for seven days. The workflow log reports an ARM64 `0.5.0-debug` APK (versionCode 27), 119,297,951 bytes, APK SHA-256 `59a033edc60d7e06300b35b22a3c59cf5d728a4938f4063c602039f61a58e816`, successful signature validation and certificate fingerprint `ab660bd12cb93714ec2c91e1124dca81cf6579c95c909fba5542b78429177c6c`. These APK details have not been independently rehashed because the artifact was not downloaded.

This was an unintended workflow side effect and a sequencing deviation: the artifact uploaded before the emulator run completed with FAIL. This executor did not download, install, or distribute the APK, and did not delete the artifact or launch another workflow. The QA build does not constitute emulator or physical-device validation. The emulator run remains 9 PASS / 1 FAIL / 6 SKIPPED, with screenshot inspection NOT_RUN; the Room fixture is still unavailable.

## Previous M00 recovery result (2026-10-11) — superseded by run 38178581012

The only authorized recovery run is [38171385512](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38171385512), code SHA `e80a8ed8d7f9389639d1a9c35cd313ed0ea1b567`, job `114564548056`. API35 x86_64 AVD and ADB passed; `:app:connectedDebugAndroidTest` executed 16 cases and exited 1: **7 passed, 3 failed, 0 errors, 6 skipped; 10 executed**. Failures are the unavailable authentic Room v1 schema and two ChatHeader `assertIsDisplayed()` lookups. Both `AgentsSkillsNavigationTest` cases now pass. This is **EMULATOR: FAIL**; the one-run budget is consumed.

Room history was inspected through all reachable commits and unreachable objects. The oldest tracked WorkspaceDatabase code is already version 8 with schema export disabled; only schema 8 was later exported. No authenticated v1 schema exists in Git history or the scanned unreachable blobs. The migration test remains intact; no schema was fabricated.

The Skills failure was a test race against Room-backed built-in seeding on `Dispatchers.IO`. The updated test waits for the exact `Spreadsheet Analysis` node, scrolls, asserts displayed, and opens it; the new XML confirms both navigation tests passed.

Header diagnostics captured the Compose tree and device metrics. The tree merges descendants on `chat-header-title`, and its combined text contains all three expected lines; child test tags/bounds are absent from the default merged tree. The two `assertIsDisplayed()` calls query those child tags in that merged tree, explaining the assertion failures as a test semantics lookup issue. This does not prove visual clipping. No production UI change was made. The screenshot collector’s local-directory bug is fixed, but both remote screenshot files were absent (remote-check and pull exit 1); the test’s shell-capture output was empty. Screenshot production remains unresolved, and no image was fabricated. Next focused step, after independent review: query header nodes in the unmerged Compose tree while retaining visibility assertions, and repair screenshot capture using a verified method before any visual claim.

The LiteRT `@Test` method is still `Unit`/void, preserves setup/assertions and its exact real-model assumption; both LiteRT device tests were skipped because no model was present. Four real Gemma cases were also skipped. No models were downloaded or faked.

Host evidence is in [run 38171385512 host results](../validation/android-emulator-ci/diagnostic-runs/38171385512/host/RESULTS.md): app JVM 733/733, LiteRT compatibility 2/2, focused collector tests 2/2; debug build, lint (0 errors, 82 warnings) and AndroidTest compilation passed. `PHYSICAL_DEVICE=NOT_RUN`; no workbook/Moto was used. M01-06 remains `PENDING_REQUIRES_DEVICE`; all 97 task IDs, order and dependencies remain unchanged. No further run, APK, main/tag change, merge or release was made.

**APK handling deviation:** the host command included Debug app and test APK assembly and produced local ignored outputs despite the instruction not to generate an APK before the emulator gate passed. Those two new outputs were removed and not uploaded/distributed; the older ignored Release output was untouched. This deviation is documented in the host evidence.

Full artifact/XML/log hashes, run metadata and root evidence manifest are in [the run evidence directory](../validation/android-emulator-ci/diagnostic-runs/38171385512/RESULTS.md). `main` and peeled `v0.4.2` remain `de615b772cf37a99e7e08a647c59607b271de606`.

## Previous Android emulator CI run (2026-10-11; run 38165315028) — superseded by the recovery result above

The single authorized follow-up was tested from code SHA `ee6909d86c9a3477734addc5e933cfc2dfe40fd7` (commits `8fdc40a` and `ee6909d`). Host validation passed: app JVM 733/733 + LiteRT compatibility 2/2, debug assemble, lintDebug and AndroidTest compilation; no host command executed instrumentation. GitHub Actions [run 38165315028](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38165315028), job `114546554444`, used the existing API 35 x86_64 workflow. AVD preflight/boot and ADB passed (`emulator-5554`, `sdk_gphone64_x86_64`, Android 15/API 35, ABI x86_64). The real `:app:connectedDebugAndroidTest` task ran 16 tests and exited 1: **6 passed, 4 failed, 0 errors, 6 skipped; 10 executed**. This is **EMULATOR: FAIL**, not PASS.

Observed failures are: missing authentic `WorkspaceDatabase/1.json`; `AgentsSkillsNavigationTest` cannot `performScrollTo()` the `Spreadsheet Analysis` text; and both `ChatHeaderLayoutTest` cases fail their first `assertIsDisplayed()` on the agent-title node (large-font line 89, standard line 79). The `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` JUnit declaration is corrected and is now skipped by its real-model guard, alongside one other LiteRT smoke and four native-chat cases. No model was downloaded or simulated. The Room test/schema was not altered; reachable Git/workspace evidence did not provide an authentic v1 Room schema, so none was fabricated.

Header bounds, Compose semantics and screenshots were attempted but not retained. The artifact records that screenshot `adb pull` failed because the collector-local `ui-diagnostics/screenshots` directory was absent; filtered logcat has only TestRunner output. Therefore the header root cause remains **NOT DETERMINED** and no production UI change was made. The additional Skills navigation failure was outside the named corrections and was not changed. Collector summary reports packages absent in its post-task scan; XML/Gradle prove the test runner executed, but no persistent installed-package state is claimed.

The exact Actions ZIP, its 28-entry internal manifest, full job log, JUnit XML, summary, host logs and 85 host JUnit XML files are in [run 38165315028 evidence](../validation/android-emulator-ci/diagnostic-runs/38165315028/). Artifact `11711863899`: 40,988 bytes, SHA-256 `ea98cfd8652e092994bab9e9550c64b9b27c790e2abd95c93c8504e47f56a71c`; readable XML copy SHA-256 `7035efbe8b3d2f15a27d7d93972f2bb35fd9d87f3e4f20608867267014ec3b51` (CRLF normalized to LF; exact original artifact XML SHA-256 `ed93b43fd2a8faf0d7f4bdacf0787076e5785a37e592bf32eb22dfc08b58cdbf`); full job log SHA-256 `02e94be88feee10ce453f89d7d8bc48797ff3bafc2dec5e179c913ea7fbb6de3`. The 99-file evidence manifest verifies all retained evidence; its SHA-256 is `3124e2f16d037d87ea39b4b7a8929c213b02cad6b4b3bd37da9c58914bb8d19d`.

This contract's one CI run is consumed; do not retry it. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. The source 97-task plan, IDs, order and dependencies are unchanged. M01-06 remains `PENDING_REQUIRES_DEVICE`; Motorola validation is **PHYSICAL_DEVICE: NOT_RUN**, and no XLSX fixture was used. See [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md).

## Previous Android emulator CI run (2026-10-11; run 38156881656)

The previous verification run after the AVD path correction was [GitHub Actions run 38156881656](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656), source/workflow SHA `3c5589e7592cb52a88bb2132b1b30fc30d737e03`, job `114521577365`. The correction pins `ANDROID_AVD_HOME=/home/runner/.android/avd` before creation and uses that same environment for launch. Evidence shows `avdmanager list avd` and `emulator -list-avds` both found `localai-api35-x86_64`; the `.ini` and `.avd/config.ini` existed; preflight passed; the emulator booted and ADB connected as `emulator-5554` (`sdk_gphone64_x86_64`, Android 15/API 35, ABI `x86_64`). This confirms the prior AVD discovery path mismatch is resolved for this runner/run.

The real task `:app:connectedDebugAndroidTest` executed and exited 1. Its one JUnit XML reports **15 total: 7 passed, 4 failed, 0 errors, 4 skipped; 11 executed**. The four failures are: missing Room schema asset `WorkspaceDatabase/1.json`; JUnit `InvalidTestClassError` because `normalCpuTurnsReuseNativeSessionAndRestoredHistoryUsesBundleRole()` is not `void`; and the standard-font and large-font `ChatHeaderLayoutTest` assertions both report “component is not displayed.” Four model-dependent `NormalChatNativeDeviceTest` cases were skipped by source `Assume` guards because no Gemma model is installed; the XML skip-reason text is empty, so this is attributed to the source guard rather than a runner-supplied reason. The job’s post-task package inventory did not find either expected package, and no pre-task inventory was captured; the XML establishes that instrumentation ran, but persistent post-task installation state is not claimed.

The run is **EMULATOR: EXECUTED_WITH_FAILURES / overall FAIL**, not PASS. **PHYSICAL_DEVICE: NOT_RUN.** The APK is not retained or distributed. At that earlier checkpoint, no second CI run was authorized; the later single run is recorded at the top of this handoff. That earlier result required a separate reviewed task for subsequent test changes.

Artifact [11709571451](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38156881656/artifacts/11709571451) (`android-emulator-evidence-38156881656-1`) is 40,894 bytes, SHA-256 `b6152538416c30e5b8d2b94521f36b0344b00ae42044ba994f154b4d8bb7ddd7`; all 27 payload entries in the artifact manifest verify (the ZIP has 28 entries including `SHA256SUMS`), and the durable evidence-directory manifest verifies (manifest SHA-256 `66c7b7732e7bc131912ee79927cf1a5eb6e6527d33871778e5f274b34e9dcdef`). JUnit XML readable copy SHA-256: `346a773d82c8a632c3192f00996a7eecb728a896baa1e2c28f61624bd91a7cd5`; its original artifact entry `instrumented-xml/debug/TEST-emulator-5554 - 15-_app-.xml` has SHA-256 `3cbb54410bcaa7a18c5597f1115ca599712d403a5e8ca0422c985dd5bccd52d4` (the exact original bytes are inside `actions-artifact.zip`). The exact XML, sanitized compressed job log, AVD preflight/environment/listings, original artifact ZIP, run metadata, and manifests are in [the run evidence directory](../validation/android-emulator-ci/diagnostic-runs/38156881656/). Job-log original SHA-256: `529e1e95d45c786e9f39e0fe57df9fc3f176849c5fe813d0ea8a9e0b06a7f730` (558,314 bytes); only the sanitized log is persisted, with one credential-like field redacted. The sanitized decompressed log SHA-256 is `b71a3d67ed067fcaf2ed700349c2d0c9103a3e9b6857160221d6a576f903cdd0`; compressed file SHA-256 is `74b94f7e4c0f0963b026d00f7116c36f1dd1866c7fbb30a62de267029ddff13e`.

No Android product/runtime code changed in this task. The original 97-task plan, order, IDs, and dependencies remain unchanged. M01-06 “Ingesta e indexación reanudable” remains `PENDING_REQUIRES_DEVICE`; no workbook/device test was attempted. The separate fresh-empty-Gradle-User-Home Release attempt remains `INFRASTRUCTURE_FAILURE / RETRY_EXHAUSTED` before Gradle startup; lower-level network cause is `NOT_DETERMINED` and it was not retried. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

# Codex → Work handoff: M01-05 context budget and deduplication

## Historical Android emulator CI reinspection (2026-10-09; superseded by run 38156881656)

The assignment head `ba3117bca90494514cd7f2c78f65d53e69625c1a` was fetched and checked in a clean detached worktree. The Android emulator workflow and evidence collector are byte-identical to the files used at incident SHA `bdae87820b73dbcc0decfde3a65c391c6fb63279`; only documentation was updated. The fresh GitHub job-log response (211,335 characters / 1,541 lines) and newly downloaded artifact 11588945524 (23,012 bytes; SHA-256 `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`) were inspected. All 11 manifest entries verified; job steps show AVD setup failed, connected instrumentation was skipped, XML=0 and executed=0.

The log establishes that `avdmanager create avd` did not abort the shell pipeline and the emulator was launched, but the emulator then reported `Unknown AVD name`; ADB remained empty and `wait-for-device` exited 124. SDK tool paths expand under `/usr/local/lib/android/sdk` in the SDK install step. The AVD-step environment dump does not include the direct values of `HOME` or `ANDROID_HOME`; values for `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, and `ANDROID_SDK_HOME` are also absent. The log additionally lacks `avdmanager list avd`, `emulator -list-avds`, and `.ini/config.ini` inventory, were not captured. The cause of the discovery failure is therefore still **UNPROVEN**. No workflow/script edit and no CI run were made; no speculative path correction was applied.

At that historical checkpoint, state was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the prior attempt failed before instrumentation. This was superseded by the single 2026-10-11 run recorded above, where emulator instrumentation executed and failed four tests. Main and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. M01-06 remains pending and device-required; the source plan of 97 IDs/dependencies is unchanged. Details and hashes are in [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md).

## Historical verification: Release from a clean Git checkout (2026-10-09)

The remote feature branch was fetched at `83affa67584292137a17dd544a4f6e207cf7c96f`. A new detached worktree was created from that exact commit. Before the build, its tracked, untracked and ignored status counts were all zero, and no project `.gradle`, `build`, native `.cxx`, or `local.properties` output existed. The primary checkout remained untouched; its 197 pre-existing ignored paths were excluded from the verification. All 1,865 tracked llama.cpp vendor files matched their Git blobs, including `src/models/models.h`; the model-weight ignore probes passed 12/12.

`:app:assembleRelease -Parm64Only=true --offline --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info` completed with exit 0: `BUILD SUCCESSFUL in 28m 32s`, 128/128 actionable Gradle tasks executed. The log shows fresh `configureCMakeRelease[arm64-v8a]`, Ninja compilation/linking for ARM64, and `buildCMakeRelease` process result 0; neither CMake task was `UP-TO-DATE`. Toolchain: Temurin 17.0.20.1+1, Gradle 8.10.2, Android SDK 35, NDK 27.0.12077973, CMake 3.31.6. The checked-out product code is still `260a3f7c6267cc5a90a9cf516356951aec0627a1`; the intervening paths from that code commit to tested checkout `83affa6` are documentation/evidence only.

The shared offline Gradle User Home `/home/agent/.gradle` supplied wrapper/dependency artifacts; no source, build output, or project cache was copied from the primary checkout. `--no-build-cache` and `--rerun-tasks` were used. Gradle's normal Release outputs were discarded with the isolated build worktree; no APK is retained or distributed. JVM tests, AndroidTest compilation, instrumentation, emulator and Motorola checks are **NOT_RUN**. At that earlier Release checkpoint, Android emulator CI was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current state is recorded at the top of this handoff. M01-05 remains `VALIDADA_HOST`; original task M01-06 remains `PENDING`, `requires_device=true`; the user-supplied 97-task plan and IDs/dependencies were not changed.

Full raw log, clean-tree preflight, task outcomes, summary and checksums are in [clean-checkout Release reproducibility evidence](../validation/clean-release-repro-83affa/RESULTS.md). This supplements, and does not relabel, the earlier `b6c4607` clean build.

**Independent clean-checkout Release audit (2026-10-09):** Accepted as `PASS_HOST_CLEAN_CHECKOUT_RELEASE` for checkout `83affa67584292137a17dd544a4f6e207cf7c96f` (product source `260a3f7c6267cc5a90a9cf516356951aec0627a1`). The independent review verified the remote diff is documentation/evidence only, the detached worktree and llama.cpp tracked-source inventory, the ARM64 CMake configure/Ninja build and exit 0, and all six evidence-file SHA-256 values plus the manifest SHA. No tests, APK delivery, emulator, Android instrumentation or physical-device validation occurred. The next authorized work is diagnosis and proof of the AVD creation/discovery cause from run `37865813921`; do not edit the workflow or relaunch CI before the cause is documented.

**M01-05 evidence publication correction:** Audit of the previous published tree `d170173aee31a9503f5b9a9f539b797e3e202645` found eight raw `.log` files missing from Git although present locally and listed by the manifest. `.gitignore` excluded them. They have been recovered verbatim from the checkout and force-tracked; the manifest is regenerated from the evidence files and checked against the index. This is a documentation/evidence-only supplement; test counts and tested code SHA remain unchanged. The regenerated 114-entry manifest SHA-256 is `33446fd4af5bcf27aac64eeaecb89f7c62dbaeb89a56c8bf589a60fe8d062e78`. The delivery commit is the commit containing this update (see Git history); the post-push remote tree check is reported with this delivery. M01-05 is now `VALIDADA_HOST` by independent audit, limited to host evidence; Android instrumentation, emulator, and physical-device runs remain **NOT_RUN**.

**M01-05:** `VALIDADA_HOST` by independent audit, tested code commit `260a3f7c6267cc5a90a9cf516356951aec0627a1`, base `4c5fce553b007c7e565f55b389a162c9b0ecb880`. Focused tests 70/70; full JVM app 733/733 and LiteRT compatibility 2/2; host build/lint gate accepted. AndroidTest was compile-only; instrumented, emulator, and Motorola execution are **NOT_RUN**.
**Next original backlog item:** M01-06 “Ingesta e indexación reanudable” is **PENDING**, `requires_device=true`; wait for device access and preserve its original position/dependencies from the attached 97-task plan. No backlog file or task ID was reconstructed.
**Android CI gate at that earlier snapshot:** `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. This is historical and superseded by the AVD-path correction and run 38156881656 described at the top of this handoff.
**M01-05 report:** [Context budget and relevance deduplication results](../validation/m01-05/RESULTS.md). Raw logs, red/green XML, summary JSON and checksums are under `docs/validation/m01-05/`.
**M01-05 code commit:** `260a3f7c6267cc5a90a9cf516356951aec0627a1`; independently accepted as `VALIDADA_HOST` only. **Prior evidence commit:** `b086adf8fad3e37d718d9c41e3499384bc83c7ef`; its published tree omitted eight ignored logs, now restored by the current documentation-only supplement. The corrected manifest has 114 entries and SHA-256 `33446fd4af5bcf27aac64eeaecb89f7c62dbaeb89a56c8bf589a60fe8d062e78`. The supplement commit SHA is the current delivery commit reported in the final handoff.
**Task-plan source:** The original user-provided attachment containing 97 tasks remains the source of IDs, order and dependencies. The repository has no `ROADMAP.md`, `BACKLOG.json`, or `LOOP_RULES.md`; none were reconstructed or changed.

**Earlier QA/M00 status (unchanged):** The QA-only ARM64 Debug build was independently accepted as `VALIDADA_HOST_QA_BUILD`. The focused Motorola preflight on 2026-10-09 is `BLOCKED_PREREQUISITES_UNAVAILABLE`: ADB found no device and the original workbook is absent. No APK was downloaded or installed in that attempt; all physical checks are **NOT_RUN**. At that 2026-10-09 preflight checkpoint, Android emulator CI was separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the latest result is recorded above.
**Repository:** `https://github.com/javix142-gif/local-ai-workspace.git`
**Branch:** `feat/0.5.0-skills-agents`
**QA APK source SHA:** `4ef2b39b9cba6aa1def081171d42d9146bbd968c` (QA build workflow and verification script; app/runtime behavior was not changed for this artifact task).
**Android CI attempt SHA:** `bdae87820b73dbcc0decfde3a65c391c6fb63279` (separate emulator workflow attempt; instrumentation NOT_RUN).
**Accepted preceding source SHA:** `b6c460763b19de41e689c493859bc1ee649faf2c` (`.gitignore` only; ignore matrix 17/17 and clean-checkout `assembleRelease` PASS, accepted by independent documentary audit).
**Accepted preceding source-build evidence commit:** `b5fa6a2062f7aed568f33bc66c2e4ea229f28a66`.
**Most recent complete JVM gate:** `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`, app 727/727 + compatibility 2/2 = 729/729 PASS; historical, not rerun on `b6c4607`, `bdae878`, or the QA workflow source `4ef2b39`.
**AndroidTest compilation/instrumentation:** final emulator workflow run `37865813921` failed before `:app:connectedDebugAndroidTest`; XML 0, executed 0. The separate QA artifact below is a Debug APK, not evidence of instrumentation. **PHYSICAL_DEVICE:** NOT_RUN.
**Baseline:** `v0.4.2` → `de615b772cf37a99e7e08a647c59607b271de606`
**App:** `com.localai.workspace`, version `0.5.0` (`versionCode 27`).

## Historical delivery: QA ARM64 Debug APK (2026-10-09)

The [GitHub Actions artifact ZIP](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37874837502/artifacts/11592306483) contains `local-ai-workspace-0.5.0-debug-arm64.apk`, `RESULTS.json`, `SHA256SUMS`, and the sanitized build log. Run `37874837502` completed successfully from source SHA `4ef2b39b9cba6aa1def081171d42d9146bbd968c`; job `113640935577` ran `:app:clean :app:assembleDebug -Parm64Only=true` using the project-pinned Android toolchain. The artifact is retained for seven days through `2026-10-16T02:37:56Z`.

- APK: 119,281,567 bytes; SHA-256 `287ac97d6ce4e0563d1618e59726177355673321e401085c6383fe19b9ba4650`.
- Package/version: `com.localai.workspace.debug`, `0.5.0-debug`, versionCode 27.
- Signing: `apksigner` verified the standard Gradle Debug signature (v2); certificate SHA-256 `56d5fb674b00a7aa2c86ff4835690e5b1ef3a1fcc525b08f2a1ec0c355129ffa`. The ephemeral debug keystore was not exported.
- ABI: `arm64-v8a` only, 9 native libraries. The artifact ZIP is 47,787,296 bytes; SHA-256 `5ef240ea08899079e85835bcd2778eeda2b5bd11abde5472f113a974bf6884a4`.
- `RESULTS.json`, log, and member checksums match the downloaded artifact. Full hashes, exact command, toolchain, and verification details are in [QA ARM64 artifact results](../validation/qa-debug-arm64/RESULTS.md).

Status for this delivery: **HOST build/artifact verification PASS**; JVM tests not run for this task; **EMULATOR NOT_RUN**; **PHYSICAL_DEVICE NOT_RUN**. Emulator CI remains blocked as described below. No APK is tracked in Git, and no application/runtime changes were made for this QA delivery.

## Historical M00 Motorola physical validation preflight (2026-10-09)

Preflight stopped before APK download, installation, or testing because both required inputs were not available. The SDK ADB executable `/workspace/.toolchain/android-sdk/platform-tools/adb` ran `adb devices -l` successfully but returned only `List of devices attached` with no serials. Therefore no Motorola identity could be confirmed and `getprop` was not run. Searching `/workspace`, `/tmp`, and `/mnt/data` for `.xlsx`, `.xlsm`, and `.xls` found no original user workbook. The only relevant candidate was `docs/validation/part2/moto-fixtures/sales.xlsx`, a synthetic fixture, which was deliberately not used as a substitute. The exact user workbook had also been recorded as unavailable in the earlier M00 handoff.

Consequently, artifact download/hash recheck, installed-package check, install, workbook import, M00 context/provenance/notice checks, keyboard/header check, and cold/warm Motorola timing are all **NOT_RUN**. This is a prerequisite block, not a product test failure. The detailed read-only evidence is in [M00 Motorola preflight results](../validation/m00-physical-device/RESULTS.md); machine-readable state is `EXECUTION_STATE.json` → `physicalDeviceResults.latestM00FocusedPreflight`. The accepted QA artifact and its hashes remain recorded separately above.

## Historical Android CI follow-up (2026-10-09)

The workflow and its evidence are described in [Android emulator CI results](../validation/android-emulator-ci/RESULTS.md). Final source commit `bdae87820b73dbcc0decfde3a65c391c6fb63279` was tested by [Actions run 37865813921](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921), job `113612155627`, artifact [11588945524](https://github.com/javix142-gif/local-ai-workspace/actions/runs/37865813921/artifacts/11588945524). GitHub-hosted `ubuntu-24.04` had `/dev/kvm`; the workflow enabled access and the KVM verification passed. `avdmanager` returned through its pipeline and the script launched the emulator, but the captured output contains no AVD path, list, or creation confirmation. The emulator reported `Unknown AVD name`, ADB had no connected emulator and its 600-second wait timed out. Instrumentation was skipped by the job dependency and no JUnit XML exists. `summary.json` correctly records `FAIL`, zero tests, and physical validation `NOT_RUN`.

### AVD cause investigation

The full 23,012-byte artifact was downloaded and all 11 entries in `SHA256SUMS` verified. Its SHA-256 is `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. `android-toolchain.txt` resolves both manager executables under `/usr/local/lib/android/sdk`, and confirms the API 35 `google_apis;x86_64` image revision 9, emulator 37.2.12 and platform-tools 37.0.1. The literal `ANDROID_HOME` value is not printed, although the workflow derives these paths from it.

The AVD step does not set `ANDROID_AVD_HOME`, `ANDROID_USER_HOME`, `ANDROID_SDK_HOME`, or `HOME`; the runner log does not record their effective inherited values for that step. The checkout action's temporary `HOME` override applies only during checkout and does not identify the later AVD-step value. The emulator's message prints unexpanded variable-name templates, not resolved search paths. The artifact contains no `avdmanager list avd` or filesystem inventory for `.ini`/`config.ini`. Thus the evidence proves that a usable named AVD was not discoverable, but **does not prove** whether `avdmanager` wrote it elsewhere, created an incomplete entry, or encountered another condition. The local SDK lacks the API 35 image and emulator package, so no local reproduction was attempted. No workflow edit and no further Actions run were made. A fixed `-p` path/preflight is an unverified hypothesis, not a root-cause fix.

The run summary remains: connected device false; Gradle task NOT_RUN/exit null; XML 0; total, passed, failed, errors, skipped, executed all 0; `EMULATOR` instrumentation NOT_RUN; `PHYSICAL_DEVICE` NOT_RUN. See the RESULTS report for the command, hashes and entry-level manifest.

The first run (37865314837) was rejected before a job because of an invalid runner-context location; the second run (37865443624) stopped in the KVM preflight and exposed the collector's missing SDK-path `adb` lookup. The follow-up corrected both KVM preparation and ADB lookup. The final run confirms those checks pass but reveals the remaining AVD creation/location blocker. The two available correction attempts are exhausted; no further workflow change or rerun was made in this handoff.

The final artifact contains the ADB listing, SDK inventory, KVM report, `avdmanager`/emulator logs, summary, and SHA-256 manifest. Its ZIP SHA-256 is `007278791dc27fcda430694be6f3b5d7e3620932548145ba5b28e9907bebb9ac`. The artifact is retained by GitHub through 2026-10-16. No APK, model, secret, or personal data is included.

## Accepted preceding delivery: clean-checkout source ignore matrix and release build (2026-10-09)

The published feature base was `8837517ee502b700c85a2b2c484beddcbcc879be`. Code commit `b6c460763b19de41e689c493859bc1ee649faf2c` narrows the root `.gitignore` exceptions to the vendored llama.cpp model registry's `.cpp` sources and `models.h`; the production application code is unchanged.

The original clean-checkout failure is independently preserved at code SHA `f4695b5644f1230b5f711ec8300937027fb39941`: `:app:assembleRelease` failed because `third_party/llama.cpp/src/models/models.h` was absent from Git. Raw log: [`base-red-assembleRelease.log`](../validation/clean-build-repro-e367dc7/logs/base-red-assembleRelease.log), SHA-256 `14cc8269f6db42fb570a882cb1172c8664b5313c8b08ef6a6fa2f50fe55f0943`. The subsequent `e367dc7` repair tracked the pinned upstream sources; the current `.gitignore` follow-up was then verified against that complete tracked tree.

From a fresh detached checkout of that exact source SHA, after `git clean -ffdx`, the deterministic `git check-ignore --no-index` matrix passed 17/17 assertions: the required C++ sources/header are trackable, all 12 probed model/binary extensions remain ignored, the registry has exactly 158 tracked source files (157 `.cpp`, one `.h`), and the checkout remains unmodified. Script, raw output and checksums are in [the ignore-fix evidence](../validation/clean-build-repro-e367dc7/ignore-fix/RESULTS.md).

From the same clean checkout, `:app:assembleRelease` passed on code SHA `b6c460763b19de41e689c493859bc1ee649faf2c`: exit 0, 128/128 Gradle tasks, 2026-10-08 23:50:16 UTC–2026-10-09 00:07:30 UTC. It used Gradle 8.10.2, Temurin JDK 17.0.20.1, Android SDK 35, NDK 27.0.12077973, CMake 3.31.6 and ABI `arm64-v8a`. The exact command, environment, raw log and SHA-256 are recorded in the evidence report. The temporary build output was removed with the isolated worktree; no APK was delivered or distributed.

The full host JVM gate was last run on `e367dc75d0f952ef6a9b4e96d51e1263b70d3309`: app 727/727 plus LiteRT compatibility 2/2, zero failures/errors/skips. Those results are preserved as **historical per-SHA evidence**, not attributed to `b6c4607`. AndroidTest compilation was not run for `b6c4607`; instrumented tests, emulator and Motorola validation are **NOT RUN**. This delivery's machine-readable status is in [`EXECUTION_STATE.json`](EXECUTION_STATE.json) → `latestDelivery`.

## Historical follow-up: M00-03 LOG10 cell/function distinction (2026-10-08)

The published feature base before this correction was `020917cdb6e469983f77093130b58967ae56df56`. A red test against that code failed for the intended reason: the selector skipped `LOG10` unconditionally, so formula `Hoja1!A3` with formula `LOG10` omitted the existing dependency `Hoja1!LOG10`. The correction is `39aea8f55aac051edd3a43bc1b09c05d319d2783`. It treats unqualified `LOG10` as a function name only when followed by `(` (allowing whitespace); it continues scanning function arguments. A bare or qualified `LOG10` cell reference remains a cell, and missing qualified references keep their sheet/address identity and existing incomplete-evidence notice.

The selector harness reports 12/12 synthetic invariants passing. Focused Context Evidence, ContextFoundation, structured document, Semantic Context postfix and harness tests passed 106/106 on source `39aea8f`. The complete host-gate results recorded for the subsequent tracked-source baseline are 727 app JVM + 2 compatibility tests on `e367dc7`; do not attribute that later run to `39aea8f`. The earlier recorded run also built debug/release/lint/AndroidTest, with lint 0 errors, 40 warnings and 1 informational issue. Instrumented tests, emulator execution and Motorola validation are **NOT RUN**. These source/log records remain historical.

The exact red/green XML, commands, JSON harness report, losslessly compressed logs and SHA-256 manifest are in [M00-03 LOG10 correction evidence](../validation/m00-03-log10/RESULTS.md). The LOG10 harness uses `M00_SOURCE_COMMIT=39aea8f55aac051edd3a43bc1b09c05d319d2783`. At that historical stage, a clean-worktree native build exposed missing ignored llama.cpp model sources. The later `e367dc7` commit tracks the pinned sources and adds the CMake guard; the current `b6c4607` clean-checkout release build passed. The clean-build evidence and raw logs are in [the reproducibility bundle](../validation/clean-build-repro-e367dc7/README.md).

## Historical follow-up: M00-03 sheet-scoped references (2026-10-08)

The earlier M00-03 selector follow-up below fixed range interiors, absolute/mixed references and multibyte text anchors. A separate audit then found that qualified formula references still lost their worksheet identity. At the clean feature HEAD `9c1f32c4277a187c65af715890d1061a2244e771`, the red regression selected `Hoja1!A1=999` for formula `'Hoja2'!A1` and reported no missing cell. The correction is `ca3fcd7ca492ea35d2255b2665c27fa26c2466cf`; its host validation evidence is in [M00-03 sheet-scope results](../validation/m00-03-sheet-scope/RESULTS.md).

Design decision: A1 identity is the pair `(NFC + Locale.ROOT uppercase worksheet key, normalized coordinate)`, while the original sheet name is preserved for display. Unqualified formula references resolve only on the formula cell's sheet. Qualified references resolve only on the named sheet. A direct unqualified query address with multiple represented candidates is treated as ambiguous: no candidate values are sent and a material diagnostic is raised. `$` does not change identity. Qualified single-sheet references/ranges and escaped-apostrophe sheet names are supported. 3D ranges are expanded inclusively only when source metadata supplies a contiguous, conflict-free workbook sheet order; otherwise the excerpt is explicitly incomplete and does not claim endpoint-only coverage.

Selection is bounded to 64 unique composite `(sheet,address)` identities and 480 UTF-8 bytes. Formula strings are masked; formulas are not evaluated or recursively followed. At that historical source commit, `LOG10` was skipped unconditionally; the current contextual behavior is documented above. External workbook references, dynamic `INDIRECT`/`OFFSET`, defined names and structured table references remain unresolved with diagnostics. Missing sheets/cells, ambiguity, 3D ordering failures and evidence-limit omissions propagate through Context provenance/Inspector and the existing material user notice. The indexed source stays unchanged.

Context Foundation reuses only passages already returned by authorized Semantic V2 or lexical retrieval, grouped within the same document. It does not search globally to complete a formula or cross document/project boundaries. F1/F2 and the retrieval-before-generation-gate tests remain in the full host suite.

Historical validation at code commit `ca3fcd7`: focused suites 99/99, app JVM 720/720, LiteRT compatibility 2/2; debug/release builds, lint and AndroidTest APK compilation succeeded. These counts are not the current LOG10 correction result; see the latest section above. Instrumented and Moto G86 execution were **NOT RUN** for that revision.

## Historical follow-up: M00-03 evidence selector correction (2026-10-08)

The later authorized host correction is implemented in `594a511cc088a53b97ccb5f21b720e20e8e4639a`, based on `7da2cc71e57aed1d213ea03b01ba8b41be849939`. It fixes the range-interior omission, absolute/mixed formula reference parsing, and UTF-8 query-anchor windowing. Missing cell dependencies and cell/byte-budget omissions now remain explicit in provenance/Inspector data and trigger a concise user notice; ordinary text excerpting does not.

Focused tests passed 70/70. The subsequent complete host gate passed app JVM 700/700 and LiteRT compatibility 2/2, with debug/release assemble, lintDebug and AndroidTest APK assemble successful. Lint had 0 errors, 40 warnings and 1 informational issue. The test APK was only built: instrumented tests and Motorola validation are **NOT RUN** because no ADB device, emulator binary/system image or KVM was available. The full synthetic before/after JSON, JUnit XML, gate log, summary and hashes are in [M00-03 validation evidence](../validation/m00-03/RESULTS.md).

The prior sections below describe earlier stabilization revisions. Their 686, 700, and 720 app-test counts, APK metadata, and push state are historical only. The LOG10 correction is also historical; current clean-checkout results are recorded at the beginning of this handoff and in `EXECUTION_STATE.json` → `latestDelivery`.

The implementation is based on the completed 0.5.0 Skills/Agents and Semantic V2 postfix. It preserves the single Gemma runtime, Skills/Agents, existing databases, Semantic V2 and the F1/F2 fixes. No merge or release tag is part of this handoff.

## Work completed in this stabilization

- **M00-1 XLSX:** representation budgeting now accounts for actual serialized cells incrementally; blank/style-only cells are omitted instead of consuming estimated JSON budget. Formula text, cached values, coordinates, XML/cell limits, interruption and parser failure behavior remain. A synthetic workbook with 40,000 formatted empty cells exercises the regression. The user’s original ~300 KB workbook was not available, so its exact ZIP/XML dimensions and exact failure trigger remain **NOT DETERMINED**.
- **M00-2 context:** full indexed segments remain intact, while Context Builder chooses a query-relevant, bounded excerpt for the current request. XLSX evidence prioritizes queried cells, formula dependencies and headers; provenance includes cell addresses and character offsets when the excerpt is contiguous. Repetitive/deduplicated/scope-filtered or old-history omissions are Inspector diagnostics; important items lost to token budget retain a user notice. Prompt metadata identifies source content as selected passages and avoids claiming whole-file coverage.
- **M00-3 chat header:** the three identity rows use an adaptive top-bar height, font-scale-aware padding, one-line ellipsis for long names and Compose test tags. A Compose UI test covers standard and 1.6 font scales; execution requires an Android device/emulator.
- **M00-4 diagnostics:** import/copy/hash, parse, chunk, segment commit, background EG1 indexing, skill/agent routing, memory retrieval, source retrieval, conversation retrieval, context construction, inference gate wait, generation/TTFT and existing load/warm-up/device metrics are separated where the production pipeline exposes them. Timings contain metadata only, not prompt, document, filename, path or content. `contextBuildMs` is inclusive of sub-stages; do not add the inclusive total to its component timings.

Focused commits on the feature branch:

- `2df24f2` — bounded XLSX representation.
- `8c48d68` — bounded relevant context evidence; F1/F2 regression tests.
- `5a27a6e` — stage timings and diagnostics.
- `b577934` — adaptive chat header and Compose test.

### Root-cause confidence

The production XLSX code had been charging its representation estimate for blank formatted cells that do not produce useful evidence. Skipping empty cells and accounting serialized output addresses that avoidable expansion while retaining hard bounds. Because the original workbook was unavailable, this is a code-level reproduction and explanation, not proof of its precise internal cell count or expanded size.

The observed ~3-minute first answer has **no established root cause** from the available physical evidence. The host synthetic fixture measures parser/serialization/chunk stages only; it does not run Android native embedding or Gemma. The new instrumentation is intended to isolate the next cold and warm physical run. No speedup is claimed.

## F1 / F2 preservation

Targeted tests continue to cover EG2 retrieval through Context Builder even when precomputed evidence is empty, project and selected-document isolation, no duplicate evidence, lexical fallback on V2 failure, and work completing before the generation gate. Stale `NEEDS_REINDEX`, `INDEXING`, `FAILED` and `CANCELLED` generations remain ineligible as current evidence; READY generation publication and rollback are retained. No Semantic V2 redesign was made.

## Validation

The first full gate run passed JVM tests, debug/release builds and lint but exposed a compile error in the new Compose test (`assertExists` import unsupported by the project’s Compose test API). The redundant assertion/import was removed; the final full-gate outcome is recorded in `EXECUTION_STATE.json` after rerun.

Host synthetic XLSX evidence is written by `StructuredDocumentsTest` to `app/build/reports/xlsx-formatting-repro.json`. The fixture is 207,035 compressed bytes, with 1,778,015 bytes of worksheet XML, 40,000 formatted empty cells, three populated cells and 293 serialized UTF-8 bytes. The parser reported 299 ms on this host; serialization and chunk creation rounded to 0 ms at the report’s millisecond resolution. The report labels itself `HOST_SYNTHETIC_NOT_USER_WORKBOOK`; embedding/indexing is explicitly marked not executed. These values are not measurements for the user’s workbook or Motorola.

Final host gate command (JDK 17, Android SDK, ARM64-only, offline, sequential Gradle):

```text
./gradlew --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx2048m -Dfile.encoding=UTF-8' \
  -Pkotlin.daemon.jvmargs=-Xmx2048m \
  -I docs/validation/skills050/force-test-execution.gradle \
  :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true --offline --console=plain
```

Result: `:app:testDebugUnitTest` **686/686 PASS**; `:litert-compat:testDebugUnitTest` **2/2 PASS**; debug, release, lint and AndroidTest APK build PASS. Lint has 0 errors, 40 warnings and 1 informational issue. The first gate attempt compiled the new Compose test with an unavailable `assertExists` import; that redundant call was removed and the complete gate then passed. The Compose test APK was built, but tests were not run.

### Android execution environment

Environment inspection found no attached ADB device, no AVD, no installed Android system image and no `/dev/kvm`. Therefore Android instrumented/UI tests cannot run here. `assembleDebugAndroidTest` means only that the test APK compiles; it is not a UI test PASS. Moto G86 performance, thermal, native inference and real-workbook behavior remain physical-device validations.

## Artifacts and Git synchronization

Historical artifact from an earlier stabilization build (not the current `b6c4607` delivery): ARM64-only release APK `dist/apk/local-ai-workspace-0.5.0-preloop-stabilization-arm64.apk`, 100,130,461 bytes, SHA-256 `a7bd3f5c69c2644b7de9fbe86f62aa3edf78fb60961487cd96782fe66848c0d0`. It was not device validated. The `b6c4607` clean release output was removed with its temporary worktree and no APK was delivered. APK/model binaries remain outside Git.

Historical Git synchronization records above are not the current feature-branch publication state. For this delivery the intended code/evidence commits are `b6c460763b19de41e689c493859bc1ee649faf2c` and `b5fa6a2062f7aed568f33bc66c2e4ea229f28a66`; publication/readback status is reported after the authorized fast-forward push. `main` and peeled `v0.4.2` are expected to remain at `de615b772cf37a99e7e08a647c59607b271de606`. No force push, merge, release or `v0.5.0` tag is authorized.

## Physical validations still required

On the Motorola Moto G86 Power 5G, test with the original workbook when available:

1. Import the ~300 KB XLSX and inspect parse, chunk, and background-index stage timings.
2. Ask the A3/formula follow-up twice: once cold and once warm; export metadata-only diagnostics.
3. Confirm the relevant cell/formula excerpt reaches Gemma, provenance contains A1/A2/A3, and ordinary redundant trimming does not show the prominent omission notice.
4. Confirm all three header lines remain visible with the keyboard open, a long chat title and standard/large font scale.
5. Run the F1/F2 Context Builder checks after importing/updating a document and after reindex.

Historical physical evidence (12/12 AGENTS_SKILLS_V1 and prior 0.5.0 validations) belongs to its recorded build; it does not validate this stabilization APK.

## Historical recommendation from the prior handoff

The previous handoff recommended installing its stabilization candidate and collecting cold/warm timings. That recommendation belongs to that earlier delivery.

## Historical next step from the prior handoff (superseded)

Resume the focused M00 Motorola validation only when the exact original XLSX is accessible and a Motorola Moto G86 Power is connected and ADB-authorized. Then use only the accepted artifact from run `37874837502`, recheck its ZIP/APK hashes before installation, and stop if `com.localai.workspace.debug` is already installed. Do not substitute the synthetic fixture or another device. Until those prerequisites are met, `PHYSICAL_DEVICE` remains **NOT_RUN**. At that historical preflight, Android emulator CI was `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current run and classification are recorded above.

## Independent orchestration audit: Android AVD blocker (2026-10-09 07:36:16 UTC)

Audit record: `docs/validation/android-emulator-ci/RESULTS.md`, appended for audited feature head `a18f1fc99be211fe2346799218233548a15adf16`.

The executor's bounded AVD-cause investigation is independently confirmed as `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`: GitHub reports AVD setup failure, instrumentation skipped, and zero XML/tests; its job log records the AVD create/launch commands and a 600-second `adb wait-for-device` timeout, but does not establish whether creation was partial/absent or used an undiscovered path. No workflow fix or new CI run was made. No new executor instruction is assigned while this evidence boundary remains unchanged.

The current audit checked the one-commit fast-forward comparison from `ba3117bca90494514cd7f2c78f65d53e69625c1a` to `a18f1fc99be211fe2346799218233548a15adf16`; only documentation files changed. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. The auditor did not execute local Android tests and could not recompute artifact ZIP/internal hashes from the connector's returned file reference; see the audit limitation recorded in RESULTS.md. The original 97-task plan remains unchanged. Resume this blocked track only when new evidence or a bounded diagnostic opportunity can distinguish the AVD creation/discovery cause.


## Historical clean-home ARM64 Release reproducibility attempt (2026-10-09)

This bounded attempt used a fresh detached worktree of remote `feat/0.5.0-skills-agents` SHA `4559b502c55497f9f3c932c3e428f708fb086fd2`; the branch had not advanced from the assigned SHA. The checkout had zero tracked, untracked, or ignored paths before the command. All 1,865 tracked llama.cpp files matched disk, including `src/models/models.h` blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`; all 15 model/binary ignore probes were ignored.

The one requested invocation was `./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info`, with Temurin JDK 17.0.20.1+1, SDK 35, NDK 27.0.12077973, CMake 3.31.6 and a new empty `GRADLE_USER_HOME`. At `2026-10-09T08:31:52Z`, the wrapper attempted to download pinned Gradle 8.10.2 from `services.gradle.org`; it received `java.net.ConnectException: Connection refused` and exited 1 at `08:31:58Z`. The Gradle distribution did not start: zero Gradle tasks ran, CMake/Ninja were not reached, and no APK was generated. No retry or cache/source copy was made. This is a **network-blocked attempt**, not an application build failure or PASS; the previous `83affa6` clean Release PASS remains preserved under `previousCleanReleaseRepro` in `EXECUTION_STATE.json`.

Raw log, preflight, toolchain, failed wrapper-home inventory, task outcomes, remote refs, summary and checksums are in [the 4559b5 clean Release attempt](../validation/clean-release-repro-4559b5/RESULTS.md). The remote feature HEAD was still `4559b502…` after the command; `main` and peeled `v0.4.2` were still `de615b772cf37a99e7e08a647c59607b271de606`. No JVM, AndroidTest, emulator, Android CI or Motorola work was performed. Android/emulator/instrumented/physical validation remain **NOT_RUN** for this attempt; the separate Android CI status remains `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`. The original 97-task plan is unchanged and M01-06 remains pending because `requires_device=true`.

## Independent audit: clean-home Release network block (2026-10-09 09:35:53 UTC)

Audit record: `docs/validation/clean-release-repro-4559b5/RESULTS.md`. For tested checkout `4559b502c55497f9f3c932c3e428f708fb086fd2`, the detached-worktree/source preflight and 13/13 evidence hashes were independently read back and verified. The raw wrapper log establishes `Connection refused` while fetching pinned Gradle 8.10.2; Gradle task graph=0, CMake/Ninja not reached, APK not generated. This is `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`, not a build result. No retry or cache/source copy was made under the active contract. The earlier clean Release PASS for `83affa6` remains separately scoped to that SHA. Feature branch at the evidence delivery was `fcae468dc2a776d6e86709547a8bc3f295d1063d`; main/tag stayed at `de615b772cf37a99e7e08a647c59607b271de606`. Android and Motorola remain `NOT_RUN`.


## Final empty-home ARM64 Release attempt (2/2; 2026-10-09)

The assigned remote SHA `6d1b256b23c4e76dad72413ced9b10419425c134` was fetched and compared with the preceding evidence head `fcae468dc2a776d6e86709547a8bc3f295d1063d`; intervening changes were documentation only. A fresh detached worktree was clean before the build. All 1,865 llama.cpp files matched Git, including `src/models/models.h` blob `387a4adcb239db69d72349ee57e2fbfe2db04e5a`, with 16/16 model/binary ignore probes passing.

The one and final authorized command was `./gradlew :app:assembleRelease -Parm64Only=true --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 --console=plain --info` with a newly empty `GRADLE_USER_HOME`. It ran from `2026-10-09T21:50:17Z` to `21:50:23Z`, exit 1. The wrapper tried to fetch Gradle 8.10.2 from `services.gradle.org` and got `java.net.ConnectException: Connection refused`. The tool environment reported `connected` with an enforced unrestricted network policy; this does not establish the lower-level cause of the refused socket. Gradle did not start, zero tasks ran, CMake/Ninja were not reached, and no APK was generated. No retry, alternate source, or cache was used. This is **not a PASS and not an application compilation failure**; fresh empty-home Release reproducibility remains unverified.

The complete run record and manifest are in [the final 6d1b25 attempt](../validation/clean-release-repro-6d1b25/RESULTS.md). Remote feature SHA remained `6d1b256…` through the build; `main` and peeled `v0.4.2` remained `de615b772cf37a99e7e08a647c59607b271de606`. The prior blocked attempt at `4559b502…` and its independent audit are preserved as history in `EXECUTION_STATE.json`; the earlier successful clean Release check at `83affa6…` also remains separately scoped to its SHA.

No JVM, AndroidTest, emulator, Android CI or Motorola validation was run. Those statuses remain **NOT_RUN** (Android emulator CI separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`). The original 97-task plan is unchanged; M01-06 remains `PENDING_REQUIRES_DEVICE`. This contract's two empty-home attempts are exhausted.


## Independent orchestration audit: final empty-home ARM64 Release attempt (2026-10-09 22:56 UTC)

**Status: `BLOCKED_BEFORE_GRADLE_START_NETWORK_CONFIRMED`.** I reviewed the final attempt at checkout `6d1b256b23c4e76dad72413ced9b10419425c134` from GitHub evidence only; I did not run Gradle locally. GitHub comparison confirms evidence commit `7b1997589328852ff81dddc2ce0355b1342e1913` is one fast-forward, documentation/evidence-only commit after the tested SHA. The application source baseline is `260a3f7c6267cc5a90a9cf516356951aec0627a1`.

The published preflight reports an exact-SHA clean detached worktree, empty ignored/tracked/untracked status, 1,865/1,865 vendored llama.cpp files matching Git (including `models.h`), and 16/16 model/binary ignore probes. This is artifact-backed evidence, not a local reproduction by the auditor. I independently fetched and recomputed the SHA-256 of all 17 files in the evidence manifest: 17/17 match. The raw wrapper log SHA-256 is `3caee44390cf5c8ccec0423a07baf95079d50a7112b328b126f003501b76fe6c`; it records `Connection refused` while downloading Gradle 8.10.2. Gradle did not start (0 tasks), CMake/Ninja were not reached, and no APK was generated. Although the environment reported connected/unrestricted policy, the lower-level cause of the refused socket remains NOT DETERMINED. The two authorized attempts are exhausted; this is not an application build failure or PASS.

GitHub refs still show `main` and peeled `v0.4.2` at `de615b772cf37a99e7e08a647c59607b271de606`. JVM, AndroidTest, emulator and Motorola execution remain NOT_RUN; At that historical attempt, Android CI was separately `BLOCKED_ANDROID_CI_CAUSE_UNPROVEN`; the current run is recorded at the top. The original 97-task plan is unchanged. Updated results SHA-256: `c5f7bc2f5f77d3731179b9188c4567e4150d0294ef39827e97926df308d4615f`; refreshed 17-entry manifest SHA-256: `b92cb4d60e103f65326965d32c7951d70f55a8ad02f9fedc6c1ea74a4345c8c4`.

No further clean-home build retry is assigned under this exhausted contract. Continue only with other original tasks whose prerequisites are satisfied; do not claim clean-release reproducibility or Android validation from this attempt.

## Historical bounded follow-up: Release evidence classification and AVD diagnostics (2026-10-11)

At fetched feature HEAD `fcad2eca9bfb4d0b286e1508f1eaeb67ab726a7b`, the fresh empty-Gradle-home Release path remains `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED`: both authorized wrapper attempts stopped before Gradle startup with `Connection refused` fetching Gradle 8.10.2; lower network cause is `NOT_DETERMINED`. The separate `83affa67584292137a17dd544a4f6e207cf7c96f` result is a historical ARM64 `assembleRelease` PASS only with pre-provisioned shared Gradle dependencies (`--offline`), 128/128 tasks, and CMake/Ninja success. It is not an empty-home result and no APK was retained. The current ARM64 Actions artifact is run `37874837502`, explicitly `:app:assembleDebug` and QA/debug-signed; it is not Release evidence. Details are in `EXECUTION_STATE.json` → `latestGradleEvidenceClassification` and the AVD [results report](../validation/android-emulator-ci/RESULTS.md).

At this pre-run checkpoint, the original AVD artifact established only that the named AVD was undiscoverable, ADB was empty, instrumentation was skipped, and XML/executed counts were zero. A diagnostics-only workflow/collector change was then prepared without changing AVD creation/name/path/launch. The single authorized diagnostic run 38154790618 later demonstrated the AVD home/path mismatch; the subsequent path correction and instrumented run 38156881656 are retained in history, and the current run 38165315028 is recorded at the top of this handoff. These historical steps do not change the separate Motorola state; M01-06 remains `PENDING_REQUIRES_DEVICE`, and the original 97 task IDs/dependencies are unchanged.

## Historical Android AVD diagnostic run before the path correction (2026-10-11)

The diagnostics-only workflow source `d0d4d7608c8e8149bdb05d003da2742260c1b8dd` was run once by push: [run 38154790618](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618), job `114515427123`, artifact [11708477219](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38154790618/artifacts/11708477219). SDK/API 35 and KVM setup passed. `avdmanager create` exit=0 and pipeline=`0 0 0`; `avdmanager list avd` exit=0 lists the named AVD under `/home/runner/.config/.android/avd/...`; `emulator -list-avds` exit=0 is empty. `HOME=/home/runner`; both SDK variables resolve to `/usr/local/lib/android/sdk`; `ANDROID_USER_HOME`, `ANDROID_AVD_HOME`, `ANDROID_SDK_HOME` are unset. The recorded emulator candidate `/home/runner/.android/avd` does not exist, and emulator logged that the expected `.ini` was missing there. The path divergence demonstrates the AVD discovery cause; why the manager selected `.config` remains unknown. The inventory did not inspect the alternate `.config` path, so `.ini/config.ini` there are not independently hashed. Do not change or rerun the workflow before independent audit.

Run result is **FAIL before instrumentation**, not Android PASS: ADB wait timed out with exit 124; connected tests were skipped; Gradle task NOT_RUN, XML=0, executed=0; physical validation NOT_RUN. Full artifact ZIP and the sanitized job log are preserved and checksummed under `docs/validation/android-emulator-ci/diagnostic-runs/38154790618/`. See `EXECUTION_STATE.json` → `latestAndroidCi.diagnosticFollowUp` for run/source/artifact hashes and layer-separated statuses.

Release classification stays separate: empty Gradle-home attempts are `INFRASTRUCTURE_FAILURE` / `RETRY_EXHAUSTED`, lower-level cause `NOT_DETERMINED`; the older `83affa6` Release success used a pre-provisioned shared Gradle cache; Actions artifact `37874837502` is Debug ARM64 only. No build was run here. Main/tag are unchanged, M01-06 remains `PENDING_REQUIRES_DEVICE`, and the original 97-task plan is unchanged. This executor stops after this run for independent audit.

## Independent orchestration audit — UX-02e (2026-10-12)

The coder's final result was relayed before this audit. Remote branch HEAD at audit start was `51adf9458bddd3bac184137a091f097c3a02b7a1`; comparison from the UX-02e code commit `a1827fa204cda0cdf80a7e5a51670435e8c8d4b7` showed six commits ahead, zero behind, and documentation/evidence paths only. No app or workflow source changed after the UX-02e code commit.

The read-back `RESULTS.md`, `RESULTS.json`, `EXECUTION_STATE.json`, and handoff consistently record the one explicit-JDK21 attempt: preflight PASS, Gradle exit 1 before task execution because the required Java 17 toolchain was not discovered, zero tasks/tests/XML. This is an environment/toolchain blocker, not a product test failure. The standalone parser result remains supplemental 20/20 and does not accept UX-02e. No retry, emulator, APK, or physical-device run is authorized by this contract.

Audit found the nested `latestUx02e.pendingRecovery` object still contained obsolete `assigned:false`, `BLOCKED_EXECUTOR_ACCESS`, and a next step to dispatch the now-completed attempt. That stale state was reconciled in `EXECUTION_STATE.json` by commit `e6e8ed9f29fbb38312d645aabbfb8c631a50e0bb`; the final gate outcome and UX-02e NOT_ACCEPTED state are preserved. No further retry or UX-02f/UX-03/function-backlog work should begin without new user authorization or material evidence.

Main and peeled `v0.4.2` were read back at `de615b772cf37a99e7e08a647c59607b271de606`. M01-06 remains `PENDING_REQUIRES_DEVICE`; the original 97-task plan is not tracked in Git and was not reconstructed or edited.

## Current authorized chat visual refresh and PERSONAL QA ARM64 delivery (2026-10-14)

Product/UI commit `412d61ae1ff9e9da710d6cf6619a935fdb352125` changes only the chat transcript/composer/Context Inspector presentation and adds `ChatVisualSurfacesUiTest`; callbacks and product behavior remain intact. The one post-reflow focused HOST run passed 1/1. Two synthetic Compose captures at 411x900/fontScale 1.6 were opened; these are host captures, not emulator or Motorola evidence. Full JVM suite and lint were not run.

The personal workflow initially failed its broad `.bin` suffix check on run `38491402535`. Inspection of artifact `11861604731` established that its sole match, root `DebugProbesKt.bin` (1,728 bytes, SHA-256 `4d13bd92f86a0b1415062b8ed57655c0738ccfbdf0786c1d730d988b27fd4400`), is compiled Java/Kotlin coroutine debug-probe bytecode, not a model. The same workflow step had an empty `ANDROID_HOME` override. The failed run remains recorded as FAIL and is not the delivered artifact. Evidence and exact local CRC/zipalign checks are in [the failed-run artifact audit](../validation/chat-visual-personal-qa/failed-run-38491402535/ARTIFACT_AUDIT.txt).

Workflow correction `0844159b1c8653c15da6a2f47c098e46d38478ba` added a content-pinned exception for only that exact class entry and restored SDK-root resolution from the runner environment. The corrected workflow run `38492777065` passed every step for that exact source SHA. It logged `/usr/local/lib/android/sdk`, ZIP CRC PASS, no model payloads, and successful `zipalign`.

The downloadable [PERSONAL QA Debug ARM64 artifact 11862891302](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38492777065/artifacts/11862891302) is 47,852,757 bytes with SHA-256 `836a7da89a15ccc1459e9d1ad8ce2d10141ae7b2a1a094624578bd8da6857e42`. The included APK is 119,445,466 bytes, SHA-256 `f3e87a33662784ac9ace0bd4a5779f59589b7bc891f058b0ccf755076fd35765`, package `com.localai.workspace.debug`, version `0.5.0-debug` / 27, arm64-v8a only, 9 native libraries. Its v2 Android Debug signature passed; certificate SHA-256 `39e1d90e77864dfb824bfe716cffe3e9574441e7044db108762e8ed85923f455`. ZIP CRC and zipalign passed. Artifact expires `2026-11-13T01:02:36Z`. This is not Release or production, and no APK is committed to Git.

Layered statuses: PERSONAL QA artifact **PASS**; instrumented/emulator and Motorola **NOT_RUN** by this workflow. The previously recorded global EMULATOR result remains FAIL for the authentic historical Room v1 fixture, with six model-dependent cases skipped. PHYSICAL_DEVICE remains `NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`. UX-02e remains `AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`, M00-04 remains not accepted, and the original 97 task IDs/order/dependencies/progress are unchanged. No main/tag/release ref was modified.

Final run details, all five artifact manifest checks, and artifact RESULTS metadata are preserved in [the readback record](../validation/chat-visual-personal-qa/final-run-38492777065/RUN_READBACK.txt).

## Preparatory memory bundle export and round-trip — 2026-10-16

Code commit `0f31e918efd63601818a01ebcb8529fba5befd9b` adds an explicit ZIP export through Android's SAF `CreateDocument` flow. Follow-up `b6d68b1c443696cf243003ce806f1e425a89af9d` maps sensitive/size rejection codes to readable guidance. Export reads only active, non-expired records accessible through the current `ScopeAccess`; pending records are excluded, vectors are omitted, and heuristic sensitive-data matches stop the export with a visible error. ZIP structure is `AWS_MEMORY_IMPORT/` with bounded entries, manifest integrity hashes, and required Markdown/JSON/JSONL files. Import still creates local USER/PENDING entries; original scope is retained only as provenance, and approval/indexing remain separate. Title and origin metadata survive import/re-export. The selected SAF provider can be local or cloud-backed; the app does not transmit the archive itself.

The exact final source SHA `b6d68b1c443696cf243003ce806f1e425a89af9d` passed focused HOST validation: 13/13 JVM tests across `MemoryBundleV1Test` and `MemoryBundleImportTest`, plus `lintDebug` exit 0. The initial code SHA `0f31e918efd63601818a01ebcb8529fba5befd9b` also passed 13/13; its separate attempt logs/XML are retained. Gradle used 8.10.2 with JDK 17 runtime and JDK 21 test launcher, Android API 35/NDK 27.0.12077973/CMake 3.31.6, and the existing offline Gradle cache. Raw final log, command/environment, task outcomes, JUnit XML and the checksum manifest are in [memory export evidence](../validation/master-program-memory-export/RESULTS.md). No full JVM suite, LiteRT compatibility test, AndroidTest compile, emulator or Motorola test ran. Lint's report task was up-to-date, so no current-run warning count is asserted.

No APK was generated: the prior update-compatible Debug private signing key is not mounted in this executor; no replacement key was created. Motorola remains `PHYSICAL_DEVICE=NOT_RUN`. This work is preparatory and marks no original task ID complete. The archived source backlog still has 97 unique IDs, 120 dependency edges, and all source states TODO; its files were not edited. The backup bundle remains `/workspace/backup/aws-pre-integral-20261016-458864a/repository.bundle` (SHA-256 `1dc13d8142c6f0beac1038aa7a8abc5ec766b88648e32aefcfba45e037e4f925`). `main` and `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

## Preparatory M02-03 — DOCX structure and traceable locations (2026-10-16)

Current tested code/test SHA: `2646c745b6c3393fbdbdad1d49a1b1490a7cd3e8`; production change: `4c91d79fe8f9ffe052a33f710f5818afad5df446`; two test-only follow-ups: `88e4c21899bac393e519ae0b655132bb4e97b83f`, `2646c745b6c3393fbdbdad1d49a1b1490a7cd3e8`. The exact-SHA API 35 focused class passed 3/3 (exit 0; 123s wall; Gradle reported 1m43s; 63 tasks, 3 executed/60 up-to-date). It exercises DocumentParser, Heading1, table cell coordinates and header-row metadata, an empty cell, explicit `w:br` and `w:pageBreakBefore` true/false, and chunk page/character offsets. Plain DOCX paragraphs and tab separators are checked for compatibility.

The baseline red run at SHA `49e4eb15a9ebd1e783a4b1724f9c1b0852d4c0e4` had 1 expected failure: a DOCX with an explicit page break yielded one unnumbered segment instead of two. An intermediate invocation at `4c91d79` passed the two DOCX tests but also selected two unrelated API 29 `OfficeRetrievalTest` cases; both failed before their bodies because Robolectric's `android-all-instrumented:10-robolectric-5803371-i7` is absent from the offline cache and network fetch was refused. This was classified as environment/runtime failure, not product failure. The added API 35 regression verifies the relevant plain-text/table separator contract; the API 29 tests were not rerun.

The implementation retains bounded XML/ZIP handling and does not change database, schema, dependencies, inference, retrieval architecture or permissions. It is not Word visual rendering: natural pagination, headers/footers/notes/comments, custom styles, list numbering, nested/merged tables and images are not implemented. `cell=N` is a physical table-cell ordinal, not a computed grid column.

Evidence: [M02-03 results](../validation/master-program/05-docx-structure/RESULTS.md), [structured results](../validation/master-program/05-docx-structure/RESULTS.json), manifest `SHA256SUMS` (31/31 payloads pass; manifest SHA-256 `ac14d8a1fdd10568dd31ce702d3c85cff71a4a9595c5b3ff80a0f5f6f65d7292`). Final Gradle content/archive/XML SHA-256 values are `3b2eb654ea273443ff595aeb5c84aca37af86f32a6b38ca2e00fffff8b10e8c2` / `68217f00ccb8896be138376a5e2150af703406c74a9f0055d12112c9413142b2` / `18088fc84ca5e1ae713e1515c27588c2cb28da1a7bb469bc740089ea403f1663`. Full logs are preserved losslessly as gzip with original XML, command, toolchain, task outcome and exit code. Full JVM/LiteRT/lint/AndroidTest/emulator/Motorola/APK were NOT_RUN.

This is preparatory partial evidence only. Authentic backlog M02-03 remains TODO because M02-01 remains TODO and depends on M00-10; no original ID, ordering, dependency or source status changed. The archived source files remain 97 unique IDs/120 edges/all TODO (backlog SHA `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`; roadmap SHA `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`). M02-04 is device-required. M02-05 is the next preparatory candidate only; its formal M02-01 dependency remains outstanding. `main` and peeled `v0.4.2` remain at the frozen baseline; no release/APK was made.

## M02-05 exact page locator follow-up — 2026-10-16

At feature base `c3ae446ee5ba7bec1063129303250df4d50c89b4`, the existing M02-05 work already preserved exact spreadsheet/cross-sheet references and EG2 code paths/line citations, but the production retrieval route treated an explicit page locator as ordinary text. A test-only commit `06c5bedb7a349d01fb589ea11c0d8e078e540e7e` reproduced the defect in both EG2 and lexical ContextFoundation paths: page 1 was included for `página 2`; two tests failed (exit 1, 78 s). The raw log is retained. Its build-output JUnit XML was overwritten before copying and is not claimed.

Production commit `c8434a3603a48ebe8547bbb049d6cfdf98a61809` recognizes only explicit accent-insensitive `pagina N` / `page N` locators (1–9999). It constrains EG2 hits, filters precomputed legacy evidence, and uses a bounded Room page-range query for selected/eligible READY documents; absent pages and unpaginated sources abstain. No schema, migration, ranking policy beyond explicit locator scope, project/document access, or stored document content changed. Test commit `0769ca3b370efb19f5770ea06564c2a7cc2e1717` added a legacy case with stale page-1 precomputed evidence to prove it is discarded and page 2 is fetched afresh.

The exact-SHA final focused run at `0769ca3b370efb19f5770ea06564c2a7cc2e1717` passed 2/2 (0 failure/error/skip), Gradle exit 0, 77 s; final JUnit and compressed full log are in [M02-05 exact retrieval evidence](../validation/master-program-exact-retrieval/RESULTS.md). It used Wrapper 8.10.2, JDK 17 Gradle runtime, JDK 21 test worker, existing offline cache, SDK API 35 and NDK 27.0.12077973. The full host gate, lint, AndroidTest, emulator, Motorola and APK were not run. This is preparatory evidence only: M02-05 remains source `TODO` because M02-01 remains TODO pending M00-10 device validation; no original ID or dependency was changed. The next authorized preparation candidate is M02-08 codebase indexing, also partial/preparatory only while its M02-05 dependency remains formally open.


## Master-program increment — M02-08 code archive indexing (2026-10-16)

The focused source commit is `1863a68e407fd40d10fcc4fb13d21afeef94d60b` (tree `731ea3efba9fa4f84575fe2a730774c2491e614c`), based on the existing master-program work branch and intended for fast-forward publication only to `feat/0.5.0-skills-agents`. A committed red test at `dd4bd0c59098692557c8b10176dcf418f3cf8f25` reproduced the flattened-ZIP source name and ignored-directory content. The green focused run used the exact same staged source tree as the code commit and passed 5/5 tests in 246 seconds (63/63 Gradle tasks, exit 0). Full JVM, lint, AndroidTest, emulator, Motorola and APK were not run.

This is **preparatory M02-08 evidence only**. The original M02-08 task remains TODO and depends on M02-05; no dependency or original task status is changed. EG2/Semantic V2 returns a relative code path and line interval in the synthetic scoped integration fixture, ignores the tested root/nested Git-ignore folders, and does not surface a homonymous foreign-project result. The ignore grammar is a safe documented subset, not full Git compatibility. At that initial implementation stage, lexical fallback still lacked code-line coordinates; the follow-up below closes that measured gap. Symbol-aware retrieval is not implemented.

Evidence and source-plan checks are at `docs/validation/master-program-codebase-indexing/RESULTS.md`; the manifest now includes the follow-up attempts (61 payload entries, all verified locally). Backlog and roadmap originals remain byte-identical at their recorded SHA-256 values, with 97 unique IDs and 120 dependencies; no source task states were changed. The physical Motorola/performance/model gates remain unavailable and unclaimed.

## M02-08 lexical fallback follow-up — 2026-10-16

The codebase-indexing preparation now has two code commits after the initial implementation: `9cdee355943ff62656ad8973b13acb7b39766772` adds the red final-context regression, and `a6037e8d0260386a4f42448b05f81c251208ea7e` preserves lexical fallback source path and matching stored line ranges through context/citation provenance. A test draft briefly expected a symbol-start line instead of the whole indexed chunk; the recorded 1-test failure was a test expectation mismatch and was corrected to assert lines 1–4 without weakening coverage.

The exact committed source tree `8a67620f2a00e5df4a0c6717944c6c3e450beead` passed the focused 6/6 host run on 2026-10-16 (32 s, exit 0; Gradle 8.10.2 offline, Temurin 17 runtime/21 worker, SDK API 35/NDK 27.0.12077973). The XML, command, task outcomes and compressed full log are in `docs/validation/master-program-codebase-indexing/attempts/attempt-09-committed-source-final/`; the results and checksums are in the same directory's parent report.

This is still preparatory M02-08 evidence, not original task completion. M02-08 remains TODO behind M02-05/M02-01/M00-10. No full JVM gate, lint, AndroidTest compile, emulator, Motorola, APK or model-performance measurement ran in this follow-up. Preserve all 97 original TODO task IDs, order and 120 dependencies. The automatic emulator/QA runs triggered by the prior publication are for the earlier SHA `661c7e2a83b9f629f79dd79175da19337a6d7a3d`; they are not evidence for this code SHA. No main/tag/release refs were changed.


## Preparatory M02-07 citation source-segment navigation — 2026-10-16

Code commit `ab0b14f128fdc12907391ddab0ca6f36f9f3465f` links each citation chip to its persisted `CitationEvidenceEntity` for that assistant message and adds an explicit action to show the exact indexed segment. Legacy rows are checked against the cited document and active project. Semantic V2 rows are checked by exact segment ID, source type, authorization state, TEXT/CODE modality, PROJECT/SESSION scope and exact local path. If the linked segment is unavailable, the preview uses only the saved citation excerpt and says why; it never searches another document/project or presents an excerpt as a full-document review. The original evidence ID callback is unchanged. DAO additions are read-only; no entity/schema/migration, retrieval, inference, permission, or dependency change was made.

The API 35/offline focused host run at `docs/validation/master-program-citation-navigation/attempts/attempt-06-focused-host/` passed 9/9 across four JUnit XML files (0 failures/errors/skips), Gradle 8.10.2, JDK 17 runtime and JDK 21 worker. It covered exact legacy and Semantic V2 source selection, wrong-document/scope fallback, conversation/message grouping, the persisted citation query and accessible preview at font scale 1.0/1.6. Attempt 05's one assertion failure and malformed extra test filter remain preserved; attempt 06 used the corrected assertion and exact filters. The code commit contains the same app source/test content as the run; only untracked evidence files were present during execution.

This is **preparatory evidence only**. Original M02-07 remains `TODO`, `requires_device=true`, and depends on M02-05; M02-05 and upstream M02-01/M01-05 remain formally open. M01-06 remains `PENDING_REQUIRES_DEVICE`. The canonical source files were read-only and retain SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` (backlog: 97 unique IDs/120 edges/all TODO) and `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5` (roadmap). Full JVM/LiteRT gates, lint, AndroidTest compilation, current-SHA emulator, Motorola and APK were not run in this increment. The prior automatic run `38967472318` is for source `a42135088ad87d7a3e483570f3b915bdd34dd966`: emulator FAIL from the historical missing Room v1 fixture (9 pass, 1 fail, 6 skipped, 10 executed); QA `38967472333` failed closed with no artifact. A new source-SHA run is pending publication. See [M02-07 evidence](../validation/master-program-citation-navigation/RESULTS.md) for attempts and hashes.

Next host-preparation candidate: review M02-06's authentic stale-index/reindex DoD and production invalidation paths. Preserve its source status/dependency; M02-05 remains TODO. Do not report device completion or select E4B from host evidence.

## 2026-10-16 — current preparatory M03-06 checkpoint

The code patch preview increment is committed locally as `7c2ab7a603b67b8f8058959a16164a4c1e7781bd`; the focused-test-only follow-up is `3f6387b304d615cca6d5f403d6ac938d41c964a7`. It targets generated private CODE artifacts only. A model tool can prepare a bounded, exact SHA-guarded preview; code bytes remain unchanged until explicit UI approval creates a new immutable private version. No source path is accepted and no code is executed. The UI and ArtifactStore integration regressions compile but could not start under Robolectric because `android-all-instrumented:10-robolectric-5803371-i7` was uncached and repository access returned connection refused. The generated XML for that failed run was replaced by the following Gradle invocation before copy; the raw log remains, and no XML is claimed for that attempt.

Host evidence: `CodePatchPlannerTest` 3/3 on both `7c2ab7a` and `3f6387b`; `:app:lintDebug` exit 0 on `7c2ab7a` (77 warnings, 1 information, 0 errors, none in touched files). Full app JVM, LiteRT compat, assembleDebug, AndroidTest, emulator and physical device were NOT_RUN for these code commits. The new artifact does not yet have a matching-source APK build. The 2cebd17 push-triggered personal APK belongs to older code and was not locally rehashed after its download returned HTTP 403. Its reported certificate (`706cead75d02faf718e67560008c8b9a4b054c80be081d356786add157c5823c`) differs from the recorded previous personal APK certificate (`f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`); update compatibility is not established. A matching local debug keystore was not found at the standard paths checked. Do not advise uninstall/clear-data.

The authentic plan remains unchanged at 97 unique TODO IDs/120 dependency edges. M03-06 remains TODO and device-required; this increment does not patch user-selected workspace files and does not satisfy rollback-on-device. M01-06 remains PENDING_REQUIRES_DEVICE. The canonical plan files retain their recorded hashes. A local recovery archive for the pre-resume dirty state is `/workspace/backups/aws-pre-resume-20261016-215330/` (archive SHA `164f76a3cf5f6359bc36f982730063717b04d83b0f1e61809e431834e25b4809`; manifest SHA `3d7232d8da931958bbe91e62ff0b35571eba25b66b550111c214621d55472504`).

Next blocker is access to the exact Robolectric Android runtime for running the compiled store/UI regressions; the formal M03-06 workspace and SAF validation also requires the Moto G86 and a user-selected workspace. Do not promote any original task status. Evidence manifest `docs/validation/master-program-code-patch/SHA256SUMS` covers 47 payload files and verified locally 47/47; SHA-256 `f8774baae005d4025a8fc93da1526cc662ee4ec3328b6298fa71f9023a731cfa`. Detailed attempts, action runs on the preceding SHA, and limitations are in `docs/validation/master-program-code-patch/RESULTS.md`.


## Master program: current M03-06 preparatory code patch and Actions readback — 2026-10-16

Code patch preview: `7c2ab7a603b67b8f8058959a16164a4c1e7781bd`; focused-test follow-up: `3f6387b304d615cca6d5f403d6ac938d41c964a7`; current published head: `9cfb0d8112342ee097ac64aa573e5d35b446f834`. The original M03-06 remains TODO/device-required; no original task ID or dependency was changed.

Exact-source Actions: emulator run 38974433001 is FAIL (16 total, 9 pass, 1 fail, 6 skipped, 10 executed); the Room migration test cannot load authentic schema v1. Six actual-model cases were skipped. ChatHeader semantic assertions pass, but only the standard synthetic screenshot is valid; large-font screenshot transfer is incomplete. `applicationApkInstalled=false` is a post-run collector observation only. Physical Motorola remains NOT_RUN. The evidence bundle is preserved under `docs/validation/master-program-code-patch/actions/emulator-38974433001/`.

Personal QA run 38975236297 is PASS only for building/validating a Debug ARM64 APK. APK SHA-256 `fc18e2d802814cf0e37b1a8edbc81d73740a9a8cd849684e5153e5bf1953730f`, size 120,051,674 bytes; package `com.localai.workspace.debug`, version 0.5.0-debug/27, arm64-v8a only; apksigner v2, ZIP CRC and zipalign PASS; no model weights included. The GitHub artifact id is 12078078659, 48,074,199 bytes, digest `2275b4382b63903f8c1afb7b2b1c26181fba81bc36f546c949ad866ef23fb491`, expiry 2026-11-15. The APK certificate `635bc8…` differs from the previous recorded Drive build (`f9085e…`), so it is not safe as an update; do not uninstall or clear data. The APK-only ZIP is on Drive id `1kiiLJWiW-dnCnArZVw4TVj6GIcXdPWri`; its Drive readback bytes match SHA-256 `da911324c5d1ef81a4b81667b63d113bed236f06a5fba5672b0c5d21992642ca`. Direct APK upload was rejected by the connector's 100MiB limit.

The original 97 IDs, ordering, 120 dependencies and TODO source statuses remain unchanged. Next work is a source-grounded review of M02-06 stale-index invalidation followed by a bounded implementation only if a concrete production gap is demonstrated; no Robolectric retries without the missing runtime dependency.

## Current master-program checkpoint — 2026-10-17

The verified pre-edit backup is at `/workspace/backups/aws-pre-integral-20261017T020113Z/`; its complete Git bundle SHA-256 is `09901498b8d70545ae7c1266685b0cd46ad94aa74e061d22f1bf1edcc3505896`. `BACKUP.md` contains restore commands. The initial untracked Semantic V2 recovery test is preserved separately; ignored caches/build outputs were not copied or deleted.

Preparatory M01-06 source commit `b48338c557a94351157de18378a5221d00a4ba43` adds restart classification/recovery and exact stored-vector reuse. Tested tree is `2313d77ab258beb6dd72b940a28da60772656adc`. Focused HOST tests passed 3/3; lint and AndroidTest Kotlin compilation passed, both exit 0. A separate API 29 Robolectric composite was blocked by an uncached runtime artifact/network refusal. No full JVM/LiteRT gate, physical device, APK or product speed benchmark is claimed. The original M01-06 entry remains TODO / `requires_device=true` / `depends_on=[M00-02]`; no source task is closed.

Baseline run `38992803542` on parent `77f86c0` is EMULATOR FAIL (16 total, 8 PASS, 2 FAIL, 6 model-dependent SKIPPED): authentic Room v1 schema unavailable and `AgentsSkillsNavigationTest.generalAgentIsProtectedAndValidationIsDeveloperOnly` cannot find the expected visible text. QA run `38992803632` failed closed with no artifact. Both observations are tied to the parent SHA, not the recovery commit. Current-SHA Actions run IDs and exact results will be appended after their completion under `docs/validation/master-program-semantic-recovery/RESULTS.md` and `EXECUTION_STATE.json`.

The canonical plan source remains untouched (97 task IDs, 120 dependency edges, all original statuses TODO). `main` and peeled `v0.4.2` remain frozen at `de615b772cf37a99e7e08a647c59607b271de606`.

## Master program checkpoint — M00-06 import/index correlation (2026-10-17)

The new code `9dc50102f47d551d9d3d0298c65106e802d1021c` links successful document-import timings to the asynchronous EG1 project-index status with a random opaque UUID, only when the callback confirms that indexing was scheduled. A versioned synthetic XLSX test drives production ingestion and `SemanticRetrievalService.indexProject`, verifies the matching IDs/stages, and checks that diagnostics omit file/project names, paths, content hash and cell text. Performance UI now distinguishes index callback timing from background indexing timing and reports when latest diagnostics have been superseded.

HOST: exact source tree `daa6c615534b445ef37196fabdde6242cb99f1fa`, focused JUnit 2/2 PASS; `lintDebug` PASS (0 errors, 77 warnings, 1 informational, none in changed files); `compileDebugAndroidTestKotlin` PASS_COMPILE_ONLY. Commands and immutable output evidence are in `docs/validation/master-program-m00-06/RESULTS.md`. A first compile failure was only a missing test coroutine import, corrected before final passing XML; both attempts are retained.

The exact-SHA push-triggered emulator run `38997371699` and QA gate `38997371673` have completed; see the current checkpoint at the top of this handoff for the reconciled failure/incomplete counts, artifact, and no-APK result. This increment does not produce an APK. `M00-06` remains source `TODO` because its cross-stage request timeline and actual-device metrics are incomplete; neural speed/quality/memory/energy/thermal are not measured. No original plan IDs, order, dependencies or statuses changed. PHYSICAL_DEVICE remains NOT_RUN; the source backlog remains 97 unique IDs and 120 edges, all `TODO`.

## Workspace recovery and M03-07 host checkpoint — 2026-10-19

The prior local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` could not be recovered: available workspace/mount/temp locations contained no prior checkout or patch, and GitHub lookup returned HTTP 422. No source was reconstructed as if recovered. The exact feature head `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc` is preserved by remote ref `recovery/backup-b8e75b4`; implementation proceeds on independent branch `codex/master-program-20261019`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

M03-07 receives a focused host regression only. Code commits `4d7014a`, `8feb93f`, `0412f60` change one test file and no production files. The final exact-SHA test passed 1/1 on `0412f605ed090c2c5ced147d297d9a9fde0781db`: before/proposed preview visibility, export affordance for saved v1, and cancel/close preserving the saved revision count, bytes, and SHA-256. Attempt 03 failed before test execution due to a missing test import. Attempt 04's test failed because the test incorrectly scrolled the AlertDialog Close action; its raw log and the observed failure note are preserved, but the original XML was overwritten before archival. See `docs/validation/master-program-m03-07/RESULTS.md` and its 34-entry payload manifest (`ad0a1589d993ed728a1f4a44d9ef56e924d402984d5434137b64808945997369`).

This is not M03-07 acceptance: source status remains TODO; the original task requires a device and depends on M03-01, also TODO. This run does not establish all format previews, a generic unified diff, real SAF export, or device usability. Full host suites/lint/build, emulator, physical device, APK and performance measurement were not run. Original plan files remain unchanged at 97 IDs/120 dependencies; M01-06 remains PENDING_REQUIRES_DEVICE. Continue only under the authentic task graph; do not mark blocked tasks complete.

## Master-program checkpoint — M03-01 artifact API host preparation (2026-10-19)

The current test-source SHA is `1b82c05c65edfe173fe2d968c85dc38598f2f769`; no production file changed in this increment. Existing artifact implementation/tests were inspected for bounded specs, immutable app-private versions, byte/spec hashes, atomic publication, read-back/recovery, preview and explicit SAF export. The M03-01-focused API/preview/SAF slice passed 13/13 across four suites in attempts 01 and 02. These are synthetic Robolectric results and remain preparatory only.

The separate code-patch UI test had two failing cases in the composite runs and two failures in the class run. The test was corrected to wait for its asynchronous preview row and compare the exact emitted status. The isolated discard case passed 1/1 on `1b82c05`; the apply case later passed 1/1 on `f8a4093` after the assertion correction. Both runs have fresh XML and captured Gradle exit 0, while their outer execution-tool sessions returned 1; the discrepancy remains `NOT_DETERMINED`. Attempt 05 was UP-TO-DATE and executed no test. See [`M03-01 results`](../validation/master-program-m03-01/RESULTS.md), per-attempt compressed logs/XML, and its checksum manifest.

No full app/LiteRT gate, lint, AndroidTest compilation, emulator, Motorola or APK ran in this increment. Original M03-01 stays `TODO`, depending on M02-05; M02-05 and M02-01/M01-05 remain TODO, and M02-01 depends on M00-10. M03-07 remains TODO and device-required. M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. The canonical backlog/roadmap files remain unchanged: 97 unique IDs, 120 dependency edges, all source statuses TODO. No original task is closed by this checkpoint.


## Master-program M03-03 — built-in DOCX style templates (2026-10-19)

Current tested HEAD: `56090d0e4d5c686a9346a5359dfd18d86e7c5d78` on `codex/master-program-20261019`; production commit `1cdcddb471eb795b526e16b4cd43997176958ba1`. Test-first commit `90cf6cb0e81c7d378cdc774799c981f15636247c` reproduced the parser rejection `ARTIFACT_FIELD_UNSUPPORTED`. The final focused HOST run passed 4/4 (3 DOCX structure/revision tests plus 1 Compose selector test), Gradle exit 0, 49 seconds. `python-docx 1.2.0` reopened both generated template fixtures, validated ZIP CRC, paragraph/table order, text and resolved font/color/size. Full commands, raw-log gzip/XML, sample files, reader script/output and hashes are in `docs/validation/master-program-docx-templates/RESULTS.md` (manifest SHA-256 `0ed2a7266c2fc727214dd189756905f84043ec26d0ce53b6094a6b67d94f561e`, 16 entries).

The implementation adds DOCX-only `standard` (byte-identical to the legacy styles part), `report` (Calibri, #3E746D), and `meeting_minutes` (Arial, #1D2522) presets. The Artifacts UI can select them, the ArtifactSpec retains the choice across immutable revisions, and the tool schema describes them. No dependency, DB, schema, migration, inference or permission changes. The imported-file editing route is unchanged.

This remains preparatory evidence; original M03-03 stays TODO, `requires_device=true`, and depends on M03-01 and M02-03, both TODO. No original task ID/order/dependency/status changed. Backlog/roadmap source hashes remain the authentic 97-ID/120-edge all-TODO values. `lintDebug`, full JVM, LiteRT and AndroidTest compile were not run in this increment; the prior offline artifact blocks remain in the ordered-blocks evidence. EMULATOR and PHYSICAL_DEVICE were not run here; the persisted global Room v1 failure/six model skips remain unchanged. No APK was generated. M01-06 remains `PENDING_REQUIRES_DEVICE`.

## Master-program M03-04 — real PDF table layout preparation (2026-10-19)

Code/test SHA `effb4ccd8089e04b98ffa4f6af6d6fdacb4bf475` on `codex/master-program-20261019` fixes a reproduced output gap: PDF table rows were plain text joined with `|`; they now render as bounded cells with borders, wrapped text and page-split long rows. The test-first red run failed specifically because the separator remained in extracted text; the exact final `PdfArtifactLayoutTest` run passed 2/2 (the existing word-boundary/revision case plus the new grid/pagination case), Gradle exit 0. Gradle reported 46 seconds; captured start/end timestamps span 47 seconds. PyMuPDF 1.26.6 and pypdf 6.10.0 independently read the synthetic three-page output: all 300 repeated cell tokens remain, the grid is vector content, no pipe delimiter remains, and text/drawings are inside page bounds. The first-page render was inspected and is retained with the PDF, JUnit, full logs and checksums in the [M03-04 PDF table evidence](../validation/master-program-pdf-table-layout/RESULTS.md); manifest SHA-256 `a2ec21748b01203baeef5f02c7bf4da109490ff1016eab0a9e34efe101e42d51` (18 entries).

This is preparatory only: original M03-04 remains `TODO`, `requires_device=true`, and depends on M03-01 `TODO`. No source task, ID, order, dependency or status changed; the source backlog remains 97 unique IDs, 120 dependency edges, all 97 `TODO`. No full JVM/LiteRT gate, lint, AndroidTest compile, emulator, Motorola or APK ran for this increment. PDF fonts remain the built-in Helvetica family; continuation pages do not repeat table headers, imported-PDF editing and mobile-reader fidelity remain unverified. Historical EMULATOR remains FAIL for the unavailable authentic Room v1 fixture plus six model-dependent skips; `PHYSICAL_DEVICE=NOT_RUN`; M01-06 remains `PENDING_REQUIRES_DEVICE`.

## Recovery continuation and exact-current-source checks — 2026-10-19

The recovery prompt referenced feature HEAD `b8e75b4`, but the actual fetched feature and existing implementation branch were both at `95199cd3f7f5703bf436de4214cb42e4721e2787`. The prior backup `recovery/backup-b8e75b4` still points to the original `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`; a second remote backup `recovery/backup-aws-20261019-95199cd` now preserves the actual current HEAD. The working tree's untracked M03-05 evidence bundle was also copied to `/workspace/recovery-backups/m03-05-roundtrip-untracked-95199cd.tar.gz` (SHA-256 `3337fcfaa8e3dbdf12eeb840e7caccb01dc25d708c8cff29ad2492c82c374e3c`) before continuing. The reported lost local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` remains `NOT_RECOVERED`; no source was reconstructed. The already-existing `codex/master-program-20261019` branch was reused; no duplicate work branch was created.

At exact source checkout `95199cd`, the current-source M03-05 `PptxImageArtifactTest` passed 3/3, Gradle exit 0 in 29 seconds (63 tasks executed). `python-pptx 1.0.2` reopened the generated image-bearing v1 and immutable edited v2; ZIP CRC, Blank layout, master/theme, one picture, alt text and unchanged embedded PNG hash all passed. This is synthetic single-slide HOST evidence only; no external Office GUI, Android viewer, emulator or device validation. Full command/log/XML and fixtures are in [`PPTX current-source roundtrip`](../validation/master-program-pptx/roundtrip-v2-current-sha-95199cd/), child manifest SHA-256 `a3bc59ab7dfda994ee451adc2e48852f5e794b3d88ef08c8e2c362b7646bf62c` (22 entries); enclosing PPTX payload manifest: 178 entries, SHA-256 `92c773d3e62b86f7eb127cb49209afcf1dab087761cfbf77218a94c3c0646f90`.

At the same exact source checkout, `ArtifactRevisionPreviewUiTest` passed 2/2, Gradle exit 0 in 3m27 (63 tasks executed). It verifies before/after visibility, bounded line diff, saved-v1 export affordance, and cancel preserving the original saved bytes/hash. Relevant UI/test files have no diff from the earlier `bceb60a` check, but this run is attributed to `95199cd`. Details/XML/log and hashes are under [`M03-07 exact-current revalidation`](../validation/master-program-recovery-20261019/m03-07-current-sha-95199cd/), child manifest SHA-256 `f8352ccb265beb15fdb36ffa3847ba6596665ec8622b1d27985866ccf44f7ee8` (16 entries).

Neither source task is formally complete: M03-05 and M03-07 remain `TODO`, both require a device and depend on M03-01 `TODO`. No full host gate, lint, AndroidTest, emulator, Motorola, APK or model benchmark ran here. Source plan files were not changed; their exact 97 IDs/120 dependencies/all-TODO statuses remain intact. Next work must remain preparatory and source-backed; do not infer completion from focused host checks.

## Current-source multi-slide PPTX image roundtrip — 2026-10-19

Test-only source commit `a5c5a432666d0bc1ee4749d6d9f5d9a36840a797cb` adds multi-slide coverage to `PptxImageArtifactTest.kt`; no production source changed. The exact focused test class passed 4/4 on this SHA (0 failures/errors/skips), Gradle exit 0, 50 seconds, 3 tasks executed and 60 up-to-date. `python-pptx 1.0.2` independently reopened synthetic v1/v2 packages and verified two ordered slides, layout/master/theme, per-slide image relationships, distinct PNG bytes, alt text, ZIP CRC and image preservation through an immutable title revision. Full command, environment, logs, JUnit, fixture hashes and reader output are in [`multi-slide PPTX evidence`](../validation/master-program-pptx/multi-slide-current-sha-a5c5a43/); child manifest SHA-256 `737c7dfef3a63fda1f4d8c46b094661dd6feb839aaddcde5146e412152365472` (26 entries).

This is synthetic HOST-only evidence, not Office/Android/Moto G86 validation. No full suite, lint, AndroidTest, emulator, physical device, APK or model benchmark ran. M03-05 remains source `TODO`, requires device validation and depends on M03-01 (`TODO`); no original task is closed. The canonical 97 task IDs, 120 dependency edges and all-`TODO` source baseline remain unchanged. The next preparatory step is to inspect the exact M02-05 DoD, dependencies and current retrieval implementation for one concrete remaining host-testable gap before changing code.

## Preparatory benchmark cohort metrics presentation — 2026-10-19

Code SHA `f21939773735ff17f338d571cadc793484f0bd01` adds p50/p95 display for existing same-cohort model-load, model-preparation, prefill, app/worker PSS endpoint maxima and available-RAM-after measurements in the Performance screen. Cohort construction still separates device/model/task/configuration/backend/TTFT-boundary; failed and explicitly quality-rejected samples do not improve speed summaries. Unknown metrics remain unavailable. The screen states that PSS is based on before/after endpoints and is not peak generation memory. No metric collection, runtime, model, database or dependency changed.

The exact-code focused `BenchmarkSummaryTest` passed 5/5 (0 failures/errors/skips), Gradle exit 0, 63/63 actionable tasks executed, 2m59s. Environment was Gradle 8.10.2, JDK 17 Gradle runtime/JDK 21 test worker, existing Android API 35/NDK 27.0.12077973 toolchain and offline `/workspace/.gradle` cache. Command, environment, full log, task outcomes, fresh XML, source diff and checksums are in [`benchmark cohort evidence`](../validation/master-program-benchmark-cohort-metrics-f219397/RESULTS.md); SHA256SUMS has 12 entries and digest `f92f5e8c05c4c21d08dfacbb13012c55996f6d55ff299824c781e6e35615bbf3`.

This is synthetic focused HOST validation only; no Moto/model performance was measured. Full JVM/LiteRT, lint, assembly, AndroidTest, emulator, physical device and APK were not run. M00-06 remains source TODO because its full end-to-end timeline is incomplete. M01-01 remains TODO, requires the Moto, and depends on M00-10; this display work does not create the device protocol or evidence. The original catalog files remain unchanged: 97 IDs, 120 dependency edges, all source statuses TODO. PHYSICAL_DEVICE and this increment's EMULATOR state are NOT_RUN.

## 2026-10-19 — repeated headers on continued PDF tables

Code SHA `786f979ba50a1bd2d609712e297bec24909a0ac5` fixes a reproduced generated-PDF layout gap: the first table row appeared only on page one. The test-first red run at `b2681a631cd6e7a91bcc196e83c3524918c347a` failed with page results `[true, false, false]`; the same focused `PdfArtifactLayoutTest` then passed 2/2 on the exact final code SHA (Gradle exit 0; 63 actionable tasks, 7 executed). pypdf 6.10.0 and PyMuPDF 1.26.6 independently reopened all three synthetic pages, found the header on every page, confirmed it precedes continuation text, preserved all 300 repeated data tokens, and found no out-of-bounds words. Full commands, XML, logs, PDF, reader output and checksums are in [M03-04 repeated-header evidence](../validation/master-program-pdf-table-header-20261019/RESULTS.md); the 24-entry manifest digest is `94bc18c729e1402f78bc3bd7f047855058e51ebd80ef8c18f70b412a0f348a8d`.

The renderer reserves room for one body line after a repeated header; an exceptionally tall header that cannot meet this bound is not repeated. This remains synthetic focused HOST preparation only: no full JVM/LiteRT, lint, assembly, AndroidTest, emulator, external office/mobile-reader or Motorola validation, and no APK. Original M03-04 stays TODO, requires a device, and depends on M03-01 TODO. The original backlog remains 97 IDs, 120 dependencies and all source statuses TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`, and `PHYSICAL_DEVICE=NOT_RUN`.

## 2026-10-19 — XLSX style/number-format metadata and recovery boundary

The interrupted local commit `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was not available as a Git object; it was not reconstructed. The exact named recovery backup remains `recovery/backup-b8e75b4` at `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`. At recovery inspection, the published feature branch had advanced to `e6f16ac30914fe32f7140241e81e5976660c03d2`; implementation continues on the already isolated recovery branch `codex/master-program-recovery-20261019`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`.

A preparatory M02-02 fidelity increment is now at code SHA `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea` (test-first commit `d3064ffe729ae1be9ab1feb10dab340a2358d098`). The new regression failed on the missing style-to-number-format association; the exact `StructuredDocumentsTest` class then passed 11/11. The parser carries a cell style index, raw number format ID, and custom format code into structured metadata while preserving the stored value, type, formula, and cached value. It does not convert date serials or calculate formulas. Evidence and SHA256SUMS are in `docs/validation/master-program-xlsx-style-metadata-20261019/`.

An `openpyxl 3.1.5` attempt is explicitly `NOT_VALIDATED`: the minimal parser ZIP fixture lacks `[Content_Types].xml`, so the reader stopped before cell inspection. It is not counted as a pass. Full suites, lint, assembly, AndroidTest, emulator, physical device, and APK were not run for this increment. Source M02-02 remains `TODO`, depends on M02-01 (`TODO`), and is not formally complete. The canonical plan is unchanged (97 unique IDs, 120 dependencies, 97 source `TODO` statuses).

Next: verify the surviving M03-07 preview/diff/cancel implementation at the exact current source. It remains preparatory only; M03-01 is `TODO` and the source task requires device validation.

## 2026-10-19 — M03-07 preview current-source revalidation

The M03-07 preview, bounded line diff, saved-version export affordance, and cancel safety implementation was already reachable in the recovered history (`bddf6827` plus test commit `fad7acb`); the unavailable local commit `2f8b0d4` was not recreated. On exact current code SHA `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`, the focused `ArtifactRevisionPreviewUiTest` passed 2/2 (Gradle exit 0, 63/63 tasks executed). It verifies visible before/after and added/removed lines, export remains attached to saved v1, cancel preserves saved bytes/hash, and large diffs retain full before/after text. Evidence and the 11-entry payload manifest are under `docs/validation/master-program-m03-07-revalidation-20261019/`.

This remains HOST-only preparation. M03-07 is still source `TODO`, `requires_device=true`, and depends on M03-01 (`TODO`); no formal source task was closed. Full JVM/LiteRT, lint, assembly, AndroidTest, emulator, Moto, APK, and model/performance checks were not run in this revalidation. The preceding M02-02 XLSX style metadata increment is independently recorded at `docs/validation/master-program-xlsx-style-metadata-20261019/`; its external OpenXML reader attempt is `NOT_VALIDATED` because its parser fixture is not a complete OPC package.


## 2026-10-19 — M03-06 preparatory fix: multi-edit ordering

At baseline code SHA `efb5a089268be3b03238b97561b07733a44b071f`, a focused test reproduced a code-patch planner defect: after replacing `alpha` with `beta` in `alpha beta`, the next edit searched the mutated result and treated `beta` as ambiguous. Code commits `aebd6bbe03d4f958cce526dd4d2f98d2883e0841` and `f235a2385d0f885291256ba7ed7299aceeee36a4` now resolve all unique edit ranges against the immutable original body, reject overlaps, and apply replacements right-to-left. The final exact source SHA `f235a2385d0f885291256ba7ed7299aceeee36a4` passed `CodePatchPlannerTest` 5/5 (0 failures/errors/skips), Gradle exit 0, 63/63 tasks executed, 192 seconds by UTC timestamps. The expected-red baseline was 3/4; the test-only run first failed at wrapper download, and the initial implementation attempt had a Kotlin compile error. All attempts remain separately recorded. See `../validation/master-program-code-patch-order-20261019/RESULTS.md` and its 29-entry SHA-256 manifest.

This is a focused HOST preparatory slice, not M03-06 completion: the original task stays TODO, requires a device, and depends on M03-01 and M02-08, which remain TODO. Full app/LiteRT suites, lint, AndroidTest, emulator, physical device and APK were not run. Source backlog/roadmap remain unchanged: 97 IDs, 120 dependency edges, all source statuses TODO.


## 2026-10-19 — M03-06 actual ArtifactStore multi-edit integration

On exact test/code SHA `498519c696ee19bc53c734a110cabee46849fe9b`, `ArtifactRoundtripTest.multiEditPatchSurvivesPreviewReopenAndCreatesAnImmutableRevision` passed 1/1, zero failures/errors/skips, Gradle exit 0. It exercised `stageCodePatch`, persisted and reopened the preview with a new `ArtifactStore`, applied it as immutable version 2, and verified the original body/version remained unchanged. The previous planner class remains 5/5 at code SHA `f235a2385d0f885291256ba7ed7299aceeee36a4`; no production file changed between those two tested SHAs. XML and complete logs are included in the 36-file local manifest at `../validation/master-program-code-patch-order-20261019/`.

This is preparatory HOST evidence only. M03-06 remains source TODO because device validation and dependencies M03-01/M02-08 are open. No full suite, lint, AndroidTest, emulator, physical device or APK was run. Original source plan stays 97 unique IDs, 120 dependency edges and all statuses TODO.


## 2026-10-19 — M03-06 malformed UTF-16 patch rejection

The test-only red commit `7b737dc0a0a584a69a617e18166d580374ad26f9` reproduced that a half-surrogate `before` fragment was accepted against a valid supplementary character and returned malformed text. Production commit `9778e4f5dd4ea5358011189e3be366351747c86f` validates well-formed UTF-16 for the source body and both edit strings, refusing invalid input with `CODE_PATCH_INVALID_UTF16`. On the exact source SHA, `CodePatchPlannerTest` passed 6/6 and the persisted `ArtifactStore` multi-edit preview/reopen/apply test passed 1/1; both runs exited 0. See `../validation/master-program-code-patch-unicode-20261019/RESULTS.md` and its 23-file checksum manifest.

This is focused HOST preparation only. M03-06 remains source TODO, device-required and dependent on M03-01/M02-08, both TODO. Full gates, emulator, Motorola and APK were not run. Source plan is unchanged: 97 IDs, 120 dependency edges, all 97 statuses TODO.

## 2026-10-19 — recovery branch continuity and exact-source artifact revalidation

The requested checkpoint `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc` remains preserved by remote ref `recovery/backup-b8e75b4`. The actual feature branch was at `e6f16ac30914fe32f7140241e81e5976660c03d2`, 81 commits ahead of the requested checkpoint. Work continues in independent branch `codex/aws-master-resume-20261019`, initially based on e6. Thirty-two already-existing commits were fast-forwarded from the available local recovery clone to `f4f69f5980401bc6ae87bab65119134f8b57fc92`; the lost SHA `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was not found in the available objects/refs/reflogs/bundles and was not reconstructed. The prior volume was not separately mounted. Preflight details and the two new focused test runs are under `../validation/aws-recovery-m03-07-20261019/`.

On exact code SHA `f4f69f5980401bc6ae87bab65119134f8b57fc92`, the M03-07 preview/SAF classes and PDF layout class reported **7/7 JUnit PASS** (0 failures/errors/skips), Gradle internal exit 0 and `BUILD SUCCESSFUL`, 63/63 tasks executed. The outer `functions.write_stdin` session reported exit 1 for this run, as it did for the prior e6 run; that discrepancy remains unexplained and is explicitly retained. This is focused HOST evidence only. The original M03-07 task remains TODO, requires a device, and depends on M03-01 TODO. Full suites, lint, AndroidTest, APK, emulator and physical device remain NOT_RUN.

At the same f4 SHA, `PdfArtifactLayoutTest` passed 3/3. The direct run exited 0; Poppler/pypdf independently read the generated synthetic PDF and confirmed the tested extended Unicode text and embedded subset fonts. Evidence and the 53-entry payload manifest are under `../validation/master-program-pdf-unicode-20261019/`. M03-04 remains TODO/device-required and depends on M03-01 TODO. Noto Sans does not cover CJK or emoji; unsupported glyphs fail explicitly. No APK/key was created.

Plan-source integrity remains unchanged: backlog SHA `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`, roadmap SHA `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`, 97 unique IDs, 120 dependency edges and 97 TODO source statuses. M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. `main` and peeled `v0.4.2` remain at `de615b772cf37a99e7e08a647c59607b271de606`. No APK is included in this recovery.

Next bounded preparation, under the master-program authorization: inspect the authentic M02-01 fixture-corpus DoD and existing test fixtures, then create a small versioned synthetic corpus only if it adds real reusable coverage. This preparation would not complete M02-01 because its source dependency M00-10 remains TODO. Do not change source task states or dependencies.

## Current master-program increment — ArtifactStore symlink cleanup boundary

The exact test/code SHA `96359377838e2dfe1670604a73354fbe09ec6b57` adds one regression to `ArtifactPatchRecoveryTest`; production code is unchanged. `listingNeverFollowsAbandonedStagingSymlinksOutsideArtifactItem` passed with the class: 4/4 JUnit, zero failures/errors/skips, Gradle exit 0 and outer exec exit 0. It checks nested-link cleanup, an escaped `.pending-UUID` link, external target bytes, and committed revision bytes. Full command, environment, log, fresh XML and checksums: [`ArtifactStore recovery results`](../validation/master-program-artifact-recovery/RESULTS.md).

This was a focused offline HOST run using existing ignored build/cache state, not a clean-checkout build. No full gates, AndroidTest, emulator, device or APK ran. M03-01 remains source `TODO` because M02-05 is `TODO`; no source task was closed. M02-01 synthetic corpus preparation is already evidenced and must not be repeated. Source plan remains 97 IDs and 120 edges unchanged.

## 2026-10-19 — conversational Skill authoring checkpoint

The active continuation branch `codex/aws-master-resume-20261019` is based on feature SHA `e6f16ac30914fe32f7140241e81e5976660c03d2`; code commit `3bccb94a9a22164088b9b1facde1fe7bbd92f13b` is the tested source. This adds **CREAR SKILL** to the existing Skills management surface and starts a dedicated authoring chat through the existing shared chat/model session. The prompt begins “¿Qué debiese hacer la Skill?” and requests a complete `SKILL.md` only after the user supplies enough detail. A completed response opens an editable review; explicit save registers a `USER_AUTHORED`, disabled, explicit-only Skill. It never auto-enables or executes generated instructions. Tool/source/capability proposals are data; only existing standard tools and local sources can be selected, and unavailable capabilities are visibly retained as unavailable metadata.

The exact-source focused HOST run passed **7/7** JUnit tests, 0 failures/errors/skips, Gradle exit 0 in 89.602 seconds. It used Gradle 8.10.2, Temurin JDK 17 runtime, JDK 21 toolchain discovery, API 35 SDK, NDK 27.0.12077973 and the pre-existing offline Gradle cache. The controlled test backend proves the session/policy/review/save path; it does not prove actual Gemma quality, native inference, or device performance. Commands, all 13 preserved attempts, final XML/logs/toolchain preflight and checksums are in [`Skill authoring results`](../validation/master-program-skill-creation/RESULTS.md); payload manifest SHA-256 `0d1230378914b4cdbae2e56e875e39131aa5513cd104ea2be526d7c6b7b69cb5` (137 entries).

No original backlog task is assigned or closed by this feature. The canonical plan files are byte-identical at backlog SHA `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` and roadmap SHA `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`; the backlog remains 97 unique IDs, 120 `depends_on` edges, all 97 source statuses `TODO`. M01-06 remains `PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`. The existing global emulator Room-v1 fixture failure and six model-dependent skips are unchanged. Full JVM/LiteRT, lint, APK assembly, AndroidTest, emulator and Motorola were not run for this increment.

## 2026-10-19 — PPTX native editable figures preparation

On `codex/aws-master-resume-20261019`, production commit `8573aa3ef5c865c5554c6ba67751b6f51768c20e` adds four bounded native editable PPTX presets (rectangle, ellipse, rounded rectangle, triangle) with required accessible descriptions and optional visible labels. Test/evidence code SHA `b46827368ad289a73ea9bde342e86aaebe54efa8` passed `PptxImageArtifactTest` 6/6, exit 0; an independent `python-pptx 1.0.2` open verified four editable shape types, labels/descriptions, and ZIP CRC. Red/green logs, JUnit, generated synthetic PPTX, reader outputs and checksums are in `docs/validation/master-program-pptx-shapes-20261019/RESULTS.md`.

This is preparation only, not closure of original M03-05: its source state remains TODO, it requires device validation, and its M03-01 dependency remains TODO. The authentic plan files are unchanged (97 IDs, 120 edges, all source statuses TODO). Full JVM/LiteRT, lint, assembly, AndroidTest, EMULATOR, PHYSICAL_DEVICE, and APK are NOT_RUN for this increment. No PowerPoint/LibreOffice visual rendering or Motorola validation is claimed. M01-06 remains `PENDING_REQUIRES_DEVICE`; preserve the existing Room v1 and model-dependent test limitations. Continue only with a distinct, source-backed bounded preparatory gap; do not mark a task complete from this increment.

The evidence manifest for this increment contains 33 payloads and has SHA-256 `b616c55fde5059e42ed4ec227177a45c5356d33b35644906629d20003350623c`; all 33 entries pass local `sha256sum -c`.

## 2026-10-19 — bounded EG2 online-search corpus paging

The independent continuation remains on `codex/aws-master-resume-20261019`, based on the verified recovery line; `main`, `v0.4.2`, and `feat/0.5.0-skills-agents` were not modified. Current source SHA is `d214453df7e1c53ed60107d76e98017ce7beab6c`; the executable paging/test source passed its focused run at `bb85544e37a44ed1a8a9eacfcd843ee0a79f5112`. `d214453` is only a KDoc wording change removing a stale “version-8” statement. Its later Gradle invocation compiled app Kotlin, but the test task was `UP-TO-DATE`; do not count it as another test run.

`SemanticLayer.search()` now reads deterministic keyset pages of at most 256 joined rows and scores incrementally into a semantic top-30 heap, retaining at most 30 lexical candidates. It preserves the 10,000-row cap, source/project/selected-document/modality constraints, FTS rank fusion, cancellation and READY-generation checks. An eligibility pre-scan avoids query embedding if no permitted row can match. This structurally bounds vector payloads per page to 786,432 bytes at 768 dimensions (metadata and runtime buffers excluded) versus up to 30,720,000 bytes in the previous maximum-size materialization; these are calculated payload bounds, not measured heap, latency or phone results.

Focused HOST Robolectric tests at `bb85544` passed 49/49 (SemanticContextPostfixTest 40, SemanticStorageTest 9; zero failure/error/skip), with Gradle 8.10.2 offline, JDK 17 runtime/JDK 21 worker, 185 seconds, exit 0. The initial red page regression failed 1/1 as expected. An intermediate 49-test run exposed the existing storage test’s stale literal Workspace version 8; the source is now version 10. The test was updated to compare its current version before/after opening the independent sidecar and retains all data/EG1-vector assertions; no DB schema/runtime was changed. Full suite/LiteRT/lint/build/AndroidTest, emulator, Motorola, native inference, device metrics and APK were not run for this increment. Evidence, compressed Gradle logs, XML and checksums: [`EG2 paged-search results`](../validation/master-program-eg2-paged-search-20261019/RESULTS.md); top-level manifest has 69 payload entries, SHA-256 `74d5bc75e65559680a34c2990d9a26810e8f57e9ad2cb80f7f0b84ba5d21f146`, all verified locally.

This is preparatory implementation only; no original task ID was marked complete. The canonical backlog and roadmap remain unchanged: 97 unique IDs, 120 dependency edges, all source statuses TODO. M00-06 and physical-device work remain open; no performance claim is made for Gemma or Motorola. Continue with another distinct, source-backed preparatory increment only after inspecting the existing code/tests and authentic dependencies; do not repeat already covered corpus fixture work.

## 2026-10-19 — M02-02 complete OPC fixture and independent reader follow-up

The earlier M02-02 style-metadata test used a parser-oriented XML ZIP rather than a complete OPC workbook. OpenPyXL stopped at the missing `[Content_Types].xml`; that earlier attempt remains `NOT_VALIDATED`. Test commit `156b3e43f8728d7755b8f46f3bc474b009368097` replaces the test input with the committed synthetic XLSX `app/src/test/resources/fixtures/xlsx-style-metadata-valid.xlsx` and checks required package parts and ZIP CRC. The test retains its raw value/type, shared/inline string, style, formula and cached-value assertions. Production code did not change.

On exact test/source SHA `156b3e43f8728d7755b8f46f3bc474b009368097`, the selected `StructuredDocumentsTest.xlsxPreservesRawStyleNumberFormatsAndCellTypesWithoutConvertingCachedValues` passed 1/1, 0 failures/errors/skips; Gradle exit 0, `BUILD SUCCESSFUL`, 63 actionable tasks executed, 175 seconds by UTC timestamps. OpenPyXL 3.1.5 independently opened the exact fixture copy emitted by the passing test, verified ZIP CRC/OPC parts, styles, cell types, formula text and stored cache. It did not calculate formulas or exercise an office GUI. Full suites, lint, assembly, AndroidTest, emulator, Motorola and APK were not run. Evidence and hashes: [`M02-02 OPC follow-up`](../validation/master-program-xlsx-style-opc-20261019/source-156b3e4/RESULTS.md).

This is preparatory HOST evidence, not formal M02-02 completion: its source status remains `TODO` and M02-01 remains `TODO`. M03-07 preview/diff/cancel already has focused current-source HOST revalidation at `a72db3db5ad5e8ca7c6b178e723a9fc332d865ea`; M03-07 remains `TODO`, requires device validation, and depends on M03-01 (`TODO`). Lost local SHA `2f8b0d496db8973edf161c7703efc8f0c17d34fd` was not recovered or reconstructed. The canonical plan remains untouched at 97 IDs, 120 dependency edges, all 97 source statuses `TODO`; M01-06 remains `PENDING_REQUIRES_DEVICE` and `PHYSICAL_DEVICE=NOT_RUN`.

## 2026-10-20 — M02-08 code line chunk budget boundary

At test-only red commit `6ce7788429a00260816d184eb39601905fad317c`, `CodeLineChunkerTest` reproduced a one-character overflow: a maximum-length source line after a leading blank row gained an extra newline. Production commit and exact tested code SHA `3e3fb0dffcd6e4488e1f28522067bfad7dd2a270` omits that separator only when it would exceed `maxCharacters`, and starts the chunk at the physical line actually emitted. Leading blank lines remain preserved when they fit.

On the exact code SHA, `CodeLineChunkerTest` passed 3/3 (0 failures/errors/skips, Gradle exit 0, 45 seconds wall) and the existing `CodeSegmentsTest` wrapper passed 3/3 (0 failures/errors/skips, Gradle exit 0, 20 seconds wall). Both used Gradle 8.10.2 offline, Temurin JDK 17 runtime, JDK 21 test worker and the existing SDK/NDK/cache. The red run was 3 tests/1 expected failure, exit 1, 168 seconds. Full commands, losslessly compressed complete logs, XML, timestamps, environment and hashes are in [M02-08 code-line chunk boundary evidence](../validation/master-program-code-line-chunker-20261020/RESULTS.md); its 31-entry payload manifest SHA-256 is `7082f57d6ffac4cc1e5b9be0fa0a46e84f3802d1afc9a7e5840024ac85bdfeac` and verifies 31/31 locally.

This is focused HOST preparation only. M02-08 remains source `TODO` and depends on M02-05 (`TODO`); no original ID/dependency changed. Full app/LiteRT, lint, assemble, AndroidTest, emulator, Motorola and APK were NOT_RUN. The authentic source plan remains 97 unique IDs, 120 dependency edges, all source statuses TODO; M01-06 remains `PENDING_REQUIRES_DEVICE`, and `PHYSICAL_DEVICE=NOT_RUN`.
H0 publication readback: source 49f5913937fe7c049bf0ad71d2d75f7e5321b557 and evidence are included in feature head d7baa4d5e56caf0ee3d826aab39994150bcaac43 via fast-forward. main/v0.4.2 remain de615b772cf37a99e7e08a647c59607b271de606.
