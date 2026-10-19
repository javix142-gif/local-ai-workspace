# Recuperación de continuidad y revalidación focalizada de M03-07

## Estado del respaldo y rama

- Commit pedido como checkpoint: `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`.
- Feature remoto observado al recuperar: `e6f16ac30914fe32f7140241e81e5976660c03d2`; ya estaba 81 commits por delante del checkpoint solicitado.
- Ref de respaldo `recovery/backup-b8e75b4`: lectura verificada en el SHA exacto `b8e75b4a688d9e1f441324a3a1ecccb369f99ebc`.
- Rama independiente: `codex/aws-master-resume-20261019`, base exacta `e6f16ac30914fe32f7140241e81e5976660c03d2`.
- El checkout local recuperó por fast-forward 32 commits preexistentes hasta `f4f69f5980401bc6ae87bab65119134f8b57fc92`; no se reescribieron ni reconstruyeron.
- El commit local no encontrado `2f8b0d496db8973edf161c7703efc8f0c17d34fd` no existe en los object databases/ref/reflogs/bundles disponibles de los dos checkouts examinados. No se reconstruyó. El volumen anterior no estaba montado por separado; solo se inspeccionó el overlay actual.
- `main` y el tag `v0.4.2` permanecen en `de615b772cf37a99e7e08a647c59607b271de606` (el tag ref bruto tiene objeto anotado distinto; su commit peeled es ese SHA).

La salida exacta del preflight y el control de cambios de los dos archivos de M03-07 están en `recovery-preflight.txt` y `m03-07-source-unchanged.txt`.

## M03-07: preview, comparación y cancelación

La fuente original declara M03-07 `TODO`, `requires_device=true`, dependencia M03-01 (`TODO`). La interfaz y la prueba ya existían en la historia recuperada; `git diff --quiet e6f16ac f4f69f5 -- ArtifactsUi.kt ArtifactRevisionPreviewUiTest.kt` devolvió 0: el rango recuperado no cambió esos archivos. Estas verificaciones no cierran la tarea fuente.

| SHA probado | Clases | JUnit | Gradle interno | Herramienta externa |
|---|---|---:|---:|---:|
| `e6f16ac30914fe32f7140241e81e5976660c03d2` | `ArtifactRevisionPreviewUiTest`, `ArtifactSafExportUiTest` | 4/4; 0 fallos, errores u omitidos | `exit 0`, `BUILD SUCCESSFUL` | `functions.write_stdin` reportó `exit_code=1`; no se determinó la discrepancia |
| `f4f69f5980401bc6ae87bab65119134f8b57fc92` | las dos anteriores y `PdfArtifactLayoutTest` | 7/7; 0 fallos, errores u omitidos | `exit 0`, `BUILD SUCCESSFUL`; 63 tareas ejecutadas | `functions.write_stdin` reportó `exit_code=1`; no se determinó la discrepancia |

La ejecución final filtrada en f4 duró 187.220 s (UTC `2026-10-19T21:16:32Z`–`2026-10-19T21:19:40Z`), Gradle 8.10.2, runtime Temurin JDK 17 y worker/toolchain Temurin JDK 21, SDK API 35 y NDK 27.0.12077973, con caché existente y modo offline. El XML JUnit por clase y el log sin alterar están incluidos en `focused-e6f16ac/` y `focused-f4f69f5/`. La discrepancia del código exterior queda abierta y no se oculta. Por tanto el resultado se clasifica como **focused host evidence: Gradle/XML PASS; outer execution status unresolved**, no como gate HOST global.

El diff de fuente también muestra cambios preparatorios previos en documentos/XLSX/PDF/PPTX/harness. Ninguno cambia los archivos de preview de M03-07 dentro del rango e6..f4.

## Límites

- No se cambió ningún estado del backlog original. Backlog SHA-256 `bbb06425761f480a90f06ea8bb987235041f496fcc1065fb4936db3d55dd0235`; roadmap SHA-256 `260590f125e988e92bae284df8366ceda3a15c39c546e0bc78a4d21bbcbbd1c5`; 97 IDs únicos, 120 dependencias, 97 estados fuente `TODO`.
- M03-07 y M03-04 siguen `TODO`; M03-01 sigue `TODO` y bloquea formalmente ambas.
- Suite JVM completa, LiteRT, lint, AndroidTest, build APK, emulador y dispositivo: `NOT_RUN` en esta revalidación. `M01-06=PENDING_REQUIRES_DEVICE`; `PHYSICAL_DEVICE=NOT_RUN`.
- No se afirma compatibilidad visual con lectores Android/Office a partir de pruebas host.
- Los logs, JUnit, comandos, entorno, tiempos, inventario XML y sus hashes están incluidos aquí. `SHA256SUMS` cubre payloads salvo `RESULTS.md`, `RESULTS.json` y el propio manifiesto.

## Manifest

`SHA256SUMS` contains 28 payload entries (including both Gradle logs and all JUnit XML). Its SHA-256 is stored in `RESULTS.json`; the manifest excludes the two results documents and itself.
