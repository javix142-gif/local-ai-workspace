package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.context.MemoryKind
import com.localai.workspace.semantic.v2.ScopeType
import com.localai.workspace.semantic.v2.SemanticScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MultiProcessUiTestApplication::class, qualifiers = "w320dp-h640dp")
class ContextOmissionInspectorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialDropReasonIsVisibleInInspectorWithoutShowingDroppedMemoryText() {
        val app = RuntimeEnvironment.getApplication() as LocalAiApplication
        val foundation = app.graph.contextFoundation
        val query = "Summarize M00INSPECTOR retention marker guidance"
        val privateFixtureSentinel = "SYNTHETIC_DROPPED_MEMORY_BODY_7F31"
        val text = ("M00INSPECTOR retention marker synthetic guidance ".repeat(320) + privateFixtureSentinel)
        val memory = runBlocking {
            foundation.memory.create(
                text,
                SemanticScope(ScopeType.USER, "local"),
                MemoryKind.FACT,
            )
        }

        try {
            compose.setContent { LocalAiTheme { ContextDiagnosticsEntry() } }
            compose.onNodeWithText("Context Inspector / Memory Validation").performClick()
            compose.onNodeWithText("Query").performTextInput(query)
            compose.onNodeWithText("Build context · no generation").performClick()

            compose.waitUntil(25_000) {
                compose.onAllNodesWithText("M1", substring = false).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("TOKEN_BUDGET", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("M1", substring = false).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("TOKEN_BUDGET", substring = false).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(privateFixtureSentinel).assertDoesNotExist()
        } finally {
            runBlocking { foundation.memory.delete(memory.id) }
        }
    }
}
