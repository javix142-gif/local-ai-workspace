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
        val text=zip.getInputStream(entry).use{StructuredDocuments.bounded(it,8*1024*1024)}.toString(Charsets.UTF_8)
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
    fun docx(zip:ZipFile)=ParsedDocument(listOf(ParsedPage(null,text(xml(zip,"word/document.xml")))))
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
