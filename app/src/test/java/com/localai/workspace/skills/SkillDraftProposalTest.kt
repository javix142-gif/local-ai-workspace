package com.localai.workspace.skills

import com.localai.workspace.capabilities.Capability
import com.localai.workspace.sources.SourceType
import org.junit.Assert.*
import org.junit.Test

class SkillDraftProposalTest {
    private val response = """```markdown
---
name: "Administrative Review"
description: "Checks administrative documents and reports concrete issues."
version: "1.0"
tags:
  - "administrative review"
  - "document checks"
activation: "When explicitly selected for a document review."
required-capabilities: "LOCAL_READ, NETWORK_READ, UNKNOWN_CAPABILITY"
allowed-tools: "files.read, files.not-real"
allowed-sources: "PROJECT_DOCUMENT, UNKNOWN_SOURCE"
response-format: "Ordered findings with source excerpts."
restrictions: "Do not claim unsupported checks."
use-cases: "Review a policy draft."
---
## Instructions
1. Identify the document type.
2. Report only issues supported by the supplied text.
```"""

    @Test fun parsesFencedSkillMarkdownAsUntrustedDisabledProposal() {
        val proposal = SkillDraftProposalParser.parse(response)

        assertEquals("Administrative Review", proposal.definition.name)
        assertEquals(SkillOrigin.USER_AUTHORED, proposal.definition.origin)
        assertFalse(proposal.definition.enabled)
        assertTrue(proposal.definition.allowedTools.isEmpty())
        assertTrue(proposal.definition.requiredCapabilities.isEmpty())
        assertTrue(proposal.definition.allowedSourceTypes.isEmpty())
        assertTrue(proposal.definition.routingProfile.explicitOnly)
        assertEquals("When explicitly selected for a document review.", proposal.activationConditions)
        assertEquals(setOf("files.read"), proposal.suggestedTools)
        assertEquals(setOf(Capability.LOCAL_READ, Capability.NETWORK_READ), proposal.suggestedCapabilities)
        assertEquals(setOf(SourceType.PROJECT_DOCUMENT), proposal.suggestedSources)
        assertEquals(setOf("files.not-real"), proposal.unavailableTools)
        assertEquals(setOf("NETWORK_READ", "UNKNOWN_CAPABILITY"), proposal.unavailableCapabilities)
        assertEquals(setOf("UNKNOWN_SOURCE"), proposal.unavailableSources)
        assertTrue(proposal.definition.instructions.contains("Report only issues"))
    }

    @Test fun userSelectionIsRequiredToGrantToolsSourcesOrCapabilities() {
        val proposal = SkillDraftProposalParser.parse(response)
        val approved = proposal.configured(
            name = "Administrative Review - Edited",
            description = "Edited description",
            instructions = "Edited instructions",
            activationConditions = "Only on explicit request",
            responseFormat = "Bulleted findings",
            restrictions = "No unsupported claims",
            useCases = "Policy review",
            tools = setOf("files.read", "files.not-real"),
            capabilities = setOf(Capability.LOCAL_READ, Capability.EXTERNAL_SEND),
            sources = setOf(SourceType.PROJECT_DOCUMENT),
            version = "2.0",
            tags = listOf("administrative", "reviewed"),
        )

        assertEquals("skill.user-administrative-review-edited", approved.id)
        assertEquals(SkillOrigin.USER_AUTHORED, approved.origin)
        assertFalse("a reviewed draft stays disabled until the user enables it", approved.enabled)
        assertEquals("2.0", approved.version)
        assertEquals(listOf("administrative", "reviewed"), approved.tags)
        assertEquals(approved.tags, approved.routingProfile.keywords)
        assertEquals(setOf("files.read"), approved.allowedTools)
        assertEquals(setOf(Capability.LOCAL_READ, Capability.EXTERNAL_SEND), approved.requiredCapabilities)
        assertEquals(setOf(SourceType.PROJECT_DOCUMENT), approved.allowedSourceTypes)
        assertTrue(approved.routingProfile.explicitOnly)
        assertEquals("Only on explicit request", approved.routingProfile.routingText)
        assertEquals("files.read", approved.externalMetadata["allowed-tools"])
        assertEquals("Bulleted findings", approved.externalMetadata["response-format"])
        assertEquals("files.not-real", approved.externalMetadata["unavailable-tools"])
        assertEquals("EXTERNAL_SEND, NETWORK_READ, UNKNOWN_CAPABILITY", approved.externalMetadata["unavailable-capabilities"])
    }

    @Test fun rejectsResponseWithoutValidInteroperableSkillMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            SkillDraftProposalParser.parse("Here is a Skill idea, but no SKILL.md metadata.")
        }
    }

    @Test fun authoringInstructionNamesReviewAndExistingLocalCapabilities() {
        val instruction = SkillDraftProposalParser.systemInstruction()

        assertTrue(instruction.contains("¿Qué debiese hacer la Skill?"))
        assertTrue(instruction.contains("SKILL.md"))
        assertTrue(instruction.contains("files.read"))
        assertTrue(instruction.contains("Capabilities currently available to this local workspace"))
        assertTrue(instruction.contains("Recognized but currently unavailable capabilities"))
        assertTrue(instruction.contains("Do not invent tools or capabilities"))
        assertTrue(instruction.contains("user reviews it"))
    }
}
