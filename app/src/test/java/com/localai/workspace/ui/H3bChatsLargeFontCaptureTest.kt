package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bChatsLightLargeFontCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesEmptyChatAndProjectListsAndAccessibleNavigation() = captureChatsList(compose, "light")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bChatsDarkLargeFontCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesEmptyChatAndProjectListsAndAccessibleNavigation() = captureChatsList(compose, "dark")
}

private fun captureChatsList(compose: AndroidComposeTestRule<*, MainActivity>, theme: String) {
    compose.onNodeWithTag("top-level-workspace").performClick()
    compose.waitForIdle()
    val context = compose.activity
    compose.onNodeWithTag("workspace-chats-section-title").assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.workspace_no_chats)).assertIsDisplayed()
    compose.onNodeWithTag("workspace-new-chat").assertIsDisplayed()
    compose.onNodeWithTag("workspace-new-project").assertIsDisplayed()
    listOf(
        "home" to R.string.navigation_home,
        "workspace" to R.string.navigation_chats,
        "memory" to R.string.navigation_memory,
        "settings" to R.string.navigation_more,
    ).forEach { (route, labelId) ->
        compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true)
            .assertIsDisplayed().assertTextEquals(context.getString(labelId))
    }
    captureActivity(compose, "h3b-chats-$theme-360dp-fontScale-2.0-overview")

    compose.onNodeWithTag("workspace-projects-section-title").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText(context.getString(R.string.workspace_no_projects)).performScrollTo().assertIsDisplayed()
    captureActivity(compose, "h3b-chats-$theme-360dp-fontScale-2.0-projects")
}

private fun captureActivity(compose: AndroidComposeTestRule<*, MainActivity>, name: String) {
    compose.waitForIdle()
    var bitmap: Bitmap? = null
    compose.runOnUiThread {
        val view = compose.activity.window.decorView
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap!!))
    }
    val directory = File("build/h3b-final-captures").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use {
        check(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
