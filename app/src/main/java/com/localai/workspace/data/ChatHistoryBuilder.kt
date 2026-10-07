package com.localai.workspace.data

import com.localai.workspace.domain.inference.RepetitionLoopDetector
import com.localai.workspace.domain.model.ChatMessage
import com.localai.workspace.domain.model.MessageRole
import com.localai.workspace.domain.model.MessageStatus

/** Only completed user/assistant pairs become model history; failures remain visible in Room/UI. */
object ChatHistoryBuilder {
    fun previousOutputLimitNotice(messages: List<MessageEntity>): String? {
        val previous = messages.maxByOrNull { it.createdAt } ?: return null
        if (previous.role != MessageRole.ASSISTANT.name || previous.status != MessageStatus.COMPLETE.name ||
            GenerationMetricsPresentation.decode(previous.generationMetrics)?.outputLimitReached != true) return null
        return "APP STATUS: The previous answer stopped at its output token limit. Text after that cut was never generated. If asked about missing text, explain that it was unfinished; a continuation is newly generated."
    }

    fun completedTurns(messages: List<MessageEntity>, currentUserId: String? = null, modelId: String? = null): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        var user: MessageEntity? = null
        for (message in messages.sortedBy { it.createdAt }) {
            if (message.id == currentUserId) { user = null; continue }
            when (message.role) {
                MessageRole.USER.name -> user = message.takeIf {
                    it.status == MessageStatus.COMPLETE.name && it.content.isNotBlank()
                }
                MessageRole.ASSISTANT.name -> {
                    val previous = user
                    user = null
                    if (previous != null && message.status == MessageStatus.COMPLETE.name &&
                        message.content.isNotBlank() && !RepetitionLoopDetector.containsLoop(message.content)) {
                        val effective = previous.effectiveContent.takeIf { modelId == null || previous.effectiveModelId == modelId }
                        result += ChatMessage(MessageRole.USER, effective ?: previous.content,
                            previous.imagePath.takeIf { modelId == null || previous.effectiveModelId == modelId },
                            previous.audioPath.takeIf { modelId == null || previous.effectiveModelId == modelId })
                        result += ChatMessage(MessageRole.ASSISTANT, message.content)
                    }
                }
                else -> user = null
            }
        }
        return result
    }
}
