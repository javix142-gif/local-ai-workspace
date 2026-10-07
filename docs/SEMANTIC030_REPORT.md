# Local AI Workspace 0.3.0 — Semantic Layer V2

Estado: integración Android implementada; **NOT YET DEVICE VALIDATED**.
Este informe distingue compilación/pruebas host de ejecución física. Ningún resultado
Linux se presenta como rendimiento del Motorola.

## A. Baseline y retorno

No existe `.git` en el workspace recibido: commit/tag **NO DETERMINADO**. No se
inventó un commit ni se reescribió historia. Se congeló el código antes de modificarlo:

`/workspace/baselines/local-ai-workspace-0.2.4-source.tar.gz`

SHA-256: `f145f5649d5eccd31e1b6b18ff18e38495dcd03fd1161337cdf8447ff182fa69`.
Incluye código, wrapper, configuración y fuentes vendorizadas; excluye artefactos
reconstruibles y caches. La baseline tenía versionCode 21, versionName 0.2.4.
0.3.0 usa **22 / 0.3.0**, mismo applicationId y certificado de instalación.
El resultado físico 33/33 de 0.2.4 es el aportado por el usuario; no se ejecutó de
nuevo en este host. La suite histórica y sus expectativas se conservaron byte a byte.

## B. Runtime discovery

Dependencia exacta conservada: `com.google.ai.edge.litertlm:litertlm-android:0.17.1`.
Fuentes Kotlin oficiales vendorizadas: commit
`5e58e9a0aef7abf7091207a8b1d1063a1c800f08`.

API comprobada en fuentes y símbolos nativos arm64:

- `EmbeddingEngine(EmbeddingEngineConfig)`, `initialize`, `isInitialized`,
  `computeEmbedding`, `computeEmbeddingBatch`, `close`.
- `EmbeddingOptions(normalize, insertSpecialTokens, outputSize, visionTokensPerImage)`.
- `InputData.Text`, `InputData.Image(bytes)`, `InputData.Audio(bytes)`.
- `Capabilities.inputModalities()`, backend CPU/GPU y backends vision/audio separados.

No se usa `Conversation` para embeddings ni se añadieron bindings JNI. No hay
actualización del runtime generativo. El artefacto Android expone estos símbolos;
la ejecución efectiva de ese artefacto sobre Mali/Android sigue pendiente.

No hay API Kotlin pública de enumeración de signatures/longitudes, cancelación
nativa de embeddings, backend GPU efectivo ni residencia real de encoders.
Inicialización y probes reales sustituyen discovery no disponible, sin inventarlo.
No existe `InputData.Video`: **RUNTIME/API BLOCKER para video nativo directo**.

## C. Dependencias

Sin dependencias nuevas de IA ni cambios de SDK/NDK/runtime/JNI. Se reutilizaron
Room, coroutines, Gson, parsers y preprocesadores existentes.

## D. Modelo EG2

Repositorio oficial: `litert-community/embeddinggemma-2-740m-litert-lm`.
Revisión: `24d962e906c7d332c6428e71c9676855024569e2`.
Archivo genérico: `embeddinggemma-2-740m.litertlm`.
Tamaño medido: **484622336 bytes** (no variante SoC-specific).
SHA-256 calculado sobre el archivo descargado:
`e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`.

La importación local vuelve a calcular SHA; identifica este export exacto mediante
manifest fijado. Un archivo diferente se rechaza explícitamente; no se reconoce por
nombre. Model descriptor separa hash, versión HF y versión runtime.

| Nivel | Capacidades |
|---|---|
| Modelo / model card | Text, Code, Image, Audio, Video; 768/512/256/128 declarados |
| Runtime / bundle | Text, Vision, Audio; video=false en Capabilities |
| App | Text/Code/Image/Audio; video temporal mediante frames representativos |
| Probes host reales | 768 y 256 finitos/unitarios; imagen/audio nativos 768 |
| Dispositivo | NOT YET DEVICE VALIDATED |

Sólo 768 y 256 están habilitados; 768 es default, 256 experimental. No se afirma
validación de 512/128. No se incluye el modelo de ~485 MB en el APK ni se descarga
al abrir la app. Importación SAF funciona offline.

