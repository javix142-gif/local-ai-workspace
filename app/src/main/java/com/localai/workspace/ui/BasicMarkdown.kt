package com.localai.workspace.ui

/** Deliberately small renderer grammar: no HTML, remote resources or executable links. */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>, val source: String) : MarkdownBlock
}

data class InlineRun(val text: String, val bold: Boolean = false, val code: Boolean = false)

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
        if (value.endsWith('|')) value = value.dropLast(1)
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

    fun inline(text: String): List<InlineRun> {
        val result = mutableListOf<InlineRun>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { result += InlineRun(plain.toString()); plain.clear() } }
        var index = 0
        while (index < text.length) {
            if (text[index] == '\\' && index + 1 < text.length && text[index + 1] in "*`\\") {
                plain.append(text[index + 1]); index += 2; continue
            }
            val delimiter = when { text[index] == '`' -> "`"; text.startsWith("**", index) -> "**"; else -> null }
            val end = delimiter?.let { text.indexOf(it, index + it.length) } ?: -1
            if (delimiter != null && end > index + delimiter.length) {
                flush(); result += InlineRun(text.substring(index + delimiter.length, end), bold = delimiter == "**", code = delimiter == "`")
                index = end + delimiter.length
            } else plain.append(text[index++])
        }
        flush()
        return result
    }
}
