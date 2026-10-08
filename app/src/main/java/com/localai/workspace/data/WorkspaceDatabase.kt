package com.localai.workspace.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ModelEntity::class,
        DocumentEntity::class,
        DocumentSegmentEntity::class,
        DocumentSegmentFtsEntity::class,
        EmbeddingRecordEntity::class,
        MemoryItemEntity::class,
        CitationEvidenceEntity::class,
        ToolCallEntity::class,
        PendingDocumentDeletionEntity::class, SemanticVectorEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class WorkspaceDatabase : RoomDatabase() {
    abstract fun semanticVectorDao(): SemanticVectorDao
    abstract fun projectDao(): ProjectDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelDao(): ModelDao
    abstract fun documentDao(): DocumentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun citationEvidenceDao(): CitationEvidenceDao
    abstract fun toolCallDao(): ToolCallDao
    abstract fun pendingDocumentDeletionDao(): PendingDocumentDeletionDao

    companion object {
        const val VALIDATION_DATABASE = "device_validation.db"
        fun createValidation(context: Context): WorkspaceDatabase = Room.databaseBuilder(context, WorkspaceDatabase::class.java, VALIDATION_DATABASE).enableMultiInstanceInvalidation().build()
        fun create(context: Context): WorkspaceDatabase = Room.databaseBuilder(
            context,
            WorkspaceDatabase::class.java,
            "local_ai_workspace.db",
        ).enableMultiInstanceInvalidation().addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE messages ADD COLUMN audioPath TEXT") }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memory_items ADD COLUMN kind TEXT NOT NULL DEFAULT 'CONTEXT'")
                database.execSQL("CREATE TABLE IF NOT EXISTS semantic_vectors (originId TEXT NOT NULL, modelId TEXT NOT NULL, documentId TEXT, memoryId TEXT, sourceHash TEXT NOT NULL, dimensions INTEGER NOT NULL, version INTEGER NOT NULL, vector BLOB NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(originId, modelId))")
                listOf("modelId", "documentId", "memoryId").forEach { database.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_vectors_$it ON semantic_vectors ($it)") }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE tool_calls ADD COLUMN resultJson TEXT") }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN imagePath TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN effectiveContent TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN effectiveModelId TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing project IDs, chats, models, files and preferences are unchanged.
                database.execSQL("ALTER TABLE projects ADD COLUMN workspaceKind TEXT NOT NULL DEFAULT 'PROJECT'")
                database.execSQL("CREATE TABLE IF NOT EXISTS pending_document_deletions (localPath TEXT NOT NULL PRIMARY KEY)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE models ADD COLUMN family TEXT")
                database.execSQL("ALTER TABLE models ADD COLUMN parameterCount INTEGER")
                database.execSQL("ALTER TABLE models ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'LOCAL_IMPORT'")
                database.execSQL("ALTER TABLE models ADD COLUMN auxiliaryFiles TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE models ADD COLUMN accelerators TEXT NOT NULL DEFAULT 'CPU'")
                database.execSQL("ALTER TABLE models ADD COLUMN preferredAccelerator TEXT NOT NULL DEFAULT 'CPU'")
                database.execSQL("ALTER TABLE models ADD COLUMN importStatus TEXT NOT NULL DEFAULT 'READY'")
                database.execSQL("ALTER TABLE models ADD COLUMN bundleStatus TEXT NOT NULL DEFAULT 'COMPLETE'")
                database.execSQL("ALTER TABLE models ADD COLUMN metadataJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE models ADD COLUMN sourceRepository TEXT")
                database.execSQL("ALTER TABLE models ADD COLUMN originalFilename TEXT")
                database.execSQL("ALTER TABLE models ADD COLUMN backendVersion TEXT")
                database.execSQL("ALTER TABLE models ADD COLUMN configuredContext INTEGER")
                database.execSQL("ALTER TABLE models ADD COLUMN recommendedContext INTEGER")
                database.execSQL("ALTER TABLE models ADD COLUMN maxOutputTokens INTEGER NOT NULL DEFAULT 512")
                database.execSQL("ALTER TABLE models ADD COLUMN temperature REAL NOT NULL DEFAULT 0.3")
                database.execSQL("ALTER TABLE models ADD COLUMN topP REAL NOT NULL DEFAULT 0.95")
                database.execSQL("ALTER TABLE models ADD COLUMN topK INTEGER NOT NULL DEFAULT 40")
                database.execSQL("ALTER TABLE models ADD COLUMN repeatPenalty REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE models ADD COLUMN seed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE models ADD COLUMN lastMetrics TEXT")
                database.execSQL("ALTER TABLE models ADD COLUMN lastTestedAt INTEGER")
                database.execSQL("ALTER TABLE models ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                // Version 1 only represented GGUF/llama.cpp models. Make that
                // legacy assumption explicit while preserving the binary path/hash.
                database.execSQL("UPDATE models SET format = 'GGUF', runtimeId = 'llama.cpp-android', sourceType = 'LOCAL_IMPORT', importStatus = 'READY', bundleStatus = 'COMPLETE'")
                database.execSQL("UPDATE models SET updatedAt = importedAt, originalFilename = displayName WHERE updatedAt = 0")
            }
        }
    }
}
