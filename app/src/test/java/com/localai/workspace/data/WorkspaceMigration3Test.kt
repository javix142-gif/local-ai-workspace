package com.localai.workspace.data

import android.app.Application
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** Opens a real version-2 SQLite file through Room's version-3 migration AND schema validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class WorkspaceMigration3Test {
    @Test fun version2WorkspacesChatsAndModelsSurviveRoomMigrationAndDeletedDefaultStaysDeleted() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "workspace-v2-${UUID.randomUUID()}.db"
        val template = Room.inMemoryDatabaseBuilder(context, WorkspaceDatabase::class.java).build()
        val oldProject = ProjectEntity(WorkspaceRepository.DEFAULT_PROJECT_ID, "Legacy workspace", 10, 11,
            defaultModelId = "model", systemInstructions = "Saved instructions")
        val oldConversation = ConversationEntity("legacy-chat", oldProject.id, "Saved title", 10, 11)
        val oldMessage = MessageEntity("legacy-message", oldConversation.id, "ASSISTANT", "Saved answer", 12, status = "COMPLETE")
        val oldModel = ModelEntity("model", "Qwen", "/private/model.litertlm", fileHash = "a".repeat(64), fileSize = 42,
            format = "LITERT_LM", runtimeId = "litert-lm-android", compatibilityStatus = "COMPATIBLE", importedAt = 10,
            maxOutputTokens = 256, lastMetrics = "saved benchmark")
        template.projectDao().upsert(oldProject); template.conversationDao().upsert(oldConversation)
        template.messageDao().insert(oldMessage); template.modelDao().insert(oldModel)
        val source = template.openHelper.readableDatabase
        val ddl = source.query("SELECT name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata', 'pending_document_deletions') ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 ELSE 2 END").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    if (table == "semantic_vectors" || table.startsWith("index_semantic_vectors_") || table.startsWith("document_segments_fts_")) continue // FTS creates its own shadow tables.
                    val sql = cursor.getString(1)
                    add(if (table == "projects") sql.replace(Regex(", [`\"]?workspaceKind[`\"]? TEXT NOT NULL DEFAULT 'PROJECT'"), "").also {
                        assertFalse("Version2 fixture must omit the new column", it.contains("workspaceKind"))
                    } else if (table == "messages") sql.replace(Regex(", [`\"]?effectiveContent[`\"]? TEXT"), "")
                        .replace(Regex(", [`\"]?effectiveModelId[`\"]? TEXT"), "").replace(Regex(", [`\"]?imagePath[`\"]? TEXT"), "") .replace(Regex(", [`\"]?audioPath[`\"]? TEXT"), "") else if (table == "memory_items") sql.replace(Regex(", [`\"]?kind[`\"]? TEXT NOT NULL DEFAULT 'CONTEXT'"), "") else if (table == "tool_calls") sql.replace(Regex(", [`\"]?resultJson[`\"]? TEXT"), "") else sql)
                }
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) { ddl.forEach { db.execSQL(it) } }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture is version2")
            }).build())
        val old = helper.writableDatabase
        for (table in listOf("projects", "conversations", "messages", "models")) copyRows(source, old, table)
        helper.close(); template.close()

        var migrated: WorkspaceDatabase? = null
        try {
            migrated = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).addMigrations(WorkspaceDatabase.MIGRATION_2_3, WorkspaceDatabase.MIGRATION_3_4, WorkspaceDatabase.MIGRATION_4_5, WorkspaceDatabase.MIGRATION_5_6, WorkspaceDatabase.MIGRATION_6_7, WorkspaceDatabase.MIGRATION_7_8).build()
            assertEquals(8, migrated.openHelper.readableDatabase.version) // Forces real migration + Room validation.
            assertEquals(oldProject, migrated.projectDao().get(oldProject.id))
            assertEquals(oldConversation, migrated.conversationDao().get(oldConversation.id))
            assertEquals(oldMessage, migrated.messageDao().recent(oldConversation.id, 10).single())
            assertEquals(oldModel, migrated.modelDao().get(oldModel.id))
            assertTrue(migrated.pendingDocumentDeletionDao().pending().isEmpty())
            WorkspaceRepository(migrated).deleteWorkspace(oldProject.id)
            migrated.close()
            migrated = Room.databaseBuilder(context, WorkspaceDatabase::class.java, name).addMigrations(WorkspaceDatabase.MIGRATION_2_3, WorkspaceDatabase.MIGRATION_3_4, WorkspaceDatabase.MIGRATION_4_5, WorkspaceDatabase.MIGRATION_5_6, WorkspaceDatabase.MIGRATION_6_7, WorkspaceDatabase.MIGRATION_7_8).build()
            assertNull(migrated.projectDao().get(oldProject.id))
            assertEquals(oldModel, migrated.modelDao().get(oldModel.id))
        } finally { migrated?.close(); context.deleteDatabase(name) }
    }

    private fun copyRows(source: SupportSQLiteDatabase, target: SupportSQLiteDatabase, table: String) {
        source.query("SELECT * FROM $table").use { cursor ->
            val indexes = (0 until cursor.columnCount).filter { cursor.getColumnName(it) !in setOf("workspaceKind", "effectiveContent", "effectiveModelId", "imagePath", "audioPath") }
            val names = indexes.joinToString(",") { "`${cursor.getColumnName(it)}`" }
            val placeholders = indexes.joinToString(",") { "?" }
            while (cursor.moveToNext()) {
                val args = indexes.map { index -> when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                    else -> cursor.getString(index)
                } }.toTypedArray()
                target.execSQL("INSERT INTO $table ($names) VALUES ($placeholders)", args)
            }
        }
    }
}
