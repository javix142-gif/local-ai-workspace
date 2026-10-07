# Local AI Workspace 0.4.1 — Normal Chat Generation Hotfix

## A. Reproduction

Se comparó la baseline congelada 0.3.1 (SHA `5e1405da528b2b636a048be422e9fde0f86d0f9f3e7de8b827e04cecb1f23683`) con fuentes 0.4.0 antes de modificar producción. Diff exacto previo: `docs/validation/chat041/baseline031-to040.diff`; reparación exacta 0.4.0→0.4.1: `hotfix040-to041.diff`, validada contra SHA de cada fuente anterior. No hay Git, no se inventan tags.

Prueba dirigida antes del fix: ChatViewModel, Room, preparación, ChatRuntimePool y mutex reales; sólo inferencia nativa sustituida por un double en **test**. OFF recibió contenido visible y completó. ON quedó antes de `runtime.generate`, con gate tomado y ninguna solicitud al backend. Reproducción y XML preservados. **No es reproducción de inferencia nativa ni del bloqueo físico OFF.** No hay Motorola conectado a ADB.

## B. Root cause

**Demostrada en ON:** `ChatViewModel.send` tomaba `graph.inferenceGate` antes del ensamblado de contexto. `ContextFoundation.build → MemoryManager.queryEmbedding → ContextFoundation.embedding` vuelve a adquirir ese mismo mutex, bien mediante EG1 `withLock`, bien a través de `EmbeddingEngineManager.serialized`. Kotlin Mutex no es reentrante. El request espera su propio gate; Stop cancela la espera y libera el mutex. Inspector/ContextInterop construyen contexto fuera del gate de Gemma, por eso no siguen esta secuencia defectuosa.

**Demostrada por regresión de cancelación temprana:** un job cancelado antes de empezar no entra en try/finally. La prueba inicial de Stop inmediato agotó su deadline esperando `isGenerating=false`. Además, la captura inicial de RAM estaba fuera de try. Se protegieron ambas ventanas.

**NO DETERMINADO:** causa exacta del caso físico OFF + Think OFF + Tools OFF + `hola` después de force-stop. El deadlock ON no demuestra ni explica por sí solo ese caso. No se atribuye al runtime, al modelo, a OOM ni a una configuración persistida sin evidencia. La reparación física completa queda pendiente de smoke y tracing.

## C. Why 0.3.1 worked

0.3.1 no llamaba ContextFoundation dentro del gate; no podía incurrir en esta reentrada introducida en 0.4.0. Con OFF, los cambios 0.4.0 incluían leer `graph.contextFoundation.enabled`, inicializando innecesariamente la nueva foundation incluso para legacy. Se elimina esa dependencia: lectura del mismo flag persistido sin abrir esa infraestructura.

No se encontró cambio entre 0.3.1 y 0.4.0 en LiteRT callback adapter, VisibleModelOutput, ChatRuntimePool, RuntimePreparation o inferencia nativa. Que esos archivos sean idénticos no prueba que un comportamiento físico OFF sea imposible; por eso se conserva la incertidumbre.

## D. Stall phase

Reproducción ON: ensamblado V1 / adquisición del embedding gate, **antes del SDK generativo**. El registro previo de model READY/WARMED no demuestra que `sendMessageAsync` haya sido ejecutado para el request atascado.

Nuevo journal durable: REQUEST_CREATED, CHAT_INITIALIZATION_WAIT/CHAT_INITIALIZED, MODEL_PREPARATION_WAIT/MODEL_READY, CONTEXT_BUILD_START, RAG_START/COMPLETE, MEMORY_START/COMPLETE, CONTEXT_V1_BUILD_START/COMPLETE, CONTEXT_BUILD_COMPLETE, CONVERSATION_ACQUIRE_START/GATE_ACQUIRED, CONFIG_BUILT, CONVERSATION_ACQUIRED, GENERATION_START, SDK_SEND_START, SDK_CALLBACK_FIRST/FINAL, UI_FIRST_CONTENT, RUNTIME_METRICS, PERSIST_START/COMPLETE, REQUEST_COMPLETE/CANCELLED/FAILED, REQUEST_SETTLED.

`SDK_SEND_START` observa el checkpoint **existente** del worker `LITERT_PREFILL_START`, justo antes de sendMessageAsync; se declara esa frontera en JSON. No inventa una llamada nativa completada. Se conservan además los cambios significativos de checkpoint del worker (verificación, creación, render, etc.). Un replay de StateFlow durante load nunca se clasifica como callback de este request: sólo se habilitan esos marcadores al empezar su generación. No se escribe un journal por token ni por frame. StateFlow puede conflar checkpoints; un evento ausente no demuestra que no sucedió. `firstCallbackMs` sólo se copia de métricas reales del runtime; hasta entonces null. Primer contenido UI se mide separadamente. `nativeInFlight` es observación/null, no acceso ficticio a un lock privado.

