# Local AI Workspace 0.3.1 — estabilización de Semantic V2

**NOT YET DEVICE VALIDATED.** La validación física exitosa de 0.3.0 fue comunicada
por el usuario: Historical Full 33/33 y Semantic V2 20/20. Los resultados host
que se adjuntan corresponden a pruebas JVM y compilación; no son benchmarks del Moto.

## A. Cierre del benchmark: causa y evidencia

**Causa raíz del cierre físico anterior: NO DETERMINADO.** No se recibió un
logcat, tombstone ni registro Android de salida de proceso. No se atribuye a OOM,
JNI ni lifecycle sin evidencia.

La inspección del benchmark 0.3.0 confirmó riesgos que se corrigen:

- El benchmark ejecutaba ambas dimensiones y luego cargaba EG1 sin haber descargado
  todavía EG2. El benchmark 0.3.1 es exclusivo de EG2 y no carga EG1 implícitamente.
- La creación del driver seguida de `.also { initialize() }` perdía la referencia
  al candidato cuando la inicialización lanzaba una excepción. Ahora se libera
  cualquier handle obtenido antes del fallo.
- La clave del engine no incluía dimensión. Se añade una frontera conservadora
  de configuración: cambiar de dimensión descarga el engine y lo inicializa
  bajo demanda. No se afirma que el SDK anterior fuera necesariamente incorrecto.
- No había progreso durable antes de JNI ni reporte parcial después de cada muestra.
  Ahora existen ambos. No se reclasifica retrospectivamente el cierre antiguo.

## B. Lifecycle

`EmbeddingEngineManager` serializa selección, configuración, carga, cómputo y cierre.
Orden de locks: sesión → gate de recursos compartido → lifecycle nativo.
Una sesión exclusiva cubre el benchmark completo e impide que un cambio de
configuración o cierre intercale sus operaciones. Dentro de esa sesión las llamadas
son reentrantes para el mismo manager; no se crea un segundo engine.

Cambiar 768→256 o 256→768 espera al cómputo activo, cierra de forma segura y deja
la configuración lista para carga diferida. `UNLOADED/LOADING/READY/BUSY/UNLOADING/ERROR`
son estados internos; el usuario ve estados compactos. La limpieza sigue funcionando
si el registro diagnóstico falla antes de iniciar el cierre nativo.

## C. Interfaz

Models/Settings → Semantic search → Embedding models muestra dos cards equivalentes:
EmbeddingGemma 300M y EmbeddingGemma 2 740M. La vista normal contiene nombre,
formato/tamaño/dimensión, estado y selección/importación. No muestra SHA, matrices,
reason codes, GPU, unload, Remove ni suites.

Advanced / Diagnostics contiene esos datos, CPU/GPU experimental, 768/256 experimental,
Revalidate, Unload, Remove con confirmación y protección de índices retenidos,
benchmark/comparación, progreso, cancelación y copia de JSON.

La búsqueda se abre desde Workspace o Project → Files, no desde el gestor de modelos.
La indexación de medios experimental vive junto a ese workflow. El proyecto conserva
su scope; EG1 usa su retrieval existente de proyecto. EG1 global indica que se debe
abrir un proyecto, sin fingir soporte de búsqueda global.

Los diálogos tienen scroll, límites según viewport y ajuste al teclado. Acciones
verticales evitan filas comprimidas y botones cortados. No hay nuevos controles
productivos para funciones futuras.

## D. Importación y primer lookup de la suite

La causa del antiguo `MODEL_NOT_INSTALLED` está confirmada en código: la importación
insertaba un registro validado, pero no persistía `selected`; la suite restauraba
únicamente esa selección. Pulsar Select CPU manualmente cubría ese paso faltante.

Tras copy/hash/probes reales 768 y 256, se persisten registro, selección EG2 y CPU;
queda Ready para lazy load. La configuración registrada sin selección de 0.3.0 se
repara al restaurar, preservando una selección EG1 explícita. No se cambia la suite.
El picker genérico detecta GGUF/LITERTLM; los pickers por card rechazan el provider
incorrecto antes de copiar o invocar su importador. Diagnostics conserva nombre
original, tamaño, SHA disponible, provider esperado/detectado y etapa de fallo.

