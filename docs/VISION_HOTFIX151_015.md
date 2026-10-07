# HOTFIX 1.5.1 — Local AI Workspace 0.1.15

Base: 0.1.14; actualización: versionCode 16, paquete `com.localai.workspace`.
Estado: correcciones de aplicación verificadas en Linux/Robolectric. **PHYSICAL_DEVICE_TEST_REQUIRED** para confirmar la percepción visual real de Gemma. No se declara cumplido el criterio físico de aceptación 2.

## A. Causa raíz y auditoría previa

La ruta original sí tenía transporte multimodal: `ChatViewModel.send()` tomaba `_attachedImagePath`, construía `GenerationRequest.imagePath`, `LiteRtLmInferenceRuntime.generate()` lo enviaba como `image` por Messenger y `LiteRtLmService.generate()` creaba `Contents.of(Content.ImageFile(...), Content.Text(...))`. El SDK vendorizado 0.17.1 serializa ImageFile con `type=image` y `path`. La creación de Engine utilizaba `visionBackend=Backend.CPU(...)` cuando `enableVision=true`. No se encontró una transformación de la etapa 1.5 que eliminara incondicionalmente la imagen del primer request.

Por ello, la causa exacta de que Gemma negara ver la PRIMERA imagen en el Motorola es **NO DETERMINADO**. Una respuesta del modelo no prueba que faltara el contenido visual. Este hotfix no atribuye a la UI un descarte que el código no demuestra, ni afirma éxito nativo sin dispositivo.

Defectos comprobados:

1. La imagen se consultaba desde estado mutable después de esperas de preparación/cola, sin propiedad explícita por turno. Nunca se consumía después del envío: quedaba en el compositor y podía reenviarse en mensajes posteriores.
2. Room y `ChatMessage` carecían de referencia visual. `initialMessages` reconstruía historial exclusivamente textual. Además, la continuación nativa se deshabilitaba para solicitudes con visión. Un turno posterior podía perder su contexto visual al reconstruirse.
3. La validación del adjunto sólo comprobaba la declaración del modelo, no el resultado de capacidades del runtime cargado ni la presencia de un engine visual antes del envío nativo.
4. La conversión a JPEG ignoraba la orientación EXIF. No se modificaron resolución máxima, muestreo ni calidad JPEG.

## B. Archivos modificados

Todas las rutas siguientes son relativas a `/workspace/local-ai-workspace`.

