package com.localai.workspace.rag

import com.localai.workspace.domain.rag.ReciprocalRankFusion
import org.junit.Assert.assertEquals
import org.junit.Test

class ReciprocalRankFusionTest {
    @Test
    fun combinesLexicalAndVectorCandidatesWithoutInventingIds() {
        val ranked = ReciprocalRankFusion.fuse(
            lexicalIds = listOf(10L, 20L),
            vectorScores = listOf(20L to 0.9, 30L to 0.8),
        )
        assertEquals(setOf(10L, 20L, 30L), ranked.map { it.segmentId }.toSet())
        assertEquals(20L, ranked.first().segmentId)
    }
}
