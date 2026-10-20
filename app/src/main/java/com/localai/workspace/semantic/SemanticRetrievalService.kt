package com.localai.workspace.semantic

import com.localai.workspace.data.*
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.domain.model.SourceTrust
import com.localai.workspace.domain.rag.ReciprocalRankFusion
import com.localai.workspace.domain.rag.cosineSimilarity
import com.localai.workspace.documents.CodeLineChunker
import com.localai.workspace.documents.CodebaseArchive
import com.localai.workspace.documents.StructuredDocuments
import com.localai.workspace.rag.RetrievalQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.room.withTransaction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
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
data class RetrievalIndexDiagnostics(val status:String="NOT_RUN",val durationMs:Long?=null,val candidateSegments:Int=0,val processedSegments:Int=0,val candidateMemories:Int=0,val processedMemories:Int=0,val failureType:String?=null,val runId:String?=null)
class SemanticRetrievalService(private val db: WorkspaceDatabase, private val models: EmbeddingProvider) {
 private data class CodeLineRange(val start:Int,val end:Int)
 val diagnostics=MutableStateFlow(RetrievalDiagnostics())
 val indexingDiagnostics=MutableStateFlow(RetrievalIndexDiagnostics())
 private var nativeEmbeddingNanos=0L
 private fun embed(encoder:NeuralEmbeddings,text:String):FloatArray { val start=System.nanoTime();try{return encoder.embed(text)}finally{nativeEmbeddingNanos+=System.nanoTime()-start} }
 private suspend fun vector(encoder: NeuralEmbeddings, origin: String, content: String, documentId: String): FloatArray {
  val hash=VectorPersistence.hash(content)
  db.semanticVectorDao().get(origin,encoder.modelId)?.let { VectorPersistence.decode(it,hash,2)?.let { v->return v } }
  currentCoroutineContext().ensureActive()
  val value=embedFullPassage(encoder,content)
  // Re-check document ownership before committing: deletion/edit cannot resurrect an index.
  db.withTransaction {
   if(db.documentDao().get(documentId)!=null) db.semanticVectorDao().put(SemanticVectorEntity(
    originId=origin,modelId=encoder.modelId,documentId=documentId,memoryId=null,sourceHash=hash,
    dimensions=768,version=2,vector=VectorPersistence.encode(value),updatedAt=System.currentTimeMillis(),
   ))
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
  val lineRangesBySource=mutableMapOf<Pair<String,String>,Map<Long,CodeLineRange>>()
  val result=selected.mapNotNull { s->db.documentDao().documentForSegment(s.id)?.takeIf { it.projectId==projectId }?.let { d->
   val sourcePath=s.sectionPath?.takeIf(CodebaseArchive::isCodePath)
   val location=sourcePath?.let { path ->
    val key=d.id to path
    if(!lineRangesBySource.containsKey(key)) lineRangesBySource[key]=codeLineRanges(d,path)
    lineRangesBySource[key]?.get(s.id)
   }
   Evidence(
    id="LCL-${VectorPersistence.hash("$projectId:${s.id}").take(8).uppercase()}",segmentId=s.id,
    documentId=d.id,documentTitle=sourcePath ?: d.displayName,excerpt=s.text,
    pageStart=s.pageStart,pageEnd=s.pageEnd,charStart=s.charStart,charEnd=s.charStart?.plus(s.text.length),
    trust=SourceTrust.UNTRUSTED_DOCUMENT,retrievalScore=ranks.firstOrNull { it.segmentId==s.id }?.fusedScore?:0.0,
    lineStart=location?.start,lineEnd=location?.end,
   )
  } }
  diagnostics.value=diagnostics.value.copy(mode=if(used)"SEMANTIC + LEXICAL" else "LEXICAL",retrieved=result.size,retrievalMs=(System.nanoTime()-start)/1_000_000,embeddingMs=ms,notice=notice)
  result
 }

 /** Resolve a lexical segment to its original line interval without adding Room columns. */
 private suspend fun codeLineRanges(document:DocumentEntity,path:String):Map<Long,CodeLineRange> {
  return try {
   val file=File(document.localPath)
   val sourceText=if(document.displayName.substringAfterLast('.',"").equals("zip",true) || document.mimeType=="application/zip") {
    CodebaseArchive.codeFiles(file).firstOrNull { it.relativePath==path }?.text
   } else if(path==document.displayName) {
    StructuredDocuments.text(StructuredDocuments.readFile(file)).first
   } else null
   val chunks=sourceText?.let(CodeLineChunker::chunk).orEmpty()
   if(chunks.isEmpty()) return emptyMap()
   val siblings=db.documentDao().segmentsForDocument(document.id).filter { it.sectionPath==path }
   if(siblings.size<chunks.size) return emptyMap()
   var start=-1
   for(candidate in 0..(siblings.size-chunks.size)) {
    if((0 until chunks.size).all { offset->siblings[candidate+offset].text==chunks[offset].text }) {
     start=candidate;break
    }
   }
   if(start<0) return emptyMap()
   chunks.indices.associate { offset->siblings[start+offset].id to CodeLineRange(chunks[offset].lineStart,chunks[offset].lineEnd) }
  } catch(cancel:CancellationException) { throw cancel }
    catch(_:Exception) { emptyMap() }
 }
 suspend fun indexProject(projectId: String, diagnosticRunId: String = java.util.UUID.randomUUID().toString()) {
  require(diagnosticRunId.isNotBlank() && diagnosticRunId.length <= 64)
  val started=System.nanoTime();var segmentCount=0;var completedSegments=0
  indexingDiagnostics.value=RetrievalIndexDiagnostics(status="RUNNING",runId=diagnosticRunId)
  try {
   models.use { encoder->
    val segments=db.documentDao().segmentsForProject(projectId,1000);segmentCount=segments.size
    indexingDiagnostics.value=RetrievalIndexDiagnostics(status="RUNNING",candidateSegments=segmentCount,runId=diagnosticRunId)
    segments.forEach { currentCoroutineContext().ensureActive();vector(encoder,"D:${it.id}",it.text,it.documentId);completedSegments++ }
   } ?: error("Embedding model unavailable")
   indexingDiagnostics.value=RetrievalIndexDiagnostics("COMPLETE",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,runId=diagnosticRunId)
  } catch(cancel:CancellationException) {
   indexingDiagnostics.value=RetrievalIndexDiagnostics("CANCELLED",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,runId=diagnosticRunId)
   throw cancel
  } catch(error:Throwable) {
   indexingDiagnostics.value=RetrievalIndexDiagnostics("FAILED",(System.nanoTime()-started)/1_000_000,segmentCount,completedSegments,failureType=error.javaClass.simpleName,runId=diagnosticRunId)
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
