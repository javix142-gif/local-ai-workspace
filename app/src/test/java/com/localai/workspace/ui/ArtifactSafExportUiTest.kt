package com.localai.workspace.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.artifacts.ArtifactSpec
import com.localai.workspace.artifacts.ArtifactStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ArtifactSafExportUiTest {
    @get:Rule val compose=createComposeRule()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val project="saf-export-${UUID.randomUUID()}"
    private val store get()=ArtifactStore(context)

    private class ExportRegistry(private val destination:Uri):ActivityResultRegistry() {
        var requestedName:String?=null
        override fun <I,O> onLaunch(
            requestCode:Int,
            contract:ActivityResultContract<I,O>,
            input:I,
            options:ActivityOptionsCompat?,
        ) {
            requestedName=input as? String
            @Suppress("UNCHECKED_CAST")
            check(dispatchResult(requestCode,destination as O))
        }
    }

    @After fun cleanup() {
        File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray())}").deleteRecursively()
    }

    @Test fun explicitCreateDocumentChoiceExportsExactVerifiedBytesAndReadsThemBack() {
        val spec=ArtifactSpec.parse(JSONObject().put("format","CODE").put("name","answer.kt")
            .put("body","// SAF round-trip · España 界\nfun answer() = 42\n"))
        val revision=store.create(project,spec)
        val expected=store.verifiedFile(project,revision).readBytes()
        val destination=Uri.parse("content://local.fixture/exported/answer.kt")
        val bytes=ByteArrayOutputStream()
        val shadow=shadowOf(context.contentResolver)
        shadow.registerOutputStream(destination,bytes)
        shadow.registerInputStreamSupplier(destination){ByteArrayInputStream(bytes.toByteArray())}
        val registry=ExportRegistry(destination)
        val owner=object:ActivityResultRegistryOwner { override val activityResultRegistry=registry }

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LocalAiTheme { ArtifactsDialog(project,onDismiss={}) }
            }
        }
        compose.onNodeWithTag("artifact-${revision.id}").assertIsDisplayed().performClick()
        compose.onNodeWithText("Preview changes").performClick()
        compose.onNodeWithText("Export saved v1").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("artifact-status").assertTextContains("Exported and read back")
            }.isSuccess
        }

        assertEquals("CreateDocument receives the artifact's suggested name",revision.name,registry.requestedName)
        assertArrayEquals("SAF destination bytes match the immutable verified artifact",expected,bytes.toByteArray())
        compose.onNodeWithTag("artifact-status").assertTextContains(revision.name)
        assertEquals(revision.sha256,ArtifactStore.hash(bytes.toByteArray()))
    }
}
