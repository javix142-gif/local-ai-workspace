package com.localai.workspace.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.localai.workspace.MainActivity
import com.localai.workspace.data.AssistantProfile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatRefinementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun compactComposerAndNestedInsetsKeepConversationAndActionsVisible() {
        var configure: ((Int, Float, Boolean, Boolean) -> Unit)? = null
        var send = 0; var stop = 0; var attach = 0
        var observedProfile = AssistantProfile()
        compose.setContent {
            var width by remember { mutableStateOf(360) }
            var scale by remember { mutableStateOf(1f) }
            var dark by remember { mutableStateOf(false) }
            var generating by remember { mutableStateOf(false) }
            var input by remember { mutableStateOf("A draft") }
            var profile by remember { mutableStateOf(AssistantProfile()) }
            val base = LocalDensity.current
            SideEffect { configure = { w, s, d, g -> width = w; scale = s; dark = d; generating = g } }
            LocalAiTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                    Box(Modifier.width(width.dp).fillMaxHeight().testTag("refinement-viewport")) {
                        // Same nested Scaffold padding/consumption as the production root/chat.
                        // A reserved bottom inset tests layout under keyboard-like constraints;
                        // it is not a claim of physical IME validation.
                        Scaffold(contentWindowInsets = WindowInsets(top = 24.dp, bottom = 120.dp)) { outer ->
                            Scaffold(Modifier.padding(outer).consumeWindowInsets(outer),
                                topBar = { ChatTopBar(modifier = Modifier.testTag("refinement-header"), title = {
                                    ChatHeaderTitle({ Text("General Agent") },
                                        "Conversation title that wraps without hiding the first message",
                                        "Gemma 4 E2B · Local · Ready", {}, true)
                                }) },
                                bottomBar = {
                                    ChatComposer(input, generating, { input = it }, { attach++ }, {}, true,
                                        onStop = { stop++ }, onSend = { send++ },
                                        contextualControlsInline = true,
                                        contextualControls = { AssistantControls(profile, true, true, !generating,
                                            compact = true, onChange = { profile = it; observedProfile = it }) })
                                }) { inner ->
                                LazyColumn(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner),
                                    contentPadding = PaddingValues(16.dp)) {
                                    item { ChatMessageSurface(true) { Text("Beginning of conversation", Modifier.testTag("first-message")) } }
                                    item { ChatMessageSurface(false) { Text("The response has room to breathe. Paragraphs remain legible and keep their full content. ".repeat(3)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(Triple(360, 1f, false), Triple(393, 1.3f, true),
            Triple(411, 1.6f, true), Triple(360, 2f, true)).forEach { (width, scale, dark) ->
            compose.runOnIdle { configure!!(width, scale, dark, false) }
            compose.waitForIdle()
            val header = compose.onNodeWithTag("refinement-header").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("first-message", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val composer = compose.onNodeWithTag("chat-composer-surface").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("First message starts below the header", first.top >= header.bottom)
            assertTrue("First message is not hidden by composer", first.bottom <= composer.top)
            compose.onNodeWithTag("chat-header-model", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-header-agent", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-composer-input").assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithContentDescription("Add attachment").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("assistant-options-button").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            if (scale == 1f) {
                compose.onNodeWithTag("chat-composer-surface").assertHeightIsEqualTo(56.dp)
            }
            capture("chat-${width}dp-scale-$scale-${if (dark) "dark" else "light"}.png")
        }
        compose.runOnIdle { configure!!(360, 1f, false, false) }
        // Editable fields/popups use explicit settled frames on the host. Keep
        // all visibility and callback assertions while controlling animation time.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("chat-composer-input").performTextReplacement("A new draft")
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Send").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, send)
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("File").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, attach)
        compose.onNodeWithTag("assistant-options-button").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Tools · Auto").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Off").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertTrue(observedProfile.tools.isEmpty())
        compose.onNodeWithText("Done").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("General Agent", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { configure!!(360, 1f, false, true) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Stop").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, stop)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap!!))
        }
        try {
            File("build/chat-refinement-captures").apply { mkdirs() }.resolve(name).outputStream().use {
                assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap?.recycle() }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceRefinementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun compactWorkspaceKeepsBothCreationActionsAndProjectDialog() =
        inspectWorkspace("workspace-light-360dp.png")

    @Test @Config(qualifiers = "w411dp-h900dp-night")
    fun darkWorkspaceKeepsBothCreationActionsAndProjectDialog() =
        inspectWorkspace("workspace-dark-411dp.png")

    private fun inspectWorkspace(captureName: String) {
        val chat = compose.onNodeWithText("Start chat").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val project = compose.onNodeWithText("Start project").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Creation actions share one row", kotlin.math.abs(chat.top - project.top) < 2f)
        assertTrue("Creation actions do not overlap", chat.right < project.left)
        compose.onNodeWithText("Chats").assertIsDisplayed()
        compose.onNodeWithText("Projects").assertIsDisplayed()
        // Complete real UI interactions before software-canvas snapshot capture.
        compose.onNodeWithText("Start project").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Project name").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Start chat").assertIsDisplayed()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/chat-refinement-captures").apply { mkdirs() }.resolve(captureName)
                .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
