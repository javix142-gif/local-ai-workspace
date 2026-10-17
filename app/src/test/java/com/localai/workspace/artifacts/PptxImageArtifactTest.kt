package com.localai.workspace.artifacts

import android.app.Application
import com.localai.workspace.data.DocumentParser
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PptxImageArtifactTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAIAAAB7QOjdAAAAD0lEQVR4nGO8pBvAwMAAAAcbAVHil4//AAAAAElFTkSuQmCC"
    private val pngBytes = java.util.Base64.getDecoder().decode(pngBase64)

    private fun spec(title: String) = ArtifactSpec.parse(JSONObject("""
        {
          "format":"PPTX","name":"visual.pptx",
          "slides":[{"title":"$title","body":"España y una imagen local.","imagePngBase64":"$pngBase64","imageAltText":"Mapa & datos"}]
        }
    """.trimIndent()))

    @Test
    fun generatedPptxEmbedsAccessiblePngAndKeepsImageAcrossImmutableRevision() = runBlocking {
        val root = File(context.cacheDir, "pptx-image-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val outputDir = File("build/pptx-image").apply { mkdirs() }
        val project = "pptx-image-project"
        try {
            val first = store.create(project, spec("Informe visual"))
            val firstFile = File(outputDir, "fixture-v1.pptx").apply {
                writeBytes(store.verifiedFile(project, first).readBytes())
            }
            assertTrue(spec("Informe visual").preview().contains("[PNG image: Mapa & datos]"))
            val slideXml = "ppt/slides/slide1.xml"
            val relsXml = "ppt/slides/_rels/slide1.xml.rels"
            ZipFile(firstFile).use { zip ->
                assertArrayEquals(pngBytes, zip.getInputStream(zip.getEntry("ppt/media/image1.png")).use { it.readBytes() })
                val types = xml(zip, "[Content_Types].xml")
                assertEquals("image/png", types.getElementsByTagName("Default").let { nodes ->
                    (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("Extension").nodeValue to nodes.item(it).attributes.getNamedItem("ContentType").nodeValue }
                        .toMap()["png"]
                })
                assertTrue(zip.getEntry("ppt/slideLayouts/slideLayout1.xml") != null)
                assertTrue(zip.getEntry("ppt/slideMasters/slideMaster1.xml") != null)
                assertTrue(zip.getEntry("ppt/theme/theme1.xml") != null)
                val slide = xml(zip, slideXml)
                assertEquals(1, slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "pic").length)
                val pictureName = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "cNvPr")
                assertEquals("Mapa & datos", pictureName.item(2).attributes.getNamedItem("descr").nodeValue)
                val blip = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip").item(0)
                assertEquals("rIdImage", blip.attributes.getNamedItemNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed").nodeValue)
                val pictureExt = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "ext").item(0)
                val width = pictureExt.attributes.getNamedItem("cx").nodeValue.toLong()
                val height = pictureExt.attributes.getNamedItem("cy").nodeValue.toLong()
                assertTrue("Image aspect ratio must be preserved", kotlin.math.abs(width.toDouble() / height - 2.0) < 0.02)
                val rels = xml(zip, relsXml).getElementsByTagName("Relationship")
                val imageRelationship = (0 until rels.length()).map { rels.item(it) }
                    .single { it.attributes.getNamedItem("Type").nodeValue.endsWith("/image") }
                assertEquals("rIdImage", imageRelationship.attributes.getNamedItem("Id").nodeValue)
                assertEquals("../media/image1.png", imageRelationship.attributes.getNamedItem("Target").nodeValue)
                zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry ->
                    zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                }
            }
            val parsed = DocumentParser(context).parse(firstFile, first.format.mime, first.name)
            assertEquals(1, parsed.pages.size)
            assertTrue(parsed.pages.single().text.contains("Informe visual"))
            assertTrue(parsed.pages.single().text.contains("España y una imagen local."))

            val revisedSpec = store.spec(project, first).json().apply {
                getJSONArray("slides").getJSONObject(0).put("title", "Informe revisado")
            }
            val second = store.revise(project, first.id, first.version, ArtifactSpec.parse(revisedSpec))
            val secondFile = File(outputDir, "fixture-v2.pptx").apply {
                writeBytes(store.verifiedFile(project, second).readBytes())
            }
            assertTrue(first.sha256 != second.sha256)
            assertEquals(first.sha256, ArtifactStore.fileHash(store.verifiedFile(project, first)))
            ZipFile(secondFile).use { zip ->
                assertArrayEquals(pngBytes, zip.getInputStream(zip.getEntry("ppt/media/image1.png")).use { it.readBytes() })
                assertTrue(xml(zip, slideXml).getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "cNvPr").let { nodes ->
                    (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.contains("Title")
                })
            }
            assertTrue(DocumentParser(context).parse(secondFile, second.format.mime, second.name).pages.single().text.contains("Informe revisado"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun xml(zip: ZipFile, path: String) =
        zip.getInputStream(zip.getEntry(path)).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
}
