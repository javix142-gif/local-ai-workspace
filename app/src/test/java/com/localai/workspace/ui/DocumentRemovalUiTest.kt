package com.localai.workspace.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.localai.workspace.MainActivity
import com.localai.workspace.LocalAiApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MultiProcessUiTestApplication::class, qualifiers = "w360dp-h800dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentRemovalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun removingImportedFileRequiresConfirmationAndPreservesItsOriginal() {
        val graph = (compose.activity.application as LocalAiApplication).graph
        val projectId = runBlocking(Dispatchers.IO) { graph.workspace.createProject("Remove file UI test") }
        val displayName = "private-note-${System.currentTimeMillis()}.txt"
        val original = File(compose.activity.cacheDir, displayName).apply { writeText("original user file") }
        val imported = File(compose.activity.filesDir, "documents/$displayName").apply {
            parentFile!!.mkdirs(); writeText("private imported copy")
        }
        val documentId = "ui-remove-${System.currentTimeMillis()}"
        runBlocking(Dispatchers.IO) {
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
            compose.onNode(hasText(displayName) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            assertTrue("cancel leaves the private copy intact", imported.isFile)
            compose.onNodeWithText("Remove file").performClick()
            compose.onNodeWithText("Remove file from workspace?").assertIsDisplayed()
            compose.onNodeWithText("Remove file").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(displayName).fetchSemanticsNodes().isEmpty() }
            runBlocking(Dispatchers.IO) {
                assertNull(graph.database.documentDao().get(documentId))
                assertTrue(graph.database.documentDao().segmentsForDocument(documentId).isEmpty())
            }
            assertFalse("the app-private imported copy is removed", imported.exists())
            assertTrue("the source file remains untouched", original.isFile)
        } finally {
            runBlocking(Dispatchers.IO) { graph.workspace.deleteWorkspace(projectId) }
            original.delete()
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
