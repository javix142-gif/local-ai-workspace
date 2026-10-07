package com.localai.workspace.documents

import com.localai.workspace.data.*
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/** Data-only records, bounded before allocation. Never evaluates formulas or archive contents. */
object StructuredDocuments {
 const val MAX_BYTES = 20 * 1024 * 1024
 const val MAX_EXPANDED = 32 * 1024 * 1024
 const val MAX_ENTRIES = 100
 const val MAX_CELLS = 100000
 const val MAX_REPRESENTATION_BYTES = 8 * 1024 * 1024
 data class Cell(val sheet: String, val row: Int, val column: Int, val address: String, val value: String, val formula: String? = null, val type: String? = null)
 data class Table(val cells: List<Cell>, val encoding: String = "UTF-8") {
  fun document(): ParsedDocument {
   // Shared strings and sheet names can expand many times without increasing ZIP size.
   // Bound the escaped JSON (including duplicated header candidates) before allocating it.
   var budget = 0L
   cells.forEach { c ->
    if(Thread.currentThread().isInterrupted) throw InterruptedException()
    require(c.sheet.length <= 128 && c.value.length <= 32768 && (c.formula?.length ?: 0) <= 32768 && (c.type?.length ?: 0) <= 32) { "Structured cell exceeds limit" }
    budget += 256L + 12L * (c.sheet.length.toLong() + c.address.length + c.value.length + (c.formula?.length ?: 0) + (c.type?.length ?: 0))
    require(budget <= MAX_REPRESENTATION_BYTES) { "Structured document representation exceeds 8 MiB budget" }
   }
   return ParsedDocument(cells.groupBy { it.sheet }.map { (sheet, cells) ->
   val firstRow=cells.filter { it.row==1 }.sortedBy { it.column }
   val header=if(firstRow.isNotEmpty() && firstRow.all { it.value.isNotBlank() && it.value.toDoubleOrNull()==null && it.formula==null } && firstRow.map { it.value }.toSet().size==firstRow.size)
    JSONObject().put("sheet",sheet).put("headerCandidate",JSONArray().apply { firstRow.forEach { put(JSONObject().put("column",it.column).put("label",it.value)) } }).toString()+"\n" else ""
   ParsedPage(null, header + cells.groupBy { it.row }.entries.joinToString("\n") { (row, values) ->
    JSONObject().put("sheet", sheet).put("row", row).put("cells", JSONArray().apply { values.forEach { c ->
     put(JSONObject().put("column", c.column).put("address", c.address).put("value", c.value).put("valueType",c.type ?: "raw").apply { c.formula?.let { put("formula", it) } })
    } }).toString()
   })
  })
  }
 }
 fun text(bytes: ByteArray): Pair<String,String> {
  require(bytes.size <= MAX_BYTES) { "File exceeds 20 MiB" }
  val (offset, charset) = when {
   bytes.size >= 3 && bytes.take(3) == listOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) -> 3 to Charsets.UTF_8
   bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> 2 to Charsets.UTF_16LE
   bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> 2 to Charsets.UTF_16BE
   else -> 0 to Charsets.UTF_8
  }
  val decoded = try { charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,offset,bytes.size-offset)).toString() }
  catch (_: java.nio.charset.CharacterCodingException) { throw UnsupportedDocumentException("Encoding is ambiguous or invalid. Save as UTF-8 or UTF-16 with BOM.") }
  require(!decoded.contains('\u0000')) { "Binary data is not a text document" }
  return decoded to charset.name()
 }
 fun csv(file: File): Table {
  val (source, encoding) = text(readFile(file))
  val first = source.lineSequence().firstOrNull().orEmpty()
  val delimiter = listOf(',', ';', '\t', '|').maxBy { candidate ->
   var quoted = false; first.count { c -> if(c == '"') quoted = !quoted; c == candidate && !quoted }
  }
  val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var i = 0; var fields=0
  fun field() { require(++fields <= MAX_CELLS) { "CSV exceeds 100000 cells" }; require(value.length <= 32768) { "Cell too large" }; row.add(value.toString()); value.setLength(0) }
  fun line() { field(); require(row.size <= 1000) { "Too many columns" }; rows.add(row.toList()); row.clear(); require(rows.size <= 10000) { "Too many rows" } }
  while(i < source.length) {
   if(Thread.currentThread().isInterrupted) throw InterruptedException()
   val c=source[i++]
   if(c=='"') { if(quoted && i<source.length && source[i]=='"') { value.append('"');i++ } else quoted=!quoted }
   else if(!quoted && c==delimiter) field()
   else if(!quoted && (c=='\n'||c=='\r')) { if(c=='\r'&&i<source.length&&source[i]=='\n') i++;line() }
   else { value.append(c); require(value.length<=32768) { "Cell too large" } }
  }
  require(!quoted) { "CSV has an unterminated quoted field" }
  if(value.isNotEmpty() || row.isNotEmpty()) line()
  return Table(rows.flatMapIndexed { r, values -> values.mapIndexed { c,v -> Cell("CSV",r+1,c+1,"${column(c+1)}${r+1}",v) } },encoding)
 }
 fun readFile(file: File): ByteArray { require(file.length() <= MAX_BYTES) { "File exceeds 20 MiB" }; return file.inputStream().use { bounded(it, MAX_BYTES) } }
 fun safePath(path: String): Boolean = path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') && path.split('/').none { it == ".." || it == "." }
 fun inspect(zip: ZipFile) {
  val entries=zip.entries().asSequence().take(MAX_ENTRIES + 1).toList(); require(entries.size<=MAX_ENTRIES) { "Archive exceeds 100 entries" }
  var size=0L; val names=mutableSetOf<String>()
  entries.forEach { e -> require(safePath(e.name) && names.add(e.name)) { "Unsafe or duplicate archive path" }; require(e.size>=0 && e.size<=8*1024*1024) { "Archive entry exceeds 8 MiB" };size+=e.size; require(size<=MAX_EXPANDED) { "Archive exceeds 32 MiB expanded" }; require(e.size<=1048576 || e.compressedSize>0 && e.size/e.compressedSize<=200) { "Suspicious compression ratio" } }
 }
 fun bounded(input: java.io.InputStream, max: Int): ByteArray {
  val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192);var n:Int
  while(input.read(buffer).also { n=it }>=0) { if(Thread.currentThread().isInterrupted) throw InterruptedException(); require(out.size()+n<=max) { "Expanded input exceeds limit" };out.write(buffer,0,n) };return out.toByteArray()
 }
 private fun xml(bytes: ByteArray, visit: (XmlPullParser)->Unit) {
  val s=text(bytes).first;require(!s.contains("<!DOCTYPE",true) && !s.contains("<!ENTITY",true)) { "XML entities are forbidden" }
  val parser=Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);setInput(s.reader()) }
  val deadline=System.nanoTime()+15_000_000_000
  while(parser.eventType!=XmlPullParser.END_DOCUMENT) { require(System.nanoTime()<deadline) { "XML parsing timed out" }; if(Thread.currentThread().isInterrupted) throw InterruptedException(); visit(parser);parser.next() }
 }
 fun xlsx(file: File): Table = ZipFile(file).use { zip ->
  inspect(zip)
  fun bytes(name:String) = zip.getEntry(name)?.let { e->zip.getInputStream(e).use { bounded(it,8*1024*1024) } } ?: error("Missing XLSX part: $name")
  val shared=mutableListOf<String>(); var s:StringBuilder?=null; var inT=false
  zip.getEntry("xl/sharedStrings.xml")?.let { e-> xml(zip.getInputStream(e).use { bounded(it,8*1024*1024) }) { p->
   if(p.eventType==XmlPullParser.START_TAG) when(p.name) { "si"->s=StringBuilder();"t"->inT=true }
   if(p.eventType==XmlPullParser.TEXT && inT) { s?.append(p.text); require((s?.length ?: 0)<=32768) { "Shared string exceeds cell limit" } }
   if(p.eventType==XmlPullParser.END_TAG) when(p.name) { "si"->{ require(shared.size<MAX_CELLS);shared.add(s.toString());s=null };"t"->inT=false }
  } }
  val rels=mutableMapOf<String,String>()
  xml(bytes("xl/_rels/workbook.xml.rels")) { p->if(p.eventType==XmlPullParser.START_TAG&&p.name=="Relationship") {
   val target=p.getAttributeValue(null,"Target") ?: ""; require(p.getAttributeValue(null,"TargetMode")!="External") { "External XLSX relationships are forbidden" }
   val path=if(target.startsWith("/xl/")) target.drop(1) else "xl/$target"; require(safePath(path));rels[p.getAttributeValue(null,"Id")]=path
  } }
  val sheets=mutableListOf<Pair<String,String>>()
  xml(bytes("xl/workbook.xml")) { p->if(p.eventType==XmlPullParser.START_TAG&&p.name=="sheet") {
   val id=(0 until p.attributeCount).firstOrNull { p.getAttributeName(it)=="id" }?.let { p.getAttributeValue(it) }
   val name=p.getAttributeValue(null,"name") ?: "Sheet"; require(name.length<=128) { "Worksheet name exceeds limit" }
   sheets.add(name to requireNotNull(rels[id]) { "Missing worksheet relation" })
  } }
  val cells=mutableListOf<Cell>()
  sheets.forEach { (sheet,path)->
   var address="";var type="";var value=StringBuilder();var formula=StringBuilder();var tag=""
   xml(bytes(path)) { p->when(p.eventType) {
    XmlPullParser.START_TAG -> { if(p.name=="c") { address=p.getAttributeValue(null,"r")?:error("Cell missing address");type=p.getAttributeValue(null,"t").orEmpty();value=StringBuilder();formula=StringBuilder() };tag=p.name }
    XmlPullParser.TEXT -> { if(tag=="v"||tag=="t") value.append(p.text);if(tag=="f")formula.append(p.text);require(value.length<=32768&&formula.length<=32768) }
    XmlPullParser.END_TAG -> { if(p.name=="c") { require(cells.size<MAX_CELLS);require(Regex("[A-Z]{1,3}[1-9][0-9]{0,6}").matches(address));val col=address.takeWhile { it.isLetter() }.fold(0){a,c->a*26+c.code-64};val row=address.dropWhile { it.isLetter() }.toInt();require(col<=16384&&row<=1048576);val v=if(type=="s") shared.getOrNull(value.toString().toIntOrNull()?:-1)?:error("Invalid shared string") else value.toString();cells.add(Cell(sheet,row,col,address,v,formula.toString().takeIf { it.isNotBlank() },type.ifBlank { "n" })) };tag="" }
   } }
  }
  Table(cells)
 }
 fun column(n: Int): String { var x=n;var out="";while(x>0){x--;out=('A'+x%26)+out;x/=26};return out }
}
