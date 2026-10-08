package com.localai.workspace.data
import com.localai.workspace.documents.StructuredDocuments as S
import com.localai.workspace.context.ContextEvidenceExcerptSelector
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29], application=android.app.Application::class)
class StructuredDocumentsTest {
 private fun zip(entries: Map<String,String>): File = File.createTempFile("docs", ".zip").apply { ZipOutputStream(outputStream()).use { z -> entries.forEach { (p,s) -> z.putNextEntry(ZipEntry(p));z.write(s.toByteArray());z.closeEntry() } } }
 @Test fun csvPreservesQuotedCellsAndRows() { val f=File.createTempFile("csv", ".csv");try { f.writeText("name;value\n\"a;b\";12\n\"multi\nline\";4");val table=S.csv(f);assertEquals("a;b",table.cells[2].value);assertEquals(3,table.cells[4].row);assertEquals("multi\nline",table.cells[4].value) } finally { f.delete() } }
 @Test fun invalidEncodingDoesNotBecomeGibberish() { assertThrows(Exception::class.java) { S.text(byteArrayOf(0xc3.toByte(),0x28)) };assertEquals("a",S.text(byteArrayOf(-1,-2,97,0)).first) }
 @Test fun zipSlipAndBombRejected() { val f=zip(mapOf("../secret" to "x"));try { ZipFile(f).use { assertThrows(Exception::class.java) { S.inspect(it) } } } finally { f.delete() };assertFalse(S.safePath("C:/a"));assertFalse(S.safePath("a\\b"));assertTrue(S.safePath("folder/a.csv")) }
 @Test fun xlsxPreservesSheetAddressCachedValueFormula() { val f=zip(mapOf("xl/workbook.xml" to "<workbook xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='Sales' r:id='r1'/></sheets></workbook>", "xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id='r1' Target='worksheets/sheet1.xml'/></Relationships>", "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r='1'><c r='B1'><f>SUM(A1:A2)</f><v>12</v></c></row></sheetData></worksheet>"));try { val c=S.xlsx(f).cells.single();assertEquals("Sales",c.sheet);assertEquals(2,c.column);assertEquals("12",c.value);assertEquals("SUM(A1:A2)",c.formula) } finally { f.delete() } }
 @Test fun formattedEmptyXlsxCellsDoNotConsumeRepresentationBudget() {
  val blankCells=40_000
  val rows=buildString {
   append("<row r='1'><c r='A1'><v>1</v></c></row><row r='2'><c r='A2'><v>5</v></c></row><row r='3'><c r='A3'><f>A2-A1</f><v>4</v></c></row>")
   for(row in 4..blankCells+3) {
    val type=if(row%2==0) " t='s'" else ""
    append("<row r='$row'><c r='A$row' s='1'$type/></row>")
   }
  }
  val f=zip(mapOf(
   "xl/workbook.xml" to "<workbook xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='Sales' r:id='r1'/></sheets></workbook>",
   "xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id='r1' Target='worksheets/sheet1.xml'/></Relationships>",
   "xl/styles.xml" to "<styleSheet><cellXfs count='1'><xf numFmtId='0' fontId='0' fillId='0' borderId='0'/></cellXfs></styleSheet>",
   "xl/worksheets/sheet1.xml" to "<worksheet><dimension ref='A1:A${blankCells+3}'/><sheetData>$rows</sheetData></worksheet>"
  ))
  try {
   val (compressedBytes,expandedXmlBytes)=ZipFile(f).use { archive ->
    val xml=archive.getEntry("xl/worksheets/sheet1.xml")
    f.length() to xml.size
   }
   var stageStarted=System.nanoTime()
   val table=S.xlsx(f)
   val parserMs=(System.nanoTime()-stageStarted)/1_000_000
   assertEquals(3,table.cells.size)
   assertEquals("A2-A1",table.cells.single { it.address=="A3" }.formula)
   assertEquals("4",table.cells.single { it.address=="A3" }.value)
   stageStarted=System.nanoTime()
   val document=table.document()
   val representationMs=(System.nanoTime()-stageStarted)/1_000_000
   stageStarted=System.nanoTime()
   val chunks=TextChunker().chunk(document)
   val chunkMs=(System.nanoTime()-stageStarted)/1_000_000
   assertTrue(document.pages.single().text.length<2_000)
   val evidence=ContextEvidenceExcerptSelector.select("¿Cuál es el valor de la casilla A3?",document.pages.single().text)
   assertEquals(listOf("A3","A1","A2"),evidence.cellAddresses)
   assertTrue(evidence.text.contains("A3=4"))
   assertTrue(evidence.text.contains("formula=A2-A1"))
   val report=org.json.JSONObject().put("environment","HOST_SYNTHETIC_NOT_USER_WORKBOOK")
    .put("compressedWorkbookBytes",compressedBytes).put("expandedWorksheetXmlBytes",expandedXmlBytes)
    .put("declaredDimensions","A1:A${blankCells+3}").put("populatedCellCount",table.cells.size)
    .put("emptyFormattedCellCount",blankCells).put("styleDefinitions",1)
    .put("serializedRepresentationUtf8Bytes",document.pages.sumOf { it.text.toByteArray(Charsets.UTF_8).size })
    .put("chunkCount",chunks.size).put("hostXlsxParserMs",parserMs).put("hostRepresentationMs",representationMs).put("hostChunkCreationMs",chunkMs)
    .put("embeddingOrIndexingExecuted",false)
   File("build/reports/xlsx-formatting-repro.json").apply { parentFile?.mkdirs();writeText(report.toString(2)) }
   assertTrue("Synthetic XLSX should be well below the reported 8 MiB compressed size",compressedBytes<8*1024*1024)
   assertTrue(expandedXmlBytes>compressedBytes)
  } finally { f.delete() }
 }
 @Test fun emptySharedStringTypedCellsAreSkippedButExplicitValuesRemainValidated() {
  val f=zip(mapOf(
   "xl/workbook.xml" to "<workbook xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='Sales' r:id='r1'/></sheets></workbook>",
   "xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id='r1' Target='worksheets/sheet1.xml'/></Relationships>",
   "xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r='1'><c r='A1' t='s' s='1'/><c r='B1'><v>2</v></c></row></sheetData></worksheet>"
  ))
  try { assertEquals(listOf("B1"),S.xlsx(f).cells.map{it.address}) } finally { f.delete() }
 }
 @Test fun tooLargeFileRejectedBeforeRead() { val f=File.createTempFile("big", ".csv");try { java.io.RandomAccessFile(f,"rw").use { it.setLength(S.MAX_BYTES.toLong()+1) };assertThrows(Exception::class.java) { S.csv(f) } } finally { f.delete() } }
 @Test fun repeatedSharedValuesCannotExpandContextWithoutBound() {
  val shared="x".repeat(32768)
  val cells=(1..1000).map { S.Cell("Sheet",it,1,"A$it",shared) }
  val error=assertThrows(IllegalArgumentException::class.java) { S.Table(cells).document() }
  assertTrue(error.message!!.contains("representation"))
 }
 @Test fun normalStructuredRowsStillPreserveHeaderAndFormula() {
  val doc=S.Table(listOf(S.Cell("Sales",1,1,"A1","Amount"),S.Cell("Sales",2,1,"A2","12","SUM(B2:C2)"))).document()
  assertTrue(doc.pages.single().text.contains("headerCandidate"))
  assertTrue(doc.pages.single().text.contains("SUM(B2:C2)"))
 }
}
