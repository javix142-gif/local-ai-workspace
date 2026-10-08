package com.localai.workspace.semantic

import com.localai.workspace.data.*
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.domain.model.SourceTrust
import com.localai.workspace.domain.rag.ReciprocalRankFusion
import com.localai.workspace.domain.rag.cosineSimilarity
import com.localai.workspace.rag.RetrievalQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.room.withTransaction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object VectorPersistence {
 fun hash(text: String): String=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
 fun encode(vector: FloatArray): ByteArray { require(vector.size==768 && vector.all { it.isFinite() });return ByteBuffer.allocate(vector.size*4).order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach { putFloat(it) } }.array() }
 fun decode(record: SemanticVectorEntity, hash: String, expectedVersion: Int = 1): FloatArray? {
  if(record.sourceHash!=hash || record.version!=expectedVersion || record.dimensions!=768 || record.vector.size!=3072)return null
  val buffer=ByteBuffer.wrap(record.vector).order(ByteOrder.LITTLE_ENDIAN);return FloatArray(768){buffer.float}.takeIf { it.all { n->n.isFinite() } }
 }
}
data class RetrievalDiagnostics(val mode: String="LEXICAL",val retrieved: Int=0,val memoryRetrieved:Int=0,val retrievalMs:Long=0,val embeddingMs:Long=0,val notice:String?="Embedding model unavailable")
data class RetrievalIndexDiagnostics(val status:String="NOT_RUN",val durationMs:Long?=null,val candidateSegments:Int=0,val processedSegments:Int=0,val candidateMemories:Int=0,val processedMemories:Int=0,val failureType:String?=null)
class SemanticRetrievalService(private val db: WorkspaceDatabase, private val models: EmbeddingProvider) {
 val diagnostics=MutableStateFlow(RetrievalDiagnostics())
 val indexingDiagnostics=MutableStateFlow(RetrievalIndexDiagnostics())
 private var nativeEmbeddingNanos=0L
 private fun embed(encoder:NeuralEmbeddings,text:String):FloatArray { val start=System.nanoTime();try{return encoder.embed(text)}finally{nativeEmbeddingNanos+=System.nanoTime()-start} }
 private suspend fun vector(encoder: NeuralEmbeddings, origin: String, content: String, documentId: String?=null,memoryId:String?=null): FloatArray {
  val hash=VectorPersistence.hash(content)
  db.semanticVectorDao().get(origin,encoder.modelId)?.let { VectorPersistence.decode(it,hash,if(documentId!=null)2 else 1)?.let { v->return v } }
  currentCoroutineContext().ensureActive()
  val value=embedFullPassage(encoder,content)
  // Re-check ownership/hash before committing: deletion/edit cannot resurrect an index.
  db.withTransaction {
   val valid=if(documentId!=null) db.documentDao().get(documentId)!=null else false
   // Project memories use an additional scope-independent lookup.
   val memoryValid=memoryId?.let { db.memoryDao().get(it)?.let { item->item.status=="ACTIVE" && item.content==content } } ?: false
   if(valid || memoryValid) db.semanticVectorDao().put(SemanticVectorEntity(origin,encoder.modelId,documentId,memoryId,hash,768,if(documentId!=null)2 else 1,VectorPersistence.encode(value),System.currentTimeMillis()))
  }
  return value
 }
 /** EG1 JNI has a real 512-token cap. On that explicit failure, cover the ENTIRE stored
  * passage using bounded UTF-8 windows and a length-weighted, normalized centroid.
  * No truncation and no runtime/tokenizer change. Other failures remain visible fallback. */
 private suspend fun embedFullPassage(encoder:NeuralEmbeddings,content:String):FloatArray {
  try { return embed(encoder,"title: none | text: $content") }
  catch(failure:Exception) { if(failure.message?.contains("TOKEN_LIMIT")!=true)throw failure }
  val windows=fullCoverageWindows(content)
  val sum=DoubleArray(768)
  for(window in windows) {
   currentCoroutineContext().ensureActive()
   val value=embed(encoder,"title: none | text: $window");val weight=window.toByteArray(Charsets.UTF_8).size
   require(value.size==768 && value.all{it.isFinite()})
   for(i in sum.indices)sum[i]+=value[i]*weight
  }
  val norm=kotlin.math.sqrt(sum.sumOf{it*it});require(norm>0 && norm.isFinite()){"FULL_PASSAGE_NORMALIZATION_FAILED"}
  return FloatArray(768){(sum[it]/norm).toFloat()}
 }
 suspend fun retrieve(projectId:String,query:String,limit:Int=3,documentIds:Set<String> = emptySet()):List<Evidence> = withContext(Dispatchers.IO) {
  val start=System.nanoTime();diagnostics.value=RetrievalDiagnostics(notice=null);if(query.isBlank()||limit<=0||documentIds.isEmpty()&&RetrievalQuery.greeting(query))return@withContext emptyList()
  val terms=RetrievalQuery.terms(query)
  val fts=terms.joinToString(" OR "){"\"$it\""}
  val lexical=if(terms.isEmpty()) emptyList() else if(documentIds.isEmpty()) db.documentDao().searchLexical(projectId,fts,18) else db.documentDao().searchLexicalDocuments(projectId,documentIds.toList(),fts,18)
  val candidates=if(documentIds.isEmpty()) db.documentDao().segmentsForProject(projectId,1000) else db.documentDao().segmentsForDocuments(projectId,documentIds.toList(),1000)
  if(candidates.isEmpty())return@withContext emptyList()
  var semantic=emptyList<Pair<Long,Double>>();var ms=0L;var notice:String?=null;var used=false
  try {
   used=models.use { encoder->
    val beforeEmbedding=nativeEmbeddingNanos
    val q=embed(encoder,"task: search result | query: ${query.take(1000)}")
    semantic=candidates.map { segment->currentCoroutineContext().ensureActive();segment.id to cosineSimilarity(q,vector(encoder,"D:${segment.id}",segment.text,segment.documentId)) }.filter { it.second>=0.25 }.sortedWith(compareByDescending<Pair<Long,Double>> { it.second }.thenBy { it.first }).take(18)
    ms=(nativeEmbeddingNanos-beforeEmbedding)/1_000_000
    true
   } == true
   if(!used)notice="Embedding model unavailable · lexical fallback"
  } catch(cancel:CancellationException){throw cancel} catch(error:Throwable){notice="Embedding failed: ${error.javaClass.simpleName} · lexical fallback"}
  val ranks=ReciprocalRankFusion.fuse(lexical.map { it.id },semantic)
  val byId=(candidates+lexical).associateBy { it.id }
  val ordered=ranks.mapNotNull { byId[it.segmentId] }
  val selected=ordered.take(limit)
  val result=selected.mapNotNull { s->db.documentDao().documentForSegment(s.id)?.takeIf { it.projectId==projectId }?.let { d->
   Evidence("LCL-${VectorPersistence.hash("$projectId:${s.id}").take(8).uppercase()}",s.id,d.id,d.displayName,s.text,s.pageStart,s.pageEnd,s.charStart,s.charStart?.plus(s.text.length),SourceTrust.UNTRUSTED_DOCUMENT,ranks.firstOrNull { it.segmentId==s.id }?.fusedScore?:0.0)
  } }
  diagnostics.value=diagnostics.value.copy(mode=if(used)"SEMANTIC + LEXICAL" else "LEXICAL",retrieved=result.size,retrievalMs=(System.nanoTime()-start)/1_000_000,embeddingMs=ms,notice=notice)
  result
 }
 suspend fun memories(projectId:String,query:String,limit:Int=4): List<MemoryItemEntity> = withContext(Dispatchers.IO) {
  diagnostics.value=diagnostics.value.copy(memoryRetrieved=0)
  if(Regex("(?i)^(calcula|calculate)\\b.{0,50}[0-9].*$").matches(query)||RetrievalQuery.greeting(query)||Regex("^[0-9+*/().%\\s?¿=-]+$").matches(query))return@withContext emptyList()
  val candidates=db.memoryDao().relevant(projectId,1000).filter { it.sourceType=="USER_APPROVED" && it.status=="ACTIVE" }
  if(candidates.isEmpty())return@withContext emptyList()
  val terms=RetrievalQuery.terms(query).toSet()
  val lexical=candidates.map { m->m to RetrievalQuery.terms(m.content).count { it in terms }.toDouble() }.filter { it.second>0 }.sortedByDescending { it.second }
  var result=lexical.take(limit).map { it.first }
  try { val used = models.use { encoder->
   val beforeEmbedding = nativeEmbeddingNanos
   val q=embed(encoder,"task: search result | query: ${query.take(1000)}")
   result=candidates.map { m->m to cosineSimilarity(q,vector(encoder,"M:${m.id}",m.content,memoryId=m.id)) }.filter { it.second>=0.40 }.sortedByDescending { it.second }.take(limit).map { it.first }
   diagnostics.value=diagnostics.value.copy(embeddingMs=diagnostics.value.embeddingMs+(nativeEmbeddingNanos-beforeEmbedding)/1_000_000)
   true
  }
   if (used != true) diagnostics.value=diagnostics.value.copy(notice="Memory embedding model unavailable · lexical fallback")
  } catch(cancel:CancellationException){throw cancel} catch(error:Throwable){diagnostics.value=diagnostics.value.copy(notice="Memory embeddings failed: ${error.javaClass.simpleName}; lexical fallback")}
  diagnostics.value=diagnostics.value.copy(memoryRetrieved=result.size);result
 }
 suspend fun indexProject(projectId: String) {
  val started=System.nanoTime();var segmentCount=0;var memoryCount=0;var completedSegments=0;var completedMemories=0
  indexingDiagnostics.value=RetrievalIndexDiagnostics("RUNNING")
  try {
   models.use { encoder->
    val segments=db.documentDao().segmentsForProject(projectId,1000);segmentCount=segments.size
    val memories=db.memoryDao().relevant(projectId,1000).filter { it.sourceType=="USER_APPROVED" };memoryCount=memories.size
    indexingDiagnostics.value=RetrievalIndexDiagnostics("RUNNING",candidateSegments=segmentCount,candidateMemories=memoryCount)
    segments.forEach { currentCoroutineContext().ensureActive();vector(encoder,"D:${it.id}",it.text,it.documentId);completedSegments++ }
    memories.forEach { currentCoroutineContext().ensureActive();vector(encoder,"M:${it.id}",it.content,memoryId=it.id);completedMemories++ }
   } ?: error("Embedding model unavailable")
   indexingDiagnostics.value=RetrievalIndexDiagnostics("COMPLETE",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,memoryCount,completedMemories)
  } catch(cancel:CancellationException) {
   indexingDiagnostics.value=RetrievalIndexDiagnostics("CANCELLED",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,memoryCount,completedMemories)
   throw cancel
  } catch(error:Throwable) {
   indexingDiagnostics.value=RetrievalIndexDiagnostics("FAILED",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,memoryCount,completedMemories,error.javaClass.simpleName)
   throw error
  }
 }
}

/** Byte ceiling leaves room for task framing and special tokens within EG1's 512-token cap. */
internal fun fullCoverageWindows(text:String,byteLimit:Int=384):List<String> {
 require(byteLimit>=4)
 val result=mutableListOf<String>();var start=0;var at=0;var bytes=0
 while(at<text.length) {
  val cp=text.codePointAt(at);val next=at+Character.charCount(cp);val n=text.substring(at,next).toByteArray(Charsets.UTF_8).size
  if(bytes+n>byteLimit && at>start){result+=text.substring(start,at);start=at;bytes=0}
  bytes+=n;at=next
 }
 if(start<text.length)result+=text.substring(start)
 return result
}
