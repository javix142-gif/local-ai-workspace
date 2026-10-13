# Local AI Workspace — Roadmap maestro del producto

**Edición:** 1.0 · **Fecha de referencia:** 2026-10-08  
**Estado:** documento de planificación, no especificación de funciones ya entregadas  
**Uso:** lectura humana, planificación en GitHub y contexto estable para Codex/otros agentes  
**Archivos complementarios:** `BACKLOG.json`, `LOOP_RULES.md`, `PROGRESS_TEMPLATE.md`.

> **Regla central:** este documento define el destino del producto. La autorización de trabajo autónomo se limita siempre al **hito activo** y a las tareas expresamente desbloqueadas del backlog. No ejecutar el roadmap completo de una sola vez.

---

## 1. Visión del producto

**Local AI Workspace** es un espacio de trabajo y asistente personal de IA para Android, **local-first, offline-first, privado por defecto y con nube opcional**. Debe permitir comprender información, organizar conocimiento, generar y modificar artefactos, ejecutar acciones autorizadas, y coordinar tareas mediante habilidades y agentes lógicos, con especial cuidado de que funcione en un móvil de memoria y batería finitas.

El objetivo **no** es competir únicamente como interfaz de chat para un modelo local. La propuesta diferenciadora es unir, en una sola aplicación y sin requerir servidores propios:

1. Conversación local y multimodal con modelos administrados por el usuario.
2. Proyectos y espacios de trabajo persistentes: chats, documentos, código, medios y fuentes.
3. Búsqueda híbrida (estructurada, lexical y semántica), citas, procedencia y contexto relevante.
4. Memoria editable, verificable y con ámbitos definidos; fuentes y memorias no son lo mismo.
5. Skills interoperables y Agents lógicos que comparten uno o pocos runtimes según recursos reales, **sin cargar innecesariamente múltiples LLM grandes**.
6. Herramientas y capacidades del dispositivo; acciones sobre archivos, Android y servicios conectados cuando el usuario las habilite.
7. Creación y edición real de PDF, DOCX, XLSX, PPTX, código y otros artefactos, no solo instrucciones textuales.
8. Gestión de imagen, audio, video, galería, transcripciones y búsqueda por significado.
9. Flujos de trabajo y automatizaciones reanudables, trazables y controladas.
10. Extensibilidad por plugins y conectores; uso remoto opcional, nunca dependencia silenciosa.

**Público inicial:** un usuario avanzado de Android que desea un asistente propio y capaz. **Posible evolución:** herramienta de productividad más general. No imponer desde el inicio flujos empresariales ni moderación artificial del contenido. Distinguir libertad de consulta de riesgos de acciones externas o irreversibles.

### 1.1 Principios de producto

- **Local como comportamiento predeterminado.** Descargas o conectores de red solo cuando sean habilitados expresamente.
- **Capacidad antes que burocracia.** Lectura y cómputo local sin pasos redundantes; confirmación principalmente para borrado irreversible, pagos, envío externo y acciones sensibles.
- **Verificable antes que aparente.** Distinguir «implementado», «probado en host», «probado en dispositivo» y «comprobado con datos reales».
- **Una acción observable.** Cada tool/skill/workflow importante debe producir estado, resultado o error trazable; jamás inventar éxito.
- **Datos recuperables.** Sin migraciones destructivas, con exportación/restauración y pruebas de actualización.
- **Escalabilidad gradual.** No mantener todos los modelos, herramientas, vectores o documentos en RAM; usar carga diferida, streaming y cuotas por operación.
- **Modularidad pragmática.** Interfaces claras entre capa de modelo, fuentes, memoria, skills, agentes, herramientas, documentos y UI, sin refactorizaciones preventivas gigantescas.
- **Capacidad real sobre marketing.** Una Skill no implica que la Tool exista; registrar disponibilidad y dependencia explícitamente.

### 1.2 Fuera del alcance inicial

Un «agente universal» capaz de operar cualquier aplicación Android sin límites de plataforma, inferencia en la nube obligatoria, varias instancias grandes de Gemma simultáneas, autonomía desatendida sin límites, compatibilidad perfecta con todos los archivos o análisis temporal nativo de video sin un motor apropiado.

---

## 2. Línea de base: qué existe y qué falta comprobar

### 2.1 Estado conocido al 2026-10-08

**Versión en desarrollo:** `0.5.0 / versionCode 27`, rama `feat/0.5.0-skills-agents`. Tras el postfix de EG2/índices stale, HEAD comunicado: `7aa57492fd3117c5b14b2a11b8167ba55c4347e3`. **Aún sin push, merge ni tag de 0.5.0** según el último informe compartido. El baseline protegido es el tag `v0.4.2` sobre `de615b772cf37a99e7e08a647c59607b271de606`.

**Capacidades implementadas:** Gemma 4 E2B en LiteRT-LM; GGUF/llama.cpp; chat y proyectos; importación y lectura básica de documentos; RAG lexical y EG1; Semantic V2/EmbeddingGemma 2; memoria aprobada y acotada por scope; Context Builder V1; Python local y herramientas limitadas; Skills integradas y SKILL.md subset; General Agent y agentes personalizados; routing determinista; base `agents_skills.db`; diagnóstico/Inspector.

**Evidencia recibida:** gate host **673 pruebas app + 2 compatibilidad**, builds debug/release/lint/AndroidTest compilados sin errores de compilación; **AGENTS_SKILLS_V1 físico en Motorola: 12/12 PASS, 0 fail, 0 blocked**. La suite física cubre chat nativo, activación de Skills, ámbito de memoria, gate, cancelación y casos sintéticos; **no demuestra toda la calidad documental, rendimiento ni las pruebas físicas F1/F2**.

**Problemas observados con uso real** (no atribuir causa sin instrumentar):

