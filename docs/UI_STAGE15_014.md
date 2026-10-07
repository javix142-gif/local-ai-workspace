# Local AI Workspace — etapa 1.5 UX/UI (0.1.14)

Fecha: 2026-10-06. Base: 0.1.13. Entrega: 0.1.14 / versionCode 15 / ARM64.

Se simplificó la interfaz conservando navegación, tema, inferencia y datos. No hay cambios de runtime, sampling, warm-up, KV, GPU, speculative, benchmarks, RAG, memoria persistente ni esquema Room. **PHYSICAL_DEVICE_TEST_REQUIRED** para teclado real, gestos, fuentes/configuración del Motorola y adjuntos SAF.

## A. Archivos modificados

Rutas relativas a `/workspace/local-ai-workspace`:

| Archivo | Cambio |
|---|---|
| `app/src/main/java/com/localai/workspace/MainActivity.kt` | Simplificación de pantallas, menús, tabs de proyecto, diálogo de privacidad, detalles de archivos y acciones de respuesta |
| `app/src/main/java/com/localai/workspace/ui/ChatContent.kt` | Compositor único, menú +, slot contextual vacío y renderer visual de respuestas/código |
| `app/src/main/java/com/localai/workspace/ui/BasicMarkdown.kt` | Parser acotado de párrafos/listas/negrita/código; sin HTML ni recursos remotos |
| `app/src/main/java/com/localai/workspace/ui/UiPresentation.kt` | Nombres humanos conservadores, etiquetas de estado breves y clasificación de disponibilidad de capacidades |
| `app/build.gradle.kts` | Versión 0.1.14 y dependencias oficiales de test Compose; runtime de producción sin nuevas dependencias |
| `app/src/test/java/com/localai/workspace/ui/BasicMarkdownTest.kt` | Gramática, streaming parcial, escapes, código literal y contenido remoto no ejecutable |
| `app/src/test/java/com/localai/workspace/ui/UiPresentationTest.kt` | Nombre/estado/disponibilidad; audio declarado no se presenta como función disponible |
| `app/src/test/java/com/localai/workspace/ui/ChatContentUiTest.kt` | Código copiable, compositor con texto largo/fuente ampliada, stop y tarjeta de modelo |
| `app/src/test/java/com/localai/workspace/ui/UiSmokeTest.kt` | Navegación, Home, Chat, Library/Add Model, Settings/Diagnostics, tabs y retirada de memoria aprobada |
| `app/src/androidTest/java/com/localai/workspace/ui/ProjectUiDeviceTest.kt` | Flujo de creación/edición de memoria con modales en Android; compilado, no ejecutado aquí |

Documentación adicional: este informe, changelog, manifiesto de APK y evidencias en `docs/validation/ui-stage15/`.

Antes de editar se preservaron 106 fuentes/configuraciones de app. Sólo dos de esos archivos existentes cambiaron: MainActivity y app/build.gradle.kts; los otros **104 permanecen idénticos por SHA-256**, incluyendo ViewModels, Theme, AppGraph, inferencia, modelos, Room, RAG, preparación y diagnóstico de rendimiento. Los ocho `.so` del APK son idénticos a 0.1.13. No se eliminaron fuentes ni se añadió una migración de datos.

Inventario y diff: `validation/ui-stage15/source-changes.json`, `source.patch`, `source-before.json` y `source-before.tar.gz`. Metadatos del APK: `validation/apk-014.json`.

## B. Cambios por pantalla

### Workspace

- Conserva título y navegación inferior Workspace/Models/Settings.
- Privacidad pasa de banner a una línea táctil `Private · Local`; abre explicación en diálogo.
- Start chat y Start project aparecen antes del modelo y del contenido reciente.
- Modelo activo compacto: nombre humano, estado breve y acceso a selector/detalles/cancelación/reintento.
- Chats y proyectos muestran título, icono y menú de eliminación; sin metadata técnica repetida.
- Búsqueda opcional por título, activada desde el icono de búsqueda; no es semántica ni cambia datos.
- Actividades en curso mantienen acceso al chat y Stop, con estados compactos reales.

