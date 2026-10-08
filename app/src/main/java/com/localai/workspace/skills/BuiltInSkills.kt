package com.localai.workspace.skills

import com.localai.workspace.agents.*
import com.localai.workspace.sources.SourceType

object BuiltInSkills {
    private fun profile(words:List<String>,positive:List<String>,negative:List<String>,ext:Set<String> = emptySet(),mime:Set<String> = emptySet())=
        RoutingProfile(words,positive,negative,mime,ext,setOf(SourceType.PROJECT_DOCUMENT))
    val definitions=listOf(
        SkillDefinition("skill.general-writing","General Writing","Draft and revise prose for the requested audience.",origin=SkillOrigin.BUILT_IN,
            instructions="For a writing task, follow the requested audience, tone, language and length. Preserve the user's facts. Ask for missing essential facts; never invent them. Return the requested draft or revision directly.",
            routingProfile=profile(listOf("redacta","redactar","reescribe","rewrite","draft","write an email","escribe una carta","escribe un correo","write a letter","corrige la redacción"),listOf("Redacta un correo","Draft a letter"),listOf("Revisa código","Analyze spreadsheet")),createdAt=0),
        SkillDefinition("skill.document-analysis","Document Analysis","Analyze and summarize document passages with evidence.",origin=SkillOrigin.BUILT_IN,
            instructions="For document analysis, distinguish supplied text from inference. Use passage provenance and supplied evidence IDs. Explain missing or unextractable content. Report only conclusions supported by available passages; do not claim to have read an entire file when only excerpts were supplied.",
            routingProfile=profile(listOf("resume","resumir","summarize","summarise","analiza","analyze","analyse","revisa","review","compara","compare"),listOf("Resume este PDF","Summarize the document"),listOf("Revisa código Kotlin","Analyze Excel totals"),setOf("pdf","docx","txt","md","json","xml","yaml","yml"),setOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/plain")),createdAt=0),
        SkillDefinition("skill.code-assistant","Code Assistant","Review, explain and debug source code.",origin=SkillOrigin.BUILT_IN,
            instructions="For code tasks, locate the relevant code and explain concrete failure paths. Distinguish confirmed bugs from hypotheses. Propose focused changes and tests. Preserve existing behavior and avoid unrelated rewrites. Writing code does not mean it has been executed; report execution only from successful tool results.",
            routingProfile=profile(listOf("código","codigo","code","debug","depura","deadlock","bug","refactor","viewmodel","implementa","programa","function","función","kotlin"),listOf("Revisa esta clase por deadlock","Review this Kotlin code"),listOf("Resume este PDF","Excel totals"),setOf("kt","java","py","js","ts","tsx","jsx","c","cpp","h","rs","go","swift","cs","sh")),createdAt=0),
        SkillDefinition("skill.spreadsheet-analysis","Spreadsheet Analysis","Inspect spreadsheet cells, formulas, rows and totals.",origin=SkillOrigin.BUILT_IN,
            instructions="For spreadsheets, preserve sheet names, cell/row/column references and formulas. Compare totals with the supplied values; do not infer missing cells. Use available file tools for bounded passages and calculation tools when useful. Explain assumptions and distinguish stored formulas from evaluated results.",
            routingProfile=profile(listOf("excel","xlsx","csv","spreadsheet","planilla","hoja","sheet","formula","fórmula","totales","totals","columna","column","filas","rows"),listOf("Revisa este Excel","Why don't the totals match?"),listOf("Revisa código Kotlin","Resume este PDF"),setOf("xlsx","csv"),setOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/csv")),createdAt=0)
    )
    val general=AgentDefinition(AgentResolver.GENERAL,"General Agent","Personal local assistant",skillIds=definitions.map{it.id}.toSet(),createdAt=0)
}