## E. Error legacy

Una importación EG1 fallida es historial de EG1, no salud global ni error de EG2.
Si el archivo EG1 configurado existe con cabecera GGUF, la card indica Installed
incluso si hay un HASH_MISMATCH antiguo. El error se conserva en Diagnostics como
historial; no se elimina evidencia ni se altera el runtime EG1.

## F. Benchmark

Job propiedad de la aplicación, no del diálogo. Cambiar de pantalla no lo cancela.
Rango 1–20, default 5; todas las operaciones pesadas son secuenciales, fuera de Main.

INITIALIZE → FIRST_EMBEDDING → WARMUP → N MEASURE → QUALITY → FINALIZE.
Inicialización, primera inferencia, warmup y muestras calientes son medidas separadas.
N muestras reutilizan el mismo engine. El tiempo SDK se mide con reloj monotónico
y excluye la escritura del journal; el coste total de diagnóstico sí incluye I/O.

Se guarda `benchmark-current.json` mediante AtomicFile antes/después de cada llamada
nativa y cada iteración. Incluye runId, PID, fase, dimensión, backend configurado,
modality/configuración de encoders solicitada, timestamp, engine state, memoria,
thermal y última iteración terminada. No contiene prompts privados ni rutas privadas.

Un RUNNING heredado al iniciar se convierte en ABORTED_PROCESS_RESTART, preservando
muestras y operación en vuelo. ApplicationExitInfo API30+ aporta evidencia cuando
existe: JAVA_EXCEPTION, NATIVE_PROCESS_DEATH, ANDROID_LMK_SUSPECTED,
NATIVE_ABORT_SUSPECTED, USER_REQUESTED_STOP o UNKNOWN_PROCESS_RESTART. Un OutOfMemoryError
capturado se identifica como tal; no se inventa OOM a partir de una desaparición.

## G. Comparación

Compare 768D vs 256D configura CPU y ejecuta la misma secuencia/fixtures para ambas
dimensiones, descargando entre ellas y restaurando perfil/dimensión originales,
incluso ante cancelación o error. No se promueve 256D ni se cambia el índice activo.

JSON por dimensión: load, first, warmup, N samples, P50/P95/mean/min/max, throughput,
PSS antes/pico observado/después, RAM disponible antes/después, thermal antes/después,
bytes de vector (3072/1024), Recall@1/3/5 y MRR. El pico es el máximo de capturas
antes/después/por iteración, NO un pico continuo garantizado. EG2 permanece en el
proceso principal; PSS de un worker EG2 separado no existe.

La pequeña fixture versionada de comparación utiliza documentos y consultas ES/EN
con ground truth de recuperación de contraseña y distractores de facturación/clima.
Vectores/rankings/scores son reales en dispositivo; no se codifican scores esperados.
No reemplaza ni debilita las fixtures de los 20 casos históricos de Semantic V2.
Recommendation: NO AUTOMATIC RECOMMENDATION.

## H. Tests

Resultados finales y cantidades: `validation/semantic031/test-summary.json`.
Tests nuevos: lookup tras registro, reparación del viejo import, preservación EG1,
archivo ausente, mapping UX/legacy error, detección de formato, stress 768→256→768,
init/unload repetidos, close/compute exclusión, cleanup de handle tras fallo,
benchmark secuencial, cancelación, reporte parcial durable, recuperación, evidencia
de salida y restauración después de comparar. Mocks de driver sólo en tests JVM.

La prueba Compose instrumentada se actualiza para el diálogo compacto. Construir
su APK no implica que se haya ejecutado en un teléfono.

## I. Builds

Gates: :app:testDebugUnitTest, :litert-compat:testDebugUnitTest,
:app:assembleDebug, :app:assembleRelease, :app:lintDebug, :app:assembleDebugAndroidTest.
JDK17, SDK Android local, Gradle8.10.2, arm64Only. Logs y resultados se adjuntan.
No se modificó LiteRT-LM0.17.1 ni dependencias de inferencia.

## J. APK, baseline y compatibilidad

