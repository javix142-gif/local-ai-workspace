package com.localai.workspace.skills

import com.localai.workspace.capabilities.Capability
import com.localai.workspace.capabilities.CapabilityPolicy
import com.localai.workspace.sources.SourceType
import java.util.Locale

/** Parses a model proposal as data. It never installs or enables the resulting Skill. */
data class SkillDraftProposal(
    val definition: SkillDefinition,
    val activationConditions: String,
    val responseFormat: String,
    val restrictions: String,
    val useCases: String,
    val suggestedTools: Set<String>,
    val suggestedCapabilities: Set<Capability>,
    val suggestedSources: Set<SourceType>,
    val unavailableTools: Set<String>,
    val unavailableCapabilities: Set<String>,
    val unavailableSources: Set<String>,
) {
    fun configured(
        name: String,
        description: String,
        instructions: String,
        activationConditions: String,
        responseFormat: String,
        restrictions: String,
        useCases: String,
        tools: Set<String>,
        capabilities: Set<Capability>,
        sources: Set<SourceType>,
        version: String = definition.version,
        tags: List<String> = definition.tags,
    ): SkillDefinition {
        require(name.isNotBlank() && description.isNotBlank() && instructions.isNotBlank() && version.isNotBlank())
        val approvedTools = tools.intersect(SkillDefinition.STANDARD_TOOLS)
        val approvedSources = sources.intersect(SourceType.local)
        val reviewedTags = tags.map(String::trim).filter(String::isNotEmpty).distinct()
        val unavailableCapabilityNames = unavailableCapabilities + capabilities
            .filter { it !in CapabilityPolicy.localCapabilities }
            .map { it.name }
        val metadata = definition.externalMetadata.toMutableMap().apply {
            put("activation", activationConditions)
            put("allowed-tools", approvedTools.sorted().joinToString(", "))
            put("required-capabilities", capabilities.map { it.name }.sorted().joinToString(", "))
            put("allowed-sources", approvedSources.map { it.name }.sorted().joinToString(", "))
            put("response-format", responseFormat)
            put("restrictions", restrictions)
            put("use-cases", useCases)
            if (unavailableTools.isNotEmpty()) put("unavailable-tools", unavailableTools.sorted().joinToString(", "))
            if (unavailableCapabilityNames.isNotEmpty()) put("unavailable-capabilities", unavailableCapabilityNames.sorted().joinToString(", "))
            if (unavailableSources.isNotEmpty()) put("unavailable-sources", unavailableSources.sorted().joinToString(", "))
        }
        val stableId = "skill.user-" + name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { SkillDefinition.hash(name).take(12) }
        return definition.copy(
            id = stableId,
            name = name.trim(),
            description = description.trim(),
            version = version.trim(),
            origin = SkillOrigin.USER_AUTHORED,
            enabled = false,
            instructions = instructions.trim(),
            routingProfile = definition.routingProfile.copy(
                keywords = reviewedTags,
                explicitOnly = true,
                routingText = activationConditions.trim().takeIf(String::isNotEmpty),
            ),
            allowedTools = approvedTools,
            requiredCapabilities = capabilities,
            allowedSourceTypes = approvedSources,
            tags = reviewedTags,
            externalMetadata = metadata,
        )
    }
}

