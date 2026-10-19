package com.localai.workspace.ui

import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.data.DocumentEntity
import com.localai.workspace.data.DocumentSegmentEntity
import com.localai.workspace.semantic.v2.SemanticSegmentRecord
import com.localai.workspace.semantic.v2.SemanticSourceRecord

/** A preview of the exact indexed segment attached to a citation, never the whole source file. */
internal data class CitationSourcePreview(
    val sourceName: String,
    val location: String,
    val text: String,
    val notice: String,
    val indexedSegmentAvailable: Boolean,
)

internal fun groupCitationEvidenceByMessage(
    evidence: List<CitationEvidenceEntity>,
): Map<String, List<CitationEvidenceEntity>> = evidence.groupBy { it.messageId }

/** Pure scope/identity check shared by the UI flow and focused host tests. */
internal fun resolveCitationSourcePreview(
    citation: CitationEvidenceEntity,
    activeConversationId: String,
    expectedProjectId: String,
    expectedSemanticScopeKey: String,
    document: DocumentEntity?,
    legacySegment: DocumentSegmentEntity?,
    semanticSource: SemanticSourceRecord?,
    semanticSegment: SemanticSegmentRecord?,
): CitationSourcePreview? {
    if (citation.conversationId != activeConversationId) return null

    val documentMatches = document?.id == citation.documentId && document.projectId == expectedProjectId
    val legacyText = legacySegment
        ?.takeIf { documentMatches && it.id == citation.segmentId && it.documentId == citation.documentId }
        ?.text
        ?.takeIf(String::isNotBlank)
    val semanticText = semanticSegment
        ?.takeIf {
            documentMatches &&
                it.id == citation.semanticSegmentId &&
                it.sourceId == semanticSource?.id &&
                it.modality in setOf("TEXT", "CODE") &&
                !it.textContent.isNullOrBlank() &&
                semanticSource.scopeKey == expectedSemanticScopeKey &&
                semanticSource.sourceType == "DOCUMENT" &&
                semanticSource.permissionState == "AUTHORIZED" &&
                semanticSource.reference == document?.localPath
        }
        ?.textContent

    val indexedText = legacyText ?: semanticText
    val cellReferences = CitationCellReferenceCodec.decode(citation.cellReferencesJson)
    val sourceName = if (documentMatches) document!!.displayName else citation.sourceLabel.ifBlank { "Saved citation" }
    val location = buildList {
        citation.pageStart?.let { start ->
            val end = citation.pageEnd?.takeIf { it != start }
            add(if (end == null) "Page $start" else "Pages $start–$end")
        }
        citation.lineStart?.let { start ->
            val end = citation.lineEnd?.takeIf { it != start }
            add(if (end == null) "Line $start" else "Lines $start–$end")
        }
        if (citation.charStart != null || citation.charEnd != null) {
            add("Text offsets ${citation.charStart ?: "?"}–${citation.charEnd ?: "?"}")
        }
        if (cellReferences == null) add("Cell location unavailable")
        else if (cellReferences.isNotEmpty()) add("Cells ${cellReferences.joinToString(", ")}")
    }.joinToString(" · ").ifBlank { "Indexed segment" }

    return CitationSourcePreview(
        sourceName = sourceName,
        location = location,
        text = indexedText ?: citation.excerpt,
        notice = when {
            cellReferences == null -> "Cell locator metadata is unavailable. " + when {
                indexedText != null -> "Indexed segment only; this is not a full-document review."
                !documentMatches -> "The source is unavailable in this workspace. Showing the saved citation excerpt."
                else -> "The indexed segment is unavailable. Showing the saved citation excerpt."
            }
            indexedText != null -> "Indexed segment only; this is not a full-document review."
            !documentMatches -> "The source is unavailable in this workspace. Showing the saved citation excerpt."
            else -> "The indexed segment is unavailable. Showing the saved citation excerpt."
        },
        indexedSegmentAvailable = indexedText != null,
    )
}
