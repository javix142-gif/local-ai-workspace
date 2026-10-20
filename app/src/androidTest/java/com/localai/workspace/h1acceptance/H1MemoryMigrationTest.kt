package com.localai.workspace.h1acceptance

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localai.workspace.AppGraph
import com.localai.workspace.data.WORKSPACE_DATABASE_VERSION
import com.localai.workspace.data.WorkspaceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class H1MemoryMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, WorkspaceDatabase::class.java)

    @Test fun H1_AT_10_migratesMemoryFromAuthenticWorkspaceSchema8() = runBlocking { migrateMemoryFixture(8) }
    @Test fun H1_AT_10_migratesMemoryFromAuthenticWorkspaceSchema9() = runBlocking { migrateMemoryFixture(9) }
    @Test fun H1_AT_10_migratesMemoryFromAuthenticWorkspaceSchema10() = runBlocking { migrateMemoryFixture(10) }

    private suspend fun migrateMemoryFixture(startVersion: Int) {
        val context = instrumentation.targetContext
        val suffix = "${startVersion}-${UUID.randomUUID()}"
        val databaseName = "h1-memory-v$suffix.db"
        val memoryId = "h1-legacy-memory-v$startVersion"
        val content = "H1_AT_10_SCHEMA_${startVersion}_Ñ · original memory"
        val starting = helper.createDatabase(databaseName, startVersion)
        starting.execSQL(
            """INSERT INTO memory_items (
                id, scopeType, scopeId, content, sourceType, sourceReference, relevance,
                status, createdAt, updatedAt, kind
            ) VALUES (?, 'GLOBAL', NULL, ?, 'USER_APPROVED', 'legacy-ref', 0.75,
                'ACTIVE', 101, 202, 'PREFERENCE')""",
            arrayOf(memoryId, content),
        )
        starting.execSQL(
            """INSERT INTO semantic_vectors (
                originId, modelId, documentId, memoryId, sourceHash, dimensions,
                version, vector, updatedAt
            ) VALUES (?, 'legacy-eg1', NULL, ?, 'legacy-hash', 1, 1, X'01', 202)""",
            arrayOf("legacy-vector-$startVersion", memoryId),
        )
        starting.close()

        val migrations = when (startVersion) {
            8 -> arrayOf(WorkspaceDatabase.MIGRATION_8_9, WorkspaceDatabase.MIGRATION_9_10)
            9 -> arrayOf(WorkspaceDatabase.MIGRATION_9_10)
            10 -> emptyArray()
            else -> error("Unexpected schema fixture")
        }
        helper.runMigrationsAndValidate(databaseName, WORKSPACE_DATABASE_VERSION, true, *migrations).close()

        context.getSharedPreferences("context_foundation_v1", Context.MODE_PRIVATE)
            .edit().remove("legacy_memory_migration_complete_v1").commit()
        val workspace = Room.databaseBuilder(context, WorkspaceDatabase::class.java, databaseName)
            .addMigrations(WorkspaceDatabase.MIGRATION_8_9, WorkspaceDatabase.MIGRATION_9_10)
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val graph = AppGraph(
            context = context,
            databaseOverride = workspace,
            applicationScope = scope,
            nativeScope = scope,
            validationId = "h1-migration-$suffix",
        )
        try {
            val result = graph.contextFoundation.migrateLegacyMemories()
            assertEquals(1, result.activeRecords)
            assertEquals(1, result.legacyRowsRemoved)
            val migrated = graph.contextFoundation.memory.items.first().single { it.sourceId == memoryId }
            assertEquals("ACTIVE", migrated.status)
            assertEquals("PREFERENCE", migrated.kind)
            assertEquals("USER", migrated.scopeType)
            assertEquals("local", migrated.scopeId)
            assertEquals(content, migrated.text)
            assertEquals(101L, migrated.createdAt)
            assertEquals(202L, migrated.updatedAt)
            assertTrue(workspace.memoryDao().allForMigration().isEmpty())
            assertEquals(0, workspace.semanticVectorDao().countMemoryVectors())
        } finally {
            graph.contextFoundation.database.close()
            workspace.close()
            scope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(databaseName)
            context.deleteDatabase("context-memory-h1-migration-$suffix.db")
        }
    }
}
