# Estado operativo — programa AWS H0 → H1 → H2 y continuidad autorizada

Fecha: 2026-10-21. Rama `feat/0.5.0-skills-agents`; código H2 `a65a80dae1cf7edc3ae42afeb76620f66bfdc309`.

## H2 — herramientas Android locales: HOST focal, EMULATOR y QA personal verificados

- Añade `calendar.read` y `contacts.lookup` acotados, con permisos solicitados en el punto de uso, resultados marcados como datos locales no confiables y redacción de argumentos/resultados personales en el historial persistido de herramientas. El enrutamiento es determinista; correo no se deriva a la búsqueda telefónica. Las propuestas de acciones Android conservan confirmación explícita. Las descripciones compactas de artefactos conservan detalles de listas DOCX.
- HOST focal: 19/19 eventos de prueba completados; LiteRT compatibilidad 2/2. `lintDebug` (0 errores, 128 avisos, 1 información) y `compileDebugAndroidTestKotlin` PASS. La suite completa de app tuvo un intento de 960 pruebas con 959 PASS y un fallo DOCX. La descripción se corrigió y la clase afectada pasó 4/4 en validaciones posteriores; no se repitió la suite completa, por lo que el PASS global final no está establecido. El primer foco de 18 pruebas tuvo un fallo adicional, seguido de dos resultados verdes (18 y 19); causa de esa discrepancia NOT_DETERMINED.
- M00-04 se verificó focalmente en el SHA actual: 2/2 PASS. La omisión material produce el aviso y el Inspector expone su motivo; la omisión rutinaria permanece solo diagnóstica. No se cambió el estado histórico TODO del catálogo.
- EMULATOR run [39520261327](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520261327) en el SHA exacto pasó la tarea conectada: 22 XML, 16 PASS, 0 FAIL/ERROR y 6 SKIPPED por falta de modelos reales. H1-AT-12 pasó con datos sintéticos. Esto no valida Moto G86 ni la migración histórica Room v1-v7.
- PERSONAL_QA run [39520260941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520260941) entregó APK Debug ARM64 verificada; resultado y Drive readback en [H2 RESULTS](../validation/master-program-h2-20261021/RESULTS.md). Es PERSONAL_QA, no Release. Motorola `NOT_RUN`.

El source backlog conserva sus 97 IDs, 120 dependencias y estados TODO. H1 mantiene criterios físicos pendientes. El siguiente original host-elegible es M00-06; se inspeccionará la instrumentación existente antes de cualquier cambio. `main` y peeled `v0.4.2` permanecen en `de615b772cf37a99e7e08a647c59607b271de606`.

## H1 — memoria personalizada y unificada: HOST/CI_VERIFIED; DEVICE_PENDING

- Contrato vigente: [H1_PRUEBAS_ACEPTACION.md v1.2](H1_PRUEBAS_ACEPTACION.md). El tope de memorias fijadas es 15 %; tres fijadas cortas caben y una nueva fijación que rebase el tope se rechaza con explicación antes de persistir.
- HOST completo histórico en `aed4d8f6220e9d1e65e93f7d54a5bbd77e749755`: app JVM 941/941 y LiteRT compat 2/2; lint y compilación Kotlin AndroidTest exit 0; `assembleDebug` up-to-date. No atribuir ese gate completo al SHA `0116149`.
- En el SHA `0116149`, `:app:compileDebugAndroidTestKotlin` pasó localmente. El run conectado [39511660788](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660788) terminó PASS en API 35 x86_64: XML 22 casos, 16 ejecutados PASS, 0 FAIL/ERROR, 6 SKIPPED por falta de pesos reales. La cobertura del XML incluye migración auténtica 8→actual y H1; no prueba migraciones Room 1→7 sin sus esquemas históricos.
- H1-AT-12 pasó con `adb install -r`: APK anterior `9cfb0d8` y actual `0116149`, package `com.localai.workspace.debug`, versionCode 27, mismo certificado `b0d1c408…ab120b`. Se preservaron 20 recuerdos sintéticos, proyecto, chat/mensaje y preferencia usada en la siguiente solicitud de Context. No se usaron datos reales.
- Evidencia exacta: [RESULTS.md](../validation/master-program-h1-20261020/RESULTS.md), [artifact de instrumentación](../validation/master-program-h1-20261020/emulator-39511660788/), [artifact QA](../validation/master-program-h1-20261020/qa-39511660726/).

