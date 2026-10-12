# UX-02b — legibilidad del contenido conversacional

**Estado de entrega:** HOST PASS para el renderer y las pruebas listadas; pendiente de auditoría independiente. No se ejecutó validación nueva de emulador ni de dispositivo.

## Identidad y alcance

- Rama: `feat/0.5.0-skills-agents`.
- Base verificada: `224756694d174af2ed00db4b8c1b5c9fb3f9cb5d`.
- Commit de código probado: `3fc4bf6d496702f98f370e6089e98141486b284d`.
- UX-02a fue aceptado por auditoría independiente para su alcance visual, en el código `d0832e225f51f624c981f6b68da6193b72a78ef2`.
- Cambios funcionales/UI limitados a `BasicMarkdown.kt` y `ChatContent.kt`; pruebas en `BasicMarkdownTest.kt` y `ChatContentUiTest.kt`.
- No cambiaron MainActivity, callbacks de mensajes/copia, atribución/procedencia, persistencia, inferencia, navegación, datos, Room, permisos, Agents/Tools, dependencias ni workflows.

## Hallazgo y cambio

La gramática existente ya reconocía encabezados Markdown de niveles 1–6, pero el parser descartaba el nivel y todos se dibujaban con `titleSmall`. Los bloques tenían además un intervalo uniforme, aunque una lista y un cambio de sección no tienen la misma relación visual.

El selector conserva ahora el nivel que ya reconocía. El renderer lo mapea a la tipografía del sistema existente: encabezados 1/2 usan `titleLarge`/`titleMedium`; 3 usa `bodyLarge` en semibold; 4 usa `bodySmall` en semibold; 5/6 usan `labelLarge` en semibold. Los encabezados reciben semántica Compose `heading` para accesibilidad. Se amplía el espacio antes de una sección, se mantiene un espacio corto después de encabezado y entre elementos consecutivos de lista, y se separan párrafos/bloques con 12 dp. No se fijan alturas ni se limita el número de líneas.

El texto de párrafos, encabezados, listas y código permanece intacto y seleccionable. Se conserva el botón que copia exactamente el contenido del bloque de código. Las líneas largas de código mantienen desplazamiento horizontal. El formato actual no interpreta enlaces, citas ni tablas; permanecen como texto literal, según una regresión explícita. No se incorporó parser nuevo ni se inventó contenido.

## Pruebas host

Toolchain: Linux, Gradle Wrapper 8.10.2, Temurin JDK 17.0.20.1 para Gradle y Temurin JDK 21.0.12.1 para el worker JVM, Android SDK/API 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, Ninja 1.12.1. El entorno y comandos están en [`host/environment.txt`](host/environment.txt) y [`host/commands.txt`](host/commands.txt).

| Gate | Resultado | Evidencia |
|---|---:|---|
| `BasicMarkdownTest` + `ChatContentUiTest` | 17/17 PASS; 0 fallos, errores u omitidos; exit 0 | [`host/focused.log.gz`](host/focused.log.gz), [`host/focused-junit/`](host/focused-junit/) |
| `:app:testDebugUnitTest` completa | 744/744 PASS; 89 XML; 0 fallos, errores u omitidos | [`host/full-junit.tar.gz`](host/full-junit.tar.gz), [`host/full-gate.log.gz`](host/full-gate.log.gz) |
| `:app:lintDebug` | PASS; 0 errores, 82 advertencias | [`host/lint-results-debug.txt`](host/lint-results-debug.txt) |
| `:app:assembleDebug -Parm64Only=true` | PASS; CMake configure/build ARM64 ejecutados | log del gate completo |
| `:app:compileDebugAndroidTestKotlin` | PASS; compilación solamente | log del gate completo |
| Gate combinado | exit 0; 151 tareas accionables, 15 ejecutadas, 136 UP-TO-DATE; 6m38s | `host/full-gate.log.gz`, `host/full-gate-exit-code.txt` |
| `git diff --check` | PASS | comprobado antes de publicar |

La nueva prueba de Compose usa el qualifier compacto `w320dp-h640dp-night` y escalas 1.0, 1.6 y 2.0. Comprueba que el párrafo completo continúa en semántica y se envuelve en múltiples líneas, recorre encabezados y elementos de lista hasta mostrarlos, comprueba semántica accesible de encabezado y jerarquía de tamaños de nodo, y comprueba el eje de desplazamiento horizontal del código. La prueba existente continúa verificando que copiar código devuelve el texto exacto.

La primera versión de la aserción comparó `boundsInRoot` de encabezados fuera del viewport y falló a fontScale 1.6 porque esos bounds representan el recorte visible. Se corrigió la prueba para comparar `SemanticsNode.size`, el tamaño de layout real; la nueva regresión pasó a las tres escalas y vuelve a pasar dentro de la suite completa. No fue un fallo de la UI ni se debilitó la aserción funcional.

Estas son comprobaciones Compose/Robolectric host; no se generaron capturas de pantalla en este contrato ni se afirma revisión visual. Tampoco equivalen a una ejecución en Motorola.

## Formatos y límites

El parser actual representa párrafos, encabezados 1–6, elementos de listas simples, bloques cercados de código, negrita y código en línea. Enlaces/citas/tablas no son formatos renderizados hoy; se conservan literalmente. El código sigue siendo horizontalmente desplazable. No se modificó el contenido copiable del mensaje ni su atribución.

## Validación por capa

- `HOST`: PASS para 17 pruebas focalizadas, 744 pruebas JVM completas, lint, assemble Debug ARM64 y compilación de AndroidTest.
- `EMULATOR`: no repetido para UX-02b. El estado global conocido sigue siendo **FAIL** en el run `38210637867` del código UX-02a: 16 casos, 9 PASS, 1 FAIL por ausencia del fixture histórico auténtico `WorkspaceDatabase/1.json`, 6 SKIPPED por requerir modelos reales, 10 ejecutados. No se modificaron Room ni esos tests.
- `PHYSICAL_DEVICE`: `NOT_RUN`.
- El plan original de 97 IDs/orden/dependencias no está en Git y no fue reconstruido ni editado. M01-06 permanece `PENDING_REQUIRES_DEVICE`; el backlog funcional queda pausado.
- `main` y `v0.4.2` no se modificaron.

## Siguiente paso

Detenerse para auditoría independiente de UX-02b. No comenzar UX-02c, UX-03 ni trabajo funcional de roadmap en este contrato.

Los hashes SHA-256 de los logs, XML y reporte se encuentran en [`SHA256SUMS`](SHA256SUMS).
