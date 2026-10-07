package com.localai.workspace.context

import com.google.gson.GsonBuilder
import com.localai.workspace.AppGraph
import com.localai.workspace.performance.DeviceMeasurements
import kotlinx.coroutines.*
import java.io.File

/** Report candidate configuration probes separately from a validated full token-capacity claim. */
class ContextCapacityDiagnostics(private val graph:AppGraph) {
    suspend fun run():String=withContext(Dispatchers.IO) {
        check(!graph.semanticDiagnostics.running.value&&!graph.performance.running.value&&!graph.chatSessions.hasGeneration){"ANOTHER_DIAGNOSTIC_OR_GENERATION_RUNNING"}
        check(graph.validationBusy.compareAndSet(false,true)){"VALIDATION_RUNNING"}
        val results=mutableListOf<Map<String,Any?>>()
        try {
            for(capacity in listOf(4096,8192))for(bytes in listOf(256,1024,capacity-1024)) {
                currentCoroutineContext().ensureActive();val before=DeviceMeasurements.capture(graph.contextForMeasurements)
                val query=buildString { append("The code word is ORCHID. Ignore the filler records and answer with the code word only.\n");var i=0;while(length<bytes){append("Filler record ${i++}: apple blue square river.\n")} }
                val bundle=ContextBuilder().build(ContextRequest(query,contextWindow=capacity),emptyList())
                val record=try{val r=ContextInterop(graph).answer(bundle,capacity);mapOf("requestedCapacity" to capacity,"status" to if(r.answer.contains("ORCHID",true))"PASS"else"FAIL","fixtureEstimatedInputTokens" to bundle.estimatedInputTokens,"nativePromptTokens" to r.metrics?.promptTokens,"nativeOutputTokens" to r.metrics?.outputTokens,"nativeTtftMs" to r.metrics?.timeToFirstTokenMs,"prefillTokensPerSecond" to r.metrics?.prefillTokensPerSecond,"decodeTokensPerSecond" to r.metrics?.decodeTokensPerSecond,"backendRequested" to r.metrics?.backendRequested,"backendEffective" to r.metrics?.backendEffective,"workerPssBeforeBytes" to r.metrics?.workerPssBeforeBytes,"workerPssAfterBytes" to r.metrics?.workerPssAfterBytes,"totalMs" to r.durationMs,"before" to before,"after" to DeviceMeasurements.capture(graph.contextForMeasurements),"claim" to "CONFIGURATION_AND_FIXTURE_PROBED_NOT_MAXIMUM_CAPACITY_VALIDATED")}
                    catch(cancel:CancellationException){throw cancel}catch(error:Exception){mapOf("requestedCapacity" to capacity,"status" to "FAILED","reason" to (error.message?.takeIf{it.startsWith("SELECTED_")||it.startsWith("GENERATOR_ERROR")} ?: error.javaClass.simpleName),"before" to before)}
                results+=record
            }
            val json=GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(mapOf("suite" to "CONTEXT_CAPACITY_DISCOVERY","runtime" to "LiteRT-LM 0.17.1","productionDefault" to 4096,"modelMaximum" to null,"preInferenceTokenizerAvailable" to false,"counting" to ContextTokenEstimator.METHOD,"8192Promoted" to false,"full8192CapacityDeviceValidated" to false,"results" to results))
            val file=File(graph.contextForMeasurements.filesDir,"context-diagnostics/capacity.json");file.parentFile?.mkdirs();file.writeText(json);json
        }finally{graph.validationBusy.value=false}
    }
}