- Un XLSX de aproximadamente **300 KB** fue rechazado con `Structured document representation exceeds 8 MiB budget`.
- La aplicación muestra frecuentemente `Some context was omitted to keep this turn within its budget` durante consultas documentales.
- En un XLSX diminuto de tres celdas, una respuesta tardó alrededor de **3 minutos**; no hay desglose fiable de preparación, retrieval, prefill ni decode.
- Cabecera del chat recorta la tercera línea (información de modelo/estado) debajo del nombre del agente y el título.
- El XLSX pequeño sí fue interpretado: celdas A1=1, A2=5, fórmula `=A2-A1` y A3=4. Eso prueba un caso concreto de lectura, no la fidelidad general de hojas de cálculo.
- F1 (EG2 omitido por `evidence != null`) y F2 (uso de índice `NEEDS_REINDEX`) fueron corregidos en el postfix y pasaron tests host; falta spot-check físico específico.

### 2.2 Qué NO debe asumirse por estar en el código

- Que lectura/escritura de todos los formatos sea perfecta, ni que Excel evalúe fórmulas como Excel.
- Que multimodalidad, audio, GPU/NPU o video temporal funcionen bien en el Motorola por el mero hecho de existir adaptadores.
- Que una consulta indexada sea suficientemente relevante por ocupar el primer puesto del ranking.
- Que 12 tests sintéticos equivalgan a 12 flujos cotidianos reales.
- Que el límite de `4096` del contexto y la estimación de bytes equivalgan a conteos exactos de tokens.
- Que se pueda borrar, enviar, editar o interactuar con apps Android únicamente añadiendo una Skill.

---

## 3. Restricciones técnicas de referencia

**Dispositivo prioritario de prueba:** Motorola Moto G86 Power 5G, Android API 36, RAM cercana a 8 GB. No asumir que toda esa RAM queda disponible para modelos. CPU es el baseline reproducible; GPU experimental debe medirse antes de usarse como valor predeterminado; NPU no debe anunciarse operativa sin integración y pruebas.

**Modelos de referencia:**

- **Gemma 4 E2B** `.litertlm`: generación/razonamiento y multimodalidad condicionada por formato y runtime; un runtime generativo físico compartido entre Agents lógicos.
- **EmbeddingGemma 300M (EG1)** GGUF: RAG de texto legado, cuando corresponda.
- **EmbeddingGemma 2 740M (EG2)** LiteRT-LM: espacio multimodal y semántico separado; 768 dimensiones como configuración productiva reportada. Los índices EG1/EG2 **no comparten espacio vectorial**.
- **Modelos especializados opcionales** (ASR, OCR, VAD, reranking o FunctionGemma-like) solo cuando un benchmark demuestre valor neto. Preferir carga **on demand** y liberación de memoria.

**Contexto de generación actual:** ~4096 unidades configuradas; se reservan salida y margen. `ESTIMATED_UTF8_BYTES_V1` es un estimador conservador, no tokenizer exacto. No multiplicar contexto indiscriminadamente para esconder mala selección de evidencia.

**Restricciones estructurales:** Android Scoped Storage / SAF, permisos de usuario, ejecución en segundo plano y políticas de aplicaciones externas. Diseñar degradación clara cuando una acción no sea posible mediante APIs oficiales.

---

## 4. Arquitectura objetivo

```text
USER / PROJECT / INTENT
         |
         v
Request + Attachments + Permissions
         |
         v
Logical Agent Resolver ----- Agent Registry
         |
         v
Skill Eligibility/Selection ---- Skill Registry (L1/L2/L3)
         |
         +-------> Tool Candidate Resolver ---> Tool Registry
         |                                      |-- Native/Android
         |                                      |-- Python/JS
         |                                      `-- future MCP/Plugins
         |
         +-------> Source Registry -------------|-- Project documents
         |                                      |-- Conversations/Code
         |                                      |-- Gallery/Audio/Video
         |                                      `-- future Connectors
         |
         +-------> Memory Manager -------------> Canonical records
         |
         +-------> Retrieval ------------------> FTS / EG1 / EG2 indexes
         |
         v
Context Builder / Provenance / Budget / Trust boundaries
         |
         v
Model Runtime Manager ---> one resident Gemma (when possible)
         |
         v
Inference / Tool calls / Structured Results
         |
         v
Agent Activity Timeline + Chat + Artifact Viewer/Editor
         |
         v
Workspace Persistence / Audit Metadata / Export
```

### 4.1 Fronteras conceptuales obligatorias

| Objeto | Contiene | No debe convertirse en |
|---|---|---|
| **Source** | Origen y permisos de información; texto/medios/archivos | Memoria aprobada automáticamente |
| **Memory** | Hecho/preferencia/decisión estructurada con scope, origen y versión | Almacén indiscriminado de documentos |
| **Embedding** | Representación indexable asociada a modelo/versión/espacio | Fuente canónica ni verdad independiente |
| **Context** | Paquete temporal de instrucciones, historia y evidencia para un turno | Nueva persistencia oculta |
| **Skill** | Procedimiento, instrucciones, referencias, routing metadata | Tool ejecutable o Agent físico |
| **Agent** | Rol/configuración, Skills, herramientas, fuentes, memoria y contexto | Otro Gemma residente |
| **Tool** | Acción ejecutable con contrato de entrada/salida | Texto de una Skill |
| **Plugin** | Código/hook/extensión instalable con ciclo de vida y permisos | Skill.md declarativa |
| **Workflow** | Estado y coordinación de varias tareas/tools | Chat infinito sin límites |

### 4.2 Decisiones vigentes que preservar

- Progressive disclosure: L1 metadata, L2 instrucciones solo si activas, L3 referencias bajo demanda.
- `INSTALLED → ENABLED → ELIGIBLE → ACTIVE → EXECUTED`; la confianza/origen no equivale a «enabled».
- Routing V1 determinista; **0 Skills es resultado correcto**. EG2 semantic ranking de Skills, si se adopta, debe abstenerse bajo umbral y medirse contra V1; Gemma router solo si aporta mejora.
- Resolver agente, activar Skill y elegir Tools son problemas diferentes.
- Orden para evitar deadlock: request → agente/Skills → memoria/retrieval → tools/context → **gate** → inferencia.
- Evidencia ausente y memoria no relevante deben ser resultados normales, no desencadenar fallback arbitrario.
- Tool execution verifica capacidad real, resultado y estado; UI no debe presentar una «acción completada» solo porque Gemma lo escribió.
- Múltiples indexadores/modelos pueden convivir, con identificador de espacio y migración reversible; no consolidar vectores por estética.

