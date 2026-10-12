# UX-00 — Auditoría y fundamento visual

**Estado:** UX-00 entregado para auditoría del orquestador; no implementa cambios visuales ni funcionales.
**Rama / HEAD remoto verificado al inicio:** feat/0.5.0-skills-agents / ea8b25ea77319a7650dae8f870c788979530feca.
**Código de UI inspeccionado:** 5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1. Los commits hasta el HEAD revisado contienen evidencia y documentación posteriores; no atribuyo los estados de Android al build personal de QA.

## Alcance y fuentes

Revisé AGENTS.md, README.md, STATUS.md, docs/ARCHITECTURE_OVERVIEW.md, docs/SKILLS_AGENTS_ARCHITECTURE.md, docs/STATUS.md, docs/development/EXECUTION_STATE.json, docs/development/CODEX_TO_WORK_HANDOFF.md, rutas Compose, ViewModels, componentes UI, pruebas y capturas versionadas.

- STATUS.md es el resumen actual 0.5.0. docs/STATUS.md se identifica a sí mismo como histórico 0.2.1; docs/development/STATUS.md no existe.
- No hay ROADMAP, BACKLOG, LOOP_RULES ni archivo de las 97 tareas versionado en el árbol. No reconstruí ni edité esa planificación. El estado persistente y los handoffs existentes conservan el encargo de no alterar los 97 IDs, orden y dependencias; la lista original sigue siendo la fuente adjunta por el usuario, no una fuente recuperable de este repositorio.
- No se adjuntaron imágenes de referencia de ChatGPT o Claude. No hice comparación visual con esas marcas ni atribuyo sus estilos a capturas locales.
- Capturas de producto observadas: docs/validation/ui-stage15/screenshots/{workspace,chat,models,project-settings}.png, 360×800, capturas históricas de la suite Robolectric/Compose UiSmokeTest. También existen capturas equivalentes bajo docs/validation/part2/screenshots. Las dos imágenes docs/validation/android-emulator-ci/compose-capture-5e93ba6/screenshots/chat-header-{standard,large-font}.png son capturas del árbol Compose del test de encabezado, 1080×1920; muestran el encabezado y no una conversación completa. Las fixtures red-bicycle.png y blue-car.png bajo app/src/main/assets/semantic-v2/fixtures son entradas de pruebas semánticas, no referencias de interfaz.
- Evidencia Android vigente al iniciar: el estado persistente registra el run 38186081844 sobre el código 5e93ba6: 16 casos, 9 PASS, 1 fallo por el fixture histórico auténtico de Room v1, 6 SKIPPED por modelos reales ausentes, 10 ejecutados. Las capturas del encabezado estándar y fontScale 1.6 se revisaron visualmente. EMULATOR sigue FAIL, PHYSICAL_DEVICE NOT_RUN; el cambio de encabezado aún requiere validación en Motorola. HOST en ese SHA fue parcial: pruebas del collector y compilación Kotlin de AndroidTest, no una suite JVM completa.

**Fuentes navegables:** [MainActivity.kt](../../app/src/main/java/com/localai/workspace/MainActivity.kt), [Theme.kt](../../app/src/main/java/com/localai/workspace/ui/Theme.kt), [ChatContent.kt](../../app/src/main/java/com/localai/workspace/ui/ChatContent.kt), [AssistantControls.kt](../../app/src/main/java/com/localai/workspace/ui/AssistantControls.kt), [SkillsAgentsUi.kt](../../app/src/main/java/com/localai/workspace/ui/SkillsAgentsUi.kt), [ContextMemoryUi.kt](../../app/src/main/java/com/localai/workspace/ui/ContextMemoryUi.kt), [SemanticSearch.kt](../../app/src/main/java/com/localai/workspace/ui/SemanticSearch.kt), [SemanticV2Settings.kt](../../app/src/main/java/com/localai/workspace/ui/SemanticV2Settings.kt), [ViewModels.kt](../../app/src/main/java/com/localai/workspace/ui/ViewModels.kt), [UiSmokeTest.kt](../../app/src/test/java/com/localai/workspace/ui/UiSmokeTest.kt), [ChatContentUiTest.kt](../../app/src/test/java/com/localai/workspace/ui/ChatContentUiTest.kt) y [ChatHeaderLayoutTest.kt](../../app/src/androidTest/java/com/localai/workspace/ui/ChatHeaderLayoutTest.kt). Capturas de producto: [workspace](../validation/ui-stage15/screenshots/workspace.png), [chat](../validation/ui-stage15/screenshots/chat.png), [models](../validation/ui-stage15/screenshots/models.png), [project settings](../validation/ui-stage15/screenshots/project-settings.png). Capturas de test del encabezado: [estándar](../validation/android-emulator-ci/compose-capture-5e93ba6/screenshots/chat-header-standard.png) y [fuente grande](../validation/android-emulator-ci/compose-capture-5e93ba6/screenshots/chat-header-large-font.png).

