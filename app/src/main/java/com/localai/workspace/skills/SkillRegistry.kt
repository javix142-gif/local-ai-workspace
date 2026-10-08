package com.localai.workspace.skills

import com.google.gson.Gson
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

class SkillRegistry(private val db:AgentsSkillsDatabase) {
    private val gson=Gson()
    val changes=db.dao().skills().map{rows->rows.map{gson.fromJson(it.definitionJson,SkillDefinition::class.java)}}
    suspend fun initialize()=withContext(Dispatchers.IO){db.withTransaction{BuiltInSkills.definitions.forEach{db.dao().seedSkill(record(it))}}}
    suspend fun list():List<SkillDefinition> {initialize();return db.dao().skillList().map{gson.fromJson(it.definitionJson,SkillDefinition::class.java)}}
    suspend fun get(id:String):SkillDefinition? {initialize();return db.dao().skill(id)?.let{gson.fromJson(it.definitionJson,SkillDefinition::class.java)}}
    suspend fun enable(id:String,enabled:Boolean) {val skill=requireNotNull(get(id));db.dao().putSkill(record(skill.copy(enabled=enabled,updatedAt=System.currentTimeMillis())))}
    suspend fun install(text:String):SkillDefinition {val skill=SkillPackageParser.parse(text);update(skill);return skill}
    suspend fun update(skill:SkillDefinition) {
        require(skill.id !in BuiltInSkills.definitions.map{it.id}){"BUILT_IN_SKILL_READ_ONLY"}
        require(skill.name.isNotBlank()&&skill.description.isNotBlank()&&skill.instructions.isNotBlank()){"SKILL_METADATA_INVALID"}
        require(skill.instructions.toByteArray().size<=SkillPackageParser.MAX_BYTES){"SKILL_TOO_LARGE"}
        require(skill.origin!=SkillOrigin.BUILT_IN){"BUILT_IN_ORIGIN_RESERVED"}
        val previous=get(skill.id)
        val sameName=list().firstOrNull{it.name.equals(skill.name,true) && it.id!=skill.id}
        require(sameName==null){"SKILL_DUPLICATE_NAME"}
        require(previous==null || previous.origin==skill.origin){"SKILL_ORIGIN_MISMATCH"}
        val normalized=skill.copy(contentHash=SkillDefinition.hash(skill.instructions),createdAt=previous?.createdAt ?: skill.createdAt,updatedAt=System.currentTimeMillis())
        db.dao().putSkill(record(normalized))
    }
    suspend fun remove(id:String) {require(id !in BuiltInSkills.definitions.map{it.id}){"BUILT_IN_SKILL_READ_ONLY"};db.dao().deleteSkill(id)}
    suspend fun export(id:String)=SkillPackageParser.export(requireNotNull(get(id)))
    private fun record(s:SkillDefinition)=SkillRecord(s.id,gson.toJson(s))
}
