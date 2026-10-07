package com.localai.workspace.domain

import com.localai.workspace.domain.rag.ContextBudgetInput
import com.localai.workspace.domain.rag.ContextBudgetManager
import com.localai.workspace.domain.rag.ContextItem
import com.localai.workspace.domain.rag.ContextItemKind
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
}