| Archivo | Cambio |
|---|---|
| `app/src/main/java/com/localai/workspace/ui/ViewModels.kt` | Captura/transferencia del adjunto, rechazo previo, comprobación del runtime cargado, reintento explícito con la referencia original y estado de diagnóstico visual. `send()` devuelve aceptación; espera importaciones pendientes antes de fijar el turno. |
| `app/src/main/java/com/localai/workspace/MainActivity.kt` | Thumbnail en compositor y mensaje USER, X, conservar entrada si send rechaza, reintento con imagen original, estado visual dentro de Diagnostics. |
| `app/src/main/java/com/localai/workspace/ui/ImageAttachment.kt` | Thumbnail en IO con decoding por bounds, submuestreo a un máximo de 512 píxeles; límite visual 56 dp en compositor, 240×180 dp en mensaje. Sin segunda copia del JPEG. |
| `app/src/main/java/com/localai/workspace/data/Entities.kt` | Referencia nullable `MessageEntity.imagePath`. |
| `app/src/main/java/com/localai/workspace/data/WorkspaceDatabase.kt` | Room 5; migración aditiva 4→5 añade `imagePath TEXT`; se mantiene cadena 1→2→3→4→5, sin fallback destructivo. |
| `app/src/main/java/com/localai/workspace/data/Daos.kt` | Consultas de referencias visuales para eliminación segura. |
| `app/src/main/java/com/localai/workspace/data/WorkspaceRepository.kt` | Imágenes de mensajes eliminados entran en el outbox existente; limpieza sólo en raíces privadas documents/attachments, sin borrar archivos todavía referenciados. |
| `app/src/main/java/com/localai/workspace/data/ChatHistoryBuilder.kt` | Referencia visual en turnos efectivos del mismo modelo. |
| `app/src/main/java/com/localai/workspace/data/ImagePreprocessor.kt` | Aplicar orientación EXIF al JPEG preparado; eliminar salida parcial si la preparación falla. Conserva límite 2048 y JPEG 90. |
| `app/src/main/java/com/localai/workspace/domain/model/ConversationPrompt.kt` | Campo opcional de imagen por turno histórico. |
| `app/src/main/java/com/localai/workspace/domain/model/WorkspaceModels.kt` | Indicador `ModelLoadConfig.nextHasImage`, separado de engine visual necesario por historial. |
| `app/src/main/java/com/localai/workspace/inference/LiteRtConversationPayload.kt` | Referencias de historial por IPC; `initialMessages` multimodales utilizando API oficial. |
| `app/src/main/java/com/localai/workspace/inference/LiteRtVisionInput.kt` | Construcción comprobable imagen+texto; exige archivo privado legible y engine visual. Nunca devuelve sólo texto ante fallo de imagen. |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmInferenceRuntime.kt` | Propaga `nextHasImage` sin cambiar backend/sampling. |
| `app/src/main/java/com/localai/workspace/inference/LiteRtLmService.kt` | Validación de referencias históricas y actuales, guard de engine visual, dimensiones decodificables, diagnóstico metadata-only, `MODALITY_CHANGED` y continuidad textual segura de una conversación visual. Reinicio del campo histórico nuevo en la conversación temporal ya existente. |
| `app/src/main/java/com/localai/workspace/inference/LiteRtConversationReuse.kt` | El tracker registra referencia visual como parte del turno efectivo completado; mantiene igualdad exacta, aislamiento e invalidación existentes. |
| `app/src/test/java/com/localai/workspace/ui/ChatSessionPipelineTest.kt` | Tests reales de VM/Room/request con frontera nativa controlada; fake distingue capacidad/fallo visual. Teardown espera scopes de ViewModels antes de resetMain. |
| `app/src/test/java/com/localai/workspace/ui/ChatContentUiTest.kt` | Adjuntar visualmente y quitar con X. |
| `app/src/test/java/com/localai/workspace/inference/LiteRtVisionInputTest.kt` | Contenido SDK del mismo turno, ausencia en texto, fallos sin fallback, tracker visual/aislamiento. |
| `app/src/test/java/com/localai/workspace/inference/LiteRtVisionPayloadTest.kt` | Referencia visual sobrevive IPC y configuración de historial SDK. |
| `app/src/test/java/com/localai/workspace/data/VisionPersistenceTest.kt` | Reapertura de Room, referencia persistida, limpieza del adjunto y orientación EXIF. |
| `app/src/test/java/com/localai/workspace/data/WorkspaceMigration3Test.kt` | Fixture realmente antiguo sin campos nuevos; migración completa hasta versión 5 y preservación de datos. |
| `app/src/test/java/com/localai/workspace/data/ChatDeletionTest.kt` | Aserción de versión actual; mismas comprobaciones de eliminación/aislamiento. |
| `app/build.gradle.kts` | Versión 0.1.15 / code 16. Sin nuevas dependencias. |

## C. Flujo final

Image picker / archivo reconocido como imagen
→ URI temporal de SAF
→ ImagePreprocessor: bounds, submuestreo, EXIF, JPEG 90 en `filesDir/attachments`
→ draft privado mostrado en compositor
→ send aceptado, importaciones pendientes terminan, referencia fijada
→ MessageEntity USER con imagePath en Room; draft consumido sin borrar archivo
→ GenerationRequest: imagen y texto enriquecido del MISMO turno
→ IPC Messenger (`image` y `prompt`)
→ LiteRtLmService comprueba engine visual y archivo privado/decodificable
→ `Message.user(Contents.of(Content.ImageFile, Content.Text))`
→ `Conversation.sendMessageAsync`
→ Gemma / callbacks de texto.

No se transforma un fallo visual en una solicitud sólo-texto. Las capacidades del bundle siguen verificándose en el worker. Visión permanece CPU; backend de texto y parámetros existentes no se alteran.

## D. Ciclo de vida del adjunto

ANTES: draft retenido después de enviar, no asociado en Room al USER, podía viajar de nuevo en otro turno y borrarse al limpiar/reemplazar el draft.

AHORA: draft pertenece al próximo mensaje. X borra únicamente el draft no enviado. Después de insertar el USER durable, ese mensaje pasa a ser dueño del mismo archivo y el compositor se limpia. Rechazo síncrono conserva texto y adjunto; fallo previo a insertar USER conserva adjunto. Un fallo posterior deja imagen y error visibles en el turno aceptado. Retry explícito conserva la referencia del mensaje original; un mensaje normal posterior tiene `GenerationRequest.imagePath=null`.

Room/archivo privado permiten reabrir el chat y reiniciar el proceso sin perder el thumbnail de un mensaje enviado. No se duplica el archivo grande. Borrado de chats/proyectos usa outbox persistente; se respetan referencias restantes. Una muerte de proceso durante preparación de un draft todavía no enviado puede dejar un JPEG huérfano: no se introduce un recolector general en este hotfix.

## E. Impacto en conversación

Texto→visión requiere engine compatible: el recurso existente cierra/reemplaza el anterior; no se crean engines paralelos. Motivo observado: `MODALITY_CHANGED`. Un engine ya visual puede seguir atendiendo texto.

La referencia visual forma parte de la igualdad exacta del turno efectivo. Un siguiente turno sólo-texto puede continuar la conversación visual si coinciden chat/modelo/config/historial y pasa el control existente basado en `getTokenCount()` y reserva conservadora. No se cambia la fórmula de capacidad ni se inventa un conteo de tokens de imagen. Una imagen NUEVA fuerza reconstrucción segura: no se intenta estimar su coste usando longitud textual.

Si la conversación se pierde, se trunca historial o cambia configuración, la reconstrucción puede recuperar imágenes en sus turnos HISTÓRICOS. Esto es reproducción del historial, no una imagen adjunta al nuevo USER. Si el archivo no existe o no cabe en el contexto nativo, el error se muestra; no se descarta silenciosamente para seguir como texto.

Las validaciones nativas y la memoria real de imagen requieren Motorola. Los tests de tracker/engine/pool no equivalen a comprobar KV visual físico.

## F. Verificación

Comando completo desde el proyecto:

```bash
JAVA_HOME=/workspace/.toolchain/jdk-17 \
ANDROID_HOME=/workspace/.toolchain/android-sdk \
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest \
  :app:lintDebug -Parm64Only=true --max-workers=2 --console=plain
