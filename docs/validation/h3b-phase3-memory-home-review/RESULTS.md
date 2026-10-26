# H3b — capturas para revisión visual

Las 26 capturas son de Compose/Robolectric a 360×800 dp, con temas claro/oscuro y fontScale 1.0/2.0. Se generaron sobre `194bf4024b59ee7ddf641303e48c7dcfb120241c` más los cambios de localización y desplazamiento incluidos en el mismo commit que este informe. Son fixtures de prueba: no contienen datos del usuario ni prueban inferencia o el Motorola.

| Pantalla | Claro 1.0 | Oscuro 1.0 | Claro 2.0 | Oscuro 2.0 |
|---|---|---|---|---|
| Primer uso sin modelo | [claro 1.0](captures-20261026/SINTETICO-primer-uso-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-primer-uso-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-primer-uso-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-primer-uso-oscuro-2.0.png) |
| Inicio con agenda | [claro 1.0](captures-20261026/SINTETICO-inicio-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-inicio-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-inicio-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-inicio-oscuro-2.0.png) |
| Chats | [claro 1.0](captures-20261026/SINTETICO-chats-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-chats-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-chats-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-chats-oscuro-2.0.png) |
| Más | [claro 1.0](captures-20261026/SINTETICO-mas-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-mas-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-mas-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-mas-oscuro-2.0.png) |
| Memoria | [claro 1.0](captures-20261026/SINTETICO-memoria-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-memoria-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-memoria-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-memoria-oscuro-2.0.png) |
| Nueva habilidad | [claro 1.0](captures-20261026/SINTETICO-habilidad-claro-1.0.png) | [oscuro 1.0](captures-20261026/SINTETICO-habilidad-oscuro-1.0.png) | [claro 2.0](captures-20261026/SINTETICO-habilidad-claro-2.0.png) | [oscuro 2.0](captures-20261026/SINTETICO-habilidad-oscuro-2.0.png) |

Nueva habilidad usa el `ChatScreen` real con un proyecto sintético y sin modelo; la fixture monta directamente el componente porque el acceso normal exige un modelo preparado. El compositor permanece desactivado. Capturas desplazadas de su explicación: [claro 2.0](captures-20261026/SINTETICO-habilidad-claro-2.0-desplazada.png) · [oscuro 2.0](captures-20261026/SINTETICO-habilidad-oscuro-2.0-desplazada.png). El título se elide a escala 2.0. Algunas capturas de la actividad de Chats/primer uso no muestran la barra inferior en el PNG; requiere revisión visual, aunque las pruebas semánticas de Chats 2.0 comprobaron sus cuatro etiquetas.

Inicio contiene tres eventos y un modelo listo **sintéticos**; Memoria usa recuerdos sintéticos. Chats, Más y Primer uso usan una aplicación de prueba sin datos personales. Las capturas históricas del checkpoint anterior se conservan en `attempt-01/captures/`.

Validación local del árbol fotografiado: 25/25 pruebas focalizadas HOST, incluyendo 22 de captura y tres de navegación/localización, exit 0; tras actualizar un selector localizado, su prueba pasó 1/1. La suite completa no se repitió. Lint y compilación AndroidTest se registran en [STATUS](../../../STATUS.md).

El [emulador 40027014969](https://github.com/javix142-gif/local-ai-workspace/actions/runs/40027014969) probó `194bf402`: 18 PASS y 6 SKIPPED por modelos ausentes; el [QA 40027014977](https://github.com/javix142-gif/local-ai-workspace/actions/runs/40027014977) pasó para el mismo SHA. El [run anterior 39856422426](https://github.com/javix142-gif/local-ai-workspace/actions/runs/39856422426) falló con dos pruebas, luego corregidas; se conserva como histórico. Estos runs no prueban los cambios locales posteriores a `194bf402`. PHYSICAL_DEVICE: NOT_RUN.