## E. Semantic Layer V2

Dominio central Android-free:
`app/src/main/java/com/localai/workspace/semantic/v2/SemanticDomain.kt`.

`EmbeddingProviderV2` recibe Text/Code/Image/Audio y contratos de VideoSegment;
`EmbeddingEngineManager` adapta oficialmente LiteRT y `LegacyEmbeddingProvider`
adapta EG1 GGUF/JNI. `SourceProvider` es contrato, no un connector ficticio.
Scopes: GLOBAL, USER, PROJECT, AGENT, TASK, SESSION. No hay agentes físicos nuevos.

`EmbeddingSpaceKey` incluye provider, family, hash exacto, versión de modelo,
dimensión, normalización, task profile y schema. Representación canónica con
longitudes, identificada por SHA. Cosine rechaza espacios o dimensiones distintos.
EG1-768, EG2-768 y EG2-256 nunca se mezclan vectorialmente.

`TaskPromptProfile.v1` centraliza los nueve templates oficiales. Prefijos sólo para
Text/Code. Inputs media se envían como bytes reales, sin captions/ASR intermediarios.
Toda salida se valida finite/nonzero y L2. Truncación se renormaliza; se solicita al
SDK el tamaño de salida y se verifica la longitud realmente recibida.

`ContextBuilder` produce evidencia con provenance y costo **estimado** bajo
presupuesto. Actualmente alimenta interop diagnóstico real; no cambia el chat por
omisión. **El RAG normal del chat sigue utilizando EG1**. No se promociona EG2 ni se
sustituye silenciosamente la memoria existente.

## F. Base de datos / reversibilidad

Decisión deliberada: una **Room sidecar nueva `semantic_v2.db`, v1**, aditiva a la
aplicación, en lugar de migrar la DB productiva v8. No se tocó `WorkspaceDatabase`,
no hubo destructive migration. El rollback a EG1 no requiere desinstalar.

Tablas: `semantic_models_v2`, `semantic_sources`, `semantic_segments`,
`semantic_embeddings_v2` (links), `semantic_vector_payloads_v2` (payload único),
`semantic_index_jobs`, `semantic_active_indexes`.

Sources no están restringidas a File; segmentos conservan página/líneas/startMs/
endMs. Un segmento admite múltiples representaciones. Vectores únicos por
contentHash + space + task: generaciones nuevas comparten payload físico, sin
copiarlo en cada reindexación. Serialización explícita **FLOAT32_LE_V1**.
768D ocupa 3072 bytes y 256D 1024 bytes de payload; SQLite overhead se mide aparte.

Blue/green: nueva generación se construye sin borrar la anterior; sólo una
transacción final READY conmuta el pointer. Fallo/cancelación no sustituye el índice
activo. Los índices anteriores se conservan y pueden restaurarse, incluso si después se
marcaron NEEDS_REINDEX por cambios de documentos; sólo generaciones completas
(conteo validado) se pueden activar. FAILED/CANCELLED/INDEXING no se restauran. Eliminar modelo
referenciado por índices se bloquea; no destruye vectores implícitamente.
Jobs interrumpidos pasan a CANCELLED al recuperar la UI. Corpus sin cambio reutiliza
vectores por hash. La reanudación fina de un job parcial no está implementada.

Tests Room verifican que proyectos, chats, mensajes, modelos, memoria aprobada y
semantic_vectors EG1 de v8 permanecen intactos al abrir la sidecar. **Esto no es una
migración v8→v9** ni debe presentarse como tal.

## G. Indexación

`SemanticLayer`: Source → parsers existentes → segmentos → fingerprint → provider
→ persistencia → pointer. I/O y nativo fuera de Main, jobs observables/cancelables.

- TXT/CSV/XLSX/DOCX/PDF/ZIP: parsers 0.2.4 sin reescritura; se reutilizan documentos
  y segmentos ya extraídos. Título/chunk se formatean una vez.
- Código: fallback line-aware de hasta 1800 caracteres, filename/language/líneas;
  no parser universal ni división arbitraria de una línea gigante.
