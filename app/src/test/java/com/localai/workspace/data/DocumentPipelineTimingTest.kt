package com.localai.workspace.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.localai.workspace.semantic.EmbeddingProvider
import com.localai.workspace.semantic.NeuralEmbeddings
import com.localai.workspace.semantic.SemanticRetrievalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** A versioned synthetic XLSX exercises import and the linked EG1 project-index timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocumentPipelineTimingTest {
    private val context = RuntimeEnvironment.getApplication()

    private class FixedEmbeddingProvider : EmbeddingProvider {
        override suspend fun <T> use(block: suspend (NeuralEmbeddings) -> T): T? = block(
            object : NeuralEmbeddings {
                override val modelId = "synthetic-timing-embedding"
                override fun embed(text: String) = FloatArray(768).also { it[0] = 1f }
                override fun close() = Unit
            },
        )
    }

    private fun corpusFixture(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (directory != null) {
            val candidate = File(directory, "docs/validation/master-program/02-artifacts-documents/synthetic-corpus/v1-fixture.xlsx")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("Versioned synthetic XLSX fixture not found")
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun xlsxImportAndBackgroundIndexShareOnlyOpaqueTimingRunId() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        val root = File(context.cacheDir, "m00-06-timing-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var indexJob: Job? = null
        try {
            val repository = WorkspaceRepository(database, root)
            val projectId = repository.createProject("Synthetic timing project")
            val retrieval = SemanticRetrievalService(database, FixedEmbeddingProvider())
            val source = corpusFixture().readBytes()
            val fileName = "PRIVATE_DIAGNOSTIC_FILENAME_91C7.xlsx"
            val uri = Uri.parse("content://local.synthetic/$fileName")
            shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(source))
            val service = DocumentIngestionService(
                context = context,
                database = database,
                documents = database.documentDao(),
                onIndexed = { indexedProject, runId ->
                    indexJob = scope.launch { retrieval.indexProject(indexedProject, runId) }
                    true
                },
                outputDirectory = root,
            )

            val documentId = service.ingest(projectId, uri).getOrThrow()
            indexJob?.join()
            val imported = service.lastDiagnostics.value!!
            val indexed = retrieval.indexingDiagnostics.value

            assertEquals("READY", imported.status)
            assertEquals("XLSX", imported.format)
            assertTrue(imported.sourceBytes == source.size.toLong())
            listOf(imported.copyHashMs, imported.metadataCommitMs, imported.parseMs, imported.chunkMs,
                imported.segmentCommitMs, imported.postImportCallbackMs, imported.totalMs).forEach {
                assertNotNull("Every measured import stage must be present", it)
                assertTrue("Elapsed times are monotonic non-negative durations", it!! >= 0)
            }
            assertTrue(imported.pageCount!! > 0)
            assertTrue(imported.segmentCount!! > 0)
            assertTrue("The imported document remains indexed in the authorized project", database.documentDao().get(documentId)?.projectId == projectId)

            val runId = imported.indexRunId
            assertNotNull("A scheduled index must be correlated with the import", runId)
            assertEquals("COMPLETE", indexed.status)
            assertEquals(runId, indexed.runId)
            assertNotNull(indexed.durationMs)
            assertTrue(indexed.durationMs!! >= 0)
            assertTrue(indexed.candidateSegments > 0)
            assertEquals(indexed.candidateSegments, indexed.processedSegments)

            val importJson = com.google.gson.Gson().toJson(imported)
            val indexJson = com.google.gson.Gson().toJson(indexed)
            listOf(fileName, root.canonicalPath, corpusFixture().canonicalPath, sha256(source), projectId)
                .forEach { privateValue ->
                    assertFalse("Import diagnostics must not expose file/project identity or payload metadata", importJson.contains(privateValue))
                    assertFalse("Index diagnostics must not expose file/project identity or payload metadata", indexJson.contains(privateValue))
                }
            assertFalse("No spreadsheet cell content is part of either diagnostic", importJson.contains("Exacto 界"))
            assertFalse("No spreadsheet cell content is part of either diagnostic", indexJson.contains("Exacto 界"))
            assertTrue("Correlation is a random opaque UUID", UUID.fromString(runId!!).toString() == runId)
        } finally {
            scope.cancel()
            database.close()
            root.deleteRecursively()
        }
    }
}
