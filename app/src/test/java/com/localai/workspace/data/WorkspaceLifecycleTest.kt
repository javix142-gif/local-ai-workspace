package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
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
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class WorkspaceLifecycleTest {
    private lateinit var db: WorkspaceDatabase
    private lateinit var repo: WorkspaceRepository
    private lateinit var files: File
    private lateinit var documents: File
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkspaceDatabase::class.java).build()
        files = Files.createTempDirectory("workspace-lifecycle").toFile()
        documents = File(files, "documents").apply { mkdirs() }
        repo = WorkspaceRepository(db, documents)
    }
    @After fun close() { db.close(); files.deleteRecursively() }
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private suspend fun conversation(project: String, text: String): ConversationEntity {
        val chat = repo.createConversation(project)
        repo.addMessage(MessageEntity("msg-${chat.id}", chat.id, "USER", text, 5))
        repo.saveCitationEvidence(listOf(CitationEvidenceEntity("cite-${chat.id}", chat.id, "msg-${chat.id}", "LCL", 1,
            "evidence", "doc", "doc", retrievalScore = 1.0, createdAt = 1)))
        db.toolCallDao().insert(ToolCallEntity("tool-${chat.id}", chat.id, "msg-${chat.id}", "calculator", "{}", "ALLOW", status = "COMPLETE", startedAt = 1))
        return chat
    }
    private suspend fun document(project: String, id: String, path: File): Long {
        path.writeText("$id private source")
        db.documentDao().insert(DocumentEntity(id, project, id, localPath = path.path, mimeType = "text/plain",
            fileHash = id, byteSize = path.length(), importedAt = 1, updatedAt = 1))
        db.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId = id, segmentIndex = 0,
            text = id, normalizedText = id, contentHash = id)))
        val segment = db.documentDao().segmentsForDocument(id).single().id
        db.openHelper.writableDatabase.execSQL("INSERT INTO embedding_records VALUES (?, ?, ?, ?, ?)",
            arrayOf(segment, "embedding", 1, byteArrayOf(1), 1L))
        return segment
    }

    @Test fun directChatIsIndependentAndNamedFromItsFirstMessage() = runBlocking {
        val a = repo.createChat(); val b = repo.createChat()
        assertNotEquals(a, b)
        assertEquals(WorkspaceKind.CHAT, db.projectDao().get(a)!!.workspaceKind)
        val chat = repo.ensureConversation(a)
        repo.addMessage(MessageEntity("user", chat.id, "USER", "Hola desde mi chat", 10))
        assertEquals("Hola desde mi chat", db.projectDao().get(a)!!.name)
        assertEquals("Hola desde mi chat", db.conversationDao().get(chat.id)!!.title)
        assertEquals("New chat", db.projectDao().get(b)!!.name)
        assertTrue(repo.observeMessages(repo.ensureConversation(b).id).first().isEmpty())
    }

    @Test fun projectContainsMultipleIndependentlyAddressableChats() = runBlocking {
        val project = repo.createProject("Study")
        val a = conversation(project, "First subject"); val b = conversation(project, "Second subject")
        assertNotEquals(a.id, b.id)
        assertEquals(WorkspaceKind.PROJECT, db.projectDao().get(project)!!.workspaceKind)
        assertEquals("Study", db.projectDao().get(project)!!.name)
        assertEquals(a.id, repo.conversationForChat(project, a.id).id)
        assertEquals(b.id, repo.conversationForChat(project, b.id).id)
        assertEquals("First subject", repo.observeMessages(a.id).first().single().content)
        assertEquals(2, repo.observeConversations(project).first().size)
    }

    @Test fun deletingOneProjectChatKeepsSiblingsFilesAndMemory() = runBlocking {
        val project = repo.createProject("Study"); val a = conversation(project, "one"); val b = conversation(project, "two")
        val file = File(documents, "keep.txt"); document(project, "keepdoc", file)
        db.memoryDao().upsert(MemoryItemEntity("memory", "PROJECT", project, "keep", "USER", createdAt = 1, updatedAt = 1))
        assertEquals(0, repo.deleteConversation(a.id))
        assertNull(db.conversationDao().get(a.id)); assertNotNull(db.conversationDao().get(b.id))
        assertNotNull(db.projectDao().get(project)); assertTrue(file.isFile)
        assertEquals(1, count("messages")); assertEquals(1, count("citation_evidence")); assertEquals(1, count("tool_calls"))
        assertEquals(1, count("documents")); assertEquals(1, count("memory_items"))
    }

    @Test fun deletingProjectRemovesItsCardAndEveryOwnedResourceButKeepsOtherWorkAndModels() = runBlocking {
        val target = repo.createProject("target"); val other = repo.createProject("other")
        conversation(target, "one"); conversation(target, "two"); conversation(other, "keep")
        val targetFile = File(documents, "remove.txt"); val keepFile = File(documents, "keep.txt")
        document(target, "uniquewordtarget", targetFile); document(other, "keepword", keepFile)
        db.memoryDao().upsert(MemoryItemEntity("local", "PROJECT", target, "remove", "USER", createdAt = 1, updatedAt = 1))
        db.memoryDao().upsert(MemoryItemEntity("global", "GLOBAL", null, "keep", "USER", createdAt = 1, updatedAt = 1))
        val modelFile = File(files, "models/model.gguf").apply { parentFile!!.mkdirs(); writeText("model") }
        db.modelDao().insert(ModelEntity("model", "Model", modelFile.path, fileHash = "model", fileSize = 5,
            format = "GGUF", runtimeId = "llama.cpp-android", compatibilityStatus = "COMPATIBLE", importedAt = 1))
        assertEquals(0, repo.deleteWorkspace(target))
        assertNull(db.projectDao().get(target)); assertFalse(targetFile.exists()); assertTrue(keepFile.exists())
        assertNotNull(db.projectDao().get(other)); assertTrue(modelFile.exists()); assertNotNull(db.modelDao().get("model"))
        for (table in listOf("projects", "conversations", "messages", "citation_evidence", "tool_calls", "documents", "document_segments", "embedding_records", "memory_items")) assertEquals(table, 1, count(table))
        db.openHelper.readableDatabase.query("SELECT docid FROM document_segments_fts WHERE document_segments_fts MATCH 'uniquewordtarget'").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun standaloneChatDeletionRemovesItsListEntryFilesAndLocalMemory() = runBlocking {
        val id = repo.createChat(); val chat = repo.ensureConversation(id)
        val file = File(documents, "attachment.txt"); document(id, "chatfile", file)
        db.memoryDao().upsert(MemoryItemEntity("memory", "PROJECT", id, "remove", "USER", createdAt = 1, updatedAt = 1))
        repo.deleteConversation(chat.id)
        assertTrue(repo.activeProjects.first().isEmpty()); assertEquals(0, count("projects"))
        assertEquals(0, count("conversations")); assertEquals(0, count("memory_items")); assertFalse(file.exists())
    }

    @Test fun storageFailureRollsBackProjectAndItsCleanupOutbox() = runBlocking {
        val target = repo.createProject("target"); conversation(target, "one")
        val file = File(documents, "rollback.txt"); document(target, "rollbackword", file)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER block_project_delete BEFORE DELETE ON projects BEGIN SELECT RAISE(ABORT, 'storage failure'); END")
        try { repo.deleteWorkspace(target); fail("Expected database failure") } catch (_: android.database.sqlite.SQLiteException) { }
        for (table in listOf("projects", "conversations", "messages", "documents", "document_segments", "embedding_records")) assertEquals(table, 1, count(table))
        assertEquals(0, count("pending_document_deletions")); assertTrue(file.exists())
    }

    @Test fun pendingPrivateFilesAreRetriedAfterRepositoryRestart() = runBlocking {
        val target = repo.createProject("target"); val file = File(documents, "later.txt"); document(target, "laterword", file)
        // Missing root deliberately models an unavailable cleaner; metadata commits with a durable outbox.
        assertEquals(1, WorkspaceRepository(db).deleteWorkspace(target))
        assertNull(db.projectDao().get(target)); assertTrue(file.exists()); assertEquals(1, count("pending_document_deletions"))
        assertEquals(0, WorkspaceRepository(db, documents).cleanupDeletedDocuments())
        assertFalse(file.exists()); assertEquals(0, count("pending_document_deletions"))
    }

    @Test fun cleanupNeverDeletesModelsOriginalsOrSymlinkTargetsOutsideDocuments() = runBlocking {
        val original = File(files, "original.txt").apply { writeText("original") }
        val model = File(files, "models/model.litertlm").apply { parentFile!!.mkdirs(); writeText("model") }
        val link = File(documents, "escape.txt"); Files.createSymbolicLink(link.toPath(), original.toPath())
        db.pendingDocumentDeletionDao().insertAll(listOf(original, model, link).map { PendingDocumentDeletionEntity(it.path) })
        assertEquals(3, repo.cleanupDeletedDocuments()); assertTrue(original.isFile); assertTrue(model.isFile)
    }

    @Test fun cleanupKeepsFilesStillReferencedByAnotherWorkspace() = runBlocking {
        val a = repo.createProject("a"); val b = repo.createProject("b"); val file = File(documents, "shared.txt")
        document(a, "doca", file)
        db.documentDao().insert(DocumentEntity("docb", b, "B", localPath = file.path, mimeType = "text/plain", fileHash = "b",
            byteSize = file.length(), importedAt = 1, updatedAt = 1))
        repo.deleteWorkspace(a); assertTrue(file.isFile); assertNotNull(db.documentDao().get("docb"))
        repo.deleteWorkspace(b); assertFalse(file.exists())
    }

    @Test fun deletedWorkCannotBeRecreatedByLateChatOrCitationWrites() = runBlocking {
        val target = repo.createProject("target"); val chat = conversation(target, "first")
        repo.deleteWorkspace(target)
        try { repo.ensureConversation(target); fail("Missing project must not be recreated") } catch (_: IllegalStateException) { }
        try { repo.createConversation(target); fail("Missing project must not accept new chat") } catch (_: IllegalStateException) { }
        try { repo.addMessage(MessageEntity("late", chat.id, "USER", "late", 99)); fail("Deleted chat must not accept output") } catch (_: IllegalStateException) { }
        repo.saveCitationEvidence(listOf(CitationEvidenceEntity("late", chat.id, "late", "LCL", 1, "text", "doc", "doc", retrievalScore = 1.0, createdAt = 1)))
        assertEquals(0, count("projects")); assertEquals(0, count("conversations")); assertEquals(0, count("messages")); assertEquals(0, count("citation_evidence"))
        repo.deleteWorkspace(target); repo.deleteConversation(chat.id) // Idempotent.
        Unit
    }

    @Test fun explicitConversationIdCannotOpenAnotherProjectsHistory() = runBlocking {
        val a = repo.createProject("a"); val b = repo.createProject("b"); val chat = conversation(a, "secret")
        try { repo.conversationForChat(b, chat.id); fail("Cross-project conversation must be rejected") } catch (_: IllegalStateException) { }
        assertEquals(1, count("conversations")); assertEquals(1, count("messages"))
    }
}
