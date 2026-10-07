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
}
