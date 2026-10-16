package com.localai.workspace.documents

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Bounded, read-only view of an imported ZIP. The ignore reader supports nested `.gitignore` files,
 * comments, `!` negation, root anchoring, directory rules, and `*`/`?`/`**` globs. Patterns it
 * cannot interpret are rejected rather than silently treating an ignored path as visible. This is
 * intentionally not a claim of full Git ignore-spec compatibility.
 */
object CodebaseArchive {
    data class CodeFile(val relativePath: String, val language: String, val text: String)

    private const val MAX_IGNORE_FILE_BYTES = 64 * 1024
    private const val MAX_TOTAL_IGNORE_BYTES = 256 * 1024
    private const val MAX_CODE_TEXT_CHARS = 1_000_000
    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "c", "h", "cc", "cpp", "hpp", "rs", "go", "swift", "sh", "sql",
    )

    fun isCodePath(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in codeExtensions

    /** Visible archive entries, after applying bounded `.gitignore` directory/file rules. */
    fun visibleEntries(zip: ZipFile): List<ZipEntry> {
        StructuredDocuments.inspect(zip)
        val entries = zip.entries().asSequence().toList()
        val rules = readIgnoreRules(zip, entries)
        return entries.filter { entry ->
            !entry.isDirectory && entry.name.substringAfterLast('/') != ".gitignore" &&
                !isGitMetadata(entry.name) && !rules.isIgnored(entry.name)
        }
    }

    /** Reads only recognized source files; ZIP safety and expansion caps remain enforced. */
    fun codeFiles(file: File): List<CodeFile> = ZipFile(file).use { zip ->
        var totalCharacters = 0
        visibleEntries(zip).mapNotNull { entry ->
            val language = entry.name.substringAfterLast('.', "").lowercase()
            if (!isCodePath(entry.name)) return@mapNotNull null
            val bytes = zip.getInputStream(entry).use { StructuredDocuments.bounded(it, 8 * 1024 * 1024) }
            val text = StructuredDocuments.text(bytes).first
            totalCharacters += text.length
            require(totalCharacters <= MAX_CODE_TEXT_CHARS) { "Code archive extracted text exceeds limit" }
            CodeFile(entry.name, language, text)
        }
    }

    private fun isGitMetadata(path: String): Boolean = path.trimEnd('/').split('/').any { it == ".git" }

    private fun readIgnoreRules(zip: ZipFile, entries: List<ZipEntry>): List<IgnoreRule> {
        var totalBytes = 0
        return entries.asSequence()
            .filter { !it.isDirectory && it.name.substringAfterLast('/') == ".gitignore" }
            .sortedBy { it.name.count { character -> character == '/' } }
            .flatMap { entry ->
                val bytes = zip.getInputStream(entry).use { StructuredDocuments.bounded(it, MAX_IGNORE_FILE_BYTES) }
                totalBytes += bytes.size
                require(totalBytes <= MAX_TOTAL_IGNORE_BYTES) { "Code archive ignore files exceed limit" }
                val base = entry.name.substringBeforeLast('/', "").trim('/')
                StructuredDocuments.text(bytes).first.lineSequence().mapNotNull { raw ->
                    require(raw.isEmpty() || raw.first() != ' ' && raw.first() != '\t') { "UNSUPPORTED_GITIGNORE_PATTERN" }
                    val line = raw.trimEnd()
                    if (line.isEmpty() || line.startsWith('#')) return@mapNotNull null
                    val negated = line.startsWith('!') && !line.startsWith("\\!")
                    val unescaped = if (line.startsWith("\\#") || line.startsWith("\\!")) line.drop(1) else line
                    val value = (if (negated) unescaped.drop(1) else unescaped).trim()
                    if (value.isEmpty()) return@mapNotNull null
                    require(!value.contains('\\') && !value.contains('[') && !value.contains(']')) { "UNSUPPORTED_GITIGNORE_PATTERN" }
                    val directoryOnly = value.endsWith('/')
                    val anchored = value.startsWith('/')
                    val pattern = value.trim('/').replace("\\#", "#").replace("\\!", "!")
                    if (pattern.isEmpty()) null else IgnoreRule(base, pattern, negated, directoryOnly, anchored)
                }
            }
            .toList()
    }

    private data class IgnoreRule(
        val base: String,
        val pattern: String,
        val negated: Boolean,
        val directoryOnly: Boolean,
        val anchored: Boolean,
    ) {
        private val glob = compileGlob(pattern)

        fun matches(candidate: String, directory: Boolean): Boolean {
            if (directoryOnly && !directory) return false
            val relative = if (base.isEmpty()) candidate else {
                if (!candidate.startsWith("$base/")) return false
                candidate.removePrefix("$base/")
            }
            if (relative.isEmpty()) return false
            if (anchored || pattern.contains('/')) return glob.matches(relative)
            return relative.split('/').any { glob.matches(it) }
        }

        private fun compileGlob(value: String): Regex {
            val regex = StringBuilder("^")
            var index = 0
            while (index < value.length) {
                when {
                    value.startsWith("**/", index) -> { regex.append("(?:.*/)?"); index += 3 }
                    value.startsWith("**", index) -> { regex.append(".*"); index += 2 }
                    value[index] == '*' -> { regex.append("[^/]*"); index++ }
                    value[index] == '?' -> { regex.append("[^/]"); index++ }
                    else -> { regex.append(Regex.escape(value[index].toString())); index++ }
                }
            }
            regex.append('$')
            return Regex(regex.toString())
        }
    }

    private fun List<IgnoreRule>.isIgnored(path: String): Boolean {
        val parts = path.trimEnd('/').split('/').filter(String::isNotEmpty)
        if (parts.any { it == ".git" }) return true
        var parentIgnored = false
        parts.forEachIndexed { index, _ ->
            if (parentIgnored) return true
            val candidate = parts.take(index + 1).joinToString("/")
            val directory = index < parts.lastIndex || path.endsWith('/')
            val decision = lastOrNull { it.matches(candidate, directory) }
            if (decision != null) parentIgnored = !decision.negated
        }
        return parentIgnored
    }
}
