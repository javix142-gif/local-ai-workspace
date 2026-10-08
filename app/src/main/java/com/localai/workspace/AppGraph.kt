package com.localai.workspace

import android.content.Context
import com.localai.workspace.data.DocumentIngestionService
import com.localai.workspace.data.HuggingFaceClient
import com.localai.workspace.data.HuggingFaceDownloader
import com.localai.workspace.data.HuggingFaceTokenStore
import com.localai.workspace.data.ImagePreprocessor
import com.localai.workspace.data.ModelImportService
import com.localai.workspace.data.WorkspaceDatabase
import com.localai.workspace.data.WorkspaceRepository
import com.localai.workspace.domain.rag.HashEmbeddingRuntime
import com.localai.workspace.domain.tools.CalculatorTool
import com.localai.workspace.domain.tools.LocalFileReadTool
import com.localai.workspace.domain.tools.ToolRegistry
import com.localai.workspace.domain.inference.InferenceRuntimeRegistry
import com.localai.workspace.inference.LlamaCppInferenceRuntime
import com.localai.workspace.inference.LiteRtLmInferenceRuntime
import com.localai.workspace.rag.LocalRetrievalService
import kotlinx.coroutines.launch

class AppGraph(context: Context,
    runtimeOverrides: List<com.localai.workspace.domain.inference.InferenceRuntime>? = null,
    databaseOverride: WorkspaceDatabase? = null,
    applicationScope: kotlinx.coroutines.CoroutineScope? = null,
    nativeScope: kotlinx.coroutines.CoroutineScope? = null,
    internal val validationOwner: AppGraph? = null,
    internal val validationId: String? = null,
) {
    private val appContext = context.applicationContext
    val contextForMeasurements get() = appContext
    val database = databaseOverride ?: WorkspaceDatabase.create(appContext)
    val llamaRuntime by lazy { LlamaCppInferenceRuntime(appContext) }
    val liteRtLmRuntime by lazy { LiteRtLmInferenceRuntime(appContext) }
    val runtimes: InferenceRuntimeRegistry = validationOwner?.runtimes ?: InferenceRuntimeRegistry(runtimeOverrides ?: listOf(llamaRuntime, liteRtLmRuntime))
    private val runtimeScope = nativeScope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val preparationScope = applicationScope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val inferenceGate: kotlinx.coroutines.sync.Mutex = validationOwner?.inferenceGate ?: kotlinx.coroutines.sync.Mutex()
    val validationBusy: kotlinx.coroutines.flow.MutableStateFlow<Boolean> = validationOwner?.validationBusy ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    internal var agentsSkillsValidationEnabled: Boolean = false
    internal var validationRequestObserver: ((com.localai.workspace.domain.model.GenerationRequest) -> Unit)? = null
    val assistantSettings = com.localai.workspace.data.AssistantSettings(appContext)
    val performanceSettings = com.localai.workspace.performance.PerformanceSettings(appContext)
    val chatSessions: com.localai.workspace.ui.ChatSessions = com.localai.workspace.ui.ChatSessions(this, preparationScope)
    val chatRuntimes: com.localai.workspace.domain.inference.ChatRuntimePool = validationOwner?.chatRuntimes ?: com.localai.workspace.domain.inference.ChatRuntimePool(runtimeScope, idleRetentionMs = null,
        reportCleanupFailure = { android.util.Log.e("LocalAI/Runtime", "warm_cleanup_error type=${it.javaClass.name}") },
        onEngineReleased = { preparationScope.launch { modelPreparation.invalidate() } })
    fun releaseIdleModel() { runtimeScope.launch { chatRuntimes.releaseIdle() } }
    /** Compatibility alias for older screens while chat is migrated to the registry. */
    val runtime get() = llamaRuntime
    val workspace = WorkspaceRepository(database, java.io.File(appContext.filesDir, "documents"))
    val documents = DocumentIngestionService(appContext, database, database.documentDao(), onIndexed = { id ->
        if(validationOwner==null) {
            // Ingestion awaits this callback: stale the active semantic generation before
            // reporting the import complete, rather than racing chat against a fire-and-forget job.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { semanticV2.markNeedsReindex(id) }
        }
        if(embeddingModels.id != null) runtimeScope.launch { try { retrieval.indexProject(id) } catch(cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch(error: Throwable) { embeddingModels.status.value = "Index failed: ${error.javaClass.simpleName} · lexical fallback" } }
    }, outputDirectory = validationId?.let { java.io.File(appContext.filesDir,"documents/self-test-"+it) })
    val modelImport = ModelImportService(appContext, database.modelDao(), runtimes)
    val liteRtDefaults = com.localai.workspace.data.LiteRtChatDefaultsUpgrade(appContext, database.modelDao(), readOnly = validationOwner != null)
    val modelPreparation: com.localai.workspace.data.ApplicationModelPreparation = com.localai.workspace.data.ApplicationModelPreparation(appContext, preparationScope,
        workspace, liteRtDefaults, runtimes, chatRuntimes, inferenceGate, performanceSettings)
    val performance = com.localai.workspace.performance.PerformanceDiagnostics(appContext, runtimeScope, workspace,
        runtimes, chatRuntimes, inferenceGate, modelPreparation, performanceSettings,
        storageFile = validationId?.let { java.io.File(appContext.filesDir,"device-validation/runs/"+it+"/benchmarks.json") },
        anotherDiagnosticRunning = { validationBusy.value && validationOwner == null })
    val huggingFaceTokenStore = HuggingFaceTokenStore(appContext)
    val huggingFace = HuggingFaceClient(huggingFaceTokenStore)
    val huggingFaceDownloader = HuggingFaceDownloader(appContext, huggingFaceTokenStore)
    val imagePreprocessor = ImagePreprocessor(appContext, validationId?.let { java.io.File(appContext.filesDir,"attachments/self-test-"+it) })
    val audioPreprocessor = com.localai.workspace.audio.AudioPreprocessor(appContext, validationId?.let { java.io.File(appContext.filesDir,"audio-attachments/self-test-"+it) })
    val embeddingModels: com.localai.workspace.semantic.EmbeddingModels = validationOwner?.embeddingModels ?: com.localai.workspace.semantic.EmbeddingModels(appContext)
    val retrieval = com.localai.workspace.semantic.SemanticRetrievalService(database, embeddingModels)
    val semanticV2 by lazy { com.localai.workspace.semantic.v2.SemanticLayer(appContext, database, inferenceGate) }
    val semanticImports by lazy { com.localai.workspace.semantic.v2.SemanticImportCoordinator(appContext,embeddingModels,{semanticV2}) }
    val semanticDiagnostics by lazy { com.localai.workspace.semantic.v2.SemanticDiagnostics(appContext,runtimeScope,{semanticV2},validationBusy,{performance.running.value || chatSessions.hasGeneration}) }
    val normalGenerationTrace by lazy { com.localai.workspace.diagnostics.NormalGenerationTrace(java.io.File(appContext.filesDir, validationId?.let { "device-validation/runs/$it/normal-generation.json" } ?: "diagnostics/normal-generation.json")) }
    val contextFoundation by lazy { com.localai.workspace.context.ContextFoundation(this) }
    val agentsSkillsDatabase by lazy { com.localai.workspace.skills.AgentsSkillsDatabase.create(appContext, validationId?.let { "agents-skills-$it.db" } ?: "agents_skills.db") }
    val skillRegistry by lazy { com.localai.workspace.skills.SkillRegistry(agentsSkillsDatabase) }
    val agentRegistry by lazy { com.localai.workspace.agents.AgentRegistry(agentsSkillsDatabase) }
    val agentSkills by lazy { com.localai.workspace.agents.AgentSkillCoordinator(skillRegistry, agentRegistry) }
    val localTools = ToolRegistry(listOf(CalculatorTool(), LocalFileReadTool()))
    val deviceValidation by lazy { com.localai.workspace.validation.DeviceValidation(this, preparationScope) }
}
