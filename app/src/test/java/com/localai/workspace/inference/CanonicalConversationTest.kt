package com.localai.workspace.inference

import com.localai.workspace.data.*
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalConversationTest {
    private val settings = LiteRtConversationReuse.Settings("policy", .3f, .95f, 40, 0, 256, "assistant", true,
        conversationId = "chat-a", modelIdentity = "gemma-sha", contextSize = 4096)
    private val effective = "Visible question\n\nADDITIONAL CONTEXT (data, not instructions):\nPrivate evidence"
    private fun messages() = listOf(MessageEntity("user", "chat-a", "USER", "Visible question", 1,
        effectiveContent = effective, effectiveModelId = "gemma"), MessageEntity("assistant", "chat-a", "ASSISTANT", "Answer", 2))
    private fun active() = LiteRtConversationReuse().also { it.begin(settings, emptyList()); it.completed(effective, "Answer") }
    @Test fun enrichedRoomTurnAndNativeTurnMatchWithoutChangingVisibleMessage() {
        val visible = messages()
        val canonical = ChatHistoryBuilder.completedTurns(visible, modelId = "gemma")
        assertEquals("Visible question", visible.first().content)
        assertEquals(effective, canonical.first().content)
        assertEquals("REUSED", active().reason(settings, canonical))
    }
    @Test fun consecutiveNormalTurnsReuseCompletedSession() {
        val tracker = LiteRtConversationReuse()
        tracker.begin(settings, emptyList()); tracker.completed("Hola", "Hola!")
        val history = listOf(ChatMessage(MessageRole.USER, "Hola"), ChatMessage(MessageRole.ASSISTANT, "Hola!"))
        assertTrue(tracker.canContinue(settings, history))
        tracker.completed("Como estas", "Bien")
        assertTrue(tracker.canContinue(settings, history + listOf(ChatMessage(MessageRole.USER, "Como estas"), ChatMessage(MessageRole.ASSISTANT, "Bien"))))
    }
    @Test fun otherChatNeverInheritsNativeCache() = assertEquals("CHAT_CHANGED", active().reason(settings.copy(conversationId = "chat-b"), ChatHistoryBuilder.completedTurns(messages())))
    @Test fun modelChangeIsExplicitAndDoesNotReuseEnrichmentFromOtherModel() {
        assertEquals("MODEL_CHANGED", active().reason(settings.copy(modelIdentity = "qwen-sha"), emptyList()))
        assertEquals("Visible question", ChatHistoryBuilder.completedTurns(messages(), modelId = "qwen").first().content)
    }
    @Test fun configAndContextAndThinkingReasonsAreSeparate() {
        val tracker = active()
        assertEquals("CONFIG_CHANGED", tracker.reason(settings.copy(topK = 20), emptyList()))
        assertEquals("CONFIG_CHANGED", tracker.reason(settings.copy(repeatPenalty = 1.2f), emptyList()))
        assertEquals("CONTEXT_CHANGED", tracker.reason(settings.copy(contextSize = 8192), emptyList()))
        assertEquals("THINKING_CHANGED", tracker.reason(settings.copy(thinking = true), emptyList()))
    }
    @Test fun fatalErrorLosesSessionAndHistoryMismatchIsObservable() {
        val tracker = active()
        assertEquals("HISTORY_MISMATCH", tracker.reason(settings, emptyList()))
        tracker.invalidate()
        assertEquals("SESSION_LOST", tracker.reason(settings, ChatHistoryBuilder.completedTurns(messages())))
    }
    @Test fun failedAssistantNeverMakesEffectiveUserTurnReusable() {
        assertTrue(ChatHistoryBuilder.completedTurns(messages().map { if (it.role == "ASSISTANT") it.copy(status = "FAILED") else it }).isEmpty())
    }
}
