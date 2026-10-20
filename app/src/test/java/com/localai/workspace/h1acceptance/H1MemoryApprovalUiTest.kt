package com.localai.workspace.h1acceptance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.context.MemoryBundleV1
import com.localai.workspace.context.MemoryContent
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.context.MemoryRecord
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import com.localai.workspace.ui.ContextMemoryEntry
import com.localai.workspace.ui.LocalAiTheme
import com.localai.workspace.ui.MultiProcessUiTestApplication
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=MultiProcessUiTestApplication::class,qualifiers="w360dp-h780dp")
class H1MemoryApprovalUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun H1_AT_17_overBudgetImportedPinsRequireVisibleSelectionAndConfirmationBeforeApproval() {
        val app=RuntimeEnvironment.getApplication() as LocalAiApplication
        val manager=app.graph.contextFoundation.memory
        val now=System.currentTimeMillis()
        val source=(1..3).map{i->
            val text="H1_AT17_UI_PIN_$i-Ñ "+"x".repeat(280)
            MemoryRecord(
                id="h1-at17-ui-$i",kind=MemoryKind.PREFERENCE.name,scopeType=ScopeType.USER.name,scopeId="local",
                title="H1_AT17_UI_TITLE_$i",text=text,pinned=true,createdAt=now,updatedAt=now,
                contentHash=MemoryContent.hash(text,MemoryKind.PREFERENCE),
            )
        }
        val preview=MemoryBundleV1.read(ByteArrayInputStream(MemoryBundleV1.write(source)))
        val imported=runBlocking{manager.importBundlePending(preview,preview.candidates.map{it.key}.toSet())}
        assertEquals(3,imported.imported)

        try {
            compose.setContent { LocalAiTheme { ContextMemoryEntry(initiallyOpen=true) } }
            compose.onNodeWithText("Revisar y aprobar 3 recuerdos pendientes").performScrollTo().performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithText("Elige qué recuerdos aprobar sin fijar").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Elige qué recuerdos aprobar sin fijar").assertIsDisplayed()
            assertTrue(imported.pendingIds.all{runBlocking{manager.get(it)?.status=="PENDING"}})

            compose.onNodeWithText("Cancelar").performClick()
            assertTrue("Cancel must leave every imported memory pending",imported.pendingIds.all{runBlocking{manager.get(it)?.status=="PENDING"}})

            compose.onNodeWithText("Revisar y aprobar 3 recuerdos pendientes").performScrollTo().performClick()
            imported.pendingIds.forEach{id->
                compose.onNodeWithTag("memory-approve-without-pin-$id").performScrollTo().performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("memory-approve-without-pin-$id").assertIsOn()
            }
            assertTrue("Selection alone must not mutate imported records",imported.pendingIds.all{runBlocking{manager.get(it)?.status=="PENDING"}})
            compose.onNodeWithText("Confirmar selección").assertIsEnabled().performClick()

            compose.waitUntil(10_000){
                compose.onAllNodesWithText("Elige qué recuerdos aprobar sin fijar").fetchSemanticsNodes().isEmpty() ||
                    compose.onAllNodesWithText("La selección aún supera el límite").fetchSemanticsNodes().isNotEmpty()
            }
            val approved=imported.pendingIds.mapNotNull{runBlocking{manager.get(it)}}
            assertTrue("Confirmation must close the review after approval; statuses=${approved.map{it.status}}, pinned=${approved.count{it.pinned}}",approved.all{it.status=="ACTIVE"})
            assertEquals(3,approved.size)
            assertTrue(approved.none{it.pinned})
            assertTrue(approved.all{it.structuredPayloadJson.orEmpty().contains("APPROVED_WITHOUT_PIN")})
            assertTrue(approved.all{it.structuredPayloadJson.orEmpty().contains("\"originalPinned\":true")})
        } finally {
            runBlocking {
                imported.pendingIds.forEach{id->manager.get(id)?.let{manager.delete(id)}}
            }
        }
    }
}
