package com.localai.workspace.skills

import com.localai.workspace.capabilities.Capability
import com.localai.workspace.sources.SourceType
import java.security.MessageDigest

enum class SkillOrigin { BUILT_IN, USER_AUTHORED, IMPORTED }
data class RoutingProfile(
    val keywords: List<String> = emptyList(),
    val positiveExamples: List<String> = emptyList(),
    val negativeExamples: List<String> = emptyList(),
    val mimeTypes: Set<String> = emptySet(),
    val fileExtensions: Set<String> = emptySet(),
    val sourceTypes: Set<SourceType> = emptySet(),
    val explicitOnly: Boolean = false,
    val routingText: String? = null,
)
data class SkillDefinition(
    val id: String, val name: String, val description: String,
    val version: String = "1.0", val origin: SkillOrigin = SkillOrigin.USER_AUTHORED,
    val enabled: Boolean = true, val instructions: String,
    val routingProfile: RoutingProfile = RoutingProfile(explicitOnly = true),
    val allowedTools: Set<String> = STANDARD_TOOLS,
    val allowedSourceTypes: Set<SourceType> = SourceType.local,
    val requiredCapabilities: Set<Capability> = emptySet(),
    val tags: List<String> = emptyList(),
    val contentHash: String = hash(instructions),
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val externalMetadata: Map<String,String> = emptyMap(),
) {
    companion object {
        val STANDARD_TOOLS = setOf("calculator.evaluate", "files.read", "files.list", "python.execute")
        fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
}
data class SkillEvaluation(val skillId:String, val installed:Boolean=true, val enabled:Boolean,
    val eligible:Boolean, val active:Boolean=false, val reasons:List<String>, val executed:Boolean=false)
data class SkillSelection(val active:List<SkillDefinition>, val evaluations:List<SkillEvaluation>, val routingMs:Long)
data class RoutingAttachment(val mimeType:String?=null, val filename:String?=null)
data class SkillRoutingRequest(val query:String, val explicitSkills:Set<String> = emptySet(),
    val attachments:List<RoutingAttachment> = emptyList(), val sourceTypes:Set<SourceType> = emptySet(),
    val capabilities:Set<Capability> = emptySet())
