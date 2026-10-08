package com.localai.workspace.agents

import com.google.gson.Gson
import com.localai.workspace.skills.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentRegistry(private val db:AgentsSkillsDatabase) {
    private val gson=Gson()
    private val initialization=Mutex()
    private var initialized=false
    val changes=combine(db.dao().agents(),db.dao().skills()){rows,skills->
        rows.map{availableSkills(gson.fromJson(it.definitionJson,AgentDefinition::class.java),skills.map{it.id}.toSet())}
    }
    suspend fun initialize() {initialization.withLock{if(!initialized){db.dao().seedAgent(record(BuiltInSkills.general));initialized=true}}}
    suspend fun list():List<AgentDefinition>{initialize();val skills=db.dao().skillList().map{it.id}.toSet();return db.dao().agentList().map{availableSkills(gson.fromJson(it.definitionJson,AgentDefinition::class.java),skills)}}
    suspend fun get(id:String):AgentDefinition? {initialize();return db.dao().agent(id)?.let{availableSkills(gson.fromJson(it.definitionJson,AgentDefinition::class.java),db.dao().skillList().map{it.id}.toSet())}}
    suspend fun update(agent:AgentDefinition) {
        require(agent.id!=AgentResolver.GENERAL){"GENERAL_AGENT_READ_ONLY"}
        require(agent.name.isNotBlank()&&agent.name.length<=100&&agent.systemRole.toByteArray().size<=16*1024){"AGENT_METADATA_INVALID"}
        val previous=get(agent.id)
        db.dao().putAgent(record(agent.copy(createdAt=previous?.createdAt ?: agent.createdAt,updatedAt=System.currentTimeMillis())))
    }
    suspend fun enable(id:String,enabled:Boolean){update(requireNotNull(get(id)).copy(enabled=enabled))}
    suspend fun remove(id:String){require(id!=AgentResolver.GENERAL){"GENERAL_AGENT_READ_ONLY"};db.dao().deleteAgent(id)}
    suspend fun prefer(projectId:String,id:String?){require(id==null||get(id)?.enabled==true){"AGENT_UNAVAILABLE"};db.dao().preference(ProjectAgentPreference(projectId,id))}
    suspend fun resolve(explicit:String?,projectId:String?)=AgentResolver.resolve(list(),explicit,projectId?.let{db.dao().preferred(it)})
    // General offers installed procedures; enabled/eligible/active remain separate routing decisions.
    // Custom Agents retain their explicit assignment, and actual capabilities never expand here.
    private fun availableSkills(agent:AgentDefinition,installed:Set<String>)=if(agent.id==AgentResolver.GENERAL)
        agent.copy(skillIds=BuiltInSkills.general.skillIds+installed)else agent
    private fun record(a:AgentDefinition)=AgentRecord(a.id,gson.toJson(a))
}
