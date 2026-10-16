package com.localai.workspace.context

import androidx.room.withTransaction
import com.google.gson.Gson
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
    data class BundleImportResult(val imported:Int,val duplicates:Int,val pendingIds:List<String>)
    data class BundleCandidateReview(val exactDuplicate:Boolean=false,val possibleContradiction:Boolean=false)
    suspend fun inspectBundle(preview:MemoryBundleV1.Preview):Map<String,BundleCandidateReview> = withContext(Dispatchers.IO){
        val now=System.currentTimeMillis();val duplicateKeys=mutableSetOf<String>();val conflictKeys=mutableSetOf<String>()
        val seen=mutableMapOf<Pair<String,String>,MutableList<MemoryBundleV1.Candidate>>()
        for(candidate in preview.candidates){
            val hash=MemoryContent.hash(candidate.content,candidate.kind)
            val sourceId="${preview.bundleId}:${candidate.key}"
            if(database.dao().bySource("IMPORTED_UNREVIEWED",sourceId)!=null || database.dao().activeDuplicate(candidate.kind.name,hash,"USER","local",now)!=null){duplicateKeys+=candidate.key}
            seen.getOrPut(candidate.kind.name to hash){mutableListOf()}+=candidate
            if(!candidate.subject.isNullOrBlank()&&!candidate.predicate.isNullOrBlank()){
                val matches=database.dao().matchingStructured(candidate.kind.name,candidate.subject,candidate.predicate,"USER","local",now)
                if(matches.any{it.contentHash!=hash})conflictKeys+=candidate.key
            }
        }
        seen.values.filter{group->group.map{it.key}.distinct().size>1}.flatten().forEach{duplicateKeys+=it.key}
        preview.candidates.filter{!it.subject.isNullOrBlank()&&!it.predicate.isNullOrBlank()}
            .groupBy{Triple(it.kind.name,it.subject,it.predicate)}.values.filter{group->group.map{MemoryContent.hash(it.content,it.kind)}.distinct().size>1}
            .flatten().forEach{conflictKeys+=it.key}
        preview.candidates.associate{it.key to BundleCandidateReview(it.key in duplicateKeys,it.key in conflictKeys)}
    }
    suspend fun importBundlePending(preview:MemoryBundleV1.Preview,selectedKeys:Set<String>):BundleImportResult=withContext(Dispatchers.IO){
        require(selectedKeys.isNotEmpty()){"NO_MEMORIES_SELECTED"}
        val candidates=preview.candidates.filter{it.key in selectedKeys}
        require(candidates.size==selectedKeys.size){"UNKNOWN_MEMORY_SELECTION"}
        require(candidates.all{it.content.isNotBlank()&&it.content.length<=16_000}){"INVALID_MEMORY_CONTENT"}
        require(candidates.none{it.sensitiveCandidate||MemoryBundleV1.isSensitive(it)}){"SENSITIVE_MEMORY_REQUIRES_REMOVAL"}
        require(candidates.size<=250){"TOO_MANY_SELECTED_MEMORIES"}
        val now=System.currentTimeMillis();val pendingIds=mutableListOf<String>();var duplicates=0
        database.withTransaction {
            for(candidate in candidates){
                val sourceId="${preview.bundleId}:${candidate.key}"
                if(database.dao().bySource("IMPORTED_UNREVIEWED",sourceId)!=null){duplicates++;continue}
                val hash=MemoryContent.hash(candidate.content,candidate.kind)
                if(database.dao().activeDuplicate(candidate.kind.name,hash,"USER","local",now)!=null){duplicates++;continue}
                val id=UUID.randomUUID().toString()
                val provenance=Gson().toJson(mapOf(
                    "packageSchema" to MemoryBundleV1.SCHEMA,
                    "bundleId" to preview.bundleId,
                    "externalId" to candidate.externalId,
                    "category" to candidate.category,
                    "trust" to candidate.trust,
                    "provenance" to candidate.provenance,
                    "originalScope" to candidate.originalScope,
                    "originalCreatedAt" to candidate.originalCreatedAt,
                    "originalUpdatedAt" to candidate.originalUpdatedAt,
                    "originalStatus" to candidate.originalStatus,
                    "subject" to candidate.subject,
                    "predicate" to candidate.predicate,
                    "object" to candidate.objectValue,
                    "instructionsAreData" to true,
                ))
                database.dao().put(MemoryRecord(
                    id=id,kind=candidate.kind.name,scopeType="USER",scopeId="local",title=candidate.category,
                    text=candidate.content,status="PENDING",confidence=candidate.confidence ?: .5,
                    createdAt=now,updatedAt=now,sourceType="IMPORTED_UNREVIEWED",sourceId=sourceId,
                    contentHash=hash,indexState="INDEX_PENDING",structuredPayloadJson=provenance,
                    subject=candidate.subject,predicate=candidate.predicate,objectValue=candidate.objectValue,
                ))
                pendingIds+=id
            }
        }
        BundleImportResult(pendingIds.size,duplicates,pendingIds)
    }
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
