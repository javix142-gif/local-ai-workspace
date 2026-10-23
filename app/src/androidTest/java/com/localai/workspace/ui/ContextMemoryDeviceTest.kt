package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import com.localai.workspace.context.ContextVector
import com.localai.workspace.context.MemoryBundleV1
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Built on host, executed only on Android. No inference or private fixture exports. */
@RunWith(AndroidJUnit4::class)
class ContextMemoryDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun structuredMemoryEntryAndEditorAreAccessible() {
        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithText(text(R.string.memory_search)).assertExists()
        compose.onNodeWithText(text(R.string.common_add)).performClick()
        compose.onNodeWithText(text(R.string.memory_content)).assertExists()
        compose.onNodeWithText(text(R.string.memory_expiration_days)).assertExists()
        compose.onNodeWithText(text(R.string.memory_scope, text(R.string.memory_scope_user))).performClick()
        compose.onNodeWithText(text(R.string.memory_global_scope)).performClick()
        compose.onNodeWithText(text(R.string.memory_scope, text(R.string.memory_scope_global))).assertExists()
    }
    @Test fun inspectorDoesNotStartGenerationAutomatically() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithTag("more-advanced-toggle").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_context_validation)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_build_context_no_generation)).assertExists().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.memory_run_gemma_explicit)).assertDoesNotExist()
    }

    @Test fun H1_AT_13_forgetPermanentlyUsesConfirmationAndLeavesNoAppOwnedMarkerBytes() {
        val context=compose.activity.applicationContext
        val graph=(compose.activity.application as LocalAiApplication).graph
        val manager=graph.contextFoundation.memory
        val marker="H1_AT13_FORGET_Ñ_7E31"
        val fixture=runBlocking {
            val scope=SemanticScope(ScopeType.USER,"local")
            val first=manager.create("$marker version one",scope,MemoryKind.FACT)
            val second=manager.update(first.id,"$marker version two")
            val third=manager.update(second.id,"$marker version three")
            listOf(first,second,third).forEachIndexed{index,row->
                graph.contextFoundation.database.dao().vector(
                    ContextVector(row.id,"h1-at13-${row.id}",row.contentHash,1,byteArrayOf(index.toByte()),index.toLong()),
                )
            }
            val archive=manager.exportBundle(ScopeAccess()).archive
            assertTrue(MemoryBundleV1.read(archive.inputStream()).candidates.any{it.content.contains(marker)})
            listOf(first,second,third) to archive
        }
        val versions=fixture.first
        val archive=fixture.second
        val current=versions.last()

        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithText(text(R.string.memory_search)).performTextReplacement(marker)
        val factCategory=compose.onNodeWithTag("memory-kind-fact").performScrollTo().assertIsDisplayed()
        factCategory.performClick()
        compose.onNodeWithTag("memory-record-${current.id}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-details-${current.id}").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_forget_permanently)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.memory_forget_permanently)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_forget_title)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.common_cancel)).performClick()
        compose.waitForIdle()
        runBlocking {
            assertTrue("Cancel preserves every memory version",versions.all{manager.get(it.id)!=null})
            assertEquals(3,graph.contextFoundation.database.dao().vectorsForOrigins(versions.map{it.id}).size)
            assertTrue(MemoryBundleV1.read(archive.inputStream()).candidates.any{it.content.contains(marker)})
        }

        compose.onNodeWithTag("memory-details-${current.id}").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_forget_permanently)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_forget_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.memory_forget_permanently)).performClick()

        compose.waitUntil(10_000) { runBlocking { manager.get(current.id)==null } }
        runBlocking {
            assertTrue(versions.all{manager.get(it.id)==null})
            assertTrue(graph.contextFoundation.database.dao().vectorsForOrigins(versions.map{it.id}).isEmpty())
        }
        archive.fill(0)
        val utf8=marker.toByteArray(Charsets.UTF_8)
        val utf16=marker.toByteArray(Charsets.UTF_16LE)
        val files=linkedSetOf<File>()
        context.databaseList().forEach{name->
            val base=context.getDatabasePath(name)
            files+=base;files+=File(base.path+"-wal");files+=File(base.path+"-shm")
        }
        listOf(context.cacheDir,context.filesDir).forEach{root->
            if(root.exists())root.walkTopDown().filter{it.isFile}.forEach(files::add)
        }
        files.filter{it.exists()&&it.isFile}.forEach{file->
            val bytes=file.readBytes()
            assertFalse("UTF-8 marker remains in ${file.name}",bytes.containsSequence(utf8))
            assertFalse("UTF-16 marker remains in ${file.name}",bytes.containsSequence(utf16))
        }
    }

    private fun ByteArray.containsSequence(needle:ByteArray):Boolean {
        if(needle.isEmpty()||needle.size>size)return false
        for(start in 0..size-needle.size){
            var matches=true
            for(index in needle.indices)if(this[start+index]!=needle[index]){matches=false;break}
            if(matches)return true
        }
        return false
    }

    private fun text(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)
}
