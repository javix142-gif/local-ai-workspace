package com.localai.workspace.domain.rag

import kotlin.math.max

enum class ContextItemKind {
    SYSTEM_POLICY,
    PROJECT_INSTRUCTIONS,
    HISTORY,
    MEMORY,
    LOCAL_EVIDENCE,
    WEB_EVIDENCE,
    TOOL_CONTEXT,
    USER_MESSAGE,
}

data class ContextItem(
    val kind: ContextItemKind,
    val text: String,
    val priority: Int,
    val evidenceId: String? = null,
    val mustPreserveWhole: Boolean = false,
    val chatHistory: List<com.localai.workspace.domain.model.ChatMessage> = emptyList(),
    val historyOrder: Int = 0,
)

data class ContextBudgetInput(
    val modelContextLength: Int,
    val selectedContextLength: Int? = null,
    val outputReserveTokens: Int = 512,
    val items: List<ContextItem>,
)

data class ContextBudgetResult(
    val included: List<ContextItem>,
    val excluded: List<ContextItem>,
    val estimatedInputTokens: Int,
    val availableInputTokens: Int,
)

/**
 * Allocates prompt space without silently cutting a citation-bearing evidence segment.
 * Token estimation is intentionally conservative and can later be replaced with a
 * tokenizer supplied by the active runtime.
 */
class ContextBudgetManager {
    fun allocate(input: ContextBudgetInput): ContextBudgetResult {
        val contextLimit = minOf(
            input.modelContextLength.coerceAtLeast(256),
            (input.selectedContextLength ?: input.modelContextLength).coerceAtLeast(256),
        )
        val available = max(0, contextLimit - input.outputReserveTokens.coerceAtLeast(0))
        val mandatory = input.items.filter {
            it.kind == ContextItemKind.SYSTEM_POLICY || it.kind == ContextItemKind.USER_MESSAGE
        }
        val optional = input.items.filterNot { it in mandatory }
            .sortedWith(compareByDescending<ContextItem> { it.priority }.thenBy { it.kind.ordinal })

        val included = mutableListOf<ContextItem>()
        var used = 0
        fun tryAdd(item: ContextItem): Boolean {
            val cost = estimateTokens(item.text)
            if (used + cost > available) return false
            included += item
            used += cost
            return true
        }

        mandatory.forEach { item ->
            if (!tryAdd(item)) {
                // The caller receives an explicit exclusion instead of an invalid citation.
                // The current user message is still represented in the result when it fits.
            }
        }
        optional.filter { it.kind != ContextItemKind.HISTORY }.forEach(::tryAdd)
        // One item per completed pair. Keep a contiguous suffix, never skip a large
        // recent pair to resurrect an older turn. Memory/evidence retain their priorities.
        for (item in optional.filter { it.kind == ContextItemKind.HISTORY }.sortedByDescending { it.historyOrder }) {
            if (!tryAdd(item)) break
        }

        return ContextBudgetResult(
            included = included.filter { it.kind != ContextItemKind.HISTORY } +
                included.filter { it.kind == ContextItemKind.HISTORY }.sortedBy { it.historyOrder },
            excluded = input.items - included.toSet(),
            estimatedInputTokens = used,
            availableInputTokens = available,
        )
    }

    fun estimateTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)
}
