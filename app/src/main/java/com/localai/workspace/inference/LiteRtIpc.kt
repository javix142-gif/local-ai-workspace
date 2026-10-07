package com.localai.workspace.inference

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import com.localai.workspace.domain.model.RuntimeMetrics

/** Internal, non-exported Messenger protocol. Models stay on disk; IPC carries small messages. */
internal object LiteRtIpc {
    const val HELLO = 1
    const val LOAD = 2
    const val GENERATE = 3
    const val CANCEL = 4
    const val UNLOAD = 5
    const val RESET_CONVERSATION = 6
    const val EVENT = 10
    const val VERSION = "0.17.1"

    /** Handler recycles Message after dispatch. Never retain it in a queued native task. */
    class Request(val command: Int, val id: String, val reply: Messenger, val data: Bundle)

    fun snapshot(message: Message): Request? {
        val reply = message.replyTo ?: return null
        val data = message.data.deepCopy()
        val id = data.getString("id") ?: return null
        return Request(message.what, id, reply, data)
    }

    fun commandName(command: Int): String = when (command) {
        HELLO -> "HELLO"
        LOAD -> "LOAD"
        GENERATE -> "GENERATE"
        CANCEL -> "CANCEL"
        UNLOAD -> "UNLOAD"
        RESET_CONVERSATION -> "RESET_CONVERSATION"
        else -> "UNKNOWN"
    }

    fun encodeMetrics(metrics: RuntimeMetrics) = Bundle().apply {
        putString("performanceMetrics", com.google.gson.Gson().toJson(metrics))
        metrics.modelLoadDurationMs?.let { putLong("loadMs", it) }
        metrics.engineReused?.let { putBoolean("engineReused", it) }
        metrics.sessionReused?.let { putBoolean("sessionReused", it) }
        metrics.modelPreparationDurationMs?.let { putLong("prepareMs", it) }
        metrics.modelRetainedAfterStop?.let { putBoolean("engineRetained", it) }
        metrics.sessionCreationDurationMs?.let { putLong("sessionMs", it) }
        metrics.prefillDurationMs?.let { putLong("prefillMs", it) }
        metrics.promptTokens?.let { putInt("promptTokens", it) }
        metrics.outputTokens?.let { putInt("outputTokens", it) }
        metrics.timeToFirstTokenMs?.let { putLong("ttftMs", it) }
        metrics.prefillTokensPerSecond?.let { putDouble("prefillTps", it) }
        metrics.decodeTokensPerSecond?.let { putDouble("decodeTps", it) }
        metrics.totalGenerationDurationMs?.let { putLong("totalMs", it) }
        metrics.contextSize?.let { putInt("context", it) }
        metrics.generationRequestedAt?.let { putLong("requestedAt", it) }
        metrics.prefillStartedAt?.let { putLong("prefillStartedAt", it) }
        metrics.firstTokenAt?.let { putLong("firstTokenAt", it) }
        metrics.outputChunks?.let { putInt("chunks", it) }
        metrics.eosObserved?.let { putBoolean("eos", it) }
        metrics.nativeCompletionObserved?.let { putBoolean("nativeDone", it) }
        metrics.outputLimitReached?.let { putBoolean("outputLimitReached", it) }
        putString("backend", metrics.backend)
    }

    fun decodeMetrics(data: Bundle): RuntimeMetrics = data.getString("performanceMetrics")?.let {
        com.google.gson.Gson().fromJson(it, RuntimeMetrics::class.java)
    } ?: RuntimeMetrics(
        modelLoadDurationMs = data.longOrNull("loadMs"),
        engineReused = if (data.containsKey("engineReused")) data.getBoolean("engineReused") else null,
        sessionReused = if (data.containsKey("sessionReused")) data.getBoolean("sessionReused") else null,
        modelPreparationDurationMs = data.longOrNull("prepareMs"),
        modelRetainedAfterStop = if (data.containsKey("engineRetained")) data.getBoolean("engineRetained") else null,
        sessionCreationDurationMs = data.longOrNull("sessionMs"),
        prefillDurationMs = data.longOrNull("prefillMs"),
        promptTokens = data.intOrNull("promptTokens"),
        outputTokens = data.intOrNull("outputTokens"),
        timeToFirstTokenMs = data.longOrNull("ttftMs"),
        prefillTokensPerSecond = data.doubleOrNull("prefillTps"),
        decodeTokensPerSecond = data.doubleOrNull("decodeTps"),
        totalGenerationDurationMs = data.longOrNull("totalMs"),
        contextSize = data.intOrNull("context"),
        backend = data.getString("backend"),
        generationRequestedAt = data.longOrNull("requestedAt"),
        prefillStartedAt = data.longOrNull("prefillStartedAt"),
        firstTokenAt = data.longOrNull("firstTokenAt"),
        outputChunks = data.intOrNull("chunks"),
        eosObserved = if (data.containsKey("eos")) data.getBoolean("eos") else null,
        nativeCompletionObserved = if (data.containsKey("nativeDone")) data.getBoolean("nativeDone") else null,
        outputLimitReached = if (data.containsKey("outputLimitReached")) data.getBoolean("outputLimitReached") else null,
    )

    private fun Bundle.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
    private fun Bundle.intOrNull(key: String): Int? = if (containsKey(key)) getInt(key) else null
    private fun Bundle.doubleOrNull(key: String): Double? = if (containsKey(key)) getDouble(key) else null
}
