package com.localai.workspace.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkspaceDatabase::class.java,
    )

    @Test
    fun v1ModelsAreExtendedWithoutLosingExistingRecords() {
        val database = helper.createDatabase("migration-test.db", 1)
        createV1ModelsTable(database)
        database.execSQL("INSERT INTO models(id, displayName, localPath, sourceUri, fileHash, fileSize, format, architecture, quantization, parameterLabel, declaredContext, runtimeId, compatibilityStatus, compatibilityWarning, capabilities, importedAt) VALUES ('old', 'Legacy GGUF', '/private/old.gguf', NULL, 'hash', 42, 'GGUF', 'llama', 'Q4_K_M', NULL, 4096, 'llama.cpp-android', 'COMPATIBLE', NULL, 'text-generation', 1000)")

        WorkspaceDatabase.MIGRATION_1_2.migrate(database)

        val columns = database.query("PRAGMA table_info(models)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertTrue(columns.containsAll(setOf("family", "sourceType", "importStatus", "bundleStatus", "updatedAt")))
        database.query("SELECT format, runtimeId, sourceType, importStatus, updatedAt FROM models WHERE id = 'old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GGUF", cursor.getString(0))
            assertEquals("llama.cpp-android", cursor.getString(1))
            assertEquals("LOCAL_IMPORT", cursor.getString(2))
            assertEquals("READY", cursor.getString(3))
            assertEquals(1000L, cursor.getLong(4))
        }
        database.close()
    }

    private fun createV1ModelsTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE models (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, localPath TEXT NOT NULL, sourceUri TEXT, fileHash TEXT NOT NULL, fileSize INTEGER NOT NULL, format TEXT NOT NULL, architecture TEXT, quantization TEXT, parameterLabel TEXT, declaredContext INTEGER, runtimeId TEXT NOT NULL, compatibilityStatus TEXT NOT NULL, compatibilityWarning TEXT, capabilities TEXT NOT NULL, importedAt INTEGER NOT NULL)",
        )
    }
}