## a) Inventario verificable: rutas, pantallas, tabs y modales

El NavHost se declara en MainActivity.kt y registra nueve destinos. Solo tres son entradas de navegación principal; la barra inferior desaparece en destinos secundarios.

| Tipo | Ruta / superficie actual | Contenido y acceso observado |
|---|---|---|
| Entrada principal | workspace | Inicio combinado: iniciar chat/proyecto, privacidad local, modelo activo si existe, Semantic search, actividad en curso, lista de chats y proyectos, búsqueda y creación/borrado de proyecto o chat. |
| Entrada principal | models | Model Library: filtros por formato/capacidad/origen, orden, importación local/Hugging Face, proveedor de EmbeddingGemma y tarjetas de modelos. |
| Entrada principal | settings | Privacidad local, configuración semántica, memoria estructurada, administración de Skills y Logical Agents, Developer / Diagnostics plegable, capacidades, validación y rendimiento. |
| Destino secundario | model/{modelId} | Ficha del modelo generativo: metadatos, capacidades, contexto, salida, sampling/backend, aplicar como modelo y retirar con confirmación. |
| Destino secundario | project/{projectId} | Detalle de proyecto con tabs Chats, Files, Memory y Settings; menú de proyecto para borrar con confirmación. |
| Destino secundario | chat/{projectId}?conversationId=… | Conversación, encabezado Agente/conversación/modelo, mensajes, citas, composer, adjuntos, acciones y diagnóstico. |
| Destino secundario | memory/{projectId} | Lista de memoria estructurada de ese scope; accesible desde el menú de chat. |
| Destino secundario | performance | Diagnóstico y benchmark local, backend y opciones experimentales. |
| Destino secundario | device_validation | Suites Quick/Full, Thinking budgets, historial y exportación JSON. |

Fuentes: LocalAiWorkspaceRoot y NavHost, MainActivity.kt:L155-L238; WorkspaceScreen, L245-L319; ProjectScreen, L384-L460; ModelsScreen, L477-L602; SettingsScreen, L1258-L1282; PerformanceScreen, app/src/main/java/com/localai/workspace/ui/PerformanceScreen.kt; DeviceValidationScreen, ui/DeviceValidationScreen.kt.

**Tabs y paneles, no rutas independientes:** los cuatro tabs del proyecto; el acordeón Developer / Diagnostics; Skill/Agent management; Semantic search y Semantic diagnostics; Structured memory; selector de Skills para el siguiente turno. Algunos paneles usan SemanticPanel, otros AlertDialog.

**Diálogos, menús y pickers encontrados:**

- Inicio/proyecto: selector del modelo activo, creación de proyecto y confirmaciones de borrado.
- Modelos: Add model, Hugging Face/download, selección del modelo en chat, picker de documentos del sistema y confirmación para retirar un modelo. Model details es una pantalla de navegación.
- Proyecto: menú para borrar, detalles de archivo (extracción/indexación), borrado de proyecto y conversación.
- Chat: menú de opciones con Model, Agent, Skills, Files, Memory, Diagnostics y Delete chat; modelos y Agents tienen selectores; Skills tiene panel explícito para el siguiente turno. Files y preview de texto, error técnico, detalles de métricas, cita/fuente y borrado aparecen como diálogos. El menú + del composer ofrece solo fuentes conectadas y compatibles. Thinking y Tools son controles del composer que abren diálogo.
- Memory: panel con búsqueda/filtros/exportación, detalles, edición/adición, selección de kind/scope y confirmación de eliminación.
- Skills/Agents: listado, editor, import/export SKILL.md y confirmación de borrado; crear/editar Agent con Skills, Tools, Sources y scopes de memoria.
- Semántica: Semantic search abre consulta y resultados; el botón homónimo en Settings/Models abre en cambio configuración de Embedding models. Advanced / Diagnostics aloja dimension, backend, validación y benchmarks; retirar modelo pide confirmación. Index media está en el flujo de búsqueda de proyecto.
- Settings: detalle Private · Local y About capabilities; herramientas de inspector y validación aparecen al expandir Developer / Diagnostics.
- Los pickers OpenDocument/CreateDocument son hojas de sistema Android, no diálogos propios de Compose.

## b) Diagnóstico priorizado

Esfuerzo es una estimación relativa de diseño/implementación, no una medición del equipo. El riesgo indica posibilidad de afectar navegación, scope o uso diario.

