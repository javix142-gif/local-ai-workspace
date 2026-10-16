package com.localai.workspace.context

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.localai.workspace.semantic.v2.EmbeddingResult
import com.localai.workspace.semantic.v2.EmbeddingSpaceKey
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import com.localai.workspace.semantic.v2.SemanticVectors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MemoryBundleV1Test {
    @Test fun parsesCanonicalFolderAndRetainsTrustProvenanceAndDates() {
        val preview = MemoryBundleV1.read(ByteArrayInputStream(bundle(
            records = listOf(
                """{"id":"fact-1","type":"FACT","content":"Use Spanish","trust":"USER_DECLARED","provenance":"chat turn 2","scope":{"type":"USER"},"confidence":0.9,"createdAt":"2026-10-01","status":"ACTIVE"}""",
                """{"id":"fact-2","type":"PREFERENCE","content":"api_key=example-secret","trust":"INFERRED","provenance":"external","scope":"USER","confidence":0.4}""",
            ), rooted = true,
        )))

        assertTrue(preview.bundleId.matches(Regex("[0-9a-f]{64}")))
        assertEquals("Portable profile", preview.profile)
        assertEquals("Local source", preview.sourceLabel)
        assertEquals(3, preview.candidates.size)
        val declared = preview.candidates.first()
        assertEquals(MemoryKind.FACT, declared.kind)
        assertEquals("USER_DECLARED", declared.trust)
        assertEquals("chat turn 2", declared.provenance)
        assertEquals("2026-10-01", declared.originalCreatedAt)
        assertEquals("ACTIVE", declared.originalStatus)
        assertFalse(declared.sensitiveCandidate)
        assertTrue(preview.candidates.single { it.externalId == "fact-2" }.sensitiveCandidate)
    }

    @Test fun rejectsBadIntegrityUnknownPathsUnsupportedVersionAndMalformedJsonl() {
        assertInvalid("FILE_INTEGRITY_MISMATCH", bundle(hashOverride = "0".repeat(64)))
        assertInvalid("UNSAFE_OR_UNKNOWN_PATH", bundle(extraEntries = mapOf("AWS_MEMORY_IMPORT/../escape.txt" to byteArrayOf(1))))
        assertInvalid("UNSUPPORTED_SCHEMA", bundle(schemaVersion = 2))
        assertInvalid("INVALID_JSONL_RECORD", bundle(records = listOf("not-json")))
    }

    @Test fun rejectsInvalidUtf8DuplicateIdsAndIncompatiblePackage() {
        assertInvalid("INVALID_RECORD_UTF8", bundle(facts = byteArrayOf(0xc3.toByte(), 0x28)))
        assertInvalid("DUPLICATE_RECORD_ID", bundle(records = listOf(
            """{"id":"same","content":"first","type":"FACT"}""",
            """{"id":"same","content":"second","type":"FACT"}""",
        )))
        assertInvalid("INCOMPATIBLE_PACKAGE", bundle(compatibility = listOf("other")))
    }

    @Test fun rejectsHighlyCompressedInputBeforeParsingRecords() {
        assertInvalid("EXPANSION_LIMIT_EXCEEDED", bundle(facts = ByteArray(2 * 1024 * 1024) { 'x'.code.toByte() }))
    }

    @Test fun promptCarriesRequestedPurposeAndImportContract() {
        val prompt = MemoryBundleV1.copyPrompt()
        assertTrue(prompt.startsWith("Analiza todo el contexto personal que tienes disponible sobre mí"))
        assertTrue(prompt.contains("AWS_MEMORY_IMPORT/"))
        assertTrue(prompt.contains("sha256"))
        assertTrue(prompt.contains("PENDING"))
        assertTrue(prompt.contains("No sobrescribas recuerdos existentes"))
    }

    @Test fun exportedActiveMemoryRoundTripsMetadataThroughCanonicalZipRoot() {
        val memory = MemoryRecord(
            id = "memory-1", kind = "PREFERENCE", scopeType = "USER", scopeId = "local",
            title = "Language", text = "Responde en español", confidence = .9,
            createdAt = 1_760_000_000_000, updatedAt = 1_760_000_001_000,
            sourceType = "USER_EXPLICIT", contentHash = MemoryContent.hash("Responde en español", MemoryKind.PREFERENCE),
            structuredPayloadJson = """{"trust":"USER_DECLARED","reviewNote":"confirmed"}""",
            subject = "language", predicate = "response-language", objectValue = "Spanish",
        )
        val archive = MemoryBundleV1.write(listOf(memory), exportedAt = "2026-10-16T12:00:00Z")
        val firstEntry = java.util.zip.ZipInputStream(ByteArrayInputStream(archive)).use { it.nextEntry?.name }
        assertEquals("AWS_MEMORY_IMPORT/", firstEntry)

        val preview = MemoryBundleV1.read(ByteArrayInputStream(archive))
        val candidate = preview.candidates.single()
        assertEquals("Local AI Workspace", preview.sourceLabel)
        assertEquals(memory.id, candidate.externalId)
        assertEquals(MemoryKind.PREFERENCE, candidate.kind)
        assertEquals(memory.text, candidate.content)
        assertEquals(memory.title, candidate.originalTitle)
        assertEquals("USER_DECLARED", candidate.trust)
        assertEquals("ACTIVE", candidate.originalStatus)
        assertEquals(.9, candidate.confidence!!, 0.0)
        assertEquals("language", candidate.subject)
        assertEquals("Spanish", candidate.objectValue)
        assertTrue(candidate.originalScope.contains("USER"))
        val origin = JsonParser.parseString(requireNotNull(candidate.originalMetadataJson)).asJsonObject
        assertEquals("confirmed", origin.get("reviewNote").asString)
        assertEquals("USER_EXPLICIT", origin.get("sourceType").asString)
        assertTrue(preview.metadata.contains("\"vectorsIncluded\":false"))
    }

    @Test fun exportRejectsPendingAndHeuristicallySensitiveRecords() {
        val approved = testRecord("safe", "A harmless preference")
        assertWriteInvalid("UNAPPROVED_MEMORY_EXPORT", approved.copy(status = "PENDING"))
        assertWriteInvalid("SENSITIVE_MEMORY_REQUIRES_REVIEW", testRecord("secret", "password=example"))
    }

    private fun assertWriteInvalid(reason: String, record: MemoryRecord) {
        try {
            MemoryBundleV1.write(listOf(record))
            fail("Expected $reason")
        } catch (expected: MemoryBundleV1.InvalidPackage) {
            assertEquals(reason, expected.reason)
        }
    }

    private fun testRecord(id: String, text: String) = MemoryRecord(
        id = id, kind = "FACT", scopeType = "USER", scopeId = "local", title = null, text = text,
        contentHash = MemoryContent.hash(text, MemoryKind.FACT), createdAt = 1_760_000_000_000, updatedAt = 1_760_000_000_000,
    )

    private fun assertInvalid(reason: String, bytes: ByteArray) {
        try {
            MemoryBundleV1.read(ByteArrayInputStream(bytes))
            fail("Expected $reason")
        } catch (expected: MemoryBundleV1.InvalidPackage) {
            assertEquals(reason, expected.reason)
        }
    }

    private fun bundle(
        records: List<String> = listOf("""{"id":"fact-1","type":"FACT","content":"Remember this","trust":"USER_DECLARED"}"""),
        rooted: Boolean = true,
        facts: ByteArray? = null,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        hashOverride: String? = null,
        schemaVersion: Int = 1,
        compatibility: List<String> = listOf(MemoryBundleV1.SCHEMA),
    ): ByteArray {
        val files = linkedMapOf(
            "metadata.json" to "{}".toByteArray(),
            "profile.md" to "Portable profile".toByteArray(),
            "facts.jsonl" to (facts ?: records.joinToString("\n", postfix = "\n").toByteArray()),
        )
        val manifest = JsonObject().apply {
            addProperty("schema", MemoryBundleV1.SCHEMA)
            addProperty("schemaVersion", schemaVersion)
            addProperty("packageType", "PERSONAL_MEMORY")
            addProperty("exportedAt", "2026-10-16T12:00:00Z")
            add("source", JsonObject().apply { addProperty("label", "Local source") })
            add("compatibility", JsonArray().apply { compatibility.forEach { add(it) } })
            addProperty("recordCount", if (facts == null) records.size else 0)
            add("files", JsonArray().apply {
                files.forEach { (path, bytes) ->
                    add(JsonObject().apply {
                        addProperty("path", path)
                        addProperty("bytes", bytes.size)
                        addProperty("sha256", hashOverride ?: sha256(bytes))
                    })
                }
            })
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val root = if (rooted) "AWS_MEMORY_IMPORT/" else ""
            if (rooted) { zip.putNextEntry(ZipEntry(root)); zip.closeEntry() }
            zip.putNextEntry(ZipEntry(root + "manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
            files.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(root + path)); zip.write(bytes); zip.closeEntry() }
            extraEntries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    fun validBundleForImport(content: String = "Remember this"): ByteArray {
        val record = JsonObject().apply {
            addProperty("id", "fact-1"); addProperty("type", "FACT"); addProperty("content", content); addProperty("trust", "USER_DECLARED")
        }
        return bundle(records = listOf(record.toString()))
    }

    fun bundleForImport(vararg records: JsonObject): ByteArray = bundle(records = records.map(JsonObject::toString))

    fun record(id: String, content: String, subject: String? = null, predicate: String? = null, objectValue: String? = null): JsonObject = JsonObject().apply {
        addProperty("id", id); addProperty("type", "FACT"); addProperty("content", content); addProperty("trust", "USER_DECLARED")
        subject?.let { addProperty("subject", it) }; predicate?.let { addProperty("predicate", it) }; objectValue?.let { addProperty("object", it) }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryBundleImportTest {
    private lateinit var database: MemoryContextDatabase
    private lateinit var manager: MemoryManager
    private var embeddingCalls = 0
    private val space = EmbeddingSpaceKey("memory-import-test", "EG2", "a".repeat(64), "v1", 768)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MemoryContextDatabase::class.java).build()
        manager = MemoryManager(database) { _, _ ->
            embeddingCalls++
            EmbeddingResult(space, SemanticVectors.normalize(FloatArray(768) { 1f }), 1)
        }
    }

    @After fun tearDown() = database.close()

    @Test fun importIsPendingScopedIdempotentAndIndexedOnlyAfterApproval() = runBlocking {
        val preview = MemoryBundleV1.read(ByteArrayInputStream(MemoryBundleV1Test().validBundleForImport()))
        val key = preview.candidates.single { it.category == "facts" }.key
        val access = ScopeAccess()

        val first = manager.importBundlePending(preview, setOf(key))
        assertEquals(1, first.imported)
        assertEquals(0, first.duplicates)
        assertEquals(0, embeddingCalls)
        val imported = requireNotNull(manager.get(first.pendingIds.single()))
        assertEquals("PENDING", imported.status)
        assertEquals("USER", imported.scopeType)
        assertEquals("local", imported.scopeId)
        assertEquals("INDEX_PENDING", imported.indexState)
        assertTrue(imported.structuredPayloadJson!!.contains("\"instructionsAreData\":true"))
        assertTrue(manager.lookup(access).none { it.id == imported.id })

        val second = manager.importBundlePending(preview, setOf(key))
        assertEquals(0, second.imported)
        assertEquals(1, second.duplicates)
        assertEquals(1, database.dao().observe().first().size)

        manager.approve(imported.id)
        assertEquals(1, manager.lookup(access).count { it.id == imported.id })
        manager.recover()
        assertEquals(1, embeddingCalls)
        assertEquals("INDEXED", manager.get(imported.id)?.indexState)
    }

    @Test fun identicalProjectMemoryDoesNotSuppressPersonalImport() = runBlocking {
        val text = "A local preference"
        manager.create(text, SemanticScope(ScopeType.PROJECT, "another-project"), MemoryKind.FACT)
        val preview = MemoryBundleV1.read(ByteArrayInputStream(MemoryBundleV1Test().validBundleForImport(text)))
        val key = preview.candidates.single { it.category == "facts" }.key
        val result = manager.importBundlePending(preview, setOf(key))
        assertEquals(1, result.imported)
        assertEquals("USER", manager.get(result.pendingIds.single())?.scopeType)
        assertEquals(0, embeddingCalls)
    }

    @Test fun sensitiveCandidateAndUnknownSelectionCannotBeImported() = runBlocking {
        val preview = MemoryBundleV1.read(ByteArrayInputStream(MemoryBundleV1Test().validBundleForImport("password=example")))
        val candidate = preview.candidates.single { it.category == "facts" }
        try { manager.importBundlePending(preview, setOf(candidate.key)); fail("sensitive content must be excluded") }
        catch (expected: IllegalArgumentException) { assertEquals("SENSITIVE_MEMORY_REQUIRES_REMOVAL", expected.message) }
        try { manager.importBundlePending(preview, setOf("not-a-candidate")); fail("unknown selection must be rejected") }
        catch (expected: IllegalArgumentException) { assertEquals("UNKNOWN_MEMORY_SELECTION", expected.message) }
    }

    @Test fun exactDuplicatesAndStructuredConflictsAreReportedWithoutOverwriting() = runBlocking {
        manager.create("Preferred language is English", SemanticScope(ScopeType.USER, "local"), MemoryKind.FACT,
            subject = "language", predicate = "preferred", objectValue = "English")
        val preview = MemoryBundleV1.read(ByteArrayInputStream(MemoryBundleV1Test().bundleForImport(
            MemoryBundleV1Test().record("same", "Preferred language is English", "language", "preferred", "English"),
            MemoryBundleV1Test().record("new", "Preferred language is Spanish", "language", "preferred", "Spanish"),
        )))
        val reviews = manager.inspectBundle(preview)
        assertTrue(reviews.getValue("facts.jsonl:same").exactDuplicate)
        assertTrue(reviews.getValue("facts.jsonl:new").possibleContradiction)
        val selected = preview.candidates.filter { it.category == "facts" }.map { it.key }.toSet()
        val imported = manager.importBundlePending(preview, selected)
        assertEquals(1, imported.imported)
        assertEquals(1, imported.duplicates)
        assertEquals(2, database.dao().observe().first().size)
    }

    @Test fun exportIncludesOnlyApprovedMemoriesReadableInSelectedScope() = runBlocking {
        val user = manager.create("User preference", SemanticScope(ScopeType.USER, "local"), MemoryKind.PREFERENCE)
        val currentProject = manager.create("Current project decision", SemanticScope(ScopeType.PROJECT, "project-a"), MemoryKind.DECISION)
        manager.create("Other project decision", SemanticScope(ScopeType.PROJECT, "project-b"), MemoryKind.DECISION)
        manager.create("Pending note", SemanticScope(ScopeType.USER, "local"), MemoryKind.FACT, pending = true)

        val result = manager.exportBundle(ScopeAccess(projectId = "project-a"))
        val preview = MemoryBundleV1.read(ByteArrayInputStream(result.archive))
        assertEquals(2, result.exportedRecords)
        assertEquals(setOf(user.id, currentProject.id), preview.candidates.map { it.externalId }.toSet())
        assertEquals(setOf("USER", "PROJECT"), preview.candidates.map { JsonParser.parseString(it.originalScope).asJsonObject.get("type").asString }.toSet())
        assertEquals(0, embeddingCalls)
    }

    @Test fun importedBundleRemainsPendingAndPreservesTitleAndOriginOnReexport() = runBlocking {
        manager.create(
            "A project preference", SemanticScope(ScopeType.PROJECT, "source-project"), MemoryKind.PREFERENCE,
            title = "Writing style", structuredPayloadJson = """{"trust":"USER_DECLARED","originMarker":"source-1"}""",
        )
        val sourceBundle = manager.exportBundle(ScopeAccess(projectId = "source-project"))
        val sourceCandidate = MemoryBundleV1.read(ByteArrayInputStream(sourceBundle.archive)).candidates.single()
        assertEquals("Writing style", sourceCandidate.originalTitle)
        val imported = manager.importBundlePending(
            MemoryBundleV1.read(ByteArrayInputStream(sourceBundle.archive)), setOf(sourceCandidate.key),
        )
        val pending = requireNotNull(manager.get(imported.pendingIds.single()))
        assertEquals("PENDING", pending.status)
        assertEquals("Writing style", pending.title)
        assertEquals(0, embeddingCalls)
        assertTrue(manager.exportBundle(ScopeAccess()).archive.let {
            MemoryBundleV1.read(ByteArrayInputStream(it)).candidates.none { candidate -> candidate.externalId == pending.id }
        })

        manager.approve(pending.id)
        val reexported = manager.exportBundle(ScopeAccess())
        val roundTripped = MemoryBundleV1.read(ByteArrayInputStream(reexported.archive)).candidates.single { it.externalId == pending.id }
        assertEquals("Writing style", roundTripped.originalTitle)
        val origin = JsonParser.parseString(requireNotNull(roundTripped.originalMetadataJson)).asJsonObject
        assertEquals("source-1", origin.getAsJsonObject("originMetadata").get("originMarker").asString)
    }
}
