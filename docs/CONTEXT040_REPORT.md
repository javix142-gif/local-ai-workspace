# Local AI Workspace 0.4.0 — Context + Memory Foundation

## A. Baseline

Base real: 0.3.1, versionCode 23. No existe Git en el workspace; no se inventó un commit ni tag. Se congeló una copia reconstruible del código **antes de modificarlo**:

`/workspace/baselines/local-ai-workspace-0.3.1-source.tar.gz`

SHA-256: `5e1405da528b2b636a048be422e9fde0f86d0f9f3e7de8b827e04cecb1f23683`.

El APK anterior permanece en `dist/apk/local-ai-workspace-0.3.1-semantic-stability-arm64.apk`. La evidencia física 33/33 + 20/20 de 0.3.1 corresponde al reporte del usuario; no se volvió a ejecutar en este host. El archivo `baseline031-hashes.json` permite comparar cada fuente anterior.

## B. Memory architecture

`context/MemoryManager.kt` conserva datos canónicos aprobados y un outbox `INDEX_PENDING`. Tipos FACT/PREFERENCE/DECISION/EVENT/RELATIONSHIP/PROJECT_CONTEXT/SUMMARY/PROCEDURE/TASK_STATE/OTHER; scopes reutilizados GLOBAL/USER/PROJECT/AGENT/TASK/SESSION. Scope/status/TTL se aplican en SQL **antes** de ranking. Búsqueda estructurada por tipo/subject/predicate complementa búsqueda lexical/semántica. No existe aprendizaje silencioso.

Create explícito → ACTIVE; propuesta → PENDING; aprobación explícita; edición → nueva versión + supersedes; expiración → EXPIRED; borrado → DELETED + eliminación de los vectores propios. Las versiones anteriores conservan su procedencia, pero no se recuperan. Se detectan patrones de credenciales: candidatos rechazados, escritura explícita advertida en UI. Este detector no garantiza descubrir cualquier secreto imaginable.

Dedup por scope + tipo + SHA de contenido normalizado NFC/espacios, **case-sensitive**: `Foo()` y `foo()` nunca se fusionan automáticamente. Las memorias PROCEDURE preservan contenido exacto e indentación; fuentes e historial usan fingerprints del texto original, evitando confundir código con espacios distintos. Pinning, importancia y confianza forman parte del ranking. Memorias existentes pueden copiarse explícitamente desde el almacén aprobado legacy; la copia es idempotente por referencia original y no modifica ese almacén.

## C. Database

Nueva Room sidecar `memory_context.db`, versión 1, con `memories`, `context_vectors`, `conversation_sources`, `project_briefs`. No se cambió WorkspaceDatabase v8, semantic_v2.db v1, entidades/DAOs anteriores ni migraciones existentes. No hay migración destructiva ni transacción ficticia entre bases.

Una escritura de memoria y su cambio de estado son transacciones locales. El embedding se produce fuera de la transacción; antes de persistir se verifica de nuevo el hash, estado ACTIVE y TTL para impedir resurrección tras borrado concurrente. El arranque recupera estados canónicos/expiración sin cargar motores auxiliares. Un encoder ausente deja texto intacto y trabajo pendiente. La reconstrucción de pendientes es explícita; la búsqueda puede completar hasta ocho embeddings faltantes por consulta. Los modelos/espacios anteriores no se sobrescriben.

## D. Memory indexing

`ContextFoundation.embedding` reutiliza el **mismo** EmbeddingEngineManager de Semantic V2 y su gate. EG2 usa proveedor/dimensión actual, task DOCUMENT/SEARCH y exact EmbeddingSpaceKey. EG1 usa el adaptador JNI existente si se selecciona EG1. Error de proveedor → lexical fallback con código diagnóstico; nunca vectores falsos ni cloud fallback.

Vectores FLOAT32_LE_V1, dimensión validada y finite, clave exacta de espacio + hash del contenido. La serialización y comparación se delegan a SemanticVectors. El JSON exportado contiene sólo datos canónicos autorizados, sin vectores, mediante acción explícita.

## E. Context Builder

