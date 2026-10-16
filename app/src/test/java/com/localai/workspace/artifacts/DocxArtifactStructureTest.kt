package com.localai.workspace.artifacts

import android.app.Application
import com.localai.workspace.data.DocumentParser
import kotlinx.coroutines.runBlocking
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
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocxArtifactStructureTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val packageRelationships = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val word = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val contentTypes = "http://schemas.openxmlformats.org/package/2006/content-types"

    private fun xml(zip: ZipFile, part: String): Document {
        val bytes = zip.getInputStream(zip.getEntry(part) ?: error("Missing DOCX part: $part"))
            .use { it.readBytes() }
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun resolve(sourcePart: String, target: String): String {
        require(!target.startsWith("/")) { "Unexpected absolute relationship target" }
        val parts = mutableListOf<String>()
        val base = sourcePart.substringBeforeLast('/', "")
        (if (base.isEmpty()) target else "$base/$target").split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> check(parts.isNotEmpty()) { "Relationship escapes package root" }.also { parts.removeAt(parts.lastIndex) }
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    @Test
    fun generatedDocxResolvesStylesNumberingTablesAndImmutableRevisions() = runBlocking {
        val root = File(context.cacheDir, "docx-structure-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val source = JSONObject("""
            {
              "format":"DOCX","name":"informe.docx",
              "paragraphs":[
                {"text":"Informe español","style":"title"},
                {"text":"Primera sección","style":"heading"},
                {"text":"Texto con ñ, € y <marcas>.","style":"body"},
                {"text":"Elemento enumerado","style":"bullet"},
                {"text":"Segunda página","style":"pageBreak"}
              ],
              "tables":[{"rows":[["Nombre","Valor"],["Álvaro","42"]]}]
            }
        """.trimIndent())
        try {
            val first = store.create("docx-structure-project", ArtifactSpec.parse(source))
            val firstBytes = store.verifiedFile("docx-structure-project", first).readBytes()
            val firstHash = first.sha256
            val outputDir = File("build/docx-independent-reader").apply { mkdirs() }
            val firstOutput = File(outputDir, "fixture-v1.docx").apply { writeBytes(firstBytes) }

            ZipFile(firstOutput).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(entries.size, entries.distinct().size)
                val required = setOf(
                    "[Content_Types].xml", "_rels/.rels", "word/document.xml",
                    "word/_rels/document.xml.rels", "word/styles.xml", "word/numbering.xml"
                )
                assertTrue("Missing required package parts: ${required - entries.toSet()}", entries.containsAll(required))

                val types = xml(zip, "[Content_Types].xml")
                val overrides = types.getElementsByTagNameNS(contentTypes, "Override")
                val overrideParts = (0 until overrides.length).map {
                    overrides.item(it).attributes.getNamedItem("PartName").nodeValue
                }.toSet()
                assertTrue("/word/document.xml" in overrideParts)
                assertTrue("/word/styles.xml" in overrideParts)
                assertTrue("/word/numbering.xml" in overrideParts)

                listOf("_rels/.rels" to "", "word/_rels/document.xml.rels" to "word/document.xml")
                    .forEach { (relationshipPart, sourcePart) ->
                        val relationships = xml(zip, relationshipPart)
                            .getElementsByTagNameNS(packageRelationships, "Relationship")
                        assertTrue("No relationships in $relationshipPart", relationships.length > 0)
                        for (index in 0 until relationships.length) {
                            val relationship = relationships.item(index)
                            assertFalse(relationship.attributes.getNamedItem("TargetMode")?.nodeValue == "External")
                            val target = relationship.attributes.getNamedItem("Target").nodeValue
                            assertNotNull("Unresolved relationship $relationshipPart -> $target", zip.getEntry(resolve(sourcePart, target)))
                        }
                    }

                val document = xml(zip, "word/document.xml")
                val styleIds = document.getElementsByTagNameNS(word, "pStyle")
                assertEquals(setOf("Title", "Heading1"), (0 until styleIds.length).map {
                    styleIds.item(it).attributes.getNamedItemNS(word, "val").nodeValue
                }.toSet())
                assertEquals(1, document.getElementsByTagNameNS(word, "pageBreakBefore").length)
                assertEquals(1, document.getElementsByTagNameNS(word, "numId").length)
                assertEquals("1", document.getElementsByTagNameNS(word, "numId").item(0)
                    .attributes.getNamedItemNS(word, "val").nodeValue)
                assertEquals(2, document.getElementsByTagNameNS(word, "gridCol").length)
                assertEquals(2, document.getElementsByTagNameNS(word, "tr").length)
                assertEquals(4, document.getElementsByTagNameNS(word, "tc").length)

                val styles = xml(zip, "word/styles.xml").getElementsByTagNameNS(word, "style")
                val definedStyles = (0 until styles.length).map {
                    styles.item(it).attributes.getNamedItemNS(word, "styleId").nodeValue
                }.toSet()
                assertTrue(definedStyles.containsAll(setOf("Normal", "Title", "Heading1")))

                val numbering = xml(zip, "word/numbering.xml")
                val nums = numbering.getElementsByTagNameNS(word, "num")
                assertEquals(1, nums.length)
                assertEquals("1", nums.item(0).attributes.getNamedItemNS(word, "numId").nodeValue)
            }

            val parsed = DocumentParser(context).parse(firstOutput, first.format.mime, first.name)
            assertEquals(2, parsed.pages.size)
            assertTrue(parsed.pages[0].text.contains("Informe español"))
            assertTrue(parsed.pages[0].text.contains("Álvaro"))
            assertTrue(parsed.pages[1].text.contains("Segunda página"))

            val originalSpec = store.spec("docx-structure-project", first).json()
            val revisedSpec = originalSpec.put("paragraphs", revisedParagraphs(originalSpec))
            val second = store.revise("docx-structure-project", first.id, first.version, ArtifactSpec.parse(revisedSpec))
            val secondOutput = File(outputDir, "fixture-v2.docx").apply {
                writeBytes(store.verifiedFile("docx-structure-project", second).readBytes())
            }
            assertTrue(firstOutput.isFile && secondOutput.isFile)
            assertEquals(firstHash, ArtifactStore.fileHash(store.verifiedFile("docx-structure-project", first)))
            assertTrue(second.sha256 != firstHash)
            assertTrue(DocumentParser(context).parse(secondOutput, second.format.mime, second.name)
                .pages.joinToString("\n") { it.text }.contains("Informe actualizado"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun revisedParagraphs(original: JSONObject): org.json.JSONArray =
        original.getJSONArray("paragraphs").apply { getJSONObject(0).put("text", "Informe actualizado") }
}
