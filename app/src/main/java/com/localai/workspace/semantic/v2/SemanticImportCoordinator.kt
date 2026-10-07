package com.localai.workspace.semantic.v2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.localai.workspace.semantic.EmbeddingModels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class SemanticImportAttempt(val provider:String,val originalSelectedFilename:String?,val detectedProvider:String?,val detectedSize:Long?,val sha256:String?=null,val failureStage:String="TYPE_DETECTION",val status:String="RUNNING",val reasonCode:String?=null,val timestamp:Long=System.currentTimeMillis())
class SemanticImportCoordinator(private val context:Context,private val legacy:EmbeddingModels,private val layer:()->SemanticLayer) {
    private val gson=GsonBuilder().serializeNulls().setPrettyPrinting().create()
    val attempts=MutableStateFlow<Map<String,SemanticImportAttempt>>(emptyMap())
    private fun file(provider:String)=AtomicFile(File(context.filesDir,"semantic-v2-reports/import-$provider.json"))
    suspend fun restore()=withContext(Dispatchers.IO) { attempts.value=listOf("EG1","EG2").mapNotNull { p->runCatching { file(p).openRead().bufferedReader().use{gson.fromJson(it,SemanticImportAttempt::class.java)} }.getOrNull()?.let{p to it} }.toMap() }
    private fun publish(value:SemanticImportAttempt) { val output=file(value.provider);output.baseFile.parentFile?.mkdirs();val stream=output.startWrite();try{stream.write(gson.toJson(value).toByteArray());output.finishWrite(stream)}catch(error:Throwable){output.failWrite(stream);throw error};attempts.value=attempts.value+(value.provider to value) }
    suspend fun import(uri:Uri,expected:SemanticProviderChoice?=null)=withContext(Dispatchers.IO) {
        val columns=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { c->if(c.moveToFirst())c.getString(0) to if(c.isNull(1))null else c.getLong(1) else null }
        val header=try{context.contentResolver.openInputStream(uri)?.use { input->val b=ByteArray(8);var n=0;while(n<8){val count=input.read(b,n,8-n);if(count<=0)break;n+=count};b.copyOf(n) } ?: byteArrayOf()}catch(error:Throwable){publish(SemanticImportAttempt(expected?.name ?: "UNKNOWN",columns?.first,null,columns?.second,failureStage="SOURCE_OPEN",status="FAILED",reasonCode=error.javaClass.simpleName));throw error}
        val detected=detect(header)
        var attempt=SemanticImportAttempt((expected ?: detected)?.name ?: "UNKNOWN",columns?.first,detected?.name,columns?.second)
        publish(attempt)
        try {
            if(detected==null || expected!=null && detected!=expected)throw SemanticFailure(SemanticError.MODEL_METADATA_UNSUPPORTED)
            attempt=attempt.copy(provider=detected.name,failureStage="COPY_PRIVATE");publish(attempt)
            when(detected) {
                SemanticProviderChoice.EG1 -> { legacy.import(uri);val d=legacy.diagnostics.value;attempt=attempt.copy(sha256=d?.sha256,detectedSize=d?.bytes ?: attempt.detectedSize,failureStage=d?.stage?.name ?: "CONFIG_COMMIT");layer().chooseProvider(SemanticProviderChoice.EG1) }
                SemanticProviderChoice.EG2 -> { val record=layer().import(uri) { stage,size,hash->attempt=attempt.copy(failureStage=stage,detectedSize=size ?: attempt.detectedSize,sha256=hash ?: attempt.sha256);publish(attempt) };attempt=attempt.copy(sha256=record.sha256,detectedSize=record.fileSize) }
            }
            publish(attempt.copy(status="SUCCESS",failureStage="COMPLETE"))
        } catch(cancel:CancellationException){publish(attempt.copy(status="CANCELLED",reasonCode="USER_CANCELLED"));throw cancel}
        catch(error:Throwable){ val legacyFailure=error as? com.localai.workspace.semantic.EmbeddingImportFailure
            publish(attempt.copy(status="FAILED",reasonCode=(error as? SemanticFailure)?.code?.name ?: legacyFailure?.diagnostics?.code ?: error.javaClass.simpleName,failureStage=legacyFailure?.diagnostics?.stage?.name ?: attempt.failureStage,sha256=legacyFailure?.diagnostics?.sha256 ?: attempt.sha256,detectedSize=legacyFailure?.diagnostics?.bytes ?: attempt.detectedSize));throw error }
    }
    companion object { fun detect(header:ByteArray):SemanticProviderChoice?=when { header.size>=8 && String(header,0,8,Charsets.US_ASCII)=="LITERTLM"->SemanticProviderChoice.EG2;header.size>=4 && String(header,0,4,Charsets.US_ASCII)=="GGUF"->SemanticProviderChoice.EG1;else->null } }
}
