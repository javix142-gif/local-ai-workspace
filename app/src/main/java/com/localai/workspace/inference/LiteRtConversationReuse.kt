package com.localai.workspace.inference

import com.localai.workspace.domain.model.ChatMessage
import com.localai.workspace.domain.model.MessageRole

/** Exact budgeted transcript/config equality; never keep dropped evidence or hidden older turns. */
internal class LiteRtConversationReuse {
    data class Settings(
        val systemInstruction: String?, val temperature: Float, val topP: Float, val topK: Int,
        val seed: Int, val maxOutput: Int, val historyRole: String,
        val enabled: Boolean,
        val conversationId: String? = null,
        val modelIdentity: String? = null,
        val contextSize: Int? = null,
        val repeatPenalty: Float = 1f,
        val thinking: Boolean = false,
    )
    private var settings: Settings? = null
    private var history = emptyList<ChatMessage>()
    private var completed = false

    fun reason(requested: Settings, requestedHistory: List<ChatMessage>): String {
        val previous = settings ?: return "SESSION_LOST"
        if (previous.modelIdentity != requested.modelIdentity) return "MODEL_CHANGED"
        if (previous.conversationId != requested.conversationId) return "CHAT_CHANGED"
        if (previous.contextSize != requested.contextSize) return "CONTEXT_CHANGED"
        if (previous.thinking != requested.thinking) return "THINKING_CHANGED"
        if (previous.enabled != requested.enabled || !requested.enabled) return "MODALITY_CHANGED"
        if (previous.systemInstruction != requested.systemInstruction) return "CONTEXT_CHANGED"
        if (previous != requested) return "CONFIG_CHANGED"
        if (!completed) return "SESSION_LOST"
        if (history != requestedHistory) return "HISTORY_MISMATCH"
        return "REUSED"
    }

    fun canContinue(requested: Settings, requestedHistory: List<ChatMessage>): Boolean = reason(requested, requestedHistory) == "REUSED"

    fun begin(requested: Settings, requestedHistory: List<ChatMessage>) {
        settings = requested
        history = requestedHistory.toList()
        completed = false
    }

    fun completed(userMessage: String, answer: String, imagePath: String? = null, audioPath: String? = null) {
        if (settings?.enabled != true || userMessage.isBlank() || answer.isBlank() ||
            history.sumOf { it.content.length } + userMessage.length + answer.length > 100_000) {
            invalidate(); return
        }
        history = history + listOf(ChatMessage(MessageRole.USER, userMessage, imagePath, audioPath), ChatMessage(MessageRole.ASSISTANT, answer))
        completed = true
    }

    fun invalidate() { settings = null; history = emptyList(); completed = false }
}
