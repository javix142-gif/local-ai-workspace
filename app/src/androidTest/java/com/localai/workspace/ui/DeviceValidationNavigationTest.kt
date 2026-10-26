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
        compose.onNodeWithText(localized(R.string.more_device_validation)).assertDoesNotExist()
        compose.onNodeWithTag("more-advanced-toggle").performScrollTo().performClick()
        compose.onNodeWithText(localized(R.string.more_device_validation)).performScrollTo().performClick()
        compose.onNodeWithText(localized(R.string.device_validation_quick)).assertExists()
        compose.onNodeWithText(localized(R.string.device_validation_full)).assertExists()
        compose.onNodeWithText(localized(R.string.chat_back)).performClick()
    }
}
