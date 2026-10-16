package com.localai.workspace.semantic.v2

import com.localai.workspace.documents.CodeLineChunker

/** Bounded, language-neutral, line-aware fallback; line provenance is original-file based. */
object CodeSegments {
    data class Chunk(val text:String,val lineStart:Int,val lineEnd:Int)
    fun chunk(text:String,maxCharacters:Int=1800):List<Chunk> =
        CodeLineChunker.chunk(text,maxCharacters).map { Chunk(it.text,it.lineStart,it.lineEnd) }
}
