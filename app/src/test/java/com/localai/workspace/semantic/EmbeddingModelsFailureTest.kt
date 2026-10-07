package com.localai.workspace.semantic

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class EmbeddingModelsFailureTest {
    @Test fun failedImportPreservesOldConfigurationAndOriginalSourceAndPersistsDiagnostics() = runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("local_embedding_model",0)
        val old=File(context.filesDir,"embedding-models/old.gguf").apply { parentFile!!.mkdirs();writeBytes(ByteArray(64)) }
        val source=File(context.cacheDir,"user-source.gguf").apply { writeBytes(ByteArray(64) { 1 }) }
        prefs.edit().clear().putString("path",old.path).putString("hash","old-hash").commit()
        try {
            val model=EmbeddingModels(context)
            try { model.import(Uri.fromFile(source));fail("Expected hash mismatch") } catch(e:EmbeddingImportFailure) {
                assertEquals("HASH_MISMATCH",e.diagnostics.code)
            }
            assertEquals(old.path,prefs.getString("path",null));assertEquals("old-hash",prefs.getString("hash",null))
            assertTrue(old.isFile);assertTrue(source.isFile);assertEquals(64,source.length().toInt())
            assertEquals(listOf(old.name),old.parentFile!!.listFiles()!!.filter { it.isFile }.map { it.name })
            val restored=EmbeddingModels(context)
            assertEquals("FAILED",restored.diagnostics.value?.status)
            assertEquals(EmbeddingImportStage.HASH_VERIFY,restored.diagnostics.value?.stage)
        } finally { old.delete();source.delete();prefs.edit().clear().commit() }
    }
}
