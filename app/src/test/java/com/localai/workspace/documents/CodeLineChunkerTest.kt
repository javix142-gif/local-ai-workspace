package com.localai.workspace.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLineChunkerTest {
    @Test
    fun preservesLeadingBlankLineWhenItFitsTheChunkBudget() {
        val chunk = CodeLineChunker.chunk("\nfun value() = 1", maxCharacters = 32).single()

        assertEquals("\nfun value() = 1", chunk.text)
        assertEquals(1, chunk.lineStart)
        assertEquals(2, chunk.lineEnd)
        assertTrue(chunk.text.length <= 32)
    }

    @Test
    fun maxLengthLineAfterLeadingBlankLineDoesNotExceedBudgetOrMisreportRange() {
        val sourceLine = "x".repeat(8)
        val chunk = CodeLineChunker.chunk("\n$sourceLine", maxCharacters = sourceLine.length).single()

        assertEquals(sourceLine, chunk.text)
        assertEquals(2, chunk.lineStart)
        assertEquals(2, chunk.lineEnd)
        assertTrue(chunk.text.length <= sourceLine.length)
    }

    @Test
    fun internalBlankLineKeepsItsSourceRangeAcrossChunkBoundary() {
        val chunks = CodeLineChunker.chunk("one\n\nthree", maxCharacters = 5)

        assertEquals(listOf("one\n", "three"), chunks.map { it.text })
        assertEquals(listOf(1, 3), chunks.map { it.lineStart })
        assertEquals(listOf(2, 3), chunks.map { it.lineEnd })
        assertTrue(chunks.all { it.text.length <= 5 })
    }
}
