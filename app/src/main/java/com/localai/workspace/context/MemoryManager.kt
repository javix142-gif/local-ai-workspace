package com.localai.workspace.context

import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Canonical data and rebuildable vectors live in a new sidecar. Existing EG1/EG2 DBs are untouched. */
class MemoryManager(val database:MemoryContextDatabase,private val embed:suspend(SemanticInput,EmbeddingTask)->EmbeddingResult?) {
    private val indexing=Mutex();val items get()=database.dao().observe()
    suspend fun create(text:String,scope:SemanticScope,kind:MemoryKind=MemoryKind.FACT,title:String?=null,expiresAt:Long?=null,sourceType:String="USER_EXPLICIT",sourceId:String?=null,sourceMessageId:String?=null,pending:Boolean=false,subject:String?=null,predicate:String?=null,objectValue:String?=null,structuredPayloadJson:String?=null):MemoryRecord=withContext(Dispatchers.IO){
        require(text.isNotBlank()&&text.length<=16000);if(pending)require(!MemoryContent.secret(text)){"SECRET_CANDIDATE_REJECTED"}
        val now=System.currentTimeMillis();require(expiresAt==null||expiresAt>now)
        database.withTransaction {
            val hash=MemoryContent.hash(text,kind)
            database.dao().active(listOf(scope.key),now).firstOrNull{it.contentHash==hash&&it.kind==kind.name} ?: MemoryRecord(UUID.randomUUID().toString(),kind.name,scope.type.name,scope.id,title,text.trim(),status=if(pending)"PENDING"else"ACTIVE",confidence=if(pending).5 else 1.0,createdAt=now,updatedAt=now,expiresAt=expiresAt,sourceType=sourceType,sourceId=sourceId,sourceMessageId=sourceMessageId,contentHash=hash,subject=subject,predicate=predicate,objectValue=objectValue,structuredPayloadJson=structuredPayloadJson).also{database.dao().put(it)}
        }
    }
    suspend fun get(id:String)=database.dao().get(id)
    suspend fun update(id:String,text:String,expiresAt:Long?=null):MemoryRecord=withContext(Dispatchers.IO){database.withTransaction{
        val old=requireNotNull(get(id));require(old.status in setOf("ACTIVE","PENDING"));require(text.isNotBlank()&&text.length<=16000);val now=System.currentTimeMillis();require(expiresAt==null||expiresAt>now);if(old.status=="PENDING")require(!MemoryContent.secret(text)){"SECRET_CANDIDATE_REJECTED"}
        val next=old.copy(id=UUID.randomUUID().toString(),text=text.trim(),createdAt=now,updatedAt=now,version=old.version+1,supersedesId=old.id,contentHash=MemoryContent.hash(text,MemoryKind.valueOf(old.kind)),expiresAt=expiresAt,indexState="INDEX_PENDING")
        database.dao().put(old.copy(status="SUPERSEDED",updatedAt=now));database.dao().deleteVectors(old.id);database.dao().put(next);next
    }}
    suspend fun approve(id:String)=database.withTransaction{val m=requireNotNull(get(id));require(m.status=="PENDING");require(!MemoryContent.secret(m.text)){"SECRET_CANDIDATE_REJECTED"};database.dao().put(m.copy(status="ACTIVE",confidence=1.0,updatedAt=System.currentTimeMillis()))}
    suspend fun pin(id:String)=database.withTransaction{val m=requireNotNull(get(id));database.dao().put(m.copy(pinned=!m.pinned,updatedAt=System.currentTimeMillis()))}
    suspend fun delete(id:String)=database.withTransaction{val m=get(id) ?: return@withTransaction;database.dao().put(m.copy(status="DELETED",indexState="NOT_INDEXED",updatedAt=System.currentTimeMillis()));database.dao().deleteVectors(id)}
    suspend fun lookup(access:ScopeAccess,kind:MemoryKind?=null,subject:String?=null,predicate:String?=null):List<MemoryRecord>{database.dao().expire(System.currentTimeMillis());return database.dao().active(access.allowed.map{it.key},System.currentTimeMillis()).filter{(kind==null||it.kind==kind.name)&&(subject==null||it.subject==subject)&&(predicate==null||it.predicate==predicate)}}
    suspend fun recover()=withContext(Dispatchers.IO){indexing.withLock{database.dao().expire(System.currentTimeMillis());database.dao().cleanupVectors();for(m in database.dao().pending()){currentCoroutineContext().ensureActive();index(m)}}}
    suspend fun index(m:MemoryRecord):Boolean {
        if(m.status!="ACTIVE"||m.expiresAt?.let{it<=System.currentTimeMillis()}==true)return false
        val v=embed(SemanticInput.Text(m.text,m.title ?: "memory"),EmbeddingTask.DOCUMENT) ?: return false
        require(v.vector.size==v.space.dimension && v.vector.all{it.isFinite()}){"INVALID_EMBEDDING_VECTOR"}
        var committed=false
        database.withTransaction{val current=get(m.id);if(current?.status=="ACTIVE"&&current.contentHash==m.contentHash&&current.expiresAt?.let{it<=System.currentTimeMillis()}!=true){committed=true;database.dao().vector(ContextVector(m.id,v.space.id,m.contentHash,v.vector.size,SemanticVectors.encode(v.vector),System.currentTimeMillis()));database.dao().put(current.copy(indexState="INDEXED"))}}
        return committed
    }
    suspend fun queryEmbedding(query:String)=embed(SemanticInput.Text(query),EmbeddingTask.SEARCH)
    suspend fun search(query:String,access:ScopeAccess,limit:Int=6,queryVector:EmbeddingResult?=null,allowEmbedding:Boolean=true):List<MemoryHit> = select(query,access,limit,queryVector,allowEmbedding).included
    suspend fun select(query:String,access:ScopeAccess,limit:Int=6,queryVector:EmbeddingResult?=null,allowEmbedding:Boolean=true):MemorySelection = withContext(Dispatchers.IO){
        require(limit in 1..50)
        if(MemoryRelevance.smallTalk(query))return@withContext MemorySelection(emptyList(),emptyList(),true)
        val candidates=lookup(access);val vector=queryVector ?: if(allowEmbedding&&query.length>2)queryEmbedding(query)else null
        var newEmbeddings=0
        val hits=candidates.map{m->var stored=vector?.let{database.dao().vector(m.id,it.space.id,m.contentHash)}
            if(vector!=null&&stored==null&&newEmbeddings<8){newEmbeddings++;index(m);stored=database.dao().vector(m.id,vector.space.id,m.contentHash)}
            val semantic=if(stored!=null&&vector!=null)SemanticVectors.cosine(vector,EmbeddingResult(vector.space,SemanticVectors.decode(stored.bytes,stored.dimension),0))else null
            val relevance=MemoryRelevance.assess(query,m,semantic)
            MemoryHit(m,relevance.rankingScore,semantic,relevance)
        }.sortedWith(compareByDescending<MemoryHit>{it.score}.thenBy{it.record.id})
        MemorySelection(hits.filter{it.relevance!!.accepted}.take(limit),hits.filter{!it.relevance!!.accepted})
    }
    suspend fun exportJson(access:ScopeAccess)=GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(mapOf("schema" to "MEMORY_CONTEXT_V1","memories" to lookup(access),"vectorsIncluded" to false))
}
