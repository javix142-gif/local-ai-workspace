package com.localai.workspace.ui

import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.domain.model.Evidence

/** Keeps the validated evidence identity and every supported locator when a citation is persisted. */
internal fun Evidence.toCitationEvidenceEntity(
    id: String,
    conversationId: String,
    messageId: String,
    createdAt: Long,
): CitationEvidenceEntity = CitationEvidenceEntity(
    id = id,
    conversationId = conversationId,
    messageId = messageId,
    evidenceId = this.id,
    segmentId = segmentId,
    excerpt = excerpt,
    documentId = documentId,
    sourceLabel = documentTitle,
    pageStart = pageStart,
    pageEnd = pageEnd,
    charStart = charStart,
    charEnd = charEnd,
    retrievalScore = retrievalScore,
    createdAt = createdAt,
    semanticSegmentId = semanticSegmentId,
    lineStart = lineStart,
    lineEnd = lineEnd,
    cellReferencesJson = CitationCellReferenceCodec.encode(cellReferences),
)
