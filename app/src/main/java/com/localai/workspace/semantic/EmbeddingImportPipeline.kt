package com.localai.workspace.semantic

import com.arm.aichat.EmbeddingNativeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.sqrt

enum class EmbeddingImportStage { SOURCE_OPEN, COPY_PRIVATE, HASH_VERIFY, NATIVE_LIBRARY_LOAD, GGUF_OPEN, MODEL_LOAD, ARCHITECTURE_VALIDATE, CONTEXT_CREATE, EMBEDDING_PROBE, DIMENSION_VALIDATE, CONFIG_COMMIT }
data class EmbeddingDiagnostics(val stage:EmbeddingImportStage, val status:String="RUNNING",val code:String?=null,
    val errorClass:String?=null,val safeMessage:String?=null,val bytes:Long?=null,val sha256:String?=null,
    val architecture:String?=null,val dimension:Int?=null,val nativeCode:String?=null,val nativeMessage:String?=null) {
    fun text()=listOfNotNull("EmbeddingGemma import: $status", "Stage: $stage",code?.let { "Code: $it" },
        "File: embeddinggemma-300M-Q8_0.gguf",bytes?.let { "Size: $it bytes" },sha256?.let { "SHA-256: $it" },
        architecture?.let { "Architecture: $it" },dimension?.let { "Dimension: $it" },errorClass?.let { "Error class: $it" },
        safeMessage,nativeCode?.let { "Native code: $it" },nativeMessage?.let { "Native error: $it" }).joinToString("\n")
}
class EmbeddingImportFailure(val diagnostics:EmbeddingDiagnostics):IllegalStateException("Embedding import failed · ${diagnostics.stage} · ${diagnostics.code}")
internal interface ImportEncoder:AutoCloseable { val dimension:Int;val architecture:String;fun embed(text:String):FloatArray }
internal class EmbeddingImportPipeline(private val expectedHash:String=EXPECTED_HASH) {
    companion object { const val EXPECTED_HASH="b5ce9d77a3fc4b3b39ccb5643c36777911cc4eb46a66962eadfa3f5f60490d63" }
    suspend fun run(file:File,sourceSize:Long?,open:()->InputStream?,load:(File)->ImportEncoder,
        commit:(File,String)->Unit,publish:(EmbeddingDiagnostics)->Unit):EmbeddingDiagnostics {
        var d=EmbeddingDiagnostics(EmbeddingImportStage.SOURCE_OPEN)
        fun stage(s:EmbeddingImportStage) { d=d.copy(stage=s);publish(d) }
        fun reject(code:String,message:String):Nothing = throw EmbeddingImportFailure(d.copy(status="FAILED",code=code,safeMessage=message))
        try {
            publish(d)
            val input=open() ?: reject("SOURCE_NOT_READABLE","Selected source could not be opened")
            val digest=MessageDigest.getInstance("SHA-256");var total=0L
            input.use { stream->
                stage(EmbeddingImportStage.COPY_PRIVATE)
                file.outputStream().use { output->
                    val buffer=ByteArray(65536)
                    while(true) { currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0)break;if(n==0)continue
                        total+=n;if(total>600L*1024*1024)reject("SIZE_LIMIT","Embedding model exceeds 600 MiB")
                        output.write(buffer,0,n);digest.update(buffer,0,n)
                    }
                    output.fd.sync()
                }
            }
            d=d.copy(bytes=total,sha256=digest.digest().joinToString(""){"%02x".format(it)})
            if(!file.isFile || !file.canRead() || file.length()!=total || (sourceSize!=null && sourceSize>=0 && sourceSize!=total))reject("COPY_SIZE_MISMATCH","Private copy size does not match the readable source")
            if(total<24) { stage(EmbeddingImportStage.GGUF_OPEN);reject("TRUNCATED_FILE","GGUF file is too short") }
            stage(EmbeddingImportStage.HASH_VERIFY)
            if(d.sha256!=expectedHash)reject("HASH_MISMATCH","Expected the pinned ggml-org EmbeddingGemma 300M Q8_0 SHA-256; native loading was not attempted")
            stage(EmbeddingImportStage.NATIVE_LIBRARY_LOAD)
            load(file).use { encoder->
                stage(EmbeddingImportStage.ARCHITECTURE_VALIDATE);d=d.copy(architecture=encoder.architecture)
                if(encoder.architecture!="gemma-embedding")reject("WRONG_ARCHITECTURE","Native metadata is not gemma-embedding")
                stage(EmbeddingImportStage.DIMENSION_VALIDATE);d=d.copy(dimension=encoder.dimension)
                if(encoder.dimension!=768)reject("WRONG_DIMENSION","Native output dimension is not 768")
                stage(EmbeddingImportStage.EMBEDDING_PROBE)
                val vector=encoder.embed("task: search result | query: prueba de embedding")
                if(vector.size!=encoder.dimension)reject("WRONG_DIMENSION","Probe output and native dimension differ")
                if(vector.any { !it.isFinite() })reject("NONFINITE_VECTOR","Probe contains NaN or infinity")
                val norm=sqrt(vector.sumOf { it.toDouble()*it })
                if(norm !in .99..1.01)reject("INVALID_NORM","Probe is not L2 normalized")
            }
            currentCoroutineContext().ensureActive();stage(EmbeddingImportStage.CONFIG_COMMIT)
            commit(file,checkNotNull(d.sha256))
            return d.copy(status="IMPORTED").also(publish)
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Throwable) {
            d=when(error) {
                is EmbeddingImportFailure -> error.diagnostics.copy(errorClass=error.diagnostics.errorClass ?: error.javaClass.simpleName)
                is EmbeddingNativeException -> d.copy(stage=runCatching { EmbeddingImportStage.valueOf(error.stage) }.getOrDefault(EmbeddingImportStage.MODEL_LOAD),status="FAILED",code=error.code,errorClass=error.javaClass.simpleName,nativeCode=error.code,nativeMessage=error.safeDetail,
                    architecture=if(error.safeDetail.contains("architecture=gemma-embedding"))"gemma-embedding" else d.architecture)
                is UnsatisfiedLinkError -> d.copy(stage=EmbeddingImportStage.NATIVE_LIBRARY_LOAD,status="FAILED",code="NATIVE_LIBRARY_UNAVAILABLE",errorClass=error.javaClass.simpleName,safeMessage="ARM64 native library or a dependency could not be linked")
                else -> d.copy(status="FAILED",code=when(d.stage){EmbeddingImportStage.SOURCE_OPEN->"SOURCE_OPEN_FAILED";EmbeddingImportStage.COPY_PRIVATE->"COPY_FAILED";EmbeddingImportStage.CONFIG_COMMIT->"CONFIG_COMMIT_FAILED";else->"IMPORT_STAGE_FAILED"},errorClass=error.javaClass.simpleName,safeMessage="Operation failed at ${d.stage}; private paths and raw exception text are withheld")
            }
            publish(d);throw EmbeddingImportFailure(d)
        }
    }
}
