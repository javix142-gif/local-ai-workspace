package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localai.workspace.MainActivity
import com.localai.workspace.LocalAiApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PHYSICAL_DEVICE_TEST_REQUIRED: modal input/IME is not validated by the host smoke suite. */
@RunWith(AndroidJUnit4::class)
class ProjectUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createProjectAndExplicitlySaveEditRemoveMemory() {
        val name = "UI validation ${System.currentTimeMillis()}"
        compose.onNodeWithText("Start project").performClick()
        compose.onNodeWithText("Project name").performTextInput(name)
        compose.onNodeWithText("Create").performClick()
        awaitText("Files")
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("No files yet").assertExists()
        compose.onNodeWithText("Memory").performClick()
        compose.onNodeWithContentDescription("Add memory").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Approved UI validation memory")
        compose.onNodeWithText("Save").performClick()
        awaitText("Approved UI validation memory")
        val graph = (compose.activity.application as LocalAiApplication).graph
        val memoryId = runBlocking(Dispatchers.IO) {
            val project = graph.workspace.activeProjects.first().single { it.name == name }
            graph.database.memoryDao().relevant(project.id, 100).single {
                it.scopeId == project.id && it.content == "Approved UI validation memory"
            }.id
        }
        compose.onNode(hasContentDescription("Edit memory") and hasAnyAncestor(hasTestTag("memory-$memoryId"))).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Edited UI validation memory")
        compose.onNodeWithText("Save").performClick()
        awaitText("Edited UI validation memory")
        compose.onNode(hasContentDescription("Remove memory") and hasAnyAncestor(hasTestTag("memory-$memoryId"))).performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Edited UI validation memory").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Current configuration").assertExists()
        compose.onNodeWithContentDescription("Project options").performClick()
        compose.onNodeWithText("Delete project").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Start project").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).assertDoesNotExist()
    }

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
