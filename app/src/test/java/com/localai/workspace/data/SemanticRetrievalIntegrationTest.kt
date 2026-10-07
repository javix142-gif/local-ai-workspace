package com.localai.workspace.data
import android.app.Application
import androidx.room.Room
import com.localai.workspace.semantic.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
/** Controlled encoder verifies orchestration/cache/SQLite, not neural model accuracy.
 * Actual EmbeddingGemma inference is tested separately in embedding-real-linux.json. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],application=Application::class)
class SemanticRetrievalIntegrationTest {
 @Test fun hybridSearchPersistsEmbeddingsReusesThemAndInvalidatesChangedSources()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WorkspaceDatabase::class.java).build()
  var calls=0
  val provider=object:EmbeddingProvider {
   override suspend fun <T> use(block:suspend(NeuralEmbeddings)->T):T=block(object:NeuralEmbeddings {
    override val modelId="controlled-test-model"
    override fun embed(text:String):FloatArray {calls++;return FloatArray(768){i->if(i==(if("ocean" in text)1 else 0))1f else 0f}}
    override fun close()=Unit
   })
  }
  try {
   for ((id,text) in listOf(1L to "domestic feline cat",2L to "ocean sea water")) {
    db.documentDao().insert(DocumentEntity("d$id","p","doc$id.txt",localPath="/private/$id",mimeType="text/plain",fileHash="$id",byteSize=1,extractionStatus="READY",indexingStatus="READY",importedAt=1,updatedAt=1))
    db.documentDao().insertSegments(listOf(DocumentSegmentEntity(id,"d$id",0,text=text,normalizedText=text,contentHash=VectorPersistence.hash(text))))
   }
   val service=SemanticRetrievalService(db,provider)
   assertEquals("d1",service.retrieve("p","cats",1).single().documentId);assertEquals("SEMANTIC + LEXICAL",service.diagnostics.value.mode)
   assertEquals(3,calls);assertNotNull(db.semanticVectorDao().get("D:1","controlled-test-model"))
   service.retrieve("p","cats",1);assertEquals(4,calls) // Only the new query, not document vectors.
   db.documentDao().insertSegments(listOf(DocumentSegmentEntity(1,"d1",0,text="edited feline",normalizedText="edited feline",contentHash="changed")))
   service.retrieve("p","cats",1);assertEquals(6,calls);assertEquals(VectorPersistence.hash("edited feline"),db.semanticVectorDao().get("D:1","controlled-test-model")!!.sourceHash)
   assertTrue(service.retrieve("other","cats",1).isEmpty())
  }finally{db.close()}
 }
}
