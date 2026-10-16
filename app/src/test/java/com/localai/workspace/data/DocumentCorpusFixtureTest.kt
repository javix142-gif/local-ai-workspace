package com.localai.workspace.data

import android.app.Application
import com.localai.workspace.context.ContextEvidenceExcerptSelector
import com.localai.workspace.documents.StructuredDocuments
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Exercises the checked-in synthetic corpus through the production local DocumentParser. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocumentCorpusFixtureTest {
    private val parser = DocumentParser(RuntimeEnvironment.getApplication())
    private val corpus: File by lazy {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (directory != null) {
            val candidate = File(directory, "docs/validation/master-program/02-artifacts-documents/synthetic-corpus")
            if (File(candidate, "expected-queries.json").isFile) return@lazy candidate
            directory = directory.parentFile
        }
        error("Checked-in synthetic document corpus was not found from the test working directory")
    }

    private val expectations: JSONObject by lazy {
        JSONObject(File(corpus, "expected-queries.json").readText(Charsets.UTF_8))
    }

    private fun expected(file: String, query: String): JSONObject {
        val cases = expectations.getJSONArray("cases")
        return (0 until cases.length()).asSequence()
            .map(cases::getJSONObject)
            .firstOrNull { it.optString("file") == file && it.optString("query") == query }
            ?: error("No expected corpus case for $file / $query")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private suspend fun parse(fileName: String, mimeType: String): ParsedDocument =
        parser.parse(File(corpus, fileName), mimeType, fileName)

    @Test fun corpusFilesHaveVersionedMetadataAndExpectedQueries() {
        assertTrue(expectations.getBoolean("synthetic"))
        assertTrue(expectations.getBoolean("notPrivateMotorolaWorkbook"))
        assertEquals(1, expectations.getInt("corpusVersion"))
        val validatedFormats = expectations.getJSONArray("appParserValidatedFormats")
        assertEquals(
            listOf("code", "csv", "docx", "pdf", "pptx", "xlsx"),
            (0 until validatedFormats.length()).map(validatedFormats::getString).sorted(),
        )

        val files = listOf(
            "v1-fixture.pdf", "v1-fixture.docx", "v1-fixture.xlsx", "v1-fixture.kt",
            "v2-fixture.pdf", "v2-fixture.docx", "v2-fixture.xlsx", "v2-fixture.kt",
            "v1-fixture.pptx", "v2-fixture.pptx",
            "quoted.csv",
        )
        files.forEach { name ->
            val source = File(corpus, name).readBytes()
            val metadata = JSONObject(File(corpus, "$name.metadata.json").readText(Charsets.UTF_8))
            assertTrue("$name must not be marked non-synthetic", metadata.optBoolean("synthetic", true))
            assertFalse("$name must not carry private source data", metadata.optBoolean("containsPrivateUserData", false))
            assertEquals("$name corpus revision", if (name.startsWith("v2-")) 2 else 1, metadata.getInt("version"))
            assertEquals("$name byte length", source.size, metadata.getInt("bytes"))
            assertEquals("$name SHA-256", metadata.getString("sha256"), sha256(source))
        }

        val formats = files.map { it.substringAfterLast('.') }.toSet()
        assertTrue(formats.containsAll(setOf("pdf", "docx", "xlsx", "csv", "kt")))
        assertEquals(1, files.count { it == "quoted.csv" })
    }

    @Test fun xlsxCellRangeFormulaCacheAndRevisionMatchExpectedResults() = runBlocking {
        val v1 = parse("v1-fixture.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val text = v1.pages.joinToString("\n") { it.text }
        val cellCase = expected("v1-fixture.xlsx", "Hoja2!B14")
        val cell = ContextEvidenceExcerptSelector.select(cellCase.getString("query"), text)
        assertNotNull(cell)
        assertEquals(listOf("Hoja2!B14"), cell!!.cellReferences)
        assertTrue(cell.text.contains(cellCase.getString("expected")))

        val rangeCase = expected("v1-fixture.xlsx", "Suma Hoja1!A1:A3")
        val range = ContextEvidenceExcerptSelector.select(rangeCase.getString("query"), text)
        assertNotNull(range)
        val expectedReferences = rangeCase.getJSONArray("expectedCellReferences")
        assertEquals((0 until expectedReferences.length()).map(expectedReferences::getString), range!!.cellReferences)
        val expectedValues = rangeCase.getJSONArray("expectedValues")
        (0 until expectedValues.length()).forEach { index ->
            assertTrue(range.text.contains("A${index + 1}=${expectedValues.getString(index)}"))
        }
        assertTrue(range.text.contains("formula=${rangeCase.getString("formula")}"))
        assertFalse("cached formula value is supplied, not recalculated", range.text.contains("recalculated=true"))

        val v2 = parse("v2-fixture.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .pages.joinToString("\n") { it.text }
        val revised = ContextEvidenceExcerptSelector.select(expected("v2-fixture.xlsx", "Hoja1!A2").getString("query"), v2)
        assertNotNull(revised)
        assertTrue(revised!!.text.contains("=7"))
        assertTrue(v2.contains("A3") && v2.contains("\$A\$2-\$A\$1") && v2.contains("4"))
    }

    @Test fun xlsxPreservesCellTypesSheetOrderFormulaAndCachedValueWithoutRecalculation() {
        val original = StructuredDocuments.xlsx(File(corpus, "v1-fixture.xlsx"))
        assertEquals(listOf("Hoja1", "Hoja2"), original.sheetNames)
        fun cell(sheet: String, address: String) = original.cells.single { it.sheet == sheet && it.address == address }

        assertEquals("n", cell("Hoja1", "A1").type)
        assertEquals("1", cell("Hoja1", "A1").value)
        assertEquals("inlineStr", cell("Hoja1", "B1").type)
        assertEquals("España & <valor>", cell("Hoja1", "B1").value)
        assertEquals("n", cell("Hoja1", "A2").type)
        assertEquals("5", cell("Hoja1", "A2").value)
        val formula = cell("Hoja1", "A3")
        assertEquals("n", formula.type)
        assertEquals("\$A\$2-\$A\$1", formula.formula)
        assertEquals("4", formula.value)
        assertEquals("inlineStr", cell("Hoja2", "B14").type)
        assertEquals("Exacto 界", cell("Hoja2", "B14").value)

        val serialized = original.document().pages.joinToString("\n") { it.text }
        assertTrue(serialized.contains("\"valueType\":\"n\""))
        assertTrue(serialized.contains("\"valueType\":\"inlineStr\""))
        assertTrue(serialized.contains(formula.formula!!))
        assertFalse("The structured representation must not claim formula recalculation", serialized.contains("recalculated"))

        val revised = StructuredDocuments.xlsx(File(corpus, "v2-fixture.xlsx"))
        assertEquals(listOf("Hoja1", "Hoja2"), revised.sheetNames)
        assertEquals("7", revised.cells.single { it.sheet == "Hoja1" && it.address == "A2" }.value)
        val cached = revised.cells.single { it.sheet == "Hoja1" && it.address == "A3" }
        assertEquals("\$A\$2-\$A\$1", cached.formula)
        assertEquals("4", cached.value)
    }

    @Test fun docxTableCellMatchesExpectedAnswer() = runBlocking {
        val expected = expected("v1-fixture.docx", "table row 2 column 1")
        val parsed = parse("v1-fixture.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        assertEquals(1, parsed.pages.size)
        assertTrue(parsed.pages.single().text.contains(expected.getString("expected")))
    }

    @Test fun digitalPdfPreservesExpectedPageAndAnswer() = runBlocking {
        val expected = expected("v1-fixture.pdf", "page 2")
        val parsed = parse("v1-fixture.pdf", "application/pdf")
        val page = expected.getString("query").substringAfterLast(' ').toInt()
        val selected = parsed.pages.singleOrNull { it.pageNumber == page }
        assertNotNull(selected)
        assertTrue(selected!!.text.contains(expected.getString("contains")))
    }

    @Test fun pptxSlideMatchesExpectedTitleAndSlideNumber() = runBlocking {
        val expected = expected("v1-fixture.pptx", "slide 2 title")
        val parsed = parse(
            "v1-fixture.pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )
        val slide = parsed.pages.singleOrNull { it.pageNumber == 2 }
        assertNotNull(slide)
        assertTrue(slide!!.text.contains(expected.getString("expected")))
    }

    @Test fun quotedCsvCellMatchesExpectedAnswerAndCoordinate() = runBlocking {
        val expected = expected("quoted.csv", "row 2 column 1")
        val parsed = parse("quoted.csv", "text/csv")
        val selected = ContextEvidenceExcerptSelector.select(expected.getString("cellReference"), parsed.pages.joinToString("\n") { it.text })
        assertNotNull(selected)
        assertEquals(listOf(expected.getString("cellReference")), selected!!.cellReferences)
        assertTrue(selected.text.contains(expected.getString("expected")))
    }

    @Test fun codeFixtureReturnsExpectedLineWithoutExecutingIt() = runBlocking {
        val expected = expected("v1-fixture.kt", "line 2")
        val parsed = parse("v1-fixture.kt", "text/plain")
        val line = expected.getString("query").substringAfterLast(' ').toInt()
        assertEquals(expected.getString("expected"), parsed.pages.single().text.lineSequence().elementAt(line - 1))
    }
}
