package com.localai.workspace.context

import android.content.Context
import androidx.room.withTransaction
import com.localai.workspace.AppGraph
import com.localai.workspace.data.MemoryItemEntity
import com.localai.workspace.domain.model.*
import com.localai.workspace.rag.RetrievalQuery
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Additive orchestration. It neither replaces semantic indexes nor owns a second embedding engine. */
class ContextFoundation(private val graph:AppGraph, private val semanticLayerProvider:()->SemanticLayer={graph.validationOwner?.semanticV2 ?: graph.semanticV2}) {
    val database=MemoryContextDatabase.create(graph.contextForMeasurements,graph.validationId?.let{"context-memory-$it.db"} ?: "memory_context.db")
    private val prefs=graph.contextForMeasurements.getSharedPreferences("context_foundation_v1",Context.MODE_PRIVATE)
    val enabled=MutableStateFlow(prefs.getBoolean("enabled",true))
    val notice=MutableStateFlow<String?>(null)
    val last=MutableStateFlow<ContextBundle?>(null)
    val memory=MemoryManager(database){input,task->embedding(input,task)}
    private val legacyMigration=Mutex()
    fun enable(value:Boolean){check(prefs.edit().putBoolean("enabled",value).commit());enabled.value=value}
    suspend fun embedding(input:SemanticInput,task:EmbeddingTask):EmbeddingResult?=withContext(Dispatchers.IO){
        try {
            val layer=semanticLayerProvider()
            val result=if(layer.selection.choice==SemanticProviderChoice.EG2){layer.restore();layer.manager.embed(input,task,layer.dimension)}
            else graph.inferenceGate.withLock{LegacyEmbeddingProvider(graph.embeddingModels).embed(input,task,768)}
            notice.value=null;result
        }catch(cancel:CancellationException){throw cancel}catch(error:Exception){notice.value="LEXICAL_FALLBACK:${if(error is SemanticFailure)error.code.name else error.javaClass.simpleName}";null}
    }
    suspend fun recoverCanonical()=withContext(Dispatchers.IO){database.dao().expire(System.currentTimeMillis());database.dao().cleanupVectors()}
    internal data class LegacyMigrationResult(
        val activeRecords:Int=0,val duplicateRecords:Int=0,val archivedPurged:Int=0,
        val previouslyImported:Int=0,val legacyRowsRemoved:Int=0,
    )

