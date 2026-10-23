package com.localai.workspace.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMoreScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun moreShowsRealFeaturesAndPermissionStateWithoutRequestingPermissions() {
        compose.onNodeWithTag("top-level-settings").performClick()
        listOf(
            "more-actions-section",
            "more-connections-section",
            "more-assistant-section",
            "more-privacy-section",
            "more-advanced-section",
        ).forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }

        val context = compose.activity
        val calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val granted = context.getString(R.string.more_permission_granted)
        val requestedWhenUsed = context.getString(R.string.more_permission_requested_when_used)
        val expectedStatuses = listOf(
            if (calendarGranted) granted else requestedWhenUsed,
            if (contactsGranted) granted else requestedWhenUsed,
        )
        expectedStatuses.distinct().forEach { status ->
            compose.onAllNodesWithText(status).assertCountEquals(expectedStatuses.count { it == status })
        }
        compose.onNodeWithText(context.getString(R.string.personal_data_permission_request)).assertDoesNotExist()

        val density = context.resources.displayMetrics.density
        listOf("more-calendar-connection", "more-contacts-connection", "more-models-entry", "more-memory-entry").forEach { tag ->
            val heightDp = compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().boundsInRoot.height / density
            assertTrue("$tag touch target was ${heightDp}dp", heightDp >= 48f)
        }

        capture("h3b-more-light-360dp-fontScale-1.0")
        compose.onNodeWithTag("more-phone-actions").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.more_phone_actions_detail)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_close)).performClick()

        compose.onNodeWithTag("more-skills-open").performScrollTo().performClick()
        compose.onNodeWithTag("skills-create-conversation").assertIsNotEnabled()
        compose.onNodeWithTag("skills-create-unavailable").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.skills_import)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.skills_create_manual)).assertIsDisplayed()
    }

    private fun capture(name: String) {
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

}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMoreLightLargeFontCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesMoreAtLightFontScaleTwoWithScrollableControls() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithTag("more-actions-section").assertIsDisplayed()
        assertBottomLabelsVisible()
        capture("h3b-more-light-360dp-fontScale-2.0-overview")
        compose.onNodeWithTag("more-calendar-connection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more-models-entry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more-advanced-toggle").performScrollTo().assertIsDisplayed()
        capture("h3b-more-light-360dp-fontScale-2.0-advanced")
        compose.onNodeWithTag("more-skills-open").performScrollTo().performClick()
        compose.onNodeWithTag("skills-create-conversation").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("skills-create-unavailable").assertIsDisplayed()
        assertBottomLabelsVisible()
    }

    private fun assertBottomLabelsVisible() {
        listOf("home", "workspace", "memory", "settings").forEach { route ->
            compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun capture(name: String) {
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

}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bMoreDarkLargeFontCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesMoreAtDarkFontScaleTwoWithScrollableControls() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithTag("more-actions-section").assertIsDisplayed()
        listOf("home", "workspace", "memory", "settings").forEach { route ->
            compose.onNodeWithTag("top-level-label-$route", useUnmergedTree = true).assertIsDisplayed()
        }
        capture("h3b-more-dark-360dp-fontScale-2.0-overview")
        compose.onNodeWithTag("more-calendar-connection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more-models-entry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("more-advanced-toggle").performScrollTo().assertIsDisplayed()
        capture("h3b-more-dark-360dp-fontScale-2.0-advanced")
    }

    private fun capture(name: String) {
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
}
