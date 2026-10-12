# UX-02a — chat header and composer

**Estado:** HOST PASS; UX-02a header checks PASS dentro del run de emulador; suite Android global FAIL por el fixture histórico de Room v1; APK PERSONAL_QA entregado; pendiente de auditoría independiente. No inicia UX-02b ni UX-03.

## Identidad y alcance

- Rama: `feat/0.5.0-skills-agents`
- Base auditada: `cd7ea02bba7dbaa0d557259ac73f6ad3427b9ad6`
- Commit de código probado: `d0832e225f51f624c981f6b68da6193b72a78ef2`
- UX-01: `INDEPENDENTLY_AUDITED_ACCEPTED`; aceptación y sus límites están preservados en `docs/development/EXECUTION_STATE.json`.
- Cambios de código: `MainActivity.kt`, `ui/ChatContent.kt`, `ui/AssistantControls.kt`, `ChatHeaderLayoutTest.kt`, `ChatContentUiTest.kt` y `ChatHeaderComposerCaptureTest.kt`.
- No se cambiaron navegación, destinos, ViewModels, modelos/runtime, inferencia, herramientas/routing, archivos/adjuntos, permisos, retrieval/memoria, Room, dependencias ni workflows.

## Hallazgo y cambio

La cabecera anterior imponía a `TopAppBar` una altura calculada, mientras Material 3 limita y recorta el área de título a `expandedHeight`; además, los títulos de conversación y modelo estaban limitados a una línea. La cabecera nueva `ChatTopBar` mantiene colores e insets M3, un mínimo estándar de 64dp y altura natural según el texto envuelto. `ChatHeaderTitle` conserva el agente, título, estado/selector del modelo y callback; los textos ya no tienen ellipsis ni un límite de líneas.

El compositor conserva adjuntos, archivos, escritura, envío, detención y callbacks. Think y Tools pasan a un menú secundario `Options`, con los valores visibles en el resumen. Tools se muestra como `Off`, `Auto` (solo el conjunto predeterminado) o `Custom`; se mantienen los diálogos y `AssistantProfile`. Tools Off no desactiva ni representa al Agent lógico.

## Pruebas y compilación host

Ambiente: Linux; Gradle 8.10.2; Temurin JDK 17.0.20.1 para Gradle y Temurin JDK 21.0.12.1 para tests; Android SDK/API 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, Ninja 1.12.1. El detalle está en [`host/environment.txt`](host/environment.txt); comandos en [`host/commands.txt`](host/commands.txt).

| Comprobación | Resultado | Evidencia |
|---|---:|---|
| Focalizada sobre el commit `d0832e2` | 9/9 PASS, 0 fail/error/skip | [`focused-exact-commit.log.gz`](host/focused-exact-commit.log.gz), exit 0, 3 XML en `host/focused-junit/` |
| Suite JVM app | 741/741 PASS, 0 fail/error/skip en 89 XML | [`full-jvm-xml.tar.gz`](host/full-jvm-xml.tar.gz), resumen del gate en [`full-gate-final.log.gz`](host/full-gate-final.log.gz) |
| `:app:lintDebug` | PASS; 0 errores, 82 advertencias | [`lint-results-debug.txt`](host/lint-results-debug.txt) |
| `:app:assembleDebug -Parm64Only=true` | PASS, fuente `d0832e2` | [`exact-commit-arm64-assemble.log.gz`](host/exact-commit-arm64-assemble.log.gz) |
| `:app:compileDebugAndroidTestKotlin` | PASS; UP-TO-DATE en el gate final. Se compiló explícitamente en el gate exitoso anterior, después del cambio del test Android | [`full-gate-before-last-adjustment.log.gz`](host/full-gate-before-last-adjustment.log.gz), [`full-gate-final.log.gz`](host/full-gate-final.log.gz) |
| `git diff --check` | PASS | comprobado antes de los commits de código y evidencia |

El gate final tardó 6m41s: 151 tareas, 19 ejecutadas y 132 UP-TO-DATE. El archivo de salida de lint completo fue preservado. La suite completa se ejecutó sobre el mismo árbol de código posteriormente comprometido como `d0832e2`; después del commit se volvió a ejecutar la suite focalizada con el SHA explícito.

Los logs originales de texto se versionan como `.log.gz`; al descomprimirlos se recuperan byte por byte las salidas originales. El manifiesto SHA-256 cubre 37 archivos versionados después de añadir los resultados del run de emulador y se verificará contra el árbol publicado.

Los intentos previos también están preservados: una invocación inicial no llegó a ejecutar tests porque la instalación Java 21 del host no tenía `javac`; un gate inicial detectó una referencia antigua al helper de altura en el nuevo AndroidTest. La referencia se corrigió y el gate final pasó. La validación inicial del APK se interrumpió por SIGPIPE del comando `aapt | head`; el verificador completo posterior no trunca la salida y pasó. Véanse los logs `focused-first.log.gz`, `full-gate.log.gz`, `apk-validation.log.gz` y `apk-validation-final.log.gz`; no se cuentan como PASS.

## Capturas Compose host

Las cuatro capturas actuales se generaron de nuevo con las pruebas Robolectric del commit de código, se abrieron y revisaron visualmente. Muestran las tres líneas del encabezado, el resumen de opciones y el compositor:

