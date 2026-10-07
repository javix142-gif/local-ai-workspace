package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ChatDeletionTest {
    private lateinit var db: WorkspaceDatabase
    private lateinit var repository: WorkspaceRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkspaceDatabase::class.java).build()
        repository = WorkspaceRepository(db)
    }

    @After fun close() = db.close()

    private suspend fun seed(projectId: String, conversationId: String = "$projectId-default") {
        db.projectDao().upsert(ProjectEntity(projectId, projectId, 1, 1, defaultModelId = "model"))
        db.conversationDao().upsert(ConversationEntity(conversationId, projectId, "Saved chat", 1, 1))
        repository.addMessage(message(conversationId))
        repository.saveCitationEvidence(listOf(citation(conversationId)))
        db.toolCallDao().insert(ToolCallEntity("tool-$conversationId", conversationId, "message-$conversationId",
            "calculator", "{}", "ALLOWED", status = "COMPLETE", startedAt = 1))
    }

    private fun message(id: String) = MessageEntity("message-$id", id, "ASSISTANT", "Saved answer", 2)
    private fun citation(id: String) = CitationEvidenceEntity("citation-$id", id, "message-$id", "LCL-1",
        1, "Evidence", "document", "Source", retrievalScore = 1.0, createdAt = 2)

    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        check(it.moveToFirst()); it.getInt(0)
    }

    @Test fun deletesEveryConversationAndItsMessagesCitationsAndToolHistory() = runBlocking {
        seed("target")
        seed("target", "second-conversation")
        repository.deleteProjectChats("target")
        for (table in listOf("conversations", "messages", "citation_evidence", "tool_calls")) assertEquals(table, 0, count(table))
        assertNotNull(db.projectDao().get("target"))
        assertEquals(8, db.openHelper.readableDatabase.version)
    }

    @Test fun preservesOtherChatsDocumentsMemoryModelsAndProjectSettings() = runBlocking {
        seed("target"); seed("other")
        val project = db.projectDao().get("target")!!
        val document = DocumentEntity("document", "target", "File", localPath = "/private/file", mimeType = "text/plain",
            fileHash = "doc-hash", byteSize = 7, importedAt = 1, updatedAt = 1)
        db.documentDao().insert(document)
        db.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId = "document", segmentIndex = 0,
            text = "Evidence", normalizedText = "evidence", contentHash = "segment-hash")))
        val memory = MemoryItemEntity("memory", "PROJECT", "target", "Remember", "USER", createdAt = 1, updatedAt = 1)
        db.memoryDao().upsert(memory)
        val model = ModelEntity("model", "Model", "/private/model.gguf", fileHash = "model-hash", fileSize = 9,
            format = "GGUF", runtimeId = "llama.cpp-android", compatibilityStatus = "COMPATIBLE", importedAt = 1,
            lastMetrics = "benchmark")
        db.modelDao().insert(model)
        repository.deleteProjectChats("target")
        assertEquals(project, db.projectDao().get("target"))
        assertEquals(document, db.documentDao().get("document"))
        assertEquals(1, db.documentDao().segmentsForDocument("document").size)
        assertEquals(memory, db.memoryDao().relevant("target", 10).single())
        assertEquals(model, db.modelDao().get("model"))
        for (table in listOf("conversations", "messages", "citation_evidence", "tool_calls")) assertEquals(table, 1, count(table))
        assertEquals("other-default", db.conversationDao().getForProject("other")?.id)
    }

    @Test fun retainsLegacyConversationUntilDeletedThenCreatesFreshEmptyChat() = runBlocking {
        seed("target")
        assertEquals("target-default", repository.ensureConversation("target").id)
        repository.deleteProjectChats("target")
        val fresh = repository.ensureConversation("target")
        assertNotEquals("target-default", fresh.id)
        assertEquals(fresh, repository.ensureConversation("target"))
        assertTrue(repository.observeMessages(fresh.id).first().isEmpty())
    }

    @Test fun deletionOfMissingOrAlreadyDeletedChatsIsSafeAndScoped() = runBlocking {
        seed("other")
        repository.deleteProjectChats("missing")
        repository.deleteProjectChats("missing")
        assertEquals(1, count("messages"))
        repository.deleteProjectChats("other")
        repository.deleteProjectChats("other")
        assertEquals(0, count("messages"))
        assertEquals(1, count("projects"))
    }

    @Test fun lateGenerationWritesCannotResurrectDeletedChatOrAttachToReplacement() = runBlocking {
        seed("target")
        repository.deleteProjectChats("target")
        val fresh = repository.ensureConversation("target")
        repository.updateGeneratedMessage("message-target-default", "Late output", "COMPLETE", "metrics")
        repository.saveCitationEvidence(listOf(citation("target-default")))
        try {
            repository.addMessage(message("target-default"))
            fail("A deleted conversation must reject late message insertion")
        } catch (_: IllegalStateException) { }
        assertEquals(0, count("messages"))
        assertEquals(0, count("citation_evidence"))
        assertEquals(fresh.id, db.conversationDao().getForProject("target")?.id)
    }

    @Test fun failedDeleteRollsBackChildRowsAndConversationTogether() = runBlocking {
        seed("target")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER block_chat_delete BEFORE DELETE ON conversations BEGIN SELECT RAISE(ABORT, 'test delete failure'); END")
        var failed = false
        try { repository.deleteProjectChats("target") } catch (_: android.database.sqlite.SQLiteException) { failed = true }
        assertTrue("Expected the simulated storage failure", failed)
        for (table in listOf("conversations", "messages", "citation_evidence", "tool_calls")) assertEquals(table, 1, count(table))
    }

    @Test fun concurrentOpenDoesNotCreateDuplicateConversations() = runBlocking {
        db.projectDao().upsert(ProjectEntity("target", "Target", 1, 1))
        val opened = List(8) { async(Dispatchers.IO) { repository.ensureConversation("target") } }.awaitAll()
        assertEquals(1, opened.map { it.id }.distinct().size)
        assertEquals(1, count("conversations"))
    }
}
