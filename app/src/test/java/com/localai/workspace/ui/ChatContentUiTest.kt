package com.localai.workspace.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
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
        compose.onNodeWithText("Think: Auto").performClick(); compose.onNodeWithText("OFF").performClick(); compose.onNodeWithText("Think: Off").assertExists()
        compose.onNodeWithText("Tools: Auto").performClick(); compose.onNodeWithText("Off").performClick(); compose.onNodeWithText("Done").performClick(); compose.onNodeWithText("Tools: Off").assertExists()
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

    @Test fun longInputLargeFontAndGenerationKeepComposerActionsVisible() {
        var stopped = false
        compose.setContent { LocalAiTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                var input by remember { mutableStateOf("Long input\n".repeat(20)) }
                ChatComposer(input, true, { input = it }, {}, {}, false, onStop = { stopped = true }, onSend = {})
            }
        } }
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
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
