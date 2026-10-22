# H3b Phase 3 — Inicio y punto de revisión visual

- Código de Inicio: `f4574ecf2f18e7a9838dc1d741b0cbbd4edd3147` en `feat/0.5.0-skills-agents`.
- Cambio acotado: saludo y fecha local en español; tarjeta «Tu día» que solo consulta Calendar mediante el lector local acotado existente si el permiso ya se concedió; si no, presenta una acción para solicitarlo explícitamente. Mantiene el estado real de modelo y los botones existentes para iniciar un chat y abrir chats/proyectos. No cambia navegación, almacenamiento, persistencia, motores, ni incorpora eventos o recordatorios ficticios.
- Archivos de producto/pruebas: `MainActivity.kt`, `ViewModels.kt`, `strings.xml`, dos pruebas de captura de `MainActivity` actualizadas y `H3bHomeScreenUiTest.kt`.

## HOST

- Pruebas focalizadas Inicio: 5/5 PASS, 0 fallos/errores/omisiones, exit 0, 3m18s. Incluyen una comprobación de estado/permiso real y capturas claro/oscuro, 360dp, escalas 1.0/2.0; con escala 2.0 verifican que las acciones siguen alcanzables mediante scroll.
- Suite app completa: 978/978 PASS, 142 XML JUnit, 0 fallos/errores/omisiones; `:app:testDebugUnitTest` se ejecutó.
- LiteRT compatibilidad: 2/2 PASS, 1 XML. En el comando final de gate, `:litert-compat:testDebugUnitTest` quedó `UP-TO-DATE`; el XML de 2/2 procede de su ejecución anterior de la misma validación HOST, sin cambios en ese módulo.
- `:app:lintDebug`: PASS, 0 errores y 90 advertencias; no hay incremento respecto de la línea base de Memoria.
- Gate: exit 0, `BUILD SUCCESSFUL` en 7m26s; 128 tareas, 27 ejecutadas y 101 `UP-TO-DATE`. Los comandos, ambiente, logs completos y XML están bajo `host/`.
- `git diff --check`: PASS antes del commit. El commit contiene solo los seis archivos enumerados arriba.
- `full-host.log` conserva un intento anterior de la variante previa a la corrección de plural: 978/978 y exit 0, pero lint mostraba una advertencia adicional (91). Se cambió ese contador a recurso plural Android; el gate final `final-host.log` del código actual vuelve a 90 advertencias, sin errores.

## EMULATOR

- Run [39839230088](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39839230088), source SHA `f4574ecf2f18e7a9838dc1d741b0cbbd4edd3147`, job `120035537807`: **success**, 26m38s. AVD API 35 / Android 15 / x86_64, `emulator-5554`; AVD preflight, ADB, actualización sintética H1-AT-12, instrumentación conectada, collector y upload pasaron.
- Tarea ejecutada: `:app:connectedDebugAndroidTest`; exit 0. XML: 24 total = 18 PASS, 6 SKIPPED, 0 FAIL/ERROR; 18 ejecutados. Las seis omisiones son dos pruebas LiteRT y cuatro pruebas de chat nativo que requieren pesos reales no descargados. No se atribuye PASS a inferencia nativa.
- H1-AT-12 pasó con fixture sintética (20 memorias, proyecto, chat/mensaje y preferencia de contexto); no son datos personales ni una prueba física.
- Artefacto oficial de evidencia: [12454476399](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39839230088/artifacts/12454476399), `android-emulator-evidence-39839230088-1`, 176,375 bytes; SHA-256 del ZIP descargado `512ce1057e25f9e055e3be7574608c345b6510a39d271c5b8b7b5fd3c75db1b2`. `unzip -t` pasó; manifiesto interno: 50/50 entradas verificadas, SHA-256 del manifiesto `fc3a3871feed62728ac3a4316ba7ff3866be4de7bbaf30a94d02c7fc8f0307f1`.
- ZIP original, summary, XML JUnit, logs de Gradle/ADB/AVD y resumen H1-AT-12 se preservan bajo `emulator/`.

