package com.localai.workspace

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import com.localai.workspace.ui.ChatViewModel
import com.localai.workspace.ui.LocalAiTheme
import com.localai.workspace.ui.MultiProcessUiTestApplication
import com.localai.workspace.ui.components.AwsBottomBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real Skill-authoring ChatScreen with a synthetic project and no model. */
private fun captureSkillScreen(
    compose: AndroidComposeTestRule<*, ComponentActivity>,
    name: String,
    expectedScale: Float,
) {
    assertEquals(expectedScale, compose.activity.resources.configuration.fontScale, 0f)
    val graph = (compose.activity.application as LocalAiApplication).graph
    val projectId = runBlocking(Dispatchers.IO) { graph.workspace.createProject("SINTÉTICO · captura H3b") }
    lateinit var viewModel: ChatViewModel
    compose.runOnUiThread { viewModel = graph.chatSessions.get(projectId, null, skillAuthoring = true) }
    compose.setContent {
        LocalAiTheme {
            val navController = rememberNavController()
            Scaffold(bottomBar = { AwsBottomBar("workspace", onDestinationSelected = {}) }) { padding ->
                Box(Modifier.padding(padding)) { ChatScreen(viewModel, navController, skillAuthoringMode = true) }
            }
        }
    }
    compose.onNodeWithTag("skill-authoring-prompt-title").assertExists()
    compose.onNodeWithTag("chat-model-unavailable").assertExists()
    compose.onNodeWithTag("chat-composer-input").assertIsNotEnabled()
    compose.waitForIdle()
    writeSkillCapture(compose, name)
    compose.onNodeWithText(compose.activity.getString(R.string.chat_skill_prompt_local))
        .performScrollTo().assertIsDisplayed()
    if (expectedScale >= 2f) writeSkillCapture(compose, "$name-scrolled")
}

private fun writeSkillCapture(compose: AndroidComposeTestRule<*, ComponentActivity>, name: String) {
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
        val view = compose.activity.window.decorView
        require(view.width > 0 && view.height > 0)
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(requireNotNull(bitmap)))
    }
    val output = File("build/h3b-current-skill-captures").apply { mkdirs() }.resolve("$name.png")
    try {
        output.outputStream().use { check(requireNotNull(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        bitmap?.recycle()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bSkillLightScaleOneCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun capture() = captureSkillScreen(compose, "SYNTHETIC-skill-light-360dp-fontScale-1.0", 1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bSkillDarkScaleOneCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun capture() = captureSkillScreen(compose, "SYNTHETIC-skill-dark-360dp-fontScale-1.0", 1f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bSkillLightScaleTwoCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun capture() = captureSkillScreen(compose, "SYNTHETIC-skill-light-360dp-fontScale-2.0", 2f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bSkillDarkScaleTwoCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun capture() = captureSkillScreen(compose, "SYNTHETIC-skill-dark-360dp-fontScale-2.0", 2f)
}
