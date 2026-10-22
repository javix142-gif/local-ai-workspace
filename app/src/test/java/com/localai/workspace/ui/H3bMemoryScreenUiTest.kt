package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.ScopeAccess
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryScreenUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun topLevelMemoryIsAnIntegratedScreenWithTheRealEmptyState() {
        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithTag("memory-screen").assertIsDisplayed()
        compose.onNodeWithTag("memory-screen-title").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_intro)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_search)).assertIsDisplayed()
        compose.onNodeWithTag("memory-empty-state").assertIsDisplayed()
        compose.onNodeWithTag("top-level-memory").assertIsSelected()
    }

    @Test fun pendingAndPinnedSectionsUseStoredRecordsAndKeepDiscardConfirmed() {
        val graph=(compose.activity.application as LocalAiApplication).graph
        val memory=graph.contextFoundation.memory
        val pendingText="H3B_TEST pending memory ${System.nanoTime()}"
        val pinnedText="H3B_TEST pinned preference ${System.nanoTime()}"
        val categoryText="H3B_TEST saved fact ${System.nanoTime()}"
        val (pending,pinned,category)=runBlocking(Dispatchers.IO) {
            val pending=memory.create(pendingText,SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT,pending=true)
            val pinned=memory.create(pinnedText,SemanticScope(ScopeType.USER,"local"),MemoryKind.PREFERENCE)
            memory.pin(pinned.id,ScopeAccess())
            val category=memory.create(categoryText,SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT)
            Triple(pending,pinned,category)
        }

        compose.onNodeWithTag("top-level-memory").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("memory-record-${pending.id}").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("memory-record-${pinned.id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("memory-pending-section").assertIsDisplayed()
        compose.onNodeWithTag("memory-pinned-section").assertIsDisplayed()
        compose.onNodeWithTag("memory-kind-fact").assertExists()
        compose.onNodeWithTag("memory-discard-${pending.id}").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_delete_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        assertEquals("discard requires confirmation and cancel preserves pending status", "PENDING", runBlocking(Dispatchers.IO) { memory.get(pending.id)?.status })

        compose.onNodeWithTag("memory-approve-${pending.id}").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) { memory.get(pending.id)?.status == "ACTIVE" } }
        compose.onNodeWithTag("memory-pinned-section").assertIsDisplayed()
        compose.onNodeWithTag("memory-kind-fact").assertExists()
        compose.onNodeWithText(categoryText).performScrollTo().assertIsDisplayed()
    }
}

/** Actual MainActivity captures at 360dp and system fontScale 2.0; database state is empty. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryLargeFontLightCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun capturesFullMemoryScreenAtLightThemeScaleTwo() {
        assertEquals(2f,compose.activity.resources.configuration.fontScale,0f)
        captureMemory("h3b-memory-light-360dp-fontScale-2.0")
    }

    private fun captureMemory(name:String) {
        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithTag("memory-screen").assertIsDisplayed()
        compose.onNodeWithTag("memory-empty-state").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        var bitmap:Bitmap?=null
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap!!))
        }
        val directory=java.io.File("build/h3b-phase3-captures").apply{mkdirs()}
        java.io.File(directory,"$name.png").outputStream().use{check(bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}

/** Actual MainActivity capture at 360dp, dark system theme and fontScale 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp-night",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryLargeFontDarkCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun capturesFullMemoryScreenAtDarkThemeScaleTwo() {
        assertEquals(2f,compose.activity.resources.configuration.fontScale,0f)
        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithTag("memory-screen").assertIsDisplayed()
        compose.onNodeWithTag("memory-empty-state").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        var bitmap:Bitmap?=null
        compose.runOnUiThread {
            val view=compose.activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap!!))
        }
        val directory=java.io.File("build/h3b-phase3-captures").apply{mkdirs()}
        java.io.File(directory,"h3b-memory-dark-360dp-fontScale-2.0.png").outputStream().use{check(bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
}
