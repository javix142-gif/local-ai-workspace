package com.localai.workspace.rag

import com.localai.workspace.data.DocumentDao
import com.localai.workspace.data.DocumentSegmentEntity
import com.localai.workspace.domain.model.Evidence
import com.localai.workspace.domain.model.SourceTrust
import com.localai.workspace.domain.rag.EmbeddingRuntime
import com.localai.workspace.domain.rag.ReciprocalRankFusion
import com.localai.workspace.domain.rag.cosineSimilarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest

class LocalRetrievalService(private val documents: DocumentDao, private val embeddingRuntime: EmbeddingRuntime) {
    suspend fun retrieve(projectId: String, query: String, limit: Int = 3,
        documentIds: Set<String> = emptySet()): List<Evidence> = withContext(Dispatchers.Default) {
        if (query.isBlank() || limit <= 0 || (documentIds.isEmpty() && RetrievalQuery.greeting(query))) return@withContext emptyList()
        val ready = documents.forProject(projectId).filter { it.extractionStatus == "READY" && it.indexingStatus == "READY" }
        if (ready.isEmpty()) return@withContext emptyList()
        val terms = RetrievalQuery.terms(query)
        val selected = if (documentIds.isNotEmpty()) ready.filter { it.id in documentIds }
            else if (RetrievalQuery.refersToDocument(query)) ready.take(limit) else emptyList()
        if (documentIds.isNotEmpty() && selected.isEmpty()) return@withContext emptyList()
        val lexical = if (terms.isEmpty()) emptyList() else documents.searchLexical(projectId,
            terms.joinToString(" OR ") { "\"$it\"" }, limit * 6)
        if (selected.isNotEmpty()) {
            val picked = selected.take(limit).flatMap { doc ->
                (lexical.filter { it.documentId == doc.id } + documents.openingSegments(doc.id, limit))
                    .distinctBy { it.id }.take(limit)
            }
            val primary = selected.mapNotNull { doc -> picked.firstOrNull { it.documentId == doc.id } }
            return@withContext (primary + picked).distinctBy { it.id }.take(limit).mapNotNull { evidence(projectId, it, 1.0) }
        }
        if (terms.isEmpty()) return@withContext emptyList()
        val patterns = terms.map { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])") }
        val allSegments = documents.segmentsForProject(projectId, limit = 1_000).filter { segment ->
            patterns.any { it.containsMatchIn(RetrievalQuery.normalize(segment.normalizedText)) }
        }
        if (allSegments.isEmpty() && lexical.isEmpty()) return@withContext emptyList()
        val queryEmbedding = embeddingRuntime.embed(query)
        val vectors = allSegments.map { segment ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            segment.id to cosineSimilarity(queryEmbedding, embeddingRuntime.embed(segment.normalizedText))
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }.take(limit * 6)
        val segmentsById = (lexical + allSegments).associateBy { it.id }
        ReciprocalRankFusion.fuse(lexical.map { it.id }, vectors).take(limit).mapNotNull { rank ->
            segmentsById[rank.segmentId]?.let { evidence(projectId, it, rank.fusedScore) }
        }
    }

    private suspend fun evidence(projectId: String, segment: DocumentSegmentEntity, score: Double): Evidence? {
        val document = documents.documentForSegment(segment.id)?.takeIf {
            it.projectId == projectId && it.extractionStatus == "READY" && it.indexingStatus == "READY"
        } ?: return null
        // A bounded excerpt is a source range, not a claim to have sent the whole file.
        val excerpt = segment.text.take(1000)
        return Evidence(id = evidenceId(projectId, segment.id), segmentId = segment.id,
            documentId = document.id, documentTitle = document.displayName, excerpt = excerpt,
            pageStart = segment.pageStart, pageEnd = segment.pageEnd, charStart = segment.charStart,
            charEnd = if (excerpt.length < segment.text.length) segment.charStart?.plus(excerpt.length) else segment.charEnd,
            trust = SourceTrust.UNTRUSTED_DOCUMENT, retrievalScore = score)
    }
    private fun evidenceId(projectId: String, segmentId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$projectId:$segmentId".toByteArray())
            .joinToString("") { "%02X".format(it) }
        return "LCL-${digest.take(8)}"
    }
}
