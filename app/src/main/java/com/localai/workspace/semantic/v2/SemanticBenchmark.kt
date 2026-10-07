package com.localai.workspace.semantic.v2

import com.localai.workspace.performance.DeviceMeasurement
import kotlinx.coroutines.*
import kotlin.math.ceil

/** No private prompts/documents in this journal. Samples use fixed public fixtures only. */
data class SemanticBenchmarkProgress(
    val benchmarkRunId:String, val benchmarkStartedAt:Long, val processId:Int,
    val status:String="RUNNING", val currentPhase:String="INITIALIZE", val currentDimension:Int=768,
    val iterations:Int=5, val currentIteration:Int=0, val lastCompletedIteration:Int=0,
    val requestedBackend:String="CPU", val engineState:String="UNLOADED", val nativeInFlight:Boolean=false,
    val lastNativeOperation:EmbeddingNativeOperation?=null, val currentMs:Long?=null,
    val warmSamplesMs:List<Long> = emptyList(), val measurements:DeviceMeasurement?=null,
    val records:List<DimensionBenchmarkResult> = emptyList(), val failureKind:String?=null,
    val completedAt:Long?=null, val errorClass:String?=null, val exitEvidence:ProcessExitEvidence?=null,
    val recommendation:String="NO_AUTOMATIC_RECOMMENDATION",
)
data class ProcessExitEvidence(val reason:Int, val status:Int, val timestamp:Long, val descriptionAvailable:Boolean=false)
data class DimensionBenchmarkResult(
    val dimension:Int, val loadMs:Long, val firstEmbeddingMs:Long, val warmupMs:Long,
    val warmSamplesMs:List<Long>, val p50Ms:Long, val p95Ms:Long, val meanMs:Double,
    val minMs:Long, val maxMs:Long, val throughput:Double?, val appPssBefore:Long?,
    val appPssPeakObserved:Long?, val appPssAfter:Long?, val availableRamBefore:Long?,
    val availableRamAfter:Long?, val thermalBefore:String?, val thermalAfter:String?,
    val vectorBytes:Int, val quality:Map<String,Double>, val backendRequested:String,
    val backendEffective:String?, val backendEvidence:String,
    val modelHash:String, val fixtureVersion:String="text-comparison-v1",
    val latencyMeasurement:String="SDK_COMPUTE_CALL_MONOTONIC_EXCLUDING_JOURNAL_IO",
    val peakObservation:String="BEFORE_AFTER_AND_EACH_ITERATION_NOT_CONTINUOUS_PEAK",
)
interface SemanticBenchmarkPort {
    val modelHash:String
    fun state():ProviderStatus
    fun capture():DeviceMeasurement
    suspend fun prepare(dimension:Int):Long
    suspend fun embed(input:SemanticInput,task:EmbeddingTask,dimension:Int):EmbeddingResult
    suspend fun unload()
}
/** Identical corpus and queries for both dimensions. No observed score or rank is hardcoded. */
object SemanticBenchmarkQuality {
    val documents=listOf(
        SemanticInput.Text("Para restablecer la clave de acceso, solicita un enlace de recuperación.","Account recovery ES"),
        SemanticInput.Text("Request a recovery link to restore account access.","Account recovery EN"),
        SemanticInput.Text("La factura debe abonarse antes del día diez de cada mes.","Billing"),
        SemanticInput.Text("Rain is forecast tomorrow and a blue car is parked outside.","Weather"),
    )
    val queries=listOf("¿Cómo puedo recuperar mi contraseña?","How can I reset my password?")
    val relevant=listOf(setOf("0","1"),setOf("0","1"))
    suspend fun evaluate(port:SemanticBenchmarkPort,dim:Int):Map<String,Double> {
        val vectors=documents.map { currentCoroutineContext().ensureActive();port.embed(it,EmbeddingTask.DOCUMENT,dim) }
        val ranks=queries.map { query->currentCoroutineContext().ensureActive();val vector=port.embed(SemanticInput.Text(query),EmbeddingTask.SEARCH,dim);vectors.indices.sortedByDescending{SemanticVectors.cosine(vector,vectors[it])}.map(Int::toString) }
        return RetrievalQuality.metrics(ranks,relevant)
    }
}
/** All inference is sequential. Caller owns one exclusive engine session and restoration. */
class SemanticBenchmarkRunner(private val port:SemanticBenchmarkPort,private val checkpoint:(SemanticBenchmarkProgress)->Unit) {
    suspend fun run(initial:SemanticBenchmarkProgress,dimensions:List<Int>):SemanticBenchmarkProgress {
        require(initial.iterations in 1..20 && dimensions.isNotEmpty() && dimensions.all{it in setOf(768,256)})
        var progress=initial
        fun publish(){progress=progress.copy(engineState=port.state().state.name,measurements=port.capture());checkpoint(progress)}
        for(dim in dimensions) {
            currentCoroutineContext().ensureActive()
            progress=progress.copy(currentDimension=dim,currentPhase="INITIALIZE",currentIteration=0,lastCompletedIteration=0,warmSamplesMs=emptyList(),currentMs=null);publish()
            val before=port.capture();var peak=before.pssBytes
            fun observe(){port.capture().pssBytes?.let{peak=maxOf(peak ?: 0,it)}}
            val load=port.prepare(dim);observe()
            progress=progress.copy(currentPhase="FIRST_EMBEDDING");publish()
            val input=SemanticInput.Text("How can I recover account access?")
            val first=port.embed(input,EmbeddingTask.SEARCH,dim);observe()
            progress=progress.copy(currentPhase="WARMUP");publish()
            val warmup=port.embed(input,EmbeddingTask.SEARCH,dim);observe()
            val times=mutableListOf<Long>()
            repeat(initial.iterations) { i ->
                currentCoroutineContext().ensureActive();progress=progress.copy(currentPhase="MEASURE",currentIteration=i+1);publish()
                val result=port.embed(input,EmbeddingTask.SEARCH,dim);times+=result.durationMs;observe()
                progress=progress.copy(lastCompletedIteration=i+1,currentMs=result.durationMs,warmSamplesMs=times.toList());publish();yield()
            }
            progress=progress.copy(currentPhase="QUALITY");publish()
            val quality=SemanticBenchmarkQuality.evaluate(port,dim);observe();val after=port.capture();val state=port.state();val sorted=times.sorted()
            val record=DimensionBenchmarkResult(dim,load,first.durationMs,warmup.durationMs,times.toList(),sorted[ceil(times.size*.5).toInt()-1],sorted[ceil(times.size*.95).toInt()-1],times.average(),sorted.first(),sorted.last(),if(times.sum()>0)times.size*1000.0/times.sum()else null,before.pssBytes,peak,after.pssBytes,before.availableRamBytes,after.availableRamBytes,before.thermal,after.thermal,SemanticVectors.encode(first.vector).size,quality,initial.requestedBackend,state.effective?.name,state.backendEvidence,port.modelHash)
            progress=progress.copy(currentPhase="FINALIZE",records=progress.records+record);publish();port.unload();publish()
        }
        return progress
    }
}
