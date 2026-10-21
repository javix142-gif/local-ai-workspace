package com.localai.workspace.capabilities

import com.localai.workspace.skills.SkillDefinition

enum class Capability { LOCAL_READ, LOCAL_COMPUTE, LOCAL_WRITE, LOCAL_DELETE, NETWORK_READ, EXTERNAL_WRITE, EXTERNAL_SEND, PURCHASE_PAYMENT, IRREVERSIBLE_SYSTEM_ACTION, SENSITIVE_ACTION }
enum class CapabilityDecision { ALLOW, UNAVAILABLE, CONFIRM }
/** Operational capability resolution; never a content filter. Local read/compute need no new dialogs. */
class CapabilityPolicy(val enabled:Set<Capability> = Capability.entries.toSet()-Capability.NETWORK_READ) {
    val profile="PERMISSIVE_PERSONAL"
    fun evaluate(required:Set<Capability>, app:Set<Capability>, agent:Set<Capability>, skill:Set<Capability> = Capability.entries.toSet()):CapabilityDecision {
        if(!required.all{it in enabled && it in app && it in agent && it in skill})return CapabilityDecision.UNAVAILABLE
        return if(required.any{it in confirmation})CapabilityDecision.CONFIRM else CapabilityDecision.ALLOW
    }
    fun tools(selected:Set<String>, agent:Set<String>, activeSkills:List<SkillDefinition>):List<ToolAvailability> {
        val skillTools=if(activeSkills.isEmpty())SkillDefinition.STANDARD_TOOLS else activeSkills.flatMap{it.allowedTools + if(it.origin==com.localai.workspace.skills.SkillOrigin.BUILT_IN)com.localai.workspace.artifacts.ArtifactTool.IDS else emptySet()}.toSet()
        return SkillDefinition.STANDARD_TOOLS.sorted().map{id->
            val required=requirements(id)
            val decision=evaluate(required,supportedToolCapabilities,supportedToolCapabilities)
            val reason=when {
                id !in selected->"DISABLED"
                id !in agent->"AGENT_NOT_ALLOWED"
                id !in skillTools->"SKILL_NOT_ALLOWED"
                decision==CapabilityDecision.UNAVAILABLE->"CAPABILITY_UNAVAILABLE"
                decision==CapabilityDecision.CONFIRM->"CONFIRMATION_REQUIRED"
                else->"AVAILABLE"
            };ToolAvailability(id,reason=="AVAILABLE"||reason=="CONFIRMATION_REQUIRED",reason)
        }
    }
    companion object {
        val localCapabilities=setOf(Capability.LOCAL_READ,Capability.LOCAL_COMPUTE,Capability.LOCAL_WRITE)
        /** Capabilities implemented locally that always pass through an explicit confirmation UI. */
        val supportedToolCapabilities=localCapabilities+Capability.EXTERNAL_SEND
        private val confirmation=setOf(Capability.LOCAL_DELETE,Capability.EXTERNAL_SEND,Capability.PURCHASE_PAYMENT,Capability.IRREVERSIBLE_SYSTEM_ACTION,Capability.SENSITIVE_ACTION)
        fun requirements(id:String)=when(id){"files.read","files.list","artifacts.list","artifacts.read","calendar.read","contacts.lookup"->setOf(Capability.LOCAL_READ);"artifacts.create","artifacts.revise","artifacts.patchPreview","artifacts.xlsxSetCells","artifacts.xlsxAddSheet","artifacts.xlsxSetTable"->setOf(Capability.LOCAL_WRITE);"calculator.evaluate","python.execute"->setOf(Capability.LOCAL_COMPUTE);com.localai.workspace.domain.tools.AndroidActionPlanner.TOOL_ID->setOf(Capability.EXTERNAL_SEND);else->emptySet()}
    }
}
data class ToolAvailability(val toolId:String,val available:Boolean,val reason:String)