---

## 5. Hitos y dependencias del roadmap

**Importante:** los identificadores `M00`–`M12` son hitos de producto, **no** compromisos de fechas ni números de versión. Solo `M00` corresponde específicamente al cierre pendiente de **0.5.0**. Fijar las versiones siguientes cuando cada hito tenga alcance y evidencia. Pueden existir subreleases 0.5.x, 0.6, etc.

| ID | Hito | Resultado verificable | Dependencias | Prioridad |
|---|---|---|---|---|
| **M00** | Estabilizar 0.5.0 | XLSX razonable importable, contexto útil, UI corregida, latencias medidas, pruebas físicas dirigidas | baseline 0.5.0 | **P0** |
| **M01** | Performance y calidad transversal | Preparación/retrieval/modelos medidos; warm path eficiente; budgets explicables | M00 | **P1** |
| **M02** | Documentos: ingesta y recuperación robusta | PDF/DOCX/XLSX/CSV/código fiables, citas y navegación al fragmento | M00, M01 parcial | **P1** |
| **M03** | Creación/edición de artefactos y código | Generar y modificar archivos reales con vista previa, diff y exportación | M02 | **P1** |
| **M04** | Skills/Agents V2 y UX agentic | Skills mejor seleccionadas; Tools filtradas; timeline de actividad real | M00, M01 | **P2** |
| **M05** | Galería e imágenes a escala | OCR, búsqueda semántica, deduplicación y trabajos sobre miles de fotos | M01, M02 | **P2** |
| **M06** | Audio, voz y reuniones | Dictado, transcripción offline, segmentos, resumen y exportación | M01, M04 parcial | **P2** |
| **M07** | Video multimodal práctico | Keyframes+audio+timestamps y respuestas con procedencia temporal | M05, M06 | **P2** |
| **M08** | Acciones Android reales | Capacidades explícitas para Intents/Contactos/Calendario/archivos/apps | M04 | **P2** |
| **M09** | Workflows y automatizaciones | Planes acotados, estado persistente, pausas, reintentos, programación | M04, M08 parcial | **P3** |
| **M10** | Plugins, MCP y conectores opt-in | Tool/Source providers extensibles; conectores con autorización clara | M04, M08 parcial | **P3** |
| **M11** | Sincronización y expansión opcional | Respaldo/restauración, exportaciones y, si vale la pena, dispositivo/desktop | M03, M10 parcial | **P3** |
| **M12** | Release 1.0 y calidad final | Matriz de pruebas, recuperación de datos, accesibilidad y distribución | hitos esenciales seleccionados | **P1 final** |

**Ruta crítica sugerida:** `M00 → M01 → M02 → M03`, en paralelo controlado con `M04`; posteriormente `M05/M06 → M07`, `M04 → M08 → M09/M10`, convergencia en `M12`. No iniciar todas las ramas simultáneamente.

---

## 6. Especificación por hito

### M00 — Estabilización y cierre de 0.5.0 (ACTIVO)

**Problema:** arquitectura nueva validada en host y suite física acotada, pero la experiencia con XLSX/contexto/tiempos/chat tiene defectos observados. **Prohibido inflar este hito con funciones nuevas**.

**Frentes:**

1. **XLSX >8 MiB de representación desde ~300 KB comprimidos.** Reproducir sin alterar el archivo del usuario, medir tamaño de ZIP, shared strings, filas/celdas realmente útiles, texto expandido y pico de memoria. Compactar representación y/o procesar por streaming/lotes; conservar límites contra archivos patológicos. No elevar el umbral ciegamente.
2. **Presupuesto contextual.** Separar documento indexado **completo** de snippets **relevantes/acotados** entregados a Gemma. Soportar localizadores exactos de celda/rango/página; decidir reducción por relevancia, sin volver a los antiguos cortes arbitrarios del índice. No avisar intrusivamente cuando solo se omiten datos redundantes. Inspector registra `included/dropped/reason`.
3. **Cabecera Compose.** Ajustar altura/medidas, ellipsis y layout con títulos largos, agente/modelo, tipografía escalada y teclado abierto; evitar solapamiento con lista/chat.
4. **Latencia.** Instrumentar por fase: import → parse → ingest → index → memory/semantic lookup → context → gate → model load/warmup → TTFT → decode → render. Comparar el mismo micro-XLSX cold vs warm y sin documentos. Optimizar **solo después de conocer la causa**.
5. **Postfix F1/F2 en dispositivo.** Evidencia EG2 llega a ContextBuilder chat con fuente; índice `NEEDS_REINDEX` no se presenta como vigente y dispone de fallback sin reindex síncrono.
6. **Persistencia y UX.** Un rechazo de archivo muestra tamaño/límite real y sugerencia útil, sin corromper archivo/datos o dejar spinner permanente.

**Criterios de salida:**

- El caso real de XLSX ~300 KB se importa o produce un error acotado y **explicable con medidas**, si verdaderamente rebasa un límite razonable; incluye test de regresión representativo no sensible.
- El aviso de omisión se justifica por pérdida significativa; un turno pequeño con contexto disponible no genera banner repetitivo; el Inspector conserva detalle.
- La cabecera no recorta modelo/Agent ni invade mensajes en los escenarios fijados.
- Existe desglose de los ~180 s observados; cold/warm reportados separadamente. **No prometer una cifra objetivo absoluta sin baseline**.
- F1/F2 comprobados al menos con spot-check físico reproducible, sin manipular datos personales destructivamente.
- Host gate final PASS, APK ARM64 upgrade sin borrar datos, smoke 4 escenarios (saludo, XLSX, agente custom, desactivación) y suite `AGENTS_SKILLS_V1` según cambios.