| Prioridad | Hallazgo basado en código/evidencia | Impacto | Esfuerzo | Riesgo |
|---|---|---:|---:|---:|
| P0 | LocalAiTheme usa dynamicLightColorScheme/dynamicDarkColorScheme en Android 12+ y esquemas M3 por defecto en versiones anteriores; no define paleta de marca, Typography ni Shapes. La captura histórica de 360×800 muestra un acento púrpura del tema del dispositivo. | Alto: identidad y contraste varían según wallpaper/API; no hay escala tipográfica común verificable. | S | Bajo/medio: cambiar color visible sin tocar lógica. |
| P1 | workspace combina chats, proyectos, estado/modelo y Semantic search; Models es una pestaña raíz; proyecto y Files viven dentro de otra ruta. Solo hay tres entradas principales y no hay destino Library. | Alto: la ubicación de tareas y documentos exige aprender la jerarquía actual. | M | Medio/alto: el cambio de rutas puede afectar back stack y enlaces internos. |
| P1 | Settings muestra varios temas técnicos en una columna y los paneles se abren desde botones de texto; solo Developer / Diagnostics está plegado. Hay dos etiquetas “Semantic search”: una abre configuración de embedding y otra una búsqueda. | Medio/alto: se confunde configuración con acción de buscar y se descubre poco la jerarquía. | S/M | Medio: reordenar sin borrar capacidades. |
| P1 | MainActivity.kt concentra NavHost, varias pantallas, modales y componentes privados (1323 líneas); ViewModels.kt tiene 1293 líneas. No existe una capa visual común para theme, shell y componentes. | Alto para velocidad/mantenibilidad de próximas fases; el acoplamiento aumenta riesgo de un cambio visual global. | M/L | Alto si se divide todo de una vez; bajo si se extrae por fases. |
| P1 | La observación de documentos requiere projectId: HomeViewModel.observeDocuments(id) consulta documentDao().observeForProject(id), ViewModels.kt:L81-L84. Files existe solo dentro de ProjectScreen. | Alto para la futura pestaña Library propuesta: hoy no hay una lista global implementada. | M/L | Alto si una nueva UI mezcla proyectos o amplía retrieval sin scope explícito. |
| P2 | Encabezado de chat comunica Agent, conversación y estado/modelo en tres líneas. La prueba mantiene las tres visibles incluso con escala solicitada 1.6; el título largo queda truncado con ellipsis, como muestra la captura. | Medio: se identifica el modelo/Agent, pero no siempre el título completo. | S/M | Bajo si se conserva el test de layout y escala. |
| P2 | Modelos generativos y EmbeddingGemma aparecen ambos en áreas llamadas Models/Semantic search. Internamente la distinción existe, pero la etiqueta visible se reutiliza. | Medio: un usuario puede creer que el modelo de embeddings es el modelo que redacta. | S | Bajo si se mantiene separado provider semántico vs runtime generativo. |
| P2 | Agent, Skill y Tools ya son selectores separados. La etiqueta Tools: Off/Auto es funcional y no representa el Agent. Agent aparece en encabezado/menú; Skills aparece en menú. | Medio: el significado de Tools Off requiere mantener la distinción visual y textual. | S | Medio si un rediseño los agrupa como un único switch de “agente”. |
| P3 | Se mezclan márgenes 16/20dp y espacios 6/8/10/12/16; radios explícitos 10/22 junto a Shapes Material por defecto. Los touch targets dependen de los componentes Material en vez de un token de producto. | Medio, sobre todo en pantallas compactas/teclado. | S | Bajo; tokens no cambian datos ni lógica. |
| P3 | Los textos de la interfaz inspeccionada son mayormente literales en inglés y no hay preferencias de apariencia propias: el tema sigue el sistema. | Medio para localización y control de apariencia; no es requisito de UX-01 traducir o añadir ajustes. | M | Bajo si se separa de la fase visual actual. |

## c) Navegación final propuesta y flujos

Propuesta para una fase posterior, no una descripción del producto actual:

**Chats · Projects · Library · Settings**

La barra inferior es adecuada para el teléfono; en ancho mayor se puede usar rail si los componentes Material3 ya presentes lo soportan, sin añadir dependencias en UX-01.

1. **Chats:** lista de conversaciones y botón New chat. Abrir una conversación conserva su project/chat ID y vuelve a la lista correcta. Una conversación originada desde un proyecto conserva su scope.
2. **Projects:** lista y creación; abrir proyecto mantiene sus tabs Chats, Files, Memory y Settings. La configuración del proyecto conserva instrucciones, modelo preferido, Agent preferido y control de memoria existentes.
3. **Library:** nueva superficie propuesta que agrupa por proyecto. El usuario elige el proyecto antes de ver/importar/buscar sus documentos. No representa hoy un agregador global: el provider actual observa documentos por proyecto. Ninguna búsqueda cruza proyectos implícitamente; un modo global solo podría añadirse si una fuente y un permiso reales lo respaldan en una fase futura.
4. **Settings:** secciones con nombre visible: Models, Semantic search models, Agents, Skills, Memory, Appearance (solo controles que se implementen) y Advanced / Diagnostics. Diagnostics y benchmarks quedan anidados; siguen accesibles.

