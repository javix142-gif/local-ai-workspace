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
@Config(sdk = [35], application = Application::class)
class DocumentDeletionLifecycleTest {
    private lateinit var db: WorkspaceDatabase
    private lateinit var root: File
    private lateinit var documents: File
    private lateinit var repository: WorkspaceRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkspaceDatabase::class.java).build()
        root = Files.createTempDirectory("document-delete").toFile()
        documents = File(root, "documents").apply { mkdirs() }
        repository = WorkspaceRepository(db, documents)
    }

    @After fun close() { db.close(); root.deleteRecursively() }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private suspend fun document(projectId: String, id: String, path: File): Long {
        path.writeText("$id private source")
        db.documentDao().insert(DocumentEntity(id, projectId, id, localPath = path.path, mimeType = "text/plain",
            fileHash = id, byteSize = path.length(), importedAt = 1, updatedAt = 1))
        db.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId = id, segmentIndex = 0,
            text = id, normalizedText = id, contentHash = id)))
        val segment = db.documentDao().segmentsForDocument(id).single().id
        db.openHelper.writableDatabase.execSQL("INSERT INTO embedding_records VALUES (?, ?, ?, ?, ?)",
            arrayOf(segment, "embedding", 1, byteArrayOf(1), 1L))
        return segment
    }

    @Test fun deleteIsWorkspaceScopedRetainsCitationAndHonorsSharedPrivatePathReferences() = runBlocking {
        val owner = repository.createProject("owner")
        val other = repository.createProject("other")
        val chat = repository.createConversation(owner)
        val message = MessageEntity("document-citation-message", chat.id, "ASSISTANT", "A prior answer", 2)
        repository.addMessage(message)
        repository.saveCitationEvidence(listOf(CitationEvidenceEntity(
            id = "document-citation", conversationId = chat.id, messageId = message.id,
            evidenceId = "evidence-1", segmentId = 1, excerpt = "Historical excerpt",
            documentId = "remove-me", sourceLabel = "source.txt", retrievalScore = 0.8, createdAt = 3,
        )))
        val sharedFile = File(documents, "shared-source.txt")
        val indexedSegment = document(owner, "remove-me", sharedFile)
        db.documentDao().insert(DocumentEntity("other-reference", other, "Shared source", localPath = sharedFile.path,
            mimeType = "text/plain", fileHash = "other-hash", byteSize = sharedFile.length(), importedAt = 1, updatedAt = 1))
        db.semanticVectorDao().put(SemanticVectorEntity("D:$indexedSegment", "eg1", documentId = "remove-me",
            sourceHash = "source-hash", dimensions = 1, version = 1, vector = byteArrayOf(1), updatedAt = 1))
        var callbackObservedCommittedDeletion = false
        val deletingRepository = WorkspaceRepository(db, documents, onDocumentDeleted = { callbackProject, callbackDocument ->
            callbackObservedCommittedDeletion = callbackObservedCommittedDeletion || (callbackProject == owner && callbackDocument == "remove-me" &&
                db.documentDao().get("remove-me") == null && db.documentDao().segmentsForDocument("remove-me").isEmpty())
        })

        try {
            deletingRepository.deleteDocument(other, "remove-me")
            fail("A workspace must not delete another workspace's document")
        } catch (_: IllegalStateException) { }
        assertNotNull(db.documentDao().get("remove-me"))

        assertEquals(0, deletingRepository.deleteDocument(owner, "remove-me"))

        assertTrue(callbackObservedCommittedDeletion)
        assertNull(db.documentDao().get("remove-me"))
        assertNotNull(db.documentDao().get("other-reference"))
        assertTrue("the shared private copy remains while another document references it", sharedFile.isFile)
        assertTrue(db.documentDao().segmentsForDocument("remove-me").isEmpty())
        assertEquals(0, count("embedding_records"))
        assertNull(db.semanticVectorDao().get("D:$indexedSegment", "eg1"))
        assertEquals("the existing message citation snapshot is retained", "remove-me",
            db.citationEvidenceDao().observeForMessage(message.id).first().single().documentId)
        assertEquals(0, count("pending_document_deletions"))
        deletingRepository.deleteDocument(other, "other-reference")
        assertFalse("the last private reference can now be deleted", sharedFile.exists())
        assertEquals(0, count("pending_document_deletions"))
    }
}
