# HOTFIX 0.2.3 — Thinking de Gemma 4 E2B

Fecha: 2026-10-06. Base inspeccionada: **0.2.2 / versionCode 19**. Entrega: **0.2.3 / versionCode 20**, LiteRT-LM **0.17.1 sin cambios**.

**Estado:** implementación y comprobaciones de host; validación de esta versión en Motorola pendiente. Los PASS físicos de Tools, documentos, EmbeddingGemma/RAG, Python, Vision y Audio en 0.2.2 fueron comunicados por el usuario. No se presentan como pruebas físicas de 0.2.3.

## A. Causa raíz

**Causa reproducida: presupuesto total agotado por thinking sin límite separado.** La app 0.2.2 construía `ThinkingConfig(enableThinking=true)` tanto para Conversation como para send. El valor predeterminado real de `thinkingTokenBudget` es **-1, ilimitado**. Thinking y final comparten `maxOutputToken`.

Hay una diferencia concreta con el probe anterior: `docs/validation/hotfix022/probe_gemma.py` utilizaba explícitamente **thinking_token_budget=128**. Su PASS en Linux no validaba la configuración ilimitada utilizada por la app.

El nuevo probe con el mismo bundle real, CPU, contexto 4096, sampling fijo y el prefijo obligatorio de política de la app reprodujo:

- output 256, thinking -1: **256 tokens totales nativos**, 253 callbacks thought, **0 callbacks de final**, cierre normal del iterador, sin respuesta final;
- output 256, thinking 128: **140 tokens totales nativos**, thought presente, final presente y respuesta validada 7;
- output 512/1024, thinking -1: final presente, respuesta 7, 371 tokens totales en cada ejecución.

Los callbacks no son contadores de tokens. Los 253/128 son callbacks; sólo `lastDecodeTokenCount` proporciona el contador total nativo.

En 0.2.2, `LiteRtLmService.generate()` comprobaba `chunks > 0` **antes** de `getBenchmarkInfo()`. Thought se ocultaba correctamente, por lo que thought-only significaba cero chunks visibles. Se lanzaba `NO_OUTPUT_FROM_NATIVE_CALLBACK`, convertido en `NO_TOKENS`, perdiendo la evidencia del límite. `ProductionValidationBackend.thinking()` además abortaba en el primer subcaso fallido.

**Run físico exacto:** el número de tokens, subcaso y causa específica de esos dos runs Moto de ~39 s son **NO DETERMINADO**, porque sus contadores no fueron exportados. La causa se reprodujo en host y es coherente con la ruta auditada, pero no se sustituye evidencia física por Linux.

No existe un timeout de 39/40/45 s en esa ruta: preparación de prompt 30 s, espera de primer contenido visible 180 s, decode-idle 120 s, generación total 900 s, caso de validación 180 s. No se aumentó ningún timeout. La explicación cuantitativa de los ~39 s físicos es **NO DETERMINADO**.

## B. Comportamiento real del SDK

APIs verificadas en fuentes vendorizadas:

- `litert-compat/src/main/java/com/google/ai/edge/litertlm/Config.kt`: `ThinkingConfig(enableThinking: Boolean=true, thinkingTokenBudget: Int=-1)`;
- `Conversation.kt`: `sendMessageAsync(... thinkingConfig, maxOutputToken ...)`, `MessageCallback.onMessage(Message)` y `onDone()`;
- `Message.kt`: canales como `Map<String,String>`, no enum de canal;
- `Conversation.kt`, `jsonToMessage()`: contenido principal en `Contents` / `Content.Text`, contenido de canales separados en `Message.channels`;
- referencia nativa 0.17.1 preservada en `docs/validation/hotfix022/conversation.cc`: `SetThinkingTokenBudget`, con delimitadores de canal obtenidos por el SDK, no insertados manualmente por la app.

