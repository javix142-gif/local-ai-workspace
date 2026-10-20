# Local AI Workspace (AWS): objetivo del producto y meta esperada

> Documento rector del producto. Define **qué tiene que lograr AWS para considerarse terminado y útil**, en un orden que respeta las prioridades del usuario. Complementa el roadmap existente (`docs/development/plan-source/2026-10-08/Local_AI_Workspace_ROADMAP.md`) y debe prevalecer sobre él cuando haya conflicto de prioridad.
> Base: auditoría independiente del SHA `aed43ee` (2026-10-19).

---

## 1. Objetivo en una frase

**AWS es un asistente personal privado que vive en el teléfono: recuerda cómo es y qué prefiere su usuario, lo ayuda a organizar su día (agenda, correo, mensajes, tareas) y ejecuta acciones reales del teléfono con su confirmación, todo con Gemma 4 E2B local y sin enviar datos fuera salvo que el usuario lo active.**

## 2. Usuario, dispositivo y restricciones

| Aspecto | Definición |
|---|---|
| Usuario | Una sola persona, dueña del teléfono; uso diario en español |
| Dispositivo objetivo | Motorola Moto G86 Power, 8 GB de RAM, Android 15 |
| Modelo generativo | Gemma 4 E2B (LiteRT-LM), **único modelo residente** |
| Embeddings | EmbeddingGemma 2, que da servicio a memoria, documentos y enrutado |
| Conectividad | Offline por defecto; la red solo se usa para lo que el usuario active (búsqueda web, conectores, descarga de modelos) |
| Permisos | Solo permisos estándar solicitados en el momento de uso; nunca permisos privilegiados ni *root* |
| Principio de acción | El asistente **prepara**, el usuario **confirma** y la app **informa del resultado real**. Nunca dice que algo ocurrió sin evidencia |

## 3. Las tres metas principales (en orden de prioridad)

### Meta 1: Recordar y adaptarse al usuario

**Resultado esperado:** después de unos días de uso, las respuestas reflejan el idioma, el tono, el formato, los nombres, los proyectos y las preferencias del usuario sin que tenga que repetirlos.

Escenarios que deben funcionar:

| El usuario… | AWS debe… |
|---|---|
| Dice "prefiero respuestas cortas y en viñetas" | Proponer guardar la preferencia y, una vez aprobada, aplicarla en **todas** las conversaciones, aunque la pregunta no tenga relación |
| Menciona "mi jefa se llama Carla" en una conversación | Proponer el recuerdo como pendiente; tras aprobarlo, usarlo cuando se hable de "mi jefa" |
| Cambia de opinión ("ahora prefiero respuestas largas") | Detectar la contradicción, mostrar ambas versiones y reemplazar la anterior al confirmar |
| Pide "olvida lo de Carla" | Borrarlo **físicamente** (sin dejar copias ocultas) y confirmar que se borró |
| Usa "MEMORIA → IMPLEMENTAR → COPIAR PROMPT" en otro asistente | Importar el ZIP resultante, mostrar duplicados y contradicciones, y activar solo lo que el usuario apruebe |
| Exporta su memoria | Obtener un ZIP completo y reimportable, sin perder tipo, ámbito ni fijado, aunque tenga miles de recuerdos |

Criterios de aceptación:
- Las preferencias y procedimientos aprobados de ámbito usuario entran **siempre** al contexto, con un presupuesto fijo.
- Existe una sola base de memoria, que usan todos los chats (con o sin adjuntos).
- Ninguna memoria pasa a activa sin aprobación explícita.
- Borrar una memoria la elimina físicamente y borrar un proyecto borra sus memorias.

### Meta 2: Organizar el día del usuario

**Resultado esperado:** el usuario puede preguntar y gestionar su agenda, recordatorios, correo y mensajes conversando.