`context/ContextBuilder.kt`: ContextRequest/ContextItem/ContextBundle/DroppedContext/ContextProvenance, plantilla central V1. Acepta contexto de proyecto, memoria relevante, evidencia, historial reciente/antiguo y observaciones de tools como datos. Incluye contratos de agent/task/skill/allowedTools, sin implementar sus frameworks ni nuevas tools.

Packing determinista por prioridad/relevancia/recencia/ID, dedup de contenido, máximo tres fragmentos de una misma fuente, exclusión observable por scope/presupuesto/diversidad/duplicado. El bundle mantiene IDs y páginas/líneas/timestamps/message IDs. `safeReport()` excluye pregunta y contenido privado; expansión del inspector es explícita.

El flag **Context Builder V1 queda OFF**. Al activarlo, sólo las conversaciones LiteRT de texto usan la nueva ruta; GGUF conserva su ruta anterior. La evidencia se presupuesta en el builder nuevo y las citas se restringen a IDs realmente incluidos. Chats con imagen/audio actual o histórico conservan la ruta de 0.3.1: aún no existe conteo previo fiable de coste multimodal. Sampling, Thinking normal, tools y conversación efectiva siguen los controles existentes. No se cambia el default de contexto ni la inferencia nativa.

## F. Token budgeting

LiteRT-LM 0.17.1 ofrece `Conversation.getTokenCount()` después de ingestión, pero **no API pública para tokenizar un prompt antes de enviarlo**. Se usa `ESTIMATED_UTF8_BYTES_V1`: bytes UTF-8 como límite conservador textual, más framing explícito; nunca se muestra como conteo exacto.

Input = ventana − salida configurada − reservas de schemas/resultados de tools − max(128, ceil(5% de ventana)). En 4096, margen 205. Thinking comparte salida y no se reserva dos veces. La pregunta y política obligatoria nunca se descartan ni recortan; si no caben, error explícito. Los turnos recientes se incorporan completos del más reciente hacia atrás y se detienen cuando uno no cabe.

## G. Trust boundary

Política fija y project/skill instructions habilitadas explícitamente se separan de SOURCE/MEMORY/HISTORY/TOOL_DATA. Los datos se envuelven/escapan con tipo de confianza, nunca ascienden a system. La separación estructural reduce riesgo de prompt injection; no es una garantía de obediencia del LLM. Ninguna tool nueva sensible se expone por este cambio.

## H. Project context

Brief persistido por proyecto: objetivo, estado, restricciones, decisiones, tareas y arquitectura; exportable como representación Markdown. UI accesible desde Memory → Structured memory → Project brief. El editor de memoria permite elegir explícitamente USER, GLOBAL o el proyecto actual; los previews son compactos de cuatro líneas. Se reutiliza la identidad canónica del proyecto y se comprueba que exista. Un scope de otro proyecto no se recupera.

## I. Conversation retrieval

La historia Room sigue siendo fuente original. Indexación antigua opt-in desde Inspector con ID de conversación, segmentos de mensaje y scope propietario. Se excluyen turnos recientes del retrieval antiguo. Antes de recuperar una copia se verifican conversación, propietario, estado y hash del mensaje original; borrar/mover/cambiar una conversación o mensaje impide recuperar la copia. La mera posesión de un session ID no concede acceso a otro proyecto.

Los pares recientes conservan effectiveContent y procedencia; los originales nunca se eliminan. RollingSummary queda como contrato y SUMMARY como tipo aprobado manualmente: no se añadió resumen automático ni extracción LLM de memoria.

## J. Context capacity discovery

Runtime **sin actualizar**, LiteRT-LM 0.17.1. `EngineConfig.maxNumTokens` existe; el worker permite solicitar hasta 8192. Esto prueba que una configuración puede intentarse, **no que el bundle soporte plenamente 8192**.

Diagnóstico separado solicita 4096 y candidato 8192 con fixtures conservadoramente estimados de tamaño creciente, generación real aislada, conteos nativos cuando disponibles, TTFT, duración, PSS/RAM/estado térmico antes/después y respuesta esperada. No registra texto ni salida completa. El reporte mantiene máximo del modelo desconocido y `full8192CapacityDeviceValidated=false`: cargar una configuración y pasar un fixture corto no valida la ventana completa. No existe selector productivo 8192 ni promoción automática. Ejecución Android pendiente.

## K. Tests

