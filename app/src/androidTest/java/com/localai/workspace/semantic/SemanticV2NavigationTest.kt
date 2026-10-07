package com.localai.workspace.semantic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import org.junit.Rule
import org.junit.Test

/** Device UI smoke only. Building this APK does not imply this test has run. */
class SemanticV2NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun modelsExposeExperimentalSemanticDialogWithoutAddingAnotherNavigationRoute() {
        compose.onNodeWithText("Models").performClick()
        compose.onNodeWithText("Semantic search").performClick()
        compose.onNodeWithText("Embedding models").assertIsDisplayed()
        compose.onNodeWithText("EmbeddingGemma 2 740M").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Embedding models").assertDoesNotExist()
    }
}
