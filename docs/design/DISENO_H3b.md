# AWS · H3b: especificación visual de implementación

Versión 1.0 · 2026-10-21. Complementa `docs/design/GUIA_VISUAL_AWS.md`; si algo choca, prevalece este documento. Guárdalo en `docs/design/DISENO_H3b.md` y las imágenes en `docs/design/referencias/`.

## 1. Reglas generales

1. **Las imágenes son referencias de diseño, no especificaciones de funciones.** Implementa solo lo que la app ya soporta. Si una imagen muestra algo sin respaldo en el código (por ejemplo, la tarea "Almuerzo con Ana" sin un sistema de tareas), muestra los datos reales o un estado vacío. Nunca muestres datos de ejemplo como si fueran reales.
2. **Reutiliza Compose, la navegación y los ViewModels actuales.** No reescribas la arquitectura ni cambies la lógica para lograr una apariencia.
3. **Avanza pantalla por pantalla.** Cada pantalla se entrega con su captura real (Robolectric, 360 dp, claro y oscuro, fontScale 1.0 y 2.0) y sus pruebas de semántica.
4. **Los textos van en `strings.xml` (español por defecto),** sin textos fijos en el código.
5. **Las correcciones de la sección 5 prevalecen sobre las imágenes.**

## 2. Referencias (`docs/design/referencias/`)

| Archivo | Pantalla | Destino activo |
|---|---|---|
| `01_inicio.jpg` | Inicio con modelo listo | Inicio |
| `02_chat_acciones.jpg` | Chat con tarjeta de acción y propuesta de memoria | Chats |
| `03_confirmacion_evento.webp` | Hoja de confirmación de una acción | Chats |
| `04_memoria.jpg` | Memoria | Memoria |
| `05_chats.jpg` | Lista de chats y proyectos | Chats |
| `06_mas.jpg` | Más: acciones, conexiones y asistente | Más |
| `07_nueva_habilidad.jpg` | Crear una habilidad conversando | Más |
| `08_respuesta_con_citas.webp` | Respuesta sobre un documento con citas | Chats |
| `09_primer_uso_sin_modelo.webp` | Primer uso sin modelo | Inicio |
| `10_inicio_modo_oscuro.jpg` | Referencia de modo oscuro | Inicio |

## 3. Tokens de diseño

Define los tokens en `ui/theme/LocalAiTokens.kt` y sustituye la paleta verde actual.

| Token | Claro | Oscuro |
|---|---|---|
| Fondo | `#F7F8FA` | `#121418` |
| Superficie (tarjetas) | `#FFFFFF` | `#1C1F25` |
| Principal | `#2F6FD1` | `#7FA8F0` |
| Contenedor principal (píldoras, burbuja del usuario) | `#E8F0FC` | `#23324A` |
| Texto principal | `#1B1F24` | `#E6E8EB` |
| Texto secundario | `#5A6270` | `#A8B0BC` |
| Estado activo | `#286F47` | `#7FCB9C` |
| Estado pendiente | `#9A6700` | `#E3B964` |
| Estado desactivado | `#6B7280` | `#9AA1AD` |

- **Forma:** tarjetas con radio de 16 dp; botones, píldoras y caja de texto completamente redondeados.
- **Profundidad:** superficies planas, sin degradados y con elevación máxima de 1 dp.
- **Iconos:** Material Symbols *outlined*, de 24 dp, dentro de círculos pastel de 40 a 48 dp.
- **Espaciado:** márgenes de pantalla de 16 dp, 12 dp entre tarjetas y al menos 12 dp entre el último elemento y la barra de navegación.
- **Modo oscuro:** sigue el ajuste del sistema por defecto. Si en Más existe un selector, ofrece Sistema, Claro y Oscuro.
- **Contraste:** todos los pares de color cumplen WCAG AA (4,5:1 para texto). Agrega un test unitario que calcule el contraste de cada par de tokens.

Nota de contraste: el estado activo claro del paquete original era `#2E7D4F`; su contraste con el contenedor claro `#E0F2E6` es 4,33:1. Se ajustó a `#286F47` (5,21:1) para cumplir AA. El test `LocalAiThemeTest.registeredTextAndControlColorPairsMeetWcagAa` calcula los pares sobre los tokens activos.

## 4. Componentes compartidos

Créalos una vez y reutilízalos en todas las pantallas.

| Componente | Uso |
|---|---|
| `AwsBottomBar` | Cuatro destinos (Inicio, Chats, Memoria y Más) con icono y texto; el activo lleva una píldora en el color de contenedor principal |
| `StatusPill` | Punto e icono más texto: "Memoria activa", "Web desactivada", "Preparada", "Permitido", "No descargado" |
| `AwsCard` / `AwsListRow` | Tarjeta blanca; fila con icono circular, título, descripción opcional y flecha (mínimo 56 dp de alto) |
| `ActionCard` | Acción propuesta en el chat: icono, título, detalle y `StatusPill`, con los botones Cancelar y Confirmar |
| `ConfirmSheet` | `ModalBottomSheet` con icono grande, título, tarjeta de datos, línea informativa y dos botones a todo el ancho |
| `MemoryProposalCard` | "¿Recordar que…?" con los botones No y Recordar |
| `CitationChip` | Fuente de una afirmación ("Propuesta.pdf · pág. 2"); al tocarla abre el fragmento citado (ya existe la vista previa de fuentes) |
| `SourcesCard` | Archivo, número de fragmentos usados y la nota "Basado en fragmentos seleccionados…" |
| `Composer` | Caja redondeada con micrófono y botón de enviar; píldoras de Web y Adjuntar; botón Detener mientras genera; estado desactivado con su motivo |
| `SuggestionChips` | Fila de sugerencias con desplazamiento horizontal |

