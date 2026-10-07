package com.localai.workspace.data
import com.localai.workspace.documents.StructuredDocuments as S
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