**Bloqueo de liberación:** merge/tag solamente después de revisar evidencia física y diff; cambios de versión 0.5.0 deben permanecer en la rama hasta entonces.

### M01 — Rendimiento, presupuestos y fiabilidad

**Objetivo:** que la experiencia local sea predecible y fluida en un móvil de ~8GB RAM, con memoria y calor bajo control.

- Métricas por `requestId`, fase, modelo y estado cold/warm; TTFT de modelo y TTFT UI separados; P50/P95 tras fijar tamaño de muestra.
- Pruebas repetibles con corpus pequeño/medio/grande; medir PSS, riesgo OOM, térmica, batería, latencia EG1/EG2 y costos de reindex.
- Política de carga y liberación: modelo generativo residente mientras aporte valor; embeddings especializados por demanda; no dos modelos grandes residentes sin necesidad.
- Context reuse/KV: registrar hit/miss y razones, invalidación por agente/Skill/contexto, sin afirmar reducción sin medición.
- Context budget: reservas por bloque, compresión selectiva, deduplicación de evidencia, estimación calibrable y degradación cuando falta espacio.
- Jobs de import/indexación en segundo plano con cancelación, recuperación y progresos; evitar bloqueo de UI.
- Configuración de perfiles por dispositivo (CPU estable; GPU si gana en generación sostenida; NPU solo tras integración).
- Análisis real de costo-beneficio de modelos pequeños especializados (p.ej. FunctionGemma para acciones, ASR/OCR separados) sin asumir que modelos de 270M resuelven lenguaje libre.

**Salida:** métricas repetibles, ausencia de bloqueos durante uso normal, mejoras demostradas frente a baseline, sin regresión relevante de exactitud.

### M02 — Documentos e información: ingesta, retrieval y procedencia

**Objetivo:** que la app encuentre y responda sobre datos **reales**, no solo muestras preparadas.

- Importar PDF digital, DOCX, XLSX, CSV, TXT/Markdown, código, ZIP válido, formatos prioritarios; errores precisos para archivos no soportados.
- XLSX: nombres de hojas, A1/rangos, tipos (valor/fórmula/cache), fechas y CSV delimitado correctamente; celdas dispersas, rangos usados inflados y hojas extensas; no afirmar que se recalculan fórmulas si solo se usa valor cacheado.
- DOCX: párrafos, tablas, encabezados, saltos y partes relevantes preservadas; distinguir comentarios/notas si se soportan.
- PDF: texto, páginas y citas verificables; OCR local para escaneados como capacidad opcional, con límites y reportes de calidad.
- Codebase: rutas, archivos, símbolos, líneas, referencias entre archivos y diffs; respetar exclusiones `.gitignore`/carpetas seleccionadas.
- Retrieval híbrido EG1/EG2/FTS y consultas exactas (celda, ruta, fecha, cifra); umbral/abstención y deduplicación; no fallback arbitrario.
- Índices `READY/NEEDS_REINDEX/FAILED`, generaciones blue/green, scope de proyecto, invalidación por edición/borrado y conservación de fuentes canónicas.
- Navegación desde una cita hasta página, celda, fragmento, archivo o instante correcto.

**Salida:** corpus fixture con respuestas citadas correctas, pruebas de aislamiento y edición→reindex, y análisis de casos adversos sin degradar móvil.

### M03 — Creación y edición de archivos, código y artefactos

**Objetivo:** producir archivos entregables y modificaciones verificables, no únicamente recomendar cómo hacerlas.

- API de artefactos con formato, nombre, origen, versión, bytes, checksum, vista previa y destino de exportación SAF.
- **XLSX:** importar/editar rangos, insertar hojas, formulas y estilos básicos, respetar libros existentes; explicitar motor/no motor de recálculo y validar con suite de fórmulas.
- **DOCX:** generar plantillas, párrafos, tablas y documentos editables; conservar original/versiones.
- **PDF:** crear desde contenido estructurado o conversión soportada; anotaciones/ensamblado/lectura OCR cuando la pila local lo permita.
- **PPTX:** crear/editar diapositivas con layouts y recursos; garantizar apertura en aplicaciones compatibles.
- **Código/repo:** lectura de árbol, búsqueda contextual, cambios propuestos en diff, escritura restringida a workspace seleccionado, comandos de prueba habilitados según capacidad; snapshots antes de modificar.
- **Artefactos visuales:** preview local y exportación de imagen/diagramas donde exista motor capaz; no confundir generación descriptiva con archivo real.
- UI de comparación antes/después y mecanismos de deshacer/recuperar. Escritura local amplia, con confirmaciones especialmente para sobrescritura irreversible.

**Salida:** fixtures editables, round-trip en formatos soportados, validación de estructura/archivo abierto, exportación y recuperación tras fallo.

### M04 — Skills/Agents V2 y actividad del agente

**Objetivo:** más capacidad sin inflar cada prompt ni cargar otro Gemma.

- Ruta compatible con Skill Registry ya existente; ampliación gradual de SKILL.md, metadata enriquecida, ejemplos positivos/negativos, recursos diferidos y gestión/versionado.
- Importación de paquetes con referencias/assets; mantener código ejecutable como Plugin separado.
- **Agent Activity Timeline** estilo panel de pasos observables: Agent selected → Skill eligible/active → source lookup → tool called → tool result → artifact created → completed/error. Mostrar duración, estado, reintentos y panel expandible; **no venderlo como pensamiento privado del modelo**.
- Routing basado en señales duras primero; evaluar EG2 con conjunto de consultas y falsos positivos, comparación V1/V2, abstención y ranking medido; activar solo si mejora.
- Tools relevantes por tarea, no todas por haber activado una Skill; evaluar embeddings de Tool descriptions luego de definir baseline.
- Agentes personalizados con project preferred, scope de memoria, perfil de Tool/Source/capability, configuración de contexto; cambios de agente en chat sin filtración cruzada.
- Gestión de Skills creadas por usuario, plantillas orientadas a trabajo (documentos, código, medios, reuniones, Android) y prueba reproducible de activación.