### Chat

```text
←  Nombre del chat                           ⋮
   Gemma 4 E2B · Local · Ready

   conversación / respuestas / código

   [adjuntos seleccionados, sólo si existen]
+  Ask anything…                           Send
```

- El título/modelo/estado comparten una cabecera. El área del título es táctil, con altura mínima 48 dp, y abre el selector.
- El menú ⋮ conserva Model, Files, Memory, Diagnostics y Delete chat. No hay selector horizontal ni fila Model ready/Diagnostics permanente.
- Adjuntos unificados en +: File; Image sólo cuando existe el camino de visión de la app para ese modelo; Workspace files cuando ya hay documentos.
- Selección de imagen/archivos y lectura en curso aparecen de forma contextual junto al compositor, no como paneles permanentes encima del chat.
- Estados técnicos se presentan como Loading…, Warming…, Processing…, Generating… o Stopping…; las fases/detalles originales siguen dentro de Diagnostics.
- Compositor con `imePadding`, texto de hasta cinco líneas, objetivos táctiles de IconButton y Send/Stop. El IME físico queda pendiente de validación.

### Respuestas

- Párrafos, listas, headings sencillos, negrita, código inline y bloques fenced.
- Código con tipografía monoespaciada, superficie diferenciada, lenguaje reconocido y Copy code. Líneas largas pueden desplazarse horizontalmente.
- Código y texto no se ejecutan. No se cargan imágenes remotas ni HTML.
- Se retiraron etiquetas repetidas You/Local assistant. Se mantiene diferenciación por alineación/superficie.
- Al terminar: Copy y menú ⋮ discreto. Details permanece accesible; Continue sólo cuando el límite de salida lo justifica; Retry sólo para el último intento fallido/cancelado.
- Retry utiliza el `send()` existente y añade otro intento; no borra ni reemplaza respuestas anteriores. No se añadió regeneración completa.
- Se mantiene advertencia de posible respuesta incompleta y acceso a evidencia/citas.

### Projects

- Cuatro tabs funcionales: Chats, Files, Memory y Settings. No Tools.
- Chats: creación/listado/eliminación existentes.
- Files: importación/listado; detalles de estado de extracción/indexación, tamaño, MIME y error al tocar el archivo. Un fallo de indexación no se presenta como Ready.
- Memory: misma pantalla/VM de memoria aprobada, integrada como panel del proyecto.
- Settings: configuración actual de modelo, memoria e instrucciones. Es consulta de configuración; no se inventó un editor nuevo de instrucciones o controles sin backend.
- Eliminación del proyecto en menú y confirmación existente; tabs y memoria dejan de ser operables mientras se elimina.

### Memory

- Estado vacío breve; la explicación de aprobación está en About memory.
- Tarjetas compactas con contenido y Edit/Remove. Global sólo se muestra si realmente es memoria global; no se muestran IDs/sourceType internos.
- Remove pide confirmación y utiliza el archivo lógico existente (`archive()`); el item deja de usarse en nuevos requests. No se cambió la política de aprobación ni se implementó borrado físico nuevo.
- El editor está limitado visualmente a seis líneas con scroll interno.

### Models / Add Model

- Mantiene filtros, orden, importación, descarga y compatibilidad.
- Deriva nombres humanos sólo con patrones fiables: Gemma 4 E2B y Qwen 3.5 2B. Los nombres desconocidos/personalizados se conservan. IDs, filenames y registros no se renombran.
- Tarjetas: nombre, runtime, tamaño y funciones principales disponibles. Filename discreto cuando aporta información.
- No muestra Audio ✓. En Model Details se distingue Available in app / Supported by model, unavailable in app / Experimental / Unavailable.
- “Available” describe el camino implementado en la app y la declaración del modelo compatible; no garantiza calidad ni compatibilidad física de un bundle/dispositivo. La activación efectiva del engine continúa en Diagnostics.
- Add Model agrupa FROM DEVICE: GGUF, LiteRT-LM, Multimodal; ONLINE: Hugging Face. Todas las opciones y callbacks anteriores permanecen.
- El diálogo Hugging Face puede desplazarse; se conserva token en Keystore, cancelación, búsqueda y selección de archivos.

