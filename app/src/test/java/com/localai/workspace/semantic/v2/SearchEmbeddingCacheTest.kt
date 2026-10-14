package com.localai.workspace.semantic.v2

import org.junit.Assert.*
import org.junit.Test

class SearchEmbeddingCacheTest {
    private val space=EmbeddingSpaceKey("test","EG2","a".repeat(64),"v1",768)
    @Test fun cacheCopiesVectorsAndEvictsLeastRecentlyUsed() {
        val cache=SearchEmbeddingCache(2)
        val vector=FloatArray(768){1f}
        cache.put("a",EmbeddingResult(space,vector,123));vector[0]=0f
        assertEquals(1f,cache.get("a")!!.vector[0],0f)
        cache.put("b",EmbeddingResult(space,vector,123))
        cache.get("a")!!.vector[0]=2f
        cache.put("c",EmbeddingResult(space,vector,123))
        assertNull(cache.get("b"))
        assertEquals(1f,cache.get("a")!!.vector[0],0f)
        assertEquals(0L,cache.get("a")!!.durationMs)
        cache.clear();assertNull(cache.get("a"))
    }
    @Test fun repeatedQueryAblationHasOneComputeInsteadOfFifty() {
        val cache=SearchEmbeddingCache();var computations=0
        repeat(50) { cache.get("immutable-descriptor") ?: EmbeddingResult(space,FloatArray(768){1f},1).also{computations++;cache.put("immutable-descriptor",it)} }
        assertEquals(1,computations)
        // Operation count, not a phone latency/quality benchmark.
        repeat(9) { cache.put("distinct-$it",EmbeddingResult(space,FloatArray(768),1)) }
        assertNull(cache.get("immutable-descriptor"))
    }
}
