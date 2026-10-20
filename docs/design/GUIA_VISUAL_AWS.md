# AWS: guía visual y de accesibilidad

> Para Codex. Se aplica **junto con** los hitos funcionales (H1 memoria, H2 acciones, H3 mi día), no como un proyecto aparte. Cada pantalla nueva se construye ya con estos componentes.

## 1. Estado actual (verificado en el código, SHA `aed43ee`)

| Aspecto | Hoy | Problema |
|---|---|---|
| Idioma | 416 textos fijos en inglés dentro de Compose (`Text("Cancel")`…); `strings.xml` tiene 1 entrada | La UI y TalkBack hablan en inglés a un usuario hispanohablante; no se puede traducir |
| Navegación | `Workspace` · `Models` · `Settings` | Está pensada para quien desarrolla. La memoria, prioridad n.º 1, queda escondida en Settings → "Structured memory" |
| Ajustes | Lista de enlaces de texto pequeños: "Semantic search", "Logical Agents", "Skills" | Jerga técnica, sin iconos ni descripciones y con poca jerarquía |
| Cabecera del chat | "General Agent · Gemma 4 E2B · Local · Ready" | Muestra detalles técnicos que el usuario no necesita ver |
| Mensajes | Etiquetas visibles "You" / "Assistant response" | Ruido visual. Esa información corresponde a la semántica de accesibilidad |
| Paleta | `ui/theme/LocalAiTokens.kt`: verde azulado sobrio, con tema claro y oscuro | **Está bien y se conserva.** Contraste de `#3E746D` sobre blanco ≈ 5,4:1 y sobre el fondo `#F6F7F4` ≈ 5,0:1 (cumple AA) |
| Pruebas visuales | Capturas Robolectric a 360/393/411 dp y fontScale 1.0–2.0 | **Buena práctica: mantenerla** |

## 2. Principios

1. **Primero lo humano, después lo técnico.** El modelo, el backend y los tokens van en *Más → Avanzado*.
2. **Español por defecto**, con frases cortas, tratamiento de tú y verbos al inicio: "Crear evento", no "Creación de evento".
3. **Las acciones se ven antes de ocurrir.** Cada acción muestra qué va a pasar, su estado real y cómo cancelarla.
4. **La memoria es visible y controlable.** El usuario ve qué se sabe de él, lo aprueba y puede borrarlo.
5. **Calma visual.** Se mantienen la paleta actual, un solo botón principal por pantalla, superficies planas y mucho aire.

## 3. Navegación propuesta

Barra inferior de 4 destinos con icono y texto (nunca solo icono):

| Destino | Contenido | Hito |
|---|---|---|
| **Hoy** | Saludo y fecha; estado ("Privado · listo"); *Tu día* (eventos y recordatorios); "N recuerdos por revisar"; sugerencias rápidas; caja "Pregúntame algo" con micrófono | Inicio en H1; agenda real en H3 |
| **Chats** | Conversaciones y proyectos (lo que hoy es Workspace) | Ya existe |
| **Memoria** | Pendientes por aprobar arriba; categorías (Preferencias, Personas, Proyectos, Rutinas, Fechas importantes); búsqueda; Importar y Exportar | H1 |
| **Más** | Modelo y rendimiento (hoy "Models"), Habilidades, Asistentes, Búsqueda en documentos, Privacidad, Avanzado (diagnóstico) | Reorganizar |

## 4. Glosario: de jerga a lenguaje claro

| Hoy | Propuesta |
|---|---|
| Workspace | Chats |
| Models | Más → Modelo y rendimiento |
| Structured memory | Memoria |
| Semantic search | Búsqueda en tus documentos |
| Skills | Habilidades |
| Logical Agents / General Agent | Asistentes / Asistente |
| Developer / Diagnostics | Avanzado |
| Ready / Loading model | Listo / Preparando el asistente… |
| Context Inspector | Qué usó el asistente |
| TOKEN_BUDGET y otros códigos | Texto claro ("No cupo todo el documento; usé las partes más relevantes") y el código solo en Avanzado |

## 5. Componentes nuevos (Compose)