`onDone()` no entrega una respuesta adicional ni finish reason. No se inventó un mecanismo de completion-final. La salida final observada llega en callbacks `onMessage`, como contenido principal; no necesita un canal llamado `final`.

## C. Output budget

El límite incluye **thought + final + tokens de control generados**. No se modificó el límite normal del usuario ni se introdujo un floor global.

Política normal para thinking efectivo ON:

```
maxOutput = límite configurado por el usuario, sin cambios
thinkingTokenBudget = min(128, maxOutput / 2)
```

Se utiliza la API oficial. Limita razonamiento y deja margen para final/control; no promete una reserva exacta de tokens finales. OFF mantiene `enableThinking=false`; el campo -1 de su configuración no activa thinking y el SDK lo ignora para esa modalidad.

Diagnostics registra `configuredMaxOutput`, `effectiveMaxOutput` y `thinkingTokenBudget`. En uso normal configured=effective. No existe `THINKING_BUDGET_FLOOR`, porque no se aplicó un floor.

No hay contadores nativos separados de thought/final en la API inspeccionada: `thoughtTokenCount`, `finalTokenCount` y `finishReason` se exportan como **null**. `outputLimitReached` compara el contador total nativo con el límite; es una clasificación de la app, no un finish reason nativo.

Probes temporales 256/512/1024: sólo ViewModels de validación pueden aplicar el override. Reproducen deliberadamente thinking -1; la misma `ThinkingConfig` cargada se utiliza al enviar, evitando sobrescribir el override. No cambian preferencias ni parámetros del modelo instalado. El reporte conserva la configuración normal del modelo y el límite utilizado por cada request.

## D. Flujo de callbacks e instrumentación

```
ChatViewModel / AssistantRouting
→ ConversationPrompt.enableThinking y modo solicitado
→ ModelLoadConfig.conversation
→ LiteRtConversationPayload / IPC
→ LiteRtLmService.load: ConversationConfig + ThinkingConfig oficial
→ configuración activa conservada para sendMessageAsync
→ MessageCallback.onMessage
→ ThinkingCallbackMetrics: sólo contadores y tiempos
→ VisibleModelOutput: sólo salida principal/final
→ token IPC / UI / Room
→ onDone + benchmark nativo
→ métricas, incluso si no hubo salida visible
→ aserciones independientes de Device Validation
```

Se capturan raw/thought/final/unknown callbacks, caracteres thought/final/visibles, primer callback, primer thought, primer final, configuración y razón de rebuild. Las métricas existentes de preparación, TTFT visible, total, PSS y thermal se conservan.

El campo heredado `timeToFirstTokenMs` sigue refiriéndose al primer contenido visible. `firstCallbackMs`, `timeToFirstThoughtMs` y `timeToFirstFinalMs` separan las tres fronteras. No se usa primer thought para aparentar mejor TTFT de respuesta final.

La telemetría no retiene cadenas de thought. Room recibe sólo la respuesta visible; JSON, logs y Diagnostics contienen contadores/tiempos, nunca razonamiento oculto. Tampoco se guardaron respuestas completas en los probes.

## E. VisibleModelOutput antes/después

Antes: final explícito si existía; si había cualquier canal, descartaba todo el contenido principal; si no había canales, conservaba texto.

Ahora: conserva final explícito y contenido principal del SDK cuando los canales adicionales son únicamente `thought`/`analysis`. Nunca devuelve sus valores. Un callback thought-only sigue produciendo salida visible vacía. Canales desconocidos se ocultan conservadoramente y se contabilizan.

Un mensaje SDK puede contener contenido principal y canales separados según su contrato. Ese caso se prueba sintéticamente. **No se observaron callbacks mixtos en los probes reales ejecutados**, por lo que no se atribuye a este filtro la causa física sin evidencia. El caso reproducido fue ausencia de final por presupuesto, no final descartado.

## F. Cambios de configuración

