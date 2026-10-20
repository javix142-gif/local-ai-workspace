# Backlog operativo — AWS H0 → H1 → H2

Esta vista sigue [`CORRECCION_CONTINUIDAD_H0_H1_H2.md`](CORRECCION_CONTINUIDAD_H0_H1_H2.md); no sustituye ni modifica el catálogo histórico de 97 tareas/120 dependencias.

## Secuencia vigente

1. **H0 — instrumentación validada en emulador para SHA `3ca1d79`.** Run `39474604686` completó la tarea conectada; 14 PASS, 6 skips por falta de modelos, 0 fallos. El fixture Room v1 auténtico y Motorola siguen fuera de alcance/pendientes.
2. **H1 — memoria personalizada y unificada.** Contrato [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md), v1.1. El gate HOST completo pasó en el árbol fuente publicado como `3ca1d79`; H1-AT-04 y AT-17 están cubiertos por 22 tests PASS. H1 global sigue abierto por criterios físicos y revisión completa.
3. **H2 — acciones Android.** Permanece sin iniciar hasta que se satisfagan los prerequisitos H1 de dispositivo indicados en [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md).

## Estado operativo

- **HOST:** 939 app + 2 LiteRT PASS; `lintDebug`, `assembleDebug` y compilación Kotlin AndroidTest PASS. Log, comando, entorno y checksums están en [`STATUS.md`](STATUS.md) y [evidencia H1](../validation/master-program-h1-20261020/RESULTS.md).
- **AT-04:** prevalece el límite del 15 %. Se aceptan los pins cortos que caben; la acción de fijar rechaza y explica un pin que rebasaría el límite. No se descarta silenciosamente memoria fijada.
- **AT-17:** la prueba actual pasa importación/roundtrip de 1,000 registros, preservación de metadata y duplicados, además de rechazar un prompt sobredimensionado antes de inferencia. La importación conserva pins existentes; no simula fijar manualmente 50 memorias largas.
- **EMULATOR:** run `39474604686` PASS para 20 casos XML: 14 ejecutados PASS, 6 SKIPPED que requieren modelos reales. No equivale a dispositivo físico ni a mediciones de inferencia.
- **APK QA:** artifact oficial `12290056446` validado, pero su certificado difiere de la instalación personal previa. El ZIP está en Drive claramente marcado `NO COMPATIBLE COMO ACTUALIZACIÓN`; no instalar sobre la aplicación con datos existentes. Personal QA con clave persistente sigue bloqueado por cuatro secretos ausentes.
- **DEVICE:** `PHYSICAL_DEVICE=NOT_RUN`; AT-06/20/24 y `M01-06=PENDING_REQUIRES_DEVICE`. Se necesita Moto G86, backup externo previo a AT-20, XLSX original y firma compatible.
- Los IDs, orden, dependencias y estado del catálogo original no se alteran. No se cierra H1 ni se inicia H2 en este checkpoint.

## Referencias

- [Objetivo](OBJETIVO_AWS.md)
- [Redirección operativa](PROMPT_CODEX_REDIRECCION.md)
- [Aceptación H1 v1.1](H1_PRUEBAS_ACEPTACION.md)
- [Guía visual](../design/GUIA_VISUAL_AWS.md)
- [Estado breve](STATUS.md)
