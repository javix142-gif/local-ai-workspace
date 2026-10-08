package com.localai.workspace.skills

import android.app.Application
import androidx.room.Room
import com.localai.workspace.agents.*
import com.localai.workspace.capabilities.*
import com.localai.workspace.sources.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class SkillAgentRegistryTest {
    private lateinit var db:AgentsSkillsDatabase;private lateinit var skills:SkillRegistry;private lateinit var agents:AgentRegistry
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AgentsSkillsDatabase::class.java).build();skills=SkillRegistry(db);agents=AgentRegistry(db)}
    @After fun close(){db.close()}
    @Test fun repeatedAndConcurrentSeedingDoesNotOverwriteDisabledBuiltIn()=runBlocking {
        skills.initialize();skills.enable("skill.code-assistant",false)
        coroutineScope{(1..8).map{async{skills.initialize();agents.initialize()}}.awaitAll()}
        assertEquals(4,skills.list().size);assertFalse(skills.get("skill.code-assistant")!!.enabled);assertEquals(1,agents.list().size)
    }
    @Test fun explicitProjectGeneralPrecedenceAndMissingDisabledFallback()=runBlocking {
        val a=AgentDefinition("a","A");val b=AgentDefinition("b","B");agents.update(a);agents.update(b);agents.prefer("p","b")
        assertEquals("a",agents.resolve("a","p").agent.id);assertEquals("PROJECT_AGENT",agents.resolve(null,"p").reason)
        agents.enable("a",false);assertEquals("b",agents.resolve("a","p").agent.id)
        agents.remove("b");assertEquals(AgentResolver.GENERAL,agents.resolve(null,"p").agent.id)
        assertEquals(AgentResolver.GENERAL,agents.resolve("missing","other").agent.id)
    }
    @Test fun generalAndBuiltInsCannotBeDeleted()=runBlocking {
        try{agents.remove(AgentResolver.GENERAL);fail()}catch(_:IllegalArgumentException){}
        try{agents.enable(AgentResolver.GENERAL,false);fail()}catch(_:IllegalArgumentException){}
        try{skills.remove("skill.code-assistant");fail()}catch(_:IllegalArgumentException){}
        assertTrue(agents.resolve(null,null).agent.enabled)
    }
    @Test fun importUpdateAndRoundTripPreserveMetadata()=runBlocking {
        val text="---\nname: My Skill\ndescription: Local procedure\nversion: 1.0\ntags:\n  - local\nreferences: refs.md\nscripts: do-not-run.py\n---\n\n# Steps\nDo the requested work."
        val s=skills.install(text);assertEquals(SkillOrigin.IMPORTED,s.origin);assertTrue(s.routingProfile.explicitOnly)
        val round=SkillPackageParser.parse(skills.export(s.id));assertEquals(s.instructions,round.instructions);assertEquals(s.externalMetadata,round.externalMetadata);assertEquals(s.tags,round.tags)
        skills.install(text.replace("1.0","2.0"));assertEquals("2.0",skills.get(s.id)!!.version);assertEquals(5,skills.list().size)
    }
    @Test fun invalidMetadataAndOversizedInputAreRejected() {
        for(text in listOf("no metadata","---\nname: a\n---\nbody","---\ndescription: a\n---\nbody","---\nname: a\ndescription: b\nname: c\n---\nbody","x".repeat(70000))) {
            try{SkillPackageParser.parse(text);fail(text.take(20))}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun duplicateNameIsNotInstalledUnderNewId()=runBlocking {
        skills.update(SkillDefinition("a","custom","d",instructions="body"))
        try{skills.update(SkillDefinition("b","custom","d",instructions="body"));fail()}catch(_:IllegalArgumentException){}
        assertEquals(5,skills.list().size)
    }
    @Test fun userEditsSurviveInitialization()=runBlocking {
        val s=SkillDefinition("user","User","d",instructions="edited")
        skills.update(s);skills.initialize();assertEquals("edited",skills.get(s.id)!!.instructions)
    }
    @Test fun generalOffersInstalledSkillsWithoutForcingActivationOrExpandingCustomAgents()=runBlocking {
        val imported=skills.install("---\nname: Budget Procedure\ndescription: Budget review\n---\nReview only the requested budget.")
        val authored=SkillDefinition("skill.user-budget","My Budget","Budget analysis",instructions="Analyze the provided budget.",routingProfile=RoutingProfile(keywords=listOf("presupuesto")))
        skills.update(authored)
        val coordinator=AgentSkillCoordinator(skills,agents)
        assertTrue(imported.id in agents.get(AgentResolver.GENERAL)!!.skillIds)
        assertEquals(listOf(imported.id),coordinator.resolve(SkillRoutingRequest("hola",explicitSkills=setOf(imported.id)),null,null).selection.active.map{it.id})
        assertEquals(listOf(authored.id),coordinator.resolve(SkillRoutingRequest("Analiza mi presupuesto"),null,null).selection.active.map{it.id})
        assertTrue(coordinator.resolve(SkillRoutingRequest("presupuesto"),null,null).selection.active.isEmpty())
        agents.update(AgentDefinition("restricted","Assigned skills only",skillIds=emptySet()))
        val restricted=coordinator.resolve(SkillRoutingRequest("hola",explicitSkills=setOf(imported.id)),null,"restricted")
        assertTrue(restricted.selection.active.isEmpty())
        assertEquals(listOf("AGENT_NOT_ALLOWED"),restricted.selection.evaluations.first{it.skillId==imported.id}.reasons)
        skills.remove(imported.id);assertFalse(imported.id in agents.get(AgentResolver.GENERAL)!!.skillIds)
    }
    @Test fun sidecarReopenPersistsPreferencesAndOrigin()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val name="agents-reopen.db";context.deleteDatabase(name)
        val first=AgentsSkillsDatabase.create(context,name)
        AgentRegistry(first).update(AgentDefinition("custom","Custom"));AgentRegistry(first).prefer("p","custom")
        SkillRegistry(first).install("---\nname: Imported\ndescription: d\n---\nbody");first.close()
        val reopened=AgentsSkillsDatabase.create(context,name)
        try{assertEquals("custom",AgentRegistry(reopened).resolve(null,"p").agent.id);assertEquals(SkillOrigin.IMPORTED,SkillRegistry(reopened).get("skill.user-imported")!!.origin)}finally{reopened.close();context.deleteDatabase(name)}
    }
    @Test fun installedEnabledEligibleActiveAreIndependent()=runBlocking {
        val coordinator=AgentSkillCoordinator(skills,agents)
        val greeting=coordinator.resolve(SkillRoutingRequest("hola"),null,null)
        assertTrue(greeting.selection.active.isEmpty());assertTrue(greeting.selection.evaluations.all{it.installed&&it.enabled&&it.eligible&&!it.active})
        skills.enable("skill.spreadsheet-analysis",false)
        val query=coordinator.resolve(SkillRoutingRequest("Revisa este Excel"),null,null)
        assertTrue(query.selection.active.isEmpty());assertFalse(query.selection.evaluations.first{it.skillId=="skill.spreadsheet-analysis"}.eligible)
    }
    @Test fun capabilitiesAndSourcesDoNotInventFutureAvailability() {
        val policy=CapabilityPolicy()
        assertEquals(CapabilityDecision.ALLOW,policy.evaluate(setOf(Capability.LOCAL_COMPUTE),CapabilityPolicy.localCapabilities,CapabilityPolicy.localCapabilities))
        assertEquals(CapabilityDecision.UNAVAILABLE,policy.evaluate(setOf(Capability.NETWORK_READ),CapabilityPolicy.localCapabilities,CapabilityPolicy.localCapabilities))
        assertEquals(CapabilityDecision.UNAVAILABLE,policy.evaluate(setOf(Capability.NETWORK_READ),Capability.entries.toSet(),Capability.entries.toSet()))
        assertEquals(CapabilityDecision.ALLOW,CapabilityPolicy(Capability.entries.toSet()).evaluate(setOf(Capability.NETWORK_READ),Capability.entries.toSet(),Capability.entries.toSet()))
        assertEquals(CapabilityDecision.CONFIRM,policy.evaluate(setOf(Capability.EXTERNAL_SEND),Capability.entries.toSet(),Capability.entries.toSet()))
        assertTrue(SourceRegistry().list(SourceType.entries.toSet()).filter{it.type !in SourceType.local}.none{it.available})
        assertFalse(policy.tools(setOf("files.read"),emptySet(),emptyList()).first{it.toolId=="files.read"}.available)
        assertFalse(CapabilityPolicy(emptySet()).tools(setOf("calculator.evaluate"),SkillDefinition.STANDARD_TOOLS,emptyList()).any{it.available})
    }
    @Test fun sourceAllowanceIsNotARequirementForInstructionOnlyTasks() {
        val skill=BuiltInSkills.definitions.first{it.id=="skill.general-writing"}
        val agent=BuiltInSkills.general.copy(allowedSourceTypes=emptySet())
        val selection=SkillRouterV1().select(SkillRoutingRequest("hola",explicitSkills=setOf(skill.id)),agent,listOf(skill))
        assertEquals(listOf(skill.id),selection.active.map{it.id})
        assertTrue(SourceRegistry().list(agent.allowedSourceTypes).none{it.available})
    }
    @Test fun executedRequiresAnActiveSkillAndSuccessfulAssociatedTool() {
        val skill=BuiltInSkills.definitions.first{it.id=="skill.code-assistant"}
        val active=SkillRouterV1().select(SkillRoutingRequest("Review this code"),BuiltInSkills.general,listOf(skill))
        val trace=AgentSkillTrace(AgentResolution(BuiltInSkills.general,"GENERAL_FALLBACK"),active,emptyList())
        assertFalse(trace.withSuccessfulTools(emptySet()).selection.evaluations.single().executed)
        assertTrue(trace.withSuccessfulTools(setOf("python.execute")).selection.evaluations.single().executed)
        assertFalse(trace.copy(selection=SkillRouterV1().select(SkillRoutingRequest("hola"),BuiltInSkills.general,listOf(skill))).withSuccessfulTools(setOf("python.execute")).selection.evaluations.single().executed)
    }
}