- OFF: ruta directa y sampling previo, sin cambios.
- ON/AUTO efectivo ON: presupuesto oficial acotado, máximo 128 y mitad del límite total.
- Mismos límites de contexto y salida normal, CPU/GPU, speculative, warm-up, penalty, temperature, top-k/top-p y seed.
- GPU/speculative/runtimes/dependencias/modelos no fueron modificados.
- Override -1 y límites 256/512/1024 únicamente en validación aislada.

El presupuesto de razonamiento sí cambia: de ilimitado a acotado. Su efecto en calidad de problemas complejos en Moto es **NO DETERMINADO**; este hotfix valida el problema determinista pequeño. No se afirman ganancias porcentuales ni calidad equivalente para todas las tareas.

## G. AUTO y Quick Validation

`AssistantRouting` no se modificó:

| Subcaso | Solicitado | Política / esperado efectivo | Validación |
|---|---|---|---|
| OFF_SIMPLE | OFF | OFF / false | Tokio o Tokyo, final visible |
| AUTO_SIMPLE | AUTO | OFF / false | saludo con final visible |
| AUTO_REASONING | AUTO | ON / true | thought observado + final con 7 |
| ON_REASONING | ON | ON / true | thought observado + final con 7 |

Cada subcaso usa una conversación aislada, mismo modelo residente, sin memoria/documentos/tools. Un fallo no oculta los siguientes. Details/JSON contiene campos prefijados por subcaso.

Quick mantiene nueve cards y PASS de Thinking exige los cuatro PASS, final visible, final callback, completion nativo, configuración correcta y thought observado cuando está ON. Un 7 sin activación y evidencia de thought no basta.

Clasificaciones: `THINKING_OUTPUT_BUDGET_EXHAUSTED`, `THINKING_NO_FINAL_CHANNEL`, `THINKING_CALLBACK_FILTERED`, `THINKING_CONFIG_NOT_APPLIED`, `THINKING_TIMEOUT`, `THINKING_SDK_ERROR`, `THINKING_NO_VISIBLE_OUTPUT`, `THINKING_VALIDATION_ERROR`. Se conserva causa de origen sin mensajes privados. La ruta textual normal conserva su fallback `NO_TOKENS`.

Si vence el timeout general del caso, se conserva evidencia de subcasos ya terminados y se identifica el subcaso activo como timeout. No se prolongaron deadlines.

## H. Rebuild / KV

No se cambiaron las reglas de reutilización de KV, aislamiento, historial efectivo ni presupuesto contextual. OFF→ON efectivo sigue invalidando Conversation con `THINKING_CHANGED` cuando corresponde. La app ya reconstruía conversaciones Thinking; este hotfix no intenta ampliar su reutilización.

La configuración efectiva se conserva por conversación para pasar exactamente el mismo presupuesto al SDK al enviar. Se descarta al cerrar recursos. No se comparten conversaciones de validación ni overrides con chats normales.

## I. Performance y pruebas nativas

Todos los probes usan el bundle oficial local con SHA-256:

`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`

El hash del archivo realmente instalado en el Moto es **NO DETERMINADO** hasta recibir su reporte. CPU Linux x86_64, contexto 4096, threads 4, temperature .3, top-k 40, top-p .95, seed 0. Se usó la política obligatoria de la app; no se ejecutó ChatViewModel/IPC Android en Linux. No se afirma identidad de todos los parámetros físicos sin su JSON.

Los probes con política de app coincidieron con compilación; sus tiempos no son un benchmark controlado de velocidad ni predicción para Moto. Sirven como evidencia de callback/budget/resultado.

| Caso host | Thought callbacks | Final callbacks | Output total nativo | Primer thought ms | Primer final ms | Total ms | Resultado |
|---|---:|---:|---:|---:|---:|---:|---|
| OFF_SIMPLE | 0 | 7 | 8 | unavailable | 5577 | 7368 | PASS |
| AUTO_SIMPLE | 0 | 10 | 11 | unavailable | 2326 | 5337 | PASS |
| AUTO_REASONING | 128 | 7 | 140 | 4461 | 40585 | 42281 | PASS |
| ON_REASONING | 128 | 7 | 140 | 3709 | 50985 | 52468 | PASS |
| OLD_ON_UNBOUNDED | 253 | 0 | 256 | 4119 | unavailable | 116389 | FAIL |

