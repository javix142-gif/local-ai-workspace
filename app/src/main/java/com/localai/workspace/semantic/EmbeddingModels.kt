package com.localai.workspace.semantic

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arm.aichat.EmbeddingGemma
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

interface NeuralEmbeddings : AutoCloseable { val modelId: String;fun embed(text: String): FloatArray }
interface EmbeddingProvider { suspend fun <T> use(block: suspend (NeuralEmbeddings)->T): T? }
class EmbeddingModels(private val context: Context) : EmbeddingProvider {
 private val prefs=context.getSharedPreferences("local_embedding_model",Context.MODE_PRIVATE)
 private val gate=Mutex()
 private val gson=Gson()
 val diagnostics=MutableStateFlow(runCatching { prefs.getString("diagnostics",null)?.let { gson.fromJson(it,EmbeddingDiagnostics::class.java) } }.getOrNull())
 val status=MutableStateFlow(if(prefs.contains("path")) "EmbeddingGemma configured" else "Embedding model unavailable · lexical fallback")
 val id: String? get()=prefs.getString("hash",null)?.let { "embeddinggemma-gguf:$it:v1" }
 suspend fun import(uri: Uri)=withContext(Dispatchers.IO) { gate.withLock {
  val dir=File(context.filesDir,"embedding-models").apply { mkdirs() };val file=File(dir,"import-${java.util.UUID.randomUUID()}.gguf")
  val old=prefs.getString("path",null);val oldHash=prefs.getString("hash",null)
  try {
   val sourceSize=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { c->if(c.moveToFirst() && !c.isNull(0))c.getLong(0) else null } }.getOrNull()
   EmbeddingImportPipeline().run(file,sourceSize,{context.contentResolver.openInputStream(uri)}, { privateFile->
    val encoder=EmbeddingGemma(privateFile.path,context.applicationInfo.nativeLibraryDir)
    object:ImportEncoder { override val dimension get()=encoder.dimension;override val architecture get()=encoder.architecture;override fun embed(text:String)=encoder.embed(text);override fun close()=encoder.close() }
   }, { privateFile,hash->
    if(!prefs.edit().putString("path",privateFile.path).putString("hash",hash).commit()) {
     val rollback=prefs.edit();if(old==null)rollback.remove("path") else rollback.putString("path",old)
     if(oldHash==null)rollback.remove("hash") else rollback.putString("hash",oldHash)
     rollback.commit();error("Config commit failed")
    }
   }, { d->diagnostics.value=d;status.value=if(d.status=="FAILED") "Embedding import failed · ${d.stage} · ${d.code}" else "Embedding import · ${d.stage}" })
   runCatching { old?.let { p->if(File(p).canonicalFile.parentFile==dir.canonicalFile && p!=file.path)File(p).delete() } } // Cleanup failure must not roll back a committed import.
   status.value="EmbeddingGemma · 768 dimensions · CPU"
  } catch(cancel: kotlinx.coroutines.CancellationException){file.delete();diagnostics.value=diagnostics.value?.copy(status="CANCELLED",code="CANCELLED");throw cancel}
  catch(error:Throwable){file.delete();throw error}
  finally { diagnostics.value?.let { prefs.edit().putString("diagnostics",gson.toJson(it)).apply() } }
 } }
 override suspend fun <T> use(block: suspend (NeuralEmbeddings)->T): T? = withContext(Dispatchers.IO) { gate.withLock {
  val path=prefs.getString("path",null)?:return@withLock null;val modelId=id?:return@withLock null
  val encoder=EmbeddingGemma(path,context.applicationInfo.nativeLibraryDir)
  try { block(object:NeuralEmbeddings { override val modelId=modelId;override fun embed(text:String)=encoder.embed(text);override fun close()=Unit }) }
  finally { encoder.close() }
 } }
}
