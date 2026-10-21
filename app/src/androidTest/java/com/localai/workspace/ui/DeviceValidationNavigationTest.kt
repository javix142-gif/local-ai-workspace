package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import org.junit.Rule
import org.junit.Test

/** Navigation only: no model fixtures, fake device PASS or automatic inference. */
class DeviceValidationNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun localized(id: Int) = compose.activity.getString(id)
    @Test fun developerEntryOpensQuickAndFullScreen() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithText("Device Validation").assertDoesNotExist()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Device Validation").performClick()
        compose.onNodeWithText("Run Quick Validation").assertExists()
        compose.onNodeWithText("Run Full Validation").assertExists()
        compose.onNodeWithText("Back").performClick()
    }
}
