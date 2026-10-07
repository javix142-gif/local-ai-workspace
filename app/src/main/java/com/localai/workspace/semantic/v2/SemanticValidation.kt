package com.localai.workspace.semantic.v2

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.google.gson.GsonBuilder
import com.localai.workspace.AppGraph
import com.localai.workspace.data.*
import com.localai.workspace.performance.DeviceMeasurements
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.sqrt

/** Real native independent suite; synthetic private fixtures never touch the user's workspace DB. */
class SemanticValidation(private val installed: SemanticLayer, private val context: Context, private val graph: AppGraph) {
    data class Case(val id: String, val status: String, val reason: String? = null, val metrics: Map<String,Any?> = emptyMap())
    suspend fun run(): String = withContext(Dispatchers.IO) {
        check(!graph.validationBusy.value && !graph.performance.running.value && !graph.chatSessions.hasGeneration) { "OTHER_INFERENCE_OPERATION_ACTIVE" }
        graph.validationBusy.value=true
        installed.restore();val model=installed.manager.selected
        val cases=mutableListOf<Case>();val before=DeviceMeasurements.capture(context)
        val runId=UUID.randomUUID().toString();val name="semantic-validation-$runId.db";val prefs="semantic-validation-$runId"
        val root=File(context.filesDir,"semantic-validation/$runId").apply{mkdirs()}
        val workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        var db=Room.databaseBuilder(context,SemanticDatabase::class.java,name).build()
        val layer=SemanticLayer(context,workspace,graph.inferenceGate,db,prefs)
        val originalProfile=installed.manager.profile
        try { installed.manager.unload() } catch(error:Throwable) { workspace.close();db.close();graph.validationBusy.value=false;throw error }
        suspend fun test(id:String,block:suspend()->Map<String,Any?>) {
            installed.notice.value="Semantic V2 · $id"
            try { cases+=Case(id,"PASS",metrics=withTimeout(240_000){block()}) }
            catch(timeout:TimeoutCancellationException){cases+=Case(id,"FAIL","CASE_TIMEOUT")}
            catch(cancel:CancellationException){throw cancel}
            catch(error:Throwable){cases+=Case(id,"FAIL",(error as? SemanticFailure)?.code?.name ?: error.javaClass.simpleName)}
        }
        fun fixture(name:String)=File(root,name).also { f->if(!f.exists())context.assets.open("semantic-v2/fixtures/$name").use { input->f.outputStream().use{input.copyTo(it)} } }
        suspend fun vector(input:SemanticInput,task:EmbeddingTask=EmbeddingTask.DOCUMENT)=layer.manager.embed(input,task,768)
        val project="semantic-test-$runId";val scope=SemanticScope(ScopeType.PROJECT,project)
        suspend fun document(id:String,text:String) { val file=File(root,"$id.txt").apply{writeText(text)};workspace.documentDao().insert(DocumentEntity(id,project,"$id.txt",localPath=file.path,mimeType="text/plain",fileHash=fingerprint(text),byteSize=file.length(),extractionStatus="READY",indexingStatus="READY",importedAt=1,updatedAt=1));workspace.documentDao().insertSegments(listOf(DocumentSegmentEntity(documentId=id,segmentIndex=0,text=text,normalizedText=text,contentHash=fingerprint(text)))) }
        try {
            if(model==null) { cases+=Case("eg2_import","BLOCKED","MODEL_NOT_INSTALLED") }
            else {
                db.dao().model(model);layer.activate(model.id)
                workspace.projectDao().upsert(ProjectEntity(project,"Synthetic fixture",1,1,archived=true))
                document("expected","The workspace code word is ORCHID. Account access can be recovered by requesting a password reset link.")
                document("distractor","Tomorrow's weather forecast is rain. A blue car is parked outside.")
                test("eg2_import") { val file=File(model.privatePath);check(file.isFile && file.length()==model.fileSize);val hash=file.inputStream().use { input-> val md=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){ensureActive();val n=input.read(b);if(n<0)break;md.update(b,0,n)};md.digest().joinToString(""){"%02x".format(it)} };check(hash==model.sha256);mapOf("bytes" to model.fileSize,"sha256" to hash) }
                test("eg2_model_info") { check(model.nativeDimension==768);mapOf("runtime" to model.runtime,"declaredModalities" to model.declaredModalities,"lengthDiscovery" to "NATIVE_INIT_AND_REAL_PROBES") }
                for(dim in listOf(768,256)) test("eg2_text_$dim") { val a=layer.manager.embed(SemanticInput.Text("bicicleta roja"),EmbeddingTask.SEARCH,dim);val b=layer.manager.embed(SemanticInput.Text("bicicleta roja"),EmbeddingTask.SEARCH,dim);val norm=sqrt(a.vector.sumOf{it.toDouble()*it});check(a.vector.size==dim && a.vector.all{it.isFinite()} && kotlin.math.abs(norm-1)<1e-3 && SemanticVectors.cosine(a,b)>0.999);mapOf("modelLoaded" to (layer.manager.status.value.state==ProviderState.READY),"nonZero" to a.vector.any{it!=0f},"dimension" to dim,"finite" to true,"norm" to norm,"deterministicEnough" to true,"embeddingMs" to a.durationMs) }
                test("eg2_space_isolation") { val eg2=EmbeddingEngineManager.space(model,768);val eg1=eg2.copy(provider="gguf-jni",family="EMBEDDING_GEMMA_1");check(eg2!=eg1 && eg2!=eg2.copy(dimension=256));try{SemanticVectors.cosine(EmbeddingResult(eg2,floatArrayOf(1f),0),EmbeddingResult(eg1,floatArrayOf(1f),0));error("Mixed spaces")}catch(e:SemanticFailure){check(e.code==SemanticError.SPACE_MISMATCH)};mapOf("observedCondition" to "SPACE_MISMATCH_REJECTED") }
                test("eg2_task_prefix_retrieval") {
                    val positives=listOf("Para restablecer la clave de acceso, solicita un enlace de recuperación.","Request a recovery link to restore account access.")
                    val queries=listOf("¿Cómo puedo recuperar mi contraseña?","How can I reset my password?")
                    val rankings=queries.indices.map { i->val q=vector(SemanticInput.Text(queries[i]),EmbeddingTask.SEARCH);val a=vector(SemanticInput.Text(positives[i]));val b=vector(SemanticInput.Text("Rain is forecast tomorrow."));if(SemanticVectors.cosine(q,a)>SemanticVectors.cosine(q,b))listOf("positive","negative")else listOf("negative","positive") }
                    val metrics=RetrievalQuality.metrics(rankings,listOf(setOf("positive"),setOf("positive")));check(metrics["recall@1"]==1.0);metrics+mapOf("prefixProfile" to TaskPromptProfile.VERSION,"languages" to "es,en")
                }
                test("eg2_code_retrieval") { val q=vector(SemanticInput.Text("where is session reuse handled?"),EmbeddingTask.CODE_QUERY);val a=vector(SemanticInput.Code("fun reuseConversation(session: Session) { cachedSession = session }","Session.kt"),EmbeddingTask.CODE_DOCUMENT);val b=vector(SemanticInput.Code("fun parseCsv(row: String) = row.split(',')","Csv.kt"),EmbeddingTask.CODE_DOCUMENT);val sa=SemanticVectors.cosine(q,a);val sb=SemanticVectors.cosine(q,b);check(sa>sb);mapOf("positiveScore" to sa,"distractorScore" to sb,"taskType" to "CODE_QUERY") }
                val bicycle=SemanticInput.Image(fixture("red-bicycle.png").path);val car=SemanticInput.Image(fixture("blue-car.png").path)
                test("eg2_image_text") { val q=vector(SemanticInput.Text("a red bicycle"),EmbeddingTask.SEARCH);val a=vector(bicycle);val b=vector(car);val sa=SemanticVectors.cosine(q,a);val sb=SemanticVectors.cosine(q,b);check(sa>sb);mapOf("visionInputExecuted" to true,"positiveScore" to sa,"distractorScore" to sb) }
                test("eg2_image_image") { val a=vector(bicycle);val b=vector(bicycle);val c=vector(car);check(SemanticVectors.cosine(a,b)>SemanticVectors.cosine(a,c));mapOf("directImageEmbedding" to true) }
                test("eg2_audio_text") { val q=vector(SemanticInput.Text("a steady continuous musical tone"),EmbeddingTask.SEARCH);val a=vector(SemanticInput.Audio(fixture("tone.wav").path));val b=vector(SemanticInput.Audio(fixture("noise.wav").path));val sa=SemanticVectors.cosine(q,a);val sb=SemanticVectors.cosine(q,b);check(sa>sb);mapOf("audioInputExecuted" to true,"positiveScore" to sa,"distractorScore" to sb,"transcriptionUsed" to false) }
                test("eg2_video_text") { layer.indexMedia(Uri.fromFile(fixture("bicycle-car.mp4")),scope,SemanticModality.VIDEO);val hit=layer.search(SemanticInput.Text("a red bicycle"),scope,setOf(SemanticModality.VIDEO),1).single();check(hit.startMs==0L && hit.endMs==5000L);mapOf("videoEmbeddingMethod" to "FRAME_SEQUENCE_REPRESENTATIVE_IMAGE","startMs" to hit.startMs,"endMs" to hit.endMs) }
                test("eg2_cross_modal") { val q=vector(bicycle);val a=vector(SemanticInput.Text("a red bicycle"));val b=vector(SemanticInput.Text("a blue automobile"));check(SemanticVectors.cosine(q,a)>SemanticVectors.cosine(q,b));mapOf("queryModality" to "IMAGE","corpusModality" to "TEXT") }
                var oldIndex:String?=null
                test("eg2_hybrid_retrieval") { oldIndex=layer.indexProject(project);val hit=layer.search(SemanticInput.Text("workspace code word ORCHID"),scope,setOf(SemanticModality.TEXT)).first();check(hit.sourceName=="expected.txt" && hit.semanticScore!=null && hit.lexicalScore!=null);mapOf("semantic" to true,"lexical" to true,"fusion" to "RRF") }
                if(graph.embeddingModels.id==null) cases+=Case("eg1_fallback","BLOCKED","EG1_MODEL_NOT_INSTALLED") else test("eg1_fallback") { layer.manager.unload();val v=graph.inferenceGate.withLock{LegacyEmbeddingProvider(graph.embeddingModels).embed(SemanticInput.Text("password recovery"),EmbeddingTask.SEARCH,768)};check(v.vector.size==768 && v.space.family=="EMBEDDING_GEMMA_1");mapOf("nativeEg1Executed" to true) }
                test("eg2_reindex") { val old=oldIndex ?: error("OLD_INDEX_UNAVAILABLE");layer.setDimension(256);val started=System.nanoTime();val green=layer.indexProject(project);val elapsed=(System.nanoTime()-started)/1_000_000;val ranking=layer.search(SemanticInput.Text("ORCHID"),scope,setOf(SemanticModality.TEXT)).map{it.sourceName};val quality=RetrievalQuality.metrics(listOf(ranking),listOf(setOf("expected.txt")));check(quality["recall@1"]==1.0);check(db.dao().count(old)>0 && db.dao().active(scope.key)?.indexId==green);layer.setDimension(768);layer.switchIndex(old);check(layer.search(SemanticInput.Text("ORCHID"),scope,setOf(SemanticModality.TEXT)).first().sourceName=="expected.txt");check(layer.search(SemanticInput.Text("a red bicycle"),scope,setOf(SemanticModality.VIDEO),1).single().startMs==0L);mapOf("oldIndexRetained" to true,"rollbackSearchSucceeded" to true,"quality256" to quality,"indexingElapsedMs" to elapsed,"indexingSegmentsPerSecond" to if(elapsed>0)db.dao().count(green)*1000.0/elapsed else null) }
                test("eg2_persistence") { val active=db.dao().active(scope.key) ?: error("INDEX_UNAVAILABLE");val count=db.dao().count(active.indexId);val reopen=Room.databaseBuilder(context,SemanticDatabase::class.java,name).build();try{check(reopen.dao().active(scope.key)==active && reopen.dao().count(active.indexId)==count)}finally{reopen.close()};mapOf("indexPersisted" to true,"vectors" to count) }
                test("eg2_lazy_modality") { layer.manager.unload();vector(SemanticInput.Text("profile"));val first=layer.manager.status.value.engineGeneration;vector(bicycle);val vision=layer.manager.status.value.engineGeneration;vector(SemanticInput.Text("profile"));val next=layer.manager.status.value.engineGeneration;check(vision>first && next==vision);mapOf("textToVisionReconfiguration" to true,"subsequentTextReusedEngine" to true,"actualEncoderLoadedTelemetry" to null) }
                test("eg2_unload_reload") { layer.manager.unload();check(layer.manager.status.value.state==ProviderState.UNLOADED);vector(SemanticInput.Text("reload"));check(layer.manager.status.value.state==ProviderState.READY);mapOf("reloadObserved" to true) }
                test("eg2_gemma_interop") { val hits=layer.search(SemanticInput.Text("workspace code word"),scope,setOf(SemanticModality.TEXT));val bundle=ContextBuilder().build(hits,512);check(bundle.evidence.isNotEmpty() && SemanticInterop(graph).answer(bundle));vector(SemanticInput.Text("after Gemma"));mapOf("retrievalUsed" to true,"evidenceReceivedByGemma" to true,"expectedAnswerObserved" to true,"eg2AfterGenerationSucceeded" to true) }
            }
            val after=DeviceMeasurements.capture(context);cases+=Case("eg2_device_metrics",if(after.pssBytes!=null && after.thermal!=null)"PASS"else"BLOCKED",metrics=mapOf("before" to before,"after" to after,"workerPss" to null,"workerPssAvailability" to "EG2 runs in app process"))
            val report=GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(mapOf("suite" to "SEMANTIC_V2","runtime" to "LiteRT-LM 0.17.1","deviceModel" to android.os.Build.MODEL,"androidApi" to android.os.Build.VERSION.SDK_INT,"semanticSchema" to 2,"iterations" to 1,"overall" to if(cases.all{it.status=="PASS"})"PASS"else if(cases.any{it.status=="FAIL"})"FAIL"else"BLOCKED","cases" to cases))
            val dir=File(context.filesDir,"semantic-v2-reports").apply{mkdirs()};val temp=File(dir,"latest.part");temp.writeText(report);check(temp.renameTo(File(dir,"latest.json")));report
        } finally { withContext(NonCancellable) { try { layer.manager.unload();db.dao().sources(scope.key).filter { it.sourceType=="VIDEO" }.forEach { source->val f=File(source.reference);if(f.canonicalFile.parentFile?.parentFile==File(context.filesDir,"semantic-media-v2").canonicalFile)f.parentFile?.deleteRecursively() };workspace.close();db.close();context.deleteDatabase(name);context.deleteSharedPreferences(prefs);root.deleteRecursively() } finally { graph.validationBusy.value=false;if(model!=null)installed.manager.select(model,originalProfile) } } }
    }
}
