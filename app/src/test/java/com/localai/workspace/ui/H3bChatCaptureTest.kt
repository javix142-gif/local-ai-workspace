package com.localai.workspace.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.localai.workspace.R
import com.localai.workspace.ui.components.AwsBottomBar
import com.localai.workspace.data.CitationCellReferenceCodec
import com.localai.workspace.data.CitationEvidenceEntity
import com.localai.workspace.data.ToolCallEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic Compose-only fixture for H3b visual evidence; it contains no user data or model output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bChatCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capturesChatAndConfirmationLightDarkAtSystemFontScaleOne() = captureChatAndConfirmation(1f)

    @Test
    @Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h1000dp", fontScale = 2f)
    fun capturesChatAndConfirmationLightDarkAtSystemFontScaleTwo() = captureChatAndConfirmation(2f)

    private fun captureChatAndConfirmation(fontScale: Float) {
        var configureDark: ((Boolean) -> Unit)? = null
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val evidence = syntheticEvidence()
        val action = syntheticCalendarAction()

        compose.setContent {
            val baseDensity = LocalDensity.current
            var dark by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                SideEffect { configureDark = { dark = it } }
                LocalAiTheme(darkTheme = dark) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().testTag("h3b-chat-screen"),
                        topBar = {
                            ChatTopBar(
                                navigationIcon = {
                                    IconButton(onClick = {}) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                },
                                title = {
                                    ChatHeaderTitle(
                                        agentTitle = { Text("General") },
                                        conversationTitle = "Conversación de diseño",
                                        modelStatus = "Gemma E2B",
                                        modelState = "Listo",
                                        onModelClick = {},
                                        enabled = true,
                                    )
                                },
                            )
                        },
                        bottomBar = {
                            Column {
                                ChatComposer(
                                    input = "¿Qué muestra esta fuente?",
                                    generating = false,
                                    onInputChange = {},
                                    onAttach = {},
                                    onAttachImage = {},
                                    imageEnabled = false,
                                    onStop = {},
                                    onSend = {},
                                    contextualControls = {
                                        AssistantControls(
                                            profile = com.localai.workspace.data.AssistantProfile(),
                                            toolsAvailable = true,
                                            thinkingAvailable = true,
                                            enabled = true,
                                            compact = true,
                                            onChange = {},
                                        )
                                    },
                                    contextualControlsInline = true,
                                )
                                AwsBottomBar(selectedRoute = "workspace", onDestinationSelected = {})
                            }
                        },
                    ) { padding ->
                        Column(
                            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp).testTag("h3b-chat-transcript"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChatMessageSurface(isUser = true) {
                                Text("¿Qué muestra esta fuente?")
                            }
                            ChatMessageSurface(isUser = false) {
                                Text("El fragmento seleccionado resume la decisión y conserva su ubicación.")
                                CitationEvidenceReferences(listOf(evidence)) {}
                            }
                            ToolExecutionCard(
                                action,
                                onOpenAndroidAction = { opened.incrementAndGet() },
                                onDeclineAndroidAction = { declined.incrementAndGet() },
                            )
                        }
                    }
                }
            }
        }

        assertEquals("Robolectric activity uses the requested system fontScale", fontScale, compose.activity.resources.configuration.fontScale, 0f)
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { configureDark?.invoke(dark) }
            compose.waitForIdle()
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true)
                .assertTextEquals("Conversación de diseño")
            assertHeightDpAtMost(
                compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true),
                if (fontScale == 2f) 68f else 40f,
                "Conversation title remains a single visual line at fontScale=$fontScale",
            )
            val chatsLabel = compose.activity.getString(R.string.navigation_chats)
            if (fontScale >= 1.5f) {
                compose.onNodeWithTag("top-level-label-workspace", useUnmergedTree = true)
                    .assertTextEquals(chatsLabel).assertIsDisplayed()
            } else {
                compose.onNodeWithTag("top-level-workspace").assertTextContains(chatsLabel)
            }
            compose.onNodeWithText("Listo", useUnmergedTree = true).assertIsDisplayed()
            val statusRowBounds = compose.onNodeWithTag("chat-header-status-row", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            listOf("chat-header-model-state", "chat-header-model", "chat-header-agent").forEach { tag ->
                val pill = compose.onNodeWithTag(tag, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "$tag remains fully inside the wrapping status row at fontScale=$fontScale",
                    pill.left >= statusRowBounds.left && pill.right <= statusRowBounds.right &&
                        pill.top >= statusRowBounds.top && pill.bottom <= statusRowBounds.bottom,
                )
            }
            listOf(
                "home" to R.string.navigation_home,
                "workspace" to R.string.navigation_chats,
                "memory" to R.string.navigation_memory,
                "settings" to R.string.navigation_more,
            ).forEach { (route, labelId) ->
                val label = compose.activity.getString(labelId)
                val destination = compose.onNodeWithTag("top-level-$route").assertIsDisplayed()
                    .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                if (route == "workspace") destination.assertIsSelected() else destination.assertIsNotSelected()
                if (fontScale >= 1.5f) {
                    destination.assertContentDescriptionEquals(label)
                    if (route != "workspace") {
                        compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                            .assertDoesNotExist()
                    }
                } else {
                    val labelNode = compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
                        .assertContentDescriptionEquals(label).assertIsDisplayed()
                    assertHeightDpAtMost(
                        labelNode,
                        24f,
                        "Navigation label $label stays on one line at fontScale=$fontScale",
                    )
                }
            }
            compose.onNodeWithTag("chat-message-user-surface").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("chat-message-assistant-surface").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("android-action-card-${action.id}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_add_attachment))
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_send))
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("chat-message-user-surface").performScrollTo()
            compose.waitForIdle()
            capture("chat-${if (dark) "dark" else "light"}-360dp-fontScale-${fontScale.toInt()}.png")
        }

        listOf(false, true).forEach { dark ->
            compose.runOnIdle { configureDark?.invoke(dark) }
            compose.waitForIdle()
            compose.onNodeWithTag("android-action-review-button-${action.id}")
                .performScrollTo().performClick()
            val sheetTag = "android-action-confirm-sheet-${action.id}"
            compose.onNodeWithTag(sheetTag).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.android_action_review_calendar_notice))
                .assertIsDisplayed()
            val modalValue = compose.onNodeWithTag("android-action-value-title")
                .assertTextEquals("Revisión sintética de calendario")
                .assertIsDisplayed()
            assertTrue("Confirmation data has measured text at fontScale=$fontScale", modalValue.fetchSemanticsNode().size.height > 0)
            compose.onNodeWithTag("calendar-event-confirmation-title")
                .assertTextEquals(compose.activity.getString(R.string.android_action_create_event)).assertIsDisplayed()
            compose.onNodeWithTag("android-action-calendar-start").performScrollTo()
                .assertTextEquals(formatAndroidActionDateTime(1792605600000L)).assertIsDisplayed()
            compose.onNodeWithTag("android-action-calendar-end").performScrollTo()
                .assertTextEquals(formatAndroidActionDateTime(1792609200000L)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.android_action_review_calendar_notice))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("android-action-confirm-button").performScrollTo()
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("android-action-decline-button").performScrollTo()
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.runOnIdle {
                assertEquals("the modal only proposes; it does not open the external app", 0, opened.get())
            }
            compose.onNodeWithText(compose.activity.getString(R.string.android_action_review_calendar_notice)).performScrollTo()
            captureModal(
                sheetTag,
                "confirmation-sheet-${if (dark) "dark" else "light"}-360dp-fontScale-${fontScale.toInt()}.png",
                fontScale,
            )
            compose.onNodeWithTag("android-action-decline-button").performClick()
            compose.runOnIdle {
                assertEquals(0, opened.get())
                assertEquals(if (dark) 2 else 1, declined.get())
            }
        }
    }

    private fun capture(filename: String) {
        compose.waitForIdle()
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap!!))
        }
        try {
            val output = File("build/h3b-chat-captures").apply { mkdirs() }.resolve(filename)
            output.outputStream().use { assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Compose capture is a nonempty PNG: $filename", output.length() > 0)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun captureModal(sheetTag: String, filename: String, expectedFontScale: Float) {
        compose.waitForIdle()
        compose.onNodeWithTag(sheetTag).assertIsDisplayed()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("The opened Compose ModalBottomSheet owns a real Dialog window", dialog != null && dialog.isShowing)
        val view = requireNotNull(dialog?.window?.decorView)
        assertTrue("The modal window has measured pixels", view.width > 0 && view.height > 0)
        assertEquals("The captured modal window has the requested system fontScale", expectedFontScale, view.resources.configuration.fontScale, 0f)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            compose.runOnUiThread { view.draw(Canvas(bitmap)) }
            val output = File("build/h3b-chat-captures").apply { mkdirs() }.resolve(filename)
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("The opened Compose modal is captured as a nonempty PNG: $filename", output.length() > 0)
            assertTrue("Modal capture has a real rendered area", bitmap.width > 0 && bitmap.height > 0)
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertHeightDpAtMost(
        node: androidx.compose.ui.test.SemanticsNodeInteraction,
        maximumDp: Float,
        message: String,
    ) {
        val density = compose.activity.resources.displayMetrics.density
        val heightDp = node.fetchSemanticsNode().size.height / density
        assertTrue("$message (height=${heightDp}dp)", heightDp <= maximumDp)
    }

    private fun syntheticEvidence() = CitationEvidenceEntity(
        id = "h3b-synthetic-evidence",
        conversationId = "h3b-synthetic-conversation",
        messageId = "h3b-synthetic-assistant-message",
        evidenceId = "H3B-SYNTHETIC-01",
        segmentId = 1,
        excerpt = "Fragmento sintético guardado para validar la presentación de una fuente.",
        documentId = "h3b-synthetic-document",
        sourceLabel = "Fixture sintética · informe de diseño",
        pageStart = 2,
        retrievalScore = 0.9,
        createdAt = 1L,
        cellReferencesJson = CitationCellReferenceCodec.encode(emptyList()),
    )

    private fun syntheticCalendarAction() = ToolCallEntity(
        id = "h3b-synthetic-action",
        conversationId = "h3b-synthetic-conversation",
        messageId = "h3b-synthetic-assistant-message",
        toolId = "android.action.prepare",
        validatedArgumentsJson = """{"action":"calendar_event","title":"Revisión sintética de calendario","startsAtEpochMillis":1792605600000,"endsAtEpochMillis":1792609200000}""",
        permissionDecision = "EXTERNAL_SEND_REQUIRES_CONFIRMATION",
        confirmationDecision = null,
        status = "CONFIRMATION_REQUIRED",
        startedAt = 1L,
        finishedAt = null,
        errorCode = "CONFIRMATION_REQUIRED",
        resultJson = """{"confirmationRequired":true,"executed":false}""",
    )
}
