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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.localai.workspace.data.CitationEvidenceEntity
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

    @Test fun citationReferenceKeepsEvidenceIdAndShowsStoredSourcePageAndExcerpt() {
        val excerpt = "The indexed passage preserves the user's selected project preference."
        val evidence = listOf(citationEvidence(
            evidenceId = "LCL-AB12",
            sourceLabel = "Project notes · onboarding.txt",
            pageStart = 7,
            excerpt = excerpt,
        ))
        var selectedId: String? = null
        var minimumTouchTargetPx = 0f

        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            SideEffect { minimumTouchTargetPx = with(density) { 48.dp.toPx() } }
            var selectedEvidenceId by remember { mutableStateOf<String?>(null) }
            SideEffect { selectedId = selectedEvidenceId }
            Column(Modifier.width(280.dp)) {
                CitationEvidenceReferences(evidence) { selectedEvidenceId = it }
                selectedEvidenceId?.let { id ->
                    evidence.firstOrNull { it.evidenceId == id }?.let { item ->
                        CitationEvidenceDialog(item) { selectedEvidenceId = null }
                    }
                }
            }
        } }

        compose.onNodeWithText("Evidence references").assertIsDisplayed()
        compose.onNodeWithText("Evidence references")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("LCL-AB12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Project notes · onboarding.txt", useUnmergedTree = true).assertIsDisplayed()
        val reference = compose.onNodeWithContentDescription(
            "Evidence LCL-AB12, source Project notes · onboarding.txt, page 7",
        ).assertIsDisplayed()
        val referenceNode = reference.fetchSemanticsNode()
        assertEquals(Role.Button, referenceNode.config[SemanticsProperties.Role])
        assertTrue("Reference target must be at least 48dp", referenceNode.size.height >= minimumTouchTargetPx)
        reference.performClick()

        compose.runOnIdle { assertEquals("LCL-AB12", selectedId) }
        compose.onNodeWithTag("citation-detail-title", useUnmergedTree = true).assertTextEquals("LCL-AB12").assertIsDisplayed()
        compose.onNodeWithTag("citation-source", useUnmergedTree = true).assertTextEquals("Project notes · onboarding.txt")
        compose.onNodeWithTag("citation-page", useUnmergedTree = true).assertTextEquals("Page 7")
        compose.onNodeWithTag("citation-excerpt", useUnmergedTree = true).assertTextEquals(excerpt)
        compose.onNodeWithText("Excerpt only; it does not represent a full-document review.").assertIsDisplayed()
        compose.onNodeWithText("Metadata comes from the local index, not from model-generated citation text.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("citation-detail-scroll").assertDoesNotExist()
    }

    @Test fun longCitationExcerptAndSourceRemainScrollableAndUntruncatedOnCompactScaledLayout() {
        val sourceLabel = "A deliberately long indexed source label that wraps across lines on a compact screen"
        val excerpt = (1..18).joinToString("\n\n") { paragraph ->
            "Paragraph $paragraph: this exact stored excerpt remains available in the citation details and can be reached by scrolling."
        }
        val evidence = citationEvidence("LCL-LONG", sourceLabel, pageStart = 12, excerpt = excerpt)
        var fontScale by mutableStateOf(1f)
        var updateScale: ((Float) -> Unit)? = null

        compose.setContent { LocalAiTheme {
            val systemDensity = LocalDensity.current
            SideEffect { updateScale = { fontScale = it } }
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, fontScale)) {
                CitationEvidenceDialog(evidence) {}
            }
        } }

        listOf(1f, 1.6f, 2f).forEach { scale ->
            compose.runOnIdle { updateScale?.invoke(scale) }
            compose.waitForIdle()

            compose.onNodeWithTag("citation-detail-scroll")
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            compose.onNodeWithTag("citation-source", useUnmergedTree = true)
                .assertTextEquals(sourceLabel).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("citation-page", useUnmergedTree = true)
                .assertTextEquals("Page 12").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("citation-excerpt", useUnmergedTree = true).assertTextEquals(excerpt)
            compose.onNodeWithText("Excerpt only; it does not represent a full-document review.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Metadata comes from the local index, not from model-generated citation text.")
                .performScrollTo().assertIsDisplayed()
        }
    }

    private fun citationEvidence(
        evidenceId: String,
        sourceLabel: String,
        pageStart: Int?,
        excerpt: String,
    ) = CitationEvidenceEntity(
        id = "citation-$evidenceId",
        conversationId = "conversation-1",
        messageId = "assistant-message-1",
        evidenceId = evidenceId,
        segmentId = 42,
        excerpt = excerpt,
        documentId = "document-1",
        sourceLabel = sourceLabel,
        pageStart = pageStart,
        retrievalScore = 0.91,
        createdAt = 1L,
    )

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

    @Test fun markdownTableKeepsHeadersCellsAndHorizontalAccessAtCompactWidthAndScaledText() {
        val longValue = "This deliberately long table value wraps into readable lines instead of being clipped or dropped."
        val markdown = """| Item | Description | State |
            | --- | --- | --- |
            | Evidence | $longValue | Ready |
            | Scope | Project-local result | Complete |""".trimIndent()
        var setFontScale: ((Float) -> Unit)? = null

        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            var fontScale by remember { mutableStateOf(1f) }
            SideEffect { setFontScale = { fontScale = it } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column(Modifier.width(280.dp).height(440.dp).verticalScroll(rememberScrollState())) {
                    ChatRichText(markdown)
                }
            }
        } }

        listOf(1f, 1.6f, 2f).forEach { scale ->
            compose.runOnIdle { setFontScale?.invoke(scale) }
            compose.waitForIdle()

            val table = compose.onNodeWithTag("chat-markdown-table-0", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            table.performTouchInput { swipeRight() }
            val scrollRange = table.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            assertTrue("Three columns should scroll horizontally at fontScale=$scale", scrollRange.maxValue() > 0f)
            assertTrue(table.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
                .single().contains("3 columns and 2 data rows"))

            compose.onNodeWithText("Item", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
            compose.onNodeWithText("Description", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()

            val longCell = compose.onNodeWithTag("chat-markdown-cell-1-1", useUnmergedTree = true)
                .assertTextEquals(longValue).assertExists()
            assertTrue("Long value should wrap at fontScale=$scale", longCell.fetchSemanticsNode().size.height > 48f)
            compose.onNodeWithContentDescription("Description: $longValue", useUnmergedTree = true).assertExists()

            table.performTouchInput { swipeLeft(startX = right * 0.88f, endX = right * 0.42f) }
            longCell.assertIsDisplayed()
            table.performTouchInput { swipeLeft() }
            compose.onNodeWithText("State", useUnmergedTree = true).assertIsDisplayed()
            val finalValue = compose.onNodeWithText("Complete", useUnmergedTree = true)
                .assertExists()
            assertTrue("Final-column value remains laid out at fontScale=$scale", finalValue.fetchSemanticsNode().size.height > 0)
        }
    }

    @Test fun malformedTableRemainsLiteralAndKeepsEveryRowVisible() {
        val malformed = "| Name | Value |\n| --- | --- |\n| A | 1 |\n| incomplete |"
        compose.setContent { LocalAiTheme {
            Column(Modifier.width(280.dp)) { ChatRichText(malformed) }
        } }

        compose.onNodeWithTag("chat-markdown-table-0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("chat-rich-text-block-0", useUnmergedTree = true)
            .assertTextEquals(malformed).assertIsDisplayed()
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