## APK Debug ARM64 de QA

- Run [39511660726](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660726), mismo SHA `0116149`, terminó PASS. Artifact GitHub [12307042341](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660726/artifacts/12307042341), 48,959,437 bytes, SHA-256 `313faccd96667f3d0977dcfc676dbd9701f432a3e4fd0b678df19d66c30b617e`, retención hasta 2026-10-27.
- APK: `com.localai.workspace.debug`, `0.5.0-debug`/27, únicamente `arm64-v8a`, 121,582,979 bytes, SHA-256 `2dc3dd24992d2878099e67c2117bbc711fd1bce96ab610be4ba8a35ab7de6b6f`. ZIP/CRC y zipalign PASS; apksigner v2 PASS; certificado Debug SHA-256 `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. No contiene pesos.
- El usuario pidió solo el APK en Drive. Drive limita la carga directa a 100 MiB, así que el archivo está dentro de un ZIP contenedor que contiene exactamente ese APK: [descarga](https://drive.google.com/file/d/1adOGxZnyyePC_CY68ZMWWBjy6mKjIWt9/view?usp=drivesdk). Tamaño 47,248,070 bytes; SHA-256 del ZIP `1146cacfc2989b4cd22c208af004f09acef209873437dd7f145c9bb1a33cc8b8`; readback descargado coincide byte por byte. La actualización se probó en emulador con igual paquete y certificado; si Android muestra conflicto en el teléfono, cancela y no desinstales ni borres datos.
- La verificación focal del código actual `16287d649eabf063fb4482d302060f378b2ff35a` pasó `H1PreferenceContextTest` (24/24) y `H1MemoryApprovalUiTest` (1/1), exit 0. Incluye AT-04/AT-17: el límite del 15 % se conserva, el pin conflictivo se rechaza antes de persistir, el lote permanece pendiente hasta la selección/confirmación explícita y `originalPinned` queda en la procedencia. Evidencia: [`corrective/verification-16287d6`](../validation/master-program-h1-20261020/corrective/verification-16287d6/).
- El keystore QA del repositorio es intencionalmente público y solo sirve para Debug/QA: `keystore/aws-qa-dev.jks`, alias `awsqa`, contraseña pública `awsqadev`; certificado `b0d1c408…ab120b`. La variante `.parallel` sigue disponible para la transición controlada. La rotación a una clave protegida queda pendiente antes de cualquier publicación oficial.
- Artifact de QA etiqueta `QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`; no es release. EMULATOR=PASS solo para la matriz ejecutada arriba; PHYSICAL_DEVICE=NOT_RUN.

## Estado y siguientes pasos autorizados

- **H1: `HOST/CI_VERIFIED`; no equivale a cierre físico integral.** Permanecen AT-06/20/24 y la validación Moto G86 con el XLSX original como `DEVICE_PENDING`. AT-20 exige una copia externa antes de instalar en el teléfono. El fixture Room 1→7 auténtico no está disponible; no se inventó ni se reabrió.
- Los seis tests que requieren modelos reales están SKIPPED, no aprobados. No se midieron TTFT, tokens/s, PSS, energía, temperatura ni calidad de inferencia en el Motorola.
- La autorización vigente permite continuar secuencialmente con el trabajo preparatorio H2 a pesar de las dependencias físicas pendientes; registrar esa excepción y no cerrar los criterios bloqueados.
- `M01-06=PENDING_REQUIRES_DEVICE`; PHYSICAL_DEVICE=NOT_RUN. No se sustituyó el workbook original por datos sintéticos.
- El ZIP de memoria restaura solo registros de memoria elegibles y metadatos soportados. No restaura chats/mensajes, entidades de proyecto, documentos/archivos, modelos, ajustes ni la base completa; la importación exige revisión/aprobación y no transporta vectores.
- El catálogo fuente mantiene sus 97 IDs, 120 dependencias y estados históricos TODO. No se editó. `EXECUTION_STATE.json` se conserva intacto según la instrucción activa.
- `main` y el tag `v0.4.2` no se modifican; ambos resuelven al commit pelado `de615b772cf37a99e7e08a647c59607b271de606`.

Referencias: [backlog operativo](BACKLOG_ACTIVO.md), [handoff](CODEX_TO_WORK_HANDOFF.md), [contrato de continuidad](CORRECCION_CONTINUIDAD_H0_H1_H2.md), [resultados H1](../validation/master-program-h1-20261020/RESULTS.md).