    /** Copy and verify approved legacy rows before deleting their rebuildable Room source. */
    internal suspend fun migrateLegacyMemories(afterRecord:suspend(Int)->Unit={}):LegacyMigrationResult = withContext(Dispatchers.IO){
        legacyMigration.withLock {
            if(prefs.getBoolean(LEGACY_MEMORY_MIGRATION_COMPLETE,false))return@withLock LegacyMigrationResult()
            val legacy=graph.database.memoryDao().allForMigration()
            if(legacy.isEmpty()){
                check(prefs.edit().putBoolean(LEGACY_MEMORY_MIGRATION_COMPLETE,true).commit()){"LEGACY_MEMORY_MIGRATION_STATE_WRITE_FAILED"}
                return@withLock LegacyMigrationResult()
            }
            val dao=database.dao()
            val existing=dao.legacyImports().associateBy{it.sourceId.orEmpty()}.toMutableMap()
            val canonicalByKey=linkedMapOf<String,String>()
            val expectedById=linkedMapOf<String,MemoryRecord>()
            val activeKeys=mutableSetOf<String>()
            var duplicateCount=0
            var archivedCount=0
            var previouslyImported=0
            var processed=0
            val ordered=legacy.sortedWith(compareBy<MemoryItemEntity>{when(it.status){"ACTIVE"->0;"ARCHIVED"->1;else->2}}
                .thenByDescending{existing.containsKey(it.id)}.thenBy{it.id})
            for(old in ordered){
                currentCoroutineContext().ensureActive()
                if(old.status=="ARCHIVED"){
                    archivedCount++
                    val prior=existing.remove(old.id)
                    if(prior!=null)database.withTransaction{dao.deleteVectors(prior.id);dao.deleteMemories(listOf(prior.id))}
                    processed++;afterRecord(processed);continue
                }
                val kind=legacyKind(old.kind)
                val scope=legacyScope(old)
                val text=old.content.trim()
                require(text.isNotBlank()&&text.length<=16_000){"LEGACY_MEMORY_INVALID:${old.id}"}
                val hash=MemoryContent.hash(text,kind)
                val key="${scope.key}|${kind.name}|$hash"
                val prior=existing[old.id]
                val targetStatus=when(old.status){
                    "ACTIVE"->{
                        if(prior!=null)previouslyImported++
                        if(activeKeys.add(key))"ACTIVE" else {duplicateCount++;"SUPERSEDED"}
                    }
                    "PENDING"->"PENDING"
                    "DELETED"->"DELETED"
                    "SUPERSEDED"->"SUPERSEDED"
                    "EXPIRED"->"EXPIRED"
                    else->"PENDING"
                }
                val canonical=canonicalByKey[key]
                val record=MemoryRecord(
                    id=prior?.id?:"legacy-${old.id}",kind=kind.name,scopeType=scope.type.name,scopeId=scope.id,
                    title=prior?.title,text=text,status=targetStatus,confidence=1.0,importance=old.relevance.toDouble(),
                    pinned=prior?.pinned?:false,createdAt=old.createdAt,updatedAt=old.updatedAt,
                    sourceType="APPROVED_LEGACY",sourceId=old.id,sourceSegmentId=old.sourceReference,
                    contentHash=hash,version=prior?.version?:1,
                    supersedesId=if(targetStatus=="SUPERSEDED")canonical?:prior?.supersedesId else prior?.supersedesId,
                    indexState=if(targetStatus=="ACTIVE")prior?.indexState?:"INDEX_PENDING" else "NOT_INDEXED",
                    structuredPayloadJson=legacyOriginMetadata(old),
                )
                database.withTransaction{dao.put(record);if(prior!=null&&prior.contentHash!=record.contentHash)dao.deleteVectors(prior.id)}
                expectedById[old.id]=record
                existing[old.id]=record
                if(targetStatus=="ACTIVE")canonicalByKey.putIfAbsent(key,record.id)
                processed++
                afterRecord(processed)
            }

            val nonArchived=legacy.filter{it.status!="ARCHIVED"}
            val importedById=dao.legacyImports().filter{it.sourceId in nonArchived.map(MemoryItemEntity::id)}.associateBy{it.sourceId.orEmpty()}
            check(importedById.keys==nonArchived.map{it.id}.toSet()){"LEGACY_MEMORY_SOURCE_COVERAGE_MISMATCH"}
            for(old in nonArchived){
                val actual=checkNotNull(importedById[old.id]);val expected=checkNotNull(expectedById[old.id])
                check(actual.text==old.content.trim()&&actual.kind==legacyKind(old.kind).name&&
                    actual.scopeType==expected.scopeType&&actual.scopeId==expected.scopeId&&
                    actual.createdAt==old.createdAt&&actual.updatedAt==old.updatedAt&&actual.status==expected.status){"LEGACY_MEMORY_VERIFY_FAILED:${old.id}"}
            }
            val importedActive=importedById.values.count{it.status=="ACTIVE"}
            check(importedActive==activeKeys.size){"LEGACY_MEMORY_ACTIVE_COUNT_MISMATCH"}
            check(importedById.values.count{it.status=="SUPERSEDED"}>=duplicateCount){"LEGACY_MEMORY_DUPLICATE_COUNT_MISMATCH"}
            val archivedIds=legacy.filter{it.status=="ARCHIVED"}.map{it.id}.toSet()
            check(dao.legacyImports().none{it.sourceId in archivedIds}){"LEGACY_ARCHIVED_MEMORY_NOT_PURGED"}

            // The original Room rows and their vectors remain intact until the sidecar checks above pass.
            graph.database.withTransaction {
                graph.database.memoryDao().deleteAllAfterVerifiedMigration()
                graph.database.semanticVectorDao().deleteAllMemoryVectorsAfterVerifiedMigration()
            }
            check(prefs.edit().putBoolean(LEGACY_MEMORY_MIGRATION_COMPLETE,true).commit()){"LEGACY_MEMORY_MIGRATION_STATE_WRITE_FAILED"}
            LegacyMigrationResult(importedActive,duplicateCount,archivedCount,previouslyImported,legacy.size)
        }
    }

