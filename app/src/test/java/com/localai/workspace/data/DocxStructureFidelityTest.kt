package com.localai.workspace.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.runBlocking
import com.localai.workspace.documents.OfficeText
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocxStructureFidelityTest {
    private fun fixture(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (directory != null) {
            val candidate = File(directory, "docs/validation/master-program/05-docx-structure/fixtures/structure-v1.docx")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        error("Synthetic DOCX structure fixture not found")
    }

    private fun parseDocumentXml(xml: String, numberingXml: String? = null): ParsedDocument {
        val file = File.createTempFile("docx-structure-", ".docx", RuntimeEnvironment.getApplication().cacheDir)
        try {
            ZipOutputStream(file.outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("word/document.xml"))
                archive.write(xml.toByteArray(Charsets.UTF_8))
                archive.closeEntry()
                numberingXml?.let {
                    archive.putNextEntry(ZipEntry("word/numbering.xml"))
                    archive.write(it.toByteArray(Charsets.UTF_8))
                    archive.closeEntry()
                }
            }
            return ZipFile(file).use(OfficeText::docx)
        } finally {
            file.delete()
        }
    }

    @Test fun headingsTableLocationsAndExplicitPageBreaksRemainTraceable() = runBlocking {
        val file = fixture()
        val parsed = DocumentParser(RuntimeEnvironment.getApplication()).parse(
            file,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            file.name,
        )
        assertEquals("The explicit page break should define two traceable page segments", 2, parsed.pages.size)
        val first = parsed.pages[0]
        val second = parsed.pages[1]
        assertEquals(1, first.pageNumber)
        assertEquals(2, second.pageNumber)
        assertTrue(first.text.contains("[DOCX paragraph=1 heading=Heading1]"))
        assertTrue(first.text.contains("Informe anual"))
        assertTrue(first.text.contains("Introducción del documento."))
        assertTrue(first.text.contains("[DOCX table=1 row=1 cell=1]"))
        assertTrue(first.text.contains("[DOCX table=1 row=1 cell=2]"))
        assertTrue(first.text.contains("[DOCX table=1 row=2 cell=1]"))
        assertTrue(first.text.contains("[DOCX table=1 row=2 cell=2]"))
        assertTrue(first.text.contains("Álvaro"))
        assertTrue(first.text.contains("42"))
        assertTrue(first.text.contains("Antes del salto."))
        assertTrue(second.text.contains("Después del salto."))
        val chunks = TextChunker(maxCharacters = 1800, overlapCharacters = 0).chunk(parsed)
        assertTrue(chunks.any {
            it.pageStart == 1 && it.charStart != null && it.charEnd != null && it.charEnd > it.charStart &&
                it.text.contains("[DOCX table=1 row=2 cell=1]") && it.text.contains("Álvaro")
        })
        assertTrue(chunks.any {
            it.pageStart == 2 && it.charStart != null && it.charEnd != null && it.charEnd > it.charStart &&
                it.text.contains("Después del salto.")
        })
    }

    @Test fun tableHeaderEmptyCellsAndPageBreakBeforeRemainTraceable() {
        val xml = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>Página uno.</w:t></w:r></w:p>
            <w:tbl><w:tr><w:trPr><w:tblHeader/></w:trPr>
                <w:tc><w:p><w:r><w:t>Encabezado</w:t></w:r></w:p></w:tc>
                <w:tc><w:p/></w:tc>
            </w:tr></w:tbl>
            <w:p><w:pPr><w:pageBreakBefore w:val="0"/></w:pPr><w:r><w:t>Sin salto.</w:t></w:r></w:p>
            <w:p><w:pPr><w:pageBreakBefore/></w:pPr><w:r><w:t>Página dos.</w:t></w:r></w:p>
        </w:body></w:document>""".trimIndent()
        val parsed = parseDocumentXml(xml)
        assertEquals(listOf(1, 2), parsed.pages.map { it.pageNumber })
        val first = parsed.pages[0].text
        assertTrue(first.contains("[DOCX table=1 row=1 header=true cell=1]"))
        assertTrue(first.contains("Encabezado"))
        assertTrue(first.contains("[DOCX table=1 row=1 header=true cell=2]"))
        assertTrue(first.contains("Sin salto."))
        assertTrue(parsed.pages[1].text.contains("Página dos."))
    }

    @Test fun plainParagraphAndTableSeparatorsRemainCompatible() {
        val parsed = parseDocumentXml("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:r><w:t>con</w:t></w:r><w:r><w:t>tenido</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Nombre</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>Valor</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
        </w:body></w:document>""".trimIndent())
        assertEquals(1, parsed.pages.size)
        assertEquals(null, parsed.pages.single().pageNumber)
        assertTrue(parsed.pages.single().text.startsWith("contenido\n"))
        assertTrue(parsed.pages.single().text.contains("\t"))
    }

    @Test fun docxListNumberingAndNestingArePreservedInExtractedText() {
        val numbering = """<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:abstractNum w:abstractNumId="0">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/></w:lvl>
                <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="o"/></w:lvl>
            </w:abstractNum>
            <w:abstractNum w:abstractNumId="1">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>
            </w:abstractNum>
            <w:abstractNum w:abstractNumId="2">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="upperLetter"/><w:lvlText w:val="%1)"/></w:lvl>
            </w:abstractNum>
            <w:num w:numId="7"><w:abstractNumId w:val="0"/></w:num>
            <w:num w:numId="8"><w:abstractNumId w:val="1"/></w:num>
            <w:num w:numId="9"><w:abstractNumId w:val="2"/><w:lvlOverride w:ilvl="0"><w:startOverride w:val="3"/></w:lvlOverride></w:num>
        </w:numbering>""".trimIndent()
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="7"/></w:numPr></w:pPr><w:r><w:t>Primer elemento</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="7"/></w:numPr></w:pPr><w:r><w:t>Segundo elemento</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="7"/></w:numPr></w:pPr><w:r><w:t>Elemento anidado</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="8"/></w:numPr></w:pPr><w:r><w:t>Primero numerado</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="8"/></w:numPr></w:pPr><w:r><w:t>Segundo numerado</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="9"/></w:numPr></w:pPr><w:r><w:t>Tercer valor alfabético</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="9"/></w:numPr></w:pPr><w:r><w:t>Cuarto valor alfabético</w:t></w:r></w:p>
        </w:body></w:document>""".trimIndent()

        val text = parseDocumentXml(document, numbering).pages.single().text
        assertTrue("Bullet list semantics were lost: $text", text.contains("• Primer elemento"))
        assertTrue("Bullet counter/marker should remain stable: $text", text.contains("• Segundo elemento"))
        assertTrue("Nested list level should remain visible: $text", text.contains("  ◦ Elemento anidado"))
        assertTrue("Numbering should resolve from numbering.xml: $text", text.contains("1. Primero numerado"))
        assertTrue("The same numId should increment in document order: $text", text.contains("2. Segundo numerado"))
        assertTrue("A numbering-instance start override should be used: $text", text.contains("C) Tercer valor alfabético"))
        assertTrue("The overridden numbering instance should advance: $text", text.contains("D) Cuarto valor alfabético"))
    }

    @Test fun unresolvedDocxListDefinitionIsVisibleAndKeepsItemText() {
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:pPr><w:numPr><w:numId w:val="99"/></w:numPr></w:pPr><w:r><w:t>Elemento sin definición</w:t></w:r></w:p>
        </w:body></w:document>""".trimIndent()
        val text = parseDocumentXml(document).pages.single().text
        assertTrue("Missing numbering metadata must be visible: $text", text.contains("[DOCX list unresolved numId=99"))
        assertTrue("An unresolved list must retain its actual text: $text", text.contains("Elemento sin definición"))
    }
}
