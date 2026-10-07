package com.localai.workspace.context

import java.text.Normalizer
import java.util.Locale

/** V1 policy: eligibility is handled by lookup; bonuses never determine relevance. */
object MemoryRelevance {
    const val VERSION = "MEMORY_RELEVANCE_V1"
    // Conservative provisional cosine floor, independent of the additive ranking scale.
    // Requires Moto calibration for each embedding space; not a measured universal cutoff.
    const val SEMANTIC_FLOOR = 0.80
    const val LEXICAL_FLOOR = 0.50
    private fun normalized(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    fun smallTalk(query: String): Boolean {
        val text = normalized(query).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return false
        // Anchored grammar of complete utterances; a substantive suffix cannot match.
        return Regex("(?:(?:hola|buenas|buenos dias|buenas tardes|buenas noches|gracias|muchas gracias|ok|okay|perfecto|como estas|que tal|chao|adios|hello|hi|thanks|thank you)(?:\\s+|$))+").matches(text)
    }
    private val stop = setOf("mi","mis","su","sus","me","es","el","la","lo","un","una","de","del","en","por","con","y","a","al","cual","que","como","este","esta","estos","estas","what","which","is","my","the","of","this","does","use","uses","usa","utiliza","utilizar","hola","buenos","dias","buenas","tardes","prueba")
    private fun terms(text: String) = Regex("[\\p{L}\\p{N}_]{2,}").findAll(normalized(text)).map { it.value }
        .filter { it !in stop }.map { when(it) { "prefiero","preferida","preferido","favorito","favorita","preferred","favorite" -> "preferencia"; "editor" -> "editor"; else -> it } }.toSet()
    fun assess(query: String, memory: MemoryRecord, semantic: Double?): MemoryRelevanceScore {
        val q = terms(query)
        val contentQuery = q - "preferencia"
        fun coverage(text:String, queryTerms:Set<String>):Double = if(queryTerms.isEmpty()) 0.0 else queryTerms.intersect(terms(text)).size.toDouble()/queryTerms.size
        fun overlap(text: String): Double = coverage(text,contentQuery)
        val lexical = overlap(memory.text+" "+memory.title.orEmpty())
        val subjectTerms = terms(memory.subject.orEmpty())
        val structured = subjectTerms.isNotEmpty() && q.containsAll(subjectTerms) &&
            (memory.predicate.isNullOrBlank() || coverage(memory.predicate,q) > 0)
        val accepted = structured || lexical >= LEXICAL_FLOOR || (semantic?.isFinite()==true && semantic >= SEMANTIC_FLOOR)
        val raw = lexical + (semantic?.takeIf { it.isFinite() } ?: 0.0) + if(structured) 1.0 else 0.0
        val importance = if(memory.pinned) 0.0 else .1*memory.importance
        val pin = if(memory.pinned) 2.0 else 0.0
        val confidence = .05*memory.confidence
        return MemoryRelevanceScore(raw,raw+importance+pin+confidence,semantic,lexical,structured,importance,pin,confidence,accepted)
    }
}
data class MemoryRelevanceScore(val rawRelevance:Double,val rankingScore:Double,val semanticSimilarity:Double?,val lexicalContribution:Double,val structuredMatch:Boolean,val importanceBonus:Double,val pinBonus:Double,val confidenceBonus:Double,val accepted:Boolean,val policy:String=MemoryRelevance.VERSION)
data class MemorySelection(val included:List<MemoryHit>,val dropped:List<MemoryHit>,val lookupSkipped:Boolean=false)
