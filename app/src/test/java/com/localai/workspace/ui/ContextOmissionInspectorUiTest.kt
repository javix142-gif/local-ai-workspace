package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.R
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

    private fun localized(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

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
            compose.onNodeWithText(localized(R.string.memory_context_validation)).performClick()
            compose.onNodeWithText(localized(R.string.memory_query)).performTextInput(query)
            compose.onNodeWithText(localized(R.string.memory_build_context_no_generation)).performClick()

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