Los probes 512/1024 ilimitados produjeron ambos final 7 con 371 tokens totales, sin alcanzar el límite. Los primeros probes sin política también produjeron final a 256: **no se afirma que 256 siempre sea insuficiente**. La longitud de reasoning depende del contexto.

Para validar performance real, comparar en Moto primer final y TTFT end-to-end, total, decode total, PSS/thermal; no confundir tokens totales con tokens de respuesta visible.

## J. Archivos modificados

- `app/src/main/java/com/localai/workspace/ui/ViewModels.kt` → override temporal limitado a validación; modo/política en métricas de request.
- `app/src/main/java/com/localai/workspace/ui/DeviceValidationScreen.kt` → botón de probes Thinking exclusivamente en Diagnostics.
- `app/src/main/java/com/localai/workspace/ui/PerformanceScreen.kt` → métricas Thinking en Diagnostics, sin nuevos controles del chat.
- `app/src/main/java/com/localai/workspace/validation/ValidationEngine.kt` → conserva evidencia parcial segura al vencer timeout.
- `app/src/main/java/com/localai/workspace/validation/ValidationModels.kt` → tres casos de presupuesto, modo Thinking-only y whitelist de contadores por subcaso.
- `app/src/main/java/com/localai/workspace/validation/DeviceValidation.kt` → selección de suite Thinking-only con el coordinador existente.
- `app/src/main/java/com/localai/workspace/validation/ProductionValidationBackend.kt` → cuatro subcasos independientes, assertions, errores precisos y métricas también en fallos.
- `app/src/main/java/com/localai/workspace/inference/LiteRtLmService.kt` → configuración oficial coherente load/send, contadores y benchmark antes de rechazar salida vacía.
- `app/src/main/java/com/localai/workspace/inference/LiteRtConversationPayload.kt` → ThinkingConfig con budget oficial y override interno validado.
- `app/src/main/java/com/localai/workspace/inference/VisibleModelOutput.kt` → conserva contenido principal del SDK junto a canales ocultos conocidos.
- `app/src/main/java/com/localai/workspace/domain/model/ConversationPrompt.kt` → campo opcional exclusivamente de validación para budget -1.
- `app/src/main/java/com/localai/workspace/domain/model/WorkspaceModels.kt` → métricas escalares nullable, sin texto de reasoning.
- `app/src/test/java/com/localai/workspace/validation/ValidationFrameworkTest.kt` → catálogo 33, suite Thinking-only y timeout con evidencia sin CoT.
- `app/src/test/java/com/localai/workspace/inference/LiteRtConversationPayloadTest.kt` → API real, budget y restricción de override a validación.
- `app/src/test/java/com/localai/workspace/inference/Part2ThinkingTest.kt` → fixture thought-only ajustado al contrato real del SDK.
- `app/build.gradle.kts` → 0.2.3 / versionCode 20; dependencias sin cambios.
- `app/src/main/java/com/localai/workspace/inference/ThinkingDiagnostics.kt` → política acotada, observer sin contenido y clasificación de fallos.
- `app/src/test/java/com/localai/workspace/inference/ThinkingDiagnosticsTest.kt` → callbacks, política, budgets, invalidación y privacidad.

No se modificaron entidades/esquema Room, importadores de modelos, documentos, herramientas, Python, audio, visión, embedding, inferencia GGUF ni las librerías nativas. Datos y preferencias existentes se conservan.

## K. Tests y builds

Comando final:

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk \
./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest \
 :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest \
 -Parm64Only=true -Pkotlin.compiler.execution.strategy=in-process \
 -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain --offline
