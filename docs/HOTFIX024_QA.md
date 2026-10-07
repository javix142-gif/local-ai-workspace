# Local AI Workspace 0.2.4 — QA / Device Validation

Base inspeccionada: 0.2.3, versionCode 20. Nueva versión: 0.2.4, versionCode 21. Paquete y certificado de actualización conservados.

La validación física 0.2.3 comunicada por el usuario fue 31 PASS, 0 FAIL, 0 SKIPPED y 2 BLOCKED. Este entorno no dispone del Motorola conectado. No se afirma un Full físico PASS de 0.2.4.

## A. Cambios realizados

Cambios limitados a Device Validation, fixtures, reglas de interpretación, métricas escalares exportables y versionado. Full conserva 33 casos y Quick conserva sus 9 casos, con los mismos IDs. No se añadieron pruebas al catálogo del dispositivo.

## B. Semántica del control Thinking 256

Antes, `ProductionValidationBackend.thinking(256)` trataba cualquier error sin respuesta visible como BLOCKED. Para el control deliberadamente ilimitado, el agotamiento era el resultado que se pretendía caracterizar: el runtime había ejecutado la prueba, observado finalización nativa y detectado el límite. Marcarlo BLOCKED confundía un resultado conocido con una prueba que no pudo ejecutarse.

Ahora `ThinkingBudgetControl.evaluate()` exige simultáneamente:

- ON solicitado y Thinking efectivo;
- `thinkingTokenBudget=-1`, salida configurada y efectiva de 256;
- `outputTokens=256` y `outputLimitReached=true`;
- callbacks y caracteres de thought positivos;
- cero callbacks/caracteres finales y cero contenido visible;
- finalización nativa observada;
- contadores de callbacks coherentes, sin canales desconocidos;
- razón interna exacta `THINKING_OUTPUT_BUDGET_EXHAUSTED`.

Sólo entonces retorna PASS, `reasonCode=null`, `expectedExhaustionObserved=true` y `observedCondition=THINKING_OUTPUT_BUDGET_EXHAUSTED`. Se mantienen los contadores y la evidencia prefijada `ON_REASONING.*`. No se almacena razonamiento interno.

Como alternativa explícita de **behavior characterized**, se acepta una respuesta final correcta `7`, validada por las reglas existentes, si Thinking ilimitado estuvo realmente activo, hubo thought, finalización nativa y contadores compatibles con la salida 256. En ese caso `expectedExhaustionObserved=false` y `observedCondition=CORRECT_FINAL_ANSWER`.

Un SDK error, desconexión inesperada del worker, configuración incorrecta, salida insuficiente sin final, callbacks inconsistentes o timeout después de iniciar la solicitud es FAIL. El coordinador también clasifica errores no capturados de estos dos controles como FAIL; una infraestructura de archivos explícitamente no disponible (FileNotFoundException/AccessDeniedException) es BLOCKED. Una ZipException no capturada del control ZIP es ZIP_CORRUPT / FAIL. Los demás IDs mantienen su contrato anterior. Un timeout antes de poder iniciar la solicitud o infraestructura no disponible es BLOCKED. Nunca se convierte un timeout en agotamiento esperado. Los controles 512/1024 y Thinking normal no cambian.

## C. Causa raíz del caso ZIP

El fixture anterior ya utilizaba `ZipOutputStream` con una entrada `../escape.txt`. No se encontró construcción manual de un ZIP corrupto. Su problema de QA era que el test sólo reconocía `IllegalArgumentException("Unsafe or duplicate archive path")` procedente de `StructuredDocuments.inspect()`.

Android 14+ activa `SafeZipPathValidatorCallback` para aplicaciones con target SDK >=34; nuestra app utiliza target SDK 35. Ese callback puede lanzar `ZipException("Invalid zip entry path: ../escape.txt")` al abrir `ZipFile`, antes del validador Kotlin de la app. La excepción no se reconocía y acababa clasificada como `EXECUTION_ZipException` / BLOCKED. El tipo observado físicamente coincide con esta ruta; el mensaje exacto de aquella ejecución no fue aportado, por lo que su origen en ese dispositivo no puede confirmarse retrospectivamente sólo con el tipo.

