package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import org.junit.Rule
import org.junit.Test

/** Navigation only: no model fixtures, fake device PASS or automatic inference. */
class DeviceValidationNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun developerEntryOpensQuickAndFullScreen() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Device Validation").assertDoesNotExist()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Device Validation").performClick()
        compose.onNodeWithText("Run Quick Validation").assertExists()
        compose.onNodeWithText("Run Full Validation").assertExists()
        compose.onNodeWithText("Back").performClick()
    }
}
