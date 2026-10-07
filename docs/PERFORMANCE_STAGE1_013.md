# Local AI Workspace 0.1.13 — etapa 1 de rendimiento

Implementación y validación en Linux: 2026-10-06. Versión anterior: 0.1.12. Versión entregada: 0.1.13, versionCode 14, paquete `com.localai.workspace`, ARM64.

La infraestructura, los cambios de reutilización y las opciones experimentales están implementados. **El rendimiento real de Gemma, la compatibilidad Mali y speculative requieren validación física: PHYSICAL_DEVICE_TEST_REQUIRED.** No hay un Motorola conectado ni pesos de Gemma disponibles en este entorno. No se atribuyen mejoras porcentuales ni tiempos de inferencia al dispositivo.

## Inspección y baseline

Se revisaron `LiteRtLmInferenceRuntime`, `LiteRtLmService`, `ChatRuntimePool`, `ChatViewModel`, `ChatSessions`, `ConversationPromptBuilder`, `LiteRtConversationReuse`, `ContextBudgetManager`, `GenerationDiagnostics`, `GenerationMetricsPresentation`, `ApplicationModelPreparation` y `LocalAiApplication` antes de modificar la inferencia. Los originales están preservados en `validation/performance-stage1/source-before.tar.gz`; sus hashes, en `source-before.json`.

| Parámetro | Baseline 0.1.12 | Etapa 1 |
|---|---|---|
| Runtime | LiteRT-LM 0.17.1 vendorizado | Sin cambio de SDK/JNI nativo |
| Texto / visión | CPU / CPU | Texto CPU por defecto; GPU opt-in. Visión CPU |
| Threads CPU | `min(4, availableProcessors)`, worker limita 1–8 | Misma política; se reporta configuración, no threads observados |
| Contexto | configurado → declarado → 4096; limitado 256–8192 | Sin aumento automático |
| Salida | `ModelEntity.maxOutputTokens` | Sin modificación para inflar benchmarks |
| Sampling | valores guardados por modelo: temperature/topP/topK/repeatPenalty/seed | Sin cambio simultáneo de sampling |
| Defaults existentes | temperature 0.3, topP 0.95, topK 40; actualización previa del perfil stock a repeatPenalty 1.1 y salida 256 | Se conservan preferencias y lógica previa; valores exactos del teléfono: NO DETERMINADO |
| Prefill inicial | `prefillPrefaceOnInit=false` | Sin cambio |
| Warm-up real | Ninguno; sólo initialize/createConversation | Inferencia temporal de un token, opt-in |
| Speculative | flag false | False por defecto; flag experimental validado contra bundle |
| Reutilización | historial nativo enriquecido comparado con mensajes visibles | Historial efectivo canónico separado del visible |
| Selección de historial | bloque de mensajes recientes | Pares completos recientes seleccionados por presupuesto |

APIs comprobadas en `litert-compat/src/main/java/com/google/ai/edge/litertlm/`: `Backend.CPU(threadCount)`, `Backend.GPU()`, `EngineConfig`, `Engine.initialize()`, `Engine.createConversation()`, `Conversation.sendMessageAsync()`, `cancelProcess()`, `getTokenCount()`, `renderMessageIntoString()`, `getBenchmarkInfo()`, `Capabilities.hasSpeculativeDecodingSupport` y `ExperimentalFlags.enableSpeculativeDecoding`. Pin: `litert-compat/UPSTREAM.json`, tag v0.17.1, commit `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`.

No se encontró una API Kotlin de warm-up dedicado apropiada para este lifecycle. La función oficial `benchmark()` crea su propio engine y usa padding/truncación de tokens; no se usa como sustituto de una generación de chat comparable. El warm-up utiliza una conversación temporal del engine residente. No se añadieron dependencias ni APIs inventadas. No se modificaron los archivos vendorizados del SDK.

## A. Archivos modificados