**Dónde quedan modelos y selectores:**

- Modelo generativo activo: selector compacto en el encabezado de chat; el resumen de modelo que ya aparece en Workspace puede seguir como acceso. El catálogo/importación se reorganiza a Settings → Models; no es tab principal.
- EmbeddingGemma 300M/740M: Settings → Semantic search models, en sección independiente de modelos generativos. La acción de uso se llama Search documents y vive en Library/Files.
- Agent: selector separado en chat y Preferred Agent dentro de Settings del proyecto.
- Skills: administración en Settings → Skills; selección explícita/automática para turno desde chat.
- Tools: control propio del composer, con Tools Off/Auto; la UI nunca lo renombra como “sin Agent”.

**Flujo recomendado:** Chats → New chat → elegir Agent (si procede) y modelo físico actual → añadir archivo/imagen/audio cuando el runtime lo ofrece → redactar/responder. Projects → proyecto → Files → importar/ver estado/buscar dentro del proyecto → abrir un chat del mismo proyecto. Settings → Models o Semantic search models para configurar proveedores, separados de la búsqueda de documentos.

## d) Tokens visuales concretos y contraste medido

Base propuesta para UX-01. La paleta conserva los colores pedidos; los tonos de contenedor semántico marcados como derivados son añadidos para controles con fondo. La selección clara/oscura sigue el tema del sistema; no agrega un selector de apariencia en UX-01.

| Token / rol | Light | Dark |
|---|---|---|
| background | #F6F7F4 | #101613 |
| surface | #FFFFFF | #171E1B |
| surfaceContainer | #EFF1EE | #202824 |
| onBackground / onSurface | #1D2522 | #E1E4DF |
| textSecondary / onSurfaceVariant | #54605B | #BDC8C1 |
| outline | #77817C | #89948E |
| primary / brand | #3E746D | #A5D8CE |
| onPrimary | #FFFFFF | #183833 |
| primaryContainer (derivado) / onPrimaryContainer | #DDEBE7 / #173E38 | #294B44 / #D5EEE8 |
| error / onError | #BA1A1A / #FFFFFF | #FFB4AB / #690005 |
| warning foreground / container / onContainer (extendido) | #7A5D00 / #F4E7BE / #4A3600 | #E6C66B / #3C3218 / #FFF0BE |
| success foreground / container / onContainer (extendido) | #256D46 / #DCEFE1 / #173C27 | #8BD2A4 / #203B2B / #D5F4DB |

Mapeo M3: primary/onPrimary y primaryContainer/onPrimaryContainer; background/onBackground; surface/onSurface; surfaceVariant=surfaceContainer con onSurfaceVariant=textSecondary; outline solo para bordes/divisores; error/onError y errorContainer/onErrorContainer. Warning y success son tokens extendidos, no se infieren de tertiary. En disabled se comunica el estado también por affordance/texto, no solo reduciendo el contraste.

**Medición:** calculé luminancia relativa sRGB y razón WCAG 2.x con la fórmula (Lmax + 0.05)/(Lmin + 0.05). AA se evaluó como 4.5:1 para texto normal y 3:1 para bordes/estados gráficos relevantes. Estos son cálculos de los hex propuestos, no una medición de la UI actual.

| Combinación representativa | Light | Dark | Criterio / resultado |
|---|---:|---:|---|
| texto principal sobre background | 14.57:1 | 14.28:1 | AA normal: pasa |
| texto principal sobre surface | 15.67:1 | 13.22:1 | AA normal: pasa |
| texto secundario sobre surfaceContainer | 5.78:1 | 8.77:1 | AA normal: pasa |
| brand sobre background | 4.98:1 | 11.62:1 | AA normal: pasa |
| onPrimary sobre primary | 5.35:1 | 8.06:1 | AA normal: pasa |
| onPrimaryContainer sobre primaryContainer | 9.60:1 | 7.89:1 | AA normal: pasa |
| error sobre surface; onError sobre error | 6.46:1 / 6.46:1 | 9.99:1 / 7.72:1 | AA normal: pasa |
| warning foreground sobre surface; sobre warningContainer | 6.19:1 / 5.02:1 | 10.22:1 / 7.62:1 | AA normal: pasa |
| success foreground sobre surface; sobre successContainer | 6.26:1 / 5.21:1 | 9.57:1 / 6.88:1 | AA normal: pasa |
| onStatusContainer sobre statusContainer (warning / success) | 9.36:1 / 10.21:1 | 11.11:1 / 10.33:1 | AA normal: pasa |
| outline sobre surfaceContainer | 3.54:1 | 4.81:1 | Pasa 3:1 para gráfico; Light outline no debe usarse como texto |
| outline light sobre surface blanca | 4.03:1 | — | No alcanza 4.5:1 para texto; usar #54605B |

