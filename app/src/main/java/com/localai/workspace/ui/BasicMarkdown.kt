package com.localai.workspace.ui

/** Deliberately small renderer grammar: no HTML, remote resources or executable links. */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Heading(val text: String) : MarkdownBlock
    data class Code(val language: String?, val text: String) : MarkdownBlock
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
                val list = Regex("^\\s*([-*+]|\\d+[.)])\\s+(.+)$").matchEntire(line)
                val heading = Regex("^#{1,6}\\s+(.+)$").matchEntire(line)
                when {
                    line.isBlank() -> flush()
                    list != null -> { flush(); result += MarkdownBlock.ListItem(if (list.groupValues[1].length == 1) "•" else list.groupValues[1], list.groupValues[2]) }
                    heading != null -> { flush(); result += MarkdownBlock.Heading(heading.groupValues[1]) }
                    else -> paragraph += line
                }
            }
            index++
        }
        flush()
        return result
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
