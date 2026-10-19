package com.localai.workspace.artifacts

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.localai.workspace.data.DocumentParser
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.CRC32
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

    private fun specWithImage(title: String, image: String) = ArtifactSpec.parse(JSONObject()
        .put("format", "PPTX").put("name", "visual.pptx")
        .put("slides", org.json.JSONArray().put(JSONObject()
            .put("title", title).put("body", "España y una imagen local.")
            .put("imagePngBase64", image).put("imageAltText", "Mapa & datos"))))

    private fun solidPngBase64(color: Int): String = java.io.ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, color)
            setPixel(1, 0, color)
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
        java.util.Base64.getEncoder().encodeToString(output.toByteArray())
    }

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
            assertTrue("The textual preview must not expose the base64 payload", !spec("Informe visual").preview().contains(pngBase64))
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
                val picture = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "pic").item(0) as org.w3c.dom.Element
                val pictureName = picture.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "cNvPr")
                assertEquals("Mapa & datos", pictureName.item(0).attributes.getNamedItem("descr").nodeValue)
                val blip = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip").item(0)
                assertEquals("rIdImage", blip.attributes.getNamedItemNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed").nodeValue)
                val pictureExt = picture.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "ext").item(0)
                val width = pictureExt.attributes.getNamedItem("cx").nodeValue.toLong()
                val height = pictureExt.attributes.getNamedItem("cy").nodeValue.toLong()
                assertTrue("Image aspect ratio must be preserved", kotlin.math.abs(width.toDouble() / height - 2.0) < 0.02)
                val rels = xml(zip, relsXml).getElementsByTagName("Relationship")
                val imageRelationship = (0 until rels.length).map { rels.item(it) }
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

    @Test
    fun multiSlidePptxKeepsDistinctImagesAndRelationshipsAcrossImmutableRevision() = runBlocking {
        val root = File(context.cacheDir, "pptx-multislide-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val outputDir = File("build/pptx-image/multi-slide").apply { mkdirs() }
        val project = "pptx-multislide-project"
        val firstPng = solidPngBase64(Color.RED)
        val secondPng = solidPngBase64(Color.BLUE)
        val input = JSONObject().put("format", "PPTX").put("name", "visual.pptx")
            .put("slides", org.json.JSONArray()
                .put(JSONObject().put("title", "Primera").put("body", "Mapa rojo")
                    .put("imagePngBase64", firstPng).put("imageAltText", "Mapa rojo"))
                .put(JSONObject().put("title", "Segunda").put("body", "Mapa azul")
                    .put("imagePngBase64", secondPng).put("imageAltText", "Mapa azul")))
        try {
            val first = store.create(project, ArtifactSpec.parse(input))
            val firstBytes = store.verifiedFile(project, first).readBytes()
            val firstFile = File(outputDir, "multi-slide-v1.pptx").apply { writeBytes(firstBytes) }
            ZipFile(firstFile).use { zip ->
                val presentation = xml(zip, "ppt/presentation.xml")
                assertEquals("The package must expose both slides in order", 2,
                    presentation.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "sldId").length)
                val expectedImages = listOf(firstPng, secondPng).map { java.util.Base64.getDecoder().decode(it) }
                val expectedAlt = listOf("Mapa rojo", "Mapa azul")
                expectedImages.forEachIndexed { index, expectedBytes ->
                    val slideNumber = index + 1
                    val slide = xml(zip, "ppt/slides/slide$slideNumber.xml")
                    val pictures = slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "pic")
                    assertEquals("Each slide must contain its own image", 1, pictures.length)
                    val nonVisual = pictures.item(0) as org.w3c.dom.Element
                    val pictureProperties = nonVisual.getElementsByTagNameNS(
                        "http://schemas.openxmlformats.org/presentationml/2006/main", "cNvPr")
                    assertEquals(expectedAlt[index], pictureProperties.item(0).attributes.getNamedItem("descr").nodeValue)

                    val rels = xml(zip, "ppt/slides/_rels/slide$slideNumber.xml.rels")
                        .getElementsByTagName("Relationship")
                    val imageRelation = (0 until rels.length).map { rels.item(it) as org.w3c.dom.Element }
                        .single { it.attributes.getNamedItem("Type").nodeValue.endsWith("/image") }
                    assertEquals("rIdImage", imageRelation.attributes.getNamedItem("Id").nodeValue)
                    assertEquals("../media/image$slideNumber.png", imageRelation.attributes.getNamedItem("Target").nodeValue)
                    assertArrayEquals(expectedBytes, zip.getInputStream(zip.getEntry("ppt/media/image$slideNumber.png")).use { it.readBytes() })
                }
                zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry ->
                    zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                }
            }

            val parsedFirst = DocumentParser(context).parse(firstFile, first.format.mime, first.name)
            assertEquals(listOf(1, 2), parsedFirst.pages.map { it.pageNumber })
            assertTrue(parsedFirst.pages[0].text.contains("Primera"))
            assertTrue(parsedFirst.pages[1].text.contains("Segunda"))

            val revisedSpec = store.spec(project, first).json().apply {
                getJSONArray("slides").getJSONObject(1).put("title", "Segunda revisada")
            }
            val revised = store.revise(project, first.id, first.version, ArtifactSpec.parse(revisedSpec))
            val revisedBytes = store.verifiedFile(project, revised).readBytes()
            val revisedFile = File(outputDir, "multi-slide-v2.pptx").apply { writeBytes(revisedBytes) }
            assertNotEquals(first.sha256, revised.sha256)
            assertArrayEquals("The first immutable revision remains intact", firstBytes, store.verifiedFile(project, first).readBytes())
            ZipFile(revisedFile).use { zip ->
                assertEquals(2, xml(zip, "ppt/presentation.xml")
                    .getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "sldId").length)
                assertArrayEquals(firstPng.let { java.util.Base64.getDecoder().decode(it) },
                    zip.getInputStream(zip.getEntry("ppt/media/image1.png")).use { it.readBytes() })
                assertArrayEquals(secondPng.let { java.util.Base64.getDecoder().decode(it) },
                    zip.getInputStream(zip.getEntry("ppt/media/image2.png")).use { it.readBytes() })
            }
            val parsedRevised = DocumentParser(context).parse(revisedFile, revised.format.mime, revised.name)
            assertEquals(listOf(1, 2), parsedRevised.pages.map { it.pageNumber })
            assertTrue(parsedRevised.pages[0].text.contains("Primera"))
            assertTrue(parsedRevised.pages[1].text.contains("Segunda revisada"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedOversizedOrUnlabelledPptxImages() {
        val tooLarge = java.util.Base64.getEncoder().encodeToString(ByteArray(PptxImagePayloadValidator.MAX_IMAGE_BYTES + 1))
        val oversizedDimensions = pngBytes.copyOf()
        java.nio.ByteBuffer.wrap(oversizedDimensions).putInt(16, PptxImagePayloadValidator.MAX_DIMENSION + 1)
        val headerCrc = CRC32().apply { update(oversizedDimensions, 12, 17) }.value.toInt()
        java.nio.ByteBuffer.wrap(oversizedDimensions).putInt(29, headerCrc)
        val badCrc = pngBytes.copyOf().apply { this[29] = (this[29].toInt() xor 1).toByte() }
        val invalidDescriptors = listOf(
            JSONObject().put("imagePngBase64", pngBase64),
            JSONObject().put("imageAltText", "Map without bytes"),
            JSONObject().put("imagePngBase64", "%%%not-base64%%%").put("imageAltText", "Image"),
            JSONObject().put("imagePngBase64", java.util.Base64.getEncoder().encodeToString("not a PNG".toByteArray())).put("imageAltText", "Image"),
            JSONObject().put("imagePngBase64", java.util.Base64.getEncoder().encodeToString(badCrc)).put("imageAltText", "Image"),
            JSONObject().put("imagePngBase64", java.util.Base64.getEncoder().encodeToString(oversizedDimensions)).put("imageAltText", "Image"),
            JSONObject().put("imagePngBase64", tooLarge).put("imageAltText", "Image"),
            JSONObject().put("imagePngBase64", pngBase64).put("imageAltText", " "),
            JSONObject().put("imagePngBase64", pngBase64).put("imageAltText", "x".repeat(121))
        )
        invalidDescriptors.forEach { fields ->
            val slide = JSONObject().put("title", "Slide").put("body", "Body")
            fields.keys().forEach { slide.put(it, fields.get(it)) }
            val source = JSONObject().put("format", "PPTX").put("name", "invalid.pptx").put("slides", org.json.JSONArray().put(slide))
            try {
                ArtifactSpec.parse(source)
                fail("invalid image descriptor must be rejected: $fields")
            } catch (failure: IllegalArgumentException) {
                assertTrue("Expected a specific image-validation reason", failure.message?.startsWith("PPTX_IMAGE_") == true)
            }
        }
    }

    @Test
    fun textualPreviewDistinguishesImageRevisionWithoutExposingPngPayload() {
        val red = solidPngBase64(Color.RED)
        val blue = solidPngBase64(Color.BLUE)
        val first = specWithImage("Informe visual", red).preview()
        val revised = specWithImage("Informe visual", blue).preview()

        assertTrue(first.contains("Mapa & datos"))
        assertTrue("The selectable textual preview must not expose the PNG payload", !first.contains(red))
        val redHash = ArtifactStore.hash(PptxImagePayloadValidator.decodeAndValidate(red).bytes)
        val blueHash = ArtifactStore.hash(PptxImagePayloadValidator.decodeAndValidate(blue).bytes)
        assertTrue(first.contains("PNG SHA-256: $redHash"))
        assertTrue(revised.contains("PNG SHA-256: $blueHash"))
        assertNotEquals("Different image bytes need a visible revision marker even when alt text is unchanged", first, revised)
    }

    @Test
    fun generatedPptxRendersBoundedAccessibleEditableFiguresAndKeepsRevisionImmutable() = runBlocking {
        val root = File(context.cacheDir, "pptx-figures-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val project = "pptx-figures-project"
        val figures = org.json.JSONArray()
            .put(JSONObject().put("kind", "rectangle").put("description", "Entrada & revisión")
                .put("label", "Entrada").put("left", 100).put("top", 200)
                .put("width", 300).put("height", 180).put("fillColor", "3E746D"))
            .put(JSONObject().put("kind", "ellipse").put("description", "Resultado aprobado")
                .put("label", "Aprobado").put("left", 600).put("top", 620)
                .put("width", 250).put("height", 200).put("fillColor", "FFFFFF")
                .put("lineColor", "1D2522"))
            .put(JSONObject().put("kind", "roundedRectangle").put("description", "Revisión pendiente")
                .put("left", 100).put("top", 700).put("width", 250).put("height", 100))
            .put(JSONObject().put("kind", "triangle").put("description", "Decisión requerida")
                .put("left", 700).put("top", 100).put("width", 200).put("height", 250))
        val descriptor = JSONObject().put("format", "PPTX").put("name", "figures.pptx")
            .put("slides", org.json.JSONArray().put(JSONObject().put("title", "Flujo")
                .put("body", "Entrada, revisión y resultado.").put("shapes", figures)))
        try {
            val firstSpec = ArtifactSpec.parse(descriptor)
            val first = store.create(project, firstSpec)
            val firstBytes = store.verifiedFile(project, first).readBytes()
            File("build/pptx-image/figures/figures-v1.pptx").apply {
                parentFile?.mkdirs()
                writeBytes(firstBytes)
            }
            assertTrue(firstSpec.preview().contains("Figure rectangle: Entrada & revisión"))
            assertTrue(firstSpec.preview().contains("Figure ellipse: Resultado aprobado"))
            assertTrue(firstSpec.preview().contains("Figure roundedRectangle: Revisión pendiente"))
            assertTrue(firstSpec.preview().contains("Figure triangle: Decisión requerida"))
            ZipFile(store.verifiedFile(project, first)).use { zip ->
                val slide = xml(zip, "ppt/slides/slide1.xml")
                val presentationNs = "http://schemas.openxmlformats.org/presentationml/2006/main"
                val drawingNs = "http://schemas.openxmlformats.org/drawingml/2006/main"
                val shapes = slide.getElementsByTagNameNS(presentationNs, "sp")
                val rectangle = (0 until shapes.length).map { shapes.item(it) as org.w3c.dom.Element }
                    .single { shape -> shape.getElementsByTagNameNS(presentationNs, "cNvPr").let { it.length > 0 && it.item(0).attributes.getNamedItem("name").nodeValue == "Figure 1" } }
                val ellipse = (0 until shapes.length).map { shapes.item(it) as org.w3c.dom.Element }
                    .single { shape -> shape.getElementsByTagNameNS(presentationNs, "cNvPr").let { it.length > 0 && it.item(0).attributes.getNamedItem("name").nodeValue == "Figure 2" } }
                val roundedRectangle = (0 until shapes.length).map { shapes.item(it) as org.w3c.dom.Element }
                    .single { shape -> shape.getElementsByTagNameNS(presentationNs, "cNvPr").let { it.length > 0 && it.item(0).attributes.getNamedItem("name").nodeValue == "Figure 3" } }
                val triangle = (0 until shapes.length).map { shapes.item(it) as org.w3c.dom.Element }
                    .single { shape -> shape.getElementsByTagNameNS(presentationNs, "cNvPr").let { it.length > 0 && it.item(0).attributes.getNamedItem("name").nodeValue == "Figure 4" } }
                assertEquals("Entrada & revisión", rectangle.getElementsByTagNameNS(presentationNs, "cNvPr").item(0)
                    .attributes.getNamedItem("descr").nodeValue)
                assertEquals("rect", rectangle.getElementsByTagNameNS(drawingNs, "prstGeom").item(0)
                    .attributes.getNamedItem("prst").nodeValue)
                assertEquals("ellipse", ellipse.getElementsByTagNameNS(drawingNs, "prstGeom").item(0)
                    .attributes.getNamedItem("prst").nodeValue)
                assertEquals("roundRect", roundedRectangle.getElementsByTagNameNS(drawingNs, "prstGeom").item(0)
                    .attributes.getNamedItem("prst").nodeValue)
                assertEquals("triangle", triangle.getElementsByTagNameNS(drawingNs, "prstGeom").item(0)
                    .attributes.getNamedItem("prst").nodeValue)
                assertEquals("Entrada", rectangle.getElementsByTagNameNS(drawingNs, "t").item(0).textContent)
                assertEquals("Aprobado", ellipse.getElementsByTagNameNS(drawingNs, "t").item(0).textContent)
                assertEquals("FFFFFF", rectangle.getElementsByTagNameNS(drawingNs, "rPr").item(0)
                    .let { it as org.w3c.dom.Element }.getElementsByTagNameNS(drawingNs, "srgbClr").item(0)
                    .attributes.getNamedItem("val").nodeValue)
                assertEquals("000000", ellipse.getElementsByTagNameNS(drawingNs, "rPr").item(0)
                    .let { it as org.w3c.dom.Element }.getElementsByTagNameNS(drawingNs, "srgbClr").item(0)
                    .attributes.getNamedItem("val").nodeValue)
                val rectangleOffset = rectangle.getElementsByTagNameNS(drawingNs, "off").item(0) as org.w3c.dom.Element
                val rectangleExtent = rectangle.getElementsByTagNameNS(drawingNs, "ext").item(0) as org.w3c.dom.Element
                assertEquals("1219200", rectangleOffset.getAttribute("x"))
                assertEquals("1371600", rectangleOffset.getAttribute("y"))
                assertEquals("3657600", rectangleExtent.getAttribute("cx"))
                assertEquals("1234440", rectangleExtent.getAttribute("cy"))
                assertTrue(zip.getEntry("ppt/slideLayouts/slideLayout1.xml") != null)
                assertTrue(zip.getEntry("ppt/slideMasters/slideMaster1.xml") != null)
                assertTrue(zip.getEntry("ppt/theme/theme1.xml") != null)
                zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { entry ->
                    zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                }
            }

            val revisedDescriptor = store.spec(project, first).json().apply {
                getJSONArray("slides").getJSONObject(0).getJSONArray("shapes").getJSONObject(0)
                    .put("label", "Entrada validada")
            }
            val revised = store.revise(project, first.id, first.version, ArtifactSpec.parse(revisedDescriptor))
            assertNotEquals(first.sha256, revised.sha256)
            assertArrayEquals("The saved source revision remains unchanged", firstBytes,
                store.verifiedFile(project, first).readBytes())
            ZipFile(store.verifiedFile(project, revised)).use { zip ->
                val slide = xml(zip, "ppt/slides/slide1.xml")
                assertTrue((0 until slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "t").length)
                    .map { slide.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "t").item(it).textContent }
                    .contains("Entrada validada"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsPptxFiguresOutsideCanvasOrShapeLimits() {
        fun source(shapes: org.json.JSONArray) = JSONObject().put("format", "PPTX").put("name", "bounded.pptx")
            .put("slides", org.json.JSONArray().put(JSONObject().put("title", "Title").put("body", "Body").put("shapes", shapes)))
        fun shape(left: Int = 100, width: Int = 100) = JSONObject().put("kind", "rectangle")
            .put("description", "Accessible figure").put("left", left).put("top", 100)
            .put("width", width).put("height", 100)

        val invalid = listOf(
            shape(left = 950, width = 100),
            shape().put("kind", "freeform"),
            shape().put("fillColor", "GG0000"),
            shape().put("description", " "),
            shape().put("width", 0),
        )
        invalid.forEach { descriptor ->
            try {
                ArtifactSpec.parse(source(org.json.JSONArray().put(descriptor)))
                fail("invalid figure descriptor must be rejected: $descriptor")
            } catch (failure: IllegalArgumentException) {
                assertTrue("Expected a PPTX shape validation reason", failure.message?.startsWith("PPTX_SHAPE_") == true)
            }
        }
        val wrongCollectionType = source(org.json.JSONArray()).apply {
            getJSONArray("slides").getJSONObject(0).put("shapes", "not an array")
        }
        try {
            ArtifactSpec.parse(wrongCollectionType)
            fail("a malformed figures field must not be silently ignored")
        } catch (failure: IllegalArgumentException) {
            assertEquals("PPTX_SHAPE_ARRAY_REQUIRED", failure.message)
        }

        val tooManyOnOneSlide = org.json.JSONArray().apply { repeat(PptxFigureDescriptor.MAX_PER_SLIDE + 1) { put(shape()) } }
        try {
            ArtifactSpec.parse(source(tooManyOnOneSlide))
            fail("per-slide shape bound must be enforced")
        } catch (failure: IllegalArgumentException) {
            assertEquals("PPTX_SHAPE_LIMIT_PER_SLIDE", failure.message)
        }

        val twelve = org.json.JSONArray().apply { repeat(PptxFigureDescriptor.MAX_PER_SLIDE) { put(shape()) } }
        val manySlides = org.json.JSONArray().apply {
            repeat(PptxFigureDescriptor.MAX_PER_PRESENTATION / PptxFigureDescriptor.MAX_PER_SLIDE + 1) {
                put(JSONObject().put("title", "Title").put("body", "Body").put("shapes", twelve))
            }
        }
        try {
            ArtifactSpec.parse(JSONObject().put("format", "PPTX").put("name", "bounded.pptx").put("slides", manySlides))
            fail("deck-wide shape bound must be enforced")
        } catch (failure: IllegalArgumentException) {
            assertEquals("PPTX_SHAPE_LIMIT_PER_PRESENTATION", failure.message)
        }
    }

    private fun xml(zip: ZipFile, path: String) =
        zip.getInputStream(zip.getEntry(path)).use {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(it)
        }
}
