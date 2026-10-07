package com.localai.workspace.performance

import android.app.Application
import androidx.room.Room
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real graph/gate/pool/Room/persistence, fake native boundary. No hardware claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class PerformanceDiagnosticsIntegrationTest {
    private class Backend : InferenceRuntime {
        override val runtimeType = RuntimeType.LITERT_LM
        override val runtimeId = "litert-lm-android"
        val loads = mutableListOf<ModelLoadConfig>()
        var initialized = false
        var current: ModelLoadConfig? = null
        var completed = emptyList<ChatMessage>()
        var measured = RuntimeMetrics()
        override fun supports(format: ModelFormat) = format == ModelFormat.LITERT_LM
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Not used")
        override suspend fun load(config: ModelLoadConfig) {
            val reused = initialized
            val session = current?.conversation?.conversationId == config.conversation?.conversationId &&
                completed.isNotEmpty() && completed == config.conversation?.history
            initialized = true; loads += config; current = config
            measured = RuntimeMetrics(engineReused = reused, sessionReused = session, backendRequested = "CPU",
                backendEffective = "CPU", speculativeEnabled = false, speculativeActive = false,
                conversationRebuildReason = if (session) "REUSED" else "SESSION_LOST")
        }
        override fun generate(request: GenerationRequest) = flow {
            val answer = when {
                "capital" in request.prompt -> "Tokio"
                "lápices" in request.prompt -> "7"
                "Suma" in request.prompt -> "20"
                "número" in request.prompt -> "17"
                else -> "Hola"
            }
            emit(GenerationEvent.Token(answer))
            completed = request.conversation!!.history + listOf(ChatMessage(MessageRole.USER, request.conversation.userMessage), ChatMessage(MessageRole.ASSISTANT, answer))
            emit(GenerationEvent.Metrics(measured)); emit(GenerationEvent.Completed)
        }
        override fun cancelGeneration() = Unit
        override suspend fun unload() { initialized = false; resetConversation() }
        override suspend fun resetConversation() { current = null; completed = emptyList() }
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, false, false, false)
        override fun metrics() = measured
    }
    @Test fun fullSuiteIsIsolatedReproducibleAndContinuationUsesSameOwner() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backend = Backend()
        val model = ModelEntity("benchmark-model", "Gemma", "/private/models/gemma.litertlm", fileHash = "f".repeat(64),
            fileSize = 99, format = "LITERT_LM", runtimeId = backend.runtimeId, compatibilityStatus = "COMPATIBLE",
            configuredContext = 4096, maxOutputTokens = 256, temperature = .3f, topP = .95f, topK = 40,
            repeatPenalty = 1.1f, seed = 0, importedAt = 1)
        try {
            db.modelDao().insert(model)
            db.projectDao().upsert(ProjectEntity("private-project", "Private", 1, 1))
            db.conversationDao().upsert(ConversationEntity("private-chat", "private-project", "Private", 1, 1))
            val privateTurn = MessageEntity("private-user", "private-chat", "USER", "Private visible text", 1,
                effectiveContent = "Private evidence and memory", effectiveModelId = model.id)
            db.messageDao().insert(privateTurn)
            val graph = AppGraph(context, listOf(backend), db, scope, scope)
            // Start with someone else's loaded conversation; COLD must still unload it.
            graph.chatRuntimes.preloader(backend).load(ModelLoadConfig(ModelSource(model.localPath, "Gemma"), 4096))
            graph.performance.run(model.id, 2)
            withTimeout(15_000) { graph.performance.running.first { !it } }
            val records = graph.performance.records.value.filter { it.modelId == model.id }
            assertEquals(22, records.size)
            assertTrue(records.all { it.result == BenchmarkResult.SUCCESS })
            assertTrue(records.filter { it.mode == BenchmarkMode.COLD }.all { it.metrics.engineReused == false })
            assertTrue(records.filter { it.mode == BenchmarkMode.WARM }.all { it.metrics.engineReused == true })
            assertTrue(records.filter { it.mode == BenchmarkMode.CONTINUATION }.all { it.metrics.sessionReused == true })
            assertTrue(records.all { it.configuration.temperature == .3f && it.configuration.repetitionPenalty == 1.1f })
            assertTrue(records.all { it.metrics.timeToFirstTokenMs == null }) // fake native boundary exposes no TTFT
            assertEquals(privateTurn, db.messageDao().recent("private-chat", 10).single())
            assertFalse(graph.performance.report().contains("Private evidence and memory"))
            assertFalse(graph.performance.report().contains("Private visible text"))
        } finally { scope.cancel(); db.close(); java.io.File(context.filesDir, "diagnostics").deleteRecursively() }
    }
    @Test fun effectiveTurnsSurvivePrivateDatabaseReopenAndDeletionRemovesThem() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "canonical-turns-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
        try {
            db.projectDao().upsert(ProjectEntity("p", "p", 1, 1))
            db.conversationDao().upsert(ConversationEntity("c", "p", "c", 1, 1))
            db.messageDao().insert(MessageEntity("u", "c", "USER", "visible", 1))
            db.messageDao().insert(MessageEntity("a", "c", "ASSISTANT", "answer", 2))
            db.messageDao().setEffectiveTurn("u", "visible + private context", "gemma")
            db.close(); db = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).build()
            assertEquals("visible + private context", ChatHistoryBuilder.completedTurns(db.messageDao().recent("c", 10), modelId = "gemma").first().content)
            WorkspaceRepository(db).deleteWorkspace("p")
            assertTrue(db.messageDao().recent("c", 10).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
