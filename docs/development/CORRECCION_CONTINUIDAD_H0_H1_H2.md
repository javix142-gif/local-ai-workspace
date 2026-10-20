# CORRECCIÓN DE CONTINUIDAD — AWS H0 → H1 → H2

## Contexto y objetivo

He revisado H0 y el código publicado en GitHub. La estabilización HOST está verificada, pero quedan un fallo de instrumentación y un desvío hacia el programa antiguo.

Esta es una instrucción directa de ejecución. El plan vigente sigue siendo **H0 → H1 → H2**, sin volver al trabajo preparatorio de M00–M03.

## Archivos que debes leer

Lee `AGENTS.md`, los cuatro documentos adjuntos (`OBJETIVO_AWS.md`, `PROMPT_CODEX_REDIRECCION.md`, `GUIA_VISUAL_AWS.md` y `H1_PRUEBAS_ACEPTACION.md`), `BACKLOG_ACTIVO.md` y solo los módulos afectados.

`H1_PRUEBAS_ACEPTACION.md` es el contrato independiente y obligatorio de H1.

## Archivos modificables

Código y pruebas de H0, H1 y H2, los workflows necesarios y los archivos operativos de continuidad.

## Archivos prohibidos

No modifiques `main`, `v0.4.2`, los respaldos, los secretos, los datos reales ni el backlog histórico de 97 tareas y 120 dependencias.

## Procedimiento obligatorio

1. **Corrige de inmediato la dirección.**
   - `BACKLOG_ACTIVO.md` y `PROGRESS.md` señalan por error M02-01 como próxima tarea. Actualízalos para que digan H1 y después H2, con las tareas de H1 identificadas con los IDs de `H1_PRUEBAS_ACEPTACION.md`.
   - Crea `docs/development/STATUS.md` (150 líneas como máximo) y deja de actualizar `EXECUTION_STATE.json`.
   - Corrige el handoff real que corresponda, sin crear duplicados.
   - Guarda los documentos adjuntos en el repositorio: `OBJETIVO_AWS.md`, `PROMPT_CODEX_REDIRECCION.md` y `H1_PRUEBAS_ACEPTACION.md` en `docs/development/`, y `GUIA_VISUAL_AWS.md` en `docs/design/`.
   - Haz que `STATUS.md` y `BACKLOG_ACTIVO.md` los referencien como contrato vigente, para que se conserven aunque la conversación se resuma.

2. **Rama única:** usa `feat/0.5.0-skills-agents`, que ya contiene el avance publicado de H0. No crees ramas nuevas ni hagas reset, y preserva cualquier cambio local no publicado.

3. **Emulador:**
   - En la ejecución 39431545966 falló `WorkspaceDatabaseMigrationTest.v8CitationRowsSurviveThroughTheCurrentSchemaUpgrade`, porque la anotación Room `@Database` llega nula al leer `.version`.
   - Corrige esa causa y vuelve a ejecutar el gate.
   - Conserva la verificación real de la migración 8→10. No omitas ni debilites pruebas para obtener PASS.

4. **Firma estable:**
   - Prepara el workflow Personal QA para firmar con una clave persistente leída de los secretos de GitHub Actions: `QA_KEYSTORE_B64`, `QA_KEYSTORE_PASSWORD`, `QA_KEY_ALIAS` y `QA_KEY_PASSWORD`.
   - Comprueba que la APK generada usa ese certificado y que el build falla con un mensaje claro si faltan los secretos.
   - No publiques los secretos ni los escribas en logs. Yo crearé la clave y configuraré los secretos.
   - Prepara además la opción de instalar en paralelo, con otro `applicationId`, para probar la importación del respaldo sin tocar la versión instalada.
   - No retrases H1 esperando esta configuración.

5. **Prueba del diálogo:** acepto la comprobación instrumentada en `ProjectUiDeviceTest`. Registra la limitación de Robolectric en `BACKLOG_ACTIVO.md` como pendiente hasta que la CI la ejecute con éxito.

6. **Evidencia:** deja de versionar logs, XML, archivos gzip, tarballs y manifiestos de hashes. Mantén solo un registro breve y verificable de resultados y commits en `PROGRESS.md`.

7. **Comienza H1:**
   - Al terminar los puntos 1 a 6 (la firma puede quedar a la espera de mis secretos), inicia la memoria personalizada y unificada.
   - Cumple los criterios de `H1_PRUEBAS_ACEPTACION.md`: preferencias aprobadas siempre incluidas, rutas multimodales, migración sin pérdida, borrado definitivo, exportación e importación de 1.000 recuerdos, y aislamiento de ámbitos.
   - Si un criterio es inviable, márcalo como `BLOQUEADO` con evidencia y consúltame. No lo debilites.

## Reglas adicionales de seguridad y desbloqueo

**Continuidad H0 → H1:** si la instrumentación de CI sigue bloqueada por una causa externa demostrada después de corregir los fallos de código identificados, registra `CI_PENDING` con evidencia concreta y continúa con H1. No declares H0 completamente aprobado ni omitas pruebas críticas. Si el bloqueo es un defecto reproducible del producto, corrígelo antes de avanzar.

**Firma e instalación:** verifica primero los `applicationId` y certificados existentes. La nueva clave persistente sirve para las actualizaciones futuras firmadas con ella, pero no reemplaza la clave anterior. Si hace falta instalar ambas versiones en paralelo, usa una variante independiente, con `applicationId` y autoridades Android (*authorities*) propios. No desinstales ni modifiques la aplicación existente. Antes de cualquier migración sobre datos reales, exige una exportación y una restauración verificadas.

**Memoria:** incluye siempre las preferencias aprobadas que correspondan (de ámbito usuario y del proyecto activo, como define `H1_PRUEBAS_ACEPTACION.md`), dentro de un presupuesto propio y determinista. Los recuerdos recuperados y los documentos externos siguen siendo datos no confiables. Ninguna memoria puede ampliar permisos, autorizar acciones Android ni anular restricciones de seguridad.

**Pruebas independientes:** los criterios y resultados esperados de `H1_PRUEBAS_ACEPTACION.md` no se pueden cambiar sin aprobación humana. Si una prueba falla, corrige la implementación o informa el bloqueo; no la elimines, omitas ni debilites para conseguir PASS.

## Criterios de aceptación

- **H0:** pruebas JVM en verde, instrumentación de CI verificada y APK trazable al SHA.
- **H1:** todas las pruebas bloqueantes de `H1_PRUEBAS_ACEPTACION.md` superadas con datos sintéticos realistas.
- **H2:** acciones Android tipadas, permisos mínimos y confirmación explícita.
- **Motorola:** `DEVICE_PENDING` hasta que yo autorice y se ejecute la validación física.

## Pruebas requeridas

Ejecuta pruebas focalizadas en cada cambio y suites completas al cerrar cada hito. Conserva la cobertura, valida las migraciones y no declares PASS de pruebas que no se ejecutaron.

## Formato de respuesta

Resumen breve al cerrar cada hito: archivos, SHA, pruebas, pendientes, riesgos y próxima tarea.

## Prohibiciones

No vuelvas a investigar la recuperación, no retomes M02/M03, no alteres ramas protegidas, no manipules datos reales y no desinstales la aplicación del Motorola.

**Continúa de forma autónoma. No necesito otra revisión del plan. Al completar lo pendiente de H0, pasa directamente a H1 y luego a H2.**
