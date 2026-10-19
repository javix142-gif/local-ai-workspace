package com.localai.workspace.ui

import android.app.Application
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.artifacts.ArtifactCreateDocumentContract
import com.localai.workspace.artifacts.ArtifactExportRequest
import com.localai.workspace.artifacts.ArtifactFormat
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

    private class ExportRegistry(private val context:Context,private val destination:Uri):ActivityResultRegistry() {
        var requestedName:String?=null
        var requestedMimeType:String?=null
        var requestedAction:String?=null
        override fun <I,O> onLaunch(
            requestCode:Int,
            contract:ActivityResultContract<I,O>,
            input:I,
            options:ActivityOptionsCompat?,
        ) {
            requestedName=input as? String
            val intent=contract.createIntent(context,input)
            requestedMimeType=intent.type
            requestedAction=intent.action
            if(requestedName==null)requestedName=intent.getStringExtra(Intent.EXTRA_TITLE)
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
        val registry=ExportRegistry(context,destination)
        val owner=object:ActivityResultRegistryOwner { override val activityResultRegistry=registry }

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LocalAiTheme { ArtifactsDialog(project,onDismiss={}) }
            }
        }
        compose.onNodeWithTag("artifact-${revision.id}").assertIsDisplayed().performClick()
        compose.onNodeWithText("Preview changes").performClick()
        compose.onNodeWithText("Export saved v1").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { bytes.size() == expected.size }

        assertEquals("CreateDocument receives the artifact's suggested name",revision.name,registry.requestedName)
        assertEquals("SAF must receive the artifact format MIME", "text/plain", registry.requestedMimeType)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, registry.requestedAction)
        assertArrayEquals("SAF destination bytes match the immutable verified artifact",expected,bytes.toByteArray())
        compose.onNodeWithTag("artifact-status").assertTextContains("Exported and read back: ${revision.name} · v${revision.version}")
        assertEquals(revision.sha256,ArtifactStore.hash(bytes.toByteArray()))
    }

    @Test fun createDocumentRequestCarriesEachArtifactMimeAndFilename() {
        val contract=ArtifactCreateDocumentContract()
        ArtifactFormat.entries.forEach { format ->
            val fileName="draft.${format.extension}"
            val intent=contract.createIntent(context,ArtifactExportRequest(fileName,format))
            assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.action)
            assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE)==true)
            assertEquals(format.mime,intent.type)
            assertEquals(fileName,intent.getStringExtra(Intent.EXTRA_TITLE))
            assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,false))
        }
    }
}