**Salida:** timeline fiel a eventos reales; activación precisa y observable; benchmark V1/V2 antes de promover cualquier router nuevo.

### M05 — Imágenes, galería y búsqueda a escala

**Objetivo:** indexar galerías grandes y operar con ellas localmente sin bloqueos ni falsas certezas.

- Acceso mediante permisos Android / Photo Picker, sin exploración clandestina.
- Metadatos (ruta/URI, fecha, dimensiones, EXIF autorizado), hashes exactos y perceptuales, miniaturas, estado incremental por lote.
- EG2 embeddings imagen ↔ texto, agrupación temática y búsqueda por frases, ubicación solo si hay metadata/autorización, filtros tiempo/tipo.
- OCR local de imágenes y fotos de documentos, títulos/palabras reconocidas con referencias visuales.
- Duplicados exactos y visualmente similares: preview, agrupar y ofrecer limpieza reversible; no borrar automáticamente.
- Fotos numerosas (ej. **30.000**): jobs pausables/reanudables, lotes, checkpoint, cuota de RAM/almacenamiento, carga diferida y benchmark real.
- **Detección/agrupación de rostros** y comparación de similitud solamente si el usuario activa ese flujo y los modelos apropiados existen; permitir corregir clusters. Señales de foto de pantalla/impresión/suplantación solo probabilísticas, **nunca prueba concluyente de identidad o fraude**.

**Salida:** búsquedas y deduplicación medibles con datasets autorizados, sin OOM y con borrado/restauración claros.

### M06 — Audio, voz, reuniones y transcripciones

**Objetivo:** comprender y operar con audio local, manteniendo referencias por tiempo.

- Captura de audio y archivos importados bajo control del usuario; formatos y duración con límites/streaming.
- ASR offline español/inglés y, cuando sea viable, multilenguaje; comparar calidad/velocidad en Moto antes de seleccionar runtime/modelo.
- VAD y segmentación temporal; diarización opcional si calidad aceptable; no inventar quién habló.
- Corrección de transcripción, marcadores/timestamps, resumen de reunión, decisiones y tareas propuestas; aprobación antes de enviarlas a un calendario externo.
- Búsqueda semántica de pasajes y reproducción desde el instante correspondiente.
- Exportación TXT/Markdown/SRT/VTT/DOCX, con procedencia y errores honestos.
- Voz conversacional/STT-TTS opcional y manos libres, priorizando latencia/consumo y controles claros de micrófono.

**Salida:** transcripciones reproducibles y navegables; métricas WER aproximadas sobre corpus de prueba; grabación/stop/resume probados físicamente.

### M07 — Video y comprensión temporal práctica

**Objetivo:** responder con marcas de tiempo sobre videos reales sin inventar soporte nativo.

- Indexar metadatos, keyframes/escenas y pista de audio/transcripción, correlacionados por timestamp.
- Consultas por «¿cuándo ocurre X?» con evidencia de frame/intervalo y, si existe, frase de audio.
- Evitar procesar todos los fotogramas por fuerza bruta; selección adaptativa y trabajos incrementales.
- Thumbnail y reproducción desde segmento; exportar clipping cuando se implemente con motor local adecuado.
- Distinguir **keyframe-based understanding** de un auténtico encoder temporal; soportar upgrade futuro sin mentir sobre capacidad presente.

**Salida:** timeline de video, búsqueda con timestamps correctos y medidas de almacenamiento/latencia.

### M08 — Acciones Android reales y herramientas especializadas

**Objetivo:** que el asistente **haga** acciones reales en Android, dentro de lo permitido por APIs y permisos.

- Crear adaptadores nativos de capacidades: abrir app/URL, mapa, compartir archivo, ajustes pertinentes, linterna, calendario, contactos, notificaciones, archivos; documentar permisos y degradación.
- Contratos de Tools tipados con schema, resultado, cancelación y tiempo límite; precondiciones, confirmación de envío externo/borrado irreversible.
- Diferenciar Intent que abre otra app para completar la acción de operación realmente ejecutada por la nuestra.
- Inicio gradual: 5–10 acciones confiables, flujos de integración y pruebas físicas antes de generalizar.
- Soporte robusto para instrucciones en español; evaluación de parser/ejecutor pequeño tipo FunctionGemma **como opción**, cargado a demanda y comparado contra routing determinista/Gemma principal.
- Automatización avanzada de UI/Accessibility solo con permiso y pruebas específicas; no garantizar control universal de otras apps.

**Salida:** acciones reales y auditables; tasas de éxito por Tool; confirmación de estado Android tras ejecución.

### M09 — Agentes con workflows, programación y autonomía acotada

**Objetivo:** pasar de Tools unitarias a secuencias de trabajo persistentes y reanudables.

- Plan estructurado → steps → tool execution → resultado → condición de continuación; límites de pasos, tiempo, cómputo y reintentos.
- Persistencia de `workflow_run`, estados (`QUEUED/RUNNING/WAITING_USER/FAILED/COMPLETED/CANCELLED`), checkpoint y reanudación segura.
- Tareas de varias fuentes: leer documentos, resumir, generar artefacto, pedir confirmación y exportar/enviar.
- Programación local mediante WorkManager/alarmas cuando Android lo permita, respetando límites de background y batería.
- Triggers explícitos y rutinas; si el teléfono no garantiza precisión temporal, reportarlo.
- Idempotencia para operaciones repetibles, control de loops, rollback de operaciones locales cuando sea posible; pausa humana para pasos críticos.
- Separar «Agent» lógico del «Workflow/Planner» de ejecución; no convertir todos los chats en bucles autónomos.

**Salida:** workflows que sobreviven a cierre/reinicio, sin pasos duplicados ni afirmaciones de acciones no confirmadas.

### M10 — Plugins, MCP y conectores opt-in

**Objetivo:** capacidades externas sin contaminar el núcleo local ni fingir que una Skill aporta código.

