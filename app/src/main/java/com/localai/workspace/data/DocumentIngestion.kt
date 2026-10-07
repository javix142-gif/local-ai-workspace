package com.localai.workspace.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.room.withTransaction
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

data class ParsedPage(val pageNumber: Int?, val text: String)

data class ParsedDocument(val pages: List<ParsedPage>)

data class TextChunk(
    val pageStart: Int?,
    val pageEnd: Int?,
    val charStart: Int?,
    val charEnd: Int?,
    val text: String,
    val sectionPath: String? = null,
)

class UnsupportedDocumentException(message: String) : Exception(message)

class DocumentParser(private val context: Context) {
    suspend fun parse(file: File, mimeType: String, displayName: String): ParsedDocument = kotlinx.coroutines.withTimeout(20_000) { kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
        require(file.length() <= com.localai.workspace.documents.StructuredDocuments.MAX_BYTES) { "File exceeds 20 MiB" }
        val extension = displayName.substringAfterLast('.', "").lowercase()
        when {
            extension == "xlsx" || mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> com.localai.workspace.documents.StructuredDocuments.xlsx(file).document()
            extension == "csv" || mimeType == "text/csv" -> com.localai.workspace.documents.StructuredDocuments.csv(file).document()
            extension == "zip" || mimeType == "application/zip" -> parseZip(file)
            extension == "pdf" || mimeType == "application/pdf" -> parsePdf(file)
            extension == "docx" || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> parseDocx(file)
            extension in setOf("html", "htm") || mimeType == "text/html" -> {
                ParsedDocument(listOf(ParsedPage(null, stripHtml(com.localai.workspace.documents.StructuredDocuments.text(com.localai.workspace.documents.StructuredDocuments.readFile(file)).first))))
            }
            extension in setOf("txt", "md", "markdown", "log", "json", "xml", "yaml", "yml", "py", "kt", "java", "js", "ts", "c", "cpp", "h", "rs", "sh", "sql", "toml", "ini") || mimeType in setOf("application/json", "application/xml", "application/yaml") || mimeType.startsWith("text/") -> {
                ParsedDocument(listOf(ParsedPage(null, com.localai.workspace.documents.StructuredDocuments.text(com.localai.workspace.documents.StructuredDocuments.readFile(file)).first)))
            }
            else -> throw UnsupportedDocumentException("No safe local parser is installed for .$extension")
        }
    }

    }

    private fun parsePdf(file: File): ParsedDocument {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { document ->
            require(document.numberOfPages <= 100) { "PDF exceeds 100 pages" }
            val deadline = System.nanoTime() + 15_000_000_000
            var total = 0
            val pages = buildList {
                for (page in 1..document.numberOfPages) {
                    require(System.nanoTime() < deadline && !Thread.currentThread().isInterrupted) { "PDF parsing cancelled or timed out" }
                    val stripper = PDFTextStripper().apply {
                        startPage = page
                        endPage = page
                    }
                    val text = stripper.getText(document).trim(); total += text.length
                    require(total <= 1_000_000) { "PDF text exceeds limit" }; add(ParsedPage(page, text))
                }
            }
            if (pages.all { it.text.isBlank() }) throw UnsupportedDocumentException("Este PDF no contiene texto extraíble. OCR is not installed.")
            return ParsedDocument(pages)
        }
    }

    private fun parseDocx(file: File): ParsedDocument {
        ZipFile(file).use { zip ->
            com.localai.workspace.documents.StructuredDocuments.inspect(zip)
            val entry = zip.getEntry("word/document.xml")
                ?: throw UnsupportedDocumentException("DOCX does not contain word/document.xml")
            val xml = zip.getInputStream(entry).use { com.localai.workspace.documents.StructuredDocuments.bounded(it, 8 * 1024 * 1024) }.toString(Charsets.UTF_8)
            require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "XML entities are forbidden" }
            val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
            val text = buildString {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (event == XmlPullParser.TEXT) append(parser.text).append(' ')
                    if (event == XmlPullParser.END_TAG && parser.name == "p") append('\n')
                    event = parser.next()
                }
            }
            return ParsedDocument(listOf(ParsedPage(null, text.trim())))
        }
    }

    private fun parseZip(file: File): ParsedDocument = ZipFile(file).use { zip ->
        com.localai.workspace.documents.StructuredDocuments.inspect(zip)
        val pages = mutableListOf<ParsedPage>(); var total = 0;var expandedBytes=0L
        zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
            val extension = entry.name.substringAfterLast('.', "").lowercase()
            pages.add(ParsedPage(null, "Archive entry: ${entry.name} · ${entry.size} bytes"))
            if (extension in setOf("txt", "md", "json", "xml", "yaml", "yml", "py", "kt", "java", "js", "ts", "csv", "xlsx", "docx", "pdf")) {
                val temporary = File.createTempFile("archive-", ".$extension", context.cacheDir)
                try {
                    zip.getInputStream(entry).use { input -> val bytes=com.localai.workspace.documents.StructuredDocuments.bounded(input, 8 * 1024 * 1024);expandedBytes+=bytes.size;require(expandedBytes<=32L*1024*1024) { "Archive exceeds expanded byte limit" };temporary.writeBytes(bytes) }
                    val parsed = when(extension) {
                        "xlsx" -> com.localai.workspace.documents.StructuredDocuments.xlsx(temporary).document()
                        "csv" -> com.localai.workspace.documents.StructuredDocuments.csv(temporary).document()
                        "pdf" -> parsePdf(temporary)
                        "docx" -> parseDocx(temporary)
                        else -> ParsedDocument(listOf(ParsedPage(null, com.localai.workspace.documents.StructuredDocuments.text(temporary.readBytes()).first)))
                    }
                    parsed.pages.forEach { page -> total += page.text.length;require(total <= 1_000_000) { "Archive extracted text exceeds limit" }; pages.add(page.copy(text = "Archive entry: ${entry.name}\n${page.text}")) }
                } finally { temporary.delete() }
            }
        }
        ParsedDocument(pages)
    }

    private fun stripHtml(html: String): String = html
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}

