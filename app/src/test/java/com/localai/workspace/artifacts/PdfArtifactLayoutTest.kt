package com.localai.workspace.artifacts

import android.app.Application
import com.localai.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun generatedPdfKeepsWordsTogetherAndPreservesPageLayoutAndVersions() {
        PDFBoxResourceLoader.init(context)
        val bodyFont = PDType1Font.HELVETICA
        val maxLineWidth = 504f
        val bodySize = 12f
        val marker = "WORD_BOUNDARY_MARKER"
        fun width(text: String) = bodyFont.getStringWidth(text) / 1000f * bodySize

        var prefix = ""
        val targetWidth = width(marker)
        while (width(prefix + "palabra ") <= maxLineWidth - targetWidth * 0.55f) {
            prefix += "palabra "
        }
        assertTrue(width(prefix) < maxLineWidth)
        assertTrue("Fixture must put the whole marker beyond the line boundary", width(prefix + marker) > maxLineWidth)

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

            val revisedSpec = store.spec(project, first).json()
                .put("paragraphs", store.spec(project, first).json().getJSONArray("paragraphs").apply {
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
}