| El usuario dice… | AWS debe… | Estado final informado |
|---|---|---|
| "¿Qué tengo mañana?" | Leer el calendario Android y responder con los eventos y sus horas | Datos leídos (con permiso) |
| "Agenda reunión con Pedro el jueves a las 10" | Abrir el formulario de evento **prellenado** | `PREPARED`, y después `OPENED` |
| "Recuérdame llamar al banco a las 17:00" | Crear una alarma o recordatorio con la app de reloj | `PREPARED` → `CONFIRMED_BY_USER` |
| "Escríbele a Ana que llego tarde" | Buscar el contacto y abrir el SMS o WhatsApp con el borrador | `OPENED`; nunca dice "enviado" |
| "Redacta un correo para el cliente sobre el retraso" | Generar el texto y abrir el cliente de correo con el borrador | `OPENED` |
| "Resume mis correos de hoy" (fase posterior) | Con Gmail u Outlook conectado por OAuth, leer y resumir citando cada correo | Solo si el usuario conectó la cuenta |

Criterios de aceptación:
- El **calendario y los contactos** se leen con permisos estándar.
- Las **alarmas, eventos, mensajes y correos** se crean por Intents con confirmación, sin permisos especiales.
- La app distingue y muestra al usuario estos tres estados: *abrí la app*, *dejé la acción preparada* y *el usuario confirmó*.
- Gmail y Outlook son una fase posterior, opcional y desactivada por defecto.

### Meta 3: Ejecutar funciones reales del teléfono

**Resultado esperado:** el asistente actúa sobre el teléfono con un catálogo cerrado y tipado de acciones.

Catálogo mínimo (sin permisos privilegiados):

| Acción | Mecanismo |
|---|---|
| Alarma y temporizador | `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` |
| Crear un evento | `CalendarContract` mediante `ACTION_INSERT` |
| Leer la agenda | `CalendarContract.Instances` (`READ_CALENDAR`) |
| Buscar un contacto | `ContactsContract` (`READ_CONTACTS`) |
| Llamar (marcador) | `ACTION_DIAL` |
| Mensaje o correo en borrador | `ACTION_SENDTO` / `ACTION_SEND` |
| Navegación y mapas | `geo:` / Intents de mapas |
| Compartir y abrir archivos | `ACTION_SEND` / `ACTION_VIEW` y SAF |
| Abrir ajustes concretos | `Settings.ACTION_*` |
| Notas y tareas propias | Almacén local de AWS, con recordatorios por notificación |

Criterios de aceptación:
- Cada acción es una herramienta tipada con su esquema, nivel de riesgo y política de confirmación.
- Toda acción con efectos pide confirmación.
- Se evalúa **AppFunctions** cuando el Moto lo soporte. **AccessibilityService** queda fuera del alcance inicial y, si se usa, será opcional y con límites claramente explicados.

## 4. Metas complementarias

| Meta | Resultado esperado | Nivel requerido |
|---|---|---|
| **Documentos** | Leer, buscar y citar PDF, DOCX, XLSX, PPTX y código; crear documentos nuevos; editar los documentos generados por AWS con versiones | Ya existe en buena parte. Pendiente: PDF con cualquier carácter y edición de documentos externos conservando el formato (fase posterior) |
| **Búsqueda web** | Interruptor por chat, desactivado por defecto; resultados con fuente citada y tratados como datos no confiables | Proveedor configurable, sin navegación automática |
| **Skills** | Crear una Skill conversando con Gemma: borrador → validación → revisión → guardado → activación. También importar archivos SKILL.md | El parser y el registro ya existen; falta el flujo conversacional |
| **Agentes** | Configuraciones lógicas (rol, Skills, herramientas, ámbitos de memoria) sobre el mismo modelo | **No se crean subagentes ni modelos adicionales** salvo evidencia de beneficio |
| **Automatizaciones** | Rutinas acotadas, como un resumen diario de la agenda o recordatorios, con permisos y estado recuperable | Fase posterior a la Meta 3 |

## 5. Metas de calidad en el Motorola

Los valores son **propuestas iniciales**. Hay que fijarlos con la primera medición física y no darlos por cumplidos sin evidencia del dispositivo.

