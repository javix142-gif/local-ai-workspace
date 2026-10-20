# Nuevo contrato de trabajo para Codex: AWS (reemplaza a todos los anteriores)

> Pegar en un **hilo nuevo** de Codex. No reutilizar el hilo actual: el contrato de recuperación adjunto ahí se vuelve a ejecutar tras cada compactación de contexto.

---

## 0. Contexto y cierre de lo anterior

- Repositorio: `javix142-gif/local-ai-workspace`. **Rama de trabajo única: `codex/aws-master-resume-20261019`** (HEAD `e83322d` al 2026-10-20). Debe existir **una sola sesión de Codex** escribiendo en ella. Si hay otra en paralelo, detenla: el push rechazado porque "el remoto ya estaba en ese SHA" y los directorios de evidencia ajenos indican trabajo duplicado.
- **No crees más ramas `codex/*` ni `recovery/backup-*`.** Ya hay 4 ramas de trabajo y 4 de respaldo. Haz *fast-forward* de `feat/0.5.0-skills-agents` a la rama de trabajo y deja de usar `codex/master-program-20261016`, `codex/master-program-20261019` y `codex/master-program-recovery-20261019`; todas son antecesoras y no se pierde nada.
- **La recuperación está CERRADA.** El commit `2f8b0d4` no es recuperable y eso ya está documentado. **No vuelvas a buscarlo**, ni a verificar respaldos, refs de `main`/`v0.4.2` o el catálogo de 97 tareas, ni a revalidar M03-07. Si el hilo se compacta, **este documento es el único contrato vigente**; ignora cualquier adjunto o prompt anterior.
- Ya están hechos y **no se rehacen**: el PDF con Noto Sans (Unicode) y la creación de Skills conversando con el modelo (`SkillDraftProposal`, con revisión antes de guardar). **Congela M02 y M03** desde ahora.

## 1. Objetivo del producto (resumen)

AWS debe convertirse en un asistente personal local que: (1) **recuerde y aplique las preferencias del usuario**, (2) **lo ayude con la agenda, los mensajes y las tareas**, y (3) **ejecute acciones reales del teléfono con confirmación**. La fidelidad de documentos ya es suficiente por ahora. Referencia completa: `OBJETIVO_AWS.md` (si no está en el repo, agrégalo en `docs/OBJETIVO_AWS.md`).

## 2. Qué hacer, en este orden

### H0: Base confiable (pequeño; hazlo primero)
0. **Suite JVM completa, una vez.** No se ejecuta desde `f54c143` (12/10/2026), hace casi 300 commits. Ya apareció un test roto sin detectar (`SemanticStorageTest` esperaba la versión 8 de Room). Ejecuta `:app:testDebugUnitTest` completo y `:litert-compat:test`, corrige lo que falle y anota el resultado en una línea.
1. **CI verde.**
   - Reemplaza en `WorkspaceDatabaseMigrationTest` la creación desde la versión 1 (no existe `1.json`) por migraciones probadas desde el esquema 8 hasta el 10. Las migraciones 1→7 quedan como deuda documentada, o se prueban con DDL literal.
   - Haz que los workflows se disparen también en `codex/aws-master-resume-20261019`. Hoy solo se disparan en `feat/0.5.0-skills-agents`, así que nada de lo hecho desde el 19/10 ha pasado por CI.
   - Que la puerta QA no falle por tests que se omiten al no haber modelo.
   - **Hecho cuando:** el workflow del emulador está en verde y hay un APK QA del HEAD.
2. **Una sola rama** (ver sección 0). Haz *fast-forward* de `feat/0.5.0-skills-agents` al HEAD de trabajo. No crees ramas `backup`/`recovery` salvo antes de una operación destructiva.
3. **Script de humo para el Motorola**, `scripts/device/smoke_moto.sh` (adb): instala el APK, abre la app y recoge PSS, logcat y la traza JSON de una conversación. El usuario lo ejecuta. **No bloquees tareas esperando el dispositivo.**

### H1: Memoria que funciona (prioridad n.º 1 del usuario)
1. **Bloque de preferencias siempre incluido.**
   - Contenido: las memorias `ACTIVE` de tipo `PREFERENCE`/`PROCEDURE` y ámbito `USER`, más las fijadas (*pinned*).
   - Ubicación: una sección "Preferencias declaradas por el usuario" de la instrucción de sistema, con presupuesto propio (≤15 % del contexto de entrada) y orden determinista.
   - Ajusta `ContextTemplate.POLICY` para que el modelo **siga** esas preferencias aprobadas por el usuario. Los documentos, la web y las herramientas siguen siendo datos no confiables.
   - **Test:** una preferencia fijada aparece en el contexto aunque la consulta no tenga relación.
2. **Unificar la memoria.**
   - Migra `MemoryItemEntity` (en `WorkspaceDatabase`) a `MemoryRecord` (en `memory_context.db`) conservando tipo y ámbito.
   - Retira `MemoryViewModel`, la UI legacy y `retrieval.memories`.
   - La selección de memoria debe usar el sidecar también en turnos con imagen o audio (hoy `ViewModels.kt:729` cambia al pipeline legacy).
