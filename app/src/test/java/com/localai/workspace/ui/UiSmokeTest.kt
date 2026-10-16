package com.localai.workspace.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Host-rendered UI checks, not a physical Android/inference benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homePrivacyAndModelImportNavigation() {
        compose.onNodeWithText("Start chat").assertIsDisplayed()
        compose.onNodeWithText("No chats yet").assertExists()
        screenshot("workspace")
        compose.onNodeWithText("Private · Local").performClick()
        compose.onNodeWithText("Inference and imported models", substring = true).assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Models").performClick()
        screenshot("models")
        compose.onNodeWithText("Add model").performClick()
        listOf("FROM DEVICE", "GGUF", "LiteRT-LM", "Multimodal", "ONLINE", "Hugging Face").forEach { compose.onNode(hasText(it) and hasAnyAncestor(isDialog())).assertExists() }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("No models in this view").assertExists()
    }

    @Test fun settingsKeepsDiagnosticsBehindDeveloperEntry() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Local AI Performance").assertDoesNotExist()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Local AI Performance").performClick()
        compose.onNodeWithText("RUNTIME: LiteRT-LM 0.17.1").assertExists()
        compose.onNodeWithText("CPU").assertExists()
        compose.onNodeWithText("GPU Experimental").assertExists()
    }

    @Test fun deviceValidationIsDeveloperOnlyAndHasRealSuiteActions() {
        compose.onNodeWithText("Device Validation").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Device Validation").assertDoesNotExist()
        compose.onNodeWithText("Developer / Diagnostics").performClick()
        compose.onNodeWithText("Device Validation").performClick()
        awaitText("Run Quick Validation")
        compose.onNodeWithText("Run Full Validation").assertExists()
        compose.onNodeWithText("Full iterations").assertExists()
        screenshot("device-validation")
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Developer / Diagnostics").assertExists()
    }

    @Test fun chatComposerAndMenusHaveNoPermanentModelPickerOrFakeTools() {
        compose.onNodeWithText("Start chat").performClick()
        awaitText("Ask anything…")
        compose.onNodeWithText("Model picker").assertDoesNotExist()
        compose.onNodeWithText("Diagnostics").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithText("Ask anything…").performTextInput("Hola")
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
        screenshot("chat")
        compose.onNodeWithContentDescription("Chat options").performClick()
        listOf("Model", "Files", "Memory", "Diagnostics", "Delete chat").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun attachmentMenuOnlyOffersFunctionalSources() {
        compose.onNodeWithText("Start chat").performClick()
        awaitText("Ask anything…")
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithText("File").assertIsDisplayed()
        compose.onNodeWithText("Image").assertDoesNotExist()
        compose.onNodeWithText("Tools").assertDoesNotExist()
        compose.onNodeWithText("Think").assertDoesNotExist()
    }

    @Test fun projectTabsAndApprovedMemoryRemainFunctional() {
        val graph = (compose.activity.application as com.localai.workspace.LocalAiApplication).graph
        val projectId = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { graph.workspace.createProject("Test project") }
        awaitText("Test project")
        compose.onNodeWithText("Test project").performClick()
        awaitText("Files")
        listOf("Chats", "Files", "Memory", "Settings").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("No files yet").assertExists()
        compose.onNodeWithText("Memory").performClick()
        compose.onNodeWithText("No saved memory").assertExists()
        // Fixture is explicitly USER_APPROVED; the real text editor is exercised by the device test.
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            graph.database.memoryDao().upsert(com.localai.workspace.data.MemoryItemEntity(
                id = "approved", scopeType = "PROJECT", scopeId = projectId, content = "Prefiero respuestas breves",
                sourceType = "USER_APPROVED", relevance = 1f, createdAt = 0, updatedAt = 0))
        }
        awaitText("Prefiero respuestas breves")
        compose.onNodeWithContentDescription("Edit memory").assertExists()
        compose.onNodeWithContentDescription("Remove memory").performClick()
        compose.onNodeWithText("Remove memory?").assertExists()
        compose.onNodeWithText("Remove").performClick()
        awaitText("No saved memory")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Current configuration").assertExists()
        screenshot("project-settings")
    }

    @Test fun projectFileRemovalRequiresConfirmationAndKeepsTheOriginalFile() {
        val graph = (compose.activity.application as com.localai.workspace.LocalAiApplication).graph
        val projectId = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { graph.workspace.createProject("Remove file UI test") }
        val displayName = "private-note-${System.currentTimeMillis()}.txt"
        val original = File(compose.activity.cacheDir, displayName).apply { writeText("original user file") }
        val imported = File(compose.activity.filesDir, "documents/$displayName").apply {
            parentFile!!.mkdirs(); writeText("private imported copy")
        }
        val documentId = "ui-remove-${System.currentTimeMillis()}"
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            graph.database.documentDao().insert(com.localai.workspace.data.DocumentEntity(
                id = documentId, projectId = projectId, displayName = displayName, sourceUri = android.net.Uri.fromFile(original).toString(),
                localPath = imported.path, mimeType = "text/plain", fileHash = "a".repeat(64), byteSize = imported.length(),
                extractionStatus = "READY", indexingStatus = "READY", importedAt = 1, updatedAt = 1,
            ))
            graph.database.documentDao().insertSegments(listOf(com.localai.workspace.data.DocumentSegmentEntity(
                documentId = documentId, segmentIndex = 0, text = "Private imported copy", normalizedText = "private imported copy", contentHash = "b".repeat(64),
            )))
        }
        try {
            awaitText("Remove file UI test")
            compose.onNodeWithText("Remove file UI test").performClick()
            awaitText("Files")
            compose.onNodeWithText("Files").performClick()
            compose.onNodeWithText(displayName).performClick()
            compose.onNodeWithText("Remove file").performClick()
            compose.onNodeWithText("Remove file from workspace?").assertIsDisplayed()
            compose.onNodeWithText("The original file on your device is not deleted", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Keep file").performClick()
            compose.onNodeWithText(displayName).assertIsDisplayed()
            assertTrue("cancel leaves the private copy intact", imported.isFile)
            compose.onNodeWithText("Remove file").performClick()
            compose.onNodeWithText("Remove file from workspace?").assertIsDisplayed()
            compose.onNodeWithText("Remove file").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(displayName).fetchSemanticsNodes().isEmpty() }
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                assertNull(graph.database.documentDao().get(documentId))
                assertTrue(graph.database.documentDao().segmentsForDocument(documentId).isEmpty())
            }
            assertFalse("the app-private imported copy is removed", imported.exists())
            assertTrue("the source file remains untouched", original.isFile)
        } finally {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                graph.workspace.deleteWorkspace(projectId)
            }
            original.delete()
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        var bitmap: Bitmap? = null
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap!!))
        }
        val dir = File("build/ui-stage15-screenshots"); dir.mkdirs()
        File(dir, "$name.png").outputStream().use { assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}

/** Real Room Binder, supplied before graph startup: Robolectric otherwise supplies a null component. */
class MultiProcessUiTestApplication : com.localai.workspace.LocalAiApplication() {
 override fun onCreate() {
  val service=androidx.room.MultiInstanceInvalidationService()
  org.robolectric.Shadows.shadowOf(this).setComponentNameAndServiceForBindService(
   android.content.ComponentName(this,androidx.room.MultiInstanceInvalidationService::class.java),service.onBind(android.content.Intent()))
  super.onCreate()
 }
}
