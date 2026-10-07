package com.localai.workspace.semantic.v2

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class SemanticDomainTest {
    private fun space(family:String="EG2",dimension:Int=768,hash:String="a".repeat(64))=EmbeddingSpaceKey("litert",family,hash,"0.17.1",dimension)
    @Test fun sameDimensionDifferentFamiliesAreNotCompatible(){assertNotEquals(space("EG1"),space("EG2"));assertNotEquals(space("EG1").id,space("EG2").id)}
    @Test fun dimensionsAreIsolated(){assertNotEquals(space(dimension=768).id,space(dimension=256).id)}
    @Test fun exactModelHashIsRequired(){assertNotEquals(space(hash="a".repeat(64)),space(hash="b".repeat(64)))}
    @Test fun profileIsPartOfIdentity(){assertNotEquals(space().id,space().copy(taskProfile="v2").id)}
    @Test fun normalizationIsPartOfIdentity(){assertNotEquals(space().id,space().copy(normalized=false).id)}
    @Test fun normalizedVectorHasUnitNorm(){val v=SemanticVectors.normalize(floatArrayOf(3f,4f));assertEquals(1.0,sqrt(v.sumOf{it.toDouble()*it}),1e-6)}
    @Test fun truncationRenormalizes(){val v=SemanticVectors.normalize(FloatArray(768){1f},256);assertEquals(256,v.size);assertEquals(1.0,sqrt(v.sumOf{it.toDouble()*it}),1e-6)}
    @Test fun nanRejected(){assertCode(SemanticError.NON_FINITE_VECTOR){SemanticVectors.normalize(floatArrayOf(Float.NaN))}}
    @Test fun infinityRejected(){assertCode(SemanticError.NON_FINITE_VECTOR){SemanticVectors.normalize(floatArrayOf(Float.POSITIVE_INFINITY))}}
    @Test fun zeroRejected(){assertCode(SemanticError.NORMALIZATION_FAILED){SemanticVectors.normalize(FloatArray(768))}}
    @Test fun impossibleDimensionRejected(){assertCode(SemanticError.DIMENSION_UNSUPPORTED){SemanticVectors.normalize(floatArrayOf(1f),256)}}
    @Test fun floatSerializationIsLittleEndian(){assertArrayEquals(byteArrayOf(0,0,128.toByte(),63),SemanticVectors.encode(floatArrayOf(1f)));assertArrayEquals(floatArrayOf(1f,-2f),SemanticVectors.decode(SemanticVectors.encode(floatArrayOf(1f,-2f)),2),0f)}
    @Test fun badVectorSizeRejected(){assertThrows(IllegalArgumentException::class.java){SemanticVectors.decode(ByteArray(8),768)}}
    @Test fun comparisonRefusesSpaceMixing(){assertCode(SemanticError.SPACE_MISMATCH){SemanticVectors.cosine(EmbeddingResult(space("EG1"),floatArrayOf(1f),0),EmbeddingResult(space("EG2"),floatArrayOf(1f),0))}}
    @Test fun canonicalQueryPrefix(){assertEquals("task: search result | query: capital",TaskPromptProfile.format(EmbeddingTask.SEARCH,"capital"))}
    @Test fun documentIncludesTitle(){assertEquals("title: data.csv | text: row",TaskPromptProfile.format(EmbeddingTask.DOCUMENT,"row","data.csv"))}
    @Test fun codeTaskIsSpecialized(){assertEquals("task: code retrieval | query: session",TaskPromptProfile.format(EmbeddingTask.CODE_QUERY,"session"))}
    @Test fun scopesDoNotMix(){assertNotEquals(SemanticScope(ScopeType.PROJECT,"a").key,SemanticScope(ScopeType.PROJECT,"b").key);assertThrows(IllegalArgumentException::class.java){SemanticScope(ScopeType.SESSION)}}
    @Test fun provenancePreservedInContext(){val hit=RetrievalHit("video","s","demo",SemanticModality.TEXT,"evidence",startMs=100,endMs=200,fusedScore=1.0,embeddingSpace="space");assertEquals(100L,ContextBuilder().build(listOf(hit),100).evidence.single().hit.startMs)}
    @Test fun budgetNeverOverflows(){val hit=RetrievalHit("doc","s","doc",SemanticModality.TEXT,"x".repeat(1000),fusedScore=1.0,embeddingSpace="space");assertTrue(ContextBuilder().build(listOf(hit),10).evidence.isEmpty())}
    @Test fun qualityMetricsUseGroundTruth(){val metrics=RetrievalQuality.metrics(listOf(listOf("bad","correct")),listOf(setOf("correct")));assertEquals(0.0,metrics["recall@1"]!!,0.0);assertEquals(1.0,metrics["recall@3"]!!,0.0);assertEquals(0.5,metrics["mrr"]!!,0.0)}
    private fun assertCode(expected:SemanticError,block:()->Unit){try{block();fail("Expected $expected")}catch(error:SemanticFailure){assertEquals(expected,error.code)}}
    @Test fun naturalLanguageCodeSearchUsesCodeTaskOnlyForCodeScope() {
        val input=SemanticInput.Text("where is session reuse handled?")
        assertEquals(EmbeddingTask.CODE_QUERY,TaskPromptProfile.queryTask(input,setOf(SemanticModality.CODE)))
        assertEquals(EmbeddingTask.SEARCH,TaskPromptProfile.queryTask(input,setOf(SemanticModality.TEXT,SemanticModality.CODE)))
    }
}