Fuentes oficiales inspeccionadas:

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/com/android/internal/os/SafeZipPathValidatorCallback.java
- https://android.googlesource.com/platform/libcore/+/refs/heads/android14-release/dalvik/src/main/java/dalvik/system/ZipPathValidator.java
- https://android.googlesource.com/platform/libcore/+/refs/heads/android14-release/ojluni/src/main/java/java/util/zip/ZipFile.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/com/android/internal/os/SafeZipPathValidatorCallback.java (mismo mensaje de rechazo; copia de evidencia local).

El nuevo fixture contiene `safe.txt` y `../escape.txt`, generado mediante `ZipOutputStream`, con entradas STORED, CRC calculado y timestamp fijo. Es un archivo válido cuyo defecto deliberado es exclusivamente el nombre peligroso. El test JVM abre el original con `java.util.zip.ZipFile` y comprueba ambas entradas y contenido seguro.

**Particularidad necesaria en Android:** abrir un ZIP estructuralmente válido con traversal puede estar prohibido por la plataforma. La prevalidación no desactiva `ZipPathValidator`, no usa reflexión ni constructores ocultos. Comprueba los bytes exactos del fixture sintético conocido y verifica una copia temporal que cambia únicamente los nombres `..` por `aa`, manteniendo longitud, cabeceras, directorio central y CRC. La copia debe abrir con `ZipFile` y cada entrada debe coincidir en contenido y CRC. También intenta abrir el original; únicamente la excepción de ruta exacta, una vez probada la estructura, se reconoce como rechazo de política en esa apertura. La copia se elimina y **el original peligroso** es el que pasa al parser productivo.

La prevalidación es deliberadamente exclusiva de este pequeño fixture sintético, no un parser alternativo de documentos ni un validador de seguridad paralelo.

## D. Resultado de seguridad ZIP

`ProductionValidationBackend` llama sin modificaciones a `DocumentParser.parse()` con `ValidationContext` y un cacheDir restringido al sandbox temporal del control. La ruta productiva sigue siendo:

`DocumentParser.parse → parseZip → ZipFile → StructuredDocuments.inspect → lectura acotada / archivos temporales seguros`.

La política existente valida todas las entradas antes de extraer contenido. Puede rechazar el ZIP completo; no es obligatorio procesar `safe.txt` cuando el archivo contiene traversal.

`ZipTraversalControl.run()`:

1. Prevalida estructura y entradas del fixture. Una estructura inválida es FAIL / FIXTURE_INVALID, sin invocar el parser.
2. Comprueba que no existan previamente archivos de escape en el contenedor temporal aislado.
3. Ejecuta el parser real sobre el ZIP original.
4. Reconoce exclusivamente la razón conocida de la app o la excepción Android con el nombre peligroso exacto como `ZIP_UNSAFE_PATH_REJECTED`.
5. Comprueba `parentDir/escape.txt`, `parentDir/escape2.txt`, nuevos paths dentro del contenedor temporal y paths canónicos de todo el sandbox. Comprueba además que no se haya dejado un archivo parcial `escape.txt`/`escape2.txt` dentro del root.
6. PASS sólo con fixture válido, entrada detectada/rechazada, ningún escape y todos los paths observados dentro del sandbox.

Los errores de corrupción, compresión sospechosa, cantidad/tamaño e IO se distinguen como ZIP_CORRUPT, ZIP_BOMB_LIMIT, ZIP_TOO_MANY_FILES, ZIP_SIZE_LIMIT y ZIP_IO_ERROR. Ninguna ZipException genérica es PASS. Una escritura fuera o un archivo parcial es FAIL / ZIP_FILESYSTEM_ESCAPE, incluso si también se lanzó el error de rechazo esperado.

