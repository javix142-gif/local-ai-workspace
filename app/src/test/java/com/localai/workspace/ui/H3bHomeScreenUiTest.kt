package com.localai.workspace.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import com.localai.workspace.MainActivity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pendingMemoryCountUsesSpanishSingularAndPluralForms() {
        val resources = compose.activity.resources
        assertEquals("1 recuerdo por revisar", resources.getQuantityString(R.plurals.home_pending_memories, 1, 1))
        assertEquals("2 recuerdos por revisar", resources.getQuantityString(R.plurals.home_pending_memories, 2, 2))
    }

    @Test
    fun homeUsesActualCalendarPermissionAndModelStateAndKeepsActionsReachable() {
        compose.onNodeWithTag("home-greeting").assertIsDisplayed()
        compose.onNodeWithTag("home-date").assertIsDisplayed()
        compose.onNodeWithTag("home-calendar-card").assertIsDisplayed()
        compose.onNodeWithTag("top-level-home").assertExists()

        val hasCalendarPermission = ContextCompat.checkSelfPermission(
            compose.activity,
            android.Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCalendarPermission) {
            compose.onNodeWithTag("home-calendar-permission").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("home-calendar-event").assertDoesNotExist()
        } else {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("home-calendar-loading").fetchSemanticsNodes().isEmpty()
            }
            val hasRealEvents = compose.onAllNodesWithTag("home-calendar-event").fetchSemanticsNodes().isNotEmpty()
            val isEmpty = compose.onAllNodesWithTag("home-calendar-empty").fetchSemanticsNodes().isNotEmpty()
            val unavailable = compose.onAllNodesWithTag("home-calendar-unavailable").fetchSemanticsNodes().isNotEmpty()
            assertTrue(
                "Calendar must show provider data, an empty state, or an explicit read error.",
                hasRealEvents || isEmpty || unavailable,
            )
        }

        compose.onNodeWithTag("home-start-chat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-start-chat").assertIsNotEnabled()
        compose.onNodeWithTag("home-model-missing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-add-model").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-open-chats").performScrollTo().assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeLightRegularCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesActualHomeAtLightThemeScaleOne() = captureHome("h3b-home-light-360dp-fontScale-1.0", 1f)

    private fun captureHome(name: String, expectedScale: Float) {
        assertEquals(expectedScale, compose.activity.resources.configuration.fontScale, 0f)
        compose.onNodeWithTag("home-greeting").assertIsDisplayed()
        compose.onNodeWithTag("home-calendar-card").assertIsDisplayed()
        compose.waitForIdle()
        writeFullActivityCapture(compose.activity, name)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 1f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeDarkRegularCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesActualHomeAtDarkThemeScaleOne() {
        assertEquals(1f, compose.activity.resources.configuration.fontScale, 0f)
        compose.onNodeWithTag("home-greeting").assertIsDisplayed()
        compose.onNodeWithTag("home-calendar-card").assertIsDisplayed()
        compose.waitForIdle()
        writeFullActivityCapture(compose.activity, "h3b-home-dark-360dp-fontScale-1.0")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeLargeFontLightCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesActualHomeAtLightThemeScaleTwoAndActionsRemainScrollable() {
        assertEquals(2f, compose.activity.resources.configuration.fontScale, 0f)
        compose.onNodeWithTag("home-greeting").assertIsDisplayed()
        compose.onNodeWithTag("home-calendar-card").assertIsDisplayed()
        compose.waitForIdle()
        writeFullActivityCapture(compose.activity, "h3b-home-light-360dp-fontScale-2.0")
        compose.onNodeWithTag("home-start-chat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-open-chats").performScrollTo().assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class H3bHomeLargeFontDarkCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesActualHomeAtDarkThemeScaleTwoAndActionsRemainScrollable() {
        assertEquals(2f, compose.activity.resources.configuration.fontScale, 0f)
        compose.onNodeWithTag("home-greeting").assertIsDisplayed()
        compose.onNodeWithTag("home-calendar-card").assertIsDisplayed()
        compose.waitForIdle()
        writeFullActivityCapture(compose.activity, "h3b-home-dark-360dp-fontScale-2.0")
        compose.onNodeWithTag("home-start-chat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-open-chats").performScrollTo().assertIsDisplayed()
    }
}

private fun writeFullActivityCapture(activity: MainActivity, name: String) {
    var bitmap: Bitmap? = null
    activity.runOnUiThread {
        val view = activity.window.decorView
        bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap!!))
    }
    val directory = h3bCaptureDirectory().apply { mkdirs() }
    File(directory, "$name.png").outputStream().use {
        check(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
