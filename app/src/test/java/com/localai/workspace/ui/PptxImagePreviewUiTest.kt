package com.localai.workspace.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.localai.workspace.artifacts.ArtifactSpec
import com.localai.workspace.artifacts.ArtifactStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PptxImagePreviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val project="pptx-image-preview-${UUID.randomUUID()}"
    private val store get()=ArtifactStore(context)

    @After fun cleanup() {
        File(File(context.filesDir,"tool-workspaces"),"artifacts-${ArtifactStore.hash(project.toByteArray())}").deleteRecursively()
    }

    @Test fun savedPptxShowsAccessibleSlideLayoutAndImageAssetWithoutChangingRevision() {
        val encoded=solidPngBase64()
        val spec=ArtifactSpec.parse(JSONObject()
            .put("format","PPTX").put("name","visual.pptx")
            .put("slides",JSONArray()
                .put(JSONObject()
                    .put("title","Informe visual")
                    .put("body","España y una imagen local.")
                    .put("shapes",JSONArray().put(JSONObject()
                        .put("kind","rectangle").put("description","Zona de entrada")
                        .put("label","Entrada").put("left",100).put("top",200)
                        .put("width",300).put("height",180).put("fillColor","3E746D")))
                    .put("imagePngBase64",encoded)
                    .put("imageAltText","Mapa & datos"))
                .put(JSONObject()
                    .put("title","Resumen")
                    .put("body","Segunda diapositiva con contenido completo."))))
        val revision=store.create(project,spec)
        val bytesBefore=store.verifiedFile(project,revision).readBytes()
        val imageHash=ArtifactStore.hash(Base64.getDecoder().decode(encoded))
        val decoded=runBlocking { decodePptxImagePreviews(spec) }
        try {
            assertEquals(1,decoded.size)
            assertEquals(512,decoded.single().sourceWidth)
            assertTrue(decoded.single().bitmap.width.toLong()*decoded.single().bitmap.height<=65_536L)
        } finally {
            decoded.forEach { if(!it.bitmap.isRecycled)it.bitmap.recycle() }
        }

        compose.setContent { LocalAiTheme { ArtifactsDialog(project,onDismiss={}) } }
        val artifactTag="artifact-${revision.id}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(artifactTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(artifactTag).performScrollTo().performClick()
        val imageTag="artifact-image-preview-1"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(imageTag).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(imageTag).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Image on slide 1: Mapa & datos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pptx-slide-preview-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pptx-slide-title-1").performScrollTo()
            .assertTextContains("Informe visual", substring=true)
        compose.onNodeWithTag("pptx-slide-body-1").performScrollTo()
            .assertTextContains("España y una imagen local.", substring=true)
        compose.onNodeWithTag("pptx-slide-image-region-1").performScrollTo()
            .assertTextContains("Mapa & datos", substring=true)
        compose.onNodeWithTag("pptx-figure-canvas-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pptx-figure-preview-1-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Figure 1 on slide 1: Zona de entrada · Entrada")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pptx-figure-label-1-1").performScrollTo()
            .assertTextContains("Entrada", substring=false)
        compose.onNodeWithTag("pptx-slide-preview-note").performScrollTo()
            .assertTextContains("Approximate layout", substring=true)
        compose.onNodeWithTag("pptx-slide-preview-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pptx-slide-title-2").performScrollTo()
            .assertTextContains("Resumen", substring=true)
        compose.onNodeWithTag("pptx-slide-body-2").performScrollTo()
            .assertTextContains("Segunda diapositiva con contenido completo.", substring=true)
        compose.onNodeWithTag("artifact-preview").performScrollTo().assertTextContains("PNG SHA-256: $imageHash", substring=true)
        compose.onNodeWithText("Alternative text: Mapa & datos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PNG source: 512 × 512 px").performScrollTo().assertIsDisplayed()

        assertEquals("Preview remains read-only",1,store.history(project,revision.id).size)
        assertArrayEquals(bytesBefore,store.verifiedFile(project,revision).readBytes())
    }

    private fun solidPngBase64():String {
        val width=512;val height=512
        val raw=ByteArrayOutputStream()
        repeat(height) {
            raw.write(0) // PNG filter: None
            repeat(width) { raw.write(24);raw.write(96);raw.write(150) }
        }
        val compressed=ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
        val png=ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))
        val header=ByteArrayOutputStream()
        DataOutputStream(header).use { out ->
            out.writeInt(width);out.writeInt(height);out.writeByte(8);out.writeByte(2)
            out.writeByte(0);out.writeByte(0);out.writeByte(0)
        }
        writeChunk(png,"IHDR",header.toByteArray())
        writeChunk(png,"IDAT",compressed.toByteArray())
        writeChunk(png,"IEND",byteArrayOf())
        return Base64.getEncoder().encodeToString(png.toByteArray())
    }

    private fun writeChunk(output:ByteArrayOutputStream,type:String,data:ByteArray) {
        val typeBytes=type.toByteArray(Charsets.US_ASCII)
        val crc=CRC32().apply { update(typeBytes);update(data) }.value
        DataOutputStream(output).apply {
            writeInt(data.size);write(typeBytes);write(data);writeInt(crc.toInt())
        }
    }
}
