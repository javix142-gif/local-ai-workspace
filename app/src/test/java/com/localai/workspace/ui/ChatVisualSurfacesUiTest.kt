package com.localai.workspace.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.localai.workspace.R
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatVisualSurfacesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun localized(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    @Test fun chatAndInspectorSurfacesWrapAtWidthsAndFontScalesWithoutLosingActionsOrReasons() {
        val longMessage = "This response remains complete and wraps naturally at compact width. ".repeat(5).trim()
        var configure: ((Int, Float, Boolean) -> Unit)? = null
        var changeInput: ((String) -> Unit)? = null
        val attachCount = AtomicInteger()
        val sendCount = AtomicInteger()
        val stopCount = AtomicInteger()

        compose.setContent {
            LocalAiTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                var widthDp by remember { mutableStateOf(360) }
                var fontScale by remember { mutableStateOf(1f) }
                var generating by remember { mutableStateOf(false) }
                var input by remember { mutableStateOf("A draft that can be sent") }
                SideEffect {
                    configure = { width, scale, isGenerating ->
                        widthDp = width
                        fontScale = scale
                        generating = isGenerating
                    }
                    changeInput = { input = it }
                }
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                    Column(
                        Modifier.width(widthDp.dp).height(880.dp).verticalScroll(rememberScrollState())
                            .testTag("chat-visual-viewport"),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        ChatMessageSurface(isUser = true) {
                            Text("A long user prompt wraps with the complete message visible.")
                        }
                        ChatMessageSurface(
                            isUser = false,
                            stateLabel = if (generating) "Generating · composing response" else null,
                        ) {
                            Text(longMessage, Modifier.testTag("visual-long-message"))
                        }
                        ContextBundleSummary(
                            includedCount = 3,
                            omittedCount = 1,
                            estimatedInputTokens = 2870,
                            inputBudget = 4096,
                        )
                        ContextInspectorSection("Included context", 3)
                        ContextInspectorEntry("document-17 · SEMANTIC", "PROJECT · 128 estimated tokens", omitted = false)
                        ContextInspectorSection("Omitted context", 1, omitted = true)
                        ContextInspectorEntry("chunk-29", "Reason · TOKEN_BUDGET", omitted = true)
                        ChatComposer(
                            input = input,
                            generating = generating,
                            onInputChange = { input = it },
                            onAttach = { attachCount.incrementAndGet() },
                            onAttachImage = { attachCount.incrementAndGet() },
                            imageEnabled = true,
                            onStop = { stopCount.incrementAndGet() },
                            onSend = { sendCount.incrementAndGet() },
                        )
                    }
                }
            }
        }

        val layouts = listOf(
            Triple(360, 1f, false),
            Triple(393, 1.3f, false),
            Triple(411, 1.6f, true),
            Triple(411, 2f, true),
        )
        layouts.forEach { (width, scale, generating) ->
            compose.runOnIdle { configure?.invoke(width, scale, generating) }
            compose.waitForIdle()

            val viewportWidth = compose.onNodeWithTag("chat-visual-viewport").fetchSemanticsNode().size.width
            val userSurface = compose.onNodeWithTag("chat-message-user-surface").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            val assistantSurface = compose.onNodeWithTag("chat-message-assistant-surface").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue("User card must remain within the $width dp viewport", userSurface.size.width <= viewportWidth)
            assertTrue("Assistant card must remain within the $width dp viewport", assistantSurface.size.width <= viewportWidth)

            compose.onNodeWithTag("chat-message-user-surface")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, localized(R.string.chat_role_user)))
            compose.onNodeWithTag("chat-message-assistant-surface")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, localized(R.string.chat_role_assistant)))
            compose.onNodeWithText(longMessage, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(localized(R.string.memory_context_budget)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(localized(R.string.memory_included_count, 3)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(localized(R.string.memory_omitted_count, 1)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("document-17 · SEMANTIC").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Reason · TOKEN_BUDGET").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).performScrollTo().assertIsDisplayed()
            if (generating) {
                compose.onNodeWithText("Generating · composing response").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription(localized(R.string.chat_stop)).performScrollTo().assertIsDisplayed()
            } else {
                compose.onNodeWithContentDescription(localized(R.string.chat_send)).performScrollTo().assertIsEnabled()
            }
        }

        compose.runOnIdle { configure?.invoke(411, 1.6f, false) }
        compose.waitForIdle()
        compose.onNodeWithText("Reason · TOKEN_BUDGET").performScrollTo()
        captureHostSurface("chat-context-inspector-411dp-fontScale-1.6.png")
        compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).performScrollTo().performClick()
        compose.onNodeWithText(localized(R.string.chat_file)).performClick()
        assertEquals("Existing attachment callback remains connected", 1, attachCount.get())
        compose.onNodeWithContentDescription(localized(R.string.chat_send)).performScrollTo().performClick()
        assertEquals("Existing send callback remains connected", 1, sendCount.get())
        compose.runOnIdle {
            configure?.invoke(411, 1.6f, false)
            changeInput?.invoke("")
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(localized(R.string.chat_send)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            changeInput?.invoke("A draft that can be sent")
            configure?.invoke(411, 1.6f, true)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(localized(R.string.chat_stop)).performScrollTo()
            .assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals("Generating state keeps the stop callback", 1, stopCount.get())
        compose.onNodeWithContentDescription(localized(R.string.chat_stop)).assertIsDisplayed()
        captureHostSurface("chat-composer-generating-411dp-fontScale-1.6.png")
    }

    private fun captureHostSurface(filename: String) {
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap!!))
        }
        try {
            val output = File("build/chat-visual-personal-qa-captures").apply { mkdirs() }.resolve(filename)
            output.outputStream().use { stream -> assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
            assertTrue("Host capture should be a nonempty PNG", output.length() > 0)
        } finally {
            bitmap?.recycle()
        }
    }
}
