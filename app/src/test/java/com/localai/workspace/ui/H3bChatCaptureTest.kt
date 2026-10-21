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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic Compose-only fixture for H3b visual evidence; it contains no user data or model output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bChatCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun capturesChatAtLightAndDarkAndSystemFontScaleOneAndTwo() {
        var configure: ((Boolean, Float) -> Unit)? = null
        val opened = AtomicInteger()
        val declined = AtomicInteger()
        val evidence = syntheticEvidence()
        val action = syntheticCalendarAction()

        compose.setContent {
            val baseDensity = LocalDensity.current
            var dark by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(1f) }
            SideEffect { configure = { useDark, scale -> dark = useDark; fontScale = scale } }
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
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
                                        modelStatus = "Gemma E2B · Listo",
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

        listOf(false to 1f, false to 2f, true to 1f, true to 2f).forEach { (dark, scale) ->
            compose.runOnIdle { configure?.invoke(dark, scale) }
            compose.waitForIdle()
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("top-level-workspace")
                .assertTextContains(compose.activity.getString(R.string.navigation_chats))
            compose.onNodeWithTag("chat-message-user-surface").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("chat-message-assistant-surface").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("android-action-card-${action.id}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_add_attachment))
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_send))
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("chat-message-user-surface").performScrollTo()
            compose.waitForIdle()
            capture("chat-${if (dark) "dark" else "light"}-360dp-fontScale-${scale.toInt()}.png")
        }

        compose.runOnIdle { configure?.invoke(true, 2f) }
        compose.waitForIdle()
        compose.onNodeWithTag("android-action-review-button-${action.id}").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.android_action_review_calendar_notice))
            .assertIsDisplayed()
        compose.onNodeWithTag("android-action-value-title").assertTextEquals("Revisión sintética de calendario")
        assertEquals(0, opened.get())
        assertEquals(0, declined.get())
        // decorView captures the activity window; ModalBottomSheet is a separate window.
        // This frame is therefore named for the underlying action card, not the sheet.
        capture("chat-action-card-dark-360dp-fontScale-2.png")
        compose.onNodeWithTag("android-action-decline-button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, opened.get()); assertEquals(1, declined.get()) }
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
