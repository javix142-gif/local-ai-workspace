package com.localai.workspace.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The authentic exported schema history starts at v8 in this checkout. Exercise the
 * verifiable 8-to-current upgrade instead of fabricating the unavailable v1 schema.
 * Migrations 1-to-7 remain registered in production but need their authentic fixtures.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkspaceDatabase::class.java,
    )

    @Test
    fun v8CitationRowsSurviveThroughTheCurrentSchemaUpgrade() {
        val databaseName = "migration-v8-to-current.db"
        val starting = helper.createDatabase(databaseName, 8)
        starting.execSQL(
            """INSERT INTO citation_evidence (
                id, conversationId, messageId, evidenceId, segmentId, excerpt, documentId,
                sourceLabel, pageStart, pageEnd, charStart, charEnd, retrievalScore, createdAt
            ) VALUES (
                'citation-v8', 'conversation-v8', 'message-v8', 'DOC:segment-14', 14,
                'Original local excerpt', 'document-v8', 'Annual report', 3, 3, 40, 61, 0.875, 1234
            )""".trimIndent(),
        )
        starting.close()

        // Room's @Database is a compile-time schema declaration; its annotation
        // is not guaranteed to be present for runtime reflection on Android.
        val currentVersion = WORKSPACE_DATABASE_VERSION
        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            currentVersion,
            true,
            WorkspaceDatabase.MIGRATION_8_9,
            WorkspaceDatabase.MIGRATION_9_10,
        )
        try {
            assertEquals(currentVersion, migrated.version)
            migrated.query(
                """SELECT id, conversationId, messageId, evidenceId, segmentId, excerpt, documentId,
                    sourceLabel, pageStart, pageEnd, charStart, charEnd, retrievalScore, createdAt,
                    semanticSegmentId, lineStart, lineEnd, cellReferencesJson
                    FROM citation_evidence WHERE id = 'citation-v8'""".trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("citation-v8", cursor.getString(0))
                assertEquals("conversation-v8", cursor.getString(1))
                assertEquals("message-v8", cursor.getString(2))
                assertEquals("DOC:segment-14", cursor.getString(3))
                assertEquals(14, cursor.getInt(4))
                assertEquals("Original local excerpt", cursor.getString(5))
                assertEquals("document-v8", cursor.getString(6))
                assertEquals("Annual report", cursor.getString(7))
                assertEquals(3, cursor.getInt(8))
                assertEquals(3, cursor.getInt(9))
                assertEquals(40, cursor.getInt(10))
                assertEquals(61, cursor.getInt(11))
                assertEquals(0.875, cursor.getDouble(12), 0.000001)
                assertEquals(1234L, cursor.getLong(13))
                assertTrue(cursor.isNull(14))
                assertTrue(cursor.isNull(15))
                assertTrue(cursor.isNull(16))
                assertEquals("[]", cursor.getString(17))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            migrated.close()
        }
    }
}
