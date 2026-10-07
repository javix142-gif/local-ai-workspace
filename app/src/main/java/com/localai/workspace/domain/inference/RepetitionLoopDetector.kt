package com.localai.workspace.domain.inference

/** Detects substantial cyclic blocks and repeated paragraphs across callback boundaries. */
class RepetitionLoopDetector {
    data class Loop(val blockCharacters: Int, val repetitions: Int)
    private var tail = ""
    private var paragraph = ""
    private val recentParagraphs = ArrayDeque<String>()
    private var longParagraph = false
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")
    private val separator = Regex("\\r?\\n[ \\t]*\\r?\\n")

    fun append(delta: String): Loop? {
        val combined = paragraph + delta
        var begin = 0
        for (match in separator.findAll(combined)) {
            val text = combined.substring(begin, match.range.first).trim().replace(spaces, " ")
            if (!longParagraph && substantial(text)) {
                recentParagraphs.addLast(text)
                if (recentParagraphs.count { it == text } >= 3) return Loop(text.length, 3)
                while (recentParagraphs.size > 24) recentParagraphs.removeFirst()
            }
            begin = match.range.last + 1
            longParagraph = false
        }
        val remaining = combined.substring(begin)
        if (remaining.length > 2048) longParagraph = true
        paragraph = remaining.takeLast(2048)
        val pending = paragraph.trim().replace(spaces, " ")
        if (!longParagraph && substantial(pending) && recentParagraphs.count { it == pending } >= 2) return Loop(pending.length, 3)
        tail = (tail + delta).takeLast(4096)
        for (length in 48..minOf(512, tail.length / 3)) {
            val end = tail.length
            val last = tail.substring(end - length)
            if (!tail.regionMatches(end - length * 2, last, 0, length) ||
                !tail.regionMatches(end - length * 3, last, 0, length)) continue
            // Whitespace, punctuation and simple separator runs are not a language loop.
            if (words.findAll(last).take(6).count() < 6) continue
            return Loop(length, 3)
        }
        return null
    }

    private fun substantial(text: String) = text.length in 48..1024 && words.findAll(text).take(6).count() >= 6

    companion object {
        fun containsLoop(text: String): Boolean {
            val detector = RepetitionLoopDetector()
            // Inspect the whole saved answer without retaining it twice or ignoring an earlier loop.
            for (chunk in text.chunked(64)) if (detector.append(chunk) != null) return true
            return false
        }
    }
}
