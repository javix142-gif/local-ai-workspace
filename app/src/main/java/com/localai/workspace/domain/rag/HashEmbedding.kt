package com.localai.workspace.domain.rag

import kotlin.math.sqrt

interface EmbeddingRuntime {
    val dimensions: Int
    fun embed(text: String): FloatArray
}

/**
 * Small deterministic, fully local baseline used until a neural embedding model is
 * installed. It is a real hashed bag-of-terms vector, not a semantic-model claim.
 */
class HashEmbeddingRuntime(override val dimensions: Int = 256) : EmbeddingRuntime {
    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        Regex("[\\p{L}\\p{N}]{2,}").findAll(text.lowercase()).forEach { match ->
            val token = match.value
            val index = (token.hashCode() and Int.MAX_VALUE) % dimensions
            val sign = if ((token.hashCode() ushr 1) and 1 == 0) 1f else -1f
            vector[index] += sign
        }
        val norm = sqrt(vector.fold(0.0) { sum, value -> sum + value * value }).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
        return vector
    }
}

fun cosineSimilarity(left: FloatArray, right: FloatArray): Double {
    require(left.size == right.size) { "Embedding dimensions differ" }
    var dot = 0.0
    var leftNorm = 0.0
    var rightNorm = 0.0
    left.indices.forEach { index ->
        dot += left[index] * right[index]
        leftNorm += left[index] * left[index]
        rightNorm += right[index] * right[index]
    }
    if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
    return dot / (sqrt(leftNorm) * sqrt(rightNorm))
}

data class RetrievalRank(
    val segmentId: Long,
    val lexicalRank: Int?,
    val vectorScore: Double,
    val fusedScore: Double,
)

object ReciprocalRankFusion {
    fun fuse(
        lexicalIds: List<Long>,
        vectorScores: List<Pair<Long, Double>>,
        k: Int = 60,
    ): List<RetrievalRank> {
        val result = mutableMapOf<Long, RetrievalRank>()
        lexicalIds.forEachIndexed { index, id ->
            val current = result[id]
            result[id] = RetrievalRank(
                segmentId = id,
                lexicalRank = index + 1,
                vectorScore = current?.vectorScore ?: 0.0,
                fusedScore = (current?.fusedScore ?: 0.0) + 1.0 / (k + index + 1),
            )
        }
        vectorScores.forEachIndexed { index, (id, score) ->
            val current = result[id]
            result[id] = RetrievalRank(
                segmentId = id,
                lexicalRank = current?.lexicalRank,
                vectorScore = score,
                fusedScore = (current?.fusedScore ?: 0.0) + 1.0 / (k + index + 1),
            )
        }
        return result.values.sortedByDescending { it.fusedScore }
    }
}
