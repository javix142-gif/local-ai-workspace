package com.localai.workspace.rag

import com.localai.workspace.domain.model.CitationCandidate
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.domain.rag.CitationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationValidatorTest {
    private val evidence = Evidence(
        id = "LCL-AB12",
        segmentId = 7,
        documentId = "doc",
        documentTitle = "policy.txt",
        excerpt = "Authoritative local text",
        pageStart = null,
        pageEnd = null,
        charStart = 0,
        charEnd = 28,
        retrievalScore = 1.0,
    )

    @Test
    fun fabricatedIdsAreRejected() {
        val result = CitationValidator().validate(
            listOf(CitationCandidate("LCL-AB12"), CitationCandidate("LCL-FAKE")),
            listOf(evidence),
        )
        assertEquals(listOf("LCL-FAKE"), result.invalidEvidenceIds)
        assertEquals(listOf("LCL-AB12"), result.valid.map { it.evidence.id })
        assertEquals("policy.txt", result.valid.single().evidence.documentTitle)
    }

    @Test
    fun extractionUsesOnlyApplicationNamespaces() {
        val candidates = CitationValidator().extractCandidates("See [LCL-AB12], WEB-00FF and DOC-1234.")
        assertTrue(candidates.map { it.evidenceId }.containsAll(listOf("LCL-AB12", "WEB-00FF")))
        assertTrue(candidates.none { it.evidenceId.startsWith("DOC-") })
    }

    @Test
    fun semanticV2CitationIdsAreAcceptedOnlyWhenPresentInTurnEvidence() {
        val code = evidence.copy(
            id = "SV2-0123456789ABCDEF",
            segmentId = null,
            documentTitle = "src/Answer.kt",
            semanticSegmentId = "project:answer:0",
            lineStart = 4,
            lineEnd = 6,
        )
        val validator = CitationValidator()
        val extracted = validator.extractCandidates("See [SV2-0123456789abcdef] and [SV2-0123456789ABCDEG].")
        assertEquals(listOf(code.id), extracted.map { it.evidenceId })
        assertEquals(listOf(code.id), validator.validate(extracted, listOf(code)).valid.map { it.evidence.id })
        assertEquals(listOf(code.id), validator.validate(extracted, emptyList()).invalidEvidenceIds)
    }
}
