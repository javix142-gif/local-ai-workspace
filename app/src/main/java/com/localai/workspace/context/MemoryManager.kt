package com.localai.workspace.context

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
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
    /**
     * Creates a structured preference, requiring a separate explicit confirmation before
     * superseding an active preference with the same subject/predicate and a different value.
     */
    suspend fun createStructuredPreference(
        text:String,
        scope:SemanticScope,
        subject:String,
        predicate:String,
        objectValue:String,
        title:String?=null,
        expiresAt:Long?=null,
        replaceConflictId:String?=null,
    ):MemoryRecord=withContext(Dispatchers.IO){
        require(text.isNotBlank()&&text.length<=16000)
        require(subject.isNotBlank()&&predicate.isNotBlank()&&objectValue.isNotBlank()){"STRUCTURED_PREFERENCE_FIELDS_REQUIRED"}
        require(scope.type in setOf(ScopeType.USER,ScopeType.PROJECT)){"STRUCTURED_PREFERENCE_SCOPE_UNSUPPORTED"}
        database.withTransaction {
            val now=System.currentTimeMillis()
            require(expiresAt==null||expiresAt>now)
            val hash=MemoryContent.hash(text,MemoryKind.PREFERENCE)
            database.dao().activeDuplicate(MemoryKind.PREFERENCE.name,hash,scope.type.name,scope.id,now)?.let{return@withTransaction it}
            var superseded:MemoryRecord?=null
            val conflicts=database.dao().matchingStructured(
                MemoryKind.PREFERENCE.name,subject,predicate,scope.type.name,scope.id,now,
            ).filter{it.status==MemoryStatus.ACTIVE.name&&it.objectValue!=objectValue}
            if(conflicts.isNotEmpty()){
                val selected=conflicts.singleOrNull()?.takeIf{it.id==replaceConflictId}
                if(selected==null)throw StructuredPreferenceConflictException(conflicts.map{it.id})
                superseded=selected
                database.dao().put(selected.copy(status=MemoryStatus.SUPERSEDED.name,updatedAt=now))
                database.dao().deleteVectors(selected.id)
            }else if(replaceConflictId!=null){
                throw IllegalStateException("STRUCTURED_PREFERENCE_CONFLICT_CHANGED")
            }
            val record=MemoryRecord(
                id=UUID.randomUUID().toString(),kind=MemoryKind.PREFERENCE.name,
                scopeType=scope.type.name,scopeId=scope.id,title=title,text=text.trim(),
                status=MemoryStatus.ACTIVE.name,confidence=1.0,createdAt=now,updatedAt=now,
                expiresAt=expiresAt,sourceType="USER_EXPLICIT",contentHash=hash,version=(superseded?.version ?: 0)+1,
                supersedesId=superseded?.id,subject=subject,predicate=predicate,objectValue=objectValue,
            )
            database.dao().put(record)
            record
        }
    }
    suspend fun get(id:String)=database.dao().get(id)
    data class BundleImportResult(val imported:Int,val duplicates:Int,val pendingIds:List<String>,val missingProjectFallbackCount:Int=0)
    data class BundleExportResult(val archive:ByteArray,val exportedRecords:Int,val excludedSensitiveRecords:Int=0)
    data class JsonExportResult(val json:String,val exportedRecords:Int,val excludedSensitiveRecords:Int)
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
    suspend fun importBundlePending(preview:MemoryBundleV1.Preview,selectedKeys:Set<String>,availableProjectIds:Set<String> = emptySet()):BundleImportResult=withContext(Dispatchers.IO){
        require(selectedKeys.isNotEmpty()){"NO_MEMORIES_SELECTED"}
        val candidates=preview.candidates.filter{it.key in selectedKeys}
        require(candidates.size==selectedKeys.size){"UNKNOWN_MEMORY_SELECTION"}
        require(candidates.all{it.content.isNotBlank()&&it.content.length<=16_000}){"INVALID_MEMORY_CONTENT"}
        require(candidates.none{it.sensitiveCandidate||MemoryBundleV1.isSensitive(it)}){"SENSITIVE_MEMORY_REQUIRES_REMOVAL"}
        require(candidates.size<=1000){"TOO_MANY_SELECTED_MEMORIES"}
        val now=System.currentTimeMillis();val pendingIds=mutableListOf<String>();var duplicates=0;var missingProjectFallbackCount=0
        database.withTransaction {
            for(candidate in candidates){
                val sourceId="${preview.bundleId}:${candidate.key}"
                if(database.dao().bySourceId(sourceId)!=null){duplicates++;continue}
                val candidateScope=when(candidate.originalScopeType){
                    "USER"->"USER" to "local"
                    "PROJECT"->{
                        val project=candidate.originalScopeId?.takeIf{it in availableProjectIds}
                        if(project!=null)"PROJECT" to project else {missingProjectFallbackCount++;"USER" to "local"}
                    }
                    else->"USER" to "local"
                }
                val hash=MemoryContent.hash(candidate.content,candidate.kind)
                if(database.dao().activeDuplicate(candidate.kind.name,hash,candidateScope.first,candidateScope.second,now)!=null){duplicates++;continue}
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
                    "originalTitle" to candidate.originalTitle,
                    "originMetadata" to candidate.originalMetadataJson?.let { JsonParser.parseString(it).asJsonObject },
                    "originalScopeType" to candidate.originalScopeType,
                    "originalScopeId" to candidate.originalScopeId,
                    "scopeFallback" to (candidate.originalScopeType=="PROJECT"&&candidateScope.first=="USER"),
                    "subject" to candidate.subject,
                    "predicate" to candidate.predicate,
                    "object" to candidate.objectValue,
                    "instructionsAreData" to true,
                ))
                database.dao().put(MemoryRecord(
                    id=id,kind=candidate.kind.name,scopeType=candidateScope.first,scopeId=candidateScope.second,title=candidate.originalTitle ?: candidate.category,
                    text=candidate.content,status="PENDING",confidence=candidate.confidence ?: .5,
                    importance=candidate.originalImportance?:.5,pinned=candidate.originalPinned,createdAt=now,updatedAt=now,
                    lastAccessedAt=candidate.originalLastAccessedAt,expiresAt=candidate.originalExpiresAt,
                    sourceType="IMPORTED_UNREVIEWED",sourceId=sourceId,
                    contentHash=hash,indexState="INDEX_PENDING",structuredPayloadJson=provenance,
                    subject=candidate.subject,predicate=candidate.predicate,objectValue=candidate.objectValue,
                ))
                pendingIds+=id
            }
        }
        BundleImportResult(pendingIds.size,duplicates,pendingIds,missingProjectFallbackCount)
    }
    suspend fun update(id:String,text:String,expiresAt:Long?=null):MemoryRecord=withContext(Dispatchers.IO){database.withTransaction{
        val old=requireNotNull(get(id));require(old.status in setOf("ACTIVE","PENDING"));require(text.isNotBlank()&&text.length<=16000);val now=System.currentTimeMillis();require(expiresAt==null||expiresAt>now);if(old.status=="PENDING")require(!MemoryContent.secret(text)){"SECRET_CANDIDATE_REJECTED"}
        val next=old.copy(id=UUID.randomUUID().toString(),text=text.trim(),createdAt=now,updatedAt=now,version=old.version+1,supersedesId=old.id,contentHash=MemoryContent.hash(text,MemoryKind.valueOf(old.kind)),expiresAt=expiresAt,indexState="INDEX_PENDING")
        database.dao().put(old.copy(status="SUPERSEDED",updatedAt=now));database.dao().deleteVectors(old.id);database.dao().put(next);next
    }}
    suspend fun approve(id:String,access:ScopeAccess=ScopeAccess())=database.withTransaction{
        val m=requireNotNull(get(id));require(m.status=="PENDING");require(!MemoryContent.secret(m.text)){"SECRET_CANDIDATE_REJECTED"}
        require(access.permits(m.scopeType,m.scopeId)){"MEMORY_SCOPE_NOT_ALLOWED"}
        val now=System.currentTimeMillis();require(m.expiresAt==null||m.expiresAt>now){"EXPIRED_MEMORY_CANNOT_APPROVE"}
        val approved=m.copy(status="ACTIVE",confidence=if(m.sourceType=="IMPORTED_UNREVIEWED")m.confidence else 1.0,
            sourceType=if(m.sourceType=="IMPORTED_UNREVIEWED")"USER_APPROVED"else m.sourceType,updatedAt=now)
        if(approved.pinned)requirePinnedPreferenceBudget(approved,access)
        database.dao().put(approved)
    }
    suspend fun pin(id:String,access:ScopeAccess=ScopeAccess())=database.withTransaction{
        val m=requireNotNull(get(id));val next=m.copy(pinned=!m.pinned,updatedAt=System.currentTimeMillis())
        if(next.pinned&&next.status=="ACTIVE")requirePinnedPreferenceBudget(next,access)
        database.dao().put(next)
    }
    private suspend fun requirePinnedPreferenceBudget(candidate:MemoryRecord,access:ScopeAccess){
        require(access.permits(candidate.scopeType,candidate.scopeId)){"MEMORY_SCOPE_NOT_ALLOWED"}
        val now=System.currentTimeMillis()
        val existing=database.dao().active(access.allowed.map{it.key},now).filter{it.id!=candidate.id}
        val projected=approvedPromptMemories(existing+candidate).filter{it.pinned}
        val size=ContextBudgetPolicy.preferenceBlockSize(projected)
        val limit=ContextBudgetPolicy.preferenceBudget(ContextBudgetPolicy.inputBudget())
        require(size<=limit){"PINNED_PREFERENCE_LIMIT_EXCEEDED"}
    }
    suspend fun delete(id:String)=database.withTransaction{val m=get(id) ?: return@withTransaction;database.dao().put(m.copy(status="DELETED",indexState="NOT_INDEXED",updatedAt=System.currentTimeMillis()));database.dao().deleteVectors(id)}
    /** Permanently remove one record and every superseded version in its lineage. */
    suspend fun forgetPermanently(id:String):Int=withContext(Dispatchers.IO){
        database.enableSecureDelete()
        val removed=database.withTransaction {
            val all=database.dao().allForMaintenance()
            val byId=all.associateBy{it.id}
            if(id !in byId)return@withTransaction emptyList()
            var root=id
            val ancestors=mutableSetOf<String>()
            while(true){
                check(ancestors.add(root)){"MEMORY_VERSION_CYCLE"}
                val parent=byId[root]?.supersedesId ?: break
                if(parent !in byId)break
                root=parent
            }
            val lineage=linkedSetOf(root)
            var frontier=setOf(root)
            while(frontier.isNotEmpty()){
                val children=all.filter{it.supersedesId in frontier}.map{it.id}.filter{lineage.add(it)}.toSet()
                frontier=children
            }
            check(id in lineage){"MEMORY_VERSION_LINEAGE_INVALID"}
            val ids=lineage.toList()
            database.dao().deleteVectors(ids)
            database.dao().deleteMemories(ids)
            ids
        }
        if(removed.isNotEmpty()){
            database.compactAfterDeletion()
            check(database.dao().allForMaintenance().none{it.id in removed}){"MEMORY_FORGET_VERIFY_FAILED"}
            check(database.dao().vectorsForOrigins(removed).isEmpty()){"MEMORY_FORGET_VECTOR_VERIFY_FAILED"}
        }
        removed.size
    }

    /** Non-blocking retention cleanup; active or newer versions are never selected. */
    suspend fun purgeSuperseded(olderThanMillis:Long=30L*24*60*60*1000,now:Long=System.currentTimeMillis()):Int=withContext(Dispatchers.IO){
        require(olderThanMillis>=0)
        val cutoff=now-olderThanMillis
        val removed=database.withTransaction {
            val ids=database.dao().allForMaintenance().filter{it.status=="SUPERSEDED"&&it.updatedAt<cutoff}.map{it.id}
            if(ids.isNotEmpty()){database.dao().deleteVectors(ids);database.dao().deleteMemories(ids)}
            ids
        }
        if(removed.isNotEmpty())database.compactAfterDeletion()
        removed.size
    }
    suspend fun lookup(access:ScopeAccess,kind:MemoryKind?=null,subject:String?=null,predicate:String?=null):List<MemoryRecord>{database.dao().expire(System.currentTimeMillis());return database.dao().active(access.allowed.map{it.key},System.currentTimeMillis()).filter{(kind==null||it.kind==kind.name)&&(subject==null||it.subject==subject)&&(predicate==null||it.predicate==predicate)}}
    /** User-approved preferences/procedures and pinned records are policy context, not relevance hits. */
    fun approvedPromptMemories(records:List<MemoryRecord>):List<MemoryRecord> = records
        .filter { it.status=="ACTIVE"&&(it.expiresAt==null||it.expiresAt>System.currentTimeMillis())&&(it.kind in setOf(MemoryKind.PREFERENCE.name, MemoryKind.PROCEDURE.name) || it.pinned) }
        .sortedWith(compareByDescending<MemoryRecord>{it.pinned}
            .thenByDescending{it.importance}
            .thenByDescending{it.updatedAt}
            .thenBy{it.id})
    suspend fun exportBundle(access:ScopeAccess):BundleExportResult=withContext(Dispatchers.IO){
        val records=lookup(access)
        val safe=records.filterNot(::containsSensitiveMaterial)
        BundleExportResult(MemoryBundleV1.write(safe),safe.size,records.size-safe.size)
    }
    /** Explicit full local backup: personal and project scopes only, never session/task/agent state. */
    suspend fun exportAllWorkspaceMemories():BundleExportResult=withContext(Dispatchers.IO){
        val records=database.dao().activeAll(System.currentTimeMillis()).filter{
            it.scopeType=="USER"&&it.scopeId=="local"||it.scopeType=="PROJECT"
        }
        val safe=records.filterNot(::containsSensitiveMaterial)
        BundleExportResult(MemoryBundleV1.write(safe),safe.size,records.size-safe.size)
    }
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
    suspend fun select(query:String,access:ScopeAccess,limit:Int=6,queryVector:EmbeddingResult?=null,allowEmbedding:Boolean=true,excludedIds:Set<String> = emptySet(),candidateRecords:List<MemoryRecord>?=null):MemorySelection = withContext(Dispatchers.IO){
        require(limit in 1..50)
        if(MemoryRelevance.smallTalk(query))return@withContext MemorySelection(emptyList(),emptyList(),true)
        val candidates=(candidateRecords ?: lookup(access)).filterNot{it.id in excludedIds};val vector=queryVector ?: if(allowEmbedding&&query.length>2)queryEmbedding(query)else null
        val storedVectors=if(vector==null) mutableMapOf() else loadVectors(candidates,vector.space.id)
        if(vector!=null){
            val attempted=mutableListOf<MemoryRecord>()
            for(memory in candidates){
                if(storedVectors[memory.id]==null&&attempted.size<MAX_INLINE_INDEX_ATTEMPTS){
                    attempted+=memory
                    index(memory)
                }
            }
            if(attempted.isNotEmpty())storedVectors.putAll(loadVectors(attempted,vector.space.id))
        }
        val hits=candidates.map{m->val stored=storedVectors[m.id]?.takeIf{it.contentHash==m.contentHash}
            val semantic=if(stored!=null&&vector!=null)SemanticVectors.cosine(vector,EmbeddingResult(vector.space,SemanticVectors.decode(stored.bytes,stored.dimension),0))else null
            val relevance=MemoryRelevance.assess(query,m,semantic)
            MemoryHit(m,relevance.rankingScore,semantic,relevance)
        }.sortedWith(compareByDescending<MemoryHit>{it.score}.thenBy{it.record.id})
        MemorySelection(hits.filter{it.relevance!!.accepted}.take(limit),hits.filter{!it.relevance!!.accepted})
    }
    /** Read vectors in bounded SQL batches rather than issuing one Room roundtrip per memory. */
    private suspend fun loadVectors(memories:List<MemoryRecord>,space:String):MutableMap<String,ContextVector>{
        if(memories.isEmpty())return mutableMapOf()
        val hashes=memories.associate{it.id to it.contentHash}
        val result=mutableMapOf<String,ContextVector>()
        memories.map{it.id}.distinct().chunked(VECTOR_LOOKUP_BATCH_SIZE).forEach{ids->
            database.dao().vectors(space,ids).forEach{vector->
                if(hashes[vector.originId]==vector.contentHash)result[vector.originId]=vector
            }
        }
        return result
    }
    private companion object {
        const val VECTOR_LOOKUP_BATCH_SIZE=400
        const val MAX_INLINE_INDEX_ATTEMPTS=8
    }
    suspend fun exportJson(access:ScopeAccess):JsonExportResult=withContext(Dispatchers.IO){
        val records=lookup(access)
        val safe=records.filterNot(::containsSensitiveMaterial)
        JsonExportResult(
            GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(
                mapOf("schema" to "MEMORY_CONTEXT_V1","memories" to safe,"vectorsIncluded" to false),
            ),
            safe.size,
            records.size-safe.size,
        )
    }
    private fun containsSensitiveMaterial(record:MemoryRecord)=MemoryContent.secret(listOf(
        record.text,record.title.orEmpty(),record.structuredPayloadJson.orEmpty(),record.subject.orEmpty(),
        record.predicate.orEmpty(),record.objectValue.orEmpty(),record.sourceId.orEmpty(),
        record.sourceSegmentId.orEmpty(),record.sourceMessageId.orEmpty(),record.supersedesId.orEmpty(),
    ).joinToString(" "))
}

class StructuredPreferenceConflictException(val conflictingIds:List<String>):IllegalStateException("STRUCTURED_PREFERENCE_CONFLICT")