Rutas relativas a `/workspace/local-ai-workspace`. El inventario exacto de los 44 archivos de código/configuración añadidos o modificados está en `validation/performance-stage1/source-changes.json`. No se eliminaron fuentes.

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | Versión 0.1.13 / code 14; sin cambio de dependencias |
| `app/src/main/java/com/localai/workspace/MainActivity.kt` | Ruta de diagnóstico en Settings; observación opcional del primer contenido en Compose; capacidad del modelo diferenciada de activación |
| `app/src/main/java/com/localai/workspace/AppGraph.kt` | Conecta settings/diagnósticos al pool, gate y scope existentes |
| `app/src/main/java/com/localai/workspace/ui/PerformanceScreen.kt` | Pantalla Local AI Performance, perfiles, benchmarks, matriz, copiar/exportar |
| `app/src/main/java/com/localai/workspace/ui/ViewModels.kt` | `ChatViewModel`: turnos efectivos persistidos, pares de historial, tiempos APP/UI y PSS; conserva mensajes visibles |
| `app/src/main/java/com/localai/workspace/data/Entities.kt` | Campos nullable `effectiveContent` y `effectiveModelId` en mensajes |
| `app/src/main/java/com/localai/workspace/data/Daos.kt` | Guardar turno efectivo y métricas |
| `app/src/main/java/com/localai/workspace/data/WorkspaceDatabase.kt` | Migración no destructiva 3→4 |
| `app/src/main/java/com/localai/workspace/data/ChatHistoryBuilder.kt` | Reconstruir pares con texto efectivo del mismo modelo; fallback visible para registros antiguos |
| `app/src/main/java/com/localai/workspace/data/GenerationMetricsPresentation.kt` | Codec compatible con métricas antiguas; nuevas filas de diagnóstico |
| `app/src/main/java/com/localai/workspace/data/ApplicationModelPreparation.kt` | Perfil experimental en preparación existente; pausa/coordinación del benchmark |
| `app/src/main/java/com/localai/workspace/domain/model/ConversationPrompt.kt` | ID explícito de conversación y orden cronológico de historial |
| `app/src/main/java/com/localai/workspace/domain/model/WorkspaceModels.kt` | Configuración aditiva y métricas nullable; timestamp de recepción del token |
| `app/src/main/java/com/localai/workspace/domain/rag/ContextBudgetManager.kt` | Priorizar contexto necesario y los pares recientes que caben |
| `app/src/main/java/com/localai/workspace/domain/inference/InferenceRuntime.kt` | Reset con motivo; backend efectivo de la carga |
| `app/src/main/java/com/localai/workspace/domain/inference/ChatRuntimePool.kt` | Reset observable por cambio de dueño; descarga explícita para COLD |
| `app/src/main/java/com/localai/workspace/domain/inference/GenerationDiagnostics.kt` | Estado WARMING_MODEL y deadline independiente |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmService.kt` | Warm-up, backend, speculative, razones de reuse, conteo nativo y mediciones worker; métricas parciales también ante error |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmInferenceRuntime.kt` | IPC y perfiles; fallback acotado/visible, fallo recordado, watchdog y métricas preservadas |
| `app/src/main/java/com/localai/workspace/inference/LiteRtIpc.kt` | Serialización aditiva de métricas sin contenido privado |
| `app/src/main/java/com/localai/workspace/inference/LiteRtEngineResources.kt` | Identidad de engine incluye backend y speculative |
| `app/src/main/java/com/localai/workspace/inference/LiteRtConversationPayload.kt` | ID de chat y siguiente input efectivo |
| `app/src/main/java/com/localai/workspace/inference/LiteRtConversationReuse.kt` | Tracker canónico; invalidación y motivos explícitos |
| `app/src/main/java/com/localai/workspace/inference/LiteRtContextCapacity.kt` | Política única de admisión conservadora usando cache nativa y texto renderizado |
| `app/src/main/java/com/localai/workspace/inference/LiteRtTrace.kt` | Backend explícito, sin etiqueta CPU falsa |
| `app/src/main/java/com/localai/workspace/performance/PerformanceSettings.kt` | Preferencias diagnósticas separadas y máquina de estado de warm-up |
| `app/src/main/java/com/localai/workspace/performance/ExperimentalBackendPolicy.kt` | Memoria de fallos y semántica support/enabled/engine-configured |
| `app/src/main/java/com/localai/workspace/performance/DeviceMeasurements.kt` | PSS, RAM del sistema y thermal status Android |
| `app/src/main/java/com/localai/workspace/performance/RequestTimings.kt` | Agregación de tiempos APP independiente del TTFT nativo |
| `app/src/main/java/com/localai/workspace/performance/BenchmarkRecords.kt` | Suite fija, esquema, persistencia atómica, estados de matriz y medianas |
| `app/src/main/java/com/localai/workspace/performance/PerformanceDiagnostics.kt` | Runner manual app-owned, iteraciones y conversaciones aisladas |
| `llama-runtime/src/main/cpp/ai_chat.cpp` | Retira logs de prompts, mensajes, tokens y IDs de contenido |
| `llama-runtime/src/main/cpp/logging.h` | Redacta texto del callback de logging nativo externo |
| `llama-runtime/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt` | Log de tipo de excepción, sin Throwable/contenido completo |