Los colores exactos del usuario son base del cálculo. Para los pares derivados también se muestra el hex; no se extrapola el contraste entre fondos no incluidos en la tabla. UX-01 debe tener una prueba reproducible del mapping/contraste o una verificación equivalente en tests, más lectura visual real en light/dark y escala grande. Las capturas actuales reflejan dynamic color del emulador, no estos tokens.

**Tipografía y geometría propuestas**

| Token | Tamaño / line height |
|---|---|
| screenTitle | 22 / 28sp |
| sectionTitle | 18 / 24sp |
| body | 16 / 24sp |
| bodySmall | 14 / 20sp |
| label | 12 / 16sp |

Sans del sistema (Roboto por defecto en Android); no incorporar fuente ni dependencia nueva. No fijar tamaños de contenedor que impidan text scaling. Escala de espaciado: 4, 8, 12, 16, 20, 24, 32dp. Radios: 8, 12, 20, 28dp. Área táctil mínima: 48×48dp. Márgenes móviles: 16dp; ancho máximo de lectura centrada: 720dp; tablas/código pueden usar desplazamiento horizontal y no estrechar el cuerpo narrativo.

## e) Arquitectura Compose propuesta y reutilización

No crear otra capa de estado ni trasladar lógica de dominio a Composables.

| Capa destino | Responsabilidad futura | Piezas actuales a reutilizar |
|---|---|---|
| ui/theme | Palette light/dark, Typography, Shapes, spacing, elevation y semantic status tokens bajo LocalAiTheme. | ui/Theme.kt es el punto de entrada; hoy solo decide dynamic/system schemes y usa Typography/Shapes por defecto. |
| ui/shell | Scaffold, navegación, área de contenido y snackbar; rutas mantienen IDs y back behavior. | LocalAiWorkspaceRoot, TopLevelDestination y NavHost están en MainActivity.kt:L155-L238. UX-01 no cambia la topología. |
| ui/components | Screen content width, SectionHeader, StatusLabel, EmptyState, Card surface y destructive confirmation reutilizables. | EmptyState, WorkspaceEntryCard, WorkspaceModelCard, ModelCard y DetailSection existen hoy mayormente privados en MainActivity.kt. Extraer una sola implementación cuando se use en más de una pantalla; no duplicarlos. |
| ui/chat | Encabezado, mensajes/Markdown, composer, chips de control, adjuntos/citas. | ChatHeaderTitle, ChatComposer y ChatRichText en ui/ChatContent.kt; AssistantControls/ToolExecutionCard; ImageAttachment; MessageBubble y ChatScreen. |
| ui/projects | Lista y detalle de proyectos con tabs que conservan el contexto. | ProjectScreen, ProjectAgentPreference, WorkspaceEntryCard y HomeViewModel. |
| ui/library | Documentos, filtros y búsqueda con scope explícito de proyecto. | ProjectScreen Files, HomeViewModel.observeDocuments(projectId), SemanticSearchEntry. No existe aún pantalla global. |
| ui/settings | Secciones básicas y grupo avanzado; Agents/Skills/Memory/modelos siguen como conceptos distintos. | SkillsAgentsEntry, ContextMemoryEntry, SemanticV2Settings, SemanticSearchEntry, PerformanceScreen, DeviceValidationScreen. |
| ui state/domain | ViewModels, navegación de acciones y servicios existentes. | HomeViewModel, ChatViewModel, MemoryViewModel, AppGraph. El estilo no cambia sus contratos. |

El cambio UX-01 puede crear subpaquetes solo para tokens y primitives compartidas nuevas. No mueve todos los 1323 renglones de MainActivity ni los 1293 de ViewModels. La extracción general se decide por uso probado en UX-02..05.

## f) Wireframes textuales

Son jerarquías de contenido propuestas, no mockups ni pantallas implementadas.

**Chats / lista**

    Chats                                  Search
    [ + New chat ]
    Recent
    [Título de conversación]  Agent · modelo/estado       ⋯
    [Título de conversación]  Proyecto                    ⋯
    Projects (acceso resumido)                         See all
    Barra: Chats | Projects | Library | Settings

Lista orientada a volver al chat; la creación de proyecto pertenece a Projects. El estado activo puede mostrar generación/cancelación solo cuando ocurre.

