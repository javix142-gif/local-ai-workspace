package com.localai.workspace.domain.inference

enum class GenerationStage {
    IDLE, LOADING_MODEL, WARMING_MODEL, CREATING_SESSION, PREPARING_PROMPT, PREFILLING,
    GENERATING, COMPLETED, CANCELLED, ERROR, UNLOADING,
}

data class GenerationError(
    val stage: GenerationStage,
    val code: String,
    val message: String,
    val elapsedMs: Long = 0,
    val technicalDetail: String? = null,
    val checkpoint: String? = null,
) {
    fun displayText(): String = "$message\nStage: ${stage.name}\nCode: $code\nElapsed: ${elapsedMs} ms" +
        (checkpoint?.let { "\nLast checkpoint: $it" } ?: "")
}

class GenerationException(val diagnostic: GenerationError, cause: Throwable? = null) :
    Exception(diagnostic.displayText(), cause)

data class GenerationProgress(
    val stage: GenerationStage = GenerationStage.IDLE,
    val event: String? = null,
    val elapsedMs: Long = 0,
    val outputChunks: Int = 0,
    // A streaming callback is a chunk, not necessarily one tokenizer token.
    val generatedTokens: Int? = null,
    val error: GenerationError? = null,
    val cancellationRequested: Boolean = false,
    val detail: String? = null,
    val bytesCompleted: Long? = null,
    val bytesTotal: Long? = null,
) {
    val label: String get() = if (cancellationRequested) "Cancelling native operation…" else when (stage) {
        GenerationStage.IDLE -> "Ready"
        GenerationStage.LOADING_MODEL -> "Loading model…"
        GenerationStage.WARMING_MODEL -> "Model loaded · warming…"
        GenerationStage.CREATING_SESSION -> "Creating session…"
        GenerationStage.PREPARING_PROMPT -> "Preparing prompt…"
        GenerationStage.PREFILLING -> "Processing prompt / waiting for first token…"
        GenerationStage.GENERATING -> "Generating…"
        GenerationStage.COMPLETED -> "Completed"
        GenerationStage.CANCELLED -> "Cancelled · Ready to retry"
        GenerationStage.ERROR -> "Generation failed · Ready to retry"
        GenerationStage.UNLOADING -> "Unloading model…"
    }
}

data class InferenceTimeouts(
    val bindMs: Long = 15_000,
    val modelLoadMs: Long = 240_000,
    val sessionMs: Long = 60_000,
    val promptMs: Long = 30_000,
    val firstTokenMs: Long = 180_000,
    val decodeIdleMs: Long = 120_000,
    val totalGenerationMs: Long = 900_000,
    val cancelMs: Long = 5_000,
    val unloadMs: Long = 15_000,
)

/** Monotonic, testable deadlines. Heartbeats never extend first-token/load deadlines. */
class InferenceWatchdog(
    private val timeouts: InferenceTimeouts = InferenceTimeouts(),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var stage = GenerationStage.IDLE
    private var stageStartedAt = nowMs()
    private var lastOutputAt = stageStartedAt
    private var generationStartedAt: Long? = null
    private var cancelStartedAt: Long? = null

    @Synchronized fun enter(next: GenerationStage) {
        if (next != stage) {
            stage = next
            stageStartedAt = nowMs()
            if (next == GenerationStage.PREFILLING) generationStartedAt = stageStartedAt
            if (next == GenerationStage.GENERATING) lastOutputAt = stageStartedAt
        }
    }

    @Synchronized fun outputReceived() { lastOutputAt = nowMs(); enter(GenerationStage.GENERATING) }
    @Synchronized fun cancelRequested() { if (cancelStartedAt == null) cancelStartedAt = nowMs() }

    @Synchronized fun expired(): GenerationError? {
        val now = nowMs()
        val canceled = cancelStartedAt
        if (canceled != null && now - canceled >= timeouts.cancelMs) {
            return failure("CANCEL_TIMEOUT", "Native cancellation did not finish; the LiteRT worker was reset.", now - canceled)
        }
        val generation = generationStartedAt
        if (generation != null && now - generation >= timeouts.totalGenerationMs) {
            return failure("GENERATION_TIMEOUT", "LiteRT generation exceeded its total time limit.", now - generation)
        }
        val elapsed = now - if (stage == GenerationStage.GENERATING) lastOutputAt else stageStartedAt
        val limit = when (stage) {
            GenerationStage.LOADING_MODEL -> timeouts.modelLoadMs
            GenerationStage.WARMING_MODEL -> 45_000L
            GenerationStage.CREATING_SESSION -> timeouts.sessionMs
            GenerationStage.PREPARING_PROMPT -> timeouts.promptMs
            GenerationStage.PREFILLING -> timeouts.firstTokenMs
            GenerationStage.GENERATING -> timeouts.decodeIdleMs
            GenerationStage.UNLOADING -> timeouts.unloadMs
            else -> return null
        }
        if (elapsed < limit) return null
        val code = when (stage) {
            GenerationStage.LOADING_MODEL -> "MODEL_LOAD_TIMEOUT"
            GenerationStage.WARMING_MODEL -> "WARMUP_TIMEOUT"
            GenerationStage.CREATING_SESSION -> "SESSION_CREATE_TIMEOUT"
            GenerationStage.PREPARING_PROMPT -> "PROMPT_FORMAT_TIMEOUT"
            // Conversation combines prefill and decode; it exposes no prefill-complete callback.
            GenerationStage.PREFILLING -> "FIRST_TOKEN_TIMEOUT"
            GenerationStage.GENERATING -> "DECODE_TIMEOUT"
            else -> "UNLOAD_TIMEOUT"
        }
        return failure(code, "LiteRT exceeded its $limit ms limit (observed $elapsed ms). Worker reset requested; retry is available.", elapsed)
    }

    private fun failure(code: String, message: String, elapsed: Long) = GenerationError(
        stage, code, message, elapsed,
        "Watchdog deadline. Before the first callback, the Conversation API does not expose the boundary between prefill and first decode.",
    )
}