    private fun legacyKind(kind:String):MemoryKind=when(kind){
        "CONTEXT"->MemoryKind.PROJECT_CONTEXT
        else->runCatching{MemoryKind.valueOf(kind)}.getOrDefault(MemoryKind.OTHER)
    }
    private fun legacyScope(old:MemoryItemEntity):SemanticScope=when(old.scopeType){
        "GLOBAL"->SemanticScope(ScopeType.USER,"local")
        "USER"->SemanticScope(ScopeType.USER,old.scopeId?.takeIf{it.isNotBlank()}?:"local")
        "PROJECT"->SemanticScope(ScopeType.PROJECT,old.scopeId?.takeIf{it.isNotBlank()}?:error("LEGACY_PROJECT_SCOPE_MISSING:${old.id}"))
        else->SemanticScope(ScopeType.USER,"local")
    }
    private fun legacyOriginMetadata(old:MemoryItemEntity)=com.google.gson.Gson().toJson(mapOf(
        "legacySourceType" to old.sourceType,"legacySourceReference" to old.sourceReference,"legacyRelevance" to old.relevance,
    ))

    private companion object { const val LEGACY_MEMORY_MIGRATION_COMPLETE="legacy_memory_migration_complete_v1" }

    internal suspend fun importApprovedLegacy(access:ScopeAccess):Int {
        val before=database.dao().legacyImports().mapNotNull{it.sourceId}.toSet()
        migrateLegacyMemories()
        return database.dao().legacyImports().count{it.sourceId !in before&&access.permits(it.scopeType,it.scopeId)}
    }
    /** Remove project-owned canonical memory, brief, conversation source records and indexes. */
    internal suspend fun deleteProjectScope(projectId:String)=withContext(Dispatchers.IO){
        val dao=database.dao()
        val memories=dao.memoriesInScope(ScopeType.PROJECT.name,projectId)
        val sources=dao.conversationSourcesInScope(ScopeType.PROJECT.name,projectId)
        val origins=(memories.map{it.id}+sources.map{"history:${it.messageId}"}).distinct()
        database.enableSecureDelete()
        database.withTransaction {
            dao.deleteConversationSourcesInScope(ScopeType.PROJECT.name,projectId)
            dao.deleteBrief(projectId)
            dao.deleteMemoriesInScope(ScopeType.PROJECT.name,projectId)
            if(origins.isNotEmpty())dao.deleteVectors(origins)
        }
        database.compactAfterDeletion()
        check(dao.memoriesInScope(ScopeType.PROJECT.name,projectId).isEmpty()){"PROJECT_MEMORY_CLEANUP_FAILED"}
        check(dao.conversationSourcesInScope(ScopeType.PROJECT.name,projectId).isEmpty()){"PROJECT_MEMORY_SOURCES_CLEANUP_FAILED"}
        check(dao.brief(projectId)==null){"PROJECT_BRIEF_CLEANUP_FAILED"}
        if(origins.isNotEmpty())check(dao.vectorsForOrigins(origins).isEmpty()){"PROJECT_MEMORY_VECTOR_CLEANUP_FAILED"}
    }

    /** Remove conversation-indexed history and session memories; broader scopes survive. */
    internal suspend fun deleteConversation(conversationId:String)=withContext(Dispatchers.IO){
        val dao=database.dao()
        val sources=dao.conversationSourcesForConversation(conversationId)
        val sessions=dao.memoriesInScope(ScopeType.SESSION.name,conversationId)
        val origins=(sources.map{"history:${it.messageId}"}+sessions.map{it.id}).distinct()
        database.enableSecureDelete()
        database.withTransaction {
            dao.deleteConversation(conversationId)
            dao.deleteMemoriesInScope(ScopeType.SESSION.name,conversationId)
            if(origins.isNotEmpty())dao.deleteVectors(origins)
        }
        database.compactAfterDeletion()
        check(dao.conversationSourcesForConversation(conversationId).isEmpty()){"CONVERSATION_MEMORY_SOURCE_CLEANUP_FAILED"}
        check(dao.memoriesInScope(ScopeType.SESSION.name,conversationId).isEmpty()){"CONVERSATION_SESSION_MEMORY_CLEANUP_FAILED"}
        if(origins.isNotEmpty())check(dao.vectorsForOrigins(origins).isEmpty()){"CONVERSATION_MEMORY_VECTOR_CLEANUP_FAILED"}
    }