Tests añadidos:

- `app/src/test/java/com/localai/workspace/domain/RecentHistoryBudgetTest.kt`.
- `app/src/test/java/com/localai/workspace/inference/CanonicalConversationTest.kt`.
- `app/src/test/java/com/localai/workspace/inference/LiteRtContextCapacityTest.kt`.
- `app/src/test/java/com/localai/workspace/performance/PerformancePolicyTest.kt`.
- `app/src/test/java/com/localai/workspace/performance/PerformanceDiagnosticsIntegrationTest.kt`.

Tests actualizados:

- `app/src/test/java/com/localai/workspace/data/ChatDeletionTest.kt`.
- `app/src/test/java/com/localai/workspace/data/WorkspaceMigration3Test.kt`.
- `app/src/test/java/com/localai/workspace/inference/LiteRtEngineResourcesTest.kt`.
- `app/src/test/java/com/localai/workspace/inference/InferenceWatchdogTest.kt`.
- `app/src/test/java/com/localai/workspace/inference/LiteRtRuntimeDeadlineTest.kt`.

`ChatSessions.kt` y `LocalAiApplication.kt` se inspeccionaron y no requirieron cambios: se conserva el scope de aplicación y la navegación existente. Este informe, changelog, manifiesto de APK y evidencias son documentación adicional.

## B. Arquitectura nueva

```text
LocalAiApplication → AppGraph → ApplicationModelPreparation
                              → PerformanceSettings
                              → PerformanceDiagnostics (manual, scope IO)

ChatSessions → ChatViewModel.send()
  → reloj APP + inferenceGate
  → memoria/RAG/contexto (política existente)
  → ContextBudgetManager: contexto obligatorio + pares recientes
  → Room: user visible + effectiveContent separado
  → ChatRuntimePool lease (dueño exclusivo)
  → LiteRtLmInferenceRuntime (IPC/watchdog/fallback)
  → LiteRtLmService (:litert, executor nativo)
       → identidad engine/model/backend/context/speculative
       → initialize
       → LOADED
       → warm-up temporal opcional → WARMING → WARMED/FAILED/TIMEOUT
       → conversation efectiva: reuse o rebuild con motivo
       → sendMessageAsync → tokens + métricas nativas
  → ViewModel publica estado → Compose (observación opcional)
  → Room conserva respuesta/diagnóstico; PerformanceDiagnostics sólo metadata
```

### Lifecycle y warm-up

Un solo engine residente; el pool retiene pesos entre chats mientras el proceso y la memoria lo permiten. No se mantienen Gemma y Qwen completos simultáneamente. Cambiar modelo, backend, contexto o speculative puede requerir recrear el engine. La presión de memoria y la muerte de proceso pueden forzar una carga nueva.

