package com.localai.workspace.context

import org.json.JSONArray
import org.json.JSONObject

/** A bounded, query-specific passage; canonical indexed text is never modified. */
data class ContextEvidenceExcerpt(
    val text: String,
    val originalCharacters: Int,
    val charStart: Int?,
    val charEnd: Int?,
    val reason: String,
    val cellAddresses: List<String> = emptyList(),
) {
    val shortened: Boolean get() = reason != "FULL_PASSAGE"
}

/** Produces a small evidence view while retaining exact source offsets when a contiguous slice exists. */
object ContextEvidenceExcerptSelector {
    const val DEFAULT_MAX_UTF8_BYTES = 480
    private const val MAX_REFERENCED_CELLS = 64
    private val cellReference = Regex("(?<![\\p{L}\\p{N}])([A-Z]{1,3}[1-9][0-9]{0,6})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val splitBoundary = Regex("(?<=[.!?])\\s+|\\n+")
    private val stopWords = setOf("the", "and", "for", "with", "from", "that", "this", "what", "which", "where", "when", "how", "are", "was", "were", "you", "your", "que", "cual", "cuál", "como", "cómo", "para", "por", "una", "uno", "del", "las", "los", "con", "valor", "casilla", "celda")

    fun select(query: String, source: String, maxUtf8Bytes: Int = DEFAULT_MAX_UTF8_BYTES): ContextEvidenceExcerpt {
        require(maxUtf8Bytes >= 64)
        structuredCellSelection(query, source, maxUtf8Bytes)?.let { return it }
        val sourceBytes = utf8Length(source)
        if (sourceBytes <= maxUtf8Bytes) {
            return ContextEvidenceExcerpt(source, source.length, 0, source.length, "FULL_PASSAGE", cellAddresses(source))
        }
        return textualSelection(query, source, maxUtf8Bytes)
    }

    private data class Line(val text: String, val start: Int, val end: Int)
    private fun lines(text: String): List<Line> = buildList {
        var start = 0
        text.forEachIndexed { index, char ->
            if (char == '\n') {
                add(Line(text.substring(start, index), start, index))
                start = index + 1
            }
        }
        if (start < text.length) add(Line(text.substring(start), start, text.length))
    }

    private data class CellLine(val line: Line, val json: JSONObject, val cells: JSONArray)
    private data class CellCandidate(val start: Int, val text: String, val priority: Int, val address: String?)

    /** XLSX rows are represented as JSON at rest; chat receives compact, address-preserving cell evidence. */
    private fun structuredCellSelection(query: String, source: String, budget: Int): ContextEvidenceExcerpt? {
        val requested = cellReference.findAll(query).map { it.groupValues[1].uppercase() }.toSet()
        if (requested.isEmpty()) return null
        val parsedLines = lines(source).mapNotNull { line ->
            runCatching {
                val json = JSONObject(line.text)
                json to line
            }.getOrNull()
        }
        val rows = parsedLines.mapNotNull { (json, line) -> json.optJSONArray("cells")?.let { CellLine(line, json, it) } }
        val direct = rows.flatMap { row -> row.cells.asCells().map { row to it } }
            .filter { (_, cell) -> cell.optString("address").uppercase() in requested }
        if (direct.isEmpty()) return null

        val references = linkedSetOf<String>().apply { addAll(requested.take(MAX_REFERENCED_CELLS)) }
        direct.forEach { (_, cell) ->
            cellReference.findAll(cell.optString("formula")).take(MAX_REFERENCED_CELLS).forEach { match ->
                if (references.size < MAX_REFERENCED_CELLS) references += match.groupValues[1].uppercase()
            }
        }
        val selectedCells = rows.flatMap { row -> row.cells.asCells().map { row to it } }
            .filter { (_, cell) -> cell.optString("address").uppercase() in references }
        val columns = selectedCells.map { (_, cell) -> cell.optInt("column") }.toSet()
        val candidates = mutableListOf<CellCandidate>()
        parsedLines.forEach { (json, line) ->
            if (json.has("headerCandidate")) {
                val headers = json.optJSONArray("headerCandidate") ?: return@forEach
                for (index in 0 until headers.length()) {
                    val header = headers.optJSONObject(index) ?: continue
                    if (header.optInt("column") !in columns) continue
                    val label = boundedValue(header.optString("label"), 180)
                    candidates += CellCandidate(line.start,
                        "Sheet ${json.optString("sheet")} · header ${columnLabel(header.optInt("column"))}=$label", 1, null)
                }
            }
        }
        selectedCells.forEach { (row, cell) ->
            val address = cell.optString("address").uppercase()
            val value = boundedValue(cell.optString("value"), 180)
            val formula = cell.optString("formula").takeIf { it.isNotBlank() }?.let { " · formula=${boundedValue(it, 120)}" }.orEmpty()
            val directMatch = address in requested
            candidates += CellCandidate(row.line.start,
                "Sheet ${row.json.optString("sheet")} · row ${row.json.optInt("row")} · $address=$value$formula",
                if (directMatch) 3 else 2, address)
        }
        if (candidates.isEmpty()) return null

        val chosen = mutableListOf<CellCandidate>()
        var bytesUsed = 0
        val addresses = linkedSetOf<String>()
        candidates.sortedWith(compareByDescending<CellCandidate> { it.priority }.thenBy { it.start }.thenBy { it.address.orEmpty() })
            .forEach { candidate ->
                val separator = if (chosen.isEmpty()) 0 else 1
                val candidateBytes = utf8Length(candidate.text)
                if (candidateBytes + separator <= budget - bytesUsed) {
                    chosen += candidate
                    bytesUsed += candidateBytes + separator
                    candidate.address?.let(addresses::add)
                }
            }
        if (chosen.isEmpty()) return null
        val ordered = chosen.sortedBy { it.start }
        val text = ordered.joinToString("\n") { it.text }
        check(utf8Length(text) <= budget)
        return ContextEvidenceExcerpt(text, source.length, null, null, "MATCHED_CELL_REFERENCE", addresses.toList())
    }

    private data class UnitRange(val start: Int, val end: Int, val score: Int, val matchedTerm: String?)
    private data class Snippet(val text: String, val start: Int, val end: Int, val exact: Boolean)

    private fun textualSelection(query: String, source: String, budget: Int): ContextEvidenceExcerpt {
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in stopWords }.distinct()
        val units = mutableListOf<UnitRange>()
        var start = 0
        splitBoundary.findAll(source).forEach { match ->
            val end = match.range.first
            if (end > start) units += unit(source, start, end, terms)
            start = match.range.last + 1
        }
        if (start < source.length) units += unit(source, start, source.length, terms)
        if (units.isEmpty()) {
            val snippet = boundedSnippet(source, 0, source.length, null, budget)
            return ContextEvidenceExcerpt(snippet.text, source.length, snippet.start, snippet.end, "TOP_RANKED_FALLBACK")
        }

        val ranked = units.indices.sortedWith(compareByDescending<Int> { units[it].score }.thenBy { units[it].start })
        val hasLexicalMatch = ranked.firstOrNull()?.let { units[it].score > 0 } == true
        val selected = mutableListOf<Snippet>()
        var used = 0
        val candidates = if (hasLexicalMatch) ranked.filter { units[it].score > 0 } else listOf(ranked.first())
        for (index in candidates) {
            val separatorBytes = if (selected.isEmpty()) 0 else utf8Length("\n…\n")
            val available = budget - used - separatorBytes
            if (available < 16) break
            val item = units[index]
            val sourceUnit = source.substring(item.start, item.end)
            val snippet = if (utf8Length(sourceUnit) <= available) {
                Snippet(sourceUnit, item.start, item.end, true)
            } else {
                val termAt = item.matchedTerm?.let { term -> sourceUnit.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }
                boundedSnippet(source, item.start, item.end, termAt?.plus(item.start), available)
            }
            val cost = utf8Length(snippet.text) + separatorBytes
            if (cost <= budget - used) {
                selected += snippet
                used += cost
            }
            if (used >= budget) break
        }
        if (selected.isEmpty()) {
            val first = units[ranked.first()]
            val snippet = boundedSnippet(source, first.start, first.end, null, budget)
            selected += snippet
        }
        val text = selected.joinToString("\n…\n") { it.text }
        check(utf8Length(text) <= budget) { "Evidence excerpt exceeded its UTF-8 budget" }
        val single = selected.singleOrNull()
        return ContextEvidenceExcerpt(text, source.length,
            single?.start, single?.end,
            if (hasLexicalMatch) "QUERY_MATCH" else "TOP_RANKED_FALLBACK")
    }

