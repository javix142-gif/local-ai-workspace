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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.LinkAnnotation
import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.ModelCard
import com.localai.workspace.data.ModelEntity
import com.localai.workspace.R
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

    private fun localized(id: Int, vararg args: Any): String =
        RuntimeEnvironment.getApplication().getString(id, *args)

    private fun optionSummary(thinking: Int, tools: Int): String =
        "${localized(R.string.chat_thinking_summary, localized(thinking))} · " +
            localized(R.string.chat_tools_summary, localized(tools))

    @Test fun citationReferenceKeepsEvidenceIdAndShowsStoredSourcePageAndExcerpt() {
        val excerpt = "The indexed passage preserves the user's selected project preference."
        val evidence = listOf(citationEvidence(
            evidenceId = "LCL-AB12",
            sourceLabel = "Project notes · onboarding.txt",
            pageStart = 7,
            excerpt = excerpt,
            cellReferences = listOf("Hoja2!A1", "Hoja1!A3"),
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
                        CitationEvidenceDialog(item, onDismiss = { selectedEvidenceId = null })
                    }
                }
            }
        } }

        compose.onNodeWithText(localized(R.string.chat_sources_title)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.chat_sources_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("LCL-AB12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Project notes · onboarding.txt", useUnmergedTree = true).assertIsDisplayed()
        val referenceDescription = localized(
            R.string.chat_source_accessibility,
            "LCL-AB12",
            "Project notes · onboarding.txt, ${localized(R.string.chat_source_page, 7)}, ${localized(R.string.chat_source_cells)} Hoja2!A1, Hoja1!A3",
        )
        val reference = compose.onNodeWithContentDescription(referenceDescription).assertIsDisplayed()
        val referenceNode = reference.fetchSemanticsNode()
        assertEquals(Role.Button, referenceNode.config[SemanticsProperties.Role])
        assertTrue("Reference target must be at least 48dp", referenceNode.size.height >= minimumTouchTargetPx)
        reference.performClick()

        compose.runOnIdle { assertEquals("LCL-AB12", selectedId) }
        compose.onNodeWithTag("citation-detail-title", useUnmergedTree = true).assertTextEquals("LCL-AB12").assertIsDisplayed()
        compose.onNodeWithTag("citation-source", useUnmergedTree = true).assertTextEquals("Project notes · onboarding.txt")
        compose.onNodeWithTag("citation-page", useUnmergedTree = true).assertTextEquals(localized(R.string.chat_source_page, 7))
        compose.onNodeWithTag("citation-cells", useUnmergedTree = true)
            .assertTextEquals("${localized(R.string.chat_source_cells)} Hoja2!A1, Hoja1!A3").assertIsDisplayed()
        compose.onNodeWithTag("citation-detail-cells", useUnmergedTree = true)
            .assertTextEquals("Hoja2!A1, Hoja1!A3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("citation-excerpt", useUnmergedTree = true).assertTextEquals(excerpt)
        compose.onNodeWithTag("citation-detail-sources-note", useUnmergedTree = true)
            .assertTextEquals(localized(R.string.chat_sources_note)).assertIsDisplayed()
        compose.onNodeWithTag("citation-detail-metadata-note", useUnmergedTree = true)
            .assertTextEquals(localized(R.string.chat_sources_metadata_note)).assertIsDisplayed()
        compose.onNodeWithText(localized(R.string.chat_source_close)).performClick()
        compose.onNodeWithTag("citation-detail-scroll").assertDoesNotExist()
    }

    @Test fun citationSourceCountUsesSpanishSingularAndPluralForms() {
        val first = citationEvidence("COUNT-1", "notes-a.txt", 1, "Stored excerpt A")
        val second = citationEvidence("COUNT-2", "notes-b.txt", 2, "Stored excerpt B")
        compose.setContent { LocalAiTheme {
            Column {
                CitationEvidenceReferences(listOf(first)) {}
                CitationEvidenceReferences(listOf(first, second)) {}
            }
        } }

        compose.onNodeWithText("1 fragmento vinculado")
            .assertContentDescriptionEquals("1 fragmento de fuente vinculado a esta respuesta").assertIsDisplayed()
        compose.onNodeWithText("2 fragmentos vinculados")
            .assertContentDescriptionEquals("2 fragmentos de fuentes vinculados a esta respuesta").assertIsDisplayed()
        compose.onNodeWithText("1 fragmentos vinculados").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], application = Application::class)
    fun citationDetailOffersAnActionToOpenItsIndexedSourceSegment() {
        val evidence = citationEvidence(
            evidenceId = "LCL-OPEN-1",
            sourceLabel = "notes.txt",
            pageStart = 3,
            excerpt = "Stored excerpt from the cited segment",
        )
        var opened = false
        compose.setContent { LocalAiTheme { CitationEvidenceDialog(evidence, {}, onOpenSource = { opened = true }) } }

        compose.onNodeWithTag("citation-open-source").assertIsDisplayed().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    @Config(sdk = [35], application = Application::class)
    fun indexedSourcePreviewRetainsExactSegmentAndLocationAtLargeTextScale() {
        val preview = CitationSourcePreview(
            sourceName = "notes.txt", location = "Page 3 · Text offsets 120–148",
            text = "Full indexed segment · exact source text", notice = "Indexed segment only; this is not a full-document review.",
            indexedSegmentAvailable = true,
        )
        var fontScale by mutableStateOf(1f)
        var updateScale: ((Float) -> Unit)? = null
        var returnedToCitation = false

        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            SideEffect { updateScale = { fontScale = it } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CitationSourcePreviewDialog(preview, onBackToCitation = { returnedToCitation = true }, onDismiss = {})
            }
        } }

        listOf(1f, 1.6f).forEach { scale ->
            compose.runOnIdle { updateScale?.invoke(scale) }
            compose.waitForIdle()
            compose.onNodeWithTag("citation-source-preview-scroll")
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            compose.onNodeWithTag("citation-source-preview-name", useUnmergedTree = true).assertTextEquals("notes.txt").assertIsDisplayed()
            compose.onNodeWithTag("citation-source-preview-location", useUnmergedTree = true)
            .assertTextEquals("Page 3 · Text offsets 120–148").assertIsDisplayed()
            compose.onNodeWithTag("citation-source-preview-text", useUnmergedTree = true)
                .assertTextEquals("Full indexed segment · exact source text").assertIsDisplayed()
            compose.onNodeWithTag("citation-source-preview-notice", useUnmergedTree = true)
                .assertTextEquals(preview.notice)
                .assertIsDisplayed()
        }
        compose.onNodeWithText(localized(R.string.chat_source_back_to_citation)).performClick()
        compose.runOnIdle { assertTrue(returnedToCitation) }
    }

    @Test
    @Config(sdk = [35], application = Application::class)
    fun semanticCodeCitationKeepsPathLinesAndOpensTheSameEvidenceId() {
        val evidenceId = "SV2-0123456789ABCDEF"
        val evidence = listOf(citationEvidence(
            evidenceId = evidenceId,
            sourceLabel = "src/Answer.kt",
            pageStart = null,
            excerpt = "fun answer() = 42",
            semanticSegmentId = "project:answer:0",
            lineStart = 4,
            lineEnd = 6,
        ))
        var selectedId: String? = null

        compose.setContent { LocalAiTheme {
            var selected by remember { mutableStateOf<String?>(null) }
            SideEffect { selectedId = selected }
            Column(Modifier.width(280.dp)) {
                CitationEvidenceReferences(evidence) { selected = it }
                selected?.let { id ->
                    evidence.single { it.evidenceId == id }.let {
                        CitationEvidenceDialog(it, onDismiss = { selected = null })
                    }
                }
            }
        } }

        compose.onNodeWithContentDescription(localized(
            R.string.chat_source_accessibility,
            evidenceId,
            "src/Answer.kt, ${localized(R.string.chat_source_lines_range, 4, 6)}",
        )).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(evidenceId, selectedId) }
        compose.onNodeWithTag("citation-source", useUnmergedTree = true).assertTextEquals("src/Answer.kt")
        compose.onNodeWithTag("citation-detail-lines", useUnmergedTree = true).assertTextEquals(localized(R.string.chat_source_lines_range, 4, 6))
        compose.onNodeWithTag("citation-excerpt", useUnmergedTree = true).assertTextEquals("fun answer() = 42")
        compose.onNodeWithTag("citation-detail-metadata-note", useUnmergedTree = true)
            .assertTextEquals(localized(R.string.chat_sources_metadata_note))
            .performScrollTo().assertIsDisplayed()
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
                CitationEvidenceDialog(evidence, onDismiss = {})
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
                .assertTextEquals(localized(R.string.chat_source_page, 12)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("citation-excerpt", useUnmergedTree = true).assertTextEquals(excerpt)
            compose.onNodeWithText(localized(R.string.chat_sources_note))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(localized(R.string.chat_sources_metadata_note))
                .performScrollTo().assertIsDisplayed()
        }
    }

    private fun citationEvidence(
        evidenceId: String,
        sourceLabel: String,
        pageStart: Int?,
        excerpt: String,
        semanticSegmentId: String? = null,
        lineStart: Int? = null,
        lineEnd: Int? = null,
        cellReferences: List<String> = emptyList(),
    ) = CitationEvidenceEntity(
        id = "citation-$evidenceId",
        conversationId = "conversation-1",
        messageId = "assistant-message-1",
        evidenceId = evidenceId,
        segmentId = if (semanticSegmentId == null) 42 else null,
        excerpt = excerpt,
        documentId = "document-1",
        sourceLabel = sourceLabel,
        pageStart = pageStart,
        retrievalScore = 0.91,
        createdAt = 1L,
        semanticSegmentId = semanticSegmentId,
        lineStart = lineStart,
        lineEnd = lineEnd,
        cellReferencesJson = CitationCellReferenceCodec.encode(cellReferences),
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
        compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).performClick()
        compose.onNodeWithText(localized(R.string.chat_audio)).performClick()
        assertTrue(attached)
    }
    @Test fun thinkingAndToolOverridesAreCompactAndFunctional() {
        compose.setContent { LocalAiTheme {
            var profile by remember { mutableStateOf(com.localai.workspace.data.AssistantProfile()) }
            AssistantControls(profile, true, true, true) { profile = it }
        } }
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(optionSummary(R.string.chat_thinking_auto, R.string.chat_tools_auto))
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-thinking").performClick()
        compose.onNodeWithText(localized(R.string.chat_thinking_off)).performClick()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(optionSummary(R.string.chat_thinking_off, R.string.chat_tools_auto))
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-tools").performClick()
        compose.onNodeWithText(localized(R.string.chat_tools_off)).performClick()
        compose.onNodeWithText(localized(R.string.common_done)).performClick()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(optionSummary(R.string.chat_thinking_off, R.string.chat_tools_off))
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
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(optionSummary(R.string.chat_thinking_on, R.string.chat_tools_custom))
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.onNodeWithTag("assistant-options-tools")
            .assertTextContains(localized(R.string.chat_tools_option, localized(R.string.chat_tools_custom))).performClick()
        compose.onNodeWithTag("assistant-tool-checkbox-files.read").assertIsOn()
        compose.onNodeWithText(localized(R.string.chat_tools_off)).performClick()
        compose.onNodeWithText(localized(R.string.common_done)).performClick()

        compose.onNodeWithText("General Agent").assertIsDisplayed()
        compose.onNodeWithText("No agent").assertDoesNotExist()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(optionSummary(R.string.chat_thinking_on, R.string.chat_tools_off))
    }
    @Test fun codeHasLanguageAndCopiesExactContent() {
        compose.setContent { LocalAiTheme { ChatRichText("A **bold** answer with `inline`.\n\n```python\nprint(42)\n```") } }
        compose.onNodeWithText("python").assertIsDisplayed()
        compose.onNodeWithText("print(42)").assertExists()
        compose.onNodeWithContentDescription(localized(R.string.chat_copy_code)).performClick()
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("print(42)", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
    }

    @Test fun markdownHttpAndHttpsLinksExposeLabelAndDestinationAndOpenOnlyOnTap() {
        val https = "https://example.org/guide_(local)"
        val http = "http://example.net/help"
        val markdown = "Read [secure guide]($https).\n\nVisit [plain guide]($http)."
        val openedUris = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedUris += uri }
        }

        compose.setContent {
            LocalAiTheme {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    Column(Modifier.width(280.dp)) { ChatRichText(markdown) }
                }
            }
        }

        val secureLink = compose.onNodeWithText("secure guide", substring = true, useUnmergedTree = true).assertIsDisplayed()
        val sourceText = compose.onNodeWithTag("chat-rich-text-block-0", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals("Read secure guide.", sourceText.text)
        val links = sourceText.getLinkAnnotations(0, sourceText.length)
        assertEquals(1, links.size)
        assertEquals(https, (links[0].item as LinkAnnotation.Url).url)
        assertEquals("secure guide", sourceText.text.substring(links[0].start, links[0].end))
        val linkStyle = (links[0].item as LinkAnnotation.Url).styles?.style
        assertEquals(androidx.compose.ui.text.style.TextDecoration.Underline, linkStyle?.textDecoration)
        assertNotEquals(androidx.compose.ui.graphics.Color.Unspecified, linkStyle?.color)
        compose.runOnIdle { assertTrue("No URI should open before explicit activation", openedUris.isEmpty()) }

        secureLink.performClick()
        val plainLink = compose.onNodeWithText("plain guide", substring = true, useUnmergedTree = true).assertIsDisplayed()
        val plainText = compose.onNodeWithTag("chat-rich-text-block-1", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val plainAnnotation = plainText.getLinkAnnotations(0, plainText.length).single().item as LinkAnnotation.Url
        assertEquals(http, plainAnnotation.url)
        plainLink.performClick()
        compose.runOnIdle { assertEquals(listOf(https, http), openedUris) }
    }

    @Test fun rejectedProtocolMalformedLinkAndTableLinkStayVisibleWithoutLinkSemantics() {
        val rejected = "[run](javascript:alert(1))"
        val malformed = "[broken](https://example.org"
        val tableLink = "[table source](https://example.org/table)"
        val markdown = "$rejected\n$malformed\n\n| Item | Link |\n| --- | --- |\n| A | $tableLink |"
        val openedUris = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedUris += uri }
        }
        compose.setContent {
            LocalAiTheme {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    Column(Modifier.width(280.dp)) { ChatRichText(markdown) }
                }
            }
        }

        listOf(rejected, malformed, tableLink).forEach { literal ->
            val textNode = compose.onNodeWithText(literal, substring = true, useUnmergedTree = true).assertIsDisplayed()
            val annotated = textNode.fetchSemanticsNode().config[SemanticsProperties.Text].single()
            assertTrue("$literal should not expose a link", annotated.getLinkAnnotations(0, annotated.length).isEmpty())
        }
        assertTrue(openedUris.isEmpty())
    }

    @Test fun responseCopyActionCopiesExactMarkdownSourceInsteadOfRenderedLinkLabel() {
        val original = "See [the source](https://example.org/?q=a%2Fb) and keep `code` exactly."
        compose.setContent { LocalAiTheme { AssistantResponseCopyAction(original) } }

        compose.onNodeWithContentDescription(localized(R.string.chat_copy_response)).performClick()
        compose.runOnIdle {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(original, clipboard.primaryClip?.getItemAt(0)?.text.toString())
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
                Column(Modifier.width(280.dp).height(440.dp).verticalScroll(rememberScrollState())
                    .testTag("markdown-table-fixture-scroll")) {
                    ChatRichText(markdown)
                }
            }
        } }

        listOf(1f, 1.6f, 2f).forEach { scale ->
            compose.runOnIdle { setFontScale?.invoke(scale) }
            compose.waitForIdle()

            compose.onNodeWithTag("markdown-table-fixture-scroll")
                .performTouchInput { swipeDown() }
            val table = compose.onNodeWithTag("chat-markdown-table-0", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            table.performTouchInput { swipeRight() }
            val scrollRange = table.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            assertTrue("Three columns should scroll horizontally at fontScale=$scale", scrollRange.maxValue() > 0f)
            assertEquals(localized(R.string.chat_table_description, 3, 2),
                table.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())

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
            compose.onNodeWithTag("markdown-table-fixture-scroll")
                .performTouchInput { swipeUp() }
            finalValue.assertIsDisplayed()
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
                        conversationTitle = "A long conversation title that remains accessible when it is visually truncated at large text sizes",
                        modelStatus = "Gemma local model with an extended runtime descriptor",
                        modelState = "Preparing",
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
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(
                "Gemma local model with an extended runtime descriptor",
                useUnmergedTree = true,
            ).assertExists()
            compose.onNodeWithTag("chat-header-model-state", useUnmergedTree = true).assertIsDisplayed()
            val statusRow = compose.onNodeWithTag("chat-header-status-row", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            statusRow.performTouchInput { swipeLeft() }
            compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
            statusRow.performTouchInput { swipeRight() }
            val title = compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true)
                .assertTextEquals("A long conversation title that remains accessible when it is visually truncated at large text sizes")
            val titleHeightDp = title.fetchSemanticsNode().size.height /
                RuntimeEnvironment.getApplication().resources.displayMetrics.density
            assertTrue("The header title stays to one visual line at fontScale=$scale ($titleHeightDp dp)", titleHeightDp <= if (scale == 2f) 68f else 40f)
            val composerInput = compose.onNodeWithTag("chat-composer-input").assertIsDisplayed()
            val inputHeightDp = composerInput.fetchSemanticsNode().size.height /
                RuntimeEnvironment.getApplication().resources.displayMetrics.density
            assertTrue("Long multiline input scrolls inside a bounded composer ($inputHeightDp dp)", inputHeightDp <= 128f)
            compose.onNodeWithTag("assistant-options-button").assertIsDisplayed()
            compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).assertIsDisplayed()
            compose.onNodeWithContentDescription(localized(R.string.chat_stop)).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(localized(R.string.chat_stop)).performClick()
        assertTrue(stopped)
        compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).assertIsNotEnabled()
        compose.onNodeWithText(localized(R.string.chat_tools)).assertDoesNotExist()
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
