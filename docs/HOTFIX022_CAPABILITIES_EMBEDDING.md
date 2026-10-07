# Local AI Workspace 0.2.2 — hotfix Tools, Thinking y EmbeddingGemma

Base inspeccionada: 0.2.1 (versionCode 18). Entrega: 0.2.2 (versionCode 19). Android/Compose; LiteRT-LM **0.17.1 sin actualizar**. Objetivo físico: Motorola Moto G86 Power/API 36/8 GB. No se implementó Parte 3.

## A. Causa raíz Tools

**CASE T2** en el bundle oficial verificado. `Capabilities.supportsFunctionCalling()` devuelve false porque el protobuf no contiene el campo opcional 12 `supports_function_calling`. En 0.2.1, `LiteRtLmInferenceRuntime.inspectModel()` convertía ese false directamente en ausencia de `ModelCapability.TOOL_CALLING`; `ModelImportService` lo persistía en Room. `ChatViewModel`/`AssistantRouting` no entregaban tools, Device Validation devolvía `MODEL_TOOLS_NOT_DECLARED`, y `LiteRtLmService` repetía el mismo bloqueo al leer el bundle. No era una comprobación del contrato completo.

El mismo contenedor tiene procesador **Gemma4**, delimitadores de llamadas/respuestas y un template que integra schemas, llamadas y resultados. El SDK permite activar `ConversationConfig.tools`/`automaticToolCalling` con ese contrato. Un probe neuronal con el SDK nativo 0.17.1, CPU y ese archivo real ejecuta `calculator.evaluate` y devuelve 5781 al modelo para su respuesta final. Es una prueba **Linux x86_64**, no una certificación Moto ni una ejecución del ToolRegistry Android.

## B. Causa raíz Thinking

**CASE H2** en el bundle oficial verificado. El campo opcional 11 `supports_thinking` también está **ausente**, no declarado explícitamente como false. 0.2.1 confundía el valor por defecto de protobuf con falta de soporte y guardaba/validaba la ausencia de `ModelCapability.THINKING`.

El bundle define el canal `thought` y su template reconoce `enable_thinking`. El SDK oficial resuelve `ThinkingConfig`, integra el contexto y aplica la lógica nativa de canales/presupuesto. El probe verifica Off/On con el mismo SDK/bundle: respuesta final correcta en ambos; canal de razonamiento presente sólo con On. No se guarda ni exporta su contenido. La política Auto de la app se conserva.

## C. Evidencia del bundle

Se descargó el archivo **completo** del repositorio `litert-community/gemma-4-E2B-it-litert-lm`, revisión `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`, sin modificarlo:

| Dato | Evidencia |
|---|---|
| Archivo | `gemma-4-E2B-it.litertlm` |
| Bytes | 2.588.147.712 |
| SHA-256 completo verificado | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Versión del contenedor | **1.5.0**, diferente de la versión SDK 0.17.1 |
| Metadata | Secciones FlatBuffer y `LlmMetadata` protobuf; payload inspeccionado de 12.192 bytes |
| Procesador | `LlmModelType.gemma4`, oneof campo 8 |
| Tools declarado | Campo 12 **ABSENT** |
| Thinking declarado | Campo 11 **ABSENT** |
| Contrato tools | `code_fence_start=<\|tool_call>`, `code_fence_end=<tool_call\|>`, `function_response_start=<\|tool_response>`, `use_template_for_fc_format=true` |
| Thinking | Canal `thought` y template `enable_thinking` |
| Visión/audio | Secciones reales de encoder, adapter y end-token de visión/audio; no se infiere por filename |
| SHA del template UTF-8 | `02b3091acf53c0b722e3db0c7a1b4980363edcc2d85549dafa339ff5dbfff629` |

**SHA del archivo instalado en el Motorola: NO DETERMINADO.** No se recibió ese archivo ni su JSON de validación. La app inspecciona el contenedor privado realmente instalado y aplica el contrato verificado sólo si coincide su procesador/config/template. El nombre, la familia y el tamaño no habilitan capacidades. Un bundle desconocido queda bloqueado con motivo preciso; un false explícito prevalece incluso sobre el contrato auditado.

Evidencia en `docs/validation/hotfix022/`: `bundle-reference.json`, `gemma-peek.txt`, `gemma-metadata-summary.json`, `gemma-metadata.pb`, `gemma-template.jinja`, `gemma-native-probe.json`. La metadata/template son públicos del modelo de referencia; no son datos privados del usuario.

