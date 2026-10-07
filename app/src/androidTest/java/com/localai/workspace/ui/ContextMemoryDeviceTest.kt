package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Built on host, executed only on Android. No inference or private fixture exports. */
@RunWith(AndroidJUnit4::class)
class ContextMemoryDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun structuredMemoryEntryAndEditorAreAccessible() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Structured memory").performClick()
        compose.onNodeWithText("Search saved memory").assertExists()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Content").assertExists()
        compose.onNodeWithText("Expires in days (optional)").assertExists()
        compose.onNodeWithText("Scope: user").performClick()
        compose.onNodeWithText("Global · this device").performClick()
        compose.onNodeWithText("Scope: global").assertExists()
    }
    @Test fun inspectorDoesNotStartGenerationAutomatically() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Context Inspector / Memory Validation").performScrollTo().performClick()
        compose.onNodeWithText("Build context · no generation").assertExists().assertIsNotEnabled()
        compose.onNodeWithText("Run with Gemma · explicit").assertDoesNotExist()
    }
}
