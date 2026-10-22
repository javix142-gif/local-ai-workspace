package com.localai.workspace.semantic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import com.localai.workspace.R
import org.junit.Rule
import org.junit.Test

/** Device UI smoke only. Building this APK does not imply this test has run. */
class SemanticV2NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun modelsExposeExperimentalSemanticDialogWithoutAddingAnotherNavigationRoute() {
        compose.onNodeWithTag("top-level-settings").performClick()
        val modelsEntry = compose.activity.getString(R.string.settings_models_entry)
        compose.onNodeWithText(modelsEntry, substring = true).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(modelsEntry, substring = true)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.models_title)).assertIsDisplayed()
        compose.onNodeWithText("Semantic search").performClick()
        compose.onNodeWithText("Embedding models").assertIsDisplayed()
        compose.onNodeWithText("EmbeddingGemma 2 740M").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Embedding models").assertDoesNotExist()
    }
}
