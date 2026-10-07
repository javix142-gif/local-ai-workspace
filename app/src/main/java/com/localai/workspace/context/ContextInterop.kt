package com.localai.workspace.context

import com.localai.workspace.AppGraph
import com.localai.workspace.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ContextNativeResult(val answer:String,val metrics:RuntimeMetrics?,val durationMs:Long)
/** Explicit diagnostic action only. No Room chat mutations and no prompt/content logging. */
class ContextInterop(private val graph:AppGraph) {
    suspend fun answer(bundle:ContextBundle,capacity:Int=bundle.request.contextWindow):ContextNativeResult=withTimeout(180_000){
        check(bundle.included.none{item->item.history.any{it.imagePath!=null||it.audioPath!=null}}){"MULTIMODAL_CONTEXT_BUDGET_UNAVAILABLE_USE_LEGACY_CHAT"}
        val selected=graph.modelPreparation.state.value.modelId
        val model=graph.workspace.allModels.first().firstOrNull{it.id==selected&&it.toDescriptor().runtime==RuntimeType.LITERT_LM} ?: error("SELECTED_GENERATOR_UNAVAILABLE")
        check(com.localai.workspace.inference.LiteRtBundleMetadata.read(java.io.File(model.localPath)).modelProcessor==8){"SELECT_GEMMA4_FOR_INTEROP"}
        val descriptor=model.toDescriptor();val conversation=bundle.conversation().copy(conversationId="context-diagnostic-${UUID.randomUUID()}",enableThinking=false)
        graph.modelPreparation.pauseForBenchmark()
        try{graph.inferenceGate.withLock{
            val lease=graph.chatRuntimes.lease(graph.runtimes.runtimeFor(descriptor));val start=android.os.SystemClock.elapsedRealtime()
            try{
                val config=ModelLoadConfig(ModelSource(descriptor.localPath,descriptor.displayName,model.sourceUri,descriptor.format,expectedSizeBytes=model.fileSize,sha256=model.fileHash),contextSize=capacity,maxOutputTokens=bundle.request.reservedOutput,temperature=model.temperature,topP=model.topP,topK=model.topK,repeatPenalty=model.repeatPenalty,seed=model.seed,conversation=conversation,warmupEnabled=true)
                lease.load(config);val text=StringBuilder();var metrics:RuntimeMetrics?=null
                lease.generate(GenerationRequest(conversation.userMessage,config.maxOutputTokens,capacity,config.temperature,config.topP,config.topK,config.repeatPenalty,config.seed,conversation=conversation)).collect{event->when(event){is GenerationEvent.Token->text.append(event.text);is GenerationEvent.Metrics->metrics=event.metrics;is GenerationEvent.Error->error("GENERATOR_ERROR:${event.error.code}");GenerationEvent.Cancelled->throw CancellationException();else->Unit}}
                ContextNativeResult(text.toString(),metrics,android.os.SystemClock.elapsedRealtime()-start)
            }finally{withContext(NonCancellable){lease.close().join()}}
        }}finally{graph.modelPreparation.resumeAfterBenchmark()}
    }
}
