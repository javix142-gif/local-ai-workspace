package com.localai.workspace.capabilities

import com.localai.workspace.skills.SkillDefinition

enum class Capability { LOCAL_READ, LOCAL_COMPUTE, LOCAL_WRITE, LOCAL_DELETE, NETWORK_READ, EXTERNAL_WRITE, EXTERNAL_SEND, PURCHASE_PAYMENT, IRREVERSIBLE_SYSTEM_ACTION, SENSITIVE_ACTION }
enum class CapabilityDecision { ALLOW, UNAVAILABLE, CONFIRM }
/** Operational capability resolution; never a content filter. Local read/compute need no new dialogs. */
class CapabilityPolicy(val enabled:Set<Capability> = Capability.entries.toSet()) {
    val profile="PERMISSIVE_PERSONAL"
    fun evaluate(required:Set<Capability>, app:Set<Capability>, agent:Set<Capability>, skill:Set<Capability> = Capability.entries.toSet()):CapabilityDecision {
        if(!required.all{it in enabled && it in app && it in agent && it in skill})return CapabilityDecision.UNAVAILABLE
        return if(required.any{it in confirmation})CapabilityDecision.CONFIRM else CapabilityDecision.ALLOW
    }
    fun tools(selected:Set<String>, agent:Set<String>, activeSkills:List<SkillDefinition>):List<ToolAvailability> {
        val skillTools=if(activeSkills.isEmpty())SkillDefinition.STANDARD_TOOLS else activeSkills.flatMap{it.allowedTools}.toSet()
        return SkillDefinition.STANDARD_TOOLS.sorted().map{id->
            val required=requirements(id)
            val reason=when {
                id !in selected->"DISABLED"
                id !in agent->"AGENT_NOT_ALLOWED"
                id !in skillTools->"SKILL_NOT_ALLOWED"
                evaluate(required,localCapabilities,localCapabilities)!=CapabilityDecision.ALLOW->"CAPABILITY_UNAVAILABLE"
                else->"AVAILABLE"
            };ToolAvailability(id,reason=="AVAILABLE",reason)
        }
    }
    companion object {
        val localCapabilities=setOf(Capability.LOCAL_READ,Capability.LOCAL_COMPUTE)
        private val confirmation=setOf(Capability.LOCAL_DELETE,Capability.EXTERNAL_SEND,Capability.PURCHASE_PAYMENT,Capability.IRREVERSIBLE_SYSTEM_ACTION,Capability.SENSITIVE_ACTION)
        fun requirements(id:String)=when(id){"files.read","files.list"->setOf(Capability.LOCAL_READ);"calculator.evaluate","python.execute"->setOf(Capability.LOCAL_COMPUTE);else->emptySet()}
    }
}
data class ToolAvailability(val toolId:String,val available:Boolean,val reason:String)
