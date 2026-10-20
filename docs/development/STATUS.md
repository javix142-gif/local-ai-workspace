# Estado operativo — AWS H0 → H1 → H2

Fecha: 2026-10-20. Rama `feat/0.5.0-skills-agents`; código H1 `3efc959694f193122da45130b08b2abe60e24608`.

## H0 — verificación CI en curso

- El arreglo del collector quedó publicado en `ae4cdbd`; conserva como histórico el FAIL del run `39443809946` (16 XML: 10 ejecutados, 6 omitidos por falta de modelos; 0 fallos de pruebas, collector falló al verificar paquetes después de la tarea).
- El push del código H1 inició el run instrumentado `39463950185` en API 35 x86_64. Aún no hay resultado final; no se declara H0 cerrado.
- QA ARM64 `39463950123` espera el éxito de esa instrumentación. Personal QA `39463950118` falló el preflight porque faltan los secretos persistentes de firma; no produjo una APK personal aprobada.

## H1 — cambio publicado, HOST validado

- Contrato v1.1: [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md). Se aplica el límite de AT-04: el bloque de preferencias no excede 15 %; un pin que excede el máximo se rechaza antes de persistir, con explicación.
- El gate HOST exacto del código `3efc959` pasó: app 938/938, LiteRT compatibilidad 2/2, 0 fallos/errores/omisiones; `lintDebug`, `assembleDebug` y `compileDebugAndroidTestKotlin` también completaron. Gradle 8.10.2, runtime JDK 17, worker app JDK 21, SDK/NDK preinstalados; ejecución offline, 904 s de pared, 158 tareas ejecutadas.
- Log completo fuera de Git: `/tmp/aws-h1-full-host-gate-v2.log` (SHA-256 `c977579d1013c2e603504817b4295ea70e078a13ee0ba37cf3aa6b1613ecb75f`). El gate local produjo un APK Debug de compilación; no es una entrega ni una validación Android.
- **H1-AT-17 BLOQUEADO:** la prueba de importación de 1.000 registros conserva campos y detecta correctamente duplicados, pero su fixture exige aprobar 50 pins de ~300 caracteres y dejar 1.000 `ACTIVE`. Eso contradice el límite obligatorio de AT-04 v1.1, que rechaza el pin que excede 15 % y prohíbe omitirlo silenciosamente. La prueba no se cuenta como aceptación de AT-17; no se relajó ninguno de los criterios.
- Los demás criterios HOST requieren correlación y revisión criterio por criterio; HOST PASS no los convierte en CI_VERIFIED. H1 sigue abierto y H2 no ha comenzado.

## Pendientes y límites

- Esperar y auditar el run Android `39463950185`; si falla, separar el fallo funcional de los seis skips por modelos reales.
- AT-02/09 incluyen inspección; AT-12 requiere CI y dispositivo; AT-06/20 requieren el Motorola, con AT-20 antes de instalar H1. AT-24 también depende del dispositivo. `PHYSICAL_DEVICE=NOT_RUN`; `M01-06=PENDING_REQUIRES_DEVICE`.
- El catálogo histórico sigue intacto: 97 IDs y 120 dependencias; no se promovió ningún ID. El `EXECUTION_STATE.json` histórico no se modifica bajo el contrato vigente.
- No se afirma rendimiento nativo, TTFT, tokens/s, consumo ni compatibilidad Motorola.

Referencias: [`continuidad`](CORRECCION_CONTINUIDAD_H0_H1_H2.md), [`backlog activo`](BACKLOG_ACTIVO.md), [`contrato H1`](H1_PRUEBAS_ACEPTACION.md), [`handoff`](CODEX_TO_WORK_HANDOFF.md).
