package com.localai.workspace.agents

import com.google.gson.Gson
import com.localai.workspace.skills.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentRegistry(private val db:AgentsSkillsDatabase) {
    private val gson=Gson()
    private val initialization=Mutex()
    private var initialized=false
    val changes=db.dao().agents().map{rows->rows.map{gson.fromJson(it.definitionJson,AgentDefinition::class.java)}}
    suspend fun initialize() {initialization.withLock{if(!initialized){db.dao().seedAgent(record(BuiltInSkills.general));initialized=true}}}
    suspend fun list():List<AgentDefinition>{initialize();return db.dao().agentList().map{gson.fromJson(it.definitionJson,AgentDefinition::class.java)}}
    suspend fun get(id:String):AgentDefinition? {initialize();return db.dao().agent(id)?.let{gson.fromJson(it.definitionJson,AgentDefinition::class.java)}}
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
    private fun record(a:AgentDefinition)=AgentRecord(a.id,gson.toJson(a))
}
