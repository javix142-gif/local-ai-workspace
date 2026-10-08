package com.localai.workspace.sources

enum class SourceType {
    PROJECT_DOCUMENT, CONVERSATION, STRUCTURED_MEMORY, SEMANTIC_SOURCE,
    DRIVE, GALLERY, CALENDAR, MCP_RESOURCE;
    companion object { val local=setOf(PROJECT_DOCUMENT,CONVERSATION,STRUCTURED_MEMORY,SEMANTIC_SOURCE) }
}
data class SourceAvailability(val type:SourceType,val available:Boolean,val reason:String)
/** Adapts existing source paths; future connector names grant no functionality. */
class SourceRegistry {
    fun list(allowed:Set<SourceType>)=SourceType.entries.map{SourceAvailability(it,it in SourceType.local && it in allowed,
        if(it !in SourceType.local)"UNAVAILABLE" else if(it !in allowed)"AGENT_NOT_ALLOWED" else "AVAILABLE")}
}
