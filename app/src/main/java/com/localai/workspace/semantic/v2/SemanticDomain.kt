package com.localai.workspace.semantic.v2

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

enum class SemanticModality { TEXT, CODE, IMAGE, AUDIO, VIDEO }
enum class ScopeType { GLOBAL, USER, PROJECT, AGENT, TASK, SESSION }
data class SemanticScope(val type: ScopeType, val id: String = "") {
    init { require(type == ScopeType.GLOBAL || id.isNotBlank()) }
    val key: String get() = "${type.name}:$id"
}
enum class SourceType { LOCAL_FILE, DOCUMENT, CODE_REPOSITORY, IMAGE, GALLERY, AUDIO, VIDEO, MEETING, DRIVE, CALENDAR, MESSAGING, MEMORY, OTHER }
enum class EmbeddingTask { SEARCH, DOCUMENT, QUESTION_ANSWERING, FACT_CHECKING, CODE_QUERY, CODE_DOCUMENT, CLASSIFICATION, CLUSTERING, SENTENCE_SIMILARITY }
enum class SemanticError { MODEL_FILE_INVALID, MODEL_HASH_FAILED, MODEL_METADATA_UNSUPPORTED, EMBEDDING_SIGNATURE_MISSING, MODEL_LOAD_FAILED, DIMENSION_UNSUPPORTED, NON_FINITE_VECTOR, NORMALIZATION_FAILED, SPACE_MISMATCH, MODALITY_UNSUPPORTED, VISION_LOAD_FAILED, AUDIO_LOAD_FAILED, VIDEO_PIPELINE_UNSUPPORTED, INDEX_JOB_CANCELLED, REINDEX_REQUIRED, OUT_OF_MEMORY, RUNTIME_INCOMPATIBLE }
class SemanticFailure(val code: SemanticError, cause: Throwable? = null) : IllegalStateException(code.name, cause)

