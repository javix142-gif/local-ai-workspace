package com.localai.workspace.inference

import com.google.ai.edge.litertlm.Content
import com.localai.workspace.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LiteRtVisionInputTest {
    @Test fun sameNativeTurnContainsImageAndQuestionAndTextTurnDoesNot() {
        val root = Files.createTempDirectory("vision-test").toFile()
        try {
            val file = java.io.File(root, "attachments/image.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            val contents = LiteRtVisionInput.contents(root, "What is this?", file.path, true).contents
            assertEquals(listOf(Content.ImageFile(file.canonicalPath), Content.Text("What is this?")), contents)
            assertEquals(listOf(Content.Text("Hello")), LiteRtVisionInput.contents(root, "Hello", null, false).contents)
            for ((path, initialized) in listOf(file.path to false, "${root.path}/missing" to true)) {
                try { LiteRtVisionInput.contents(root, "Question", path, initialized); fail("Must fail, never send text") }
                catch (_: IllegalStateException) { } catch (_: IllegalArgumentException) { }
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun canonicalVisualHistoryContinuesOnlyInItsOwnChat() {
        val tracker = LiteRtConversationReuse()
        val settings = LiteRtConversationReuse.Settings(null, .3f, .95f, 40, 0, 128, "model", true,
            conversationId = "chat-a", modelIdentity = "gemma", contextSize = 4096)
        tracker.begin(settings, emptyList()); tracker.completed("Question", "Red object", "/private/image.jpg")
        val history = listOf(ChatMessage(MessageRole.USER, "Question", "/private/image.jpg"), ChatMessage(MessageRole.ASSISTANT, "Red object"))
        assertTrue(tracker.canContinue(settings, history))
        assertEquals("CHAT_CHANGED", tracker.reason(settings.copy(conversationId = "chat-b"), history))
        assertEquals("HISTORY_MISMATCH", tracker.reason(settings, history.map { it.copy(imagePath = null) }))
        tracker.invalidate(); assertFalse(tracker.canContinue(settings, history))
    }
}
