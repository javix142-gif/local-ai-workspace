package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.localai.workspace.MainActivity
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
    fun capturesLightWorkspaceModelsAndSettingsAtCompactWidth() {
        compose.onNodeWithText("Start chat").assertIsDisplayed()
        capture("workspace-light-360dp-fontScale-1.0")

        compose.onNodeWithText("Models").performClick()
        compose.onNodeWithText("Add model").assertIsDisplayed()
        capture("models-light-360dp-fontScale-1.0")

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Developer / Diagnostics").assertIsDisplayed()
        capture("settings-light-360dp-fontScale-1.0")
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
