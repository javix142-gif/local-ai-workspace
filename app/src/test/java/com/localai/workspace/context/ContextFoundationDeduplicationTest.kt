package com.localai.workspace.context

import android.app.Application
import androidx.room.Room
import com.localai.workspace.AppGraph
import com.localai.workspace.data.DocumentEntity
import com.localai.workspace.data.ProjectEntity
import com.localai.workspace.data.WorkspaceDatabase
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticDatabase
import com.localai.workspace.semantic.v2.SemanticLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContextFoundationDeduplicationTest {
    @Test
    fun productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val validationId = "m01-05-${UUID.randomUUID()}"
        val workspace = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        val semanticDb = Room.inMemoryDatabaseBuilder(context, SemanticDatabase::class.java).build()
        val graph = AppGraph(
            context,
            databaseOverride = workspace,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            validationId = validationId,
        )
        val semantic = SemanticLayer(context, workspace, graph.inferenceGate, databaseOverride = semanticDb,
            preferencesName = "m01-05-semantic-$validationId")
        val foundation = ContextFoundation(graph) { semantic }
        val now = System.currentTimeMillis()
        val sharedPassage = "Alpha revenue evidence is present in two independent documents."
        val evidenceA = Evidence("E-A", 101, "doc-a", "Report A", sharedPassage, null, null, null, null, retrievalScore = 0.95)
        val evidenceB = Evidence("E-B", 202, "doc-b", "Report B", sharedPassage, null, null, null, null, retrievalScore = 0.90)
        try {
            workspace.projectDao().upsert(ProjectEntity("project-a", "Project A", now, now))
            workspace.documentDao().insert(DocumentEntity("doc-a", "project-a", "Report A", localPath = "unused-test-path-a", mimeType = "text/plain", fileHash = "hash-a", byteSize = 1, extractionStatus = "READY", indexingStatus = "READY", importedAt = now, updatedAt = now))
            workspace.documentDao().insert(DocumentEntity("doc-b", "project-a", "Report B", localPath = "unused-test-path-b", mimeType = "text/plain", fileHash = "hash-b", byteSize = 1, extractionStatus = "READY", indexingStatus = "READY", importedAt = now, updatedAt = now))

            val request = ContextRequest(
                query = "alpha",
                access = ScopeAccess(projectId = "project-a"),
                contextWindow = 4096,
                reservedOutput = 256,
                includeConversation = false,
                sourceRetrievalMode = SourceRetrievalMode.LEGACY_ONLY,
                selectedDocumentIds = setOf("doc-a", "doc-b"),
            )
            val bundle = foundation.build(request, listOf(evidenceA, evidenceB, evidenceA), memoryEnabled = false)

            assertEquals(setOf("E-A", "E-B"), bundle.included.filter { it.kind == ContextKind.SOURCE }.map { it.id }.toSet())
            assertEquals(setOf("doc-a", "doc-b"), bundle.sourceEvidence.map { it.documentId }.toSet())
            assertEquals(2, bundle.sourceEvidence.size)
            val prompt = bundle.conversation().userMessage
            assertTrue(prompt.contains("id=\"E-A\""))
            assertTrue(prompt.contains("id=\"E-B\""))
            assertEquals(2, prompt.split("<context-data ").size - 1)
            assertTrue(bundle.dropped.any { it.reason == "DUPLICATE_CONTENT" })
            assertFalse(bundle.requiresUserNotice)
            assertTrue(bundle.estimatedInputTokens <= bundle.inputBudget)

            val selectedOnlyA = foundation.build(request.copy(selectedDocumentIds = setOf("doc-a")), listOf(evidenceA, evidenceB), memoryEnabled = false)
            assertEquals(setOf("doc-a"), selectedOnlyA.sourceEvidence.map { it.documentId }.toSet())
            assertFalse(selectedOnlyA.conversation().userMessage.contains("id=\"E-B\""))
        } finally {
            foundation.database.close()
            semanticDb.close()
            workspace.close()
            context.deleteDatabase("context-memory-$validationId.db")
        }
    }
}