## E. Fix

`ui/ViewModels.kt`: OFF conserva el orden original de adquisición; ON no retiene gate generativo mientras V1 hace embeddings; convergencia de ambas rutas en el mismo load/generate/collect/persist/finally; flag OFF sin acceso a ContextFoundation. Job `CoroutineStart.UNDISPATCHED` para garantizar entrada en try antes de devolver aceptación, y medición de RAM dentro de try. No timeout nuevo ni reinicio silencioso.

`diagnostics/NormalGenerationTrace.kt`: metadatos limitados a 64 checkpoints, tiempos monotónicos, IDs opacos, flags, backend y clases de error. AtomicFile en IO; fallo de journal no falla inferencia. Reinicio con request incompleto conserva fase/evidencia y marca processRestartObserved; no reporta éxito ni OOM. No acepta prompt/respuesta/ruta/exception.message.

`AppGraph.kt`: acceso lazy al journal. `ui/PerformanceScreen.kt`: última fase y Copy normal chat trace, sólo diagnóstico. `app/build.gradle.kts`: 0.4.1, versionCode25.

## F. Feature flag

OFF mantiene assembler legacy, retrieval legacy y la misma inferencia. No consulta memoria estructurada ni Semantic V2 adicional, ni abre memory_context.db por enviar un mensaje. ON modifica únicamente ensamblado y reserva embeddings antes de la adquisición para Gemma. Mismo GenerationRequest/config/stream/persist/cancel después del ensamblado. Visión/audio/GGUF conservan su ruta legacy.

## G. Cancel / retry

Try abarca la primera suspensión. El job entra en él antes de que Stop pueda cancelar un job nunca iniciado. Cancelación de espera por gate sigue liberando sólo si se adquirió; cancelación nativa sigue el adapter validado y su acknowledgement/reset existente. Request termina con journal y UI sin false PASS. No se borra historial fallido ni datos para recuperar el chat.

## H. Tests

Nuevos tests host: plain legacy OFF, context ON, project-memory ON, Think OFF/tools OFF, Auto/Auto, primer contenido, final/persist, rechazo de replay antiguo como callback nuevo, cancel/retry, cancel/new chat, recreación graph, toggle ON/OFF, OFF con Room memoria bloqueada, OFF con sesión embedding bloqueada, OFF sin abrir sidecar, Stop temprano/retry. Tests del journal: recuperación tras reinicio, timestamps/privacy, aislamiento de run IDs y archivo corrupto.

Frontera nativa host es un double **sólo en tests**. Cuatro pruebas Android usan Gemma instalado y el ChatViewModel normal, sin diagnosticSmoke/validation-owner bypass: OFF dos turnos, ON, proyecto-memory y cancel/retry. Usan sólo workspaces/memoria sintéticos propios, restauran el flag; no requieren clean install. Requieren modelo real, dispositivo o se omiten explícitamente; no simulan éxito.

Suite JVM final: **497 tests (495 app + 2 litert-compat), 0 failures, 0 errors, 0 skipped**. Son 477 previos + 20 nuevos. El fallo intermedio de `context_v1_toggle_on_off` ocurrió en `@After / Dispatchers.resetMain`, no en la generación: el harness ahora espera los scopes de todos sus ViewModels antes de reemplazar Main. Se conservaron log/XML y todas las assertions del caso. No se cambió producción para acomodarlo.

## I. Build / lint

Gates obligatorios: app:testDebugUnitTest, litert-compat:testDebugUnitTest, app:assembleDebug, app:assembleRelease, app:lintDebug, app:assembleDebugAndroidTest. arm64, JDK17, Gradle8.10.2, dependencias pinned existentes. No pruebas físicas ejecutadas en Linux.

| Comando | Resultado |
|---|---|
| :app:testDebugUnitTest | PASS — 495 tests |
| :litert-compat:testDebugUnitTest | PASS — 2 tests |
| :app:assembleDebug | PASS |
| :app:assembleRelease | PASS |
| :app:lintDebug | PASS — 0 errors, 40 warnings, 1 information |
| :app:assembleDebugAndroidTest | PASS — construido, no ejecutado |

Último gate completo: BUILD SUCCESSFUL, 8m39s. Advertencia Kotlin de PythonService.databaseEnabled obsoleto preexistente; ese archivo no se modificó. XML lint completo incluido, sin ocultar advertencias.

## J. Unchanged subsystems

