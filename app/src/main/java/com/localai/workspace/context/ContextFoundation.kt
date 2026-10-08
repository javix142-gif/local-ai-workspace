package com.localai.workspace.context

import android.content.Context
import com.localai.workspace.AppGraph
import com.localai.workspace.domain.model.*
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

/** Additive orchestration. It neither replaces semantic indexes nor owns a second embedding engine. */
class ContextFoundation(private val graph:AppGraph, private val semanticLayerProvider:()->SemanticLayer={graph.validationOwner?.semanticV2 ?: graph.semanticV2}) {
    val database=MemoryContextDatabase.create(graph.contextForMeasurements,graph.validationId?.let{"context-memory-$it.db"} ?: "memory_context.db")
    private val prefs=graph.contextForMeasurements.getSharedPreferences("context_foundation_v1",Context.MODE_PRIVATE)
    val enabled=MutableStateFlow(prefs.getBoolean("enabled",true))
    val notice=MutableStateFlow<String?>(null)
    val last=MutableStateFlow<ContextBundle?>(null)
    val memory=MemoryManager(database){input,task->embedding(input,task)}
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
    suspend fun importApprovedLegacy(access:ScopeAccess):Int=withContext(Dispatchers.IO){
        var imported=0
        for(old in graph.database.memoryDao().observeRelevant(access.projectId.orEmpty()).first().filter{it.status=="ACTIVE"}) {
            if(database.dao().importedLegacy(old.id)>0)continue
            val type=runCatching{ScopeType.valueOf(old.scopeType)}.getOrDefault(ScopeType.GLOBAL)
            val scope=SemanticScope(type,old.scopeId.orEmpty())
            if(!access.permits(scope.type.name,scope.id))continue
            memory.create(old.content,scope,MemoryKind.OTHER,sourceType="APPROVED_LEGACY",sourceId=old.id);imported++
        };imported
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
        val started=System.nanoTime();val access=request.access;notice.value=null
        check(access.projectId==null||graph.database.projectDao().get(access.projectId)!=null){"PROJECT_NOT_FOUND"}
        val all=mutableListOf<ContextItem>();val memoryStart=System.nanoTime()
        val memorySkipped=memoryEnabled&&MemoryRelevance.smallTalk(request.query)
        val vector=if(memoryEnabled&&!memorySkipped)memory.queryEmbedding(request.query)else null
        val selection=if(memoryEnabled)memory.select(request.query,access.copy(readableTypes=request.memoryScopes),queryVector=vector,allowEmbedding=false)else MemorySelection(emptyList(),emptyList())
        fun memoryItem(hit:MemoryHit,id:String):ContextItem {val m=hit.record;return ContextItem(id,ContextKind.MEMORY,m.text,SemanticScope(ScopeType.valueOf(m.scopeType),m.scopeId),ContextTrust.APPROVED_MEMORY,if(m.pinned)85 else 65,hit.score,ContextProvenance(sourceId=m.id,segmentId=m.sourceSegmentId,messageIds=listOfNotNull(m.sourceMessageId)),memoryRelevance=hit.relevance)}
        selection.included.forEachIndexed{i,hit->all+=memoryItem(hit,"M${i+1}")}
        val relevanceDropped=selection.dropped.mapIndexed{i,hit->DroppedContext(memoryItem(hit,"MD${i+1}"),"BELOW_RELEVANCE_THRESHOLD")}
        val memoryMs=(System.nanoTime()-memoryStart)/1_000_000
        access.projectId?.let{id->database.dao().brief(id)?.let{all+=ContextItem("P1",ContextKind.PROJECT,it.markdown(),SemanticScope(ScopeType.PROJECT,id),ContextTrust.USER_PROJECT_DATA,70,provenance=ContextProvenance(sourceId=id))}}
        val retrieveStart=System.nanoTime()
        val sourceScope=access.projectId?.let{SemanticScope(ScopeType.PROJECT,it)} ?: SemanticScope(ScopeType.GLOBAL)
        val layer=semanticLayerProvider()
        var sourceNotice:String?=null
        val canUseV2=request.sourceRetrievalMode==SourceRetrievalMode.SEMANTIC_V2_PREFERRED && layer.selection.choice==SemanticProviderChoice.EG2
        if(request.sourceRetrievalMode==SourceRetrievalMode.SEMANTIC_V2_PREFERRED && !canUseV2) {
            sourceNotice="SOURCE_SEMANTIC_V2_UNAVAILABLE;LEGACY_FALLBACK"
        }
        val v2Hits=if(canUseV2)try {
            layer.search(SemanticInput.Text(request.query),sourceScope,setOf(SemanticModality.TEXT,SemanticModality.CODE),limit=4,documentIds=request.selectedDocumentIds)
                .filter { it.content!=null && it.modality in setOf(SemanticModality.TEXT,SemanticModality.CODE) }
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
                val evidenceHit=hit.legacySegmentId?.let { legacyId->
                    val document=graph.database.documentDao().documentForSegment(legacyId)
                    val segment=graph.database.documentDao().segment(legacyId)
                    if(document==null || segment==null || document.projectId!=access.projectId || document.id!=hit.documentId)null else {
                        val base=segment.charStart
                        val start=excerpt.charStart?.let{offset->base?.plus(offset)}
                        val end=excerpt.charEnd?.let{offset->base?.plus(offset)}
                        evidence?.firstOrNull{it.segmentId==legacyId && it.documentId==hit.documentId}
                            ?.copy(excerpt=excerpt.text,charStart=start,charEnd=end)
                            ?: Evidence(
                                id="LCL-${com.localai.workspace.semantic.VectorPersistence.hash("${access.projectId.orEmpty()}:$legacyId").take(8).uppercase()}",
                                segmentId=legacyId,documentId=document.id,documentTitle=document.displayName,excerpt=excerpt.text,
                                pageStart=hit.page ?: segment.pageStart,pageEnd=segment.pageEnd,charStart=start,
                                charEnd=end,retrievalScore=hit.fusedScore)
                    }
                }
                val itemId=evidenceHit?.id ?: "SV2-${com.localai.workspace.semantic.v2.fingerprint(hit.segmentId+hit.embeddingSpace).take(16)}"
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
            val sources=when {
                evidence?.isNotEmpty()==true -> evidence
                access.projectId!=null -> graph.retrieval.retrieve(access.projectId,request.query,documentIds=request.selectedDocumentIds)
                else -> emptyList()
            }
            val allowedSources=sources.filter { e->
                val original=graph.database.documentDao().get(e.documentId)
                check(original!=null&&original.projectId==access.projectId){"SOURCE_SCOPE_MISMATCH"}
                request.selectedDocumentIds.isEmpty() || e.documentId in request.selectedDocumentIds
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
                val compact=primary.copy(excerpt=excerpt.text,charStart=start,charEnd=end)
                all+=ContextItem(primary.id,ContextKind.SOURCE,excerpt.text,sourceScope,ContextTrust.UNTRUSTED_SOURCE,80,primary.retrievalScore,
                    ContextProvenance(primary.documentId,primary.segmentId.toString(),page=primary.pageStart,sourceName=primary.documentTitle,documentId=primary.documentId,charStart=start,charEnd=end,excerpted=excerpt.shortened,originalCharacters=excerpt.originalCharacters,cellAddresses=excerpt.cellAddresses,evidenceIncompleteReasons=excerpt.incompleteReasons,missingCellAddresses=excerpt.missingCellAddresses,cellReferences=excerpt.cellReferences,missingCellReferences=excerpt.missingCellReferences,ambiguousCellReferences=excerpt.ambiguousCellReferences,unresolvedCellReferences=excerpt.unresolvedCellReferences,relatedSegmentIds=relatedIds))
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
                        relatedCandidates.forEach{candidate->graph.database.documentDao().segment(candidate.segmentId)?.segmentIndex?.let{segmentOrder[candidate.segmentId]=it}}
                        val related=relatedCandidates.sortedWith(compareBy<Evidence>{segmentOrder[it.segmentId] ?: Int.MAX_VALUE}.thenBy{it.segmentId})
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
        val result=ContextBuilder().build(request,all)
        val renderStart=System.nanoTime();result.conversation();val renderMs=(System.nanoTime()-renderStart)/1_000_000
        val includedSourceIds=result.included.filter{it.kind==ContextKind.SOURCE}.map{it.id}.toSet()
        result.copy(dropped=result.dropped+relevanceDropped,memoryLookupSkipped=memorySkipped,timings=result.timings.copy(totalMs=(System.nanoTime()-started)/1_000_000,memoryMs=memoryMs,retrievalMs=retrievalMs,historyMs=historyMs,renderMs=renderMs),notice=sourceNotice ?: notice.value,sourceEvidence=sourceEvidenceByItem.filterKeys{it in includedSourceIds}.values.toList(),sourceEvidenceIssues=sourceEvidenceIssues.distinct()).also{last.value=it}
    }
}
