package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android execution required. Management navigation performs no native inference. */
@RunWith(AndroidJUnit4::class)
class AgentsSkillsNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun builtInSkillsCanBeInspectedWithoutRunningTools() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Skills").performScrollTo().performClick()
        compose.onNodeWithText("Import SKILL.md").assertExists()
        compose.onNodeWithText("Spreadsheet Analysis").performScrollTo().performClick()
        compose.onNodeWithText("Export").assertExists()
        compose.onNodeWithText("Save").assertDoesNotExist()
    }
    @Test fun generalAgentIsProtectedAndValidationIsDeveloperOnly() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Logical Agents").performScrollTo().performClick()
        compose.onNodeWithText("General Agent").performScrollTo().performClick()
        compose.onNodeWithText("Agent instructions").assertExists()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }
}