- API común de providers para Tools/Sources, descripción de capacidades, versionado y resultados.
- Plugins ejecutables (JS/Python/WASM/nativo según viabilidad) separados de Skills declarativas, con permisos de usuario y ciclo de vida.
- MCP client para Tools/Resources si encaja en Android, primero transporte local o remoto explícito y tests de interoperabilidad.
- Conectores autorizados: Drive/OneDrive/Dropbox/Calendar/Gmail u otros según demanda, OAuth donde proceda, tokens protegidos y desconexión/revocación clara.
- Offline-first: caché/permisos/procedencia, cola de operaciones solo cuando se solicite y política clara de conflictos.
- Importación de Skill/Plugin community únicamente cuando el formato esté definido y mantenible; nunca «marketplace» como dependencia para funciones base.

**Salida:** al menos un Provider end-to-end documentado, desconexión limpia, degradación offline, pruebas de permisos/datos.

### M11 — Respaldo, continuidad y extensiones de plataforma

**Objetivo:** que el trabajo no dependa de un único teléfono sin posibilidad de exportación.

- Export/import local verificable de proyectos, chats, Skills, Agents, memoria aprobada, referencias a medios e índices regenerables.
- Copias con manifiesto, hash, versión y prueba de restauración; opción cifrada si el usuario la requiere.
- Decidir qué se sincroniza (datos canónicos), qué se regenera (índices) y qué nunca se exporta automáticamente (tokens secretos).
- Sincronización entre dispositivos o companion desktop como opción, con resolución de conflictos, no dependencia del producto móvil.
- Compartir/exportar proyectos y artefactos; portable Skills/Agents y Settings.

**Salida:** instalación limpia + import de respaldo produce workspace equivalente en datos fundamentales.

### M12 — Calidad de producto y release 1.0

**Objetivo:** confiabilidad global de los flujos realmente anunciados.

- Matriz de compatibilidad Android/dispositivo/runtimes; rendimiento publicado con metodología y percentiles.
- Tests automatizados host, instrumentados, nativos y pruebas reales por modalidad; QA de upgrade y datos grandes.
- Accesibilidad mínima, localización ES/EN consistente, orientación, teclado, tipografía y estados de carga/error claros.
- Actualización, recuperación de errores, exportaciones, reinstalación y soporte sin perder datos.
- Documentación de capacidades comprobadas vs experimentales; disclaimer claro para limitaciones reales de Android.
- Distribución, versionado y firma de lanzamiento coherentes (sin confundir certificado debug con firma definitiva).
- Cierre de defectos P0/P1 de flujos anunciados y plan de mantenimiento.

**Salida:** 1.0 lista por evidencia, no por cantidad de funciones. Pueden quedar Features experimentales claramente etiquetadas.

---

## 7. Mapa de funcionalidades objetivo por área

| Área | Mínimo funcional | Evolución avanzada |
|---|---|---|
| Chat/modelos | Gemma local, historial, streaming, stop, modelos GGUF/LiteRT | perfiles de inferencia, especialistas on-demand, hardware auto-benchmark |
| Conocimiento | fuentes, RAG, citas, memoria editable | cross-source reasoning medido, indexado incremental, preguntas con follow-up |
| Skills/Agents | Skills/Agents V1 deterministas, selector | activación EG2 evaluada, Tool advertisement selectivo, timeline fiel |
| Documentos | lectura de PDF/DOCX/XLSX/CSV | edición/export real, OCR, formatos y artefactos complejos |
| Código | leer snippets/repos y sugerir diffs | editar repo local, ejecutar tests, generar proyectos bajo permisos |
| Imagen/galería | ver imagen, embeddings EG2 | búsqueda 30k fotos, OCR, duplicados/rostros opt-in |
| Audio | archivos, inputs existentes | ASR, VAD, diarización, reuniones, subtítulos, TTS |
| Video | keyframes representativos | escenas+audio, temporal retrieval, clips y evidencia |
| Android | Tools limitadas actuales | calendario/contactos/intents, archivos/apps, notificaciones, tareas |
| Workflows | turnos con tools | secuencias, estados, scheduling, reanudación, auditoría |
| Plugins/nube | hooks de arquitectura | MCP y conectores explícitos, sin cloud silencioso |
| Export/sync | archivos locales y estados | backup verificable, portable workspace, sincronización optativa |

---

## 8. Diseño de experiencia de usuario

### 8.1 Navegación objetivo (adaptable a Compose)

```text
Inicio / Dashboard
├── Chats y proyectos
│   ├── conversación
│   ├── archivos/fuentes
│   ├── memoria y contexto
│   └── actividad del agente
├── Biblioteca
│   ├── documentos
│   ├── código
│   ├── galería
│   ├── audio/reuniones
│   └── video
├── Crear / Editar artefacto
├── Skills y Agents
├── Tools / Android / Integraciones
├── Workflows
└── Configuración
    ├── modelos/hardware
    ├── almacenamiento/respaldos
    ├── scopes/capacidades
    └── Inspector/diagnósticos
```

No introducir todas las secciones hasta tener funcionalidad real detrás. Preferir **descubrimiento progresivo** y navegación compacta.

### 8.2 Activity Timeline (inspiración de interfaz, no reproducción literal)

```text
09:46:01  Agent General seleccionado                 OK
09:46:01  Spreadsheet Analysis activada              OK
09:46:02  libro.xlsx: Hoja1 / A1:A25 indexados      OK
09:46:03  Evidencia seleccionada: A1:A5               OK
09:46:03  Modelo listo / contexto 1.8k estimado      OK
09:46:04  Generando respuesta...                     RUNNING
09:46:14  Respuesta y cita entregadas                OK
```

Cada fila debe corresponder a eventos **reales** instrumentados; mostrar latencias y fallos sin exponer razonamiento interno, tokens secretos ni contenido privado en logs por defecto. Si el proveedor no revela un paso, no inventarlo.

### 8.3 Estados UX fundamentales

`IDLE / PREPARING / INDEXING / WAITING_MODEL / GENERATING / TOOL_RUNNING / WAITING_USER / CANCELLING / COMPLETE / ERROR`. Distinguir progreso verdadero de spinner genérico. Visualizar archivos reales importados, motivo de descarte contextual, referencias, botones de cancelar/reintentar y errores traducidos.

