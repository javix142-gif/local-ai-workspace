# Backlog operativo — AWS H0 → H1 → H2

Esta vista sigue el contrato de continuidad preservado en [`CORRECCION_CONTINUIDAD_H0_H1_H2.md`](CORRECCION_CONTINUIDAD_H0_H1_H2.md). No sustituye el catálogo histórico.

## Secuencia vigente

1. **H0 — cerrar validación de CI:** corregir el collector para que reconozca la instalación demostrada por una ejecución instrumentada completada aunque Gradle desinstale los paquetes después. Ejecutar un único run nuevo después del push. El run `39443809946` tuvo 16 casos XML: 10 ejecutados sin fallos y 6 omitidos por ausencia de modelos; se clasificó FAIL por la comprobación post-run, que es objeto de la corrección. No declarar H0 cerrado hasta revisar el nuevo artefacto.
2. **H1 — memoria personalizada y unificada:** contrato independiente [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md), v1.1. Se trabaja por los IDs H1-AT-01…H1-AT-24 (incluido H1-AT-05b); no se rebajan. La corrección v1.1 de AT-04 hace prevalecer el tope 15 % e incluye rechazo comprensible al fijar por encima del límite.
3. **H2 — acciones Android:** posterior a H1; alcance de [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md), sección H2.

## Estado actual

- H1 está en curso; una regresión roja probó que Skills podía exceder el presupuesto al coexistir con preferencias. La corrección descontó el bloque de preferencias antes de admitir Skills; la clase focal actual pasó 5/5. Faltan los demás criterios y validaciones H1.
- H0 está pendiente del run posterior a la corrección del collector. El run anterior no se altera.
- Personal QA requiere la clave persistente configurada por el usuario; no se crea una clave sustituta.
- `PHYSICAL_DEVICE=NOT_RUN`; `M01-06=PENDING_REQUIRES_DEVICE`.

## Integridad del plan histórico

Los documentos canónicos de 97 tareas y 120 dependencias permanecen sin cambios. H1-AT son criterios de aceptación de este contrato, no IDs del backlog original. No se infiere cierre de tareas por pruebas parciales.

## Referencias

- [Objetivo](OBJETIVO_AWS.md)
- [Redirección operativa](PROMPT_CODEX_REDIRECCION.md)
- [Aceptación H1](H1_PRUEBAS_ACEPTACION.md)
- [Guía visual](../design/GUIA_VISUAL_AWS.md)
- [Estado breve](STATUS.md)
