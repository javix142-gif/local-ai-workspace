# Backlog operativo — AWS H0 → H1 → H2

Esta vista operativa no sustituye ni modifica el catálogo histórico de 97 IDs/120 dependencias ni sus estados fuente.

## Estado más reciente

- **H0/H1 instrumentado:** commit probado `01161491acc2a7c836222a84b7bd6f778292f8f3`; run Android [39511660788](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660788) PASS, 22 casos XML: 16 PASS, 0 FAIL/ERROR, 6 SKIPPED por ausencia de modelos reales. AVD API 35 x86_64 y ADB estuvieron conectados. H1-AT-12 `adb install -r` preservó la fixture sintética.
- **HOST:** el último gate completo es histórico, en SHA `aed4d8f6220e9d1e65e93f7d54a5bbd77e749755` (app 941/941, compatibilidad 2/2, lint y Kotlin AndroidTest PASS; `assembleDebug` up-to-date). En `0116149` solo se ejecutó localmente `compileDebugAndroidTestKotlin`, PASS; no afirmar un nuevo gate JVM completo.
- **APK QA ARM64:** run [39511660726](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660726) PASS en el mismo SHA. APK `com.localai.workspace.debug`, 0.5.0-debug/27, arm64-v8a, SHA `2dc3dd24992d2878099e67c2117bbc711fd1bce96ab610be4ba8a35ab7de6b6f`, certificado `b0d1c408…ab120b`. Está en [Drive](https://drive.google.com/file/d/1adOGxZnyyePC_CY68ZMWWBjy6mKjIWt9/view?usp=drivesdk) dentro de un ZIP que contiene solo el APK por el límite de carga de 100 MiB.
- **H1: `HOST/CI_VERIFIED`; DEVICE_PENDING:** el alcance HOST/CI está respaldado por el gate histórico, la verificación focal actual (25/25) y el run conectado del mismo SHA de código. AT-06/20/24, Motorola, XLSX original y revisión manual siguen pendientes; el XML de emulador no demuestra Room v1–7 ni ejecución con modelos reales.
- Los seis tests de inferencia con modelo real están SKIPPED, no aprobados. Motorola, workbook XLSX original y métricas nativas (TTFT, tokens/s, RAM/PSS, energía/temperatura/calidad) siguen NOT_RUN.

## Secuencia autorizada

1. H0 conserva la aceptación previa y su evidencia histórica; el resultado actual no modifica IDs ni DoD originales.
2. H1 cuenta como `HOST/CI_VERIFIED`; conservar AT-06/20/24 y la inspección Motorola como DEVICE_PENDING, sin convertir omisiones físicas en PASS.
3. **Siguiente: H2 — acciones Android, lectura local autorizada y transferencia explícita a herramientas.** La autorización del usuario permite empezar H2 secuencialmente con los bloqueos físicos de H1 separados.

## Límites y evidencia

- Mantener intactos los archivos fuente originales del backlog/roadmap y sus 97 IDs, 120 dependencias y estados TODO.
- `M01-06=PENDING_REQUIRES_DEVICE`; PHYSICAL_DEVICE=NOT_RUN. No sustituir Motorola o XLSX original con emulador/fixture.
- No desinstalar ni limpiar datos del usuario. El APK se probó con el certificado de desarrollo anterior en una fixture sintética; en el teléfono, si Android anuncia conflicto de paquete/firma, cancelar la instalación y no borrar la app existente.
- No modificar `EXECUTION_STATE.json` bajo la instrucción vigente.
- Antes de cualquier publicación oficial, sustituir la clave Debug/QA pública por una clave de firma protegida: **“Reemplazar el keystore de desarrollo por una clave segura antes de publicar”**. La clave actual nunca firma Release.
- Evidencia: [H1 RESULTS](../validation/master-program-h1-20261020/RESULTS.md), [instrumentación](../validation/master-program-h1-20261020/emulator-39511660788/), [QA ARM64](../validation/master-program-h1-20261020/qa-39511660726/).
