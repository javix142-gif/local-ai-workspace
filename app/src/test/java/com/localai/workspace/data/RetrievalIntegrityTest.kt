package com.localai.workspace.data

import android.app.Application
import androidx.room.Room
import com.localai.workspace.semantic.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class RetrievalIntegrityTest {
    private suspend fun insert(db:WorkspaceDatabase,id:String,text:String,index:Int=0):Long {
        db.documentDao().insert(DocumentEntity(id,"p",id,localPath="/private/$id",mimeType="text/plain",fileHash=id,byteSize=1,extractionStatus="READY",indexingStatus="READY",importedAt=1,updatedAt=1))
        val row=DocumentSegmentEntity(documentId=id,segmentIndex=index,text=text,normalizedText=text,contentHash=VectorPersistence.hash(text),charStart=40,charEnd=40+text.length)
        db.documentDao().insertSegments(listOf(row))
        return db.documentDao().segmentsForDocument(id).first().id
    }
    private val lexical=object:EmbeddingProvider { override suspend fun <T> use(block:suspend(NeuralEmbeddings)->T):T?=null }
    @Test fun fullChunkEmbeddingAndLateLexicalEvidenceHaveExactCoverage()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WorkspaceDatabase::class.java).build()
        val chunk="a".repeat(1250)+" latecoverage "+"z".repeat(536)
        val inputs=mutableListOf<String>()
        val provider=object:EmbeddingProvider { override suspend fun <T> use(block:suspend(NeuralEmbeddings)->T):T=block(object:NeuralEmbeddings {
            override val modelId="controlled";override fun close()=Unit
            override fun embed(text:String):FloatArray {inputs+=text;return FloatArray(768){if(it==0)1f else 0f}}
        }) }
        try {
            val id=insert(db,"a",chunk)
            // Old partial vector must be re-embedded without deleting other indexes.
            db.semanticVectorDao().put(SemanticVectorEntity("D:$id","controlled",documentId="a",sourceHash=VectorPersistence.hash(chunk),dimensions=768,version=1,vector=VectorPersistence.encode(FloatArray(768){1f}),updatedAt=1))
            val evidence=SemanticRetrievalService(db,provider).retrieve("p","latecoverage",documentIds=setOf("a")).single()
            assertTrue(inputs.any{it.endsWith(chunk)})
            assertEquals(chunk,evidence.excerpt);assertEquals(40,evidence.charStart);assertEquals(40+chunk.length,evidence.charEnd)
            assertEquals(chunk,SemanticRetrievalService(db,lexical).retrieve("p","latecoverage").single().excerpt)
        } finally {db.close()}
    }
    @Test fun selectedDocumentBeyondGlobalCapIsSearchedBeforeLimitAndNoMatchIsEmpty()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WorkspaceDatabase::class.java).build()
        try {
            insert(db,"a","common")
            db.documentDao().insertSegments((1..1001).map{DocumentSegmentEntity(documentId="a",segmentIndex=it,text="common",normalizedText="common",contentHash="$it")})
            insert(db,"z","needle retrieval")
            val service=SemanticRetrievalService(db,lexical)
            assertFalse(db.documentDao().segmentsForProject("p",1000).any{it.documentId=="z"})
            assertEquals("z",service.retrieve("p","needle",documentIds=setOf("z")).single().documentId)
            assertTrue(service.retrieve("p","unrelatedxyz",documentIds=setOf("z")).isEmpty())
            assertEquals(db.documentDao().segmentsForProject("p",20).map{it.id},db.documentDao().segmentsForProject("p",20).map{it.id})
            assertTrue(service.retrieve("other","needle",documentIds=setOf("z")).isEmpty())
        } finally {db.close()}
    }
    @Test fun cancellationIsNotConvertedToLexicalSuccess()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WorkspaceDatabase::class.java).build()
        try {
            insert(db,"a","needle")
            val cancel=object:EmbeddingProvider { override suspend fun <T> use(block:suspend(NeuralEmbeddings)->T):T?=throw CancellationException("cancelled") }
            try {SemanticRetrievalService(db,cancel).retrieve("p","needle");fail("cancellation swallowed")}catch(_:CancellationException){}
        } finally {db.close()}
    }
}
