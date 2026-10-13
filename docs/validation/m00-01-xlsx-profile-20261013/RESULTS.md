# M00-01 — perfil sintético de expansión XLSX (2026-10-13)

## Estado del ID original

El primer ID M00 pendiente de evidencia suficiente fue **M00-01**: no tiene dependencias y `requires_device=false`. El original ya contiene una corrección en `StructuredDocuments.kt` (commit `2df24f25a1156bfda054d5cafb1d02e9f85d67d1`), pero su etiqueta libre en `completedTasks` no se usó como prueba. La regresión previa intercalaba celdas vacías con `t='s'`; en el parser pre-fix esa forma podía entrar en el error de índice de shared-string antes de medir el presupuesto, por lo que no aislaba la expansión de celdas vacías que este ID pide perfilar. Este contrato ajustó **solo el fixture/prueba**, no código productivo, para aislar ese mecanismo y generar evidencia vinculada a SHA.

**Crosswalk actual:** `M00-01 = HOST_DOD_DEMONSTRATED_FOR_SYNTHETIC_FIXTURE` en el código `988e666910f093344e32e96c46646f555dfb128a`. El backlog fuente permanece intacto con sus 97 estados históricos `TODO`; este estado de evidencia no modifica su JSON. No se afirma que el XLSX privado haya sido inspeccionado ni que su causa exacta se haya probado.

## Fuente original preservada

- Backlog: [`Local_AI_Workspace_BACKLOG.json`](../../development/plan-source/2026-10-08/Local_AI_Workspace_BACKLOG.json), 46,100 bytes, SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`.
- Roadmap: [`Local_AI_Workspace_ROADMAP.md`](../../development/plan-source/2026-10-08/Local_AI_Workspace_ROADMAP.md), 47,285 bytes, SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`.
- Validación: schema `local-ai-workspace/backlog-v1`, 97 tareas, 97 IDs únicos, 120 dependencias, cero referencias faltantes, DAG acíclico; baseline fuente `TODO:97`. Los dos originales no se editaron.

## Fixture y causa sintética

La prueba `StructuredDocumentsTest.formattedEmptyXlsxCellsDoNotConsumeRepresentationBudget` genera un workbook **sintético** con 58,000 celdas vacías con estilo y tres celdas pobladas (`A1=1`, `A2=5`, `A3=4` con fórmula `A2-A1`). No contiene ni reconstruye datos del archivo privado.

| Medida del fixture sintético | Resultado |
|---|---:|
| ZIP comprimido | 291,429 bytes |
| XML de worksheet expandido | 2,414,015 bytes |
| XML expandido total | 2,414,269 bytes (3 entradas XML) |
| Celdas vacías con estilo / celdas pobladas | 58,000 / 3 |
| Representación UTF-8 actual | 392 bytes |
| Estimación del presupuesto fijo pre-fix para las 58,000 vacías | 23,066,872 bytes |
| Límite de representación | 8,388,608 bytes |

La estimación pre-fix se deriva de la fórmula versionada en el código padre de `2df24f2`: 256 bytes más 12 veces las longitudes de campos por celda, incluido el tipo predeterminado `n`. No es una ejecución del parser antiguo. La fixture actual pasa por el parser de producción, conserva las tres celdas pobladas y la fórmula/valor cacheado, produce evidencia de A3 con sus dependencias y confirma que la estimación histórica superaría el límite. Parser, representación y chunk creation se midieron respectivamente en 410 ms, 9 ms y 3 ms en este host; no se ejecutó embedding/indexación.

## Validación HOST

Código probado: `988e666910f093344e32e96c46646f555dfb128a`. Ejecutor: Linux x86_64; Gradle Wrapper 8.10.2; JDK 17 Temurin como runtime de Gradle; JDK 21 Temurin como worker; SDK `/workspace/.toolchain/android-sdk`, NDK `27.0.12077973`; resolución offline con el Gradle cache existente. Worktree detached y sin cambios tracked antes de la ejecución.

**Intento 1:** comando con `--rerun-tasks` en el worktree frío; exit 137 tras 315 s; el worker filtrado había iniciado, pero no se produjo XML ni perfil antes del timeout. Se conserva en `attempt-01-timeout/`; no se cuenta como PASS/FAIL del método.

**Intento 2:** filtro exacto, con dependencias del mismo worktree ya compiladas; Gradle exit 0 en 34 s, `BUILD SUCCESSFUL`, un método ejecutado, JUnit `1/1 PASS` (0 failures/errors/skips), método 4.274 s. Gradle reporta 63 tareas accionables: 1 ejecutada (`:app:testDebugUnitTest`) y 62 up-to-date. Comando y entorno completos están en `attempt-02-pass/`; log completo comprimido, XML y perfil se conservan junto a hashes.

No se ejecutó el gate completo, otras suites, AndroidTest, emulador, Motorola ni APK. `PHYSICAL_DEVICE=NOT_RUN`. El resultado solo valida el perfil sintético de M00-01; no afirma causa del archivo original, ni cambia el estado de UX-02e (`AUDITED_INCOMPLETE_TIMEOUT_NOT_ACCEPTED`).

## Secuencia original

El siguiente ID en el backlog fuente es **M00-02**, depende de M00-01 y no requiere dispositivo. No se inició ni se declaró completo aquí; sus requisitos/evidencia deben reconciliarse en un contrato separado sin inferirlos de etiquetas antiguas. **M01-06** sigue `PENDING_REQUIRES_DEVICE` (depende de M00-02); Motorola y el XLSX original permanecen pendientes.
