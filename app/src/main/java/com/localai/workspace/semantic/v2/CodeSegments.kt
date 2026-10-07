package com.localai.workspace.semantic.v2

/** Bounded, language-neutral, line-aware fallback; line provenance is original-file based. */
object CodeSegments {
    data class Chunk(val text:String,val lineStart:Int,val lineEnd:Int)
    fun chunk(text:String,maxCharacters:Int=1800):List<Chunk> {
        require(maxCharacters>0)
        val lines=text.split('\n');val chunks=mutableListOf<Chunk>();val buffer=StringBuilder();var start=1
        fun flush(end:Int){if(buffer.isNotEmpty()){chunks+=Chunk(buffer.toString(),start,end);buffer.setLength(0)}}
        lines.forEachIndexed { i,line ->
            require(line.length<=maxCharacters) { "CODE_LINE_EXCEEDS_SEGMENT_LIMIT" }
            if(buffer.isNotEmpty() && buffer.length+line.length+1>maxCharacters){flush(i);start=i+1}
            if(i+1>start)buffer.append('\n');buffer.append(line)
        }
        flush(lines.size);return chunks.filter { it.text.isNotBlank() }
    }
}