Pruebas JVM de memoria/canonización/scopes/TTL/dedup/versiones/borrado/provenance/SPO/secretos/outbox/cancelación/espacios/packing/protección de pregunta/trust/render/persistencia/conversaciones. Fixtures host de 100 y 1000 memorias reportan tiempos **HOST_ROBOLECTRIC_NOT_MOTO**, sin embedding nativo. Dos tests Compose instrumentados prueban acceso al editor e inspector sin generación automática; compilación no equivale a ejecución física.

Suite nueva separada CONTEXT_MEMORY_V1: 24 casos, fixture DBs aisladas; sus pruebas EG2 y Gemma sólo PASS tras callbacks/resultados reales. El caso Gemma interop requiere simultáneamente CPU desde memoria canónica y ORCHID desde evidencia: no basta responder una de las dos partes. Un encoder deliberadamente deshabilitado se usa únicamente como control negativo de recuperación canónica, sin fabricar vectores. Reporte parcial persistido tras cada caso; cancelación nunca se convierte en PASS. Las suites anteriores no se modificaron.

La última suite JVM ejecutó **477 tests: 475 app + 2 litert-compat; 0 failures, 0 errors, 0 skipped**. Incluye 34 tests nuevos del fundamento de contexto/memoria. Resultados exactos de tests/build/lint: ver `docs/validation/context040/test-summary.json` y log `host-checks.log` generados al terminar los checks.

## L. Build / lint

Comandos: :app:testDebugUnitTest, :litert-compat:testDebugUnitTest, :app:assembleDebug, :app:assembleRelease, :app:lintDebug, :app:assembleDebugAndroidTest; variantes arm64Only, JDK17, Gradle8.10.2. No se ejecutan benchmarks nativos Android en Linux. ADB no presenta dispositivos conectados.

| Comando | Resultado |
|---|---|
| :app:testDebugUnitTest | PASS — 475 tests |
| :litert-compat:testDebugUnitTest | PASS — 2 tests |
| :app:assembleDebug | PASS |
| :app:assembleRelease | PASS |
| :app:lintDebug | PASS — 0 errors, 40 warnings, 1 information |
| :app:assembleDebugAndroidTest | PASS — APK instrumentado construido, no ejecutado |

Último gate completo: BUILD SUCCESSFUL, 13m23s. Las advertencias lint se conservan en el XML incluido; no se ocultaron ni elevaron a validación física.

## M. Regression

Auditoría SHA de todos los archivos congelados: los cambios anteriores se limitan a AppGraph, LocalAiApplication, MainActivity, ViewModels (único branch experimental) y versionado. Código/schema de EG1/EG2, retrieval RRF, benchmark, native inference, Thinking, KV, Vision, Audio, Python, sampling, tools y ambos catálogos físicos quedan idénticos. No se borran chats/proyectos/modelos/documentos/memorias/vectores/índices.

## N. APK

`dist/apk/local-ai-workspace-0.4.0-context-memory-arm64.apk`, versionCode 24, applicationId `com.localai.workspace`. Firma de desarrollo existente, certificado SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Instalar como actualización **sin desinstalar**. SHA/tamaño/firma/zipalign exactos en `apk-verification.json`. Comparación byte a byte: **22 bibliotecas nativas/assets Python, 0 diferencias**.

APK: 99,557,021 bytes. SHA-256: `e2fde8b6d6f28d2260aa0741352fb6243a79aa1aa8af006345ae07602485d8d0`. Firma verificada y zipalign 16 KiB PASS.

Baseline fuente congelada disponible en Drive: https://drive.google.com/file/d/12SybtzhebRD1_9HEMNtnnzqmKPhtuwkL/view

## O. Motorola checklist — PHYSICAL_DEVICE_TEST_REQUIRED

