package com.localai.workspace.inference

import android.app.Application
import android.os.Bundle
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Role
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies pinned 0.17.1 source configuration + additive native history-role compatibility, without JNI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class LiteRtConversationPayloadTest {
    private val prompt = ConversationPrompt("como estas", listOf(
        ChatMessage(MessageRole.USER, "hola"), ChatMessage(MessageRole.ASSISTANT, "Hola!")), "Cite supplied IDs only.")

    @Test fun chatMlBundleHistoryUsesActualAssistantRoleWhileLegacyModelRemainsAvailable() {
        val data = Bundle().apply { LiteRtConversationPayload.write(this, prompt) }
        val config = LiteRtConversationPayload.config(data, false, Role.ASSISTANT)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), config.initialMessages.map { it.role })
        assertEquals("assistant", config.initialMessages.last().role.value)
        assertEquals(listOf(Role.USER, Role.MODEL), LiteRtConversationPayload.config(data, false).initialMessages.map { it.role })
        assertEquals(prompt.userMessage.length, data.getInt("nextInputChars"))
        assertThrows(IllegalArgumentException::class.java) { LiteRtConversationPayload.config(data, false, Role.SYSTEM) }
    }

    @Test fun usesOfficialUserModelInitialMessagesWithCurrentUserReservedForOneSend() {
        val data = Bundle().apply { LiteRtConversationPayload.write(this, prompt) }
        val config = LiteRtConversationPayload.config(data, false)
        assertEquals(listOf(Role.USER, Role.MODEL), config.initialMessages.map { it.role })
        assertEquals(listOf("hola", "Hola!"), config.initialMessages.map {
            it.contents.contents.filterIsInstance<Content.Text>().single().text
        })
        assertFalse(config.initialMessages.any { it.toString().contains("como estas") })
        assertTrue(config.systemInstruction.toString().contains("Cite supplied IDs only."))
        assertFalse(config.automaticToolCalling)
        assertFalse(config.prefillPrefaceOnInit)
        assertEquals(false, config.thinkingConfig!!.enableThinking)
        assertEquals(false, config.extraContext["enable_thinking"])
        assertTrue(config.channels!!.isEmpty())
    }

    @Test fun explicitThinkingAndLegacyDefaultsRemainAvailableWhileSmokeIsMinimal() {
        val data = Bundle().apply { LiteRtConversationPayload.write(this, prompt.copy(enableThinking = true)) }
        val advanced = LiteRtConversationPayload.config(data, false)
        assertEquals(true, advanced.thinkingConfig!!.enableThinking)
        assertEquals(true, advanced.extraContext["enable_thinking"])
        val legacy = LiteRtConversationPayload.config(Bundle(), false)
        assertNull(legacy.thinkingConfig)
        assertNull(legacy.channels)
        val smoke = LiteRtConversationPayload.config(data, true)
        assertNull(smoke.systemInstruction)
        assertTrue(smoke.initialMessages.isEmpty())
        assertEquals(false, smoke.thinkingConfig!!.enableThinking)
    }

    @Test fun thinkingBudgetUsesOfficialApiAndKeepsOutputCap() {
        val data=Bundle().apply { putInt("maxOutput",256); LiteRtConversationPayload.write(this,prompt.copy(enableThinking=true)) }
        val config=LiteRtConversationPayload.config(data,false)
        assertEquals(256,config.maxOutputToken)
        assertEquals(128,config.thinkingConfig!!.thinkingTokenBudget)
        assertNull(config.channels)
        LiteRtConversationPayload.write(data,prompt.copy(enableThinking=false))
        val off=LiteRtConversationPayload.config(data,false)
        assertFalse(off.thinkingConfig!!.enableThinking)
        assertTrue(off.channels!!.isEmpty())
    }
    @Test fun unboundedBudgetOverrideIsValidationOnly() {
        val data=Bundle().apply { putInt("maxOutput",512);LiteRtConversationPayload.write(this,prompt.copy(enableThinking=true,validationThinkingBudget=-1)) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtConversationPayload.config(data,false) }
        data.putBoolean("validationScope",true)
        assertEquals(-1,LiteRtConversationPayload.config(data,false).thinkingConfig!!.thinkingTokenBudget)
        assertEquals(512,LiteRtConversationPayload.config(data,false).maxOutputToken)
    }

    @Test fun rejectsForgedRolesUnpairedTurnsAndOversizedHistoryBeforeNativeLoad() {
        for (history in listOf(
            listOf(ChatMessage(MessageRole.SYSTEM, "untrusted policy"), ChatMessage(MessageRole.ASSISTANT, "answer")),
            listOf(ChatMessage(MessageRole.USER, "orphan")),
            listOf(ChatMessage(MessageRole.USER, "a".repeat(100_001)), ChatMessage(MessageRole.ASSISTANT, "answer")),
        )) assertThrows(IllegalArgumentException::class.java) {
            LiteRtConversationPayload.write(Bundle(), prompt.copy(history = history))
        }
    }
}