0.3.1 incrementa versionCode22→23. Mantiene applicationId com.localai.workspace,
firma existente y compatibilidad de actualización. Instalar sin desinstalar.
SHA, tamaño, certificado y alineación reales: `validation/semantic031/apk-verification.json`.

No existe Git en el workspace recibido; no se inventó commit/tag. Baseline congelada:
`/workspace/baselines/local-ai-workspace-0.3.0-source.tar.gz`.
SHA256: d79babd0a039878c39cccd16d195d2274e1d6114483f79894bc4b42a1bb3adce.
Inventario antes/después y protecciones: `validation/semantic031/source-audit.json`.
Room Workspace v8, semantic_v2.db v1, vectores EG1/EG2, blue/green, modelos y datos
no requieren migración ni borrado. Las funciones de indexación/search/RRF existentes
permanecen byte a byte iguales; sólo cambia configuración/lifecycle alrededor.

## K. Checklist Motorola

1. Instalar APK 0.3.1 encima de 0.3.0, sin desinstalar ni borrar datos.
2. Comprobar que chats, proyectos, modelos y proveedores siguen presentes.
3. Ejecutar Historical Full, 1 iteración. Esperado 33/33; exportar JSON.
4. Advanced / Diagnostics → Semantic V2 Validation. Esperado 20/20; exportar JSON.
5. Poner iterations=10, Benchmark CPU · 768D. Debe permanecer abierta y terminar;
   copiar reporte detallado. Revisar load/first/warmup/N samples/PSS/thermal.
6. Repetir CPU · 256D, 10 iteraciones; copiar JSON.
7. Compare 768D vs 256D, 10 iteraciones. Comprobar ambas dimensiones y que la
   configuración original vuelve después. No seleccionar 256 automáticamente.
8. Opcional: cancelar una corrida; comprobar CANCELLED y configuración restaurada.
9. Si ocurre un cierre real, reabrir, ir a Diagnostics y copiar reporte de
   ABORTED_PROCESS_RESTART: fase, nativeInFlight, lastNativeOperation, últimas muestras
   y exitEvidence. No ejecutar otro benchmark antes de copiar ese reporte.
10. Verificar diálogos con teclado abierto y search/index media desde Workspace/Project.

Un force-stop controlado sólo verifica recuperación; no demuestra la causa del crash
original. La cancelación es cooperativa: el SDK compute síncrono no puede interrumpirse
a mitad de JNI. No se finge cancelación nativa inmediata.

## L. Límites y pendientes

- La estabilidad del benchmark de 10 iteraciones debe confirmarse físicamente.
- ApplicationExitInfo puede no estar disponible; entonces se conserva UNKNOWN.
- No se migra EG2 a proceso dedicado sin evidencia. Si el nuevo diagnóstico confirma
  muerte nativa, evaluar aislamiento :semantic en una versión posterior.
- Las muestras de PSS no garantizan observar cada pico transitorio.
- GPU sigue experimental; CPU baseline. No se afirma aceleración efectiva GPU nueva.
- Validate significa probe real; suite física PASS sólo aparece si existe JSON
  persistido para el hash seleccionado, nunca por convertir la declaración del usuario.
- No se cambian tools, Thinking normal, RAG/retrieval semantics, EG1, Gemma4, Python,
  Vision/Audio productivos, KV, sampling, warmup, speculative ni schemas DB.

## Inventario de archivos de código

