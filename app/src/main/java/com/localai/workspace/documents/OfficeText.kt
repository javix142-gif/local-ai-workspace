package com.localai.workspace.documents

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import com.localai.workspace.data.ParsedDocument
import com.localai.workspace.data.ParsedPage

/** Bounded extraction of actual text nodes; field instructions/XML indentation are not prose. */
object OfficeText {
    private fun xml(zip:ZipFile,name:String):String {
        require(StructuredDocuments.safePath(name)){"OFFICE_PART_PATH_UNSAFE"}
        val entry=zip.getEntry(name) ?: error("OFFICE_PART_MISSING:$name")
        val text=StructuredDocuments.text(zip.getInputStream(entry).use{StructuredDocuments.bounded(it,8*1024*1024)}).first
        require(!text.contains("<!DOCTYPE",true)&&!text.contains("<!ENTITY",true)){"XML_ENTITIES_FORBIDDEN"};return text
    }
    private fun parser(text:String)=Xml.newPullParser().apply{setInput(text.reader())}
    private fun text(text:String):String {
        val p=parser(text);val out=StringBuilder();var inText=false
        while(p.eventType!=XmlPullParser.END_DOCUMENT) {
            if(Thread.currentThread().isInterrupted)throw InterruptedException()
            when(p.eventType) {
                XmlPullParser.START_TAG->when(p.name){"t"->inText=true;"tab"->out.append('\t');"br"->out.append('\n')}
                XmlPullParser.TEXT->if(inText)out.append(p.text)
                XmlPullParser.END_TAG->when(p.name){"t"->inText=false;"p"->out.append('\n');"tc"->out.append('\t');"tr"->out.append('\n')}
            }
            require(out.length<=1_000_000){"OFFICE_TEXT_LIMIT"};p.next()
        };return out.toString().trim()
    }
    private fun attribute(parser: XmlPullParser, name: String): String? = (0 until parser.attributeCount)
        .firstOrNull { parser.getAttributeName(it).substringAfter(':') == name }
        ?.let(parser::getAttributeValue)

    fun docx(zip: ZipFile): ParsedDocument {
        val source = parser(xml(zip, "word/document.xml"))
        val pages = mutableListOf<Pair<Int, String>>()
        var page = StringBuilder()
        var pageNumber = 1
        var hasExplicitPageBreak = false
        var emittedCharacters = 0
        var sourceCharacters = 0
        var paragraphNumber = 0
        var paragraphOpen = false
        var paragraphStyle: String? = null
        var paragraphText = StringBuilder()
        var inText = false
        var tableNumber = 0
        var tableRow = 0
        var tableCell = 0
        var rowIsHeader = false
        var cellOpen = false
        var cellText = StringBuilder()
        var cellHasBeenEmitted = false

        fun appendPage(value: String) {
            emittedCharacters += value.length
            require(emittedCharacters <= 1_000_000) { "OFFICE_TEXT_LIMIT" }
            page.append(value)
        }

        fun appendParagraph(value: String) {
            require(paragraphText.length + value.length <= 1_000_000) { "OFFICE_TEXT_LIMIT" }
            paragraphText.append(value)
        }

        fun appendCell(value: String) {
            require(cellText.length + value.length <= 1_000_000) { "OFFICE_TEXT_LIMIT" }
            cellText.append(value)
        }

        fun styleMarker(): String? {
            val style = paragraphStyle?.takeIf(String::isNotBlank) ?: return null
            if (!(style.startsWith("Heading", ignoreCase = true) ||
                style.equals("Title", ignoreCase = true) || style.equals("Subtitle", ignoreCase = true)
            )) return null
            return "[DOCX paragraph=$paragraphNumber heading=$style]"
        }

        fun emitParagraph(preserveEmpty: Boolean = false) {
            if (paragraphText.isEmpty() && !preserveEmpty) return
            val content = paragraphText.toString()
            paragraphText.setLength(0)
            val marker = styleMarker()
            if (cellOpen) {
                if (cellText.isNotEmpty()) appendCell("\n")
                if (marker != null) appendCell("$marker\n")
                appendCell(content)
            } else {
                if (page.isNotEmpty() && page.last() != '\n') appendPage("\n")
                if (marker != null) {
                    appendPage(marker)
                    appendPage("\n")
                }
                appendPage(content)
                appendPage("\n")
            }
        }

        fun emitCell() {
            if (cellHasBeenEmitted && cellText.isEmpty()) return
            if (page.isNotEmpty() && page.last() != '\n' && page.last() != '\t') appendPage("\n")
            appendPage("[DOCX table=$tableNumber row=$tableRow")
            if (rowIsHeader) appendPage(" header=true")
            appendPage(" cell=$tableCell]\n")
            appendPage(cellText.toString())
            appendPage("\t")
            cellText.setLength(0)
            cellHasBeenEmitted = true
        }

        fun splitPage() {
            if (paragraphText.isNotEmpty()) emitParagraph()
            if (cellOpen && cellText.isNotEmpty()) emitCell()
            pages.add(pageNumber to page.toString().trimEnd())
            page = StringBuilder()
            pageNumber++
            hasExplicitPageBreak = true
        }

        while (source.eventType != XmlPullParser.END_DOCUMENT) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            when (source.eventType) {
                XmlPullParser.START_TAG -> when (source.name) {
                    "p" -> {
                        paragraphOpen = true
                        paragraphNumber++
                        paragraphStyle = null
                        paragraphText = StringBuilder()
                    }
                    "pStyle" -> if (paragraphOpen) {
                        paragraphStyle = attribute(source, "val")?.takeIf(String::isNotBlank)
                    }
                    "pageBreakBefore" -> if (paragraphOpen && attribute(source, "val") !in setOf("0", "false", "off")) {
                        splitPage()
                    }
                    "tbl" -> {
                        tableNumber++
                        tableRow = 0
                    }
                    "tr" -> {
                        tableRow++
                        tableCell = 0
                        rowIsHeader = false
                    }
                    "tblHeader" -> rowIsHeader = attribute(source, "val") !in setOf("0", "false", "off")
                    "tc" -> {
                        tableCell++
                        cellOpen = true
                        cellText = StringBuilder()
                        cellHasBeenEmitted = false
                    }
                    "t" -> inText = true
                    "tab" -> appendParagraph("\t")
                    "cr" -> appendParagraph("\n")
                    "br" -> if (attribute(source, "type") == "page") splitPage() else appendParagraph("\n")
                }
                XmlPullParser.TEXT -> if (inText) {
                    sourceCharacters += source.text.length
                    require(sourceCharacters <= 1_000_000) { "OFFICE_TEXT_LIMIT" }
                    appendParagraph(source.text)
                }
                XmlPullParser.END_TAG -> when (source.name) {
                    "t" -> inText = false
                    "p" -> {
                        emitParagraph(preserveEmpty = true)
                        paragraphOpen = false
                    }
                    "tc" -> {
                        emitCell()
                        cellOpen = false
                    }
                    "tr" -> if (page.isNotEmpty() && page.last() != '\n') appendPage("\n")
                    "tbl" -> if (page.isNotEmpty() && page.last() != '\n') appendPage("\n")
                }
            }
            source.next()
        }