3. **Borrado y exportación.**
   - Añade `purge` físico y la opción "Olvidar definitivamente", con purga de `DELETED`/`SUPERSEDED`.
   - Al borrar un proyecto o una conversación, borra también sus filas de `memory_context.db` (memorias, *briefs* y `conversation_sources`).
   - Exportación ZIP sin el tope de 250 registros (paginada).
   - Elimina "Export JSON" al portapapeles o cámbialo por SAF con filtro de secretos.
4. **Propuestas de memoria.** Herramienta `memory.propose` o propuesta tras el turno. Las propuestas siempre quedan `PENDING` y aparecen en la bandeja de aprobación existente. **Nunca** se activan sin aprobación.

### H2: Acciones Android (prioridad n.º 3; adelanta M08)
1. Crea un `AndroidActionTool` tipado con UI de confirmación para estas acciones: `AlarmClock` (alarma y temporizador), `CalendarContract ACTION_INSERT` (evento prellenado), `ACTION_DIAL`, `ACTION_SENDTO` (borrador de SMS o correo), mapas, compartir y `Settings.ACTION_*`.
   - Estados que se muestran al usuario: `PREPARED` → `OPENED` → `CONFIRMED_BY_USER`. **Nunca** afirmes "enviado" ni "creado" sin evidencia.
2. Añade `calendar.read` (`READ_CALENDAR`, instancias por rango) y `contacts.lookup` (`READ_CONTACTS`), con el permiso pedido en el momento de uso y los resultados tratados como datos.
3. Acorta las descripciones de las herramientas, porque consumen contexto. La de `artifacts.create/revise` tiene más de 2.000 caracteres.

### Congelado hasta terminar H2
M02/M03: fidelidad OOXML, previews y figuras PPTX, plantillas, tablas, metadatos y casos límite de troceado; M00-06: líneas de tiempo y presentación de benchmarks; y optimizaciones de rendimiento sin una medición en el dispositivo que las justifique. **Solo** se corrigen defectos que el usuario reporte.

### Guía visual
Aplica `GUIA_VISUAL_AWS.md` (agrégala a `docs/design/`) **dentro** de H1 y H2: primero los textos a `strings.xml` en español; después la navegación Hoy · Chats · Memoria · Más y los componentes nuevos de cada hito.

## 3. Reglas de eficiencia (obligatorias)

1. **Entorno:** al inicio de cada comando, `export GRADLE_USER_HOME=/workspace/.gradle` y `JAVA_HOME` apuntando al JDK 17 ya instalado en `/workspace/.toolchain`. Anótalo una vez en `AGENTS.md`. Si el entorno se perdió, reinstala una vez y sigue.
2. **No uses `--rerun-tasks`**, porque recompila todo y cada ejecución pasa de unos 40 s a unos 3,5 min. Para forzar solo los tests: `./gradlew :app:testDebugUnitTest --tests '<Clase>' --rerun --offline`.
3. **TDD con ejecución roja separada solo para corregir defectos.** En funciones nuevas, escribe el código y los tests y ejecuta una vez. Revisa la sintaxis del test antes de ejecutar.
4. **La suite JVM completa se ejecuta una vez por hito** (H0, H1, H2), no por incremento. Lint y androidTest se ejecutan en CI, no en local offline.
5. **No revalides funciones que no cambiaron.** No investigues discrepancias del código de salida del wrapper: valen el XML de JUnit y `BUILD SUCCESSFUL`.
6. **Evidencia mínima:**
   - **No** se versionan logs, `.gz`, `SHA256SUMS`, XML, PDF ni capturas.
   - Por cada incremento, añade 3-5 líneas a `docs/development/PROGRESS.md`: fecha, SHA, qué cambió, tests ejecutados y qué falta en el dispositivo.
   - Sustituye `EXECUTION_STATE.json` por `docs/development/STATUS.md` de ≤150 líneas, con rama, HEAD, último APK, pendientes en el dispositivo y próxima tarea. Archiva el JSON anterior una sola vez.
7. **Commits:** uno por incremento (código y tests juntos). Nada de commits `docs: record …` separados. Publica por *fast-forward* cada 3 incrementos o al cerrar un hito.
8. **Backlog:** no modifiques el archivo histórico `plan-source/2026-10-08/Local_AI_Workspace_BACKLOG.json`. Crea `docs/development/BACKLOG_ACTIVO.md` con el estado real de cada tarea (`HOST_VERIFIED`, `DEVICE_PENDING`, `DONE`) y los IDs nuevos de H1 y H2. Ese es el backlog vigente; no dejes todo en `TODO`.

## 4. Lo que se mantiene

- No declarar validaciones que no ocurrieron.
- Migraciones Room aditivas con tests.
- Confirmación explícita antes de cualquier acción con efectos.
- No tocar `main` ni `v0.4.2`.
- No meter modelos, APK ni secretos en git.

## 5. Informe al cerrar cada hito (máximo 15 líneas)

1. Qué puede hacer ahora el usuario en el teléfono.
2. SHA y enlace del APK QA.
3. Tests ejecutados (una línea).
4. Checklist de ≤10 pasos para probar en el Motorola.
5. Riesgos o pendientes.
