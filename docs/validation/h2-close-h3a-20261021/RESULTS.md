# Cierre H2 y transición H3a/H3b — evidencia

Fecha: 2026-10-21
Repositorio: javix142-gif/local-ai-workspace
Rama: feat/0.5.0-skills-agents
Base remota antes del cambio: a779880351ae1ed25ab32e8f6ef906abd7252fe8

## Código y decisión

- H2: commit 6e6acdaaedb20b7aff95fd2154d2b20eb8495cc6. Declara com.android.alarm.permission.SET_ALARM en la manifest app y añade AlarmPermissionManifestTest para consultar requestedPermissions del paquete de destino después de la fusión de manifiestos.
- La implementación previa de acciones Android conserva confirmación explícita. AndroidActionToolTest mantiene ACTION_SET_ALARM/ACTION_SET_TIMER y EXTRA_SKIP_UI=false. AndroidActionUiTest conserva la interacción explícita de confirmación.
- H3a: commit fa6071f0322e0a60da5c2acf023e95847ab16619. Añade scripts/device/smoke_moto.ps1, de solo lectura por defecto, y sanea los errores ADB antes de guardarlos. Guía: ../../development/H3A_MOTO_G86_SMOKE.md.
- No se modificaron inferencia, permisos en runtime, datos, persistencia, esquema, workflow ni catálogo fuente.

## HOST

El gate comenzó a las 2026-10-21T12:28:54Z y terminó a las 12:36:12Z (Gradle informa 7m17s; diferencia de redondeo de timestamps: 7m18s). Exit code 0, BUILD SUCCESSFUL. Entorno: Gradle Wrapper 8.10.2, JDK Temurin 17.0.20.1 como Gradle runtime, JDK Temurin 21.0.12.1 para los workers de tests de app, SDK API 35, NDK 27.0.12077973, CMake 3.31.6, GRADLE_USER_HOME existente en /workspace/.gradle, modo offline, Linux x86_64.

Atribución importante: al inicio Git HEAD era a779880. El árbol de trabajo contenía las dos modificaciones H2 de manifest/test; quedaron comprometidas sin cambios de aplicación en 6e6acda. Este gate valida ese contenido de trabajo, pero no fue un checkout limpio con HEAD=6e6acda. El commit posterior fa6071f solo modifica el script de smoke, no app. Ver tested-code-attribution.txt.

Resultados:
- :app:testDebugUnitTest: 132 XML; 961/961 PASS, 0 failures, 0 errors, 0 skipped.
- :litert-compat:testDebugUnitTest estaba UP-TO-DATE en el gate combinado; la ejecución forzada separada posterior a 6e6acda pasó 2/2 en un XML, exit 0.
- :app:lintDebug: exit 0; XML de lint con 0 errores, 128 avisos y 1 información.
- :app:assembleDebug: ejecutado, exit 0.
- :app:compileDebugAndroidTestKotlin: ejecutado, exit 0. Esto compila el nuevo test de permiso, pero no lo ejecuta.
- :app:assembleDebugAndroidTest: la tarea completó sin acciones; no equivale a instrumentación.
- Conteo de tareas del gate: 177 accionables, 40 ejecutadas, 3 de caché, 134 up-to-date.

Cuatro ejecuciones focales adicionales, todas exit 0 y XML 1/1: dos veces AndroidPersonalDataToolsTest.toolSchemasAndDeterministicRoutingExposeOnlyRelevantPersonalReadsAndActionHandoffs y dos veces DocxArtifactStructureTest.generatedDocxEmitsOrderedListNumberingAndKeepsBulletList. No se reprodujeron las discrepancias anteriores; su causa histórica permanece NOT_DETERMINED.

La inspección de la APK Debug local de comprobación y de la manifest fusionada encontró SET_ALARM, paquete com.localai.workspace.debug, versionCode 27 y versionName 0.5.0-debug. Esa APK local no es el artefacto de entrega ni fue instalada.

Comandos, ambiente, logs, XML originales, inventarios y hashes están en las carpetas full-host/, litert-compat/ y focused/. Cada log Gradle se conserva sin pérdida como .log.gz en Git; los archivos crudos originales permanecen en este ejecutor. log-compression.json registra SHA-256 y bytes de ambos y confirma que descomprimir reproduce exactamente el log crudo. El comando exacto del gate completo está en full-host/command.txt; el del gate LiteRT separado, en litert-compat/command.txt.

## H3a y H3b

La revisión estática del script se registra en device-tool/static-validation.txt. Este ejecutor no dispone de pwsh ni powershell. El ADB fijado existe en el SDK; adb devices -l terminó exit 0 con cero dispositivos conectados. Por tanto el script no se ejecutó y no se identificó un Moto G86. H3a=DEVICE_PENDING y PHYSICAL_DEVICE=NOT_RUN.

El smoke script por defecto no instala/actualiza/desinstala, no borra datos, no cambia permisos, no envía mensajes, no crea alarmas o eventos y no descarga modelos. Solo abre la app con una opción explícita. La captura completa es una opción aparte: pide confirmación interactiva, se guarda localmente fuera del repositorio, dentro de private-evidence, y se excluye del manifiesto compartible. El informe compartible enmascara el serial y filtra los logs.

H3b queda a la espera de las nueve referencias visuales. No se implementó cambio visual.

## Workflows para el SHA `fa6071f0322e0a60da5c2acf023e95847ab16619`

