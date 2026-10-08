package com.localai.workspace.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET defaultModelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDefaultModel(id: String, modelId: String?, updatedAt: Long)

    @Query("UPDATE projects SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)
    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getForProject(projectId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT imagePath FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :id) AND imagePath IS NOT NULL UNION SELECT audioPath FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :id) AND audioPath IS NOT NULL")
    suspend fun imagesForProject(id: String): List<String>
    @Query("SELECT imagePath FROM messages WHERE conversationId = :id AND imagePath IS NOT NULL UNION SELECT audioPath FROM messages WHERE conversationId = :id AND audioPath IS NOT NULL")
    suspend fun imagesForConversation(id: String): List<String>
    @Query("SELECT COUNT(*) FROM messages WHERE imagePath = :path OR audioPath = :path")
    suspend fun imageReferences(path: String): Int

    @Query("UPDATE messages SET effectiveContent = :content, effectiveModelId = :modelId WHERE id = :id")
    suspend fun setEffectiveTurn(id: String, content: String, modelId: String)
    @Query("UPDATE messages SET generationMetrics = :metrics WHERE id = :id")
    suspend fun setMetrics(id: String, metrics: String)
    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteForConversation(id: String)
    @Query("DELETE FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :projectId)")
    suspend fun deleteForProject(projectId: String)

    @Query("UPDATE messages SET status = 'FAILED', content = CASE WHEN content = '' THEN 'Generation was interrupted before completion. Ready to retry.' ELSE content END WHERE conversationId = :conversationId AND status = 'GENERATING'")
    suspend fun recoverInterruptedGeneration(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, generationMetrics = :metrics WHERE id = :id")
    suspend fun updateGenerated(id: String, content: String, status: String, metrics: String?)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>
}