- Imágenes: JPEG privado de preprocesador existente; bytes a `InputData.Image`.
- Audio: WAV PCM preparado por pipeline existente, hasta 30 s; `InputData.Audio`.
  Es embedding directo; no transcripción.
- Video: hasta 100 MiB / 60 s, ventanas de 5 s, frame central escalado 448px.
  Método **FRAME_SEQUENCE_REPRESENTATIVE_IMAGE**, no native video. Cada frame tiene
  provenance start/end y embedding real de visión. No se analiza automáticamente
  toda la pista de audio ni se garantiza localizar un objeto fuera del frame elegido.

Los hashes media corresponden a los bytes preparados efectivamente enviados, no a
un timestamp que impediría dedup. Cambios de hash/espacio/dimensión requieren nueva
indexación; el índice anterior permanece disponible.

## H. Retrieval

API `SemanticLayer.search(query, scope, modalities, limit)` devuelve scores y
provenance completo. Soporta queries de imagen desde API de dominio; la UI mínima
actual presenta búsqueda textual e importación/indexación de medios.

FTS existente + cosine exact-space + RRF. No se heredó threshold 0.25 de EG1;
EG2 usa top-k sin threshold para calibración. No se fusionan rankings vectoriales
EG1/EG2. Límite explícito de 10000 representaciones por corpus; requiere escalar el
retriever más adelante, no se oculta una truncación de resultados.

Benchmarks controlados: español/inglés paráfrasis, distractor, código, bicicleta
roja/coche azul, tono/ruido y video temporal. Recall@1/3/5 y MRR calculados a partir
de rankings reales. nDCG no se añadió. No hay scores esperados hardcodeados.

## I. Recursos y observabilidad

Un único engine EG2 residente, mutex/single-flight y `inferenceGate` compartido con
Gemma. Carga lazy, unload explícito, transición de modalidad controlada; no hay
inferencia pesada concurrente accidental. Texto no solicita encoder vision/audio;
la primera imagen/audio reconstruye configuración necesaria. Un texto posterior
reutiliza el engine expandido. Residencia interna real de encoders: unavailable.

CPU default. GPU sólo petición experimental: error visible y CPU fallback recordado
por hash evita retry loops. **GPU initialization accepted != GPU active**: backend
GPU efectivo permanece null, evidence `GPU_REQUEST_ACCEPTED_EXECUTION_UNVERIFIED`.
No NPU. Vision/audio CPU explícitos. No perfil AUTO opaco.

EG2 corre nativo en el proceso app sobre IO; no hay worker de embeddings separado.
Worker PSS de EG2 es null. PSS app se muestrea, RAM/thermal antes/después; peak es
**peak observado**, no una medición continua garantizada. No se inventan grados C.
Benchmark EG1/EG2-768/256 manual, 1–20 iteraciones, cold load/first/warm/P50/P95/
throughput/storage y JSON copiable. No se ejecuta al iniciar la app.

Cancelación nativa es cooperativa antes/después de compute síncrono. Timeout coroutine
no puede matar una llamada nativa colgada: **RUNTIME/API BLOCKER** para hard timeout.
No aislamiento de crash nativo adicional respecto del proceso app.

## J. Tests

JVM final: **419 tests app + 2 litert-compat = 421**, cero failures/errors/skipped;
39 tests nuevos y las suites históricas conservadas. Resultados definitivos y comandos: ver `docs/validation/semantic030/test-summary.json`
y `final-checks.log`. Suites nuevas: dominio/espacios/L2/prefijos/quality/provenance,
Room/payload dedup/blue-green/recuperación/scopes, manager/lifecycle/fallback/cancel,
chunking de código. Mocks sólo en tests de manager, no ruta productiva.

La revisión final también corrigió la selección temporal tras importación y el task
CODE_QUERY para consultas naturales limitadas al corpus de código; ambos tienen
cobertura JVM. Un test nuevo detectó pérdida de posición de líneas al comenzar código con salto de
línea; se corrigió y se repitieron gates. No se alteraron expectations históricas.
El APK instrumentado incluye smoke de navegación Models → Semantic V2; se construye,
**no se ejecutó** sin Android conectado.