    suspend fun saveBrief(brief:ProjectBrief)=withContext(Dispatchers.IO){check(graph.database.projectDao().get(brief.projectId)!=null);database.dao().brief(brief.copy(updatedAt=System.currentTimeMillis()))}
    /** Opt-in source indexing. Stored content is rechecked against canonical Room before retrieval. */
    suspend fun indexConversation(id:String):Int=withContext(Dispatchers.IO){
        val conversation=graph.database.conversationDao().get(id) ?: error("CONVERSATION_NOT_FOUND")
        val project=conversation.projectId?.let{graph.database.projectDao().get(it)}
        val scope=if(project?.workspaceKind=="PROJECT")SemanticScope(ScopeType.PROJECT,project.id)else SemanticScope(ScopeType.SESSION,id)
        var count=0
        for(message in graph.database.messageDao().observeForConversation(id).first().filter{it.status=="COMPLETE"&&it.role in setOf("USER","ASSISTANT")}.takeLast(2000)) {
            currentCoroutineContext().ensureActive()
            val source=ConversationSource(message.id,id,scope.type.name,scope.id,message.role,message.content,message.createdAt,fingerprint(message.content))
            database.dao().conversation(source)
            embedding(SemanticInput.Text(message.content,"conversation"),EmbeddingTask.DOCUMENT)?.let{v->database.dao().vector(ContextVector("history:${source.messageId}",v.space.id,source.contentHash,v.vector.size,SemanticVectors.encode(v.vector),System.currentTimeMillis()));database.dao().conversation(source.copy(indexState="INDEXED"))}
            count++
        };count
    }
    suspend fun olderHistory(query:String,access:ScopeAccess,excluded:Set<String>,vector:EmbeddingResult?):List<ContextItem> {
        val candidates=database.dao().conversations(access.allowed.map{it.key})
        val live=mutableMapOf<String,Map<String,String>>()
        return candidates.mapNotNull { source->
            currentCoroutineContext().ensureActive()
            if(source.messageId in excluded)return@mapNotNull null
            val messages=live.getOrPut(source.conversationId){ emptyMap() }.takeIf{it.isNotEmpty()} ?: run {
                val owner=graph.database.conversationDao().get(source.conversationId)
                if(owner==null){database.dao().deleteVectors("history:${source.messageId}");database.dao().deleteConversation(source.conversationId);return@mapNotNull null}
                val project=owner.projectId?.let{graph.database.projectDao().get(it)}
                val actualScope=if(project?.workspaceKind=="PROJECT")SemanticScope(ScopeType.PROJECT,project.id)else SemanticScope(ScopeType.SESSION,owner.id)
                if(actualScope.type.name!=source.scopeType||actualScope.id!=source.scopeId||!access.permits(actualScope.type.name,actualScope.id))return@mapNotNull null
                graph.database.messageDao().observeForConversation(owner.id).first().filter{it.status=="COMPLETE"}.associate{it.id to it.content}.also{live[owner.id]=it}
            }
            if(messages[source.messageId]?.let{fingerprint(it)}!=source.contentHash){database.dao().deleteVectors("history:${source.messageId}");return@mapNotNull null}
            val lexical=MemoryContent.lexical(query,source.text)
            val stored=vector?.let{database.dao().vector("history:${source.messageId}",it.space.id,source.contentHash)}
            val score=lexical+(if(stored!=null)SemanticVectors.cosine(vector,EmbeddingResult(vector.space,SemanticVectors.decode(stored.bytes,stored.dimension),0))else 0.0)
            if(score<=0)return@mapNotNull null
            ContextItem("H-${source.messageId}",ContextKind.OLD_CONVERSATION,source.text,SemanticScope(ScopeType.valueOf(source.scopeType),source.scopeId),ContextTrust.CONVERSATION,priority=20,score=score,provenance=ContextProvenance(sourceId=source.conversationId,messageIds=listOf(source.messageId)))
        }.sortedByDescending{it.score}.take(3)
    }
    suspend fun build(request:ContextRequest,evidence:List<Evidence>?=null,memoryEnabled:Boolean=true):ContextBundle=withContext(Dispatchers.IO){
        migrateLegacyMemories()
        val started=System.nanoTime();val access=request.access;notice.value=null
        check(access.projectId==null||graph.database.projectDao().get(access.projectId)!=null){"PROJECT_NOT_FOUND"}
        val all=mutableListOf<ContextItem>();val memoryStart=System.nanoTime()
        val memoryAccess=access.copy(readableTypes=request.memoryScopes)
        val activeMemories=if(memoryEnabled)memory.lookup(memoryAccess)else emptyList()
        val approvedPreferences=memory.approvedPromptMemories(activeMemories)
        val approvedPreferenceIds=approvedPreferences.map{it.id}.toSet()
        val searchableMemories=activeMemories.filterNot{record->record.id in approvedPreferenceIds}
        val memorySkipped=memoryEnabled&&MemoryRelevance.smallTalk(request.query)
        val vector=if(memoryEnabled&&!memorySkipped&&searchableMemories.isNotEmpty())memory.queryEmbedding(request.query)else null
        val selection=if(memoryEnabled)memory.select(request.query,memoryAccess,queryVector=vector,allowEmbedding=false,excludedIds=approvedPreferenceIds,candidateRecords=searchableMemories)else MemorySelection(emptyList(),emptyList())
        fun memoryItem(hit:MemoryHit,id:String):ContextItem {val m=hit.record;return ContextItem(id,ContextKind.MEMORY,m.text,SemanticScope(ScopeType.valueOf(m.scopeType),m.scopeId),ContextTrust.APPROVED_MEMORY,if(m.pinned)85 else 65,hit.score,ContextProvenance(sourceId=m.id,segmentId=m.sourceSegmentId,messageIds=listOfNotNull(m.sourceMessageId)),memoryRelevance=hit.relevance)}
        selection.included.forEachIndexed{i,hit->all+=memoryItem(hit,"M${i+1}")}
        val relevanceDropped=selection.dropped.mapIndexed{i,hit->DroppedContext(memoryItem(hit,"MD${i+1}"),"BELOW_RELEVANCE_THRESHOLD")}
        val memoryMs=(System.nanoTime()-memoryStart)/1_000_000
        access.projectId?.let{id->database.dao().brief(id)?.let{all+=ContextItem("P1",ContextKind.PROJECT,it.markdown(),SemanticScope(ScopeType.PROJECT,id),ContextTrust.USER_PROJECT_DATA,70,provenance=ContextProvenance(sourceId=id))}}
        val retrieveStart=System.nanoTime()
        val exactPage=RetrievalQuery.exactPageNumber(request.query)
        val sourceScope=access.projectId?.let{SemanticScope(ScopeType.PROJECT,it)} ?: SemanticScope(ScopeType.GLOBAL)
        val layer=semanticLayerProvider()
        var sourceNotice:String?=null
        val canUseV2=request.sourceRetrievalMode==SourceRetrievalMode.SEMANTIC_V2_PREFERRED && layer.selection.choice==SemanticProviderChoice.EG2
        if(request.sourceRetrievalMode==SourceRetrievalMode.SEMANTIC_V2_PREFERRED && !canUseV2) {
            sourceNotice="SOURCE_SEMANTIC_V2_UNAVAILABLE;LEGACY_FALLBACK"
        }
        val v2Hits=if(canUseV2)try {
            layer.search(SemanticInput.Text(request.query),sourceScope,setOf(SemanticModality.TEXT,SemanticModality.CODE),limit=4,documentIds=request.selectedDocumentIds)
                .filter { it.content!=null && it.modality in setOf(SemanticModality.TEXT,SemanticModality.CODE) && (exactPage==null || it.page==exactPage) }
        }catch(cancel:CancellationException){throw cancel}catch(failure:Exception){
            sourceNotice="SOURCE_SEMANTIC_V2_SKIPPED:${if(failure is SemanticFailure)failure.code.name else failure.javaClass.simpleName};LEGACY_FALLBACK"
            emptyList()
        }else emptyList()
        if(canUseV2 && v2Hits.isEmpty() && sourceNotice==null) sourceNotice="SOURCE_SEMANTIC_V2_NO_HITS;LEGACY_FALLBACK"
        val sourceEvidenceByItem=linkedMapOf<String,Evidence>()
        val sourceEvidenceIssues=mutableListOf<SourceEvidenceIssue>()
        if(v2Hits.isNotEmpty()) {
            suspend fun addV2Excerpt(hit:RetrievalHit,excerpt:ContextEvidenceExcerpt,related:List<RetrievalHit>) {
                val relatedIds=related.map{it.segmentId}.distinct()
                if(excerpt.incompleteReasons.isNotEmpty()) sourceEvidenceIssues+=SourceEvidenceIssue(
                    sourceId=hit.sourceId,segmentId=hit.segmentId,documentId=hit.documentId,
                    reasons=excerpt.incompleteReasons,missingCellAddresses=excerpt.missingCellAddresses,
                    missingCellReferences=excerpt.missingCellReferences,ambiguousCellReferences=excerpt.ambiguousCellReferences,
                    unresolvedCellReferences=excerpt.unresolvedCellReferences,relatedSegmentIds=relatedIds)
                if(excerpt.text.isBlank()) return
                val semanticEvidenceId="SV2-${com.localai.workspace.semantic.v2.fingerprint(hit.segmentId+hit.embeddingSpace).take(16).uppercase()}"
                val evidenceHit=if(hit.legacySegmentId!=null) {
                    val legacyId=hit.legacySegmentId
                    val document=graph.database.documentDao().documentForSegment(legacyId)
                    val segment=graph.database.documentDao().segment(legacyId)
                    if(document==null || segment==null || document.projectId!=access.projectId || document.id!=hit.documentId ||
                        (request.selectedDocumentIds.isNotEmpty() && document.id !in request.selectedDocumentIds)) null else {
                        val base=segment.charStart
                        val start=excerpt.charStart?.let{offset->base?.plus(offset)}
                        val end=excerpt.charEnd?.let{offset->base?.plus(offset)}
                        evidence?.firstOrNull{it.segmentId==legacyId && it.documentId==hit.documentId}
                            ?.copy(documentTitle=hit.sourceName.ifBlank{document.displayName},excerpt=excerpt.text,charStart=start,charEnd=end,semanticSegmentId=hit.segmentId,lineStart=hit.lineStart,lineEnd=hit.lineEnd,cellReferences=excerpt.cellReferences)
                            ?: Evidence(
                                id="LCL-${com.localai.workspace.semantic.VectorPersistence.hash("${access.projectId.orEmpty()}:$legacyId").take(8).uppercase()}",
                                segmentId=legacyId,documentId=document.id,documentTitle=hit.sourceName.ifBlank{document.displayName},excerpt=excerpt.text,
                                pageStart=hit.page ?: segment.pageStart,pageEnd=segment.pageEnd,charStart=start,
                                charEnd=end,retrievalScore=hit.fusedScore,semanticSegmentId=hit.segmentId,lineStart=hit.lineStart,lineEnd=hit.lineEnd,
                                cellReferences=excerpt.cellReferences)
                    }
                } else {
                    val document=hit.documentId?.let{graph.database.documentDao().get(it)}
                    if(document==null || document.projectId!=access.projectId ||
                        (request.selectedDocumentIds.isNotEmpty() && document.id !in request.selectedDocumentIds)) null else Evidence(
                        id=semanticEvidenceId,segmentId=null,documentId=document.id,
                        documentTitle=hit.sourceName.ifBlank{document.displayName},excerpt=excerpt.text,
                        pageStart=hit.page,pageEnd=hit.page,charStart=null,charEnd=null,
                        retrievalScore=hit.fusedScore,semanticSegmentId=hit.segmentId,lineStart=hit.lineStart,lineEnd=hit.lineEnd,
                        cellReferences=excerpt.cellReferences)
                }
                val itemId=evidenceHit?.id ?: semanticEvidenceId
                val segmentStart=hit.legacySegmentId?.let{graph.database.documentDao().segment(it)?.charStart}
                val absoluteStart=excerpt.charStart?.let{offset->segmentStart?.plus(offset)}
                val absoluteEnd=excerpt.charEnd?.let{offset->segmentStart?.plus(offset)}
                all+=ContextItem(itemId,ContextKind.SOURCE,excerpt.text,sourceScope,ContextTrust.UNTRUSTED_SOURCE,80,hit.fusedScore,
                    ContextProvenance(hit.sourceId,hit.segmentId,page=hit.page,lineStart=hit.lineStart,lineEnd=hit.lineEnd,startMs=hit.startMs,endMs=hit.endMs,sourceName=hit.sourceName,documentId=hit.documentId,charStart=absoluteStart,charEnd=absoluteEnd,excerpted=excerpt.shortened,originalCharacters=excerpt.originalCharacters,cellAddresses=excerpt.cellAddresses,evidenceIncompleteReasons=excerpt.incompleteReasons,missingCellAddresses=excerpt.missingCellAddresses,cellReferences=excerpt.cellReferences,missingCellReferences=excerpt.missingCellReferences,ambiguousCellReferences=excerpt.ambiguousCellReferences,unresolvedCellReferences=excerpt.unresolvedCellReferences,relatedSegmentIds=relatedIds))
                evidenceHit?.let{sourceEvidenceByItem[itemId]=it}
            }

            val handledStructuredDocuments=mutableSetOf<String>()
            val attemptedStructuredDocuments=mutableSetOf<String>()
            for(hit in v2Hits) {
                val content=hit.content ?: continue
                val documentId=hit.documentId
                val structured=ContextEvidenceExcerptSelector.isStructuredCellPassage(content)
                if(structured && documentId!=null) {
                    if(documentId in handledStructuredDocuments) continue
                    if(attemptedStructuredDocuments.add(documentId)) {
                        val relatedCandidates=v2Hits.filter{it.documentId==documentId && it.content?.let(ContextEvidenceExcerptSelector::isStructuredCellPassage)==true}.distinctBy{it.segmentId}
                        val segmentOrder=mutableMapOf<String,Int>()
                        relatedCandidates.forEach{candidate->candidate.legacySegmentId?.let{id->graph.database.documentDao().segment(id)?.segmentIndex?.let{segmentOrder[candidate.segmentId]=it}}}
                        val related=relatedCandidates.sortedWith(compareBy<RetrievalHit>{segmentOrder[it.segmentId] ?: Int.MAX_VALUE}.thenBy{it.segmentId})
                        val excerpt=ContextEvidenceExcerptSelector.selectRetrievedPassages(request.query,related.mapNotNull{it.content})
                        if(excerpt!=null) { handledStructuredDocuments+=documentId;addV2Excerpt(related.firstOrNull() ?: hit,excerpt,related);continue }
                    }
                }
                addV2Excerpt(hit,ContextEvidenceExcerptSelector.select(request.query,content),listOf(hit))
            }
        } else if(request.sourceRetrievalMode!=SourceRetrievalMode.DISABLED) {
            // The retrieval policy, not whether a legacy list happens to be null or empty,
            // decides whether Semantic V2 runs and whether lexical fallback is required.
            fun matchesRequestedPage(item:Evidence):Boolean {
                if(exactPage==null)return true
                val start=item.pageStart ?: return false
                val end=item.pageEnd ?: start
                return exactPage in start..end
            }
            val precomputed=evidence.orEmpty().filter(::matchesRequestedPage)
            val sources=when {
                precomputed.isNotEmpty() -> precomputed
                access.projectId!=null -> graph.retrieval.retrieve(access.projectId,request.query,documentIds=request.selectedDocumentIds)
                else -> emptyList()
            }
            val allowedSources=sources.filter { e->
                val original=graph.database.documentDao().get(e.documentId)
                check(original!=null&&original.projectId==access.projectId){"SOURCE_SCOPE_MISMATCH"}
                (request.selectedDocumentIds.isEmpty() || e.documentId in request.selectedDocumentIds) && matchesRequestedPage(e)
            }
            suspend fun addLegacyExcerpt(primary:Evidence,excerpt:ContextEvidenceExcerpt,related:List<Evidence>) {
                val relatedIds=related.map{it.segmentId.toString()}.distinct()
                if(excerpt.incompleteReasons.isNotEmpty()) sourceEvidenceIssues+=SourceEvidenceIssue(
                    sourceId=primary.documentId,segmentId=primary.segmentId.toString(),documentId=primary.documentId,
                    reasons=excerpt.incompleteReasons,missingCellAddresses=excerpt.missingCellAddresses,
                    missingCellReferences=excerpt.missingCellReferences,ambiguousCellReferences=excerpt.ambiguousCellReferences,
                    unresolvedCellReferences=excerpt.unresolvedCellReferences,relatedSegmentIds=relatedIds)
                if(excerpt.text.isBlank()) return
                val singleSegment=related.size==1
                val start=excerpt.charStart?.takeIf{singleSegment}?.let{primary.charStart?.plus(it)}
                val end=excerpt.charEnd?.takeIf{singleSegment}?.let{primary.charStart?.plus(it)}
                val compact=primary.copy(excerpt=excerpt.text,charStart=start,charEnd=end,cellReferences=excerpt.cellReferences)
                all+=ContextItem(primary.id,ContextKind.SOURCE,excerpt.text,sourceScope,ContextTrust.UNTRUSTED_SOURCE,80,primary.retrievalScore,
                    ContextProvenance(primary.documentId,primary.segmentId.toString(),page=primary.pageStart,lineStart=primary.lineStart,lineEnd=primary.lineEnd,sourceName=primary.documentTitle,documentId=primary.documentId,charStart=start,charEnd=end,excerpted=excerpt.shortened,originalCharacters=excerpt.originalCharacters,cellAddresses=excerpt.cellAddresses,evidenceIncompleteReasons=excerpt.incompleteReasons,missingCellAddresses=excerpt.missingCellAddresses,cellReferences=excerpt.cellReferences,missingCellReferences=excerpt.missingCellReferences,ambiguousCellReferences=excerpt.ambiguousCellReferences,unresolvedCellReferences=excerpt.unresolvedCellReferences,relatedSegmentIds=relatedIds))
                sourceEvidenceByItem[primary.id]=compact
            }
            val handledStructuredDocuments=mutableSetOf<String>()
            val attemptedStructuredDocuments=mutableSetOf<String>()
            for(source in allowedSources) {
                if(ContextEvidenceExcerptSelector.isStructuredCellPassage(source.excerpt)) {
                    if(source.documentId in handledStructuredDocuments) continue
                    if(attemptedStructuredDocuments.add(source.documentId)) {
                        val relatedCandidates=allowedSources.filter{it.documentId==source.documentId && ContextEvidenceExcerptSelector.isStructuredCellPassage(it.excerpt)}.distinctBy{it.segmentId}
                        val segmentOrder=mutableMapOf<Long,Int>()
                        relatedCandidates.forEach{candidate->candidate.segmentId?.let{id->graph.database.documentDao().segment(id)?.segmentIndex?.let{segmentOrder[id]=it}}}
                        val related=relatedCandidates.sortedWith(compareBy<Evidence>{it.segmentId?.let(segmentOrder::get) ?: Int.MAX_VALUE}.thenBy{it.segmentId ?: Long.MAX_VALUE})
                        val excerpt=ContextEvidenceExcerptSelector.selectRetrievedPassages(request.query,related.map{it.excerpt})
                        if(excerpt!=null){handledStructuredDocuments+=source.documentId;addLegacyExcerpt(related.firstOrNull() ?: source,excerpt,related);continue}
                    }
                }
                addLegacyExcerpt(source,ContextEvidenceExcerptSelector.select(request.query,source.excerpt),listOf(source))
            }
        }
        val retrievalMs=(System.nanoTime()-retrieveStart)/1_000_000
        val historyStart=System.nanoTime();val recentIds=mutableSetOf<String>()
        request.conversationId?.takeIf{request.includeConversation}?.let{id->val owner=graph.database.conversationDao().get(id);if(owner!=null){
            val ownerProject=owner.projectId?.let{graph.database.projectDao().get(it)}
            val owned=owner.projectId==access.projectId||(access.projectId==null&&access.sessionId==id&&ownerProject?.workspaceKind=="CHAT")
            check(owned){"CONVERSATION_SCOPE_MISMATCH"}
            val rows=graph.database.messageDao().observeForConversation(id).first().filter{it.status=="COMPLETE"}.takeLast(20)
            var user:com.localai.workspace.data.MessageEntity?=null
            for(row in rows){if(row.role=="USER")user=row else if(row.role=="ASSISTANT"&&user!=null){val u=user!!;recentIds+=u.id;recentIds+=row.id
                all+=ContextItem("R-${u.id}",ContextKind.RECENT_CONVERSATION,(u.effectiveContent ?: u.content)+"\n"+row.content,SemanticScope(ScopeType.SESSION,id),ContextTrust.CONVERSATION,60,order=row.createdAt,provenance=ContextProvenance(sourceId=id,messageIds=listOf(u.id,row.id)),history=listOf(ChatMessage(MessageRole.USER,u.effectiveContent ?: u.content,u.imagePath,u.audioPath),ChatMessage(MessageRole.ASSISTANT,row.content)));user=null}
            }
        }}
        if(request.includeConversation)all+=olderHistory(request.query,access,recentIds,vector)
        val historyMs=(System.nanoTime()-historyStart)/1_000_000
        val result=ContextBuilder().build(request.copy(approvedPreferences=approvedPreferences),all)
        val renderStart=System.nanoTime();result.conversation();val renderMs=(System.nanoTime()-renderStart)/1_000_000
        val includedSourceIds=result.included.filter{it.kind==ContextKind.SOURCE}.map{it.id}.toSet()
        result.copy(dropped=result.dropped+relevanceDropped,memoryLookupSkipped=memorySkipped,timings=result.timings.copy(totalMs=(System.nanoTime()-started)/1_000_000,memoryMs=memoryMs,retrievalMs=retrievalMs,historyMs=historyMs,renderMs=renderMs),notice=sourceNotice ?: notice.value,sourceEvidence=sourceEvidenceByItem.filterKeys{it in includedSourceIds}.values.toList(),sourceEvidenceIssues=sourceEvidenceIssues.distinct()).also{last.value=it}
    }
}