**Conversación activa**

    ‹ Projects / Nombre del proyecto (si aplica)       ⋯
    Agent: General Agent      Conversation title…
    Modelo generativo · Local · Ready
    ───────────────────────────────────────────────────
    mensajes y citas locales; acciones de copiar / continuar / detalles
    ───────────────────────────────────────────────────
    chips: Think: Auto     Tools: Auto
    [+ adjuntar] [Ask anything…                    ] [Send]

La cabecera permite cambiar modelo; el overflow conserva Agent, Skills, Files, Memory, Diagnostics y borrar. Thinking/Tools siguen siendo controles distintos. A escala grande, texto y cabecera pueden crecer; evidencia actual advierte que los títulos largos se ellipsizan.

**Projects**

    Projects                                [ + New project ]
    [Proyecto A]  chats …  files … (solo si los conteos ya están disponibles)
    [Proyecto B]
    Barra principal

Al entrar, mantener tabs Chats | Files | Memory | Settings. No asumir conteos si no hay datos en el flujo actual.

**Library**

    Library                     [Scope: Proyecto actual ▾]
    Search files within this project
    [ Add file ]
    [Documento.xlsx] Ready · 12 segments             ⋯
    [Informe.pdf]    Indexing …                      ⋯
    [Search this project] → resultados con documento y provenance
    Barra principal

El selector de scope es obligatorio para la lista/búsqueda propuesta. No hay opción “todos los proyectos” a menos que se implemente una consulta explícita que mantenga aislamiento y se valide.

**Settings**

    Settings
    Models                         Installed model · ready
    Semantic search models         EG provider · ready/index state
    Agents                         General + logical Agents
    Skills                         enabled / installed
    Memory                         approved items
    Advanced / Diagnostics         collapsed
    Barra principal

Appearance solo aparece cuando hay un control de apariencia real; el modo de UX-01 sigue el sistema.

**Selector / opciones avanzadas**

    Modelo activo (en encabezado de chat)
      • modelo generativo físicamente disponible
      • Settings → Models: importar, detalle, capacidad/estado, retirar

    Agent (selector separado)
      • General / Agents habilitados / preferencia de proyecto
    Skills for next turn
      • selección explícita, routing determinista sigue activo
    Think: Auto / Tools: Auto
      • controles independientes del Agent
    Settings → Advanced / Diagnostics
      • Context/Memory Inspector, Semantic diagnostics/validation,
        performance, benchmark, Device Validation

“Tools Off” nunca se traduce a “sin agente”. Modelos generativos y embeddings usan tarjetas/secciones distintas.

## g) Matriz: conservar, simplificar y reubicar

No se propone eliminar nada. Advanced indica jerarquía de presentación, no deshabilitar ni restringir capacidades.

| Área | Conservar exactamente en función | Simplificar/reorganizar en UX |
|---|---|---|
| Modelos generativos | Importar GGUF/LiteRT-LM/multimodal, Hugging Face explícito, detalle, selección, estado, runtime, sampling, context, quitar con confirmación. | Catálogo bajo Settings → Models; tarjetas con nombre legible/estado/capacidad disponible. Hash, sampling fino, import/download avanzado en detalles. Selector activo permanece en chat. |
| EmbeddingGemma / semantic models | EG1 y EG2 coexistentes, provider, dimensión, indexación/reindexación, búsqueda e integridad de índice. | Sección de configuración separada de LLMs. Nombrar “Embedding models” y separar “Search documents” de “model setup”. Advanced conserva hash/runtime/benchmark. |
| Projects | Crear, renombrar/editar si ya existe, chats, settings, archivos, memoria, instrucciones, Agent/model preference y confirmación de borrado. | Entrada Projects propia; al abrir, conservar tabs actuales hasta que una migración UX se valide. |
| Documents / Files | SAF import, estado parsing/index, preview, summarize existente, búsqueda/citas y selección en chat. | Library como lista por proyecto y acceso contextual desde chat. Mantener estado/fallo y provenance visibles. No crear índice global implícito. |
| Memory | Solo memoria explícita/aprobada; editar/archivar, scope user/global/project y control de inclusión existente. | Atajo de memoria contextual desde chat; administración agrupada en Settings y en scope del proyecto. No autoextracción ni mezcla de scopes. |
| Agents | General y lógicos, selector, preference de proyecto, tools/sources/scopes propios. | Separar visualmente de modelo físico y Tools; mostrar Agent seleccionado sin llamarlo modelo. |
| Skills | Built-in/import/custom, enable/disable, metadata/instrucciones, explicit/auto routing, export y límites de ejecución. | Settings → Skills; vista breve por defecto, instrucciones completas al abrir; no introducir scripts/plugins ni nuevos routers. |
| Tools | Calculator/files/Python conectados, schemas, estados/call details, Think/Tools behavior. | Composer contextual y detalles en overflow; Tools Off/Auto visibles como estado propio. No equivaler Tools Off con no-agent. |
| Diagnostics | Métricas de generación, RAG, memoria, tools, audio/vision, device reports y exportaciones existentes. | Advanced / Diagnostics plegado por defecto, con encabezados legibles; no datos técnicos en la lista principal. |
| Performance | Benchmark y backend actuales, experimentalidad y fallback medido. | Advanced; resumen primero, configuración/telemetría completa en detalle. No anunciar GPU activa por solicitud. |