### Settings / Diagnostics

- Settings compacto: privacidad, entrada Developer / Diagnostics y explicación opcional de capacidades.
- Ruta: Settings → Developer / Diagnostics → Local AI Performance.
- Desde Chat: ⋮ → Diagnostics; acceso a fase técnica, detalles de preparación, smoke test y Performance.
- `PerformanceScreen.kt` no se modificó. Se conservan todas las métricas/acciones/matriz de Parte 1.

## C. Elementos eliminados o compactados

| Antes | Ahora |
|---|---|
| Banner de privacidad permanente | Línea táctil + diálogo |
| Tarjeta extensa Active model | Resumen compacto + selector/detalles |
| Model picker horizontal en cada chat | Selector bajo título/menú |
| Fila Ready + Diagnostics | Estado en subtítulo y Diagnostics en menú |
| Dos botones permanentes de adjuntos | Un + con fuentes funcionales |
| Explicación de adjuntos sin selección | Indicadores sólo con selección/lectura |
| Etiquetas de rol en cada burbuja | Alineación/superficie |
| Details/Continue permanentes | Acciones contextuales en menú |
| Metadata de memoria interna | Contenido; Global cuando importa |
| Capacidades Audio ✓ en tarjeta | Funciones realmente disponibles; declaración en Details |
| Banners/explicaciones de Settings/Home | Información bajo demanda |

No se retiraron confirmaciones destructivas ni información técnica útil; permanecen en diálogos, menús y Diagnostics.

## D. Preparación para Parte 2

- `ChatComposer.contextualControls` es un slot opcional vacío: permite chips funcionales futuros sin reservar espacio ahora.
- Menú de adjuntos admite fuentes sólo cuando se provee una acción real.
- ChatRichText y las superficies de código ofrecen una separación de contenido/presentación reutilizable.
- Cards, acciones secundarias y AlertDialog existentes sirven como patrón para futuros estados compactos y confirmaciones de acciones sensibles.
- No se añadieron ejemplos falsos de tools ni una jerarquía nueva de permisos/ejecución sin backend.

## E. No implementado deliberadamente

Tool calling, Thinking como función, web search, Python/ejecución de código, audio, embeddings/RAG real, memoria semántica, nuevos parsers, entrenamiento, NPU ni nuevos modelos. Tampoco un renderer Markdown completo: tablas, HTML y recursos remotos quedan fuera. No nuevas paletas, logos, temas, efectos ni animaciones propias.

Theme.kt permanece igual: se conserva la política de colores dinámicos/tema del sistema. Código usa Monospace; cuerpo/controles siguen Material3 y las preferencias del sistema. No había una fuente serif explícita en el código actual que debiera retirarse.

## F. Tests y builds

Comprobación UI focalizada: 19 tests PASS. Incluye 7 de parser, 4 de presentación, 3 de componentes y 5 de navegación/pantallas.

