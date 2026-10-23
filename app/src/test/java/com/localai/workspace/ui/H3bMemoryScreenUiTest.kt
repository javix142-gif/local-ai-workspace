package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryScreenUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun pendingShowAllUsesSpanishSingularAndPluralForms() {
        val resources=compose.activity.resources
        assertEquals("Mostrar 1 propuesta más",resources.getQuantityString(R.plurals.memory_pending_show_all,1,1))
        assertEquals("Mostrar 2 propuestas más",resources.getQuantityString(R.plurals.memory_pending_show_all,2,2))
    }

    @Test fun topLevelMemoryIsAnIntegratedScreenWithTheRealEmptyState() {
        compose.onNodeWithTag("top-level-memory").performClick()
        compose.onNodeWithTag("memory-screen").assertIsDisplayed()
        compose.onNodeWithTag("memory-screen-title").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_intro)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_search)).assertIsDisplayed()
        compose.onNodeWithTag("memory-empty-state").assertIsDisplayed()
        compose.onNodeWithTag("memory-import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-export-json").assertDoesNotExist()
        compose.onNodeWithTag("memory-rebuild-embeddings").assertDoesNotExist()
        compose.onNodeWithTag("memory-advanced-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("memory-export-json").assertIsDisplayed()
        compose.onNodeWithTag("memory-rebuild-embeddings").assertIsDisplayed()
        compose.onNodeWithTag("top-level-memory").assertIsSelected()
    }

    @Test fun pendingAndPinnedSectionsUseStoredRecordsAndKeepDiscardConfirmed() {
        val graph=(compose.activity.application as LocalAiApplication).graph
        val memory=graph.contextFoundation.memory
        val pendingText="H3B_TEST pending memory ${System.nanoTime()}"
        val pinnedText="H3B_TEST pinned preference ${System.nanoTime()}"
        val categoryText="H3B_TEST saved fact ${System.nanoTime()}"
        val fixture=runBlocking(Dispatchers.IO) {
            val pending=memory.create(pendingText,SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT,pending=true)
            val secondPending=memory.create("H3B_TEST second pending ${System.nanoTime()}",SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT,pending=true)
            val pinned=memory.create(pinnedText,SemanticScope(ScopeType.USER,"local"),MemoryKind.PREFERENCE)
            memory.pin(pinned.id,ScopeAccess())
            val category=memory.create(categoryText,SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT)
            listOf(pending.id,secondPending.id,pinned.id)
        }
        val pending=fixture[0]
        val secondPending=fixture[1]
        val pinned=fixture[2]
        val pendingIds=listOf(pending,secondPending)

        compose.onNodeWithTag("top-level-memory").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("memory-pending-card").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("memory-record-$pinned").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("memory-pending-section").assertIsDisplayed()
        compose.onNodeWithText("Por aprobar · 2").assertIsDisplayed()
        compose.onNodeWithTag("memory-pinned-section").assertIsDisplayed()
        compose.onNodeWithTag("memory-kind-fact").assertExists()
        val initiallyVisiblePending=pendingIds.filter { id ->
            compose.onAllNodesWithTag("memory-record-$id").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Only one pending proposal is expanded initially",1,initiallyVisiblePending.size)
        val initialPendingId=initiallyVisiblePending.single()
        compose.onNodeWithTag("memory-pending-show-all").performScrollTo().performClick()
        pendingIds.forEach { id -> compose.onNodeWithTag("memory-record-$id").performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("memory-review-all-pending").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("memory-discard-$initialPendingId").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_delete_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_cancel)).performClick()
        assertEquals("discard requires confirmation and cancel preserves pending status", "PENDING", runBlocking(Dispatchers.IO) { memory.get(initialPendingId)?.status })

        compose.onNodeWithTag("memory-approve-$initialPendingId").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) { memory.get(initialPendingId)?.status == "ACTIVE" } }
        assertEquals("The second proposal remains pending", "PENDING", runBlocking(Dispatchers.IO) { memory.get(pendingIds.single { it != initialPendingId })?.status })
        compose.onNodeWithTag("memory-pinned-section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-kind-fact").performScrollTo().performClick()
        compose.onNodeWithText(categoryText).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("memory-export").performScrollTo().assertIsDisplayed()
    }
}

private fun seedSyntheticMemoryFixture(activity:MainActivity):List<String> {
    val manager=(activity.application as LocalAiApplication).graph.contextFoundation.memory
    return runBlocking(Dispatchers.IO) {
        val pendingIds=listOf(
            manager.create("SINTÉTICO · Prefiere respuestas breves",SemanticScope(ScopeType.USER,"local"),MemoryKind.PREFERENCE,pending=true).id,
            manager.create("SINTÉTICO · Usa español en los ejemplos",SemanticScope(ScopeType.USER,"local"),MemoryKind.FACT,pending=true).id,
        )
        val pinned=manager.create("SINTÉTICO · Idioma preferido: español",SemanticScope(ScopeType.USER,"local"),MemoryKind.PREFERENCE)
        manager.pin(pinned.id,ScopeAccess())
        listOf(
            MemoryKind.FACT to "SINTÉTICO · trabaja con documentos locales",
            MemoryKind.PROCEDURE to "SINTÉTICO · revisar antes de exportar",
            MemoryKind.DECISION to "SINTÉTICO · conservar el original",
            MemoryKind.EVENT to "SINTÉTICO · revisión anual",
            MemoryKind.RELATIONSHIP to "SINTÉTICO · equipo de proyecto",
        ).forEach { (kind,text)->manager.create(text,SemanticScope(ScopeType.USER,"local"),kind) }
        pendingIds
    }
}

