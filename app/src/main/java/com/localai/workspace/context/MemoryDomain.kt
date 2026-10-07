package com.localai.workspace.context

import com.localai.workspace.semantic.v2.*

enum class MemoryKind { FACT,PREFERENCE,DECISION,EVENT,RELATIONSHIP,PROJECT_CONTEXT,SUMMARY,PROCEDURE,TASK_STATE,OTHER }
enum class MemoryStatus { PENDING,ACTIVE,SUPERSEDED,EXPIRED,DELETED }
data class ScopeAccess(val userId:String="local",val projectId:String?=null,val sessionId:String?=null,val agentId:String?=null,val taskId:String?=null) {
    val allowed:List<SemanticScope> get()=buildList {
        sessionId?.let{add(SemanticScope(ScopeType.SESSION,it))};taskId?.let{add(SemanticScope(ScopeType.TASK,it))};agentId?.let{add(SemanticScope(ScopeType.AGENT,it))};projectId?.let{add(SemanticScope(ScopeType.PROJECT,it))};add(SemanticScope(ScopeType.USER,userId));add(SemanticScope(ScopeType.GLOBAL))
    }
    fun permits(type:String,id:String)=allowed.any{it.type.name==type&&it.id==id}
}
object MemoryContent {
    const val NORMALIZATION="NFC_WHITESPACE_CASE_SENSITIVE_V1"
    fun hash(text:String,kind:MemoryKind?=null)=if(kind==MemoryKind.PROCEDURE)fingerprint("EXACT_PROCEDURE_V1:"+text.trim())else fingerprint(NORMALIZATION+":"+java.text.Normalizer.normalize(text.trim().replace(Regex("\\s+")," "),java.text.Normalizer.Form.NFC))
    fun secret(text:String)=Regex("(?i)(password|contraseña|api[_ -]?key|auth[_ -]?token|private key|recovery phrase|bearer\\s+[a-z0-9]|sk-[a-z0-9])").containsMatchIn(text)
    fun words(text:String)=Regex("[\\p{L}\\p{N}_]{2,}").findAll(text.lowercase(java.util.Locale.ROOT)).map{it.value}.filter{it !in setOf("the","what","this","does","have","with","para","que","como","este","una","los","las","del")}.toSet()
    fun lexical(query:String,text:String):Double {val q=words(query);return if(q.isEmpty())0.0 else q.intersect(words(text)).size.toDouble()/q.size}
}
data class MemoryHit(val record:MemoryRecord,val score:Double,val semanticScore:Double?=null,val relevance:MemoryRelevanceScore?=null)
data class RollingSummary(val conversationId:String,val fromMessageId:String,val toMessageId:String,val text:String,val createdAt:Long,val version:Int=1)