Comando completo final:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk ./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug -Parm64Only=true -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 --max-workers=2 --console=plain
```

| Check | Resultado |
|---|---|
| App JVM | **221 tests, 0 fallos, 0 errores, 0 skipped** |
| Compat JVM | 2 resultados PASS existentes; tarea UP-TO-DATE |
| Debug | PASS |
| Release | PASS |
| APK de tests instrumentados | Compilado PASS; **NO EJECUTADO** |
| Lint | 0 errores; 75 warnings app + 1 compat |
| Firma/alineación/paquete | PASS; mismo certificado/paquete que 0.1.13 |
| Native .so | Ocho idénticos a 0.1.13 |

BUILD SUCCESSFUL en 4m 45s. Log: `validation/ui-stage15/full-checks.log`; XMLs y conteos preservados en la misma carpeta.

La primera descarga de la nueva dependencia de test falló porque Gradle no utilizaba el proxy del entorno; se corrigió la invocación. Hubo fallos iniciales de selectores (GGUF duplicado en filtro/modal y Back de popup) que se corrigieron. Robolectric no estabilizó los campos de texto modales de creación/memoria, incluso probando reloj manual/graphics legacy. Ese flujo se trasladó a una prueba instrumentada compilable. No se ocultó como un test físico aprobado ni se modificó inferencia para resolverlo.

El test host de Project usa un proyecto fixture y memoria explícitamente USER_APPROVED para comprobar tabs y retirada. No afirma validar la escritura con IME. El test instrumentado crea su propio proyecto/item y selecciona ese item por ID; evita editar/eliminar memoria global existente.

Los warnings nuevos son recomendaciones de catálogo de versiones y estado Int de Compose; no errores de compilación. No se presentan como cero warnings.

## G. Capturas y resultado visual

Capturas **reales del renderer Compose en Robolectric/API 29**, 360×800, modo nocturno, sin dispositivo conectado. No son capturas del Motorola ni evidencia de inferencia Gemma. La paleta del host difiere del color dinámico celeste del teléfono; Theme.kt no cambió.

- [Workspace](validation/ui-stage15/screenshots/workspace.png)
- [Chat](validation/ui-stage15/screenshots/chat.png)
- [Models sin modelos importados](validation/ui-stage15/screenshots/models.png)
- [Project Settings](validation/ui-stage15/screenshots/project-settings.png)

La captura Chat muestra un borrador de test, no una respuesta generada. Nombres/capacidades de Gemma y copia/formato se verificaron además con fixtures en tests de componentes. No se fabricaron capturas de conversación o tools.

## H. Riesgos y pendientes

**PHYSICAL_DEVICE_TEST_REQUIRED**:

1. Instalar como actualización, sin desinstalar; comprobar modelos, chats/proyectos, documentos, memoria y preferencias.
2. Abrir un chat con Gemma: comprobar cabecera, selector al tocar título, menú, teclado y Send/Stop. Volver al Workspace durante generación y reabrirlo.
3. Pedir una respuesta con listas, negrita y código; comprobar código largo, scroll vertical/horizontal, Copy y Details.
4. Probar + → imagen/archivo y Workspace files; comprobar selección, retirada y estados durante importación.
5. Crear un proyecto; cambiar tabs; añadir/editar/quitar memoria aprobada; revisar Settings. Comprobar memoria global existente antes/después.
6. Probar títulos largos, listas extensas, búsqueda, texto/fuente ampliada y campos de modales con teclado abierto.
7. Abrir Settings → Developer / Diagnostics → Local AI Performance; comprobar métricas/benchmark/perfiles anteriores sin ejecutarlos automáticamente.

Para ejecutar únicamente el nuevo test de Android con un dispositivo de desarrollo:

```sh
./gradlew :app:connectedDebugAndroidTest -Parm64Only=true -Pandroid.testInstrumentationRunnerArguments.class=com.localai.workspace.ui.ProjectUiDeviceTest
```

Ese comando instala el paquete debug y requiere dispositivo; no se ejecutó aquí. La actualización release conserva datos, pero la instalación termina el proceso Android: los pesos se volverán a preparar según el lifecycle existente.

Pendientes conocidos: editor de instrucciones de Project no añadido; Archive de memoria sigue siendo lógico; Markdown deliberadamente básico; reconocimiento humano conservador de nombres; IME/modales físicos y paleta real pendientes. No se hicieron afirmaciones nuevas sobre velocidad/calidad de Gemma.