## Capturas del punto de revisión

Las capturas son PNG de la actividad Compose completa renderizada desde el código real con Robolectric; no son mockups ni capturas de emulador/teléfono. Resolución de captura 360x800 (configuración 360dp); no se concedió permiso de Calendar en el fixture HOST, por lo que se muestra el estado de permiso y no se inventan eventos. En escala 2.0 el contenido de Inicio fluye en vertical; los botones existentes se verificaron alcanzables con scroll.

- [Inicio — claro, escala 1.0](captures/h3b-home-light-360dp-fontScale-1.0.png) — SHA-256 `693e5b087db707c6ff1db0c9e304808d9c8ee50773d12cc5e888bbdad31e07c2`.
- [Inicio — oscuro, escala 1.0](captures/h3b-home-dark-360dp-fontScale-1.0.png) — SHA-256 `f20018cbb6d2489e886dcd2cef02f67818d3c011a41c933408c5a77d37dbbca2`.
- [Inicio — claro, escala 2.0](captures/h3b-home-light-360dp-fontScale-2.0.png) — SHA-256 `b9f5deb5e157d5677f4ff3cfe898bccb94efb64644fdb4bbf4ca9b4ead12e121`.
- [Inicio — oscuro, escala 2.0](captures/h3b-home-dark-360dp-fontScale-2.0.png) — SHA-256 `918457719c1048f19703dd00106683cbc05ee7b1e961652208964a5a28076bdd`.

Las cuatro capturas correspondientes a Memoria están en [su carpeta](../../h3b-phase3-memory/attempt-01/captures/) y el detalle HOST/EMULATOR está en [Memoria RESULTS](../../h3b-phase3-memory/attempt-01/RESULTS.md). Ambas pantallas fueron revisadas visualmente en claro/oscuro, escalas 1.0/2.0.

## Intermitencia y límites

`H1PreferenceContextTest.H1_AT_01_approvedPreferencesReachTheRealGenerationRequestIncludingGreeting` falló una vez en cuatro ejecuciones completas observadas durante este tramo: una de dos sobre el código de Memoria y ninguna de las dos ejecuciones completas de Inicio (incluida la versión final). La causa permanece `NOT_DETERMINED`; no se atribuye a Memoria ni a Inicio y no se ocultó ni relajó la prueba. La frecuencia se registra en `docs/development/BACKLOG_ACTIVO.md`.

`PHYSICAL_DEVICE=NOT_RUN`; el emulador no acredita el Moto G86 ni datos reales del usuario. El alcance autorizado llega al punto de revisión de Memoria e Inicio. Esperar aprobación visual del usuario antes de Primer uso sin modelo, Lista de chats, Más, Nueva habilidad o ajustes finales del modo oscuro.

## APK personal QA generado automáticamente

El push de UI también activó el workflow independiente Personal QA. Run [39839230085](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39839230085) terminó success para el mismo SHA. Artifact descargable: [12453375144](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39839230085/artifacts/12453375144), `local-ai-workspace-personal-qa-debug-arm64-39839230085-1`, 49,054,182 bytes. El log del runner reporta digest del ZIP `8d73bd153d94d767772a5d996e9c87eb3d7f9e0ec6a1979a06ef58d12c7007e8`.

El reporte del build identifica `local-ai-workspace-0.5.0-debug-arm64.apk`, 121,861,443 bytes, SHA-256 `b0e13ea4548ddcfdc89098ee1074320d61c636fbaebcef376c58158a05811f6f`; paquete `com.localai.workspace.debug`, versión `0.5.0-debug`/27, ABI únicamente `arm64-v8a`, firma Debug v2 verificada, certificado SHA-256 `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. El workflow informó CRC ZIP y zipalign PASS. Estos valores proceden del log/resultados del workflow; el APK y ZIP de 49 MB no se descargaron y no se recalcularon localmente en este cierre. Es PERSONAL_QA, no Release ni validación física; ese workflow no ejecuta instrumentación.