| Archivo | Cambio |
|---|---|
| `app/src/androidTest/java/com/localai/workspace/semantic/SemanticV2NavigationTest.kt` | Smoke Compose con labels del diálogo nuevo; no cambia Device Validation. |
| `app/src/main/java/com/localai/workspace/MainActivity.kt` | Entradas a search desde Workspace y Project Files; sin cambios en chat. |
| `app/src/main/java/com/localai/workspace/AppGraph.kt` | Controllers de importación y diagnostics compartidos por la aplicación. |
| `app/src/main/java/com/localai/workspace/LocalAiApplication.kt` | Recuperación de benchmark interrumpido al arrancar; no ejecución automática. |
| `app/src/main/java/com/localai/workspace/ui/SemanticSettings.kt` | Wrapper único del gestor compacto; se elimina error legacy global. |
| `app/src/main/java/com/localai/workspace/ui/SemanticV2Settings.kt` | Cards simples y diálogo Advanced con scroll/teclado, confirmación de Remove y progreso. |
| `app/src/main/java/com/localai/workspace/semantic/v2/EmbeddingEngineManager.kt` | Serialización de lifecycle, dimensión en clave, single-flight, cleanup y marcadores nativos. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticLayer.kt` | Selección y configuración persistidas tras importación y reparación de lookup antiguo. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticDomain.kt` | ProviderStatus añade modelId/outputDimension; espacios/vectores/tasks sin cambios. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticBenchmark.kt` | Port/runner secuencial, load/first/warmup/N/quality y resultados por dimensión. |
| `app/build.gradle.kts` | versionName0.3.1, versionCode23; sin nuevas dependencias. |
| `app/src/main/java/com/localai/workspace/ui/SemanticSearch.kt` | Workflow separado y scoped, usa indexación/retrieval existentes. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticBenchmarkJournal.kt` | AtomicFile, resultados parciales, recuperación y ApplicationExitInfo. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticDiagnostics.kt` | Job app-owned, exclusión de diagnostics, comparación y restauración segura. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticValidationSummary.kt` | Sólo lee PASS persistido para el hash correcto. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticModelState.kt` | Selección/restauración y estados UX; errores legacy son historial. |
| `app/src/main/java/com/localai/workspace/semantic/v2/SemanticImportCoordinator.kt` | Detección de provider antes de copia y diagnóstico de importación por provider. |
| `app/src/test/java/com/localai/workspace/semantic/v2/SemanticStabilityTest.kt` | 22 casos JVM de estado, lifecycle, concurrencia, benchmark, cancelación y recuperación. |

El informe y los archivos de evidencia se añaden en docs/validation/semantic031. No se eliminó ningún archivo de código.

Para comprobar el primer import en el Moto sin borrar índices, se puede usar Change model con el mismo archivo EG2 genérico y lanzar la suite inmediatamente, sin Revalidate ni Select CPU manuales.

## Resultados finales verificados

| Comando | Resultado | Tests / observación |
|---|---|---|
| `:app:testDebugUnitTest` | PASS | 441 tests, 0 fallos/errores/skips; 22 nuevos |
| `:litert-compat:testDebugUnitTest` | PASS | 2 tests, 0 fallos/errores/skips |
| `:app:assembleDebug` | PASS | APK debug .debug |
| `:app:assembleRelease` | PASS | APK release producido y luego firmado |
| `:app:lintDebug` | PASS | 0 errors/fatal; 40 warnings, 1 information |
| `:app:assembleDebugAndroidTest` | PASS | APK instrumentado construido, NO ejecutado |

Resultado Gradle final: BUILD SUCCESSFUL, 12m15s. El intento inicial de tests nuevos con Robolectric API30 no pudo descargar esa imagen; se repitió con API35 ya instalada. Los resultados anteriores no se cuentan como PASS.

Warnings lint: GradleDependency27, ApplySharedPref3, TrustAllX509TrustManager3, UseTomlInstead3; ExifInterface1, ChromeOsAbiSupport1, DataExtractionRules1, AutoboxingStateCreation1, MissingApplicationIcon1 (Information). Son 40 warnings frente a39 en baseline: el adicional ApplySharedPref corresponde al rollback explícito de configuración. Se mantiene commit síncrono para durabilidad, en el camino IO de importación. No se actualizan dependencias ni runtimes para silenciar warnings heredados.

APK: `dist/apk/local-ai-workspace-0.3.1-semantic-stability-arm64.apk`

Tamaño real: 99180131 bytes. SHA256: `7eed3a38cfa4e33dbf1addfd9b68249fce9db4c3edf8aa111e5f1a6b712fada4`.

Certificado: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`; mismo que baseline. 22 archivos nativos/Python comparados con0.3.0: ninguno cambió. Firma y zipalign16KiB PASS.

No hay dispositivo conectado (`adb devices` vacío); ni las suites físicas ni el benchmark Moto se ejecutaron aquí.