class TextChunker(
    private val maxCharacters: Int = 1_800,
    private val overlapCharacters: Int = 180,
) {
    fun chunk(document: ParsedDocument): List<TextChunk> = buildList {
        document.pages.forEach { page ->
            val source = page.text.trim()
            if (source.isBlank()) return@forEach
            var cursor = 0
            while (cursor < source.length) {
                val hardEnd = minOf(source.length, cursor + maxCharacters)
                val boundary = source.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'), hardEnd - 1)
                    .takeIf { it > cursor + maxCharacters / 2 }
                    ?.plus(1)
                    ?: hardEnd
                val chunkText = source.substring(cursor, boundary).trim()
                if (chunkText.isNotBlank()) {
                    add(
                        TextChunk(
                            pageStart = page.pageNumber,
                            pageEnd = page.pageNumber,
                            charStart = cursor,
                            charEnd = boundary,
                            text = chunkText,
                        ),
                    )
                }
                if (boundary >= source.length) break
                cursor = (boundary - overlapCharacters).coerceAtLeast(cursor + 1)
            }
        }
    }
}

class DocumentIngestionService(
    private val context: Context,
    private val database: WorkspaceDatabase,
    private val documents: DocumentDao,
    private val onIndexed: (suspend (String) -> Unit)? = null,
    private val outputDirectory: File? = null,
) {
    private val parser = DocumentParser(context)
    private val chunker = TextChunker()

    fun isImage(uri: Uri): Boolean = context.contentResolver.getType(uri)?.startsWith("image/") == true ||
        displayName(uri).substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "heic", "heif")

    suspend fun ingest(projectId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        var copiedFile: File? = null
        val result = runCatching {
            check(database.projectDao().get(projectId) != null) { "This workspace was deleted" }
            val displayName = displayName(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val destinationDirectory = (outputDirectory ?: File(context.filesDir, "documents")).apply { mkdirs() }
            val destination = File(destinationDirectory, "${UUID.randomUUID()}_${safeFileName(displayName)}")
            copiedFile = destination
            val hashAndSize = copyWithHash(uri, destination)
            val existing = documents.findByHash(projectId, hashAndSize.second)
            if (existing != null) {
                destination.delete()
                return@runCatching existing.id
            }
            val documentId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            database.withTransaction {
            check(database.projectDao().get(projectId) != null) { "This workspace was deleted during import" }
            documents.insert(
                DocumentEntity(
                    id = documentId,
                    projectId = projectId,
                    displayName = displayName,
                    sourceUri = uri.toString(),
                    localPath = destination.absolutePath,
                    mimeType = mimeType,
                    fileHash = hashAndSize.second,
                    byteSize = hashAndSize.first,
                    importedAt = now,
                    updatedAt = now,
                ),
            )
            }
            try {
                val parsed = parser.parse(destination, mimeType, displayName)
                val chunks = chunker.chunk(parsed)
                if (chunks.isEmpty()) throw UnsupportedDocumentException("No readable text was found. Scanned documents need OCR; add readable pages through the image button.")
                database.withTransaction {
                    // A deletion may commit while parsing is running. Do not resurrect its index.
                    if (documents.get(documentId) == null || database.projectDao().get(projectId) == null) return@withTransaction
                    documents.deleteSegments(documentId)
                    documents.insertSegments(chunks.mapIndexed { index, chunk ->
                        DocumentSegmentEntity(
                            documentId = documentId,
                            segmentIndex = index,
                            pageStart = chunk.pageStart,
                            pageEnd = chunk.pageEnd,
                            charStart = chunk.charStart,
                            charEnd = chunk.charEnd,
                            text = chunk.text,
                            normalizedText = normalize(chunk.text),
                            sectionPath = chunk.sectionPath,
                            contentHash = sha256(chunk.text.toByteArray()),
                        )
                    })
                    documents.updateStatus(documentId, "READY", "READY", parsed.pages.size, null, System.currentTimeMillis())
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) {
                documents.updateStatus(
                    documentId,
                    extraction = "FAILED",
                    indexing = "FAILED",
                    pageCount = null,
                    error = error.message ?: "Document parsing failed",
                    updatedAt = System.currentTimeMillis(),
                )
            }
            if (documents.get(documentId)?.extractionStatus == "READY") onIndexed?.invoke(projectId)
            documentId
        }
        if (result.isFailure) {
            copiedFile?.let { file ->
                withContext(kotlinx.coroutines.NonCancellable) {
                    if (documents.pathReferences(file.absolutePath) == 0 && file.exists() && !file.delete()) {
                        database.pendingDocumentDeletionDao().insertAll(listOf(PendingDocumentDeletionEntity(file.absolutePath)))
                    }
                }
            }
            (result.exceptionOrNull() as? kotlinx.coroutines.CancellationException)?.let { throw it }
        }
        result
    }

    private fun displayName(uri: Uri): String {
        val resolver = context.contentResolver
        val cursor: Cursor? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor.use { if (it != null && it.moveToFirst()) return it.getString(0) }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported document"
    }

    private suspend fun copyWithHash(uri: Uri, destination: File): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected file could not be opened" }
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } >= 0) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    size += read
                    require(size <= com.localai.workspace.documents.StructuredDocuments.MAX_BYTES) { "File exceeds 20 MiB" }
                }
            }
        }
        return size to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private fun normalize(text: String): String = text.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
