package com.localai.workspace.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.context.MemoryContextDatabase
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.MemoryManager
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.semantic.v2.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], application=android.app.Application::class)
class SemanticMemoryTest {
    @Test fun canonicalSidecarSearchPersistsVectorsAndCannotCrossProjectScopes()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,MemoryContextDatabase::class.java).build()
        val space=EmbeddingSpaceKey("test","EG2","a".repeat(64),"test",768)
        val embedding=EmbeddingResult(space,SemanticVectors.normalize(FloatArray(768){if(it==0)1f else 0f}),1)
        val manager=MemoryManager(db){_,_->embedding}
        try {
            val own=manager.create("PREF-H1-CANONICAL · Prefiero respuestas breves",SemanticScope(ScopeType.PROJECT,"p"),MemoryKind.PREFERENCE)
            manager.create("PREF-H1-FOREIGN · Prefer short answers",SemanticScope(ScopeType.PROJECT,"other"),MemoryKind.PREFERENCE)
            val access=ScopeAccess(projectId="p")
            val hits=manager.search("¿Cómo prefiero las respuestas?",access)
            assertEquals(listOf(own.id),hits.map{it.record.id})
            assertEquals("INDEXED",manager.get(own.id)?.indexState)
            val stored=db.dao().vector(own.id,space.id,own.contentHash)
            assertNotNull(stored)
            assertEquals(768,stored?.dimension)
            assertArrayEquals(embedding.vector,SemanticVectors.decode(stored!!.bytes,stored.dimension),0f)
            assertTrue(manager.search("PREF-H1-FOREIGN",access).none{it.record.scopeId=="other"})
        } finally { db.close() }
    }
}
