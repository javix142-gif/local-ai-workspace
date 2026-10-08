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
    val incompleteReasons: List<String> = emptyList(),
    val missingCellAddresses: List<String> = emptyList(),
) {
    val shortened: Boolean get() = reason != "FULL_PASSAGE"
}

/** Produces a small evidence view while retaining exact source offsets when a contiguous slice exists. */
object ContextEvidenceExcerptSelector {
    const val DEFAULT_MAX_UTF8_BYTES = 480
    private const val MAX_REFERENCED_CELLS = 64
    private val cellReference = Regex(
        """(?<![\p{L}\p{N}_])(\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6})(?::(\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6}))?(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE,
    )
    private val cellAddress = Regex("([A-Z]{1,3})([1-9][0-9]{0,6})", RegexOption.IGNORE_CASE)
    private val splitBoundary = Regex("(?<=[.!?])\\s+|\\n+")
    private val stopWords = setOf("the", "and", "for", "with", "from", "that", "this", "what", "which", "where", "when", "how", "are", "was", "were", "you", "your", "que", "cual", "cuál", "como", "cómo", "para", "por", "una", "uno", "del", "las", "los", "con", "valor", "casilla", "celda")

    fun select(query: String, source: String, maxUtf8Bytes: Int = DEFAULT_MAX_UTF8_BYTES): ContextEvidenceExcerpt {
        require(maxUtf8Bytes >= 64)
        structuredCellSelection(query, source, maxUtf8Bytes)?.let { return it }
        if (utf8Length(source) <= maxUtf8Bytes) {
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

    private data class CellCoordinate(val column: Int, val row: Int) {
        fun address(): String {
            var value = column
            var label = ""
            while (value > 0) {
                value--
                label = ('A' + value % 26) + label
                value /= 26
            }
            return "$label$row"
        }
    }

    private data class CellRange(val first: CellCoordinate, val last: CellCoordinate) {
        val area: Long get() = (last.column - first.column + 1L) * (last.row - first.row + 1L)
        fun contains(cell: CellCoordinate) = cell.column in first.column..last.column && cell.row in first.row..last.row
    }

    private data class CellReference(val range: CellRange)
    private data class CellLine(val line: Line, val json: JSONObject, val cells: JSONArray)
    private data class SourceCell(val line: Line, val row: JSONObject, val cell: JSONObject, val coordinate: CellCoordinate)
    private data class CellCandidate(val start: Int, val text: String, val priority: Int, val address: String?, val truncated: Boolean = false)
    private class ReferencePlan(val maxAddresses: Int) {
        val addresses = linkedSetOf<String>()
        val ranges = mutableListOf<CellRange>()
        val incompleteReasons = linkedSetOf<String>()
    }

    /** XLSX rows are represented as JSON at rest; chat receives compact, address-preserving cell evidence. */
    private fun structuredCellSelection(query: String, source: String, budget: Int): ContextEvidenceExcerpt? {
        val queryReferences = references(query)
        if (queryReferences.isEmpty()) return null

        val parsedLines = lines(source).mapNotNull { line ->
            runCatching { JSONObject(line.text).let { it to line } }.getOrNull()
        }
        val rows = parsedLines.mapNotNull { (json, line) -> json.optJSONArray("cells")?.let { CellLine(line, json, it) } }
        if (rows.isEmpty()) return null
        val cells = rows.flatMap { row -> row.cells.asCells().mapNotNull { cell ->
            val coordinate = parseAddress(cell.optString("address")) ?: return@mapNotNull null
            SourceCell(row.line, row.json, cell, coordinate)
        } }
        val cellsByAddress = cells.groupBy { it.coordinate.address() }

        val directPlan = ReferencePlan(MAX_REFERENCED_CELLS)
        addReferences(queryReferences, directPlan)
        val directCells = selectCells(cells, cellsByAddress, directPlan)
        if (directCells.size >= MAX_REFERENCED_CELLS && directPlan.ranges.isNotEmpty()) {
            directPlan.incompleteReasons += "CELL_REFERENCE_LIMIT"
        }

        // Only formulas in cells selected by the user's query are inspected; dependencies are not recursively evaluated.
        val dependencyPlan = ReferencePlan((MAX_REFERENCED_CELLS - directCells.size).coerceAtLeast(0))
        directCells.forEach { sourceCell ->
            addReferences(references(sourceCell.cell.optString("formula")), dependencyPlan)
        }
        val dependencyCells = selectCells(cells, cellsByAddress, dependencyPlan)
        val missing = linkedSetOf<String>().apply {
            addAll(directPlan.addresses.filter { it !in cellsByAddress })
            addAll(dependencyPlan.addresses.filter { it !in cellsByAddress })
        }
        val incomplete = linkedSetOf<String>().apply {
            addAll(directPlan.incompleteReasons)
            addAll(dependencyPlan.incompleteReasons)
            if (missing.isNotEmpty()) add("MISSING_REFERENCED_CELLS")
        }

        val selectedByAddress = linkedMapOf<String, SourceCell>()
        directCells.forEach { selectedByAddress.putIfAbsent(it.coordinate.address(), it) }
        dependencyCells.forEach { selectedByAddress.putIfAbsent(it.coordinate.address(), it) }
        if (selectedByAddress.size > MAX_REFERENCED_CELLS) {
            incomplete += "CELL_REFERENCE_LIMIT"
        }
        val boundedCells = selectedByAddress.values.take(MAX_REFERENCED_CELLS)
        val selectedDirectAddresses = directCells.mapTo(hashSetOf()) { it.coordinate.address() }
        val columns = boundedCells.map { it.coordinate.column }.toSet()
        val candidates = mutableListOf<CellCandidate>()

        parsedLines.forEach { (json, line) ->
            if (!json.has("headerCandidate")) return@forEach
            val headers = json.optJSONArray("headerCandidate") ?: return@forEach
            for (index in 0 until headers.length()) {
                val header = headers.optJSONObject(index) ?: continue
                if (header.optInt("column") !in columns) continue
                val label = boundedValue(header.optString("label"), 180)
                candidates += CellCandidate(line.start,
                    "Sheet ${json.optString("sheet")} · header ${columnLabel(header.optInt("column"))}=$label", 1, null)
            }
        }
        boundedCells.forEach { sourceCell ->
            val address = sourceCell.coordinate.address()
            val value = boundedValueResult(sourceCell.cell.optString("value"), 180)
            val formulaValue = sourceCell.cell.optString("formula")
            val formulaResult = formulaValue.takeIf { it.isNotBlank() }?.let { boundedValueResult(it, 120) }
            val formula = formulaResult?.let { " · formula=${it.value}" }.orEmpty()
            val direct = address in selectedDirectAddresses
            candidates += CellCandidate(
                sourceCell.line.start,
                "Sheet ${sourceCell.row.optString("sheet")} · row ${sourceCell.row.optInt("row")} · $address=${value.value}$formula",
                if (direct) 3 else 2,
                address,
                value.truncated || formulaResult?.truncated == true,
            )
        }

        val chosen = mutableListOf<CellCandidate>()
        var bytesUsed = 0
        val selectedAddresses = linkedSetOf<String>()
        candidates.sortedWith(compareByDescending<CellCandidate> { it.priority }.thenBy { it.start }.thenBy { it.address.orEmpty() })
            .forEach { candidate ->
                val separator = if (chosen.isEmpty()) 0 else 1
                val candidateBytes = utf8Length(candidate.text)
                if (candidateBytes + separator <= budget - bytesUsed) {
                    chosen += candidate
                    bytesUsed += candidateBytes + separator
                    candidate.address?.let(selectedAddresses::add)
                    if (candidate.truncated) incomplete += "CELL_EVIDENCE_BYTE_BUDGET"
                } else if (candidate.address != null) {
                    incomplete += "CELL_EVIDENCE_BYTE_BUDGET"
                }
            }

        if (chosen.isEmpty()) {
            if (directCells.isNotEmpty() || dependencyCells.isNotEmpty()) incomplete += "CELL_EVIDENCE_BYTE_BUDGET"
            return ContextEvidenceExcerpt(
                text = "",
                originalCharacters = source.length,
                charStart = null,
                charEnd = null,
                reason = "NO_REFERENCED_CELL_EVIDENCE_INCLUDED",
                incompleteReasons = incomplete.toList(),
                missingCellAddresses = missing.toList(),
            )
        }
        val ordered = chosen.sortedBy { it.start }
        val text = ordered.joinToString("\n") { it.text }
        check(utf8Length(text) <= budget)
        return ContextEvidenceExcerpt(
            text = text,
            originalCharacters = source.length,
            charStart = null,
            charEnd = null,
            reason = "MATCHED_CELL_REFERENCE",
            cellAddresses = selectedAddresses.toList(),
            incompleteReasons = incomplete.toList(),
            missingCellAddresses = missing.toList(),
        )
    }

    private fun references(text: String): List<CellReference> = cellReference.findAll(text).mapNotNull { match ->
        val first = parseAddress(match.groupValues[1]) ?: return@mapNotNull null
        val last = match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.let(::parseAddress) ?: first
        CellReference(CellRange(
            CellCoordinate(minOf(first.column, last.column), minOf(first.row, last.row)),
            CellCoordinate(maxOf(first.column, last.column), maxOf(first.row, last.row)),
        ))
    }.toList()

    private fun addReferences(references: List<CellReference>, plan: ReferencePlan) {
        for (reference in references) {
            val range = reference.range
            if (range.area == 1L) {
                val address = range.first.address()
                if (address !in plan.addresses) {
                    if (plan.addresses.size < plan.maxAddresses) plan.addresses += address
                    else plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                }
            } else if (range.area <= MAX_REFERENCED_CELLS) {
                for (row in range.first.row..range.last.row) {
                    for (column in range.first.column..range.last.column) {
                        val address = CellCoordinate(column, row).address()
                        if (address !in plan.addresses) {
                            if (plan.addresses.size < plan.maxAddresses) plan.addresses += address
                            else plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                        }
                    }
                }
            } else {
                if (plan.ranges.size < MAX_REFERENCED_CELLS) plan.ranges += range
                plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
            }
        }
    }

    private fun selectCells(
        cells: List<SourceCell>,
        cellsByAddress: Map<String, List<SourceCell>>,
        plan: ReferencePlan,
    ): List<SourceCell> {
        val selected = linkedMapOf<String, SourceCell>()
        for (address in plan.addresses) {
            cellsByAddress[address]?.firstOrNull()?.let { selected.putIfAbsent(address, it) }
        }
        for (range in plan.ranges) {
            val remaining = MAX_REFERENCED_CELLS - selected.size
            if (remaining <= 0) {
                plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                break
            }
            val matches = cells.asSequence().filter { range.contains(it.coordinate) }.distinctBy { it.coordinate.address() }.take(remaining + 1).toList()
            if (matches.size > remaining) plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
            matches.take(remaining).forEach { selected.putIfAbsent(it.coordinate.address(), it) }
        }
        return selected.values.take(MAX_REFERENCED_CELLS)
    }

    private fun parseAddress(raw: String): CellCoordinate? {
        val normalized = raw.replace("$", "").uppercase()
        val match = cellAddress.matchEntire(normalized) ?: return null
        val column = match.groupValues[1].fold(0) { value, char -> value * 26 + (char - 'A' + 1) }
        val row = match.groupValues[2].toIntOrNull() ?: return null
        if (column !in 1..16_384 || row !in 1..9_999_999) return null
        return CellCoordinate(column, row)
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
            val snippet = boundedSnippet(source, 0, source.length, null, null, budget)
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
            val termAt = item.matchedTerm?.let { term -> sourceUnit.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }
            val snippet = if (utf8Length(sourceUnit) <= available) {
                Snippet(sourceUnit, item.start, item.end, true)
            } else {
                boundedSnippet(
                    source,
                    item.start,
                    item.end,
                    termAt?.plus(item.start),
                    item.matchedTerm?.length,
                    available,
                )
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
            val termAt = first.matchedTerm?.let { term -> source.substring(first.start, first.end).indexOf(term, ignoreCase = true).takeIf { it >= 0 } }
            selected += boundedSnippet(source, first.start, first.end, termAt?.plus(first.start), first.matchedTerm?.length, budget)
        }
        val text = selected.joinToString("\n…\n") { it.text }
        check(utf8Length(text) <= budget) { "Evidence excerpt exceeded its UTF-8 budget" }
        val single = selected.singleOrNull()
        return ContextEvidenceExcerpt(text, source.length, single?.start, single?.end,
            if (hasLexicalMatch) "QUERY_MATCH" else "TOP_RANKED_FALLBACK")
    }

    private fun unit(source: String, start: Int, end: Int, terms: List<String>): UnitRange {
        val text = source.substring(start, end)
        val lower = text.lowercase()
        val matches = terms.filter { lower.contains(it) }
        return UnitRange(start, end, matches.size, matches.maxByOrNull { it.length })
    }

    /** Keeps the selected lexical anchor intact, budgets context in UTF-8 bytes and returns source offsets. */
    private fun boundedSnippet(source: String, start: Int, end: Int, anchor: Int?, anchorLength: Int?, maxBytes: Int): Snippet {
        val left = start.coerceIn(0, source.length)
        val right = end.coerceIn(left, source.length)
        if (utf8Length(source.substring(left, right)) <= maxBytes) return Snippet(source.substring(left, right), left, right, true)

        val anchorStart = anchor?.coerceIn(left, right)
        val anchorEnd = if (anchorStart != null && anchorLength != null) (anchorStart + anchorLength).coerceAtMost(right) else null
        if (anchorStart == null || anchorEnd == null || anchorEnd <= anchorStart || utf8Length(source.substring(anchorStart, anchorEnd)) > maxBytes) {
            val from = left
            val reserveSuffix = if (right > from) utf8Length("…") else 0
            val to = advanceUtf8(source, from, right, maxBytes - reserveSuffix)
            val suffix = if (to < right) "…" else ""
            return Snippet(source.substring(from, to) + suffix, from, to, false)
        }

        val anchorBytes = utf8Length(source.substring(anchorStart, anchorEnd))
        if (anchorBytes > maxBytes - utf8Length("…") * 2) {
            return Snippet(source.substring(anchorStart, anchorEnd), anchorStart, anchorEnd, false)
        }
        val contextBudget = maxBytes - anchorBytes - utf8Length("…") * 2
        val leftBudget = contextBudget / 2
        var from = retreatUtf8(source, anchorStart, left, leftBudget)
        val usedLeft = utf8Length(source.substring(from, anchorStart))
        var to = advanceUtf8(source, anchorEnd, right, contextBudget - usedLeft)
        val prefixMark = if (from > left) "…" else ""
        val suffixMark = if (to < right) "…" else ""

        // If the selected match sits near an edge, spend unused window budget on the other side.
        if (from == left && to < right) {
            val usedLeft = utf8Length(source.substring(from, anchorStart))
            to = advanceUtf8(source, anchorEnd, right, maxBytes - anchorBytes - usedLeft - utf8Length(suffixMark))
        } else if (to == right && from > left) {
            val usedRight = utf8Length(source.substring(anchorEnd, to))
            from = retreatUtf8(source, anchorStart, left, maxBytes - anchorBytes - usedRight - utf8Length(prefixMark))
        }
        val actualPrefix = if (from > left) "…" else ""
        val actualSuffix = if (to < right) "…" else ""
        val value = actualPrefix + source.substring(from, to) + actualSuffix
        check(utf8Length(value) <= maxBytes)
        check(value.contains(source.substring(anchorStart, anchorEnd)))
        return Snippet(value, from, to, false)
    }

    private fun retreatUtf8(source: String, from: Int, lowerBound: Int, budget: Int): Int {
        var index = from
        var bytes = 0
        while (index > lowerBound) {
            var previous = index - 1
            if (previous > lowerBound && Character.isLowSurrogate(source[previous]) && Character.isHighSurrogate(source[previous - 1])) previous--
            val codePoint = source.codePointAt(previous)
            val size = utf8CodePointLength(codePoint)
            if (bytes + size > budget) break
            bytes += size
            index = previous
        }
        return index
    }

    private fun advanceUtf8(source: String, from: Int, end: Int, budget: Int): Int {
        var index = from
        var bytes = 0
        while (index < end) {
            val codePoint = source.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val size = utf8CodePointLength(codePoint)
            if (bytes + size > budget) break
            bytes += size
            index += chars
        }
        return index
    }

    private fun utf8CodePointLength(codePoint: Int): Int = when {
        codePoint <= 0x7f -> 1
        codePoint <= 0x7ff -> 2
        codePoint <= 0xffff -> 3
        else -> 4
    }

    private data class BoundedValue(val value: String, val truncated: Boolean)
    private fun boundedValue(value: String, maxBytes: Int): String = boundedValueResult(value, maxBytes).value

    private fun boundedValueResult(value: String, maxBytes: Int): BoundedValue {
        val safe = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        if (utf8Length(safe) <= maxBytes) return BoundedValue(safe, false)
        val budget = maxBytes - utf8Length("…")
        return BoundedValue(sourcePrefix(safe, budget) + "…", true)
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
