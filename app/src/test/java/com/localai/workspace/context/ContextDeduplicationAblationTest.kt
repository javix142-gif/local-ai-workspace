package com.localai.workspace.context

import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDeduplicationAblationTest {
    private val access = ScopeAccess(projectId = "project-a", sessionId = "chat-a")

    private fun source(
        id: String,
        text: String,
        scope: SemanticScope = SemanticScope(ScopeType.PROJECT, "project-a"),
        sourceId: String,
        documentId: String,
        segmentId: String,
        priority: Int = 50,
        score: Double = 0.0,
    ) = ContextItem(
        id = id,
        kind = ContextKind.SOURCE,
        text = text,
        scope = scope,
        trust = ContextTrust.UNTRUSTED_SOURCE,
        priority = priority,
        score = score,
        provenance = ContextProvenance(
            sourceId = sourceId,
            documentId = documentId,
            segmentId = segmentId,
            sourceName = documentId,
        ),
    )

    @Test
    fun duplicateAblationAt4096IsStableAcrossRepeatedBuildsAndInputOrder() {
        val query = "Compare the current project revenue evidence and explain the priority order."
        val request = ContextRequest(query, access, contextWindow = 4096, reservedOutput = 256, extraReserve = 128)
        val high = source(
            id = "E-HIGH",
            text = "High relevance quarterly revenue evidence. ".repeat(18),
            sourceId = "source-high",
            documentId = "doc-high",
            segmentId = "segment-high",
            priority = 90,
            score = 0.95,
        )
        val secondary = source(
            id = "E-SECONDARY",
            text = "Secondary project comparison evidence. ".repeat(18),
            sourceId = "source-secondary",
            documentId = "doc-secondary",
            segmentId = "segment-secondary",
            priority = 70,
            score = 0.55,
        )
        val baseItems = listOf(high, secondary)
        val repeatedItems = baseItems + List(8) { index -> high.copy(id = "E-HIGH-copy-$index") }
        val builder = ContextBuilder()
        val empty = builder.build(request, emptyList())
        val rawDuplicateEstimate = empty.estimatedInputTokens + repeatedItems.sumOf { it.estimatedTokens }
        assertTrue("The un-deduplicated fixture must exceed its 4096-derived input budget", rawDuplicateEstimate > empty.inputBudget)

        val withoutDuplicates = builder.build(request, baseItems)
        val withDuplicates = builder.build(request, repeatedItems)
        val reversedDuplicates = builder.build(request, repeatedItems.reversed())

        assertEquals(listOf("E-HIGH", "E-SECONDARY"), withoutDuplicates.included.map { it.id })
        assertEquals(withoutDuplicates.included.map { it.id }, withDuplicates.included.map { it.id })
        assertEquals(withDuplicates.included.map { it.id }, reversedDuplicates.included.map { it.id })
        assertEquals(withoutDuplicates.estimatedInputTokens, withDuplicates.estimatedInputTokens)
        assertEquals(withDuplicates.estimatedInputTokens, reversedDuplicates.estimatedInputTokens)
        assertEquals(8, withDuplicates.dropped.count { it.reason == "DUPLICATE_CONTENT" })
        assertFalse("Routine deduplication must not become a material user warning", withDuplicates.requiresUserNotice)
        assertTrue(withDuplicates.estimatedInputTokens <= withDuplicates.inputBudget)
        assertEquals(4096, withDuplicates.request.contextWindow)
        assertEquals(query, withDuplicates.request.query)
        assertTrue(withDuplicates.conversation().userMessage.contains(query))
    }

    @Test
    fun sameTextFromDifferentSourcesAndScopesKeepsSeparateProvenanceAndProjectIsolation() {
        val sharedText = "A shared passage that exists independently in more than one authorized source."
        val sharedOrigin = source(
            id = "20-project-a",
            text = sharedText,
            sourceId = "same-source-id",
            documentId = "same-document-id",
            segmentId = "same-segment-id",
        )
        val globalOrigin = sharedOrigin.copy(
            id = "10-global",
            scope = SemanticScope(ScopeType.GLOBAL),
        )
        val secondDocument = source(
            id = "30-project-a-second-source",
            text = sharedText,
            sourceId = "different-source-id",
            documentId = "different-document-id",
            segmentId = "different-segment-id",
        )
        val foreignProject = source(
            id = "00-project-b",
            text = sharedText,
            scope = SemanticScope(ScopeType.PROJECT, "project-b"),
            sourceId = "same-source-id",
            documentId = "same-document-id",
            segmentId = "same-segment-id",
        )

        val bundle = ContextBuilder().build(
            ContextRequest("Compare this passage", access),
            listOf(foreignProject, secondDocument, sharedOrigin, globalOrigin),
        )

        assertEquals(setOf("10-global", "20-project-a", "30-project-a-second-source"), bundle.included.map { it.id }.toSet())
        assertEquals(3, bundle.conversation().userMessage.split("<context-data ").size - 1)
        assertFalse(bundle.conversation().userMessage.contains("id=\"00-project-b\""))
        assertTrue(bundle.dropped.any { it.item.id == "00-project-b" && it.reason == "SCOPE_NOT_ALLOWED" })
        assertFalse("Scope filtering and duplicate trimming are routine diagnostics", bundle.requiresUserNotice)
    }
}