```

Resultados finales y evidencia: `docs/validation/vision-hotfix151/verified.log`, `artifact.json`, `native-comparison.json`, `signature.txt`, `badging.txt`. Las pasadas anteriores y sus fallos de fixtures/cierre de dispatcher se conservan en el mismo directorio; no se presenta que todas las pasadas hayan pasado.

- App: 232 tests, 0 fallos, 0 errores, 0 omitidos.
- LiteRT compat: 2 tests, 0 fallos, 0 errores, 0 omitidos.
- Debug, release e APK de instrumentación compilados. Comando completo: BUILD SUCCESSFUL.
- Lint: 0 errores; 77 advertencias existentes/recomendaciones no bloqueantes entre app y compat.
- El primer lint señaló ProduceStateDoesNotAssignValue; se cambió el thumbnail a remember + LaunchedEffect y se repitieron todos los checks. Los tests Compose son Robolectric; instrumentación compilada **NO ejecutada**. `adb devices -l` no muestra un dispositivo.
- El certificado coincide con 0.1.14. Las ocho bibliotecas nativas son idénticas; no se modifica SDK/JNI/modelo.
- No se ejecutó inferencia visual nativa sobre Linux.

## G. Limitaciones restantes

Una imagen por solicitud. Sin OCR, vídeo, audio o múltiples imágenes adjuntas. Los mensajes anteriores a 0.1.15 sin referencia visual no pueden recuperar retroactivamente su thumbnail; no se inventa una asociación con archivos viejos. Un historial reconstruido depende de que el archivo privado siga disponible y que el contexto nativo admita su coste. No hay nueva estimación de tokens visuales. Percepción de Gemma, continuidad nativa visual y la causa de su negación original: **PHYSICAL_DEVICE_TEST_REQUIRED / NO DETERMINADO** hasta probar.

El diagnóstico muestra presencia/preparación/backend CPU/dimensiones/bytes después de validar el contenido en worker. Eso prueba el contrato de entrada, **no** que Gemma haya entendido correctamente la imagen. No incluye ruta completa ni contenido privado en logs. EXIF usa API Android existente; no se incorporan dependencias.

## H. Checklist Motorola Moto G86 Power

Instalar el APK 0.1.15 sobre la app existente, SIN desinstalar. Se conserva firma/paquete y se migra Room aditivamente. No cambiar CPU, sampling, GPU/speculative o warm-up para esta prueba.

A. Abrir Gemma 4 E2B; elegir una foto clara de un objeto con color reconocible; comprobar preview y X; enviar «¿Qué aparece en esta imagen?». El USER debe mostrar thumbnail, el composer quedar limpio y Gemma identificar la imagen real. Menú ⋮ → Diagnostics: verificar entrada presente, CPU, dimensiones/bytes; guardar Details del turno. Si sigue negando visión, el criterio físico NO pasa.

B. Enviar «¿De qué color era el objeto?», sin volver a adjuntar. Comprobar compositor limpio, nuevo USER sin thumbnail nuevo y respuesta contextual. Revisar Details: sesión reutilizada o motivo de reconstrucción real. No confundir reconstrucción de historial visual con nueva imagen adjunta.

C. Crear chat nuevo; enviar «Hola». No debe heredar thumbnail, draft ni contexto del chat anterior.

D. Adjuntar foto, quitarla con X, enviar texto. No debe haber imagen en el USER ni entrada visual actual en Diagnostics.

E. Cambiar a Qwen/modelo sin Vision. Image debe estar ausente del menú disponible; si se elige una imagen por File, debe rechazarse con aviso antes de inferencia. Si se cambia de modelo con draft existente, send debe bloquearse; no debe convertirse a texto silenciosamente.

Adicionales: volver al Workspace mientras genera y regresar; cerrar/reabrir app con turno terminado y verificar thumbnail; probar foto de cámara vertical (EXIF); repetir Retry tras error con archivo original; borrar chat de prueba y comprobar que desaparece sin afectar otros chats/modelos/documentos.

No considerar cerrada la incidencia física hasta que A–E pasen. Si A falla, conservar screenshot de Diagnostics/Details y versión/hash del bundle: no extrapolar una negación textual del modelo a una pérdida de IPC sin evidencia.