```

- JVM app: **347 tests, 0 fallos, 0 errores**.
- JVM SDK: **2 tests, 0 fallos, 0 errores**; además ejecutado con `:litert-compat:testDebugUnitTest --rerun` en la comprobación previa.
- Lint: **0 errores, 38 warnings, 1 info**.
- Debug, release y APK AndroidTest: compilados. Tests instrumentados ejecutados: **0**, ADB sin dispositivo.
- Probes nativos con bundle real: OFF/AUTO simple/AUTO reasoning/ON reasoning PASS; ruta antigua ilimitada 256 reproduce thought-only; ilimitada 512/1024 produce final 7.
- Primer intento: fallo del assertion antiguo que esperaba 30 casos Full; actualizado a 33 y añadido test de suite Thinking-only. No se eliminó ninguna comprobación funcional. Logs iniciales se conservan.
- Revisión final posterior a los últimos cambios de diagnóstico: `build-verified.log`; fuentes congeladas/verificadas por hashes.

APK ARM64 firmado, alineado a 16 KiB y verificado con apksigner. Misma firma que 0.2.2. Las nueve librerías nativas y assets Python son idénticos byte a byte frente a 0.2.2.

Archivo: `local-ai-workspace-0.2.3-thinking-arm64.apk`. Tamaño: 98520164 bytes. SHA-256:

`7daaee409d0f717ddd27b5bc3af62d03bef5525f34c09a46cbd22d07ec0ac3c5`

## L. Limitaciones

**PHYSICAL_DEVICE_TEST_REQUIRED:** SDK/JNI Android sobre Moto, final visible y pensamiento efectivo en los cuatro subcasos, regressions de las nueve cards, tiempos/RAM/thermal reales y cambio OFF→ON en chat real.

No se afirma un PASS físico de 0.2.3. No hay contador por canal ni finish reason nativo en la API inspeccionada. No se exporta CoT. No se midió calidad de razonamiento complejo con budget acotado. Los tiempos host concurrentes no son cifras de rendimiento Moto.

No se añadieron Tools, nuevos controles de Thinking en chat, documentos, RAG, memoria, Python, audio, GPU, speculative ni otras funciones. Se utilizó únicamente la Thinking existente y Diagnostics.

## M. Checklist Moto

1. Instalar el APK **como actualización**, sin desinstalar ni volver a importar modelos/EmbeddingGemma.
2. Abrir app y esperar Ready. Confirmar CPU, contexto 4096, maxOutput 256, warm-up ON, speculative OFF para comparación con el run anterior.
3. Settings → Developer / Diagnostics → Device Validation → **Run Quick Validation**, una vez.
4. Verificar las nueve cards PASS: Text, Calculator, Tools Off, Thinking, XLSX, Semantic, Python, Vision y Audio. Abrir Thinking: cuatro subcasos PASS, effective false/false/true/true, thought >0 en los dos de razonamiento, final >0 y sin timeout.
5. Exportar JSON. Si Thinking falla, el prefijo del subcaso y reasonCode deben identificarlo. No enviar ni buscar razonamiento oculto.
6. Sólo si hace falta investigar presupuesto: **Thinking budget probes · 256 / 512 / 1024**. Ejecuta únicamente esos tres casos, con thinking ilimitado para reproducir la configuración antigua. Un BLOCKED por agotamiento en 256 es evidencia diagnóstica, no un PASS fabricado ni fallo del nuevo presupuesto normal.
7. Opcional: en chat real, probar OFF con capital de Japón, AUTO con Hola, AUTO/ON con las cajas. Verificar respuesta final 7 y ausencia de CoT; revisar primer thought/final y rebuild en Local AI Performance.

Si cualquier card previamente válida deja de pasar, no considerar cerrada la validación física del hotfix: conservar/exportar ese reporte antes de seguir.

Evidencia reproducible: `docs/validation/hotfix023/`, con probes, registros de build, tests, auditoría del APK y hashes de fuentes.
