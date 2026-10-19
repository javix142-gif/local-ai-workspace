package com.localai.workspace.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.artifacts.ArtifactSpec
import com.localai.workspace.artifacts.ArtifactStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ArtifactRevisionPreviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val project="revision-preview-${UUID.randomUUID()}"
    private val store get()=ArtifactStore(context)

    @After fun removeSyntheticProject() {
        File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray())}").deleteRecursively()
    }

    @Test fun reviewShowsExactBeforeAndAfterAndClosingKeepsSavedRevisionUnchanged() {
        val originalBody="fun answer() = 42\n"
        val proposedBody="fun answer() = 43\n"
        val original=store.create(project,ArtifactSpec.parse(JSONObject()
            .put("format","CODE").put("name","answer.kt").put("body",originalBody)))
        val originalBytes=store.verifiedFile(project,original).readBytes()
        var dismissed=false

        compose.setContent { LocalAiTheme { ArtifactsDialog(project,onDismiss={dismissed=true}) } }
        val revisionTag="artifact-${original.id}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(revisionTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(revisionTag).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("artifact-spec-editor").fetchSemanticsNodes().isNotEmpty() }

        val proposed=ArtifactSpec.parse(JSONObject()
            .put("format","CODE").put("name","answer.kt").put("body",proposedBody))
        compose.onNodeWithTag("artifact-spec-editor").performScrollTo()
            .performTextReplacement(proposed.json().toString(2))
        compose.onNodeWithText("Preview changes").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Structure and supplied-image preview (not page/slide layout rendering)")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Saved v1 · before").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("artifact-preview").performScrollTo().assertTextEquals(proposedBody)
        compose.onNodeWithText(originalBody).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Line-by-line changes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Removed: fun answer() = 42").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Added: fun answer() = 43").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export saved v1").performScrollTo().assertIsDisplayed()
        assertEquals("Reviewing a proposal must not create a revision",1,store.history(project,original.id).size)
        assertArrayEquals("Preview is read-only",originalBytes,store.verifiedFile(project,original).readBytes())

        // Close is the AlertDialog confirm button, outside the scrollable body.
        compose.onNodeWithText("Close").performClick()
        assertTrue("The dialog exposes its normal cancel/dismiss callback",dismissed)
        assertEquals("Closing a proposal must not persist it",1,store.history(project,original.id).size)
        assertArrayEquals("Cancel preserves the original saved bytes",originalBytes,store.verifiedFile(project,original).readBytes())
        assertEquals(original.sha256,ArtifactStore.hash(store.verifiedFile(project,original).readBytes()))
    }

    @Test fun oversizedLineDiffFallsBackWithoutTruncatingBeforeOrAfterPreview() {
        val originalBody=(0..256).joinToString("\n") { "val item$it = $it" }
        val proposedBody=originalBody.replace("val item256 = 256","val item256 = 257")
        val original=store.create(project,ArtifactSpec.parse(JSONObject()
            .put("format","CODE").put("name","large.kt").put("body",originalBody)))

        compose.setContent { LocalAiTheme { ArtifactsDialog(project,onDismiss={}) } }
        val revisionTag="artifact-${original.id}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(revisionTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(revisionTag).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("artifact-spec-editor").fetchSemanticsNodes().isNotEmpty() }
        val proposed=ArtifactSpec.parse(JSONObject()
            .put("format","CODE").put("name","large.kt").put("body",proposedBody))
        compose.onNodeWithTag("artifact-spec-editor").performScrollTo()
            .performTextReplacement(proposed.json().toString(2))
        compose.onNodeWithText("Preview changes").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("artifact-diff-unavailable").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("artifact-diff-unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("artifact-saved-preview").performScrollTo().assertTextEquals(originalBody)
        compose.onNodeWithTag("artifact-preview").performScrollTo().assertTextEquals(proposedBody)
        assertEquals("Review remains read-only",1,store.history(project,original.id).size)
    }
}
