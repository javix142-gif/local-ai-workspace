# UX-02d — presentación accesible de tablas Markdown

**Estado:** La presentación inicial y su gate HOST pasaron; la corrección posterior de los pipes exteriores también pasó HOST y queda pendiente de auditoría independiente. No se generó APK ni se ejecutó emulador o Motorola para esa corrección.

## Base y cambios

- Rama: `feat/0.5.0-skills-agents`.
- Base: `e8fdc02cb423aa871aa2c3e0f4b52a7c33df3cda`.
- Implementación de parser/Compose: `df8aeb26ed2287802c1bd8a412441752c56c0fce`.
- HEAD de verificación, incluido el commit de prueba final: `e97a5a185dc25372a6a526bdb8b13310857cfca6`.
- El commit `e97a5a1` solo amplía la prueba Compose para comprobar desplazamiento vertical y llegada al último valor; no cambia código de producción. El snapshot de entorno fue capturado en `df8aeb2`; los registros de cada comando identifican `e97a5a1` como SHA probado.
- UX-02c se registra como `INDEPENDENTLY_AUDITED_ACCEPTED` solamente para presentar `CitationEvidenceEntity` ya asociadas a mensajes y su evidencia HOST. La auditoría no cubrió tablas ni citas de texto libre. Aceptó 10/10 pruebas focalizadas y los registros de 748/748 JVM; no recalculó los hashes de archivos comprimidos. El run EMULATOR anterior sigue FAIL por la fixture Room v1 (1 fallo) y seis pruebas que requieren modelos reales omitidas; no hubo nuevo run. `PHYSICAL_DEVICE=NOT_RUN`.

La gramática reconoce filas delimitadas por `|`, una fila separadora con una celda delimitadora por columna (`---`, `:---`, `---:` o `:---:`), al menos dos columnas y el mismo número de celdas en las filas. En la implementación final, el pipe exterior inicial y el final son opcionales de forma independiente; `\|` representa un pipe literal y los pipes dentro de spans de backticks simples no dividen celdas. Las fences de código no se analizan como tablas. La auditoría detectó que la primera implementación solo quitaba ambos pipes como pareja; la corrección y su evidencia roja/verde están en el [seguimiento de pipes exteriores](../ux-02d-pipe-boundaries/RESULTS.md).

Una tabla candidata irregular se deja como párrafo literal completo, sin descartar filas/celdas. El bloque también conserva su segmento fuente. La respuesta persistida/copiada sigue usando el `content` original: `MainActivity` mantiene el callback de copiar respuesta con `AnnotatedString(content)`; el manejo y copia exacta de fences no cambió.

La tabla se presenta como una colección accesible con encabezados semánticos, índices fila/columna, descripciones `encabezado: valor`, y una descripción de cantidad de filas/columnas e instrucción de desplazamiento. Cada celda tiene ancho legible de 176dp y su texto envuelve; el contenedor permite scroll horizontal cuando excede el viewport.

## Pruebas y gates HOST

En Robolectric/Compose con ancho compacto (cualifier `w320dp`, fixture de 280dp) se comprobó: tabla de tres columnas, overflow real y scroll horizontal, encabezados, contenido de una celda larga que envuelve, descripción accesible y llegada visible a la última columna/valor mediante scroll horizontal y vertical. Se repitió con fontScale 1.0, 1.6 y 2.0. Las pruebas también confirman que una tabla mal formada queda como texto literal y que los pipes dentro de un fence siguen siendo código.

- Focalizado en HEAD `e97a5a1`: **24/24 PASS**, 12 `BasicMarkdownTest` y 12 `ChatContentUiTest`, 0 fallos/errores/omitidos. [Log](host/focused.log), [XML BasicMarkdown](host/focused-junit/TEST-com.localai.workspace.ui.BasicMarkdownTest.xml), [XML Compose](host/focused-junit/TEST-com.localai.workspace.ui.ChatContentUiTest.xml).
- Suite JVM final: app **751/751 PASS**; LiteRT compatibilidad **2/2 PASS**; total **753/753** en 90 XML. El app test corrió en el gate final. El task de compatibilidad apareció UP-TO-DATE ahí, así que además se ejecutó con `--rerun-tasks` en el mismo SHA y pasó 2/2. [Log del gate](host/full-host.log), [log compat forzado](host/litert-compat-rerun.log), [XML completo](host/full-junit.tar.gz).
- `:app:lintDebug`: **PASS**, 0 errores y 82 avisos. No se identificaron avisos lint en los cuatro archivos de parser/UI/pruebas cambiados. [Reporte](host/lint-results-debug.txt).
- `:app:compileDebugAndroidTestKotlin`: incluido en el gate final y UP-TO-DATE. El compile task se había ejecutado y terminado correctamente en la misma implementación de app `df8aeb2`; el commit posterior `e97a5a1` solo ajustó la prueba JVM para desplazar también el fixture vertical. No se ejecutó instrumentación.
- Gate combinado final: exit 0, **137 tareas accionables (8 ejecutadas, 129 UP-TO-DATE)**. El log no reporta tareas `FROM-CACHE`. Logs crudos, comandos, entorno, XML y hashes se conservan en este directorio.
- Iteraciones de desarrollo, incluidos logs/XML de intentos corregidos y un XML copiado tras un fallo de compilación que está expresamente marcado como obsoleto, están archivadas en [iteration-history.tar.gz](host/iteration-history.tar.gz); no se usan como resultado final.

## Capas y límites

- **HOST:** PASS para parser, presentación y gate proporcional indicado.
- **EMULATOR:** NOT_RUN para UX-02d. El resultado global previamente conocido no cambia: FAIL por fixture histórica auténtica Room v1; seis pruebas requieren modelos y permanecen SKIPPED. Este contrato no repitió ese run. Siguen sin causa determinada tanto el indicador collector `applicationApkInstalled=false` como la discrepancia de progreso `22/16` frente al XML de 16 casos.
- **PHYSICAL_DEVICE:** NOT_RUN. No se afirma revisión visual en el Motorola.
- No se generó APK y no se cambiaron workflows, dependencias, almacenamiento, recuperación, navegación, inferencia, permisos ni semántica de Agents/Tools.
- El plan original de 97 tareas no está en el árbol Git; no se reconstruyó ni editó. M01-06 continúa `PENDING_REQUIRES_DEVICE`; UX-03 sigue pendiente y el backlog funcional permanece pausado.

Siguiente paso: auditoría independiente de UX-02d. No iniciar UX-03 ni trabajo funcional.