## D. Evidencia del SDK

APIs existentes, sin modificar el SDK:

- `litert-compat/src/main/java/com/google/ai/edge/litertlm/Capabilities.kt`: getters booleanos, no getters Kotlin distintos que conserven la presencia opcional.
- `ConversationConfig`, `OpenApiTool`, `ToolManager`, `ThinkingConfig`, `Engine.createConversation`: configuración y ejecución nativas ya utilizadas por la app.
- `capabilities.cc` líneas 128–141: lee `proto_metadata.supports_thinking()`/`supports_function_calling()`, sin comprobar presencia.
- `llm_metadata.proto` líneas 104–114: **documenta explícitamente que exportaciones anteriores retornan false aunque soporten estas funciones**.
- `conversation.cc`: usa `ThinkingConfig`/`enable_thinking` sin exigir esas banderas como prerrequisito.
- `gemma4_data_processor.cc`: parser de llamadas/respuestas real.

Fuentes oficiales fijadas en revisión `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`, copias de evidencia incluidas. A/B: no exige una bandera explícita cuando el procesador/template soporta la operación. C: sí puede configurar ambas APIs, comprobado con inferencia real de referencia. D: la API booleana oficial no distingue ausencia; por eso se complementa con lectura acotada del protobuf/template. E: la app interpretaba incorrectamente el **significado** del valor por defecto; los números de campo no estaban desplazados.

## E. Cambio realizado

`LiteRtBundleMetadata` conserva `toolsDeclared`/`thinkingDeclared` como booleanos **nullable**, hash del template y prueba del contrato. `LiteRtCapabilityResolver` distingue:

