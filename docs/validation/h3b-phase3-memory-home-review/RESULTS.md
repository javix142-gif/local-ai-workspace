# H3b Fase 3 — checkpoint de Memoria e Inicio

**Estado:** Memoria/Inicio implementadas; HOST PASS; EMULATOR FAIL (2 fallos, 6 omisiones); aún no aceptadas visualmente. Esperar la revisión del usuario. No iniciar Primer uso, Lista de chats, Más, Nueva habilidad ni ajustes finales del modo oscuro hasta recibir revisión.

## Código y alcance

- Rama: `feat/0.5.0-skills-agents`.
- Código: [`c7bd9447606020de6a2093b72a17a47be5705299`](https://github.com/javix142-gif/local-ai-workspace/commit/c7bd9447606020de6a2093b72a17a47be5705299), hijo de `49ce2b53f2c77ef4be33d367787d7c134d11258b`.
- Archivos: `MainActivity.kt`, `AssistantControls.kt`, `ContextMemoryUi.kt`, `SemanticV2Settings.kt`, `AwsBottomBar.kt`, nuevo `AwsSurfaces.kt`, `strings.xml`, y pruebas `H1PreferenceContextTest.kt`, `AwsBottomBarUiTest.kt`, `H3bMemoryScreenUiTest.kt`, nuevo `H3bSyntheticHomeCaptureTest.kt` y `UiSmokeTest.kt`.
- No se cambiaron dependencias, persistencia, Room, routing, motores ni el catálogo original. Los 97 IDs/120 dependencias permanecen intactos.

`AwsCard`, `AwsListRow` y `StatusPill` son compartidos. `AwsListRow` muestra icono circular, título, cantidad y flecha con mínimo 56 dp; `StatusPill` también reemplaza la píldora específica de las tarjetas de acciones. Memoria muestra primero pendientes con Aprobar/Descartar, categorías expandibles con cantidades, sección `Fijadas · N`, botones Importar/Exportar al pie y controles JSON/reconstrucción bajo Avanzado. Inicio usa estado real en producción para memoria, web, calendario, modelo y cantidad pendiente; la tarjeta de pendientes solo aparece con conteo mayor que cero. A escala 2.0, las píldoras y acciones se apilan y la barra inferior muestra la etiqueta completa del destino activo.

## Preferencias: prueba que antes fallaba

Se ejecutó en bucle el test `H1PreferenceContextTest.H1_AT_01_approvedPreferencesReachTheRealGenerationRequestIncludingGreeting` **20 veces** sobre este SHA: **20 PASS, 0 FAIL, 0 errores, 0 omitidos**, exit 0 en 575 s. Logs/XML y el comando están en [`preference-loop-final/`](attempt-01/preference-loop-final/).

La falla histórica estaba en la aserción del ID de documento seleccionado después de completar el envío. `ChatViewModel.send()` agrega ese ID al `ContextRequest`, verifica el contenido usado y, al concluir, consume los IDs correspondientes a evidencia suministrada. La prueba ahora verifica selección antes del envío, preferencias/ID/contenido en la solicitud y consumo después de terminar. No hubo evidencia de una carrera de producción que impida entregar preferencias; no se cambió lógica de producción por esta observación.

## Validación HOST

Entorno: Gradle 8.10.2, JDK 17 para Gradle, JDK 21 para workers, SDK `/workspace/.toolchain/android-sdk`, NDK 27.0.12077973, caché existente `/workspace/.gradle`, modo offline.

| Comprobación | Resultado | Evidencia |
|---|---:|---|
| UI/capturas focalizadas Memoria e Inicio | 16/16 PASS | `attempt-01/focused-final/run.log`, exit 0; XML de la suite completa también incluye estas pruebas |
| `UiSmokeTest.projectTabsAndApprovedMemoryRemainFunctional` | 1/1 PASS | [`uismoke-followup/`](attempt-01/uismoke-followup/) |
| `:app:testDebugUnitTest` | 984/984 PASS; 148 XML | [`final-host-c7bd944/`](attempt-01/final-host-c7bd944/), exit 0, 331 s |
| `:litert-compat:testDebugUnitTest` | 2/2 PASS; 1 XML | [`litert-compat-c7bd944/`](attempt-01/litert-compat-c7bd944/), tarea forzada, exit 0 |
| `:app:lintDebug` | PASS, 0 errores, 96 advertencias, 1 información | [`lint-results-debug.txt`](attempt-01/final-host-c7bd944/lint/lint-results-debug.txt) |
| `git diff --cached --check` sobre los documentos del checkpoint y el manifiesto de capturas | PASS | sin hallazgos en los cambios editoriales |

El `git diff --cached --check` de todo el paquete de evidencia señala espacios/saltos de línea finales presentes en salidas crudas del artefacto de Actions (por ejemplo, `gradle-current-build.txt` y XML instrumentado). Esos archivos se conservaron byte por byte para no modificar la evidencia fuente; el aviso no corresponde a código ni a los documentos del checkpoint.

La primera suite completa en el árbol de trabajo reportó 983/984 por la expectativa de `UiSmokeTest` de que un recuerdo estuviera visible sin abrir la nueva fila de categoría. Se actualizó el test para expandir la categoría y conservar las aserciones de lectura, detalle, edición y eliminación; el caso enfocado pasó 1/1 y la suite exacta del código publicado pasó 984/984. El informe Lint anterior registrado tenía 90 advertencias; esta ejecución muestra 96 (+6), cero advertencias ubicadas en los archivos Compose modificados y una información `AutoboxingStateCreation` en `MainActivity.kt`. No se atribuye ese delta global sin una ejecución base equivalente.

## Capturas

Las **18 imágenes** son capturas Robolectric de la actividad, configuración 360×800 dp. Son sintéticas, identificadas como tales, y no representan datos del usuario ni una prueba física. La fixture de Memoria crea 2 pendientes, 1 fijada y cinco categorías. La fixture de Inicio proporciona un día de calendario con tres eventos sintéticos y una preparación de modelo sintética para mostrar el estado visual "Modelo listo"; no prueba inferencia ni permiso/dispositivo real.

- [Manifiesto de capturas y SHA-256](attempt-01/capture-manifest.json)
- [Memoria · claro · escala 1.0](attempt-01/captures/SYNTHETIC-h3b-memory-light-360dp-fontScale-1.0-overview.png)
- [Memoria · oscuro · escala 1.0](attempt-01/captures/SYNTHETIC-h3b-memory-dark-360dp-fontScale-1.0-overview.png)
- [Memoria · claro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-memory-light-360dp-fontScale-2.0-overview.png)
- [Memoria · oscuro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-memory-dark-360dp-fontScale-2.0-overview.png)
- [Inicio · claro · escala 1.0](attempt-01/captures/SYNTHETIC-h3b-home-light-360dp-fontScale-1.0-overview.png)
- [Inicio · oscuro · escala 1.0](attempt-01/captures/SYNTHETIC-h3b-home-dark-360dp-fontScale-1.0-overview.png)
- [Inicio · claro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-home-light-360dp-fontScale-2.0-overview.png)
- [Inicio · oscuro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-home-dark-360dp-fontScale-2.0-overview.png)
- [Inicio · agenda de tres eventos · claro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-home-light-360dp-fontScale-2.0-agenda.png)
- [Inicio · agenda de tres eventos · oscuro · escala 2.0](attempt-01/captures/SYNTHETIC-h3b-home-dark-360dp-fontScale-2.0-agenda.png)

Los archivos `-categories.png` y `-pinned.png` muestran las filas/cantidades de categorías y el recuerdo fijado; los `*-layout.txt` vinculados incluyen bounds. Las vistas de escala 2.0 son desplazables: la captura overview puede mostrar solo el comienzo del calendario o del contenido bajo el pie fijo; las capturas de agenda y sección muestran las filas desplazadas. Las aserciones verifican que los controles de acciones sintéticas se pueden alcanzar y que la etiqueta activa de navegación no queda recortada.

## CI y límites

- Android emulator run [39856422426](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39856422426), source `c7bd9447606020de6a2093b72a17a47be5705299`: **FAIL**. El AVD arrancó y ADB detectó `emulator-5554`, Android 15/API 35, ABI `x86_64`. La verificación H1-AT-12 terminó PASS con una fixture sintética de 10 filas antiguas, 10 sidecar, un proyecto y un chat; esto no es prueba física.
- Se ejecutó `:app:connectedDebugAndroidTest` de verdad; exit code 1. XML JUnit: 24 casos, 16 PASS, 2 FAIL, 6 SKIPPED por las pruebas que requieren pesos/modelos reales, 0 errores; 18 ejecutados. Los fallos observados:
  - `ContextMemoryDeviceTest.H1_AT_13_forgetPermanentlyUsesConfirmationAndLeavesNoAppOwnedMarkerBytes`: tras buscar el marcador, el test no encontró `Detalles` para desplazarse. En la pantalla actual los recuerdos se muestran dentro de una categoría inicialmente colapsada; el test no la expandió. El flujo de borrado permanente no llegó a ejecutarse. Hace falta adaptar el paso de navegación del test preservando todas sus aserciones; no se cambió aquí.
  - `ProjectUiDeviceTest.createProjectAndExplicitlySaveEditRemoveMemory`: `NoSuchElementException` buscando el recuerdo recién guardado en el scope/proyecto esperado. Causa no determinada; no se infiere pérdida de datos. El stack reporta `ProjectUiDeviceTest.kt:93`, mientras que el archivo fuente versionado en `c7bd944` tiene 70 líneas y el lookup está en las líneas 40–45; esa discrepancia también queda sin explicar.
- El log de progreso terminó en `29/24`, aunque el XML contiene 24 casos y su propio resumen es coherente. El collector marcó el post-check `applicationApkInstalled=false` y validación general FAIL; no se atribuye una causa a ese post-check. No afirmar PASS de EMULATOR.
- Manifiesto de hashes del bundle: [attempt-01/SHA256SUMS](attempt-01/SHA256SUMS), 408 archivos cubiertos, SHA-256 63531e9d3103548adeb74e527b76ba3fb5aeb4ac5b691da8cafcd91ef79203b6; incluye logs/XML HOST, 18 capturas/layouts y la copia íntegra del artefacto EMULATOR.
- Artefacto crudo de evidencia [12460364221](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39856422426/artifacts/12460364221), `android-emulator-evidence-39856422426-1`, 180,354 bytes, SHA-256 `b233846048a499e3869ccf467684c6d389d36aeedfd993725ee7725a05eed1cf`, retención hasta 2026-10-29. La copia íntegra está en [`attempt-01/emulator-39856422426/`](attempt-01/emulator-39856422426/); ZIP CRC correcto, SHA coincide con la API de GitHub y las 50 entradas del manifiesto interno verifican.
- La vía Personal QA existente se activó automáticamente: run [39856422471](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39856422471) PASS, artifact 12459577844 generado y disponible en Actions; no se descargó ni se incluye como entrega de este checkpoint. QA estándar [39856422564](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39856422564) falló cerrado esperando el PASS del emulador; build/upload quedaron omitidos, sin artifact.
- `EMULATOR=FAIL`; `PHYSICAL_DEVICE=NOT_RUN`. Las 18 capturas Robolectric siguen siendo evidencia HOST sintética, no capturas del emulador ni del Motorola.
- No se inició ninguna pantalla de Fase 3 posterior. El checkpoint espera revisión visual del usuario; los dos fallos de instrumentación permanecen visibles y sin rerun.

Comandos, salidas, exit codes, XML, capturas, layouts, evidencia cruda de Actions y manifiestos están bajo [`attempt-01/`](attempt-01/).
