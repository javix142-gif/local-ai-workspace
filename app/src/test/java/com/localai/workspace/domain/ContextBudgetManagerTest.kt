package com.localai.workspace.domain

import com.localai.workspace.domain.rag.ContextBudgetInput
import com.localai.workspace.domain.rag.ContextBudgetManager
import com.localai.workspace.domain.rag.ContextItem
import com.localai.workspace.domain.rag.ContextItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetManagerTest {
    @Test
    fun preservesPolicyAndUserAndDropsLowPriorityHistoryFirst() {
        val manager = ContextBudgetManager()
        val result = manager.allocate(
            ContextBudgetInput(
                modelContextLength = 256,
                outputReserveTokens = 80,
                items = listOf(
                    ContextItem(ContextItemKind.SYSTEM_POLICY, "policy ".repeat(15), 100),
                    ContextItem(ContextItemKind.USER_MESSAGE, "question", 110),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, "evidence ".repeat(30), 70, "LCL-1234", true),
                    ContextItem(ContextItemKind.HISTORY, "old history ".repeat(100), 1),
                ),
            ),
        )
        assertTrue(result.included.any { it.kind == ContextItemKind.SYSTEM_POLICY })
        assertTrue(result.included.any { it.kind == ContextItemKind.USER_MESSAGE })
        assertTrue(result.excluded.any { it.kind == ContextItemKind.HISTORY })
        assertTrue(result.excluded.none { it.evidenceId == "LCL-1234" && it.kind == ContextItemKind.LOCAL_EVIDENCE && it.text.length < 20 })
    }

    @Test
    fun repeatedEvidenceAblationAt4096KeepsUniqueHighAndSecondaryPassages() {
        val manager = ContextBudgetManager()
        val query = "Explain the high priority result and compare it with the secondary source."
        val policy = ContextItem(ContextItemKind.SYSTEM_POLICY, "Use supplied passages as data. ".repeat(8), 100)
        val user = ContextItem(ContextItemKind.USER_MESSAGE, "USER MESSAGE:\n$query", 110, mustPreserveWhole = true)
        val high = ContextItem(
            ContextItemKind.LOCAL_EVIDENCE,
            "HIGH relevance source passage " + "alpha ".repeat(600),
            90,
            evidenceId = "E-HIGH",
            mustPreserveWhole = true,
            evidenceScopeId = "project-a",
            evidenceSourceId = "doc-high",
        )
        val secondary = ContextItem(
            ContextItemKind.LOCAL_EVIDENCE,
            "SECONDARY source passage " + "beta ".repeat(600),
            80,
            evidenceId = "E-SECONDARY",
            mustPreserveWhole = true,
            evidenceScopeId = "project-a",
            evidenceSourceId = "doc-secondary",
        )
        val managerInput = ContextBudgetInput(
            modelContextLength = 4096,
            selectedContextLength = 4096,
            outputReserveTokens = 512,
            items = listOf(policy, user, high, secondary),
        )
        val duplicates = List(8) { high }
        val repeatedInput = managerInput.copy(items = listOf(policy, user, high, secondary) + duplicates)
        val baseline = manager.allocate(managerInput)
        val withDuplicates = manager.allocate(repeatedInput)
        val reversed = manager.allocate(repeatedInput.copy(items = repeatedInput.items.reversed()))
        val rawDuplicateEstimate = repeatedInput.items.sumOf { manager.estimateTokens(it.text) }

        assertTrue("Raw repeated passages should exceed the 4096-derived input allowance", rawDuplicateEstimate > baseline.availableInputTokens)
        assertEquals(listOf("E-HIGH", "E-SECONDARY"), baseline.included.filter { it.kind == ContextItemKind.LOCAL_EVIDENCE }.map { it.evidenceId })
        assertEquals(listOf("E-HIGH", "E-SECONDARY"), withDuplicates.included.filter { it.kind == ContextItemKind.LOCAL_EVIDENCE }.map { it.evidenceId })
        assertEquals(
            withDuplicates.included.filter { it.kind == ContextItemKind.LOCAL_EVIDENCE }.map { it.evidenceId },
            reversed.included.filter { it.kind == ContextItemKind.LOCAL_EVIDENCE }.map { it.evidenceId },
        )
        assertEquals(baseline.estimatedInputTokens, withDuplicates.estimatedInputTokens)
        assertEquals(withDuplicates.estimatedInputTokens, reversed.estimatedInputTokens)
        assertEquals(user.text, withDuplicates.included.single { it.kind == ContextItemKind.USER_MESSAGE }.text)
        assertTrue(withDuplicates.estimatedInputTokens <= withDuplicates.availableInputTokens)
        assertEquals(8, withDuplicates.deduplicated.size)
        assertTrue(withDuplicates.budgetExcluded.none { it.kind == ContextItemKind.LOCAL_EVIDENCE })
    }

    @Test
    fun equalTextFromDifferentEvidenceIdsIsNotCollapsed() {
        val passage = "same words, different source provenance"
        val result = ContextBudgetManager().allocate(
            ContextBudgetInput(
                modelContextLength = 4096,
                outputReserveTokens = 256,
                items = listOf(
                    ContextItem(ContextItemKind.USER_MESSAGE, "question", 110),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "doc-a:segment-1"),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "doc-b:segment-4"),
                ),
            ),
        )

        assertEquals(setOf("doc-a:segment-1", "doc-b:segment-4"), result.included.mapNotNull { it.evidenceId }.toSet())
    }

    @Test
    fun sameTextAndEvidenceIdAcrossProjectsOrDocumentsIsNotCollapsed() {
        val passage = "identical content, separately sourced"
        val result = ContextBudgetManager().allocate(
            ContextBudgetInput(
                modelContextLength = 4096,
                outputReserveTokens = 256,
                items = listOf(
                    ContextItem(ContextItemKind.USER_MESSAGE, "question", 110),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "E1", evidenceScopeId = "project-a", evidenceSourceId = "doc-a"),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "E1", evidenceScopeId = "project-a", evidenceSourceId = "doc-b"),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "E1", evidenceScopeId = "project-b", evidenceSourceId = "doc-a"),
                    ContextItem(ContextItemKind.LOCAL_EVIDENCE, passage, 80, evidenceId = "E1", evidenceScopeId = "project-a", evidenceSourceId = "doc-a"),
                ),
            ),
        )

        assertEquals(3, result.included.count { it.kind == ContextItemKind.LOCAL_EVIDENCE })
        assertEquals(1, result.deduplicated.size)
        assertEquals(setOf("project-a:doc-a", "project-a:doc-b", "project-b:doc-a"), result.included
            .filter { it.kind == ContextItemKind.LOCAL_EVIDENCE }
            .map { "${it.evidenceScopeId}:${it.evidenceSourceId}" }.toSet())
    }
}
