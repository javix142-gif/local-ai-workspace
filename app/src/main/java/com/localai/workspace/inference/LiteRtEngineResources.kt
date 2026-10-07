package com.localai.workspace.inference

/** Identity of a verified private file plus every option that changes the native engine. */
internal data class LiteRtEngineKey(
    val canonicalPath: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val expectedSizeBytes: Long?,
    val sha256: String?,
    val contextSize: Int,
    val threads: Int,
    val vision: Boolean,
    val cacheDirectory: String,
    val backendVersion: String,
    val diagnosticMode: Boolean,
    val accelerator: String = "CPU",
    val speculative: Boolean = false,
    val audio: Boolean = false,
)

/**
 * Owns one engine and one disposable conversation. The service's native executor serializes
 * preparation; the shared lock protects destruction against its cancellation executor.
 * Sampler settings and conversation history deliberately do not form part of the engine key.
 */
internal class LiteRtEngineResources<E : AutoCloseable, C : AutoCloseable>(
    private val lock: Any,
    private val engineReady: (E) -> Boolean,
    private val conversationAlive: (C) -> Boolean,
) : AutoCloseable {
    @Volatile var engine: E? = null
        private set
    @Volatile var conversation: C? = null
        private set
    private var loadedKey: LiteRtEngineKey? = null
    val key: LiteRtEngineKey? get() = loadedKey

    fun canReuse(key: LiteRtEngineKey): Boolean =
        !key.diagnosticMode && key.sha256?.matches(Regex("[a-f0-9]{64}")) == true &&
            (loadedKey?.let { loaded -> (!key.vision || loaded.vision) && (!key.audio || loaded.audio) && loaded.copy(vision = key.vision, audio = key.audio) == key } == true) &&
            engine?.let(engineReady) == true

    /** Returns true only when initialization was skipped for an unchanged verified engine. */
    fun prepareEngine(key: LiteRtEngineKey, preserveConversation: Boolean = false, create: () -> E, initialize: (E) -> Unit): Boolean {
        val reusable = canReuse(key)
        if (!preserveConversation || !reusable) closeConversation()
        if (reusable) return true
        close()
        val created = create()
        engine = created // Retain ownership even if initialize throws after allocating a handle.
        initialize(created)
        loadedKey = key // A failed initialization must never qualify for reuse.
        return false
    }

    fun createConversation(create: (E) -> C) {
        closeConversation()
        val current = checkNotNull(engine) { "No LiteRT engine is loaded" }
        check(engineReady(current)) { "LiteRT engine is not initialized" }
        val created = create(current)
        synchronized(lock) { conversation = created }
    }

    fun closeConversation() = synchronized(lock) {
        val old = conversation
        conversation = null
        if (old?.let(conversationAlive) == true) old.close()
    }

    override fun close() = synchronized(lock) {
        try { closeConversation() }
        finally {
            val old = engine
            engine = null
            loadedKey = null
            if (old?.let(engineReady) == true) old.close()
        }
    }
}
