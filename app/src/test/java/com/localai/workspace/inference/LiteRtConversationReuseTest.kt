package com.localai.workspace.inference

import com.localai.workspace.domain.model.ChatMessage
import com.localai.workspace.domain.model.MessageRole
import org.junit.Assert.*
import org.junit.Test

class LiteRtConversationReuseTest {
    private val settings = LiteRtConversationReuse.Settings("policy", .3f, .95f, 40, 0, 128, "assistant", true)
    private val pair = listOf(ChatMessage(MessageRole.USER, "hola"), ChatMessage(MessageRole.ASSISTANT, "Hola!"))

    @Test fun onlyCompletedExactBudgetedTurnsCanUseExistingNativeCache() {
        val reuse = LiteRtConversationReuse()
        reuse.begin(settings, emptyList())
        assertFalse(reuse.canContinue(settings, emptyList()))
        reuse.completed("hola", "Hola!")
        assertTrue(reuse.canContinue(settings, pair))
        reuse.completed("como estas", "Bien, gracias.")
        assertFalse(reuse.canContinue(settings, pair))
        assertTrue(reuse.canContinue(settings, pair + listOf(ChatMessage(MessageRole.USER, "como estas"), ChatMessage(MessageRole.ASSISTANT, "Bien, gracias."))))
    }

    @Test fun droppedHistoryAndContextOrAnswerChangesNeverRetainHiddenNativeData() {
        val reuse = LiteRtConversationReuse()
        reuse.begin(settings, emptyList()); reuse.completed("hola", "Hola!")
        for (history in listOf(emptyList(), pair.drop(1), pair.reversed(), pair.map { it.copy(content = "changed") })) {
            assertFalse(reuse.canContinue(settings, history))
        }
        reuse.begin(settings, emptyList()); reuse.completed("hola\nADDITIONAL CONTEXT: private evidence", "Hola!")
        assertFalse(reuse.canContinue(settings, pair)) // Room's plain turn must not silently keep old retrieval.
    }

    @Test fun samplerPolicyOutputRoleChangesAndNonTextModesRequireFreshConversation() {
        val reuse = LiteRtConversationReuse()
        reuse.begin(settings, emptyList()); reuse.completed("hola", "Hola!")
        for (changed in listOf(settings.copy(systemInstruction = "new policy"), settings.copy(temperature = .7f),
            settings.copy(topP = .8f), settings.copy(topK = 20), settings.copy(seed = 42),
            settings.copy(maxOutput = 512), settings.copy(historyRole = "model"), settings.copy(enabled = false))) {
            assertFalse(reuse.canContinue(changed, pair))
        }
    }

    @Test fun errorCancellationOversizedOrBlankOutputInvalidatesConversationIdentity() {
        val reuse = LiteRtConversationReuse()
        reuse.begin(settings, emptyList()); reuse.completed("hola", "Hola!")
        reuse.invalidate()
        assertFalse(reuse.canContinue(settings, pair))
        for (answer in listOf("", " ", "a".repeat(100_001))) {
            reuse.begin(settings, emptyList()); reuse.completed("hola", answer)
            assertFalse(reuse.canContinue(settings, listOf(ChatMessage(MessageRole.USER, "hola"), ChatMessage(MessageRole.ASSISTANT, answer))))
        }
    }
}
