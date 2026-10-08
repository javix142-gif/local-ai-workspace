package com.localai.workspace.agents

import com.localai.workspace.skills.SkillDefinition
import com.localai.workspace.sources.SourceType
import com.localai.workspace.semantic.v2.ScopeType

data class AgentDefinition(
    val id:String, val name:String, val description:String="", val enabled:Boolean=true,
    val systemRole:String="", val skillIds:Set<String> = emptySet(),
    val allowedTools:Set<String> = SkillDefinition.STANDARD_TOOLS,
    val allowedSourceTypes:Set<SourceType> = SourceType.local,
    val memoryScopes:Set<ScopeType> = setOf(ScopeType.GLOBAL,ScopeType.USER,ScopeType.PROJECT,ScopeType.SESSION),
    val contextPolicy:String="RECENT_RELEVANT_V1",
    val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt,
)
data class AgentResolution(val agent:AgentDefinition,val reason:String)
object AgentResolver {
    const val GENERAL="agent.general"
    fun resolve(agents:List<AgentDefinition>,explicit:String?=null,preferred:String?=null):AgentResolution {
        agents.firstOrNull{it.id==explicit && it.enabled}?.let{return AgentResolution(it,"EXPLICIT_SELECTION")}
        agents.firstOrNull{it.id==preferred && it.enabled}?.let{return AgentResolution(it,"PROJECT_AGENT")}
        return AgentResolution(agents.first{it.id==GENERAL},"GENERAL_FALLBACK")
    }
}