- Android emulator, run [39601660535](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39601660535), job `119227404771`: AVD/ADB API 35 Android 15 x86_64 pasó. H1-AT-12 actualización real con fixture sintética pasó. `:app:connectedDebugAndroidTest` exit 0 y BUILD SUCCESSFUL; XML 23 total: 17 PASS, 0 FAIL/ERROR, 6 SKIPPED (tests que requieren modelos reales no descargados). `AlarmPermissionManifestTest.packagedApplicationRequestsSetAlarmPermission` PASS. El run global y collector son **FAIL**, porque la captura obligatoria de fuente grande no quedó completa: estándar 1080×1920 PASS; large-font sin chunks/end marker y sin PNG. Artefacto original y XML: [evidencia del run](emulator-39601660535/); 97,340 bytes, SHA-256 `66edcc56b60c6a7ed0a36778344944c96ea4a24441cb220353beed1d40e1ee9d`, ID `12347627628`, expira 2026-10-28T13:06:56Z.
- El log Gradle anuncia “28/23 completed” y “Finished 29 tests”; el único XML y el collector reportan 23. La diferencia de progreso queda sin explicación. El post-run package check no encontró instalada la app ni el APK test, aunque el resumen registra `verifiedByConnectedInstrumentation=true`; no se infiere fallo de instalación de ese dato posterior.
- QA oficial, run [39601660283](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39601660283), job `119227404353`: **FAIL_CLOSED**, motivo `ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL`; el build y upload oficial se omitieron y no se creó artifact.
- Personal QA, run [39601677489](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39601677489) — SUCCESS en el SHA exacto. Artifact GitHub `12346548223`, 48,987,531 bytes, SHA-256 `a4056d3180c27c89b1f98cb1756cd8f395c02c12916072e1ac203ff79930d40c`, retención hasta 2026-11-20T12:50:44Z. Manifiesto verificado; APK CRC, zipalign, firma y metadata PASS; APK arm64-v8a sin payload de modelo. Instrumentación en el workflow personal: NOT_RUN.

### APK personal en Drive

APK Debug personal, no Release: `com.localai.workspace.debug`, `0.5.0-debug` / versionCode 27, ABI únicamente `arm64-v8a`, 9 bibliotecas nativas. APK 121,635,503 bytes; SHA-256 `2ecbb8dcbb4e5101f29adb9a76582a412f4c87a58496997bdb8f4a643f8cf366`. `apksigner` v2 PASS; certificado Debug SHA-256 `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. ZIP CRC y zipalign PASS. `MODEL_PAYLOADS=NONE`; el único `.bin` es `DebugProbesKt.bin`, 1,728 bytes, identificado por el verificador como clase Kotlin debug-probe.

Drive contiene el paquete APK-only [LocalAIWorkspace-0.5.0-debug-arm64-APK-only.zip](https://drive.google.com/file/d/10K4tD-Gbo0bKGZ4jtgC4Dmfh79Z17oJI/view?usp=drivesdk), ID `10K4tD-Gbo0bKGZ4jtgC4Dmfh79Z17oJI`, 47,264,496 bytes. Contiene exactamente `local-ai-workspace-0.5.0-debug-arm64.apk`. SHA-256 local y tras lectura de vuelta desde Drive `3f098eca94460cd9bbc04678a21977da04fc88c43a9e95576e7d84970f181fc1`. Extrae el único APK del ZIP para instalarlo. El firmante coincide con el certificado Debug QA anterior; no desinstales la aplicación ni borres datos. Esto no equivale a instrumentación ni validación física.

### APK personal en Drive

APK Debug personal, no Release: `com.localai.workspace.debug`, `0.5.0-debug` / versionCode 27, ABI únicamente `arm64-v8a`, 9 bibliotecas nativas. APK 121,635,503 bytes; SHA-256 `2ecbb8dcbb4e5101f29adb9a76582a412f4c87a58496997bdb8f4a643f8cf366`. `apksigner` v2 PASS; certificado Debug SHA-256 `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. ZIP CRC y zipalign PASS. `MODEL_PAYLOADS=NONE`; el único `.bin` es `DebugProbesKt.bin`, 1,728 bytes, identificado y hasheado por el verificador como clase Kotlin debug-probe.

Drive contiene el paquete APK-only [LocalAIWorkspace-0.5.0-debug-arm64-APK-only.zip](https://drive.google.com/file/d/10K4tD-Gbo0bKGZ4jtgC4Dmfh79Z17oJI/view?usp=drivesdk), ID `10K4tD-Gbo0bKGZ4jtgC4Dmfh79Z17oJI`, 47,264,496 bytes. El ZIP incluye exactamente un archivo: `local-ai-workspace-0.5.0-debug-arm64.apk`. SHA-256 del ZIP `3f098eca94460cd9bbc04678a21977da04fc88c43a9e95576e7d84970f181fc1`; leí el archivo de vuelta desde Drive por rangos y el hash coincide con el local. Para instalar, extrae el único `.apk` del ZIP. La firma coincide con el certificado anterior de QA (`b0d1…12b0`); el usuario no debe desinstalar ni borrar datos. Esto no es validación física ni resultado del gate instrumentado.

Personal QA nunca equivale a instrumentación ni validación física.

## Backlog y referencias

Los archivos fuente no se cambiaron:
- BACKLOG SHA-256 BBB06425761F480A90F06EA8BB987235041F496FCC1065FB4936DB3D55DD0235 (46,100 bytes): 97 tareas, 97 IDs únicos, 120 dependencias, grafo acíclico, todos los estados fuente TODO.
- ROADMAP SHA-256 260590F125E988E92BAE284DF8366CEDA3A15C39C546E0BC78A4D21BBCBBD1C5 (47,285 bytes).

M00–M03 quedan congelados durante el cierre y transición H2→H3a→H3b. M01-06 sigue PENDING_REQUIRES_DEVICE. main y el tag v0.4.2 continúan en de615b772cf37a99e7e08a647c59607b271de606.
