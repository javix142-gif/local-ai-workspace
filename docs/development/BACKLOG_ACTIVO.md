# Backlog operativo — AWS H0 → H1 → H2

Esta vista operativa no sustituye ni modifica el catálogo histórico de 97 IDs/120 dependencias ni sus estados fuente.

## Estado más reciente

- **H2 — publicado y validado en capas:** el código `a65a80dae1cf7edc3ae42afeb76620f66bfdc309` añade lecturas locales acotadas y confirmación explícita de acciones. HOST focal 19/19, LiteRT compat 2/2, lint y compilación Kotlin AndroidTest PASS; el gate completo de JVM no se repitió tras corregir el único fallo DOCX.
- **M00-04 — evidencia HOST focal:** 2/2 pruebas PASS en el código H2 actual. La omisión material muestra aviso y motivo en el Inspector; la omisión rutinaria no muestra aviso. El catálogo histórico continúa con estado TODO y sin edición.
- **EMULATOR:** run [39520261327](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520261327), connected AndroidTest PASS; XML 22 total, 16 PASS y 6 SKIPPED por falta de pesos reales. H1-AT-12 pasó en emulador con fixture sintética. PHYSICAL_DEVICE=NOT_RUN.
- **PERSONAL_QA:** [run 39520260941](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39520260941) pasó en el SHA H2. APK Debug ARM64 y Drive readback están en [H2 RESULTS](../validation/master-program-h2-20261021/RESULTS.md).
- La suite completa de app tuvo un único resultado 959/960; la clase DOCX pasó 4/4 tras restaurar la descripción. No se repitió toda la suite y no se declara full-JVM PASS. Una discrepancia de una prueba en un intento focal posterior queda con causa NOT_DETERMINED.
- El siguiente original elegible para preparación HOST es **M00-06**, ya parcialmente implementado: inspeccionar la correlación restante entre la línea temporal de documento/importación e inferencia antes de modificar. No cerrar formalmente el ID ni inferir métricas físicas.

## Secuencia autorizada

1. H0 conserva la aceptación previa y su evidencia histórica; el resultado actual no modifica IDs ni DoD originales.
2. H1 cuenta como `HOST/CI_VERIFIED`; conservar AT-06/20/24 y la inspección Motorola como DEVICE_PENDING, sin convertir omisiones físicas en PASS.
3. **H2:** implementación publicada y workflows PERSONAL_QA/EMULATOR leídos de vuelta; el alcance y los skips se detallan arriba. No equivale a validación física ni a aceptación de la suite JVM completa.

## Límites y evidencia

- Mantener intactos los archivos fuente originales del backlog/roadmap y sus 97 IDs, 120 dependencias y estados TODO.
- `M01-06=PENDING_REQUIRES_DEVICE`; PHYSICAL_DEVICE=NOT_RUN. No sustituir Motorola o XLSX original con emulador/fixture.
- No desinstalar ni limpiar datos del usuario. El APK se probó con el certificado de desarrollo anterior en una fixture sintética; en el teléfono, si Android anuncia conflicto de paquete/firma, cancelar la instalación y no borrar la app existente.
- No modificar `EXECUTION_STATE.json` bajo la instrucción vigente.
- Antes de cualquier publicación oficial, sustituir la clave Debug/QA pública por una clave de firma protegida: **“Reemplazar el keystore de desarrollo por una clave segura antes de publicar”**. La clave actual nunca firma Release.
- Evidencia: [H1 RESULTS](../validation/master-program-h1-20261020/RESULTS.md), [instrumentación](../validation/master-program-h1-20261020/emulator-39511660788/), [QA ARM64](../validation/master-program-h1-20261020/qa-39511660726/).