    private fun unit(source: String, start: Int, end: Int, terms: List<String>): UnitRange {
        val text = source.substring(start, end)
        val lower = text.lowercase()
        val matches = terms.filter { lower.contains(it) }
        return UnitRange(start, end, matches.size, matches.maxByOrNull { it.length })
    }

    /** Returns a Unicode-safe window around a match (or a bounded prefix) with explicit truncation marks. */
    private fun boundedSnippet(source: String, start: Int, end: Int, anchor: Int?, maxBytes: Int): Snippet {
        val left = start.coerceIn(0, source.length)
        val right = end.coerceIn(left, source.length)
        val original = source.substring(left, right)
        if (utf8Length(original) <= maxBytes) return Snippet(original, left, right, true)
        val anchorLocal = (anchor ?: left).minus(left).coerceIn(0, original.length)
        val desiredOffset = (anchorLocal - maxBytes / 2).coerceAtLeast(0)
        var from = left + desiredOffset
        if (from > left && from < right && Character.isLowSurrogate(source[from])) from--
        val prefix = from > left
        val prefixMark = if (prefix) "…" else ""
        val prefixBytes = utf8Length(prefixMark)
        var available = maxBytes - prefixBytes
        var to = advanceUtf8(source, from, right, available)
        val suffix = to < right
        if (suffix) {
            available -= utf8Length("…")
            to = advanceUtf8(source, from, right, available)
        }
        if (to <= from && from < right) to = advanceUtf8(source, from, right, maxBytes - prefixBytes - if (right > from) utf8Length("…") else 0)
        val suffixMark = if (to < right) "…" else ""
        val value = prefixMark + source.substring(from, to) + suffixMark
        check(utf8Length(value) <= maxBytes)
        return Snippet(value, from, to, false)
    }

