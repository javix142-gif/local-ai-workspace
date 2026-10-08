package com.localai.workspace.semantic.v2

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.localai.workspace.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class SemanticStorageTest {
    @Test fun additiveSidecarLeavesVersion8WorkspaceAndEg1VectorsIntact()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val old=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        val project=ProjectEntity("p","Saved project",1,2);val chat=ConversationEntity("chat","p","Saved chat",1,2);val message=MessageEntity("msg","chat","USER","saved",3)
        val model=ModelEntity("model","Gemma","private",fileHash="a".repeat(64),fileSize=42,format="LITERT_LM",runtimeId="litert-lm-android",compatibilityStatus="COMPATIBLE",importedAt=1)
        val memory=MemoryItemEntity("memory","GLOBAL",content="Approved preference",sourceType="USER",createdAt=1,updatedAt=2)
        old.memoryDao().upsert(memory)
        val vector=SemanticVectorEntity("D:1","eg1",sourceHash="source",dimensions=768,version=1,vector=ByteArray(3072),updatedAt=1)
        old.projectDao().upsert(project);old.conversationDao().upsert(chat);old.messageDao().insert(message);old.modelDao().insert(model);old.semanticVectorDao().put(vector)
        val newer=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build()
        try { newer.openHelper.writableDatabase
            assertEquals(8,old.openHelper.readableDatabase.version)
            assertEquals(memory,old.memoryDao().get("memory"));assertEquals(project,old.projectDao().get("p"));assertEquals(chat,old.conversationDao().get("chat"));assertEquals(message,old.messageDao().recent("chat",10).single());assertEquals(model,old.modelDao().get("model"));assertArrayEquals(vector.vector,old.semanticVectorDao().get("D:1","eg1")!!.vector)
        } finally { newer.close();old.close() }
    }
    @Test fun cancelledGreenBuildDoesNotReplaceBluePointer()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),SemanticDatabase::class.java).build()
        try { val dao=db.dao();dao.activate(SemanticActiveIndex("PROJECT:p","old","space"))
            try { db.withTransaction { dao.activate(SemanticActiveIndex("PROJECT:p","partial","space"));error("Build interrupted") } }catch(_:IllegalStateException){}
            assertEquals("old",dao.active("PROJECT:p")!!.indexId)
        } finally { db.close() }
    }
    @Test fun deduplicationIncludesSpaceTaskAndHash()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),SemanticDatabase::class.java).build()
        try { val dao=db.dao();val row=SemanticEmbeddingRecord("index","segment","space","litert","model","a".repeat(64),768,true,"DOCUMENT","hash",SemanticVectors.FORMAT,ByteArray(3072),1);dao.embedding(row)
            dao.embedding(row.copy(indexId="green",segmentId="another"));assertEquals(1,dao.payloadCount());assertEquals(1,dao.count("green"));assertNotNull(dao.reusable("hash","space","DOCUMENT"));assertNull(dao.reusable("hash","other","DOCUMENT"));assertNull(dao.reusable("hash","space","SEARCH"));assertNull(dao.reusable("changed","space","DOCUMENT"))
        } finally { db.close() }
    }
    @Test fun scopeCannotReadAnotherProjectsVectors()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),SemanticDatabase::class.java).build()
        try { val dao=db.dao();dao.source(SemanticSourceRecord("source","PROJECT:p","DOCUMENT","file","text/plain","reference",1,1,"hash"));dao.segment(SemanticSegmentRecord("seg","source","TEXT","private","file",2,10,20,100,200,"hash"));dao.embedding(SemanticEmbeddingRecord("index","seg","space","litert","model","a".repeat(64),768,true,"DOCUMENT","hash",SemanticVectors.FORMAT,ByteArray(3072),1))
            val hit=dao.corpus("index","PROJECT:p","space",10).single();assertEquals(10,hit.segment.lineStart);assertEquals(100L,hit.segment.startMs);assertTrue(dao.corpus("index","PROJECT:other","space",10).isEmpty());assertTrue(dao.corpus("index","PROJECT:p","different",10).isEmpty())
        } finally { db.close() }
    }
    @Test fun sameSegmentCanRetainDifferentRepresentationsWithoutOverwriting()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),SemanticDatabase::class.java).build()
        try { val dao=db.dao();val row=SemanticEmbeddingRecord("index","audio-segment","space","litert","model","a".repeat(64),768,true,"DOCUMENT","audio-hash",SemanticVectors.FORMAT,ByteArray(3072),1)
            dao.embedding(row);dao.embedding(row.copy(contentHash="transcript-hash"));assertEquals(2,dao.count("index"))
        } finally { db.close() }
    }
    @Test fun interruptedJobRecoveryPreservesActiveIndex()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),SemanticDatabase::class.java).build()
        try { val dao=db.dao();dao.activate(SemanticActiveIndex("GLOBAL:","blue","space"));dao.job(SemanticIndexJob("green","GLOBAL:","space","INDEXING",1,1,1,1,1,null));dao.recoverInterruptedJobs();assertEquals("CANCELLED",dao.job("green")!!.state);assertEquals("blue",dao.active("GLOBAL:")!!.indexId)
        } finally { db.close() }
    }

    @Test fun staleOrFailedIndexCannotBeRestored()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,SemanticDatabase::class.java).build()
        val workspace=Room.inMemoryDatabaseBuilder(context,WorkspaceDatabase::class.java).build()
        try {
            val dao=db.dao();val row=SemanticEmbeddingRecord("ready","seg-ready","space","litert","model","a".repeat(64),768,true,"DOCUMENT","ready-hash",SemanticVectors.FORMAT,ByteArray(3072),1)
            dao.embedding(row);dao.job(SemanticIndexJob("ready","GLOBAL:","space","READY",1,1,1,1,1,null));dao.activate(SemanticActiveIndex("GLOBAL:","ready","space"))
            dao.embedding(row.copy(indexId="blue",segmentId="seg-blue",contentHash="stale-hash"));dao.job(SemanticIndexJob("blue","GLOBAL:","space","NEEDS_REINDEX",1,1,1,1,1,null))
            val layer=SemanticLayer(context,workspace,kotlinx.coroutines.sync.Mutex(),db,"restore-test")
            // Stale generations cannot be restored; rollback of an older READY generation remains covered separately.
            try { layer.switchIndex("blue");fail("Stale index restored as current") } catch(_:IllegalArgumentException) {}
            assertEquals("ready",dao.active("GLOBAL:")!!.indexId)
            dao.job(SemanticIndexJob("failed","GLOBAL:","space","FAILED",1,1,1,1,1,"INDEX_FAILED"))
            try { layer.switchIndex("failed");fail("Partial index restored") } catch(_:IllegalArgumentException) {}
            assertEquals("ready",dao.active("GLOBAL:")!!.indexId)
        } finally { db.close();workspace.close() }
    }
}
