package com.localai.workspace.artifacts

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfArtifactLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun generatedPdfPreservesExtendedUnicodeText() {
        val expected = "Español: mañana, café · Ελληνικά · Tiếng Việt · Łódź"
        val tableHeader = "Επικεφαλίδα"
        val tableValue = "Tiếng Việt"
        val spec = ArtifactSpec.parse(JSONObject("""
            {
              "format":"PDF","name":"unicode.pdf",
              "paragraphs":[{"text":"Título Ελληνικά","style":"title"},{"text":"$expected","style":"body"}],
              "tables":[{"rows":[["$tableHeader","Campo"],["$tableValue","Łódź"]]}]
            }
        """.trimIndent()))
        val root = File(context.cacheDir, "pdf-unicode-${UUID.randomUUID()}")
        try {
            val revision = ArtifactStore(context, root).create("pdf-unicode-project", spec)
            val generated = ArtifactStore(context, root).verifiedFile("pdf-unicode-project", revision)
            PDDocument.load(generated).use { document ->
                val extracted = PDFTextStripper().getText(document)
                assertTrue(extracted.contains("Título Ελληνικά"))
                assertTrue(extracted.contains(expected))
                assertTrue(extracted.contains(tableHeader))
                assertTrue(extracted.contains(tableValue))
                assertTrue(extracted.contains("Łódź"))
                val embeddedFonts = document.pages.flatMap { page ->
                    page.resources.fontNames.mapNotNull(page.resources::getFont)
                }
                assertTrue("Generated Unicode PDF must embed a usable font", embeddedFonts.isNotEmpty() && embeddedFonts.all { it.isEmbedded })
            }
            File("build/pdf-layout/unicode-v1.pdf").apply {
                parentFile?.mkdirs()
                writeBytes(generated.readBytes())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generatedPdfKeepsWordsTogetherAndPreservesPageLayoutAndVersions() {
        PDFBoxResourceLoader.init(context)
        val maxLineWidth = 504f
        val bodySize = 12f
        val marker = "WORD_BOUNDARY_MARKER"
        val prefix = PDDocument().use { sizingDocument ->
            val bodyFont = context.assets.open("fonts/NotoSans-Regular.ttf").use {
                PDType0Font.load(sizingDocument, it, true)
            }
            fun width(text: String) = bodyFont.getStringWidth(text) / 1000f * bodySize

            var candidatePrefix = ""
            val targetWidth = width(marker)
            while (width(candidatePrefix + "palabra ") <= maxLineWidth - targetWidth * 0.55f) {
                candidatePrefix += "palabra "
            }
            assertTrue(width(candidatePrefix) < maxLineWidth)
            assertTrue("Fixture must put the whole marker beyond the line boundary", width(candidatePrefix + marker) > maxLineWidth)
            candidatePrefix
        }

        val spec = ArtifactSpec.parse(JSONObject("""
            {
              "format":"PDF","name":"informe.pdf",
              "paragraphs":[
                {"text":"Informe de prueba","style":"title"},
                {"text":"Sección y formato","style":"heading"},
                {"text":"$prefix$marker continúa aquí.","style":"body"},
                {"text":"Segunda página","style":"pageBreak"},
                {"text":"Cierre: España, € y texto verificable.","style":"body"}
              ],
              "tables":[{"rows":[["Campo","Valor"],["Estado","Correcto"]]}]
            }
        """.trimIndent()))
        val root = File(context.cacheDir, "pdf-layout-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val project = "pdf-layout-project"
        try {
            val first = store.create(project, spec)
            val outputDir = File("build/pdf-layout").apply { mkdirs() }
            val firstFile = File(outputDir, "fixture-v1.pdf").apply {
                writeBytes(store.verifiedFile(project, first).readBytes())
            }
            PDDocument.load(firstFile).use { document ->
                assertEquals(2, document.numberOfPages)
                assertEquals(612f, document.getPage(0).mediaBox.width, 0.01f)
                assertEquals(792f, document.getPage(0).mediaBox.height, 0.01f)
                val extracted = PDFTextStripper().getText(document)
                assertTrue(extracted.contains("Informe de prueba"))
                assertTrue(extracted.contains("Sección y formato"))
                assertTrue("Renderer split a word at the line boundary: $extracted", extracted.contains(marker))
                assertTrue(extracted.contains("Segunda página"))
                assertTrue(extracted.contains("Correcto"))
            }

            val revisedJson = store.spec(project, first).json()
            val revisedSpec = revisedJson.put("paragraphs", revisedJson.getJSONArray("paragraphs").apply {
                getJSONObject(0).put("text", "Informe revisado")
            })
            val second = store.revise(project, first.id, first.version, ArtifactSpec.parse(revisedSpec))
            val secondFile = File(outputDir, "fixture-v2.pdf").apply {
                writeBytes(store.verifiedFile(project, second).readBytes())
            }
            assertTrue(first.sha256 != second.sha256)
            assertEquals(first.sha256, ArtifactStore.fileHash(store.verifiedFile(project, first)))
            PDDocument.load(secondFile).use { document ->
                assertEquals(2, document.numberOfPages)
                assertTrue(PDFTextStripper().getText(document).contains("Informe revisado"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generatedPdfTablesUseGridAndPaginateLongCellsWithoutLosingText() {
        val longCell = "INICIO " + "dato ".repeat(300) + " FINAL"
        val source = JSONObject("""
            {
              "format":"PDF","name":"tabla.pdf",
              "paragraphs":[{"text":"Resumen de tabla","style":"heading"}],
              "tables":[{"rows":[
                ["ID","Descripción","Estado","D","E","F","G","H"],
                ["registro-1","$longCell","Activo","d","e","f","g","h"],
                ["registro-2","Cierre visible","Completo","d2","e2","f2","g2","h2"]
              ]}]
            }
        """.trimIndent())
        val spec = ArtifactSpec.parse(source)
        val root = File(context.cacheDir, "pdf-table-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        try {
            val revision = store.create("pdf-table-project", spec)
            val output = File("build/pdf-layout/table-v1.pdf").apply {
                parentFile?.mkdirs()
                writeBytes(store.verifiedFile("pdf-table-project", revision).readBytes())
            }
            PDDocument.load(output).use { document ->
                val extracted = PDFTextStripper().getText(document)
                assertTrue(extracted.contains("INICIO"))
                assertTrue(extracted.contains("FINAL"))
                assertTrue(extracted.contains("Cierre visible"))
                assertEquals("Long cell text must not be clipped between table pages", 300, Regex("\\bdato\\b").findAll(extracted).count())
                assertFalse("PDF table cells must be laid out as cells, not pipe-delimited prose", extracted.contains('|'))
                assertTrue("Long cell should continue onto later pages", document.numberOfPages > 1)
                val pageTexts = (1..document.numberOfPages).map { pageNumber ->
                    PDFTextStripper().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                    }.getText(document)
                }
                assertTrue("Each table page should repeat its header row: ${pageTexts.map { it.contains("Descripción") }}",
                    pageTexts.all { it.contains("Descripción") })
                assertTrue("Repeated header should precede continued row content", pageTexts.drop(1).all { pageText ->
                    val header = pageText.indexOf("Descripción")
                    val continuedCell = pageText.indexOf("dato")
                    header >= 0 && continuedCell >= 0 && header < continuedCell
                })

                val operators = document.pages.flatMap { page ->
                    PDFStreamParser(page).apply { parse() }.tokens.filterIsInstance<Operator>().map { it.name }
                }
                assertTrue("Table must draw cell rectangles", "re" in operators)
                assertTrue("Table cell rectangles must be stroked", "S" in operators)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
