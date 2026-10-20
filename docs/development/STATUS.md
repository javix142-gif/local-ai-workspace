# Estado operativo — AWS H0 → H1 → H2

Fecha: 2026-10-20. Rama `feat/0.5.0-skills-agents`; código validado `3ca1d7945752b89817ea1e972fed2cdc4872e6d4`.

## H0 — instrumentación del emulador

- Run [39474604686](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604686), job `118807197684`, probó ese SHA en Android 15/API 35 x86_64. El AVD/ADB, `:app:connectedDebugAndroidTest`, collector y carga del artifact terminaron correctamente. XML: 20 casos, 14 PASS, 6 SKIPPED por no instalar pesos reales, 0 FAIL/ERROR.
- El XML y el collector concuerdan. El log de progreso imprimió `26/20 completed`; esa discrepancia de progreso no tiene causa demostrada y no se usa para cambiar el conteo XML.
- Artifact de evidencia `12289288740`, 148,684 bytes, SHA-256 `81b0f027ad80a80196961fd603b0ee8c7a7a541328a046c6c83cd2097ab0d6cc`, retención hasta 2026-10-27. El manifiesto interno se verificó 32/32. Las capturas de encabezado estándar y escala grande son PNG válidos de 1080×1920.
- Esto valida el run EMULATOR de este SHA; no demuestra el fixture histórico Room v1 ni completa H0 en Motorola. Las seis pruebas nativas con modelo continúan omitidas, no aprobadas.

## H1 — memoria unificada

- Contrato activo [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md), versión 1.1. El límite fijado es 15 %: tres pins cortos caben; un pin explícito que lo exceda se rechaza antes de persistir, con explicación.
- La importación/aprobación conserva los metadatos `pinned` de los registros importados; aprobar una importación no equivale a fijar manualmente. Si una consulta incluye pins cuyo bloque rebasa el 15 %, el Context Builder impide iniciar generación y muestra el motivo; ninguna fijada se descarta silenciosamente.
- El gate HOST del árbol fuente publicado como `3ca1d79` pasó: app 939/939 y LiteRT compatibilidad 2/2, 127 XML, cero fallos/errores/omisiones. También pasaron `lintDebug`, `assembleDebug` y `compileDebugAndroidTestKotlin`; Gradle 8.10.2, JDK 17 runtime, JDK 21 worker, SDK/NDK existentes, offline, 158 tareas, 9m41s, exit 0. Log `/tmp/aws-h1-full-host-gate-v4.log`, SHA-256 `1d4c70f0f05faca56f59145df85a93c7b319225161a623c28b85fff11767aaaf`.
- La clase de aceptación H1 pasó 22/22 e incluye AT-04, AT-17 (1,000 registros, metadata/importación duplicada y rechazo explicado de prompt sobredimensionado) y regresión AT-13. Esto no cierra todos los criterios H1: quedan validaciones físicas y revisión criterio por criterio.

## APK ARM64 de QA

- El QA oficial [39474604678](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39474604678) fue PASS en el mismo SHA. Artifact `12290056446`, 48,879,298 bytes, digest `7de7ca2697176fe57e691d052e7d5344636e062a2771a14c030bccfed424136f`, retención hasta 2026-10-27.
- APK Debug, `com.localai.workspace.debug`, `0.5.0-debug`/27, solo `arm64-v8a`, 121,190,875 bytes, SHA-256 `0ec88a2efeffb4adb95b8bd869cfe6ada9a3caeb89bf698c181e24faad9352d6`. ZIP/CRC, `zipalign`, `apksigner` v2, package/version y ausencia de pesos de modelo fueron comprobados.
- **No instalar como actualización de la copia existente:** certificado actual `f68ad607fee4d7145ac631d92645a4cd5d72c050a74ce9334fce1f851830ccd7` no coincide con el certificado previo `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Android rechazará la actualización; desinstalar antes podría borrar chats y datos locales. No se desinstaló ni instaló nada.
- El ZIP de Drive contiene únicamente el APK: [descargar APK QA (NO COMPATIBLE COMO ACTUALIZACIÓN)](https://drive.google.com/file/d/1DQ2MaHDvo1u1c5vphlg-gM4QTFLlfbqA/view?usp=drivesdk). ZIP 47,297,228 bytes, SHA-256 `4fff7aad4c8e812d4b039161262b55da23d9647bc8c46dbda4b3ec70604524d5`; lectura posterior de Drive coincidió byte por byte con el archivo subido.
- El flujo PERSONAL QA separado `39474605036` no compiló: faltan los secretos `QA_KEYSTORE_B64`, `QA_KEYSTORE_PASSWORD`, `QA_KEY_ALIAS` y `QA_KEY_PASSWORD`; solo subió un informe de fallo de 1,120 bytes. El QA oficial usó el keystore Debug estándar del runner, no la clave personal anterior.

## Bloqueos y límites

- `PHYSICAL_DEVICE=NOT_RUN`; AT-06/20/24 y `M01-06=PENDING_REQUIRES_DEVICE`. Antes de instalar en el Moto G86 se requiere la clave de firma compatible y el backup externo de AT-20. El XLSX original debe estar disponible para la validación documental real.
- Room v1 sigue sin fixture histórica auténtica verificable. No se inventó ni se cambió schema/test.
- No se midieron TTFT, tokens/s, consumo, temperatura ni calidad del modelo en el Motorola. H2 no iniciado.
- El catálogo fuente permanece intacto: 97 IDs, 120 dependencias y estado histórico TODO. `EXECUTION_STATE.json` no se modificó bajo el alcance vigente.

Referencias: [`continuidad`](CORRECCION_CONTINUIDAD_H0_H1_H2.md), [`backlog activo`](BACKLOG_ACTIVO.md), [`contrato H1`](H1_PRUEBAS_ACEPTACION.md), [`handoff`](CODEX_TO_WORK_HANDOFF.md), [evidencia H1](../validation/master-program-h1-20261020/RESULTS.md).
