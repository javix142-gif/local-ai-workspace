package com.localai.workspace.data

import android.content.Context
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One app-owned preparation; never owned/cancelled by a disposable chat screen. */
class ApplicationModelPreparation(
    context: Context,
    private val scope: CoroutineScope,
    private val workspace: WorkspaceRepository,
    private val defaults: LiteRtChatDefaultsUpgrade,
    private val runtimes: InferenceRuntimeRegistry,
    private val pool: ChatRuntimePool,
    private val inferenceGate: Mutex = Mutex(),
    private val performanceSettings: com.localai.workspace.performance.PerformanceSettings = com.localai.workspace.performance.PerformanceSettings(context),
) {
    private val preferences = context.getSharedPreferences("app_model_preparation", Context.MODE_PRIVATE)
    val controller = RuntimePreparation(scope)
    val state = controller.state
    private val preloaders = mutableMapOf<InferenceRuntime, InferenceRuntime>()
    private var setup: Job? = null
    private var started = false
    private var selectedId = preferences.getString("selected_model", null)
    fun selectedModelId(): String? = selectedId

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Room emissions include settings changes/import/removal; identical configs deduplicate.
            workspace.allModels.collect { available ->
                val chosen = usableModel(available, selectedId)
                if (chosen == null) {
                    setup?.cancel(); controller.cancel(); controller.invalidateReady()
                } else prepare(chosen.id)
            }
        }
    }

    fun select(modelId: String) {
        selectedId = modelId
        preferences.edit().putString("selected_model", modelId).apply()
        prepare(modelId)
    }

    fun retry() { selectedId?.let(::prepare) }
    fun cancel() { setup?.cancel(); controller.cancel() }
    fun invalidate() = controller.invalidateReady()
    private var benchmarkPaused = false
    suspend fun pauseForBenchmark() {
        benchmarkPaused = true
        setup?.cancel(); setup?.join()
        controller.cancel(); controller.awaitPending()
    }
    fun resumeAfterBenchmark() { benchmarkPaused = false; selectedId?.let(::prepare) }

    private fun prepare(modelId: String) {
        if (benchmarkPaused) return
        setup?.cancel()
        setup = scope.launch {
            try {
                val model = workspace.allModels.first().firstOrNull { it.id == modelId && isUsable(it) } ?: return@launch
                selectedId = model.id
                preferences.edit().putString("selected_model", model.id).apply()
                // Keep GGUF's existing first-send path; do not claim a prepared llama engine.
                if (model.toDescriptor().runtime != RuntimeType.LITERT_LM) {
                    controller.defer(model.id)
                    return@launch
                }
                val effective = defaults.applyOnce(model.id) ?: return@launch
                val descriptor = effective.toDescriptor()
                val backend = runtimes.runtimeFor(descriptor)
                val preloader = preloaders.getOrPut(backend) {
                    val loader = pool.preloader(backend)
                    object : InferenceRuntime by loader {
                        override suspend fun load(config: ModelLoadConfig) = inferenceGate.withLock { loader.load(config) }
                    }
                }
                controller.start(RuntimePreparation.Request(effective.id, preloader, ModelLoadConfig(
                    ModelSource(descriptor.localPath, descriptor.displayName, effective.sourceUri, descriptor.format,
                        auxiliaryFiles = descriptor.auxiliaryFiles.map { it.absolutePath }, sourceType = descriptor.source,
                        expectedSizeBytes = effective.fileSize, sha256 = effective.fileHash),
                    contextSize = (effective.configuredContext ?: effective.declaredContext ?: 4096).coerceIn(256, 8192),
                    maxOutputTokens = effective.maxOutputTokens.coerceIn(1, 8192), temperature = effective.temperature,
                    topP = effective.topP, topK = effective.topK, repeatPenalty = effective.repeatPenalty, seed = effective.seed,
                    preferredAccelerator = performanceSettings.state.value.backend, conversation = ConversationPrompt(""),
                    warmupEnabled = performanceSettings.state.value.warmup,
                    speculativeEnabled = performanceSettings.state.value.speculative)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                controller.setupFailed(modelId, (failure as? GenerationException)?.diagnostic ?: GenerationError(
                    GenerationStage.LOADING_MODEL, "MODEL_PREPARATION_FAILED", failure.message ?: "Model preparation failed",
                    technicalDetail = failure.javaClass.name))
            }
        }
    }

    companion object {
        fun isUsable(model: ModelEntity) = model.importStatus in setOf(ModelImportStatus.READY.name, ModelImportStatus.COMPATIBLE_WARNING.name)
        fun usableModel(models: List<ModelEntity>, preferred: String?): ModelEntity? =
            models.firstOrNull { it.id == preferred && isUsable(it) } ?: models.firstOrNull(::isUsable)
    }
}