/** Android-free inputs. Files are private references resolved by the platform adapter. */
sealed interface SemanticInput {
    data class Text(val content: String, val title: String = "none") : SemanticInput
    data class Code(val content: String, val filename: String, val language: String? = null) : SemanticInput
    data class Image(val privateReference: String) : SemanticInput
    data class Audio(val privateReference: String, val startMs: Long = 0, val endMs: Long? = null) : SemanticInput
    data class VideoSegment(val frames: List<Image>, val startMs: Long, val endMs: Long) : SemanticInput
}
object TaskPromptProfile {
    const val VERSION = "v1"
    fun queryTask(input: SemanticInput, modalities: Set<SemanticModality>): EmbeddingTask =
        if(input is SemanticInput.Code || modalities == setOf(SemanticModality.CODE)) EmbeddingTask.CODE_QUERY else EmbeddingTask.SEARCH
    fun format(task: EmbeddingTask, content: String, title: String = "none"): String = when (task) {
        EmbeddingTask.DOCUMENT, EmbeddingTask.CODE_DOCUMENT -> "title: $title | text: $content"
        else -> "task: ${when (task) {
            EmbeddingTask.SEARCH -> "search result"
            EmbeddingTask.QUESTION_ANSWERING -> "question answering"
            EmbeddingTask.FACT_CHECKING -> "fact checking"
            EmbeddingTask.CODE_QUERY -> "code retrieval"
            EmbeddingTask.CLASSIFICATION -> "classification"
            EmbeddingTask.CLUSTERING -> "clustering"
            EmbeddingTask.SENTENCE_SIMILARITY -> "sentence similarity"
            else -> error("Document task")
        }} | query: $content"
    }
}
data class EmbeddingSpaceKey(
    val provider: String, val family: String, val modelHash: String,
    val modelVersion: String, val dimension: Int, val normalized: Boolean = true,
    val taskProfile: String = TaskPromptProfile.VERSION, val schema: Int = 2,
) {
    init { require(modelHash.matches(Regex("[a-f0-9]{64}"))); require(dimension > 0) }
    // Hash the canonical length-prefixed representation: no delimiter ambiguity.
    val id: String get() = fingerprint(listOf(provider, family, modelHash, modelVersion, dimension.toString(), normalized.toString(), taskProfile, schema.toString()).joinToString("") { "${it.length}:$it" })
}
fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
object SemanticVectors {
    const val FORMAT = "FLOAT32_LE_V1"
    fun normalize(vector: FloatArray, dimension: Int = vector.size): FloatArray {
        if (dimension <= 0 || dimension > vector.size) throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
        if (vector.any { !it.isFinite() }) throw SemanticFailure(SemanticError.NON_FINITE_VECTOR)
        val norm = sqrt(vector.take(dimension).sumOf { it.toDouble() * it })
        if (!norm.isFinite() || norm <= 1e-12) throw SemanticFailure(SemanticError.NORMALIZATION_FAILED)
        return FloatArray(dimension) { (vector[it] / norm).toFloat() }
    }
    fun encode(vector: FloatArray): ByteArray {
        require(vector.isNotEmpty() && vector.all { it.isFinite() })
        return ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { vector.forEach { putFloat(it) } }.array()
    }
    fun decode(bytes: ByteArray, dimension: Int): FloatArray {
        require(dimension in 1..8192 && bytes.size == dimension * 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dimension) { buffer.float }.also { if (it.any { v -> !v.isFinite() }) throw SemanticFailure(SemanticError.NON_FINITE_VECTOR) }
    }
    fun cosine(a: EmbeddingResult, b: EmbeddingResult): Double {
        if (a.space != b.space || a.vector.size != b.vector.size) throw SemanticFailure(SemanticError.SPACE_MISMATCH)
        if(a.vector.size!=a.space.dimension || b.vector.size!=b.space.dimension)throw SemanticFailure(SemanticError.DIMENSION_UNSUPPORTED)
        return a.vector.indices.sumOf { a.vector[it].toDouble() * b.vector[it] }
    }
}
data class EmbeddingModelDescriptor(val id: String, val family: String, val sha256: String, val nativeDimension: Int, val supportedDimensions: Set<Int>, val declaredModalities: Set<SemanticModality>)
data class EmbeddingResult(val space: EmbeddingSpaceKey, val vector: FloatArray, val durationMs: Long)
enum class ProviderState { UNLOADED, LOADING, READY, BUSY, UNLOADING, ERROR }
enum class SemanticBackend { CPU, GPU_EXPERIMENTAL }
enum class RuntimeProfile { TEXT_ONLY, TEXT_VISION, FULL_MULTIMODAL }
data class EmbeddingRuntimeProfile(val backend: SemanticBackend = SemanticBackend.CPU, val modalities: RuntimeProfile = RuntimeProfile.TEXT_ONLY)
data class ProviderStatus(val state: ProviderState = ProviderState.UNLOADED, val requested: SemanticBackend = SemanticBackend.CPU, val effective: SemanticBackend? = null, val fallbackReason: String? = null, val loadMs: Long? = null, val lastEmbeddingMs: Long? = null, val error: String? = null, val engineGeneration: Long = 0, val backendEvidence: String = "NOT_INITIALIZED", val modelId: String? = null, val outputDimension: Int? = null)
interface EmbeddingProviderV2 {
    suspend fun embed(input: SemanticInput, task: EmbeddingTask, outputDimension: Int = 768): EmbeddingResult
    suspend fun embedBatch(inputs: List<SemanticInput>, task: EmbeddingTask, outputDimension: Int = 768): List<EmbeddingResult> = inputs.map { embed(it, task, outputDimension) }
    suspend fun unload()
}
interface SourceProvider { val kind: SourceType; suspend fun sources(scope: SemanticScope): List<SemanticSource> }
data class SemanticSource(val id: String, val scope: SemanticScope, val type: SourceType, val name: String, val contentHash: String)
data class RetrievalHit(val sourceId: String, val segmentId: String, val sourceName: String, val modality: SemanticModality, val content: String?, val page: Int? = null, val lineStart: Int? = null, val lineEnd: Int? = null, val startMs: Long? = null, val endMs: Long? = null, val semanticScore: Double? = null, val lexicalScore: Double? = null, val fusedScore: Double, val embeddingSpace: String)
data class ContextEvidence(val id: String, val hit: RetrievalHit, val estimatedTokens: Int, val priority: Int)
data class ContextBundle(val evidence: List<ContextEvidence>, val estimatedTokens: Int)
/** Experimental builder; does not change the baseline chat prompt. Estimates are explicitly estimates. */
class ContextBuilder {
    fun build(hits: List<RetrievalHit>, tokenBudget: Int): ContextBundle {
        var remaining = tokenBudget.coerceAtLeast(0)
        val evidence = hits.mapNotNull { hit ->
            val text = hit.content ?: return@mapNotNull null
            val cost = (text.length + 2) / 3 + 32
            if (cost > remaining) return@mapNotNull null
            remaining -= cost
            ContextEvidence("SV2-${fingerprint(hit.segmentId + hit.embeddingSpace).take(16)}", hit, cost, 1)
        }
        return ContextBundle(evidence, tokenBudget.coerceAtLeast(0) - remaining)
    }
}
object RetrievalQuality {
    fun metrics(rankings: List<List<String>>, relevant: List<Set<String>>): Map<String, Double> {
        require(rankings.size == relevant.size && rankings.isNotEmpty() && relevant.all { it.isNotEmpty() })
        fun recall(k: Int) = rankings.indices.sumOf { i -> rankings[i].take(k).count { it in relevant[i] }.toDouble() / relevant[i].size } / rankings.size
        val mrr = rankings.indices.sumOf { i -> val rank = rankings[i].indexOfFirst { it in relevant[i] }; if (rank < 0) 0.0 else 1.0 / (rank + 1) } / rankings.size
        return mapOf("recall@1" to recall(1), "recall@3" to recall(3), "recall@5" to recall(5), "mrr" to mrr)
    }
}
