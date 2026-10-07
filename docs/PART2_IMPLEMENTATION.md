# Local AI Workspace — Parte 2 — 0.2.0

Implementación local en `/workspace/local-ai-workspace`. La base inspeccionada era **0.1.15 / versionCode 16**, con LiteRT-LM **0.17.1**, no 0.1.14. Esta entrega incrementa a **0.2.0 / versionCode 17**. [Descargar APK ARM64 0.2.0](https://drive.google.com/file/d/17yCnb5BFxLZwuKxidzbhxahY8b2TUrrV/view?usp=drivesdk) — instalar como actualización.

APK ARM64 firmado: **98065334 bytes /93,52MiB**, SHA256 `c5c87d2bdd1f1aa0acd1c2a4f654c06b8b9c7c476e7825e42772553cbeaacb3d`. [Manifest verificado](validation/part2/apk-manifest.json). Misma firma/package que0.1.15; las ocho bibliotecas nativas previas son idénticas, sólo se añade `liblocal-embedding.so`. Incremento de APK:6054909bytes; encoder de318,14MiB se importa aparte.

La aceptación de inferencia real en el Motorola permanece **PHYSICAL_DEVICE_TEST_REQUIRED**; compilación y pruebas Linux no sustituyen esa prueba.

## A. Archivos modificados

Listado completo y comprobable: [modified-files.json](validation/part2/modified-files.json). Snapshot anterior de `app/src` y Gradle: [source-before.tar.gz](validation/part2/source-before.tar.gz). No se modificó código vendorizado de LiteRT ni llama.cpp.

| Archivo/ruta relativa real | Cambio |
|---|---|
| `app/build.gradle.kts` | Versión 0.2.0/code17; mismo package y compatibilidad Android29+ |
| `app/src/main/AndroidManifest.xml` | Servicio privado `:python`, sin permiso de micrófono |
| `app/src/main/java/com/localai/workspace/AppGraph.kt` | RAG neuronal auxiliar y callback de indexación |
| `LocalAiApplication.kt` en el mismo package | Evitar crear graph en workers; clase abierta para fixture UI |
| `data/AssistantSettings.kt`, `data/AssistantRouting.kt` | Preferencias por proyecto/chat, Auto conservador y selección de schemas |
| `domain/tools/ProjectTools.kt`, `domain/tools/ToolFramework.kt` | Lectura scoped/listado, schemas oficiales, validación, límites y timeouts |
| `inference/NativeChatTools.kt` | Adaptador OpenApiTool, ejecución real, loop, persistencia privada y IPC de estado |
| `inference/LiteRtLmService.kt`, `LiteRtLmInferenceRuntime.kt` | Tools/Thinking/audio mediante APIs oficiales; backends multimodales explícitos |
| `inference/VisibleModelOutput.kt` | Suprimir analysis; enviar sólo respuesta final o texto sin canales |
| `inference/LiteRtConversationPayload.kt`, `LiteRtConversationReuse.kt`, `LiteRtEngineResources.kt` | Historial efectivo multimodal, configuración compatible y aislamiento |
| `inference/LiteRtVisionInput.kt`, `LlamaCppInferenceRuntime.kt` | Audio + texto validado; GGUF rechaza audio, sin descarte silencioso |
| `domain/model/WorkspaceModels.kt`, `ConversationPrompt.kt` | Campos aditivos de configuración/request/historial de audio |
| `data/WorkspaceDatabase.kt`, `Entities.kt`, `Daos.kt`, `WorkspaceRepository.kt`, `ChatHistoryBuilder.kt` | Migraciones aditivas 5→6→7→8; vectores, tipo de memoria, referencia de audio, recuperación/cleanup |
| `documents/StructuredDocuments.kt`, `data/DocumentIngestion.kt` | XLSX/CSV estructurados, ZIP seguro y parsers documentales acotados |
| `semantic/EmbeddingModels.kt`, `SemanticRetrievalService.kt` | Encoder real bajo demanda; persistencia, RRF/cosine/FTS, memoria relevante y diagnóstico |
| `llama-runtime/src/main/java/com/arm/aichat/EmbeddingGemma.kt` | JNI auxiliar con handles independientes |
| `llama-runtime/src/main/cpp/local_embedding.h`, `local_embedding_jni.cpp`, `CMakeLists.txt` | Inferencia neuronal CPU/mmap 768 dimensiones; nueva biblioteca auxiliar |
| `llama-runtime/build.gradle.kts` | Respetar propiedad ARM64-only existente; default conserva ARM64/x86 |
| `python/PythonTool.kt`, `PythonService.kt`, `app/src/main/assets/python/*` | CPython WASM local, WebView privado, límites externos y licencias/provenance |
| `audio/AudioInput.kt` | Preparación Android local + WAV validado/limitado |
| `ui/ViewModels.kt`, `MainActivity.kt`, `ui/AssistantControls.kt`, `SemanticSettings.kt`, `ChatContent.kt`, `UiPresentation.kt`, `MemoryViewModel.kt` | Chips compactos, cards, ajustes scoped, adjunto audio, memoria aprobada y diagnóstico |
| `performance/PerformanceSettings.kt` | Warm-up ON sólo si no había preferencia persistida; se respeta cualquier elección existente |
| `app/src/test/java/...` | Tests de schemas/SDK, tools, Thinking, documentos, persistencia/migración, RAG/memoria, Python, audio y UI |
| `scripts/test_python_sandbox.cjs` | Ejecución real reproducible CPython/WASM en Linux |

Las rutas abreviadas de la tabla pertenecen a `app/src/main/java/com/localai/workspace/`. README, CHANGELOG y este informe documentan la entrega. [Capturas host/Robolectric](validation/part2/screenshots/) son evidencia visual de UI; no son capturas del Moto.

## B. Tool calling real

Flujo: UI → `ChatViewModel` → `ModelLoadConfig.enabledTools` → IPC LOAD → `ConversationConfig.tools`/`automaticToolCalling` → SDK detecta llamada nativa → `OpenApiTool.execute` → `ToolRegistry` → resultado estructurado → **SDK envía tool_response al modelo** → respuesta final. No se extrae una llamada de un bloque JSON libre inventado por la app.

APIs comprobadas en `litert-compat/src/main/java/com/google/ai/edge/litertlm/`: `OpenApiTool`, `tool`, `ToolManager`, `ConversationConfig`. El modelo debe declarar Function Calling y el worker lo verifica antes de configurar herramientas.

| Tool | Argumentos | Función/límites |
|---|---|---|
| calculator.evaluate | expression:string | Aritmética por parser; 256 caracteres, sin eval/shell |
| files.list | objeto vacío | Hasta 10 documentos del proyecto; IDs, título y estado |
| files.read | documentId:string; sheet/column/aggregate opcionales | Documento READY del proyecto, pasajes ≤4000 caracteres; selección de celdas y `sum` numérico cacheado en XLSX/CSV |
| python.execute | code:string | CPython virtual local; opt-in y límites descritos en G |

Schemas tipados, argumentos desconocidos rechazados, hasta 8 ejecuciones y bloqueo de llamada idéntica. SDK limita adicionalmente a 25 rondas, incluidas llamadas rechazadas. Tools normales: timeout5s; Python: techo de tool50s con deadlines internos menores. Errores/rechazos devuelven estado, no éxito fingido. Todas son READ_LOCAL/computación aislada. Política existente WRITE/EXTERNAL_SIDE_EFFECT requiere confirmación; no hay acciones destructivas nuevas.

`Tools: Auto` selecciona sólo schemas habilitados y pertinentes. `Off` no registra providers. Ajustes pertenecen al proyecto; un chat independiente tiene su owner aislado. Cards compactas Calculator/Files/Python; Details conserva código/args/resultado **privados en Room**, no en logcat/benchmark.

Limitación explícita: turnos con tools reconstruyen Conversation porque el tracker actual no contiene todos los turnos internos TOOL del SDK. El engine residente sigue aprovechándose. No se afirma KV reutilizado sobre un transcript incompleto.

## C. Thinking

OFF: generación directa. ON: `ThinkingConfig(true)` y configuración oficial del template embebido. AUTO (default): análisis explícito, inconsistencias, razonamiento paso a paso/código; Hola/factual corta OFF. No segundo LLM ni tokens especiales insertados manualmente. Modelo/runtime incompatible no activa Thinking.

`VisibleModelOutput` descarta analysis y canales desconocidos. No se guarda ni muestra CoT. Diagnóstico distingue modo elegido/efectivo. Cambio efectivo registra THINKING_CHANGED. Thinking conserva el límite de salida existente: analysis puede consumir parte de ese presupuesto. Thinking ON reconstruye de forma conservadora porque la app no conserva analysis como historial canónico; mensajes normales Off/sin tools conservan reutilización de KV previa.

## D. Documentos

| Formato | Ruta y límites |
|---|---|
| XLSX | ZIP/XML → workbook/relaciones/sheets → celdas con hoja/fila/columna/A1/valor/tipo/fórmula → filas estructuradas → chunks → retrieval/tools. 100000 celdas,32768 caracteres/celda,128 por nombre de hoja y presupuesto conservador de representación JSON≤8MiB; no edición ni evaluación de fórmulas |
| CSV | Delimitador fuera de comillas, escapes/multilínea → filas/celdas → representación estructurada. 10000 filas,1000 columnas,100000 celdas,32768 caracteres/celda |
| ZIP | Inspección de 100 entradas; máximo8MiB por entrada/32MiB expandidos, ratio≤200 en entradas grandes; tipos compatibles mediante temporales privados, nunca nombre externo como ruta de extracción |
| PDF | PDFBox scratch temporal → páginas/texto;100 páginas,1M caracteres; sin OCR. PDF sin texto: «Este PDF no contiene texto extraíble» |
| DOCX | ZIP/XML acotado, sin DOCTYPE/entidades → texto. No reconstrucción completa de tablas/layout/imágenes |
| TXT/MD/JSON/XML/YAML/código | UTF-8 estricto o UTF-16 con BOM; estructura textual preservada |
| HTML | Saneamiento previo: sin script ni markup en contexto |

Entrada máxima20MiB; parseo interruptible con timeout20s y deadlines internos. La lectura estructurada desde una tool también usa runInterruptible para respetar la cancelación de su timeout. El presupuesto JSON se comprueba antes de materializar filas/headers, evitando expansión de cadenas compartidas repetidas. Encoding ambiguo se rechaza; no fallback de binario a raw text. ZIP bloquea absolutos, ../, backslash, drive paths, duplicados y expansión excesiva. No ejecuta archivos ni extrae ZIP anidados. Una fórmula XLSX se conserva con valor cacheado; sumar una columna incluye todas sus celdas numéricas cacheadas, incluso una fila Total si está presente. No se deduce significado contable. La suma rechaza precisión o escala/exponente superiores a128 para impedir asignaciones desmedidas con valores científicos maliciosos.

`files.read` obtiene celdas seleccionadas sin enviar toda la planilla al modelo. La selección tiene límites explícitos/truncation. Chunks/excerpts siguen presupuestados, con IDs de evidencia sólo de contenido realmente incluido.

## E. EmbeddingGemma + RAG

Auxiliar: **ggml-org/embeddinggemma-300M-GGUF Q8_0**, no reemplaza Gemma4E2B. Se usa llama.cpp vendorizado, que implementa `gemma-embedding`; no se confunde TFLite+SentencePiece con un bundle `.litertlm`.

Enlace de pesos fijado:
https://huggingface.co/ggml-org/embeddinggemma-300M-GGUF/resolve/0f741b5a6585bd53aeb15cd1372c56f2a0f65e12/embeddinggemma-300M-Q8_0.gguf

SHA256: `b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63`.

Import explícito Settings → Semantic search. Valida arquitectura,768 dimensiones e inferencia antes de sustituir configuración. Aproximadamente318MiB de pesos auxiliares, **no incluidos en APK**. CPU/mmap, contexto512tokens reales, salida768float normalizada L2. Encoder abre bajo demanda dentro de mutex y se cierra al finalizar indexación/consulta; no queda residente permanentemente. Texto se acota a1000 caracteres antes de tokenización; si excede512tokens reales, error y fallback visible. No se presenta conteo estimado como medido.

SQLite `semantic_vectors`: origen D:chunk/M:memory, documentId/memoryId, modelo+hash, dimensión, versión, sourceHash, vector float32 little-endian, updatedAt. Vectores válidos se reutilizan; cambios de origen/modelo/version invalidan. RAG conserva FTS + cosine≥0.25 + RRF;top3 por defecto, candidatos≤1000chunks/proyecto, excerpts≤1000chars. Sin reranker. Index project explícito y callback tras import con encoder configurado.

Encoder ausente/fallo: **LEXICAL + aviso**, nunca HashEmbedding presentado como semántica real. No recalcular todos los vectores en cada consulta. Primera indexación/cambio de modelo puede costar tiempo apreciable; conviene Index project antes de chatear.

Prueba **real Linux/x86** con el mismo core C++:768dimensiones, norma1, cosine relevante0.506576 frente a control0.0894677. Evidencia: [embedding-real-linux.json](validation/part2/embedding-real-linux.json); [probe y receta reproducible](validation/part2/embedding-probe/README.md). No es benchmark ni inferencia de Android.

## F. Memoria semántica

Sólo USER_APPROVED/ACTIVE; GLOBAL o PROJECT exacto; tipo preference/fact/context. UI permite elegir tipo/scope, editar y archivar. No aprendizaje silencioso ni escritura de memoria desde documentos/tools.

Retrieval por relevancia: EmbeddingGemma/cosine≥0.40, hasta4memorias; fallback léxico por términos si falta encoder. Saluditos/cálculos triviales no introducen memorias irrelevantes. Edición/archivado invalidan vectores; borrar proyecto elimina índices scoped, no recuerdos globales de otros ámbitos. Historial textual y memoria son fuentes distintas y siguen presupuestadas.

## G. Python

**Pyodide0.27.7 / CPython3.12.7**, npm oficial fijado por integrity/SHA; licencias y `UPSTREAM.json` en assets. Seleccionado por arm64-independent WASM, ejecución offline, tamaño acotado y virtual filesystem. No CPython nativo con acceso libre al UID de la app ni dependencia de una app externa.

WebView privado `:python` → Dedicated Worker → CPython WASM → MEMFS. Sin file/content access, sin host filesystem; recursos HTTPS sintéticos sólo de allowlist de assets locales, CSP y bloqueo de cargas de red, fetch deshabilitado tras bootstrap. No pip/descarga de paquetes, shell/binarios externos ni subprocess. Workdir `/work` **virtual/efímero**, sin mapping de documentos privados. No puente Java que abra archivos o ejecute comandos; sólo resultado JSON acotado.

Límites: code8000chars/AST2500nodos, stdout16000chars (resultado para modelo≤3000), ejecución10s por timer externo al Worker, bootstrap30s, request45s. Memoria WASM256MiB mediante maximum real de WebAssembly.Memory antes de instanciar; **no es límite del RSS total de WebView/proceso**. Se termina Worker y se destruye WebView al finalizar/cancelar. Import/open filters son defensa adicional, no se venden como sandbox de seguridad Python por sí solos; el límite de privilegios es el navegador/MEMFS/CSP.

Librerías iniciales: math,statistics,json,csv,datetime,collections,io. Sin numpy/pandas para no ampliar APK/dependencias. `python.execute` **OFF default**, sólo seleccionable con WebView compatible. Sin permisos de red del intérprete, sin acceso arbitrario al teléfono. Details: código/stdout privados.

Ocho pruebas con CPython/WASM **real en Node/Linux** pasaron: ejecución stdlib, FS virtual, rechazo FS host/network/subprocess, output limit, heap cap, timeout externo. Evidencia: [F-python-wasm-final.log](validation/part2/F-python-wasm-final.log). Android WebView/CSP/renderer siguen pendientes de prueba física; esas pruebas no son pentest exhaustivo del navegador.

## H. Audio

+ → Audio / archivo audio → URI → copia privada≤20MiB → WAVPCM16 válido o Android MediaExtractor/MediaCodec local → WAV mono/stereo8–48kHz≤30s → `GenerationRequest.audioPath` → IPC → validación privada/WAV → EngineConfig.audioBackendCPU → **Content.AudioFile + Content.Text en el mismo Message.user** → sendMessageAsync → Gemma.

El decoder miniaudio oficial de LiteRT realiza resampling requerido por el bundle. No resampler supuesto, Whisper, ASR externo, micrófono ni TTS. Códecs comprimidos dependen de los decoders instalados de Android; error explícito si formato no decodifica. Se mide el tiempo real de preparación y metadata de entrada (duración/bytes), sin contenido/rutas en logs.

Requiere declaración Audio del modelo, runtime LiteRT y ruta implementada; worker valida backend y archivo. Error visual/audio no se transforma en texto-only. Un adjunto media por turno (imagen **o** audio), documentos aparte. Se consume al aceptar USER; mensaje conserva referencia en Room; siguiente request no lo reenvía. Historial puede rehidratar la referencia cuando reconstruye una conversación, sin convertirla en adjunto nuevo. Modelo texto→audio puede requerir reconstruir engine una vez; el engine audio-capable vuelve a servir texto sin recargar sólo por desaparecer el adjunto.

## I. Performance y observabilidad

Se conservan benchmarks cold/warm/continuation, TTFT native/end-to-end, queue/context, load/warm-up, prefill/decode/output, PSS app/worker y estado térmico existentes. CPU default; speculative OFF; no reevaluación automática GPU; sampling/contexto no se cambiaron.

Chat ⋮ Diagnostics añade Tools count/non-success/duración, Thinking requested/actual, RAG semantic/lexical/chunks/retrieval/embedding, memorias recuperadas y audio duration/bytes/backend. Native benchmark se presenta según el SDK, **no se inventan totales de tokens internos de tools** ni grados Celsius. Context build incluye retrieval; generación del turno puede incluir herramientas. Las métricas adicionales son diagnóstico de la última actividad, no profiling por frame ni contenido privado de benchmark.

No se prometen mejoras porcentuales de TTFT. Encoder y Python tienen costo de arranque; por diseño se liberan. Comparar en Moto contexto, retrieval/embedding, tool time, TTFT, RAM libre/PSS y estado térmico; probar conversaciones directas sin funciones también para detectar regresión.

## J. Security y compatibilidad

Separación trusted policy/untrusted evidence/tool results; sólo schemas habilitados. Files usa IDs scoped, no path dado por el modelo. Validación antes de ejecución, límites/loop/timeouts, errores estructurados. Recheck transaccional al persistir tools evita recrear datos de chat eliminado; RUNNING se recupera como INTERRUPTED tras error/reinicio. Cleanup de adjuntos privados tiene outbox/ref checks y límites de raíz.

Room5→6 añade resultJson;6→7 añade kind/vectores;7→8 añade audioPath. Sin destructive migration, cambio de IDs ni borrado de modelos/chats/documentos/benchmarks/preferencias. Perfil nuevo seguro y preferencias existentes respetadas. Native logging LiteRT suprimido; sin prompts/respuestas/documentos/audio/código en logcat de los nuevos subsistemas. Details sólo mediante acción explícita, datos locales privados. No cambios en runtime GGUF generativo ni reescritura de llama.cpp.

## K. Tests y builds

Resultado final: **262 tests app PASS**, **2 tests SDK PASS/UP-TO-DATE**, **8 pruebas CPython/WASM reales PASS** y **1 prueba neuronal Linux PASS**. Debug/release/lint/compilación instrumentada PASS en12m54s; lint **0 errores,76 warnings y1 information**. No se ejecutaron tests instrumentados en dispositivo.

Comandos y resultados finales: [validation-summary.json](validation/part2/validation-summary.json), [final-delivery-stable.log](validation/part2/final-delivery-stable.log). Los logs de intentos fallidos se conservan; no se presentan como PASS. Fixtures de schema/UI se actualizaron para audio y migraciones aditivas.

Comando Android (JDK17, SDK local):
```bash
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest \
  -Parm64Only=true -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=2 --console=plain
node scripts/test_python_sandbox.cjs
```

Incluye tests de UI host con Robolectric, controlador real/Room con frontera de inferencia controlada, historial/KV/isolation, schemas/tool adapter oficial, timeouts/loop, parsers, encoder persistence/cache/invalidation y audio. Test de RAG utiliza encoder **controlado** para comprobar orchestration/SQL, separado de la prueba neuronal real Linux. No afirmar inferencia física a partir de un fake runtime.

`assembleDebugAndroidTest` compila tests instrumentados; **no significa ejecutarlos**. `adb devices` no encontró dispositivos. Build entrega ARM64 para Moto; x86Android nativo auxiliar no se validó en esta entrega.

| Función | Verificado en host | Pendiente físico |
|---|---|---|
| Tools | Schema/validación/SDK ToolManager/resultado/loops/timeouts | Gemma solicita tool y consume respuesta nativa |
| Thinking | Política Off/Auto/On, filtros de canales e invalidación | Template Gemma y respuesta final en Moto |
| Documentos | XLSX/CSV/ZIP, límites, errores y cancelación | SAF/importación real y consultas del modelo |
| Embeddings/RAG/memoria | Encoder neuronal Linux + SQL/persistencia/híbrido/scope/invalidation | JNI ARM64, calidad española y RAM con Gemma residente |
| Python | CPython/WASM real, límites y políticas en Node | WebView/Worker/CSP Android y consumo RSS |
| Audio | WAV validado, request/IPC/history/consume, incompatibles/error | Codec Android + encoder Gemma + transcripción real |
| UI/performance base | Robolectric, JVM, debug/release/lint | Teclado/gestos, KV nativo, TTFT/RAM/thermal físicos |

## L. Limitaciones restantes

- Inferencia Gemma real tools/Thinking/audio y WebView sandbox requieren Moto; no se declara aceptación física completa.
- Thinking/tools rebuild conservador; tracker no conserva turnos internos tool/analysis.
- EmbeddingGemma import aparte, ≤1000candidatos/proyecto, input512tokens, sin reranker/summarization. Primer índice puede ser caro. Diagnóstico señala lexical fallback.
- CSV no adivina Latin1 ambiguo; XLSX no evalúa fórmulas/edita; DOCX no replica layout; PDF sin OCR; ZIP no anidado.
- Python sin numpy/pandas, shell, networking ni mapping de archivos del teléfono. Límite WASM no RSS total; compatibilidad WebView física pendiente.
- Audio sólo archivos≤30s, sin grabación/streaming de voz/TTS; un media por request.
- No GPU/Mali/performance/RAM/thermal físico validado. No porcentajes ni calidad neural en Moto inventados.

## M. Validación Moto

Fixtures ficticios listos para importar: [moto-fixtures.zip](https://drive.google.com/file/d/1F3V1rQmHmpxj_e--CKbKMXn_wSMxKKdr/view?usp=drivesdk). Contienen TXT, CSV, XLSX y ZIP normales, sin datos privados.

Instalar el APK firmado como actualización. **No desinstalar ni borrar datos**. Mantener Gemma 4 E2B, el contexto/sampling actuales, CPU, warm-up y speculative OFF. No evaluar GPU automáticamente.

1. Abrir app, esperar Ready. Settings → Developer / Diagnostics → Local AI Performance: guardar benchmark CPU (cold, warm/new conversation, continuation), 3 iteraciones. Registrar RAM/estado térmico; no comparar ejecuciones calientes con dispositivo ya throttled.
2. Chat nuevo, Tools Auto: «Usa la calculadora para calcular 123*47». Debe aparecer Calculator Completed, resultado 5781. Details debe contener ejecución real SUCCESS. Tools Off: repetir y comprobar que no hay card ni llamadas nuevas.
3. Proyecto: importar TXT «VENTAS_PRUEBA: 12345». Preguntar «Usa files.list y files.read para leer el archivo». Verificar IDs reales del proyecto, Files Completed y contenido correcto. En otro proyecto no debe tener acceso.
4. Think Off, Auto y On: probar «Hola», «¿Cuál es la capital de Japón?» y «Analiza paso a paso: tres cajas tienen 4 objetos cada una; se retiran 5, ¿cuántos quedan?». Respuesta 7. Diagnóstico debe mostrar modo solicitado/efectivo y THINKING_CHANGED al cambiar. Nunca debe mostrar analysis como respuesta.
5. XLSX: hoja Sales, A1=Item, B1=Total, B2=12, B3=4, B4 fórmula SUM(B2:B3) con valor cacheado. Inspeccionar vista previa: hoja, dirección, valor/fórmula. Pedir sum de columna B: incluye todos los valores numéricos cacheados; no vuelve a calcular fórmulas ni deduce exclusión de una fila Total. Para sólo 12+4, usar hoja sin fila Total. No confundir suma de todas las celdas con total contable.
6. CSV UTF-8: name;value / "a;b";12 / b;4. Leer columna B y sumar: 16. UTF-16 con BOM también; bytes inválidos sin encoding claro deben generar error.
7. ZIP normal con TXT/CSV: listado/lectura. ZIP con ../escape.txt: rechazo, sin escribir fuera de privados. ZIP anidado no se extrae/ejecuta. PDF escaneado sin texto: error explícito, sin contenido inventado.
8. Descargar/importar explícitamente EmbeddingGemma Q8_0 desde el enlace fijado en el informe: Settings → Semantic search → Import EmbeddingGemma GGUF. Proyecto → Settings → Index project. Importar dos documentos con temas distintos; preguntar con paráfrasis sobre uno. Diagnostics: SEMANTIC + LEXICAL, chunks, tiempo. Citas sólo de los fragmentos incluidos. Reiniciar app y comprobar persistencia del índice; editar/reimportar archivo debe invalidar el hash relevante. Sin encoder: LEXICAL + aviso.
9. Guardar memoria aprobada «Prefiero respuestas breves» (GLOBAL o PROJECT). Preguntar «¿Cómo prefiero las respuestas?»; verificar retrieval. «Calcula 5+5» no debe introducirla. Otro proyecto no recupera memoria scoped. Editar/borrar, comprobar invalidación. No se guarda memoria por sugerencia del documento.
10. Si WebView >=91, habilitar python.execute manualmente en Tools (OFF de fábrica). «Usa Python para imprimir la media de [1,2,3]». Card Python SUCCESS, stdout 2. Details: código y salida acotados. Probar bucle infinito (TOOL_TIMEOUT), import subprocess/urllib y /etc/passwd (rechazo), print de >16000 caracteres (OUTPUT_LIMIT). No hay numpy/pandas ni acceso a archivos importados por path; /work es virtual y efímero.
11. + → Audio: archivo de 5–10 s con voz clara, «Transcribe este audio». Debe responder a contenido real. Composer limpio; mensaje conserva Audio attachment. Segundo turno sin reenvío de audio; chat nuevo sin audio heredado; volver al chat y reiniciar proceso conserva referencia. Modelo sin Audio: bloqueo. Audio inválido o >30 s: error, nunca envío sólo-texto. Imagen: repetir prueba visual del hotfix, retirar con X y comprobar no reenvío.
12. Conversación de diez turnos, salir al Workspace durante generación y volver. Comprobar resultado persistido, ausencia de cards RUNNING tras error, CPU normal y benchmark posterior. Comparar TTFT native/end-to-end, context build, retrieval/embedding, tool duration, decode, PSS app/worker, RAM libre, estado térmico y duración total con paso 1. No exigir porcentajes inventados: documentar cifras reales y error rates.

No marcar estos pasos PASS sin ejecutarlos en el teléfono. JSON/texto de benchmark no debe contener prompts, documentos, recuerdos, respuestas, audio ni imagen. Details es acceso explícito a datos privados locales.

## N. Pendientes para Parte 3 — no implementados

1. Google Drive / búsqueda web.
2. AppFunctions / Accessibility agent.
3. Calendario, alarmas, contactos/SMS y acciones Android con confirmación.
4. Routers Laya/FunctionGemma.
5. Fine-tuning, E4B/modelos Power.
6. Generación de imágenes.

No se añadió ninguno de esos controles ni integraciones en la app.
