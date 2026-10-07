package com.localai.workspace.inference

import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Ownership tests with controllable backends; not native/device performance evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationPreparationLifecycleTest {
    private class Backend : InferenceRuntime {
        override val runtimeType = RuntimeType.LITERT_LM
        override val runtimeId = "controlled"
        val progress = MutableStateFlow(GenerationProgress())
        var gate = CompletableDeferred<Unit>()
        var generationGate = CompletableDeferred<Unit>()
        var loads = 0; var initializations = 0; var cancels = 0; var unloads = 0
        private var path: String? = null
        override fun supports(format: ModelFormat) = true
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Not needed")
        override suspend fun load(config: ModelLoadConfig) {
            loads++; progress.value = GenerationProgress(GenerationStage.LOADING_MODEL, detail = "Native load")
            gate.await()
            if (path != config.model.absolutePath) { initializations++; path = config.model.absolutePath }
        }
        override fun generate(request: GenerationRequest) = flow {
            generationGate.await(); emit(GenerationEvent.Token("fake callback"))
        }
        override fun cancelGeneration() { cancels++ }
        override suspend fun resetConversation() = Unit
        override suspend fun unload() { unloads++; path = null }
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, false, false, false)
        override fun metrics() = RuntimeMetrics(backend = runtimeId)
        override fun observeProgress() = progress
    }
    private fun config(path: String = "qwen") = ModelLoadConfig(ModelSource("/private/$path", path,
        format = ModelFormat.LITERT_LM), 4096, conversation = ConversationPrompt(""))

    @Test fun openingAndClosingChatsDuringColdLoadDoesNotCancelApplicationPreparation() = runTest {
        val b = Backend(); val pool = ChatRuntimePool(backgroundScope, idleRetentionMs = null, reportCleanupFailure = { throw it })
        val application = RuntimePreparation(backgroundScope)
        val preloader = pool.preloader(b)
        application.start(RuntimePreparation.Request("qwen", preloader, config())); runCurrent()
        assertTrue(application.state.value.preparing)
        // UI revocation is synchronous; cleanup waits behind the ongoing native load.
        val cleanup = List(3) { pool.lease(b).close() }
        assertEquals(0, b.cancels); assertEquals(1, b.loads)
        b.gate.complete(Unit); runCurrent(); application.awaitPending()
        cleanup.forEach { it.join() }
        assertTrue(application.state.value.ready)
        pool.lease(b).load(config())
        assertEquals(1, b.initializations)
    }

    @Test fun cancelledScreenWaiterDoesNotCancelSharedPreparation() = runTest {
        val b = Backend(); val pool = ChatRuntimePool(backgroundScope, reportCleanupFailure = { throw it })
        val application = RuntimePreparation(backgroundScope)
        application.start(RuntimePreparation.Request("qwen", pool.preloader(b), config())); runCurrent()
        val waiter = launch { application.awaitPending() }; runCurrent(); waiter.cancelAndJoin()
        assertTrue(application.state.value.preparing); assertEquals(0, b.cancels)
        b.gate.complete(Unit); runCurrent(); assertTrue(application.state.value.ready)
    }

    @Test fun explicitCancelReachesNativePreparationAndAllowsRetry() = runTest {
        val b = Backend(); val pool = ChatRuntimePool(backgroundScope, reportCleanupFailure = { throw it })
        val app = RuntimePreparation(backgroundScope); val request = RuntimePreparation.Request("qwen", pool.preloader(b), config())
        app.start(request); runCurrent(); app.cancel(); runCurrent()
        assertEquals(1, b.cancels); assertFalse(app.state.value.ready)
        b.gate.complete(Unit); app.start(request); runCurrent()
        assertTrue(app.state.value.ready); assertEquals(2, b.loads)
    }

    @Test fun noTimerEvictionButMemoryPressureReleasesIdleEngineAndInvalidatesReady() = runTest {
        val b = Backend().apply { gate.complete(Unit) }; var released = false
        val pool = ChatRuntimePool(backgroundScope, idleRetentionMs = null, reportCleanupFailure = { throw it }, onEngineReleased = { released = true })
        val chat = pool.lease(b); chat.load(config()); chat.close().join()
        advanceTimeBy(2 * 60 * 60_000); runCurrent(); assertEquals(0, b.unloads)
        pool.releaseIdle(); assertEquals(1, b.unloads); assertTrue(released)
    }

    @Test fun queuedPreloaderCancellationCannotStopCurrentChatGeneration() = runTest {
        val b = Backend().apply { gate.complete(Unit) }; val pool = ChatRuntimePool(backgroundScope, reportCleanupFailure = { throw it })
        val chat = pool.lease(b); chat.load(config())
        val generating = launch { chat.generate(GenerationRequest("text")).toList() }; runCurrent()
        val preloader = pool.preloader(b)
        val waiting = launch { preloader.load(config("gemma")) }; runCurrent()
        preloader.cancelGeneration(); waiting.cancelAndJoin()
        assertEquals(0, b.cancels); assertEquals(1, b.loads)
        b.generationGate.complete(Unit); generating.join()
    }
}
