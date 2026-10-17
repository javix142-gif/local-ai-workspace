package com.localai.workspace.semantic.v2

import android.app.Application
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import androidx.room.Room
import com.localai.workspace.data.DocumentEntity
import com.localai.workspace.data.DocumentSegmentEntity
import com.localai.workspace.data.ProjectEntity
import com.localai.workspace.data.WorkspaceDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SemanticIndexRestartRecoveryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var workspace: WorkspaceDatabase
    private lateinit var semanticDatabase: SemanticDatabase
    private lateinit var manager: EmbeddingEngineManager
    private lateinit var layer: SemanticLayer
    private lateinit var model: SemanticModelRecord
    private lateinit var modelFile: File
    private lateinit var documentFile: File
    private var nativeComputeCalls = 0

    private class CachedVectorDriver(private val calls: () -> Unit) : EmbeddingNativeDriver {
        private var initialized = false
        override fun initialize() { initialized = true }
        override fun isInitialized() = initialized
        override fun compute(contents: List<InputData>, options: EmbeddingOptions): FloatArray {
            calls()
            return FloatArray(options.outputSize ?: 768) { if (it == 0) 1f else 0f }
        }
        override fun close() { initialized = false }
    }

    @Before fun open() = runBlocking {
        workspace = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        semanticDatabase = Room.inMemoryDatabaseBuilder(context, SemanticDatabase::class.java).build()
        modelFile = File(context.cacheDir, "restart-model-${UUID.randomUUID()}.litertlm").apply { writeText("LITERTLM") }
        model = SemanticModelRecord(
            id = "restart-model-${UUID.randomUUID()}", displayName = "Synthetic EG2", family = "EMBEDDING_GEMMA_2",
            fileName = modelFile.name, privatePath = modelFile.path, fileSize = modelFile.length(), sha256 = "b".repeat(64),
            format = "LITERTLM", provider = "litert-lm", nativeDimension = 768, supportedDimensions = "768,256",
            declaredModalities = "TEXT", verifiedModalities = "TEXT", runtime = "LiteRT-LM 0.17.1",
            importedAt = 1, validationStatus = "TEXT_PROBE_PASSED"
        )
        val gate = Mutex()
        manager = EmbeddingEngineManager(context, gate) { CachedVectorDriver { nativeComputeCalls++ } }
        layer = SemanticLayer(context, workspace, gate, semanticDatabase, "restart-${UUID.randomUUID()}", manager)
        semanticDatabase.dao().model(model)
        layer.activate(model.id, EmbeddingRuntimeProfile())

        val projectId = "restart-project-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        workspace.projectDao().upsert(ProjectEntity(projectId, "Synthetic project", now, now))
        val text = "A durable vector checkpoint survives an interrupted local index job."
        documentFile = File(context.cacheDir, "restart-document-${UUID.randomUUID()}.txt").apply { writeText(text) }
        val document = DocumentEntity(
            id = "restart-document-${UUID.randomUUID()}", projectId = projectId, displayName = "checkpoint.txt",
            localPath = documentFile.path, mimeType = "text/plain", fileHash = fingerprint(text),
            byteSize = documentFile.length(), extractionStatus = "READY", indexingStatus = "READY",
            importedAt = now, updatedAt = now
        )
        workspace.documentDao().insert(document)
        workspace.documentDao().insertSegments(listOf(DocumentSegmentEntity(
            documentId = document.id, segmentIndex = 0, text = text, normalizedText = text.lowercase(),
            contentHash = fingerprint(text)
        )))
        val legacySegment = workspace.documentDao().segmentsForDocument(document.id).single()

        val scope = SemanticScope(ScopeType.PROJECT, projectId)
        val interruptedId = "interrupted-${UUID.randomUUID()}"
        val sourceId = "$interruptedId:${document.id}"
        val task = EmbeddingTask.DOCUMENT
        val contentHash = fingerprint(TaskPromptProfile.format(task, text, document.displayName))
        val space = EmbeddingEngineManager.space(model, 768)
        semanticDatabase.dao().job(SemanticIndexJob(
            id = interruptedId, scopeKey = scope.key, spaceKey = space.id, state = "INDEXING",
            sources = 1, segments = 1, embeddings = 1, startedAt = now, updatedAt = now, errorCode = null
        ))
        semanticDatabase.dao().source(SemanticSourceRecord(
            id = sourceId, scopeKey = scope.key, sourceType = "DOCUMENT", displayName = document.displayName,
            mimeType = document.mimeType, reference = document.localPath, size = document.byteSize,
            modifiedAt = document.updatedAt, contentHash = document.fileHash
        ))
        val vector = SemanticVectors.encode(FloatArray(768) { if (it == 0) 1f else 0f })
        semanticDatabase.dao().segment(SemanticSegmentRecord(
            id = "$sourceId:${legacySegment.id}", sourceId = sourceId, modality = "TEXT", textContent = text,
            title = document.displayName, page = null, lineStart = null, lineEnd = null, startMs = null, endMs = null,
            contentHash = contentHash, legacySegmentId = legacySegment.id
        ))
        semanticDatabase.dao().embedding(SemanticEmbeddingRecord(
            indexId = interruptedId, segmentId = "$sourceId:${legacySegment.id}", embeddingSpaceKey = space.id,
            provider = model.provider, modelId = model.id, modelHash = model.sha256, dimension = 768, normalized = true,
            taskType = task.name, contentHash = contentHash, vectorFormat = SemanticVectors.FORMAT,
            vector = vector, createdAt = now
        ))
        this@SemanticIndexRestartRecoveryTest.projectId = projectId
        this@SemanticIndexRestartRecoveryTest.interruptedJobId = interruptedId
    }

    private lateinit var projectId: String
    private lateinit var interruptedJobId: String

    @After fun close() = runBlocking {
        runCatching { manager.unload() }
        workspace.close()
        semanticDatabase.close()
        modelFile.delete()
        documentFile.delete()
        Unit
    }

    @Test fun restartRecoveryMarksInterruptedJobAndReusesItsStoredVector() = runBlocking {
        val scope = SemanticScope(ScopeType.PROJECT, projectId)
        val existingIndexId = "ready-${UUID.randomUUID()}"
        val existingSpace = EmbeddingEngineManager.space(model, 768)
        semanticDatabase.dao().job(SemanticIndexJob(
            id = existingIndexId, scopeKey = scope.key, spaceKey = existingSpace.id,
            state = "READY", sources = 1, segments = 0, embeddings = 0,
            startedAt = 1, updatedAt = 1, errorCode = null
        ))
        semanticDatabase.dao().activate(SemanticActiveIndex(
            scopeKey = scope.key, indexId = existingIndexId, spaceKey = existingSpace.id
        ))
        layer.recover()

        val recovered = requireNotNull(semanticDatabase.dao().job(interruptedJobId))
        assertEquals("CANCELLED", semanticDatabase.dao().job(interruptedJobId)!!.state)
        assertEquals("PROCESS_RESTART_RECOVERABLE", semanticDatabase.dao().job(interruptedJobId)!!.errorCode)
        assertTrue(recovered.updatedAt >= recovered.startedAt)
        assertEquals("READY", semanticDatabase.dao().job(existingIndexId)!!.state)
        assertEquals(existingIndexId, semanticDatabase.dao().active(scope.key)?.indexId)
        val callsBeforeResume = nativeComputeCalls
        val resumedGeneration = layer.indexProject(projectId)

        assertTrue(resumedGeneration != interruptedJobId)
        assertEquals("READY", semanticDatabase.dao().job(resumedGeneration)!!.state)
        assertEquals(resumedGeneration, semanticDatabase.dao().active(scope.key)?.indexId)
        assertEquals(1, semanticDatabase.dao().count(resumedGeneration))
        assertEquals(1, semanticDatabase.dao().payloadCount())
        assertEquals("The local checkpoint segment is reused without another embedding call.", callsBeforeResume, nativeComputeCalls)
        assertEquals("The interrupted attempt remains inspectable.", "CANCELLED", recovered.state)
    }
}
