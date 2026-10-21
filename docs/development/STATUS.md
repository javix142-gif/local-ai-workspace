# Estado operativo — AWS H0 → H1 → H2

Fecha: 2026-10-21. Rama: `feat/0.5.0-skills-agents`. El código H2 está en el commit que contiene este checkpoint.

## H2 — acciones Android y lectura personal local: HOST focalizado; validación global pendiente

- Añade lecturas locales acotadas de calendario y contactos con `READ_CALENDAR`/`READ_CONTACTS` comprobados al ejecutar. La tarjeta de chat solicita permiso solo tras un toque; concederlo no reintenta la lectura por sí solo. Los resultados se marcan como datos no confiables y la consulta/resultado personal se excluye del historial Room de llamadas de herramienta.
- Añade routing determinista para consultas compatibles y la herramienta Android de acciones existentes; mantiene la confirmación explícita antes de abrir una aplicación externa. Consultas de correo no se enrutan a una búsqueda que solo devuelve teléfonos. El conjunto Auto heredado incorpora las lecturas nuevas; selecciones personalizadas y Tools Off se conservan.
- HOST focal final: 19 pruebas únicas PASS (herramientas 7, esquemas 4, defaults 1, UI 3, DOCX schema 4). `lintDebug` y `compileDebugAndroidTestKotlin` finalizaron exit 0; lint reporta 0 errores, 128 avisos y 1 información. LiteRT compatibilidad: 2/2 PASS en la ejecución completa inicial.
- Una ejecución completa de app JUnit (960 casos) detectó un fallo de esquema DOCX causado por el acortamiento de `artifacts.create/revise`; se restauró la sintaxis DOCX de forma compacta y la clase afectada pasó 4/4. No se repitió la suite completa después de esa corrección; por tanto, el gate JVM global del código final no se declara PASS.
- No se ejecutaron pruebas instrumentadas en esta sesión ni pruebas en Motorola. Al publicar este commit, los workflows existentes pueden verificar el mismo SHA y generar el APK personal; sus resultados deberán leerse por separado. `PHYSICAL_DEVICE=NOT_RUN`.

H1 sigue `HOST/CI_VERIFIED` solo en su alcance auditado; conserva sus criterios físicos pendientes. No se modificaron el catálogo histórico, `EXECUTION_STATE.json`, IDs ni dependencias.

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
