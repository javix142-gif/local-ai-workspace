# AWS H3b — fases 1 y 2: fundamentos, navegación y chat

**Estado:** `HOST_PASS_AWAITING_REVIEW` para el alcance focalizado descrito aquí. No representa aceptación independiente, gate global, ejecución de Android instrumentado ni validación física.

## Fuente y límites

- Rama: `feat/0.5.0-skills-agents`.
- Código: [`35f23d814d7850e3d18e642b4d9b94a3511c6e7c`](https://github.com/javix142-gif/local-ai-workspace/commit/35f23d814d7850e3d18e642b4d9b94a3511c6e7c), hijo de `4fc5637377be8283a9580a3291857799af5e5c13`.
- Cambios de código/pruebas: 22 archivos bajo `app/`, 986 inserciones y 285 eliminaciones. Sin cambios en dependencias, workflows, Room, persistencia, inferencia ni selección/routing de Agents y Tools.
- Esta entrega termina en H3b Fases 1–2. No inicia la Fase 3 ni cierra IDs del backlog funcional.
- Las 10 referencias visuales del usuario están preservadas en [`docs/design/referencias/`](../../design/referencias/); su especificación está en [`DISENO_H3b.md`](../../design/DISENO_H3b.md). La comparación sigue esas imágenes como guía de diseño, no simula funciones sin respaldo. Inicio muestra el estado real “Aún no hay un modelo listo”; las capturas de chat usan fixture sintética `H3B-SYNTHETIC-01`.
- El cambio menor del token de estado activo claro, de `#2E7D4F` a `#286F47`, corrige el ratio medido sobre su contenedor claro de 4.327:1 a 5.214:1; la prueba WCAG del tema cubre los pares registrados.

## Cambios incluidos

- Navegación superior persistente de cuatro destinos: Inicio, Chats, Memoria y Más. Las rutas y funciones existentes se reutilizan; Modelos y administración de Skills quedan dentro de Más. No se añadieron entidades ni contenido ficticio.
- Chat: jerarquía del encabezado y estados de modelo, transcript, referencias de evidencia, tarjetas de acciones propuestas y acceso a su revisión explícita. Los callbacks existentes permanecen conectados; una propuesta no se presenta como ejecutada antes de confirmación.
- Se conservaron las capacidades de adjuntar, enviar/detener, citas, estado de memoria y herramientas. Tools Off sigue describiendo solo herramientas y no significa que no haya Agent.
- Los tres reportes sobre Mensajes, Calendario y Alarma en el Moto G86 siguen `DEVICE_REPORTED / NOT_DIAGNOSED`; esta entrega no reproduce ni atribuye causas.

## Validación HOST exacta del commit

Entorno: Gradle Wrapper 8.10.2, Temurin JDK 17 como runtime de Gradle, rutas JDK 17 y JDK 21 expuestas para toolchain discovery, Android SDK `/workspace/.toolchain/android-sdk`, NDK existente `27.0.12077973`, `GRADLE_USER_HOME=/workspace/.gradle`, modo offline. No se instalaron ni descargaron componentes.

Comando exacto y entorno completo: [`attempt-07/command.txt`](attempt-07/command.txt) y [`attempt-07/environment.txt`](attempt-07/environment.txt). Log combinado completo: [`attempt-07/gradle.log`](attempt-07/gradle.log). El intento corrió contra el SHA de código indicado arriba, de `2026-10-21T18:40:47Z` a `18:44:04Z` (196.945 s), exit 0, `BUILD SUCCESSFUL`.

| Tarea | Resultado de esta invocación | Detalle |
|---|---|---|
| `:app:testDebugUnitTest` con nueve filtros UI | PASS | 37 tests en 9 XML recién generados; 37 pass, 0 failures, 0 errors, 0 skipped. Reconciliación: [`attempt-07/app-junit-summary.json`](attempt-07/app-junit-summary.json). |
| `:litert-compat:testDebugUnitTest` | UP-TO-DATE | No se cuenta como ejecución nueva en attempt-07. El resultado histórico previo de 2/2 está preservado en attempt-06 y no se suma a los 37 tests frescos. |
| `:app:lintDebug` | PASS | El informe XML registra 92 warnings, 1 info y 0 errors. Véase [`attempt-07/lint-results-debug.xml`](attempt-07/lint-results-debug.xml). |
| `:app:compileDebugAndroidTestKotlin` | UP-TO-DATE | Sin ejecución de instrumentación; este intento no instala ni ejecuta pruebas Android. |
| Suite JVM completa / `assembleDebug` | NOT_RUN | Fuera del gate focalizado de esta entrega. |

Tareas y estados del log: [`attempt-07/task-outcomes.txt`](attempt-07/task-outcomes.txt). Los intentos fallidos/intermedios 01–06 no se borraron ni sustituyen este resultado; sus logs y XML permanecen junto al intento final para conservar las regresiones y correcciones observadas.

## Capturas

`attempt-07/captures/` contiene 15 PNG producidos por Compose/Robolectric durante esta ejecución: cinco vistas de chat y las pantallas Inicio, Chats, Memoria, Más y Modelos en temas claro/oscuro. No contienen datos del usuario. La prueba dedicada del chat usa la fixture sintética etiquetada `H3B-SYNTHETIC-01`; las capturas de navegación usan `MultiProcessUiTestApplication` y reflejan su estado vacío/sin modelo. Dimensiones, bytes y SHA-256 están en [`attempt-07/captures.json`](attempt-07/captures.json); todas se verificaron como PNG válidos.

Se inspeccionaron visualmente la conversación clara a escala 1.0 y oscura a 2.0, Inicio claro y las referencias 01/02. En escala 2.0, el transcript ocupa el viewport desplazable y la cabecera, composer y navegación siguen presentes; el rótulo “Memoria” se ajusta en dos líneas. Esa escala aún se ve densa. La imagen llamada `chat-action-card-dark-360dp-fontScale-2.png` no contiene la ventana modal separada de confirmación: la confirmación y su callback se verifican semánticamente, pero no se afirma una captura visual del bottom sheet.

Las 10 imágenes fuente se preservan con `docs/design/referencias/SHA256SUMS` (10/10; SHA-256 del manifiesto `3398895eb2013f0519de7962c56bb0565c2683fefae63f76e12b4eb5caa2ef3b`). El manifiesto de la ejecución final `attempt-07/SHA256SUMS` verifica 38/38 payloads; su digest vigente se registra en `RESULTS.json` y se puede recalcular directamente.

## Emulador, dispositivo y APK

- **EMULATOR:** `NOT_RUN`. La consulta directa al `adb` disponible devolvió cero dispositivos; no existe ejecutable `emulator` en el SDK instalado, así que no se inició un AVD.
- **PHYSICAL_DEVICE:** `NOT_RUN`; no hubo Motorola conectado.
- **APK:** `NOT_RUN`; no se generó ni publicó APK en este contrato.

La evidencia de preflight está en [`attempt-07/device-preflight.txt`](attempt-07/device-preflight.txt). No se atribuye a esta validación una mejora de inferencia, TTFT, tokens/s, memoria o consumo en el teléfono.

## Integridad del plan y hashes

Los originales siguen intactos: backlog SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` y roadmap SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`; 97/97 IDs únicos, 120 dependencias, 97 estados fuente `TODO`. M00–M03 continúan congelados según el contrato. `main` y el peeled tag `v0.4.2` no se modifican.

El manifiesto `attempt-07/SHA256SUMS` cubre los logs, comandos, XML, lint y PNG de la invocación final. El manifiesto raíz `SHA256SUMS` cubre los archivos persistidos de este expediente. Ambos se verificaron con `sha256sum -c` antes de publicar.