## K. Build / lint

Los seis comandos exigidos se ejecutan juntos con Java17, SDK local, ARM64:

```
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
 :app:assembleDebug :app:assembleRelease :app:lintDebug \
 :app:assembleDebugAndroidTest -Parm64Only=true \
 -Pkotlin.compiler.execution.strategy=in-process \
 -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain --offline
```

Resultado final: **los seis comandos PASS**, BUILD SUCCESSFUL en 11m49s.
Lint: **0 errores, 39 warnings y 1 Information**. Los IDs/detalles se adjuntan en
lint-summary.json y el XML original; no se ocultaron warnings. Existe la advertencia
de deprecación de WebSettings.databaseEnabled en PythonService ya existente.
No se ejecutaron tests instrumentados ni suites físicas en este host.

## L. Regresión y privacidad

`source-audit.json` compara hashes de baseline. Sólo cuatro archivos existentes
cambiaron: build.gradle.kts (versión), AppGraph (servicio lazy y aviso de documento
indexado), MainActivity y SemanticSettings (entradas compactas a la sección).

Sin cambios a inferencia generativa, Tool Calling, Thinking normal, warm-up, KV,
sampling, CPU/GPU generativo, speculative, Python, parsers, vision/audio del chat,
memoria antigua, schema8, benchmarks antiguos ni las 33 assertions Device Validation.
El hook de documentos V2 omite documentos de validación antigua y captura errores
sin bloquear EG1. Los modelos/Python/native assets existentes se comparan en APK.

No cloud fallback, descarga automática ni telemetría obligatoria. Logs/reportes
registran estados/tamaños/tiempos/hash, no contenidos completos privados. Reportes
V2 persisten en almacenamiento privado y se copian sólo por acción explícita.

## M. APK

`dist/apk/local-ai-workspace-0.3.0-semantic-v2-arm64.apk`.
Tamaño: **99016291 bytes**. SHA-256 APK:
`0b334f2c649a4037262e8e0c17828b5d28902a65984ea76c25fb68a7a42c96ab`.
Certificado SHA-256:
`f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`.
Firma verificada y zipalign16KiB PASS. **22 archivos nativos/Python de la baseline
comparados: cero cambios**. Metadatos completos en `apk-verification.json`.
Firma de desarrollo existente, no una nueva clave release comercial.

## N. Checklist Motorola

1. Instalar encima de 0.2.4; **no desinstalar**, mantener datos.
2. Seleccionar Gemma 4 E2B y esperar Ready.
3. Device Validation → **Full**, iterations=1; exportar JSON. Esperado histórico
   33/33, pero sólo afirmarlo después de ejecutarlo físicamente.
4. Descargar manualmente el archivo genérico oficial, no el paquete MT6991/MT6993.
5. Models → Semantic V2 → Import EG2; esperar validación; Select CPU, 768D.
   Mantener también EG1 300M instalado para probar fallback.
6. Run Semantic V2 validation (20 casos, una iteración); Copy report. Cualquier
   FAIL/BLOCKED debe investigarse, no convertirse en PASS. Interop exige Gemma 4
   seleccionado. No descargar ni cambiar modelos durante esta suite.
7. Ejecutar benchmark CPU 1 iteración y después 5–10; comparar 768/256 con iguales
   condiciones: P50/P95, first/warm, PSS/RAM/thermal, quality rankings y storage.
8. Proyecto → Semantic V2 → Build project index; probar búsqueda, indexar imagen,
   audio/video corto. Cambiar dimensión, construir índice nuevo y restaurar anterior.
9. Reiniciar app y comprobar persistencia; unload/reload. Activar modo avión y repetir
   búsqueda/indexación con modelos ya instalados.
10. GPU experimental sólo después de CPU: revisar backendEvidence/fallback. No asumir
    ejecución Mali efectiva ni que sea más rápida. Registrar ambos JSON y condiciones.
11. Tras EG2, chat normal de varios turnos/imagen/audio y revisión TTFT/RAM de Gemma.

