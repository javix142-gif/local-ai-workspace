# Local AI Workspace 0.2.1 — Device Validation

[Descargar APK firmado 0.2.1](https://drive.google.com/file/d/1RQ_WbpVHPxkAfpQMY-NkJoE7pEM2dAum/view?usp=drivesdk). Instalar como actualización sin desinstalar ni borrar datos. [Evidencias host y ejemplo JSON — no ejecución física](https://drive.google.com/file/d/1q-QKmuHqi2RG9ifs-qWW2nng7CjfSd-K/view?usp=drivesdk).

## A. Arquitectura y base comprobada

APK ARM64 de entrega: `dist/apk/local-ai-workspace-0.2.1-device-validation-arm64.apk`, 98,425,956 bytes (93.87 MiB), SHA-256 `5c2f6b76fdca514241b4ab55148d1e11b4651ac483ab546d12faef3efff87f3e`. Package `com.localai.workspace`, minAPI29/target35, firma de actualización conservada. Los modelos no se incluyen ni se vuelven a descargar. [Manifest](validation/device021/apk-manifest.json).

Base de trabajo real: `app/build.gradle.kts` 0.2.0/code17; entrega 0.2.1/code18. LiteRT-LM permanece 0.17.1. La Parte 1, interfaz de Parte 1.5, referencias persistidas de imagen y Parte 2 están presentes. No se añadió ninguna capacidad del asistente ni se cambió sampling, KV, warm-up, GPU/speculative, entrenamiento o dependencias nativas.

`Settings → Developer / Diagnostics → Device Validation` crea una operación propiedad de AppGraph/application scope. Volver atrás no la cancela. No se ejecuta al abrir la app. El coordinador usa `ValidationBackend`; la implementación de dispositivo exige la clase productiva LiteRtLmInferenceRuntime. Los fakes sólo están en tests host y su evidencia es HOST_TESTED.

```
Developer action → DeviceValidation → ValidationEngine
 → ProductionValidationBackend
 → AppGraph scoped + ChatViewModel productivo
 → GenerationRequest → mismo pool/gate → LiteRtLmInferenceRuntime
 → IPC → LiteRtLmService → SDK Conversation/automaticToolCalling/Content
 → resultado + métricas existentes → comprobaciones → reporte sanitizado
```

La base temporal `device_validation.db` conserva los mismos DAO/esquema8, separado de `local_ai_workspace.db`. Sólo copia metadata del modelo, nunca sus 2.59GB. Cada proyecto/conversación tiene prefijo self-test. NativeChatTools abre la base temporal únicamente con `validationScope=true`; no cambia la base normal. Se comparte el único engine, mutex y provider semántico reales, no se duplica Gemma. Antes de ejecutar se exige que no haya chat/import/benchmark activo. Durante la reserva nuevos sends normales se rechazan con aviso. Al finalizar se restaura la preparación del modelo original.

ValidationContext proporciona SharedPreferences en RAM; Tools/Thinking/Python/performance son overrides temporales. El ajuste normal de Python sigue OFF. Sampling proviene del ModelEntity real sin aplicar upgrades a esa copia. El perfil de validación usa CPU, warm-up ON y speculative OFF; esto no cambia las preferencias normales.

## B. Archivos

El inventario completo de código, con rutas reales y hashes de antes/después, está en [source-changes.json](validation/device021/source-changes.json). No se eliminó ningún archivo de código. También se actualizan este informe, `STATUS.md` y las evidencias de validación.

- `validation/DeviceValidation.kt`: reserva, prerequisites, ejecución explícita, recuperación, historial y exportación.
- `validation/ValidationEngine.kt`: estados, timeout/cancelación, persistencia incremental, cleanup.
- `validation/ProductionValidationBackend.kt`: casos en rutas productivas y assertions de evidencia real.
- `validation/ValidationModels.kt`: catálogo, resultado/reporte, reglas, comparación, whitelist y store atómico acotado.
- `validation/ValidationContext.kt`: preferencias/cache scoped.
- `validation/ValidationFixtures.kt`: fixtures deterministas y limpieza sin seguir symlinks.
- `ui/DeviceValidationScreen.kt`, `MainActivity.kt`: pantalla de desarrollador y navegación aditiva.
- `AppGraph.kt`: fork diagnóstico de la arquitectura existente; comparte pool/gate/runtime, encapsula DB/archivos.
- `data/WorkspaceDatabase.kt`: factory para DB temporal; sin migración.
- `domain/model/WorkspaceModels.kt`: scope y flags observables de configuración/entrada real.
- `inference/LiteRtLmInferenceRuntime.kt`, `LiteRtLmService.kt`, `NativeChatTools.kt`: transportar scope, métricas y cierre de conexión temporal.
- `ui/ViewModels.kt`: bloqueo de sends durante reserva, observador interno de requests y cierre/espera diagnósticos.
- `data/LiteRtChatDefaultsUpgrade.kt`: modo read-only para copia temporal.
- `data/DocumentIngestion.kt`, `ImagePreprocessor.kt`, `audio/AudioInput.kt`: directorio privado opcional, defaults productivos intactos.
- `domain/tools/ProjectTools.kt`: raíz privada explícita para validar lectura estructurada en subdirectorios scoped.
- `performance/PerformanceDiagnostics.kt`: storage scoped y exclusión mutua con la validación; benchmark original intacto.
- `assets/python/main.js`, `worker.js`: exponer bootstrapMs medido; política/limits/runtime intactos.
- `assets/device_validation/audio_fixture.wav`, `PROVENANCE.json`: voz sintética conocida y procedencia.
- Nuevos tests `validation/*`, UI smoke y navegación instrumentada; no simulan un PASS físico.

## C. Quick: nueve casos, una iteración

1. Text inference: native completion y Tokio/Tokyo.
2. Calculator: llamada nativa calculator.evaluate, resultado real5781 y respuesta final5781.
3. Tools Off: inferencia real con cero llamadas/ejecuciones y automaticToolCalling false.
4. Thinking: OFF capital; AUTO saludo; AUTO problema; ON problema. Comprueba configuración efectiva SDK, final7 y filtrado de canales.
5. XLSX: parser Sales/B2=12/B3=4 y files.read nativo con aggregate=sum, resultado16 y respuesta16.
6. Semantic: sólo si EmbeddingGemma está instalado; encoder real768D finito, L2≈1, retrieval A>B y vector persistido.
7. Python: PythonClient → service → WebView → Worker → CPython/WASM; mean([1,2,3]) stdout2/2.0.
8. Vision: imagen real preparada, entrada/backend reales, número42; composer consumido y referencia del mensaje.
9. Audio: WAV real preparado, Content.AudioFile/backend reales y transcripción bicicleta.

Quick no ejecuta un cold benchmark completo ni repite casos. Deadline de ejecución seis minutos; hasta tres minutos por caso, sujeto al deadline restante. Limpieza/cancelación nativa ocurre después del deadline. Un teléfono lento puede producir BLOCKED/SUITE_TIMEOUT; nunca se convierte en PASS por timeout.

## D. Full: treinta casos, 1–3 iteraciones

Catálogo exacto, en orden de ejecución:

| # | ID estable | Caso |
|---|---|---|
| 1 | `text` | Text inference |
| 2 | `calculator` | Calculator Tool |
| 3 | `tools_off` | Tools Off |
| 4 | `thinking` | Thinking Off / Auto / On |
| 5 | `xlsx` | XLSX parser / tool |
| 6 | `semantic` | EmbeddingGemma / RAG |
| 7 | `python` | Python / WebView |
| 8 | `vision` | Vision |
| 9 | `vision_lifecycle` | Vision lifecycle |
| 10 | `audio` | Audio |
| 11 | `audio_lifecycle` | Audio lifecycle |
| 12 | `warmup` | Engine warm-up |
| 13 | `cold` | Cold text |
| 14 | `warm` | Warm / new conversation |
| 15 | `continuation` | Continuation / KV |
| 16 | `files` | Files list / read |
| 17 | `csv` | CSV structured tool |
| 18 | `zip` | Safe ZIP |
| 19 | `zip_traversal` | ZIP traversal |
| 20 | `memory` | Semantic memory |
| 21 | `lexical` | Lexical fallback |
| 22 | `python_timeout` | Python timeout |
| 23 | `python_subprocess` | Python subprocess |
| 24 | `python_network` | Python network |
| 25 | `python_filesystem` | Python host filesystem |
| 26 | `python_output` | Python output limit |
| 27 | `rebuild` | Tools / Thinking rebuild |
| 28 | `persistence` | Local persistence |
| 29 | `errors` | Error isolation |
| 30 | `device_metrics` | RAM / PSS / thermal |

Incluye los nueve anteriores y: vision lifecycle, audio lifecycle, warm-up, cold, warm/new conversation, continuation/KV, files.list/read, CSV, ZIP seguro, ZIP traversal, memoria semántica, fallback lexical sin encoder, Python timeout/subprocess/network/filesystem/output limit, tools/thinking rebuild, persistencia Room independiente, invalid media/no fallback, métricas Android.

Los lifecycle se ejecutan inmediatamente después de su caso multimodal, antes de recrear el engine. Comprueban el siguiente GenerationRequest sin adjunto y otra conversación sin media heredida. El historial puede conservar la referencia anterior; no equivale a un adjunto activo del próximo request.

Cold descarga explícitamente el engine a través de releaseForDiagnostics, prepara de nuevo y conserva sus métricas de carga. El primer send posterior reutiliza ese engine preparado. Warm crea otra conversación con pesos residentes. Continuation envía el código3817 y lo consulta en la misma sesión: PASS de KV exige sessionReused y cachedTokenCount real>0; sólo recordar el código sin esa evidencia es INCONCLUSIVE.

Full tiene deadline15 minutos por iteración, 1 por defecto. Los valores son límites operativos, no predicciones de rendimiento. Cancel puede tardar en esperar el acknowledgement/teardown nativo.

## E. Fixtures

Generados en un directorio privado exclusivo del run: TXT marker12345; CSV con delimitador ; y campo a;b entre comillas; XLSX estándar hoja Sales; ZIP con TXT/CSV; ZIP ../escape.txt; textos flamencos/fotosíntesis; PNG640×480 con número42; PNG inválido para rechazo. WAV sintético eSpeak NG1.52.0, español, PCM16mono22050Hz, 2642ms, frase «La palabra de prueba es bicicleta». Procedencia/SHA en assets/device_validation/PROVENANCE.json. No grabación, datos reales, micrófono, descargas ni binarios eSpeak en el APK.

## F. Reglas de resultado

PASS requiere evidencia observable del comportamiento; coincidencia textual por sí sola no certifica tools/media/KV. FAIL indica mismatch demostrado. BLOCKED indica runtime/dependencia declarada/timeout que impidió ejecutar. INCONCLUSIVE indica evidencia insuficiente, cancelación/interrupción o limpieza fallida. SKIPPED indica dependencia opcional ausente (encoder, WebView no compatible, modalidad no declarada); NOT_TESTED indica no seleccionado o no alcanzado en reporte parcial.

Cualquier FAIL produce overallFAIL. Críticos bloqueados producen BLOCKED, críticos inconclusos/no probados impiden PASS. Embedding ausente deja de ser crítico: PASS_WITH_SKIPS si el resto pasa. Cleanup distinto de SUCCESS impide PASS. HOST_TESTED y DEVICE_TESTED aparecen por reporte/caso. El código de pruebas host no puede habilitar la certificación del backend real.

## G. Integración real

Gemma y tools/thinking/media pasan por ChatViewModel real, no un send alternativo. Tools exige los eventos persistidos por NativeChatTools/OpenApiTool/ToolRegistry; ese resultado SDK vuelve al modelo y se verifica el final. No se llama directamente a Calculator para aprobar el caso.

XLSX/CSV usan StructuredDocuments y files.read real; ZIP usa DocumentParser real con sus límites y rechazo exacto de traversal. EmbeddingGemma usa EmbeddingModels/JNI ARM64 bajo demanda, no HashEmbeddingRuntime. RAG usa SemanticRetrievalService con vectores persistidos y lexical/FTS; memoria usa recuerdos sintéticos USER_APPROVED/PROJECT/PREFERENCE y retrieval directo. El caso lexical crea deliberadamente un provider ausente, sin fabricar vectores ni cambiar configuración real.

Python llama al mismo PythonClient/Service/WebView/Worker que PythonTool. TIMEOUT se presenta como TOOL_TIMEOUT según el contrato productivo; POLICY_REJECTED y OUTPUT_LIMIT son resultados explícitos. Renderer crash/init error nunca se interpretan como protección demostrada.

Vision usa ImagePreprocessor → imagePath → Content.ImageFile en el mismo turno de texto. Audio usa AudioPreprocessor/WavInput → audioPath → Content.AudioFile; no ASR alternativo. Flags de entrada salen de los objetos Content construidos en el worker y flags backend/config salen del engine/Conversation SDK realmente inicializados. Un flag de backend no mide actividad GPU/NPU ni uso interno del encoder; respuesta42/bicicleta es la comprobación funcional adicional.

## H. Métricas

Se reutiliza RuntimeMetrics y DeviceMeasurements: preparation/load/session/warm-up, nativeTTFT, requestTTFT, gate/context, prefill/decode/output/total, reuse/reason/count, PSS app/worker y RAM/thermal antes/después. Flags Tools/Thinking/backend/input son observación de configuración/contenido efectivos. Retrieval/embedding/Python/audio preparation provienen de sus subsistemas productivos.

Request TTFT es desde aceptación de ChatViewModel.send hasta primer contenido mostrable enviado a su estado; no un timestamp de frame dibujado. No hay pantalla Chat observando el self-test: la métrica de frame Compose queda unavailable. Native TTFT es la métrica existente del worker. Prefill duration conserva su fuente DERIVED_FROM_NATIVE_COUNT_AND_RATE cuando deriva de count/rate; no se anuncia como timestamp nativo directo. Null significa unavailable, no cero. Los campos de sampling del reporte son la configuración de solicitud; `repeatPenalty` no se presenta como prueba de que LiteRT lo implemente. La configuración actual no se altera para mejorar cifras. No grados Celsius, peakRAM, batería consumida ni hit-rate estimados.

Comparación sólo entre suite/device/model-hash/config/runtime/environment compatibles: valores Previous/Current del caso texto iteración1 para TTFT/decode/PSS; no se concluye una regresión de velocidad por una muestra. PASS→FAIL funcional se destaca.

## I. Seguridad, privacidad y recuperación

No nuevos permisos/dependencias, Internet, auto-download, acceso a datos reales o tests contra documentos del usuario. DB propia y UUIDs separados; archivos/previews/documentos/audio/cache en namespaces exclusivos. Borrado valida UUID y no sigue symlinks. Las conexiones/leases/coroutines se cierran antes de borrar. Cancelar mantiene reporte parcial; cleanup fallido es visible y evita PASS. Una nueva app sesión detecta reportes RUNNING al abrir Diagnostics, los marca INTERRUPTED y limpia sólo recursos de validación.

Store JSON atómico, fsync, máximo10 runs y límite2MB descartando los más antiguos. Exportación SAF explícita y Copy summary. Whitelist de métricas escalares: no prompts, documentos, recuerdos, respuesta, análisis, vectors, código del usuario ni rutas completas. Los datos sintéticos pueden existir temporalmente en Room; se borran con la DB al finalizar.

No se finge restaurar KV tras process death. «Restart safely» crea un run nuevo ligado al anterior. Persistencia tras muerte real: MANUAL_RESTART_REQUIRED. No se mata el proceso para probarla. Full abre otra conexión Room para demostrar persistencia local sin reinicio forzado.

## J. Tests host/builds

Última verificación posterior al ajuste de cleanup y XLSX: **BUILD SUCCESSFUL in 16m**, 295 tareas (31 ejecutadas, 264 up-to-date). [Log final](validation/device021/build-delivery.log).

| Comando/tarea | Resultado | Alcance |
|---|---|---|
| `:app:testDebugUnitTest` | PASS | 298 tests / 58 clases; 0 failures/errors/skips |
| `:litert-compat:testDebugUnitTest --rerun` | PASS | 2 tests realmente reejecutados; 0 failures/errors/skips |
| `:app:assembleDebug` | PASS | Compilación debug ARM64 |
| `:app:assembleRelease` | PASS | Release ARM64; APK firmado posteriormente |
| `:app:lintDebug` | PASS_WITH_WARNINGS | 0 errores/fatales, 76 warnings, 1 information |
| `:app:assembleDebugAndroidTest` | PASS | APK instrumentado construido; ejecución NOT_TESTED |
| `node scripts/test_python_sandbox.cjs` | PASS | 8 controles de CPython/WASM real en Linux/Node |
| `apksigner verify` / `zipalign -c -P 16` | PASS | Misma firma y alineación de librerías a 16 KiB |

Resultados host: **298 tests de la app + 2 de litert-compat, cero failures/errors/skips**. Los 2 tests SDK se forzaron explícitamente con `--rerun`; no se presentan tareas UP-TO-DATE como tests ejecutados nuevamente. Evidencia en [tests-summary.json](validation/device021/tests-summary.json), [sdk-tests.log](validation/device021/sdk-tests.log) y log de entrega.

```bash
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain

# Rerun SDK explícito, usando el mismo JAVA_HOME/ANDROID_HOME:
./gradlew :litert-compat:testDebugUnitTest --rerun -Parm64Only=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=2 --console=plain

node scripts/test_python_sandbox.cjs
```

Los 9 `.so` ARM64 y los 4 assets Python principales (WASM/stdlib/lock/procedencia) son byte-idénticos a 0.2.0; comprobación en apk-manifest.json.

La suite focused cubre estados/reglas, cancelación/timeouts, JSON/retención/comparación/privacidad, overrides, DB/fixtures/cleanup, prerequisites y navegación. Scripts/test_python_sandbox.cjs ejecutó8 controles CPython/WASM reales en Node/Linux; no verifica WebView/CSP Android. assembleDebugAndroidTest construye los instrumentados; ejecución física pendiente. [Captura host de la pantalla](validation/device021/device-validation-host.png): Robolectric/Android API29, 360×800; no captura del Moto. Theme global intacto: el acento host puede diferir del color dinámico del teléfono.

## K. Ejecución física pendiente

PHYSICAL_DEVICE_TEST_REQUIRED: Quick/Full sobre Moto G86, inferencia nativa CPU, calculator/files llamadas por Gemma, thinking final, encoder ARM64, retrieval, WebView/WASM/CSP, visión/audio, KV, RAM/PSS/thermal/TTFT y cancelación/reinicio reales. No hay teléfono conectado en este entorno. No se generó ningún reporte DEVICE_TESTED de PASS en CI ni se inventaron porcentajes/latencias.

## L. Secuencia en Motorola

1. Instalar el APK como actualización, sin desinstalar ni borrar datos.
2. Abrir la app, elegir Gemma4E2B y esperar Ready.
3. Opcional: importar EmbeddingGemma en Settings/Semantic Search. No se descarga solo.
4. Esperar que termine cualquier chat/import/benchmark.
5. Settings → Developer / Diagnostics → Device Validation.
6. Run Quick Validation; se pueden cerrar/abrir pantallas. Esperar resultado y revisar cards noPASS.
7. Export JSON y compartir ese archivo. Copy summary es complementario.
8. Sólo si Quick pasa (o PASS_WITH_SKIPS por encoder opcional), ejecutar Full con1 iteración y exportar otro JSON. Iteraciones2–3 son opcionales.

Si Quick queda FAIL/BLOCKED/INCONCLUSIVE, compartir su JSON antes de repetir Full. Para validar cancelación: iniciar un run separado, Cancel validation, esperar cleanup y exportar parcial. Para reinicio: cerrar naturalmente la app después de completar, abrir Diagnostics y comprobar historial; no force-kill durante el primer run.

## M. JSON

El ejemplo [report-schema-example.json](validation/device021/report-schema-example.json) es deliberadamente HOST_TESTED, INTERRUPTED y NOT_TESTED: no representa una ejecución física.

schemaVersion1, runId, appVersion, device/api, runtime, started/completed, suite/iterations, modelo/hash/config, environment, phase, activeTestId, tests (id/name/expected/selected/critical/iteration/status/duration/reason/metrics/evidence), summary, previousRunId, cleanupStatus, restartPersistence y mediciones Android. Ejemplo no medido: report-schema-example.json. No raw inputs/outputs/media/vectors.

## N. Limitaciones

Resultados reales dependen del teléfono/bundle/WebView. Metadata de capacidades antigua puede dejar un caso BLOCKED aunque el bundle admita esa función: el diagnóstico no altera modelos/importaciones del usuario. La continuidad KV sólo se certifica cuando SDK publica cachedTokenCount. No tests GPU/NPU nuevos ni optimización automática. No reruns tras process death conservando estado nativo; restart seguro es un run nuevo. Sin garantía de ejecución tras muerte de procesos Android, sin foreground service nuevo. Sin TTS conversacional, micrófono, OCR, múltiples medios, web, Drive, calendarios o Parte3.
