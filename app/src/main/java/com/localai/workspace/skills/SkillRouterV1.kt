package com.localai.workspace.skills

import com.localai.workspace.agents.AgentDefinition
import java.text.Normalizer
import java.util.Locale

/** Pure metadata routing: no model, no embedding, no instruction-body reads. */
class SkillRouterV1 {
    fun select(request:SkillRoutingRequest,agent:AgentDefinition,skills:List<SkillDefinition>):SkillSelection {
        val start=System.nanoTime();val query=normalize(request.query)
        val evaluations=skills.sortedBy{it.id}.map{skill->
            val profile=skill.routingProfile
            val rejection=when {
                !skill.enabled->"DISABLED"
                skill.id !in agent.skillIds->"AGENT_NOT_ALLOWED"
                !request.capabilities.containsAll(skill.requiredCapabilities)->"CAPABILITY_UNAVAILABLE"
                else->null
            }
            if(rejection!=null)SkillEvaluation(skill.id,enabled=skill.enabled,eligible=false,reasons=listOf(rejection))
            else {
                val reasons=mutableListOf<String>()
                if(skill.id in request.explicitSkills)reasons+="EXPLICIT_SELECTION"
                if(reasons.isEmpty() && !profile.explicitOnly) {
                    val ext=request.attachments.any{it.filename?.substringAfterLast('.',"")?.lowercase(Locale.ROOT) in profile.fileExtensions}
                    val mime=request.attachments.any{it.mimeType?.lowercase(Locale.ROOT) in profile.mimeTypes}
                    val keyword=profile.keywords.any{matches(query,normalize(it))}
                    val specific=skill.id in setOf("skill.code-assistant","skill.spreadsheet-analysis")
                    val documentIntent=Regex("\\b(resume|resumir|summarize|summarise|documentos?|documents?|pdf|docx)\\b").containsMatchIn(query)
                    // A generic 'review' is insufficient without an appropriate document; code/table signals win.
                    val incompatible=request.attachments.any{it.filename?.substringAfterLast('.',"")?.lowercase(Locale.ROOT) in setOf("kt","java","py","js","ts","xlsx","csv")}
                    val negative=profile.negativeExamples.any{query==normalize(it)}
                    val intent=Regex("\\b(revisa|review|analiza|analyze|analyse|explica|explain|depura|debug|implementa|implement|escribe|write|corrige|fix|calcula|calculate|compara|compare|encuentra|find|resume|summarize|why|how|por que|que es|what is)\\b").containsMatchIn(query)
                    val spreadsheetDomain=Regex("\\b(excel|xlsx|csv|spreadsheet|planilla)\\b").containsMatchIn(query)
                    val specificStrong=specific && intent && (skill.id!="skill.spreadsheet-analysis" || ext || mime || spreadsheetDomain)
                    val strong=keyword && (specificStrong || skill.id=="skill.general-writing" || ((ext||mime||documentIntent)&&!incompatible))
                    if(!negative && strong) {
                        if(ext)reasons+="EXTENSION_MATCH"
                        if(mime)reasons+="MIME_MATCH"
                        if(profile.sourceTypes.intersect(request.sourceTypes).isNotEmpty())reasons+="SOURCE_MATCH"
                        reasons+="KEYWORD_MATCH"
                    }
                    // Source-code extension + a concrete review/debug request is also decisive.
                    if(!negative && reasons.isEmpty() && (ext || mime) && specific && Regex("\\b(revisa|review|analiza|analyze|explica|explain|depura|debug)\\b").containsMatchIn(query))reasons+=listOf(if(ext)"EXTENSION_MATCH"else"MIME_MATCH","KEYWORD_MATCH")
                }
                SkillEvaluation(skill.id,enabled=true,eligible=true,active=reasons.isNotEmpty(),reasons=if(reasons.isNotEmpty())reasons else listOf(if(profile.explicitOnly)"EXPLICIT_ONLY" else "NO_TRIGGER"))
            }
        } + request.explicitSkills.filter{id->skills.none{it.id==id}}.map{SkillEvaluation(it,installed=false,enabled=false,eligible=false,reasons=listOf("NOT_INSTALLED"))}
        return SkillSelection(skills.filter{s->evaluations.any{it.skillId==s.id && it.active}}.sortedBy{it.id},evaluations,(System.nanoTime()-start)/1_000_000)
    }
    companion object {
        fun normalize(text:String)=Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
        private fun matches(text:String,key:String)=Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(key)}(?![\\p{L}\\p{N}_])").containsMatchIn(text)
    }
}