Warm-up OFF por defecto. Al activarlo, el worker crea una conversación temporal, envía `Hola`, limita su salida a un token y cierra esa conversación antes de preparar la real. No escribe mensajes, historial ni KV temporal en Room/ChatSessions. Mantiene sampling del perfil. Se intenta una vez por engine; cambiar de chat no vuelve a calentarlo. Cargar un engine nuevo reinicia este estado.

Trabajo fuera de Main; cancelación oficial `cancelProcess()`. El worker espera hasta 30 s y permite hasta 5 s para confirmar la terminación; el watchdog cliente de WARMING es 45 s. Si el warm-up termina sin éxito de forma segura, registra FAILED/TIMEOUT y permite usar el modelo. Si deja estado nativo dudoso, el cliente reinicia y carga una vez sin warm-up. No entra en un bucle de calentamiento. LOADED y WARMED son estados distintos. No se garantiza mejora hasta medir el TTFT posterior.

### Turnos efectivos y reutilización

El usuario sigue viendo su mensaje original. `effectiveContent` conserva exactamente el input enriquecido enviado al modelo; `effectiveModelId` evita utilizarlo como historial efectivo de otro modelo. Es dato privado local del chat, eliminado con él. Registros anteriores sin estos campos usan el historial visible y pueden necesitar una reconstrucción inicial segura.

El tracker incluye ID de chat, identidad/configuración y turnos efectivos completados. Reutiliza sólo coincidencias compatibles del mismo dueño. Errores/cancelación dudosa invalidan la sesión; los turnos incompletos no pasan a historial válido. Motivos observables incluyen REUSED, MODEL_CHANGED, ENGINE_CHANGED, CHAT_CHANGED, CONTEXT_CHANGED, CONFIG_CHANGED, HISTORY_MISMATCH, CAPACITY_EXCEEDED, THINKING_CHANGED, MODALITY_CHANGED, SESSION_LOST, NEW_CONVERSATION y APPLICATION_PREPARATION.

Admisión para continuar texto:

```text
getTokenCount() real de la conversación
+ bytes UTF-8 de renderMessageIntoString(siguiente turno)
+ 128 de reserva
+ maxOutput
<= context
```

No existe tokenizador Kotlin oficial para contar el próximo turno sin prefill en esta API. El número de bytes es una estimación conservadora de admisión para texto, no una medición de tokens. Si falta cache count/rendering, no se afirma que haya espacio y se reconstruye. Visión/thinking no utilizan esta vía. Se evita el anterior `chars * 3` inconsistente con el presupuesto general, pero no se sustituye por una estimación agresiva char/4 para admitir KV. **Session reused + cachedTokenCount son evidencia de continuidad; no son un hit-rate de kernels KV.**

### Historial parcial

Se mantiene el orden de prioridad existente para policy/mensaje actual/salida y contexto explícito, memoria/evidencia. Con el presupuesto restante se añaden pares completos desde el más reciente hacia atrás; se detiene al primer par que no cabe y se envían cronológicamente los seleccionados. Un par muy grande puede impedir recuperar pares más antiguos: el algoritmo preserva un sufijo reciente, no una selección discontinua. La UI informa omisiones por presupuesto. No se añade summarization. El estimador general char/4 continúa siendo aproximado, no una métrica nativa.

### Backend y speculative

Texto: CPU por defecto o intento explícito `Backend.GPU()`; visión CPU, audio desactivado como antes. No NPU ni autodetección basada en el nombre Mali. La disponibilidad de la API permite intentar GPU; **no demuestra compatibilidad con Mali-G615**. La inicialización real decide. Se registra requested/effective, fallo, tiempos del intento fallido y fallback. Un fallo del perfil GPU/spec queda recordado; los mensajes siguientes no lo reintentan continuamente. Una acción diagnóstica explícita puede reintentar. Fallback a CPU/spec OFF, acotado y visible.

Speculative consulta la capacidad del archivo/bundle antes de activar el flag oficial. No selecciona un draft externo; recursos internos exactos de este Gemma: **NO DETERMINADO aquí**, se valida el bundle en el dispositivo. Se separan capacidad de modelo, API de runtime, opción solicitada y engine inicializado con el flag. `speculativeActive=true` significa que el engine aceptó la configuración; **el SDK no proporciona confirmación independiente de actividad de kernels ni acceptance rate**. Acceptance permanece null. Fallo de inicialización no se confunde con UNSUPPORTED salvo evidencia de capacidad negativa del bundle.