| Componente | Uso | Detalles |
|---|---|---|
| `StatusPill` | Estado del asistente y de las acciones | Icono más texto, nunca solo color. Estados: Listo, Preparando, Sin modelo, Preparada, Abierta, Confirmada, Cancelada |
| `ActionCard` | Acción que propone el asistente en el chat (H2) | Icono, título ("Alarma · mañana 17:00"), detalle, `StatusPill`, botones Cancelar / Confirmar. **Nunca** dice "enviado" ni "creado" si solo se abrió otra app |
| `ConfirmSheet` | `ModalBottomSheet` antes de ejecutar | Icono grande, resumen en lenguaje claro, aviso de qué pasará ("Se abrirá tu calendario…"), botón principal y Cancelar de 48 dp o más, vibración breve al confirmar |
| `MemoryProposalCard` | Propuesta de recuerdo en el chat o en Hoy (H1) | "¿Recordar que…?" con No / Recordar; queda `PENDING` hasta aprobarse |
| `MemoryCategoryRow` | Lista de Memoria | `ListItem` con icono, nombre y cantidad; acciones personalizadas para TalkBack |
| `SuggestionChips` | Hoy y chat vacío | 3–4 sugerencias como "Resume mi día", "Pon una alarma" |
| `Composer` | Caja de escritura | Adjuntar, interruptor "Web: no/sí" con su estado escrito, micrófono, enviar; durante la generación, un botón **Detener** visible |
| `CitationChip` | Fuentes de la respuesta | Nombre del archivo y página o celda; al tocarlo se abre el fragmento |

En los mensajes del chat, el usuario va en burbuja (`primaryContainer`, ancho máximo del 85 %) y el asistente como texto libre sin burbuja, con interlineado amplio. Se quitan las etiquetas visibles "You" / "Assistant response".

## 6. Tipografía, espacio y forma

- Escala tipográfica de Material 3. Cuerpo a 16 sp con interlineado de 24 sp; metadatos a 12 sp como mínimo; título de pantalla a 22 sp.
- Retícula de 8 dp, márgenes de pantalla de 16 dp y radio de 12–16 dp en las tarjetas (las píldoras, totalmente redondeadas).
- Nunca alturas fijas en contenedores con texto, para que todo crezca con fontScale 2.0.
- Iconos de Material Symbols *outlined*, de 20–24 dp.

## 7. Lista de accesibilidad (obligatoria en cada pantalla)

1. **Contraste:** texto ≥ 4,5:1 e iconos y bordes ≥ 3:1. Añade un test unitario que calcule la razón WCAG de los pares de tokens en claro y oscuro.
2. **Objetivos táctiles de 48 dp o más** (`Modifier.minimumInteractiveComponentSize()`), incluidos los enlaces de Ajustes, que hoy son texto.
3. **Texto grande:** probar a fontScale 1.0, 1.3, 1.6 y 2.0 en 360 dp; nada se corta ni se superpone. Esto ya se hace y hay que mantenerlo.
4. **TalkBack:**
   - Títulos marcados con `semantics { heading() }`.
   - Tarjetas con `mergeDescendants`.
   - Mensajes leídos como "Tú dijiste: …" / "El asistente respondió: …".
   - Botones de icono con `contentDescription` en español.
   - Acciones personalizadas (Aprobar, Descartar, Borrar) en las filas.
   - `stateDescription` en los interruptores ("Búsqueda web desactivada").
5. **Anuncios:** usa `liveRegion = Polite` solo para cambios de estado ("Generando respuesta", "Respuesta lista", "Alarma preparada"). **Nunca** se anuncia token por token.
6. **El color no es la única señal:** cada estado lleva icono y texto.
7. **Movimiento reducido:** respeta la escala de animaciones del sistema; nada de cursores parpadeantes ni animaciones largas.
8. **Modo oscuro** completo (ya existe). El color dinámico de Material You queda opcional y desactivado por defecto, para mantener la identidad.
9. **Fechas y horas** según la configuración regional del dispositivo (`java.time` con `Locale` del sistema).
10. **Voz:** el micrófono siempre visible en el composer (la entrada de audio ya existe).

## 8. Tono de los textos

- Errores: qué pasó y qué hacer. "No pude abrir el calendario. Revisa que tengas una app de calendario instalada."
- Estados vacíos como invitación: "Aún no hay recuerdos. Cuéntame tus preferencias y te propondré guardarlas."
- Confirmaciones en pasado y sin exagerar: "Alarma preparada", no "¡Alarma creada exitosamente!".

## 9. Orden de implementación (dentro de los hitos)

1. **H0/H1:** extraer todos los textos a `strings.xml` (español por defecto) y aplicar el glosario. No cambia el aspecto visual; es la base para TalkBack y la traducción.
2. **H1:** navegación de 4 destinos; pantalla Memoria y `MemoryProposalCard`; Hoy en versión inicial (sin agenda).
3. **H2:** `ActionCard`, `ConfirmSheet`, `StatusPill` y el composer con el estado de la web.
4. **H3:** "Tu día" en Hoy, con datos reales del calendario.
5. Ajustes ("Más") reorganizados con `ListItem` de icono, título y descripción; diagnóstico en Avanzado.

**Pruebas por pantalla:** captura Robolectric en claro y oscuro a 360 dp con fontScale 1.0 y 2.0, más un test de semántica (`onNodeWithContentDescription`, `assertHeightIsAtLeast(48.dp)`). Sin carpetas de evidencia en git.
