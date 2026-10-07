package com.localai.workspace.semantic.v2

import android.content.Context
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

interface EmbeddingNativeDriver : AutoCloseable {
    fun initialize()
    fun isInitialized(): Boolean
    fun compute(contents: List<InputData>, options: EmbeddingOptions): FloatArray
}
private class OfficialEmbeddingDriver(config: EmbeddingEngineConfig) : EmbeddingNativeDriver {
    private val sdk = EmbeddingEngine(config)
    override fun initialize() = sdk.initialize()
    override fun isInitialized() = sdk.isInitialized()
    override fun compute(contents: List<InputData>, options: EmbeddingOptions) = sdk.computeEmbedding(contents, options).embedding
    override fun close() = sdk.close()
}
data class EmbeddingNativeOperation(val operation: String, val dimension: Int, val configuredBackend: SemanticBackend, val modelHash: String, val modality: String, val visionRequested: Boolean, val audioRequested: Boolean, val timestamp: Long = System.currentTimeMillis(), val completed: Boolean = false, val errorClass: String? = null)
private class EmbeddingSession(val owner: EmbeddingEngineManager) : AbstractCoroutineContextElement(Key) { companion object Key : CoroutineContext.Key<EmbeddingSession> }

/** Lock order: session -> shared resource gate -> native lifecycle. No close/compute overlap. */
class EmbeddingEngineManager(private val context: Context, private val resourceGate: Mutex, private val factory: (EmbeddingEngineConfig) -> EmbeddingNativeDriver = ::OfficialEmbeddingDriver) : EmbeddingProviderV2 {
    private val session = Mutex()
    private val lock = Mutex()
    private var engineGeneration = 0L
    private var engine: EmbeddingNativeDriver? = null
    private var loadedKey: String? = null
    private var loadedDimension = 768
    private var loadedBackend = SemanticBackend.CPU
    private var visionLoaded = false
    private var audioLoaded = false
    private var failedGpuModel: String? = null
    @Volatile var selected: SemanticModelRecord? = null; private set
    @Volatile var profile = EmbeddingRuntimeProfile(); private set
    @Volatile var configuredDimension = 768; private set
    /** Installed only inside an exclusive diagnostic session. Called on IO before/after JNI. */
    var nativeObserver: ((EmbeddingNativeOperation) -> Unit)? = null
    val status = MutableStateFlow(ProviderStatus())
    suspend fun <T> exclusive(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        if(currentCoroutineContext()[EmbeddingSession]?.owner===this@EmbeddingEngineManager)block()
        else session.withLock { withContext(EmbeddingSession(this@EmbeddingEngineManager)) { block() } }
    }
    private suspend fun <T> serialized(block: suspend () -> T): T = exclusive { resourceGate.withLock { lock.withLock { block() } } }
    suspend fun select(model: SemanticModelRecord, configuration: EmbeddingRuntimeProfile = EmbeddingRuntimeProfile()) = serialized {
        closeLocked(); selected = model; profile = configuration; visionLoaded = false; audioLoaded = false
        status.value = ProviderStatus(requested = configuration.backend, modelId = model.id, outputDimension = configuredDimension)
    }
    suspend fun configureDimension(dimension: Int) = serialized {
        require(dimension in setOf(768,256))
        if(engine!=null && loadedDimension!=dimension)closeLocked()
        configuredDimension=dimension;status.value=status.value.copy(outputDimension=dimension)
    }
    private fun stamp(operation:String, dimension:Int=loadedDimension, backend:SemanticBackend=loadedBackend, vision:Boolean=visionLoaded, audio:Boolean=audioLoaded, modality:String="TEXT") = EmbeddingNativeOperation(operation,dimension,backend,selected?.sha256.orEmpty(),modality,vision,audio)
    private inline fun <T> nativeCall(stamp:EmbeddingNativeOperation, block:()->T):T {
        nativeObserver?.invoke(stamp)
        try { val result=block();nativeObserver?.invoke(stamp.copy(completed=true,timestamp=System.currentTimeMillis()));return result }
        catch(error:Throwable) { try{nativeObserver?.invoke(stamp.copy(completed=true,errorClass=error.javaClass.simpleName,timestamp=System.currentTimeMillis()))}catch(reporting:Throwable){error.addSuppressed(reporting)};throw error }
    }
    private fun closeLocked() {
        val closing=engine
        status.value=status.value.copy(state=ProviderState.UNLOADING)
        // Never retain a questionable handle after a failed close.
        engine=null;loadedKey=null
        try { if(closing?.isInitialized()==true) {
            var nativeStarted=false
            try { nativeCall(stamp("CLOSE")){nativeStarted=true;closing.close()} }
            catch(error:Throwable) { if(!nativeStarted)try{closing.close()}catch(cleanup:Throwable){error.addSuppressed(cleanup)};throw error }
        } }
        finally { status.value=status.value.copy(state=ProviderState.UNLOADED) }
    }
    suspend fun deselect() = serialized { closeLocked();selected=null;visionLoaded=false;audioLoaded=false;status.value=ProviderStatus(outputDimension=configuredDimension) }
    override suspend fun unload() = serialized { closeLocked();visionLoaded=false;audioLoaded=false }
    suspend fun initialize(outputDimension:Int=configuredDimension):Long = serialized {
        require(outputDimension in setOf(768,256));ensureEngine(outputDimension,profile.modalities!=RuntimeProfile.TEXT_ONLY,profile.modalities==RuntimeProfile.FULL_MULTIMODAL)
        status.value.loadMs ?: 0L
    }
    private fun ensureEngine(dimension:Int, needVision:Boolean, needAudio:Boolean) {
        val model=selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        val key="${model.sha256}:${profile.backend}:$needVision:$needAudio:$dimension"
        if(engine!=null && loadedKey==key)return
        closeLocked();status.value=ProviderStatus(ProviderState.LOADING,profile.backend,modelId=model.id,outputDimension=dimension)
        var effective=profile.backend;var fallback:String?=null;var initializationNanos=0L
        fun create(backend:SemanticBackend):EmbeddingNativeDriver {
            val candidate=factory(EmbeddingEngineConfig(modelPath=model.privatePath,backend=if(backend==SemanticBackend.CPU)Backend.CPU()else Backend.GPU(),visionBackend=if(needVision)Backend.CPU()else null,audioBackend=if(needAudio)Backend.CPU()else null,cacheDir=File(context.cacheDir,"semantic-v2/${model.sha256.take(16)}/${backend.name}").apply{mkdirs()}.path))
            try {
                nativeCall(stamp("INITIALIZE",dimension,backend,needVision,needAudio)) { val start=System.nanoTime();try{candidate.initialize()}finally{initializationNanos+=System.nanoTime()-start} }
                check(candidate.isInitialized()) { "Embedding initialization returned without a handle" }
                return candidate
            } catch(error:Throwable) {
                // The old .also(initialize) path lost this object when initialization threw.
                if(candidate.isInitialized()) {
                    var nativeStarted=false
                    try { nativeCall(stamp("FAILED_INITIALIZATION_CLEANUP",dimension,backend,needVision,needAudio)){nativeStarted=true;candidate.close()} }
                    catch(cleanup:Throwable){if(!nativeStarted)try{candidate.close()}catch(closeError:Throwable){cleanup.addSuppressed(closeError)};error.addSuppressed(cleanup)}
                }
                throw error
            }
        }
        try {
            if(profile.backend==SemanticBackend.GPU_EXPERIMENTAL && failedGpuModel!=model.sha256) {
                try{engine=create(profile.backend)}catch(cancel:CancellationException){throw cancel}catch(error:Exception){failedGpuModel=model.sha256;effective=SemanticBackend.CPU;fallback="GPU_INITIALIZATION_FAILED:${error.javaClass.simpleName}";engine=create(effective)}
            } else { effective=SemanticBackend.CPU;if(profile.backend!=effective)fallback="GPU_PREVIOUS_FAILURE";engine=create(effective) }
            loadedKey=key;loadedDimension=dimension;loadedBackend=effective;visionLoaded=needVision;audioLoaded=needAudio
            status.value=ProviderStatus(ProviderState.READY,profile.backend,if(effective==SemanticBackend.CPU)effective else null,fallback,initializationNanos/1_000_000,engineGeneration=++engineGeneration,backendEvidence=if(effective==SemanticBackend.CPU)"CPU_EXPLICIT_SDK_CONFIGURATION"else"GPU_REQUEST_ACCEPTED_EXECUTION_UNVERIFIED",modelId=model.id,outputDimension=dimension)
        } catch(error:Throwable) { engine=null;loadedKey=null;status.value=status.value.copy(state=ProviderState.ERROR,error=if(error is OutOfMemoryError)"OUT_OF_MEMORY"else"MODEL_LOAD_FAILED");throw error }
    }
    override suspend fun embed(input: SemanticInput, task: EmbeddingTask, outputDimension: Int): EmbeddingResult = serialized {
        val model=selected ?: throw SemanticFailure(SemanticError.MODEL_FILE_INVALID)
        if(outputDimension !in setOf(768,256))throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
        val modality=when(input){is SemanticInput.Text->SemanticModality.TEXT;is SemanticInput.Code->SemanticModality.CODE;is SemanticInput.Image->SemanticModality.IMAGE;is SemanticInput.Audio->SemanticModality.AUDIO;is SemanticInput.VideoSegment->SemanticModality.VIDEO}
        if(modality.name !in model.declaredModalities.split(','))throw SemanticFailure(SemanticError.MODALITY_UNSUPPORTED)
        if(input is SemanticInput.VideoSegment)throw SemanticFailure(SemanticError.VIDEO_PIPELINE_UNSUPPORTED)
        try {
            ensureEngine(outputDimension,visionLoaded || input is SemanticInput.Image || profile.modalities!=RuntimeProfile.TEXT_ONLY,audioLoaded || input is SemanticInput.Audio || profile.modalities==RuntimeProfile.FULL_MULTIMODAL)
            currentCoroutineContext().ensureActive();status.value=status.value.copy(state=ProviderState.BUSY)
            val contents=when(input){is SemanticInput.Text->listOf(InputData.Text(TaskPromptProfile.format(task,input.content,input.title)));is SemanticInput.Code->listOf(InputData.Text(TaskPromptProfile.format(task,input.content,input.filename)));is SemanticInput.Image->listOf(InputData.Image(privateBytes(input.privateReference,12*1024*1024)));is SemanticInput.Audio->listOf(InputData.Audio(privateBytes(input.privateReference,32*1024*1024)));else->throw SemanticFailure(SemanticError.VIDEO_PIPELINE_UNSUPPORTED)}
            var nanos=0L
            val raw=nativeCall(stamp("COMPUTE",modality=modality.name)) { val start=System.nanoTime();try{engine!!.compute(contents,EmbeddingOptions(normalize=true,outputSize=outputDimension))}finally{nanos=System.nanoTime()-start} }
            currentCoroutineContext().ensureActive();if(raw.size!=outputDimension)throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
            val vector=SemanticVectors.normalize(raw);val elapsed=nanos/1_000_000
            status.value=status.value.copy(state=ProviderState.READY,lastEmbeddingMs=elapsed,error=null)
            EmbeddingResult(space(model,outputDimension),vector,elapsed)
        } catch(cancel:CancellationException){closeLocked();throw cancel}
        catch(error:Throwable){if(profile.backend==SemanticBackend.GPU_EXPERIMENTAL && status.value.fallbackReason==null && (error !is SemanticFailure || error.code in setOf(SemanticError.NON_FINITE_VECTOR,SemanticError.NORMALIZATION_FAILED)))failedGpuModel=model.sha256
            try{closeLocked()}catch(cleanup:Throwable){error.addSuppressed(cleanup)}
            val code=when(error){is SemanticFailure->error.code;is OutOfMemoryError->SemanticError.OUT_OF_MEMORY;else->SemanticError.MODEL_LOAD_FAILED};status.value=status.value.copy(state=ProviderState.ERROR,error=code.name);throw if(error is SemanticFailure)error else SemanticFailure(code,error)
        }
    }
    private fun privateBytes(reference:String,maximum:Int):ByteArray { val file=File(reference).canonicalFile;val root=context.filesDir.canonicalFile;if(!file.path.startsWith(root.path+File.separator)||!file.isFile||file.length() !in 1..maximum.toLong())throw SemanticFailure(SemanticError.MODEL_FILE_INVALID);return file.readBytes() }
    companion object { fun space(model: SemanticModelRecord, dimension: Int) = EmbeddingSpaceKey(model.provider, model.family, model.sha256, model.modelVersion, dimension) }
}