| Métrica | Meta propuesta |
|---|---|
| Primer token en chat "caliente" (modelo ya cargado, sesión reutilizada) | ≤ 2 s |
| Primer token en frío (cargar el modelo) | ≤ 15 s, con progreso visible |
| Velocidad de generación en CPU | ≥ 8 tokens/s; la GPU se usa solo si la medición demuestra mejora |
| Memoria total de la app con E2B y EG2 cargados (PSS) | ≤ 4,5 GB, sin cierres por falta de memoria en 30 minutos de uso |
| Ventana de contexto útil | La máxima que el Moto soporte sin falta de memoria (medir 8K, 16K y 32K); estimación de tokens con error < 15 % |
| Preferencias aplicadas | El 100 % de las preferencias fijadas está presente en el contexto |
| Acierto en elegir la herramienta (Gemma E2B) | ≥ 85 % en un conjunto de 40 órdenes en español |
| Acciones con confirmación | 0 acciones con efectos sin confirmar |

## 6. Fuera de alcance (por ahora)

- Un segundo LLM residente, subagentes con modelos propios o la nube como respaldo automático.
- Permisos privilegiados, *root*, o leer y enviar SMS directamente (solo se abren borradores).
- Automatización por accesibilidad de otras apps.
- Sincronización con escritorio o con otros dispositivos.
- Fidelidad OOXML avanzada más allá de lo ya construido, hasta completar las Metas 1 a 3.

## 7. Orden de entrega recomendado

Cada hito termina con una **APK instalable del commit actual** y una **prueba corta en el Motorola** (menos de 15 minutos, con un *script* preparado).

| Hito | Contenido | Tareas existentes relacionadas | Resultado visible para el usuario |
|---|---|---|---|
| **H0: Base confiable** | CI en verde, ramas unificadas, APK del commit actual y primera medición en el Moto | M00-07, M00-10 | Instalo la versión actual y sé cuánto tarda y cuánta memoria usa |
| **H1: Memoria que funciona** | Perfil siempre incluido, una sola memoria, borrado físico, exportación completa y propuestas de memoria desde el chat | (nuevas; parte de M04) | La app recuerda mis preferencias y las aplica |
| **H2: Acciones del teléfono** | Catálogo de Intents con confirmación y estados reales | M08-01 a M08-04 (**adelantadas**) | "Pon una alarma", "agenda una reunión", "escríbele a Ana" |
| **H3: Mi día** | Leer calendario y contactos, resumen diario y tareas propias con recordatorio | M08-03, parte de M09 | "¿Qué tengo mañana?" funciona con mis datos |
| **H4: Información externa** | Búsqueda web opcional con citas; primer conector OAuth (Gmail u Outlook) | M10-04 y nuevas | Respuestas actuales con fuente; resumen de correos |
| **H5: Productividad y extensiones** | Skills creadas conversando, PDF Unicode, edición de documentos externos y AppFunctions | M03, M04, M08-06 | Documentos y Skills a medida |
| **1.0** | Las metas 1 a 3 cumplen sus criterios en el Moto y la calidad de la sección 5 está medida | M12 | Asistente personal usable a diario |

## 8. Definición de "AWS 1.0 útil"

AWS 1.0 está terminado cuando, **en el Motorola y con la APK del commit publicado**, el usuario puede pasar un día normal y:

1. El asistente responde con su estilo y conoce sus datos aprobados, sin que tenga que repetirlos.
2. Consulta su agenda y crea eventos, alarmas y recordatorios conversando.
3. Prepara mensajes y correos a sus contactos, y AWS informa con honestidad si quedaron solo preparados o confirmados.
4. Pregunta sobre sus documentos y recibe respuestas citadas.
5. Puede borrar, exportar e importar su memoria con control total.
6. Todo funciona sin conexión, salvo lo que el usuario haya activado expresamente.

## 9. Reglas para el desarrollo con Codex

- **Prioridad:** primero lo que cumple metas del usuario (secciones 3 y 7); después la fidelidad de formatos y el refinamiento.
- **Estado:** un único archivo de estado breve (rama, HEAD, última APK, pendientes en el dispositivo y próxima tarea). Las evidencias pesadas van fuera de git.
- **Backlog:** debe reflejar el estado real (`HOST_VERIFIED`, `DEVICE_PENDING`, `DONE`). No se deja todo en `TODO`.
- **Pruebas:** tests de lógica en cada cambio y una prueba física corta por hito. No se repiten auditorías ya hechas sobre commits consecutivos.
- **Honestidad:** no se declara validación física ni éxito de acciones sin evidencia (se conserva la práctica actual).
