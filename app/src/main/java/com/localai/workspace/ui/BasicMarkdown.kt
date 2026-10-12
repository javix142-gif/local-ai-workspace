package com.localai.workspace.ui

import java.net.URI

/** Deliberately small renderer grammar: no HTML or embedded resources; chat links are HTTP(S) only. */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val source: String) : MarkdownBlock
}

data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String? = null,
)

object BasicMarkdown {
    fun blocks(content: String): List<MarkdownBlock> {
        val result = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        fun flush() { if (paragraph.isNotEmpty()) { result += MarkdownBlock.Paragraph(paragraph.joinToString("\n")); paragraph.clear() } }
        val lines = content.replace("\r\n", "\n").split('\n')
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$").matchEntire(line)
            if (fence != null) {
                flush()
                val marker = fence.groupValues[1]
                val language = fence.groupValues[2].trim().takeIf { it.matches(Regex("[A-Za-z0-9_+#.-]{1,32}")) }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().let { s -> s.length >= marker.length && s.all { it == marker[0] } }) {
                    code += lines[index++]
                }
                result += MarkdownBlock.Code(language, code.joinToString("\n"))
            } else {
                val table = parseTable(lines, index)
                val list = Regex("^\\s*([-*+]|\\d+[.)])\\s+(.+)$").matchEntire(line)
                val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
                when {
                    table != null -> {
                        flush()
                        result += table.block
                        index = table.endExclusive - 1
                    }
                    line.isBlank() -> flush()
                    list != null -> { flush(); result += MarkdownBlock.ListItem(if (list.groupValues[1].length == 1) "•" else list.groupValues[1], list.groupValues[2]) }
                    heading != null -> {
                        flush()
                        result += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                    }
                    else -> paragraph += line
                }
            }
            index++
        }
        flush()
        return result
    }

    private data class ParsedTable(val block: MarkdownBlock.Table, val endExclusive: Int)

    /** Supports pipe tables with a header separator; malformed candidates remain ordinary text. */
    private fun parseTable(lines: List<String>, start: Int): ParsedTable? {
        if (start + 1 >= lines.size) return null
        val headers = splitTableRow(lines[start]) ?: return null
        val separator = splitTableRow(lines[start + 1]) ?: return null
        if (headers.size < 2 || separator.size != headers.size || separator.any { !it.matches(Regex(":?-{3,}:?")) }) {
            return null
        }

        val rows = mutableListOf<List<String>>()
        var end = start + 2
        while (end < lines.size && lines[end].isNotBlank()) {
            val row = splitTableRow(lines[end]) ?: if (looksLikePipeRow(lines[end])) return null else break
            if (row.size != headers.size) return null
            rows += row
            end++
        }
        val source = lines.subList(start, end).joinToString("\n")
        return ParsedTable(MarkdownBlock.Table(headers, rows, source), end)
    }

    private fun looksLikePipeRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith('|') || trimmed.endsWith('|')
    }

    /** Splits unescaped pipes outside single-backtick spans; initial and final outer pipes are independently optional. */
    private fun splitTableRow(line: String): List<String>? {
        var value = line.trim()
        if (value.startsWith('|')) value = value.substring(1)
        if (hasOptionalFinalPipe(value)) value = value.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inCode = false
        var foundSeparator = false
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '\\' && index + 1 < value.length && value[index + 1] == '|' -> {
                    cell.append('|')
                    index += 2
                }
                char == '`' -> {
                    inCode = !inCode
                    cell.append(char)
                    index++
                }
                char == '|' && !inCode -> {
                    cells += cell.toString().trim()
                    cell.clear()
                    foundSeparator = true
                    index++
                }
                else -> {
                    cell.append(char)
                    index++
                }
            }
        }
        if (!foundSeparator) return null
        cells += cell.toString().trim()
        return cells
    }

    private fun hasOptionalFinalPipe(value: String): Boolean {
        val lastIndex = value.lastIndex
        if (lastIndex < 0 || value[lastIndex] != '|') return false
        if (lastIndex > 0 && value[lastIndex - 1] == '\\') return false

        var inCode = false
        for (index in 0 until lastIndex) {
            if (value[index] == '`') inCode = !inCode
        }
        return !inCode
    }

    fun inline(text: String, allowLinks: Boolean = true): List<InlineRun> {
        val result = mutableListOf<InlineRun>()
        val plain = StringBuilder()
        fun appendRun(run: InlineRun) {
            if (run.text.isEmpty()) return
            val previous = result.lastOrNull()
            if (previous != null && previous.bold == run.bold && previous.code == run.code && previous.linkUrl == run.linkUrl) {
                result[result.lastIndex] = previous.copy(text = previous.text + run.text)
            } else {
                result += run
            }
        }
        fun flush() { if (plain.isNotEmpty()) { appendRun(InlineRun(plain.toString())); plain.clear() } }
        var index = 0
        while (index < text.length) {
            if (text[index] == '\\' && index + 1 < text.length && text[index + 1] in "*`\\") {
                plain.append(text[index + 1]); index += 2; continue
            }
            if (allowLinks && text[index] == '!' && text.getOrNull(index + 1) == '[') {
                val image = scanLinkTokenAt(text, index + 1)
                if (image != null) {
                    plain.append(text, index, image.endExclusive)
                    index = image.endExclusive
                    continue
                }
            }
            if (text[index] == '<') {
                val htmlEnd = htmlTagEndExclusive(text, index)
                if (htmlEnd != null) {
                    plain.append(text, index, htmlEnd)
                    index = htmlEnd
                    continue
                }
            }
            val link = if (allowLinks && text[index] == '[') scanLinkTokenAt(text, index) else null
            if (link != null) {
                flush()
                if (link.label != null && link.url != null) {
                    appendRun(InlineRun(link.label, linkUrl = link.url))
                } else {
                    plain.append(text, index, link.endExclusive)
                }
                index = link.endExclusive
                continue
            }
            val delimiter = when { text[index] == '`' -> "`"; text.startsWith("**", index) -> "**"; else -> null }
            val end = delimiter?.let { text.indexOf(it, index + it.length) } ?: -1
            if (delimiter != null && end > index + delimiter.length) {
                flush()
                val inner = text.substring(index + delimiter.length, end)
                if (delimiter == "**") {
                    inline(inner, allowLinks).forEach { appendRun(it.copy(bold = true)) }
                } else {
                    appendRun(InlineRun(inner, code = true))
                }
                index = end + delimiter.length
            } else plain.append(text[index++])
        }
        flush()
        return result
    }

    private data class ScannedLinkToken(val label: String?, val url: String?, val endExclusive: Int)

    /** Returns malformed tokens too, so nested syntax cannot turn part of them into a live link. */
    private fun scanLinkTokenAt(text: String, start: Int): ScannedLinkToken? {
        if (isEscaped(text, start)) return null
        val labelEnd = text.indexOf("](", start + 1)
        if (labelEnd < 0) return null
        val label = text.substring(start + 1, labelEnd)
        val validLabel = label.isNotBlank() && '[' !in label && '\n' !in label && '\r' !in label

        var depth = 0
        var cursor = labelEnd + 2
        val urlStart = cursor
        while (cursor < text.length) {
            when (text[cursor]) {
                '\n', '\r' -> return ScannedLinkToken(null, null, cursor)
                '(' -> depth++
                ')' -> {
                    if (depth == 0) {
                        val url = text.substring(urlStart, cursor)
                        if (validLabel && isSafeHttpUrl(url)) return ScannedLinkToken(label, url, cursor + 1)
                        return ScannedLinkToken(null, null, cursor + 1)
                    }
                    depth--
                }
            }
            cursor++
        }
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return ScannedLinkToken(null, null, lineEnd)
    }

    private fun isSafeHttpUrl(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.isOpaque &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var slashes = 0
        var cursor = index - 1
        while (cursor >= 0 && text[cursor] == '\\') {
            slashes++
            cursor--
        }
        return slashes % 2 == 1
    }

    /** Keep HTML tag text literal and avoid interpreting Markdown-looking attribute values. */
    private fun htmlTagEndExclusive(text: String, start: Int): Int? {
        val endInclusive = text.indexOf('>', start + 1)
        if (endInclusive < 0) return null
        val token = text.substring(start, endInclusive + 1)
        val isTag = token.matches(Regex("</?[A-Za-z][A-Za-z0-9:-]*(?:\\s+[^<>]*)?\\s*/?>"))
        val isComment = token.startsWith("<!--") && token.endsWith("-->")
        return (isTag || isComment).takeIf { it }?.let { endInclusive + 1 }
    }
}
