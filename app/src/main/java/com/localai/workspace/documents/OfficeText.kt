package com.localai.workspace.documents

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile
import com.localai.workspace.data.ParsedDocument
import com.localai.workspace.data.ParsedPage

/**
 * Bounded resolver for the common explicit Word numbering structures used by paragraphs.
 * Unknown or missing definitions are returned as visible markers instead of flattening a list
 * item into ordinary prose.
 */
private class DocxNumbering private constructor(
    private val abstractLevels: Map<String, Map<Int, Level>>,
    private val instances: Map<String, NumberingInstance>,
) {
    private data class Level(val format: String?, val text: String?, val start: Int)
    private data class NumberingInstance(
        val abstractId: String,
        val overrides: Map<Int, Level>,
        val startOverrides: Map<Int, Int>,
    )

    fun marker(
        rawNumId: String,
        rawLevel: String?,
        counters: MutableMap<Pair<String, Int>, Int>,
    ): String {
        if (rawNumId == "0") return ""
        val level = when {
            rawLevel == null -> 0
            else -> rawLevel.toIntOrNull() ?: return unresolved(rawNumId, rawLevel)
        }
        if (level !in 0..8) return unresolved(rawNumId, rawLevel)
        val instance = instances[rawNumId] ?: return unresolved(rawNumId, rawLevel)
        val definition = instance.overrides[level] ?: abstractLevels[instance.abstractId]?.get(level)
            ?: return unresolved(rawNumId, rawLevel)
        val format = definition.format ?: return unresolved(rawNumId, rawLevel)
        val start = instance.startOverrides[level] ?: definition.start
        if (start !in 1..MAX_COUNTER) return unresolved(rawNumId, rawLevel)

        counters.keys.removeAll { it.first == rawNumId && it.second > level }
        val key = rawNumId to level
        val value = counters[key]?.let { previous ->
            if (previous >= MAX_COUNTER) return unresolved(rawNumId, rawLevel)
            previous + 1
        } ?: start
        counters[key] = value
        val indent = "  ".repeat(level)

        if (format == "bullet") {
            val glyph = when (level % 3) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
            return "$indent$glyph "
        }
        if (format == "none") return "${indent}[DOCX list item level=${level + 1}] "

        val template = definition.text ?: return unresolved(rawNumId, rawLevel)
        var unresolvedReference = false
        val rendered = REFERENCE.replace(template) { match ->
            val referencedLevel = match.groupValues[1].toInt() - 1
            val referencedDefinition = instance.overrides[referencedLevel]
                ?: abstractLevels[instance.abstractId]?.get(referencedLevel)
            val referencedFormat = referencedDefinition?.format
            val referencedStart = instance.startOverrides[referencedLevel]
                ?: referencedDefinition?.start
            val referencedValue = counters[rawNumId to referencedLevel] ?: referencedStart
            val replacement = if (referencedFormat != null && referencedValue != null) {
                formatNumber(referencedValue, referencedFormat)
            } else null
            if (replacement == null) {
                unresolvedReference = true
                match.value
            } else replacement
        }
        if (unresolvedReference || Regex("%[0-9]").containsMatchIn(rendered)) {
            return unresolved(rawNumId, rawLevel)
        }
        return "$indent$rendered "
    }

    private fun unresolved(numId: String, level: String?): String =
        "[DOCX list unresolved numId=${numId.take(24)} level=${level?.take(8) ?: "1"}] "

    private fun formatNumber(value: Int, format: String): String? = when (format) {
        "decimal" -> value.toString()
        "decimalZero" -> if (value < 10) "0$value" else value.toString()
        "lowerLetter" -> letters(value, upper = false)
        "upperLetter" -> letters(value, upper = true)
        "lowerRoman" -> roman(value)?.lowercase()
        "upperRoman" -> roman(value)
        else -> null
    }

    private fun letters(value: Int, upper: Boolean): String? {
        if (value !in 1..MAX_COUNTER) return null
        var remaining = value
        val output = StringBuilder()
        val base = if (upper) 'A'.code else 'a'.code
        while (remaining > 0) {
            remaining--
            output.append((base + remaining % 26).toChar())
            remaining /= 26
        }
        return output.reverse().toString()
    }

    private fun roman(value: Int): String? {
        if (value !in 1..3999) return null
        var remaining = value
        val output = StringBuilder()
        val symbols = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        symbols.forEach { (amount, symbol) ->
            while (remaining >= amount) {
                output.append(symbol)
                remaining -= amount
            }
        }
        return output.toString()
    }

    companion object {
        private const val MAX_NUMBERING_DEFINITIONS = 4096
        private const val MAX_COUNTER = 1_000_000
        private val REFERENCE = Regex("%([1-9])")
        val EMPTY = DocxNumbering(emptyMap(), emptyMap())

        fun read(zip: ZipFile): DocxNumbering {
            val entry = zip.getEntry("word/numbering.xml") ?: return EMPTY
            val bytes = zip.getInputStream(entry).use { StructuredDocuments.bounded(it, 8 * 1024 * 1024) }
            val text = StructuredDocuments.text(bytes).first
            require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) {
                "XML_ENTITIES_FORBIDDEN"
            }
            val parser = Xml.newPullParser().apply { setInput(text.reader()) }
            val abstracts = linkedMapOf<String, MutableMap<Int, Level>>()
            val instances = linkedMapOf<String, NumberingInstance>()
            var abstractId: String? = null
            var numId: String? = null
            var numAbstractId: String? = null
            var overrideLevel: Int? = null
            var activeLevel: Int? = null
            var activeLevelTarget: String? = null
            var activeFormat: String? = null
            var activeText: String? = null
            var activeStart = 1
            val levelOverrides = linkedMapOf<Int, Level>()
            val startOverrides = linkedMapOf<Int, Int>()

            fun attr(name: String): String? = (0 until parser.attributeCount)
                .firstOrNull { parser.getAttributeName(it).substringAfter(':') == name }
                ?.let(parser::getAttributeValue)

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "abstractNum" -> {
                            abstractId = attr("abstractNumId")
                            if (abstractId != null) require(abstracts.size < MAX_NUMBERING_DEFINITIONS) {
                                "DOCX_NUMBERING_LIMIT"
                            }
                        }
                        "num" -> {
                            numId = attr("numId")
                            numAbstractId = null
                            levelOverrides.clear()
                            startOverrides.clear()
                            if (numId != null) require(instances.size < MAX_NUMBERING_DEFINITIONS) {
                                "DOCX_NUMBERING_LIMIT"
                            }
                        }
                        "lvlOverride" -> overrideLevel = attr("ilvl")?.toIntOrNull()
                        "lvl" -> {
                            val index = attr("ilvl")?.toIntOrNull() ?: overrideLevel
                            if (index != null && index in 0..8) {
                                activeLevel = index
                                activeLevelTarget = when {
                                    abstractId != null -> "abstract"
                                    numId != null && overrideLevel != null -> "override"
                                    else -> null
                                }
                                activeFormat = null
                                activeText = null
                                activeStart = 1
                            }
                        }
                        "abstractNumId" -> if (numId != null && activeLevel == null) {
                            numAbstractId = attr("val")
                        }
                        "startOverride" -> if (numId != null && overrideLevel != null) {
                            attr("val")?.toIntOrNull()?.let { startOverrides[overrideLevel!!] = it }
                        }
                        "numFmt" -> if (activeLevel != null) activeFormat = attr("val")
                        "lvlText" -> if (activeLevel != null) activeText = attr("val")
                        "start" -> if (activeLevel != null) activeStart = attr("val")?.toIntOrNull() ?: 1
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "lvl" -> {
                            val index = activeLevel
                            val definition = index?.let { Level(activeFormat, activeText, activeStart) }
                            when (activeLevelTarget) {
                                "abstract" -> if (abstractId != null && index != null && definition != null) {
                                    abstracts.getOrPut(abstractId!!) { linkedMapOf() }[index] = definition
                                }
                                "override" -> if (index != null && definition != null) levelOverrides[index] = definition
                            }
                            activeLevel = null
                            activeLevelTarget = null
                        }
                        "lvlOverride" -> overrideLevel = null
                        "abstractNum" -> abstractId = null
                        "num" -> {
                            val id = numId
                            val abstract = numAbstractId
                            if (id != null && abstract != null) {
                                instances[id] = NumberingInstance(abstract, levelOverrides.toMap(), startOverrides.toMap())
                            }
                            numId = null
                            numAbstractId = null
                            levelOverrides.clear()
                            startOverrides.clear()
                        }
                    }
                }
                parser.next()
            }
            return DocxNumbering(abstracts.mapValues { it.value.toMap() }, instances)
        }
    }
}

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
        val numbering = DocxNumbering.read(zip)
        val numberingCounters = mutableMapOf<Pair<String, Int>, Int>()
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
        var inNumPr = false
        var paragraphNumId: String? = null
        var paragraphListLevel: String? = null
        var paragraphListMarker: String? = null
        var paragraphListMarkerResolved = false
        var paragraphListMarkerEmitted = false
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
            if (!paragraphListMarkerResolved) {
                paragraphListMarker = paragraphNumId?.let {
                    numbering.marker(it, paragraphListLevel, numberingCounters)
                }
                paragraphListMarkerResolved = true
            }
            val listMarker = if (paragraphListMarkerEmitted) "" else paragraphListMarker.orEmpty()
            if (listMarker.isNotEmpty()) paragraphListMarkerEmitted = true
            val content = listMarker + paragraphText.toString()
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
                        paragraphNumId = null
                        paragraphListLevel = null
                        paragraphListMarker = null
                        paragraphListMarkerResolved = false
                        paragraphListMarkerEmitted = false
                    }
                    "numPr" -> if (paragraphOpen) inNumPr = true
                    "numId" -> if (paragraphOpen && inNumPr) paragraphNumId = attribute(source, "val")
                    "ilvl" -> if (paragraphOpen && inNumPr) paragraphListLevel = attribute(source, "val")
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
                        inNumPr = false
                    }
                    "numPr" -> inNumPr = false
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