    private fun advanceUtf8(source: String, from: Int, end: Int, budget: Int): Int {
        var index = from
        var bytes = 0
        while (index < end) {
            val codePoint = source.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val size = when {
                codePoint <= 0x7f -> 1
                codePoint <= 0x7ff -> 2
                codePoint <= 0xffff -> 3
                else -> 4
            }
            if (bytes + size > budget) break
            bytes += size
            index += chars
        }
        return index
    }

    private fun boundedValue(value: String, maxBytes: Int): String {
        val safe = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        if (utf8Length(safe) <= maxBytes) return safe
        val budget = maxBytes - utf8Length("…")
        return sourcePrefix(safe, budget) + "…"
    }

    private fun sourcePrefix(value: String, budget: Int): String = value.substring(0, advanceUtf8(value, 0, value.length, budget))

    private fun JSONArray.asCells(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun columnLabel(column: Int): String {
        var value = column
        var result = ""
        while (value > 0) { value--; result = ('A' + value % 26) + result; value /= 26 }
        return result
    }

    private fun cellAddresses(text: String): List<String> = runCatching {
        lines(text).flatMap { line ->
            val row = JSONObject(line.text).optJSONArray("cells") ?: return@flatMap emptyList()
            row.asCells().mapNotNull { it.optString("address").takeIf(String::isNotBlank) }
        }.distinct()
    }.getOrDefault(emptyList())

    private fun utf8Length(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
