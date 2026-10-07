package com.localai.workspace.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val defaultModelId: String? = null,
    val systemInstructions: String? = null,
    val webEnabled: Boolean = false,
    val memoryEnabled: Boolean = true,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "'PROJECT'") val workspaceKind: String = "PROJECT",
)

/** A standalone chat owns an isolated workspace; project chats share their project resources. */
object WorkspaceKind { const val CHAT = "CHAT"; const val PROJECT = "PROJECT" }

@Entity(tableName = "pending_document_deletions")
data class PendingDocumentDeletionEntity(@PrimaryKey val localPath: String)

@Entity(
    tableName = "conversations",
    indices = [Index("projectId"), Index("updatedAt")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelConfigSnapshot: String? = null,
    val summary: String? = null,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val modelId: String? = null,
    val generationMetrics: String? = null,
    val status: String = "COMPLETE",
    // Private application data, never telemetry. Visible content remains unchanged.
    val effectiveContent: String? = null,
    val effectiveModelId: String? = null,
    val imagePath: String? = null,
    val audioPath: String? = null,
)

@Entity(tableName = "models", indices = [Index(value = ["fileHash"], unique = true)])
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val localPath: String,
    val sourceUri: String? = null,
    val fileHash: String,
    val fileSize: Long,
    val format: String,
    val family: String? = null,
    val architecture: String? = null,
    val quantization: String? = null,
    val parameterLabel: String? = null,
    val parameterCount: Long? = null,
    val declaredContext: Int? = null,
    val runtimeId: String,
    val sourceType: String = "LOCAL_IMPORT",
    val auxiliaryFiles: String = "",
    val compatibilityStatus: String,
    val compatibilityWarning: String? = null,
    val capabilities: String = "text-generation",
    val accelerators: String = "CPU",
    val preferredAccelerator: String = "CPU",
    val importStatus: String = "READY",
    val bundleStatus: String = "COMPLETE",
    val metadataJson: String = "",
    val sourceRepository: String? = null,
    val originalFilename: String? = null,
    val backendVersion: String? = null,
    val configuredContext: Int? = null,
    val recommendedContext: Int? = null,
    val maxOutputTokens: Int = 512,
    val temperature: Float = 0.3f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.0f,
    val seed: Int = 0,
    val lastMetrics: String? = null,
    val lastTestedAt: Long? = null,
    val importedAt: Long,
    val updatedAt: Long = importedAt,
)

@Entity(
    tableName = "documents",
    indices = [Index("projectId"), Index(value = ["projectId", "fileHash"], unique = true)],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val displayName: String,
    val sourceUri: String? = null,
    val localPath: String,
    val mimeType: String,
    val fileHash: String,
    val byteSize: Long,
    val pageCount: Int? = null,
    val extractionStatus: String = "EXTRACTING",
    val indexingStatus: String = "PENDING",
    val parserVersion: String = "parser-1",
    val errorMessage: String? = null,
    val importedAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "document_segments",
    indices = [Index("documentId"), Index("contentHash")],
)
data class DocumentSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val segmentIndex: Int,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
    val charStart: Int? = null,
    val charEnd: Int? = null,
    val text: String,
    val normalizedText: String,
    val sectionPath: String? = null,
    val contentHash: String,
)

@Fts4(contentEntity = DocumentSegmentEntity::class)
@Entity(tableName = "document_segments_fts")
data class DocumentSegmentFtsEntity(
    val text: String,
    val normalizedText: String,
)

@Entity(
    tableName = "embedding_records",
    primaryKeys = ["segmentId", "embeddingModelId"],
    indices = [Index("embeddingModelId")],
)
data class EmbeddingRecordEntity(
    val segmentId: Long,
    val embeddingModelId: String,
    val dimensions: Int,
    val vector: ByteArray,
    val createdAt: Long,
)

@Entity(tableName = "memory_items", indices = [Index(value = ["scopeType", "scopeId"]), Index("status")])
data class MemoryItemEntity(
    @PrimaryKey val id: String,
    val scopeType: String,
    val scopeId: String? = null,
    val content: String,
    val sourceType: String,
    val sourceReference: String? = null,
    val relevance: Float = 0.5f,
    val status: String = "ACTIVE",
    val createdAt: Long,
    val updatedAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "'CONTEXT'") val kind: String = "CONTEXT",
)

@Entity(tableName = "citation_evidence", indices = [Index("conversationId"), Index("evidenceId")])
data class CitationEvidenceEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String,
    val evidenceId: String,
    val segmentId: Long,
    val excerpt: String,
    val documentId: String,
    val sourceLabel: String,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
    val charStart: Int? = null,
    val charEnd: Int? = null,
    val retrievalScore: Double,
    val createdAt: Long,
)

@Entity(tableName = "tool_calls", indices = [Index("conversationId"), Index("startedAt")])
data class ToolCallEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String? = null,
    val toolId: String,
    val validatedArgumentsJson: String,
    val permissionDecision: String,
    val confirmationDecision: String? = null,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val errorCode: String? = null,
    val resultJson: String? = null,
)

@Entity(tableName = "semantic_vectors", primaryKeys = ["originId", "modelId"], indices = [Index("modelId"), Index("documentId"), Index("memoryId")])
data class SemanticVectorEntity(
 val originId: String, val modelId: String, val documentId: String? = null, val memoryId: String? = null,
 val sourceHash: String, val dimensions: Int, val version: Int, val vector: ByteArray, val updatedAt: Long,
)
