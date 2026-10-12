package com.localai.workspace.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.localai.workspace.ModelCard
import com.localai.workspace.data.ModelEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w320dp-h640dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatContentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingImageIsVisibleAndRemoveClearsIt() {
        compose.setContent { LocalAiTheme {
            var path by remember { mutableStateOf<String?>("/missing/image.jpg") }
            path?.let { PendingImageAttachment(it, true) { path = null } }
        } }
        compose.onNodeWithText("Image attached").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove image").performClick()
        compose.onNodeWithText("Image attached").assertDoesNotExist()
    }

    @Test fun audioSourceIsOnlyOfferedWhenConnected() {
        var attached = false
        compose.setContent { LocalAiTheme { ChatComposer("question", false, {}, {}, {}, false, onStop = {}, onSend = {}, onAttachAudio = { attached = true }, audioEnabled = true) } }
        compose.onNodeWithContentDescription("Add attachment").performClick(); compose.onNodeWithText("Audio").performClick()
        assertTrue(attached)
    }
    @Test fun thinkingAndToolOverridesAreCompactAndFunctional() {
        compose.setContent { LocalAiTheme {
            var profile by remember { mutableStateOf(com.localai.workspace.data.AssistantProfile()) }
            AssistantControls(profile, true, true, true) { profile = it }
        } }
        compose.onNodeWithTag("assistant-options-summary").assertTextContains("Think: Auto · Tools: Auto")
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-thinking").performClick()
        compose.onNodeWithText("OFF").performClick()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains("Think: Off · Tools: Auto")
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-tools").performClick()
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains("Think: Off · Tools: Off")
    }

    @Test fun toolsOffDoesNotChangeLogicalAgentAndCustomSelectionStaysDistinct() {
        compose.setContent { LocalAiTheme {
            var profile by remember {
                mutableStateOf(com.localai.workspace.data.AssistantProfile(
                    thinking = com.localai.workspace.data.ThinkingMode.ON,
                    tools = setOf("files.read"),
                ))
            }
            Column {
                ChatHeaderTitle(
                    agentTitle = { androidx.compose.material3.Text("General Agent") },
                    conversationTitle = "Current conversation",
                    modelStatus = "Local model · Ready",
                    onModelClick = {},
                    enabled = true,
                )
                AssistantControls(profile, true, true, true) { profile = it }
            }
        } }

        compose.onNodeWithText("General Agent").assertIsDisplayed()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains("Think: On · Tools: Custom")
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-tools").assertTextContains("Tools · Custom").performClick()
        compose.onNodeWithTag("assistant-tool-checkbox-files.read").assertIsOn()
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithText("General Agent").assertIsDisplayed()
        compose.onNodeWithText("No agent").assertDoesNotExist()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains("Think: On · Tools: Off")
    }
    @Test fun codeHasLanguageAndCopiesExactContent() {
        compose.setContent { LocalAiTheme { ChatRichText("A **bold** answer with `inline`.\n\n```python\nprint(42)\n```") } }
        compose.onNodeWithText("python").assertIsDisplayed()
        compose.onNodeWithText("print(42)").assertExists()
        compose.onNodeWithContentDescription("Copy code").performClick()
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("print(42)", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
    }

    @Test fun longMarkdownKeepsHierarchyContentAndScrollableCodeAtCompactWidthAndScaledText() {
        val paragraph = ("The response keeps its complete paragraph readable while the message grows. ").repeat(5).trim()
        val firstListItem = "The first list item wraps naturally without losing its wording."
        val secondListItem = "The second item stays distinct from the paragraph and code sample."
        val code = "val selectedDocument = retrieveEvidence(projectId, selectedDocumentIds)"
        val markdown = "# Overview\n\n$paragraph\n\n## Details\n\n- $firstListItem\n- $secondListItem" +
            "\n\n### Implementation\n\n```kotlin\n$code\n```" +
            "\n\n#### Notes\n\nSecondary detail remains visible." +
            "\n\n##### More detail\n\nAdditional context remains readable." +
            "\n\n###### Fine print\n\nThe original message text stays intact."
        var setFontScale: ((Float) -> Unit)? = null

        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            var fontScale by remember { mutableStateOf(1f) }
            SideEffect { setFontScale = { fontScale = it } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ChatRichText(markdown)
                }
            }
        } }

        listOf(1f, 1.6f, 2f).forEach { scale ->
            compose.runOnIdle { setFontScale?.invoke(scale) }
            compose.waitForIdle()

            val longParagraph = compose.onNodeWithText(paragraph, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            longParagraph.assertTextEquals(paragraph)
            val paragraphHeight = longParagraph.fetchSemanticsNode().size.height.toFloat()
            assertTrue("Long paragraph should wrap to multiple lines at fontScale=$scale", paragraphHeight > 48f)

            val h1 = compose.onNodeWithText("Overview", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val h2 = compose.onNodeWithText("Details", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val h4 = compose.onNodeWithText("Notes", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val h6 = compose.onNodeWithText("Fine print", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val h1Height = h1.fetchSemanticsNode().size.height
            val h2Height = h2.fetchSemanticsNode().size.height
            val h4Height = h4.fetchSemanticsNode().size.height
            val h6Height = h6.fetchSemanticsNode().size.height
            assertTrue("H1 height $h1Height should exceed H2 height $h2Height at fontScale=$scale", h1Height > h2Height)
            assertTrue("H4 height $h4Height should exceed H6 height $h6Height at fontScale=$scale", h4Height > h6Height)

            listOf(
                "Overview", "Details", firstListItem, secondListItem,
                "Implementation", "Notes", "More detail", "Fine print",
                "The original message text stays intact.",
            ).forEach { visibleText ->
                compose.onNodeWithText(visibleText, useUnmergedTree = true)
                    .performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithTag("chat-rich-text-code")
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            compose.onNodeWithText(code, useUnmergedTree = true).assertExists()
        }
    }

    @Test fun longInputLargeFontAndGenerationKeepComposerActionsVisible() {
        var stopped = false
        var setFontScale: ((Float) -> Unit)? = null
        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            var fontScale by remember { mutableStateOf(1f) }
            SideEffect { setFontScale = { fontScale = it } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(Modifier.fillMaxSize()) {
                    ChatHeaderTitle(
                        agentTitle = { androidx.compose.material3.Text("General Agent") },
                        conversationTitle = "A long conversation title that wraps instead of disappearing at large text sizes",
                        modelStatus = "Gemma local model · Preparing · waiting for this selected model to become ready",
                        onModelClick = {}, enabled = true,
                    )
                    Spacer(Modifier.weight(1f))
                    var input by remember { mutableStateOf("Long input\n".repeat(20)) }
                    ChatComposer(
                        input, true, { input = it }, {}, {}, false,
                        onStop = { stopped = true }, onSend = {},
                        contextualControls = {
                            var profile by remember { mutableStateOf(com.localai.workspace.data.AssistantProfile()) }
                            AssistantControls(profile, true, true, false) { profile = it }
                        },
                    )
                }
            }
        } }
        listOf(1f, 1.6f, 2f).forEach { scale ->
            compose.runOnIdle { setFontScale?.invoke(scale) }
            compose.waitForIdle()
            compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-model", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("assistant-options-button").assertIsDisplayed()
            compose.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Stop").performClick()
        assertTrue(stopped)
        compose.onNodeWithContentDescription("Add attachment").assertIsNotEnabled()
        compose.onNodeWithText("Tools").assertDoesNotExist()
    }

    @Test fun libraryAdvertisesOnlyConnectedAudio() {
        val model = ModelEntity(id = "gemma", displayName = "gemma-4-E2B-it.litertlm", localPath = "/test/model",
            fileHash = "hash", fileSize = 2_590_000_000, format = "LITERT_LM", runtimeId = "litert-lm-android",
            compatibilityStatus = "COMPATIBLE", capabilities = "text-generation,vision,audio", importedAt = 0)
        compose.setContent { LocalAiTheme { ModelCard(model) {} } }
        compose.onNodeWithText("Gemma 4 E2B").assertIsDisplayed()
        compose.onNodeWithText("Audio", substring = true).assertExists()
        compose.onNodeWithText("Available: Text · Images · Audio").assertExists()
    }
}