---

## 9. Métricas, evaluación y decisiones por evidencia

**Métricas de rendimiento:** TTFT UI, TTFT modelo, tiempo de indexación/MB, tiempo RAG, contexto incluido/descartado, tokens/s (solo cuando se pueda medir), PSS RAM, OOM, energía/térmica, tiempo cold/warm, ocupación en disco.

**Métricas de calidad:** precisión de citas por página/rango, `no evidence` correcto, exactitud de lectura de celdas/fórmulas, recall/precisión de búsqueda, falsos positivos de activación Skill, éxito real de Tools, exactitud de transcripción con corpus anotado.

**Métricas de confiabilidad:** crash-free test runs, cancel/timeout, rollback correcto, integridad al actualizar, duplicación de workflow steps, conflictos de sync, permanencia de índices/procedencia tras edición.

**Regla:** no definir «éxito» únicamente con tiempos absolutos inventados. Crear baseline reproducible y después objetivos cuantitativos por dispositivo y tamaño de datos; cuando exista target, publicar condiciones y muestras.

### 9.1 Matriz mínima de evidencia

| Estado | Significa | No significa |
|---|---|---|
| `IMPLEMENTED` | Hay código y wiring verificables | Funcionó en Android |
| `TESTED_HOST` | Pasó unit/integration host | Integración nativa real confirmada |
| `TESTED_DEVICE_SYNTHETIC` | Pasó suite física con fixtures | Casos cotidianos y corpus grandes resueltos |
| `TESTED_DEVICE_REAL` | Reproducción en teléfono con datos reales y resultados | Soporte universal de todas las variantes |
| `PERFORMANCE_MEASURED` | Timing/RAM con metodología repetible | Calidad semántica adecuada |
| `RELEASE_READY` | Criterios del milestone cumplidos y aprobados | Ausencia absoluta de errores |

### 9.2 Gates por tarea / hito / release

- **Por tarea:** test dirigido y diff auditado, cambios coherentes, errores gestionados, commit.
- **Por hito:** suites host relevantes, informe de regresión, artefactos reproducibles, nuevos tests de fallos observados, APK cuando corresponda.
- **Por release:** instalación como actualización, integridad de datos, smoke real de funciones cambiadas, verificación de firma/versionCode, revisión humana de `git diff` y decisión explícita de merge/tag.
- **No confundir** `assembleDebugAndroidTest PASS` con ejecución de pruebas instrumentadas en dispositivo.

---

## 10. Estrategia de desarrollo autónomo por loops

### 10.1 Dos niveles

1. **Loop de milestone (supervisor):** selecciona hito autorizado, descompone dependencias, fija definición de terminado y mantiene backlog.
2. **Loop de tarea (implementador):** una tarea verificable por vez: seleccionar → inspeccionar ruta real → reproducir → modificar → test → revisar diff → commit → registrar → siguiente.

**No** permitir que el supervisor reescriba las prioridades globales, fusione releases o amplíe el alcance sin autorización.

### 10.2 Artefactos de coordinación

- `ROADMAP.md`: visión y hitos; no se reescribe por cada bug.
- `BACKLOG.json`: fuente estructurada de tareas, prioridad, dependencias, estado, pruebas y necesidad de dispositivo.
- `LOOP_RULES.md`: contrato operativo permanente de ciclos.
- `PROGRESS_TEMPLATE.md`: formato de handoff y bitácora por ejecución.
- `docs/ARCHITECTURE_OVERVIEW.md` y `AGENTS.md` del repositorio: referencia al código real; no duplicar ni reemplazarlos ciegamente.

### 10.3 Campos de backlog y transiciones

Cada tarea incluye: `id`, `milestone`, `title`, `priority`, `status`, `depends_on`, `definition_of_done`, `requires_device`, `effort`, `notes`. Estados:

`TODO → READY → IN_PROGRESS → HOST_VERIFIED → DEVICE_PENDING → DONE`  
y estados de excepción `BLOCKED / DEFERRED`.

`DONE` requiere la evidencia pedida por el criterio de salida; una tarea que necesita dispositivo no puede pasar a DONE por un test host. Trabajos sin device pueden finalizar con host, si así está definido.

### 10.4 Límites por ejecución

- Máximo **8 iteraciones/tareas lógicas** antes de consolidar informe y reiniciar con contexto fresco.
- Un cambio de producción a la vez; paralelismo permitido para **lectura, análisis y tests independientes**, no escrituras simultáneas sobre mismo árbol.
- Dos intentos razonables ante el mismo bloqueo; después registrar causa y cambiar solo a otra tarea independiente.
- Antes de acciones de alto costo o alta irreversibilidad, detenerse y pedir aprobación: migración destructiva, borrado de modelos/datos, push/merge/tag/release, sobrescritura masiva o actualización de runtime nativo.
- No fingir pruebas: si falta Moto, `DEVICE_PENDING`; si un benchmark no corre, `NOT_MEASURED`.
- Detenerse cuando: se cumple DoD del hito, falta evidencia crítica externa, el entorno impide validar o se propone un cambio arquitectónico de alcance superior.
- No instalar productos/dependencias de red ni actualizar librerías fuera del alcance para resolver el problema equivocado.

### 10.5 Priorización

Priorizar en orden: (1) pérdida de datos/crash/bloqueo funcional; (2) defectos reproducidos de uso real; (3) regresión de exactitud o evidencia; (4) latencia grave medida; (5) mejoras de capacidad con criterio claro; (6) refactors puramente estéticos.

Use `P0` para impedimentos de release, `P1` para capacidades fundamentales, `P2` para crecimiento, `P3` para futuras extensiones. Los milestones posteriores pueden avanzar por investigación/fixtures sin integrarse antes de tiempo.

---

## 11. Estrategia técnica de datos, memoria y almacenamiento

