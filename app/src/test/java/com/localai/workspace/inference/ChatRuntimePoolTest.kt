package com.localai.workspace.inference

import com.localai.workspace.domain.inference.ChatRuntimePool
import com.localai.workspace.domain.inference.InferenceRuntime
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Lifecycle/ownership tests only; fake backends are not device inference or performance evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRuntimePoolTest {
    private class Backend(override val runtimeType: RuntimeType = RuntimeType.LITERT_LM) : InferenceRuntime {
        override val runtimeId = runtimeType.name
        var initializations = 0; var resets = 0; var unloads = 0; var cancels = 0
        var loadedPath: String? = null
        val history = mutableListOf<String>()
        var loadGate: CompletableDeferred<Unit>? = null
        var resetFailure: Throwable? = null
        var loadFailure: Throwable? = null
        val generationEntered = CompletableDeferred<Unit>()
        var generationGate: CompletableDeferred<Unit>? = null
        val cleanupStarted = CompletableDeferred<Unit>()
        var cleanupGate: CompletableDeferred<Unit>? = null
        override fun supports(format: ModelFormat) = true
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Unused")
        override suspend fun load(config: ModelLoadConfig) {
            loadGate?.await()
            loadFailure?.let { throw it }
            if (loadedPath != config.model.absolutePath) { initializations++; loadedPath = config.model.absolutePath }
        }
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            generationEntered.complete(Unit)
            try {
                generationGate?.await()
                history.add(request.prompt)
                emit(GenerationEvent.Token("actual callback boundary in fake"))
            } finally {
                withContext(NonCancellable) { cleanupStarted.complete(Unit); cleanupGate?.await() }
            }
        }
        override fun cancelGeneration() { cancels++ }
        override suspend fun resetConversation() {
            resets++; resetFailure?.let { throw it }; history.clear()
            if (runtimeType == RuntimeType.LLAMA_CPP) unload()
        }
        override suspend fun unload() { unloads++; history.clear(); loadedPath = null }
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, false, false, false)
        override fun metrics() = RuntimeMetrics(backend = runtimeId)
    }
    private fun config(path: String = "qwen") = ModelLoadConfig(ModelSource("/private/$path.litertlm", path,
        format = ModelFormat.LITERT_LM), contextSize = 4096, conversation = ConversationPrompt(""))
    private fun pool(scope: CoroutineScope, errors: MutableList<Throwable> = mutableListOf()) =
        ChatRuntimePool(scope, idleRetentionMs = 60_000, reportCleanupFailure = { errors.add(it) })

    @Test fun nextChatKeepsEngineButReceivesNoPreviousConversation() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope)
        val first = pool.lease(backend); first.load(config())
        first.generate(GenerationRequest("project one secret")).toList()
        assertEquals(1, backend.history.size)
        first.close().join()
        assertTrue(backend.history.isEmpty()); assertEquals(0, backend.unloads)
        val second = pool.lease(backend); second.load(config())
        assertEquals(1, backend.initializations); assertTrue(backend.history.isEmpty())
        second.generate(GenerationRequest("project two")).toList()
        assertEquals(listOf("project two"), backend.history)
    }

    @Test fun delayedOldCloseAndCancellationCannotAffectNewChat() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope)
        val first = pool.lease(backend); first.load(config())
        val oldCleanup = first.close() // Scheduled, deliberately not executed yet.
        val second = pool.lease(backend); second.load(config())
        backend.generationGate = CompletableDeferred()
        val generation = launch { second.generate(GenerationRequest("new")).toList() }
        runCurrent(); backend.generationEntered.await()
        first.cancelGeneration()
        val staleUnload = launch { first.unload() }
        backend.generationGate!!.complete(Unit); generation.join(); oldCleanup.join(); staleUnload.join()
        assertEquals(0, backend.cancels); assertEquals(0, backend.unloads)
        assertEquals(listOf("new"), backend.history)
    }

    @Test fun closedOwnerCannotSubmitAnotherLoad() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope)
        val first = pool.lease(backend); first.load(config()); first.close().join()
        try { first.load(config("other")); fail("Revoked lease must reject load") }
        catch (_: CancellationException) { }
        assertEquals(1, backend.initializations)
    }

    @Test fun nextChatWaitsForNativeGenerationCleanup() = runTest {
        val backend = Backend().apply { generationGate = CompletableDeferred(); cleanupGate = CompletableDeferred() }
        val pool = pool(backgroundScope); val first = pool.lease(backend); first.load(config())
        val generating = launch { first.generate(GenerationRequest("old")).toList() }
        runCurrent(); first.close(); generating.cancel(); runCurrent()
        backend.cleanupStarted.await()
        val second = pool.lease(backend)
        var ready = false
        val loading = launch { second.load(config()); ready = true }
        runCurrent(); assertFalse(ready); assertEquals(1, backend.cancels)
        backend.cleanupGate!!.complete(Unit); generating.join(); loading.join()
        assertTrue(ready); assertEquals(1, backend.initializations); assertTrue(backend.history.isEmpty())
    }

    @Test fun backendSwitchUnloadsOldEngineAndPreservesSafeGgufFallback() = runTest {
        val lite = Backend(); val llama = Backend(RuntimeType.LLAMA_CPP); val pool = pool(backgroundScope)
        pool.lease(lite).load(config())
        val gguf = pool.lease(llama); gguf.load(config("gguf"))
        assertEquals(1, lite.unloads)
        gguf.close().join()
        assertNull(llama.loadedPath)
        pool.lease(lite).load(config())
        assertEquals(2, lite.initializations)
    }

    @Test fun idleExpiryEvictsButReturningInTimeCancelsOldExpiry() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope)
        val first = pool.lease(backend); first.load(config()); first.close().join()
        advanceTimeBy(30_000)
        val second = pool.lease(backend); second.load(config())
        advanceTimeBy(60_000); runCurrent(); assertEquals(0, backend.unloads)
        second.close().join(); advanceTimeBy(60_001); runCurrent()
        assertEquals(1, backend.unloads); assertNull(backend.loadedPath)
    }

    @Test fun memoryPressureEvictsOnlyIdleModel() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope)
        val first = pool.lease(backend); first.load(config())
        pool.releaseIdle(); assertEquals(0, backend.unloads)
        first.close().join(); pool.releaseIdle(); assertEquals(1, backend.unloads)
    }

    @Test fun resetFailureIsReportedAndUnsafeEngineIsDiscardedBeforeRetry() = runTest {
        val errors = mutableListOf<Throwable>(); val backend = Backend(); val pool = pool(backgroundScope, errors)
        val first = pool.lease(backend); first.load(config())
        val failure = IllegalStateException("native session close failed"); backend.resetFailure = failure
        first.close().join()
        assertEquals(listOf(failure), errors); assertEquals(1, backend.unloads); assertNull(backend.loadedPath)
        backend.resetFailure = null; pool.lease(backend).load(config()); assertEquals(2, backend.initializations)
    }

    @Test fun actualLoadFailureReachesCallerAndRetryDoesNotClaimReadyEarly() = runTest {
        val backend = Backend(); val pool = pool(backgroundScope); val lease = pool.lease(backend)
        backend.loadFailure = IllegalStateException("native load error")
        try { lease.load(config()); fail("Real failure must propagate") }
        catch (failure: IllegalStateException) { assertEquals("native load error", failure.message) }
        assertEquals(0, backend.initializations)
        backend.loadFailure = null; lease.load(config()); assertEquals(1, backend.initializations)
    }
}
