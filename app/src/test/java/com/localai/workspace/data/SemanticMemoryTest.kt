package com.localai.workspace.data
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.semantic.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29], application=android.app.Application::class)
class SemanticMemoryTest {
 @Test fun lexicalFallbackUsesOnlyApprovedRelevantScopeAndPersistsVectors()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();context.getSharedPreferences("local_embedding_model",0).edit().clear().commit()
  val db=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
  try {
   listOf(MemoryItemEntity("a","PROJECT","p","Prefiero respuestas breves","USER_APPROVED",createdAt=1,updatedAt=1),MemoryItemEntity("b","PROJECT","other","Prefiero respuestas breves","USER_APPROVED",createdAt=1,updatedAt=1),MemoryItemEntity("c","GLOBAL",content="Prefiero respuestas con ejemplos",sourceType="EXTERNAL_DOCUMENT",createdAt=1,updatedAt=1)).forEach { db.memoryDao().upsert(it) }
   val service=SemanticRetrievalService(db,EmbeddingModels(context));assertEquals(listOf("a"),service.memories("p","¿Cómo prefiero las respuestas?").map { it.id });assertTrue(service.memories("p","Calcula 5+5").isEmpty())
   assertTrue(service.memories("p","Calcula el promedio de 5 respuestas").isEmpty())
   val v=SemanticVectorEntity("M:a","real-model-hash",memoryId="a",sourceHash=VectorPersistence.hash("Prefiero respuestas breves"),dimensions=768,version=1,vector=VectorPersistence.encode(FloatArray(768){if(it==0)1f else 0f}),updatedAt=1)
   db.semanticVectorDao().put(v);assertEquals(768,db.semanticVectorDao().get("M:a","real-model-hash")!!.dimensions)
   db.semanticVectorDao().deleteForProject("p");assertNull(db.semanticVectorDao().get("M:a","real-model-hash"))
  } finally { db.close() }
 }
}
