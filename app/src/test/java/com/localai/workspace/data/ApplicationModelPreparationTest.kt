package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Actual Room/prefs selection; controlled native boundary, not phone inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationModelPreparationTest {
    private lateinit var db: WorkspaceDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private lateinit var scope: CoroutineScope
    private lateinit var backend: Backend
    private lateinit var pool: ChatRuntimePool
    private class Backend : InferenceRuntime {
        override val runtimeType = RuntimeType.LITERT_LM
        override val runtimeId = "litert-lm-android"
        val loads = CopyOnWriteArrayList<ModelLoadConfig>()
        val gate = CompletableDeferred<Unit>()
        override fun supports(format: ModelFormat) = format == ModelFormat.LITERT_LM
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Unused")
        override suspend fun load(config: ModelLoadConfig) { loads.add(config); gate.await() }
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = error("Prewarming must never generate")
        override fun cancelGeneration() = Unit
        override suspend fun resetConversation() = Unit
        override suspend fun unload() = Unit
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, false, false, false)
        override fun metrics() = RuntimeMetrics(backend = runtimeId)
    }
    @Before fun open() {
        for (n in listOf("app_model_preparation", "litert_cpu_defaults_v016", "litert_chat_defaults_v017"))
            context.getSharedPreferences(n, 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        backend = Backend()
        pool = ChatRuntimePool(scope, idleRetentionMs = null, reportCleanupFailure = { throw it })
    }
    @After fun close() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    private fun manager() = ApplicationModelPreparation(context, scope, WorkspaceRepository(db),
        LiteRtChatDefaultsUpgrade(context, db.modelDao()), InferenceRuntimeRegistry(listOf(backend)), pool)
    private fun model(id: String, date: Long = 1, status: String = "READY") = ModelEntity(id, id,
        "/private/models/$id.litertlm", fileHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray()).joinToString("") { "%02x".format(it) }, fileSize = 4096, format = "LITERT_LM",
        runtimeId = "litert-lm-android", compatibilityStatus = "COMPATIBLE", importedAt = date,
        importStatus = status, configuredContext = 4096, maxOutputTokens = 256, repeatPenalty = 1.1f)
    private fun check(block: suspend () -> Unit) = runBlocking { withTimeout(15_000) { withContext(dispatcher) { block() } } }

    @Test fun appStartupLoadsLastSelectedModelRatherThanNewerLibraryEntry() = check {
        db.modelDao().insert(model("qwen")); db.modelDao().insert(model("gemma", 2))
        context.getSharedPreferences("app_model_preparation", 0).edit().putString("selected_model", "qwen").commit()
        val app = manager(); app.start()
        app.state.first { it.preparing }
        assertFalse(app.state.value.ready)
        backend.gate.complete(Unit); app.state.first { it.ready }
        assertEquals("qwen", app.state.value.modelId); assertEquals(1, backend.loads.size)
        assertEquals(AcceleratorType.CPU, backend.loads.single().preferredAccelerator)
        assertEquals("", backend.loads.single().conversation?.userMessage)
    }

    @Test fun importedPartialFileAndRemovedPreferredModelNeverBecomePreloadTargets() = check {
        db.modelDao().insert(model("partial", 3, "IMPORTING")); db.modelDao().insert(model("usable", 2))
        context.getSharedPreferences("app_model_preparation", 0).edit().putString("selected_model", "removed").commit()
        val app = manager(); backend.gate.complete(Unit); app.start(); app.state.first { it.ready }
        assertEquals("usable", app.selectedModelId()); assertEquals("usable", app.state.value.modelId)
        assertEquals(1, backend.loads.size)
    }

    @Test fun repeatedSelectionAndMetadataUpdatesDoNotReloadSamePreparedConfig() = check {
        val model = model("qwen"); db.modelDao().insert(model)
        val app = manager(); backend.gate.complete(Unit); app.start(); app.state.first { it.ready }
        repeat(3) { app.select("qwen") }
        // Await setup through a settings-relevant emission below; metrics alone must not load.
        db.modelDao().insert(model.copy(lastMetrics = "updated", updatedAt = 2))
        delay(100)
        assertEquals(1, backend.loads.size)
        db.modelDao().insert(model.copy(configuredContext = 2048, updatedAt = 3))
        withTimeout(5000) { while (backend.loads.size < 2) delay(10) }
        app.state.first { it.ready }; assertEquals(2048, backend.loads.last().contextSize)
    }

    @Test fun changingModelPersistsSelectionForNextApplicationInstance() = check {
        db.modelDao().insert(model("qwen")); db.modelDao().insert(model("gemma", 2))
        val app = manager(); backend.gate.complete(Unit); app.start(); app.state.first { it.ready }
        app.select("qwen"); app.state.first { it.ready && it.modelId == "qwen" }
        val restored = manager()
        assertEquals("qwen", restored.selectedModelId())
        assertEquals("qwen", context.getSharedPreferences("app_model_preparation", 0).getString("selected_model", null))
    }

    @Test fun ggufSelectionKeepsExistingFirstSendPathAndDoesNotClaimReady() = check {
        db.modelDao().insert(model("gguf").copy(format = "GGUF", runtimeId = "llama-cpp-jni"))
        val app = manager(); app.start(); app.state.first { it.modelId == "gguf" }
        assertFalse(app.state.value.ready); assertFalse(app.state.value.preparing)
        assertTrue(backend.loads.isEmpty())
    }
}
