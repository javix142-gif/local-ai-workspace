package com.localai.workspace.skills

import com.localai.workspace.context.*
import com.localai.workspace.context.ContextBuilder
import com.localai.workspace.semantic.v2.*
import org.junit.Assert.*
import org.junit.Test

class AgentSkillContextTest {
    private fun request(vararg skills:SkillInstruction)=ContextRequest("hola",activeSkillInstructions=skills.toList(),agentId="a",agentInstructions="Answer clearly",allowedTools=listOf("files.read"))
    @Test fun zeroSkillsHasZeroCostAndNoBodies() {
        val result=ContextBuilder().build(request(),emptyList());assertEquals(0,result.blockCosts["SKILLS"]);assertFalse(result.conversation().systemInstruction!!.contains(BuiltInSkills.definitions[0].instructions));assertEquals("a",result.safeReport()["agentId"])
    }
    @Test fun oneAndManySkillsAreIncludedOnlyWhenActive() {
        for(n in 1..3){val entries=BuiltInSkills.definitions.take(n).map{SkillInstruction(it.id,it.instructions)};val c=ContextBuilder().build(request(*entries.toTypedArray()),emptyList());assertEquals(entries.map{it.id},c.request.skillIds);entries.forEach{assertTrue(c.conversation().systemInstruction!!.contains(it.instructions))};assertFalse(c.conversation().userMessage.contains(entries[0].instructions))}
    }
    @Test fun oversizedSkillIsDroppedWholeWithReason() {
        val c=ContextBuilder().build(request(SkillInstruction("huge","x".repeat(5000))),emptyList())
        assertEquals("SKILL_CONTEXT_BUDGET",c.droppedSkills["huge"]);assertTrue(c.request.skillIds.isEmpty());assertFalse(c.conversation().systemInstruction!!.contains("xxxx"));assertEquals("hola",c.conversation().userMessage)
    }
    @Test fun legacySkillIdsAreNotReportedActiveWhenTheirBodyDoesNotFit() {
        val c=ContextBuilder().build(request().copy(skillIds=listOf("legacy-id"),skillInstructions="x".repeat(5000)),emptyList())
        assertTrue(c.request.skillIds.isEmpty());assertNull(c.request.skillInstructions)
        assertEquals("SKILL_CONTEXT_BUDGET",c.droppedSkills["legacy"])
    }
    @Test fun agentAndQueryAreNeverSilentlyDiscarded() {
        try{ContextBuilder().build(request().copy(agentInstructions="x".repeat(5000)),emptyList());fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.startsWith("AGENT_PROJECT_CONTEXT_BUDGET"))}
        try{ContextBuilder().build(request().copy(query="x".repeat(5000)),emptyList());fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.startsWith("CURRENT_QUERY_EXCEEDS"))}
    }
    @Test fun provenanceAndIsolationArePreserved() {
        val items=listOf(ContextItem("s",ContextKind.SOURCE,"evidence",SemanticScope(ScopeType.PROJECT,"p"),ContextTrust.UNTRUSTED_SOURCE,provenance=ContextProvenance(sourceId="doc",page=7)),ContextItem("foreign",ContextKind.MEMORY,"secret",SemanticScope(ScopeType.AGENT,"b"),ContextTrust.APPROVED_MEMORY))
        val c=ContextBuilder().build(request().copy(access=ScopeAccess(projectId="p",agentId="a"),projectInstructions="Project instructions"),items)
        assertEquals(1,c.included.size);assertEquals(7,c.included[0].provenance.page);assertTrue(c.conversation().systemInstruction!!.contains("Project instructions"));assertFalse(c.conversation().userMessage.contains("secret"));assertEquals("SCOPE_NOT_ALLOWED",c.dropped[0].reason)
    }
    @Test fun scopesDoNotGrantCrossAgentOrCrossProjectAccess() {
        val access=ScopeAccess(agentId="a",projectId="p",readableTypes=setOf(ScopeType.USER,ScopeType.AGENT))
        assertTrue(access.permits("USER","local"));assertTrue(access.permits("AGENT","a"));assertFalse(access.permits("AGENT","b"));assertFalse(access.permits("PROJECT","p"))
        val scoped=ContextBuilder().build(request().copy(access=access,memoryScopes=setOf(ScopeType.USER)),listOf(ContextItem("agent-memory",ContextKind.MEMORY,"approved agent record",SemanticScope(ScopeType.AGENT,"a"),ContextTrust.APPROVED_MEMORY)))
        assertTrue(scoped.included.isEmpty());assertEquals("MEMORY_SCOPE_NOT_ALLOWED",scoped.dropped.single().reason)
    }
    @Test fun UTF8EstimatorAndAllBudgetBlocksAreReported() {
        val c=ContextBuilder().build(request().copy(extraReserve=200),emptyList())
        assertEquals("ESTIMATED_UTF8_BYTES_V1",c.safeReport()["tokenCounting"]);assertEquals(setOf("SYSTEM","QUERY","AGENT","PROJECT","TOOLS","SKILLS","MEMORY","SOURCES","HISTORY"),c.blockCosts.keys);assertEquals(200,c.blockCosts["TOOLS"])
    }
}
