package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android execution required. Management navigation performs no native inference. */
@RunWith(AndroidJUnit4::class)
class AgentsSkillsNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun localized(id: Int) = compose.activity.getString(id)
    @Test fun builtInSkillsCanBeInspectedWithoutRunningTools() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithText(localized(R.string.navigation_skills)).performScrollTo().performClick()
        compose.onNodeWithText(localized(R.string.skills_import)).assertExists()
        // Built-ins are seeded by SkillRegistry on IO; wait for its Room flow before lookup.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Spreadsheet Analysis").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Spreadsheet Analysis")
            .assertExists()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText(localized(R.string.skills_export)).assertExists()
        compose.onNodeWithText(localized(R.string.skills_save)).assertDoesNotExist()
    }
    @Test fun generalAgentIsProtectedAndValidationIsDeveloperOnly() {
        compose.onNodeWithTag("top-level-settings").performClick()
        compose.onNodeWithText(localized(R.string.skills_agents)).performScrollTo().performClick()
        compose.onNodeWithText("General Agent").performScrollTo().performClick()
        compose.onNodeWithText(localized(R.string.agents_instructions)).assertExists()
        compose.onNodeWithText(localized(R.string.skills_remove)).assertDoesNotExist()
    }
}