Las métricas exportadas son escalares: `fixtureValid`, `unsafeEntryDetected`, `unsafeEntryRejected`, `escapedFileExists`, `allWrittenPathsInsideSandbox`, `parserValidated` y `observedCondition`. No contienen rutas privadas ni contenido de archivos. La comprobación del filesystem se limita al contenedor aislado de QA; no es un monitor global de todas las escrituras del proceso. La seguridad productiva y sus rutas de escritura siguen sin modificaciones.

## E. Archivos modificados

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | versionName 0.2.4, versionCode 21. |
| `app/src/main/java/com/localai/workspace/validation/ProductionValidationBackend.kt` | Interpretación especial únicamente del control 256; timeout de ese control; ejecución ZIP aislada contra DocumentParser. |
| `app/src/main/java/com/localai/workspace/validation/ValidationFixtures.kt` | Fixture traversal válido con entrada segura y peligrosa. |
| `app/src/main/java/com/localai/workspace/validation/ValidationEngine.kt` | Fallos no capturados de estos dos controles son FAIL; infraestructura de archivos no disponible es BLOCKED. Contratos de los demás IDs intactos. |
| `app/src/main/java/com/localai/workspace/validation/ValidationModels.kt` | Etiqueta del control 256 y whitelist de métricas seguras de QA. |
| `app/src/main/java/com/localai/workspace/validation/ThinkingBudgetControl.kt` | Nuevo clasificador QA de agotamiento esperado / final correcto / fallo. |
| `app/src/main/java/com/localai/workspace/validation/ZipTraversalControl.kt` | Fixture sintético, comprobación estructural, clasificación precisa y comprobaciones del sandbox. |
| `app/src/test/java/com/localai/workspace/validation/ThinkingBudgetControlTest.kt` | Regresión del control negativo, errores, timeout, alternativa final y exportación. |
| `app/src/test/java/com/localai/workspace/validation/ZipTraversalControlTest.kt` | Fixture, parser productivo, traversal anidado, corrupción, escapes, límites, ZIP seguro y exportación. |

El informe y `docs/validation/hotfix024/` añaden evidencias y hashes de los archivos. No se eliminaron archivos.

## F. Tests

Comprobación conjunta ejecutada con JDK 17, Android SDK local y perfil arm64:

```bash
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:lintDebug \
  :app:assembleDebugAndroidTest -Parm64Only=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain --offline
```

- `:app:testDebugUnitTest`: **380 tests, 64 suites, 0 fallos, 0 errores, 0 omitidos**.
- Nuevos controles: ThinkingBudgetControlTest **20 PASS**; ZipTraversalControlTest **13 PASS**.
- Se mantiene la suite completa de regresión, no sólo los tests nuevos. La primera comprobación completa pasó; tras la revisión de excepciones se repitieron los seis comandos con las fuentes finales congeladas.
- :litert-compat:testDebugUnitTest: **2 tests, 1 suite, 0 fallos, 0 errores, 0 omitidos**. Se ejecutó adicionalmente `./gradlew :litert-compat:testDebugUnitTest --rerun -Parm64Only=true --max-workers=1 --console=plain --offline`: BUILD SUCCESSFUL en 6 s; los tests no quedaron únicamente recuperados de caché.
- Tests instrumentados físicos: **0 ejecutados**, ningún dispositivo conectado a ADB.

Las pruebas JVM no certifican Text/Tools/Thinking/Semantic/Python/Vision/Audio nativos en Moto; esos subsistemas mantienen su código y criterios ya validados, y sus tests host permanecen dentro de la suite completa.

Los tests que simulan una excepción Android certifican únicamente la clasificación en host; no sustituyen el test físico del callback de Android. Los tests ZIP contra `DocumentParser` sí ejecutan el parser productivo con Robolectric API 29. No se reclama inferencia nativa Android ni resultados Moto a partir de pruebas JVM.

## G. Build / lint y APK

Los seis comandos requeridos terminaron correctamente con las fuentes finales:

| Comando | Resultado |
|---|---|
| `:app:testDebugUnitTest` | PASS — 380 tests / 0 fallos. |
| `:litert-compat:testDebugUnitTest` | PASS — 2 tests / 0 fallos; rerun fresco adicional. |
| `:app:assembleDebug` | PASS. |
| `:app:assembleRelease` | PASS. |
| `:app:lintDebug` | PASS — 0 errores, 38 advertencias y 1 información. No se desactivaron reglas. |
| `:app:assembleDebugAndroidTest` | PASS — APK compilado; no ejecutado en dispositivo. |

