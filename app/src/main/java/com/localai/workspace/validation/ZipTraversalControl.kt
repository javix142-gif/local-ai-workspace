package com.localai.workspace.validation

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

/** Only the bounded, synthetic QA fixture is inspected here. The security decision is always
 * obtained from DocumentParser, including Android's own ZipFile path validation.
 * Never clears the process-global ZipPathValidator and never extracts an unsafe entry.
 */
internal object ZipTraversalControl {
    const val UNSAFE = "../escape.txt"
    private val safeText = "Synthetic safe fixture."
    private val unsafeText = "Synthetic traversal fixture. Never extract."

    fun create(file: File, unsafeName: String = UNSAFE) {
        require(unsafeName in setOf(UNSAFE, "folder/../../escape2.txt"))
        file.parentFile!!.mkdirs()
        file.writeBytes(archive(linkedMapOf("safe.txt" to safeText, unsafeName to unsafeText)))
    }

    private fun archive(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, text) ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(name).apply {
                time = 0L; method = ZipEntry.STORED; size = bytes.size.toLong()
                compressedSize = size; crc = CRC32().apply { update(bytes) }.value
            })
            zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()

    /** Android 14+ intentionally refuses to OPEN a valid traversal ZIP. To validate structure
     * without weakening that protection, compare the exact deterministic fixture bytes and
     * validate a name-only, equal-length safe twin with ZipFile (central directory + every CRC).
     * The hostile ORIGINAL, never the twin, is subsequently sent to production ingestion.
     * No reflection, hidden constructor or global validator override is used.
     */
    fun verifyFixture(file: File, unsafeName: String = UNSAFE): Boolean {
        if(file.length() !in 1L..4096L) return false
        val bytes = file.readBytes()
        val expected = linkedMapOf("safe.txt" to safeText, unsafeName to unsafeText)
        if(!bytes.contentEquals(archive(expected))) return false
        val alias = unsafeName.replace("..", "aa")
        val twin = File.createTempFile("zip-structure-", ".zip", file.parentFile)
        try {
            val renamed = bytes.copyOf()
            val nameBytes = unsafeName.toByteArray(Charsets.UTF_8)
            val aliasBytes = alias.toByteArray(Charsets.UTF_8)
            var replacements = 0
            for(i in 0..(renamed.size - nameBytes.size)) {
                if(nameBytes.indices.all { renamed[i + it] == nameBytes[it] }) {
                    aliasBytes.copyInto(renamed, i); replacements++
                }
            }
            // Both local header and central directory must contain the literal unsafe name.
            if(replacements != 2 || !renamed.contentEquals(archive(linkedMapOf("safe.txt" to safeText, alias to unsafeText)))) return false
            twin.writeBytes(renamed)
            if(!verifyContents(twin, linkedMapOf("safe.txt" to safeText, alias to unsafeText))) return false
            return try { verifyContents(file, expected) } catch(error: ZipException) {
                // Valid archive + precisely identified OS security rejection; not corruption.
                error.message == "Invalid zip entry path: $unsafeName"
            }
        } finally { check(twin.delete() || !twin.exists()) }
    }

    private fun verifyContents(file: File, entries: Map<String, String>): Boolean = ZipFile(file).use { zip ->
        zip.entries().asSequence().map { it.name }.toList() == entries.keys.toList() && entries.all { (name, text) ->
            val entry = zip.getEntry(name) ?: return@all false
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            bytes.contentEquals(text.toByteArray(Charsets.UTF_8)) && CRC32().apply { update(bytes) }.value == entry.crc
        }
    }

    fun rejectionCode(error: Exception, unsafeName: String): String = when {
        error is ZipException && error.message == "Invalid zip entry path: $unsafeName" -> "ZIP_UNSAFE_PATH_REJECTED"
        error is IllegalArgumentException && error.message == "Unsafe or duplicate archive path" -> "ZIP_UNSAFE_PATH_REJECTED"
        error is ZipException -> "ZIP_CORRUPT"
        error.message == "Suspicious compression ratio" -> "ZIP_BOMB_LIMIT"
        error.message == "Archive exceeds 100 entries" -> "ZIP_TOO_MANY_FILES"
        error.message in setOf("File exceeds 20 MiB", "Archive entry exceeds 8 MiB", "Archive exceeds 32 MiB expanded", "Expanded input exceeds limit", "Archive exceeds expanded byte limit") -> "ZIP_SIZE_LIMIT"
        error is IOException -> "ZIP_IO_ERROR"
        else -> "ZIP_UNEXPECTED_ERROR"
    }

    suspend fun run(file: File, sandbox: File, unsafeName: String = UNSAFE,
        parse: suspend (File) -> Unit): ValidationOutcome {
        val data = JsonObject().apply {
            addProperty("fixtureValid", false); addProperty("unsafeEntryDetected", false)
            addProperty("unsafeEntryRejected", false); addProperty("escapedFileExists", false)
            addProperty("allWrittenPathsInsideSandbox", false)
        }
        val valid=try { verifyFixture(file, unsafeName) } catch(error: ZipException) { false }
        catch(error: IOException) { return ValidationOutcome(ValidationStatus.BLOCKED, "ZIP_IO_ERROR", data) }
        if(!valid) return ValidationOutcome(ValidationStatus.FAIL, "FIXTURE_INVALID",
            data.apply { addProperty("observedCondition", "ZIP_CORRUPT") })
        data.addProperty("fixtureValid", true); data.addProperty("unsafeEntryDetected", true)
        val root = sandbox.canonicalFile
        require(file.canonicalFile.toPath().startsWith(root.toPath()))
        val parent = root.parentFile!!
        val escaped = listOf(File(parent, "escape.txt"), File(parent, "escape2.txt"))
        if(escaped.any { it.exists() }) return ValidationOutcome(ValidationStatus.BLOCKED, "ESCAPED_PATH_ALREADY_EXISTS", data)
        val before = parent.walkTopDown().map { it.absolutePath }.toSet()
        var rejection: String? = null
        try { parse(file) }
        catch(cancel: CancellationException) { throw cancel }
        catch(error: Exception) { rejection = rejectionCode(error, unsafeName) }
        val escapedExists = escaped.any { it.exists() }
        val allInside = parent.walkTopDown().filter { it.absolutePath !in before || it.toPath().startsWith(root.toPath()) }
            .all { it.canonicalFile.toPath().startsWith(root.toPath()) }
        // Traversal must not be rewritten into a partial ordinary file either.
        val partial = root.walkTopDown().any { it.isFile && it.name in setOf("escape.txt", "escape2.txt") }
        val rejected = rejection == "ZIP_UNSAFE_PATH_REJECTED"
        data.addProperty("unsafeEntryRejected", rejected); data.addProperty("escapedFileExists", escapedExists)
        data.addProperty("allWrittenPathsInsideSandbox", allInside); data.addProperty("parserValidated", rejected)
        data.addProperty("observedCondition", rejection ?: "ZIP_TRAVERSAL_NOT_REJECTED")
        val passed = rejected && !escapedExists && allInside && !partial
        return ValidationOutcome(if(passed) ValidationStatus.PASS else if(rejection == "ZIP_IO_ERROR" && !escapedExists && allInside) ValidationStatus.BLOCKED else ValidationStatus.FAIL,
            if(passed) null else if(escapedExists || !allInside || partial) "ZIP_FILESYSTEM_ESCAPE" else rejection ?: "ZIP_TRAVERSAL_NOT_REJECTED", data)
    }
}
