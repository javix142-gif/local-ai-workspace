package com.localai.workspace.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.localai.workspace.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Component-only host captures; these are not full-app or device screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatHeaderComposerCompactCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun localized(id: Int): String = compose.activity.getString(id)

    @Test
    fun capturesLightAndDarkAt360DpAndFontScale1() {
        var setAppearance: ((Boolean, Float) -> Unit)? = null
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(1f) }
            SideEffect { setAppearance = { isDark, scale -> dark = isDark; fontScale = scale } }
            ChatHeaderComposerPreview(dark, fontScale)
        }

        listOf(false to "light", true to "dark").forEach { (dark, label) ->
            compose.runOnIdle { setAppearance?.invoke(dark, 1f) }
            compose.waitForIdle()
            assertControlsVisible()
            capture("chat-header-composer-360dp-$label-fontScale-1.0.png")
        }
    }

    private fun assertControlsVisible() {
        compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-header-model", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("assistant-options-button").assertIsDisplayed()
        compose.onNodeWithTag("assistant-options-summary").assertTextContains(
            "${compose.activity.getString(R.string.chat_thinking_summary, localized(R.string.chat_thinking_auto))} · " +
                compose.activity.getString(R.string.chat_tools_summary, localized(R.string.chat_tools_auto)),
        )
        compose.onNodeWithText(localized(R.string.chat_input_hint)).assertIsDisplayed()
        compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).assertIsDisplayed()
        compose.onNodeWithContentDescription(localized(R.string.chat_send)).assertIsDisplayed()
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
            val folder = File("build/ux-02a-captures").apply { mkdirs() }
            val file = File(folder, filename)
            file.outputStream().use { output -> assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            assertTrue("Expected a nonempty host PNG: ${file.path}", file.length() > 0)
        } finally {
            bitmap?.recycle()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatHeaderComposerLargeFontCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun localized(id: Int): String = compose.activity.getString(id)

    @Test
    fun capturesLightAndDarkAt411DpAndFontScale1_6() {
        var setAppearance: ((Boolean, Float) -> Unit)? = null
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(1.6f) }
            SideEffect { setAppearance = { isDark, scale -> dark = isDark; fontScale = scale } }
            ChatHeaderComposerPreview(dark, fontScale)
        }

        listOf(false to "light", true to "dark").forEach { (dark, label) ->
            compose.runOnIdle { setAppearance?.invoke(dark, 1.6f) }
            compose.waitForIdle()
            compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-conversation", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-model", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("assistant-options-button").assertIsDisplayed()
            compose.onNodeWithText(localized(R.string.chat_input_hint)).assertIsDisplayed()
            compose.onNodeWithContentDescription(localized(R.string.chat_add_attachment)).assertIsDisplayed()
            compose.onNodeWithContentDescription(localized(R.string.chat_send)).assertIsDisplayed()
            capture("chat-header-composer-411dp-$label-fontScale-1.6.png")
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
            val folder = File("build/ux-02a-captures").apply { mkdirs() }
            val file = File(folder, filename)
            file.outputStream().use { output -> assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            assertTrue("Expected a nonempty host PNG: ${file.path}", file.length() > 0)
        } finally {
            bitmap?.recycle()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChatHeaderComposerPreview(dark: Boolean, fontScale: Float) {
    LocalAiTheme(darkTheme = dark) {
        val density = LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Scaffold(
                topBar = {
                    ChatTopBar(
                        navigationIcon = {
                            IconButton(onClick = {}, modifier = Modifier.testTag("preview-back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            ChatHeaderTitle(
                                agentTitle = { Text("General Agent") },
                                conversationTitle = "A long conversation title that wraps naturally across the available chat header width",
                                modelStatus = "Gemma 4 E2B · Local · Ready · selected model status remains readable",
                                onModelClick = {},
                                enabled = true,
                            )
                        },
                        actions = {
                            IconButton(onClick = {}, modifier = Modifier.testTag("preview-chat-actions")) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                            }
                        },
                    )
                },
                bottomBar = {
                    Column {
                        ChatComposer(
                            input = "",
                            generating = false,
                            onInputChange = {},
                            onAttach = {},
                            onAttachImage = {},
                            imageEnabled = true,
                            onFiles = {},
                            onStop = {},
                            onSend = {},
                            contextualControls = {
                                AssistantControls(
                                    profile = com.localai.workspace.data.AssistantProfile(),
                                    toolsAvailable = true,
                                    thinkingAvailable = true,
                                    enabled = true,
                                    onChange = {},
                                )
                            },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).testTag("preview-chat-body"), contentAlignment = Alignment.Center) {
                    Text("Conversation content", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