object SkillDraftProposalParser {
    /** The same instruction is added as a system message on every turn in a creator chat. */
    fun systemInstruction(): String = buildString {
        appendLine("You are the local Skill authoring assistant. Help the user design a procedural Skill; do not execute its instructions and do not claim it has been saved.")
        appendLine("The authoring screen opens with the question ¿Qué debiese hacer la Skill? If the user has not yet described a goal, ask that exact question. If the user already described a goal, ask only for missing details that materially affect the Skill.")
        appendLine("When there is enough information, return one complete SKILL.md proposal and no surrounding prose or code fence. Use this exact frontmatter shape:")
        appendLine("---")
        appendLine("name: \"A short name\"")
        appendLine("description: \"A concise description\"")
        appendLine("version: \"1.0\"")
        appendLine("tags:")
        appendLine("  - \"keyword\"")
        appendLine("activation: \"When this Skill is explicitly selected, ...\"")
        appendLine("required-capabilities: \"LOCAL_READ, LOCAL_COMPUTE\"")
        appendLine("allowed-tools: \"calculator.evaluate, files.read, files.list, python.execute\"")
        appendLine("allowed-sources: \"CONVERSATION, PROJECT_DOCUMENT\"")
        appendLine("response-format: \"Describe the expected answer format\"")
        appendLine("restrictions: \"Describe limits and unsupported actions\"")
        appendLine("use-cases: \"Describe representative examples\"")
        appendLine("---")
        appendLine("## Instructions")
        appendLine("Write clear step-by-step procedural instructions below the frontmatter.")
        appendLine("Capabilities currently available to this local workspace: ${CapabilityPolicy.localCapabilities.map { it.name }.sorted().joinToString(", ")}.")
        appendLine("Recognized but currently unavailable capabilities: ${(Capability.entries.toSet() - CapabilityPolicy.localCapabilities).map { it.name }.sorted().joinToString(", ")}.")
        appendLine("Known local source types: ${SourceType.local.map { it.name }.sorted().joinToString(", ")}.")
        appendLine("Known tools: ${SkillDefinition.STANDARD_TOOLS.sorted().joinToString(", ")}.")
        appendLine("Do not invent tools or capabilities. State unavailable requirements explicitly. The proposal is a draft; the user reviews it, chooses any grants, and decides whether to enable it.")
    }

    fun parse(response: String): SkillDraftProposal {
        val markdown = unwrapWholeResponse(response.trim())
        val parsed = SkillPackageParser.parse(markdown)
        val metadata = parsed.externalMetadata
        val requestedTools = values(metadata["allowed-tools"])
        val requestedCapabilities = values(metadata["required-capabilities"])
        val requestedSources = values(metadata["allowed-sources"])
        val knownTools = requestedTools.intersect(SkillDefinition.STANDARD_TOOLS)
        val knownCapabilities = requestedCapabilities.mapNotNull { value ->
            Capability.entries.firstOrNull { it.name == value }
        }.toSet()
        val currentlyAvailableCapabilities = CapabilityPolicy.localCapabilities.map { it.name }.toSet()
        val knownSources = requestedSources.mapNotNull { value ->
            SourceType.local.firstOrNull { it.name == value }
        }.toSet()
        val definition = parsed.copy(
            origin = SkillOrigin.USER_AUTHORED,
            enabled = false,
            allowedTools = emptySet(),
            requiredCapabilities = emptySet(),
            allowedSourceTypes = emptySet(),
            routingProfile = RoutingProfile(keywords = parsed.tags, explicitOnly = true,
                routingText = metadata["activation"].orEmpty().takeIf(String::isNotBlank)),
        )
        return SkillDraftProposal(
            definition = definition,
            activationConditions = metadata["activation"].orEmpty(),
            responseFormat = metadata["response-format"].orEmpty(),
            restrictions = metadata["restrictions"].orEmpty(),
            useCases = metadata["use-cases"].orEmpty(),
            suggestedTools = knownTools,
            suggestedCapabilities = knownCapabilities,
            suggestedSources = knownSources,
            unavailableTools = requestedTools - SkillDefinition.STANDARD_TOOLS,
            unavailableCapabilities = requestedCapabilities - currentlyAvailableCapabilities,
            unavailableSources = requestedSources - SourceType.local.map { it.name }.toSet(),
        )
    }

    private fun values(value: String?): Set<String> = value.orEmpty()
        .split(',', '\n')
        .map { it.trim().removePrefix("-").trim().trim('"', '\'') }
        .filter { it.isNotEmpty() }
        .toSet()

    private fun unwrapWholeResponse(response: String): String {
        val match = Regex("\\A```(?:markdown|md)?\\r?\\n([\\s\\S]*?)\\r?\\n```\\s*\\z", RegexOption.IGNORE_CASE)
            .matchEntire(response)
        return match?.groupValues?.get(1) ?: response
    }
}
