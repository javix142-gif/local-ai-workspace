# Backlog operativo — AWS H0 → H1 → H2

Esta vista operativa no sustituye ni modifica el catálogo histórico de 97 IDs/120 dependencias ni sus estados fuente.

## Estado más reciente

- **H2 — implementación lista para publicar; HOST focalizado:** añade `calendar.read` y `contacts.lookup` acotados, permisos runtime solicitados desde una acción explícita en el chat, historial de herramienta sin argumentos ni valores personales, routing determinista para intenciones compatibles y descripciones de herramientas más compactas. El detalle de H2 y sus limitaciones queda en [STATUS](STATUS.md) y [handoff](CODEX_TO_WORK_HANDOFF.md).
- **HOST de H2:** 19 pruebas focales únicas PASS; lint y compilación Kotlin de AndroidTest PASS (0 errores de lint, 128 avisos, 1 información); compatibilidad LiteRT 2/2. La única suite completa de app ejecutada encontró un fallo de descripción DOCX, corregido y verificado en su clase 4/4; no se repitió toda la suite, por lo que el gate global final permanece sin aceptación.
- No se ejecutaron instrumentación, emulador ni Motorola para H2. La construcción personal QA solo se considera validada cuando el workflow asociado al SHA exacto finalice y se inspeccione su artifact.

- **H0/H1 instrumentado:** commit probado `01161491acc2a7c836222a84b7bd6f778292f8f3`; run Android [39511660788](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660788) PASS, 22 casos XML: 16 PASS, 0 FAIL/ERROR, 6 SKIPPED por ausencia de modelos reales. AVD API 35 x86_64 y ADB estuvieron conectados. H1-AT-12 `adb install -r` preservó la fixture sintética.
- **HOST:** el último gate completo es histórico, en SHA `aed4d8f6220e9d1e65e93f7d54a5bbd77e749755` (app 941/941, compatibilidad 2/2, lint y Kotlin AndroidTest PASS; `assembleDebug` up-to-date). En `0116149` solo se ejecutó localmente `compileDebugAndroidTestKotlin`, PASS; no afirmar un nuevo gate JVM completo.
- **APK QA ARM64:** run [39511660726](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39511660726) PASS en el mismo SHA. APK `com.localai.workspace.debug`, 0.5.0-debug/27, arm64-v8a, SHA `2dc3dd24992d2878099e67c2117bbc711fd1bce96ab610be4ba8a35ab7de6b6f`, certificado `b0d1c408…ab120b`. Está en [Drive](https://drive.google.com/file/d/1adOGxZnyyePC_CY68ZMWWBjy6mKjIWt9/view?usp=drivesdk) dentro de un ZIP que contiene solo el APK por el límite de carga de 100 MiB.
- **H1: `HOST/CI_VERIFIED`; DEVICE_PENDING:** el alcance HOST/CI está respaldado por el gate histórico, la verificación focal actual (25/25) y el run conectado del mismo SHA de código. AT-06/20/24, Motorola, XLSX original y revisión manual siguen pendientes; el XML de emulador no demuestra Room v1–7 ni ejecución con modelos reales.
- Los seis tests de inferencia con modelo real están SKIPPED, no aprobados. Motorola, workbook XLSX original y métricas nativas (TTFT, tokens/s, RAM/PSS, energía/temperatura/calidad) siguen NOT_RUN.

## Secuencia autorizada

1. H0 conserva la aceptación previa y su evidencia histórica; el resultado actual no modifica IDs ni DoD originales.
2. H1 cuenta como `HOST/CI_VERIFIED`; conservar AT-06/20/24 y la inspección Motorola como DEVICE_PENDING, sin convertir omisiones físicas en PASS.
3. **H2 actual:** implementación focalizada de lecturas locales y enrutamiento de acciones Android; auditoría automática del SHA publicado pendiente. No equivale a validación física ni a aceptación del gate HOST completo.

## Límites y evidencia

- Mantener intactos los archivos fuente originales del backlog/roadmap y sus 97 IDs, 120 dependencias y estados TODO.
- `M01-06=PENDING_REQUIRES_DEVICE`; PHYSICAL_DEVICE=NOT_RUN. No sustituir Motorola o XLSX original con emulador/fixture.
- No desinstalar ni limpiar datos del usuario. El APK se probó con el certificado de desarrollo anterior en una fixture sintética; en el teléfono, si Android anuncia conflicto de paquete/firma, cancelar la instalación y no borrar la app existente.
- No modificar `EXECUTION_STATE.json` bajo la instrucción vigente.
- Antes de cualquier publicación oficial, sustituir la clave Debug/QA pública por una clave de firma protegida: **“Reemplazar el keystore de desarrollo por una clave segura antes de publicar”**. La clave actual nunca firma Release.
- Evidencia: [H1 RESULTS](../validation/master-program-h1-20261020/RESULTS.md), [instrumentación](../validation/master-program-h1-20261020/emulator-39511660788/), [QA ARM64](../validation/master-program-h1-20261020/qa-39511660726/).
