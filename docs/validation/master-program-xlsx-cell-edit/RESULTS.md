# Edición acotada de celdas XLSX — preparación HOST de M03-02

Fecha: 2026-10-16. Repositorio `javix142-gif/local-ai-workspace`; rama local `codex/master-program-20261016`; publicación prevista únicamente por fast-forward a `feat/0.5.0-skills-agents`.

## Alcance y clasificación

Se añadió un camino determinista para editar de 1 a 64 celdas A1 literales en una revisión privada generada por la app. Cada operación exige proyecto, artefacto y versión actuales; crea una revisión inmutable nueva y verifica sus bytes. La resolución de hoja ignora mayúsculas/minúsculas, la dirección A1 es exacta y no se expanden rangos. Las celdas con fórmula no se sobrescriben mediante esta herramienta. Las fórmulas y valores cacheados no se recalculan; el resultado lo dice expresamente. La exportación a un destino externo sigue siendo una acción SAF separada y los documentos originales importados no se editan.

La selección determinista `artifacts.xlsxSetCells` exige un verbo de edición, contexto de hoja/celda y una referencia A1; solicitudes de fórmula/recalculo no entran en esta ruta. El tool requiere `LOCAL_WRITE`, respeta el registro de herramientas habilitadas y limita el esquema a 64 ediciones.

Esto es **preparación parcial de M03-02**, no cierre del ID original: el backlog conserva `M03-02=TODO`, `requires_device=true`, dependencias `M03-01` y `M02-02`. Ambas siguen `TODO`; M03-01 depende de M02-05, y M02-02 depende de M02-01, que a su vez espera M00-10/dispositivo. No se modificó el catálogo fuente.

## Implementación

Commits de código desde la base publicada `20a50c7166cd53aff02f9580f9056b47f7d9f31d`:

- `ba868f413e43b13e0685b9979f663e8fae6daa5a` — regresión de fórmula y valor cacheado en revisiones guardadas.
- `1ee58e4190112a5e53aa7e84365a517eaba40f6e` — prueba roja de routing de la edición explícita.
- `191fdcf655edce40a1b59139a67c424cf4f7c01f` — herramienta y operación versionada de edición literal acotada.
- `6cc90c3c1acfbaeda7b148eb0d08579de196b7f0` — integración de herramienta, capability, fórmula/cache y scope de hoja.
- `045047cca7bc9d9cb11897ccd68d3a16fe74568f` — assertion de aislamiento de proyecto.

Archivos de producto: `ArtifactStore.kt`, `ArtifactTools.kt`, `CapabilityPolicy.kt`, `AssistantRouting.kt`, `ProjectTools.kt`, `NativeChatTools.kt`; test `XlsxArtifactRevisionTest.kt`. No cambian persistencia Room, esquemas, dependencias, inferencia ni motor XLSX externo.

## Pruebas

Entorno disponible: Gradle Wrapper 8.10.2; JDK Temurin 17 como runtime de Gradle y Temurin 21 descubierto para workers; SDK `/workspace/.toolchain/android-sdk` (API 35), NDK `27.0.12077973`; Gradle user home existente `/workspace/.gradle-master-program-20261016`; modo `--offline`.

| Intento | Código probado | Resultado | Evidencia |
|---|---|---|---|
| Fórmula/cache al guardar y reabrir | `ba868f413e43b13e0685b9979f663e8fae6daa5a` | 1/1 PASS, exit 0, 47 s | `../master-program-xlsx-revisions/attempt-01-exact-source/` |
| Reproducción roja del routing | `1ee58e4190112a5e53aa7e84365a517eaba40f6e` | 1 prueba, fallo esperado: routing devolvió `[]` en vez de `artifacts.xlsxSetCells`, exit 1 | `master-program-xlsx-cell-edit/attempt-01-red/` |
| Clase completa inicial | `6cc90c3c1acfbaeda7b148eb0d08579de196b7f0` | 3/3 PASS, exit 0, 87 s | `master-program-xlsx-cell-edit/attempt-02-green-exact-source/` |
| Clase completa con aislamiento de proyecto y `--rerun-tasks` | `045047cca7bc9d9cb11897ccd68d3a16fe74568f` | 3/3 PASS, 0 failures/errors/skips; exit 0; 271 s; `BUILD SUCCESSFUL`, 63 tareas ejecutadas | `master-program-xlsx-cell-edit/attempt-03-scope-exact-source/` |

Comando final:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17-temurin \
PATH=/workspace/.toolchain/jdk-17-temurin/bin:$PATH \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk \
GRADLE_USER_HOME=/workspace/.gradle-master-program-20261016 \
./gradlew --no-daemon --no-parallel --max-workers=1 --offline --console=plain --rerun-tasks \
  -Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17-temurin,/workspace/.toolchain/jdk-21-temurin \
  :app:testDebugUnitTest --tests com.localai.workspace.artifacts.XlsxArtifactRevisionTest
```

El XML final registra 3 tests, 0 omitidos, 0 fallos y 0 errores; tiempo total de métodos 8.131 s. El log completo conserva recompilación de app/tests y `BUILD SUCCESSFUL in 4m 30s: 63 actionable tasks: 63 executed`. Los manifiestos por intento se verificaron desde la raíz del repositorio. El SHA-256 del XML final está en `attempt-03-scope-exact-source/SHA256SUMS`.

No se ejecutaron suite JVM completa, LiteRT compat completa, lint, assemble APK, AndroidTest instrumentado, emulador ni Motorola. No se midió TTFT, tokens/s, memoria, energía ni calidad en dispositivo. No se generó APK en este incremento.

## Plan original y límites

Backlog y roadmap archivados se conservan byte por byte: SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235` y `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`. El catálogo sigue con 97 IDs únicos, 120 dependencias y 97 estados `TODO`.

`M03-02` requiere además una prueba real en dispositivo y aún no cubre fórmulas editables ni recálculo; el punto de entrada agregado solo edita celdas literales de artefactos privados de la app. No se afirma fidelidad frente a importaciones arbitrarias ni apertura en una suite de oficina física. `M01-06` permanece `PENDING_REQUIRES_DEVICE`; Motorola `NOT_RUN`. El estado global EMULATOR continúa separado: fallo histórico de la fixture auténtica Room v1 y seis pruebas omitidas por ausencia de modelos reales.

## Respaldo del estado de trabajo

Antes de continuar desde el estado local con los commits XLSX ya creados, se guardó un bundle Git y un tar de la evidencia no confirmada fuera del checkout en `/workspace/backups/aws-pre-integral-20261016-231902/`; hashes y procedimiento de restauración constan en `SHA256SUMS` y `RESTORE.txt`. Incluye HEAD `6cc90c3c1acfbaeda7b148eb0d08579de196b7f0` y las evidencias entonces existentes; el commit posterior de test de aislamiento queda en el historial normal de Git, no en ese punto de retorno.
