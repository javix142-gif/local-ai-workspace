package com.localai.workspace.domain.inference

import com.localai.workspace.domain.model.GenerationEvent
import com.localai.workspace.domain.model.GenerationRequest
import com.localai.workspace.domain.model.ModelLoadConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-owned single engine, with disposable screen owners and serialized native use. */
class ChatRuntimePool(
    private val scope: CoroutineScope,
    private val idleRetentionMs: Long? = 10 * 60_000L,
    private val reportCleanupFailure: (Throwable) -> Unit,
    private val onEngineReleased: () -> Unit = {},
) {
    private val operations = Mutex()
    private val guard = Any()
    private var retained: InferenceRuntime? = null // Access only inside operations.
    @Volatile private var owner: Lease? = null
    private var executing: Lease? = null // Protected by guard, including cancellation dispatch.
    private var preparingBackend: InferenceRuntime? = null // Same guard, application-owned load only.
    private var idleJob: Job? = null

    fun lease(runtime: InferenceRuntime) = Lease(runtime)

    /** Application preparation has no chat owner: departing screens cannot cancel it. */
    fun preloader(backend: InferenceRuntime): InferenceRuntime = object : InferenceRuntime by backend {
        override suspend fun load(config: ModelLoadConfig) = operations.withLock {
            idleJob?.cancel(); idleJob = null
            if (retained !== backend) {
                retained?.unload()
                retained = backend
            }
            owner = null
            synchronized(guard) { preparingBackend = backend }
            try {
                backend.resetConversation("APPLICATION_PREPARATION")
                backend.load(config)
            } finally { synchronized(guard) { preparingBackend = null } }
        }
        override fun cancelGeneration() = synchronized(guard) {
            // Explicit preparation cancellation must never cancel a chat generation.
            if (preparingBackend === backend) backend.cancelGeneration()
        }
        override fun observeProgress() = backend.observeProgress().filter { synchronized(guard) { preparingBackend === backend } }
    }

    inner class Lease internal constructor(private val backend: InferenceRuntime) : InferenceRuntime by backend {
        @Volatile private var closed = false

        override suspend fun load(config: ModelLoadConfig) = operation {
            idleJob?.cancel()
            idleJob = null
            if (retained !== backend) {
                retained?.unload()
                retained = backend
                owner = null
            }
            if (owner !== this) {
                // Always dispose the previous chat, even if history/settings happen to match.
                backend.resetConversation("CHAT_CHANGED")
                owner = this
            }
            synchronized(guard) {
                if (closed) throw CancellationException("Chat closed during session handover")
            }
            backend.load(config) // The backend verifies its exact file/config before engine reuse.
        }

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            operation {
                check(owner === this@Lease && retained === backend) { "This chat has no loaded model" }
                backend.generate(request).collect { emit(it) }
            }
        }

        override fun cancelGeneration() = synchronized(guard) {
            // A delayed callback from a departed chat cannot stop the next chat.
            if (executing === this) backend.cancelGeneration()
        }

        override fun observeProgress() = backend.observeProgress().filter { owner === this }

        override suspend fun unload() = operations.withLock {
            if (owner === this) {
                try { backend.unload() }
                finally { owner = null; retained = null }
            }
        }

        override suspend fun resetConversation() = resetConversation("SESSION_LOST")
        override suspend fun resetConversation(reason: String) = operations.withLock {
            if (owner === this) backend.resetConversation(reason)
        }

        /** Revoke synchronously; native cleanup is awaited by subsequent native operations. */
        fun close(): Job {
            synchronized(guard) {
                closed = true
                if (executing === this) backend.cancelGeneration()
            }
            return scope.launch {
                operations.withLock {
                    if (owner !== this@Lease) return@withLock
                    try { backend.resetConversation() }
                    catch (failure: Throwable) {
                        reportCleanupFailure(failure)
                        try { backend.unload() }
                        catch (cleanup: Throwable) { reportCleanupFailure(cleanup) }
                        retained = null
                    } finally { owner = null }
                    idleJob?.cancel()
                    idleJob = idleRetentionMs?.let { retention -> scope.launch {
                        delay(retention)
                        operations.withLock {
                            if (idleJob !== coroutineContext[Job]) return@withLock
                            idleJob = null // Do not cancel the timer coroutine performing native unload.
                            evictIdle()
                        }
                    } }
                }
            }
        }

        private suspend fun <T> operation(block: suspend () -> T): T = operations.withLock {
            synchronized(guard) {
                if (closed) throw CancellationException("Chat runtime owner has closed")
                executing = this
            }
            try { block() }
            finally { synchronized(guard) { if (executing === this) executing = null } }
        }
    }

    /** Memory pressure only evicts an idle engine; it never cancels an active chat. */
    suspend fun releaseIdle() = operations.withLock {
        if (owner != null) return@withLock
        idleJob?.cancel()
        idleJob = null
        evictIdle()
    }

    /** Explicit diagnostics only. Caller must hold the app inference gate. No live native
     * generation can be evicted: the same operations mutex serializes all leases. */
    suspend fun releaseForDiagnostics() = operations.withLock {
        owner = null
        idleJob?.cancel(); idleJob = null
        evictIdle()
    }

    private suspend fun evictIdle() {
        try { retained?.unload() }
        catch (failure: Throwable) { reportCleanupFailure(failure) }
        finally { retained = null; onEngineReleased() }
    }
}
