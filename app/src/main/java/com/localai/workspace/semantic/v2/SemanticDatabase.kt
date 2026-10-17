package com.localai.workspace.semantic.v2

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "semantic_models_v2")
data class SemanticModelRecord(@PrimaryKey val id: String, val displayName: String, val family: String, val fileName: String, val privatePath: String, val fileSize: Long, val sha256: String, val format: String, val provider: String, val nativeDimension: Int, val supportedDimensions: String, val declaredModalities: String, val verifiedModalities: String, val runtime: String, val importedAt: Long, val validationStatus: String, val modelVersion: String = "unspecified", val runtimeModalities: String = "", val validatedDimensions: String = "768,256")
@Entity(tableName = "semantic_sources", indices = [Index("scopeKey")])
data class SemanticSourceRecord(@PrimaryKey val id: String, val scopeKey: String, val sourceType: String, val displayName: String, val mimeType: String, val reference: String, val size: Long, val modifiedAt: Long, val contentHash: String, val metadataJson: String = "{}", val permissionState: String = "AUTHORIZED", val indexState: String = "NEEDS_REINDEX")
@Entity(tableName = "semantic_segments", indices = [Index("sourceId")])
data class SemanticSegmentRecord(@PrimaryKey val id: String, val sourceId: String, val modality: String, val textContent: String?, val title: String?, val page: Int?, val lineStart: Int?, val lineEnd: Int?, val startMs: Long?, val endMs: Long?, val contentHash: String, val metadataJson: String = "{}", val legacySegmentId: Long? = null)
data class SemanticEmbeddingRecord(val indexId: String, val segmentId: String, val embeddingSpaceKey: String, val provider: String, val modelId: String, val modelHash: String, val dimension: Int, val normalized: Boolean, val taskType: String, val contentHash: String, val vectorFormat: String, val vector: ByteArray, val createdAt: Long)
/** Immutable index links share one payload per exact input / space / task. */
@Entity(tableName = "semantic_embeddings_v2", primaryKeys = ["indexId", "segmentId", "embeddingSpaceKey", "taskType", "contentHash"], indices = [Index(value = ["indexId", "contentHash", "taskType"]), Index("embeddingSpaceKey")])
data class SemanticEmbeddingLink(val indexId: String, val segmentId: String, val embeddingSpaceKey: String, val provider: String, val modelId: String, val modelHash: String, val dimension: Int, val normalized: Boolean, val taskType: String, val contentHash: String, val vectorFormat: String, val createdAt: Long)
@Entity(tableName = "semantic_vector_payloads_v2", primaryKeys = ["embeddingSpaceKey", "taskType", "contentHash"])
data class SemanticVectorPayload(val embeddingSpaceKey: String, val taskType: String, val contentHash: String, val vector: ByteArray)
@Entity(tableName = "semantic_index_jobs", indices = [Index("scopeKey")])
data class SemanticIndexJob(@PrimaryKey val id: String, val scopeKey: String, val spaceKey: String, val state: String, val sources: Int, val segments: Int, val embeddings: Int, val startedAt: Long, val updatedAt: Long, val errorCode: String?)
@Entity(tableName = "semantic_active_indexes")
data class SemanticActiveIndex(@PrimaryKey val scopeKey: String, val indexId: String, val spaceKey: String)
data class IndexedSegment(@Embedded val segment: SemanticSegmentRecord, val sourceName: String, val vector: ByteArray, val dimension: Int, val spaceKey: String, val modelHash: String, val modelId: String)
@Dao interface SemanticDao {
    @Query("UPDATE semantic_index_jobs SET state='CANCELLED',errorCode='PROCESS_RESTART_RECOVERABLE',updatedAt=:recoveredAt WHERE state='INDEXING'") suspend fun recoverInterruptedJobs(recoveredAt: Long): Int
    @Query("SELECT * FROM semantic_models_v2 ORDER BY importedAt DESC") fun observeModels(): Flow<List<SemanticModelRecord>>
    @Query("SELECT * FROM semantic_models_v2 WHERE id=:id") suspend fun model(id: String): SemanticModelRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun model(value: SemanticModelRecord)
    @Query("SELECT COUNT(*) FROM semantic_embeddings_v2 WHERE modelId=:id") suspend fun references(id: String): Int
    @Query("DELETE FROM semantic_models_v2 WHERE id=:id") suspend fun removeModel(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun source(value: SemanticSourceRecord)
    @Query("SELECT * FROM semantic_sources WHERE scopeKey=:scope") suspend fun sources(scope: String): List<SemanticSourceRecord>
    @Query("SELECT * FROM semantic_sources WHERE id=:id LIMIT 1") suspend fun source(id: String): SemanticSourceRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun segment(value: SemanticSegmentRecord)
    @Query("SELECT * FROM semantic_segments WHERE sourceId=:id ORDER BY id") suspend fun segments(id: String): List<SemanticSegmentRecord>
    @Query("SELECT * FROM semantic_segments WHERE id=:id LIMIT 1") suspend fun segment(id: String): SemanticSegmentRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun link(value: SemanticEmbeddingLink)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun payload(value: SemanticVectorPayload)
    @Transaction suspend fun embedding(value: SemanticEmbeddingRecord) {
        payload(SemanticVectorPayload(value.embeddingSpaceKey,value.taskType,value.contentHash,value.vector))
        link(SemanticEmbeddingLink(value.indexId,value.segmentId,value.embeddingSpaceKey,value.provider,value.modelId,value.modelHash,value.dimension,value.normalized,value.taskType,value.contentHash,value.vectorFormat,value.createdAt))
    }
    @Query("SELECT COUNT(*) FROM semantic_vector_payloads_v2") suspend fun payloadCount(): Int
    @Query("SELECT e.*, p.vector AS vector FROM semantic_embeddings_v2 e JOIN semantic_vector_payloads_v2 p ON p.embeddingSpaceKey=e.embeddingSpaceKey AND p.taskType=e.taskType AND p.contentHash=e.contentHash WHERE e.contentHash=:hash AND e.embeddingSpaceKey=:space AND e.taskType=:task LIMIT 1") suspend fun reusable(hash: String, space: String, task: String): SemanticEmbeddingRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun job(value: SemanticIndexJob)
    @Query("SELECT * FROM semantic_index_jobs ORDER BY updatedAt DESC") fun observeJobs(): Flow<List<SemanticIndexJob>>
    @Query("SELECT * FROM semantic_index_jobs WHERE id=:id") suspend fun job(id: String): SemanticIndexJob?
    @Query("UPDATE semantic_index_jobs SET state='NEEDS_REINDEX',errorCode=:reason,updatedAt=:updatedAt WHERE id=:id AND state='READY'") suspend fun markReadyNeedsReindex(id: String, reason: String, updatedAt: Long): Int
    @Query("UPDATE semantic_index_jobs SET state='NEEDS_REINDEX',errorCode='DOCUMENT_REMOVED',updatedAt=:updatedAt WHERE id IN (:ids) AND state='READY'") suspend fun markGenerationsNeedsReindex(ids: List<String>, updatedAt: Long): Int
    @Query("SELECT * FROM semantic_active_indexes WHERE scopeKey=:scope") suspend fun active(scope: String): SemanticActiveIndex?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun activate(value: SemanticActiveIndex)
    @Query("SELECT COUNT(*) FROM semantic_embeddings_v2 WHERE indexId=:id") suspend fun count(id: String): Int
    @Query("DELETE FROM semantic_active_indexes WHERE scopeKey=:scope") suspend fun deleteActive(scope: String)
    @Query("DELETE FROM semantic_embeddings_v2 WHERE indexId IN (SELECT id FROM semantic_index_jobs WHERE scopeKey=:scope)") suspend fun deleteEmbeddingsForScope(scope: String)
    @Query("DELETE FROM semantic_segments WHERE sourceId IN (SELECT id FROM semantic_sources WHERE scopeKey=:scope)") suspend fun deleteSegmentsForScope(scope: String)
    @Query("DELETE FROM semantic_sources WHERE scopeKey=:scope") suspend fun deleteSourcesForScope(scope: String)
    @Query("DELETE FROM semantic_embeddings_v2 WHERE segmentId IN (SELECT id FROM semantic_segments WHERE sourceId IN (:sourceIds))") suspend fun deleteEmbeddingsForSources(sourceIds: List<String>)
    @Query("DELETE FROM semantic_segments WHERE sourceId IN (:sourceIds)") suspend fun deleteSegmentsForSources(sourceIds: List<String>)
    @Query("DELETE FROM semantic_sources WHERE scopeKey=:scope AND id IN (:sourceIds)") suspend fun deleteSources(scope: String, sourceIds: List<String>)
    @Query("DELETE FROM semantic_index_jobs WHERE scopeKey=:scope") suspend fun deleteJobsForScope(scope: String)
    @Query("DELETE FROM semantic_vector_payloads_v2 WHERE NOT EXISTS (SELECT 1 FROM semantic_embeddings_v2 e WHERE e.embeddingSpaceKey=semantic_vector_payloads_v2.embeddingSpaceKey AND e.taskType=semantic_vector_payloads_v2.taskType AND e.contentHash=semantic_vector_payloads_v2.contentHash)") suspend fun deleteOrphanPayloads()
    @Transaction suspend fun deleteScope(scope: String) {
        deleteActive(scope)
        deleteEmbeddingsForScope(scope)
        deleteSegmentsForScope(scope)
        deleteSourcesForScope(scope)
        deleteJobsForScope(scope)
        deleteOrphanPayloads()
    }
    @Transaction suspend fun deleteDocumentSources(scope: String, sourceIds: List<String>, generationIds: List<String>, updatedAt: Long) {
        if (generationIds.isNotEmpty()) markGenerationsNeedsReindex(generationIds, updatedAt)
        if (sourceIds.isNotEmpty()) {
            deleteEmbeddingsForSources(sourceIds)
            deleteSegmentsForSources(sourceIds)
            deleteSources(scope, sourceIds)
            deleteOrphanPayloads()
        }
    }
    @Query("SELECT s.*, o.displayName AS sourceName, p.vector AS vector, e.dimension AS dimension, e.embeddingSpaceKey AS spaceKey, e.modelHash AS modelHash, e.modelId AS modelId FROM semantic_segments s JOIN semantic_sources o ON o.id=s.sourceId JOIN semantic_embeddings_v2 e ON e.segmentId=s.id JOIN semantic_vector_payloads_v2 p ON p.embeddingSpaceKey=e.embeddingSpaceKey AND p.taskType=e.taskType AND p.contentHash=e.contentHash WHERE e.indexId=:indexId AND o.scopeKey=:scope AND e.embeddingSpaceKey=:space LIMIT :limit") suspend fun corpus(indexId: String, scope: String, space: String, limit: Int): List<IndexedSegment>
}
/** Sidecar DB is additive: the complete version-8 workspace, including EG1 vectors, is untouched. */
@Database(entities = [SemanticModelRecord::class, SemanticSourceRecord::class, SemanticSegmentRecord::class, SemanticEmbeddingLink::class, SemanticVectorPayload::class, SemanticIndexJob::class, SemanticActiveIndex::class], version = 1, exportSchema = true)
abstract class SemanticDatabase : RoomDatabase() {
    abstract fun dao(): SemanticDao
    companion object { fun create(context: Context): SemanticDatabase = Room.databaseBuilder(context, SemanticDatabase::class.java, "semantic_v2.db").build() }
}
