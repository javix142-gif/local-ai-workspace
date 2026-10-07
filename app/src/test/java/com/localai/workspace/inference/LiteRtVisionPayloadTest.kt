package com.localai.workspace.inference

import android.os.Bundle
import com.google.ai.edge.litertlm.Content
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class LiteRtVisionPayloadTest {
    @Test fun imageReferenceSurvivesIpcAndNativeHistoryConstruction() {
        val prompt = ConversationPrompt("Follow up", history = listOf(
            ChatMessage(MessageRole.USER, "Describe", "/private/image.jpg"), ChatMessage(MessageRole.ASSISTANT, "Red")))
        val data = Bundle(); LiteRtConversationPayload.write(data, prompt)
        assertEquals(prompt.history, LiteRtConversationPayload.history(data))
        val user = LiteRtConversationPayload.config(data, false).initialMessages!!.first()
        assertEquals(Content.ImageFile("/private/image.jpg"), user.contents.contents.first())
        assertEquals(Content.Text("Describe"), user.contents.contents.last())
    }
}