### Métricas y privacidad

| Capa | Mediciones / límites |
|---|---|
| APP | aceptación de send, gate wait, context build, TTFT hasta estado con primer contenido, total hasta estado final |
| ENGINE | preparation/initialization, warm-up/estado, backend requested/effective/fallback, perfil speculative |
| CONVERSATION | creación, reused/rebuildReason, `getTokenCount()` nativo previo a continuidad |
| GENERATION | TTFT desde justo antes de sendMessageAsync hasta callback útil, cuentas y velocidades de getBenchmarkInfo, generación y resultado |
| UI | adapter callback → publicación de estado; opt-in primer estado observado en Compose, no pintura de frame |
| MEMORY | PSS de main y worker antes/después; available/total system RAM |
| THERMAL | PowerManager thermal status API 29+, no grados Celsius inventados |

`prefillDurationMs` derivado de count/TPS lleva fuente `DERIVED_FROM_NATIVE_COUNT_AND_RATE`; no se presenta como timestamp nativo de fin de prefill. EOS real, acceptance, peak RAM, utilización CPU/GPU y temperatura numérica no se fabrican. Métricas no disponibles son null. TTFT ya medido se conserva si posteriormente falla la generación. Los samplers reportados son configuración, no evidencia de que un parámetro sin API nativa —como repetition penalty— se aplique internamente.

La medida APP habitual parte de `ChatViewModel.send()`; el benchmark parte de aceptación del caso por su runner y lo identifica con `ttftBoundary=BENCHMARK_ACCEPT_TO_FIRST_STATE`. No se etiqueta esa medida como un frame pintado ni como un send() de ViewModel que no ocurrió. La observación Compose opcional queda null si el chat no está visible.

Persistencia de benchmark: JSON privado atómico, hasta 500 registros; selección de modelo, basename, SHA ya importado, perfil, métricas, validación y resultado. No se recalcula un SHA multi-GB sólo para telemetría. No se guarda prompt, respuesta, memoria ni documentos. Copiar limita a 50 registros y 200 KB; Export report guarda el JSON completo mediante SAF. Los turnos efectivos privados se guardan para funcionamiento del chat, nunca se añaden al reporte.

## C. Bugs corregidos

1. Reconstrucción innecesaria por comparar Room visible con historial nativo enriquecido: representación efectiva separada y testeada.
2. Pérdida de todo el historial al no caber el bloque: selección de pares recientes y aviso de omisiones.
3. Etiquetas de speculative/capacidad confundidas con uso real: estados separados y definición explícita de engine-configured.
4. Fallback GPU/spec potencialmente repetido: fallo recordado por perfil, retry explícito y motivo visible.
5. Benchmark COLD que podía no descargar el engine retenido porque el lease no era su dueño: descarga explícita del pool bajo el gate.
6. TTFT nativo perdido cuando ya había texto pero luego fallaba la generación: metadata parcial conservada en error/cancelación.
7. Logs GGUF de contenido privado: retirados/redactados en rutas locales de logging. No se afirma una auditoría de todo el logging binario cerrado de terceros.

Fuera de esta etapa, documentados y **no corregidos**: cálculo GGUF de límite de salida que vuelve a contar el prompt en `ai_chat.cpp`, parámetros no completamente propagados al JNI GGUF, reloads GGUF y cleanup de cargas parciales/estado Error con riesgo de recursos retenidos. No se reescribió llama.cpp ni se añadieron funciones de etapa 2.

## D. Opciones nuevas

Acceso: **Settings → Local AI Performance** (sección diagnóstica de la navegación existente).