private fun captureSyntheticMemory(
    compose:AndroidComposeTestRule<*,MainActivity>,
    name:String,
    expectedScale:Float,
) {
    assertEquals(expectedScale,compose.activity.resources.configuration.fontScale,0f)
    val pendingIds=seedSyntheticMemoryFixture(compose.activity)
    compose.onNodeWithTag("top-level-memory").performClick()
    compose.waitUntil(10_000) {
        compose.onAllNodesWithTag("memory-pending-card").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("memory-pinned-section").fetchSemanticsNodes().isNotEmpty() &&
            listOf("fact", "procedure", "decision", "event", "relationship").all { kind ->
                compose.onAllNodesWithTag("memory-kind-$kind").fetchSemanticsNodes().isNotEmpty()
            }
    }
    captureMemoryView(compose,name+"-overview")
    compose.onNodeWithText("Por aprobar · 2").assertExists()
    compose.onNodeWithText("Fijadas · 1").assertExists()
    val initiallyVisible=pendingIds.filter { id ->
        compose.onAllNodesWithTag("memory-record-$id").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("Memory opens with one pending proposal expanded",1,initiallyVisible.size)
    compose.onNodeWithTag("memory-pending-show-all").performScrollTo().performClick()
    pendingIds.forEach { id -> compose.onNodeWithTag("memory-record-$id").performScrollTo().assertIsDisplayed() }
    if(expectedScale>=1.5f) pendingIds.forEach { id ->
        listOf("memory-discard-$id","memory-approve-$id").forEach { tag ->
            val action=compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertHasClickAction().assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
            check(action.boundsInRoot.height<80f) { "$tag action unexpectedly occupies more than two compact rows: ${action.boundsInRoot}" }
        }
    }
    compose.onNodeWithTag("memory-pending-show-less").performScrollTo().performClick()
    assertEquals("Collapsing restores the one-card summary",1,pendingIds.count { id ->
        compose.onAllNodesWithTag("memory-record-$id").fetchSemanticsNodes().isNotEmpty()
    })
    listOf("fact","procedure","decision","event","relationship").forEach { kind ->
        compose.onNodeWithTag("memory-kind-$kind").assertExists()
    }
    compose.onNodeWithTag("memory-import").performScrollTo().assertExists().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    compose.onNodeWithTag("memory-export").performScrollTo().assertExists().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    captureMemoryView(compose,name+"-transfer-actions")

    compose.onNodeWithTag("memory-kind-fact").performScrollTo().performClick()
    compose.onNodeWithText("SINTÉTICO · trabaja con documentos locales").performScrollTo().assertIsDisplayed()
    captureMemoryView(compose,name+"-categories")
    compose.onNodeWithText("SINTÉTICO · Idioma preferido: español").performScrollTo().assertIsDisplayed()
    captureMemoryView(compose,name+"-pinned")
}

private fun captureMemoryView(compose:AndroidComposeTestRule<*,MainActivity>,name:String) {
    val directory=h3bCaptureDirectory().apply{mkdirs()}
    compose.waitForIdle()
    var bitmap:Bitmap?=null
    val diagnostic=buildString {
        appendLine("fontScale=${compose.activity.resources.configuration.fontScale}")
        listOf("memory-screen","memory-screen-title","memory-pending-card","memory-pending-show-all","memory-category-list","memory-pinned-section","memory-transfer-actions","memory-import","memory-export","top-level-memory")
            .forEach { tag ->
                val bounds=runCatching {
                    compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                }.getOrElse { "MISSING: ${it.javaClass.simpleName}" }
                appendLine("$tag=$bounds")
            }
    }
    compose.runOnUiThread {
        val view=compose.activity.window.decorView
        check(view.width>0&&view.height>0){"The Activity window must be laid out before the screenshot."}
        File(directory,"$name-layout.txt").writeText(
            diagnostic + "capture=activity-decor-view\ndecor=${view.width}x${view.height}\n",
        )
        bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap!!))
    }
    File(directory,"$name.png").outputStream().use{check(bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it))}
}

/** Full screen app capture with a synthetic memory fixture: 2 pending, 1 pinned and 5 categories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp",fontScale=1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryLightRegularCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticMemoryLightScaleOne() = captureSyntheticMemory(compose,"SYNTHETIC-h3b-memory-light-360dp-fontScale-1.0",1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp-night",fontScale=1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryDarkRegularCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticMemoryDarkScaleOne() = captureSyntheticMemory(compose,"SYNTHETIC-h3b-memory-dark-360dp-fontScale-1.0",1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryLargeFontLightCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticMemoryLightScaleTwo() = captureSyntheticMemory(compose,"SYNTHETIC-h3b-memory-light-360dp-fontScale-2.0",2f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h800dp-night",fontScale=2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMemoryLargeFontDarkCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun capturesSyntheticMemoryDarkScaleTwo() = captureSyntheticMemory(compose,"SYNTHETIC-h3b-memory-dark-360dp-fontScale-2.0",2f)
}
