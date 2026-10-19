package com.localai.workspace.ui

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.domain.inference.*
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real app controller, Room, SAF extraction and prompt assembly; controlled native boundary.
 * This does not validate Android device inference or performance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionPipelineTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val main = Dispatchers.Default.limitedParallelism(1)
    private lateinit var db: WorkspaceDatabase
    private lateinit var appScope: CoroutineScope
    private lateinit var graph: AppGraph
    private lateinit var backend: Backend
    private val controllers = mutableListOf<ChatViewModel>()
    private data class Turn(val request: GenerationRequest, val finish: CompletableDeferred<Unit> = CompletableDeferred())
    private class Backend : InferenceRuntime {
        override val runtimeType = RuntimeType.LITERT_LM
        override val runtimeId = "litert-lm-android"
        val loads = CopyOnWriteArrayList<ModelLoadConfig>()
        val turns = Channel<Turn>(Channel.UNLIMITED)
        var active: Turn? = null
        var cancellations = 0
        var vision = false
        var audio = false
        var failLoad = false
        override fun supports(format: ModelFormat) = format == ModelFormat.LITERT_LM
        override fun availableAccelerators() = setOf(AcceleratorType.CPU)
        override suspend fun inspectModel(source: ModelSource): ModelMetadata = error("Not used")
        override suspend fun load(config: ModelLoadConfig) { loads.add(config); if (failLoad && (config.enableVision || config.enableAudio)) error("Vision preparation failed") }
        override fun generate(request: GenerationRequest) = flow {
            val turn = Turn(request); active = turn
            try {
                emit(GenerationEvent.Token("partial")); turns.send(turn)
                turn.finish.await()
                emit(GenerationEvent.Token(" final")); emit(GenerationEvent.Completed)
            } finally { if (active === turn) active = null }
        }
        override fun cancelGeneration() { cancellations++; active?.finish?.cancel() }
        override suspend fun unload() = Unit
        override suspend fun resetConversation() = Unit
        override fun capabilities() = RuntimeCapabilities(runtimeId, true, false, vision, false, false, audio = audio)
        override fun metrics() = RuntimeMetrics(backend = runtimeId, modelRetainedAfterStop = true)
    }
    @Before fun open() {
        Dispatchers.setMain(main)
        for (n in listOf("app_model_preparation", "litert_cpu_defaults_v016", "litert_chat_defaults_v017"))
            context.getSharedPreferences(n, 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        appScope = CoroutineScope(SupervisorJob() + main)
        backend = Backend()
        graph = AppGraph(context, listOf(backend), db, appScope, appScope)
    }
    @After fun close() = runBlocking {
        withContext(main) { graph.chatSessions.clear(); graph.modelPreparation.cancel() }
        controllers.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        appScope.coroutineContext[Job]!!.cancelAndJoin()
        Dispatchers.resetMain(); db.close()
    }
    private fun check(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { withContext(main) { block() } } }
    private fun model(id: String) = ModelEntity(id, id, "/private/$id.litertlm", fileHash =
        java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) },
        fileSize = 4096, format = "LITERT_LM", runtimeId = backend.runtimeId, compatibilityStatus = "COMPATIBLE",
        configuredContext = 4096, maxOutputTokens = 256, repeatPenalty = 1.1f, importedAt = 1)
    private suspend fun chat(skillAuthoring: Boolean = false): Pair<String, ChatViewModel> {
        if (graph.workspace.getModel("model") == null) db.modelDao().insert(model("model"))
        val id = graph.workspace.createChat()
        val vm = graph.chatSessions.get(id, null, skillAuthoring); controllers += vm; vm.enterScreen()
        vm.currentConversationId.first { it != null }
        graph.modelPreparation.state.first { it.ready }
        return id to vm
    }

    @Test fun skillAuthoringUsesSharedLocalChatButSkipsAgentMemoryAndToolExecution() = check {
        graph.contextFoundation.enable(true)
        db.modelDao().insert(model("model").copy(capabilities = "text-generation,tool-calling"))
        val (projectId, vm) = chat(skillAuthoring = true)
        val ordinaryChat = graph.chatSessions.get(projectId, null)
        controllers += ordinaryChat
        assertTrue(vm.skillAuthoringMode)
        assertFalse(ordinaryChat.skillAuthoringMode)
        assertNotSame("authoring and ordinary chats must not share mode-specific prompt state", vm, ordinaryChat)
        val requestText = "Create a Skill for reviewing a synthetic maintenance checklist"
        assertTrue(vm.send(requestText))
        val turn = backend.turns.receive()

        assertEquals(requestText, turn.request.conversation?.userMessage)
        assertTrue(turn.request.conversation?.systemInstruction.orEmpty().contains("SKILL.md"))
        assertTrue(turn.request.conversation?.systemInstruction.orEmpty().contains("¿Qué debiese hacer la Skill?"))
        assertTrue(turn.request.prompt.contains("SKILL.md"))
        assertTrue("authoring mode must not inherit model tool grants", backend.loads.last().enabledTools.isEmpty())
        assertFalse("authoring does not add unrelated memory to the request", turn.request.conversation?.userMessage.orEmpty().contains("MEMORY_FIXTURE"))
        val trace = requireNotNull(graph.normalGenerationTrace.state.value)
        assertFalse(trace.contextBuilderEnabled)
        assertEquals(0, trace.toolsEffectiveCount)

        finish(vm, turn)
        assertEquals(requestText, graph.workspace.recentMessages(vm.currentConversationId.value!!, 10)
            .first { it.role == "USER" }.content)
    }
    private suspend fun finish(vm: ChatViewModel, turn: Turn) {
        turn.finish.complete(Unit); vm.isGenerating.first { !it }
    }
    private fun input(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://test/$name")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    private suspend fun visionChat(): ChatViewModel {
        backend.vision = true
        db.modelDao().insert(model("model").copy(capabilities = "text-generation,vision"))
        val (_, vm) = chat()
        vm.models.first { it.isNotEmpty() }
        return vm
    }
    private suspend fun attach(vm: ChatViewModel): String {
        val file = java.io.File(context.cacheDir, "hotfix-source.png")
        val bitmap = android.graphics.Bitmap.createBitmap(32, 24, android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        vm.attachImage(Uri.fromFile(file))
        return vm.attachedImagePath.first { it != null }!!
    }

    @Test fun materialContextOmissionWarnsWhileRoutineOmissionStaysDiagnosticOnly() = check {
        context.getSharedPreferences("context_foundation_v1", 0).edit().putBoolean("enabled", true).commit()
        val (_, vm) = chat()
        val foundation = graph.contextFoundation
        val materialQuery = "Summarize M00NOTICE retention marker guidance"
        val materialText = "M00NOTICE retention marker synthetic guidance ".repeat(340)
        assertTrue("Synthetic memory must fit the canonical storage limit", materialText.length <= 16_000)
        val material = foundation.memory.create(
            materialText,
            com.localai.workspace.semantic.v2.SemanticScope(com.localai.workspace.semantic.v2.ScopeType.USER, "local"),
        )
        val observedNotices = CopyOnWriteArrayList<String>()
        val collector = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            vm.notices.collect { observedNotices += it }
        }
        var routineId: String? = null
        try {
            vm.send(materialQuery)
            val materialTurn = backend.turns.receive()
            val materialBundle = requireNotNull(foundation.last.value)
            val materialDrop = materialBundle.dropped.single { it.item.provenance.sourceId == material.id }
            assertEquals(com.localai.workspace.context.ContextKind.MEMORY, materialDrop.item.kind)
            assertEquals("TOKEN_BUDGET", materialDrop.reason)
            assertTrue(materialBundle.requiresUserNotice)

            val report = materialBundle.safeReport()
            val reportedDrop = (report["dropped"] as List<*>).filterIsInstance<Map<*, *>>()
                .single { it["id"] == materialDrop.item.id }
            assertEquals("TOKEN_BUDGET", reportedDrop["reason"])
            assertFalse("Inspector metadata must not expose memory text", report.toString().contains("M00NOTICE"))
            assertTrue(
                "Expected a clear notice explaining that relevant memory was omitted: $observedNotices",
                observedNotices.any {
                    it.contains("Relevant memory did not fit this turn") &&
                        it.contains("answer may omit those details") &&
                        it.contains("Saved data remains available")
                },
            )
            finish(vm, materialTurn)

            val omissionNoticeCount = observedNotices.count { it.contains("did not fit this turn") }
            val routine = foundation.memory.create(
                "Orbital mechanics describes trajectories around a distant star.",
                com.localai.workspace.semantic.v2.SemanticScope(com.localai.workspace.semantic.v2.ScopeType.USER, "local"),
            )
            routineId = routine.id
            vm.send("Which database engine stores the project records?")
            val routineTurn = backend.turns.receive()
            val routineBundle = requireNotNull(foundation.last.value)
            assertTrue(routineBundle.dropped.any { it.item.provenance.sourceId == routine.id && it.reason == "BELOW_RELEVANCE_THRESHOLD" })
            assertFalse(routineBundle.requiresUserNotice)
            finish(vm, routineTurn)
            assertEquals(omissionNoticeCount, observedNotices.count { it.contains("did not fit this turn") })
        } finally {
            collector.cancelAndJoin()
            foundation.memory.delete(material.id)
            routineId?.let { foundation.memory.delete(it) }
        }
    }

    @Test fun normalChatTraceOrdersContextBeforeGateAndVisibleContentWithoutPersistingText() = check {
        context.getSharedPreferences("context_foundation_v1", 0).edit().putBoolean("enabled", true).commit()
        val (_, vm) = chat()
        val privatePrompt = "TRACE_PRIVATE_PROMPT_7D3A"
        vm.send(privatePrompt)
        val turn = backend.turns.receive()

        val firstContent = requireNotNull(graph.normalGenerationTrace.state.value)
        val phases = firstContent.events.map { it.phase }
        fun phase(name: String) = phases.indexOf(name).also {
            assertTrue("Expected $name in trace: $phases", it >= 0)
        }
        assertTrue("Context retrieval and composition finish before the generation gate",
            phase("CONTEXT_V1_BUILD_COMPLETE") < phase("CONTEXT_BUILD_COMPLETE") &&
                phase("CONTEXT_BUILD_COMPLETE") < phase("CONVERSATION_ACQUIRE_START") &&
                phase("CONVERSATION_ACQUIRE_START") < phase("CONVERSATION_GATE_ACQUIRED"))
        assertTrue("The runtime starts only after the gate is acquired",
            phase("CONVERSATION_GATE_ACQUIRED") < phase("GENERATION_START") &&
                phase("GENERATION_START") < phase("UI_FIRST_CONTENT"))
        assertNotNull("The first visible host state is timed", firstContent.firstVisibleUiMs)
        assertNull("A fake runtime must not fabricate a native callback metric", firstContent.firstCallbackMs)
        val diagnosticJson = graph.normalGenerationTrace.report()
        val traceJson = JSONObject(diagnosticJson)
        assertTrue("A normal-chat trace must include metadata about context composition", traceJson.has("contextComposition"))
        val contextComposition = traceJson.getJSONObject("contextComposition")
        assertEquals(com.localai.workspace.context.ContextTokenEstimator.METHOD, contextComposition.getString("estimator"))
        assertTrue(contextComposition.getInt("estimatedUnits") <= contextComposition.getInt("availableUnits"))
        fun totalCounts(key: String): Int {
            val counts = contextComposition.getJSONObject(key)
            var total = 0
            val names = counts.keys()
            while (names.hasNext()) total += counts.getInt(names.next())
            return total
        }
        assertEquals(contextComposition.getInt("includedItemCount"), totalCounts("includedByKind"))
        assertEquals(contextComposition.getInt("omittedItemCount"), totalCounts("omittedByReason"))
        assertFalse(diagnosticJson.contains(privatePrompt))
        assertFalse(diagnosticJson.contains("partial final"))
        assertFalse(diagnosticJson.contains("/private/"))

        finish(vm, turn)
        val completed = requireNotNull(graph.normalGenerationTrace.state.value)
        assertTrue(completed.events.any { it.phase == "REQUEST_COMPLETE" })
        assertTrue(completed.events.any { it.phase == "REQUEST_SETTLED" })
        listOf("MODEL_PREPARATION_WAIT", "MODEL_READY", "MEMORY_COMPLETE", "CONTEXT_V1_BUILD_COMPLETE")
            .forEach { expected -> assertTrue("Expected $expected in trace", completed.events.any { it.phase == expected }) }
        val rows = com.localai.workspace.performance.GenerationTimelinePresentation.rows(completed)
        assertEquals(completed.events.size, rows.size)
        assertTrue(rows.zipWithNext().all { (a, b) ->
            a.elapsedSinceRequestMs != null && b.elapsedSinceRequestMs != null &&
                b.elapsedSinceRequestMs >= a.elapsedSinceRequestMs
        })

        val conversationId = requireNotNull(vm.currentConversationId.value)
        val assistant = graph.database.messageDao().recent(conversationId, 10)
            .single { it.role == MessageRole.ASSISTANT.name && it.status == MessageStatus.COMPLETE.name }
        val metrics = requireNotNull(GenerationMetricsPresentation.decode(assistant.generationMetrics))
        assertEquals("Final metrics are linked to the trace that produced this request", completed.runId, metrics.normalTraceId)
        assertNotNull(com.localai.workspace.performance.GenerationTimelinePresentation.correlatedMetrics(
            completed, graph.performance.lastGeneration.value?.second))
        assertFalse("Persisted metric metadata excludes the prompt", metrics.raw.contains(privatePrompt))
        fun persistedTiming(key: String): Long? =
            Regex("(?:^|,)${key}=(\\d+)(?:,|$)").find(metrics.raw)?.groupValues?.get(1)?.toLongOrNull()
        val requestTtftMs = persistedTiming("requestTtftMs")
        val requestTotalMs = persistedTiming("requestTotalMs")
        assertNotNull("Send-to-first-visible-token timing is retained", requestTtftMs)
        assertNotNull("End-to-end completion timing is retained", requestTotalMs)
        assertTrue(requestTotalMs!! >= requestTtftMs!!)
        listOf("queueMs", "contextMs", "memoryRetrievalMs", "sourceRetrievalMs", "conversationRetrievalMs")
            .forEach { key -> assertNotNull("Host stage timing $key is retained", persistedTiming(key)) }
        assertNull("Fake backend provides no native TTFT", persistedTiming("ttftMs"))
        assertNull("Fake backend provides no native decode rate", persistedTiming("decodeTps"))
    }

    @Test fun audioMovesToStoredTurnAndNextRequestNeverReattachesIt() = check {
        backend.audio = true; db.modelDao().insert(model("model").copy(capabilities = "text-generation,audio"))
        val (_, vm) = chat(); vm.models.first { it.isNotEmpty() }
        vm.attachAudio(input("source.wav", com.localai.workspace.audio.WavInput.header(16000, 1, 32000) + ByteArray(32000)))
        val path = vm.attachedAudioPath.first { it != null }!!
        vm.send("Transcribe"); val first = backend.turns.receive()
        assertEquals(path, first.request.audioPath); assertNull(vm.attachedAudioPath.value); assertTrue(backend.loads.last().enableAudio)
        finish(vm, first); assertEquals(path, graph.workspace.recentMessages(vm.currentConversationId.value!!, 10).first { it.role == "USER" }.audioPath)
        vm.send("Continue"); val next = backend.turns.receive(); assertNull(next.request.audioPath); assertEquals(path, next.request.conversation!!.history.first().audioPath); finish(vm, next)
        val (_, other) = chat(); other.send("Hello"); val clean = backend.turns.receive(); assertNull(clean.request.audioPath); assertTrue(clean.request.conversation!!.history.isEmpty()); finish(other, clean)
    }
    @Test fun incompatibleAudioIsRejectedAndRuntimeFailureCannotSilentlySendText() = check {
        val (_, vm) = chat(); vm.models.first { it.isNotEmpty() }
        assertFalse(vm.send("Transcribe", retryAudioPath = "/private/audio.wav")); assertTrue(backend.turns.tryReceive().isFailure)
        backend.audio = true; db.modelDao().insert(model("model").copy(capabilities = "text-generation,audio")); vm.models.first { "audio" in it.single().capabilities }
        vm.attachAudio(input("source.wav", com.localai.workspace.audio.WavInput.header(16000, 1, 32000) + ByteArray(32000)))
        vm.attachedAudioPath.first { it != null }; backend.audio = false
        vm.send("Transcribe"); vm.isGenerating.first { !it }; assertTrue(backend.turns.tryReceive().isFailure)
    }
    @Test fun failedPendingAudioNeverFallsBackToText() = check {
        backend.audio = true; db.modelDao().insert(model("model").copy(capabilities = "text-generation,audio"))
        val (_, vm) = chat(); vm.models.first { it.isNotEmpty() }
        vm.attachAudio(input("bad.wav", "invalid audio".toByteArray())); vm.send("Transcribe")
        vm.isGenerating.first { !it }; assertTrue(backend.turns.tryReceive().isFailure); assertNull(vm.attachedAudioPath.value)
    }
    @Test fun imageMovesToPersistedTurnAndNextRequestHasNoImage() = check {
        val vm = visionChat(); val path = attach(vm)
        vm.send("Describe image")
        val first = backend.turns.receive()
        assertEquals(path, first.request.imagePath)
        assertNull(vm.attachedImagePath.value)
        assertTrue(backend.loads.last().enableVision)
        assertTrue(backend.loads.last().nextHasImage)
        assertTrue(java.io.File(path).isFile)
        finish(vm, first)
        val rows = graph.workspace.recentMessages(vm.currentConversationId.value!!, 10)
        assertEquals(path, rows.first { it.role == "USER" }.imagePath)
        vm.send("What color?")
        val second = backend.turns.receive()
        assertNull(second.request.imagePath)
        assertEquals(path, second.request.conversation!!.history.first().imagePath)
        assertFalse(backend.loads.last().nextHasImage)
        finish(vm, second)
    }
    @Test fun removingDraftDeletesOnlyDraftAndTextRemainsText() = check {
        val vm = visionChat(); val path = attach(vm); vm.clearImage()
        assertNull(vm.attachedImagePath.value); assertFalse(java.io.File(path).exists())
        vm.send("Hello")
        val turn = backend.turns.receive(); assertNull(turn.request.imagePath)
        assertFalse(backend.loads.last().enableVision); finish(vm, turn)
    }
    @Test fun loadedRuntimeRejectingVisionNeverGetsTextFallback() = check {
        val vm = visionChat(); attach(vm); backend.vision = false
        vm.send("Describe image")
        vm.isGenerating.first { !it }
        assertTrue(backend.turns.tryReceive().isFailure)
        assertNull(vm.attachedImagePath.value)
        assertTrue(graph.workspace.recentMessages(vm.currentConversationId.value!!, 10).any { it.status == "FAILED" })
    }
    @Test fun visionLoadFailureNeverCallsGenerate() = check {
        val vm = visionChat(); attach(vm); backend.failLoad = true
        vm.send("Describe image"); vm.isGenerating.first { !it }
        assertTrue(backend.turns.tryReceive().isFailure)
    }
    @Test fun unsupportedModelRejectsImageBeforePreparing() = check {
        val (_, vm) = chat(); vm.models.first { it.isNotEmpty() }
        val uri = Uri.parse("content://test/not-readable")
        val notice = vm.viewModelScope.async(start = CoroutineStart.UNDISPATCHED) { vm.notices.first { it.contains("vision model") } }
        vm.attachImage(uri)
        notice.await(); vm.isImporting.first { !it }
        assertNull(vm.attachedImagePath.value)
        vm.send("Hello"); val turn = backend.turns.receive(); assertNull(turn.request.imagePath); finish(vm, turn)
    }
    @Test fun navigatingAwayKeepsGenerationAndReopeningUsesSameControllerAndPersistedResult() = check {
        val (id, vm) = chat(); vm.send("Hola")
        val turn = backend.turns.receive(); assertEquals("partial", vm.streamingText.value)
        vm.leaveScreen()
        graph.chatSessions.activities.first { it.any { activity -> activity.projectId == id && activity.generating } }
        assertEquals(0, backend.cancellations)
        assertSame(vm, graph.chatSessions.get(id, vm.currentConversationId.value))
        vm.enterScreen(); finish(vm, turn)
        val answer = graph.workspace.recentMessages(vm.currentConversationId.value!!, 10).first { it.role == "ASSISTANT" }
        assertEquals("COMPLETE", answer.status); assertEquals("partial final", answer.content)
        assertEquals(0, backend.cancellations)
    }

    // 0.5.0 removes arbitrary selected-file fallback; fixtures must make a real lexical match.
    @Test fun realSafMarkdownReachesGenerationRequestAndRemainsReusableInLibrary() = check {
        val (id, vm) = chat()
        vm.attachDocument(input("notes.md", "MARCADOR_123: fuentes oficiales verificadas.".toByteArray()))
        val selected = vm.selectedDocumentIds.first { it.isNotEmpty() }.single()
        assertTrue(vm.previewDocument(selected).contains("MARCADOR_123"))
        vm.send("¿Qué indican las fuentes oficiales verificadas?"); val turn = backend.turns.receive()
        assertTrue(turn.request.conversation!!.userMessage.contains("MARCADOR_123"))
        assertTrue(turn.request.conversation!!.userMessage.contains("notes.md"))
        assertTrue(turn.request.conversation!!.systemInstruction!!.contains("data",ignoreCase=true))
        finish(vm, turn); assertTrue(vm.selectedDocumentIds.value.isEmpty())
        assertEquals("READY", db.documentDao().forProject(id).single().indexingStatus)
        assertTrue(java.io.File(db.documentDao().get(selected)!!.localPath).isFile)
    }

    @Test fun emptyDocumentIsFailedRatherThanPretendingToHaveUsableText() = check {
        val (id, vm) = chat(); vm.attachDocument(input("empty.txt", "  \n ".toByteArray()))
        vm.isImporting.first { it }; vm.isImporting.first { !it }
        val source = db.documentDao().forProject(id).single()
        assertEquals("FAILED", source.indexingStatus); assertNotNull(source.errorMessage)
        assertTrue(vm.selectedDocumentIds.value.isEmpty())
        assertTrue(vm.previewDocument(source.id).contains("text", ignoreCase = true))
    }

    @Test fun simpleGreetingDoesNotIncludeLibraryOrDefaultEvidenceInstructions() = check {
        val (id, vm) = chat()
        db.documentDao().insert(DocumentEntity("doc", id, "unrelated.md", localPath = "/private/unrelated.md",
            mimeType = "text/markdown", fileHash = "doc", byteSize = 1, extractionStatus = "READY", indexingStatus = "READY",
            importedAt = 1, updatedAt = 1))
        db.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId = "doc", segmentIndex = 0, text = "Unrelated passage",
            normalizedText = "unrelated passage", contentHash = "doc")))
        vm.send("Hola"); val turn = backend.turns.receive()
        assertEquals("Hola", turn.request.conversation!!.userMessage)
        assertTrue(turn.request.conversation!!.systemInstruction!!.contains("DATA")); assertFalse(turn.request.conversation!!.userMessage.contains("Unrelated passage")); finish(vm, turn)
    }

    @Test fun filesOmittedByTheBoundedTurnStaySelectedRatherThanBeingSilentlyConsumed() = check {
        val (_, vm) = chat()
        repeat(4) { index ->
            vm.attachDocument(input("source$index.md", "UNIQUE_SOURCE_$index referencias comunes".toByteArray()))
            vm.selectedDocumentIds.first { it.size == index + 1 }
        }
        val before = vm.selectedDocumentIds.value
        vm.send("Resume las referencias comunes"); val turn = backend.turns.receive()
        val transmitted = Regex("UNIQUE_SOURCE_[0-9]").findAll(turn.request.conversation!!.userMessage).count()
        assertEquals(3, transmitted); finish(vm, turn)
        assertEquals(1, vm.selectedDocumentIds.value.size)
        assertTrue(before.containsAll(vm.selectedDocumentIds.value))
    }

    @Test fun htmlExtractsVisibleTextAndDoesNotInjectScriptOrMarkupIntoModel() = check {
        val (_, vm) = chat()
        vm.attachDocument(input("notes.html", "<html><script>NEVER_INCLUDE</script><p>VISIBLE_MARKER ocean text</p></html>".toByteArray()))
        vm.selectedDocumentIds.first { it.isNotEmpty() }
        vm.send("Resume ocean"); val turn = backend.turns.receive()
        val actual = turn.request.conversation!!.userMessage
        assertTrue(actual.contains("VISIBLE_MARKER")); assertFalse(actual.contains("NEVER_INCLUDE"))
        assertFalse(actual.contains("<html>")); finish(vm, turn)
    }

    @Test fun unsupportedFileShowsParserFailureAndNeverBecomesSelectedModelContext() = check {
        val (id, vm) = chat(); vm.attachDocument(input("legacy.doc", "unsupported file".toByteArray()))
        vm.isImporting.first { it }; vm.isImporting.first { !it }
        val doc = db.documentDao().forProject(id).single()
        assertEquals("FAILED", doc.extractionStatus); assertTrue(doc.errorMessage!!.contains("parser"))
        assertTrue(vm.selectedDocumentIds.value.isEmpty())
    }

    @Test fun paperclipImageUsesVisionPreparationInsteadOfDocumentParser() = check {
        backend.vision = true
        val (id, vm) = chat()
        db.modelDao().insert(model("model").copy(capabilities = "TEXT,VISION"))
        vm.models.first { list -> list.any { "VISION" in it.capabilities } }
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j2ioAAAAASUVORK5CYII=")
        val uri = Uri.parse("content://test/picture.png")
        shadowOf(context.contentResolver).registerInputStream(uri, object : ByteArrayInputStream(png) {
            override fun close() { reset() } // SAF opens a fresh stream for bounds and decoding.
        })
        vm.attachDocument(uri)
        val image = vm.attachedImagePath.first { it != null }!!
        assertTrue(java.io.File(image).isFile); assertTrue(db.documentDao().forProject(id).isEmpty())
        vm.send("Describe la imagen"); val turn = backend.turns.receive()
        assertEquals(image, turn.request.imagePath); assertTrue(backend.loads.last().enableVision)
        finish(vm, turn)
    }

    @Test fun queuedChatStopNeverCancelsTheOtherBackgroundGeneration() = check {
        val (firstId, first) = chat(); first.send("First")
        val firstTurn = backend.turns.receive(); first.leaveScreen()
        val (_, second) = chat(); second.send("Second")
        second.generationProgress.first { it.detail?.contains("Waiting for the active generation") == true }
        second.stop(); second.isGenerating.first { !it }
        assertTrue(first.isGenerating.value); assertEquals(0, backend.cancellations)
        assertTrue(backend.turns.tryReceive().isFailure)
        graph.chatSessions.stop(firstId, null); first.isGenerating.first { !it }
        assertTrue(firstTurn.finish.isCancelled); assertTrue(backend.cancellations >= 1)
        assertEquals("CANCELED", graph.workspace.recentMessages(second.currentConversationId.value!!, 10)
            .first { it.role == "ASSISTANT" }.status)
    }

    @Test fun changingPreparedModelWaitsForActiveRequestInsteadOfStealingItsSession() = check {
        val (_, vm) = chat(); db.modelDao().insert(model("other"))
        vm.send("First"); val turn = backend.turns.receive()
        val before = backend.loads.size
        graph.modelPreparation.select("other")
        graph.modelPreparation.state.first { it.modelId == "other" && it.preparing }
        assertEquals(before, backend.loads.size); assertTrue(vm.isGenerating.value)
        finish(vm, turn)
        graph.modelPreparation.state.first { it.modelId == "other" && it.ready }
        assertEquals("other", backend.loads.last().model.displayName)
    }

    @Test fun removingWorkspaceSettlesActiveControllerBeforeDeletingPersistedRows() = check {
        val (id, vm) = chat(); vm.send("First"); backend.turns.receive(); vm.leaveScreen()
        graph.chatSessions.removeWorkspace(id)
        assertFalse(vm.isGenerating.value)
        graph.workspace.deleteWorkspace(id)
        assertNull(db.projectDao().get(id))
        assertTrue(graph.chatSessions.activities.value.none { it.projectId == id })
        assertTrue(db.conversationDao().observeForProject(id).first().isEmpty())
    }

    @Test fun readingFileContinuesAfterNavigationAndSendWaitsForItsActualContent() = check {
        val (id, vm) = chat(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val uri = Uri.parse("content://test/slow.md")
        val delegate = ByteArrayInputStream("SLOW_FILE_MARKER: texte extrait localement".toByteArray())
        shadowOf(context.contentResolver).registerInputStream(uri, object : InputStream() {
            var waited = false
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!waited) { waited = true; entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                return delegate.read(b, off, len)
            }
        })
        vm.attachDocument(uri)
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        try {
            vm.leaveScreen(); vm.send("Explica el texte extrait localement")
            assertTrue(backend.turns.tryReceive().isFailure)
        } finally { release.countDown() }
        val turn = backend.turns.receive()
        assertTrue(turn.request.conversation!!.userMessage.contains("SLOW_FILE_MARKER"))
        finish(vm, turn); assertEquals("READY", db.documentDao().forProject(id).single().indexingStatus)
    }
}
