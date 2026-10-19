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
import java.util.zip.CRC32
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
            assertTrue(parsed.pages[0].text.contains("Elemento enumerado"))
            assertTrue(parsed.pages[1].text.contains("Segunda página"))
            assertTrue(parsed.pages[1].text.contains("Álvaro"))
            assertTrue(parsed.pages[1].text.contains("42"))

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

    @Test
    fun generatedDocxEmitsOrderedListNumberingAndKeepsBulletList() = runBlocking {
        val root = File(context.cacheDir, "docx-ordered-list-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val source = JSONObject("""
            {
              "format":"DOCX","name":"pasos.docx",
              "blocks":[
                {"kind":"paragraph","text":"Primer paso","style":"numbered"},
                {"kind":"paragraph","text":"Segundo paso","style":"numbered"},
                {"kind":"paragraph","text":"Nota con viñeta","style":"bullet"}
              ]
            }
        """.trimIndent())
        try {
            val revision = store.create("docx-ordered-list-project", ArtifactSpec.parse(source))
            val file = store.verifiedFile("docx-ordered-list-project", revision)
            File("build/docx-independent-reader").mkdirs()
            File("build/docx-independent-reader/ordered-list.docx").writeBytes(file.readBytes())

            ZipFile(file).use { zip ->
                val document = xml(zip, "word/document.xml")
                val numberIds = document.getElementsByTagNameNS(word, "numId")
                assertEquals(3, numberIds.length)
                assertEquals(listOf("2", "2", "1"), (0 until numberIds.length).map {
                    numberIds.item(it).attributes.getNamedItemNS(word, "val").nodeValue
                })

                val numbering = xml(zip, "word/numbering.xml")
                val formats = numbering.getElementsByTagNameNS(word, "numFmt")
                assertEquals(setOf("bullet", "decimal"), (0 until formats.length).map {
                    formats.item(it).attributes.getNamedItemNS(word, "val").nodeValue
                }.toSet())
                val instances = numbering.getElementsByTagNameNS(word, "num")
                assertEquals(2, instances.length)
            }

            val parsed = DocumentParser(context).parse(file, revision.format.mime, revision.name)
            val text = parsed.pages.joinToString("\n") { it.text }
            assertTrue("Ordered numbering should survive the generated DOCX roundtrip: $text", text.contains("1. Primer paso"))
            assertTrue("The second numbered paragraph should advance: $text", text.contains("2. Segundo paso"))
            assertTrue("Existing bullet numbering should remain available: $text", text.contains("• Nota con viñeta"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generatedDocxPreservesParagraphAndTableBlockOrder() = runBlocking {
        val root = File(context.cacheDir, "docx-ordered-blocks-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val source = JSONObject("""
            {
              "format":"DOCX","name":"ordered.docx",
              "blocks":[
                {"kind":"paragraph","text":"Before the table","style":"body"},
                {"kind":"table","rows":[["Label","Value"],["Entry","7"]]},
                {"kind":"paragraph","text":"After the table","style":"body"}
              ]
            }
        """.trimIndent())
        try {
            val revision = store.create("docx-ordered-project", ArtifactSpec.parse(source))
            val file = store.verifiedFile("docx-ordered-project", revision)
            fun bodyChildren(target: File): List<String> = ZipFile(target).use { zip ->
                val body = xml(zip, "word/document.xml").getElementsByTagNameNS(word, "body").item(0)
                (0 until body.childNodes.length).mapNotNull { index ->
                    body.childNodes.item(index).takeIf { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }?.localName
                }
            }
            val originalHash = revision.sha256
            assertEquals(listOf("p", "tbl", "p", "sectPr"), bodyChildren(file))
            val preview = store.spec("docx-ordered-project", revision).preview()
            assertTrue(preview.indexOf("Before the table") < preview.indexOf("Label"))
            assertTrue(preview.indexOf("Label") < preview.indexOf("After the table"))
            File("build/docx-independent-reader").mkdirs()
            File("build/docx-independent-reader/ordered-blocks-v1.docx").writeBytes(file.readBytes())

            val revisedSpec = store.spec("docx-ordered-project", revision).json()
            revisedSpec.getJSONArray("blocks").getJSONObject(2).put("text", "After the revised table")
            val revised = store.revise("docx-ordered-project", revision.id, revision.version, ArtifactSpec.parse(revisedSpec))
            assertEquals(2, store.history("docx-ordered-project", revision.id).size)
            assertEquals(originalHash, ArtifactStore.fileHash(store.verifiedFile("docx-ordered-project", revision)))
            val revisedFile = store.verifiedFile("docx-ordered-project", revised)
            assertEquals(listOf("p", "tbl", "p", "sectPr"), bodyChildren(revisedFile))
            assertTrue(store.spec("docx-ordered-project", revision).preview().contains("After the table"))
            assertTrue(store.spec("docx-ordered-project", revised).preview().contains("After the revised table"))
            File("build/docx-independent-reader/ordered-blocks-v2.docx").writeBytes(revisedFile.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun generatedDocxBuiltInTemplatesResolveStylesAndKeepDocumentContent() = runBlocking {
        val root = File(context.cacheDir, "docx-templates-${UUID.randomUUID()}")
        val store = ArtifactStore(context, root)
        val templates = listOf(
            listOf("report", "Calibri", "3E746D", "40", "32"),
            listOf("meeting_minutes", "Arial", "1D2522", "36", "28")
        )
        val exported = File("build/docx-templates").apply { mkdirs() }
        var reportRevision:ArtifactRevision? = null
        try {
            for ((template, expectedFont, expectedTitleColor, expectedTitleSize, expectedHeadingSize) in templates) {
                val source = JSONObject("""
                    {
                      "format":"DOCX","name":"$template.docx","template":"$template",
                      "blocks":[
                        {"kind":"paragraph","text":"Título de plantilla","style":"title"},
                        {"kind":"paragraph","text":"Acuerdos","style":"heading"},
                        {"kind":"paragraph","text":"Contenido conservado","style":"body"},
                        {"kind":"table","rows":[["Campo","Valor"],["Estado","Aprobado"]]}
                      ]
                    }
                """.trimIndent())
                val revision = store.create("docx-templates-project", ArtifactSpec.parse(source))
                if(template == "report") reportRevision=revision
                val file = store.verifiedFile("docx-templates-project", revision)
                assertEquals(template, store.spec("docx-templates-project", revision).json().getString("template"))
                val output = File(exported, "$template.docx").apply { writeBytes(file.readBytes()) }
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        val crc = CRC32()
                        zip.getInputStream(entry).use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                crc.update(buffer, 0, count)
                            }
                        }
                        assertEquals("DOCX ZIP CRC mismatch in ${entry.name}", entry.crc, crc.value)
                    }
                    val styles = xml(zip, "word/styles.xml")
                    val styleNodes = styles.getElementsByTagNameNS(word, "style")
                    fun style(id:String):org.w3c.dom.Element = (0 until styleNodes.length)
                        .map { styleNodes.item(it) }
                        .single { it.attributes.getNamedItemNS(word, "styleId").nodeValue == id } as org.w3c.dom.Element
                    for ((styleId, expectedSize) in listOf("Title" to expectedTitleSize, "Heading1" to expectedHeadingSize)) {
                        val styled = style(styleId)
                        val fonts = styled.getElementsByTagNameNS(word, "rFonts")
                        assertTrue("Template $template did not define $styleId font", fonts.length > 0)
                        assertEquals(expectedFont, fonts.item(0).attributes.getNamedItemNS(word, "ascii").nodeValue)
                        val colors = styled.getElementsByTagNameNS(word, "color")
                        assertTrue("Template $template did not define $styleId color", colors.length > 0)
                        assertEquals(expectedTitleColor, colors.item(0).attributes.getNamedItemNS(word, "val").nodeValue)
                        assertEquals(expectedSize, styled.getElementsByTagNameNS(word, "sz").item(0)
                            .attributes.getNamedItemNS(word, "val").nodeValue)
                    }

                    val document = xml(zip, "word/document.xml")
                    assertEquals(1, document.getElementsByTagNameNS(word, "tbl").length)
                    val paragraphStyles = document.getElementsByTagNameNS(word, "pStyle")
                    assertEquals(listOf("Title", "Heading1"), (0 until paragraphStyles.length).map {
                        paragraphStyles.item(it).attributes.getNamedItemNS(word, "val").nodeValue
                    })
                    val body = document.getElementsByTagNameNS(word, "body").item(0)
                    val directChildren = (0 until body.childNodes.length).mapNotNull { index ->
                        body.childNodes.item(index).takeIf { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }?.localName
                    }
                    assertEquals(listOf("p", "p", "p", "tbl", "sectPr"), directChildren)
                }
                val parsed = DocumentParser(context).parse(output, ArtifactFormat.DOCX.mime, output.name)
                val extracted = parsed.pages.joinToString("\n") { it.text }
                assertTrue(extracted.contains("Título de plantilla"))
                assertTrue(extracted.contains("Acuerdos"))
                assertTrue(extracted.contains("Contenido conservado"))
                assertTrue(extracted.contains("Aprobado"))
            }

            val report=checkNotNull(reportRevision)
            val originalBytes=store.verifiedFile("docx-templates-project",report).readBytes()
            val revisedSpec=store.spec("docx-templates-project",report).json()
            revisedSpec.getJSONArray("blocks").getJSONObject(2).put("text","Contenido revisado")
            val revised=store.revise("docx-templates-project",report.id,report.version,ArtifactSpec.parse(revisedSpec))
            assertEquals(2,store.history("docx-templates-project",report.id).size)
            assertEquals("report",store.spec("docx-templates-project",report).json().getString("template"))
            assertEquals("report",store.spec("docx-templates-project",revised).json().getString("template"))
            assertEquals(report.sha256,ArtifactStore.fileHash(store.verifiedFile("docx-templates-project",report)))
            assertTrue(store.spec("docx-templates-project",revised).preview().contains("Contenido revisado"))
            assertTrue(originalBytes.contentEquals(store.verifiedFile("docx-templates-project",report).readBytes()))

            val omittedTemplate = JSONObject("""{"format":"DOCX","name":"omitted.docx","paragraphs":[{"text":"Legacy","style":"title"}]}""")
            val explicitStandard = JSONObject(omittedTemplate.toString()).put("template", "standard")
            val omittedRevision = store.create("docx-standard-project", ArtifactSpec.parse(omittedTemplate))
            val explicitRevision = store.create("docx-standard-project", ArtifactSpec.parse(explicitStandard))
            fun stylesXml(revision:ArtifactRevision):String = ZipFile(store.verifiedFile("docx-standard-project",revision)).use { zip ->
                zip.getInputStream(zip.getEntry("word/styles.xml") ?: error("Missing styles.xml")).bufferedReader().use { it.readText() }
            }
            assertEquals("Omitting template must preserve the legacy style part byte-for-byte", stylesXml(omittedRevision), stylesXml(explicitRevision))

            val unsupported = JSONObject("""
                {"format":"DOCX","name":"unknown.docx","template":"untrusted_template",
                 "blocks":[{"kind":"paragraph","text":"Content"}]}
            """.trimIndent())
            val error = try {
                ArtifactSpec.parse(unsupported)
                null
            } catch (failure: IllegalArgumentException) {
                failure
            }
            assertNotNull("Unknown DOCX templates must be rejected", error)
            assertEquals("DOCX_TEMPLATE_UNSUPPORTED", error?.message)

            val wrongFormat = try {
                ArtifactSpec.parse(JSONObject("""{"format":"XLSX","name":"book.xlsx","template":"report","sheets":[{"name":"Sheet1","cells":[]}]}"""))
                null
            } catch (failure: IllegalArgumentException) {
                failure
            }
            assertNotNull("Template selection must remain DOCX-only", wrongFormat)
            assertEquals("ARTIFACT_FIELD_UNSUPPORTED", wrongFormat?.message)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun revisedParagraphs(original: JSONObject): org.json.JSONArray =
        original.getJSONArray("paragraphs").apply { getJSONObject(0).put("text", "Informe actualizado") }
}