@Dao
interface ModelDao {
    @Query("UPDATE models SET capabilities = :capabilities, metadataJson = :metadata WHERE id = :id")
    suspend fun updateCapabilityEvidence(id: String, capabilities: String, metadata: String)
    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelEntity)

    @Update
    suspend fun update(model: ModelEntity)

    @Query("UPDATE models SET preferredAccelerator = :accelerator, configuredContext = :contextSize, maxOutputTokens = :maxOutputTokens, temperature = :temperature, topP = :topP, topK = :topK, repeatPenalty = :repeatPenalty, seed = :seed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSettings(
        id: String,
        accelerator: String,
        contextSize: Int?,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        seed: Int,
        updatedAt: Long,
    )

    @Query("UPDATE models SET lastMetrics = :metrics, lastTestedAt = :testedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetrics(id: String, metrics: String, testedAt: Long, updatedAt: Long)

    @Delete
    suspend fun delete(model: ModelEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE projectId = :projectId")
    suspend fun forProject(projectId: String): List<DocumentEntity>

    @Query("DELETE FROM embedding_records WHERE segmentId IN (SELECT id FROM document_segments WHERE documentId IN (SELECT id FROM documents WHERE projectId = :projectId))")
    suspend fun deleteEmbeddingsForProject(projectId: String)

    @Query("DELETE FROM document_segments WHERE documentId IN (SELECT id FROM documents WHERE projectId = :projectId)")
    suspend fun deleteSegmentsForProject(projectId: String)

    @Query("DELETE FROM documents WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Query("SELECT COUNT(*) FROM documents WHERE localPath = :path")
    suspend fun pathReferences(path: String): Int
    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE projectId = :projectId AND fileHash = :hash LIMIT 1")
    suspend fun findByHash(projectId: String, hash: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Query("UPDATE documents SET extractionStatus = :extraction, indexingStatus = :indexing, pageCount = :pageCount, errorMessage = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, extraction: String, indexing: String, pageCount: Int?, error: String?, updatedAt: Long)

    @Query("DELETE FROM document_segments WHERE documentId = :documentId")
    suspend fun deleteSegments(documentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<DocumentSegmentEntity>)

    @Query("SELECT * FROM document_segments WHERE documentId IN (SELECT id FROM documents WHERE projectId = :projectId AND extractionStatus = 'READY' AND indexingStatus = 'READY') ORDER BY documentId, segmentIndex, id LIMIT :limit")
    suspend fun segmentsForProject(projectId: String, limit: Int): List<DocumentSegmentEntity>

    @Query("SELECT s.* FROM document_segments s INNER JOIN documents d ON s.documentId=d.id WHERE d.projectId=:projectId AND d.id IN (:documentIds) AND d.extractionStatus='READY' AND d.indexingStatus='READY' ORDER BY s.documentId, s.segmentIndex, s.id LIMIT :limit")
    suspend fun segmentsForDocuments(projectId: String, documentIds: List<String>, limit: Int): List<DocumentSegmentEntity>

    @Query("SELECT * FROM document_segments WHERE documentId = :documentId ORDER BY segmentIndex")
    suspend fun segmentsForDocument(documentId: String): List<DocumentSegmentEntity>

    @Query("SELECT * FROM document_segments WHERE documentId = :documentId ORDER BY segmentIndex LIMIT :limit")
    suspend fun openingSegments(documentId: String, limit: Int): List<DocumentSegmentEntity>

    @Query("SELECT d.* FROM documents AS d INNER JOIN document_segments AS s ON d.id = s.documentId WHERE s.id = :segmentId LIMIT 1")
    suspend fun documentForSegment(segmentId: Long): DocumentEntity?

    @Query(
        "SELECT s.* FROM document_segments AS s " +
            "INNER JOIN document_segments_fts AS f ON s.id = f.rowid " +
            "INNER JOIN documents AS d ON s.documentId = d.id " +
            "WHERE d.projectId = :projectId AND d.extractionStatus = 'READY' AND d.indexingStatus = 'READY' AND document_segments_fts MATCH :query " +
            "ORDER BY s.documentId, s.segmentIndex, s.id LIMIT :limit",
    )
    suspend fun searchLexical(projectId: String, query: String, limit: Int): List<DocumentSegmentEntity>

    @Query("SELECT s.* FROM document_segments s INNER JOIN document_segments_fts f ON s.id=f.rowid INNER JOIN documents d ON s.documentId=d.id WHERE d.projectId=:projectId AND d.id IN (:documentIds) AND d.extractionStatus='READY' AND d.indexingStatus='READY' AND document_segments_fts MATCH :query ORDER BY s.documentId, s.segmentIndex, s.id LIMIT :limit")
    suspend fun searchLexicalDocuments(projectId: String, documentIds: List<String>, query: String, limit: Int): List<DocumentSegmentEntity>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items WHERE id = :id LIMIT 1") suspend fun get(id: String): MemoryItemEntity?
    @Query("DELETE FROM memory_items WHERE scopeType = 'PROJECT' AND scopeId = :projectId")
    suspend fun deleteForProject(projectId: String)
    @Query("SELECT * FROM memory_items WHERE status = 'ACTIVE' AND (scopeType = 'GLOBAL' OR (scopeType = 'PROJECT' AND scopeId = :projectId)) ORDER BY updatedAt DESC")
    fun observeRelevant(projectId: String): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE status = 'ACTIVE' AND (scopeType = 'GLOBAL' OR (scopeType = 'PROJECT' AND scopeId = :projectId)) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun relevant(projectId: String, limit: Int): List<MemoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryItemEntity)

    @Query("UPDATE memory_items SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long)

    @Query("UPDATE memory_items SET status = 'ARCHIVED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Long)
}

@Dao
interface CitationEvidenceDao {
    @Query("DELETE FROM citation_evidence WHERE conversationId = :id OR messageId IN (SELECT id FROM messages WHERE conversationId = :id)")
    suspend fun deleteForConversation(id: String)
    @Query("DELETE FROM citation_evidence WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :projectId) OR messageId IN (SELECT id FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :projectId))")
    suspend fun deleteForProject(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CitationEvidenceEntity>)

    @Query("SELECT * FROM citation_evidence WHERE messageId = :messageId ORDER BY id")
    fun observeForMessage(messageId: String): Flow<List<CitationEvidenceEntity>>
}

@Dao
interface ToolCallDao {
    @Query("SELECT toolId FROM tool_calls WHERE messageId=:messageId AND status='SUCCESS' ORDER BY toolId")
    suspend fun successfulTools(messageId:String):List<String>

    @Query("UPDATE tool_calls SET status = 'INTERRUPTED', errorCode = 'INTERRUPTED', finishedAt = :time WHERE status = 'RUNNING' AND conversationId = :id")
    suspend fun recoverInterrupted(id: String, time: Long)
    @Query("DELETE FROM tool_calls WHERE conversationId = :id OR messageId IN (SELECT id FROM messages WHERE conversationId = :id)")
    suspend fun deleteForConversation(id: String)
    @Query("DELETE FROM tool_calls WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :projectId) OR messageId IN (SELECT id FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE projectId = :projectId))")
    suspend fun deleteForProject(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: ToolCallEntity)

    @Query("SELECT * FROM tool_calls WHERE conversationId = :conversationId ORDER BY startedAt DESC")
    fun observeForConversation(conversationId: String): Flow<List<ToolCallEntity>>
}

@Dao
interface PendingDocumentDeletionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PendingDocumentDeletionEntity>)
    @Query("SELECT * FROM pending_document_deletions")
    suspend fun pending(): List<PendingDocumentDeletionEntity>
    @Query("DELETE FROM pending_document_deletions WHERE localPath = :path")
    suspend fun acknowledge(path: String)
    @Query("SELECT COUNT(*) FROM pending_document_deletions")
    fun observeCount(): Flow<Int>
}

@Dao interface SemanticVectorDao {
 @Query("SELECT * FROM semantic_vectors WHERE originId = :origin AND modelId = :model LIMIT 1") suspend fun get(origin: String, model: String): SemanticVectorEntity?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(record: SemanticVectorEntity)
 @Query("DELETE FROM semantic_vectors WHERE documentId IN (SELECT id FROM documents WHERE projectId = :projectId) OR memoryId IN (SELECT id FROM memory_items WHERE scopeType = 'PROJECT' AND scopeId = :projectId)") suspend fun deleteForProject(projectId: String)
 @Query("DELETE FROM semantic_vectors WHERE memoryId = :id") suspend fun deleteMemory(id: String)
}
