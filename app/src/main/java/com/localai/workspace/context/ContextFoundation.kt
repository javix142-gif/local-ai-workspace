package com.localai.workspace.context

import android.content.Context
import com.localai.workspace.AppGraph
import com.localai.workspace.domain.model.*
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

/** Additive orchestration. It neither replaces semantic indexes nor owns a second embedding engine. */
class ContextFoundation(private val graph:AppGraph) {
    val database=MemoryContextDatabase.create(graph.contextForMeasurements,graph.validationId?.let{"context-memory-$it.db"} ?: "memory_context.db")
    private val prefs=graph.contextForMeasurements.getSharedPreferences("context_foundation_v1",Context.MODE_PRIVATE)
    val enabled=MutableStateFlow(prefs.getBoolean("enabled",true))
    val notice=MutableStateFlow<String?>(null)
    val last=MutableStateFlow<ContextBundle?>(null)
    val memory=MemoryManager(database){input,task->embedding(input,task)}
    fun enable(value:Boolean){check(prefs.edit().putBoolean("enabled",value).commit());enabled.value=value}
    suspend fun embedding(input:SemanticInput,task:EmbeddingTask):EmbeddingResult?=withContext(Dispatchers.IO){
        try {
            val layer=graph.validationOwner?.semanticV2 ?: graph.semanticV2
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
        val started=System.nanoTime();val access=request.access
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
        val layer=graph.validationOwner?.semanticV2 ?: graph.semanticV2
        val v2Hits=if(evidence==null&&layer.selection.choice==SemanticProviderChoice.EG2)try{layer.search(SemanticInput.Text(request.query),sourceScope,limit=4)}catch(cancel:CancellationException){throw cancel}catch(failure:Exception){notice.value="SOURCE_LEXICAL_FALLBACK:${if(failure is SemanticFailure)failure.code.name else failure.javaClass.simpleName}";emptyList()}else emptyList()
        v2Hits.forEachIndexed{i,h->all+=ContextItem("S${i+1}",ContextKind.SOURCE,h.content.orEmpty(),sourceScope,ContextTrust.UNTRUSTED_SOURCE,80,h.fusedScore,ContextProvenance(h.sourceId,h.segmentId,page=h.page,lineStart=h.lineStart,lineEnd=h.lineEnd,startMs=h.startMs,endMs=h.endMs))}
        val sources=evidence ?: if(v2Hits.isEmpty())access.projectId?.let{graph.retrieval.retrieve(it,request.query)} ?: emptyList()else emptyList()
        sources.forEach { e->val original=graph.database.documentDao().get(e.documentId);check(original!=null&&original.projectId==access.projectId){"SOURCE_SCOPE_MISMATCH"};all+=ContextItem(e.id,ContextKind.SOURCE,e.excerpt,sourceScope,ContextTrust.UNTRUSTED_SOURCE,80,e.retrievalScore,ContextProvenance(e.documentId,e.segmentId.toString(),page=e.pageStart,sourceName=e.documentTitle)) }
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
        result.copy(dropped=result.dropped+relevanceDropped,memoryLookupSkipped=memorySkipped,timings=result.timings.copy(totalMs=(System.nanoTime()-started)/1_000_000,memoryMs=memoryMs,retrievalMs=retrievalMs,historyMs=historyMs,renderMs=renderMs),notice=notice.value).also{last.value=it}
    }
}