| Opción | Default | Alcance |
|---|---|---|
| CPU | ON | Seguro, mantiene chat normal |
| GPU Experimental | OFF | Texto; requiere prueba real; CPU fallback visible |
| Real warm-up | OFF | Una inferencia temporal por engine, cancelable y medida |
| Speculative Experimental | OFF | Valida bundle y aceptación de inicialización; nunca automático |
| Measure adapter callback → UI state | OFF | Diagnóstico de entrega/publicación y observación Compose |
| Run benchmark | Manual | 1–10 iteraciones, ALL/COLD/WARM/CONTINUATION |
| Copy report / Export report | Manual | Metadata JSON, sin contenido privado |

No segunda arquitectura de settings, perfiles automáticos opacos ni cambio de sampling. Preferencias existentes intactas; Room migra 3→4 con columnas nullable, sin borrar chats/proyectos/documentos/modelos. La firma y paquete del APK coinciden con 0.1.12 para actualización sin desinstalar.

### Suite reproducible y matriz

- Trivial: `Hola`.
- Factual: `¿Cuál es la capital de Japón?` (regex Tokio/Tokyo).
- Moderada: explicar una red neuronal en aproximadamente 150 palabras (validación de longitud 120–180, no evaluación semántica completa).
- Razonamiento: 3 cajas × 4 lápices − 5; validar 7.
- Continuación: recordar 17, recuperarlo y sumarle 3; validar 17 y 20.

ALL produce 11 registros por iteración: 4 COLD, 4 WARM, una semilla WARM y 2 CONTINUATION. COLD significa engine no inicializado al aceptar el caso; no purga page cache del sistema ni las verificaciones de integridad económicas ya válidas. WARM carga/prepara antes del caso y crea conversación nueva. CONTINUATION utiliza la misma sesión compatible y el historial efectivo de la suite. Prime failures también se registran, no se esconden como NOT TESTED. El gate global evita benchmarks simultáneos con inferencia de chats; conversaciones de suite no se guardan en Room.

La pantalla prepara las 12 celdas CPU/GPU × spec OFF/ON × COLD/WARM/CONTINUATION. Compara último run por celda, mismo modelo/SHA/sampling/context/salida/warm-up; muestra medianas por prueba. SUPPORTED significa inicialización y generación de esa celda completadas con perfil efectivo solicitado; no prueba compatibilidad universal. UNSUPPORTED requiere evidencia negativa de capacidad; FAILED conserva errores/fallbacks; NOT TESTED significa ausencia de ejecución.

## E. Tests y compilación

Entorno: Linux, JDK de ejecución `/workspace/.toolchain/jdk-17`, Android SDK `/workspace/.toolchain/android-sdk`. Son verificaciones de host, no un test físico ni un CI remoto ejecutado sobre Motorola.

