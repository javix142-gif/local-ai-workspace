package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Uses the checked-in Room v8 schema and verifies old rows survive the citation provenance upgrade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkspaceCitationEvidenceMigrationTest {
    @Test fun version8CitationRowsSurviveAndV2CodeLineEvidenceCanBeStored() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "workspace-v8-citation-${UUID.randomUUID()}.db"
        val template = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        val currentDdl = template.openHelper.readableDatabase.query(
            "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata') AND name NOT LIKE 'document_segments_fts_%' " +
                "ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 ELSE 2 END",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
        }
        template.close()

        val schemaFile = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/com.localai.workspace.data.WorkspaceDatabase/8.json") }
            .firstOrNull { it.isFile } ?: error("Checked-in WorkspaceDatabase/8.json is unavailable")
        val v8Citation = JSONObject(schemaFile.readText()).getJSONObject("database").getJSONArray("entities")
            .let { entities -> (0 until entities.length()).map { index -> entities.getJSONObject(index) } }
            .single { it.getString("tableName") == "citation_evidence" }
        val tablePlaceholder = "`\${TABLE_NAME}`"
        val v8CitationTable = v8Citation.getString("createSql").replace(tablePlaceholder, "`citation_evidence`")
        val v8CitationIndexes = v8Citation.getJSONArray("indices").let { indices ->
            (0 until indices.length()).map(indices::getJSONObject).map {
                it.getString("createSql").replace(tablePlaceholder, "`citation_evidence`")
            }
        }
        val statements = currentDdl.filterNot { (_, objectName, _) ->
            objectName == "citation_evidence" || objectName.startsWith("index_citation_evidence_")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        statements.filter { it.first == "table" }.forEach { db.execSQL(it.third) }
                        db.execSQL(v8CitationTable)
                        statements.filter { it.first == "index" }.forEach { db.execSQL(it.third) }
                        v8CitationIndexes.forEach { statement -> db.execSQL(statement) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Fixture must be created at Room v8")
                }).build(),
        )
        try {
            helper.writableDatabase.execSQL(
                "INSERT INTO citation_evidence (id, conversationId, messageId, evidenceId, segmentId, excerpt, " +
                    "documentId, sourceLabel, pageStart, pageEnd, charStart, charEnd, retrievalScore, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("legacy-citation", "chat-1", "message-1", "LCL-AB12", 77L, "saved excerpt", "doc-1", "notes.txt", 3, 3, 10, 22, 0.9, 100L),
            )
        } finally {
            helper.close()
        }

        var migrated: WorkspaceDatabase? = null
        try {
            migrated = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name)
                .addMigrations(
                    WorkspaceDatabase.MIGRATION_1_2, WorkspaceDatabase.MIGRATION_2_3,
                    WorkspaceDatabase.MIGRATION_3_4, WorkspaceDatabase.MIGRATION_4_5,
                    WorkspaceDatabase.MIGRATION_5_6, WorkspaceDatabase.MIGRATION_6_7,
                    WorkspaceDatabase.MIGRATION_7_8, WorkspaceDatabase.MIGRATION_8_9,
                ).build()
            assertEquals(9, migrated.openHelper.readableDatabase.version)
            val oldCitation = migrated.citationEvidenceDao().observeForMessage("message-1").first().single()
            assertEquals("legacy-citation", oldCitation.id)
            assertEquals(77L, oldCitation.segmentId)
            assertEquals("saved excerpt", oldCitation.excerpt)
            assertEquals("doc-1", oldCitation.documentId)
            assertEquals(3, oldCitation.pageStart)
            assertNull(oldCitation.semanticSegmentId)
            assertNull(oldCitation.lineStart)
            assertNull(oldCitation.lineEnd)

            val codeCitation = CitationEvidenceEntity(
                id = "code-citation", conversationId = "chat-1", messageId = "message-2",
                evidenceId = "SV2-0123456789ABCDEF", segmentId = null, excerpt = "fun answer() = 42",
                documentId = "code-doc", sourceLabel = "src/Answer.kt", retrievalScore = 0.8,
                createdAt = 101L, semanticSegmentId = "index:code-doc:0", lineStart = 4, lineEnd = 6,
            )
            migrated.citationEvidenceDao().insertAll(listOf(codeCitation))
            assertEquals(codeCitation, migrated.citationEvidenceDao().observeForMessage("message-2").first().single())
            assertEquals(
                listOf("legacy-citation", "code-citation"),
                migrated.citationEvidenceDao().observeForConversation("chat-1").first().map { it.id },
            )
        } finally {
            migrated?.close()
            context.deleteDatabase(name)
        }
    }
}
