# Backlog activo — contrato H0 → H1 → H2

Este documento es una vista operativa, no el backlog canónico. Los documentos fuente archivados de 97 tareas y 120 dependencias permanecen intactos; ninguno de sus estados se modifica aquí.

## Estado vigente

- H0: base HOST registrada en `49f5913937fe7c049bf0ad71d2d75f7e5321b557`. Run `39431545966` confirmó que el test fallaba al reflejar `@Database.version` (Room devolvió anotación nula); el cambio sustituye esa reflexión por una constante compartida con `@Database`. Sigue pendiente ejecutar la instrumentación en CI y producir una APK trazable.
- H1: siguiente hito activo después de corregir el defecto H0. El contrato de aceptación independiente es [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md); sus IDs son H1-AT-01…H1-AT-24 (incluido H1-AT-05b). No relajar resultados esperados.
- H2: posterior a H1, según [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md), sección “H2: Acciones Android”; aún no iniciado.
- Motorola: `DEVICE_PENDING`; no hay validación física declarada. `M01-06=PENDING_REQUIRES_DEVICE`.

## Contrato vigente y referencias

- Instrucción de continuidad: `CORRECCION_CONTINUIDAD_H0_H1_H2.md` (adjunta en el hilo; aplicarla como contrato vigente).
- [`OBJETIVO_AWS.md`](OBJETIVO_AWS.md): objetivo y metas de producto.
- [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md): orden operativo H0, H1 y H2.
- [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md): aceptación obligatoria de H1.
- [`../design/GUIA_VISUAL_AWS.md`](../design/GUIA_VISUAL_AWS.md): guía visual y de accesibilidad.
- [`STATUS.md`](STATUS.md): checkpoint breve vigente.

## Reglas de estado

Los IDs H1-AT son criterios de aceptación, no sustituyen ni reescriben IDs del plan histórico. Ningún criterio ni tarea original se considera terminado por preparación, pruebas parciales o evidencia de otro SHA. Los resultados del dispositivo, CI y HOST se registran por separado.
