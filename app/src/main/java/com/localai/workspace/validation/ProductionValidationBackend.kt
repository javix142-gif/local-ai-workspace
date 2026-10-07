package com.localai.workspace.validation

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.gson.JsonObject
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.documents.StructuredDocuments
import com.localai.workspace.domain.model.*
import com.localai.workspace.performance.DeviceMeasurements
import com.localai.workspace.python.PythonAssets
import com.localai.workspace.python.PythonClient
import com.localai.workspace.semantic.*
import com.localai.workspace.ui.ChatViewModel
import com.localai.workspace.ui.ChatViewModelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import kotlin.math.sqrt

/** Real production graph, worker, ViewModel and parsers. Only Room/preferences/files are scoped.
 * No substitute runtime, renderer, tokenizer, embedding or Python implementation exists here. */
internal class ProductionValidationBackend(private val owner: AppGraph, private val runId: String,
    private val model: ModelEntity) : ValidationBackend {
    override val environment = EvidenceEnvironment.DEVICE_TESTED
    private val context = ValidationContext(owner.contextForMeasurements,java.io.File(ValidationResources.root(owner.contextForMeasurements,runId),"cache"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = WorkspaceDatabase.createValidation(context)
    private val graph = AppGraph(context, databaseOverride=db, applicationScope=scope, nativeScope=scope,
        validationOwner=owner, validationId=runId)
    private val fixtures = ValidationFixtures(ValidationResources.root(context,runId))
    private val sessions=mutableListOf<Session>()
    private var initialized=false
    private val documentIds=mutableMapOf<String,String>()
    private var visionSession: Session?=null
    private var audioSession: Session?=null
    private var warmSession: Session?=null
    private var lastMetrics=RuntimeMetrics()
    private var thinkingPartial=JsonObject()
    override fun timeoutOutcome(id: String):ValidationOutcome = if(id=="thinking_budget_256")
        ThinkingBudgetControl.timeout(thinkingPartial)
        else if(id.startsWith("thinking"))
        ValidationOutcome(ValidationStatus.BLOCKED,"THINKING_TIMEOUT",thinkingPartial.deepCopy())
        else super.timeoutOutcome(id)
    private val embeddingStatus=owner.embeddingModels.status.value
    private val projectId="self-test-"+runId
    private data class Session(val vm: ChatViewModel, val store: ViewModelStore, val requests: MutableList<GenerationRequest>, val projectId:String, var subscription: Job?=null)
    private data class Turn(val answer: String, val message: MessageEntity, val metrics: RuntimeMetrics,
        val calls: List<ToolCallEntity>, val request: GenerationRequest?)
    private class Blocked(val code: String,val data:JsonObject=JsonObject()): Exception()
    private fun metrics(vararg values: Pair<String,Any?>)=JsonObject().apply { values.forEach { (key,value)->when(value) {
        null->add(key,com.google.gson.JsonNull.INSTANCE);is Boolean->addProperty(key,value);is Number->addProperty(key,value);else->addProperty(key,value.toString())
    } } }
    private fun checkResult(ok:Boolean, reason:String, data:JsonObject=JsonObject())=ValidationOutcome(if(ok)ValidationStatus.PASS else ValidationStatus.FAIL,if(ok)null else reason,data)
    private fun skipped(reason:String)=ValidationOutcome(ValidationStatus.SKIPPED,reason,critical=false)
    private fun evidence(turn: Turn):JsonObject=ValidationMetricCodec.runtime(turn.metrics).apply {
        addProperty("runtimeAccepted",turn.request!=null);addProperty("toolCallCount",turn.calls.size)
        addProperty("registryExecutions",turn.calls.size)
        turn.request?.let { request->addProperty("maxOutputTokens",request.maxOutputTokens);addProperty("temperature",request.temperature);addProperty("topP",request.topP);addProperty("topK",request.topK);addProperty("repeatPenalty",request.repeatPenalty);addProperty("seed",request.seed) }
        turn.calls.lastOrNull()?.let { addProperty("toolName",it.toolId);addProperty("toolResultStatus",it.status);addProperty("toolDurationMs",it.finishedAt?.minus(it.startedAt)) }
    }
    private suspend fun initialize() {
        if(initialized)return
        check(owner.runtimes.runtimeFor(model.toDescriptor()) is com.localai.workspace.inference.LiteRtLmInferenceRuntime) { "Device validation requires the real LiteRT adapter" }
        owner.inferenceGate.withLock { owner.runtimes.runtimeFor(model.toDescriptor()).resetConversation("SESSION_LOST") }
        withContext(Dispatchers.IO) {
            db.modelDao().insert(model.copy(lastMetrics=null,lastTestedAt=null))
            val now=System.currentTimeMillis()
            db.projectDao().upsert(ProjectEntity(projectId,"Device Validation",now,now,model.id,memoryEnabled=false,archived=true))
            fixtures.create(context)
        }
        initialized=true
    }
    private suspend fun newSession(profile: AssistantProfile=AssistantProfile(ThinkingMode.OFF,emptySet()),usesDocuments:Boolean=false,projectOverride:String?=null):Session = withContext(Dispatchers.Main.immediate) {
        val sessionProject=projectOverride ?: if(usesDocuments)projectId else "self-test-"+UUID.randomUUID()
        require(sessionProject.startsWith("self-test-"))
        graph.assistantSettings.update(sessionProject,profile)
        val id="self-test-"+UUID.randomUUID();val now=System.currentTimeMillis()
        if(db.projectDao().get(sessionProject)==null)db.projectDao().upsert(ProjectEntity(sessionProject,"Device Validation",now,now,model.id,memoryEnabled=false,archived=true))
        db.conversationDao().upsert(ConversationEntity(id,sessionProject,"Device Validation",now,now))
        val store=ViewModelStore()
        val vm=ViewModelProvider(store,ChatViewModelFactory(sessionProject,graph,id))[ChatViewModel::class.java]
        val session=Session(vm,store,mutableListOf(),sessionProject);sessions.add(session)
        // Keep the same model flow subscribed as the Chat UI (media send checks its value).
        session.subscription=scope.launch { vm.models.collect {} }
        vm.currentConversationId.first { it==id };vm.models.first { it.any { m->m.id==model.id } }
        vm.awaitValidationReady()
        vm.modelPreparation.first { it.ready || it.error!=null }.let { if(it.error!=null)throw Blocked(it.error.code) }
        session
    }
    private suspend fun ask(session:Session,prompt:String,profile:AssistantProfile?=null):Turn = withContext(Dispatchers.Main.immediate) {
        profile?.let { session.vm.setAssistantProfile(it) }
        graph.validationRequestObserver={ session.requests.add(it) }
        val before=session.requests.size
        if(!session.vm.send(prompt))throw Blocked("SEND_NOT_ACCEPTED")
        session.vm.isGenerating.first { !it }
        val rows=db.messageDao().recent(checkNotNull(session.vm.currentConversationId.value),3)
        val assistant=rows.firstOrNull { it.role=="ASSISTANT" } ?: throw Blocked("NO_ASSISTANT_TURN")
        if(assistant.status!="COMPLETE")throw Blocked(session.vm.generationError.value?.code ?: "GENERATION_"+assistant.status,
            ValidationMetricCodec.runtime(owner.runtimes.runtimeFor(model.toDescriptor()).metrics()))
        val metric=graph.performance.lastGeneration.value?.second ?: throw Blocked("GENERATION_METRICS_UNAVAILABLE")
        if(metric.nativeCompletionObserved!=true)throw Blocked("NATIVE_COMPLETION_NOT_OBSERVED")
        val request=session.requests.drop(before).lastOrNull() ?: throw Blocked("NO_RUNTIME_REQUEST")
        val calls=db.toolCallDao().observeForConversation(assistant.conversationId).first().filter { it.messageId==assistant.id }
        lastMetrics=metric
        Turn(assistant.content,assistant,metric,calls,request)
    }
    private suspend fun closeSession(session:Session) = withContext(Dispatchers.Main.immediate) {
        session.vm.closeValidationSession();session.subscription?.cancelAndJoin();session.store.clear();session.requests.clear();sessions.remove(session)
    }
    private suspend fun freshEngine() {
        sessions.toList().forEach { closeSession(it) };graph.modelPreparation.pauseForBenchmark()
        owner.inferenceGate.withLock { owner.chatRuntimes.releaseForDiagnostics() }
        graph.modelPreparation.resumeAfterBenchmark()
    }
    private suspend fun document(name:String):String = documentIds[name] ?: withContext(Dispatchers.IO) {
        val id=graph.documents.ingest(projectId,Uri.fromFile(fixtures.file(name))).getOrThrow()
        check(db.documentDao().get(id)?.indexingStatus=="READY") { "Fixture was not indexed" }
        documentIds[name]=id;id
    }
    private fun result(call:ToolCallEntity):JSONObject?=runCatching { JSONObject(call.resultJson!!).getJSONObject("result") }.getOrNull()
    private fun capabilityReason(feature:String):String {
        val meta=ModelDescriptorCodec.decodeMetadata(model.metadataJson)
        return when {
            meta["capability.detectionError"]!=null -> "CAPABILITY_DETECTION_ERROR"
            meta["$feature.runtimeSupport"]=="false" -> "RUNTIME_CAPABILITY_MISSING"
            meta["$feature.appImplemented"]=="false" -> "APP_CAPABILITY_MISSING"
            meta["$feature.bundleDeclared"]=="false" -> "BUNDLE_CAPABILITY_MISSING"
            meta["$feature.modelSupport"]=="false" -> "MODEL_CAPABILITY_MISSING"
            meta["$feature.source"]=="UNDETERMINED_BUNDLE" -> "BUNDLE_CAPABILITY_MISSING"
            else -> "CAPABILITY_DETECTION_ERROR"
        }
    }
    private suspend fun arithmetic():ValidationOutcome {
        if(ModelCapability.TOOL_CALLING !in model.toDescriptor().capabilities)throw Blocked(capabilityReason("tools"))
        val t=ask(newSession(AssistantProfile(ThinkingMode.OFF,setOf("calculator.evaluate"))),"Usa la calculadora para calcular 123*47. Responde brevemente.")
        val executed=t.calls.any { it.toolId=="calculator.evaluate" && it.status=="SUCCESS" && result(it)?.optString("value")?.toDoubleOrNull()==5781.0 }
        return checkResult(executed && t.metrics.automaticToolCallingActive==true && "5781" in t.answer,"NATIVE_TOOL_RESULT_OR_FINAL_MISMATCH",evidence(t))
    }
    private suspend fun thinking(budgetProbe: Int? = null):ValidationOutcome {
        if(ModelCapability.THINKING !in model.toDescriptor().capabilities)throw Blocked(capabilityReason("thinking"))
        val reasoning="Analiza cuidadosamente: tres cajas contienen 4 objetos cada una. Se retiran 5. ¿Cuántos quedan? Responde brevemente."
        val cases=if(budgetProbe != null) listOf(Triple("ON_REASONING",ThinkingMode.ON,reasoning)) else listOf(
            Triple("OFF_SIMPLE",ThinkingMode.OFF,"¿Cuál es la capital de Japón?"),
            Triple("AUTO_SIMPLE",ThinkingMode.AUTO,"Hola."),
            Triple("AUTO_REASONING",ThinkingMode.AUTO,reasoning),
            Triple("ON_REASONING",ThinkingMode.ON,reasoning))
        val data=JsonObject();thinkingPartial=data;var firstReason:String?=null
        var control:ValidationOutcome?=null
        // Isolated conversations avoid cross-subcase history changing the prompt/budget.
        for((name,mode,prompt) in cases) {
            val expected=AssistantRouting.thinking(mode,true,prompt)
            data.addProperty(name+".status","RUNNING")
            data.addProperty(name+".thinkingRequested",mode.name)
            data.addProperty(name+".thinkingPolicyDecision",if(expected)"ON" else "OFF")
            val sub=JsonObject();var session:Session?=null
            var reason:String?=null
            var executionStarted=false;var correctFinal=false
            try {
                session=newSession(AssistantProfile(mode,emptySet()))
                session.vm.validationMaxOutputOverride=budgetProbe
                executionStarted=true
                if(budgetProbe==256)data.addProperty(name+".executionStarted",true)
                val t=ask(session,prompt)
                evidence(t).entrySet().forEach { (k,v)->sub.add(k,v) }
                reason=when {
                    t.metrics.thinkingEffective!=expected -> "THINKING_CONFIG_NOT_APPLIED"
                    expected && (t.metrics.thoughtCharacterCount ?: 0)<=0 -> "THINKING_CONFIG_NOT_APPLIED"
                    (t.metrics.finalCallbackCount ?: 0)<=0 || t.answer.isBlank() -> com.localai.workspace.inference.ThinkingFailure.reason(t.metrics) ?: "THINKING_NO_VISIBLE_OUTPUT"
                    name=="OFF_SIMPLE" && !Regex("(?i)tokio|tokyo").containsMatchIn(t.answer) -> "THINKING_VALIDATION_ERROR"
                    name.endsWith("REASONING") && !Regex("\\b7\\b").containsMatchIn(t.answer) -> "THINKING_VALIDATION_ERROR"
                    Regex("(?i)<think>|<analysis>|\\[analysis\\]|<\\|channel").containsMatchIn(t.answer) -> "THINKING_VALIDATION_ERROR"
                    else -> null
                }
                correctFinal=reason==null
            } catch(cancel:CancellationException) {
                data.addProperty(name+".status","BLOCKED")
                data.addProperty(name+".reasonCode",if(cancel is TimeoutCancellationException)"THINKING_TIMEOUT" else "CANCELLED")
                data.addProperty(name+".timeoutReached",cancel is TimeoutCancellationException)
                data.addProperty(name+".cancelled",cancel !is TimeoutCancellationException)
                throw cancel
            }
            catch(blocked:Blocked) {
                if(budgetProbe==256 && (blocked.code.startsWith("THINKING_") || blocked.code in setOf("NATIVE_ERROR","NO_TOKENS","WORKER_DISCONNECTED")))executionStarted=true
                blocked.data.entrySet().forEach { (k,v)->sub.add(k,v) }
                reason=when {
                    blocked.code.startsWith("THINKING_") -> blocked.code
                    blocked.code.contains("TIMEOUT") -> "THINKING_TIMEOUT"
                    blocked.code=="NATIVE_ERROR" || budgetProbe==256 && blocked.code=="WORKER_DISCONNECTED" -> "THINKING_SDK_ERROR"
                    blocked.code=="NO_TOKENS" -> com.localai.workspace.inference.ThinkingFailure.reason(owner.runtimes.runtimeFor(model.toDescriptor()).metrics()) ?: "THINKING_NO_VISIBLE_OUTPUT"
                    else -> "THINKING_VALIDATION_ERROR"
                }
                sub.addProperty("sourceReason",blocked.code)
            } catch(error:Exception) { reason="THINKING_VALIDATION_ERROR";if(budgetProbe==256)executionStarted=true }
            finally { session?.let { closeSession(it) } }
            sub.addProperty("thinkingRequested",mode.name)
            sub.addProperty("thinkingPolicyDecision",if(expected)"ON" else "OFF")
            if(budgetProbe==256) {
                val evaluated=ThinkingBudgetControl.evaluate(sub,reason,correctFinal,executionStarted)
                control=evaluated
                evaluated.metrics.entrySet().forEach { (k,v)->sub.add(k,v) }
                reason=evaluated.reason
            }
            sub.addProperty("status",control?.status?.name ?: if(reason==null)"PASS" else "BLOCKED")
            sub.addProperty("reasonCode",reason)
            sub.addProperty("timeoutReached",reason=="THINKING_TIMEOUT")
            sub.addProperty("cancelled",false)
            // SDK doesn't expose per-channel token counts or native finish reason.
            sub.add("thoughtTokenCount",com.google.gson.JsonNull.INSTANCE)
            sub.add("finalTokenCount",com.google.gson.JsonNull.INSTANCE)
            sub.add("finishReason",com.google.gson.JsonNull.INSTANCE)
            if(firstReason==null && reason!=null)firstReason=reason
            sub.entrySet().forEach { (k,v)->data.add(name+"."+k,v) }
        }
        if(budgetProbe==256) {
            val result=checkNotNull(control)
            result.metrics.entrySet().forEach { (k,v)->data.add(k,v) }
            data.addProperty("finalOnlyValidated",result.status==ValidationStatus.PASS && result.metrics["expectedExhaustionObserved"]?.asBoolean!=true)
            return result.copy(metrics=data)
        }
        data.addProperty("finalOnlyValidated",firstReason==null)
        return ValidationOutcome(if(firstReason==null)ValidationStatus.PASS else ValidationStatus.BLOCKED,firstReason,data)
    }
    private fun evidenceFromLast()=ValidationMetricCodec.runtime(lastMetrics)
    private suspend fun structured(csv:Boolean):ValidationOutcome {
        val name=if(csv)"validation.csv" else "validation.xlsx"
        val table=withContext(Dispatchers.IO) { if(csv)StructuredDocuments.csv(fixtures.file(name)) else StructuredDocuments.xlsx(fixtures.file(name)) }
        val valid=table.cells.any { it.address=="B2" && it.value=="12" && (csv||it.sheet=="Sales") } && table.cells.any { it.address=="B3" && it.value=="4" } && (!csv || table.cells.any { it.address=="A2" && it.value=="a;b" })
        if(!valid)return checkResult(false,"STRUCTURED_PARSER_MISMATCH",metrics("parserValidated" to false))
        val id=document(name)
        val t=ask(newSession(AssistantProfile(ThinkingMode.OFF,setOf("files.read")),usesDocuments=true),"Usa files.read para leer el archivo documentId '$id', sheet '${if(csv)"CSV" else "Sales"}', column 'B', aggregate 'sum'. Indica el resultado de esa herramienta brevemente.")
        val success=t.calls.any { it.toolId=="files.read" && it.status=="SUCCESS" && result(it)?.optString("value")?.toDoubleOrNull()==16.0 }
        return checkResult(success && Regex("\\b16\\b").containsMatchIn(t.answer),"STRUCTURED_NATIVE_TOOL_MISMATCH",evidence(t).apply { addProperty("parserValidated",true) })
    }
    private suspend fun semantic():ValidationOutcome {
        if(graph.embeddingModels.id==null) {
            val failed=graph.embeddingModels.diagnostics.value?.takeIf { it.status=="FAILED" }
            if(failed!=null)throw Blocked("EMBEDDING_MODEL_LOAD_FAILED",metrics("embeddingImportStage" to failed.stage.name,"embeddingImportCode" to failed.code,"embeddingNativeCode" to failed.nativeCode))
            return skipped("EMBEDDING_MODEL_NOT_INSTALLED")
        }
        val query="¿Por qué ciertas aves adquieren tonalidades rosadas?"
        val start=System.nanoTime();var dimension=0;var norm=0.0;var finite=false;var hash:String?=null;var queryVector=FloatArray(0)
        graph.embeddingModels.use { encoder->val vector=encoder.embed("task: search result | query: "+query);queryVector=vector;dimension=vector.size;finite=vector.all { it.isFinite() };norm=sqrt(vector.sumOf { it.toDouble()*it });hash=encoder.modelId.substringAfter(':').substringBefore(':') } ?: throw Blocked("EMBEDDING_ENCODER_UNAVAILABLE")
        val embedMs=(System.nanoTime()-start)/1_000_000
        val a=document("semantic_a.txt");val b=document("semantic_b.txt")
        val found=graph.retrieval.retrieve(projectId,query,documentIds=setOf(a,b))
        val diag=graph.retrieval.diagnostics.value
        val segment=db.documentDao().segmentsForDocument(a).firstOrNull()
        val stored=segment?.let { db.semanticVectorDao().get("D:"+it.id,graph.embeddingModels.id!!) }
        suspend fun similarity(documentId:String):Double? {
            val source=db.documentDao().segmentsForDocument(documentId).firstOrNull() ?: return null
            val record=db.semanticVectorDao().get("D:"+source.id,graph.embeddingModels.id!!) ?: return null
            val vector=VectorPersistence.decode(record,VectorPersistence.hash(source.text)) ?: return null
            return com.localai.workspace.domain.rag.cosineSimilarity(queryVector,vector)
        }
        val scoreA=similarity(a);val scoreB=similarity(b)
        val data=metrics("dimension" to dimension,"vectorNorm" to norm,"finite" to finite,"embeddingDurationMs" to embedMs,"embeddingModelHash" to hash,
            "semanticScoreA" to scoreA,"semanticScoreB" to scoreB,"semanticUsed" to diag.mode.contains("SEMANTIC"),"topDocument" to if(found.firstOrNull()?.documentId==a)"A" else "OTHER","retrievalDurationMs" to diag.retrievalMs,"retrievalMode" to diag.mode,"persistedRows" to if(stored!=null)1 else 0)
        val ranked=scoreA!=null && scoreB!=null && scoreA>scoreB && scoreA>=0.25 && (found.firstOrNull()?.retrievalScore ?: 0.0)>0.0
        return checkResult(dimension==768 && finite && norm in 0.98..1.02 && ranked && found.firstOrNull()?.documentId==a && diag.mode.contains("SEMANTIC") && stored?.dimensions==768,"SEMANTIC_RETRIEVAL_OR_VECTOR_MISMATCH",data)
    }
    private suspend fun python(id:String):ValidationOutcome {
        if(!PythonAssets.available())return skipped("WEBVIEW_WASM_UNAVAILABLE")
        val source=when(id) {
            "python"->"import statistics\nprint(statistics.mean([1,2,3]))"
            "python_timeout"->"while True:\n    pass"
            "python_subprocess"->"import subprocess"
            "python_network"->"import socket"
            "python_filesystem"->"print(open('/etc/passwd').read())"
            else->"print('x' * 17000)"
        }
        val response=JSONObject(PythonClient(context).execute(source));val status=response.optString("status")
        val ok=when(id) { "python"->status=="SUCCESS" && response.optString("stdout").trim() in setOf("2","2.0")
            "python_timeout"->status=="TIMEOUT";"python_output"->status=="OUTPUT_LIMIT"
            else->status=="POLICY_REJECTED" }
        val data=metrics("pythonStatus" to if(status=="TIMEOUT")"TOOL_TIMEOUT" else status,"bootstrapMs" to response.optLong("bootstrapMs").takeIf { !response.isNull("bootstrapMs") && response.has("bootstrapMs") },
            "executionMs" to response.optLong("executionMs").takeIf { response.has("executionMs") },"pythonRequestMs" to response.optLong("requestMs"),"outputLimitReached" to (status=="OUTPUT_LIMIT"))
        if(status in setOf("INITIALIZATION_ERROR","INITIALIZATION_TIMEOUT","RENDERER_GONE","INVALID_RESULT"))return ValidationOutcome(ValidationStatus.BLOCKED,"PYTHON_"+status,data)
        return checkResult(ok,"PYTHON_CONTRACT_MISMATCH",data)
    }
    private suspend fun media(audio:Boolean):ValidationOutcome {
        val capability=if(audio)ModelCapability.AUDIO else ModelCapability.VISION
        if(capability !in model.toDescriptor().capabilities)return skipped(if(audio)"MODEL_AUDIO_NOT_DECLARED" else "MODEL_VISION_NOT_DECLARED")
        val s=newSession()
        withContext(Dispatchers.Main.immediate) {
            if(audio)s.vm.attachAudio(Uri.fromFile(fixtures.file("audio_fixture.wav"))) else s.vm.attachImage(Uri.fromFile(fixtures.file("vision_fixture.png")))
            (if(audio)s.vm.attachedAudioPath else s.vm.attachedImagePath).first { it!=null }
        }
        val t=ask(s,if(audio)"Transcribe este audio. Responde sólo con la transcripción." else "¿Qué número aparece en la imagen? Responde sólo con el número.")
        if(audio)audioSession=s else visionSession=s
        val present=if(audio)t.metrics.audioInputPresent==true && t.metrics.audioBackendInitialized==true && t.request?.audioPath!=null else t.metrics.visionInputPresent==true && t.metrics.visionBackendInitialized==true && t.request?.imagePath!=null
        val clear=if(audio)s.vm.attachedAudioPath.value==null else s.vm.attachedImagePath.value==null
        val user=db.messageDao().recent(t.message.conversationId,3).firstOrNull { it.role=="USER" }
        val persisted=if(audio)user?.audioPath!=null else user?.imagePath!=null
        val correct=if(audio)t.answer.contains("bicicleta",ignoreCase=true) else Regex("\\b42\\b").containsMatchIn(t.answer)
        val data=evidence(t).apply { addProperty("composerCleared",clear);if(audio) { addProperty("audioPreparationMs",s.vm.audioPreprocessingMs.value);addProperty("audioDurationMs",com.localai.workspace.audio.WavInput.inspect(fixtures.file("audio_fixture.wav")).durationMs) } }
        return checkResult(present && correct && clear && persisted,"MULTIMODAL_INPUT_OR_ANSWER_MISMATCH",data)
    }
    private suspend fun lifecycle(audio:Boolean):ValidationOutcome {
        val previous=if(audio)audioSession else visionSession
        if(previous==null)return ValidationOutcome(ValidationStatus.BLOCKED,"MEDIA_TEST_NOT_COMPLETED")
        val t=ask(previous,"Responde brevemente: ¿cuánto es dos más dos?",AssistantProfile(ThinkingMode.OFF,emptySet()))
        val noMedia=t.request?.let { it.imagePath==null && it.audioPath==null }==true && t.metrics.visionInputPresent==false && t.metrics.audioInputPresent==false
        val s=newSession(projectOverride=previous.projectId);val fresh=ask(s,"Hola. Responde brevemente.")
        val isolated=fresh.request?.let { it.imagePath==null && it.audioPath==null && it.conversation?.history.orEmpty().none { turn->turn.imagePath!=null || turn.audioPath!=null } }==true
        if(audio)audioSession=null else visionSession=null
        return checkResult(noMedia && isolated,"MEDIA_LIFECYCLE_MISMATCH",evidence(t).apply { addProperty("nextRequestMediaAbsent",noMedia);addProperty("newChatIsolated",isolated) })
    }
    override suspend fun execute(id:String):ValidationOutcome = try {
        initialize()
        when(id) {
            "text"->{val t=ask(newSession(),"¿Cuál es la capital de Japón? Responde brevemente.");checkResult(Regex("(?i)tokio|tokyo").containsMatchIn(t.answer),"FINAL_ANSWER_MISMATCH",evidence(t))}
            "calculator"->arithmetic()
            "tools_off"->{val t=ask(newSession(),"Calcula 123*47. Responde brevemente.");checkResult(t.calls.isEmpty() && t.metrics.automaticToolCallingActive==false,"TOOLS_OFF_EXECUTED",evidence(t))}
            "thinking"->thinking()
            "thinking_budget_256"->thinking(256)
            "thinking_budget_512"->thinking(512)
            "thinking_budget_1024"->thinking(1024)
            "xlsx"->structured(false)
            "csv"->structured(true)
            "semantic"->semantic()
            "python","python_timeout","python_subprocess","python_network","python_filesystem","python_output"->python(id)
            "vision"->media(false)
            "audio"->media(true)
            "vision_lifecycle"->lifecycle(false)
            "audio_lifecycle"->lifecycle(true)
            "warmup"->checkResult(lastMetrics.warmupStatus=="WARMED" && lastMetrics.warmupDurationMs!=null,"WARMUP_NOT_COMPLETED",evidenceFromLast())
            "cold"->{freshEngine();val s=newSession();val preparation=graph.modelPreparation.state.value.metrics;val t=ask(s,"¿Cuál es la capital de Japón? Responde brevemente.");warmSession=s;checkResult(t.metrics.engineReused==true && preparation?.engineReused==false,"COLD_ENGINE_EVIDENCE_MISSING",evidence(t).apply { addProperty("coldPreparationEngineReused",preparation?.engineReused);addProperty("modelLoadDurationMs",preparation?.modelLoadDurationMs);addProperty("modelPreparationDurationMs",preparation?.modelPreparationDurationMs) })}
            "warm"->{warmSession?.let { closeSession(it) };val s=newSession();val t=ask(s,"Hola. Responde brevemente.");warmSession=s;checkResult(t.metrics.engineReused==true && t.metrics.sessionReused==false,"WARM_STATE_MISMATCH",evidence(t))}
            "continuation"->{val s=newSession();ask(s,"Recuerda temporalmente que el código es 3817. Confirma brevemente.");val t=ask(s,"¿Cuál era el código? Responde sólo con el código.");val ok="3817" in t.answer
                if(ok && (t.metrics.sessionReused!=true || (t.metrics.cachedTokenCount ?: 0)<=0))ValidationOutcome(ValidationStatus.INCONCLUSIVE,"KV_EVIDENCE_UNAVAILABLE",evidence(t)) else checkResult(ok && t.metrics.sessionReused==true,"CONTINUATION_MISMATCH",evidence(t))}
            "files"->{val idDoc=document("validation.txt");val t=ask(newSession(AssistantProfile(ThinkingMode.OFF,setOf("files.list","files.read")),usesDocuments=true),"Usa files.list para listar los archivos. Luego usa files.read con documentId '$idDoc' para leer validation.txt. ¿Cuál es el VALIDATION_MARKER? Responde brevemente.")
                checkResult(setOf("files.list","files.read").all { name->t.calls.any { it.toolId==name && it.status=="SUCCESS" } } && t.calls.any { result(it)?.optString("text")?.contains("12345")==true } && "12345" in t.answer,"FILES_NATIVE_TOOL_MISMATCH",evidence(t))}
            "zip"->{val parsed=DocumentParser(context).parse(fixtures.file("validation.zip"),"application/zip","validation.zip");val text=parsed.pages.joinToString { it.text };checkResult(listOf("validation.txt","validation.csv","12345","a;b").all { it in text },"ZIP_PARSER_MISMATCH",metrics("parserValidated" to true))}
            "zip_traversal"->withContext(Dispatchers.IO) {
                val sandbox=java.io.File(fixtures.root,"zip-control/sandbox").apply { mkdirs() }
                val hostile=java.io.File(sandbox,"traversal.zip")
                fixtures.file("traversal.zip").copyTo(hostile,overwrite=true)
                val zipContext=ValidationContext(context,java.io.File(sandbox,"cache"))
                ZipTraversalControl.run(hostile,sandbox) { file ->
                    DocumentParser(zipContext).parse(file,"application/zip","traversal.zip")
                    Unit
                }
            }
            "lexical"->{val a=document("semantic_a.txt");document("semantic_b.txt");val off=object:EmbeddingProvider { override suspend fun <T> use(block:suspend (NeuralEmbeddings)->T):T?=null }
                val retriever=SemanticRetrievalService(db,off);val found=retriever.retrieve(projectId,"flamencos pigmentos");checkResult(found.firstOrNull()?.documentId==a && retriever.diagnostics.value.mode=="LEXICAL","LEXICAL_FALLBACK_MISMATCH",metrics("retrievalMode" to retriever.diagnostics.value.mode,"retrievalDurationMs" to retriever.diagnostics.value.retrievalMs))}
            "memory"->{if(graph.embeddingModels.id==null)skipped("EMBEDDING_MODEL_NOT_INSTALLED") else {
                val now=System.currentTimeMillis();val memoryId="self-test-"+UUID.randomUUID();db.memoryDao().upsert(MemoryItemEntity(memoryId,"PROJECT",projectId,"Prefiero respuestas breves.","USER_APPROVED",createdAt=now,updatedAt=now,kind="PREFERENCE"))
                val found=graph.retrieval.memories(projectId,"¿Cómo prefiero las respuestas?");val unrelated=graph.retrieval.memories(projectId,"Calcula 5+5.")
                val record=db.semanticVectorDao().get("M:"+memoryId,graph.embeddingModels.id!!)
                checkResult(found.any { it.id==memoryId } && unrelated.none { it.id==memoryId } && record?.dimensions==768,"MEMORY_RETRIEVAL_MISMATCH",metrics("memoryRetrieved" to found.size,"persistedRows" to if(record!=null)1 else 0))}}
            "rebuild"->{val s=newSession();ask(s,"Hola. Responde brevemente.");val tool=ask(s,"Usa la calculadora para calcular 123*47.",AssistantProfile(ThinkingMode.OFF,setOf("calculator.evaluate")));val think=ask(s,"Analiza tres cajas con 4 objetos cada una. Se retiran 5. ¿Cuántos quedan?",AssistantProfile(ThinkingMode.ON,emptySet()))
                checkResult(tool.calls.any { it.status=="SUCCESS" } && tool.metrics.sessionReused==false && tool.metrics.conversationRebuildReason in setOf("CONFIG_CHANGED","SESSION_LOST") && think.metrics.thinkingEffective==true && think.metrics.sessionReused==false && think.metrics.conversationRebuildReason=="THINKING_CHANGED","REBUILD_REASON_MISMATCH",evidence(think))}
            "persistence"->{val session=newSession();val t=ask(session,"Hola. Responde brevemente.");val reopened=WorkspaceDatabase.createValidation(context);try {
                val stored=reopened.messageDao().recent(t.message.conversationId,3)
                val origin=documentIds["semantic_a.txt"]?.let { db.documentDao().segmentsForDocument(it).firstOrNull() }?.let { "D:"+it.id }
                val modelId=graph.embeddingModels.id
                val vector=if(origin!=null && modelId!=null)db.semanticVectorDao().get(origin,modelId) else null
                val vectorPersisted=vector==null || reopened.semanticVectorDao().get(vector.originId,vector.modelId)?.vector?.contentEquals(vector.vector)==true
                checkResult(stored.any { it.id==t.message.id && it.status=="COMPLETE" } && vectorPersisted,"ROOM_PERSISTENCE_MISMATCH",metrics("persistedRows" to (stored.size+if(vector!=null)1 else 0)))
            } finally { reopened.close() }}
            "errors"->{val s=newSession();val count=s.requests.size;withContext(Dispatchers.Main.immediate) { s.vm.attachImage(Uri.fromFile(fixtures.file("invalid.png")));check(s.vm.send("Describe esta imagen."));s.vm.isGenerating.first { !it } }
                checkResult(s.requests.size==count && s.vm.generationError.value!=null && s.vm.attachedImagePath.value==null,"INVALID_IMAGE_TEXT_FALLBACK",metrics("runtimeAccepted" to (s.requests.size>count)))}
            "device_metrics"->{val data=measurement();if(data["appPssBytes"]?.isJsonNull==false && data["thermalStatus"]?.isJsonNull==false)ValidationOutcome(ValidationStatus.PASS,metrics=data) else ValidationOutcome(ValidationStatus.INCONCLUSIVE,"ANDROID_METRICS_UNAVAILABLE",data)}
            else->ValidationOutcome(ValidationStatus.NOT_TESTED,"UNKNOWN_CASE")
        }
    } catch(cancel:CancellationException) {
        withContext(NonCancellable+Dispatchers.Main.immediate) { sessions.forEach { it.vm.cancelAndAwait() } };throw cancel
    } catch(block:Blocked) { ValidationOutcome(ValidationStatus.BLOCKED,block.code,block.data) }
    catch(native:com.arm.aichat.EmbeddingNativeException) { ValidationOutcome(ValidationStatus.BLOCKED,"EMBEDDING_MODEL_LOAD_FAILED",metrics("embeddingImportStage" to native.stage,"embeddingNativeCode" to native.code)) }
    finally {
        withContext(NonCancellable+Dispatchers.Main.immediate) {
            sessions.toList().filter { it!==visionSession && it!==audioSession && it!==warmSession }.forEach { closeSession(it) }
        }
    }
    override suspend fun measurement():JsonObject=withContext(Dispatchers.IO) { val m=DeviceMeasurements.capture(context);metrics("appPssBytes" to m.pssBytes,"availableRamBytes" to m.availableRamBytes,"totalRamBytes" to m.totalRamBytes,"thermalStatus" to m.thermal) }
    override suspend fun close() {
        var failed:Throwable?=null
        suspend fun clean(block:suspend ()->Unit) { try { block() } catch(e:Throwable) { if(failed==null)failed=e } }
        clean { graph.modelPreparation.pauseForBenchmark() }
        graph.validationRequestObserver=null
        sessions.toList().forEach { session->clean { closeSession(session) } }
        clean { owner.inferenceGate.withLock { owner.chatRuntimes.releaseForDiagnostics() } }
        clean { val jobs=scope.coroutineContext[Job]?.children?.toList().orEmpty();scope.cancel();jobs.joinAll() }
        clean { withContext(Dispatchers.IO) { db.close() } }
        clean { withContext(Dispatchers.IO) { ValidationResources.deleteDatabase(context) } }
        clean { withContext(Dispatchers.IO) { ValidationResources.clean(context,runId) } }
        owner.embeddingModels.status.value=embeddingStatus
        failed?.let { throw it }
    }
}
