package com.localai.workspace.rag

import android.app.Application
import androidx.room.Room
import com.localai.workspace.data.*
import com.localai.workspace.domain.rag.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class DocumentRetrievalTest {
    private lateinit var db: WorkspaceDatabase
    private lateinit var retrieval: LocalRetrievalService
    private var embeddings = 0
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WorkspaceDatabase::class.java).build()
        retrieval = LocalRetrievalService(db.documentDao(), object : EmbeddingRuntime {
            override val dimensions = 256
            override fun embed(text: String): FloatArray { embeddings++; return HashEmbeddingRuntime().embed(text) }
        })
    }
    @After fun close() = db.close()
    private suspend fun source(id: String, project: String, text: String, ready: Boolean = true) {
        db.documentDao().insert(DocumentEntity(id, project, "$id.md", localPath = "/private/$id", mimeType = "text/markdown",
            fileHash = id, byteSize = text.length.toLong(), extractionStatus = if (ready) "READY" else "FAILED",
            indexingStatus = if (ready) "READY" else "FAILED", importedAt = 1, updatedAt = 1))
        db.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId = id, segmentIndex = 0,
            charStart = 0, charEnd = text.length, text = text, normalizedText = text.lowercase(), contentHash = id)))
    }

    @Test fun greetingDoesNotScanOrEmbedUnrelatedDocuments() = runBlocking {
        source("guide", "p", "An unrelated detailed guide with many pages")
        assertTrue(retrieval.retrieve("p", "¡Hola!").isEmpty()); assertEquals(0, embeddings)
    }
    @Test fun zeroSimilarityAndCommonQuestionWordsDoNotBecomeEvidence() = runBlocking {
        source("sports", "p", "Vanguard escopeta rafagas videojuego")
        assertTrue(retrieval.retrieve("p", "Que sabes sobre astronomia?").isEmpty())
        assertEquals(0, embeddings)
    }
    @Test fun selectedFileProducesRealBoundedSourceEvenForGenericQuestion() = runBlocking {
        source("official", "p", "MARCADOR_VERIFICABLE: la fuente oficial es el documento A.")
        val result = retrieval.retrieve("p", "¿Qué contiene?", documentIds = setOf("official"))
        assertEquals(1, result.size); assertEquals("official", result.single().documentId)
        assertTrue(result.single().excerpt.contains("MARCADOR_VERIFICABLE"))
        assertTrue(result.single().id.startsWith("LCL-"))
    }
    @Test fun requestingDocumentsUsesReadableIndexedContentWithoutExplicitSelection() = runBlocking {
        source("notes", "p", "Texto real del informe")
        assertEquals("notes", retrieval.retrieve("p", "Resume el archivo").single().documentId)
    }
    @Test fun failedOrOtherProjectSourcesCannotLeakViaSelectionOrLexicalQuery() = runBlocking {
        source("failed", "p", "secreto astronomia", false); source("other", "q", "secreto astronomia")
        assertTrue(retrieval.retrieve("p", "astronomia").isEmpty())
        assertTrue(retrieval.retrieve("p", "resume archivo", documentIds = setOf("failed", "other")).isEmpty())
    }
    @Test fun genuineTermMatchStillProducesCitationEvidence() = runBlocking {
        source("health", "p", "Derechos del paciente y consentimiento informado")
        assertEquals("health", retrieval.retrieve("p", "consentimiento informado").single().documentId)
    }
    @Test fun boundedExcerptsHaveActualSourceRangeAndMultipleFilesGetRepresentation() = runBlocking {
        source("long", "p", "x".repeat(6000)); source("short", "p", "Archivo dos")
        val results = retrieval.retrieve("p", "Resume los archivos", documentIds = setOf("long", "short"))
        assertEquals(setOf("long", "short"), results.map { it.documentId }.toSet())
        val long = results.first { it.documentId == "long" }
        assertEquals(1000, long.excerpt.length); assertEquals(1000, long.charEnd)
    }
}
