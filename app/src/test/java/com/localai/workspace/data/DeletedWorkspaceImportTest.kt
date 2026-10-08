package com.localai.workspace.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class DeletedWorkspaceImportTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var db: WorkspaceDatabase
    private lateinit var repo: WorkspaceRepository
    private lateinit var service: DocumentIngestionService
    private lateinit var root: File
    private var existingFiles = emptySet<String>()
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        root = File(context.filesDir, "documents").apply { mkdirs() }
        existingFiles = root.listFiles().orEmpty().map { it.name }.toSet()
        repo = WorkspaceRepository(db, root)
        service = DocumentIngestionService(context, db, db.documentDao())
    }
    @After fun close() { db.close(); root.listFiles().orEmpty().filter { it.name !in existingFiles }.forEach { it.delete() } }
    private fun newFiles() = root.listFiles().orEmpty().filter { it.name !in existingFiles }

    @Test fun deletedOwnerRejectsImportBeforeCopyingAnyFile() = runBlocking {
        val id = repo.createProject("Gone"); repo.deleteWorkspace(id)
        val result = service.ingest(id, Uri.parse("content://local.fixture/file.txt"))
        assertTrue(result.isFailure); assertTrue(newFiles().isEmpty())
    }

    @Test fun deletionDuringStreamingCopyCannotRecreateDocumentOrLeavePartialCopy() = runBlocking {
        val id = repo.createProject("Gone during copy")
        val uri = Uri.parse("content://local.fixture/file.txt")
        val input = object : ByteArrayInputStream("private fixture content".toByteArray()) {
            var deleted = false
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!deleted) { deleted = true; runBlocking { repo.deleteWorkspace(id) } }
                return super.read(buffer, offset, length)
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, input)
        val result = service.ingest(id, uri)
        assertTrue(result.isFailure); assertNull(db.projectDao().get(id))
        assertTrue(db.documentDao().forProject(id).isEmpty()); assertTrue(newFiles().isEmpty())
    }

    @Test fun ordinaryImportStillCopiesAndIndexesTextInPrivateStorage() = runBlocking {
        val id = repo.createProject("Normal")
        val uri = Uri.parse("content://local.fixture/notes.txt")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("local source with useful words".toByteArray()))
        val result = service.ingest(id, uri)
        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        val doc = db.documentDao().get(result.getOrThrow())!!
        assertEquals("READY", doc.indexingStatus); assertEquals("READY", doc.extractionStatus)
        assertTrue(File(doc.localPath).canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertEquals("local source with useful words", File(doc.localPath).readText())
        assertFalse(db.documentDao().segmentsForDocument(doc.id).isEmpty())
        val timings=service.lastDiagnostics.value!!
        assertEquals("READY",timings.status)
        assertEquals("TXT",timings.format)
        assertTrue(timings.sourceBytes!! > 0)
        assertNotNull(timings.copyHashMs);assertNotNull(timings.parseMs);assertNotNull(timings.chunkMs)
        assertNotNull(timings.segmentCommitMs);assertNotNull(timings.totalMs)
        assertFalse(com.google.gson.Gson().toJson(timings).contains("notes.txt"))
    }
}
