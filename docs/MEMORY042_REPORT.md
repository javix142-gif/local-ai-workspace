# Local AI Workspace 0.4.2 — Memory Relevance Hotfix

## A. Fórmula anterior y causa raíz

`context/MemoryManager.kt:search` calculaba `lexical + cosine + (pinned ? 2.0 : 0.1 * importance) + 0.05 * confidence`. Lexical era la fracción de palabras de la query presentes en texto/título, con la lista de stopwords de `MemoryContent.words`. Sin normalización de acentos ni distinción de palabras gramaticales suficientes. No había contribución de recencia, scope, tipo ni structured match al score. Scope/status/TTL se comprobaban en `lookup` antes del ranking. Subject/predicate sólo se usaban en lookup explícito.

La aceptación era `pinned || lexical != 0 || cosine > 0`. Luego ordenaba y tomaba seis. Una similitud positiva débil o pinning bastaban: disponibilidad y ranking sustituían a relevancia. Los scores físicos 0.769008 y 1.657446 son compuestos; no conocemos sus componentes individuales. No justifican un cutoff de ranking 1.0.

## B–E. Política nueva

`MemoryRelevance.V1` separa eligibility → candidatos → señales → gate → ranking → top-K → presupuesto. `lookup` sigue siendo la autoridad de scope/status/TTL. El ranking conserva los bonus anteriores, aplicados únicamente después de aceptar relevancia.

Gate: structured match explícito OR lexical de contenido >= 0.50 OR cosine finite >= 0.80. Structured exige subject completo presente y predicate coincidente cuando existe. Normaliza acentos/case y agrupa preferido/favorito/prefiero/preferred/favorite. La palabra genérica preferencia no cuenta como ancla lexical: editor preferido debe coincidir en editor, no sólo en preferencia. Se conserva para la comparación de predicate estructurado. No modifica `MemoryContent.words`, que también usa el historial antiguo. Los bonus de pin/importance/confidence nunca abren el gate. No añade bonus de recencia/scope. Una lista vacía es válida.

**Umbral semántico provisional, no calibrado en Moto:** 0.80 es una política conservadora de precisión para cosine, no una medición ni una garantía universal EG1/EG2. 0.50 lexical es cobertura de términos útiles y tiene escala propia. Requiere validar paráfrasis semánticas sin overlap y consultar los componentes antes de ajustar. No cambia thresholds de Semantic V2 ni EG1. No se promete preservar todo recall semántico previo. La suite física de Context Memory existente conserva sus expectations; su caso de paráfrasis deberá revalidarse si se ejecuta posteriormente.

Small talk: gramática determinista anclada al mensaje completo, case/acento/puntuación normalizados, permite combinaciones de saludos/cortesías. `Hola, ¿cuál es mi editor preferido?` no coincide. No usa otro LLM. Small talk evita lookup/indexing/embedding de Structured Memory; devuelve cero memorias y `memoryLookupSkipped=true`. No afirma omitir embeddings independientes de fuentes/RAG, cuya ruta permanece intacta.

## F–I. Providers, fallback y aislamiento

EG2/EG1 siguen generando vectores reales por las rutas existentes; la selección de memoria utiliza cosine sólo del espacio exacto almacenado. Si embedding no está disponible, lexical/structured son suficientes únicamente si pasan su gate. No usa hash/fake vectors nuevos. Project A/B y USER siguen elegibles sólo por `ScopeAccess`. Una USER irrelevante no entra por el mero hecho de estar disponible. Pinning ordena memorias relevantes; no obliga su inclusión.

## Diagnostics y privacidad

`MemorySelection` conserva descartes de relevancia. Context Foundation los añade a `ContextBundle.dropped` con `BELOW_RELEVANCE_THRESHOLD`. No pasan por packing ni aumentan `estimatedTokensPerSection`. `safeReport` incluye policy, raw relevance, ranking, cosine nullable, lexical, structured match, bonus y lookupSkipped, sin texto de query/memoria. Expand private context sigue mostrando sólo `included`.

En fast path no se enumera la memoria omitida: no se fabrica un candidato M1 ni un score si el lookup no ocurrió. El reporte indica el skip. No se añade logging de contenido.

## Archivos productivos

