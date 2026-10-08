package com.localai.workspace.agents

import com.localai.workspace.skills.*
import com.localai.workspace.sources.*
import com.localai.workspace.capabilities.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Request preparation only: called before any generation gate. Owns no model, engine or embeddings. */
class AgentSkillCoordinator(val skills:SkillRegistry,val agents:AgentRegistry) {
    val last=MutableStateFlow<AgentSkillTrace?>(null)
    suspend fun resolve(request:SkillRoutingRequest,projectId:String?,explicitAgent:String?):AgentSkillTrace {
        val agent=agents.resolve(explicitAgent,projectId)
        val selection=SkillRouterV1().select(request.copy(capabilities=CapabilityPolicy.localCapabilities),agent.agent,skills.list())
        val skillSources=if(selection.active.isEmpty())SourceType.local else selection.active.flatMap{it.allowedSourceTypes}.toSet()
        return AgentSkillTrace(agent,selection,SourceRegistry().list(agent.agent.allowedSourceTypes.intersect(skillSources))).also{last.value=it}
    }
}
data class AgentSkillTrace(val resolution:AgentResolution,val selection:SkillSelection,val sources:List<SourceAvailability>,val tools:List<ToolAvailability> = emptyList(),val messageId:String?=null) {
    fun withSuccessfulTools(ids:Set<String>)=copy(selection=selection.copy(evaluations=selection.evaluations.map{evaluation->
        evaluation.copy(executed=evaluation.active && selection.active.firstOrNull{it.id==evaluation.skillId}?.allowedTools?.intersect(ids)?.isNotEmpty()==true)
    }))
    /** Never exposes role/instructions/query/resource text. */
    fun safeReport():Map<String,Any> = mapOf("agentId" to resolution.agent.id,"agentReason" to resolution.reason,"routingMs" to selection.routingMs,"skills" to selection.evaluations,"tools" to tools,"sources" to sources,"router" to "DETERMINISTIC_V1")
}