| Superficie | Dimensiones PNG | SHA-256 |
|---|---:|---|
| 360dp, light, fontScale 1.0 | 360×800 | `60a43b256f1b70dedeae1ab58536e0953b46eb62625277122eaf4feb49dac4fe` |
| 360dp, dark, fontScale 1.0 | 360×800 | `716ed1b7b69680ce8aa213c3e3073271886be85af5b4e96ca3378e93333f08ba` |
| 411dp, light, fontScale 1.6 | 411×891 | `03c1466ee60518fd61a30110b241f9b9317755e2cb5b85a12fd4231ef6796fde` |
| 411dp, dark, fontScale 1.6 | 411×891 | `f4af1846b39325b5f7af5b356fb0f1486800fcd7089332f9f5ac6d5a02ba5d34` |

Los archivos están en [`screenshots/`](screenshots/). Son fixtures de componente Compose/Robolectric, no capturas del APK completo, del emulador ni del Motorola. La matriz solicitada se cubrió también con fontScale 2.0 en aserciones host, sin captura adicional.

## APK de evaluación personal

- Tipo: `PERSONAL_QA_DEBUG_ARM64_NOT_FOR_PRODUCTION`; no es Release ni una versión de producción.
- Paquete `com.localai.workspace.debug`, versión `0.5.0-debug`, versionCode `27`.
- Commit fuente: `d0832e225f51f624c981f6b68da6193b72a78ef2`.
- APK: 121,801,305 bytes; SHA-256 `c58b90d30228dbf58ce230039e44093cafbe71d6ae644d619618ed17117b6aa0`.
- Firmante Android Debug, certificado SHA-256 `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`.
- ZIP CRC, `zipalign -c 4` y `apksigner verify` v2: PASS. El APK contiene únicamente `arm64-v8a` y no incluye archivos de pesos de modelos.
- Paquete Drive: [descargar APK QA ARM64](https://drive.google.com/file/d/1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8/view?usp=drivesdk), ID `1oEFdh3Q7_r8DjBw9Zp5mtXkNWbYXniP8`, 46,262,838 bytes, SHA-256 local del ZIP `b5b1225300aa576a5f54c991a5f119844fb41eabdfdb5c7ec7b48880f65d5bc1`.
- La lectura posterior de Drive confirmó ID, nombre, MIME `application/zip`, tamaño, URL y carpeta destino. El ZIP de 46 MB no se volvió a descargar para recalcular su SHA desde Drive; el hash indicado corresponde al archivo local subido y el tamaño sí coincide con la lectura posterior. El paquete incluye APK, manifiesto, log de verificación y SHA256SUMS.

Log de build y verificaciones: [`host/exact-commit-arm64-assemble.log.gz`](host/exact-commit-arm64-assemble.log.gz), [`host/apk-validation-verified.log.gz`](host/apk-validation-verified.log.gz). El manifiesto consolidado está en [`RESULTS.json`](RESULTS.json).

## Android y estado del trabajo

- `HOST`: PASS según las pruebas y builds enumerados.
- `EMULATOR`: la ejecución automática [38210637867](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637867), source SHA `d0832e225f51f624c981f6b68da6193b72a78ef2`, sí arrancó AVD API 35 x86_64/ADB y ejecutó `:app:connectedDebugAndroidTest`. Resultado global **FAIL**: 16 casos, 9 PASS, 1 FAIL, 0 errors, 6 SKIPPED; 10 ejecutados. Las dos pruebas ChatHeader de fontScale normal y grande pasaron y sus capturas del emulador se validaron visualmente. El único caso fallido es la prueba de migración Room que requiere el esquema histórico auténtico `WorkspaceDatabase/1.json`; los seis skips dependen de modelos reales. Evidencia original, XML, logs, screenshots y hashes: [resultados del run](emulator/RESULTS.md) y [ZIP íntegro del artifact](emulator/run-38210637867-artifact.zip). El artifact de GitHub `11729571175` tiene 155,402 bytes y SHA-256 `dd75931c08f5a6ededdb73482b0c728faefcc1719f92d4d8c7ee96da1baa9911`; los 33 archivos de su manifiesto interno verifican. No se cambió la prueba ni el esquema Room.
- El gate automático de APK [38210637883](https://github.com/javix142-gif/local-ai-workspace/actions/runs/38210637883), mismo SHA, terminó FAIL al esperar el éxito instrumentado; setup de JDK, build/verificación y upload quedaron SKIPPED y el run no produjo artifact. Esto no afecta el APK PERSONAL_QA compilado localmente y ya entregado por Drive, que es un artefacto separado.
- El resumen del run informa que el sondeo post-test encontró ausentes los packages de aplicación/test. Ese estado posterior no contradice que la tarea conectada produjo XML y ejecutó los tests; se conserva como dato del collector y no como evidencia de que la instalación hubiera fallado durante la ejecución.
- `PHYSICAL_DEVICE`: `NOT_RUN`; no se afirma validación en Motorola Moto G86 Power.
- La prueba Room v1 sigue bloqueada por ausencia de fixture auténtica (`ENVIRONMENT_UNAVAILABLE`); no se inventó schema ni se debilitó el test. Los seis casos dependientes de modelos siguen SKIPPED; no se descargaron ni simularon modelos.
- La auditoría de UX-01 permanece aceptada. El plan original de 97 tareas no está versionado, no se reconstruyó ni se modificó; M01-06 continúa `PENDING_REQUIRES_DEVICE`.

UX-02a termina aquí para auditoría independiente. UX-02b y UX-03 no se iniciaron.
