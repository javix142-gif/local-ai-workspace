package com.localai.workspace.context

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

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
    /** Composite, sheet-aware references actually represented by this excerpt. */
    val cellReferences: List<String> = emptyList(),
    val missingCellReferences: List<String> = emptyList(),
    val ambiguousCellReferences: List<String> = emptyList(),
    val unresolvedCellReferences: List<String> = emptyList(),
) {
    val shortened: Boolean get() = reason != "FULL_PASSAGE"
}

/** Produces a small evidence view while retaining exact source offsets when a contiguous slice exists. */
object ContextEvidenceExcerptSelector {
    const val DEFAULT_MAX_UTF8_BYTES = 480
    private const val MAX_REFERENCED_CELLS = 64
    private val cellReference = Regex(
        """(?<![\p{L}\p{N}_])(?:(?<sheet>'(?:[^']|'')+'|[\p{L}_][\p{L}\p{N}_.]*(?::[\p{L}_][\p{L}\p{N}_.]*)?)!)?(?<first>\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6})(?:\s*:\s*(?<last>\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6}))?(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE,
    )
    private val cellAddress = Regex("([A-Z]{1,3})([1-9][0-9]{0,6})", RegexOption.IGNORE_CASE)
    private val externalWorkbookReference = Regex(
        """\[[^\]\r\n]{1,256}\][^!\r\n]{0,256}!\s*\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6}(?:\s*:\s*\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6})?""",
        RegexOption.IGNORE_CASE,
    )
    private val formulaIdentifier = Regex("(?<![\\p{L}\\p{N}_])([\\p{L}_][\\p{L}\\p{N}_.]*)(?![\\p{L}\\p{N}_])")
    private val structuredReference = Regex("(?i)[\\p{L}\\p{N}_]+\\s*\\[[^]]+]")
    private val dynamicReference = Regex("(?i)\\b(?:INDIRECT|OFFSET)\\s*\\(")
    private val commonConstants = setOf("TRUE", "FALSE", "NA")
    private val functionLikeCellTokens = setOf("LOG10")
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

    /** Combines only already-retrieved passages from one authorized source/document. */
    fun selectRetrievedPassages(query: String, passages: List<String>, maxUtf8Bytes: Int = DEFAULT_MAX_UTF8_BYTES): ContextEvidenceExcerpt? {
        require(maxUtf8Bytes >= 64)
        if (passages.isEmpty()) return null
        val joined = passages.joinToString("\n")
        return structuredCellSelection(query, joined, maxUtf8Bytes)
    }

    fun isStructuredCellPassage(source: String): Boolean = lines(source).any { line ->
        runCatching { JSONObject(line.text).optJSONArray("cells") != null }.getOrDefault(false)
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
    }

    private data class CellReference(val sheetStart: String?, val sheetEnd: String?, val range: CellRange)
    private data class ReferenceScan(val references: List<CellReference>, val incompleteReasons: Set<String>)
    private data class CellLine(val line: Line, val json: JSONObject, val cells: JSONArray)
    private data class SheetInfo(val key: String, val displayName: String, val order: Int?)
    private data class CellIdentity(val sheetKey: String, val address: String)
    private data class SourceCell(val line: Line, val row: JSONObject, val cell: JSONObject, val sheet: SheetInfo, val coordinate: CellCoordinate) {
        val identity get() = CellIdentity(sheet.key, coordinate.address())
    }
    private data class CellCandidate(val start: Int, val text: String, val priority: Int, val cell: SourceCell?, val truncated: Boolean = false)
    private class ReferencePlan {
        val requested = linkedMapOf<CellIdentity, SheetInfo>()
        val direct = linkedSetOf<CellIdentity>()
        val missing = linkedMapOf<CellIdentity, SheetInfo>()
        val ambiguous = linkedSetOf<String>()
        val unresolved = linkedSetOf<String>()
        val incompleteReasons = linkedSetOf<String>()
        val conflictedSheetKeys = linkedSetOf<String>()
        val conflictedOrderIndices = linkedSetOf<Int>()
        private val tracked = linkedSetOf<CellIdentity>()

        fun track(identity: CellIdentity, max: Int = MAX_REFERENCED_CELLS): Boolean {
            if (identity in tracked) return true
            if (tracked.size >= max) {
                incompleteReasons += "CELL_REFERENCE_LIMIT"
                return false
            }
            tracked += identity
            return true
        }
    }

    /** XLSX rows are represented as JSON at rest; chat receives compact, address-preserving cell evidence. */
    private fun structuredCellSelection(query: String, source: String, budget: Int): ContextEvidenceExcerpt? {
        val queryScan = scanReferences(query, formula = false)
        if (queryScan.references.isEmpty()) return null

        val parsedLines = lines(source).mapNotNull { line ->
            runCatching { JSONObject(line.text).let { it to line } }.getOrNull()
        }
        val sheetMap = linkedMapOf<String, SheetInfo>()
        val orderMap = linkedMapOf<Int, String>()
        val conflictedSheetKeys = linkedSetOf<String>()
        val conflictedOrderIndices = linkedSetOf<Int>()
        val initialIssues = linkedSetOf<String>().apply { addAll(queryScan.incompleteReasons) }
        fun sheetInfo(json: JSONObject): SheetInfo? {
            val display = json.optString("sheet").takeIf(String::isNotBlank) ?: return null
            val key = normalizeSheet(display)
            val order = json.optInt("sheetOrder").takeIf { json.has("sheetOrder") }
            val existing = sheetMap[key]
            val info = if (existing == null) SheetInfo(key, display, order) else existing.copy(order = existing.order ?: order)
            if (existing?.order != null && order != null && existing.order != order) conflictedSheetKeys += key
            sheetMap[key] = info
            if (order != null) {
                val previous = orderMap.putIfAbsent(order, key)
                if (previous != null && previous != key) conflictedOrderIndices += order
            }
            return info
        }
        parsedLines.forEach { (json, _) -> sheetInfo(json) }
        val rows = parsedLines.mapNotNull { (json, line) -> json.optJSONArray("cells")?.let { CellLine(line, json, it) } }
        if (rows.isEmpty() || sheetMap.isEmpty()) return null
        val cells = rows.flatMap { row -> row.cells.asCells().mapNotNull { cell ->
            val coordinate = parseAddress(cell.optString("address")) ?: return@mapNotNull null
            val sheet = sheetMap[normalizeSheet(row.json.optString("sheet"))] ?: return@mapNotNull null
            SourceCell(row.line, row.json, cell, sheet, coordinate)
        } }
        val cellsByIdentity = cells.groupBy { it.identity }.mapValues { (_, found) -> found.first() }
        val cellsByAddress = cells.groupBy { it.coordinate.address() }
        val plan = ReferencePlan().apply {
            incompleteReasons += initialIssues
            this.conflictedSheetKeys += conflictedSheetKeys
            this.conflictedOrderIndices += conflictedOrderIndices
        }
        queryScan.references.forEach { resolve(it, defaultSheet = null, queryReference = true, sheetMap, orderMap, cellsByAddress, plan) }
        val directCells = plan.requested.keys.mapNotNull(cellsByIdentity::get)
        plan.direct += plan.requested.keys

        // Inspect only directly selected cells; formulas are never evaluated or followed recursively.
        directCells.forEach { sourceCell ->
            val formula = sourceCell.cell.optString("formula")
            if (formula.isNotBlank()) {
                val scan = scanReferences(formula, formula = true)
                plan.incompleteReasons += scan.incompleteReasons
                scan.references.forEach { resolve(it, sourceCell.sheet, queryReference = false, sheetMap, orderMap, cellsByAddress, plan) }
            }
        }

        plan.requested.forEach { (identity, sheet) ->
            if (identity !in cellsByIdentity) plan.missing.putIfAbsent(identity, sheet)
        }
        if (plan.missing.isNotEmpty()) plan.incompleteReasons += "MISSING_REFERENCED_CELLS"

        val selectedByIdentity = plan.requested.keys.mapNotNull(cellsByIdentity::get).distinctBy { it.identity }.take(MAX_REFERENCED_CELLS)
        val selectedDirect = plan.direct
        val selectedColumnsBySheet = selectedByIdentity.groupBy { it.sheet.key }.mapValues { (_, list) -> list.map { it.coordinate.column }.toSet() }
        val candidates = mutableListOf<CellCandidate>()

        parsedLines.forEach { (json, line) ->
            if (!json.has("headerCandidate")) return@forEach
            val headers = json.optJSONArray("headerCandidate") ?: return@forEach
            val sheet = sheetMap[normalizeSheet(json.optString("sheet"))] ?: return@forEach
            val columns = selectedColumnsBySheet[sheet.key].orEmpty()
            for (index in 0 until headers.length()) {
                val header = headers.optJSONObject(index) ?: continue
                if (header.optInt("column") !in columns) continue
                val label = boundedValue(header.optString("label"), 180)
                candidates += CellCandidate(line.start,
                    "Sheet ${sheet.displayName} · header ${columnLabel(header.optInt("column"))}=$label", 1, null)
            }
        }
        selectedByIdentity.forEach { sourceCell ->
            val address = sourceCell.coordinate.address()
            val value = boundedValueResult(sourceCell.cell.optString("value"), 180)
            val formulaValue = sourceCell.cell.optString("formula")
            val formulaResult = formulaValue.takeIf { it.isNotBlank() }?.let { boundedValueResult(it, 120) }
            val formula = formulaResult?.let { " · formula=${it.value}" }.orEmpty()
            val qualified = qualifiedAddress(sourceCell.sheet.displayName, address)
            val direct = sourceCell.identity in selectedDirect
            candidates += CellCandidate(
                sourceCell.line.start,
                "$qualified=${value.value}$formula",
                if (direct) 3 else 2,
                sourceCell,
                value.truncated || formulaResult?.truncated == true,
            )
        }

        val chosen = mutableListOf<CellCandidate>()
        var bytesUsed = 0
        val selectedAddresses = linkedSetOf<String>()
        val selectedReferences = linkedSetOf<String>()
        val chosenIdentities = linkedSetOf<CellIdentity>()
        candidates.sortedWith(compareByDescending<CellCandidate> { it.priority }.thenBy { it.start }.thenBy { it.cell?.sheet?.key.orEmpty() }.thenBy { it.cell?.coordinate?.address().orEmpty() })
            .forEach { candidate ->
                val separator = if (chosen.isEmpty()) 0 else 1
                val candidateBytes = utf8Length(candidate.text)
                if (candidateBytes + separator <= budget - bytesUsed) {
                    chosen += candidate
                    bytesUsed += candidateBytes + separator
                    candidate.cell?.let { cell ->
                        if (chosenIdentities.add(cell.identity)) {
                            selectedAddresses += cell.coordinate.address()
                            selectedReferences += qualifiedAddress(cell.sheet.displayName, cell.coordinate.address())
                        }
                    }
                    if (candidate.truncated) {
                        plan.incompleteReasons += "CELL_EVIDENCE_BYTE_BUDGET"
                        candidate.cell?.let { plan.unresolved += qualifiedAddress(it.sheet.displayName, it.coordinate.address()) }
                    }
                } else if (candidate.cell != null) {
                    plan.incompleteReasons += "CELL_EVIDENCE_BYTE_BUDGET"
                    plan.unresolved += qualifiedAddress(candidate.cell.sheet.displayName, candidate.cell.coordinate.address())
                }
            }

        if (chosen.isEmpty()) {
            if (selectedByIdentity.isNotEmpty()) plan.incompleteReasons += "CELL_EVIDENCE_BYTE_BUDGET"
            return ContextEvidenceExcerpt(
                text = "",
                originalCharacters = source.length,
                charStart = null,
                charEnd = null,
                reason = "NO_REFERENCED_CELL_EVIDENCE_INCLUDED",
                cellAddresses = emptyList(),
                incompleteReasons = plan.incompleteReasons.toList(),
                missingCellAddresses = plan.missing.keys.map { it.address },
                missingCellReferences = plan.missing.map { (identity, sheet) -> qualifiedAddress(sheet.displayName, identity.address) },
                ambiguousCellReferences = plan.ambiguous.toList(),
                unresolvedCellReferences = plan.unresolved.toList(),
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
            incompleteReasons = plan.incompleteReasons.toList(),
            missingCellAddresses = plan.missing.keys.map { it.address },
            cellReferences = selectedReferences.toList(),
            missingCellReferences = plan.missing.map { (identity, sheet) -> qualifiedAddress(sheet.displayName, identity.address) },
            ambiguousCellReferences = plan.ambiguous.toList(),
            unresolvedCellReferences = plan.unresolved.toList(),
        )
    }

    private fun scanReferences(input: String, formula: Boolean): ReferenceScan {
        val issues = linkedSetOf<String>()
        val chars = input.toCharArray()
        if (formula) maskFormulaStrings(chars)
        val working = String(chars)
        externalWorkbookReference.findAll(working).forEach { match ->
            issues += "EXTERNAL_WORKBOOK_REFERENCE_UNRESOLVED"
            for (index in match.range) chars[index] = ' '
        }
        if (formula && dynamicReference.containsMatchIn(working)) issues += "DYNAMIC_REFERENCE_UNRESOLVED"
        if (formula && structuredReference.containsMatchIn(working)) issues += "STRUCTURED_REFERENCE_UNRESOLVED"
        val masked = String(chars)
        val covered = mutableListOf<IntRange>()
        val references = mutableListOf<CellReference>()
        cellReference.findAll(masked).forEach { match ->
            covered += match.range
            val firstRaw = match.groups["first"]?.value ?: return@forEach
            if (firstRaw.replace("$", "").uppercase(Locale.ROOT) in functionLikeCellTokens && match.groups["sheet"] == null) return@forEach
            val first = parseAddress(firstRaw) ?: return@forEach
            val last = match.groups["last"]?.value?.let(::parseAddress) ?: first
            val sheetToken = match.groups["sheet"]?.value
            val decoded = sheetToken?.let(::decodeSheetQualifier)
            references += CellReference(decoded?.first, decoded?.second, CellRange(
                CellCoordinate(minOf(first.column, last.column), minOf(first.row, last.row)),
                CellCoordinate(maxOf(first.column, last.column), maxOf(first.row, last.row)),
            ))
        }
        if (formula) {
            formulaIdentifier.findAll(masked).forEach { token ->
                if (covered.any { token.range.first in it }) return@forEach
                if (token.groupValues[1].uppercase(Locale.ROOT) in commonConstants) return@forEach
                val next = masked.indexOfFirstNonWhitespace(token.range.last + 1)
                if (next in masked.indices && masked[next] == '(') return@forEach
                issues += "UNSUPPORTED_DEFINED_NAME_OR_REFERENCE"
            }
        }
        return ReferenceScan(references, issues)
    }

    private fun maskFormulaStrings(chars: CharArray) {
        var inside = false
        var index = 0
        while (index < chars.size) {
            if (chars[index] == '"') {
                chars[index] = ' '
                if (inside && index + 1 < chars.size && chars[index + 1] == '"') {
                    chars[index + 1] = ' '
                    index += 2
                    continue
                }
                inside = !inside
            } else if (inside) chars[index] = ' '
            index++
        }
    }

    private fun decodeSheetQualifier(raw: String): Pair<String, String?> {
        val decoded = if (raw.startsWith('\'')) raw.drop(1).dropLast(1).replace("''", "'") else raw
        val endpoints = decoded.split(':', limit = 2)
        return endpoints[0] to endpoints.getOrNull(1)
    }

    private fun resolve(
        reference: CellReference,
        defaultSheet: SheetInfo?,
        queryReference: Boolean,
        sheets: Map<String, SheetInfo>,
        orderMap: Map<Int, String>,
        cellsByAddress: Map<String, List<SourceCell>>,
        plan: ReferencePlan,
    ) {
        val coordinateCount = reference.range.area
        val targetSheets: List<SheetInfo> = when {
            reference.sheetStart == null && defaultSheet != null -> listOfNotNull(sheets[defaultSheet.key])
            reference.sheetStart == null -> emptyList()
            reference.sheetEnd == null -> {
                val target = sheets[normalizeSheet(reference.sheetStart)]
                if (target == null) plan.incompleteReasons += "SHEET_NOT_FOUND"
                listOfNotNull(target)
            }
            else -> resolveThreeDimensional(reference, sheets, orderMap, plan)
        }

        if (queryReference && reference.sheetStart == null) {
            val coordinates = coordinates(reference.range, plan)
            for (coordinate in coordinates) {
                val address = coordinate.address()
                val candidates = cellsByAddress[address].orEmpty().distinctBy { it.sheet.key }
                when {
                    candidates.size == 1 -> addRequested(candidates.single().sheet, address, plan, direct = true)
                    candidates.size > 1 -> {
                        plan.incompleteReasons += "AMBIGUOUS_UNQUALIFIED_REFERENCE"
                        candidates.forEach { candidate -> addAmbiguous(candidate.sheet, address, plan) }
                    }
                    sheets.size == 1 -> addRequested(sheets.values.single(), address, plan, direct = true)
                    else -> {
                        plan.incompleteReasons += "AMBIGUOUS_UNQUALIFIED_REFERENCE"
                        sheets.values.forEach { candidate -> addAmbiguous(candidate, address, plan) }
                    }
                }
            }
            return
        }

        if (reference.sheetStart != null && reference.sheetEnd == null && targetSheets.isEmpty()) {
            val name = reference.sheetStart
            for (coordinate in coordinates(reference.range, plan)) {
                recordMissing(CellIdentity(normalizeSheet(name), coordinate.address()), SheetInfo(normalizeSheet(name), name, null), plan)
            }
            return
        }
        if (reference.sheetStart == null && defaultSheet != null && targetSheets.isEmpty()) {
            plan.incompleteReasons += "SHEET_NOT_FOUND"
            for (coordinate in coordinates(reference.range, plan)) recordMissing(
                CellIdentity(defaultSheet.key, coordinate.address()), defaultSheet, plan,
            )
            return
        }
        if (reference.sheetEnd != null && targetSheets.isEmpty()) return

        var visited = 0L
        loop@ for (sheet in targetSheets) {
            var stopped = false
            for (row in reference.range.first.row..reference.range.last.row) {
                for (column in reference.range.first.column..reference.range.last.column) {
                    if (visited++ >= MAX_REFERENCED_CELLS || plan.requested.size + plan.missing.size + plan.ambiguous.size + plan.unresolved.size >= MAX_REFERENCED_CELLS) {
                        plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                        stopped = true
                        break
                    }
                    addRequested(sheet, CellCoordinate(column, row).address(), plan, direct = queryReference)
                }
                if (stopped) break
            }
            if (stopped) break@loop
        }
        if (coordinateCount * targetSheets.size > MAX_REFERENCED_CELLS) plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
    }

    private fun resolveThreeDimensional(
        reference: CellReference,
        sheets: Map<String, SheetInfo>,
        orderMap: Map<Int, String>,
        plan: ReferencePlan,
    ): List<SheetInfo> {
        val startName = requireNotNull(reference.sheetStart)
        val endName = requireNotNull(reference.sheetEnd)
        val start = sheets[normalizeSheet(startName)]
        val end = sheets[normalizeSheet(endName)]
        if (start == null || end == null) {
            plan.incompleteReasons += "THREE_D_ENDPOINT_SHEET_NOT_FOUND"
            unresolvedBoundaryReferences(reference.range, listOfNotNull(start ?: SheetInfo(normalizeSheet(startName), startName, null), end ?: SheetInfo(normalizeSheet(endName), endName, null)), plan)
            return emptyList()
        }
        val from = start.order
        val to = end.order
        if (from == null || to == null) {
            plan.incompleteReasons += "THREE_D_SHEET_ORDER_UNAVAILABLE"
            unresolvedBoundaryReferences(reference.range, listOf(start, end), plan)
            return emptyList()
        }
        val lower = minOf(from, to)
        val upper = maxOf(from, to)
        if (start.key in plan.conflictedSheetKeys || end.key in plan.conflictedSheetKeys ||
            (lower..upper).any { it in plan.conflictedOrderIndices }) {
            plan.incompleteReasons += "SHEET_ORDER_METADATA_CONFLICT"
            plan.incompleteReasons += "THREE_D_SHEET_ORDER_CONFLICT"
            unresolvedBoundaryReferences(reference.range, listOf(start, end), plan)
            return emptyList()
        }
        if (upper - lower > 255) {
            plan.incompleteReasons += "THREE_D_SHEET_ORDER_LIMIT"
            unresolvedBoundaryReferences(reference.range, listOf(start, end), plan)
            return emptyList()
        }
        val ordered = (lower..upper).mapNotNull { orderMap[it]?.let(sheets::get) }
        if (ordered.size != upper - lower + 1 || ordered.firstOrNull()?.key != (if (from <= to) start.key else end.key) || ordered.lastOrNull()?.key != (if (from <= to) end.key else start.key)) {
            plan.incompleteReasons += "THREE_D_SHEET_ORDER_INCOMPLETE"
            unresolvedBoundaryReferences(reference.range, ordered.ifEmpty { listOf(start, end) }, plan)
            return emptyList()
        }
        return if (from <= to) ordered else ordered.asReversed()
    }

    private fun unresolvedBoundaryReferences(range: CellRange, sheets: List<SheetInfo>, plan: ReferencePlan) {
        var remaining = MAX_REFERENCED_CELLS - plan.requested.size - plan.missing.size - plan.ambiguous.size - plan.unresolved.size
        loop@ for (sheet in sheets) {
            for (row in range.first.row..range.last.row) for (column in range.first.column..range.last.column) {
                if (remaining-- <= 0) {
                    plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                    break@loop
                }
                val address = CellCoordinate(column, row).address()
                plan.unresolved += qualifiedAddress(sheet.displayName, address)
            }
        }
    }

    private fun coordinates(range: CellRange, plan: ReferencePlan): List<CellCoordinate> {
        val result = mutableListOf<CellCoordinate>()
        loop@ for (row in range.first.row..range.last.row) for (column in range.first.column..range.last.column) {
            if (result.size >= MAX_REFERENCED_CELLS || plan.requested.size + plan.missing.size + plan.ambiguous.size + plan.unresolved.size + result.size >= MAX_REFERENCED_CELLS) {
                plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
                break@loop
            }
            result += CellCoordinate(column, row)
        }
        if (range.area > result.size) plan.incompleteReasons += "CELL_REFERENCE_LIMIT"
        return result
    }

    private fun addRequested(sheet: SheetInfo, address: String, plan: ReferencePlan, direct: Boolean) {
        val identity = CellIdentity(sheet.key, address)
        if (!plan.track(identity)) return
        plan.requested.putIfAbsent(identity, sheet)
        if (direct) plan.direct += identity
    }

    private fun addAmbiguous(sheet: SheetInfo, address: String, plan: ReferencePlan) {
        val identity = CellIdentity(sheet.key, address)
        if (plan.track(identity)) plan.ambiguous += qualifiedAddress(sheet.displayName, address)
    }

    private fun recordMissing(identity: CellIdentity, sheet: SheetInfo, plan: ReferencePlan) {
        if (plan.track(identity)) plan.missing.putIfAbsent(identity, sheet)
        plan.incompleteReasons += "MISSING_REFERENCED_CELLS"
    }

    private fun normalizeSheet(name: String) = Normalizer.normalize(name, Normalizer.Form.NFC).uppercase(Locale.ROOT)

    private fun qualifiedAddress(sheet: String, address: String): String {
        val safe = if (Regex("[\\p{L}_][\\p{L}\\p{N}_.]*").matches(sheet)) sheet else "'${sheet.replace("'", "''")}'"
        return "$safe!$address"
    }

    private fun String.indexOfFirstNonWhitespace(from: Int): Int {
        var index = from.coerceAtLeast(0)
        while (index < length && this[index].isWhitespace()) index++
        return index
    }

    private fun parseAddress(raw: String): CellCoordinate? {
        val normalized = raw.replace("$", "").uppercase(Locale.ROOT)
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