1. Instalar APK encima de 0.3.1. Comprobar chats, modelos, proyectos y memorias anteriores. Esperar preparación normal Gemma; no cambiar sampling/backend/contexto.
2. Con Context Builder V1 OFF, ejecutar Historical Full, 1 iteración: esperado 33/33. Exportar JSON.
3. Ejecutar Semantic V2, 1 iteración: esperado 20/20. Exportar JSON. EG2 CPU/768 y modelos requeridos instalados.
4. Settings → Developer / Diagnostics → Context Inspector / Memory Validation → Run Context / Memory V1 suite. Exportar JSON mediante Copy report; esperado 24 PASS. Si hay BLOCKED/FAIL, conservar reporte y no promover el flag.
5. Guardar memoria de usuario y de proyecto A; pin, editar y poner TTL. Salir/reiniciar y comprobar persistencia. Proyecto B no debe recibir memoria A. Borrar memoria y comprobar que no vuelve a recuperarse.
6. Editar Project brief. Inspector con proyecto A y pregunta relevante → Build context, sin inferencia. Revisar IDs/scopes/budget/omisiones; texto sólo al expandir explícitamente.
7. Para historial antiguo, elegir su proyecto e introducir ID de conversación; indexar y consultar. Borrar la conversación y comprobar que no vuelve a recuperarse.
8. Run with Gemma explícito: comprobar respuesta sustentada en memoria/evidencia y conservar métricas. Si todo pasa, probar temporalmente flag ON en un chat de texto; compararlo con OFF. Vision/Audio siguen la ruta legacy.
9. Diagnóstico de capacidad separado: 4096 y 8192 candidato. Copiar reporte incluso si falla. No concluir soporte de 8192 por cargar un engine o por un prompt corto; mantener 4096.
10. Repetir flujo en modo avión con modelos instalados. Comprobar RAM/TTFT/thermal y que Gemma/EG2 sigan respondiendo. Ejecutar nuevamente benchmarks 768/256 de 0.3.1 si se desea; no se alteraron.

## P. Open questions / deliberate limits

**NOT YET DEVICE VALIDATED**: nueva suite, integración experimental, layout con teclado/Moto, latencias/RAM reales y capacidad del bundle. No se promete 8192 ni mejoras porcentuales.

No se implementó extracción automática, import JSON, resumen automático, nuevos agentes/skills/tools, ASR, cloud/Drive, conectores Android, modelos ni runtime nuevo. Los contratos admiten esas extensiones. El historial semántico requiere indexación explícita; el arranque no carga EG2 para indexar silenciosamente. La nueva memoria no sustituye automáticamente a la legacy: copia aprobada explícita y reversible. TTL usa reloj de pared Android. El estimador no mide encoders visuales/audio, por eso el flag del chat se limita a texto. Navegación pulida hacia cada fuente y calibración de relevancia quedan para después de la validación física.

## Archivos

- context/MemoryDomain.kt → tipos/scopes/normalización/propuestas/contrato de resumen.
- context/MemoryDatabase.kt → sidecar y entidades/DAO propios.
- context/MemoryManager.kt → CRUD/versiones/provenance/outbox/lookup/retrieval/export.
- context/ContextBuilder.kt → request/items/bundle/budget/trust/template/packing.
- context/ContextFoundation.kt → providers existentes/proyecto/historia/orquestación/flag.
- context/ContextInterop.kt → generación diagnóstica real y aislada.
- context/ContextCapacityDiagnostics.kt → probes separados, sin promover capacidad.
- context/ContextMemoryValidation.kt → catálogo físico separado de 24 casos.
- ui/ContextMemoryUi.kt → memoria, brief, inspector y acciones explícitas.
- AppGraph.kt / LocalAiApplication.kt → acceso lazy y recuperación canónica sin encoder.
- MainActivity.kt → entradas Settings/Project, sin nueva navegación principal.
- ui/ViewModels.kt → branch experimental de texto; legacy default intacto.
- app/build.gradle.kts → 0.4.0 / code24.
- context/ContextFoundationTest.kt → pruebas JVM y workloads host.
- ui/ContextMemoryDeviceTest.kt → pruebas UI Android preparadas, no ejecutadas en host.

## Benchmark host — no extrapolar a Motorola

| Memorias | Lookup ms | Lexical ms | Context build ms | Incluidas |
|---|---:|---:|---:|---:|
| 100 | 18.691 | 87.628 | 2.387 | 6 |
| 1000 | 68.035 | 108.317 | 0.680 | 6 |

Mediciones de una ejecución Robolectric, sin encoder nativo; no son latencias Android ni comparaciones de performance física. `lastAccessedAt` queda reservado, no representa una medición actual de acceso.