## h) Plan incremental UX-01..UX-05

| Fase | Alcance y archivos candidatas | Gate para cerrar |
|---|---|---|
| UX-01 — visual foundation | Solo paleta fija light/dark, typography, shape, spacing tokens; primitives/shell compartidas mínimas. Evolucionar ui/Theme.kt; añadir ui/theme y ui/components; envolver LocalAiWorkspaceRoot con shell sin cambiar destinos. Tests: UiSmokeTest, ChatContentUiTest, nuevos tests pequeños de token y ChatHeaderLayoutTest. | Contraste por pares, light/dark, fontScale estándar/1.3/1.6, widths compact/normal, navegación existente intacta; tests host pertinentes, AndroidTest compilado/ejecutado según entorno y evidencia visual. Sin cambios de ruta/datos. |
| UX-02 — navegación y hogar | Reorganizar MainActivity/NavHost y navegación principal en Chats/Projects/Library/Settings. Library scoped explícitamente a proyecto; mapear rutas antiguas o conservar sus IDs/argumentos. HomeViewModel/DAO solo si el scope real lo requiere. | Todas las rutas actuales alcanzables, back/restore y chat/project IDs conservados; aislamiento de proyecto; tests de navegación en emulator y smoke Motorola. Ninguna función perdida. |
| UX-03 — chat | Refinar ChatScreen, ChatHeaderTitle, ChatComposer, MessageBubble, citas/adjuntos y accesos avanzados. Archivos ChatContent.kt, AssistantControls.kt, ImageAttachment.kt, SkillsAgentsUi.kt y MainActivity.kt. | Tools Off no altera Agent; Thinking/Tools/adjuntos/model/Agent/Skills siguen conectados; títulos/citas/teclado/fuente grande y Android back probados. |
| UX-04 — Projects y Library | Extraer ProjectScreen/Files y construir la vista Library por proyecto reutilizando DocumentIngestion, HomeViewModel y SemanticSearchEntry. Organizar detalle/estado de archivo y accesos a semantic. | Importación/parsing/index existente, Selected-document/project scope, citas y fallback pasan sin regresión; nunca mezclar proyectos. No Room schema change por presentación. |
| UX-05 — Settings y detalles | Ordenar Models, semantic providers, Memory, Agents, Skills; progressive disclosure para inspectores, Device Validation, benchmark y detalles. Archivos SettingsScreen, ContextMemoryUi, SkillsAgentsUi, SemanticV2Settings, PerformanceScreen, DeviceValidationScreen. | Paridad funcional inventariada, accesibilidad/teclado/fuentes, capturas light/dark y Motorola visual QA; Advanced no oculta capacidades ni reports de error reales. |

## i) Riesgos funcionales y mitigaciones

1. **Pérdida de navegación/historial al mover rutas.** Mantener chatRoute(projectId, conversationId), IDs, saved state, comportamiento Back y top-level restore. Testear deep links internos desde lista, proyecto y chat.
2. **Mezcla de información entre proyectos.** Library debe recibir scope explícito y limitar a documentos del proyecto seleccionado. La implementación actual consulta documentos por projectId; una lista global requiere una decisión de producto y pruebas de aislamiento, no un filtro visual al final.
3. **Confundir modelos.** Generative Model es el runtime físico; EG1/EG2 son proveedores de embeddings. No mezclar selector, estado Ready o acción de importación.
4. **Confundir Agent, Skill y Tool.** El Agent lógico sigue independiente; Skill aporta instrucciones, Tool ejecuta capacidad, Source aporta datos, Memory son registros persistentes; Context es la composición temporal. Tools Off solo desactiva herramientas.
5. **Ocultar errores detrás de simplificación.** Los estados de importación/indexación, citas, omisiones materiales, fallback, modo experimental y confirmación destructiva deben seguir presentes; moverlos a un detalle no puede cambiar el resultado o esconder estado.
6. **Contraste y theme dinámico.** Sustituir dynamic color por la paleta fija reduce variación del wallpaper; validar cada estado light/dark, focus, error, warning y success. El outline light se reserva para bordes, no labels.
7. **IME, fontScale y ventanas pequeñas.** Usar scroll y WindowInsets existentes, evitar altura fija; cubrir teclado visible y 360dp con escalas 1.0/1.3/1.6. La captura del encabezado test no sustituye capturas de pantalla completa.
8. **Sobre-refactor.** Empezar por Theme/primitives; no dividir MainActivity/ViewModels enteros ni modificar estructura de estado/DB sin necesidad comprobada.
9. **Alcance y compatibilidad.** UX-00 y UX-01 no alteran routing, capacidades, permisos, inference, persistencia, Room, migraciones, formato ni dependencias. Tests y validación host/emulator/physical se reportan como capas distintas.

