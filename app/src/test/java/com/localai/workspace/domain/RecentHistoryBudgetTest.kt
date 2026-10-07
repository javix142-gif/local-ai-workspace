package com.localai.workspace.domain

import com.localai.workspace.domain.model.*
import com.localai.workspace.domain.rag.*
import org.junit.Assert.*
import org.junit.Test

class RecentHistoryBudgetTest {
    private fun pair(order: Int, chars: Int = 200) = ContextItem(ContextItemKind.HISTORY, "x".repeat(chars), 40,
        chatHistory = listOf(ChatMessage(MessageRole.USER, "u$order"), ChatMessage(MessageRole.ASSISTANT, "a$order")), historyOrder = order)
    private fun allocate(pairs: List<ContextItem>, reserve: Int = 100, extras: List<ContextItem> = emptyList()) =
        ContextBudgetManager().allocate(ContextBudgetInput(256, outputReserveTokens = reserve,
            items = listOf(ContextItem(ContextItemKind.SYSTEM_POLICY, "policy", 100),
                ContextItem(ContextItemKind.USER_MESSAGE, "current", 110)) + pairs + extras))
    @Test fun allPairsFitAndAreReturnedChronologicallyToModel() {
        val result = allocate((0..2).map { pair(it, 100) })
        assertEquals(6, ConversationPromptBuilder.build("current", result.included).history.size)
        assertEquals("u0", ConversationPromptBuilder.build("current", result.included).history.first().content)
        assertTrue(result.excluded.isEmpty())
    }
    @Test fun onlyNewestCompletePairsFit() {
        val result = allocate((0..4).map { pair(it) })
        val orders = result.included.filter { it.kind == ContextItemKind.HISTORY }.map { it.historyOrder }.toSet()
        assertEquals(setOf(2, 3, 4), orders)
        assertEquals(listOf("u2", "a2", "u3", "a3", "u4", "a4"), ConversationPromptBuilder.build("current", result.included).history.map { it.content })
    }
    @Test fun noHistoryFitsButCurrentAndPolicySurvive() {
        val result = allocate(listOf(pair(0)), 250)
        assertTrue(result.included.none { it.kind == ContextItemKind.HISTORY })
        assertTrue(result.included.any { it.kind == ContextItemKind.USER_MESSAGE })
    }
    @Test fun oneOversizedRecentTurnNeverResurrectsOlderHistory() {
        val result = allocate(listOf(pair(0, 4), pair(1, 4000)))
        assertTrue(result.included.none { it.kind == ContextItemKind.HISTORY })
        assertEquals(2, result.excluded.size)
    }
    @Test fun explicitDocumentHasPriorityAndIsNeverCut() {
        val document = ContextItem(ContextItemKind.LOCAL_EVIDENCE, "d".repeat(300), 70, "evidence", true)
        val result = allocate((0..3).map { pair(it) }, extras = listOf(document))
        assertTrue(document in result.included)
        assertEquals(listOf(3), result.included.filter { it.kind == ContextItemKind.HISTORY }.map { it.historyOrder })
    }
    @Test fun memoryKeepsItsExistingPriorityAboveHistory() {
        val memory = ContextItem(ContextItemKind.MEMORY, "m".repeat(300), 50)
        val result = allocate((0..3).map { pair(it) }, extras = listOf(memory))
        assertTrue(memory in result.included)
        assertEquals(listOf(3), result.included.filter { it.kind == ContextItemKind.HISTORY }.map { it.historyOrder })
    }
}
