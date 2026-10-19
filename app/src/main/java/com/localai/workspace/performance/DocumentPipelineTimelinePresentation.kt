package com.localai.workspace.performance

import com.localai.workspace.data.DocumentIngestionDiagnostics
import com.localai.workspace.semantic.RetrievalIndexDiagnostics

/** Ordered observations from the import operation and its separately scheduled background index. */
data class DocumentPipelineTimelineRow(
    val stage: String,
    val durationMs: Long?,
    val outcome: String? = null,
)

object DocumentPipelineTimelinePresentation {
    fun rows(
        ingestion: DocumentIngestionDiagnostics?,
        latestIndex: RetrievalIndexDiagnostics,
    ): List<DocumentPipelineTimelineRow> {
        ingestion ?: return emptyList()
        return buildList {
            fun stage(label: String, duration: Long?) {
                if (duration != null) add(DocumentPipelineTimelineRow(label, duration))
            }
            stage("Copy and hash", ingestion.copyHashMs)
            stage("Document metadata commit", ingestion.metadataCommitMs)
            stage("Parse", ingestion.parseMs)
            stage("Chunk", ingestion.chunkMs)
            stage("Segment commit", ingestion.segmentCommitMs)
            stage("Post-import callback", ingestion.postImportCallbackMs)
            add(DocumentPipelineTimelineRow("Import total", ingestion.totalMs, ingestion.status))

            val scheduledIndex = ingestion.indexRunId ?: return@buildList
            if (latestIndex.runId == scheduledIndex) {
                add(DocumentPipelineTimelineRow(
                    "Background EG1 project index",
                    latestIndex.durationMs,
                    latestIndex.status + (latestIndex.failureType?.let { " · $it" } ?: ""),
                ))
            } else {
                val outcome = if (latestIndex.runId == null) {
                    "scheduled; matching diagnostics unavailable"
                } else {
                    "scheduled; latest index diagnostics belong to another run"
                }
                add(DocumentPipelineTimelineRow("Background EG1 project index", null, outcome))
            }
        }
    }
}
