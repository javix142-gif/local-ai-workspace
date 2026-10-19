package com.localai.workspace.ui

import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.data.DocumentEntity
import com.localai.workspace.data.DocumentSegmentEntity
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.semantic.v2.SemanticSegmentRecord
import com.localai.workspace.semantic.v2.SemanticSourceRecord
import org.junit.Assert.*
import org.junit.Test

class CitationSourcePreviewTest {
    private val citation = CitationEvidenceEntity(
        id = "citation-1", conversationId = "conversation-1", messageId = "assistant-1",
        evidenceId = "LCL-42", segmentId = 42, excerpt = "short cited excerpt",
        documentId = "document-1", sourceLabel = "notes.txt", pageStart = 3, pageEnd = 3,
        charStart = 120, charEnd = 148, retrievalScore = .9, createdAt = 1,
    )
    private val document = DocumentEntity(
        id = "document-1", projectId = "project-1", displayName = "notes.txt",
        localPath = "/private/project-1/notes.txt", mimeType = "text/plain",
        fileHash = "hash", byteSize = 512, extractionStatus = "READY", indexingStatus = "READY",
        importedAt = 1, updatedAt = 1,
    )
    private val legacySegment = DocumentSegmentEntity(
        id = 42, documentId = "document-1", segmentIndex = 4, pageStart = 3,
        charStart = 100, charEnd = 180, text = "full indexed segment with the exact matching context",
        normalizedText = "full indexed segment with the exact matching context", contentHash = "segment-hash",
    )

    @Test fun opensOnlyTheMatchingStoredSegmentAndRetainsItsLocation() {
        val preview = resolveCitationSourcePreview(
            citation, "conversation-1", "project-1", "PROJECT:project-1",
            document, legacySegment, semanticSource = null, semanticSegment = null,
        )

        assertNotNull(preview)
        assertEquals("notes.txt", preview!!.sourceName)
        assertEquals("Page 3 · Text offsets 120–148", preview.location)
        assertEquals(legacySegment.text, preview.text)
        assertTrue(preview.indexedSegmentAvailable)
        assertTrue(preview.notice.contains("not a full-document review"))
    }

    @Test fun wrongDocumentOrSegmentNeverSubstitutesHomonymousContent() {
        val foreignDocument = document.copy(id = "document-2", projectId = "project-2", displayName = "notes.txt")
        val foreignSegment = legacySegment.copy(id = 42, documentId = "document-2", text = "foreign project secret")
        val preview = resolveCitationSourcePreview(
            citation, "conversation-1", "project-1", "PROJECT:project-1",
            foreignDocument, foreignSegment, semanticSource = null, semanticSegment = null,
        )

        assertNotNull(preview)
        assertEquals("notes.txt", preview!!.sourceName)
        assertEquals(citation.excerpt, preview.text)
        assertFalse(preview.indexedSegmentAvailable)
        assertFalse(preview.text.contains("foreign project secret"))
        assertTrue(preview.notice.contains("unavailable in this workspace"))
    }

    @Test fun missingSegmentUsesSavedExcerptAndDifferentConversationIsRejected() {
        val fallback = resolveCitationSourcePreview(
            citation, "conversation-1", "project-1", "PROJECT:project-1",
            document, legacySegment.copy(id = 99), semanticSource = null, semanticSegment = null,
        )
        assertEquals(citation.excerpt, fallback?.text)
        assertFalse(fallback!!.indexedSegmentAvailable)
        assertNull(resolveCitationSourcePreview(
            citation, "conversation-2", "project-1", "PROJECT:project-1",
            document, legacySegment, semanticSource = null, semanticSegment = null,
        ))
    }

    @Test fun semanticSegmentRequiresTheSameAuthorizedDocumentAndScope() {
        val semanticCitation = citation.copy(segmentId = null, semanticSegmentId = "index:document-1:0")
        val source = SemanticSourceRecord(
            id = "generation:document-1", scopeKey = "PROJECT:project-1", sourceType = "DOCUMENT",
            displayName = "notes.txt", mimeType = "text/plain", reference = document.localPath,
            size = document.byteSize, modifiedAt = document.updatedAt, contentHash = document.fileHash,
        )
        val segment = SemanticSegmentRecord(
            id = "index:document-1:0", sourceId = source.id, modality = "TEXT",
            textContent = "semantic source segment with exact origin", title = "notes.txt", page = 3,
            lineStart = null, lineEnd = null, startMs = null, endMs = null, contentHash = "semantic-hash",
        )
        val preview = resolveCitationSourcePreview(
            citation = semanticCitation,
            activeConversationId = "conversation-1",
            expectedProjectId = "project-1",
            expectedSemanticScopeKey = "PROJECT:project-1",
            document = document,
            legacySegment = null,
            semanticSource = source,
            semanticSegment = segment,
        )
        assertEquals(segment.textContent, preview?.text)
        assertTrue(preview!!.indexedSegmentAvailable)

        val wrongScope = resolveCitationSourcePreview(
            citation = semanticCitation,
            activeConversationId = "conversation-1",
            expectedProjectId = "project-1",
            expectedSemanticScopeKey = "PROJECT:project-1",
            document = document,
            legacySegment = null,
            semanticSource = source.copy(scopeKey = "PROJECT:other"),
            semanticSegment = segment,
        )
        assertEquals(semanticCitation.excerpt, wrongScope?.text)
        assertFalse(wrongScope!!.indexedSegmentAvailable)
    }

    @Test fun persistedCitationsGroupByTheirAssistantMessage() {
        val second = citation.copy(id = "citation-2", messageId = "assistant-2", evidenceId = "LCL-43")
        val grouped = groupCitationEvidenceByMessage(listOf(citation, second))

        assertEquals(listOf(citation), grouped["assistant-1"])
        assertEquals(listOf(second), grouped["assistant-2"])
    }

    @Test fun evidenceMapperAndSourcePreviewRetainResolvedSheetQualifiedCells() {
        val references = listOf("Hoja1!A3", "'O''Brien'!A1")
        val evidence = Evidence(
            id = "SV2-CELL-1", segmentId = null, documentId = document.id,
            documentTitle = document.displayName, excerpt = "A3=4; 'O''Brien'!A1=5",
            pageStart = null, pageEnd = null, charStart = null, charEnd = null,
            retrievalScore = 0.9, semanticSegmentId = "segment-1", cellReferences = references,
        )

        val saved = evidence.toCitationEvidenceEntity(
            id = "citation-cell-1", conversationId = "conversation-1", messageId = "assistant-1", createdAt = 2,
        )

        assertEquals(references, CitationCellReferenceCodec.decode(saved.cellReferencesJson))
        val preview = resolveCitationSourcePreview(
            saved, "conversation-1", "project-1", "PROJECT:project-1",
            document, legacySegment = null, semanticSource = null, semanticSegment = null,
        )
        assertEquals("Cells Hoja1!A3, 'O''Brien'!A1", preview?.location)
        assertEquals(saved.excerpt, preview?.text)
        assertFalse(preview!!.indexedSegmentAvailable)
    }

    @Test fun cellReferenceCodecRejectsOverflowAndCorruptMetadataWithoutTruncation() {
        val tooMany = (1..(CitationCellReferenceCodec.MAX_REFERENCES + 1)).map { "Sheet!A$it" }
        assertThrows(IllegalArgumentException::class.java) { CitationCellReferenceCodec.encode(tooMany) }
        assertNull(CitationCellReferenceCodec.decode("not-json"))
    }
}
