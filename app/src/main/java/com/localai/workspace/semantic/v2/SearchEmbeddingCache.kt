package com.localai.workspace.semantic.v2

/** Query vectors only, not search results: every hit still checks the live READY generation.
 * No query text is retained or persisted. Eight 768-float vectors use at most 24 KiB.
 * Callers include model/version/task/backend, scope and index generation in the key.
 */
internal class SearchEmbeddingCache(private val capacity: Int = 8) {
    private val entries = LinkedHashMap<String, EmbeddingResult>(16, 0.75f, true)
    init { require(capacity in 1..32) }
    @Synchronized fun get(key: String): EmbeddingResult? = entries[key]?.let { it.copy(vector = it.vector.copyOf(), durationMs = 0) }
    @Synchronized fun put(key: String, result: EmbeddingResult) {
        entries[key] = result.copy(vector = result.vector.copyOf())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() = entries.clear()
}
