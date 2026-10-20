package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.MainActivity
import com.localai.workspace.context.ContextVector
import com.localai.workspace.context.MemoryBundleV1
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import kotlinx.coroutines.runBlocking
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
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Structured memory").performClick()
        compose.onNodeWithText("Search saved memory").assertExists()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Content").assertExists()
        compose.onNodeWithText("Expires in days (optional)").assertExists()
        compose.onNodeWithText("Scope: user").performClick()
        compose.onNodeWithText("Global · this device").performClick()
        compose.onNodeWithText("Scope: global").assertExists()
    }
    @Test fun inspectorDoesNotStartGenerationAutomatically() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Context Inspector / Memory Validation").performScrollTo().performClick()
        compose.onNodeWithText("Build context · no generation").assertExists().assertIsNotEnabled()
        compose.onNodeWithText("Run with Gemma · explicit").assertDoesNotExist()
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

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Structured memory").performClick()
        compose.onNodeWithText("Search saved memory").performTextReplacement(marker)
        compose.onNodeWithText("Details").performScrollTo().performClick()
        compose.onNodeWithText("Memory details").assertIsDisplayed()
        compose.onNodeWithText("Forget permanently").performScrollTo().performClick()
        compose.onNodeWithText("Forget this memory and its versions?").assertIsDisplayed()
        compose.onNodeWithText("Forget permanently").performClick()

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
}
