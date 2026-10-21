package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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

/** Full-app light-theme captures for the top-level surfaces using AppScreenContent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityThemeCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesLightHomeChatsMoreAndModelsAtCompactWidth() {
        compose.onNodeWithText(compose.activity.getString(R.string.home_title)).assertIsDisplayed()
        capture("h3b-home-light-360dp-fontScale-1.0")

        compose.onNodeWithTag("top-level-workspace").assertTextContains(compose.activity.getString(R.string.navigation_chats)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.workspace_new_chat)).assertIsDisplayed()
        capture("h3b-chats-light-360dp-fontScale-1.0")
        compose.onNodeWithTag("workspace-chats-section-title")
            .assertTextContains(compose.activity.getString(R.string.workspace_chats_title))
            .performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("top-level-memory").assertTextContains(compose.activity.getString(R.string.navigation_memory)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.memory_search)).assertExists()
        capture("h3b-memory-light-360dp-fontScale-1.0")

        compose.onNodeWithTag("top-level-settings").assertTextContains(compose.activity.getString(R.string.navigation_more)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_models_entry)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.navigation_skills)).assertExists()
        capture("h3b-more-light-360dp-fontScale-1.0")

        compose.onNodeWithText(compose.activity.getString(R.string.settings_models_entry)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.models_add)).assertIsDisplayed()
        capture("h3b-models-light-360dp-fontScale-1.0")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap!!))
        }
        val directory = File("build/ux-01-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
