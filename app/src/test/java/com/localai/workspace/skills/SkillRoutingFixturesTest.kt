package com.localai.workspace.skills

import com.google.gson.JsonParser
import com.localai.workspace.capabilities.Capability
import com.localai.workspace.sources.SourceType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SkillRoutingFixturesTest(private val id:String,private val fixture:String) {
    @Test fun exactDeterministicActivation() {
        val f=JsonParser.parseString(fixture).asJsonObject
        fun values(key:String)=f.getAsJsonArray(key)?.map{it.asString}?.toSet() ?: emptySet()
        val request=SkillRoutingRequest(f["query"].asString,values("explicit"),
            if(f.has("filename")||f.has("mime"))listOf(RoutingAttachment(f.get("mime")?.asString,f.get("filename")?.asString))else emptyList(),
            if(f.has("filename"))setOf(SourceType.PROJECT_DOCUMENT)else emptySet(),
            if(f.get("missingCapability")?.asBoolean==true)emptySet()else Capability.entries.toSet())
        val skills=BuiltInSkills.definitions.map{if(it.id in values("disabled"))it.copy(enabled=false)else if(f.has("requiresCapability"))it.copy(requiredCapabilities=setOf(Capability.LOCAL_READ))else it}
        val agent=BuiltInSkills.general.copy(skillIds=if(f.has("agentSkills"))values("agentSkills")else BuiltInSkills.general.skillIds)
        val selected=SkillRouterV1().select(request,agent,skills)
        val actual=selected.active.map{it.id}.toSet();val expected=values("expected")
        synchronized(results){results+=mapOf("id" to id,"correct" to (actual==expected),"falsePositives" to (actual-expected).size,"falseNegatives" to (expected-actual).size,"abstention" to actual.isEmpty(),"routingMs" to selected.routingMs)}
        assertEquals(id,expected,actual)
        assertTrue(selected.evaluations.none{it.active && (!it.eligible||!it.enabled)})
    }
    companion object {
        private val results=mutableListOf<Map<String,Any>>()
        @JvmStatic @org.junit.AfterClass fun report() {
            val json=com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mapOf("environment" to "TESTED_HOST","router" to "DETERMINISTIC_V1","total" to results.size,"correctActivations" to results.count{it["correct"]==true},"falsePositives" to results.sumOf{it["falsePositives"] as Int},"falseNegatives" to results.sumOf{it["falseNegatives"] as Int},"abstentions" to results.count{it["abstention"]==true},"cases" to results))
            java.io.File("build/reports/skills-routing-v1.json").apply{parentFile?.mkdirs();writeText(json)}
        }
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases():Collection<Array<Any>> {
            val json=SkillRoutingFixturesTest::class.java.getResourceAsStream("/skills/routing-v1.json")!!.bufferedReader().use{JsonParser.parseReader(it).asJsonArray}
            require(json.size()>=100)
            return json.map{f->arrayOf(f.asJsonObject["id"].asString,f.toString())}
        }
    }
}