Descarga pinned:
https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/24d962e906c7d332c6428e71c9676855024569e2/embeddinggemma-2-740m.litertlm

## O. Preguntas abiertas / límites

- Android/Mali/rendimiento/RAM/thermal/calidad multimodal: NOT YET DEVICE VALIDATED.
- API nativa video, signature enumeration, hard cancel, GPU efectivo/residencia:
  RUNTIME/API BLOCKER; no se simuló soporte.
- EG2 no es default del RAG de chats ni reemplaza memoria EG1; promoción posterior
  debe depender de JSON físico y una decisión explícita.
- 256 sólo se mantiene experimental; calidad comparada por suite, no decidida de antemano.
  El corpus controlado es pequeño: no constituye calibración estadística de documentos
  personales ni justifica un threshold productivo o promoción automática.
- No UI completa de galería/video seek/queries de imagen ni connector de fuentes futuro.
- No índice resumible fino/ANN para corpus masivo; índices retenidos consumen storage.
- No ASR/diarización, Agents/Skills completos, Drive/calendar/Android actions, NPU,
  fine-tuning ni generación Office. Fuera de alcance productivo de esta versión.

Las probes host reales están en `host-eg2-probe.json`, `host-eg2-multimodal.json` y
`host-quality.json`; scripts/model cards/símbolos también se adjuntan. Su éxito no
es una afirmación de que "EmbeddingGemma 2 funciona en el Moto".

## Archivos modificados y nuevos

| Ruta (relativa al proyecto) | Cambio |
|---|---|
| `app/build.gradle.kts` | versión 22 / 0.3.0 |
| `app/src/main/java/com/localai/workspace/AppGraph.kt` | servicio V2 lazy, actualización del estado de índice de documentos |
| `app/src/main/java/com/localai/workspace/MainActivity.kt` | entrada Semantic V2 en Models |
| `app/src/main/java/com/localai/workspace/ui/SemanticSettings.kt` | entrada V2 en settings/proyecto |
| `app/src/main/java/com/localai/workspace/ui/SemanticV2Settings.kt` | import/validate/select/unload/index/search/rollback/benchmark/suite/report |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticDomain.kt` | inputs, providers, scopes, espacios, tasks, codec, provenance, context, quality |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticDatabase.kt` | sidecar Room, links/payloads únicos y pointers |
| `app/src/main/java/com/localai/workspace/semantic/v2/EmbeddingEngineManager.kt` | SDK real, lifecycle/gate/backends/modalidades/numerical safety |
| `app/src/main/java/com/localai/workspace/semantic/v2/LegacyEmbeddingProvider.kt` | adaptación EG1 real sin modificar su runtime |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticLayer.kt` | importación atómica, indexación blue/green, retrieval/reindex |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticMedia.kt` | media preparada, frames de video temporal |
| `app/src/main/java/com/localai/workspace/semantic/v2/CodeSegments.kt` | chunking por líneas |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticBenchmark.kt` | benchmark reproducible opt-in y reportes privados |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticInterop.kt` | prueba RAG real con Gemma 4 seleccionado |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticValidation.kt` | suite independiente de 20 casos reales |
| `app/src/main/assets/semantic-v2/` | manifest oficial pinned + fixtures multimedia sintéticos |
| `app/src/test/java/com/localai/workspace/semantic/v2/` | cuatro suites JVM nuevas |
| `app/src/androidTest/java/com/localai/workspace/semantic/SemanticV2NavigationTest.kt` | smoke de navegación instrumentado |
| `docs/validation/semantic030/` | fuentes oficiales, discovery, probes, hashes, logs y evidencia |
| `docs/SEMANTIC030_REPORT.md` | informe de entrega |

Inventario exacto y hashes: `source-audit.json`. No existen modificaciones ocultas al
SDK vendorizado o a los subsistemas generativos congelados.

Baseline preservada en Drive: https://drive.google.com/file/d/1xjBBtFCqlloVeQ3gKTQjCgwgqhN9aa9b/view

APK en Drive: https://drive.google.com/file/d/1opV92-pXc17NgG9zMpYciQfdRqMLB5XI/view
