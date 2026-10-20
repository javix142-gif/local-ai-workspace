# Estado operativo — AWS H0 → H1 → H2

Fecha: 2026-10-20. Rama: `feat/0.5.0-skills-agents`. HEAD de inicio: `986c57b01460f68ce023eb262e459f57612c81f6`.

## H0

- HOST en código `49f5913937fe7c049bf0ad71d2d75f7e5321b557`: JVM 916/916 + LiteRT compatibility 2/2; lint, AndroidTest Kotlin compile y Debug ARM64 build registrados PASS en `docs/validation/master-program-h0-stabilization-20261020/RESULTS.md`.
- Run `39431545966` demostró que `WorkspaceDatabaseMigrationTest` fallaba porque `getAnnotation(Database::class.java)` devolvió `null`. El ajuste H0 comparte la constante de versión entre `@Database` y el test; conserva la migración real 8→10 y todas las aserciones. La compilación de AndroidTest y la configuración Gradle del keystore fueron validadas en HOST; la instrumentación corregida aún debe correr en CI.
- La APK del checkpoint previo no se entregó por incompatibilidad de firma. La ruta Personal QA ahora exige los cuatro secretos de firma, comprueba el certificado y ofrece el applicationId separado `.parallel`; aún no se ha ejecutado con una clave configurada.
- Estado: `HOST_VERIFIED`, `EMULATOR_PENDING`, `PERSISTENT_SIGNING_PENDING_CONFIGURATION`, `APK_PENDING`, `PHYSICAL_DEVICE=NOT_RUN`.

## Siguiente secuencia

1. Corregir el defecto instrumentado H0 sin quitar ni debilitar la cobertura de migración; ejecutar validación focal y el workflow real. Si el impedimento restante es externo y está demostrado, registrar `CI_PENDING` y avanzar a H1 sin afirmar H0 aprobado.
2. H1: memoria personalizada según los criterios `H1-AT-01`…`H1-AT-24` en `H1_PRUEBAS_ACEPTACION.md`.
3. H2: acciones Android tipadas, lectura de calendario/contactos y estados reales, después de H1.

## Referencias y límites

- [Backlog operativo](BACKLOG_ACTIVO.md) y [handoff](CODEX_TO_WORK_HANDOFF.md) conservan la continuidad.
- [Objetivo](OBJETIVO_AWS.md), [redirección operativa](PROMPT_CODEX_REDIRECCION.md), [aceptación H1](H1_PRUEBAS_ACEPTACION.md) y [guía visual](../design/GUIA_VISUAL_AWS.md) son las referencias adjuntas preservadas sin edición.
- La fuente funcional histórica permanece en `plan-source/2026-10-08/`: 97 tareas, 120 dependencias; no se modifica.
- `M01-06=PENDING_REQUIRES_DEVICE`; no se dispone de medición física de rendimiento en el Motorola.
