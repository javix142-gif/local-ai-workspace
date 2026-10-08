package com.localai.workspace.skills

/** Interoperable SKILL.md subset, never a script/package executor. Unknown frontmatter is preserved as data. */
object SkillPackageParser {
    const val MAX_BYTES=64*1024
    fun parse(input:String):SkillDefinition {
        require(input.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"SKILL_TOO_LARGE"}
        val text=input.removePrefix("\uFEFF").replace("\r\n","\n")
        require(text.startsWith("---\n")){"SKILL_METADATA_MISSING"}
        val end=text.indexOf("\n---",4)
        require(end>0 && (end+4==text.length || text[end+4]=='\n')){"SKILL_METADATA_INVALID"}
        val fields=linkedMapOf<String,String>();val tags=mutableListOf<String>();var current:String?=null
        for(line in text.substring(4,end).lines()) {
            if(line.isBlank()||line.trimStart().startsWith("#"))continue
            if(line.trimStart().startsWith("- ")) {
                val key=requireNotNull(current){"SKILL_METADATA_INVALID"}
                val value=unquote(line.trim().removePrefix("- "))
                if(key=="tags")tags+=value else fields[key]=fields[key].orEmpty()+"\n- $value"
            } else {
                val colon=line.indexOf(':');require(colon>0){"SKILL_METADATA_INVALID"}
                val key=line.substring(0,colon).trim();require(key !in fields){"SKILL_DUPLICATE_METADATA"}
                current=key;fields[key]=unquote(line.substring(colon+1).trim())
            }
        }
        val name=fields["name"].orEmpty();val description=fields["description"].orEmpty()
        require(name.isNotBlank()&&name.length<=100){"SKILL_NAME_REQUIRED"}
        require(description.isNotBlank()&&description.length<=1000){"SKILL_DESCRIPTION_REQUIRED"}
        val id="skill.user-"+name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"),"-").trim('-').ifBlank{SkillDefinition.hash(name).take(12)}
        val body=text.substring(end+4).trim();require(body.isNotBlank()){"SKILL_INSTRUCTIONS_REQUIRED"}
        if(tags.isEmpty())tags+=fields["tags"].orEmpty().removePrefix("[").removeSuffix("]").split(',').map{unquote(it.trim())}.filter{it.isNotEmpty()}
        return SkillDefinition(id,name,description,version=fields["version"].orEmpty().ifBlank{"1.0"},origin=SkillOrigin.IMPORTED,
            instructions=body,tags=tags,externalMetadata=fields.filterKeys{it !in setOf("name","description","version","tags")})
    }
    fun export(skill:SkillDefinition):String=buildString {
        append("---\nname: ").append(quote(skill.name)).append("\ndescription: ").append(quote(skill.description)).append("\nversion: ").append(quote(skill.version)).append("\ntags:\n")
        skill.tags.forEach{append("  - ").append(quote(it)).append('\n')}
        skill.externalMetadata.toSortedMap().forEach{(key,value)->if(key.matches(Regex("[A-Za-z0-9_-]+")))append(key).append(": ").append(quote(value)).append('\n')}
        append("---\n\n").append(skill.instructions).append('\n')
    }
    private fun quote(value:String)="\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""
    private fun unquote(value:String):String=if(value.startsWith('"')&&value.endsWith('"'))value.substring(1,value.length-1).replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\") else if(value.startsWith('\'')&&value.endsWith('\''))value.substring(1,value.length-1).replace("''","'")else value
}