- `app/build.gradle.kts`: 0.4.2 / code 26.
- `app/src/main/java/com/localai/workspace/context/MemoryRelevance.kt`: política pura y modelos de selección/score.
- `.../context/MemoryDomain.kt`: metadatos de señales en MemoryHit.
- `.../context/MemoryManager.kt`: select con gate; search delega conservando API.
- `.../context/ContextFoundation.kt`: skip antes de queryEmbedding; integración de descartes y señales.
- `.../context/ContextBuilder.kt`: metadatos aditivos en item/bundle/safeReport; algoritmo de packing y render intactos.
- `app/src/test/java/com/localai/workspace/context/MemoryRelevanceTest.kt`: nuevas regresiones. Tests existentes no editados.

No cambios de schemas, engines/providers, native libraries, inferencia, gate ordering, cancellation, Thinking, tools, Vision, Audio, Python, sampling, capacidad ni KV. `ui/ViewModels.kt` de 0.4.1 idéntico; V1 OFF permanece idéntico. Sin Git disponible: baseline guardada en `/workspace/baselines/local-ai-workspace-0.4.1-pre-memory042.tar.gz`; no se inventa un tag/commit.

## J–M. QA y APK

Suite JVM: **513 tests (511 app + 2 litert-compat), 0 failures, 0 errors, 0 skipped**. Preserva 497 existentes y añade 16. El primer intento dirigido pasó; un intento posterior falló al compilar un test por un type argument no admitido por Robolectric; se corrigió el test, no expectativas ni código productivo. Ambos logs quedan conservados. Resultados exactos en `docs/validation/memory042/test-summary.json`; comandos/log en `host-checks.log`. APK, tamaño, SHA real y certificado en `apk-verification.json`. Firma de desarrollo existente compatible con los APK previos; no se distribuye keystore. Tests de host usan Room/Robolectric y doubles de embedding exclusivamente en tests, no equivalen a medición de EG2 en Moto.

## N. Motorola targeted — NOT YET DEVICE VALIDATED

1. Instalar 0.4.2 encima de 0.4.1, sin desinstalar ni borrar datos.
2. V1 ON, USER Nebula existente: chat nuevo `hola`. Debe saludar sin Nebula. Inspector: MEMORY cero y memoryLookupSkipped true.
3. Preguntar `¿Cuál es mi editor de prueba preferido?`, `¿Qué editor prefiero?` y `Hola, ¿cuál es mi editor preferido?`: Nebula incluida.
4. Project A: preguntar base de datos. SQLite; no PostgreSQL ni Nebula. Inspector muestra Nebula descartada por relevancia.
5. Project B: misma query. PostgreSQL; no SQLite ni Nebula.
6. Probar memoria fijada irrelevante y consulta sin memoria relevante: cero es válido.
7. V1 OFF: chat nuevo `hola`; conservar comportamiento 0.4.1.
8. Sólo después, Quick Validation. No ejecutar Full 33 de entrada.
9. Copiar metadata report del Inspector ante un resultado inesperado; no necesita exportar contexto privado.

## O. Incertidumbres

No hay Moto/ADB conectado. No se midieron cosine reales adicionales, ahorro de latencia, ni respuestas nativas para este hotfix. No prometer porcentajes. Threshold 0.80 podría reducir recall de paráfrasis; validación física y scores descompuestos decidirán ajustes posteriores. Stop→Retry sigue fuera del alcance. No modifica expectativas de las suites físicas históricas.

## Resultado final de gates

Comando (JDK 17, SDK local, Linux; no Motorola):

```sh
JAVA_HOME=/workspace/.toolchain/jdk-17 ANDROID_HOME=/workspace/.toolchain/android-sdk ./gradlew :app:testDebugUnitTest :litert-compat:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest -Parm64Only=true -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx3072m --max-workers=1 --console=plain --offline
```

Los seis gates PASS. BUILD SUCCESSFUL en 12m37s. Lint: 0 errores, 40 warnings y 1 Information (mismos conteos previos). Advertencia de deprecación PythonService preexistente. APK instrumentado construido, no ejecutado.

APK: `dist/apk/local-ai-workspace-0.4.2-memory-relevance-hotfix-arm64.apk`, 99,638,941 bytes.

SHA-256: `2de32fbad0f295baebccd6aba67060140e78f1436f293a2905a90f069066af4a`.

Certificado SHA-256: `f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b`. Firma y zipalign 16 KiB verificados. 22 archivos nativos/Python comparados byte a byte contra 0.4.1: cero diferencias. Mantiene package `com.localai.workspace` y compatibilidad de actualización.

**NOT YET DEVICE VALIDATED.** No hay resultado físico nuevo ni porcentaje de mejora medido.