Sin modificaciones en Gemma/bundle, LiteRT-LM0.17.1, CPU/context4096/output256/warmup/speculative defaults, sampling, adapter LiteRT, VisibleModelOutput, session/KV implementations, Thinking/tool semantics, Python, Vision, Audio, EG1/EG2, Semantic V2, MemoryManager/ContextFoundation/ContextBuilder, ni schemas WorkspaceDB/semantic_v2.db/memory_context.db. Catálogos históricos33, semantic20 y context24 intactos. Se cambia el **orden de adquisición en el caller normal**, no el mutex/manager compartido.

## K. APK

`dist/apk/local-ai-workspace-0.4.1-normal-chat-hotfix-arm64.apk`. Package/firma existentes; actualización encima de 0.4.0, sin desinstalar ni borrar datos. VersionName0.4.1 / versionCode25; applicationId `com.localai.workspace`.

99,622,557 bytes. SHA-256: `56e47434a1ff233efb3f308536e3f88b9fbe35eef930d78ca308b8997d4b7676`.

Certificado SHA-256: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Firma verificada, zipalign16KiB PASS. Comparación contra APK0.4.0: 22 bibliotecas nativas/assets Python, **0 diferencias**.

## L. Motorola targeted checklist

1. Instalar 0.4.1 encima de 0.4.0. Esperar Ready; no reimportar modelos ni borrar datos.
2. Context Builder OFF, Think OFF, Tools OFF. Chat nuevo: `hola` → respuesta visible → completado/Ready. Segundo turno `¿cómo estás?` → completa.
3. Iniciar respuesta más larga; Stop; otro envío → debe completar. Otro chat tras Stop también debe completar.
4. Proyecto A, flag OFF, `hola` → completa.
5. Flag ON, chat nuevo Proyecto A: `¿Qué base de datos utiliza este proyecto?` → SQLite, completa. Proyecto B → PostgreSQL.
6. OFF de nuevo, chat nuevo `hola` → completa. Si no completa, **antes de Stop** ir a Diagnostics/Local AI Performance → Copy normal chat trace. Conservar JSON y repetir copia tras Stop. No hace falta esperar un timeout artificial.
7. Inspector: Build context y Run with Gemma; confirmar scope A/B. Nunca confundir éxito del Inspector con éxito del chat normal.
8. Sólo cuando smoke normal pase: Quick Validation, esperado9/9. No Full obligatorio inicialmente: native adapter, coordinator core, KV/Thinking/tools no se modificaron. El cambio del caller de gate justifica vigilar ambos paths; si aparece regresión o se cambia core posteriormente, repetir Full33. No se tocaron ContextFoundation/MemoryManager/EmbeddingEngineManager; semantic20/context24 pueden diferirse tras sus smokes dirigidos.
9. Guardar traces incluso si smoke pasa. La validación normal nativa OFF/ON es la aceptación física, no sólo MODEL_READY.

## M. Remaining uncertainty

**NOT YET DEVICE VALIDATED.** ON deadlock y cancelación temprana se demostraron en host; el bloqueo físico OFF permanece **NO DETERMINADO**. 0.4.1 no se presenta como reparación física confirmada hasta ejecutar el smoke. No se inventan callbacks, latencias ni porcentajes. Si persiste OFF, el journal permitirá decidir si se espera preparación/gate, se entra al SDK, falta output útil o falla persistencia/finalización.

## Archivos modificados

- `app/src/main/java/com/localai/workspace/ui/ViewModels.kt` → orden de gate condicionado al flag, comienzo/cancelación protegidos y checkpoints.
- `app/src/main/java/com/localai/workspace/AppGraph.kt` → journal lazy, reportes de fixtures en su directorio de validación.
- `app/src/main/java/com/localai/workspace/ui/PerformanceScreen.kt` → lectura/copia de trace metadata.
- `app/src/main/java/com/localai/workspace/diagnostics/NormalGenerationTrace.kt` → journal metadata AtomicFile.
- `app/src/test/java/com/localai/workspace/ui/NormalChatHotfixTest.kt` → 16 regresiones del controller real.
- `app/src/test/java/com/localai/workspace/diagnostics/NormalGenerationTraceTest.kt` → 4 tests durable/privacy.
- `app/src/androidTest/java/com/localai/workspace/ui/NormalChatNativeDeviceTest.kt` → 4 pruebas con Gemma real instalado, no ejecutadas en Linux.
- `app/build.gradle.kts` → versionName0.4.1/code25.

La corrección no altera expectations de tests anteriores. El log de la falla de cancelación temprana se incluye como evidencia, junto al resultado corregido. Los doubles no forman parte del APK productivo.