1. **Datos canónicos:** Room y archivos privados con identificadores estables, origen y versiones; incluir manejo de SAF URIs revocadas.
2. **Índices derivados:** regenerables y vinculados a hash de contenido, modelo/espacio, generación `READY/NEEDS_REINDEX` y scope; stale no equivale a actual.
3. **Memoria aprobada:** campos, scope (`USER/PROJECT/AGENT/TASK/SESSION` si son compatibles), TTL, versiones, origen y corrección/borrado; sugerencias automáticas solo opt-in futuro.
4. **Evidencia por turno:** citas y rangos coherentes, materialización bounded; una fuente completa puede producir un snippet corto sin perder información al indexar.
5. **Media:** originales referenciados sin duplicación innecesaria, derivados/miniaturas con reglas de limpieza y límites por tipo; recuperación de interrupción.
6. **Backups:** almacenar contenido canónico y manifiesto; documentar qué índices se regeneran, no exportar tokens sin una decisión expresa.
7. **Privacidad:** almacenamiento local por defecto; capacidades de red y external send opcionales, sin promesas absolutas de «cero riesgo».

---

## 12. Registro de riesgos y decisiones pendientes

| Riesgo / pregunta | Cuándo decidir | Evidencia necesaria |
|---|---|---|
| XLSX ~300KB expande >8MiB | M00 | fixture + perfil de XML/representación/memoria |
| 3 minutos para XLSX mínimo | M00 | cold/warm timings por etapa |
| ContextBuilder 4096 descarta información útil | M00/M01 | auditoría de snippets, respuestas y costo por bloque |
| EG2 V2 chat / índice stale | M00 | spot-check físico y regresión host |
| Exact tokenizer vs bytes | M01 | acceso SDK a token count y pruebas Unicode |
| 10k embeddings y RAM | M01/M05 | benchmark 1k–10k corpus antes de ANN |
| ¿EG2 router supera determinista? | M04 | benchmark etiquetado, falsos positivos y abstención |
| FunctionGemma especializado en español | M08 | exactitud de tool calls, latencia end-to-end y memoria |
| OCR/ASR modelo local más conveniente | M02/M06 | calidad, tamaño, latencia y compatibilidad |
| Externos/MCP requieren red | M10 | contratos de opt-in, auth y fallbacks |
| Soporte real de aplicaciones Android | M08 | APIs/Intents/Accessibility caso a caso |
| Respaldo/restore sin corrupción | M11/M12 | prueba de round-trip en instalación limpia |

**Backlog técnico no bloqueante heredado:** Python `io.open` vs wrapper local (el aislamiento real está en WebView/Worker/MEMFS); restricciones de documentos y CVEs por verificar; mantenimiento de fuentes y frames; parsers; reintentos concurrentes Python; comparación CPU/GPU; soporte de razonamiento por canales; normalización de errores. Priorizar solo cuando haya evidencia o un hito relacionado.

---

## 13. Criterios de “producto terminado” para 1.0

No significa implementar toda idea imaginable. Significa un conjunto anunciado de casos de uso **realmente funcionales**:

- Conversación local estable con memoria, proyectos y documentos, sin pérdida al actualizar.
- Lectura de PDF/DOCX/XLSX/CSV/código con límites claros, recuperación útil y citas comprobables.
- Generación/edición de al menos formatos priorizados con archivos exportables y reversibles.
- Skills y agentes configurables con activación precisa, tools reales y actividad observable.
- Al menos una línea multimodal madura (imagen/galería o audio/transcripción), además de imagen/entrada experimental existente; declarar claramente alcance.
- Acciones Android con contratos tipados y estados comprobables en una lista delimitada.
- Documentación y pruebas repetibles en dispositivo de referencia; latencia/memoria conocidas y aceptables según baseline.
- Backup/export que preserve datos importantes y actualización sin migración destructiva.
- Offline-first verificable y conectores opcionales, no silenciosos.

**Stretch posterior a 1.0 (si no pasa los gates a tiempo):** edición avanzada completa de todos los formatos, video profundo, sync multidispositivo, automation extensa, MCP/community plugin marketplace. No retrasar indefinidamente 1.0 por una lista infinita.

---

## 14. Primer ciclo recomendado para el próximo agente

**Hito autorizado:** `M00` únicamente.  
**Rama prevista:** `feat/0.5.0-skills-agents` (verificar estado real antes de escribir).  
**No realizar:** push, merge o tag automático.  
**Orden inicial:** reproducir XLSX >8MiB → contexto e indicador de omisión → cabecera Compose → instrumentar latencia → F1/F2 device evidence → gate. Pueden adelantarse tests e instrumentación si son dependencia técnica.

**Criterio de detención:** cuando M00 cumpla su DoD y exista una APK ARM64 verificable para la validación final del Motorola, o si hay un bloqueo que requiera los datos/archivo privado de reproducción. Si faltan datos reales, construir una fixture sintética comparable sin inventar el contenido del XLSX del usuario.

### Siguiente acción operativa

1. Copiar `ROADMAP.md`, `BACKLOG.json`, `LOOP_RULES.md` y `PROGRESS_TEMPLATE.md` a `docs/development/` del repositorio.
2. Registrar una entrada en `AGENTS.md` que indique leer `docs/development/LOOP_RULES.md` y el hito activo antes de iniciar trabajo autónomo; mantener las instrucciones existentes si no hay conflicto.
3. Ejecutar un solo loop M00 con límite de 8 iteraciones y registrar resultados.
4. Revisar artefactos y resultados host; luego prueba física dirigida y decisión de release.

---

## 15. Mantenimiento de este documento

- **Versión de roadmap:** cambiar el encabezado solo cuando cambie visión, arquitectura u orden de hitos, no con cada commit.
- **Bitácora:** evidencia, commits, pendientes y errores van a `PROGRESS.md`/backlog, no a esta página.
- **Revisión por hito:** al completar un milestone, ajustar dependencias y redefinir prioridades según resultados, sin asumir que las funciones futuras ya están implementadas.
- **Trazabilidad:** enlazar pruebas y commits exactos; no afirmar estabilidad física sin archivo de salida.

**Fin del roadmap maestro.**