Comando final:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk ./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug -Parm64Only=true --max-workers=2 --console=plain
```

| Check | Resultado | Tests / fallos |
|---|---|---|
| `:app:testDebugUnitTest` | PASS | 202, 0 fallos, 0 errores, 0 skipped |
| `:litert-compat:testDebugUnitTest` | PASS, resultados existentes UP-TO-DATE | 2, 0 fallos; no nueva ejecución nativa |
| `:app:assembleDebug` | PASS | No corresponde conteo de tests |
| `:app:assembleRelease` | PASS | No corresponde conteo de tests |
| `:app:lintDebug` | PASS | 0 errores; 72 warnings app + 1 compat existentes |
| `zipalign -c -p 4` | PASS | APK alineado |
| `apksigner verify --verbose --print-certs` | PASS | Firma coincide con 0.1.12 |
| `aapt dump badging` | PASS | Paquete/versión14, minSDK29, target35, ARM64 |

Batch final BUILD SUCCESSFUL en 2m 9s; log exacto `validation/performance-stage1/delivery-checks.log`. Rondas completas anteriores también pasaron. La primera compilación detectó un import faltante de Dispatchers, corregido antes de las rondas finales; no quedan fallos abiertos. Los XML de resultados proporcionan los conteos anteriores.

Cobertura nueva: seis escenarios de history budget, canonical turns/isolation/config/model/error, admisión de contexto/UTF-8/overflow, warm-up state machine/deadlines/cancelación, GPU retry/fallback, speculative semántica, matriz/latest retry, métricas/serialization, y dos integraciones con Room/pool/gate: 22 registros para dos iteraciones de suite aislada y persistencia/reapertura/eliminación de turnos efectivos. Transporte nativo simulado en tests; las llamadas reales del SDK se verificaron compilando. No equivale a ejecutar LiteRT sobre Android.

Evidencias: `validation/performance-stage1/`, `validation/apk-013.json`. El SDK y librería JNI LiteRT no cambian; el binario GGUF propio sí cambia por la corrección de privacidad.

## F. Lo que no pudo probarse

**PHYSICAL_DEVICE_TEST_REQUIRED**:

- Carga y warm-up real del archivo Gemma del teléfono; duración/beneficio/timeout nativo.
- GPU Mali, drivers y kernels del bundle; inicialización, generación y fallback reales.
- Soporte speculative del bundle exacto y ejecución con CPU/GPU; acceptance rate no disponible incluso en SDK actual.
- Continuidad de conversación nativa y `getTokenCount()` con contenido real; hit-rate KV no expuesto.
- PSS real main/worker, RAM, thermal status y estabilidad del Motorola.
- TTFT, prefill/decode, temperatura relativa entre perfiles y calidad de las respuestas.
- UI física, navegación durante warm-up/generación, adjuntos previos y respuesta después de errores nativos.

El APK compilado no constituye evidencia de aceleración. Recursos speculative internos/draft exacto, backend por operación GPU, peak RAM y uso CPU/GPU: NO DETERMINADO. No hay cifras de rendimiento de Gemma obtenidas en Linux.

## G. Validación física en Motorola

1. Instalar 0.1.13 **como actualización**, sin desinstalar ni borrar datos. Comprobar chats, proyectos, documentos y modelos anteriores.
2. Abrir la app, seleccionar Gemma y esperar Ready / MODEL LOADED. Abrir Settings → Local AI Performance y elegir Gemma.
3. Dejar CPU, speculative OFF, warm-up OFF. Ejecutar 1 iteración ALL. Export report. Son varias cargas COLD; puede tardar minutos.
4. Con el teléfono en condiciones estables y frío, repetir con 3 iteraciones; exportar. Mantener SHA/context/salida/sampling/seed iguales y anotar condiciones de carga/batería. No mezclar ejecuciones con thermal status muy diferente.
5. Activar Real warm-up, esperar MODEL WARMED o revisar fallo/timeout. Ejecutar la misma suite CPU/spec OFF; exportar. Comparar WARM TTFT y coste de preparación/warm-up frente a OFF. Warm-up no tiene que producir mensajes visibles.
6. Abrir un chat normal: enviar dos mensajes. En Details/diagnóstico comprobar REUSED, cache count nativo y prefill del segundo. Repetir con memoria/contexto documental existente: Room visible diferente no debe causar por sí solo HISTORY_MISMATCH. El cambio de chat/modelo sí debe aislar/reconstruir.
7. Probar GPU Experimental con spec OFF y exactamente la misma configuración/warm-up. Ejecutar 1 iteración ALL y exportar. Si falla, comprobar motivo y CPU efectivo; la matriz debe marcar FAILED. Enviar después un mensaje normal: debe funcionar en CPU sin repetir el fallo GPU por cada mensaje.
8. Sólo si el bundle declara soporte, activar speculative para CPU, ejecutar y exportar. Revisar si el flag fue aceptado o hubo fallback; no interpretar “Supported” como uso. GPU/spec ON se prueba sólo si GPU/spec OFF funcionó.
9. Comparar por prueba y estado, no promediar preguntas distintas. Repetir los perfiles que funcionaron con 3 iteraciones y condiciones térmicas comparables. Revisar validationPassed y respuestas visibles para no aceptar velocidad por truncación o peor calidad.
10. Probar Cancel benchmark/warm-up y volver a CPU/spec OFF/warm-up OFF: chatear normalmente. Retroceder al Workspace durante un chat y reabrirlo; verificar procesamiento app-owned, aislamiento y ausencia de pérdida de datos. Reinicio del proceso puede requerir recarga, esto es esperado.

No hay ajuste automático del perfil ganador. Conservar CPU si GPU/spec fallan, empeoran total/TTFT, consumen más memoria de la aceptable o degradan calidad/estabilidad. Exportar el reporte completo; Copy report es una vista acotada.

## H. Tabla final

| Funcionalidad | Antes | Ahora | Verificado en host / CI equivalente | Requiere Moto |
|---|---|---|---|---|
| Benchmark | Diagnósticos de generaciones sueltas | Suite manual/iteraciones/JSON/matriz | Runner/persistencia/aislamiento | Sí, cifras reales |
| Cold/Warm/Continuation | Sin suite comparable | Límites explícitos, semilla etiquetada WARM | Tests del runner/pool | Sí |
| TTFT | Métricas parcialmente combinadas | Nativo, APP, benchmark host y UI opt-in separados | Agregación/IPC/tests | Sí, tiempos |
| Warm-up | Sólo engine init | Generación temporal medida, cancelable | Estados/watchdog/fallback compilados/testeados | Sí |
| Reuse | Mismatch visible/efectivo | Turnos efectivos + razones + cache count | Canonical/pool/Room/config/errors | Sí, LiteRT nativo |
| Historial | Bloque completo | Pares recientes que caben y aviso | Unit tests | UI física |
| GPU | CPU exclusivo | Opt-in, fallback visible recordado | Política y transporte simulado/build | Sí, Mali |
| Speculative | OFF; soporte confundible | Support/request/engine-configured separados | Capacidad/política/serialization | Sí, bundle |
| RAM/thermal | Sin reporte coherente por request | PSS main/worker, RAM y status antes/después | Compilación/API/serialization | Sí |
| Privacidad GGUF | Logging de contenido | Rutas locales retiradas/redactadas | Inspección y build nativo | Logcat físico recomendable |
| Datos/CPU normal | Room3/CPU | Room4 aditivo, CPU default | Migración, deletion, suite 202 | Upgrade/UX físico |

## I. Resultados esperados y decisión con datos

- **Warm-up:** comparar WARM-new-conversation native y end-to-end TTFT OFF/ON. Reportar además initialization + warmup y tiempo hasta Ready: desplazar trabajo al inicio no es reducirlo. Si no mejora el primer turno útil, no activarlo por defecto.
- **KV:** mismo chat/configuración, REUSED y cachedTokenCount real, junto con lastPrefillTokenCount/TPS y TTFT. Menor prefill es señal útil; sessionReused solo no demuestra hit-rate de KV. MEMORY/RAG históricos ya enviados no deberían causar mismatch por diferencia con Room.
- **GPU:** backend efectivo GPU sin fallback, init/TTFT/prefill/decode/total y PSS/thermal/validación comparados contra CPU. No decidir por un único caso o por el nombre de GPU.
- **Speculative:** flag aceptado en ese engine, misma salida/configuración, decode TPS y total mejores sin coste prohibitivo de init/TTFT/RAM/calidad. Acceptance permanece NO DISPONIBLE. No default automático.
- **Chat:** comparar APP gate/context/native/UI para localizar tiempo fuera del modelo; avisos de historial/salida y validationPassed evitan confundir truncación con mejora.

No se prometen porcentajes ni una cifra mínima de tokens/s. El APK permite obtener evidencia reproducible antes de escoger nuevas optimizaciones.

## J. Pendientes para etapa 2 — no implementados

1. Tool calling y skills.
2. Thinking configurable para usuario.
3. Documentos estructurados y parsers ampliados.
4. RAG con embeddings reales.
5. Memoria semántica/selectiva.
6. Ejecución Python/código con sandbox.
7. Audio input/STT/TTS/output.

Deuda separada: defectos GGUF no críticos de esta etapa. NPU, entrenamiento/fine-tuning, E4B y generación de imágenes siguen fuera de alcance.