## j) Contrato técnico listo para UX-01

**Nombre:** UX-01 — Tokens, tema y shell compartida, sin cambiar navegación de producto.

**Base:** rama feat/0.5.0-skills-agents, auditar de nuevo el HEAD remoto antes de editar. UI fuente revisada: 5e93ba67aae84b1103ffad70e7c8ee28c75d0bb1; snapshot documental de esta auditoría: ea8b25ea77319a7650dae8f870c788979530feca. UX-01 no inicia hasta auditoría/aceptación del presente documento.

**Objetivo:** implementar los tokens de la sección d y un pequeño set de primitives/shell para que las pantallas actuales los usen de forma consistente, con cambios visuales verificables y sin reorganizar rutas.

**Archivos dentro del scope de implementación:** ui/Theme.kt; nuevos archivos bajo ui/theme y ui/components solo para tokens/primitives compartidas; extracción mínima desde MainActivity.kt de la shell actual y EmptyState solo si se reutiliza sin duplicarla; pruebas Compose/JVM existentes o nuevas en app/src/test/java/com/localai/workspace/ui y la prueba ChatHeader correspondiente. No es necesario modificar ViewModels.

**Requisitos:**

- Hacer explícita la paleta light/dark fija y seguir isSystemInDarkTheme. Typography debe declarar los tamaños/line heights de la sección d; Shapes/Spacing centralizan los tokens. No agregar font, dependency o control de apariencia no existente.
- Shell envuelve los mismos destinos workspace, models, model/{modelId}, settings, performance, device_validation, project/{projectId}, chat/{projectId}?conversationId=… y memory/{projectId}; mantiene las mismas tres entradas principales, start destination, NavController/back stack, insets y snackbar. UX-01 no añade Charts/Projects/Library ni mueve Models.
- Reutilizar ChatHeaderTitle, ChatComposer, AssistantControls, SemanticPanel, ModelCard, EmptyState y WorkspaceEntryCard donde corresponde; no hacer copias con estilos paralelos. Extraer solo si reduce duplicación real.
- Mantener intactos significado y comportamiento de Model/Embedding Model, Agent, Skills, Tools/Tools Off/Auto, Thinking, Files, Memory y Diagnostics.
- Ningún cambio a rutas de inferencia, routing, seguridad/permisos, datos, documentos, Context Builder, retrieval, DB/schema, modelo, runtime, sampling o test expectation funcional.

**Aceptación verificable:**

1. Tests verifican valores de colores/typography y pares AA definidos; outline no se usa para texto normal.
2. Compose checks cubren render light/dark y que las áreas táctiles comunes no sean menores de 48dp; uso de tipografía y escala no recorta los tres elementos del header/composer.
3. Las pruebas host UI afectadas pasan; compilan assembleDebug y AndroidTest. La ejecución de AndroidTest se informa por separado y la revisión física en Moto G86 sigue pendiente hasta que efectivamente se realice.
4. Capturas reales del workspace, chat, models y project screen en light/dark, 360/411dp y fontScale estándar/1.6 se guardan como evidencia UX con origen, SHA y dimensiones. No se usa screenshot de ChatHeader aislado como sustituto.
5. git diff --check limpio; diff limitado a los archivos declarados; ningún cambio de producto fuera de UI/theme/shared primitives.

**No incluido:** UX-02..UX-05, nuevas rutas/tabs, Library global, rediseño de todas las pantallas, localización, preference de tema, cambios de datos, flujos nuevos, refactor completo de MainActivity/ViewModels, dependencias, APK release, afirmaciones de validación Motorola.

## Decisiones que deben persistir en todas las fases

- Chat es la superficie principal; la complejidad se descubre progresivamente.
- Mantener luz/oscuridad y accesibilidad de escalado; el contenido no puede desaparecer con tamaño de fuente grande.
- No eliminar capacidades; una reorganización visual no cambia permisos ni disponibilidad.
- Modelos generativos, modelos de embeddings, Logical Agents, Skills y Tools son entidades distintas.
- Tools Off nunca equivale a “sin Agent”.
- Agents comparten el runtime físico; no se crea otro LLM por Agent.
- No modificar routing, permisos, inferencia, memoria/datos, persistencia, Room, migraciones o dependencias en UX-00/UX-01.
- El original de 97 tareas no existe como archivo rastreado aquí; permanece fuera del alcance y no fue reconstruido.