- MODEL_SUPPORT: true/false/unknown.
- BUNDLE_DECLARED: true/false/**ABSENT**.
- RUNTIME_SUPPORT: APIs implementadas en el SDK fijado.
- APP_IMPLEMENTED: infraestructura real ya existente.
- ENABLED: depende de la selección/routing del turno; disponibilidad no equivale a activación.

Orden: declaración explícita → API SDK positiva → procesador/config/template auditados → desconocido/bloqueado. Tanto importación como worker usan el mismo criterio; modalidades y speculative mantienen su lógica previa. La evidencia persistida incluye fuente, estados, flags SDK originales y hash del template.

`LiteRtCapabilityRefresh.refresh()` repara **sólo** capacidades Tools/Thinking y metadata de modelos LiteRT existentes mediante SQL UPDATE; conserva IDs, configuración, demás capacidades y relaciones. Se ejecuta en IO al iniciar y antes de Device Validation, sin leer todos los pesos ni modificar el estado de capacidades del engine activo. No requiere reimportar Gemma ni migrar Room. Errores de detección conservan los datos previos y quedan marcados.

Device Validation usa motivos separados: `MODEL_CAPABILITY_MISSING`, `BUNDLE_CAPABILITY_MISSING`, `RUNTIME_CAPABILITY_MISSING`, `APP_CAPABILITY_MISSING`, `CAPABILITY_DETECTION_ERROR`. El test Calculator sigue exigiendo tool automática real, ejecución registrada, resultado 5781 y respuesta final; no acepta cálculo mental. XLSX conserva su parser y prueba nativa original.

## F. Embedding root cause

El mensaje exacto de 0.2.1 se lanzaba **después de `llama_model_load_from_file()` devolver nullptr**: etapa confirmada **MODEL_LOAD**. La causa nativa más profunda del teléfono era descartada: **NO DETERMINADO con el diagnóstico antiguo**.

Se identificó un defecto concreto del empaquetado/carga: `GGML_BACKEND_DL=ON` produce un backend CPU dinámico; el importador dependía de escanear `nativeLibraryDir`, y `liblocal-embedding.so` no tenía dependencia ELF de `libggml-cpu.so`. El APK 0.2.1 confirma `extractNativeLibs=false`; Android puede cargar `.so` directamente desde el APK y dejar aquel directorio sin librerías extraídas. `llama_backend_init()` también intenta búsqueda dinámica si el registro está vacío, pero un path dentro del ZIP no es un directorio de backends normal. En esta revisión, `llama.cpp:409` devuelve nullptr con “no backends are loaded” si el registro está vacío; también existen comprobaciones “no CPU backend found” en el model loader. El probe host reproduce el primer camino con un registro vacío controlado.

La corrección enlaza **sólo el encoder auxiliar** con la librería CPU oficial mediante nombre base (MODULE sin SONAME, evitando un DT_NEEDED con ruta de build) y llama `ggml_backend_register(ggml_backend_cpu_reg())` si no hay dispositivo CPU. No cambia la compilación global de llama.cpp ni el motor generativo GGUF. La reproducción negativa/positiva host y la inspección ARM64 quedan en las evidencias de validación.

Esto corrige un defecto real y hace observable cualquier causa restante; **no demuestra todavía que ésa fuera la única causa en ese Motorola**. El siguiente import del teléfono dará stage/code/errorClass y un mensaje nativo sanitizado.

## G. Embedding native details y flujo final

Modelo esperado: `ggml-org/embeddinggemma-300M-GGUF`, `embeddinggemma-300M-Q8_0.gguf`, SHA obligatorio `b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63`. También verificado en el GGUF completo usado por las pruebas host: **333.590.944 bytes (318,14 MiB)**.

Flujo: SAF → SOURCE_OPEN → COPY_PRIVATE (stream de 64 KiB, fsync, límite 600 MiB) → tamaño/legibilidad → HASH_VERIFY → NATIVE_LIBRARY_LOAD → GGUF_OPEN → ARCHITECTURE_VALIDATE → MODEL_LOAD → DIMENSION_VALIDATE → CONTEXT_CREATE → EMBEDDING_PROBE → validación de dimensión/finitos/norma → CONFIG_COMMIT. El wrapper vuelve a comprobar los getters de arquitectura/dimensión tras cargar; las etapas internas JNI se reportan con precisión al fallar, sin inventar callbacks de progreso ni duraciones separadas.

- El SHA se calcula sobre los bytes realmente copiados, no sobre nombre/tamaño/metadata remota. Un hash incorrecto impide invocar JNI.
- Archivo privado estable, nunca mmap sobre SAF; no se materializa el GGUF completo en Java/Kotlin.
- CPU, `LLAMA_LOAD_MODE_MMAP`, cero capas GPU; contexto/batch/ubatch 512, threads 4, embeddings=true, pooling UNSPECIFIED: configuración anterior preservada.
- GGUF noalloc valida `general.architecture=gemma-embedding` antes de crear el modelo. Getter JNI obtiene arquitectura real; `llama_model_n_embd_out()` obtiene dimensión real y se exige 768.
- Probe real de texto corto: dimensión correcta, finitos, norma L2 0,99–1,01. No se utiliza un vector hash ni un dummy como prueba física.
- Modelo y contexto se liberan con RAII; el encoder cierra antes del commit. Sigue load-on-demand.
- Native captura únicamente categorías de error reconocidas (CPU ausente, asignación, modelo no soportado, apertura); no retiene ni registra mensajes arbitrarios con paths, tokens o vectores.
- `EmbeddingNativeException` transporta stage/code/detail seguros; fallos de linker se distinguen como NATIVE_LIBRARY_UNAVAILABLE.
- Configuración anterior se conserva ante hash/probe/copy/load fallidos. Se limpia sólo la copia privada fallida, jamás el original SAF. Cleanup del antiguo privado no puede invalidar un commit exitoso.
- Settings → Semantic search muestra error por etapa y **Copy diagnostics**, persistido tras reiniciar. Un fallo conocido sin instalación válida hace Quick Semantic **BLOCKED/EMBEDDING_MODEL_LOAD_FAILED** con stage/code, no SKIP “not installed”.

No es posible convertir cualquier abort/assert fatal de una dependencia nativa en excepción Java. Hash/arquitectura verificados reducen entradas incompatibles; fallos fatales de driver/ABI/OOM todavía requieren observación física. No se afirma una inmunidad total a aborts.

## H. Archivos modificados

Inventario completo: `docs/validation/hotfix022/source-changes.json`; comparación contra snapshot previo por SHA, sin repositorio Git disponible en este entorno.

| Archivo relativo | Cambio |
|---|---|
| `app/build.gradle.kts` | 0.2.2/code19; dependencias/configuración de inferencia intactas |
| `app/src/main/java/com/localai/workspace/inference/LiteRtBundleMetadata.kt` | Presencia de flags y contrato público auditado |
| `app/src/main/java/com/localai/workspace/inference/LiteRtCapabilityState.kt` | Nuevo estado/resolver de capacidades por evidencia |
| `app/src/main/java/com/localai/workspace/inference/LiteRtCapabilityRefresh.kt` | Repair aditivo de registros existentes |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmInferenceRuntime.kt` | Detección enriquecida; lector puro para refresh |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmService.kt` | Mismo resolver en la comprobación worker |
| `app/src/main/java/com/localai/workspace/domain/model/WorkspaceModels.kt` | Evidence map adicional, sin cambio de esquema Room |
| `app/src/main/java/com/localai/workspace/data/ModelImportService.kt` | Persistencia de evidencia en metadataJson existente |
| `app/src/main/java/com/localai/workspace/data/Daos.kt` | UPDATE acotado sin REPLACE/FK cascade |
| `app/src/main/java/com/localai/workspace/LocalAiApplication.kt` | Refresh en coroutine IO existente |
| `app/src/main/java/com/localai/workspace/validation/DeviceValidation.kt` | Refresh antes del prerequisite check |
| `app/src/main/java/com/localai/workspace/validation/ProductionValidationBackend.kt` | Reason codes y bloqueo semántico preciso |
| `app/src/main/java/com/localai/workspace/validation/ValidationModels.kt` | Whitelist de stage/code nativos seguros |
| `app/src/main/java/com/localai/workspace/semantic/EmbeddingImportPipeline.kt` | Pipeline nuevo verificable de copia/hash/probe/commit |
| `app/src/main/java/com/localai/workspace/semantic/EmbeddingModels.kt` | Import robusto, rollback/cleanup y diagnóstico persistido |
| `app/src/main/java/com/localai/workspace/ui/SemanticSettings.kt` | Sólo diagnóstico de importación y copiar |
| `llama-runtime/src/main/cpp/CMakeLists.txt` | CPU oficial enlazado exclusivamente al encoder |
| `llama-runtime/src/main/cpp/local_embedding.h` | CPU registration, RAII, arquitectura/dimensión reales, errores por etapa |
| `llama-runtime/src/main/cpp/local_embedding_jni.cpp` | Propagación JNI segura y getters nativos |
| `llama-runtime/src/main/java/com/arm/aichat/EmbeddingGemma.kt` | Getters/exception tipada, mismo wrapper load-on-demand |
| `app/src/test/java/com/localai/workspace/inference/LiteRtCapabilityStateTest.kt` | 8 pruebas de presencia/contrato/precedencia/capas |
| `app/src/test/java/com/localai/workspace/semantic/EmbeddingImportPipelineTest.kt` | 18 pruebas del pipeline sin certificar inferencia física con mocks |
| `app/src/test/java/com/localai/workspace/semantic/EmbeddingModelsFailureTest.kt` | Persistencia real de preferencias/cleanup/source en Robolectric |
| `app/src/test/resources/litert/gemma4-legacy-metadata.pb` | Metadata pública de referencia, 12 KiB; no pesos |

Evidencias/reporte/harness de validación también añadidos; no son cambios de comportamiento.

## I. Tests y builds

Comando completo, ejecutado con JDK 17, Android SDK del workspace y un worker:

```bash
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk ./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest -Parm64Only=true -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain
```

**BUILD SUCCESSFUL, 22m 27s; 295 tasks (78 ejecutadas, 217 up-to-date).**

| Objetivo | Resultado | Tests/fallos |
|---|---|---|
| `:app:testDebugUnitTest` | PASS | **325 / 0**; 61 suites, sin skips |
| `:litert-compat:testDebugUnitTest` | PASS; cache en batch y repetición forzada posterior | **2 / 0** |
| `:app:assembleDebug` | PASS | Compilación Android; no inferencia física |
| `:app:assembleRelease` | PASS | APK release 0.2.2/code19 ARM64 |
| `:app:lintDebug` | PASS, **0 errores** | 77 warnings, 1 información |
| `:app:assembleDebugAndroidTest` | PASS/up-to-date | APK instrumentado compilado; **0 tests instrumentados ejecutados** |

Repetición real del SDK: mismo entorno/flags, `./gradlew :litert-compat:testDebugUnitTest --rerun ...`: **BUILD SUCCESSFUL, 3m 16s, 1 task ejecutada y 15 up-to-date**, 2 tests/0 fallos. Hubo espera/reintentos de resolución de artefactos, pero terminó correctamente. La repetición adicional con `--offline` también terminó correctamente en **38 s** (2 tests/0 fallos), usando caché. No se cuentan ejecuciones duplicadas como nuevos tests: **327 tests JVM distintos**.

Primera ejecución: 325 tests, 2 fallos en las pruebas nuevas del contrato legacy por usar campo 9 en lugar de 12 para `function_response_start`. Corregido contra el schema oficial y repetida la suite completa: 325/0. `build-first.log` conserva el intento fallido; no se presenta como PASS.

Lint subió de 76 a 77 warnings: el nuevo `ApplySharedPref` en el rollback conserva intencionalmente `commit()` para comprobar fallo de escritura, **dentro de Dispatchers.IO**, sin bloquear Main. Sustituirlo por `apply()` perdería esa comprobación. Las demás advertencias existentes (incluida la selección ARM64 para ChromeOS) no se reescribieron en este hotfix.

Pruebas nativas adicionales:

| Prueba real | Resultado |
|---|---|
| Python binding del **SDK nativo 0.17.1**, archivo Gemma completo verificado, CPU/4096 | **3 PASS**: calculator 1 llamada → 5781 → respuesta final; Thinking Off sin canal/On con canal, ambas finales correctas |
| CMake/C++ con **GGML_BACKEND_DL=ON**, GGUF completo y registro inicialmente vacío | **PASS**: CPU inicialmente ausente, MODEL_LOAD_NULL y log sanitizable “no backends are loaded” reproducidos; tras registro explícito, 768D, norma²=1, similitud relevante **0,506576** frente a distractor **0,0894677** |
| **JNI real Linux x86_64**, código de `local_embedding_jni.cpp`, firma Java equivalente | **PASS**: excepción tipada/sin path para archivo inexistente; arquitectura real `gemma-embedding`, dimensión real 768, finitos, L2 **1,0000000016840538**, cierre del handle |
| ELF/JNI **ARM64** del APK final | **PASS de empaquetado**, no ejecución: Machine AArch64, 5 exports JNI, referencia directa `ggml_backend_cpu_reg`, DT_NEEDED `libggml-cpu.so` sin rutas absolutas |
| Android/Moto | **PHYSICAL_DEVICE_TEST_REQUIRED**; ADB sin dispositivos |

Comandos reproducibles de los probes en las evidencias: `probe_gemma.py`; `embedding-probe-CMakeLists.txt`/`embedding-probe-main.cpp`; `host-EmbeddingGemma.java`/`host-EmbeddingNativeException.java`; `native-probe-build.log`. Se utilizan archivos reales cuyos SHA están fijados; los mocks del pipeline sólo prueban el protocolo de importación y no certifican inferencia.

APK firmado: `dist/apk/local-ai-workspace-0.2.2-capabilities-embedding-arm64.apk`, **98.487.396 bytes**. SHA-256: `72f9d68fdfd26eee26772923ffebacc213980e08230dde0502cae22ea0f41616`. Certificado SHA-256 igual al APK 0.2.1: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. `apksigner verify` y `zipalign -c -P 16 4`: PASS.

La comparación del paquete demuestra: **8 de 9 librerías nativas idénticas**, incluida `liblitertlm_jni.so` y todo llama.cpp/ggml; **sólo `liblocal-embedding.so` cambia**. Todos los assets Python son idénticos. Ambos manifiestos tienen `extractNativeLibs=false`, dato que respalda el defecto de dependencia de un directorio de librerías extraídas. `apk-audit.json`/`embedding-arm64-elf.txt` conservan resultados.

Evidencias de comandos/conteos: `build-final.log`, `sdk-tests-rerun.log`, `jvm-test-summary.json`, `lint-summary.json`, `gemma-native-probe.json`, `embedding-native-probe.json`, `embedding-jni-probe.json`, `apk-audit.json`.


## J. Regresiones y compatibilidad

`ChatViewModel`, ImagePreprocessor, AudioInput, Python WebView/WASM, sampling, context budget, KV/reuse, warm-up, GPU/speculative y XLSX parser **sin modificar**. La modificación compartida de LiteRT se limita a capacidades e interpretación de metadata, no sendMessage/Content/preprocessing ni parámetros. La comparación por SHA incluye fuentes y assets antes/después: 181 archivos del snapshot sin cambios, 17 modificados y 7 nuevos de fuentes/tests/fixture. El paquete final confirma las 8 librerías nativas no modificadas y assets Python idénticos.

Se conservan modelos importados, chats, proyectos, documentos, preferencias, memoria y benchmarks. No hay migración de esquema ni eliminación de datos. Instalar como actualización sobre 0.2.1, sin desinstalar ni limpiar datos.

## K. Validación Moto — PHYSICAL_DEVICE_TEST_REQUIRED

1. Instalar el APK firmado 0.2.2 como actualización. **No desinstalar**. Abrir, seleccionar Gemma existente y esperar Ready; no hace falta reimportarlo.
2. Settings → Semantic search → Import EmbeddingGemma GGUF. Seleccionar el **mismo original** `embeddinggemma-300M-Q8_0.gguf`. Esperar validación/cierre del probe.
3. Si falla: pulsar **Copy diagnostics** y guardar stage, code, errorClass, tamaño, SHA, arquitectura/dimensión y native error. No borrar el original ni declarar Semantic PASS. Un hash diferente requiere revisar la descarga.
4. Settings → Developer/Diagnostics → Device Validation → **Quick, 1 iteración**. No mandar mensajes paralelos mientras corre. Guardar/copiar/exportar su JSON real.
5. Text: PASS. Calculator: automaticToolCallingActive=true, toolCallCount≥1, registryExecutions≥1, calculator.evaluate, resultado 5781 y respuesta final 5781. Tools Off: PASS sin llamadas.
6. Thinking: Off=false; Auto simple=false; Auto razonamiento según política; On=true, respuesta final 7, sin analysis/CoT visible. Observar invalidación de conversación al cambiar configuración como antes.
7. XLSX: **mismo test** sin cambiar parser; parserValidated=true, tool automática/files.read real y total/respuesta 16. Si Calculator pasa y XLSX falla, conservar JSON para una investigación documental separada.
8. Semantic: probe JNI ARM64 real, 768D finitos/normalizados, documento relevante primero y vector persistido. Si import falló, esperar BLOCKED con stage/code preciso, nunca PASS/SKIP engañoso.
9. Python, Vision y Audio: comprobar de nuevo PASS con los fixtures reales. Hacer además un chat textual de dos turnos y un turno con imagen/audio para comprobar las funciones previamente validadas.
10. Si Quick pasa, ejecutar Full y después varias iteraciones. Guardar RAM/PSS/thermal/TTFT del reporte sin extrapolar cifras host al Moto. Reiniciar la app y comprobar persistencia de import/configuración e historial.

## L. Limitaciones

- No hay Moto/ADB conectado: no se ejecutó inferencia física ARM64 ni tests instrumentados en teléfono. Sólo se compila su APK.
- Hash/metadata exactos del Gemma instalado en el Motorola: NO DETERMINADO. El resolver inspeccionará ese archivo al actualizar.
- Causa nativa final del antiguo fallo físico más allá de MODEL_LOAD: NO DETERMINADO. Se corrigió el defecto verificable de descubrimiento/registro CPU y se añade el diagnóstico que faltaba.
- El contrato legacy se habilita sólo para el template/procesador auditado. Otro export desconocido puede requerir auditoría; no se fuerza soporte por nombre.
- No se afirma rendimiento más rápido, reducción de RAM ni porcentajes. Los probes verifican funcionalidad, no equivalencia de performance entre Linux y Moto.
- No hay OCR, tools nuevas, Part3, cambios de modelo ni upgrades SDK.

## M. Decisión

| Capability | Decisión | Motivo y límite |
|---|---|---|
| Tools | **SUPPORTED** para el bundle/contrato verificado; **BLOCKED** si el instalado no coincide o declara false | Contrato real y llamada neuronal nativa de referencia; ToolRegistry Android debe validarse en Moto |
| Thinking | **SUPPORTED** para el mismo contrato; **BLOCKED** si declaración/contrato incompatible | Config oficial real y canal separado On observado en referencia; integración física final pendiente |
| EmbeddingGemma | **SUPPORTED**, import Android pendiente; no se declara IMPORTED en el Moto | Modelo SHA fijado y corrección de CPU + pruebas host/JNI/ARM64 de empaquetado. IMPORTED sólo tras el probe real/commit en el teléfono; cualquier fallo queda LOAD_FAILED con etapa/code |

## Entrega

APK en Drive: https://drive.google.com/file/d/1Mo0fTpkxa8fBQGAfYYEBbGoaWGzpjpwQ/view

Tamaño remoto comprobado: 98.487.396 bytes, igual al archivo local firmado. El proveedor no devolvió checksum remoto; el SHA-256 y la firma se verificaron localmente. No se cambiaron permisos de sharing ni se sustituyó el APK 0.2.1.
