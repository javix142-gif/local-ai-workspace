package com.localai.workspace.performance

import com.localai.workspace.data.DocumentIngestionDiagnostics
import com.localai.workspace.semantic.RetrievalIndexDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPipelineTimelinePresentationTest {
    @Test
    fun ordersMeasuredImportStagesAndIncludesOnlyTheCorrelatedBackgroundIndex() {
        val imported = DocumentIngestionDiagnostics(
            status = "READY", format = "XLSX", sourceBytes = 123,
            copyHashMs = 3, metadataCommitMs = 4, parseMs = 5, chunkMs = 6,
            segmentCommitMs = 7, postImportCallbackMs = 8, totalMs = 35,
            pageCount = 1, segmentCount = 2, indexRunId = "opaque-index-run",
        )
        val sameRun = RetrievalIndexDiagnostics(
            status = "READY", durationMs = 19, candidateSegments = 2, processedSegments = 2,
            runId = "opaque-index-run",
        )

        val rows = DocumentPipelineTimelinePresentation.rows(imported, sameRun)
        assertEquals(listOf(
            "Copy and hash", "Document metadata commit", "Parse", "Chunk", "Segment commit",
            "Post-import callback", "Import total", "Background EG1 project index",
        ), rows.map { it.stage })
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L, 8L, 35L, 19L), rows.map { it.durationMs })
        assertEquals("READY", rows.last().outcome)
        assertFalse(rows.any { it.stage.contains("private") || it.stage.contains("document name") })
    }

    @Test
    fun doesNotJoinAStaleOrUnidentifiedIndexRunToTheImport() {
        val imported = DocumentIngestionDiagnostics(status = "READY", format = "CSV", indexRunId = "scheduled-a")
        val stale = RetrievalIndexDiagnostics(status = "READY", durationMs = 900, runId = "different-run")
        val staleRow = DocumentPipelineTimelinePresentation.rows(imported, stale).last()
        assertNull(staleRow.durationMs)
        assertTrue(staleRow.outcome!!.contains("another run"))

        val noCurrentIndex = DocumentPipelineTimelinePresentation.rows(imported, RetrievalIndexDiagnostics()).last()
        assertNull(noCurrentIndex.durationMs)
        assertTrue(noCurrentIndex.outcome!!.contains("unavailable"))
        assertTrue(DocumentPipelineTimelinePresentation.rows(null, stale).isEmpty())
    }
}
