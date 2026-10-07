package com.localai.workspace.diagnostics

import android.os.SystemClock
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Metadata only: never accepts a prompt, answer, file path or exception message. */
data class GenerationCheckpoint(val phase:String,val elapsedRealtimeMs:Long)
data class NormalGenerationSnapshot(
 val runId:String,val conversationId:String?,val projectPresent:Boolean,
 val startedAt:Long,val startedElapsedMs:Long,val phase:String="REQUEST_CREATED",
 val completedAt:Long?=null,val firstCallbackMs:Long?=null,val firstVisibleUiMs:Long?=null,
 val cancelled:Boolean=false,val errorClass:String?=null,val nativeInFlight:Boolean?=null,
 val gateLocked:Boolean=false,val gateOwned:Boolean=false,
 val contextBuilderEnabled:Boolean=false,val thinkingRequested:String?=null,val thinkingEffective:Boolean?=null,
 val toolsRequested:Boolean?=null,val toolsEffectiveCount:Int?=null,
 val modelState:String?=null,val engineReused:Boolean?=null,val sessionReused:Boolean?=null,
 val backendRequested:String?=null,val backendEffective:String?=null,val workerCheckpoint:String?=null,
 val processRestartObserved:Boolean=false,val checkpointBoundary:String="APP_PIPELINE_AND_EXISTING_WORKER_PROGRESS",
 val events:List<GenerationCheckpoint> = emptyList(),val persistenceErrorClass:String?=null,
)
class NormalGenerationTrace(private val file:File,private val clock:()->Long={SystemClock.elapsedRealtime()},private val wall:()->Long={System.currentTimeMillis()}) {
 private val gson=GsonBuilder().serializeNulls().setPrettyPrinting().create()
 private val guard=Any()
 private val mutable=MutableStateFlow<NormalGenerationSnapshot?>(null)
 val state=mutable.asStateFlow()
 init { synchronized(guard) {
  if(file.exists())try {
   val prior=AtomicFile(file).openRead().bufferedReader().use{gson.fromJson(it,NormalGenerationSnapshot::class.java)}
   if(prior!=null)mutable.value=if(prior.completedAt==null)prior.copy(processRestartObserved=true,nativeInFlight=null,gateLocked=false,gateOwned=false)else prior
  }catch(error:Exception){android.util.Log.w("LocalAI/ChatTrace","read_failed type=${error.javaClass.simpleName}")}
 } }
 fun begin(conversationId:String?,projectPresent:Boolean):String=synchronized(guard){
  val id=UUID.randomUUID().toString();val now=clock()
  mutable.value=NormalGenerationSnapshot(id,conversationId,projectPresent,wall(),now,events=listOf(GenerationCheckpoint("REQUEST_CREATED",now)));id
 }
 /** Suspend IO for ordered, durable markers. A journal failure never blocks inference. */
 suspend fun mark(id:String,phase:String,update:(NormalGenerationSnapshot)->NormalGenerationSnapshot={it})=withContext(Dispatchers.IO){synchronized(guard){
  val old=mutable.value?.takeIf{it.runId==id} ?: return@synchronized
  val now=clock();val next=update(old).copy(phase=phase,events=(old.events+GenerationCheckpoint(phase,now)).takeLast(64))
  mutable.value=next
  var stream:java.io.FileOutputStream?=null
  try{file.parentFile?.mkdirs();val atomic=AtomicFile(file);stream=atomic.startWrite();stream.write(gson.toJson(next).toByteArray());atomic.finishWrite(stream)}
  catch(error:Exception){stream?.let{runCatching{AtomicFile(file).failWrite(it)}};mutable.value=next.copy(persistenceErrorClass=error.javaClass.simpleName);android.util.Log.w("LocalAI/ChatTrace","write_failed type=${error.javaClass.simpleName}")}
 }}
 fun report():String=synchronized(guard){gson.toJson(mutable.value)}
 fun terminalTime():Long=wall()
}
