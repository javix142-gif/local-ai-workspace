package com.localai.workspace.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    private fun render(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Scaffold(topBar = {
                        TopAppBar(
                            expandedHeight = chatTopBarExpandedHeight(LocalDensity.current.fontScale),
                            navigationIcon = { Text("‹") },
                            title = {
                                ChatHeaderTitle(
                                    agentTitle = { Text("General Agent", maxLines = 1) },
                                    conversationTitle = "A deliberately long conversation title that must end with an ellipsis on narrow screens",
                                    modelStatus = "Gemma 4 E2B · Local · Ready",
                                    onModelClick = {}, enabled = true,
                                )
                            },
                            actions = { Text("⋮") },
                        )
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).testTag("chat-body"))
                    }
                }
            }
        }
    }

    @Test fun standardFontScaleKeepsAgentTitleAndModelInsideHeader() {
        render(1f)
        compose.onNodeWithTag("chat-header-agent").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model").assertIsDisplayed()
        assertModelLinePrecedesChatBody()
    }

    @Test fun largeFontScaleExpandsHeaderAndKeepsAllThreeLinesVisible() {
        assertTrue(chatTopBarExpandedHeight(1.6f) > chatTopBarExpandedHeight(1f))
        render(1.6f)
        compose.onNodeWithTag("chat-header-agent").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model").assertIsDisplayed()
        assertModelLinePrecedesChatBody()
    }

    private fun assertModelLinePrecedesChatBody() {
        val model = compose.onNodeWithTag("chat-header-model").fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithTag("chat-body").fetchSemanticsNode().boundsInRoot
        assertTrue("Model status should not be clipped under the header", model.bottom <= body.top)
    }
}
