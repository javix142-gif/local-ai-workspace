package com.localai.workspace.domain.rag

import com.localai.workspace.domain.model.CitationCandidate
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.domain.model.ResolvedCitation

data class CitationValidationResult(
    val valid: List<ResolvedCitation>,
    val invalidEvidenceIds: List<String>,
)

/** Resolves model-provided IDs only against evidence selected for the current turn. */
class CitationValidator {
    fun validate(
        candidates: List<CitationCandidate>,
        includedEvidence: List<Evidence>,
    ): CitationValidationResult {
        val byId = includedEvidence.associateBy(Evidence::id)
        val valid = mutableListOf<ResolvedCitation>()
        val invalid = mutableListOf<String>()
        candidates.distinctBy { it.evidenceId }.forEach { candidate ->
            val evidence = byId[candidate.evidenceId]
            if (evidence == null) invalid += candidate.evidenceId
            else valid += ResolvedCitation(evidence, candidate.claimAnchor)
        }
        return CitationValidationResult(valid = valid, invalidEvidenceIds = invalid)
    }

    /** Extracts only the app-owned citation namespace; document text cannot mint metadata. */
    fun extractCandidates(answer: String): List<CitationCandidate> =
        Regex("\\b(?:LCL|WEB)-[A-F0-9]{4,12}\\b")
            .findAll(answer.uppercase())
            .map { CitationCandidate(it.value) }
            .toList()
}
