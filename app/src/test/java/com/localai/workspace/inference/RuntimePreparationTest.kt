package com.localai.workspace.inference

import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimePreparationTest {
    private class FakeRuntime : InferenceRuntime {
        var loads = 0
        var cancels = 0
        var generations = 0
        var failure: Throwable? = null
        var progressFailure: Throwable? = null
        val gate = CompletableDeferred<Unit>()
        var cleanup = CompletableDeferred(Unit)
        val progress = MutableStateFlow(GenerationProgress())
        override val runtimeType = RuntimeType.LITERT_LM
        override val runtimeId = "test-control-peer"
        override fun supports(format: ModelFormat) = true
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Inspection is not preparation")
        override suspend fun load(config: ModelLoadConfig) {
            loads++
            try { gate.await(); failure?.let { throw it } }
            finally { withContext(NonCancellable) { cleanup.await() } }
        }
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> { generations++; return emptyFlow() }
        override fun cancelGeneration() { cancels++ }
        override suspend fun unload() = Unit
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, false, false, false)
        override fun metrics() = RuntimeMetrics(modelLoadDurationMs = 40_369, engineReused = false)
        override fun observeProgress(): Flow<GenerationProgress> = progressFailure?.let { error -> flow { throw error } } ?: progress
    }
    private fun request(runtime: FakeRuntime, id: String = "qwen", context: Int = 4096) = RuntimePreparation.Request(id, runtime,
        ModelLoadConfig(ModelSource("/private/$id.litertlm", id, format = ModelFormat.LITERT_LM), contextSize = context,
            maxOutputTokens = 256, conversation = ConversationPrompt("")))

    @Test fun readyRequiresActualLoadAndSendWaitsWithoutGeneratingAPrompt() = runTest {
        val runtime = FakeRuntime(); val controller = RuntimePreparation(backgroundScope)
        controller.start(request(runtime)); runCurrent()
        assertTrue(controller.state.value.preparing); assertFalse(controller.state.value.ready)
        var sendCanProceed = false
        val waiting = launch { controller.awaitPending(); sendCanProceed = true }
        runCurrent(); assertFalse(sendCanProceed)
        runtime.progress.value = GenerationProgress(GenerationStage.CREATING_SESSION, detail = "Native session")
        runCurrent(); assertEquals(GenerationStage.CREATING_SESSION, controller.state.value.progress.stage)
        runtime.gate.complete(Unit); runCurrent(); waiting.join()
        assertTrue(sendCanProceed); assertTrue(controller.state.value.ready)
        assertEquals(40_369L, controller.state.value.metrics?.modelLoadDurationMs)
        assertEquals(0, runtime.generations); assertEquals(1, runtime.loads)
    }

    @Test fun repeatedSelectionDoesNotDuplicateRunningOrReadyNativeLoads() = runTest {
        val runtime = FakeRuntime(); val controller = RuntimePreparation(backgroundScope); val config = request(runtime)
        controller.start(config); runCurrent(); controller.start(config); runCurrent()
        assertEquals(1, runtime.loads)
        runtime.gate.complete(Unit); runCurrent(); controller.start(config); runCurrent()
        assertEquals(1, runtime.loads); assertEquals(0, runtime.cancels)
        controller.start(request(runtime, context = 2048)); runCurrent()
        assertEquals(2, runtime.loads)
    }

    @Test fun modelSwitchWaitsForCancelledNativeTeardownAndSuppressesStaleProgress() = runTest {
        val first = FakeRuntime().apply { cleanup = CompletableDeferred() }
        val second = FakeRuntime(); val controller = RuntimePreparation(backgroundScope)
        controller.start(request(first, "first")); runCurrent()
        controller.start(request(second, "second")); runCurrent()
        assertEquals(1, first.cancels); assertEquals(0, second.loads)
        first.progress.value = GenerationProgress(GenerationStage.COMPLETED); runCurrent()
        assertEquals("second", controller.state.value.modelId); assertFalse(controller.state.value.ready)
        first.cleanup.complete(Unit); runCurrent(); assertEquals(1, second.loads)
        second.gate.complete(Unit); runCurrent()
        assertTrue(controller.state.value.ready); assertEquals("second", controller.state.value.modelId)
    }

    @Test fun cancelReachesRuntimeReleasesSendWaiterAndAllowsRetry() = runTest {
        val runtime = FakeRuntime(); val controller = RuntimePreparation(backgroundScope)
        controller.start(request(runtime)); runCurrent()
        val waiting = launch { controller.awaitPending() }; runCurrent()
        controller.cancel(); runCurrent(); waiting.join()
        assertEquals(1, runtime.cancels); assertFalse(controller.state.value.ready)
        assertFalse(controller.state.value.preparing); assertEquals(GenerationStage.CANCELLED, controller.state.value.progress.stage)
        runtime.gate.complete(Unit); controller.start(request(runtime)); runCurrent()
        assertTrue(controller.state.value.ready); assertEquals(2, runtime.loads)
    }

    @Test fun loadFailureKeepsRealDiagnosticAndSuccessfulForegroundRetryClearsIt() = runTest {
        val error = GenerationError(GenerationStage.LOADING_MODEL, "MODEL_LOAD_TIMEOUT", "Real native timeout", checkpoint = "LITERT_ENGINE_INITIALIZE_START")
        val runtime = FakeRuntime().apply { failure = GenerationException(error); gate.complete(Unit) }
        val controller = RuntimePreparation(backgroundScope)
        controller.start(request(runtime)); runCurrent()
        assertEquals(error, controller.state.value.error); assertFalse(controller.state.value.ready)
        controller.loadedForRequest("qwen", RuntimeMetrics(engineReused = true))
        assertNull(controller.state.value.error); assertTrue(controller.state.value.ready)
        controller.invalidateReady(); assertFalse(controller.state.value.ready)
        runtime.failure = null; controller.start(request(runtime)); runCurrent()
        assertTrue(controller.state.value.ready); assertEquals(0, runtime.generations)
    }

    @Test fun observerFailureBecomesVisibleErrorAndStopsTheActiveNativeLoad() = runTest {
        val runtime = FakeRuntime().apply { progressFailure = IllegalStateException("broken monitor") }
        val controller = RuntimePreparation(backgroundScope)
        controller.start(request(runtime)); runCurrent()
        assertEquals("MODEL_PREPARATION_PROGRESS_FAILED", controller.state.value.error?.code)
        assertEquals(1, runtime.cancels)
        assertFalse(controller.state.value.preparing); assertFalse(controller.state.value.ready)
        controller.awaitPending()
    }
}
