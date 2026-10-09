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
    val evidenceScopeId: String? = null,
    val evidenceSourceId: String? = null,
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
    /** Repeated copies of the same scoped source passage, omitted as routine deduplication. */
    val deduplicated: List<ContextItem> = emptyList(),
    /** Unique items that did not fit the computed input allowance. */
    val budgetExcluded: List<ContextItem> = excluded,
)

private data class EvidenceDeduplicationKey(
    val scopeId: String,
    val sourceId: String,
    val evidenceId: String,
    val text: String,
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
        val deduplicated = mutableListOf<ContextItem>()
        val seenEvidence = mutableSetOf<EvidenceDeduplicationKey>()
        val optional = input.items.filterNot { it in mandatory }
            .sortedWith(compareByDescending<ContextItem> { it.priority }
                .thenBy { it.kind.ordinal }
                .thenBy { it.evidenceId.orEmpty() }
                .thenBy { it.evidenceScopeId.orEmpty() }
                .thenBy { it.evidenceSourceId.orEmpty() }
                .thenBy { it.text })
            .filter { item ->
                val scopeId = item.evidenceScopeId
                val sourceId = item.evidenceSourceId
                val evidenceId = item.evidenceId
                if (item.kind != ContextItemKind.LOCAL_EVIDENCE || scopeId == null || sourceId == null || evidenceId == null) {
                    true
                } else if (seenEvidence.add(EvidenceDeduplicationKey(scopeId, sourceId, evidenceId, item.text))) {
                    true
                } else {
                    deduplicated += item
                    false
                }
            }

        val included = mutableListOf<ContextItem>()
        val budgetExcluded = mutableListOf<ContextItem>()
        var used = 0
        fun tryAdd(item: ContextItem): Boolean {
            val cost = estimateTokens(item.text)
            if (used + cost > available) return false
            included += item
            used += cost
            return true
        }

        mandatory.forEach { item ->
            if (!tryAdd(item)) budgetExcluded += item
        }
        optional.filter { it.kind != ContextItemKind.HISTORY }.forEach { item ->
            if (!tryAdd(item)) budgetExcluded += item
        }
        // One item per completed pair. Keep a contiguous suffix, never skip a large
        // recent pair to resurrect an older turn. Memory/evidence retain their priorities.
        val history = optional.filter { it.kind == ContextItemKind.HISTORY }.sortedByDescending { it.historyOrder }
        for ((index, item) in history.withIndex()) {
            if (!tryAdd(item)) {
                budgetExcluded += history.drop(index)
                break
            }
        }

        val orderedBudgetExcluded = budgetExcluded.toList()
        val orderedDeduplicated = deduplicated.toList()
        return ContextBudgetResult(
            included = included.filter { it.kind != ContextItemKind.HISTORY } +
                included.filter { it.kind == ContextItemKind.HISTORY }.sortedBy { it.historyOrder },
            excluded = orderedBudgetExcluded + orderedDeduplicated,
            estimatedInputTokens = used,
            availableInputTokens = available,
            deduplicated = orderedDeduplicated,
            budgetExcluded = orderedBudgetExcluded,
        )
    }

    fun estimateTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)
}
