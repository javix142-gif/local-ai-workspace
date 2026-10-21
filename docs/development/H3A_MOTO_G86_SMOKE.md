# H3a — guía de smoke test local para Moto G86 Power

Estado: `DEVICE_PENDING`. Este documento prepara la validación; no afirma que el Motorola se haya conectado ni que se ejecutaran pruebas físicas.

## Requisitos y salida

- Windows con PowerShell y Android Platform Tools (`adb`) disponibles en `PATH`, `ANDROID_HOME` o `ANDROID_SDK_ROOT`.
- Conecta y autoriza **un solo** dispositivo. El script exige exactamente un dispositivo en estado `device` y se detiene si el paquete `com.localai.workspace.debug` no está instalado.
- Ejecuta desde una copia local del repositorio:

```powershell
.\scripts\device\smoke_moto.ps1
```

El resultado se crea por defecto bajo `%LOCALAPPDATA%\LocalAiWorkspace\device-smoke\<UTC timestamp>`, fuera del repositorio. Una ruta `-OutputDirectory` también debe quedar fuera del repositorio y vacía. El script registra modelo/Android/ABI, versión del paquete, estado de proceso, permisos relevantes, PSS, una lectura térmica disponible y logcat limitado a tags estructurales de Local AI. El log se reduce a eventos/campos permitidos; no se guarda texto de mensajes, respuestas, nombres de contacto ni rutas de archivos. El serial se enmascara.

Por defecto el script es de solo lectura: no instala ni desinstala, no limpia datos, no cambia permisos, no envía mensajes, no crea alarmas/eventos, no descarga modelos y no abre la app. `-OpenApp` solo solicita abrir la pantalla principal. Para una captura manual explícita, navega primero en el teléfono y ejecuta:

```powershell
.\scripts\device\smoke_moto.ps1 -OpenApp -CaptureConversationEvidence
```

El script pedirá confirmación antes de capturar la pantalla completa. La PNG se guarda en `private-evidence/`, no entra en `SHA256SUMS` y puede contener datos personales; no compartas esa carpeta sin revisar y redactar la imagen. Los demás archivos tienen hashes en `SHA256SUMS`. Si ADB, el dispositivo, el paquete o un comando fallan, el proceso devuelve error y conserva el motivo sin cambiar el teléfono.

## Secuencia manual

1. Comprueba modelo, versión Android, ABI, aplicación instalada, `versionCode` y permisos en `summary.json`/`permissions.json`. Ante un identificador distinto de Moto G86 Power, anota el bloqueo y no presentes el resultado como validación de ese modelo.
2. Abre la app y comprueba carga del modelo **Gemma E2B real** seleccionado. Usa un prompt inocuo; registra por separado inicio frío, primer contenido visible, tiempo total y sesión tibia. No copies prompts/respuestas personales al informe compartible. Si no existe el modelo, no descargues pesos como parte del smoke script.
3. Crea una memoria de prueba solo si lo deseas, confirma su guardado y comprueba su uso en una conversación nueva. El script no crea memorias.
4. Importa un documento de prueba no sensible y pide un dato verificable con página/hoja/celda cuando aplique. No uses una fixture sintética como sustituto del XLSX privado requerido por un gate original.
5. Revisa las propuestas de alarma, temporizador o evento sin aceptar acciones que no quieras realizar. El handoff de la app debe conservar la confirmación del usuario. Solo continúa a la app externa y confirma si realmente deseas crear esa acción.
6. Toma snapshots separados de PSS/estado térmico antes y después de los turnos si las condiciones lo permiten. Usa los diagnósticos locales existentes para etapas de inferencia. Registra temperatura/condiciones y duración; no atribuyas datos del emulador o del host al Motorola.
7. Revisa los archivos locales antes de compartir. No incluyas la captura privada sin consentimiento y redacción; no compartas contenido de conversaciones, contactos, documentos, serial completo ni datos personales.

La instrumentación no automatiza inferencia ni acciones del usuario; la latencia manual no equivale a un benchmark controlado. Para comparar rendimiento se deben mantener constantes dispositivo, modelo, corpus, contexto y preguntas, y repetir cold/warm de forma documentada.

## H3b visual

No se modifica la interfaz hasta recibir las nueve referencias visuales enumeradas en el contrato de continuidad. La guía visual existente es referencia de diseño, no sustituto de esas imágenes.
