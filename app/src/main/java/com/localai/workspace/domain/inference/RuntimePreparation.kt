package com.localai.workspace.domain.inference

import com.localai.workspace.domain.model.ModelLoadConfig
import com.localai.workspace.domain.model.RuntimeMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Prepare on explicit chat entry/selection; no prompt is generated or sent anywhere. */
class RuntimePreparation(private val scope: CoroutineScope) {
    data class Request(val modelId: String, val runtime: InferenceRuntime, val config: ModelLoadConfig)
    data class State(val modelId: String? = null, val preparing: Boolean = false, val ready: Boolean = false,
        val progress: GenerationProgress = GenerationProgress(), val error: GenerationError? = null,
        val metrics: RuntimeMetrics? = null)

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var request: Request? = null
    private var revision = 0L

    fun start(next: Request) {
        if (request == next && (mutableState.value.preparing || mutableState.value.ready)) return
        val previousJob = job
        if (previousJob?.isActive == true) { request?.runtime?.cancelGeneration(); previousJob.cancel() }
        val currentRevision = ++revision
        request = next
        mutableState.value = State(next.modelId, preparing = true, progress = GenerationProgress(GenerationStage.LOADING_MODEL))
        job = scope.launch {
            // Native teardown/cancellation must settle before another prepare request.
            previousJob?.join()
            val loadJob = coroutineContext[Job]!!
            val progressFailure = AtomicReference<Throwable?>(null)
            val observer = launch {
                try {
                    next.runtime.observeProgress().collect { progress ->
                        if (revision == currentRevision && progress.stage != GenerationStage.IDLE) {
                            mutableState.value = mutableState.value.copy(progress = progress)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Throwable) {
                    progressFailure.set(failure)
                    next.runtime.cancelGeneration()
                    loadJob.cancel()
                }
            }
            try {
                next.runtime.load(next.config)
                if (revision == currentRevision) mutableState.value = State(next.modelId, ready = true,
                    progress = GenerationProgress(GenerationStage.IDLE), metrics = next.runtime.metrics())
            } catch (cancelled: CancellationException) {
                if (revision == currentRevision) {
                    val failure = progressFailure.get()
                    if (failure == null) mutableState.value = State(next.modelId, progress = GenerationProgress(GenerationStage.CANCELLED))
                    else setupFailed(next.modelId, GenerationError(mutableState.value.progress.stage,
                        "MODEL_PREPARATION_PROGRESS_FAILED", "Model preparation progress failed", technicalDetail = failure.javaClass.name))
                }
                throw cancelled
            } catch (failure: Throwable) {
                val error = (failure as? GenerationException)?.diagnostic ?: GenerationError(
                    mutableState.value.progress.stage, "MODEL_PREPARATION_FAILED", failure.message ?: "Model preparation failed",
                    technicalDetail = failure.javaClass.name)
                if (revision == currentRevision) mutableState.value = State(next.modelId,
                    progress = GenerationProgress(GenerationStage.ERROR, error = error), error = error)
            } finally { observer.cancel() }
        }
    }

    suspend fun awaitPending() { job?.join() }

    fun cancel() {
        val current = job
        if (current?.isActive == true) { request?.runtime?.cancelGeneration(); current.cancel() }
        ++revision // Ignore an acknowledgement/error belonging to cancelled preparation.
        mutableState.value = State(request?.modelId, progress = GenerationProgress(GenerationStage.CANCELLED))
    }

    fun invalidateReady() { mutableState.value = mutableState.value.copy(ready = false, metrics = null) }

    /** A supported runtime may deliberately keep its existing first-send load path. */
    fun defer(modelId: String) {
        if (mutableState.value.modelId == modelId && !mutableState.value.preparing && !mutableState.value.ready) return
        cancel()
        mutableState.value = State(modelId)
    }

    /** Called only after the foreground request has actually returned from runtime.load. */
    fun loadedForRequest(modelId: String, metrics: RuntimeMetrics) {
        mutableState.value = State(modelId, ready = true, metrics = metrics)
    }

    fun setupFailed(modelId: String?, error: GenerationError) {
        mutableState.value = State(modelId, progress = GenerationProgress(GenerationStage.ERROR, error = error), error = error)
    }
}
