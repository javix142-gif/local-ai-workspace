package com.localai.workspace.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.localai.workspace.artifacts.ArtifactSpec
import com.localai.workspace.artifacts.ArtifactStore
import com.localai.workspace.artifacts.CodePatchEdit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class ArtifactsPatchPreviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=RuntimeEnvironment.getApplication()
    private val project="patch-ui-${UUID.randomUUID()}"
    private val body="fun answer() = 42\n"
    private val store get()=ArtifactStore(context)
    private val revision by lazy { store.create(project,ArtifactSpec.parse(JSONObject().put("format","CODE").put("name","answer.kt").put("body",body))) }
    private val patch by lazy { store.stageCodePatch(project,revision.id,revision.version,revision.sha256,listOf(CodePatchEdit("fun answer() = 42","fun answer() = 43"))) }

    @After fun cleanupSyntheticProject() {
        File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray())}").deleteRecursively()
    }

    @Test fun previewShowsExactBeforeAndAfterAndOnlyAppliesAfterUserAction() {
        val preview=patch
        compose.setContent { LocalAiTheme { ArtifactsDialog(project,onDismiss={}) } }
        compose.onNodeWithText("Code patch previews · not applied").assertIsDisplayed()
        val previewTag="artifact-patch-${preview.id}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(previewTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(previewTag).performScrollTo().performClick()
        compose.onNodeWithTag("artifact-patch-review-title").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("artifact-patch-before").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("artifact-patch-before").assertTextEquals(body)
        compose.onNodeWithTag("artifact-patch-after").performScrollTo().assertTextEquals("fun answer() = 43\n")
        assertEquals("Before explicit review action there is only the original revision",1,store.history(project,revision.id).size)
        assertEquals(body,store.verifiedFile(project,revision).readText())

        compose.onNodeWithTag("artifact-patch-apply").performClick()
        compose.waitUntil(10_000) { store.history(project,revision.id).size==2 }
        compose.onNodeWithTag("artifact-status").assertTextContains("new immutable private draft v2")
        assertEquals("fun answer() = 43\n",store.verifiedFile(project,store.history(project,revision.id).first()).readText())
        assertEquals(body,store.verifiedFile(project,revision).readText())
    }

    @Test fun discardingPreviewDoesNotChangeArtifactHistory() {
        val preview=patch
        compose.setContent { LocalAiTheme { ArtifactsDialog(project,onDismiss={}) } }
        val previewTag="artifact-patch-${preview.id}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(previewTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(previewTag).performScrollTo().performClick()
        compose.onNodeWithTag("artifact-patch-discard").performScrollTo().performClick()
        compose.waitUntil(10_000) { store.codePatchPreviews(project).isEmpty() }
        assertEquals(1,store.history(project,revision.id).size)
        assertEquals(body,store.verifiedFile(project,revision).readText())
    }
}