        if (paragraphText.isNotEmpty()) emitParagraph()
        if (cellOpen) emitCell()
        if (page.isNotEmpty() || pages.isEmpty() || hasExplicitPageBreak) {
            pages.add(pageNumber to page.toString().trimEnd())
        }
        val parsedPages = if (hasExplicitPageBreak) {
            pages.map { (number, content) -> ParsedPage(number, content) }
        } else {
            listOf(ParsedPage(null, pages.firstOrNull()?.second.orEmpty()))
        }
        return ParsedDocument(parsedPages)
    }
    fun pptx(zip:ZipFile):ParsedDocument {
        StructuredDocuments.inspect(zip)
        val rel=parser(xml(zip,"ppt/_rels/presentation.xml.rels"));val targets=mutableMapOf<String,String>()
        while(rel.eventType!=XmlPullParser.END_DOCUMENT) {
            if(rel.eventType==XmlPullParser.START_TAG && rel.name=="Relationship" && rel.getAttributeValue(null,"Type").orEmpty().endsWith("/slide")) {
                require(rel.getAttributeValue(null,"TargetMode")!="External"){"EXTERNAL_SLIDE_UNSUPPORTED"}
                val target=rel.getAttributeValue(null,"Target");require(target!=null){"SLIDE_TARGET_MISSING"}
                val path=if(target.startsWith("/ppt/slides/"))target.drop(1)else "ppt/$target"
                require(path.startsWith("ppt/slides/")&&StructuredDocuments.safePath(path)){"SLIDE_PATH_UNSAFE"}
                targets[rel.getAttributeValue(null,"Id") ?: error("SLIDE_ID_MISSING")]=path
            };rel.next()
        }
        val presentation=parser(xml(zip,"ppt/presentation.xml"));val paths=mutableListOf<String>()
        while(presentation.eventType!=XmlPullParser.END_DOCUMENT) {
            if(presentation.eventType==XmlPullParser.START_TAG && presentation.name=="sldId") {
                val id=presentation.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id") ?: error("SLIDE_REFERENCE_MISSING")
                paths+=targets[id] ?: error("SLIDE_RELATIONSHIP_MISSING");require(paths.size<=100){"SLIDE_LIMIT"}
            };presentation.next()
        }
        require(paths.isNotEmpty()&&paths.distinct().size==paths.size){"SLIDE_ORDER_INVALID"}
        var size=0
        return ParsedDocument(paths.mapIndexed{i,path->val content=text(xml(zip,path));size+=content.length;require(size<=1_000_000){"PRESENTATION_TEXT_LIMIT"};ParsedPage(i+1,content)})
    }
}