Primera pasada: BUILD SUCCESSFUL en 14m 18s. Comprobación final tras la regla de errores: BUILD SUCCESSFUL en 10m 47s. **382 tests JVM en total, 0 fallos, 0 errores, 0 omitidos**.

APK: `dist/apk/local-ai-workspace-0.2.4-qa-arm64.apk` — 98,536,548 bytes, arm64-v8a, versionName 0.2.4 / versionCode 21.

SHA-256: `9ee785da378a6b24999dbcd1cfb552898b83406b7b9f4c6f9c4bde68c18412e8`.

Certificado SHA-256: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`, coincidente con 0.2.3. Firma y alineación 16 KiB verificadas. Las **9 librerías nativas** y los assets Python son byte a byte idénticos a 0.2.3. Los hashes de fuentes comprueban que sólo cambió QA/versionado; no se modificaron fuentes después de iniciar la comprobación final.

El APK de actualización se firma con el mismo certificado de 0.2.3. El APK debug conserva su sufijo `.debug`; para actualizar la instalación normal se entrega el APK release firmado del paquete `com.localai.workspace`.

## H. Qué no se modificó

Sin cambios de comportamiento ni código en inferencia, Tools, Thinking normal Off/Auto/On, controles 512/1024, XLSX/CSV/Files productivos, EmbeddingGemma, RAG, Memory, Python, Vision, Audio, KV/conversation reuse, LiteRT-LM, sampling, warm-up, GPU/speculative, UI principal o schema Room.

No se borran chats, proyectos, documentos, modelos, preferencias ni benchmarks. La versión se incrementa de forma compatible con la actualización. Las verificaciones de hashes de fuentes y librerías nativas permiten comprobar este alcance.

## I. Checklist Motorola — PHYSICAL_DEVICE_TEST_REQUIRED

1. Instalar el APK 0.2.4 como actualización sobre 0.2.3, sin desinstalar ni borrar datos.
2. Abrir la app y esperar a que Gemma 4 E2B esté Ready.
3. Ir a Settings → Developer / Diagnostics → Device Validation.
4. Seleccionar directamente **Full**, **iterations = 1**. No hace falta ejecutar Quick primero.
5. Dejar que finalice y comprobar cleanup SUCCESS.
6. Verificar `thinking_budget_256 = PASS`, reasonCode null y evidencia completa. Para el resultado físico comunicado anteriormente: `expectedExhaustionObserved=true`, `observedCondition=THINKING_OUTPUT_BUDGET_EXHAUSTED`, outputTokens 256, outputLimitReached true, thoughtCallbackCount positivo, finalCallbackCount 0, visibleOutputLength 0 y nativeCompletionObserved true. Si existe final correcto 7, el resultado alternativo debe quedar identificado como CORRECT_FINAL_ANSWER.
7. Verificar `thinking_budget_512` y `thinking_budget_1024` PASS con final correcto 7, sin cambio de criterios.
8. Verificar `zip_traversal = PASS`, fixtureValid/unsafeEntryDetected/unsafeEntryRejected/allWrittenPathsInsideSandbox true y escapedFileExists false.
9. Verificar Text, Calculator, Tools Off, Thinking normal, XLSX, Semantic, Python, Vision y Audio siguen PASS.
10. Exportar JSON. Esperado para Full: **33 PASS / 0 FAIL / 0 SKIPPED / 0 BLOCKED / 0 INCONCLUSIVE**, overall PASS. Es una expectativa de validación, no un resultado ya obtenido por este entorno.
11. Si un control falla, conservar y compartir el JSON; no se debe renombrar un error desconocido como agotamiento ni como traversal válido.

Quick conserva 9 casos y sus criterios; su resultado esperado sigue siendo 9 PASS / 0 FAIL / 0 BLOCKED.