## 5. Correcciones sobre las imágenes (prevalecen)

1. **Memoria (04):**
   - "Preferencias" usa un icono de controles deslizantes (`tune`), no un engranaje.
   - "Fijada" pasa a ser la sección "Fijadas · N", con su lista.
   - Deja más margen entre Importar/Exportar y la barra de navegación.
2. **Inicio (01 y 10):**
   - Las sugerencias se desplazan en horizontal y nunca cortan el texto; con fontScale 2.0 pueden ocupar dos líneas.
   - "Tu día" muestra solo eventos reales del calendario (con permiso) y recordatorios existentes.
   - Sin permiso, muestra la fila "Permitir acceso al calendario para ver tu día".
   - Sin datos, muestra el estado vacío "No tienes eventos hoy".
3. **Más (06):** cada fila tiene una descripción breve, o ninguna la tiene; no se mezclan los dos estilos.
4. **Respuesta con citas (08):**
   - La respuesta del asistente va como texto libre sin tarjeta, igual que en 02.
   - `SourcesCard` usa un icono de documento, no de calendario.
   - Las horas en los mensajes son opcionales y deben ser coherentes en todas las pantallas de chat: o todas las muestran, o ninguna.
5. **Primer uso (09):**
   - La extensión correcta es ".litertlm".
   - Elimina el botón "Configurar modelo", porque duplica las dos filas de opciones; las filas son la acción.
   - Si quieres conservar un botón principal, que sea "Descargar Gemma E2B".
6. **Confirmación (03):** el texto informativo depende de la acción ("Se abrirá tu calendario…", "Se abrirá tu reloj…"). Nunca dice que la acción ya se ejecutó.
7. **Todas:** no muestres checks de lectura, estados "En línea", llamadas o videollamadas, agentes múltiples ni conexiones de correo.

## 6. Criterios de aceptación por pantalla

| Pantalla | Criterio verificable |
|---|---|
| Barra inferior | 4 destinos idénticos en todas las pantallas; Habilidades accesible desde Más; prueba de navegación |
| Inicio | El estado del modelo es real: "Listo" solo con un modelo cargado; las píldoras reflejan la memoria y la web reales; sin datos inventados |
| Primer uso | Se muestra cuando no hay modelo: estado "No descargado", composer desactivado con su motivo y las dos opciones conectadas a la importación y descarga existentes |
| Chat | Burbuja del usuario sin checks; respuesta del asistente sin burbuja; `ActionCard` conectada a los estados reales de H2 (preparada, abierta o completada según el usuario); `MemoryProposalCard` crea un registro `PENDING` |
| Confirmación | `ConfirmSheet` para toda acción de H2 antes de llamar a `startActivity`; Cancelar no ejecuta nada |
| Citas | `CitationChip` por afirmación citada, con página o celda cuando exista; abre la vista previa de la fuente; incluye `SourcesCard` |
| Memoria | Pendientes arriba con Aprobar y Descartar, categorías, fijadas, búsqueda, importar/exportar y "Olvidar definitivamente" con confirmación |
| Chats | Lista con búsqueda; proyectos identificados; botón + crea un chat |
| Más | Secciones Acciones, Conexiones (con el estado real de cada permiso) y Asistente (Habilidades, Modelo y rendimiento, Privacidad), más "Avanzado" con el diagnóstico |
| Nueva habilidad | Usa el flujo existente de `SkillDraftProposal`; el borrador queda desactivado hasta que el usuario lo active |
| Accesibilidad | Objetivos táctiles de al menos 48 dp, `contentDescription` en español, títulos marcados como encabezado, contraste AA y nada cortado con fontScale 2.0 |
| Modo oscuro | Todas las pantallas con los tokens oscuros; captura de cada una |

## 7. Orden recomendado

1. Tokens, tema y componentes compartidos.
2. Barra inferior y navegación de 4 destinos.
3. Chat, confirmación y citas, que es donde se usan las funciones de H1 y H2.
4. Memoria.
5. Inicio y primer uso.
6. Chats y Más.
7. Nueva habilidad.

Entrega cada paso con su commit, sus pruebas y sus capturas. No hagas cambios de lógica salvo para conectar datos reales que ya existen.

## 8. Implementación verificada — fases 1 y 2

El commit `35f23d814d7850e3d18e642b4d9b94a3511c6e7c` implementa los fundamentos/navegación de cuatro destinos y el incremento visual del chat: jerarquía de encabezado, transcript/referencias, tarjetas de acción y composer usando componentes y callbacks existentes. Es una entrega parcial de H3b; no afirma que estén implementadas todas las filas de los criterios anteriores.

La prueba HOST exacta informa 37/37 tests Compose/UI PASS. Las capturas Robolectric de Inicio, Chats, Memoria, Más, Modelos y chat están bajo `docs/validation/h3b-chat-phase2-4fc5637/attempt-07/captures/`; el chat usa `H3B-SYNTHETIC-01` y la navegación refleja el estado vacío/sin modelo de la app de prueba, sin datos del usuario. Inicio muestra el estado vacío real de modelo. A fontScale 2.0 el contenido se desplaza y el rótulo Memoria ocupa dos líneas. La captura de la actividad no incluye el `ModalBottomSheet` separado; su interacción se validó con semántica/callback, no visualmente. EMULATOR y PHYSICAL_DEVICE no se ejecutaron.

Fase 3 — completar y pulir Memoria, Inicio/primer uso, Chats/Más y flujo de nueva habilidad — sigue pendiente de revisión y autorización. No inferir que esos requisitos están cerrados por compartir navegación o capturas.
