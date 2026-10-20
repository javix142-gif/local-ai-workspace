# Backlog operativo — AWS H0 → H1 → H2

Esta vista sigue [`CORRECCION_CONTINUIDAD_H0_H1_H2.md`](CORRECCION_CONTINUIDAD_H0_H1_H2.md); no sustituye ni modifica el catálogo histórico de 97 tareas/120 dependencias.

## Secuencia vigente

1. **H0 — verificar CI del collector corregido.** El arreglo fue publicado en `ae4cdbd`. Run de verificación `39463950185` iniciado por el push del código H1; pendiente de resultado. El run `39443809946` permanece como histórico FAIL del collector, no se reescribe.
2. **H1 — memoria personalizada y unificada.** Contrato independiente [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md), v1.1; criterios H1-AT-01…24 (incluido 05b). No se cambian resultados esperados.
3. **H2 — acciones Android.** Posterior a H1; alcance indicado en la sección H2 de [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md).

## Estado al código `3efc959694f193122da45130b08b2abe60e24608`

- **HOST:** gate Gradle completo PASS: app 938/938 y LiteRT compatibilidad 2/2; lint, `assembleDebug` y compilación Kotlin AndroidTest PASS. Comando/entorno/conteos y hash del log están en `STATUS.md` y `CODEX_TO_WORK_HANDOFF.md`.
- **H1-AT-04:** el 15 % prevalece; tres pins cortos caben y fijar por encima del cupo se rechaza antes de persistir. Prueba incluida en HOST.
- **H1-AT-17:** `BLOQUEADO`, no aceptado. El resultado original requiere aprobar 1.000 registros conservando 50 pins largos; eso no se puede satisfacer junto con AT-04 v1.1. La prueba conserva esta incompatibilidad como bloqueo verificable y no se usa como PASS del criterio.
- **EMULATOR/CI:** run `39463950185` en curso; aún no marca H0 ni los criterios H1 como CI_VERIFIED. QA ARM64 `39463950123` espera el gate. Personal QA `39463950118` falló por secretos persistentes de firma ausentes.
- **DEVICE:** `PHYSICAL_DEVICE=NOT_RUN`; AT-06/20/24 y M01-06 continúan pendientes del Motorola; no se instala ni migra información real.
- No se declara H1 cerrado ni se inicia H2. El trabajo registrado aquí no cambia IDs, orden, dependencias ni estados del catálogo original.

## Referencias

- [Objetivo](OBJETIVO_AWS.md)
- [Redirección operativa](PROMPT_CODEX_REDIRECCION.md)
- [Aceptación H1 v1.1](H1_PRUEBAS_ACEPTACION.md)
- [Guía visual](../design/GUIA_VISUAL_AWS.md)
- [Estado breve](STATUS.md)
